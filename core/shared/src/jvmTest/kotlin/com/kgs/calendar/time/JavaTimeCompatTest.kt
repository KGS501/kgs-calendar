package com.kgs.calendar.time

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/** The shim must give the same answers as java.time, which the Android code used before. */
class JavaTimeCompatTest {
    private val berlin = TimeZone.of("Europe/Berlin")
    private val javaBerlin = java.time.ZoneId.of("Europe/Berlin")
    private val saoPaulo = TimeZone.of("America/Sao_Paulo") // midnight DST gap in 2018

    @Test
    fun startOfDayMatchesJavaTimeIncludingMidnightGaps() {
        val days = listOf(LocalDate(2026, 3, 29), LocalDate(2026, 10, 25), LocalDate(2018, 11, 4), LocalDate(2026, 1, 1))
        for (zone in listOf(berlin, saoPaulo)) for (day in days) {
            val expected = java.time.LocalDate.of(day.year, day.monthValue, day.day)
                .atStartOfDay(java.time.ZoneId.of(zone.id)).toInstant().toEpochMilli()
            assertEquals(expected, day.atStartOfDay(zone).toInstant().toEpochMilli(), "$zone $day")
            assertEquals(expected, day.startOfDayMillis(zone))
        }
    }

    @Test
    fun zonedPlusDaysKeepsLocalTimeAcrossDst() {
        val start = java.time.ZonedDateTime.of(2026, 3, 28, 2, 30, 0, 0, javaBerlin)
        val shim = ZonedDateTime.of(LocalDateTime(2026, 3, 28, 2, 30), berlin)
        assertEquals(start.plusDays(1).toInstant().toEpochMilli(), shim.plusDays(1).toInstant().toEpochMilli())
        val overlap = java.time.ZonedDateTime.of(2026, 10, 25, 2, 30, 0, 0, javaBerlin)
        assertEquals(overlap.toInstant().toEpochMilli(), ZonedDateTime.of(LocalDateTime(2026, 10, 25, 2, 30), berlin).toInstant().toEpochMilli())
    }

    @Test
    fun calendarArithmeticMatches() {
        val d = LocalDate(2024, 1, 31)
        assertEquals(LocalDate(2024, 2, 29), d.plusMonths(1))
        assertEquals(LocalDate(2023, 2, 28), LocalDate(2024, 2, 29).minusYears(1))
        assertEquals(LocalDate(2024, 1, 29), d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
        assertEquals(30L, ChronoUnit.DAYS.between(LocalDate(2024, 1, 1), d))
        assertEquals(DayOfWeek.MONDAY, DayOfWeek.SUNDAY.plus(1))
        assertEquals(LocalTime(0, 15), LocalTime(23, 45).plusMinutes(30))
        assertEquals(LocalTime(23, 45), LocalTime(0, 15).minusMinutes(30))
    }

    @Test
    fun weekNumbersMatchWeekFields() {
        var day = LocalDate(2019, 12, 20)
        repeat(800) {
            for ((first, min) in listOf(DayOfWeek.MONDAY to 4, DayOfWeek.SUNDAY to 1)) {
                val wf = java.time.temporal.WeekFields.of(java.time.DayOfWeek.of(first.value), min)
                val expected = java.time.LocalDate.of(day.year, day.monthValue, day.day).get(wf.weekOfWeekBasedYear())
                assertEquals(expected, weekOfWeekBasedYear(day, first, min), "$day $first")
            }
            day = day.plusDays(3)
        }
    }

    @Test
    fun formattingDelegatesToJavaTime() {
        val de = appLocaleOf("de-DE")
        assertEquals("Montag, 2. November 2026", LocalDate(2026, 11, 2).format(DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", de)))
        assertEquals("09:05", LocalTime(9, 5).format(DateTimeFormatter.ofPattern("HH:mm")))
        assertEquals("20261102", LocalDate(2026, 11, 2).format(DateTimeFormatter.BASIC_ISO_DATE))
        assertEquals("Mo.", DayOfWeek.MONDAY.getDisplayName(TextStyle.SHORT, de))
        assertEquals(DayOfWeek.MONDAY, firstDayOfWeekFor(de))
        assertEquals(DayOfWeek.SUNDAY, firstDayOfWeekFor(appLocaleOf("en-US")))
        val instant = Instant.fromEpochMilliseconds(1_700_000_000_000)
        assertEquals("2023-11-14 23:13", instant.atZone(berlin).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
    }
}
