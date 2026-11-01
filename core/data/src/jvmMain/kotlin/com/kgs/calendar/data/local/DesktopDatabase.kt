package com.kgs.calendar.data.local

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import java.io.File

/** The calendar database in [file], using the bundled SQLite. */
fun desktopDatabase(file: File): KgsDatabase {
    file.parentFile?.mkdirs()
    return Room.databaseBuilder<KgsDatabase>(name = file.absolutePath)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
}
