package com.unjunkify.scanner

import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.os.storage.StorageManager
import com.unjunkify.scanner.StorageStatsProvider
import com.unjunkify.scanner.StorageSummary
import com.unjunkify.scanner.storageUsedPct
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Real storage aggregate via `StorageStatsManager` (API 29+) + filesystem
 * totals. Own-package stats need no permission; [querySummary] needs none
 * either (StatFs + `queryStatsForUser`). Every call is best-effort and
 * returns null on failure so callers degrade gracefully. No network, no
 * `MANAGE_EXTERNAL_STORAGE`.
 */
class AndroidStorageStatsProvider(
    private val context: Context,
) : StorageStatsProvider {

    override suspend fun querySummary(): StorageSummary? = withContext(Dispatchers.IO) {
        try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val total = stat.totalBytes
            if (total <= 0L) return@withContext null
            val free = stat.availableBytes
            val used = (total - free).coerceIn(0L, total)
            StorageSummary(
                totalBytes = total,
                usedBytes = used,
                usedPct = storageUsedPct(total, free),
                appBytes = queryStatsForPackage(context.packageName),
            )
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun queryStatsForPackage(packageName: String): Long? =
        withContext(Dispatchers.IO) {
            try {
                val storageManager =
                    context.getSystemService(StorageManager::class.java) ?: return@withContext null
                val statsManager =
                    context.getSystemService(StorageStatsManager::class.java)
                        ?: return@withContext null
                val uuid = storageManager.getUuidForPath(context.filesDir)
                val stats = statsManager.queryStatsForPackage(
                    uuid,
                    packageName,
                    Process.myUserHandle(),
                )
                stats.appBytes + stats.cacheBytes + stats.dataBytes
            } catch (_: Exception) {
                null
            }
        }
}
