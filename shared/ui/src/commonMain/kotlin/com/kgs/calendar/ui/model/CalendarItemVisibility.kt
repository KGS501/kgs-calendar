package com.kgs.calendar.ui.model

import com.kgs.calendar.time.*
import kotlinx.datetime.*
import com.kgs.calendar.domain.task.isOpen
import com.kgs.calendar.domain.task.isTimedMultiDayMiddleOn
import com.kgs.calendar.data.ical.RecurrenceOverrideCodec
import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.domain.event.endDateInclusive
import com.kgs.calendar.domain.event.isAllDayTopItemOn
import com.kgs.calendar.domain.event.isTimedMultiDay
import com.kgs.calendar.domain.task.occurrenceRecurrenceIdMillis
import com.kgs.calendar.domain.time.toDate
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import com.kgs.calendar.time.ZoneId

fun EventEntity.visibleAgendaDates(): List<LocalDate> {
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

data class AgendaEventDateSpan(
    val event: EventEntity,
    val startDate: LocalDate,
    val endDate: LocalDate = startDate,
)

fun agendaEventDateSpans(
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
                put(date, (this[date] ?: 0) + 1)
            }
        }
    }

    return eventDates.flatMap { (event, dates) ->
        if (dates.size <= 1) {
            listOf(AgendaEventDateSpan(event, dates.firstOrNull() ?: event.startsAtMillis.toDate()))
        } else {
            val interruptionDates = dates.filterTo(mutableSetOf()) { date ->
                date in taskDates || (eventCountByDate[date] ?: 0) > 1
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

fun TaskEntity.isFullDayTaskOn(day: LocalDate): Boolean {
    if (isTimedMultiDayMiddleOn(day)) return true
    if (startAtMillis == null && dueAtMillis == null) return false
    if (startHasTime || dueHasTime) return false
    return day in visibleDates()
}

fun EventEntity.continuesAllDayTopItemAfter(date: LocalDate): Boolean =
    isAllDayTopItemOn(date.plusDays(1))

/**
 * A timed event this short has no full middle day, even over a daylight saving change. Most events
 * are, and this is asked for every event on every layout, so they skip the date conversions.
 */
private fun EventEntity.isTooShortForAllDayTop(): Boolean = !allDay && endsAtMillis - startsAtMillis < MIN_ALL_DAY_TOP_MILLIS

private const val MIN_ALL_DAY_TOP_MILLIS = 22L * 60L * 60L * 1000L

fun EventEntity.allDayTopStartDate(): LocalDate? =
    if (isTooShortForAllDayTop()) {
        null
    } else if (allDay) {
        startsAtMillis.toDate()
    } else if (isTimedMultiDay()) {
        startsAtMillis.toDate().plusDays(1).takeIf { !it.isAfter(endDateInclusive().minusDays(1)) }
    } else {
        null
    }

fun EventEntity.allDayTopEndDate(): LocalDate? =
    if (isTooShortForAllDayTop()) {
        null
    } else if (allDay) {
        endDateInclusive()
    } else if (isTimedMultiDay()) {
        endDateInclusive().minusDays(1).takeIf { !it.isBefore(startsAtMillis.toDate().plusDays(1)) }
    } else {
        null
    }

fun TaskEntity.taskDate(): LocalDate? {
    val millis = dueAtMillis ?: startAtMillis ?: return null
    return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
}

/** An open task whose start lies before [today] and whose due day is [today] or later. */
fun TaskEntity.isOngoingOn(today: LocalDate): Boolean {
    if (!isOpen()) return false
    val startMillis = startAtMillis ?: return false
    val start = startMillis.toDate()
    // A due time is exclusive (a task due at midnight ends the day before); a due date is not.
    val due = dueAtMillis?.let { if (dueHasTime) (it - 1).coerceAtLeast(startMillis).toDate() else it.toDate() } ?: return false
    return start.isBefore(today) && !due.isBefore(today)
}

fun TaskEntity.agendaSortMillis(): Long? =
    startAtMillis ?: dueAtMillis

fun TaskEntity.occurrenceStartForEdit(): Long =
    occurrenceRecurrenceIdMillis() ?: nowMillis()

fun EventEntity.occurrenceStartForEdit(): Long =
    RecurrenceOverrideCodec.decodeEvents(recurrenceOverridesJson)
        .firstOrNull { it.matchesOccurrence(this) }
        ?.recurrenceIdMillis
        ?: startsAtMillis

fun TaskEntity.visibleDates(): List<LocalDate> {
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

fun Long.toTime(): LocalTime =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalTime()
