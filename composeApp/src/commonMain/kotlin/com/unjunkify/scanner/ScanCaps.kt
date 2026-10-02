package com.unjunkify.scanner

/**
 * Scan budget caps (Task 3). Pure values + helper so the budget is
 * JVM-unit-testable without a device (SC-002: full scan < 30s).
 */
object ScanCaps {
    /** Newest-first MediaStore rows visited per collection query. */
    const val MAX_MEDIA_ROWS = 10_000

    /** Wall-clock budget for one [StorageScanner.scan] pass, ms. */
    const val SCAN_BUDGET_MS = 30_000L

    /** Snapshots kept per scan (existing behavior, unchanged). */
    const val MAX_RESULTS = 150

    /** Duplicate candidates kept (owned by T6's content-hash work). */
    const val MAX_DUPLICATES = 20
}

/** Keeps at most [max] items, preserving order. Pure, unit-tested. */
fun <T> capResults(items: List<T>, max: Int = ScanCaps.MAX_RESULTS): List<T> =
    if (items.size <= max || max < 0) items else items.take(max)
