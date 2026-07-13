package com.kgs.calendar.ui

import com.kgs.calendar.domain.model.CalendarViewMode
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarUiHelpersTest {
    @Test
    fun weekViewFabUsesTodayWhenTodayIsInsideAlignedWeek() {
        val state = CalendarUiState(
            selectedView = CalendarViewMode.ThreeDay,
            selectedDate = LocalDate.of(2026, 7, 15),
            weekViewEnabled = true,
            firstDayOfWeek = DayOfWeek.MONDAY,
        )

        assertEquals(
            LocalDate.of(2026, 7, 19),
            state.defaultFabCreationDate(LocalDate.of(2026, 7, 19)),
        )
        assertEquals(
            LocalDate.of(2026, 7, 13),
            state.defaultFabCreationDate(LocalDate.of(2026, 7, 20)),
        )
    }

    @Test
    fun seamlessWeekFabUsesActualSettledDayWindow() {
        val state = CalendarUiState(
            selectedView = CalendarViewMode.ThreeDay,
            selectedDate = LocalDate.of(2026, 7, 15),
            weekViewEnabled = true,
            fullWeekSwipeEnabled = false,
            firstDayOfWeek = DayOfWeek.MONDAY,
        )

        assertEquals(
            LocalDate.of(2026, 7, 21),
            state.defaultFabCreationDate(LocalDate.of(2026, 7, 21)),
        )
        assertEquals(
            LocalDate.of(2026, 7, 15),
            state.defaultFabCreationDate(LocalDate.of(2026, 7, 22)),
        )
    }

    @Test
    fun multipleDaysFabKeepsConfiguredVisibleWindow() {
        val state = CalendarUiState(
            selectedView = CalendarViewMode.ThreeDay,
            selectedDate = LocalDate.of(2026, 7, 15),
            multiDayCount = 4,
            weekViewEnabled = false,
        )

        assertEquals(
            LocalDate.of(2026, 7, 18),
            state.defaultFabCreationDate(LocalDate.of(2026, 7, 18)),
        )
        assertEquals(
            LocalDate.of(2026, 7, 15),
            state.defaultFabCreationDate(LocalDate.of(2026, 7, 19)),
        )
    }
}
