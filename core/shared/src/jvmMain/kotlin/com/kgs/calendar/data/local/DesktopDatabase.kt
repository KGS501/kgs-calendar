package com.kgs.calendar.data.local

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import java.io.File

/** The desktop app's database in [file], with SQLite bundled into the app (as on iOS). */
fun buildDesktopKgsDatabase(file: File): KgsDatabase {
    file.parentFile?.mkdirs()
    return Room.databaseBuilder<KgsDatabase>(name = file.absolutePath)
        .setDriver(BundledSQLiteDriver())
        .addMigrations(*KgsSharedMigrations.ALL)
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
}
