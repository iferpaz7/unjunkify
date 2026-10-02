package com.unjunkify.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unjunkify.data.ExemptEntry
import com.unjunkify.data.ExemptKind
import com.unjunkify.data.ExemptSource
import com.unjunkify.data.JunkSnapshot
import com.unjunkify.data.repository.ExemptRepository
import com.unjunkify.data.repository.HealthReportRepository
import com.unjunkify.data.repository.currentTime
import com.unjunkify.data.repository.BatteryRepository
import com.unjunkify.scanner.DeleteOutcome
import com.unjunkify.scanner.JunkCleaner
import com.unjunkify.scanner.StorageScanner
import com.unjunkify.scanner.StorageStatsProvider
import com.unjunkify.ui.state.UiState
import com.unjunkify.utils.ExemptCache
import com.unjunkify.utils.buildHealthScoreInputs
import com.unjunkify.utils.estimateBgDrainHours
import com.unjunkify.utils.filterExempt
import com.unjunkify.utils.healthScore
import com.unjunkify.utils.overheatDays
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CleanViewModel(
    private val scanner: StorageScanner,
    private val cleaner: JunkCleaner,
    private val exemptRepository: ExemptRepository,
    private val healthRepository: HealthReportRepository,
    private val exemptCache: ExemptCache,
    private val storageStats: StorageStatsProvider? = null,
    private val batteryRepository: BatteryRepository? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _selected = MutableStateFlow<Set<String>>(emptySet())
    val selected: StateFlow<Set<String>> = _selected.asStateFlow()

    private val _reclaimed = MutableStateFlow<Long?>(null)
    val lastReclaimed: StateFlow<Long?> = _reclaimed.asStateFlow()

    /**
     * Foreign media awaiting the system delete-consent dialog (T2 consent
     * flow). The host Activity collects this, launches
     * `MediaStore.createDeleteRequest()` (API 30+) or the recoverable
     * action's IntentSender (API 29), and reports back via
     * [onDeleteConsentResult]. Empty means no dialog is needed.
     */
    private val _pendingDeleteConsent = MutableStateFlow<List<JunkSnapshot>>(emptyList())
    val pendingDeleteConsent: StateFlow<List<JunkSnapshot>> = _pendingDeleteConsent.asStateFlow()

    /** Direct-delete results held while the consent dialog is outstanding. */
    private var pendingReclaimed = 0L
    private var pendingRemoved = 0

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    /**
     * Per-type media denial, set from the runtime permission result. Scan still
     * runs; denied categories are skipped and CleanScreen explains inline (FR-009).
     * Partial grants are tracked separately so the banner never overstates:
     * images-denied skips photos, video-denied skips videos.
     */
    private val _imagesPermissionDenied = MutableStateFlow(false)
    val imagesPermissionDenied: StateFlow<Boolean> = _imagesPermissionDenied.asStateFlow()

    private val _videoPermissionDenied = MutableStateFlow(false)
    val videoPermissionDenied: StateFlow<Boolean> = _videoPermissionDenied.asStateFlow()

    /** Any media denied (images or video). */
    private val _mediaPermissionDenied = MutableStateFlow(false)
    val mediaPermissionDenied: StateFlow<Boolean> = _mediaPermissionDenied.asStateFlow()

    fun setMediaPermissionDenied(imagesDenied: Boolean, videoDenied: Boolean) {
        _imagesPermissionDenied.value = imagesDenied
        _videoPermissionDenied.value = videoDenied
        _mediaPermissionDenied.value = imagesDenied || videoDenied
    }

    /** Legacy single-flag form (e.g. pre-33 single permission): sets both types. */
    fun setMediaPermissionDenied(denied: Boolean) {
        setMediaPermissionDenied(denied, denied)
    }

    val results: StateFlow<UiState<List<JunkSnapshot>>> = combine(
        healthRepository.recentSnapshotsFlow(),
        exemptRepository.entriesFlow(),
        _query,
    ) { snapshots, exempts, q ->
        val ids = exempts.map { it.targetId }.toSet()
        val visible = filterExempt(snapshots, ids)
        if (q.isBlank()) visible
        else {
            val needle = q.trim().lowercase()
            visible.filter {
                it.label.lowercase().contains(needle) || it.category.lowercase().contains(needle)
            }
        }
    }
        .map<List<JunkSnapshot>, UiState<List<JunkSnapshot>>> { UiState.Success(it) }
        .catch { emit(UiState.Error("Scan results unavailable")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState.Loading)

    val exempts: StateFlow<UiState<List<ExemptEntry>>> =
        exemptRepository.entriesFlow()
            .map<List<ExemptEntry>, UiState<List<ExemptEntry>>> { UiState.Success(it) }
            .catch { emit(UiState.Error("Exempt list unavailable")) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState.Loading)

    fun setQuery(q: String) {
        _query.value = q
    }

    fun toggleSelect(key: String) {
        _selected.value = if (_selected.value.contains(key)) _selected.value - key
        else _selected.value + key
    }

    fun clearSelection() {
        _selected.value = emptySet()
    }

    fun scan() {
        viewModelScope.launch(ioDispatcher) {
            _scanning.value = true
            try {
                val found = scanner.scan()
                val ids = exemptRepository.allOnce().map { it.targetId }.toSet()
                exemptCache.replaceAll(ids)
                healthRepository.saveScan(filterExempt(found, ids))
            } catch (_: Exception) {
            } finally {
                _scanning.value = false
            }
        }
    }

    fun exemptItem(item: JunkSnapshot) {
        viewModelScope.launch(ioDispatcher) {
            exemptRepository.add(
                ExemptEntry(
                    targetId = item.pathOrKey,
                    label = item.label,
                    kind = ExemptKind.FILE,
                    source = ExemptSource.MANUAL,
                    createdAt = currentTime(),
                )
            )
            refreshExemptCache()
            _selected.value = _selected.value - item.pathOrKey
        }
    }

    fun removeExempt(targetId: String) {
        viewModelScope.launch(ioDispatcher) {
            exemptRepository.removeById(targetId)
            refreshExemptCache()
        }
    }

    /**
     * Two-phase confirm: exempt ids are filtered first and never reach the
     * cleaner; only snapshots actually deleted leave the list, and reclaimed
     * is the actual deleted-byte sum. Foreign media that needs the system
     * dialog is published on [pendingDeleteConsent] and finalized in
     * [onDeleteConsentResult].
     */
    fun confirmDelete(items: List<JunkSnapshot>) {
        viewModelScope.launch(ioDispatcher) {
            // One consent batch at a time: the pending accumulators below
            // belong to the outstanding system dialog; a new confirm while
            // it is up would corrupt them. The selection is kept so the user
            // can retry once the dialog resolves.
            if (_pendingDeleteConsent.value.isNotEmpty()) return@launch
            val ids = exemptRepository.allOnce().map { it.targetId }.toSet()
            val safe = items.filter { it.pathOrKey !in ids }
            if (safe.isEmpty()) return@launch
            val outcome = try {
                cleaner.deleteDirect(safe)
            } catch (_: Exception) {
                DeleteOutcome(emptyList(), 0L, emptyList())
            }
            if (outcome.deleted.isNotEmpty()) {
                healthRepository.deleteSnapshotsByKeys(outcome.deleted.map { it.pathOrKey })
                _selected.value = _selected.value - outcome.deleted.map { it.pathOrKey }.toSet()
            }
            pendingReclaimed = outcome.reclaimedBytes
            pendingRemoved = outcome.deleted.size
            if (outcome.needsConsent.isEmpty()) {
                finishDeleteBatch(reclaimed = outcome.reclaimedBytes, removed = outcome.deleted.size)
            } else {
                _pendingDeleteConsent.value = outcome.needsConsent
            }
        }
    }

    /**
     * Called by the host Activity with the system delete-consent result.
     * On grant the consent batch is finalized (only actually-deleted
     * snapshots are removed); on deny the gated snapshots stay listed and
     * only the direct-delete reclaim is reported. Exempt ids are re-checked
     * so an item exempted while the dialog was open is never deleted.
     */
    fun onDeleteConsentResult(granted: Boolean) {
        val pending = _pendingDeleteConsent.value
        _pendingDeleteConsent.value = emptyList()
        if (pending.isEmpty()) return
        viewModelScope.launch(ioDispatcher) {
            val ids = exemptRepository.allOnce().map { it.targetId }.toSet()
            val stillSafe = pending.filter { it.pathOrKey !in ids }
            _selected.value = _selected.value - pending.map { it.pathOrKey }.toSet()
            if (!granted || stillSafe.isEmpty()) {
                finishDeleteBatch(reclaimed = pendingReclaimed, removed = pendingRemoved)
                resetPendingBatch()
                return@launch
            }
            val outcome = try {
                cleaner.deleteAfterConsent(stillSafe)
            } catch (_: Exception) {
                DeleteOutcome(emptyList(), 0L, emptyList())
            }
            if (outcome.deleted.isNotEmpty()) {
                healthRepository.deleteSnapshotsByKeys(outcome.deleted.map { it.pathOrKey })
            }
            if (outcome.needsConsent.isNotEmpty()) {
                // API 29 single-item grants: bank this round, ask again.
                pendingReclaimed += outcome.reclaimedBytes
                pendingRemoved += outcome.deleted.size
                _pendingDeleteConsent.value = outcome.needsConsent
                return@launch
            }
            finishDeleteBatch(
                reclaimed = pendingReclaimed + outcome.reclaimedBytes,
                removed = pendingRemoved + outcome.deleted.size,
            )
            resetPendingBatch()
        }
    }

    private fun resetPendingBatch() {
        pendingReclaimed = 0L
        pendingRemoved = 0
    }

    private suspend fun finishDeleteBatch(reclaimed: Long, removed: Int) {
        // No-op batches (nothing removed, nothing reclaimed — e.g. consent
        // denied with no direct deletes) report null so no banner shows.
        _reclaimed.value = if (removed == 0 && reclaimed == 0L) null else reclaimed
        try {
            if (removed > 0) {
                healthRepository.recordCleanup(
                    reclaimedBytes = reclaimed,
                    removedCount = removed,
                    score = currentHealthScore(),
                )
            }
        } catch (_: Exception) {
        }
    }

    /**
     * T5: real score inputs — T3 storage aggregate, 24h battery drain delta,
     * 7d overheat days. No ranker here, so the drain heuristic runs on the
     * delta alone (foreground fallback = 0). Any failure degrades to
     * no-penalty rather than blocking the cleanup record.
     */
    private suspend fun currentHealthScore(): Int {
        val now = currentTime()
        val storagePct = try {
            storageStats?.storageUsedPct()
        } catch (_: Exception) {
            null
        }
        val events7d = try {
            batteryRepository?.historySinceOnce(now - 7 * 24 * 60 * 60 * 1000L)
        } catch (_: Exception) {
            null
        } ?: emptyList()
        val dayAgo = now - 24 * 60 * 60 * 1000L
        val inputs = buildHealthScoreInputs(
            storageUsedPctOrNull = storagePct,
            bgDrainHours = estimateBgDrainHours(
                events7d.filter { it.at >= dayAgo },
                foregroundMinutesTotal = 0,
            ),
            overheat7d = overheatDays(events7d),
        )
        return healthScore(inputs.storageUsedPct, inputs.bgDrainHours, inputs.overheat7d)
    }

    fun dismissReclaimed() {
        _reclaimed.value = null
    }

    private suspend fun refreshExemptCache() {
        try {
            exemptCache.replaceAll(exemptRepository.allOnce().map { it.targetId })
        } catch (_: Exception) {
        }
    }
}
