package com.unjunkify.data.repository

import com.unjunkify.data.BatteryEvent
import com.unjunkify.data.BatteryEventDao
import kotlinx.coroutines.flow.Flow

class BatteryRepositoryImpl(
    private val dao: BatteryEventDao,
) : BatteryRepository {

    private fun dayAgo(): Long = currentTime() - 24 * 60 * 60 * 1000L

    override fun history24hFlow(): Flow<List<BatteryEvent>> = dao.sinceFlow(dayAgo())

    override suspend fun history24hOnce(): List<BatteryEvent> = dao.sinceOnce(dayAgo())

    override suspend fun historySinceOnce(sinceMs: Long): List<BatteryEvent> = dao.sinceOnce(sinceMs)

    override suspend fun record(event: BatteryEvent) {
        dao.insert(event)
    }

    override suspend fun prune(olderThan: Long) {
        dao.pruneOlderThan(olderThan)
    }
}
