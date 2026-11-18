package com.kgs.calendar.data.local

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection

actual suspend fun <T> KgsDatabase.inWriteTransaction(block: suspend () -> T): T =
    useWriterConnection { transactor -> transactor.immediateTransaction { block() } }
