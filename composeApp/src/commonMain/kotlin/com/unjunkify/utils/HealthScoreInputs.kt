package com.unjunkify.utils

import com.unjunkify.data.BatteryEvent

/**
 * Real health-score inputs (Task 5).
 *
 * The formula in [healthScore] is unchanged — this file only maps on-device
 * signals to its three inputs. Pure commonMain, no Android APIs, JVM-testable.
 *
 * Heuristics (documented per plan):
 * - storageUsedPct: `StorageStatsProvider.storageUsedPct()` (the T3
 *   StorageStatsManager aggregate). Null (unreadable) degrades to 0, i.e. no
 *   storage penalty, so a missing reading never alarms the user.
 * - bgDrainHours: total discharged percent over the last 24h — the sum of
 *   positive level drops between consecutive readings, oldest → newest, so a
 *   mid-day charge top-up does not mask real drain — mapped linearly to 0..8
 *   (12.5% ≈ 1 "drain hour"; 100% ⇒ 8, saturating the drain term exactly like
 *   the formula's /8 clamp). With fewer than 2 readings the delta is
 *   unknowable, so fall back to foreground time (T4 ranker, 24h estimate):
 *   total minutes / 60, clamped 0..8. Sparse sampling underestimates drain;
 *   the fallback keeps the score from defaulting to "perfect".
 * - overheat7d: DISTINCT DAYS in the last 7d with ≥1 reading at/above
 *   [OVERHEAT_SCORE_THRESHOLD_C]. Day granularity (not raw event count) so a
 *   15-minute sampler cannot rack up 5 "strikes" in one hot afternoon; the
 *   formula saturates at 5 days anyway. 40°C is the warning band for scoring;
 *   the 42°C while-charging spike stays the notification/alert band (worker +
 *   `HealthViewModel.activeOverheatAlert`).
 */
const val OVERHEAT_SCORE_THRESHOLD_C = 40f

private const val DAY_MS = 24L * 60 * 60 * 1000

data class HealthScoreInputs(
    val storageUsedPct: Int,
    val bgDrainHours: Int,
    val overheat7d: Int,
)

/** Distinct-day overheat count (see file docs for why days, not events). */
fun overheatDays(events: List<BatteryEvent>, thresholdC: Float = OVERHEAT_SCORE_THRESHOLD_C): Int =
    events.asSequence()
        .filter { it.tempC >= thresholdC }
        .map { it.at / DAY_MS }
        .toSet()
        .size

/** 24h-discharge → 0..8 heuristic (see file docs); ranker fallback when thin. */
fun estimateBgDrainHours(events: List<BatteryEvent>, foregroundMinutesTotal: Long): Int {
    if (events.size >= 2) {
        val byTime = events.sortedBy { it.at }
        var discharged = 0
        for (i in 1 until byTime.size) {
            val drop = byTime[i - 1].levelPct - byTime[i].levelPct
            if (drop > 0) discharged += drop
        }
        return (discharged.coerceIn(0, 100) * 8 / 100).coerceIn(0, 8)
    }
    return (foregroundMinutesTotal / 60).toInt().coerceIn(0, 8)
}

/** Combines raw signals; unknown storage degrades to "no penalty". */
fun buildHealthScoreInputs(
    storageUsedPctOrNull: Int?,
    bgDrainHours: Int,
    overheat7d: Int,
): HealthScoreInputs = HealthScoreInputs(
    storageUsedPct = (storageUsedPctOrNull ?: 0).coerceIn(0, 100),
    bgDrainHours = bgDrainHours.coerceIn(0, 8),
    overheat7d = overheat7d.coerceAtLeast(0),
)
