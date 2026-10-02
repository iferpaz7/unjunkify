package com.unjunkify.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "exempt_entry")
data class ExemptEntry(
    @PrimaryKey val targetId: String,
    val label: String,
    val kind: String = ExemptKind.FILE,
    val source: String = ExemptSource.MANUAL,
    val createdAt: Long = 0L,
)

object ExemptKind {
    const val CACHE_PATH = "CACHE_PATH"
    const val FILE = "FILE"
    const val FOLDER = "FOLDER"
    const val APP = "APP"
}

object ExemptSource {
    const val MANUAL = "MANUAL"
    const val SEED = "SEED"
    const val SYSTEM = "SYSTEM"
}
