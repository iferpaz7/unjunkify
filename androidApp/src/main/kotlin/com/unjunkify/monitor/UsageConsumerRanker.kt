package com.unjunkify.monitor

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process

/**
 * Optional usage-stats ranking (T4 opt-in).
 *
 * `PACKAGE_USAGE_STATS` is NOT declared in the manifest as an install
 * permission: it is a protected/special access grant the user toggles in
 * system Settings (`Settings.ACTION_USAGE_ACCESS_SETTINGS`). Denial degrades
 * gracefully — [topConsumers] returns empty and Health shows battery/temperature
 * only, never crashing (FR-009).
 *
 * Ranking input is `UsageStats.totalTimeInForeground`, so results are labeled
 * in the UI as "foreground time (estimate)", not background drain.
 */
class UsageConsumerRanker(
    private val context: Context,
) : AppConsumerRanker {

    override suspend fun topConsumers(limit: Int): List<ConsumerUsage> {
        if (!hasUsagePermission()) return emptyList()
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val end = System.currentTimeMillis()
            val start = end - 24 * 60 * 60 * 1000L
            val pm = context.packageManager
            val samples = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
                .orEmpty()
                .map {
                    val label = try {
                        pm.getApplicationLabel(
                            pm.getApplicationInfo(it.packageName, 0),
                        ).toString()
                    } catch (_: PackageManager.NameNotFoundException) {
                        it.packageName
                    }
                    ForegroundSample(
                        packageName = it.packageName,
                        label = label,
                        foregroundMillis = it.totalTimeInForeground,
                    )
                }
            gatedRank(hasPermission = true, samples = samples, limit = limit)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Usage-access grant via AppOps on API 23+.
     * `unsafeCheckOpNoThrow` exists from API 29 (Q); API 23–28 uses the older
     * `checkOpNoThrow`. Previously this returned false below Q, hiding the
     * opt-in on most supported devices (minSdk 29 still needs the Q path, but
     * the gate must not blanket-deny by SDK).
     */
    internal fun hasUsagePermission(): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            @Suppress("DEPRECATION")
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName,
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName,
                )
            } else {
                return false
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            false
        }
    }
}

/** Pure ranking input: one app's 24h foreground time. Framework-free for JVM tests. */
data class ForegroundSample(
    val packageName: String,
    val label: String,
    val foregroundMillis: Long,
)

/**
 * Permission gate + pure rank. Denied (or empty input) → empty list so Health
 * falls back to battery/temperature only; granted → top foreground-time apps,
 * descending. Limit coerced to 1..10.
 */
internal fun gatedRank(
    hasPermission: Boolean,
    samples: List<ForegroundSample>,
    limit: Int,
): List<ConsumerUsage> {
    if (!hasPermission) return emptyList()
    return samples
        .filter { it.foregroundMillis > 0 }
        .sortedByDescending { it.foregroundMillis }
        .take(limit.coerceIn(1, 10))
        .map {
            ConsumerUsage(
                packageName = it.packageName,
                label = it.label,
                foregroundMinutes = it.foregroundMillis / 60000,
            )
        }
}
