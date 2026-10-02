package com.unjunkify.utils

import com.unjunkify.data.BatteryEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T5 gates: the formula is untouched (pure cases pin it), and the new
 * input-mapping heuristics behave — drain from the 24h delta, overheat as
 * distinct days, unknown storage as no-penalty.
 */
class HealthScoreInputsTest {

    // Formula (unchanged) — pinned so T5 can't silently reshape it.

    @Test
    fun healthScore_pristineIs100() {
        assertEquals(100, healthScore(50, 0, 0))
    }

    @Test
    fun healthScore_worstIs0() {
        assertEquals(0, healthScore(95, 8, 5))
    }

    @Test
    fun healthScore_storagePressure90() {
        // (90-80)/15 * 40 = 26.67 → 100 - 26.67 = 73 (truncated).
        assertEquals(73, healthScore(90, 0, 0))
    }

    @Test
    fun healthScore_variesWithEachInput() {
        val base = healthScore(50, 0, 0)
        assertTrue(healthScore(90, 0, 0) < base)
        assertTrue(healthScore(50, 8, 0) < base)
        assertTrue(healthScore(50, 0, 5) < base)
        assertEquals(65, healthScore(50, 8, 0))
        assertEquals(75, healthScore(50, 0, 5))
    }

    // overheatDays: distinct days, 40°C warning band.

    private fun event(at: Long, tempC: Float, level: Int = 80) =
        BatteryEvent(at = at, levelPct = level, tempC = tempC, charging = false)

    @Test
    fun overheatDays_countsDistinctHotDaysOnce() {
        val day = 24L * 60 * 60 * 1000
        val events = listOf(
            event(3 * day + 1, 43f),
            event(3 * day + 2, 44f), // same day, must not double-count
            event(5 * day, 41f),
            event(6 * day, 39.9f), // below band
            event(6 * day + 1, 30f),
        )
        assertEquals(2, overheatDays(events))
    }

    @Test
    fun overheatDays_thresholdEdgeInclusive() {
        val day = 24L * 60 * 60 * 1000
        assertEquals(1, overheatDays(listOf(event(day, 40f))))
        assertEquals(0, overheatDays(listOf(event(day, 39.9f))))
        assertEquals(0, overheatDays(emptyList()))
    }

    // estimateBgDrainHours: 24h delta, charge-masked, thin-history fallback.

    @Test
    fun bgDrain_fullDischargeSaturates() {
        val now = 1_700_000_000_000L
        val events = listOf(
            event(now - 20 * 60 * 60 * 1000, 30f, 100),
            event(now, 30f, 20),
        )
        assertEquals(6, estimateBgDrainHours(events, 0)) // 80% → 80*8/100
    }

    @Test
    fun bgDrain_middayChargeDoesNotMaskDrain() {
        val now = 1_700_000_000_000L
        val events = listOf(
            event(now - 20 * 60 * 60 * 1000, 30f, 20),
            event(now - 10 * 60 * 60 * 1000, 30f, 100), // charged…
            event(now, 30f, 30), // …then drained 70
        )
        assertEquals(5, estimateBgDrainHours(events, 0)) // 70% → 5
    }

    @Test
    fun bgDrain_noDrainIsZero() {
        val now = 1_700_000_000_000L
        val events = listOf(
            event(now - 3600_000, 30f, 80),
            event(now, 30f, 85), // net charge, no discharge
        )
        assertEquals(0, estimateBgDrainHours(events, 999))
    }

    @Test
    fun bgDrain_thinHistoryFallsBackToForeground() {
        assertEquals(2, estimateBgDrainHours(listOf(event(1L, 30f)), 150))
        assertEquals(0, estimateBgDrainHours(emptyList(), 0))
        assertEquals(8, estimateBgDrainHours(emptyList(), 600))
    }

    // buildHealthScoreInputs: degrade + clamps.

    @Test
    fun buildInputs_unknownStorageIsNoPenalty() {
        val inputs = buildHealthScoreInputs(null, 0, 0)
        assertEquals(0, inputs.storageUsedPct)
        assertEquals(100, healthScore(inputs.storageUsedPct, inputs.bgDrainHours, inputs.overheat7d))
    }

    @Test
    fun buildInputs_clampsRanges() {
        val inputs = buildHealthScoreInputs(999, 99, -3)
        assertEquals(100, inputs.storageUsedPct)
        assertEquals(8, inputs.bgDrainHours)
        assertEquals(0, inputs.overheat7d)
    }
}
