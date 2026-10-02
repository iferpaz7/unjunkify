package com.unjunkify.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "battery_event")
data class BatteryEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val at: Long,
    val levelPct: Int,
    val tempC: Float,
    val charging: Boolean,
    val consumerId: String? = null,
    val consumerLabel: String? = null,
)
