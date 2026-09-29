package com.kgs.calendar.data.remote

import java.io.IOException
import java.net.MalformedURLException
import java.net.URISyntaxException

/** Request timeout, too early, rate limits and server errors can clear up without the request changing. */
fun Int.isTransientHttpStatus(): Boolean =
    this == 408 || this == 425 || this == 429 || this >= 500

/**
 * Whether this failure, its cause, or any independently suppressed failure, can clear up on its
 * own: network I/O and timeouts, or an HTTP status that [isTransientHttpStatus] accepts or that is
 * in [alsoTransientStatuses]. Invalid URLs and anything unrecognised are not transient. Each throwable is visited once, so cyclic cause/suppressed graphs end.
 */
fun Throwable.isTransientFailure(alsoTransientStatuses: Set<Int> = emptySet()): Boolean {
    val pending = ArrayDeque<Throwable>()
    val visited = mutableSetOf<Throwable>()
    pending.add(this)
    while (pending.isNotEmpty()) {
        val current = pending.removeFirst()
        if (!visited.add(current)) continue
        pending.addAll(current.suppressed)
        when (current) {
            is HttpStatusException -> if (current.statusCode.isTransientHttpStatus() || current.statusCode in alsoTransientStatuses) return true
            is MalformedURLException, is URISyntaxException, is IllegalArgumentException -> Unit
            is IOException -> return true
            else -> current.cause?.let(pending::add)
        }
    }
    return false
}
