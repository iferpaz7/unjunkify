package com.unjunkify.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "health_report")
data class HealthReport(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val at: Long,
    val score: Int,
    val reclaimedBytes: Long = 0L,
    val junkCount: Int = 0,
    val overheatCount: Int = 0,
)
