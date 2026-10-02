package com.unjunkify.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ExemptDao {
    @Query("SELECT * FROM exempt_entry ORDER BY label ASC")
    fun exemptFlow(): Flow<List<ExemptEntry>>

    @Query("SELECT * FROM exempt_entry")
    suspend fun allOnce(): List<ExemptEntry>

    @Query("SELECT targetId FROM exempt_entry")
    suspend fun allIdsOnce(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: ExemptEntry)

    @Query("DELETE FROM exempt_entry WHERE targetId = :targetId")
    suspend fun removeById(targetId: String)
}
