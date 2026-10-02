package com.unjunkify.scanner

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.unjunkify.data.JunkCategory
import com.unjunkify.data.JunkSnapshot
import kotlinx.coroutines.withTimeoutOrNull

/**
 * MediaStore grouping (large/screenshot/installer/duplicates) plus this
 * app's own `cacheDir` only — never a system-wide cache clean (Task 3).
 * All sizes are MediaStore/file estimates; UI labels them as estimates.
 *
 * Budget: newest-first [ScanCaps.MAX_MEDIA_ROWS] rows per collection,
 * [ScanCaps.SCAN_BUDGET_MS] wall-clock cap, [ScanCaps.MAX_RESULTS] kept.
 * Overheat pause is out of scope (see SDD ledger).
 */
class AndroidStorageScanner(
    private val context: Context,
) : StorageScanner {

    override fun categories(): Set<String> = setOf(
        JunkCategory.TEMP_CACHE,
        JunkCategory.LARGE_FILE,
        JunkCategory.DUPLICATE,
        JunkCategory.SCREENSHOT,
        JunkCategory.INSTALLER,
    )

    override suspend fun scan(): List<JunkSnapshot> {
        val now = System.currentTimeMillis()
        val deadline = now + ScanCaps.SCAN_BUDGET_MS
        val out = mutableListOf<JunkSnapshot>()
        try {
            val collected = withTimeoutOrNull(ScanCaps.SCAN_BUDGET_MS) {
                // FR-009 graceful degrade: on denial the scan still runs and the
                // affected media categories are skipped (self-cache always runs).
                if (canReadVideo()) {
                    out += scanMedia(
                        uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        now = now,
                        largeBytes = 100L * 1024 * 1024,
                        deadline = deadline,
                    )
                }
                if (System.currentTimeMillis() >= deadline) return@withTimeoutOrNull out
                if (canReadImages()) {
                    out += scanMedia(
                        uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        now = now,
                        largeBytes = 50L * 1024 * 1024,
                        deadline = deadline,
                    )
                }
                if (System.currentTimeMillis() >= deadline) return@withTimeoutOrNull out
                if (canReadImages() || canReadVideo()) {
                    out += scanInstallers(now, deadline)
                }
                out += scanSelfCache(now)
                out += findDuplicates(out, now)
                out
            } ?: out
            return capResults(collected)
        } catch (_: Exception) {
        }
        return capResults(out)
    }

    private fun has(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun canReadImages(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            has(android.Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            has(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    private fun canReadVideo(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            has(android.Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            has(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    private data class MediaHit(
        val uri: Uri,
        val name: String,
        val size: Long,
        val relativePath: String,
    )

    private fun queryMedia(
        uri: Uri,
        selection: String?,
        args: Array<String>?,
        maxRows: Int = ScanCaps.MAX_MEDIA_ROWS,
        deadline: Long = Long.MAX_VALUE,
    ): List<MediaHit> {
        val hits = mutableListOf<MediaHit>()
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.RELATIVE_PATH,
        )
        try {
            context.contentResolver.query(
                uri, projection, selection, args,
                // Newest first: the 10k cap keeps recent media, not arbitrary rows.
                "${MediaStore.MediaColumns.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val pathIdx = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                var count = 0
                while (cursor.moveToNext() && count < maxRows) {
                    if (System.currentTimeMillis() >= deadline) break
                    val id = cursor.getLong(idIdx)
                    hits += MediaHit(
                        uri = Uri.withAppendedPath(uri, id.toString()),
                        name = cursor.getString(nameIdx) ?: "media",
                        size = try {
                            cursor.getLong(sizeIdx)
                        } catch (_: Exception) {
                            0L
                        },
                        relativePath = try {
                            cursor.getString(pathIdx) ?: ""
                        } catch (_: Exception) {
                            ""
                        },
                    )
                    count++
                }
            }
        } catch (_: Exception) {
        }
        return hits
    }

    private fun scanMedia(uri: Uri, now: Long, largeBytes: Long, deadline: Long): List<JunkSnapshot> {
        val out = mutableListOf<JunkSnapshot>()
        for (hit in queryMedia(uri, null, null, deadline = deadline)) {
            if (hit.relativePath.lowercase().contains("screenshot")) {
                out += JunkSnapshot(
                    scannedAt = now,
                    category = JunkCategory.SCREENSHOT,
                    pathOrKey = hit.uri.toString(),
                    label = hit.name,
                    estimatedBytes = hit.size,
                )
            } else if (hit.size >= largeBytes) {
                out += JunkSnapshot(
                    scannedAt = now,
                    category = JunkCategory.LARGE_FILE,
                    pathOrKey = hit.uri.toString(),
                    label = hit.name,
                    estimatedBytes = hit.size,
                )
            }
        }
        return out
    }

    private fun scanInstallers(now: Long, deadline: Long): List<JunkSnapshot> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return emptyList()
        val out = mutableListOf<JunkSnapshot>()
        for (hit in queryMedia(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?",
            arrayOf("%.apk"),
            deadline = deadline,
        )) {
            out += JunkSnapshot(
                scannedAt = now,
                category = JunkCategory.INSTALLER,
                pathOrKey = hit.uri.toString(),
                label = hit.name,
                estimatedBytes = hit.size,
            )
        }
        return out
    }

    /**
     * This app's own cache only — scoped storage forbids touching other
     * apps' caches, so no system-wide cache clean is implied. Measured
     * file lengths, still an estimate of reclaimable bytes.
     */
    private fun scanSelfCache(now: Long): List<JunkSnapshot> {
        return try {
            var total = 0L
            context.cacheDir.walkTopDown().forEach { total += if (it.isFile) it.length() else 0L }
            if (total <= 0L) return emptyList()
            listOf(
                JunkSnapshot(
                    scannedAt = now,
                    category = JunkCategory.TEMP_CACHE,
                    pathOrKey = "cache:self",
                    label = "App temporary cache (estimate)",
                    estimatedBytes = total,
                )
            )
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun findDuplicates(collected: List<JunkSnapshot>, now: Long): List<JunkSnapshot> {
        // T6: content-hash duplicates, never size-alone. Pre-bucket by exact
        // size (cheap, no I/O), then hash only the first 64KB of files that
        // share a size; unreadable files fail closed (never flagged). Rows
        // stay individual snapshots so per-item confirm is preserved.
        val deadline = now + ScanCaps.SCAN_BUDGET_MS
        val sized = collected
            .filter { it.estimatedBytes > 0 && it.pathOrKey.startsWith("content://") }
            .groupBy { it.estimatedBytes }
            .values
            .filter { it.size > 1 }
            .flatten()
        if (sized.isEmpty()) return emptyList()
        val candidates = sized.mapNotNull { snap ->
            if (System.currentTimeMillis() >= deadline) return@mapNotNull null
            val hash = sampleHashFor(snap.pathOrKey) ?: return@mapNotNull null
            DuplicateGrouping.Candidate(
                key = snap.pathOrKey,
                sizeBytes = snap.estimatedBytes,
                sampleHash = hash,
                nameKey = DuplicateGrouping.normalizeName(snap.label),
            )
        }
        val flagged = DuplicateGrouping.flagDuplicateKeys(candidates, ScanCaps.MAX_DUPLICATES)
        if (flagged.isEmpty()) return emptyList()
        return sized
            .filter { it.pathOrKey in flagged }
            .take(ScanCaps.MAX_DUPLICATES)
            .map { it.copy(category = JunkCategory.DUPLICATE, scannedAt = now) }
    }

    /**
     * FNV-1a hex of the first [DuplicateGrouping.SAMPLE_HASH_BYTES] of the
     * content URI, or null when unreadable (fail closed: never flag).
     */
    private fun sampleHashFor(uriString: String): String? {
        return try {
            val uri = Uri.parse(uriString) ?: return null
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val buf = ByteArray(DuplicateGrouping.SAMPLE_HASH_BYTES)
                var filled = 0
                while (filled < buf.size) {
                    val read = stream.read(buf, filled, buf.size - filled)
                    if (read <= 0) break
                    filled += read
                }
                if (filled <= 0) return null
                DuplicateGrouping.sampleHashHex(buf.copyOf(filled))
            }
        } catch (_: Exception) {
            null
        }
    }
}
