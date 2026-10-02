package com.unjunkify.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T4 gates: usage-stats ranking is permission-gated and purely foreground-time
 * based. Denied → empty (Health falls back to battery/temperature only);
 * granted → ranked descending, zero-foreground entries excluded.
 * Pure JVM: exercises [gatedRank], the same function the Android ranker calls.
 */
class UsageConsumerRankerTest {

    private fun sample(pkg: String, minutes: Long) =
        ForegroundSample(
            packageName = pkg,
            label = pkg,
            foregroundMillis = minutes * 60000,
        )

    @Test
    fun denied_returnsEmpty_neverCrashes() {
        val samples = listOf(sample("com.a", 30), sample("com.b", 10))
        assertTrue(gatedRank(hasPermission = false, samples = samples, limit = 5).isEmpty())
        assertTrue(gatedRank(hasPermission = false, samples = emptyList(), limit = 5).isEmpty())
    }

    @Test
    fun granted_ranksByForegroundTimeDescending() {
        val samples = listOf(
            sample("com.low", 5),
            sample("com.high", 60),
            sample("com.mid", 20),
        )
        val ranked = gatedRank(hasPermission = true, samples = samples, limit = 5)
        assertEquals(listOf("com.high", "com.mid", "com.low"), ranked.map { it.packageName })
        assertEquals(listOf(60L, 20L, 5L), ranked.map { it.foregroundMinutes })
    }

    @Test
    fun zeroForeground_excluded() {
        val samples = listOf(sample("com.idle", 0), sample("com.used", 3))
        val ranked = gatedRank(hasPermission = true, samples = samples, limit = 5)
        assertEquals(listOf("com.used"), ranked.map { it.packageName })
    }

    @Test
    fun limit_coercedTo1dot10() {
        val samples = (1..12).map { sample("com.app$it", it.toLong()) }
        assertEquals(10, gatedRank(hasPermission = true, samples = samples, limit = 99).size)
        assertEquals(1, gatedRank(hasPermission = true, samples = samples, limit = 0).size)
        assertEquals(3, gatedRank(hasPermission = true, samples = samples, limit = 3).size)
    }
}
