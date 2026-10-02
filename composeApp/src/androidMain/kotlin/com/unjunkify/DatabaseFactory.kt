package com.unjunkify

import android.content.Context
import androidx.room.Room
import com.unjunkify.data.UnjunkifyDatabase

fun createUnjunkifyDatabase(context: Context): UnjunkifyDatabase {
    return Room.databaseBuilder(
        context.applicationContext,
        UnjunkifyDatabase::class.java,
        "unjunkify_database"
    )
        .addCallback(UnjunkifyDatabase.SeedCallback())
        .fallbackToDestructiveMigration(false)
        .build()
}
