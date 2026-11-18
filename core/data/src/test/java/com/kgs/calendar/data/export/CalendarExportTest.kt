package com.kgs.calendar.data.export

import com.kgs.calendar.time.*
import kotlinx.datetime.*
import com.kgs.calendar.data.LOCAL_COLLECTION
import com.kgs.calendar.data.RepositoryHarness
import com.kgs.calendar.data.eventPayload
import com.kgs.calendar.data.ical.IcalCodec
import com.kgs.calendar.data.ical.IcsEventReader
import com.kgs.calendar.data.taskPayload
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

@RunWith(RobolectricTestRunner::class)
class CalendarExportTest {
    private val harness = RepositoryHarness()
    private val repository = harness.repository

    @After
    fun tearDown() = harness.close()

    @Test
    fun exportsEventsAndTasksOfTheLocalCalendarAsOneFile() = runTest {
        repository.ensureLocalCalendar()
        val day = LocalDate.of(2026, 11, 5)
        repository.createEvent(eventPayload("Dentist", day, LocalTime.of(9, 0), LocalTime.of(10, 0), description = "Bring card"))
        repository.createEvent(eventPayload("Standup", day, LocalTime.of(10, 0), LocalTime.of(10, 15), recurrenceRule = "FREQ=DAILY;COUNT=3"))
        repository.createTask(taskPayload("Pay invoice"))

        val result = harness.components.export.export(listOf(LOCAL_COLLECTION))
        val raw = result.rawIcs

        assertEquals(3, result.items)
        assertTrue(raw.startsWith("BEGIN:VCALENDAR\r\n"))
        assertTrue(raw.endsWith("END:VCALENDAR\r\n"))
        assertEquals(2, Regex("BEGIN:VEVENT").findAll(raw).count())
        assertEquals(1, Regex("BEGIN:VTODO").findAll(raw).count())
        // One definition per zone, however many items use it.
        assertEquals(1, Regex("BEGIN:VTIMEZONE").findAll(raw).count())
        assertTrue(raw.lines().all { it.toByteArray().size <= 75 })

        // The exported events can be imported again.
        val events = IcsEventReader(IcalCodec(harness.zone)).read(raw.replace(Regex("BEGIN:VTODO.*?END:VTODO\r\n", RegexOption.DOT_MATCHES_ALL), ""))
        assertEquals(setOf("Dentist", "Standup"), events.map { it.event.title }.toSet())
        assertEquals("Bring card", events.single { it.event.title == "Dentist" }.event.description)
    }
}

@RunWith(RobolectricTestRunner::class)
class CalendarExportConflictTest {
    private val harness = RepositoryHarness()
    private val server = harness.server
    private val other get() = "${server.homeHref}other/"

    @After
    fun tearDown() = harness.close()

    private fun zone(offset: String) = listOf(
        "BEGIN:VTIMEZONE", "TZID:CustomZone", "BEGIN:STANDARD", "DTSTART:19700101T000000",
        "TZOFFSETFROM:$offset", "TZOFFSETTO:$offset", "END:STANDARD", "END:VTIMEZONE",
    )

    private fun inZone(uid: String, offset: String) = com.kgs.calendar.data.SampleIcs.calendar(
        *zone(offset).toTypedArray(),
        "BEGIN:VEVENT", "UID:$uid", "DTSTAMP:20260101T000000Z", "DTSTART;TZID=CustomZone:20261102T090000",
        "DTEND;TZID=CustomZone:20261102T100000", "SUMMARY:$uid", "END:VEVENT",
    )

    private suspend fun exportBoth() = harness.components.export.export(listOf(server.eventsHref, other))

    private suspend fun assertRefused() {
        try {
            exportBoth()
            org.junit.Assert.fail("The export must refuse this")
        } catch (_: CalendarExportConflictException) {
        }
    }

    @Test
    fun differentItemsWithOneUidAreRefused() = runTest {
        server.addCollection(other, "Other", setOf("VEVENT"))
        server.putRemote(server.eventsHref, "a.ics", com.kgs.calendar.data.SampleIcs.event("shared", "First"))
        server.putRemote(other, "a.ics", com.kgs.calendar.data.SampleIcs.event("shared", "Second"))
        harness.addSyncedCalDavAccount()

        assertRefused()
    }

    @Test
    fun identicalCopiesAreWrittenOnceAndDisclosed() = runTest {
        server.addCollection(other, "Other", setOf("VEVENT"))
        server.putRemote(server.eventsHref, "a.ics", com.kgs.calendar.data.SampleIcs.event("shared", "Same"))
        server.putRemote(other, "a.ics", com.kgs.calendar.data.SampleIcs.event("shared", "Same"))
        harness.addSyncedCalDavAccount()

        val result = exportBoth()

        assertEquals(1, result.items)
        assertEquals(1, result.mergedCopies)
        assertEquals(1, IcsEventReader(IcalCodec()).read(result.rawIcs).size)
    }

    @Test
    fun conflictingTimeZoneDefinitionsAreRefusedInEitherOrder() = runTest {
        server.addCollection(other, "Other", setOf("VEVENT"))
        server.putRemote(server.eventsHref, "a.ics", inZone("a", "+0100"))
        server.putRemote(other, "b.ics", inZone("b", "+0200"))
        harness.addSyncedCalDavAccount()

        assertRefused()
        try {
            harness.components.export.export(listOf(other, server.eventsHref))
            org.junit.Assert.fail("The export must refuse this")
        } catch (_: CalendarExportConflictException) {
        }
    }

    @Test
    fun matchingTimeZoneDefinitionsAreShared() = runTest {
        server.putRemote(server.eventsHref, "a.ics", inZone("a", "+0100"))
        server.putRemote(server.eventsHref, "b.ics", inZone("b", "+0100"))
        harness.addSyncedCalDavAccount()

        val raw = harness.components.export.export(listOf(server.eventsHref)).rawIcs

        assertEquals(1, Regex("BEGIN:VTIMEZONE").findAll(raw).count())
    }

    @Test
    fun calendarsThatCannotShareAFileExportOneAtATime() = runTest {
        server.addCollection(other, "Other", setOf("VEVENT"))
        server.putRemote(server.eventsHref, "a.ics", com.kgs.calendar.data.SampleIcs.event("shared", "First"))
        server.putRemote(other, "a.ics", com.kgs.calendar.data.SampleIcs.event("shared", "Second"))
        harness.addSyncedCalDavAccount()

        val first = harness.components.export.export(listOf(server.eventsHref))
        val second = harness.components.export.export(listOf(other))

        val reader = IcsEventReader(IcalCodec())
        assertEquals(listOf("First"), reader.read(first.rawIcs).map { it.event.title })
        assertEquals(listOf("Second"), reader.read(second.rawIcs).map { it.event.title })
        assertEquals(listOf("shared"), reader.read(second.rawIcs).map { it.event.uid })
    }

    @Test
    fun aRefusalNamesWhatCollided() = runTest {
        server.addCollection(other, "Other", setOf("VEVENT"))
        server.putRemote(server.eventsHref, "a.ics", inZone("a", "+0100"))
        server.putRemote(other, "b.ics", inZone("b", "+0200"))
        harness.addSyncedCalDavAccount()

        try {
            exportBoth()
            org.junit.Assert.fail("The export must refuse this")
        } catch (conflict: CalendarExportConflictException) {
            assertEquals(CalendarExportConflictException.Kind.TimeZone, conflict.kind)
            assertEquals("CustomZone", conflict.name)
        }
    }
}
