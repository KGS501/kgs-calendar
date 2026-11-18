package com.kgs.calendar.data.sync

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serializes the work that pulls from or pushes to the servers: full syncs, targeted uploads and
 * the server trash bin. Not reentrant: code already running under it must not take it again.
 */
class RemoteSyncLock {
    private val mutex = Mutex()

    suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }
}
