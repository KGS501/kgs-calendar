package com.kgs.calendar.ui

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.WeekFields

internal data class VisibleTimelineDay(
    val date: LocalDate,
    val leftPx: Float,
)

internal data class TimelineCalendarWeekPlacement(
    val weekStart: LocalDate,
    val weekNumber: Int,
    val leftPx: Float,
    val pinned: Boolean,
    val visualAlpha: Float = 1f,
)

internal enum class TimelineCalendarWeekLabelMode {
    Full,
    Compact,
}

internal fun timelineCalendarWeekLabelMode(
    dayWidthPx: Float,
    minimumFullLabelWidthPx: Float,
): TimelineCalendarWeekLabelMode = if (dayWidthPx >= minimumFullLabelWidthPx) {
    TimelineCalendarWeekLabelMode.Full
} else {
    TimelineCalendarWeekLabelMode.Compact
}

internal fun LocalDate.calendarWeekNumber(firstDayOfWeek: DayOfWeek): Int =
    get(WeekFields.of(firstDayOfWeek, CALENDAR_WEEK_MINIMAL_DAYS).weekOfWeekBasedYear())

internal fun timelineCalendarWeekPlacements(
    visibleDays: List<VisibleTimelineDay>,
    dayWidthPx: Float,
    viewportWidthPx: Float,
    firstDayOfWeek: DayOfWeek,
    labelWidthPx: Float = dayWidthPx,
): List<TimelineCalendarWeekPlacement> {
    if (dayWidthPx <= 0f || viewportWidthPx <= 0f || labelWidthPx <= 0f) return emptyList()

    val onScreenDays = visibleDays
        .asSequence()
        .filter { day -> day.leftPx < viewportWidthPx && day.leftPx + dayWidthPx > 0f }
        .distinctBy(VisibleTimelineDay::date)
        .sortedBy(VisibleTimelineDay::leftPx)
        .toList()
    if (onScreenDays.isEmpty()) return emptyList()

    val activeWeekStart = onScreenDays.first().date.startOfCalendarWeek(firstDayOfWeek)
    val activeNaturalLeft = onScreenDays
        .firstOrNull { day -> day.date == activeWeekStart }
        ?.leftPx
    val laterWeekPlacements = onScreenDays
        .asSequence()
        .filter { day -> day.date.dayOfWeek == firstDayOfWeek && day.date > activeWeekStart }
        .map { day ->
            TimelineCalendarWeekPlacement(
                weekStart = day.date,
                weekNumber = day.date.calendarWeekNumber(firstDayOfWeek),
                leftPx = day.leftPx,
                pinned = false,
            )
        }
        .toList()
    val activePinned = activeNaturalLeft == null || activeNaturalLeft < 0f
    val activeLeft = if (activePinned) {
        minOf(
            0f,
            laterWeekPlacements.firstOrNull()?.let { incoming -> incoming.leftPx - labelWidthPx } ?: 0f,
        )
    } else {
        activeNaturalLeft
    }
    val activePlacement = TimelineCalendarWeekPlacement(
        weekStart = activeWeekStart,
        weekNumber = activeWeekStart.calendarWeekNumber(firstDayOfWeek),
        leftPx = activeLeft,
        pinned = activePinned,
        visualAlpha = ((activeLeft + labelWidthPx) / labelWidthPx).coerceIn(0f, 1f),
    )

    return listOf(activePlacement) + laterWeekPlacements
}

internal fun LocalDate.startOfCalendarWeek(firstDayOfWeek: DayOfWeek): LocalDate {
    val daysSinceWeekStart = (dayOfWeek.value - firstDayOfWeek.value + DAYS_PER_WEEK) % DAYS_PER_WEEK
    return minusDays(daysSinceWeekStart.toLong())
}

private const val CALENDAR_WEEK_MINIMAL_DAYS = 4
private const val DAYS_PER_WEEK = 7
