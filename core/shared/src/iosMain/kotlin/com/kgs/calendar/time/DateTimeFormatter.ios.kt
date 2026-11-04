package com.kgs.calendar.time

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarIdentifierGregorian
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterFullStyle
import platform.Foundation.NSDateFormatterLongStyle
import platform.Foundation.NSDateFormatterMediumStyle
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone
import platform.Foundation.currentLocale
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.timeZoneWithName
import platform.Foundation.localeIdentifier
import com.kgs.calendar.platform.LockedMap
import kotlin.time.Instant

actual class AppLocale(private val tag: String) {
    actual fun toLanguageTag(): String = tag
    actual fun getLanguage(): String = tag.substringBefore('-').substringBefore('_').lowercase()
    internal fun toNSLocale(): NSLocale = NSLocale(localeIdentifier = tag.ifEmpty { "en_US_POSIX" })
    override fun equals(other: Any?): Boolean = other is AppLocale && other.tag == tag
    override fun hashCode(): Int = tag.hashCode()
    override fun toString(): String = tag
}

actual fun appLocaleOf(languageTag: String): AppLocale = AppLocale(languageTag)
actual fun defaultAppLocale(): AppLocale =
    IosAppLanguage.languageTag?.let(::AppLocale) ?: AppLocale(NSLocale.currentLocale.localeIdentifier.replace('_', '-'))

/**
 * The language chosen in the app's settings, or null to follow the system. Android makes it the
 * process default (`Locale.setDefault`); here [defaultAppLocale] returns it, so notifications,
 * messages and formats outside the UI use it too.
 */
object IosAppLanguage {
    var languageTag: String? = null
}
actual fun rootAppLocale(): AppLocale = AppLocale("")

private fun FormatStyle.toNs() = when (this) {
    FormatStyle.FULL -> NSDateFormatterFullStyle
    FormatStyle.LONG -> NSDateFormatterLongStyle
    FormatStyle.MEDIUM -> NSDateFormatterMediumStyle
    FormatStyle.SHORT -> NSDateFormatterShortStyle
}

/** Creating an NSDateFormatter is slow and it is thread-safe, so each setup is built once. */
private val formatters = LockedMap<String, NSDateFormatter>()
private val literalText = Regex("'[^']*'")
private val namedFields = Regex("""E|MMM|LLL|a|G|z|Z{4}|v""")

private fun DateTimeFormatter.toNs(hasTime: Boolean, hasDate: Boolean, zone: TimeZone?): NSDateFormatter {
    val language = (locale ?: defaultAppLocale()).toLanguageTag()
    return formatters.getOrPut("$pattern|$dateStyle|$timeStyle|$language|${zone?.id}|$hasTime|$hasDate") {
        newFormatter(hasTime, hasDate, zone)
    }
}

private fun DateTimeFormatter.newFormatter(hasTime: Boolean, hasDate: Boolean, zone: TimeZone?): NSDateFormatter {
    val f = NSDateFormatter()
    // As java.time: Gregorian dates and ASCII digits whatever the user's calendar and numbering. A
    // pattern without names (weekday, month text, AM/PM, era) gets the POSIX locale.
    val hasNames = pattern != null && namedFields.containsMatchIn(pattern.replace(literalText, ""))
    f.locale = when {
        locale != null -> locale.toNSLocale()
        pattern != null && !hasNames -> NSLocale(localeIdentifier = "en_US_POSIX")
        else -> defaultAppLocale().toNSLocale()
    }
    f.calendar = NSCalendar(calendarIdentifier = NSCalendarIdentifierGregorian)
    f.timeZone = NSTimeZone.timeZoneWithName(zone?.id ?: "UTC") ?: NSTimeZone.timeZoneWithName("UTC")!!
    if (pattern != null) {
        // java.time's 'u' is the year; ICU's 'u' is the extended year, which reads the same.
        f.dateFormat = pattern
    } else {
        f.dateStyle = if (hasDate && (dateStyle != null || timeStyle == null)) (dateStyle ?: FormatStyle.MEDIUM).toNs() else NSDateFormatterNoStyle
        f.timeStyle = if (hasTime) timeStyle?.toNs() ?: NSDateFormatterNoStyle else NSDateFormatterNoStyle
    }
    return f
}

internal actual fun platformFormat(
    formatter: DateTimeFormatter,
    dateTime: LocalDateTime,
    zone: TimeZone?,
    hasTime: Boolean,
    hasDate: Boolean,
): String {
    val instant = dateTime.toInstant(zone ?: TimeZone.UTC)
    val date = NSDate.dateWithTimeIntervalSince1970(instant.toEpochMilliseconds() / 1000.0)
    return formatter.toNs(hasTime, hasDate, zone).stringFromDate(date)
}

internal actual fun platformParse(formatter: DateTimeFormatter, text: String, hasTime: Boolean, timeOnly: Boolean): LocalDateTime {
    val f = formatter.toNs(hasTime, !timeOnly, null)
    val input = if (timeOnly) text.removePrefix("2000-01-01 ") else text
    val parsed = f.dateFromString(input) ?: throw IllegalArgumentException("Text '$input' could not be parsed")
    val local = Instant.fromEpochMilliseconds((parsed.timeIntervalSince1970 * 1000).toLong()).toLocalDateTime(TimeZone.UTC)
    return if (timeOnly) LocalDateTime(kotlinx.datetime.LocalDate(2000, 1, 1), local.time) else local
}

private val symbolFormatters = LockedMap<String, NSDateFormatter>()
private val nameLists = LockedMap<String, List<String>>()

private fun symbolFormatter(locale: AppLocale) = symbolFormatters.getOrPut(locale.toLanguageTag()) {
    NSDateFormatter().apply {
        this.locale = locale.toNSLocale()
        calendar = NSCalendar(calendarIdentifier = NSCalendarIdentifierGregorian)
    }
}

actual fun DayOfWeek.getDisplayName(style: TextStyle, locale: AppLocale): String {
    val symbols = nameLists.getOrPut("day|$style|${locale.toLanguageTag()}") {
        val f = symbolFormatter(locale)
        @Suppress("UNCHECKED_CAST")
        when (style) {
            TextStyle.FULL -> f.weekdaySymbols
            TextStyle.FULL_STANDALONE -> f.standaloneWeekdaySymbols
            TextStyle.SHORT -> f.shortWeekdaySymbols
            TextStyle.SHORT_STANDALONE -> f.shortStandaloneWeekdaySymbols
            TextStyle.NARROW -> f.veryShortWeekdaySymbols
            TextStyle.NARROW_STANDALONE -> f.veryShortStandaloneWeekdaySymbols
        } as List<String>
    }
    // Foundation lists Sunday first.
    return symbols[isoDayNumber % 7]
}

actual fun Month.getDisplayName(style: TextStyle, locale: AppLocale): String {
    val symbols = nameLists.getOrPut("month|$style|${locale.toLanguageTag()}") {
        val f = symbolFormatter(locale)
        @Suppress("UNCHECKED_CAST")
        when (style) {
            TextStyle.FULL -> f.monthSymbols
            TextStyle.FULL_STANDALONE -> f.standaloneMonthSymbols
            TextStyle.SHORT -> f.shortMonthSymbols
            TextStyle.SHORT_STANDALONE -> f.shortStandaloneMonthSymbols
            TextStyle.NARROW -> f.veryShortMonthSymbols
            TextStyle.NARROW_STANDALONE -> f.veryShortStandaloneMonthSymbols
        } as List<String>
    }
    return symbols[number - 1]
}

private val firstDays = LockedMap<String, DayOfWeek>()

actual fun firstDayOfWeekFor(locale: AppLocale): DayOfWeek = firstDays.getOrPut(locale.toLanguageTag()) {
    val calendar = NSCalendar(calendarIdentifier = NSCalendarIdentifierGregorian)
    calendar.locale = locale.toNSLocale()
    // NSCalendar: 1 = Sunday … 7 = Saturday.
    val first = calendar.firstWeekday.toInt()
    DayOfWeek(if (first == 1) 7 else first - 1)
}
