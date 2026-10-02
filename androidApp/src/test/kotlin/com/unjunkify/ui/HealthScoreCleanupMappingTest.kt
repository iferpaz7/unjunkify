package com.unjunkify.ui

import com.unjunkify.data.BatteryEvent
import com.unjunkify.data.ExemptEntry
import com.unjunkify.data.HealthReport
import com.unjunkify.data.JunkCategory
import com.unjunkify.data.JunkSnapshot
import com.unjunkify.data.repository.BatteryRepository
import com.unjunkify.data.repository.ExemptRepository
import com.unjunkify.data.repository.HealthReportRepository
import com.unjunkify.monitor.AppConsumerRanker
import com.unjunkify.monitor.BatteryMonitor
import com.unjunkify.monitor.BatterySnapshot
import com.unjunkify.monitor.ConsumerUsage
import com.unjunkify.scanner.DeleteOutcome
import com.unjunkify.scanner.JunkCleaner
import com.unjunkify.scanner.StorageScanner
import com.unjunkify.scanner.StorageStatsProvider
import com.unjunkify.scanner.StorageSummary
import com.unjunkify.utils.ExemptCache
import com.unjunkify.utils.buildHealthScoreInputs
import com.unjunkify.utils.estimateBgDrainHours
import com.unjunkify.utils.healthScore
import com.unjunkify.utils.overheatDays
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * T5 gates: cleanup records a score built from REAL inputs (T3 storage
 * aggregate, 24h drain delta, 7d overheat days) — not the old hardcoded
 * `healthScore(50, 0, 0)` = 100 — and `HealthViewModel.refresh()` publishes
 * the same real inputs. Failures degrade to no-penalty instead of crashing.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HealthScoreCleanupMappingTest {

    // Wall-clock based: the VMs window history off real currentTime().
    private val now = System.currentTimeMillis()
    private val hour = 60L * 60 * 1000
    private val day = 24 * hour

    private fun event(at: Long, level: Int, tempC: Float) =
        BatteryEvent(at = at, levelPct = level, tempC = tempC, charging = false)

    /** 7d window: 2 hot days + a 24h drain of 80 → 20. */
    private fun weekEvents() = listOf(
        event(now - 5 * day, 90, 43f),
        event(now - 3 * day, 85, 41f),
        event(now - 20 * hour, 100, 30f),
        event(now, 20, 31f),
    )

    private class FakeBattery(val events: List<BatteryEvent>) : BatteryRepository {
        override fun history24hFlow(): Flow<List<BatteryEvent>> = flowOf(events)
        override suspend fun history24hOnce(): List<BatteryEvent> = events
        override suspend fun historySinceOnce(sinceMs: Long): List<BatteryEvent> =
            events.filter { it.at >= sinceMs }
        override suspend fun record(event: BatteryEvent) = Unit
        override suspend fun prune(olderThan: Long) = Unit
    }

    private class FakeStats(val pct: Int?) : StorageStatsProvider {
        override suspend fun querySummary(): StorageSummary? =
            pct?.let { StorageSummary(1000L, (it * 10).toLong(), it) }
    }

    private class FakeHealth : HealthReportRepository {
        var lastScore: Int? = null
        override fun recentSnapshotsFlow(limit: Int): Flow<List<JunkSnapshot>> = flowOf(emptyList())
        override fun recentReportsFlow(limit: Int): Flow<List<HealthReport>> = flowOf(emptyList())
        override suspend fun saveScan(items: List<JunkSnapshot>) = Unit
        override suspend fun recordCleanup(reclaimedBytes: Long, removedCount: Int, score: Int) {
            lastScore = score
        }
        override suspend fun deleteSnapshotsByKeys(keys: List<String>) = Unit
    }

    private class FakeExempts : ExemptRepository {
        override fun exemptIdsFlow(): Flow<Set<String>> = flowOf(emptySet())
        override fun entriesFlow(): Flow<List<ExemptEntry>> = flowOf(emptyList())
        override suspend fun allOnce(): List<ExemptEntry> = emptyList()
        override suspend fun add(entry: ExemptEntry) = Unit
        override suspend fun removeById(targetId: String) = Unit
    }

    private val scanner = object : StorageScanner {
        override suspend fun scan(): List<JunkSnapshot> = emptyList()
        override fun categories(): Set<String> = emptySet()
    }
    private val cleaner = object : JunkCleaner {
        override suspend fun deleteDirect(items: List<JunkSnapshot>): DeleteOutcome =
            DeleteOutcome(items, items.sumOf { it.estimatedBytes }, emptyList())
        override suspend fun deleteAfterConsent(items: List<JunkSnapshot>): DeleteOutcome =
            DeleteOutcome(items, items.sumOf { it.estimatedBytes }, emptyList())
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun snap(key: String) = JunkSnapshot(
        scannedAt = 0L,
        category = JunkCategory.LARGE_FILE,
        pathOrKey = key,
        label = key,
        estimatedBytes = 100L,
    )

    @Test
    fun cleanup_recordsRealScore_notHardcoded100() {
        val events = weekEvents()
        val health = FakeHealth()
        val vm = CleanViewModel(
            scanner, cleaner, FakeExempts(), health, ExemptCache(),
            FakeStats(90), FakeBattery(events),
            Dispatchers.Unconfined,
        )

        vm.confirmDelete(listOf(snap("k1")))

        val recent = events.filter { it.at >= now - day }
        val expected = buildHealthScoreInputs(
            storageUsedPctOrNull = 90,
            bgDrainHours = estimateBgDrainHours(recent, 0),
            overheat7d = overheatDays(events),
        ).let { healthScore(it.storageUsedPct, it.bgDrainHours, it.overheat7d) }
        assertEquals(expected, health.lastScore)
        assertTrue("score must move under pressure/drain/heat", expected < 100)
    }

    @Test
    fun cleanup_degradesToNoPenaltyWhenSignalsMissing() {
        val health = FakeHealth()
        val failingStats = object : StorageStatsProvider {
            override suspend fun querySummary(): StorageSummary? =
                throw SecurityException("denied")
        }
        val vm = CleanViewModel(
            scanner, cleaner, FakeExempts(), health, ExemptCache(),
            failingStats, FakeBattery(emptyList()),
            Dispatchers.Unconfined,
        )

        vm.confirmDelete(listOf(snap("k1")))

        assertEquals(100, health.lastScore)
    }

    @Test
    fun healthRefresh_publishesRealStorageAndOverheat() {
        val events = weekEvents()
        val monitor = object : BatteryMonitor {
            override suspend fun snapshot() = BatterySnapshot(55, 33f, false)
        }
        val ranker = object : AppConsumerRanker {
            override suspend fun topConsumers(limit: Int) =
                listOf(ConsumerUsage("p", "App", 30))
        }
        val vm = HealthViewModel(
            monitor, ranker, FakeBattery(events), FakeStats(85),
            Dispatchers.Unconfined,
        )

        vm.refresh()

        assertEquals(85, vm.storageUsedPct.value)
        assertEquals(2, vm.overheat7d.value)
        val score = vm.scoreFor(
            vm.storageUsedPct.value ?: 0,
            estimateBgDrainHours(
                events.filter { it.at >= now - day },
                foregroundMinutesTotal = 30,
            ),
            vm.overheat7d.value,
        )
        assertTrue("score must move under pressure/drain/heat", score < 100)
    }
}
