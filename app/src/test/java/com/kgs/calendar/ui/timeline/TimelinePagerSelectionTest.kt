package com.kgs.calendar.ui.timeline

import com.kgs.calendar.domain.model.CalendarViewMode
import com.kgs.calendar.domain.model.timelineVisibleAnchor
import com.kgs.calendar.ui.calendar.toDayPage
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimelinePagerSelectionTest {
    @Test
    fun weekViewSettleOnTheAnchorOfAMonthPanelSelectionKeepsTheChosenDate() {
        // Regression for #28: choosing October in the month panel selects Thursday 1 October; the
        // week pager settles on Monday 28 September. That settle must not select the Monday.
        val chosen = LocalDate.of(2026, 10, 1)
        val anchorPage = weekAnchor(chosen).toDayPage()

        assertEquals(LocalDate.of(2026, 9, 28), weekAnchor(chosen))
        assertNull(
            timelineSettledPageSelection(
                settledPage = anchorPage,
                selectedAnchorPage = anchorPage,
                selectedDate = chosen,
            ),
        )
    }

    @Test
    fun weekViewSettleOnTheAnchorOfASundaySelectionKeepsTheChosenDate() {
        val chosen = LocalDate.of(2026, 11, 1)
        val anchorPage = weekAnchor(chosen).toDayPage()

        assertNull(
            timelineSettledPageSelection(
                settledPage = anchorPage,
                selectedAnchorPage = anchorPage,
                selectedDate = chosen,
            ),
        )
    }

    @Test
    fun userSwipeToAnotherWeekSelectsThatWeeksFirstDay() {
        val selected = LocalDate.of(2026, 10, 1)
        val nextWeek = LocalDate.of(2026, 10, 5)

        assertEquals(
            nextWeek,
            timelineSettledPageSelection(
                settledPage = nextWeek.toDayPage(),
                selectedAnchorPage = weekAnchor(selected).toDayPage(),
                selectedDate = selected,
            ),
        )
    }

    @Test
    fun daySwipeSelectsTheSettledDay() {
        val selected = LocalDate.of(2026, 9, 30)
        val next = LocalDate.of(2026, 10, 1)

        assertEquals(
            next,
            timelineSettledPageSelection(
                settledPage = next.toDayPage(),
                selectedAnchorPage = selected.toDayPage(),
                selectedDate = selected,
            ),
        )
        assertNull(
            timelineSettledPageSelection(
                settledPage = selected.toDayPage(),
                selectedAnchorPage = selected.toDayPage(),
                selectedDate = selected,
            ),
        )
    }

    private fun weekAnchor(date: LocalDate): LocalDate = timelineVisibleAnchor(
        date = date,
        viewMode = CalendarViewMode.ThreeDay,
        weekViewEnabled = true,
        fullWeekSwipeEnabled = true,
        firstDayOfWeek = DayOfWeek.MONDAY,
    )
}
