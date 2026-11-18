package com.kgs.calendar.data.recurrence

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearMonth
import kotlin.time.Instant

/**
 * A rule needs more work than a calendar view can spend. The series stays stored unchanged; callers
 * show its first occurrence instead of a partial list.
 */
internal class RecurrenceBudgetExceededException : IllegalStateException("This recurrence rule is too expensive to expand.")

/**
 * A complete RFC 5545 RRULE expander (all BYxxx parts, BYSETPOS, WKST, COUNT, UNTIL) on local
 * date-times, following the well-known dateutil algorithm. Used where ical4j isn't available (iOS),
 * and tested against ical4j on the JVM.
 */
internal class RRule private constructor(
    val freq: Freq,
    val interval: Int,
    val count: Int?,
    /** UNTIL as given: a date, a floating date-time, or (utc = true) a UTC date-time. */
    val untilDate: LocalDate?,
    val untilDateTime: LocalDateTime?,
    val untilIsUtc: Boolean,
    val bySecond: List<Int>,
    val byMinute: List<Int>,
    val byHour: List<Int>,
    val byDay: List<Pair<Int?, DayOfWeek>>,
    val byMonthDay: List<Int>,
    val byYearDay: List<Int>,
    val byWeekNo: List<Int>,
    val byMonth: List<Int>,
    val bySetPos: List<Int>,
    val weekStart: DayOfWeek,
) {
    enum class Freq { SECONDLY, MINUTELY, HOURLY, DAILY, WEEKLY, MONTHLY, YEARLY }

    companion object {
        /** Far beyond any calendar window a person looks at; hostile rules run into these. */
        const val DEFAULT_MAX_RESULTS = 20_000
        const val DEFAULT_WORK_BUDGET = 3_000_000L

        fun parse(rule: String): RRule {
            val parts = rule.removePrefix("RRULE:").split(';')
                .filter { it.contains('=') }
                .associate { it.substringBefore('=').trim().uppercase() to it.substringAfter('=').trim() }
            val freq = Freq.valueOf(requireNotNull(parts["FREQ"]) { "FREQ is required" }.uppercase())
            fun ints(key: String) = parts[key]?.split(',')?.mapNotNull { it.trim().removePrefix("+").toIntOrNull() }.orEmpty()
            val until = parts["UNTIL"]
            var untilDate: LocalDate? = null
            var untilDateTime: LocalDateTime? = null
            var untilUtc = false
            if (until != null) {
                val digits = until.trim()
                if (digits.length >= 15 && digits[8] == 'T') {
                    untilDateTime = LocalDateTime(
                        digits.substring(0, 4).toInt(), digits.substring(4, 6).toInt(), digits.substring(6, 8).toInt(),
                        digits.substring(9, 11).toInt(), digits.substring(11, 13).toInt(), digits.substring(13, 15).toInt(),
                    )
                    untilUtc = digits.endsWith("Z", ignoreCase = true)
                } else if (digits.length >= 8) {
                    untilDate = LocalDate(digits.substring(0, 4).toInt(), digits.substring(4, 6).toInt(), digits.substring(6, 8).toInt())
                }
            }
            val byDay = parts["BYDAY"]?.split(',')?.mapNotNull { token ->
                val t = token.trim().uppercase()
                if (t.length < 2) return@mapNotNull null
                val day = dayOf(t.takeLast(2)) ?: return@mapNotNull null
                val n = t.dropLast(2).removePrefix("+").takeIf { it.isNotEmpty() }?.toIntOrNull()
                n to day
            }.orEmpty()
            return RRule(
                freq = freq,
                interval = parts["INTERVAL"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                count = parts["COUNT"]?.toIntOrNull(),
                untilDate = untilDate,
                untilDateTime = untilDateTime,
                untilIsUtc = untilUtc,
                bySecond = ints("BYSECOND"),
                byMinute = ints("BYMINUTE"),
                byHour = ints("BYHOUR"),
                byDay = byDay,
                byMonthDay = ints("BYMONTHDAY"),
                byYearDay = ints("BYYEARDAY"),
                byWeekNo = ints("BYWEEKNO"),
                byMonth = ints("BYMONTH"),
                bySetPos = ints("BYSETPOS"),
                weekStart = parts["WKST"]?.let { dayOf(it.uppercase()) } ?: DayOfWeek.MONDAY,
            )
        }

        private fun dayOf(code: String): DayOfWeek? = when (code) {
            "MO" -> DayOfWeek.MONDAY
            "TU" -> DayOfWeek.TUESDAY
            "WE" -> DayOfWeek.WEDNESDAY
            "TH" -> DayOfWeek.THURSDAY
            "FR" -> DayOfWeek.FRIDAY
            "SA" -> DayOfWeek.SATURDAY
            "SU" -> DayOfWeek.SUNDAY
            else -> null
        }
    }

    /**
     * Occurrences (local date-times) from [start] on, in order, while they are before
     * [endExclusive]. [zone] resolves a UTC UNTIL; null treats UNTIL as local. Without a COUNT,
     * nothing before [searchFrom] matters and the periods up to it are skipped. Rules that need
     * more work than [workBudget] end in [RecurrenceBudgetExceededException], as does a window
     * with more than [maxResults] occurrences: a part of a series would look like the whole of it.
     */
    fun occurrences(
        start: LocalDateTime,
        endExclusive: LocalDateTime,
        zone: TimeZone?,
        maxResults: Int = DEFAULT_MAX_RESULTS,
        searchFrom: LocalDateTime? = null,
        workBudget: Long = DEFAULT_WORK_BUDGET,
    ): Sequence<LocalDateTime> = sequence {
        val untilLocal: LocalDateTime? = when {
            untilDateTime != null && untilIsUtc && zone != null ->
                untilDateTime.toInstant(TimeZone.UTC).toLocalDateTime(zone)
            untilDateTime != null -> untilDateTime
            // A date UNTIL means the start of that day in the device's zone, as ical4j reads it
            // (inclusive for all-day items, which recur on dates).
            untilDate != null && zone != null ->
                LocalDateTime(untilDate, LocalTime(0, 0)).toInstant(TimeZone.currentSystemDefault()).toLocalDateTime(zone)
            untilDate != null -> LocalDateTime(untilDate, LocalTime(0, 0))
            else -> null
        }
        val defaults = Defaults(start)
        val budget = WorkBudget(workBudget)
        var emitted = 0
        var results = 0
        var periodStart = periodAnchor(start)
        if (count == null && searchFrom != null) periodStart = fastForwarded(periodStart, searchFrom)
        var emptyPeriods = 0
        while (true) {
            budget.spend(1)
            val candidates = candidatesFor(periodStart, defaults, budget)
            var any = false
            for (candidate in candidates) {
                if (candidate < start) continue
                if (untilLocal != null && candidate > untilLocal) return@sequence
                if (count != null && emitted >= count) return@sequence
                if (candidate >= endExclusive) return@sequence
                any = true
                emitted++
                if (searchFrom == null || candidate >= searchFrom) {
                    if (++results > maxResults) throw RecurrenceBudgetExceededException()
                    yield(candidate)
                }
            }
            // Past the window without any match for a long time: the rule can't match again here.
            emptyPeriods = if (any) 0 else emptyPeriods + 1
            if (emptyPeriods > 2_000 && periodStart.date > endExclusive.date) return@sequence
            if (periodStart.date.year > 9_000) return@sequence
            periodStart = advance(periodStart)
            if (periodStart >= endExclusive) return@sequence
            if (untilLocal != null && periodBeginsAfter(periodStart, untilLocal)) return@sequence
        }
    }

    private class WorkBudget(private var left: Long) {
        fun spend(units: Long) {
            left -= units
            if (left < 0) throw RecurrenceBudgetExceededException()
        }
    }

    /** The last period before [target] in the rule's INTERVAL grid. One period of margin stays for DST. */
    private fun fastForwarded(period: LocalDateTime, target: LocalDateTime): LocalDateTime {
        if (target <= period) return period
        val gapSeconds = target.toInstant(TimeZone.UTC).epochSeconds - period.toInstant(TimeZone.UTC).epochSeconds
        val stepSeconds = when (freq) {
            Freq.SECONDLY -> interval.toLong()
            Freq.MINUTELY -> interval * 60L
            Freq.HOURLY -> interval * 3_600L
            Freq.DAILY -> interval * 86_400L
            Freq.WEEKLY -> interval * 604_800L
            Freq.MONTHLY, Freq.YEARLY -> return period
        }
        val steps = gapSeconds / stepSeconds - 1
        return if (steps <= 0) period else plusSeconds(period, steps * stepSeconds)
    }

    private inner class Defaults(start: LocalDateTime) {
        val hasDayRule = byWeekNo.isNotEmpty() || byYearDay.isNotEmpty() || byMonthDay.isNotEmpty() || byDay.isNotEmpty()
        val months: List<Int> = if (byMonth.isNotEmpty()) byMonth else if (freq == Freq.YEARLY && !hasDayRule) listOf(start.month.number) else emptyList()
        val monthDays: List<Int> = if (byMonthDay.isNotEmpty()) byMonthDay
        else if (!hasDayRule && (freq == Freq.YEARLY || freq == Freq.MONTHLY)) listOf(start.day) else emptyList()
        val weekDays: List<Pair<Int?, DayOfWeek>> = if (byDay.isNotEmpty()) byDay
        else if (!hasDayRule && freq == Freq.WEEKLY) listOf(null to start.dayOfWeek) else emptyList()
        val hours = if (byHour.isNotEmpty()) byHour.sorted() else listOf(start.hour)
        val minutes = if (byMinute.isNotEmpty()) byMinute.sorted() else listOf(start.minute)
        val seconds = if (bySecond.isNotEmpty()) bySecond.sorted() else listOf(start.second)
    }

    private fun periodAnchor(start: LocalDateTime): LocalDateTime = when (freq) {
        Freq.YEARLY -> LocalDateTime(start.year, 1, 1, 0, 0)
        Freq.MONTHLY -> LocalDateTime(start.year, start.month.number, 1, 0, 0)
        Freq.WEEKLY -> LocalDateTime(weekStartOf(start.date), LocalTime(0, 0))
        Freq.DAILY -> LocalDateTime(start.date, LocalTime(0, 0))
        Freq.HOURLY -> LocalDateTime(start.date, LocalTime(start.hour, 0))
        Freq.MINUTELY -> LocalDateTime(start.date, LocalTime(start.hour, start.minute))
        Freq.SECONDLY -> LocalDateTime(start.date, LocalTime(start.hour, start.minute, start.second))
    }

    private fun advance(period: LocalDateTime): LocalDateTime = when (freq) {
        Freq.YEARLY -> LocalDateTime(period.date.plus(interval, DateTimeUnit.YEAR), period.time)
        Freq.MONTHLY -> LocalDateTime(period.date.plus(interval, DateTimeUnit.MONTH), period.time)
        Freq.WEEKLY -> LocalDateTime(period.date.plus(interval * 7, DateTimeUnit.DAY), period.time)
        Freq.DAILY -> LocalDateTime(period.date.plus(interval, DateTimeUnit.DAY), period.time)
        Freq.HOURLY -> plusSeconds(period, interval * 3600L)
        Freq.MINUTELY -> plusSeconds(period, interval * 60L)
        Freq.SECONDLY -> plusSeconds(period, interval.toLong())
    }

    private fun plusSeconds(value: LocalDateTime, seconds: Long): LocalDateTime =
        Instant.fromEpochSeconds(value.toInstant(TimeZone.UTC).epochSeconds + seconds).toLocalDateTime(TimeZone.UTC)

    private fun periodBeginsAfter(period: LocalDateTime, limit: LocalDateTime): Boolean = period > limit

    private fun weekStartOf(date: LocalDate): LocalDate {
        val back = ((date.dayOfWeek.isoDayNumber - weekStart.isoDayNumber) % 7 + 7) % 7
        return date.plus(-back, DateTimeUnit.DAY)
    }

    /** The occurrences of one period, sorted, after every BYxxx rule and BYSETPOS. */
    private fun candidatesFor(period: LocalDateTime, d: Defaults, budget: WorkBudget): List<LocalDateTime> {
        budget.spend(if (freq == Freq.YEARLY) 366 else if (freq == Freq.MONTHLY) 31 else if (freq == Freq.WEEKLY) 7 else 1)
        val days: List<LocalDate> = when (freq) {
            Freq.YEARLY -> datesBetween(LocalDate(period.year, 1, 1), LocalDate(period.year + 1, 1, 1))
            Freq.MONTHLY -> period.date.let { datesBetween(it, it.plus(1, DateTimeUnit.MONTH)) }
            Freq.WEEKLY -> datesBetween(period.date, period.date.plus(7, DateTimeUnit.DAY))
            else -> listOf(period.date)
        }.filter { dayMatches(it, d) }
        if (days.isEmpty()) return emptyList()
        val times: List<LocalTime> = when (freq) {
            Freq.HOURLY -> if (byHour.isNotEmpty() && period.hour !in byHour) emptyList()
            else combos(listOf(period.hour), d.minutes, d.seconds)
            Freq.MINUTELY -> if ((byHour.isNotEmpty() && period.hour !in byHour) || (byMinute.isNotEmpty() && period.minute !in byMinute)) emptyList()
            else combos(listOf(period.hour), listOf(period.minute), d.seconds)
            Freq.SECONDLY -> if ((byHour.isNotEmpty() && period.hour !in byHour) || (byMinute.isNotEmpty() && period.minute !in byMinute) ||
                (bySecond.isNotEmpty() && period.second !in bySecond)
            ) emptyList() else listOf(period.time)
            else -> {
                budget.spend(d.hours.size.toLong() * d.minutes.size * d.seconds.size)
                combos(d.hours, d.minutes, d.seconds)
            }
        }
        budget.spend(days.size.toLong() * times.size)
        var all = days.flatMap { day -> times.map { LocalDateTime(day, it) } }.sorted()
        if (bySetPos.isNotEmpty() && all.isNotEmpty()) {
            all = bySetPos.mapNotNull { pos ->
                when {
                    pos > 0 -> all.getOrNull(pos - 1)
                    pos < 0 -> all.getOrNull(all.size + pos)
                    else -> null
                }
            }.distinct().sorted()
        }
        return all
    }

    private fun combos(hours: List<Int>, minutes: List<Int>, seconds: List<Int>): List<LocalTime> =
        hours.filter { it in 0..23 }.flatMap { h ->
            minutes.filter { it in 0..59 }.flatMap { m -> seconds.filter { it in 0..59 }.map { s -> LocalTime(h, m, s) } }
        }

    private fun datesBetween(from: LocalDate, until: LocalDate): List<LocalDate> {
        val result = ArrayList<LocalDate>(366)
        var day = from
        while (day < until) {
            result += day
            day = day.plus(1, DateTimeUnit.DAY)
        }
        return result
    }

    private fun dayMatches(date: LocalDate, d: Defaults): Boolean {
        if (d.months.isNotEmpty() && date.month.number !in d.months) return false
        if (byWeekNo.isNotEmpty() && !weekNoMatches(date)) return false
        if (byYearDay.isNotEmpty()) {
            val daysInYear = if (LocalDate(date.year, 12, 31).dayOfYear == 366) 366 else 365
            val ok = byYearDay.any { n -> if (n > 0) date.dayOfYear == n else date.dayOfYear == daysInYear + n + 1 }
            if (!ok) return false
        }
        if (d.monthDays.isNotEmpty()) {
            val length = date.yearMonth.numberOfDays
            val ok = d.monthDays.any { n -> if (n > 0) date.day == n else date.day == length + n + 1 }
            if (!ok) return false
        }
        if (d.weekDays.isNotEmpty()) {
            val ok = d.weekDays.any { (n, day) ->
                if (date.dayOfWeek != day) false
                else if (n == null || freq == Freq.WEEKLY || freq == Freq.DAILY || freq.ordinal < Freq.DAILY.ordinal) true
                else nthMatches(date, n)
            }
            if (!ok) return false
        }
        return true
    }

    /** BYDAY with an ordinal: within the month (MONTHLY, or YEARLY with BYMONTH) or the year. */
    private fun nthMatches(date: LocalDate, n: Int): Boolean {
        val withinMonth = freq == Freq.MONTHLY || (freq == Freq.YEARLY && byMonth.isNotEmpty())
        return if (withinMonth) {
            val first = (date.day - 1) / 7 + 1
            val last = (date.yearMonth.numberOfDays - date.day) / 7 + 1
            if (n > 0) first == n else last == -n
        } else {
            val daysInYear = if (LocalDate(date.year, 12, 31).dayOfYear == 366) 366 else 365
            val first = (date.dayOfYear - 1) / 7 + 1
            val last = (daysInYear - date.dayOfYear) / 7 + 1
            if (n > 0) first == n else last == -n
        }
    }

    /** ISO-style week numbers with [weekStart]; week 1 holds at least four days of the year. */
    private fun weekNoMatches(date: LocalDate): Boolean {
        val (weekYear, week) = weekOf(date)
        val weeksInYear = weeksIn(weekYear)
        return byWeekNo.any { n -> if (n > 0) week == n else week == weeksInYear + n + 1 }
    }

    private fun firstWeekStart(year: Int): LocalDate {
        val jan4 = LocalDate(year, 1, 4)
        return weekStartOf(jan4)
    }

    private fun weekOf(date: LocalDate): Pair<Int, Int> {
        var year = date.year
        var first = firstWeekStart(year)
        if (date < first) {
            year -= 1
            first = firstWeekStart(year)
        } else {
            val next = firstWeekStart(year + 1)
            if (date >= next) {
                year += 1
                first = next
            }
        }
        val days = (date.toEpochDays() - first.toEpochDays()).toInt()
        return year to days / 7 + 1
    }

    private fun weeksIn(year: Int): Int =
        ((firstWeekStart(year + 1).toEpochDays() - firstWeekStart(year).toEpochDays()) / 7).toInt()
}
