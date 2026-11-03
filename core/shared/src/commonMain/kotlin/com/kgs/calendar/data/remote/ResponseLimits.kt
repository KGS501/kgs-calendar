package com.kgs.calendar.data.remote

/** Upper bound for one CalDAV response; a multiget batch or listing stays far below it. */
internal const val MAX_DAV_RESPONSE_BYTES = 64L * 1024 * 1024

/** Upper bound for one subscribed iCalendar feed. */
internal const val MAX_FEED_RESPONSE_BYTES = 32L * 1024 * 1024

/** A response larger than its limit; not transient, retrying returns the same. */
typealias ResponseTooLargeException = com.kgs.calendar.platform.ResponseTooLargeException
