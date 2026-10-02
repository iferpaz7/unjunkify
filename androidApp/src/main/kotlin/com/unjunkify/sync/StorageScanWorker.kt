package com.unjunkify.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.unjunkify.UnjunkifyApp
import com.unjunkify.scanner.AndroidStorageScanner
import com.unjunkify.utils.filterExempt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.get

/** Read-only periodic storage scan. Never deletes. */
class StorageScanWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val app = applicationContext as UnjunkifyApp
            val found = AndroidStorageScanner(applicationContext).scan()
            val ids = try {
                app.exemptRepository.allOnce().map { it.targetId }.toSet()
            } catch (_: Exception) {
                emptySet()
            }
            try {
                app.exemptCache.replaceAll(ids)
            } catch (_: Exception) {
            }
            // Persist via Koin-provided repo through the app graph.
            val repo: com.unjunkify.data.repository.HealthReportRepository = app.get()
            repo.saveScan(filterExempt(found, ids))
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
