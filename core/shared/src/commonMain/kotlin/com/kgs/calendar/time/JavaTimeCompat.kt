@file:Suppress("NOTHING_TO_INLINE", "unused")

package com.kgs.calendar.time

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.YearMonth
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.monthsUntil
import kotlinx.datetime.number
import kotlinx.datetime.offsetAt
import kotlinx.datetime.onDay
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearMonth
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/*
 * java.time-style names over kotlinx-datetime, so the code shared with iOS reads like the original
 * Android code. On the JVM kotlinx-datetime is backed by java.time, so results are the same.
 */

/** java.time's ZoneId: a kotlinx [TimeZone]. */
typealias ZoneId = TimeZone

fun TimeZone.Companion.systemDefault(): TimeZone = TimeZone.currentSystemDefault()

/** The zone's id, as java.time's `ZoneId.getId()`. */
val TimeZone.zoneId: String get() = id

object ZoneOffset {
    val UTC: TimeZone get() = TimeZone.UTC
}

// ---------------------------------------------------------------- Instant

fun Instant.Companion.ofEpochMilli(epochMilli: Long): Instant = fromEpochMilliseconds(epochMilli)
fun Instant.Companion.ofEpochSecond(epochSecond: Long): Instant = fromEpochSeconds(epochSecond)
fun Instant.Companion.now(): Instant = Clock.System.now()
inline fun Instant.toEpochMilli(): Long = toEpochMilliseconds()
inline fun Instant.getEpochSecond(): Long = epochSeconds
fun Instant.atZone(zone: TimeZone): ZonedDateTime = ZonedDateTime.ofInstant(this, zone)
fun Instant.isBefore(other: Instant): Boolean = this < other
fun Instant.isAfter(other: Instant): Boolean = this > other
fun Instant.plusMillis(millis: Long): Instant = this + millis.milliseconds
fun Instant.minusMillis(millis: Long): Instant = this - millis.milliseconds
fun Instant.plusSeconds(seconds: Long): Instant = this + seconds.seconds
fun Instant.minusSeconds(seconds: Long): Instant = this - seconds.seconds

fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

// ---------------------------------------------------------------- LocalDate

fun LocalDate.Companion.of(year: Int, month: Int, dayOfMonth: Int): LocalDate = LocalDate(year, month, dayOfMonth)
fun LocalDate.Companion.of(year: Int, month: Month, dayOfMonth: Int): LocalDate = LocalDate(year, month, dayOfMonth)
fun LocalDate.Companion.ofEpochDay(epochDay: Long): LocalDate = fromEpochDays(epochDay)
fun LocalDate.Companion.now(zone: TimeZone = TimeZone.currentSystemDefault()): LocalDate =
    Clock.System.now().toLocalDateTime(zone).date
fun LocalDate.Companion.ofInstant(instant: Instant, zone: TimeZone): LocalDate = instant.toLocalDateTime(zone).date

fun LocalDate.plusDays(days: Long): LocalDate = plus(days, DateTimeUnit.DAY)
fun LocalDate.plusDays(days: Int): LocalDate = plus(days, DateTimeUnit.DAY)
fun LocalDate.minusDays(days: Long): LocalDate = minus(days, DateTimeUnit.DAY)
fun LocalDate.minusDays(days: Int): LocalDate = minus(days, DateTimeUnit.DAY)
fun LocalDate.plusWeeks(weeks: Long): LocalDate = plus(weeks, DateTimeUnit.WEEK)
fun LocalDate.plusWeeks(weeks: Int): LocalDate = plus(weeks, DateTimeUnit.WEEK)
fun LocalDate.minusWeeks(weeks: Long): LocalDate = minus(weeks, DateTimeUnit.WEEK)
fun LocalDate.minusWeeks(weeks: Int): LocalDate = minus(weeks, DateTimeUnit.WEEK)
fun LocalDate.plusMonths(months: Long): LocalDate = plus(months, DateTimeUnit.MONTH)
fun LocalDate.plusMonths(months: Int): LocalDate = plus(months, DateTimeUnit.MONTH)
fun LocalDate.minusMonths(months: Long): LocalDate = minus(months, DateTimeUnit.MONTH)
fun LocalDate.minusMonths(months: Int): LocalDate = minus(months, DateTimeUnit.MONTH)
fun LocalDate.plusYears(years: Long): LocalDate = plus(years, DateTimeUnit.YEAR)
fun LocalDate.plusYears(years: Int): LocalDate = plus(years, DateTimeUnit.YEAR)
fun LocalDate.minusYears(years: Long): LocalDate = minus(years, DateTimeUnit.YEAR)
fun LocalDate.minusYears(years: Int): LocalDate = minus(years, DateTimeUnit.YEAR)

fun LocalDate.isBefore(other: LocalDate): Boolean = this < other
fun LocalDate.isAfter(other: LocalDate): Boolean = this > other
fun LocalDate.isEqual(other: LocalDate): Boolean = this == other

val LocalDate.dayOfMonthValue: Int get() = day
val LocalDate.monthValue: Int get() = month.number
fun LocalDate.lengthOfMonth(): Int = yearMonth.numberOfDays
fun LocalDate.lengthOfYear(): Int = if (isLeapYear(year)) 366 else 365
fun LocalDate.isLeapYear(): Boolean = isLeapYear(year)
fun LocalDate.withDayOfMonth(dayOfMonth: Int): LocalDate = LocalDate(year, month, dayOfMonth)
fun LocalDate.withMonth(month: Int): LocalDate =
    LocalDate(year, month, minOf(day, YearMonth(year, month).numberOfDays))
fun LocalDate.withYear(year: Int): LocalDate =
    LocalDate(year, month, minOf(day, YearMonth(year, month).numberOfDays))
fun LocalDate.withDayOfYear(dayOfYear: Int): LocalDate = LocalDate(year, 1, 1).plusDays(dayOfYear - 1)
fun LocalDate.toEpochDay(): Long = toEpochDays()
fun LocalDate.atStartOfDay(zone: TimeZone): ZonedDateTime = ZonedDateTime.ofInstant(atStartOfDayIn(zone), zone)
fun LocalDate.atStartOfDay(): LocalDateTime = atTime(0, 0)
fun LocalDate.atTime(hour: Int, minute: Int): LocalDateTime = LocalDateTime(year, month, day, hour, minute)
fun LocalDate.with(adjuster: (LocalDate) -> LocalDate): LocalDate = adjuster(this)
/** Midnight at the start of this day in [zone], in epoch milliseconds. */
fun LocalDate.startOfDayMillis(zone: TimeZone): Long = atStartOfDayIn(zone).toEpochMilliseconds()
fun LocalDate.datesUntil(endExclusive: LocalDate): Sequence<LocalDate> =
    generateSequence(this) { it.plusDays(1) }.takeWhile { it < endExclusive }

private fun isLeapYear(year: Int): Boolean = (year % 4 == 0 && year % 100 != 0) || year % 400 == 0

// ---------------------------------------------------------------- YearMonth

fun YearMonth.Companion.of(year: Int, month: Int): YearMonth = YearMonth(year, month)
fun YearMonth.Companion.of(year: Int, month: Month): YearMonth = YearMonth(year, month)
fun YearMonth.Companion.from(date: LocalDate): YearMonth = date.yearMonth
fun YearMonth.Companion.now(zone: TimeZone = TimeZone.currentSystemDefault()): YearMonth = LocalDate.now(zone).yearMonth
fun YearMonth.atDay(dayOfMonth: Int): LocalDate = onDay(dayOfMonth)
fun YearMonth.atEndOfMonth(): LocalDate = lastDay
fun YearMonth.lengthOfMonth(): Int = numberOfDays
val YearMonth.monthValue: Int get() = month.number
fun YearMonth.plusMonths(months: Long): YearMonth = plus(months, DateTimeUnit.MONTH)
fun YearMonth.plusMonths(months: Int): YearMonth = plus(months, DateTimeUnit.MONTH)
fun YearMonth.minusMonths(months: Long): YearMonth = minus(months, DateTimeUnit.MONTH)
fun YearMonth.minusMonths(months: Int): YearMonth = minus(months, DateTimeUnit.MONTH)
fun YearMonth.plusYears(years: Long): YearMonth = plus(years, DateTimeUnit.YEAR)
fun YearMonth.plusYears(years: Int): YearMonth = plus(years, DateTimeUnit.YEAR)
fun YearMonth.minusYears(years: Long): YearMonth = minus(years, DateTimeUnit.YEAR)
fun YearMonth.minusYears(years: Int): YearMonth = minus(years, DateTimeUnit.YEAR)
fun YearMonth.isBefore(other: YearMonth): Boolean = this < other
fun YearMonth.isAfter(other: YearMonth): Boolean = this > other

// ---------------------------------------------------------------- LocalTime

fun LocalTime.Companion.of(hour: Int, minute: Int, second: Int = 0): LocalTime = LocalTime(hour, minute, second)
fun LocalTime.Companion.ofSecondOfDay(secondOfDay: Long): LocalTime = fromSecondOfDay(secondOfDay.toInt())
val LocalTime.Companion.MIDNIGHT: LocalTime get() = LocalTime(0, 0)
val LocalTime.Companion.NOON: LocalTime get() = LocalTime(12, 0)
val LocalTime.Companion.MAX_TIME: LocalTime get() = LocalTime(23, 59, 59, 999_999_999)
fun LocalTime.Companion.now(zone: TimeZone = TimeZone.currentSystemDefault()): LocalTime =
    Clock.System.now().toLocalDateTime(zone).time
fun LocalTime.plusMinutes(minutes: Long): LocalTime = plusSecondsWrapping(minutes * 60)
fun LocalTime.plusMinutes(minutes: Int): LocalTime = plusSecondsWrapping(minutes.toLong() * 60)
fun LocalTime.minusMinutes(minutes: Long): LocalTime = plusSecondsWrapping(-minutes * 60)
fun LocalTime.minusMinutes(minutes: Int): LocalTime = plusSecondsWrapping(-minutes.toLong() * 60)
fun LocalTime.plusHours(hours: Long): LocalTime = plusSecondsWrapping(hours * 3600)
fun LocalTime.plusHours(hours: Int): LocalTime = plusSecondsWrapping(hours.toLong() * 3600)
fun LocalTime.minusHours(hours: Long): LocalTime = plusSecondsWrapping(-hours * 3600)
fun LocalTime.minusHours(hours: Int): LocalTime = plusSecondsWrapping(-hours.toLong() * 3600)
fun LocalTime.withHour(hour: Int): LocalTime = LocalTime(hour, minute, second, nanosecond)
fun LocalTime.withMinute(minute: Int): LocalTime = LocalTime(hour, minute, second, nanosecond)
fun LocalTime.withSecond(second: Int): LocalTime = LocalTime(hour, minute, second, nanosecond)
fun LocalTime.withNano(nano: Int): LocalTime = LocalTime(hour, minute, second, nano)
fun LocalTime.isBefore(other: LocalTime): Boolean = this < other
fun LocalTime.isAfter(other: LocalTime): Boolean = this > other
fun LocalTime.atDate(date: LocalDate): LocalDateTime = date.atTime(this)
fun LocalTime.truncatedToMinutes(): LocalTime = LocalTime(hour, minute)

private fun LocalTime.plusSecondsWrapping(seconds: Long): LocalTime {
    val day = 86_400L
    val total = ((toSecondOfDay() + seconds) % day + day) % day
    return LocalTime.fromSecondOfDay(total.toInt()).let { if (nanosecond == 0) it else LocalTime(it.hour, it.minute, it.second, nanosecond) }
}

// ---------------------------------------------------------------- LocalDateTime

fun LocalDateTime.Companion.of(date: LocalDate, time: LocalTime): LocalDateTime = LocalDateTime(date, time)
fun LocalDateTime.Companion.of(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int = 0): LocalDateTime =
    LocalDateTime(year, month, day, hour, minute, second)
fun LocalDateTime.Companion.ofInstant(instant: Instant, zone: TimeZone): LocalDateTime = instant.toLocalDateTime(zone)
fun LocalDateTime.Companion.now(zone: TimeZone = TimeZone.currentSystemDefault()): LocalDateTime =
    Clock.System.now().toLocalDateTime(zone)
fun LocalDateTime.toLocalDate(): LocalDate = date
fun LocalDateTime.toLocalTime(): LocalTime = time
fun LocalDateTime.atZone(zone: TimeZone): ZonedDateTime = ZonedDateTime.of(this, zone)
fun LocalDateTime.isBefore(other: LocalDateTime): Boolean = this < other
fun LocalDateTime.isAfter(other: LocalDateTime): Boolean = this > other
fun LocalDateTime.plusDays(days: Long): LocalDateTime = LocalDateTime(date.plusDays(days), time)
fun LocalDateTime.plusDays(days: Int): LocalDateTime = LocalDateTime(date.plusDays(days), time)
fun LocalDateTime.minusDays(days: Long): LocalDateTime = LocalDateTime(date.minusDays(days), time)
fun LocalDateTime.minusDays(days: Int): LocalDateTime = LocalDateTime(date.minusDays(days), time)
fun LocalDateTime.plusMinutes(minutes: Long): LocalDateTime = plusSecondsLocal(minutes * 60)
fun LocalDateTime.plusMinutes(minutes: Int): LocalDateTime = plusSecondsLocal(minutes.toLong() * 60)
fun LocalDateTime.minusMinutes(minutes: Long): LocalDateTime = plusSecondsLocal(-minutes * 60)
fun LocalDateTime.minusMinutes(minutes: Int): LocalDateTime = plusSecondsLocal(-minutes.toLong() * 60)
fun LocalDateTime.plusHours(hours: Long): LocalDateTime = plusSecondsLocal(hours * 3600)
fun LocalDateTime.plusHours(hours: Int): LocalDateTime = plusSecondsLocal(hours.toLong() * 3600)
fun LocalDateTime.plusSeconds(seconds: Long): LocalDateTime = plusSecondsLocal(seconds)
fun LocalDateTime.withHour(hour: Int): LocalDateTime = LocalDateTime(date, time.withHour(hour))
fun LocalDateTime.withMinute(minute: Int): LocalDateTime = LocalDateTime(date, time.withMinute(minute))

/** Local arithmetic, as java.time's LocalDateTime: through UTC so no zone rules apply. */
private fun LocalDateTime.plusSecondsLocal(seconds: Long): LocalDateTime =
    (toInstant(TimeZone.UTC) + seconds.seconds).toLocalDateTime(TimeZone.UTC)

// ---------------------------------------------------------------- DayOfWeek / Month

/** ISO number, Monday = 1 … Sunday = 7 (java.time's `getValue()`). */
val DayOfWeek.value: Int get() = isoDayNumber
fun DayOfWeek.plus(days: Long): DayOfWeek = DayOfWeek((((isoDayNumber - 1 + days) % 7 + 7) % 7 + 1).toInt())
fun DayOfWeek.plus(days: Int): DayOfWeek = plus(days.toLong())
fun DayOfWeek.minus(days: Long): DayOfWeek = plus(-days)
fun DayOfWeek.minus(days: Int): DayOfWeek = plus(-days.toLong())
fun dayOfWeekOf(isoDayNumber: Int): DayOfWeek = DayOfWeek(isoDayNumber)

val Month.value: Int get() = number
fun monthOf(number: Int): Month = Month(number)
fun Month.plus(months: Long): Month = Month((((number - 1 + months) % 12 + 12) % 12 + 1).toInt())
fun Month.minus(months: Long): Month = plus(-months)

object TemporalAdjusters {
    fun previousOrSame(dayOfWeek: DayOfWeek): (LocalDate) -> LocalDate = { date ->
        date.minusDays(((date.dayOfWeek.isoDayNumber - dayOfWeek.isoDayNumber) % 7 + 7) % 7)
    }
    fun nextOrSame(dayOfWeek: DayOfWeek): (LocalDate) -> LocalDate = { date ->
        date.plusDays(((dayOfWeek.isoDayNumber - date.dayOfWeek.isoDayNumber) % 7 + 7) % 7)
    }
    fun previous(dayOfWeek: DayOfWeek): (LocalDate) -> LocalDate = { date ->
        previousOrSame(dayOfWeek)(date.minusDays(1))
    }
    fun next(dayOfWeek: DayOfWeek): (LocalDate) -> LocalDate = { date ->
        nextOrSame(dayOfWeek)(date.plusDays(1))
    }
    fun firstDayOfMonth(): (LocalDate) -> LocalDate = { it.withDayOfMonth(1) }
    fun lastDayOfMonth(): (LocalDate) -> LocalDate = { it.yearMonth.lastDay }
}

/** The subset of java.time's ChronoUnit used for `between`. */
enum class ChronoUnit {
    MILLIS, SECONDS, MINUTES, HOURS, DAYS, WEEKS, MONTHS, YEARS;

    fun between(start: LocalDate, endExclusive: LocalDate): Long = when (this) {
        DAYS -> start.daysUntil(endExclusive).toLong()
        WEEKS -> (start.daysUntil(endExclusive) / 7).toLong()
        MONTHS -> start.monthsUntil(endExclusive).toLong()
        YEARS -> (start.monthsUntil(endExclusive) / 12).toLong()
        else -> throw IllegalArgumentException("Unsupported unit for dates: $this")
    }

    fun between(start: YearMonth, endExclusive: YearMonth): Long = when (this) {
        MONTHS -> start.monthsUntil(endExclusive).toLong()
        YEARS -> (start.monthsUntil(endExclusive) / 12).toLong()
        else -> throw IllegalArgumentException("Unsupported unit for months: $this")
    }

    fun between(start: Instant, endExclusive: Instant): Long = between(endExclusive - start)

    fun between(start: LocalTime, endExclusive: LocalTime): Long =
        between(((endExclusive.toNanosecondOfDay() - start.toNanosecondOfDay()) / 1_000_000).milliseconds)

    fun between(start: LocalDateTime, endExclusive: LocalDateTime): Long = when (this) {
        DAYS, WEEKS, MONTHS, YEARS -> {
            // As java.time: whole units, so a later time of day on the end date doesn't add one.
            var end = endExclusive.date
            if (endExclusive.date > start.date && endExclusive.time < start.time) end = end.minusDays(1)
            if (endExclusive.date < start.date && endExclusive.time > start.time) end = end.plusDays(1)
            between(start.date, end)
        }
        else -> between(start.toInstant(TimeZone.UTC), endExclusive.toInstant(TimeZone.UTC))
    }

    fun between(start: ZonedDateTime, endExclusive: ZonedDateTime): Long = when (this) {
        DAYS, WEEKS, MONTHS, YEARS -> between(start.toLocalDateTime(), endExclusive.withZoneSameInstant(start.zone).toLocalDateTime())
        else -> between(start.toInstant(), endExclusive.toInstant())
    }

    private fun between(duration: Duration): Long = when (this) {
        MILLIS -> duration.inWholeMilliseconds
        SECONDS -> duration.inWholeSeconds
        MINUTES -> duration.inWholeMinutes
        HOURS -> duration.inWholeHours
        DAYS -> duration.inWholeDays
        else -> throw IllegalArgumentException("Unsupported unit for instants: $this")
    }
}

// ---------------------------------------------------------------- Duration

fun Duration.Companion.ofMillis(millis: Long): Duration = millis.milliseconds
fun Duration.Companion.ofSeconds(seconds: Long): Duration = seconds.seconds
fun Duration.Companion.ofMinutes(minutes: Long): Duration = minutes.minutes
fun Duration.Companion.ofHours(hours: Long): Duration = (hours * 60).minutes
fun Duration.Companion.ofDays(days: Long): Duration = (days * 24 * 60).minutes
fun Duration.toMillis(): Long = inWholeMilliseconds
fun Duration.toMinutes(): Long = inWholeMinutes
fun Duration.toHours(): Long = inWholeHours
fun Duration.toDays(): Long = inWholeDays
fun Duration.getSeconds(): Long = inWholeSeconds

// ---------------------------------------------------------------- ZonedDateTime

/** An instant seen in a zone, as java.time's ZonedDateTime (the subset this app uses). */
class ZonedDateTime private constructor(
    private val instant: Instant,
    val zone: TimeZone,
    private val local: LocalDateTime,
) : Comparable<ZonedDateTime> {
    val year: Int get() = local.year
    val month: Month get() = local.month
    val monthValue: Int get() = local.month.number
    val dayOfMonth: Int get() = local.day
    val dayOfWeek: DayOfWeek get() = local.dayOfWeek
    val dayOfYear: Int get() = local.dayOfYear
    val hour: Int get() = local.hour
    val minute: Int get() = local.minute
    val second: Int get() = local.second
    val nano: Int get() = local.nanosecond
    val offset: UtcOffset get() = zone.offsetAt(instant)

    fun toInstant(): Instant = instant
    fun toEpochSecond(): Long = instant.epochSeconds
    fun toLocalDate(): LocalDate = local.date
    fun toLocalTime(): LocalTime = local.time
    fun toLocalDateTime(): LocalDateTime = local

    fun withZoneSameInstant(zone: TimeZone): ZonedDateTime = ofInstant(instant, zone)
    fun withZoneSameLocal(zone: TimeZone): ZonedDateTime = of(local, zone)

    // Date-based amounts keep the local time (re-resolved in the zone); time-based ones are exact.
    fun plusDays(days: Long): ZonedDateTime = of(local.plusDays(days), zone)
    fun plusDays(days: Int): ZonedDateTime = plusDays(days.toLong())
    fun minusDays(days: Long): ZonedDateTime = plusDays(-days)
    fun minusDays(days: Int): ZonedDateTime = plusDays(-days.toLong())
    fun plusWeeks(weeks: Long): ZonedDateTime = plusDays(weeks * 7)
    fun plusMonths(months: Long): ZonedDateTime = of(LocalDateTime(local.date.plusMonths(months), local.time), zone)
    fun plusYears(years: Long): ZonedDateTime = of(LocalDateTime(local.date.plusYears(years), local.time), zone)
    fun plusHours(hours: Long): ZonedDateTime = ofInstant(instant + (hours * 60).minutes, zone)
    fun plusMinutes(minutes: Long): ZonedDateTime = ofInstant(instant + minutes.minutes, zone)
    fun minusMinutes(minutes: Long): ZonedDateTime = plusMinutes(-minutes)
    fun plusSeconds(seconds: Long): ZonedDateTime = ofInstant(instant + seconds.seconds, zone)
    fun withHour(hour: Int): ZonedDateTime = of(LocalDateTime(local.date, local.time.withHour(hour)), zone)
    fun withMinute(minute: Int): ZonedDateTime = of(LocalDateTime(local.date, local.time.withMinute(minute)), zone)
    fun withSecond(second: Int): ZonedDateTime = of(LocalDateTime(local.date, local.time.withSecond(second)), zone)
    fun withNano(nano: Int): ZonedDateTime = of(LocalDateTime(local.date, local.time.withNano(nano)), zone)
    fun with(time: LocalTime): ZonedDateTime = of(LocalDateTime(local.date, time), zone)
    fun with(date: LocalDate): ZonedDateTime = of(LocalDateTime(date, local.time), zone)
    fun withDayOfMonth(day: Int): ZonedDateTime = of(LocalDateTime(local.date.withDayOfMonth(day), local.time), zone)
    fun toLocalDateAtStartOfDay(): ZonedDateTime = local.date.atStartOfDay(zone)

    /** In a repeated hour, the first of the two instants with this local time (java.time's default). */
    fun withEarlierOffsetAtOverlap(): ZonedDateTime = overlapCandidates().minOrNull()?.let { ofInstant(it, zone) } ?: this

    /** In a repeated hour, the second of the two instants with this local time. */
    fun withLaterOffsetAtOverlap(): ZonedDateTime = overlapCandidates().maxOrNull()?.let { ofInstant(it, zone) } ?: this

    private fun overlapCandidates(): List<Instant> {
        val window = (3 * 60).minutes
        return listOf(zone.offsetAt(instant - window), zone.offsetAt(instant + window)).distinct()
            .map { local.toInstant(it) }
            .filter { it.toLocalDateTime(zone) == local }
    }
    fun truncatedToDays(): ZonedDateTime = local.date.atStartOfDay(zone)
    fun isBefore(other: ZonedDateTime): Boolean = instant < other.instant
    fun isAfter(other: ZonedDateTime): Boolean = instant > other.instant

    override fun compareTo(other: ZonedDateTime): Int = instant.compareTo(other.instant)
    override fun equals(other: Any?): Boolean =
        other is ZonedDateTime && instant == other.instant && zone == other.zone
    override fun hashCode(): Int = instant.hashCode() * 31 + zone.hashCode()
    override fun toString(): String = "$local${offset}[${zone.id}]"

    companion object {
        fun ofInstant(instant: Instant, zone: TimeZone): ZonedDateTime =
            ZonedDateTime(instant, zone, instant.toLocalDateTime(zone))

        /** As java.time: a time in a gap moves forward by the gap; in an overlap the earlier offset wins. */
        fun of(local: LocalDateTime, zone: TimeZone): ZonedDateTime = ofInstant(local.toInstant(zone), zone)
        fun of(date: LocalDate, time: LocalTime, zone: TimeZone): ZonedDateTime = of(LocalDateTime(date, time), zone)
        fun of(year: Int, month: Int, dayOfMonth: Int, hour: Int, minute: Int, second: Int, nanoOfSecond: Int, zone: TimeZone): ZonedDateTime =
            of(LocalDateTime(year, month, dayOfMonth, hour, minute, second, nanoOfSecond), zone)
        fun now(zone: TimeZone = TimeZone.currentSystemDefault()): ZonedDateTime = ofInstant(Clock.System.now(), zone)
    }
}

fun Instant.toZoned(zone: TimeZone): ZonedDateTime = ZonedDateTime.ofInstant(this, zone)

/** Calendar date of these epoch milliseconds in [zone]. */
fun Long.epochMillisToLocalDate(zone: TimeZone): LocalDate = Instant.fromEpochMilliseconds(this).toLocalDateTime(zone).date
fun Long.epochMillisToLocalDateTime(zone: TimeZone): LocalDateTime = Instant.fromEpochMilliseconds(this).toLocalDateTime(zone)
fun LocalDateTime.toEpochMillis(zone: TimeZone): Long = toInstant(zone).toEpochMilliseconds()

/** RFC 1123 date-time (`Tue, 3 Jun 2008 11:05:30 GMT` or with an offset like `+0200`), in epoch millis; null if unreadable. */
fun parseRfc1123Millis(text: String): Long? {
    val match = Regex("""^(?:[A-Za-z]{3},\s*)?(\d{1,2})\s+([A-Za-z]{3})\s+(\d{4})\s+(\d{2}):(\d{2})(?::(\d{2}))?\s+(GMT|UT|Z|[+-]\d{4})$""")
        .find(text.trim()) ?: return null
    val (day, monthName, year, hour, minute, second, zone) = match.destructured
    val month = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
        .indexOf(monthName.lowercase()).takeIf { it >= 0 } ?: return null
    val local = runCatching {
        LocalDateTime(year.toInt(), month + 1, day.toInt(), hour.toInt(), minute.toInt(), second.ifEmpty { "0" }.toInt())
    }.getOrNull() ?: return null
    val offsetSeconds = if (zone.startsWith("+") || zone.startsWith("-")) {
        val sign = if (zone[0] == '-') -1 else 1
        sign * (zone.substring(1, 3).toInt() * 3600 + zone.substring(3, 5).toInt() * 60)
    } else 0
    return local.toInstant(TimeZone.UTC).toEpochMilliseconds() - offsetSeconds * 1000L
}

private val monotonicOrigin = kotlin.time.TimeSource.Monotonic.markNow()

/** A monotonic clock in nanoseconds, for measuring intervals (as `System.nanoTime()`). */
fun monotonicNanos(): Long = monotonicOrigin.elapsedNow().inWholeNanoseconds
