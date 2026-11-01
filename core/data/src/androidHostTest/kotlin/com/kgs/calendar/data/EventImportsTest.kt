package com.kgs.calendar.data

import com.kgs.calendar.data.ical.IcalCodec
import com.kgs.calendar.data.ical.IcsEventReader
import com.kgs.calendar.data.ical.RecurrenceOverrideCodec
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class EventImportsTest {
    private val harness = RepositoryHarness()
    private val imports = harness.components.eventImports
    private val reader = IcsEventReader(IcalCodec(TEST_ZONE))

    @After fun close() = harness.close()

    @Test fun invitationRetainsDetailsTimeZoneAndRecurrenceExceptions() = runTest {
        harness.repository.ensureLocalCalendar()
        val raw = calendar("METHOD:REQUEST\n" + event("meeting", """
            DESCRIPTION:First line\nSecond line
            LOCATION:Room\, one
            ORGANIZER;CN=Alice:mailto:alice@example.com
            ATTENDEE;PARTSTAT=ACCEPTED:mailto:bob@example.com
            RRULE:FREQ=DAILY;COUNT=3
            EXDATE;TZID=Europe/Berlin:20261020T100000
            X-IMPORT-TEST:preserved
            BEGIN:VALARM
            ACTION:DISPLAY
            TRIGGER:-PT15M
            END:VALARM
        """.trimIndent()) + """
            BEGIN:VEVENT
            UID:meeting
            RECURRENCE-ID;TZID=Europe/Berlin:20261021T100000
            DTSTART;TZID=Europe/Berlin:20261021T110000
            DTEND;TZID=Europe/Berlin:20261021T113000
            SUMMARY:Moved meeting
            END:VEVENT
        """.trimIndent())
        assertEquals(1, imports.importEvents(raw, LOCAL_COLLECTION).imported)
        val saved = harness.eventsIn(LOCAL_COLLECTION).single()
        assertEquals("meeting", saved.uid)
        assertEquals("Europe/Berlin", saved.timezoneId)
        assertEquals(Instant.parse("2026-10-19T08:00:00Z").toEpochMilli(), saved.startsAtMillis)
        assertEquals("First line\nSecond line", saved.description)
        assertEquals("Room, one", saved.location)
        assertEquals("15", saved.remindersCsv)
        assertTrue(saved.organizerJson!!.contains("alice@example.com"))
        assertTrue(saved.attendeesJson!!.contains("ACCEPTED"))
        assertEquals("FREQ=DAILY;COUNT=3", saved.recurrenceRule)
        assertNotNull(saved.exDatesCsv)
        assertEquals("Moved meeting", RecurrenceOverrideCodec.decodeEvents(saved.recurrenceOverridesJson).single().title)
        val stored = harness.resource(saved.resourceHref)!!.rawIcs
        assertTrue(stored.contains("X-IMPORT-TEST:preserved"))
        assertFalse(stored.contains("METHOD:REQUEST"))
        assertTrue(harness.pendingMutations().isEmpty())
    }

    @Test fun foldedUnicodeMultipleEventsAndAllDayDatesArePreserved() {
        val raw = "\uFEFF" + calendar(event("one", "SUMMARY:Team\n  meeting 🗓️") + """
            BEGIN:VEVENT
            UID:two
            DTSTART;VALUE=DATE:20261024
            DTEND;VALUE=DATE:20261027
            SUMMARY:Holiday
            END:VEVENT
        """.trimIndent())
        val events = reader.read(raw)
        assertEquals(2, events.size)
        assertEquals("Team meeting 🗓️", events[0].event.title)
        assertTrue(events[1].event.allDay)
        assertEquals(harness.startOfDay(java.time.LocalDate.of(2026, 10, 24)), events[1].event.startsAtMillis)
        assertEquals(harness.startOfDay(java.time.LocalDate.of(2026, 10, 27)), events[1].event.endsAtMillis)
        assertFalse(events[0].rawIcs.contains("UID:two"))
    }

    @Test fun reopeningSkipsExistingUidWithoutOverwritingEdits() = runTest {
        harness.repository.ensureLocalCalendar()
        val raw = calendar(event("same"))
        imports.importEvents(raw, LOCAL_COLLECTION)
        val saved = harness.eventsIn(LOCAL_COLLECTION).single()
        harness.database.eventDao().upsert(saved.copy(title = "Local edit"))
        val second = imports.importEvents(raw, LOCAL_COLLECTION)
        assertEquals(0, second.imported)
        assertEquals(1, second.skipped)
        assertEquals("Local edit", harness.eventsIn(LOCAL_COLLECTION).single().title)
    }

    @Test fun calDavImportUsesNormalCreateOnlyOutbox() = runTest {
        harness.addSyncedCalDavAccount()
        val href = harness.server.eventsHref
        val raw = calendar(event("remote-import"))
        imports.importEvents(raw, href)
        val queued = harness.pendingMutations().single()
        assertNull(queued.baseEtag)
        harness.repository.pushPendingChangesCreatedSince(0)
        val request = harness.server.requests("PUT").single()
        assertEquals("*", request.header("If-None-Match"))
        assertTrue(request.body.contains("UID:remote-import"))
        assertTrue(harness.pendingMutations().isEmpty())
    }

    @Test fun invalidLaterEventRejectsTheEntireImport() = runTest {
        harness.repository.ensureLocalCalendar()
        val raw = calendar(event("valid") + event("invalid").replace("DTSTART;TZID=Europe/Berlin:20261019T100000", "DTSTART:not-a-date"))
        try { imports.importEvents(raw, LOCAL_COLLECTION); fail("Expected invalid calendar") } catch (_: IllegalArgumentException) { }
        assertTrue(harness.eventsIn(LOCAL_COLLECTION).isEmpty())
    }

    @Test fun malformedAndUnsupportedCalendarsAreRejected() {
        val invalid = listOf(
            "plain email text",
            calendar(event("one")).replace("END:VEVENT", ""),
            calendar(event("one")).replace("UID:one", ""),
            calendar(event("one") + event("one")),
            calendar(event("one")).replace("DTEND;TZID=Europe/Berlin:20261019T110000", "DTEND;TZID=Europe/Berlin:20261018T110000"),
            calendar(event("one")).replace("DTEND;TZID=Europe/Berlin:20261019T110000", "DTEND:not-a-date"),
            calendar(event("one")).replace("Europe/Berlin", "Unknown/TimeZone"),
            calendar("BEGIN:VTODO\nUID:task\nSUMMARY:Task\nEND:VTODO"),
        )
        invalid.forEach { raw ->
            try { reader.read(raw); fail("Expected invalid calendar") } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun disabledOrReadOnlyDestinationIsRejectedWithoutFallback() = runTest {
        harness.repository.ensureLocalCalendar()
        val collection = harness.collection(LOCAL_COLLECTION)!!
        for (blocked in listOf(collection.copy(isEnabled = false), collection.copy(readOnly = true), collection.copy(supportsEvents = false))) {
            harness.database.collectionDao().upsertAll(listOf(blocked))
            try { imports.importEvents(calendar(event("one")), LOCAL_COLLECTION); fail("Expected unwritable destination") } catch (_: IllegalArgumentException) { }
        }
        assertTrue(harness.eventsIn(LOCAL_COLLECTION).isEmpty())
    }

    @Test fun utcRecurrenceAndImplicitAllDayEndStayCorrectAcrossDaylightSaving() = runTest {
        harness.repository.ensureLocalCalendar()
        val raw = calendar("""
            BEGIN:VEVENT
            UID:utc-series
            DTSTART:20261024T080000Z
            DTEND:20261024T090000Z
            RRULE:FREQ=DAILY;COUNT=3
            SUMMARY:UTC series
            END:VEVENT
            BEGIN:VEVENT
            UID:dst-day
            DTSTART;VALUE=DATE:20261025
            SUMMARY:Clock change day
            END:VEVENT
        """.trimIndent())
        imports.importEvents(raw, LOCAL_COLLECTION)
        val day = harness.eventsIn(LOCAL_COLLECTION).single { it.uid == "dst-day" }
        assertEquals(harness.startOfDay(java.time.LocalDate.of(2026, 10, 26)), day.endsAtMillis)
        val expanded = harness.repository.eventsSnapshot(
            harness.startOfDay(java.time.LocalDate.of(2026, 10, 24)),
            harness.startOfDay(java.time.LocalDate.of(2026, 10, 28)),
        ).filter { it.uid == "utc-series" }
        assertEquals(listOf("2026-10-24T08:00:00Z", "2026-10-25T08:00:00Z", "2026-10-26T08:00:00Z"),
            expanded.map { Instant.ofEpochMilli(it.startsAtMillis).toString() })
    }

    private fun calendar(body: String) = "BEGIN:VCALENDAR\nVERSION:2.0\nPRODID:-//KGS Test//EN\n$body\nEND:VCALENDAR"
    private fun event(uid: String, properties: String = "") =
        "BEGIN:VEVENT\nUID:$uid\nDTSTART;TZID=Europe/Berlin:20261019T100000\n" +
            "DTEND;TZID=Europe/Berlin:20261019T110000\nSUMMARY:Invitation\n$properties\nEND:VEVENT\n"
}
