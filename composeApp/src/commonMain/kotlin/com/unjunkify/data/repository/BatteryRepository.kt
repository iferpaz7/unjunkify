package com.unjunkify.data.repository

import com.unjunkify.data.BatteryEvent
import kotlinx.coroutines.flow.Flow

interface BatteryRepository {
    fun history24hFlow(): Flow<List<BatteryEvent>>
    suspend fun history24hOnce(): List<BatteryEvent>
    /**
     * T5: arbitrary-window history (the DAO already supports it; the worker
     * retains 7d). Defaults to filtering the 24h snapshot so existing fakes
     * keep compiling; the real impl queries the DAO directly.
     */
    suspend fun historySinceOnce(sinceMs: Long): List<BatteryEvent> =
        history24hOnce().filter { it.at >= sinceMs }
    suspend fun record(event: BatteryEvent)
    suspend fun prune(olderThan: Long)
}
