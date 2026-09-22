package com.kgs.calendar.sync

import com.kgs.calendar.data.remote.HttpStatusException
import java.io.IOException
import java.net.MalformedURLException
import java.net.URISyntaxException

internal enum class SyncFailureOutcome { Retry, Fail }

internal const val MAX_SYNC_RUN_ATTEMPTS = 5

/**
 * Decides whether a failed background sync is worth retrying with backoff.
 *
 * Only transient failures (network I/O, timeouts, throttling, server errors) are retried, and
 * only until [MAX_SYNC_RUN_ATTEMPTS] is reached. Authentication failures, invalid URLs and
 * anything unrecognised fail immediately; periodic sync still runs again on its next interval.
 * The repository wraps source errors in `IllegalStateException(message, cause)`, so the whole
 * cause chain is inspected.
 */
internal fun classifySyncFailure(error: Throwable, runAttemptCount: Int): SyncFailureOutcome {
    if (runAttemptCount >= MAX_SYNC_RUN_ATTEMPTS) return SyncFailureOutcome.Fail
    return if (error.isTransientSyncFailure()) SyncFailureOutcome.Retry else SyncFailureOutcome.Fail
}

private fun Throwable.isTransientSyncFailure(): Boolean {
    var current: Throwable? = this
    val visited = mutableSetOf<Throwable>()
    while (current != null && visited.add(current)) {
        when (current) {
            is HttpStatusException -> return current.statusCode.isTransientHttpStatus()
            // Invalid URL/config: checked before IOException because MalformedURLException is one.
            is MalformedURLException, is URISyntaxException, is IllegalArgumentException -> return false
            is IOException -> return true
        }
        current = current.cause
    }
    return false
}

private fun Int.isTransientHttpStatus(): Boolean =
    this == 408 || this == 425 || this == 429 || this >= 500
