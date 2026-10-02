package com.unjunkify.utils

import kotlin.math.max
import kotlin.math.min

/**
 * Pure health-score calculator: 0–100, deterministic, no platform APIs.
 *
 * 100 − (storage pressure × 40 + background drain × 35 + overheat × 25).
 */
fun healthScore(storageUsedPct: Int, bgDrainHours: Int, overheat7d: Int): Int {
    val storagePressure = when {
        storageUsedPct >= 95 -> 1.0
        storageUsedPct <= 80 -> 0.0
        else -> (storageUsedPct - 80) / 15.0
    }
    val drain = min(1.0, max(0, bgDrainHours) / 8.0)
    val overheat = min(1.0, max(0, overheat7d) / 5.0)
    val penalty = storagePressure * 40.0 + drain * 35.0 + overheat * 25.0
    return (100.0 - penalty).toInt().coerceIn(0, 100)
}
