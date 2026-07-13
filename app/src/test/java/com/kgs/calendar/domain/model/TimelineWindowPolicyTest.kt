package com.kgs.calendar.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineWindowPolicyTest {
    private val focusDate = LocalDate.of(2026, 7, 15) // Wednesday

    @Test
    fun weekAnchorHonorsEveryConfiguredWeekStart() {
        DayOfWeek.entries.forEach { firstDay ->
            val anchor = timelineAnchorDate(
                date = focusDate,
                viewMode = CalendarViewMode.ThreeDay,
                weekViewEnabled = true,
                firstDayOfWeek = firstDay,
            )

            assertEquals(firstDay, anchor.dayOfWeek)
            assertFalse(anchor.isAfter(focusDate))
            assertTrue(focusDate.isBefore(anchor.plusDays(WEEK_DAY_COUNT.toLong())))
        }
    }

    @Test
    fun multipleDaysKeepsTheFocusedDateAsItsAnchor() {
        assertEquals(
            focusDate,
            timelineAnchorDate(focusDate, CalendarViewMode.ThreeDay, false, DayOfWeek.MONDAY),
        )
    }

    @Test
    fun seamlessWeekKeepsItsSettledDayWhileFullWeekStaysAligned() {
        assertEquals(
            focusDate,
            timelineVisibleAnchor(
                focusDate,
                CalendarViewMode.ThreeDay,
                weekViewEnabled = true,
                fullWeekSwipeEnabled = false,
                firstDayOfWeek = DayOfWeek.MONDAY,
            ),
        )
        assertEquals(
            LocalDate.of(2026, 7, 13),
            timelineVisibleAnchor(
                focusDate,
                CalendarViewMode.ThreeDay,
                weekViewEnabled = true,
                fullWeekSwipeEnabled = true,
                firstDayOfWeek = DayOfWeek.MONDAY,
            ),
        )
    }

    @Test
    fun timelineDayCountUsesOneSevenOrConfiguredCount() {
        assertEquals(1, timelineDayCount(CalendarViewMode.Day, true, 3))
        assertEquals(7, timelineDayCount(CalendarViewMode.ThreeDay, true, 3))
        assertEquals(4, timelineDayCount(CalendarViewMode.ThreeDay, false, 4))
    }

    @Test
    fun weekVisibleRangeStartsOnPreferredWeekdayAndContainsSevenDays() {
        val range = visibleRangeFor(
            date = timelineAnchorDate(
                focusDate,
                CalendarViewMode.ThreeDay,
                weekViewEnabled = true,
                firstDayOfWeek = DayOfWeek.MONDAY,
            ),
            viewMode = CalendarViewMode.ThreeDay,
            multiDayCount = 3,
            weekViewEnabled = true,
        )

        assertEquals(LocalDate.of(2026, 7, 13), range.startDate)
        assertEquals(LocalDate.of(2026, 7, 20), range.endExclusiveDate)
    }

    @Test
    fun periodMovementUsesWeekOrConfiguredMultipleDayCount() {
        assertEquals(
            LocalDate.of(2026, 7, 20),
            moveCalendarPeriod(focusDate, CalendarViewMode.ThreeDay, 1, true, 3, DayOfWeek.MONDAY),
        )
        assertEquals(
            LocalDate.of(2026, 7, 19),
            moveCalendarPeriod(focusDate, CalendarViewMode.ThreeDay, 1, false, 4, DayOfWeek.MONDAY),
        )
    }
}
