package com.kgs.calendar.data.recurrence

import com.kgs.calendar.data.ical.EventRecurrenceOverride
import com.kgs.calendar.data.ical.RecurrenceOverrideCodec
import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.domain.model.CalendarOccurrenceId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class RecurrenceExpanderTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val expander = RecurrenceExpander(zone)

    private fun masterEvent(
        startDate: LocalDate,
        startTime: LocalTime,
        durationMinutes: Long = 60,
        rrule: String?,
        exDatesCsv: String? = null,
    ): EventEntity {
        val start = startDate.atTime(startTime).atZone(zone).toInstant().toEpochMilli()
        val end = start + durationMinutes * 60 * 1000
        return EventEntity(
            uid = "u",
            collectionHref = "/c/",
            resourceHref = "/c/u.ics",
            title = "T",
            description = null,
            location = null,
            startsAtMillis = start,
            endsAtMillis = end,
            allDay = false,
            recurrenceRule = rrule,
            isRecurring = rrule != null,
            exDatesCsv = exDatesCsv,
            timezoneId = zone.id,
            color = 0,
        )
    }

    private fun rangeMillis(from: LocalDate, to: LocalDate): Pair<Long, Long> {
        val start = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = to.atStartOfDay(zone).toInstant().toEpochMilli()
        return start to end
    }

    @Test
    fun weeklyBydayExpandsAllOccurrencesInRange() {
        // Master: 2024-01-01 (Mon) 09:00, weekly on MO,WE
        val master = masterEvent(
            startDate = LocalDate.of(2024, 1, 1),
            startTime = LocalTime.of(9, 0),
            rrule = "FREQ=WEEKLY;BYDAY=MO,WE",
        )
        val (s, e) = rangeMillis(LocalDate.of(2026, 5, 25), LocalDate.of(2026, 6, 1))

        val out = expander.expand(master, s, e)

        // 2026-05-25 Mon, 2026-05-27 Wed
        assertEquals(2, out.size)
    }

    @Test
    fun dailyEveryOtherDayWithCountStopsAtCount() {
        val master = masterEvent(
            startDate = LocalDate.of(2026, 5, 25),
            startTime = LocalTime.of(8, 0),
            rrule = "FREQ=DAILY;INTERVAL=2;COUNT=5",
        )
        val (s, e) = rangeMillis(LocalDate.of(2026, 5, 25), LocalDate.of(2026, 12, 31))

        val out = expander.expand(master, s, e)

        assertEquals(5, out.size)
    }

    @Test
    fun untilHonoredInclusive() {
        val master = masterEvent(
            startDate = LocalDate.of(2026, 5, 25),
            startTime = LocalTime.of(8, 0),
            rrule = "FREQ=DAILY;UNTIL=20260527T235959Z",
        )
        val (s, e) = rangeMillis(LocalDate.of(2026, 5, 25), LocalDate.of(2026, 6, 1))

        val out = expander.expand(master, s, e)

        assertEquals(3, out.size)
    }

    @Test
    fun exDateExcludesOccurrence() {
        val skip = LocalDate.of(2026, 6, 1).atTime(9, 0).atZone(zone).toInstant().toEpochMilli().toString()
        val master = masterEvent(
            startDate = LocalDate.of(2026, 5, 25),
            startTime = LocalTime.of(9, 0),
            rrule = "FREQ=WEEKLY;BYDAY=MO",
            exDatesCsv = skip,
        )
        val (s, e) = rangeMillis(LocalDate.of(2026, 5, 25), LocalDate.of(2026, 6, 15))

        val out = expander.expand(master, s, e)

        // Without EXDATE: 2026-05-25, 06-01, 06-08 → 3.
        // With EXDATE on 06-01: 2 occurrences.
        assertEquals(2, out.size)
    }

    @Test
    fun monthlyBymonthdayExpands() {
        val master = masterEvent(
            startDate = LocalDate.of(2026, 1, 15),
            startTime = LocalTime.of(10, 0),
            rrule = "FREQ=MONTHLY;BYMONTHDAY=15",
        )
        val (s, e) = rangeMillis(LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1))

        val out = expander.expand(master, s, e)

        assertEquals(12, out.size)
    }

    @Test
    fun yearlyExpandsBirthday() {
        val master = masterEvent(
            startDate = LocalDate.of(1990, 5, 28),
            startTime = LocalTime.of(0, 0),
            rrule = "FREQ=YEARLY",
        )
        val (s, e) = rangeMillis(LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1))

        val out = expander.expand(master, s, e)

        assertEquals(1, out.size)
    }

    @Test
    fun monthlyByDayFirstMondayWorks() {
        val master = masterEvent(
            startDate = LocalDate.of(2026, 1, 5), // first Monday of Jan 2026
            startTime = LocalTime.of(10, 0),
            rrule = "FREQ=MONTHLY;BYDAY=1MO",
        )
        val (s, e) = rangeMillis(LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1))

        val out = expander.expand(master, s, e)

        assertEquals(12, out.size)
    }

    @Test
    fun nonRecurringMasterReturnedAsSingleWhenInRange() {
        val master = masterEvent(
            startDate = LocalDate.of(2026, 5, 28),
            startTime = LocalTime.of(10, 0),
            rrule = null,
        )
        val (s, e) = rangeMillis(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 1))

        val out = expander.expand(master, s, e)

        assertEquals(1, out.size)
    }

    @Test
    fun weeklyEventFromPastFastForwardsToRange() {
        // Started in 2020, weekly Wednesdays. View 2026-05.
        val master = masterEvent(
            startDate = LocalDate.of(2020, 1, 1), // Wednesday
            startTime = LocalTime.of(9, 0),
            rrule = "FREQ=WEEKLY",
        )
        val (s, e) = rangeMillis(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 1))

        val out = expander.expand(master, s, e)

        // 4 or 5 Wednesdays in May 2026 (6, 13, 20, 27 → 4)
        assertTrue("expected 4 occurrences, got ${out.size}", out.size == 4)
    }

    @Test
    fun monthlyBySetPosUsesLastWeekday() {
        val master = masterEvent(
            startDate = LocalDate.of(2026, 1, 30),
            startTime = LocalTime.of(9, 0),
            rrule = "FREQ=MONTHLY;COUNT=3;BYDAY=MO,TU,WE,TH,FR;BYSETPOS=-1",
        )
        val (s, e) = rangeMillis(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 5, 1))

        val out = expander.expand(master, s, e)

        assertEquals(
            listOf(
                LocalDate.of(2026, 1, 30),
                LocalDate.of(2026, 2, 27),
                LocalDate.of(2026, 3, 31),
            ),
            out.map { java.time.Instant.ofEpochMilli(it.startsAtMillis).atZone(zone).toLocalDate() },
        )
    }

    @Test
    fun hourlyFrequencyIsSupported() {
        val master = masterEvent(
            startDate = LocalDate.of(2026, 6, 1),
            startTime = LocalTime.of(9, 0),
            rrule = "FREQ=HOURLY;COUNT=4",
        )
        val (s, e) = rangeMillis(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2))

        val out = expander.expand(master, s, e)

        assertEquals(listOf(9, 10, 11, 12), out.map { java.time.Instant.ofEpochMilli(it.startsAtMillis).atZone(zone).hour })
    }

    @Test
    fun weeklyLocalTimeSurvivesDstChange() {
        val master = masterEvent(
            startDate = LocalDate.of(2026, 3, 22),
            startTime = LocalTime.of(9, 0),
            rrule = "FREQ=WEEKLY;COUNT=3",
        )
        val (s, e) = rangeMillis(LocalDate.of(2026, 3, 20), LocalDate.of(2026, 4, 10))

        val out = expander.expand(master, s, e)

        assertEquals(listOf(9, 9, 9), out.map { java.time.Instant.ofEpochMilli(it.startsAtMillis).atZone(zone).hour })
    }

    @Test
    fun movedOverrideKeepsOriginalRecurrenceIdentity() {
        val master = masterEvent(
            startDate = LocalDate.of(2026, 7, 9),
            startTime = LocalTime.of(9, 0),
            rrule = "FREQ=DAILY;COUNT=2",
        )
        val originalSecondStart = LocalDate.of(2026, 7, 10).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val movedStart = LocalDate.of(2026, 7, 10).atTime(14, 0).atZone(zone).toInstant().toEpochMilli()
        val withOverride = master.copy(
            recurrenceOverridesJson = RecurrenceOverrideCodec.encodeEvents(
                listOf(
                    EventRecurrenceOverride.fromEvent(
                        originalSecondStart,
                        master.copy(startsAtMillis = movedStart, endsAtMillis = movedStart + 60 * 60 * 1000),
                    ),
                ),
            ),
        )
        val (rangeStart, rangeEnd) = rangeMillis(LocalDate.of(2026, 7, 9), LocalDate.of(2026, 7, 12))

        val occurrences = expander.expandWithIdentity(withOverride, rangeStart, rangeEnd)

        val moved = occurrences.single { it.item.startsAtMillis == movedStart }
        assertEquals(CalendarOccurrenceId.Event(master.resourceHref, originalSecondStart), moved.occurrenceId)
    }
}
