package com.kgs.calendar.domain.event

import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.domain.model.EventStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class EventRulesTest {
    private val zone = ZoneId.of("Europe/Berlin")

    @Test
    fun statusChecksAreCaseInsensitive() {
        assertTrue(event(status = "tentative").isTentative())
        assertTrue(event(status = "Cancelled").isCancelled())
        assertFalse(event(status = "CANCELED").isCancelled())
        assertFalse(event(status = null).isTentative())
        assertEquals(EventStatus.Other("X-DRAFT"), event(status = "X-DRAFT").eventStatus)
    }

    @Test
    fun monthOccurrenceKeyOnlyFallsBackToTheUidWhenAskedTo() {
        val start = millis(LocalDateTime.of(2026, 9, 1, 10, 0))

        assertEquals("/cal/e.ics:$start", event(start = start).monthOccurrenceKey())
        assertEquals("/cal/e.ics:$start", event(start = start).monthOccurrenceKey(uidFallbackForBlankHref = true))
        assertEquals(":$start", event(start = start, resourceHref = "").monthOccurrenceKey())
        assertEquals("uid:$start", event(start = start, resourceHref = "").monthOccurrenceKey(uidFallbackForBlankHref = true))
    }

    @Test
    fun displayColorPrefersTheManualColor() {
        assertEquals(0x111111, event().displayColor())
        assertEquals(0x222222, event(manualColor = 0x222222).displayColor())
    }

    @Test
    fun endDateIsInclusiveAndUsesTheGivenZone() {
        val allDay = event(
            start = millis(LocalDateTime.of(2026, 9, 1, 0, 0)),
            end = millis(LocalDateTime.of(2026, 9, 3, 0, 0)),
            allDay = true,
        )

        assertEquals(LocalDate.of(2026, 9, 2), allDay.endDateInclusive(zone))
        val zeroLength = millis(LocalDateTime.of(2026, 9, 2, 0, 30)).let { event(start = it, end = it) }
        assertEquals(LocalDate.of(2026, 9, 2), zeroLength.endDateInclusive(zone))
        assertEquals(LocalDate.of(2026, 9, 1), zeroLength.endDateInclusive(ZoneId.of("UTC")))
    }

    @Test
    fun allDayTopItemsCoverAllDayDatesAndMiddleDaysOfTimedSpans() {
        val allDay = event(
            start = millis(LocalDateTime.of(2026, 9, 1, 0, 0)),
            end = millis(LocalDateTime.of(2026, 9, 3, 0, 0)),
            allDay = true,
        )
        val timed = event(
            start = millis(LocalDateTime.of(2026, 9, 1, 18, 0)),
            end = millis(LocalDateTime.of(2026, 9, 4, 9, 0)),
        )

        assertEquals(
            listOf(false, true, true, false),
            (0L..3L).map { allDay.isAllDayTopItemOn(LocalDate.of(2026, 8, 31).plusDays(it), zone) },
        )
        assertEquals(
            listOf(false, true, true, false),
            (0L..3L).map { timed.isAllDayTopItemOn(LocalDate.of(2026, 9, 1).plusDays(it), zone) },
        )
        assertTrue(timed.isTimedMultiDay(zone))
        assertTrue(timed.occursOn(LocalDate.of(2026, 9, 4), zone))
        assertFalse(allDay.isTimedMultiDayMiddleOn(LocalDate.of(2026, 9, 2), zone))
    }

    private fun millis(dateTime: LocalDateTime): Long = dateTime.atZone(zone).toInstant().toEpochMilli()

    private fun event(
        status: String? = null,
        start: Long = 0,
        end: Long = start + 3_600_000,
        allDay: Boolean = false,
        resourceHref: String = "/cal/e.ics",
        manualColor: Int? = null,
    ) = EventEntity(
        uid = "uid",
        collectionHref = "/cal/",
        resourceHref = resourceHref,
        title = "Event",
        description = null,
        location = null,
        startsAtMillis = start,
        endsAtMillis = end,
        allDay = allDay,
        recurrenceRule = null,
        isRecurring = false,
        status = status,
        color = 0x111111,
        manualColor = manualColor,
    )
}
