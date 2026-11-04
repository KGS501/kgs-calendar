package com.kgs.calendar.data.recurrence

import kotlin.math.roundToLong
import com.kgs.calendar.time.*
import kotlinx.datetime.*
import com.kgs.calendar.domain.time.orDeviceZone
import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.ical.EventRecurrenceOverride
import com.kgs.calendar.data.ical.RecurrenceOverrideCodec
import com.kgs.calendar.domain.model.CalendarOccurrenceId
import com.kgs.calendar.domain.model.CalendarOccurrenceEnvelope
import com.kgs.calendar.data.ical.IcalPlatform
import kotlinx.datetime.DayOfWeek
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month
import com.kgs.calendar.time.ZoneId
import com.kgs.calendar.time.ZoneOffset
import com.kgs.calendar.time.DateTimeFormatter
import com.kgs.calendar.time.TemporalAdjusters

/**
 * Expands a recurring event (one with an RRULE) into individual occurrences
 * that intersect [rangeStartMillis, rangeEndMillis].
 *
 * Supports the subset of RFC 5545 RRULE features that real-world calendar
 * clients overwhelmingly produce: FREQ (DAILY/WEEKLY/MONTHLY/YEARLY), INTERVAL,
 * COUNT, UNTIL, BYDAY (with optional ordinal for monthly/yearly), BYMONTHDAY,
 * BYMONTH and WKST. Combined with EXDATE handling, this covers daily standups,
 * weekly meetings, biweekly classes, monthly billing reminders and yearly
 * birthdays — the most common cases users notice as "missing" without
 * expansion.
 *
 * The expander is deliberately conservative: anything it cannot interpret
 * (e.g. BYSETPOS with complex combinations) falls back to returning just the
 * master event when in range, rather than silently producing wrong dates.
 */
/** One occurrence and the original recurrence identity it keeps, wherever an exception moved it. */
internal class RecurrenceEntry(val recurrenceIdMillis: Long, val occurrence: EventEntity)

/** A null [zoneId] follows the device's zone (see [orDeviceZone]). */
class RecurrenceExpander(private val fixedZoneId: ZoneId? = null) {
    internal val zoneId: ZoneId get() = fixedZoneId.orDeviceZone()

    fun expand(master: EventEntity, rangeStartMillis: Long, rangeEndMillis: Long): List<EventEntity> =
        expandEntries(master, rangeStartMillis, rangeEndMillis).map { it.occurrence }

    /**
     * The recurrence set first, exceptions second, the range last: an exception is part of the set
     * whatever window its original date falls in, and only where it ended up decides if it shows.
     */
    internal fun expandEntries(master: EventEntity, rangeStartMillis: Long, rangeEndMillis: Long): List<RecurrenceEntry> {
        val overrides = RecurrenceOverrideCodec.decodeEvents(master.recurrenceOverridesJson)
        val zone = master.seriesZone()
        val following = overrides.filter { it.thisAndFuture }.sortedBy { it.recurrenceIdMillis }
        // A RANGE exception can bring occurrences into the window from outside of it.
        val shifts = following.map { it.startsAtMillis - it.recurrenceIdMillis }
        val originals = originalOccurrences(
            master,
            rangeStartMillis - (shifts.maxOrNull() ?: 0L).coerceAtLeast(0L),
            rangeEndMillis - (shifts.minOrNull() ?: 0L).coerceAtMost(0L),
        ).distinctBy { it.startsAtMillis }
        val exact = overrides.associateBy { it.recurrenceIdMillis }
        val entries = originals.mapNotNull { occurrence ->
            val recurrenceId = occurrence.startsAtMillis
            val own = exact[recurrenceId]
            val inherited = following.lastOrNull { it.recurrenceIdMillis < recurrenceId }
            when {
                own != null -> own.takeUnless { it.isCancelled() }?.let { RecurrenceEntry(recurrenceId, it.applyTo(occurrence)) }
                inherited != null -> inherited.takeUnless { it.isCancelled() }
                    ?.let { RecurrenceEntry(recurrenceId, it.applyToLater(occurrence, zone)) }
                else -> RecurrenceEntry(recurrenceId, occurrence)
            }
        }
        val listed = originals.mapTo(HashSet()) { it.startsAtMillis }
        val detached = overrides
            .filter { it.recurrenceIdMillis !in listed && !it.isCancelled() }
            .filter { intersects(it.startsAtMillis, it.endsAtMillis, rangeStartMillis, rangeEndMillis) }
            .mapNotNull { override ->
                val original = originalOccurrences(master, override.recurrenceIdMillis - 1, override.recurrenceIdMillis + 1)
                    .firstOrNull { it.startsAtMillis == override.recurrenceIdMillis }
                original?.let { RecurrenceEntry(override.recurrenceIdMillis, override.applyTo(it)) }
            }
        return (entries + detached)
            .filter { intersects(it.occurrence.startsAtMillis, it.occurrence.endsAtMillis, rangeStartMillis, rangeEndMillis) }
            .sortedBy { it.occurrence.startsAtMillis }
    }

    private fun EventRecurrenceOverride.isCancelled(): Boolean = status.equals("CANCELLED", ignoreCase = true)

    /** The occurrence moves as far on the clock as the exception that starts its range. */
    private fun EventRecurrenceOverride.applyToLater(occurrence: EventEntity, zone: ZoneId): EventEntity {
        val start = shiftedByWallClock(occurrence.startsAtMillis, recurrenceIdMillis, startsAtMillis, zone)
        return applyTo(occurrence).copy(startsAtMillis = start, endsAtMillis = start + (endsAtMillis - startsAtMillis))
    }

    private fun EventEntity.seriesZone(): ZoneId =
        timezoneId?.let { runCatching { zoneOf(it) }.getOrNull() } ?: zoneId

    /** The series without its exceptions; ones starting outside the range may be included. */
    private fun originalOccurrences(master: EventEntity, rangeStartMillis: Long, rangeEndMillis: Long): List<EventEntity> {
        val rule = master.recurrenceRule?.takeIf { it.isNotBlank() }
        val eventZone = master.seriesZone()
        if (rule == null) {
            val duration = (master.endsAtMillis - master.startsAtMillis).coerceAtLeast(0L)
            val excluded = master.exDatesCsv?.split(',')?.mapNotNull { it.trim().toLongOrNull() }?.toSet().orEmpty()
            val base = masterIfInRange(master, rangeStartMillis, rangeEndMillis)
            val additions = master.rDatesCsv
                ?.split(',')
                ?.mapNotNull { it.trim().toLongOrNull() }
                .orEmpty()
                .filter { intersects(it, it + duration, rangeStartMillis, rangeEndMillis) }
                .map { master.occurrenceStartingAt(it, duration, eventZone) }
            return (base + additions).filterNot { it.startsAtMillis in excluded }
        }
        val parts = parseRrule(rule)
        val freq = parts["FREQ"]?.uppercase() ?: return masterIfInRange(master, rangeStartMillis, rangeEndMillis)
        val interval = parts["INTERVAL"]?.toIntOrNull()?.takeIf { it > 0 } ?: 1
        val count = parts["COUNT"]?.toIntOrNull()
        val until = parts["UNTIL"]?.let { parseUntil(it, eventZone) }
        val wkst = parts["WKST"]?.let { dayCode(it) } ?: DayOfWeek.MONDAY
        val byDay = parts["BYDAY"]?.split(",")?.mapNotNull { it.trim().takeIf { s -> s.isNotEmpty() } }.orEmpty()
        val byMonthDay = parts["BYMONTHDAY"]?.split(",")?.mapNotNull { it.trim().toIntOrNull() }.orEmpty()
        val byMonth = parts["BYMONTH"]?.split(",")?.mapNotNull { it.trim().toIntOrNull() }.orEmpty()

        val exDates = master.exDatesCsv
            ?.split(',')
            ?.mapNotNull { it.trim().toLongOrNull() }
            ?.toMutableSet()
            ?: mutableSetOf()
        // Also tolerate exact-date EXDATE matches by normalising to start-of-day in the local zone.
        // A rule with several occurrences a day only matches whole days for date-only EXDATEs.
        val singleOccurrencePerDay = freq in setOf("DAILY", "WEEKLY", "MONTHLY", "YEARLY") &&
            parts.keys.none { it in setOf("BYHOUR", "BYMINUTE", "BYSECOND") }
        val exDatesAsLocalDate = exDates
            .map { Instant.ofEpochMilli(it).atZone(eventZone) }
            .filter { singleOccurrencePerDay || it.toLocalTime() == kotlinx.datetime.LocalTime.MIDNIGHT }
            .map { it.toLocalDate() }
            .toSet()

        val duration = (master.endsAtMillis - master.startsAtMillis).coerceAtLeast(0L)
        val masterLdt = Instant.ofEpochMilli(master.startsAtMillis).atZone(eventZone).toLocalDateTime()
        val masterDate = masterLdt.toLocalDate()
        val masterTime = masterLdt.toLocalTime()

        val out = mutableListOf<EventEntity>()
        val state = EmitState(
            master = master,
            duration = duration,
            rangeStart = rangeStartMillis,
            rangeEnd = rangeEndMillis,
            count = count,
            until = until,
            exDates = exDates,
            exDatesAsLocalDate = exDatesAsLocalDate,
            zone = eventZone,
            out = out,
        )

        if (canExpandManually(freq, parts, byDay, byMonthDay, byMonth)) {
            runCatching {
                when (freq) {
                    "DAILY" -> expandDaily(masterDate, masterTime, interval, state)
                    "WEEKLY" -> expandWeekly(masterDate, masterTime, interval, wkst, byDay, state)
                    "MONTHLY" -> expandMonthly(masterDate, masterTime, interval, byDay, byMonthDay, byMonth, state)
                    "YEARLY" -> expandYearly(masterDate, masterTime, interval, byDay, byMonthDay, byMonth, state)
                    else -> masterIfInRange(master, rangeStartMillis, rangeEndMillis).also(out::addAll)
                }
            }.onFailure {
                // Defensive: if anything throws, surface just the master so the event isn't
                // silently lost from the UI.
                out.clear()
                out.addAll(masterIfInRange(master, rangeStartMillis, rangeEndMillis))
            }
        } else {
            val standardsExpansion = expandRuleWithIcal4j(
                master = master,
                rule = rule,
                sourceZone = eventZone,
                rangeStartMillis = rangeStartMillis,
                rangeEndMillis = rangeEndMillis,
                exDates = exDates,
                exDatesAsLocalDate = exDatesAsLocalDate,
            )
            if (standardsExpansion != null) {
                out += standardsExpansion
            } else {
                out.addAll(masterIfInRange(master, rangeStartMillis, rangeEndMillis))
            }
        }
        val rDates = master.rDatesCsv
            ?.split(',')
            ?.mapNotNull { it.trim().toLongOrNull() }
            .orEmpty()
            .filter { intersects(it, it + duration, rangeStartMillis, rangeEndMillis) }
            .filterNot { it in exDates }
            .map { start -> master.occurrenceStartingAt(start, duration, eventZone) }
        return out + rDates
    }

    fun expandWithIdentity(
        master: EventEntity,
        rangeStartMillis: Long,
        rangeEndMillis: Long,
    ): List<CalendarOccurrenceEnvelope<EventEntity>> =
        expandEntries(master, rangeStartMillis, rangeEndMillis).map { entry ->
            CalendarOccurrenceEnvelope(
                occurrenceId = CalendarOccurrenceId.Event(master.resourceHref, entry.recurrenceIdMillis),
                item = entry.occurrence,
            )
        }

    private fun expandRuleWithIcal4j(
        master: EventEntity,
        rule: String,
        sourceZone: ZoneId,
        rangeStartMillis: Long,
        rangeEndMillis: Long,
        exDates: Set<Long>,
        exDatesAsLocalDate: Set<LocalDate>,
    ): List<EventEntity>? = runCatching {
        val duration = (master.endsAtMillis - master.startsAtMillis).coerceAtLeast(0L)
        // Occurrences that began before the range but still reach into it count too.
        val searchStartMillis = rangeStartMillis - duration
        IcalPlatform.recurrenceStarts(rule, master.allDay, master.startsAtMillis, sourceZone, searchStartMillis, rangeEndMillis)!!
            .asSequence()
            .filter { start -> intersects(start, start + duration, rangeStartMillis, rangeEndMillis) }
            .filterNot { start ->
                start in exDates || Instant.ofEpochMilli(start).atZone(sourceZone).toLocalDate() in exDatesAsLocalDate
            }
            .take(MAX_ITERATIONS)
            .map { start -> master.occurrenceStartingAt(start, duration, sourceZone) }
            .toList()
    }.getOrNull()

    private fun canExpandManually(
        freq: String,
        parts: Map<String, String>,
        byDay: List<String>,
        byMonthDay: List<Int>,
        byMonth: List<Int>,
    ): Boolean {
        if (freq !in setOf("DAILY", "WEEKLY", "MONTHLY", "YEARLY")) return false
        val supportedKeys = setOf("FREQ", "INTERVAL", "COUNT", "UNTIL", "WKST", "BYDAY", "BYMONTHDAY", "BYMONTH")
        if (parts.keys.any { it !in supportedKeys }) return false
        return when (freq) {
            "DAILY" -> byDay.isEmpty() && byMonthDay.isEmpty() && byMonth.isEmpty()
            "WEEKLY" -> byMonthDay.isEmpty() && byMonth.isEmpty() && byDay.all { parseByDay(it)?.first == null }
            // Both BY parts intersect (RFC 5545 3.3.10); the standards expansion handles that.
            "MONTHLY" -> byMonth.isEmpty() && (byMonthDay.isEmpty() || byDay.isEmpty()) && byDay.all { parseByDay(it) != null }
            "YEARLY" -> byDay.isEmpty() && byMonthDay.isEmpty() && byMonth.isEmpty()
            else -> false
        }
    }

    private fun masterIfInRange(master: EventEntity, rangeStart: Long, rangeEnd: Long): List<EventEntity> =
        if (intersects(master.startsAtMillis, master.endsAtMillis, rangeStart, rangeEnd)) listOf(master) else emptyList()

    private class EmitState(
        val master: EventEntity,
        val duration: Long,
        val rangeStart: Long,
        val rangeEnd: Long,
        val count: Int?,
        val until: Long?,
        val exDates: Set<Long>,
        val exDatesAsLocalDate: Set<LocalDate>,
        val zone: ZoneId,
        val out: MutableList<EventEntity>,
    ) {
        var emitted: Int = 0
        var iterations: Int = 0

        /** The earliest start that still reaches into the range: fast-forwards stop here, not at its start. */
        val searchStart: Long get() = rangeStart - duration
    }

    /** Returns false to signal the outer loop must stop (count/until exhausted or safety cap). */
    private fun emitAt(startLdt: LocalDateTime, zone: ZoneId, state: EmitState): Boolean {
        if (++state.iterations > MAX_ITERATIONS) return false
        val startMillis = startLdt.atZone(zone).toInstant().toEpochMilli()
        if (state.until != null && startMillis > state.until) return false
        if (state.count != null && state.emitted >= state.count) return false
        state.emitted++
        // EXDATE check
        if (startMillis in state.exDates) return true
        if (startLdt.toLocalDate() in state.exDatesAsLocalDate) return true
        // Range check
        if (startMillis >= state.rangeEnd) return true
        if (!intersects(startMillis, startMillis + state.duration, state.rangeStart, state.rangeEnd)) return true
        state.out += state.master.occurrenceStartingAt(startMillis, state.duration, zone)
        return true
    }

    /**
     * The master moved to [startMillis]. Timed occurrences keep the exact duration; all-day ones keep
     * their number of days, so a day with a DST switch (23 or 25 hours) still ends at midnight.
     */
    private fun EventEntity.occurrenceStartingAt(startMillis: Long, durationMillis: Long, zone: ZoneId): EventEntity {
        val endMillis = if (allDay) {
            val days = (durationMillis.toDouble() / DAY_MILLIS).roundToLong()
            Instant.ofEpochMilli(startMillis).atZone(zone).toLocalDate()
                .plusDays(days)
                .atStartOfDay(zone)
                .toInstant()
                .toEpochMilli()
        } else {
            startMillis + durationMillis
        }
        return copy(startsAtMillis = startMillis, endsAtMillis = endMillis)
    }

    private fun expandDaily(
        masterDate: LocalDate,
        masterTime: kotlinx.datetime.LocalTime,
        interval: Int,
        state: EmitState,
    ) {
        var cursor = masterDate
        // Fast-forward to first occurrence in [rangeStart, ...] when COUNT is not set.
        if (state.count == null) {
            val rangeStartDate = Instant.ofEpochMilli(state.searchStart).atZone(state.zone).toLocalDate()
            if (cursor.isBefore(rangeStartDate)) {
                val daysGap = com.kgs.calendar.time.ChronoUnit.DAYS.between(cursor, rangeStartDate)
                val skip = (daysGap / interval).coerceAtLeast(0)
                if (skip > 0) cursor = cursor.plusDays(skip * interval)
            }
        }
        while (true) {
            val ldt = cursor.atTime(masterTime)
            val millis = ldt.atZone(state.zone).toInstant().toEpochMilli()
            if (millis >= state.rangeEnd) return
            if (!emitAt(ldt, state.zone, state)) return
            cursor = cursor.plusDays(interval.toLong())
        }
    }

    private fun expandWeekly(
        masterDate: LocalDate,
        masterTime: kotlinx.datetime.LocalTime,
        interval: Int,
        wkst: DayOfWeek,
        byDayRaw: List<String>,
        state: EmitState,
    ) {
        val daysOfWeek = byDayRaw
            .mapNotNull { dayCode(it) }
            .ifEmpty { listOf(masterDate.dayOfWeek) }
            .distinct()
            .sortedBy { offsetFromWeekStart(it, wkst) }
        var weekStart = masterDate.with(TemporalAdjusters.previousOrSame(wkst))
        // Fast-forward weeks when COUNT is not set.
        if (state.count == null) {
            val rangeStartDate = Instant.ofEpochMilli(state.searchStart).atZone(state.zone).toLocalDate()
            if (weekStart.isBefore(rangeStartDate)) {
                val weeksGap = com.kgs.calendar.time.ChronoUnit.WEEKS.between(weekStart, rangeStartDate)
                val skip = (weeksGap / interval).coerceAtLeast(0)
                if (skip > 0) weekStart = weekStart.plusWeeks(skip * interval)
            }
        }
        while (true) {
            for (dow in daysOfWeek) {
                val date = weekStart.plusDays(offsetFromWeekStart(dow, wkst).toLong())
                if (date.isBefore(masterDate)) continue
                val ldt = date.atTime(masterTime)
                val millis = ldt.atZone(state.zone).toInstant().toEpochMilli()
                if (millis >= state.rangeEnd) return
                if (!emitAt(ldt, state.zone, state)) return
            }
            weekStart = weekStart.plusWeeks(interval.toLong())
        }
    }

    private fun expandMonthly(
        masterDate: LocalDate,
        masterTime: kotlinx.datetime.LocalTime,
        interval: Int,
        byDayRaw: List<String>,
        byMonthDay: List<Int>,
        byMonth: List<Int>,
        state: EmitState,
    ) {
        var cursor = masterDate.withDayOfMonth(1)
        if (state.count == null) {
            val rangeStartDate = Instant.ofEpochMilli(state.searchStart).atZone(state.zone).toLocalDate().withDayOfMonth(1)
            if (cursor.isBefore(rangeStartDate)) {
                val monthsGap = com.kgs.calendar.time.ChronoUnit.MONTHS.between(cursor, rangeStartDate)
                val skip = (monthsGap / interval).coerceAtLeast(0)
                if (skip > 0) cursor = cursor.plusMonths(skip * interval)
            }
        }
        while (true) {
            // A month can yield nothing (BYMONTHDAY=31), so no occurrence is guaranteed to end this.
            if (cursor.atTime(masterTime).atZone(state.zone).toInstant().toEpochMilli() >= state.rangeEnd) return
            if (byMonth.isEmpty() || cursor.monthValue in byMonth) {
                val dates = monthlyDatesFor(cursor, masterDate, byMonthDay, byDayRaw)
                for (date in dates) {
                    if (date.isBefore(masterDate)) continue
                    val ldt = date.atTime(masterTime)
                    val millis = ldt.atZone(state.zone).toInstant().toEpochMilli()
                    if (millis >= state.rangeEnd) return
                    if (!emitAt(ldt, state.zone, state)) return
                }
            }
            cursor = cursor.plusMonths(interval.toLong())
        }
    }

    private fun expandYearly(
        masterDate: LocalDate,
        masterTime: kotlinx.datetime.LocalTime,
        interval: Int,
        byDayRaw: List<String>,
        byMonthDay: List<Int>,
        byMonth: List<Int>,
        state: EmitState,
    ) {
        var cursor = masterDate.withDayOfYear(1)
        if (state.count == null) {
            val rangeStartYear = Instant.ofEpochMilli(state.searchStart).atZone(state.zone).toLocalDate().year
            if (cursor.year < rangeStartYear) {
                val yearsGap = (rangeStartYear - cursor.year).toLong()
                val skip = (yearsGap / interval).coerceAtLeast(0)
                if (skip > 0) cursor = cursor.plusYears(skip * interval)
            }
        }
        while (true) {
            if (cursor.atTime(masterTime).atZone(state.zone).toInstant().toEpochMilli() >= state.rangeEnd) return
            val months = if (byMonth.isNotEmpty()) byMonth else listOf(masterDate.monthValue)
            for (m in months.sorted()) {
                val monthStart = LocalDate.of(cursor.year, monthOf(m), 1)
                val dates = monthlyDatesFor(monthStart, masterDate, byMonthDay, byDayRaw)
                for (date in dates) {
                    if (date.isBefore(masterDate)) continue
                    val ldt = date.atTime(masterTime)
                    val millis = ldt.atZone(state.zone).toInstant().toEpochMilli()
                    if (millis >= state.rangeEnd) return
                    if (!emitAt(ldt, state.zone, state)) return
                }
            }
            cursor = cursor.plusYears(interval.toLong())
        }
    }

    private fun monthlyDatesFor(
        anyDayInMonth: LocalDate,
        masterDate: LocalDate,
        byMonthDay: List<Int>,
        byDayRaw: List<String>,
    ): List<LocalDate> {
        val firstOfMonth = anyDayInMonth.withDayOfMonth(1)
        val daysInMonth = firstOfMonth.lengthOfMonth()
        if (byMonthDay.isEmpty() && byDayRaw.isEmpty()) {
            // Same day of the month as the master; a month without that day has no occurrence.
            val d = masterDate.dayOfMonth
            return if (d <= daysInMonth) listOf(firstOfMonth.withDayOfMonth(d)) else emptyList()
        }
        val out = mutableSetOf<LocalDate>()
        for (d in byMonthDay) {
            val day = if (d < 0) daysInMonth + d + 1 else d
            if (day in 1..daysInMonth) out += firstOfMonth.withDayOfMonth(day)
        }
        for (raw in byDayRaw) {
            val parsed = parseByDay(raw) ?: continue
            val (ordinal, dow) = parsed
            out += datesForByDayInMonth(firstOfMonth, dow, ordinal)
        }
        return out.sorted()
    }

    private fun datesForByDayInMonth(firstOfMonth: LocalDate, dow: DayOfWeek, ordinal: Int?): List<LocalDate> {
        val daysInMonth = firstOfMonth.lengthOfMonth()
        val matches = (1..daysInMonth).map { firstOfMonth.withDayOfMonth(it) }.filter { it.dayOfWeek == dow }
        if (matches.isEmpty()) return emptyList()
        return when {
            ordinal == null -> matches
            ordinal > 0 -> matches.getOrNull(ordinal - 1)?.let { listOf(it) }.orEmpty()
            else -> matches.getOrNull(matches.size + ordinal)?.let { listOf(it) }.orEmpty()
        }
    }

    private fun parseByDay(raw: String): Pair<Int?, DayOfWeek>? {
        val token = raw.trim().uppercase().ifEmpty { return null }
        val day = dayCode(token) ?: return null
        if (token.length <= 2) return null to day
        val prefix = token.dropLast(2)
        val ordinal = prefix.toIntOrNull() ?: return null to day
        return ordinal to day
    }

    private fun offsetFromWeekStart(target: DayOfWeek, wkst: DayOfWeek): Int =
        (target.value - wkst.value + 7) % 7

    private fun parseRrule(rule: String): Map<String, String> =
        rule.split(';').mapNotNull {
            val parts = it.split('=', limit = 2)
            if (parts.size == 2 && parts[0].isNotBlank()) parts[0].trim().uppercase() to parts[1].trim() else null
        }.toMap()

    private fun parseUntil(value: String, sourceZone: ZoneId): Long? {
        val v = value.trim()
        return runCatching {
            when {
                v.length == 8 -> LocalDate.parse(v, DATE).plusDays(1).atStartOfDay(sourceZone).toInstant().toEpochMilli() - 1
                v.endsWith("Z") -> LocalDateTime.parse(v.dropLast(1), DATE_TIME).toInstant(ZoneOffset.UTC).toEpochMilli()
                else -> LocalDateTime.parse(v, DATE_TIME).atZone(sourceZone).toInstant().toEpochMilli()
            }
        }.getOrNull()
    }

    private fun dayCode(s: String): DayOfWeek? {
        val code = s.trim().takeLast(2).uppercase()
        return when (code) {
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

    companion object {
        /** A zero-length item still occupies its instant, so it isn't lost at a range's start. */
        internal fun intersects(start: Long, end: Long, rangeStart: Long, rangeEnd: Long): Boolean =
            start < rangeEnd && (if (end > start) end else start + 1) > rangeStart

        /**
         * [millis] moved as far on the clock as [fromMillis] to [toMillis]: an occurrence after a
         * DST change keeps the wall-clock time the exception gave its range.
         */
        internal fun shiftedByWallClock(millis: Long, fromMillis: Long, toMillis: Long, zone: ZoneId): Long {
            fun offsetMillis(at: Long) = Instant.fromEpochMilliseconds(at).offsetIn(zone).totalSeconds * 1000L
            val clockShift = (toMillis + offsetMillis(toMillis)) - (fromMillis + offsetMillis(fromMillis))
            val shifted = millis + clockShift
            return shifted + offsetMillis(millis) - offsetMillis(shifted)
        }

        private val DATE = DateTimeFormatter.BASIC_ISO_DATE
        private val DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
        private const val HOUR_MILLIS = 60L * 60L * 1000L
        private const val DAY_MILLIS = 24L * HOUR_MILLIS
        // Safety cap; far above realistic occurrence counts within any UI window.
        private const val MAX_ITERATIONS = 20000
    }
}
