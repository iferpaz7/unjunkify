package com.unjunkify.ui

import com.unjunkify.data.ExemptEntry
import com.unjunkify.data.HealthReport
import com.unjunkify.data.JunkCategory
import com.unjunkify.data.JunkSnapshot
import com.unjunkify.data.repository.ExemptRepository
import com.unjunkify.data.repository.HealthReportRepository
import com.unjunkify.scanner.DeleteOutcome
import com.unjunkify.scanner.JunkCleaner
import com.unjunkify.scanner.StorageScanner
import com.unjunkify.utils.ExemptCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * T2 gates: `confirmDelete` removes only actually-deleted snapshots,
 * reclaimed is the actual deleted-byte sum (not estimates), exempt ids never
 * reach the cleaner, and the consent handshake (direct pass defers foreign
 * media → grant/deny finalizes) behaves without a device.
 *
 * The system dialog itself (`MediaStore.createDeleteRequest`) needs an
 * emulator; these JVM tests stub the handshake through a fake [JunkCleaner].
 * On-device verify: own-created media deletes silently, foreign media shows
 * the system dialog, exempt items are never deleted.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DeleteReclaimedAccountingTest {

    private class FakeCleaner(
        var direct: (List<JunkSnapshot>) -> DeleteOutcome =
            { items -> DeleteOutcome(items, items.sumOf { it.estimatedBytes }, emptyList()) },
        var afterConsent: (List<JunkSnapshot>) -> DeleteOutcome =
            { items -> DeleteOutcome(items, items.sumOf { it.estimatedBytes }, emptyList()) },
    ) : JunkCleaner {
        val directCalls = mutableListOf<List<JunkSnapshot>>()
        val consentCalls = mutableListOf<List<JunkSnapshot>>()
        override suspend fun deleteDirect(items: List<JunkSnapshot>): DeleteOutcome {
            directCalls += items
            return direct(items)
        }
        override suspend fun deleteAfterConsent(items: List<JunkSnapshot>): DeleteOutcome {
            consentCalls += items
            return afterConsent(items)
        }
    }

    private class FakeExempts(var entries: List<ExemptEntry> = emptyList()) : ExemptRepository {
        override fun exemptIdsFlow(): Flow<Set<String>> =
            flowOf(entries.map { it.targetId }.toSet())
        override fun entriesFlow(): Flow<List<ExemptEntry>> = flowOf(entries)
        override suspend fun allOnce(): List<ExemptEntry> = entries
        override suspend fun add(entry: ExemptEntry) {
            entries = entries + entry
        }
        override suspend fun removeById(targetId: String) {
            entries = entries.filter { it.targetId != targetId }
        }
    }

    private class FakeHealth : HealthReportRepository {
        val deletedKeys = mutableListOf<List<String>>()
        var lastCleanup: Pair<Long, Int>? = null
        override fun recentSnapshotsFlow(limit: Int): Flow<List<JunkSnapshot>> = flowOf(emptyList())
        override fun recentReportsFlow(limit: Int): Flow<List<HealthReport>> = flowOf(emptyList())
        override suspend fun saveScan(items: List<JunkSnapshot>) = Unit
        override suspend fun recordCleanup(reclaimedBytes: Long, removedCount: Int, score: Int) {
            lastCleanup = reclaimedBytes to removedCount
        }
        override suspend fun deleteSnapshotsByKeys(keys: List<String>) {
            deletedKeys += keys
        }
    }

    private lateinit var cleaner: FakeCleaner
    private lateinit var exempts: FakeExempts
    private lateinit var health: FakeHealth
    private lateinit var vm: CleanViewModel

    private fun snap(key: String, bytes: Long) = JunkSnapshot(
        scannedAt = 0L,
        category = JunkCategory.LARGE_FILE,
        pathOrKey = key,
        label = key,
        estimatedBytes = bytes,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        cleaner = FakeCleaner()
        exempts = FakeExempts()
        health = FakeHealth()
        val scanner = object : StorageScanner {
            override suspend fun scan(): List<JunkSnapshot> = emptyList()
            override fun categories(): Set<String> = emptySet()
        }
        vm = CleanViewModel(scanner, cleaner, exempts, health, ExemptCache(), null, null, Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun confirmDelete_removesOnlyActuallyDeleted_reclaimedIsActual() {
        val a = snap("content://media/1", 100L)
        val b = snap("content://media/2", 200L)
        val c = snap("content://media/3", 300L)
        // Cleaner actually deletes a and c only; b fails silently.
        cleaner.direct = { DeleteOutcome(listOf(a, c), 100L + 300L, emptyList()) }

        vm.confirmDelete(listOf(a, b, c))

        assertEquals(listOf(listOf("content://media/1", "content://media/3")), health.deletedKeys)
        assertEquals(400L, vm.lastReclaimed.value)
        assertEquals(400L to 2, health.lastCleanup)
    }

    @Test
    fun confirmDelete_exemptNeverReachesCleanerOrSnapshotDelete() {
        val a = snap("content://media/1", 100L)
        val b = snap("content://media/2", 200L)
        exempts.entries = listOf(ExemptEntry(targetId = "content://media/2", label = "b"))

        vm.confirmDelete(listOf(a, b))

        assertEquals(listOf("content://media/1"), cleaner.directCalls.single().map { it.pathOrKey })
        assertTrue(health.deletedKeys.flatten().none { it == "content://media/2" })
    }

    @Test
    fun consentFlow_granted_finalizesBatchWithActualBytes() {
        val a = snap("content://media/1", 100L)
        val b = snap("content://media/2", 500L)
        cleaner.direct = { DeleteOutcome(listOf(a), 100L, listOf(b)) }
        cleaner.afterConsent = { DeleteOutcome(it, 500L, emptyList()) }

        vm.confirmDelete(listOf(a, b))

        // Direct pass finalized; gated media waits for the system dialog.
        assertEquals(listOf(b), vm.pendingDeleteConsent.value)
        assertEquals(listOf(listOf("content://media/1")), health.deletedKeys)
        assertNull(vm.lastReclaimed.value)

        vm.onDeleteConsentResult(true)

        assertEquals(
            setOf("content://media/1", "content://media/2"),
            health.deletedKeys.flatten().toSet(),
        )
        assertEquals(600L, vm.lastReclaimed.value)
        assertEquals(600L to 2, health.lastCleanup)
        assertTrue(vm.pendingDeleteConsent.value.isEmpty())
    }

    @Test
    fun consentFlow_denied_keepsGatedSnapshots_reportsDirectOnly() {
        val a = snap("content://media/1", 100L)
        val b = snap("content://media/2", 500L)
        cleaner.direct = { DeleteOutcome(listOf(a), 100L, listOf(b)) }

        vm.confirmDelete(listOf(a, b))
        vm.onDeleteConsentResult(false)

        assertEquals(listOf(listOf("content://media/1")), health.deletedKeys)
        assertEquals(100L, vm.lastReclaimed.value)
        assertEquals(100L to 1, health.lastCleanup)
        assertTrue(vm.pendingDeleteConsent.value.isEmpty())
        assertTrue(cleaner.consentCalls.isEmpty())
    }

    @Test
    fun consentFlow_allDeniedNoDirectDeletes_reportsNullNoBanner() {
        val b = snap("content://media/2", 500L)
        cleaner.direct = { DeleteOutcome(emptyList(), 0L, listOf(b)) }

        vm.confirmDelete(listOf(b))
        assertNull(vm.lastReclaimed.value)

        vm.onDeleteConsentResult(false)

        // No-op batch: no banner, snapshots kept, nothing recorded.
        assertNull(vm.lastReclaimed.value)
        assertTrue(health.deletedKeys.isEmpty())
        assertNull(health.lastCleanup)
        assertTrue(vm.pendingDeleteConsent.value.isEmpty())
    }

    @Test
    fun consentFlow_exemptedWhileDialogOpen_neverDeleted() {
        val a = snap("content://media/1", 100L)
        val b = snap("content://media/2", 500L)
        cleaner.direct = { DeleteOutcome(listOf(a), 100L, listOf(b)) }

        vm.confirmDelete(listOf(a, b))
        exempts.entries = listOf(ExemptEntry(targetId = "content://media/2", label = "b"))
        vm.onDeleteConsentResult(true)

        assertTrue(cleaner.consentCalls.flatten().none { it.pathOrKey == "content://media/2" })
        assertTrue(health.deletedKeys.flatten().none { it == "content://media/2" })
        assertEquals(100L, vm.lastReclaimed.value)
    }
}
