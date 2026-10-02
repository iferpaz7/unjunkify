package com.unjunkify.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HealthReportDao {
    @Query("SELECT * FROM health_report ORDER BY at DESC LIMIT :limit")
    fun recentFlow(limit: Int = 30): Flow<List<HealthReport>>

    @Query("SELECT * FROM health_report ORDER BY at DESC LIMIT 1")
    suspend fun latestOnce(): HealthReport?

    @Insert
    suspend fun insert(report: HealthReport)
}
