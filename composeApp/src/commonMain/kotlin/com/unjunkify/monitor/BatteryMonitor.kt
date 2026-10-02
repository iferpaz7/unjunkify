package com.unjunkify.monitor

data class BatterySnapshot(
    val levelPct: Int,
    val tempC: Float,
    val charging: Boolean,
)

data class ConsumerUsage(
    val packageName: String,
    val label: String,
    val foregroundMinutes: Long,
)

interface BatteryMonitor {
    suspend fun snapshot(): BatterySnapshot
}

interface AppConsumerRanker {
    /** Top apps by 24h foreground time (estimate), empty when permission is missing. */
    suspend fun topConsumers(limit: Int = 5): List<ConsumerUsage>
}
