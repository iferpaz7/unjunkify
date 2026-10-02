package com.unjunkify.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface JunkSnapshotDao {
    @Query("SELECT * FROM junk_snapshot ORDER BY scannedAt DESC LIMIT :limit")
    fun recentFlow(limit: Int = 30): Flow<List<JunkSnapshot>>

    @Query("SELECT * FROM junk_snapshot ORDER BY scannedAt DESC")
    suspend fun allOnce(): List<JunkSnapshot>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<JunkSnapshot>)

    @Query("DELETE FROM junk_snapshot WHERE pathOrKey IN (:keys)")
    suspend fun deleteByKeys(keys: List<String>)

    @Query("DELETE FROM junk_snapshot WHERE scannedAt < :olderThan")
    suspend fun pruneOlderThan(olderThan: Long)
}
