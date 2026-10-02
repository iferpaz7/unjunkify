package com.unjunkify

import androidx.room.Room
import com.unjunkify.data.UnjunkifyDatabase

fun createUnjunkifyDatabase(): UnjunkifyDatabase {
    return Room.databaseBuilder<UnjunkifyDatabase>(
        name = "unjunkify_database"
    )
        .addCallback(UnjunkifyDatabase.SeedCallback())
        .build()
}
