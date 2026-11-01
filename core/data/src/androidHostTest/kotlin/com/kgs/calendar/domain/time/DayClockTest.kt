package com.kgs.calendar.domain.time

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class DayClockTest {
    private val berlin = ZoneId.of("Europe/Berlin")

    @Test
    fun followsTheClockOnTheDayTheClocksGoBack() {
        val day = LocalDate.of(2026, 10, 25)

        assertEquals(10 * 60, millis(day, 10, 0).clockMinuteOn(day, berlin))
        assertEquals(23 * 60 + 59, millis(day, 23, 59).clockMinuteOn(day, berlin))
        assertEquals(0, millis(day.minusDays(1), 22, 0).clockMinuteOn(day, berlin))
        assertEquals(MINUTES_PER_DAY, millis(day.plusDays(1), 0, 0).clockMinuteOn(day, berlin))
    }

    @Test
    fun followsTheClockOnTheDayTheClocksGoForward() {
        val day = LocalDate.of(2026, 3, 29)

        assertEquals(10 * 60 to 11 * 60, clockMinutesOn(day, millis(day, 10, 0), millis(day, 11, 0), berlin))
        // 01:30 to 03:30 is one real hour, but covers two hour rows.
        assertEquals(90 to 210, clockMinutesOn(day, millis(day, 1, 30), millis(day, 3, 30), berlin))
    }

    @Test
    fun aSpanInTheRepeatedHourKeepsItsLength() {
        val day = LocalDate.of(2026, 10, 25)
        val start = ZonedDateTime.of(day, LocalTime.of(2, 40), berlin).withEarlierOffsetAtOverlap()
        val end = ZonedDateTime.of(day, LocalTime.of(2, 10), berlin).withLaterOffsetAtOverlap()

        assertEquals(160 to 190, clockMinutesOn(day, start.toInstant().toEpochMilli(), end.toInstant().toEpochMilli(), berlin))
    }

    private fun millis(day: LocalDate, hour: Int, minute: Int): Long =
        day.atTime(hour, minute).atZone(berlin).toInstant().toEpochMilli()
}
