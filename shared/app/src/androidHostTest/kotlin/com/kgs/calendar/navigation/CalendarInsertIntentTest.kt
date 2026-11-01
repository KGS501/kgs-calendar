package com.kgs.calendar.navigation

import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import com.kgs.calendar.ui.editorSchedule
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CalendarInsertIntentTest {
    @Test
    fun insertPrefillsTextAndTimedScheduleAcrossMidnight() {
        val intent = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, "Trip")
            .putExtra(CalendarContract.Events.DESCRIPTION, "Booking details")
            .putExtra(CalendarContract.Events.EVENT_LOCATION, "Berlin")
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, Instant.parse("2026-10-12T21:30:00Z").toEpochMilli())
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, Instant.parse("2026-10-13T00:30:00Z").toEpochMilli())
        val draft = SharedEventDraft.fromCalendarInsertIntent(intent)!!
        assertEquals("Trip", draft.title)
        assertEquals("Booking details", draft.notes)
        assertEquals("Berlin", draft.location)
        val schedule = draft.editorSchedule(LocalDate.now(), LocalTime.NOON, 60, ZoneId.of("Europe/Berlin"))
        assertEquals("2026-10-12", schedule.startDateText)
        assertEquals("23:30", schedule.startTimeText)
        assertEquals("2026-10-13", schedule.endDateText)
        assertEquals("02:30", schedule.endTimeText)
    }

    @Test
    fun allDayUsesUtcDatesAndConvertsExclusiveEnd() {
        val intent = Intent(Intent.ACTION_INSERT).setType("vnd.android.cursor.item/event")
            .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, Instant.parse("2026-10-12T00:00:00Z").toEpochMilli())
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, Instant.parse("2026-10-15T00:00:00Z").toEpochMilli())
        val draft = SharedEventDraft.fromCalendarInsertIntent(intent)!!
        val schedule = draft.editorSchedule(LocalDate.now(), LocalTime.NOON, 60, ZoneId.of("America/Los_Angeles"))
        assertTrue(schedule.allDay)
        assertEquals("2026-10-12", schedule.startDateText)
        assertEquals("2026-10-14", schedule.endDateText)
        assertFalse(schedule.hasStartTime)
    }

    @Test
    fun missingOrInvalidEndUsesDefaultDuration() {
        val begin = Instant.parse("2026-10-12T23:30:00Z").toEpochMilli()
        val draft = SharedEventDraft("", "", beginTimeMillis = begin, endTimeMillis = begin - 1)
        val schedule = draft.editorSchedule(LocalDate.now(), LocalTime.NOON, 60, ZoneId.of("UTC"))
        assertEquals("2026-10-13", schedule.endDateText)
        assertEquals("00:30", schedule.endTimeText)
    }

    @Test
    fun unrelatedIntentsAndExistingEventEditsDoNotCreateDuplicates() {
        assertNull(SharedEventDraft.fromCalendarInsertIntent(Intent(Intent.ACTION_VIEW, CalendarContract.Events.CONTENT_URI)))
        assertNull(SharedEventDraft.fromCalendarInsertIntent(Intent(Intent.ACTION_INSERT, Uri.parse("content://contacts/people"))))
        assertNull(SharedEventDraft.fromCalendarInsertIntent(Intent(Intent.ACTION_EDIT, Uri.parse("content://com.android.calendar/events/42"))))
    }
}
