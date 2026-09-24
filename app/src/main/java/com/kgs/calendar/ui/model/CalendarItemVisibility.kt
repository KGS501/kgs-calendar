package com.kgs.calendar.ui.model

import com.kgs.calendar.data.ical.RecurrenceOverrideCodec
import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.domain.event.endDateInclusive
import com.kgs.calendar.domain.event.isAllDayTopItemOn
import com.kgs.calendar.domain.event.isTimedMultiDay
import com.kgs.calendar.domain.model.CalendarOccurrenceId
import com.kgs.calendar.domain.task.isRecurring
import com.kgs.calendar.domain.time.toDate
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

internal fun EventEntity.visibleAgendaDates(): List<LocalDate> {
    val start = startsAtMillis.toDate()
    val end = endDateInclusive().coerceAtLeast(start)
    val dates = mutableListOf<LocalDate>()
    var date = start
    var guard = 0
    while (!date.isAfter(end) && guard < 370) {
        dates += date
        date = date.plusDays(1)
        guard++
    }
    return dates
}

internal data class AgendaEventDateSpan(
    val event: EventEntity,
    val startDate: LocalDate,
    val endDate: LocalDate = startDate,
)

internal fun agendaEventDateSpans(
    events: List<EventEntity>,
    tasks: List<TaskEntity>,
): List<AgendaEventDateSpan> {
    val taskDates = tasks
        .flatMap { it.visibleDates() }
        .toSet()
    val eventDates = events.map { event -> event to event.visibleAgendaDates() }
    val eventCountByDate = buildMap<LocalDate, Int> {
        eventDates.forEach { (_, dates) ->
            dates.forEach { date ->
                put(date, getOrDefault(date, 0) + 1)
            }
        }
    }

    return eventDates.flatMap { (event, dates) ->
        if (dates.size <= 1) {
            listOf(AgendaEventDateSpan(event, dates.firstOrNull() ?: event.startsAtMillis.toDate()))
        } else {
            val interruptionDates = dates.filterTo(mutableSetOf()) { date ->
                date in taskDates || eventCountByDate.getOrDefault(date, 0) > 1
            }
            event.toAgendaDateSpans(dates, interruptionDates)
        }
    }
}

private fun EventEntity.toAgendaDateSpans(
    dates: List<LocalDate>,
    interruptionDates: Set<LocalDate>,
): List<AgendaEventDateSpan> {
    val sortedDates = dates.sorted()
    if (sortedDates.isEmpty()) return emptyList()
    val results = mutableListOf<AgendaEventDateSpan>()
    var segmentStart: LocalDate? = null
    var previous: LocalDate? = null

    fun flushSegment(end: LocalDate) {
        val start = segmentStart ?: return
        if (!end.isBefore(start)) {
            results += AgendaEventDateSpan(this, start, end)
        }
        segmentStart = null
    }

    sortedDates.forEach { date ->
        val last = previous
        if (last != null && last.plusDays(1) != date) {
            flushSegment(last)
        }
        if (date in interruptionDates) {
            flushSegment(date.minusDays(1))
            results += AgendaEventDateSpan(this, date)
        } else if (segmentStart == null) {
            segmentStart = date
        }
        previous = date
    }
    previous?.let(::flushSegment)
    return results
}

internal fun TaskEntity.isFullDayTaskOn(day: LocalDate): Boolean {
    if (startAtMillis == null && dueAtMillis == null) return false
    if (startHasTime || dueHasTime) return false
    return day in visibleDates()
}

internal fun EventEntity.continuesAllDayTopItemAfter(date: LocalDate): Boolean =
    isAllDayTopItemOn(date.plusDays(1))

internal fun EventEntity.allDayTopStartDate(): LocalDate? =
    if (allDay) {
        startsAtMillis.toDate()
    } else if (isTimedMultiDay()) {
        startsAtMillis.toDate().plusDays(1).takeIf { !it.isAfter(endDateInclusive().minusDays(1)) }
    } else {
        null
    }

internal fun EventEntity.allDayTopEndDate(): LocalDate? =
    if (allDay) {
        endDateInclusive()
    } else if (isTimedMultiDay()) {
        endDateInclusive().minusDays(1).takeIf { !it.isBefore(startsAtMillis.toDate().plusDays(1)) }
    } else {
        null
    }

internal fun TaskEntity.taskDate(): LocalDate? {
    val millis = dueAtMillis ?: startAtMillis ?: return null
    return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
}

internal fun TaskEntity.agendaSortMillis(): Long? =
    startAtMillis ?: dueAtMillis

internal fun TaskEntity.occurrenceStartForEdit(): Long =
    RecurrenceOverrideCodec.decodeTasks(recurrenceOverridesJson)
        .firstOrNull { it.matchesOccurrence(this) }
        ?.recurrenceIdMillis
        ?: startAtMillis
        ?: dueAtMillis
        ?: System.currentTimeMillis()

/** The occurrence of a recurring task, or null for a single task, which has no stable start. */
internal fun TaskEntity.occurrenceIdOrNull(): CalendarOccurrenceId.Task? =
    if (isRecurring) CalendarOccurrenceId.Task(resourceHref, occurrenceStartForEdit()) else null

internal fun EventEntity.occurrenceStartForEdit(): Long =
    RecurrenceOverrideCodec.decodeEvents(recurrenceOverridesJson)
        .firstOrNull { it.matchesOccurrence(this) }
        ?.recurrenceIdMillis
        ?: startsAtMillis

internal fun TaskEntity.visibleDates(): List<LocalDate> {
    val start = startAtMillis?.toDate()
    val due = dueAtMillis?.toDate()
    val first = start ?: due ?: return emptyList()
    val last = due ?: start ?: first
    if (last.isBefore(first)) return listOf(first)
    val dates = mutableListOf<LocalDate>()
    var date = first
    var guard = 0
    while (!date.isAfter(last) && guard < 370) {
        dates += date
        date = date.plusDays(1)
        guard++
    }
    return dates
}

internal fun Long.toTime(): LocalTime =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalTime()
