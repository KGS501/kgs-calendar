package com.kgs.calendar.time

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlin.time.Instant

/**
 * A user locale. On Android and the JVM this is `java.util.Locale` itself, so Android code passes
 * its locales unchanged; on iOS it wraps a BCP 47 language tag.
 */
expect class AppLocale {
    fun toLanguageTag(): String
    fun getLanguage(): String
}

expect fun appLocaleOf(languageTag: String): AppLocale
expect fun defaultAppLocale(): AppLocale
/** The locale with no language, for text that must not depend on the user (java's `Locale.ROOT`). */
expect fun rootAppLocale(): AppLocale

enum class FormatStyle { FULL, LONG, MEDIUM, SHORT }

enum class TextStyle { FULL, FULL_STANDALONE, SHORT, SHORT_STANDALONE, NARROW, NARROW_STANDALONE }

/**
 * java.time's DateTimeFormatter for the patterns and styles this app uses. Patterns follow
 * java.time (`yyyy-MM-dd`, `EEE`, `MMMM`, `HH:mm`…); on iOS they go to NSDateFormatter, whose ICU
 * patterns read the same for these letters.
 */
class DateTimeFormatter private constructor(
    internal val pattern: String?,
    internal val dateStyle: FormatStyle?,
    internal val timeStyle: FormatStyle?,
    val locale: AppLocale?,
) {
    fun withLocale(locale: AppLocale): DateTimeFormatter = DateTimeFormatter(pattern, dateStyle, timeStyle, locale)

    fun format(date: LocalDate): String = platformFormat(this, LocalDateTime(date, LocalTime(0, 0)), null, hasTime = false)
    fun format(time: LocalTime): String = platformFormat(this, LocalDateTime(LocalDate(2000, 1, 1), time), null, hasTime = true, hasDate = false)
    fun format(dateTime: LocalDateTime): String = platformFormat(this, dateTime, null, hasTime = true)
    fun format(dateTime: ZonedDateTime): String = platformFormat(this, dateTime.toLocalDateTime(), dateTime.zone, hasTime = true)
    fun format(yearMonth: YearMonth): String = platformFormat(this, LocalDateTime(yearMonth.firstDay, LocalTime(0, 0)), null, hasTime = false)

    fun parseLocalDate(text: CharSequence): LocalDate = platformParse(this, text.toString(), hasTime = false).date
    fun parseLocalDateTime(text: CharSequence): LocalDateTime = platformParse(this, text.toString(), hasTime = true)
    fun parseLocalTime(text: CharSequence): LocalTime = platformParse(this, "2000-01-01 ${text}", hasTime = true, timeOnly = true).time

    companion object {
        fun ofPattern(pattern: String): DateTimeFormatter = DateTimeFormatter(pattern, null, null, null)
        fun ofPattern(pattern: String, locale: AppLocale): DateTimeFormatter = DateTimeFormatter(pattern, null, null, locale)
        fun ofLocalizedDate(style: FormatStyle): DateTimeFormatter = DateTimeFormatter(null, style, null, null)
        fun ofLocalizedTime(style: FormatStyle): DateTimeFormatter = DateTimeFormatter(null, null, style, null)
        fun ofLocalizedDateTime(dateStyle: FormatStyle, timeStyle: FormatStyle = dateStyle): DateTimeFormatter =
            DateTimeFormatter(null, dateStyle, timeStyle, null)

        val ISO_LOCAL_DATE: DateTimeFormatter = ofPattern("yyyy-MM-dd")
        val BASIC_ISO_DATE: DateTimeFormatter = ofPattern("yyyyMMdd")
    }
}

internal expect fun platformFormat(
    formatter: DateTimeFormatter,
    dateTime: LocalDateTime,
    zone: TimeZone?,
    hasTime: Boolean,
    hasDate: Boolean = true,
): String

internal expect fun platformParse(formatter: DateTimeFormatter, text: String, hasTime: Boolean, timeOnly: Boolean = false): LocalDateTime

fun LocalDate.format(formatter: DateTimeFormatter): String = formatter.format(this)
fun LocalTime.format(formatter: DateTimeFormatter): String = formatter.format(this)
fun LocalDateTime.format(formatter: DateTimeFormatter): String = formatter.format(this)
fun ZonedDateTime.format(formatter: DateTimeFormatter): String = formatter.format(this)
fun YearMonth.format(formatter: DateTimeFormatter): String = formatter.format(this)

fun LocalDate.Companion.parse(text: CharSequence, formatter: DateTimeFormatter): LocalDate = formatter.parseLocalDate(text)
fun LocalDateTime.Companion.parse(text: CharSequence, formatter: DateTimeFormatter): LocalDateTime = formatter.parseLocalDateTime(text)
fun LocalTime.Companion.parse(text: CharSequence, formatter: DateTimeFormatter): LocalTime = formatter.parseLocalTime(text)

/** Localised weekday name, as java.time's `DayOfWeek.getDisplayName`. */
expect fun DayOfWeek.getDisplayName(style: TextStyle, locale: AppLocale): String

/** Localised month name, as java.time's `Month.getDisplayName`. */
expect fun Month.getDisplayName(style: TextStyle, locale: AppLocale): String

/** The first day of the week for [locale], as java's `WeekFields.of(locale).firstDayOfWeek`. */
expect fun firstDayOfWeekFor(locale: AppLocale): DayOfWeek

/** ISO-8601-style week number of [date] for weeks starting on [firstDayOfWeek] with [minimalDays] in week one. */
fun weekOfWeekBasedYear(date: LocalDate, firstDayOfWeek: DayOfWeek, minimalDays: Int = 4): Int {
    fun weekStart(d: LocalDate) = TemporalAdjusters.previousOrSame(firstDayOfWeek)(d)
    fun firstWeekStart(year: Int): LocalDate {
        val jan1 = LocalDate(year, 1, 1)
        val start = weekStart(jan1)
        val daysInYear = 7 - start.daysBetween(jan1)
        return if (daysInYear >= minimalDays) start else start.plusDays(7)
    }
    var year = date.year
    var first = firstWeekStart(year)
    if (date < first) {
        year -= 1
        first = firstWeekStart(year)
    } else {
        val nextFirst = firstWeekStart(year + 1)
        if (date >= nextFirst) first = nextFirst
    }
    return (first.daysBetween(weekStart(date)) / 7) + 1
}

private fun LocalDate.daysBetween(other: LocalDate): Int = (other.toEpochDays() - toEpochDays()).toInt()

/** Epoch milliseconds as a [ZonedDateTime] in [zone]. */
fun zonedFromEpochMillis(epochMillis: Long, zone: TimeZone): ZonedDateTime =
    ZonedDateTime.ofInstant(Instant.fromEpochMilliseconds(epochMillis), zone)
