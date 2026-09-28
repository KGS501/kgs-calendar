package com.kgs.calendar.ui

import com.kgs.calendar.navigation.SharedEventDraft
import com.kgs.calendar.ui.editor.EditorScheduleState
import com.kgs.calendar.ui.shell.newEventSchedule
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Calendar intents encode all-day dates at UTC midnight and use an exclusive end date. */
internal fun SharedEventDraft.editorSchedule(
    fallbackDate: LocalDate,
    fallbackTime: LocalTime,
    defaultDurationMinutes: Int,
    zone: ZoneId = ZoneId.systemDefault(),
): EditorScheduleState {
    val fallback = newEventSchedule(fallbackDate, fallbackTime, defaultDurationMinutes)
    val begin = beginTimeMillis ?: return fallback
    val eventZone = if (allDay) ZoneOffset.UTC else zone
    val start = Instant.ofEpochMilli(begin).atZone(eventZone)
    val end = endTimeMillis?.takeIf { it > begin }?.let { Instant.ofEpochMilli(it).atZone(eventZone) }
        ?: if (allDay) start.plusDays(1) else start.plusMinutes(defaultDurationMinutes.toLong())
    val endDate = if (allDay) end.toLocalDate().minusDays(1).coerceAtLeast(start.toLocalDate()) else end.toLocalDate()
    val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
    return fallback.copy(
        startDateText = start.toLocalDate().toString(),
        endDateText = endDate.toString(),
        startTimeText = start.format(timeFormat),
        endTimeText = end.format(timeFormat),
        hasStartTime = !allDay,
        hasEndTime = !allDay,
        allDay = allDay,
    ).recalculatePreview()
}
