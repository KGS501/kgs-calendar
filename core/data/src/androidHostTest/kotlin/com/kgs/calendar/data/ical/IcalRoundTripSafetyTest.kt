package com.kgs.calendar.data.ical

import com.kgs.calendar.data.local.entity.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Reading and writing back must not change what other clients stored. */
class IcalRoundTripSafetyTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val codec = IcalCodec(zone)

    private fun calendar(vararg eventLines: String, header: String = "") = buildString {
        append("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//Test//EN\r\n")
        append(header)
        append("BEGIN:VEVENT\r\nUID:probe\r\nDTSTAMP:20260101T000000Z\r\n")
        eventLines.forEach { append(it).append("\r\n") }
        append("END:VEVENT\r\nEND:VCALENDAR\r\n")
    }

    private val berlinMorning = arrayOf(
        "DTSTART;TZID=Europe/Berlin:20261105T090000",
        "DTEND;TZID=Europe/Berlin:20261105T100000",
        "SUMMARY:Probe",
    )

    private fun roundTrip(raw: String, codec: IcalCodec = this.codec): Pair<com.kgs.calendar.data.local.entity.EventEntity, String> {
        val event = codec.parse(raw, "/cal/", "/cal/probe.ics", 0)?.event ?: error("not parsed")
        return event to unfold(codec.serializeEvent(event.copy(sequence = event.sequence + 1), raw))
    }

    private fun unfold(raw: String) = raw.replace("\r\n ", "").replace("\r\n", "\n")

    private fun valarmBlocks(raw: String): List<String> =
        Regex("BEGIN:VALARM\n(.*?)END:VALARM", RegexOption.DOT_MATCHES_ALL).findAll(raw).map { it.groupValues[1] }.toList()

    @Test
    fun meetingLinksWithEqualsAndPercentSignsAreKept() {
        val description = "Join https://zoom.us/j/123?pwd=AbCdEf12 or https://x.org/a%20b, budget +20%BE, x=10"
        val location = "https://meet.example.com/room?id=C3PO"
        val (event, written) = roundTrip(calendar(*berlinMorning, "DESCRIPTION:${description.replace(",", "\\,")}", "LOCATION:$location"))

        assertEquals(description, event.description)
        assertEquals(location, event.location)
        assertTrue(written.contains("DESCRIPTION:${description.replace(",", "\\,")}"))
        assertTrue(written.contains("LOCATION:$location"))
    }

    @Test
    fun angleBracketsInPlainTextAreNotTreatedAsHtml() {
        val description = "Contact Anna <anna@example.com>; if a < b and c > d"
        val (event, _) = roundTrip(calendar(*berlinMorning, "DESCRIPTION:${description.replace(";", "\\;")}"))

        assertEquals(description, event.description)
    }

    @Test
    fun unchangedHtmlDescriptionIsWrittenBackAsItWas() {
        val original = "DESCRIPTION:<p>Agenda</p><p>Item &amp\\; more</p>"
        val (event, written) = roundTrip(calendar(*berlinMorning, original))

        assertEquals("Agenda\nItem & more", event.description)
        assertTrue(written.contains(original))
    }

    @Test
    fun changedTextReplacesTheOriginalValue() {
        val raw = calendar(*berlinMorning, "DESCRIPTION;LANGUAGE=de:Alt")
        val event = codec.parse(raw, "/cal/", "/cal/probe.ics", 0)!!.event!!
        val written = unfold(codec.serializeEvent(event.copy(description = "Neu"), raw))

        assertTrue(written.contains("DESCRIPTION:Neu"))
        assertFalse(written.contains("Alt"))
    }

    @Test
    fun reminderBeforeAnEventsEndIsKeptUnchanged() {
        val (event, written) = roundTrip(
            calendar(*berlinMorning, "BEGIN:VALARM", "ACTION:DISPLAY", "TRIGGER;RELATED=END:-PT15M", "DESCRIPTION:x", "END:VALARM"),
        )

        assertNull(event.remindersCsv)
        val alarms = valarmBlocks(written)
        assertEquals(1, alarms.size)
        assertTrue(alarms.single().contains("TRIGGER;RELATED=END:-PT15M"))
    }

    @Test
    fun reminderBeyondTheSupportedRangeIsKept() {
        val (event, written) = roundTrip(
            calendar(*berlinMorning, "BEGIN:VALARM", "ACTION:DISPLAY", "TRIGGER:-P5W", "DESCRIPTION:x", "END:VALARM"),
        )

        assertNull(event.remindersCsv)
        assertEquals(listOf(true), valarmBlocks(written).map { it.contains("TRIGGER:-P5W") })
    }

    @Test
    fun emailAlarmIsNotDuplicatedAsANotification() {
        val (event, written) = roundTrip(
            calendar(
                *berlinMorning,
                "BEGIN:VALARM", "ACTION:EMAIL", "TRIGGER:-PT30M", "SUMMARY:x", "DESCRIPTION:x",
                "ATTENDEE:mailto:a@example.com", "END:VALARM",
            ),
        )

        assertNull(event.remindersCsv)
        val alarms = valarmBlocks(written)
        assertEquals(1, alarms.size)
        assertTrue(alarms.single().contains("ACTION:EMAIL"))
    }

    @Test
    fun keptReminderKeepsItsOriginalAlarm() {
        val raw = calendar(
            *berlinMorning,
            "BEGIN:VALARM", "ACTION:AUDIO", "TRIGGER:-PT15M", "X-WR-ALARMUID:apple-1", "ATTACH;VALUE=URI:Chord", "END:VALARM",
        )
        val (event, written) = roundTrip(raw)

        assertEquals("15", event.remindersCsv)
        val alarms = valarmBlocks(written)
        assertEquals(1, alarms.size)
        assertTrue(alarms.single().contains("X-WR-ALARMUID:apple-1"))

        val changed = unfold(codec.serializeEvent(event.copy(remindersCsv = "30"), raw))
        val changedAlarms = valarmBlocks(changed)
        assertEquals(1, changedAlarms.size)
        assertTrue(changedAlarms.single().contains("TRIGGER:-PT30M"))
        assertFalse(changed.contains("apple-1"))

        val removed = unfold(codec.serializeEvent(event.copy(remindersCsv = null), raw))
        assertTrue(valarmBlocks(removed).isEmpty())
    }

    @Test
    fun startRelativeReminderOfATaskWithDueIsKept() {
        val raw = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VTODO
            UID:task-alarm-start
            DTSTART;TZID=Europe/Berlin:20261105T090000
            DUE;TZID=Europe/Berlin:20261105T170000
            SUMMARY:Task
            BEGIN:VALARM
            ACTION:DISPLAY
            TRIGGER:-PT15M
            DESCRIPTION:x
            END:VALARM
            END:VTODO
            END:VCALENDAR
        """.trimIndent().replace("\n", "\r\n")
        val task = codec.parse(raw, "/tasks/", "/tasks/t.ics", 0)!!.task!!
        val written = unfold(codec.serializeTask(task, raw))

        assertNull(task.remindersCsv)
        assertEquals(listOf(true), valarmBlocks(written).map { it.contains("TRIGGER:-PT15M") && !it.contains("RELATED") })
    }

    @Test
    fun floatingTimesStayFloating() {
        val (event, written) = roundTrip(calendar("DTSTART:20261105T090000", "DTEND:20261105T100000", "SUMMARY:Floating"))

        assertNull(event.timezoneId)
        assertTrue(written.contains("DTSTART:20261105T090000\n"))
        assertTrue(written.contains("DTEND:20261105T100000\n"))
    }

    @Test
    fun utcTimesStayUtc() {
        val (event, written) = roundTrip(calendar("DTSTART:20261105T080000Z", "DTEND:20261105T090000Z", "SUMMARY:UTC"))

        assertEquals("UTC", event.timezoneId)
        assertTrue(written.contains("DTSTART:20261105T080000Z"))
        assertFalse(written.contains("BEGIN:VTIMEZONE"))
    }

    @Test
    fun windowsZoneNamesResolveToTheirZone() {
        val newYorkDevice = IcalCodec(ZoneId.of("America/New_York"))
        val (event, written) = roundTrip(
            calendar(
                "DTSTART;TZID=W. Europe Standard Time:20261105T090000",
                "DTEND;TZID=W. Europe Standard Time:20261105T100000",
                "RRULE:FREQ=WEEKLY",
                "SUMMARY:Outlook",
                header = WINDOWS_BERLIN_VTIMEZONE,
            ),
            newYorkDevice,
        )

        assertEquals(Instant.parse("2026-11-05T08:00:00Z").toEpochMilli(), event.startsAtMillis)
        assertEquals("Europe/Berlin", event.timezoneId)
        assertTrue(written.contains("DTSTART;TZID=Europe/Berlin:20261105T090000"))
        // The Windows definition is no longer referenced, so it is not carried along.
        assertFalse(written.contains("W. Europe Standard Time"))
    }

    @Test
    fun unknownZoneNamesResolveThroughTheirDefinition() {
        val tzid = "(UTC+01:00) Amsterdam, Berlin, Bern, Rome, Stockholm, Vienna"
        val header = WINDOWS_BERLIN_VTIMEZONE.replace("TZID:W. Europe Standard Time", "TZID:$tzid")
        val event = IcalCodec(ZoneId.of("America/New_York")).parse(
            calendar(
                "DTSTART;TZID=\"$tzid\":20260705T090000",
                "DTEND;TZID=\"$tzid\":20260705T100000",
                "SUMMARY:Exchange",
                header = header,
            ),
            "/cal/",
            "/cal/probe.ics",
            0,
        )!!.event!!

        assertEquals(Instant.parse("2026-07-05T07:00:00Z").toEpochMilli(), event.startsAtMillis)
        assertEquals("Europe/Amsterdam", event.timezoneId)
    }

    @Test
    fun prefixedZoneIdsResolve() {
        val event = codec.parse(
            calendar(
                "DTSTART;TZID=/mozilla.org/20050126_1/America/New_York:20261105T090000",
                "DTEND;TZID=/mozilla.org/20050126_1/America/New_York:20261105T100000",
                "SUMMARY:Prefixed",
            ),
            "/cal/",
            "/cal/probe.ics",
            0,
        )!!.event!!

        assertEquals("America/New_York", event.timezoneId)
        assertEquals(Instant.parse("2026-11-05T14:00:00Z").toEpochMilli(), event.startsAtMillis)
    }

    @Test
    fun overrideWithUtcRecurrenceIdKeepsItsAttachments() {
        val raw = "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//Test//EN\r\n" +
            "BEGIN:VEVENT\r\nUID:probe\r\nDTSTAMP:20260101T000000Z\r\nDTSTART;TZID=Europe/Berlin:20261105T090000\r\n" +
            "DTEND;TZID=Europe/Berlin:20261105T100000\r\nRRULE:FREQ=WEEKLY\r\nSUMMARY:Series\r\nEND:VEVENT\r\n" +
            "BEGIN:VEVENT\r\nUID:probe\r\nDTSTAMP:20260101T000000Z\r\nRECURRENCE-ID:20261112T080000Z\r\n" +
            "DTSTART;TZID=Europe/Berlin:20261112T110000\r\nDTEND;TZID=Europe/Berlin:20261112T120000\r\nSUMMARY:Moved\r\n" +
            "X-CONFERENCE-URL:https://meet.example.com/x\r\nATTACH:https://files.example.com/agenda.pdf\r\nEND:VEVENT\r\n" +
            "END:VCALENDAR\r\n"
        val (_, written) = roundTrip(raw)

        val override = written.substringAfter("RECURRENCE-ID")
        assertTrue(override.contains("X-CONFERENCE-URL:https://meet.example.com/x"))
        assertTrue(override.contains("ATTACH:https://files.example.com/agenda.pdf"))
        assertEquals(1, Regex("X-CONFERENCE-URL").findAll(written).count())
    }

    @Test
    fun writtenTimeZonesOmitHistoricRules() {
        val (_, written) = roundTrip(calendar(*berlinMorning))
        val timezone = written.substringAfter("BEGIN:VTIMEZONE").substringBefore("END:VTIMEZONE")

        assertTrue(timezone.contains("TZID:Europe/Berlin"))
        assertTrue(timezone.contains("BYMONTH=3"))
        assertTrue(timezone.contains("BYMONTH=10"))
        assertFalse(timezone.contains("DTSTART:1893"))
        assertFalse(timezone.contains("DTSTART:1916"))
        // The two current rules and the one in force before them, instead of the full history.
        assertEquals(3, Regex("BEGIN:(STANDARD|DAYLIGHT)").findAll(timezone).count())
    }

    @Test
    fun newItemsGetCreatedAndLastModified() {
        val task = TaskEntity(
            uid = "new-task",
            collectionHref = "/tasks/",
            resourceHref = "/tasks/new-task.ics",
            title = "New",
            notes = null,
            dueAtMillis = LocalDate.of(2026, 11, 5).atTime(17, 0).atZone(zone).toInstant().toEpochMilli(),
            dueHasTime = true,
            startAtMillis = null,
            completedAtMillis = null,
            isCompleted = false,
            priority = null,
            timezoneId = zone.id,
            color = 0,
        )
        val written = codec.serializeTask(task)

        assertTrue(written.contains("\r\nCREATED:"))
        assertTrue(written.contains("\r\nLAST-MODIFIED:"))
    }

    @Test
    fun importAcceptsResolvableZonesAndRejectsUnknownOnes() {
        val reader = IcsEventReader(codec)
        val windows = calendar(
            "DTSTART;TZID=W. Europe Standard Time:20261105T090000",
            "DTEND;TZID=W. Europe Standard Time:20261105T100000",
            "SUMMARY:Outlook",
            header = WINDOWS_BERLIN_VTIMEZONE,
        )
        assertEquals("Europe/Berlin", reader.read(windows).single().event.timezoneId)

        val unknown = calendar("DTSTART;TZID=Mars/Olympus:20261105T090000", "DTEND;TZID=Mars/Olympus:20261105T100000", "SUMMARY:x")
        try {
            reader.read(unknown)
            fail("An unknown zone must not be imported in the device's zone.")
        } catch (expected: IllegalArgumentException) {
            assertNotNull(expected.message)
        }
    }

    @Test
    fun everyWindowsZoneMapsToAKnownZone() {
        WindowsZones.ids.forEach { (name, id) ->
            assertNotNull(name, TimeZoneResolver.resolveKnownId(id))
            assertEquals(name, ZoneId.of(id).normalized(), TimeZoneResolver.resolveKnownId(name)?.normalized())
        }
    }

    private companion object {
        val WINDOWS_BERLIN_VTIMEZONE = listOf(
            "BEGIN:VTIMEZONE",
            "TZID:W. Europe Standard Time",
            "BEGIN:STANDARD",
            "DTSTART:16010101T030000",
            "TZOFFSETFROM:+0200",
            "TZOFFSETTO:+0100",
            "RRULE:FREQ=YEARLY;BYDAY=-1SU;BYMONTH=10",
            "END:STANDARD",
            "BEGIN:DAYLIGHT",
            "DTSTART:16010101T020000",
            "TZOFFSETFROM:+0100",
            "TZOFFSETTO:+0200",
            "RRULE:FREQ=YEARLY;BYDAY=-1SU;BYMONTH=3",
            "END:DAYLIGHT",
            "END:VTIMEZONE",
        ).joinToString("\r\n", postfix = "\r\n")
    }
}
