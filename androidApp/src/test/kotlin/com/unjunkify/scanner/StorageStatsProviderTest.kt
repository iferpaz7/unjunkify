package com.unjunkify.scanner

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * T3 gates: the provider contract T5 consumes (`querySummary` /
 * `storageUsedPct`) behaves through a fake, and the pure used-percent math
 * is exact on edges. The Android impl needs a device (StorageStatsManager);
 * on-device verify: summary.usedPct matches Settings → Storage within a point.
 */
class StorageStatsProviderTest {

    private class FakeProvider(var summary: StorageSummary?) : StorageStatsProvider {
        var packageBytes: Long? = null
        override suspend fun querySummary(): StorageSummary? = summary
        override suspend fun queryStatsForPackage(packageName: String): Long? = packageBytes
    }

    @Test
    fun storageUsedPct_passthroughFromSummary() = runTest {
        val provider = FakeProvider(StorageSummary(1000L, 850L, 85))
        assertEquals(85, provider.storageUsedPct())
    }

    @Test
    fun storageUsedPct_nullSummaryMeansUnknown() = runTest {
        assertNull(FakeProvider(null).storageUsedPct())
    }

    @Test
    fun storageUsedPct_throwingProviderDegradesToNull() = runTest {
        val provider = object : StorageStatsProvider {
            override suspend fun querySummary(): StorageSummary? =
                throw SecurityException("denied")
        }
        assertNull(provider.storageUsedPct())
    }

    @Test
    fun queryStatsForPackage_defaultIsNull() = runTest {
        val provider = object : StorageStatsProvider {
            override suspend fun querySummary(): StorageSummary? = null
        }
        assertNull(provider.queryStatsForPackage("com.unjunkify"))
    }

    @Test
    fun storageUsedPctMath_exactAndClamped() {
        assertEquals(85, storageUsedPct(1000L, 150L))
        assertEquals(0, storageUsedPct(1000L, 1000L))
        assertEquals(100, storageUsedPct(1000L, 0L))
        assertEquals(0, storageUsedPct(0L, 0L))
        assertEquals(0, storageUsedPct(-5L, 10L))
        assertEquals(100, storageUsedPct(1000L, -50L))
        assertEquals(0, storageUsedPct(1000L, 5000L))
    }
}
