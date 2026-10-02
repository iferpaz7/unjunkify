package com.unjunkify.monitor

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

class AndroidBatteryMonitor(
    private val context: Context,
) : BatteryMonitor {

    override suspend fun snapshot(): BatterySnapshot {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val battery = context.registerReceiver(null, filter)
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val tempTenths = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 250) ?: 250
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val pct = if (level >= 0 && scale > 0) (level * 100 / scale) else 0
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        return BatterySnapshot(
            levelPct = pct.coerceIn(0, 100),
            tempC = tempTenths / 10f,
            charging = charging,
        )
    }
}
