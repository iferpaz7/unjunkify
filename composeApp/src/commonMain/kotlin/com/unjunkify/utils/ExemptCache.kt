package com.unjunkify.utils

/**
 * In-memory snapshot of exempt ids for the scan hot path.
 *
 * The scanner checks this synchronously and only falls back to Room when
 * the cache is still empty.
 */
class ExemptCache {

    @Volatile
    private var exempt: Set<String> = emptySet()

    fun replaceAll(ids: Collection<String>) {
        exempt = ids.toSet()
    }

    fun isExempt(targetId: String): Boolean = exempt.contains(targetId)

    fun isEmpty(): Boolean = exempt.isEmpty()

    fun size(): Int = exempt.size
}
