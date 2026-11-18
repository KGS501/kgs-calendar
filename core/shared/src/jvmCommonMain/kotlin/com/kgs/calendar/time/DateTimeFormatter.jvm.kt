package com.kgs.calendar.time

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toJavaZoneId
import kotlinx.datetime.toKotlinLocalDateTime
import java.time.temporal.WeekFields

actual typealias AppLocale = java.util.Locale

actual fun appLocaleOf(languageTag: String): AppLocale = java.util.Locale.forLanguageTag(languageTag)
actual fun defaultAppLocale(): AppLocale = java.util.Locale.getDefault()
actual fun rootAppLocale(): AppLocale = java.util.Locale.ROOT

private fun FormatStyle.toJava(): java.time.format.FormatStyle = java.time.format.FormatStyle.valueOf(name)

private data class JavaFormatterKey(
    val pattern: String?,
    val dateStyle: FormatStyle?,
    val timeStyle: FormatStyle?,
    val locale: java.util.Locale,
    val hasTime: Boolean,
    val hasDate: Boolean,
    val basicIsoDate: Boolean,
)

/**
 * Built java.time formatters. Building one parses its pattern, which costs more than the formatting
 * itself, and a sync formats and parses thousands of dates. The key holds the default locale, so a
 * formatter without a locale still follows a change of the app's language.
 */
private val javaFormatters = java.util.concurrent.ConcurrentHashMap<JavaFormatterKey, java.time.format.DateTimeFormatter>()

private fun DateTimeFormatter.toJava(hasTime: Boolean, hasDate: Boolean): java.time.format.DateTimeFormatter {
    val key = JavaFormatterKey(
        pattern = pattern,
        dateStyle = dateStyle,
        timeStyle = timeStyle,
        locale = locale ?: java.util.Locale.getDefault(java.util.Locale.Category.FORMAT),
        hasTime = hasTime,
        hasDate = hasDate,
        basicIsoDate = this === DateTimeFormatter.BASIC_ISO_DATE,
    )
    return javaFormatters.getOrPut(key) { buildJava(hasTime, hasDate) }
}

private fun DateTimeFormatter.buildJava(hasTime: Boolean, hasDate: Boolean): java.time.format.DateTimeFormatter {
    // java.time's own constant parses strictly: a date such as 20240230 is an error, not 29 February.
    if (this === DateTimeFormatter.BASIC_ISO_DATE) return java.time.format.DateTimeFormatter.BASIC_ISO_DATE
    val base = when {
        pattern != null -> java.time.format.DateTimeFormatter.ofPattern(pattern)
        dateStyle != null && timeStyle != null && hasTime && hasDate ->
            java.time.format.DateTimeFormatter.ofLocalizedDateTime(dateStyle.toJava(), timeStyle.toJava())
        timeStyle != null && hasTime -> java.time.format.DateTimeFormatter.ofLocalizedTime(timeStyle.toJava())
        else -> java.time.format.DateTimeFormatter.ofLocalizedDate((dateStyle ?: FormatStyle.MEDIUM).toJava())
    }
    return locale?.let(base::withLocale) ?: base
}

internal actual fun platformFormat(
    formatter: DateTimeFormatter,
    dateTime: LocalDateTime,
    zone: TimeZone?,
    hasTime: Boolean,
    hasDate: Boolean,
): String {
    val javaFormatter = formatter.toJava(hasTime, hasDate)
    val local = dateTime.toJavaLocalDateTime()
    return when {
        zone != null -> javaFormatter.format(local.atZone(zone.toJavaZoneId()))
        !hasDate -> javaFormatter.format(local.toLocalTime())
        !hasTime -> javaFormatter.format(local.toLocalDate())
        else -> javaFormatter.format(local)
    }
}

internal actual fun platformParse(formatter: DateTimeFormatter, text: String, hasTime: Boolean, timeOnly: Boolean): LocalDateTime {
    val javaFormatter = formatter.toJava(hasTime, !timeOnly)
    return when {
        timeOnly -> java.time.LocalTime.parse(text.removePrefix("2000-01-01 "), javaFormatter)
            .atDate(java.time.LocalDate.of(2000, 1, 1)).toKotlinLocalDateTime()
        hasTime -> java.time.LocalDateTime.parse(text, javaFormatter).toKotlinLocalDateTime()
        else -> java.time.LocalDate.parse(text, javaFormatter).atStartOfDay().toKotlinLocalDateTime()
    }
}

actual fun DayOfWeek.getDisplayName(style: TextStyle, locale: AppLocale): String =
    java.time.DayOfWeek.of(isoDayNumber).getDisplayName(java.time.format.TextStyle.valueOf(style.name), locale)

actual fun Month.getDisplayName(style: TextStyle, locale: AppLocale): String =
    java.time.Month.of(number).getDisplayName(java.time.format.TextStyle.valueOf(style.name), locale)

actual fun firstDayOfWeekFor(locale: AppLocale): DayOfWeek =
    DayOfWeek(WeekFields.of(locale).firstDayOfWeek.value)
