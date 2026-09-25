package com.kgs.calendar.widget.data

import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.domain.event.isTimedMultiDayMiddleOn
import com.kgs.calendar.domain.task.effectiveStatus
import com.kgs.calendar.domain.time.toDate
import com.kgs.calendar.widget.WIDGET_DAY_DEFAULT_TASK_DURATION_MILLIS
import com.kgs.calendar.widget.WIDGET_DAY_END_HOUR
import com.kgs.calendar.widget.WIDGET_DAY_START_HOUR
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal fun TaskEntity.widgetTaskDate(zoneId: ZoneId = ZoneId.systemDefault()): LocalDate? =
    (startAtMillis ?: dueAtMillis)?.let { Instant.ofEpochMilli(it).atZone(zoneId).toLocalDate() }

internal fun TaskEntity.widgetStatusGlyph(): String = when (effectiveStatus()) {
    "COMPLETED" -> "\u2713"
    "IN-PROCESS" -> "\u25D0"
    "CANCELLED" -> "\u00D7"
    else -> "\u25CB"
}

internal fun TaskEntity.visibleDates(start: LocalDate, endExclusive: LocalDate, zoneId: ZoneId = ZoneId.systemDefault()): List<LocalDate> {
    val first = startAtMillis?.toDate(zoneId) ?: dueAtMillis?.toDate(zoneId) ?: return emptyList()
    val last = (dueAtMillis?.toDate(zoneId) ?: startAtMillis?.toDate(zoneId) ?: first).coerceAtLeast(first)
    val from = if (first.isBefore(start)) start else first
    val to = if (!last.isBefore(endExclusive)) endExclusive.minusDays(1) else last
    if (to.isBefore(from)) return emptyList()
    val days = ChronoUnit.DAYS.between(from, to).toInt()
    return (0..days).map { from.plusDays(it.toLong()) }
}

internal fun EventEntity.widgetTimedPlacementOn(day: LocalDate, zoneId: ZoneId = ZoneId.systemDefault()): Pair<Int, Int>? {
    if (allDay || isTimedMultiDayMiddleOn(day, zoneId)) return null
    val visibleStart = day.atTime(WIDGET_DAY_START_HOUR, 0).atZone(zoneId).toInstant().toEpochMilli()
    val visibleEnd = day.atTime(WIDGET_DAY_END_HOUR, 0).plusHours(1).atZone(zoneId).toInstant().toEpochMilli()
    val overlapStart = max(startsAtMillis, visibleStart)
    val overlapEnd = min(endsAtMillis, visibleEnd)
    if (overlapEnd <= overlapStart) return null
    val startMinute = ((overlapStart - visibleStart) / 60_000.0).roundToInt().coerceIn(0, 24 * 60 - 1)
    val duration = max(1, ((overlapEnd - overlapStart) / 60_000.0).roundToInt())
    return startMinute to (startMinute + duration).coerceIn(startMinute + 1, 24 * 60)
}

internal fun TaskEntity.widgetTimedPlacementOn(day: LocalDate, zoneId: ZoneId = ZoneId.systemDefault()): Pair<Int, Int>? {
    val startTimed = startAtMillis?.takeIf { startHasTime }
    val dueTimed = dueAtMillis?.takeIf { dueHasTime }
    if (startTimed == null && dueTimed == null) return null
    val start = startTimed ?: (dueTimed!! - WIDGET_DAY_DEFAULT_TASK_DURATION_MILLIS)
    val end = when {
        startTimed != null && dueTimed != null && dueTimed > startTimed -> dueTimed
        startTimed != null -> startTimed + WIDGET_DAY_DEFAULT_TASK_DURATION_MILLIS
        else -> dueTimed!!
    }
    val visibleStart = day.atTime(WIDGET_DAY_START_HOUR, 0).atZone(zoneId).toInstant().toEpochMilli()
    val visibleEnd = day.atTime(WIDGET_DAY_END_HOUR, 0).plusHours(1).atZone(zoneId).toInstant().toEpochMilli()
    val overlapStart = max(start, visibleStart)
    val overlapEnd = min(max(end, start + WIDGET_DAY_DEFAULT_TASK_DURATION_MILLIS), visibleEnd)
    if (overlapEnd <= overlapStart) return null
    val startMinute = ((overlapStart - visibleStart) / 60_000.0).roundToInt().coerceIn(0, 24 * 60 - 1)
    val duration = max(1, ((overlapEnd - overlapStart) / 60_000.0).roundToInt())
    return startMinute to (startMinute + duration).coerceIn(startMinute + 1, 24 * 60)
}

internal fun TaskEntity.isWidgetFullDayTaskOn(day: LocalDate, zoneId: ZoneId = ZoneId.systemDefault()): Boolean {
    if (startAtMillis == null && dueAtMillis == null) return false
    if ((startAtMillis != null && startHasTime) || (dueAtMillis != null && dueHasTime)) return false
    return day in visibleDates(day, day.plusDays(1), zoneId)
}

internal fun Pair<Int, Int>.timeRangeText(): String =
    "${first.minuteOfDayText()}-${second.minuteOfDayText()}"

internal fun Int.minuteOfDayText(): String {
    val bounded = coerceIn(0, 24 * 60)
    val hour = (bounded / 60).coerceAtMost(24)
    val minute = bounded % 60
    return "%02d:%02d".format(hour, minute)
}
