package com.unjunkify.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BatteryEventDao {
    @Query("SELECT * FROM battery_event WHERE at >= :since ORDER BY at DESC")
    fun sinceFlow(since: Long): Flow<List<BatteryEvent>>

    @Query("SELECT * FROM battery_event WHERE at >= :since ORDER BY at DESC")
    suspend fun sinceOnce(since: Long): List<BatteryEvent>

    @Insert
    suspend fun insert(event: BatteryEvent)

    @Query("DELETE FROM battery_event WHERE at < :olderThan")
    suspend fun pruneOlderThan(olderThan: Long)
}
