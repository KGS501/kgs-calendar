package com.kgs.calendar.domain.model

import java.time.LocalDate
import java.time.ZoneId

data class CalendarRange(
    val startDate: LocalDate,
    val endExclusiveDate: LocalDate,
) {
    fun startMillis(zoneId: ZoneId = ZoneId.systemDefault()): Long =
        startDate.atStartOfDay(zoneId).toInstant().toEpochMilli()

    fun endMillis(zoneId: ZoneId = ZoneId.systemDefault()): Long =
        endExclusiveDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
}

const val MIN_MULTI_DAY_COUNT = 2
const val DEFAULT_MULTI_DAY_COUNT = 3
const val MAX_MULTI_DAY_COUNT = 7

fun Int.coerceMultiDayCount(): Int = coerceIn(MIN_MULTI_DAY_COUNT, MAX_MULTI_DAY_COUNT)

fun visibleRangeFor(
    date: LocalDate,
    viewMode: CalendarViewMode,
    multiDayCount: Int = DEFAULT_MULTI_DAY_COUNT,
): CalendarRange {
    return when (viewMode) {
        CalendarViewMode.Month -> {
            // The month view scrolls vertically as a continuous grid, so we preload a
            // generous buffer of months around the centre date. This means events/tasks
            // are already present when the user scrolls to a neighbouring month instead
            // of popping in after a reload.
            val monthStart = date.withDayOfMonth(1)
            CalendarRange(monthStart.minusMonths(2), monthStart.plusMonths(3))
        }
        CalendarViewMode.ThreeDay -> CalendarRange(date, date.plusDays(multiDayCount.coerceMultiDayCount().toLong()))
        // Load a generous window around the day even though only one is shown. The morphs
        // into/out of this view keep the *other* days visible while they animate (the 3-day
        // neighbours sliding out, and the whole surrounding month staying populated while a
        // tapped cell zooms up), so their events must already be in memory rather than
        // blinking out the instant the range would otherwise collapse to a single day.
        // NOTE: the timeline pager anchors on selectedDate (not on this range's start), so a
        // wide, asymmetric range here is safe.
        CalendarViewMode.Day -> CalendarRange(date.minusDays(31), date.plusDays(31))
        CalendarViewMode.Agenda -> CalendarRange(date.minusYears(5), date.plusYears(10))
        CalendarViewMode.Tasks -> CalendarRange(date.minusMonths(1), date.plusMonths(6))
    }
}
