package com.unjunkify.data

import com.unjunkify.ui.dayKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Task 7: persistent one-alert-per-episode overheat gate.
 *
 * Episode key = [dayKey] + charging-spike temperature band, so a repeated
 * 42°C-charging trigger on the same day notifies only once, a UI dismiss
 * stays silent across process restarts, and a new day (or a hotter band)
 * is a new episode that may alert again.
 */
interface OverheatAlertStore {
    val lastAlertedEpisode: StateFlow<String?>
    val lastDismissedEpisode: StateFlow<String?>
    fun isSilenced(episodeKey: String): Boolean =
        episodeKey == lastAlertedEpisode.value || episodeKey == lastDismissedEpisode.value
    suspend fun markAlerted(episodeKey: String)
    suspend fun markDismissed(episodeKey: String)
}

/** Charging-spike band: 42–44°C is one episode, ≥45°C a hotter one. */
fun tempBand(tempC: Float): String = if (tempC >= 45f) "45+" else "42-44"

fun episodeKey(at: Long, tempC: Float): String = "${dayKey(at)}|${tempBand(tempC)}"

/**
 * Pure notify gate shared by [com.unjunkify.sync.BatteryCheckWorker] logic:
 * alert only for a charging spike ≥42°C whose episode was neither notified
 * nor dismissed yet.
 */
fun shouldNotifyOverheat(
    lastAlerted: String?,
    lastDismissed: String?,
    at: Long,
    tempC: Float,
    charging: Boolean,
): Boolean {
    if (tempC < 42f || !charging) return false
    val episode = episodeKey(at, tempC)
    return episode != lastAlerted && episode != lastDismissed
}

/** In-memory store: default for previews/iOS and unit tests. */
class InMemoryOverheatAlertStore : OverheatAlertStore {
    private val _alerted = MutableStateFlow<String?>(null)
    private val _dismissed = MutableStateFlow<String?>(null)
    override val lastAlertedEpisode: StateFlow<String?> = _alerted.asStateFlow()
    override val lastDismissedEpisode: StateFlow<String?> = _dismissed.asStateFlow()
    override suspend fun markAlerted(episodeKey: String) {
        _alerted.value = episodeKey
    }
    override suspend fun markDismissed(episodeKey: String) {
        _dismissed.value = episodeKey
    }
}
