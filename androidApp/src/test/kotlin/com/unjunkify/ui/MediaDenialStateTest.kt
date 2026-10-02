package com.unjunkify.ui

import com.unjunkify.data.ExemptEntry
import com.unjunkify.data.HealthReport
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fix-round 1/5: partial media grants (images yes / video no) must be tracked
 * per-type so the CleanScreen banner never overstates a full denial.
 */
class MediaDenialStateTest {

    private fun viewModel(): CleanViewModel {
        val scanner = object : StorageScanner {
            override suspend fun scan(): List<JunkSnapshot> = emptyList()
            override fun categories(): Set<String> = emptySet()
        }
        val cleaner = object : JunkCleaner {
            override suspend fun deleteDirect(items: List<JunkSnapshot>): DeleteOutcome =
                DeleteOutcome(items, items.sumOf { it.estimatedBytes }, emptyList())
            override suspend fun deleteAfterConsent(items: List<JunkSnapshot>): DeleteOutcome =
                DeleteOutcome(items, items.sumOf { it.estimatedBytes }, emptyList())
        }
        val exempts = object : ExemptRepository {
            override fun exemptIdsFlow(): Flow<Set<String>> = flowOf(emptySet())
            override fun entriesFlow(): Flow<List<ExemptEntry>> = flowOf(emptyList())
            override suspend fun allOnce(): List<ExemptEntry> = emptyList()
            override suspend fun add(entry: ExemptEntry) = Unit
            override suspend fun removeById(targetId: String) = Unit
        }
        val health = object : HealthReportRepository {
            override fun recentSnapshotsFlow(limit: Int): Flow<List<JunkSnapshot>> =
                flowOf(emptyList())
            override fun recentReportsFlow(limit: Int): Flow<List<HealthReport>> =
                flowOf(emptyList())
            override suspend fun saveScan(items: List<JunkSnapshot>) = Unit
            override suspend fun recordCleanup(reclaimedBytes: Long, removedCount: Int, score: Int) =
                Unit
            override suspend fun deleteSnapshotsByKeys(keys: List<String>) = Unit
        }
        return CleanViewModel(scanner, cleaner, exempts, health, ExemptCache(), null, null, Dispatchers.Unconfined)
    }

    @Test
    fun initial_noDenial() {
        val vm = viewModel()
        assertFalse(vm.imagesPermissionDenied.value)
        assertFalse(vm.videoPermissionDenied.value)
        assertFalse(vm.mediaPermissionDenied.value)
    }

    @Test
    fun partialGrant_imagesOnlyDenied() {
        val vm = viewModel()
        vm.setMediaPermissionDenied(imagesDenied = true, videoDenied = false)
        assertTrue(vm.imagesPermissionDenied.value)
        assertFalse(vm.videoPermissionDenied.value)
        assertTrue(vm.mediaPermissionDenied.value)
    }

    @Test
    fun partialGrant_videoOnlyDenied() {
        val vm = viewModel()
        vm.setMediaPermissionDenied(imagesDenied = false, videoDenied = true)
        assertFalse(vm.imagesPermissionDenied.value)
        assertTrue(vm.videoPermissionDenied.value)
        assertTrue(vm.mediaPermissionDenied.value)
    }

    @Test
    fun fullDenial_bothDenied() {
        val vm = viewModel()
        vm.setMediaPermissionDenied(imagesDenied = true, videoDenied = true)
        assertTrue(vm.imagesPermissionDenied.value)
        assertTrue(vm.videoPermissionDenied.value)
        assertTrue(vm.mediaPermissionDenied.value)
    }

    @Test
    fun legacySingleSetter_setsBoth() {
        val vm = viewModel()
        vm.setMediaPermissionDenied(true)
        assertTrue(vm.imagesPermissionDenied.value)
        assertTrue(vm.videoPermissionDenied.value)
        vm.setMediaPermissionDenied(false)
        assertFalse(vm.imagesPermissionDenied.value)
        assertFalse(vm.videoPermissionDenied.value)
        assertFalse(vm.mediaPermissionDenied.value)
    }
}
