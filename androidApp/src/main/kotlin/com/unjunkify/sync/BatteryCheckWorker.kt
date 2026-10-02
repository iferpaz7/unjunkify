package com.unjunkify.sync

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.unjunkify.UnjunkifyApp
import com.unjunkify.data.BatteryEvent
import com.unjunkify.data.OverheatAlertStore
import com.unjunkify.data.episodeKey
import com.unjunkify.data.repository.currentTime
import com.unjunkify.monitor.AndroidBatteryMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.get

/** Read-only periodic battery check. Notifies only on critical overheat. */
class BatteryCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val app = applicationContext as UnjunkifyApp
            val snap = try {
                AndroidBatteryMonitor(applicationContext).snapshot()
            } catch (_: Exception) {
                return@withContext Result.retry()
            }
            val repo: com.unjunkify.data.repository.BatteryRepository = app.get()
            try {
                repo.record(
                    BatteryEvent(
                        at = currentTime(),
                        levelPct = snap.levelPct,
                        tempC = snap.tempC,
                        charging = snap.charging,
                    )
                )
                repo.prune(currentTime() - 7 * 24 * 60 * 60 * 1000L)
            } catch (_: Exception) {
            }
            if (snap.tempC >= 42f && snap.charging) {
                val now = currentTime()
                val episode = episodeKey(now, snap.tempC)
                val store: OverheatAlertStore? = try {
                    app.get()
                } catch (_: Exception) {
                    null
                }
                val silenced = try {
                    store?.isSilenced(episode) ?: false
                } catch (_: Exception) {
                    false
                }
                if (!silenced) {
                    notifyOverheat(snap.tempC)
                    try {
                        store?.markAlerted(episode)
                    } catch (_: Exception) {
                    }
                }
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun notifyOverheat(tempC: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        try {
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                manager.createNotificationChannel(
                    NotificationChannel("health-critical", "Health alerts", NotificationManager.IMPORTANCE_DEFAULT)
                )
            }
            val note = NotificationCompat.Builder(applicationContext, "health-critical")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("Phone running hot")
                .setContentText("Battery at %.0f°C while charging — pause intensive apps.".format(tempC))
                .setAutoCancel(true)
                .build()
            manager.notify(1001, note)
        } catch (_: Exception) {
        }
    }
}
