package com.unjunkify

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import com.unjunkify.data.AndroidOverheatAlertStore
import com.unjunkify.data.OverheatAlertStore
import com.unjunkify.data.UnjunkifyDatabase
import com.unjunkify.data.repository.ExemptRepository
import com.unjunkify.di.appModule
import com.unjunkify.monitor.AndroidBatteryMonitor
import com.unjunkify.monitor.AppConsumerRanker
import com.unjunkify.monitor.BatteryMonitor
import com.unjunkify.monitor.UsageConsumerRanker
import com.unjunkify.scanner.AndroidJunkCleaner
import com.unjunkify.scanner.AndroidStorageScanner
import com.unjunkify.scanner.AndroidStorageStatsProvider
import com.unjunkify.scanner.JunkCleaner
import com.unjunkify.scanner.StorageScanner
import com.unjunkify.scanner.StorageStatsProvider
import com.unjunkify.utils.ExemptCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.dsl.module

class UnjunkifyApp : Application(), Configuration.Provider {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var healthDatabase: UnjunkifyDatabase
        private set

    val exemptRepository: ExemptRepository
        get() = get()

    val exemptCache: ExemptCache
        get() = get()

    override fun onCreate() {
        super.onCreate()
        healthDatabase = createUnjunkifyDatabase(this)

        startKoin {
            androidLogger()
            androidContext(this@UnjunkifyApp)
            modules(
                module {
                    single { healthDatabase }
                    single { healthDatabase.exemptDao() }
                    single { healthDatabase.junkSnapshotDao() }
                    single { healthDatabase.batteryEventDao() }
                    single { healthDatabase.healthReportDao() }
                    single<StorageScanner> { AndroidStorageScanner(androidContext()) }
                    single<StorageStatsProvider> { AndroidStorageStatsProvider(androidContext()) }
                    single<JunkCleaner> { AndroidJunkCleaner(androidContext()) }
                    single<BatteryMonitor> { AndroidBatteryMonitor(androidContext()) }
                    single<AppConsumerRanker> { UsageConsumerRanker(androidContext()) }
                    single<OverheatAlertStore> { AndroidOverheatAlertStore(androidContext()) }
                },
                appModule
            )
        }

        prewarmDatabase()
        mirrorExemptToCache()
    }

    private fun mirrorExemptToCache() {
        appScope.launch {
            try {
                val cache: ExemptCache = get()
                exemptRepository.entriesFlow()
                    .catch { Log.e("UnjunkifyApp", "Exempt flow failed", it) }
                    .collect { entries ->
                        cache.replaceAll(entries.map { it.targetId })
                    }
            } catch (e: Exception) {
                Log.e("UnjunkifyApp", "Failed to mirror exempt to cache", e)
            }
        }
    }

    private fun prewarmDatabase() {
        Thread {
            try {
                healthDatabase.openHelper.writableDatabase
            } catch (_: Exception) {
            }
        }.start()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.INFO)
            .build()
}
