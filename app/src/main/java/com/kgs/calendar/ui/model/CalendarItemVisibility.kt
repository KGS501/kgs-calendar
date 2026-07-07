package com.kgs.calendar.ui.model

import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.local.entity.TaskEntity
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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

internal fun TaskEntity.isFullDayTaskOn(day: LocalDate): Boolean {
    if (startAtMillis == null && dueAtMillis == null) return false
    if (startHasTime || dueHasTime) return false
    return day in visibleDates()
}

internal fun EventEntity.occursOn(date: LocalDate): Boolean {
    val start = startsAtMillis.toDate()
    val end = endDateInclusive()
    return !date.isBefore(start) && !date.isAfter(end)
}

internal fun EventEntity.endDateInclusive(): LocalDate =
    Instant.ofEpochMilli((endsAtMillis - 1).coerceAtLeast(startsAtMillis)).atZone(ZoneId.systemDefault()).toLocalDate()

internal fun EventEntity.isTimedMultiDay(): Boolean =
    !allDay && startsAtMillis.toDate().isBefore(endDateInclusive())

internal fun EventEntity.isTimedMultiDayMiddleOn(date: LocalDate): Boolean {
    if (!isTimedMultiDay()) return false
    val start = startsAtMillis.toDate()
    val end = endDateInclusive()
    return date.isAfter(start) && date.isBefore(end)
}

internal fun EventEntity.isAllDayTopItemOn(date: LocalDate): Boolean =
    if (allDay) occursOn(date) else isTimedMultiDayMiddleOn(date)

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
    startAtMillis ?: dueAtMillis ?: System.currentTimeMillis()

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

internal fun Long.toDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

internal fun Long.toTime(): LocalTime =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalTime()

internal fun Long.toTimeText(): String =
    toTime().format(DateTimeFormatter.ofPattern("HH:mm"))
