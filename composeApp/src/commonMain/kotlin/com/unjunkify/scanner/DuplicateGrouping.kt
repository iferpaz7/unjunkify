package com.unjunkify.scanner

/**
 * Content-based duplicate grouping (Task 6).
 *
 * Never flag by size alone: candidates are bucketed by exact size, then by a
 * content-sample hash (first [SAMPLE_HASH_BYTES] of file bytes), then by
 * normalized display name. Only members of a group with >1 member are
 * duplicates; same-size-different-content is never flagged.
 *
 * The name level is deliberately conservative: `photo.jpg` matches
 * `photo (1).jpg` (OS copy suffix), but two different names with identical
 * bytes are NOT flagged (false negatives are safe; false positives are not —
 * deletion is suggest-and-confirm per item).
 *
 * Pure commonMain so the grouping is JVM-unit-testable without a device.
 * Hash is 64-bit FNV-1a over the sample: fast, dependency-free, multiplatform.
 * A collision would only surface one extra suggest-and-confirm row, and the
 * user still confirms each deletion individually.
 */
object DuplicateGrouping {

    /** Bytes of file content sampled for the hash (brief: 64KB). */
    const val SAMPLE_HASH_BYTES = 64 * 1024

    /** Normalizes a MediaStore DISPLAY_NAME for duplicate comparison. */
    fun normalizeName(displayName: String): String {
        val trimmed = displayName.trim().lowercase()
        if (trimmed.isEmpty()) return trimmed
        val dot = trimmed.lastIndexOf('.')
        val stem = if (dot > 0) trimmed.substring(0, dot) else trimmed
        val ext = if (dot > 0) trimmed.substring(dot) else ""
        // Strip OS copy markers: "name (1)", "name - copy", "name copy", "name(2)".
        // Loop: "scan copy (2)" -> "scan copy" -> "scan".
        var base = stem
        while (true) {
            val next = base
                .removeSuffix("copy")
                .trimEnd()
                .removeSuffix("-")
                .trimEnd()
                .replace(Regex("""\(\d+\)$"""), "")
                .trimEnd()
            if (next == base) break
            base = next
        }
        return base + ext
    }

    /** 64-bit FNV-1a hex of the content sample. */
    fun sampleHashHex(sample: ByteArray): String {
        var hash = -3750763034362895579L // FNV offset basis (as signed Long)
        for (b in sample) {
            hash = hash xor (b.toLong() and 0xFF)
            hash *= 1099511628211L // FNV prime
        }
        return hash.toULong().toString(16).padStart(16, '0')
    }

    /** One hashable file: [key] identifies the snapshot row (its URI string). */
    data class Candidate(
        val key: String,
        val sizeBytes: Long,
        val sampleHash: String,
        val nameKey: String,
    )

    /**
     * Returns the keys flagged as duplicates: for each (size, sampleHash,
     * nameKey) group with >1 member, every member after the first.
     * Order-stable, capped at [max].
     */
    fun flagDuplicateKeys(
        candidates: List<Candidate>,
        max: Int = ScanCaps.MAX_DUPLICATES,
    ): Set<String> {
        val flagged = LinkedHashSet<String>()
        candidates
            .groupBy { it.sizeBytes }
            .values
            .filter { it.size > 1 }
            .flatMap { sized -> sized.groupBy { it.sampleHash to it.nameKey }.values }
            .filter { it.size > 1 }
            .flatMap { it.drop(1) }
            .take(if (max < 0) Int.MAX_VALUE else max)
            .forEach { flagged += it.key }
        return flagged
    }
}
