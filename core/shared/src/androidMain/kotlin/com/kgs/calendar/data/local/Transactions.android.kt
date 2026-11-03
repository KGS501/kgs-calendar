package com.kgs.calendar.data.local

import androidx.room.withTransaction

actual suspend fun <T> KgsDatabase.inWriteTransaction(block: suspend () -> T): T = withTransaction(block)
