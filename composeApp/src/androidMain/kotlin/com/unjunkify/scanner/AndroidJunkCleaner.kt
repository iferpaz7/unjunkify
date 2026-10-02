package com.unjunkify.scanner

import android.app.RecoverableSecurityException
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.unjunkify.data.JunkSnapshot
import java.io.File

/**
 * Two-phase MediaStore delete per Android docs `Access media files > Remove
 * an item`: try a direct delete, catch [RecoverableSecurityException] for
 * foreign media, and surface those URIs for a batched system consent dialog
 * (`MediaStore.createDeleteRequest()` on API 30+, the recoverable action's
 * [IntentSender] on API 29). Own-created media deletes silently (no dialog).
 *
 * Reclaimed accounting is honest: only actually-removed items count, using
 * the MediaStore SIZE column captured at scan time (actual bytes, not an
 * estimate) or measured file lengths for app files.
 */
class AndroidJunkCleaner(
    private val context: Context,
) : JunkCleaner {

    /**
     * Recoverable delete action seen during the last [deleteDirect] pass.
     * Only meaningful on API 29, where `createDeleteRequest()` does not
     * exist; the host Activity consumes it via [takeConsentSender] to launch
     * the system dialog for one item, then retries the remainder.
     */
    private var lastConsentSender: IntentSender? = null

    fun takeConsentSender(): IntentSender? {
        val sender = lastConsentSender
        lastConsentSender = null
        return sender
    }

    override suspend fun deleteDirect(items: List<JunkSnapshot>): DeleteOutcome {
        val deleted = mutableListOf<JunkSnapshot>()
        val needsConsent = mutableListOf<JunkSnapshot>()
        var reclaimed = 0L
        for (item in items) {
            try {
                val bytes = deleteOne(item)
                if (bytes != null) {
                    deleted += item
                    reclaimed += bytes
                }
            } catch (e: RecoverableSecurityException) {
                // Foreign media: a system consent dialog is required.
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    lastConsentSender = try {
                        e.userAction.actionIntent.intentSender
                    } catch (_: Exception) {
                        lastConsentSender
                    }
                }
                needsConsent += item
            } catch (_: SecurityException) {
                // Non-recoverable: leave the item listed, count nothing.
            } catch (_: Exception) {
            }
        }
        return DeleteOutcome(deleted, reclaimed, needsConsent)
    }

    override suspend fun deleteAfterConsent(items: List<JunkSnapshot>): DeleteOutcome {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // API 30+: RESULT_OK fires only on grant, but count nothing
            // without re-verifying — re-query each row (gone = system
            // deleted it) and retry the delete for anything still present.
            return reverifyAfterConsent(items)
        }
        // API 29: consent granted a single item; re-attempt the rest silently.
        // Anything still recoverable is reported back for another round.
        return deleteDirect(items)
    }

    /**
     * Post-grant re-verification: a consent-gated URI counts as deleted only
     * when it is already gone from MediaStore or a retry delete removes it
     * now. Anything still present stays listed and counts nothing.
     */
    private fun reverifyAfterConsent(items: List<JunkSnapshot>): DeleteOutcome {
        val deleted = mutableListOf<JunkSnapshot>()
        val needsConsent = mutableListOf<JunkSnapshot>()
        var reclaimed = 0L
        for (item in items) {
            try {
                val uri = Uri.parse(item.pathOrKey)
                val gone = isMediaGone(uri)
                val removed = gone || context.contentResolver.delete(uri, null, null) > 0
                if (removed) {
                    deleted += item
                    reclaimed += item.estimatedBytes
                }
            } catch (e: RecoverableSecurityException) {
                needsConsent += item
            } catch (_: Exception) {
            }
        }
        return DeleteOutcome(deleted, reclaimed, needsConsent)
    }

    /** True when [uri] no longer resolves to a MediaStore row. */
    private fun isMediaGone(uri: Uri): Boolean {
        return try {
            context.contentResolver.query(
                uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null
            )?.use { cursor -> cursor.count == 0 } ?: false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Deletes one item silently. Returns reclaimed bytes, or null when the
     * item was not removed (missing row, failed file delete).
     */
    private fun deleteOne(item: JunkSnapshot): Long? {
        val key = item.pathOrKey
        if (key == "cache:self") return clearSelfCache()
        if (key.startsWith("content://")) {
            val rows = context.contentResolver.delete(Uri.parse(key), null, null)
            return if (rows > 0) item.estimatedBytes else null
        }
        val file = File(key)
        if (!file.exists()) {
            // Try as content uri fallback for odd keys.
            return try {
                val rows = context.contentResolver.delete(Uri.parse(key), null, null)
                if (rows > 0) item.estimatedBytes else null
            } catch (e: RecoverableSecurityException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }
        val size = if (file.isFile) file.length() else dirSize(file)
        return if (file.deleteRecursively()) size else null
    }

    private fun clearSelfCache(): Long {
        var freed = 0L
        val dir = context.cacheDir
        dir.listFiles()?.forEach { child ->
            try {
                freed += if (child.isFile) child.length() else dirSize(child)
                child.deleteRecursively()
            } catch (_: Exception) {
            }
        }
        return freed
    }

    private fun dirSize(root: File): Long {
        var total = 0L
        try {
            root.walkTopDown().forEach { total += if (it.isFile) it.length() else 0L }
        } catch (_: Exception) {
        }
        return total
    }
}
