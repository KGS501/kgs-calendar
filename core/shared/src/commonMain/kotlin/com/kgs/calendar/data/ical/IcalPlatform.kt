package com.kgs.calendar.data.ical

import com.kgs.calendar.data.recurrence.RRule
import com.kgs.calendar.time.ZoneId
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * The iCalendar work that Android has always done with ical4j. Android and the JVM keep ical4j;
 * iOS uses [CommonIcal], which the JVM tests compare with ical4j.
 */
internal expect object IcalPlatform {
    /** Throws when [text] isn't a well-formed iCalendar object. */
    fun validateCalendar(text: String)

    /** UTC offsets in seconds of the `VTIMEZONE` [block] at each of [samplesMillis]; null if unreadable. */
    fun vtimezoneOffsetsSeconds(block: String, samplesMillis: List<Long>): List<Int>?

    /** A `VTIMEZONE` component for the IANA zone [zoneId], or null. */
    fun vtimezoneText(zoneId: String): String?

    /**
     * Start instants (epoch millis) of [rule] for an item starting at [seedMillis] in [zone], from
     * [searchStartMillis] up to [rangeEndMillis]. All-day items recur by date: their starts are
     * the dates' midnights in [zone]. Null when the rule can't be read.
     */
    fun recurrenceStarts(
        rule: String,
        allDay: Boolean,
        seedMillis: Long,
        zone: ZoneId,
        searchStartMillis: Long,
        rangeEndMillis: Long,
    ): List<Long>?
}

/** Pure-Kotlin iCalendar helpers used on platforms without ical4j. */
internal object CommonIcal {
    fun validateCalendar(text: String) {
        val lines = unfold(text)
        require(lines.firstOrNull().equals("BEGIN:VCALENDAR", ignoreCase = true)) { "Missing BEGIN:VCALENDAR" }
        val stack = ArrayDeque<String>()
        for (line in lines) {
            val name = line.substringBefore(':').substringBefore(';')
            require(name.isNotEmpty() && line.contains(':') && name.all { it.isLetterOrDigit() || it == '-' }) {
                "Invalid content line: $line"
            }
            when {
                name.equals("BEGIN", ignoreCase = true) -> stack.addLast(line.substringAfter(':').uppercase())
                name.equals("END", ignoreCase = true) -> {
                    require(stack.removeLastOrNull() == line.substringAfter(':').uppercase()) { "Unbalanced END:${line.substringAfter(':')}" }
                }
            }
        }
        require(stack.isEmpty()) { "Unterminated component" }
    }

    fun recurrenceStarts(
        rule: String,
        allDay: Boolean,
        seedMillis: Long,
        zone: TimeZone,
        searchStartMillis: Long,
        rangeEndMillis: Long,
    ): List<Long>? = runCatching {
        val recur = RRule.parse(rule)
        val seed = Instant.fromEpochMilliseconds(seedMillis).toLocalDateTime(zone)
        if (allDay) {
            val start = LocalDateTime(seed.date, LocalTime(0, 0))
            val end = LocalDateTime(Instant.fromEpochMilliseconds(rangeEndMillis).toLocalDateTime(zone).date, LocalTime(0, 0))
                .let { LocalDateTime(it.date.plusDaysSafe(2), it.time) }
            val searchStart = Instant.fromEpochMilliseconds(searchStartMillis).toLocalDateTime(zone).date
            recur.occurrences(start, end, null, searchFrom = LocalDateTime(searchStart, LocalTime(0, 0)))
                .map { it.date.atStartOfDayIn(zone).toEpochMilliseconds() }
                .toList()
        } else {
            // As ical4j's getDates, the period end is inclusive.
            val end = Instant.fromEpochMilliseconds(rangeEndMillis + 1000).toLocalDateTime(zone)
            val searchFrom = Instant.fromEpochMilliseconds(searchStartMillis).toLocalDateTime(zone)
            recur.occurrences(seed, end, zone, searchFrom = LocalDateTime(searchFrom.date.plusDaysSafe(-1), searchFrom.time))
                .map { it.toInstant(zone).toEpochMilliseconds() }
                .filter { it >= searchStartMillis && it <= rangeEndMillis }
                .toList()
        }
    }.getOrNull()

    /**
     * Offsets of a `VTIMEZONE` from its STANDARD/DAYLIGHT observances: at any instant the latest
     * onset before it decides (onsets are DTSTART, RDATEs and RRULE dates in TZOFFSETFROM time).
     */
    fun vtimezoneOffsetsSeconds(block: String, samplesMillis: List<Long>): List<Int>? = runCatching {
        val lines = unfold(block)
        data class Observance(val onsets: List<Long>, val offsetTo: Int)
        val observances = mutableListOf<Observance>()
        var current: MutableMap<String, MutableList<String>>? = null
        for (line in lines) {
            val upper = line.uppercase()
            when {
                upper == "BEGIN:STANDARD" || upper == "BEGIN:DAYLIGHT" -> current = mutableMapOf()
                upper == "END:STANDARD" || upper == "END:DAYLIGHT" -> {
                    val props = current ?: continue
                    current = null
                    val from = parseOffset(props["TZOFFSETFROM"]?.firstOrNull()) ?: continue
                    val to = parseOffset(props["TZOFFSETTO"]?.firstOrNull()) ?: continue
                    val start = props["DTSTART"]?.firstOrNull()?.let(::parseLocal) ?: continue
                    val fromOffset = UtcOffset(seconds = from)
                    val onsets = mutableListOf(start.toInstant(fromOffset).toEpochMilliseconds())
                    val last = (samplesMillis.maxOrNull() ?: 0L) + 400L * 86_400_000L
                    props["RRULE"]?.firstOrNull()?.let { rule ->
                        val endLocal = Instant.fromEpochMilliseconds(last).toLocalDateTime(TimeZone.UTC)
                        RRule.parse(rule).occurrences(start, endLocal, TimeZone.UTC)
                            .forEach { onsets += it.toInstant(fromOffset).toEpochMilliseconds() }
                    }
                    props["RDATE"]?.flatMap { it.split(',') }?.mapNotNull(::parseLocal)?.forEach {
                        onsets += it.toInstant(fromOffset).toEpochMilliseconds()
                    }
                    observances += Observance(onsets.distinct().sorted(), to)
                }
                current != null -> {
                    val name = line.substringBefore(':').substringBefore(';').uppercase()
                    current.getOrPut(name) { mutableListOf() } += line.substringAfter(':').trim()
                }
            }
        }
        require(observances.isNotEmpty())
        samplesMillis.map { sample ->
            observances.flatMap { o -> o.onsets.filter { it <= sample }.map { it to o.offsetTo } }
                .maxByOrNull { it.first }?.second
                ?: observances.minBy { it.onsets.first() }.offsetTo
        }
    }.getOrNull()

    private fun parseOffset(value: String?): Int? {
        val v = value?.trim() ?: return null
        val match = Regex("""^([+-])(\d{2})(\d{2})(\d{2})?$""").find(v) ?: return null
        val sign = if (match.groupValues[1] == "-") -1 else 1
        val (h, m) = match.groupValues[2].toInt() to match.groupValues[3].toInt()
        val s = match.groupValues[4].ifEmpty { "0" }.toInt()
        return sign * (h * 3600 + m * 60 + s)
    }

    private fun parseLocal(value: String): LocalDateTime? {
        val v = value.trim().removeSuffix("Z")
        return runCatching {
            if (v.length >= 15 && v[8] == 'T') {
                LocalDateTime(v.substring(0, 4).toInt(), v.substring(4, 6).toInt(), v.substring(6, 8).toInt(),
                    v.substring(9, 11).toInt(), v.substring(11, 13).toInt(), v.substring(13, 15).toInt())
            } else {
                LocalDateTime(LocalDate(v.substring(0, 4).toInt(), v.substring(4, 6).toInt(), v.substring(6, 8).toInt()), LocalTime(0, 0))
            }
        }.getOrNull()
    }

    fun unfold(text: String): List<String> {
        val result = mutableListOf<String>()
        text.removePrefix("﻿").replace("\r\n", "\n").replace('\r', '\n').split('\n').forEach { line ->
            if ((line.startsWith(' ') || line.startsWith('\t')) && result.isNotEmpty()) {
                result[result.lastIndex] += line.drop(1)
            } else if (line.isNotBlank()) {
                result += line
            }
        }
        return result
    }

    private fun LocalDate.plusDaysSafe(days: Int): LocalDate =
        LocalDate.fromEpochDays(toEpochDays() + days)
}
