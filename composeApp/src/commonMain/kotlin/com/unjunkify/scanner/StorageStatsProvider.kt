package com.unjunkify.scanner

/**
 * Honest on-device storage aggregate (Task 3).
 *
 * Backed on Android by [android.app.usage.StorageStatsManager]
 * (`queryStatsForPackage` / `queryStatsForUser`, API 29+) combined with
 * filesystem totals. No `MANAGE_EXTERNAL_STORAGE`, no network.
 * Sizes are estimates and must be labeled as such in UI.
 */
data class StorageSummary(
    /** Total internal-storage bytes (filesystem capacity). */
    val totalBytes: Long,
    /** Used internal-storage bytes. */
    val usedBytes: Long,
    /** Used percent, 0..100 — the real input for the health score (feeds T5). */
    val usedPct: Int,
    /** This app's own bytes (app + cache + data), best-effort, null when unavailable. */
    val appBytes: Long? = null,
)

/** Pure used-percent math, JVM-testable. Returns 0 on degenerate input. */
fun storageUsedPct(totalBytes: Long, freeBytes: Long): Int {
    if (totalBytes <= 0L) return 0
    val used = (totalBytes - freeBytes).coerceIn(0L, totalBytes)
    return ((used * 100.0) / totalBytes).toInt().coerceIn(0, 100)
}

interface StorageStatsProvider {
    /** Real storage aggregate, or null when unreadable (caller degrades gracefully). */
    suspend fun querySummary(): StorageSummary?

    /** Convenience for score inputs; null means "unknown, don't move the score". */
    suspend fun storageUsedPct(): Int? =
        try {
            querySummary()?.usedPct
        } catch (_: Exception) {
            null
        }

    /** This package's own bytes via `queryStatsForPackage`, null when unavailable. */
    suspend fun queryStatsForPackage(packageName: String): Long? = null
}
