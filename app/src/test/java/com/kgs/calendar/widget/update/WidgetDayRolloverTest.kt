package com.kgs.calendar.widget.update

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class WidgetDayRolloverTest {
    private val zone = ZoneId.of("Europe/Berlin")

    @Test
    fun rolloverIsJustAfterTheNextLocalMidnight() {
        val now = ZonedDateTime.of(LocalDate.of(2026, 10, 18), LocalTime.of(23, 59, 30), zone)

        val expected = LocalDate.of(2026, 10, 19).atStartOfDay(zone).toInstant().toEpochMilli() + 5_000L
        assertEquals(expected, WidgetDayRollover.nextRolloverAtMillis(now))
    }

    @Test
    fun rolloverAfterAShortDstDayIsStillAtMidnight() {
        // 2026-03-29 only has 23 hours in Berlin.
        val now = ZonedDateTime.of(LocalDate.of(2026, 3, 29), LocalTime.of(0, 10), zone)

        val expected = LocalDate.of(2026, 3, 30).atStartOfDay(zone).toInstant().toEpochMilli() + 5_000L
        assertEquals(expected, WidgetDayRollover.nextRolloverAtMillis(now))
    }
}
