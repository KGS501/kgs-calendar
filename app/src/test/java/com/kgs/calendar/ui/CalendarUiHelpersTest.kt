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
}
