package com.kgs.calendar.ui.time

import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A day-and-month date in the order of [locale]'s language: "Fr., 23. Okt." in German and
 * "Fri, Oct 23" in English. [month] is "MMM" or "MMMM", [weekday] "EEE", "EEEE" or null.
 */
internal fun dayMonthFormatter(
    locale: Locale,
    month: String = "MMM",
    weekday: String? = null,
    year: Boolean = false,
): DateTimeFormatter {
    val german = locale.language == Locale.GERMAN.language
    val dayMonth = if (german) "d. $month" else "$month d"
    val withYear = when {
        !year -> dayMonth
        german -> "$dayMonth yyyy"
        else -> "$dayMonth, yyyy"
    }
    return DateTimeFormatter.ofPattern(weekday?.let { "$it, $withYear" } ?: withYear, locale)
}

/** A short weekday with the day of the month: "Fr. 23." in German, "Fri 23" in English. */
internal fun weekdayDayFormatter(locale: Locale): DateTimeFormatter =
    DateTimeFormatter.ofPattern(if (locale.language == Locale.GERMAN.language) "EEE d." else "EEE d", locale)
