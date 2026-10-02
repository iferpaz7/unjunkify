package com.unjunkify.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unjunkify.data.BatteryEvent
import com.unjunkify.data.OverheatAlertStore
import com.unjunkify.data.InMemoryOverheatAlertStore
import com.unjunkify.data.episodeKey
import com.unjunkify.data.repository.BatteryRepository
import com.unjunkify.data.repository.currentTime
import com.unjunkify.monitor.AppConsumerRanker
import com.unjunkify.monitor.BatteryMonitor
import com.unjunkify.monitor.BatterySnapshot
import com.unjunkify.monitor.ConsumerUsage
import com.unjunkify.scanner.StorageStatsProvider
import com.unjunkify.ui.state.UiState
import com.unjunkify.utils.healthScore
import com.unjunkify.utils.overheatDays
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HealthViewModel(
    private val monitor: BatteryMonitor,
    private val ranker: AppConsumerRanker,
    private val batteryRepository: BatteryRepository,
    private val storageStats: StorageStatsProvider? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val alertStore: OverheatAlertStore = InMemoryOverheatAlertStore(),
) : ViewModel() {

    private val _snapshot = MutableStateFlow<BatterySnapshot?>(null)
    val snapshot: StateFlow<BatterySnapshot?> = _snapshot.asStateFlow()

    private val _dismissedAlertDay = MutableStateFlow<String?>(null)

    val history: StateFlow<UiState<List<BatteryEvent>>> =
        batteryRepository.history24hFlow()
            .map<List<BatteryEvent>, UiState<List<BatteryEvent>>> { UiState.Success(it) }
            .catch { emit(UiState.Error("Battery history unavailable")) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState.Loading)

    private val _consumers = MutableStateFlow<List<ConsumerUsage>>(emptyList())
    val consumers: StateFlow<List<ConsumerUsage>> = _consumers.asStateFlow()

    /**
     * T5 real score inputs. Null storage = unreadable (no penalty, don't
     * alarm); overheat counts distinct 7d days ≥40°C via [overheatDays].
     * Drain is derived on the fly in [HealthScreen] from the 24h history +
     * foreground minutes via `estimateBgDrainHours`.
     */
    private val _storageUsedPct = MutableStateFlow<Int?>(null)
    val storageUsedPct: StateFlow<Int?> = _storageUsedPct.asStateFlow()

    private val _overheat7d = MutableStateFlow(0)
    val overheat7d: StateFlow<Int> = _overheat7d.asStateFlow()

    fun refresh() {
        viewModelScope.launch(ioDispatcher) {
            try {
                val snap = monitor.snapshot()
                _snapshot.value = snap
                batteryRepository.record(
                    BatteryEvent(
                        at = currentTime(),
                        levelPct = snap.levelPct,
                        tempC = snap.tempC,
                        charging = snap.charging,
                    )
                )
            } catch (_: Exception) {
            }
            try {
                _consumers.value = ranker.topConsumers()
            } catch (_: Exception) {
                _consumers.value = emptyList()
            }
            try {
                _storageUsedPct.value = storageStats?.storageUsedPct()
            } catch (_: Exception) {
                _storageUsedPct.value = null
            }
            try {
                val weekAgo = currentTime() - 7 * 24 * 60 * 60 * 1000L
                _overheat7d.value =
                    overheatDays(batteryRepository.historySinceOnce(weekAgo))
            } catch (_: Exception) {
            }
        }
    }

    /** One alert per overheat episode (T026/Task 7): hottest charging spike ≥42°C.
     * Episode key = day + temp band ([episodeKey]); suppressed when that
     * episode was already notified or dismissed — persisted in [OverheatAlertStore]
     * so it survives process restarts. [dismissedDay] keeps backward compat
     * with callers holding a plain day key. */
    fun activeOverheatAlert(events: List<BatteryEvent>, dismissedDay: String?): String? {
        val spike = events
            .filter { it.tempC >= 42f && it.charging }
            .maxByOrNull { it.tempC } ?: return null
        val day = dayKey(spike.at)
        val episode = episodeKey(spike.at, spike.tempC)
        if (alertStore.isSilenced(episode)) return null
        val dismissed = dismissedDay ?: _dismissedAlertDay.value
            ?: alertStore.lastDismissedEpisode.value
        if (dismissed == day || dismissed == episode) return null
        return "Phone hot ${"%.0f".format(spike.tempC)}°C while charging — pause intensive apps or unplug for a while."
    }

    fun dismissAlert(day: String) {
        _dismissedAlertDay.value = day
        viewModelScope.launch(ioDispatcher) {
            try {
                alertStore.markDismissed(day)
            } catch (_: Exception) {
            }
        }
    }

    fun dismissedDay(): StateFlow<String?> = _dismissedAlertDay.asStateFlow()

    fun scoreFor(storageUsedPct: Int, bgDrainHours: Int, overheat7d: Int): Int =
        healthScore(storageUsedPct, bgDrainHours, overheat7d)
}

fun dayKey(epochMs: Long): String {
    val dayMs = 24L * 60 * 60 * 1000
    return "d${epochMs / dayMs}"
}
