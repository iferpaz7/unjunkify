package com.unjunkify

import androidx.compose.ui.window.ComposeUIViewController
import com.unjunkify.data.JunkSnapshot
import com.unjunkify.data.repository.BatteryRepositoryImpl
import com.unjunkify.data.repository.ExemptRepositoryImpl
import com.unjunkify.data.repository.HealthReportRepositoryImpl
import com.unjunkify.monitor.AppConsumerRanker
import com.unjunkify.monitor.BatteryMonitor
import com.unjunkify.monitor.BatterySnapshot
import com.unjunkify.scanner.DeleteOutcome
import com.unjunkify.scanner.JunkCleaner
import com.unjunkify.scanner.StorageScanner
import com.unjunkify.ui.CleanViewModel
import com.unjunkify.ui.HealthViewModel
import com.unjunkify.utils.ExemptCache

/**
 * iOS entry point. No platform scanner/monitor implementations exist yet,
 * so this wires the shared Unjunkify UI to the real Unjunkify database with
 * no-op platform ports (empty scan, no-op clean, fixed battery snapshot).
 */
fun MainViewController() = ComposeUIViewController {
    val db = createUnjunkifyDatabase()
    val exemptRepository = ExemptRepositoryImpl(db.exemptDao())
    val healthRepository = HealthReportRepositoryImpl(db.junkSnapshotDao(), db.healthReportDao())
    val batteryRepository = BatteryRepositoryImpl(db.batteryEventDao())
    val exemptCache = ExemptCache()
    val scanner = object : StorageScanner {
        override suspend fun scan(): List<JunkSnapshot> = emptyList()
    }
    val cleaner = object : JunkCleaner {
        override suspend fun deleteDirect(items: List<JunkSnapshot>) =
            DeleteOutcome(emptyList(), 0L, emptyList())

        override suspend fun deleteAfterConsent(items: List<JunkSnapshot>) =
            DeleteOutcome(emptyList(), 0L, emptyList())
    }
    val monitor = object : BatteryMonitor {
        override suspend fun snapshot() = BatterySnapshot(100, 25f, false)
    }
    val ranker = object : AppConsumerRanker {
        override suspend fun topConsumers(limit: Int) = emptyList<com.unjunkify.monitor.ConsumerUsage>()
    }
    UnjunkifyApp(
        cleanViewModel = CleanViewModel(scanner, cleaner, exemptRepository, healthRepository, exemptCache),
        healthViewModel = HealthViewModel(monitor, ranker, batteryRepository),
    )
}
