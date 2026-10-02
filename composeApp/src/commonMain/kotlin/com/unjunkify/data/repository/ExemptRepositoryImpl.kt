package com.unjunkify.data.repository

import com.unjunkify.data.ExemptDao
import com.unjunkify.data.ExemptEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ExemptRepositoryImpl(
    private val dao: ExemptDao,
) : ExemptRepository {

    override fun exemptIdsFlow(): Flow<Set<String>> =
        dao.exemptFlow().map { list -> list.map { it.targetId }.toSet() }

    override fun entriesFlow(): Flow<List<ExemptEntry>> = dao.exemptFlow()

    override suspend fun allOnce(): List<ExemptEntry> = dao.allOnce()

    override suspend fun add(entry: ExemptEntry) = dao.upsert(entry)

    override suspend fun removeById(targetId: String) = dao.removeById(targetId)
}
