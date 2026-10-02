package com.unjunkify.data.repository

import com.unjunkify.data.HealthReport
import com.unjunkify.data.HealthReportDao
import com.unjunkify.data.JunkSnapshot
import com.unjunkify.data.JunkSnapshotDao
import kotlinx.coroutines.flow.Flow

class HealthReportRepositoryImpl(
    private val snapshots: JunkSnapshotDao,
    private val reports: HealthReportDao,
) : HealthReportRepository {

    override fun recentSnapshotsFlow(limit: Int): Flow<List<JunkSnapshot>> =
        snapshots.recentFlow(limit)

    override fun recentReportsFlow(limit: Int): Flow<List<HealthReport>> =
        reports.recentFlow(limit)

    override suspend fun saveScan(items: List<JunkSnapshot>) {
        if (items.isNotEmpty()) snapshots.insertAll(items)
    }

    override suspend fun recordCleanup(reclaimedBytes: Long, removedCount: Int, score: Int) {
        reports.insert(
            HealthReport(
                at = currentTime(),
                score = score,
                reclaimedBytes = reclaimedBytes,
                junkCount = removedCount,
            )
        )
    }

    override suspend fun deleteSnapshotsByKeys(keys: List<String>) {
        if (keys.isNotEmpty()) snapshots.deleteByKeys(keys)
    }
}

expect fun currentTime(): Long
