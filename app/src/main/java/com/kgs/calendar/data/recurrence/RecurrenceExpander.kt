package com.kgs.calendar.data.recurrence

import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.ical.RecurrenceOverrideCodec
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.Recur
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.parameter.Value
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

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
class RecurrenceExpander(private val zoneId: ZoneId = ZoneId.systemDefault()) {
    private val timeZoneRegistry = TimeZoneRegistryFactory.getInstance().createRegistry()

    fun expand(master: EventEntity, rangeStartMillis: Long, rangeEndMillis: Long): List<EventEntity> {
        val rule = master.recurrenceRule?.takeIf { it.isNotBlank() }
        if (rule == null) {
            val duration = (master.endsAtMillis - master.startsAtMillis).coerceAtLeast(0L)
            val base = masterIfInRange(master, rangeStartMillis, rangeEndMillis)
            val additions = master.rDatesCsv
                ?.split(',')
                ?.mapNotNull { it.trim().toLongOrNull() }
                .orEmpty()
                .filter { it < rangeEndMillis && it + duration > rangeStartMillis }
                .map { master.copy(startsAtMillis = it, endsAtMillis = it + duration) }
            return applyOverrides(master, base + additions)
        }
        val eventZone = master.timezoneId
            ?.let { runCatching { ZoneId.of(it) }.getOrNull() }
            ?: zoneId
        val parts = parseRrule(rule)
        val freq = parts["FREQ"]?.uppercase(Locale.US) ?: return masterIfInRange(master, rangeStartMillis, rangeEndMillis)
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
        val exDatesAsLocalDate = exDates.map { Instant.ofEpochMilli(it).atZone(eventZone).toLocalDate() }.toSet()

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
            .filter { it < rangeEndMillis && it + duration > rangeStartMillis }
            .filterNot { it in exDates }
            .map { start ->
                master.copy(startsAtMillis = start, endsAtMillis = start + duration)
            }
        return applyOverrides(master, out + rDates)
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
        val recur = Recur(rule)
        val valueType = if (master.allDay) Value.DATE else Value.DATE_TIME
        val seed: net.fortuna.ical4j.model.Date
        val rangeStart: net.fortuna.ical4j.model.Date
        val rangeEnd: net.fortuna.ical4j.model.Date
        if (master.allDay) {
            seed = net.fortuna.ical4j.model.Date(
                Instant.ofEpochMilli(master.startsAtMillis).atZone(sourceZone).toLocalDate().format(DATE),
            )
            rangeStart = net.fortuna.ical4j.model.Date(
                Instant.ofEpochMilli(rangeStartMillis).atZone(sourceZone).toLocalDate().format(DATE),
            )
            rangeEnd = net.fortuna.ical4j.model.Date(
                Instant.ofEpochMilli(rangeEndMillis).atZone(sourceZone).toLocalDate().plusDays(1).format(DATE),
            )
        } else {
            val timeZone = timeZoneRegistry.getTimeZone(sourceZone.id)
            seed = if (timeZone != null) {
                DateTime(java.util.Date(master.startsAtMillis), timeZone)
            } else {
                DateTime(master.startsAtMillis)
            }
            rangeStart = if (timeZone != null) DateTime(java.util.Date(rangeStartMillis), timeZone) else DateTime(rangeStartMillis)
            rangeEnd = if (timeZone != null) DateTime(java.util.Date(rangeEndMillis), timeZone) else DateTime(rangeEndMillis)
        }
        val duration = (master.endsAtMillis - master.startsAtMillis).coerceAtLeast(0L)
        recur.getDates(seed, rangeStart, rangeEnd, valueType)
            .asSequence()
            .map { date ->
                if (master.allDay) {
                    LocalDate.parse(date.toString(), DATE)
                        .atStartOfDay(sourceZone)
                        .toInstant()
                        .toEpochMilli()
                } else {
                    date.time
                }
            }
            .filter { start -> start < rangeEndMillis && start + duration > rangeStartMillis }
            .filterNot { start ->
                start in exDates || Instant.ofEpochMilli(start).atZone(sourceZone).toLocalDate() in exDatesAsLocalDate
            }
            .take(MAX_ITERATIONS)
            .map { start -> master.copy(startsAtMillis = start, endsAtMillis = start + duration) }
            .toList()
    }.getOrNull()

    private fun applyOverrides(master: EventEntity, occurrences: List<EventEntity>): List<EventEntity> {
        val overrides = RecurrenceOverrideCodec.decodeEvents(master.recurrenceOverridesJson)
            .associateBy { it.recurrenceIdMillis }
        return occurrences
            .distinctBy { it.startsAtMillis }
            .mapNotNull { occurrence ->
                val override = overrides[occurrence.startsAtMillis]
                when {
                    override?.status.equals("CANCELLED", ignoreCase = true) -> null
                    override != null -> override.applyTo(occurrence)
                    else -> occurrence
                }
            }
            .sortedBy { it.startsAtMillis }
    }

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
            "MONTHLY" -> byMonth.isEmpty() && byDay.all { parseByDay(it) != null }
            "YEARLY" -> byDay.isEmpty() && byMonthDay.isEmpty() && byMonth.isEmpty()
            else -> false
        }
    }

    private fun masterIfInRange(master: EventEntity, rangeStart: Long, rangeEnd: Long): List<EventEntity> {
        val ends = if (master.endsAtMillis > master.startsAtMillis) master.endsAtMillis else master.startsAtMillis + HOUR_MILLIS
        return if (master.startsAtMillis < rangeEnd && ends > rangeStart) listOf(master) else emptyList()
    }

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
        val endMillis = startMillis + state.duration
        val endForRange = if (endMillis > startMillis) endMillis else startMillis + HOUR_MILLIS
        if (endForRange <= state.rangeStart) return true
        state.out += state.master.copy(
            startsAtMillis = startMillis,
            endsAtMillis = endMillis,
        )
        return true
    }

    private fun expandDaily(
        masterDate: LocalDate,
        masterTime: java.time.LocalTime,
        interval: Int,
        state: EmitState,
    ) {
        var cursor = masterDate
        // Fast-forward to first occurrence in [rangeStart, ...] when COUNT is not set.
        if (state.count == null) {
            val rangeStartDate = Instant.ofEpochMilli(state.rangeStart).atZone(state.zone).toLocalDate()
            if (cursor.isBefore(rangeStartDate)) {
                val daysGap = java.time.temporal.ChronoUnit.DAYS.between(cursor, rangeStartDate)
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
        masterTime: java.time.LocalTime,
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
            val rangeStartDate = Instant.ofEpochMilli(state.rangeStart).atZone(state.zone).toLocalDate()
            if (weekStart.isBefore(rangeStartDate)) {
                val weeksGap = java.time.temporal.ChronoUnit.WEEKS.between(weekStart, rangeStartDate)
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
        masterTime: java.time.LocalTime,
        interval: Int,
        byDayRaw: List<String>,
        byMonthDay: List<Int>,
        byMonth: List<Int>,
        state: EmitState,
    ) {
        var cursor = masterDate.withDayOfMonth(1)
        if (state.count == null) {
            val rangeStartDate = Instant.ofEpochMilli(state.rangeStart).atZone(state.zone).toLocalDate().withDayOfMonth(1)
            if (cursor.isBefore(rangeStartDate)) {
                val monthsGap = java.time.temporal.ChronoUnit.MONTHS.between(cursor, rangeStartDate)
                val skip = (monthsGap / interval).coerceAtLeast(0)
                if (skip > 0) cursor = cursor.plusMonths(skip * interval)
            }
        }
        while (true) {
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
        masterTime: java.time.LocalTime,
        interval: Int,
        byDayRaw: List<String>,
        byMonthDay: List<Int>,
        byMonth: List<Int>,
        state: EmitState,
    ) {
        var cursor = masterDate.withDayOfYear(1)
        if (state.count == null) {
            val rangeStartYear = Instant.ofEpochMilli(state.rangeStart).atZone(state.zone).toLocalDate().year
            if (cursor.year < rangeStartYear) {
                val yearsGap = (rangeStartYear - cursor.year).toLong()
                val skip = (yearsGap / interval).coerceAtLeast(0)
                if (skip > 0) cursor = cursor.plusYears(skip * interval)
            }
        }
        while (true) {
            val months = if (byMonth.isNotEmpty()) byMonth else listOf(masterDate.monthValue)
            for (m in months.sorted()) {
                val monthStart = LocalDate.of(cursor.year, Month.of(m), 1)
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
            // Default: same day-of-month as the master (clamped).
            val d = masterDate.dayOfMonth.coerceAtMost(daysInMonth)
            return listOf(firstOfMonth.withDayOfMonth(d))
        }
        val out = sortedSetOf<LocalDate>(compareBy { it })
        for (d in byMonthDay) {
            val day = if (d < 0) daysInMonth + d + 1 else d
            if (day in 1..daysInMonth) out += firstOfMonth.withDayOfMonth(day)
        }
        for (raw in byDayRaw) {
            val parsed = parseByDay(raw) ?: continue
            val (ordinal, dow) = parsed
            out += datesForByDayInMonth(firstOfMonth, dow, ordinal)
        }
        return out.toList()
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
        val token = raw.trim().uppercase(Locale.US).ifEmpty { return null }
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
            if (parts.size == 2 && parts[0].isNotBlank()) parts[0].trim().uppercase(Locale.US) to parts[1].trim() else null
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
        val code = s.trim().takeLast(2).uppercase(Locale.US)
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
        private val DATE = DateTimeFormatter.BASIC_ISO_DATE
        private val DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
        private const val HOUR_MILLIS = 60L * 60L * 1000L
        // Safety cap; far above realistic occurrence counts within any UI window.
        private const val MAX_ITERATIONS = 20000
    }
}
