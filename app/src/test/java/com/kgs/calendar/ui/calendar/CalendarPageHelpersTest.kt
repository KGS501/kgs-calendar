package com.kgs.calendar.ui.calendar

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class CalendarPageHelpersTest {
    @Test
    fun dayPageRoundTripsBaseAndFutureDate() {
        val date = LocalDate.of(2026, 7, 7)

        assertEquals(date, date.toDayPage().toDayDate())
        assertEquals(LocalDate.of(2000, 1, 1), LocalDate.of(1999, 12, 31).toDayPage().toDayDate())
    }

    @Test
    fun monthStripPageRoundTripsAndClampsBeforeRange() {
        val month = YearMonth.of(2026, 7)

        assertEquals(month, month.toMonthPage().toMonth())
        assertEquals(YearMonth.of(1000, 1), YearMonth.of(1000, 1).toMonthPage().toMonth())
    }

    @Test
    fun monthGridUsesFirstDayOfWeek() {
        assertEquals(6, YearMonth.of(2026, 3).monthGridRowCount(DayOfWeek.MONDAY))
        assertEquals(5, YearMonth.of(2026, 3).monthGridRowCount(DayOfWeek.SUNDAY))
        assertEquals(listOf("S", "M", "D", "M", "D", "F", "S"), weekHeaderLabels(DayOfWeek.SUNDAY))
    }
}
