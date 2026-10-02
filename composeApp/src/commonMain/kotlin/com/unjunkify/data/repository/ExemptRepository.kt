package com.unjunkify.data.repository

import com.unjunkify.data.ExemptDao
import com.unjunkify.data.ExemptEntry
import kotlinx.coroutines.flow.Flow

interface ExemptRepository {
    fun exemptIdsFlow(): Flow<Set<String>>
    fun entriesFlow(): Flow<List<ExemptEntry>>
    suspend fun allOnce(): List<ExemptEntry>
    suspend fun add(entry: ExemptEntry)
    suspend fun removeById(targetId: String)
}
