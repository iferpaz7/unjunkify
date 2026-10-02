package com.unjunkify.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T6 gates: duplicates are decided by content-sample hash (+ normalized
 * name) within an exact-size bucket — never by size alone.
 */
class DuplicateGroupingTest {

    private fun cand(
        key: String,
        size: Long,
        bytes: ByteArray,
        name: String = "photo.jpg",
    ) = DuplicateGrouping.Candidate(
        key = key,
        sizeBytes = size,
        sampleHash = DuplicateGrouping.sampleHashHex(bytes),
        nameKey = DuplicateGrouping.normalizeName(name),
    )

    @Test
    fun sameSize_differentContent_zeroDuplicates() {
        val a = cand("content://media/1", 2048L, "first-file-bytes".toByteArray())
        val b = cand("content://media/2", 2048L, "second-file-bytes".toByteArray())
        assertTrue(DuplicateGrouping.flagDuplicateKeys(listOf(a, b)).isEmpty())
    }

    @Test
    fun sameContent_sameSize_copySuffix_flagged() {
        val bytes = "identical-photo-bytes".toByteArray()
        val a = cand("content://media/1", 2048L, bytes, "photo.jpg")
        val b = cand("content://media/2", 2048L, bytes, "photo (1).jpg")
        assertEquals(setOf("content://media/2"), DuplicateGrouping.flagDuplicateKeys(listOf(a, b)))
    }

    @Test
    fun differentSizes_sameContent_neverFlagged() {
        val bytes = "identical-photo-bytes".toByteArray()
        val a = cand("content://media/1", 2048L, bytes)
        val b = cand("content://media/2", 4096L, bytes)
        assertTrue(DuplicateGrouping.flagDuplicateKeys(listOf(a, b)).isEmpty())
    }

    @Test
    fun normalizeName_stripsCopyMarkers_caseAndSpaces() {
        assertEquals("photo.jpg", DuplicateGrouping.normalizeName("photo (1).JPG"))
        assertEquals("photo.jpg", DuplicateGrouping.normalizeName("  photo - copy.jpg "))
        assertEquals("scan.png", DuplicateGrouping.normalizeName("scan copy (2).png"))
    }

    @Test
    fun flagDuplicateKeys_keepsFirstPerGroup_capsAtMax() {
        val bytes = "repeat".toByteArray()
        val many = (1..30).map { cand("content://media/$it", 512L, bytes) }
        val flagged = DuplicateGrouping.flagDuplicateKeys(many)
        assertEquals(ScanCaps.MAX_DUPLICATES, flagged.size)
        assertTrue("content://media/1" !in flagged)
    }
}
