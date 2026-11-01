package com.kgs.calendar.ui.platform

import java.util.Locale

/**
 * The JDK used here has no date skeletons, so the app's few skeletons are spelled out for the
 * common conventions: US English, other English and German, and a day-first default.
 */
internal actual fun bestDateTimePattern(locale: Locale, skeleton: String): String {
    val withYear = skeleton.contains('y')
    val weekday = skeleton.contains('E')
    val month = when {
        skeleton.contains("MMMM") -> "MMMM"
        skeleton.contains("MMM") -> "MMM"
        else -> "MM"
    }
    return when {
        locale.language == "en" && (locale.country.isEmpty() || locale.country == "US") ->
            (if (weekday) "EEE, " else "") + "$month d" + (if (withYear) ", y" else "")
        locale.language == "de" ->
            (if (weekday) "EEE, " else "") + "d. $month" + (if (withYear) " y" else "")
        else ->
            (if (weekday) "EEE " else "") + "d $month" + (if (withYear) " y" else "")
    }
}
