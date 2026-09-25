package com.kgs.calendar.ui.shell

import com.kgs.calendar.data.local.entity.CollectionEntity
import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.domain.source.isReadOnlyCollection
import com.kgs.calendar.domain.time.toDate
import com.kgs.calendar.ui.DraftMinDurationMinutes
import com.kgs.calendar.ui.EditorTransferDraft
import com.kgs.calendar.ui.calendar.DayEndHour
import com.kgs.calendar.ui.calendar.DayStartHour
import com.kgs.calendar.ui.defaultDraftEnd
import com.kgs.calendar.ui.editor.EditorSchedulePreview
import com.kgs.calendar.ui.editor.EditorScheduleState
import com.kgs.calendar.ui.minuteOfDay
import com.kgs.calendar.ui.model.toTime
import com.kgs.calendar.ui.nextDraftStart
import com.kgs.calendar.ui.snapDraftMinute
import com.kgs.calendar.ui.sortedWithDefaultFirst
import com.kgs.calendar.ui.toDraftLocalTime
import java.time.LocalDate
import java.time.LocalTime

internal fun editorScheduleState(
    date: LocalDate,
    start: LocalTime,
    end: LocalTime,
    hasStartDate: Boolean = true,
    hasEndDate: Boolean = true,
    hasStartTime: Boolean = true,
    hasEndTime: Boolean = true,
    allDay: Boolean = false,
    endDate: LocalDate = date,
): EditorScheduleState = EditorScheduleState(
    startDateText = date.toString(),
    endDateText = endDate.toString(),
    startTimeText = start.toString().take(5),
    endTimeText = end.toString().take(5),
    hasStartDate = hasStartDate,
    hasEndDate = hasEndDate,
    hasStartTime = hasStartTime && !allDay,
    hasEndTime = hasEndTime && !allDay,
    allDay = allDay,
    lastValidPreview = null,
).recalculatePreview()

internal fun initialEditorSchedule(today: LocalDate): EditorScheduleState =
    EditorScheduleState.fromPreview(
        EditorSchedulePreview(
            date = today,
            start = LocalTime.of(15, 0),
            end = LocalTime.of(16, 0),
        ),
    )

internal fun EventEntity.editorSchedule(): EditorScheduleState = editorScheduleState(
    date = startsAtMillis.toDate(),
    endDate = if (allDay) (endsAtMillis - 1).toDate() else endsAtMillis.toDate(),
    start = if (allDay) LocalTime.MIDNIGHT else startsAtMillis.toTime(),
    end = if (allDay) LocalTime.of(23, 59) else endsAtMillis.toTime(),
    hasStartTime = !allDay,
    hasEndTime = !allDay,
    allDay = allDay,
)

internal fun TaskEntity.editorSchedule(today: LocalDate): EditorScheduleState {
    val startDate = startAtMillis?.toDate()
    val endDate = dueAtMillis?.toDate()
    val fallbackDate = startDate ?: endDate ?: today
    val start = startAtMillis?.toTime() ?: dueAtMillis?.toTime()?.minusMinutes(30) ?: LocalTime.of(15, 0)
    val end = dueAtMillis?.toTime() ?: start.defaultDraftEnd()
    val hasStartTime = startAtMillis != null && startHasTime
    val hasEndTime = dueAtMillis != null && dueHasTime
    return editorScheduleState(
        date = startDate ?: fallbackDate,
        endDate = endDate ?: fallbackDate,
        start = start,
        end = end,
        hasStartDate = startDate != null,
        hasEndDate = endDate != null,
        hasStartTime = hasStartTime,
        hasEndTime = hasEndTime,
        allDay = (startDate != null || endDate != null) && !hasStartTime && !hasEndTime,
    )
}

/** The schedule a switched editor starts with, taken from the draft it was switched from. */
internal fun EditorTransferDraft.transferredSchedule(
    current: EditorScheduleState,
    today: LocalDate,
): EditorScheduleState = schedule ?: editorScheduleState(
    date = date ?: current.lastValidPreview?.date ?: today,
    endDate = endDate ?: date ?: current.lastValidPreview?.date ?: today,
    start = startTime ?: current.lastValidPreview?.start ?: LocalTime.of(15, 0),
    end = endTime ?: current.lastValidPreview?.end ?: LocalTime.of(16, 0),
    hasStartDate = date != null,
    hasEndDate = endDate != null,
    hasStartTime = startTime != null,
    hasEndTime = endTime != null,
    allDay = allDay == true,
)

internal fun newEventSchedule(
    date: LocalDate,
    now: LocalTime,
    defaultEventDurationMinutes: Int,
): EditorScheduleState {
    val start = now.nextDraftStart()
    return editorScheduleState(
        date = date,
        start = start,
        end = start.defaultDraftEnd(defaultEventDurationMinutes),
    )
}

internal fun newTaskSchedule(
    date: LocalDate,
    now: LocalTime,
    scheduledForDay: Boolean,
    useTaskDefaults: Boolean,
    defaultTaskHasDate: Boolean,
    defaultTaskHasTime: Boolean,
    defaultEventDurationMinutes: Int,
): EditorScheduleState {
    val start = now.nextDraftStart()
    val hasDate = scheduledForDay || (useTaskDefaults && defaultTaskHasDate)
    val allDay = scheduledForDay || (useTaskDefaults && defaultTaskHasDate && !defaultTaskHasTime)
    val usesTime = !scheduledForDay && useTaskDefaults && defaultTaskHasTime
    return editorScheduleState(
        date = date,
        start = start,
        end = start.defaultDraftEnd(defaultEventDurationMinutes),
        hasStartDate = hasDate,
        hasEndDate = usesTime,
        hasStartTime = usesTime,
        hasEndTime = usesTime,
        allDay = allDay,
    )
}

internal fun newSubtaskSchedule(date: LocalDate, now: LocalTime): EditorScheduleState {
    val start = now.nextDraftStart()
    return editorScheduleState(
        date = date,
        start = start,
        end = start.defaultDraftEnd(),
        hasStartDate = false,
        hasEndDate = false,
        hasStartTime = false,
        hasEndTime = false,
    )
}

/** A default-length draft centred on the tapped timeline slot, kept inside the visible day. */
internal fun timelineSlotDraftPreview(
    date: LocalDate,
    start: LocalTime,
    defaultEventDurationMinutes: Int,
): EditorSchedulePreview {
    val duration = defaultEventDurationMinutes.coerceIn(DraftMinDurationMinutes, 24 * 60 - 1)
    val minStartMinute = DayStartHour * 60
    val maxStartMinute = ((DayEndHour + 1) * 60 - duration).coerceAtLeast(minStartMinute)
    val centeredStartMinute = (start.minuteOfDay() - duration / 2)
        .snapDraftMinute()
        .coerceIn(minStartMinute, maxStartMinute)
    return EditorSchedulePreview(
        date = date,
        start = centeredStartMinute.toDraftLocalTime(),
        end = (centeredStartMinute + duration)
            .coerceAtMost((DayEndHour + 1) * 60 - 1)
            .toDraftLocalTime(),
    )
}

internal fun allDaySlotDraftPreview(date: LocalDate): EditorSchedulePreview =
    EditorSchedulePreview(
        date = date,
        start = LocalTime.MIDNIGHT,
        end = LocalTime.of(23, 59),
        allDay = true,
    )

internal fun List<CollectionEntity>.eventDraftColor(defaultEventCollectionHref: String?, fallback: Int): Int =
    filter { it.supportsEvents && it.isEnabled && !it.isReadOnlyCollection() }
        .sortedWithDefaultFirst(defaultEventCollectionHref)
        .firstOrNull()
        ?.color
        ?: fallback

internal fun List<CollectionEntity>.taskDraftColor(defaultTaskCollectionHref: String?, fallback: Int): Int =
    filter { it.supportsTasks && it.isEnabled && !it.isReadOnlyCollection() }
        .sortedWithDefaultFirst(defaultTaskCollectionHref)
        .firstOrNull()
        ?.color
        ?: fallback
