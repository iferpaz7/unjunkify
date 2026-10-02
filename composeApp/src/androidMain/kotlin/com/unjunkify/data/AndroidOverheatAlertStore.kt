package com.unjunkify.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Task 7: SharedPreferences-backed [OverheatAlertStore]. Synchronous
 * constructor load keeps the worker gate (a non-suspend StateFlow read)
 * correct even on the first run after a process restart. No network,
 * no new permissions.
 */
class AndroidOverheatAlertStore(context: Context) : OverheatAlertStore {
    private val prefs =
        context.applicationContext.getSharedPreferences("overheat_alerts", Context.MODE_PRIVATE)

    private val _alerted = MutableStateFlow(prefs.getString(KEY_ALERTED, null))
    private val _dismissed = MutableStateFlow(prefs.getString(KEY_DISMISSED, null))
    override val lastAlertedEpisode: StateFlow<String?> = _alerted.asStateFlow()
    override val lastDismissedEpisode: StateFlow<String?> = _dismissed.asStateFlow()

    override suspend fun markAlerted(episodeKey: String) {
        _alerted.value = episodeKey
        prefs.edit().putString(KEY_ALERTED, episodeKey).apply()
    }

    override suspend fun markDismissed(episodeKey: String) {
        _dismissed.value = episodeKey
        prefs.edit().putString(KEY_DISMISSED, episodeKey).apply()
    }

    companion object {
        const val KEY_ALERTED = "last_alerted_episode"
        const val KEY_DISMISSED = "last_dismissed_episode"
    }
}
