package com.kgs.calendar.data.local

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/** The app database in the Documents directory, with SQLite bundled into the app. */
fun buildIosKgsDatabase(fileName: String = "kgs-calendar.db"): KgsDatabase {
    val documents = NSFileManager.defaultManager.URLsForDirectory(NSDocumentDirectory, NSUserDomainMask).first() as platform.Foundation.NSURL
    val path = requireNotNull(documents.path) + "/" + fileName
    return Room.databaseBuilder<KgsDatabase>(name = path)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
}
