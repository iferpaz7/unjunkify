package com.unjunkify.scanner

import com.unjunkify.data.JunkSnapshot

/**
 * Outcome of a silent (no system dialog) delete pass.
 *
 * Per Android docs `Access media files > Remove an item`, deleting media the
 * app does not own throws [android.app.RecoverableSecurityException]: those
 * items land in [needsConsent] and are only removed after the user grants the
 * system delete dialog. [deleted] holds items actually removed in this pass
 * and [reclaimedBytes] is their actual byte sum — never estimates.
 */
data class DeleteOutcome(
    val deleted: List<JunkSnapshot>,
    val reclaimedBytes: Long,
    val needsConsent: List<JunkSnapshot>,
)

interface JunkCleaner {
    /**
     * Tries to delete [items] without a system dialog. Own media, app files
     * and self-cache go through silently; foreign media is reported in
     * [DeleteOutcome.needsConsent]. Never touches exempt ids (the caller
     * filters them before invoking).
     */
    suspend fun deleteDirect(items: List<JunkSnapshot>): DeleteOutcome

    /**
     * Completes deletion of [items] after the user granted the system delete
     * dialog. Returns which items were actually removed and their reclaimed
     * bytes; on API 29 items still gated are reported back in
     * [DeleteOutcome.needsConsent] for another consent round.
     */
    suspend fun deleteAfterConsent(items: List<JunkSnapshot>): DeleteOutcome
}
