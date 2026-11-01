package com.kgs.calendar.ui.time

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class DayMonthFormatTest {
    private val day = LocalDate.of(2026, 10, 23)

    @Test
    fun englishPutsTheMonthFirst() {
        assertEquals("Oct 23", day.format(dayMonthFormatter(Locale.ENGLISH)))
        assertEquals("Fri, Oct 23, 2026", day.format(dayMonthFormatter(Locale.ENGLISH, weekday = "EEE", year = true)))
        assertEquals("Friday, October 23", day.format(dayMonthFormatter(Locale.ENGLISH, month = "MMMM", weekday = "EEEE")))
        assertEquals("Fri 23", day.format(weekdayDayFormatter(Locale.ENGLISH)))
    }

    @Test
    fun germanPutsTheDayFirst() {
        assertEquals("23. Okt.", day.format(dayMonthFormatter(Locale.GERMAN)))
        assertEquals("Freitag, 23. Oktober", day.format(dayMonthFormatter(Locale.GERMAN, month = "MMMM", weekday = "EEEE")))
        assertEquals("23. Okt. 2026", day.format(dayMonthFormatter(Locale.GERMAN, year = true)))
    }
}
