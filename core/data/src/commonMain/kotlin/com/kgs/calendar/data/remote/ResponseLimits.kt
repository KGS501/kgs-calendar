package com.kgs.calendar.data.remote

import okhttp3.ResponseBody
import okio.Buffer

/** Upper bound for one CalDAV response; a multiget batch or listing stays far below it. */
internal const val MAX_DAV_RESPONSE_BYTES = 64L * 1024 * 1024

/** Upper bound for one subscribed iCalendar feed. */
internal const val MAX_FEED_RESPONSE_BYTES = 32L * 1024 * 1024

/**
 * Reads the body as text, refusing more than [maxBytes] so a huge or endless response fails with a
 * message instead of exhausting memory. The failure is not transient: retrying returns the same.
 */
internal fun ResponseBody.textWithin(maxBytes: Long): String {
    val declared = contentLength()
    if (declared > maxBytes) throw ResponseTooLargeException(maxBytes)
    val buffer = Buffer()
    source().use { source ->
        while (source.read(buffer, 8_192) != -1L) {
            if (buffer.size > maxBytes) throw ResponseTooLargeException(maxBytes)
        }
    }
    val charset = contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
    return buffer.readString(charset)
}

class ResponseTooLargeException(maxBytes: Long) :
    IllegalStateException("The server's response is larger than ${maxBytes / (1024 * 1024)} MB.")
