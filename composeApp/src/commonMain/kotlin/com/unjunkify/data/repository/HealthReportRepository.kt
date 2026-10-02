package com.unjunkify.data.repository

import com.unjunkify.data.HealthReport
import com.unjunkify.data.JunkSnapshot
import kotlinx.coroutines.flow.Flow

interface HealthReportRepository {
    fun recentSnapshotsFlow(limit: Int = 30): Flow<List<JunkSnapshot>>
    fun recentReportsFlow(limit: Int = 30): Flow<List<HealthReport>>
    suspend fun saveScan(items: List<JunkSnapshot>)
    suspend fun recordCleanup(reclaimedBytes: Long, removedCount: Int, score: Int)
    suspend fun deleteSnapshotsByKeys(keys: List<String>)
}
