package com.unjunkify.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "junk_snapshot")
data class JunkSnapshot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val scannedAt: Long,
    val category: String,
    val pathOrKey: String,
    val label: String,
    val estimatedBytes: Long,
    val count: Int = 1,
)

object JunkCategory {
    const val TEMP_CACHE = "TEMP_CACHE"
    const val LARGE_FILE = "LARGE_FILE"
    const val DUPLICATE = "DUPLICATE"
    const val SCREENSHOT = "SCREENSHOT"
    const val INSTALLER = "INSTALLER"
}
