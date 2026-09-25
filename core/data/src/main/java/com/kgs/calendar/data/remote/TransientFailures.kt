package com.kgs.calendar.data.remote

import java.io.IOException
import java.net.MalformedURLException
import java.net.URISyntaxException

/** Request timeout, too early, rate limits and server errors can clear up without the request changing. */
fun Int.isTransientHttpStatus(): Boolean =
    this == 408 || this == 425 || this == 429 || this >= 500

/**
 * Whether this failure, or the first recognised failure in its cause chain, can clear up on its
 * own: network I/O and timeouts, or an HTTP status that [isTransientHttpStatus] accepts or that is
 * in [alsoTransientStatuses]. Invalid URLs and anything unrecognised are not transient. The chain
 * is walked at most once per throwable, so a cyclic chain ends.
 */
fun Throwable.isTransientFailure(alsoTransientStatuses: Set<Int> = emptySet()): Boolean {
    var current: Throwable? = this
    val visited = mutableSetOf<Throwable>()
    while (current != null && visited.add(current)) {
        when (current) {
            is HttpStatusException ->
                return current.statusCode.isTransientHttpStatus() || current.statusCode in alsoTransientStatuses
            // Invalid URL/config: checked before IOException because MalformedURLException is one.
            is MalformedURLException, is URISyntaxException, is IllegalArgumentException -> return false
            is IOException -> return true
        }
        current = current.cause
    }
    return false
}
