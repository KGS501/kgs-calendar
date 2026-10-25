package com.kgs.calendar.widget.data

import com.kgs.calendar.data.local.entity.EventEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class WidgetEntityPlacementTest {
    private val berlin = ZoneId.of("Europe/Berlin")

    @Test
    fun dayGridItemsFollowTheClockOnTheDaysTheClocksChange() {
        listOf(LocalDate.of(2026, 10, 25), LocalDate.of(2026, 3, 29)).forEach { day ->
            val event = EventEntity(
                uid = "event",
                collectionHref = "collection",
                resourceHref = "event.ics",
                title = "Event",
                description = null,
                location = null,
                startsAtMillis = millis(day, LocalTime.of(10, 0)),
                endsAtMillis = millis(day, LocalTime.of(11, 30)),
                allDay = false,
                recurrenceRule = null,
                isRecurring = false,
                color = 0,
            )

            assertEquals("$day", 600 to 690, event.widgetTimedPlacementOn(day, berlin))
        }
    }

    private fun millis(day: LocalDate, time: LocalTime): Long = day.atTime(time).atZone(berlin).toInstant().toEpochMilli()
}
