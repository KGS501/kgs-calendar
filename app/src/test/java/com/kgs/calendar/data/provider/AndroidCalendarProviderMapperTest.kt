package com.kgs.calendar.data.provider

import android.provider.CalendarContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class AndroidCalendarProviderMapperTest {
    @Test
    fun mapsProviderEventToKgsEvent() {
        val start = LocalDate.of(2026, 6, 8).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val end = LocalDate.of(2026, 6, 9).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

        val mapped = androidProviderEventToEntity(
            event = AndroidProviderEvent(
                id = 42,
                calendarId = 7,
                title = "Planning",
                description = "Notes",
                location = "Berlin",
                startsAtMillis = start,
                endsAtMillis = end,
                allDay = true,
                recurrenceRule = "FREQ=WEEKLY;COUNT=3",
                exDates = "20260615",
                status = CalendarContract.Events.STATUS_TENTATIVE,
                accessLevel = CalendarContract.Events.ACCESS_PRIVATE,
                availability = CalendarContract.Events.AVAILABILITY_FREE,
                organizer = null,
                eventColor = 0xffabcdef.toInt(),
                reminderMinutes = listOf(30, 10, 10),
            ),
            collectionHref = "android://calendar/7",
            color = 0xff176b5d.toInt(),
            manualColor = 0xff123456.toInt(),
            zoneId = ZoneOffset.UTC,
        )

        assertEquals("android-event-42", mapped.uid)
        assertEquals("android://event/42", mapped.resourceHref)
        assertEquals("Planning", mapped.title)
        assertEquals("Notes", mapped.description)
        assertEquals("Berlin", mapped.location)
        assertTrue(mapped.allDay)
        assertEquals("FREQ=WEEKLY;COUNT=3", mapped.recurrenceRule)
        assertTrue(mapped.isRecurring)
        assertEquals("10,30", mapped.remindersCsv)
        assertNull(mapped.status)
        assertNull(mapped.classification)
        assertNull(mapped.transparency)
        assertEquals(LocalDate.of(2026, 6, 15).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli().toString(), mapped.exDatesCsv)
        assertEquals(0xffabcdef.toInt(), mapped.manualColor)
        assertNull(mapped.categories)
        assertNull(mapped.attendeesJson)
    }

    @Test
    fun keepsExistingKgsManualColorWhenAndroidHasNoEventColor() {
        val start = LocalDate.of(2026, 6, 8).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val end = LocalDate.of(2026, 6, 8).atTime(1, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

        val mapped = androidProviderEventToEntity(
            event = AndroidProviderEvent(
                id = 43,
                calendarId = 7,
                title = "Planning",
                description = null,
                location = null,
                startsAtMillis = start,
                endsAtMillis = end,
                allDay = false,
                recurrenceRule = null,
                exDates = null,
                status = null,
                accessLevel = null,
                availability = null,
                organizer = null,
                eventColor = null,
            ),
            collectionHref = "android://calendar/7",
            color = 0xff176b5d.toInt(),
            manualColor = 0xff123456.toInt(),
            zoneId = ZoneOffset.UTC,
        )

        assertEquals(0xff176b5d.toInt(), mapped.color)
        assertEquals(0xff123456.toInt(), mapped.manualColor)
    }

    @Test
    fun convertsAndroidAllDayUtcDatesToLocalExclusiveRange() {
        val berlin = ZoneId.of("Europe/Berlin")
        val providerStart = LocalDate.of(2026, 6, 4).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val providerEnd = LocalDate.of(2026, 6, 5).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

        val localStart = androidAllDayProviderMillisToLocalMillis(providerStart, berlin)
        val localEnd = androidAllDayProviderMillisToLocalMillis(providerEnd, berlin)

        assertEquals(LocalDate.of(2026, 6, 4).atStartOfDay(berlin).toInstant().toEpochMilli(), localStart)
        assertEquals(LocalDate.of(2026, 6, 5).atStartOfDay(berlin).toInstant().toEpochMilli(), localEnd)
        assertEquals(providerStart, androidAllDayLocalMillisToProviderMillis(localStart, berlin))
        assertEquals(providerEnd, androidAllDayLocalMillisToProviderMillis(localEnd, berlin))
    }
}
