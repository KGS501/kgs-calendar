package com.kgs.calendar.data.recurrence

import com.kgs.calendar.data.ical.IcalPlatform
import com.kgs.calendar.time.ZoneId

/**
 * Tells whether a series repeats too often to be expanded. Views then fall back to its first
 * occurrence, so the item says so instead of its dates just being missing.
 */
object RecurrenceBudget {
    private const val SAMPLE_WINDOW_MILLIS = 31L * 24L * 60L * 60L * 1000L

    /** Checks a month from [fromMillis] or the series' start, whichever is later. */
    fun isExceeded(rule: String?, allDay: Boolean, startMillis: Long, zone: ZoneId, fromMillis: Long): Boolean {
        val readableRule = rule?.takeIf { it.isNotBlank() && runCatching { RRule.parse(it) }.isSuccess } ?: return false
        val windowStart = maxOf(startMillis, fromMillis)
        return IcalPlatform.recurrenceStarts(readableRule, allDay, startMillis, zone, windowStart, windowStart + SAMPLE_WINDOW_MILLIS) == null
    }
}
