package com.kgs.calendar.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

const val WEEK_DAY_COUNT = 7

fun LocalDate.startOfWeek(firstDayOfWeek: DayOfWeek): LocalDate =
    with(TemporalAdjusters.previousOrSame(firstDayOfWeek))

fun timelineDayCount(
    viewMode: CalendarViewMode,
    weekViewEnabled: Boolean,
    multiDayCount: Int,
): Int = when (viewMode) {
    CalendarViewMode.Day -> 1
    CalendarViewMode.ThreeDay -> if (weekViewEnabled) WEEK_DAY_COUNT else multiDayCount.coerceMultiDayCount()
    else -> 1
}

fun timelineAnchorDate(
    date: LocalDate,
    viewMode: CalendarViewMode,
    weekViewEnabled: Boolean,
    firstDayOfWeek: DayOfWeek,
): LocalDate = if (viewMode == CalendarViewMode.ThreeDay && weekViewEnabled) {
    date.startOfWeek(firstDayOfWeek)
} else {
    date
}

fun timelineEntryDate(
    date: LocalDate,
    viewMode: CalendarViewMode,
    weekViewEnabled: Boolean,
    firstDayOfWeek: DayOfWeek,
): LocalDate = timelineAnchorDate(date, viewMode, weekViewEnabled, firstDayOfWeek)

fun timelineVisibleAnchor(
    date: LocalDate,
    viewMode: CalendarViewMode,
    weekViewEnabled: Boolean,
    fullWeekSwipeEnabled: Boolean,
    firstDayOfWeek: DayOfWeek,
): LocalDate = if (fullWeekSwipeEnabled) {
    timelineAnchorDate(date, viewMode, weekViewEnabled, firstDayOfWeek)
} else {
    date
}

fun moveCalendarPeriod(
    date: LocalDate,
    viewMode: CalendarViewMode,
    delta: Long,
    weekViewEnabled: Boolean,
    multiDayCount: Int,
    firstDayOfWeek: DayOfWeek,
): LocalDate = when (viewMode) {
    CalendarViewMode.Month -> date.plusMonths(delta)
    CalendarViewMode.ThreeDay -> {
        if (weekViewEnabled) {
            date.startOfWeek(firstDayOfWeek).plusWeeks(delta)
        } else {
            date.plusDays(multiDayCount.coerceMultiDayCount().toLong() * delta)
        }
    }
    CalendarViewMode.Day -> date.plusDays(delta)
    CalendarViewMode.Agenda,
    CalendarViewMode.Tasks,
    -> date.plusWeeks(delta)
}
