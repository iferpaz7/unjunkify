package com.unjunkify.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * T3 gates: scan budget caps hold — newest-10k media rows per query,
 * 30s wall-clock budget, 150 snapshots kept.
 */
class ScanCapsTest {

    @Test
    fun constants_matchBrief() {
        assertEquals(10_000, ScanCaps.MAX_MEDIA_ROWS)
        assertEquals(30_000L, ScanCaps.SCAN_BUDGET_MS)
        assertEquals(150, ScanCaps.MAX_RESULTS)
        assertEquals(20, ScanCaps.MAX_DUPLICATES)
    }

    @Test
    fun capResults_keepsFirst150InOrder() {
        val items = (1..300).toList()
        val capped = capResults(items)
        assertEquals(150, capped.size)
        assertEquals((1..150).toList(), capped)
    }

    @Test
    fun capResults_shortListPassesThrough() {
        val items = listOf("a", "b")
        assertSame(items, capResults(items))
    }

    @Test
    fun capResults_exactly150Unchanged() {
        val items = (1..150).toList()
        assertEquals(150, capResults(items).size)
    }
}
