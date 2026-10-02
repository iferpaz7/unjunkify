package com.unjunkify.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ExemptEntry::class, JunkSnapshot::class, BatteryEvent::class, HealthReport::class],
    version = 1,
    exportSchema = false,
)
abstract class UnjunkifyDatabase : RoomDatabase() {

    abstract fun exemptDao(): ExemptDao
    abstract fun junkSnapshotDao(): JunkSnapshotDao
    abstract fun batteryEventDao(): BatteryEventDao
    abstract fun healthReportDao(): HealthReportDao

    class SeedCallback : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            // Never auto-cleaned: OS backup dirs and password-manager vaults stay exempt.
            val seeds = listOf(
                "seed:os-backup" to "System backup directory",
                "seed:vault" to "Password vaults",
            )
            for ((id, label) in seeds) {
                db.execSQL(
                    "INSERT OR REPLACE INTO exempt_entry (targetId, label, kind, source, createdAt) VALUES (?, ?, ?, ?, ?)",
                    arrayOf<Any>(id, "[SYSTEM] $label", ExemptKind.FOLDER, ExemptSource.SEED, 0L),
                )
            }
        }
    }
}
