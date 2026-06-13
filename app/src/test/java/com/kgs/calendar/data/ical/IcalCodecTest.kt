package com.kgs.calendar.data.ical

import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.domain.model.ComponentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.Instant

class IcalCodecTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val codec = IcalCodec(zone)

    @Test
    fun parsesAllDayEvent() {
        val raw = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:event-1
            DTSTART;VALUE=DATE:20260525
            DTEND;VALUE=DATE:20260526
            SUMMARY:Project day
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val parsed = codec.parse(raw, "/cal/", "/cal/event-1.ics", 0xff176b5d.toInt())

        assertNotNull(parsed)
        assertEquals(ComponentType.Event, parsed!!.componentType)
        assertTrue(parsed.event!!.allDay)
        assertEquals("Project day", parsed.event.title)
    }

    @Test
    fun serializesAndParsesCompletedTask() {
        val due = LocalDate.of(2026, 5, 25).atTime(17, 0).atZone(zone).toInstant().toEpochMilli()
        val task = TaskEntity(
            uid = "task-1",
            collectionHref = "/tasks/",
            resourceHref = "/tasks/task-1.ics",
            title = "Ship MVP",
            notes = "Verify sync",
            location = "Studio",
            url = "https://nextcloud.test/tasks/1",
            categories = "Music,Work",
            dueAtMillis = due,
            startAtMillis = null,
            completedAtMillis = due,
            isCompleted = true,
            priority = 5,
            percentComplete = 100,
            color = 0xff176b5d.toInt(),
        )

        val raw = codec.serializeTask(task)
        val parsed = codec.parse(raw, "/tasks/", "/tasks/task-1.ics", task.color)

        assertTrue(raw.contains("CATEGORIES:Music,Work"))
        assertNotNull(parsed)
        assertEquals(ComponentType.Task, parsed!!.componentType)
        assertTrue(parsed.task!!.isCompleted)
        assertEquals("Ship MVP", parsed.task.title)
        assertEquals(5, parsed.task.priority)
        assertEquals("Studio", parsed.task.location)
        assertEquals("https://nextcloud.test/tasks/1", parsed.task.url)
        assertEquals("Music,Work", parsed.task.categories)
        assertEquals(100, parsed.task.percentComplete)
    }

    @Test
    fun parsesAndSerializesNestedTaskRelation() {
        val raw = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VTODO
            UID:child-task
            SUMMARY:Nested task
            RELATED-TO;RELTYPE=PARENT:parent-task
            END:VTODO
            END:VCALENDAR
        """.trimIndent()

        val parsed = codec.parse(raw, "/tasks/", "/tasks/child-task.ics", 0xff176b5d.toInt())

        assertNotNull(parsed)
        val task = parsed!!.task!!
        assertEquals("parent-task", task.parentUid)

        val serialized = codec.serializeTask(task, raw)
        assertTrue(serialized.contains("RELATED-TO;RELTYPE=PARENT:parent-task"))

        val detached = codec.serializeTask(task.copy(parentUid = null), raw)
        assertFalse(detached.contains("RELATED-TO"))
    }

    @Test
    fun parsesMailboxTaskMetadata() {
        val raw = """
            BEGIN:VCALENDAR
            PRODID:Open-Xchange
            VERSION:2.0
            CALSCALE:GREGORIAN
            BEGIN:VTODO
            DTSTAMP:20260606T121332Z
            SUMMARY:Mailbox task
            DESCRIPTION:Detailed note
            DTSTART:20260607T120000Z
            DUE:20260607T130000Z
            CLASS:PUBLIC
            STATUS:IN-PROCESS
            PERCENT-COMPLETE:45
            PRIORITY:3
            CATEGORIES:Work
            CATEGORIES:Music\,Band,Practice
            UID:mailbox-task-1
            CREATED:20260606T121153Z
            LAST-MODIFIED:20260606T121158Z
            END:VTODO
            END:VCALENDAR
        """.trimIndent()

        val parsed = codec.parse(raw, "/tasks/", "/tasks/mailbox-task-1.ics", 0xff176b5d.toInt())

        assertNotNull(parsed)
        val task = parsed!!.task!!
        assertEquals("Mailbox task", task.title)
        assertEquals("Detailed note", task.notes)
        assertEquals("IN-PROCESS", task.status)
        assertEquals(3, task.priority)
        assertEquals(45, task.percentComplete)
        assertEquals("Work,Music,Band,Practice", task.categories)

        val serialized = codec.serializeTask(task, raw)
        assertTrue(serialized.contains("STATUS:IN-PROCESS"))
        assertTrue(serialized.contains("PRIORITY:3"))
        assertTrue(serialized.contains("PERCENT-COMPLETE:45"))
        assertTrue(serialized.contains("DESCRIPTION:Detailed note"))
        assertTrue(serialized.contains("CATEGORIES:Work,Music,Band,Practice"))
        assertTrue(serialized.contains("CREATED:20260606T121153Z"))
        assertTrue(serialized.contains("LAST-MODIFIED:20260606T121158Z"))
    }

    @Test
    fun parsesAndPreservesOpenXchangeTaskParticipantMetadata() {
        val raw = """
            BEGIN:VCALENDAR
            PRODID:-//Open-Xchange//8.48.98//EN
            VERSION:2.0
            CALSCALE:GREGORIAN
            BEGIN:VTODO
            DTSTAMP:20260606T121332Z
            UID:mailbox-waiting-task
            SUMMARY:Shared mailbox task
            DESCRIPTION:Detailed note
            DTSTART;TZID=Europe/Berlin:20260607T130000
            DUE;TZID=Europe/Berlin:20260607T143000
            CLASS:PRIVATE
            STATUS:WAITING
            PERCENT-COMPLETE:75
            PRIORITY:5
            CATEGORIES:Meeting,Business,Important
            ORGANIZER;CN="Owner":mailto:owner@example.test
            ATTENDEE;CN="f.rombi501";ROLE=REQ-PARTICIPANT;PARTSTAT=NEEDS-ACTION:mailto:f.rombi501@gmail.com
            END:VTODO
            END:VCALENDAR
        """.trimIndent()

        val parsed = codec.parse(raw, "/tasks/", "/tasks/mailbox-waiting-task.ics", 0xff176b5d.toInt())

        assertNotNull(parsed)
        val task = parsed!!.task!!
        assertEquals("Shared mailbox task", task.title)
        assertEquals("WAITING", task.status)
        assertEquals(75, task.percentComplete)
        assertEquals(5, task.priority)
        assertEquals("Meeting,Business,Important", task.categories)

        val serialized = codec.serializeTask(task.copy(title = "Edited shared mailbox task"), raw)
        val unfolded = serialized.replace("\r\n ", "").replace("\n ", "")
        assertTrue(unfolded.contains("STATUS:WAITING"))
        assertTrue(unfolded.contains("CLASS:PRIVATE"))
        assertTrue(unfolded.contains("ORGANIZER;CN=\"Owner\":mailto:owner@example.test"))
        assertTrue(unfolded.contains("ATTENDEE;CN=\"f.rombi501\";ROLE=REQ-PARTICIPANT;PARTSTAT=NEEDS-ACTION:mailto:f.rombi501@gmail.com"))
    }

    @Test
    fun parsesDateOnlyTaskAsFullDayTask() {
        val raw = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VTODO
            UID:task-date-only
            DUE;VALUE=DATE:20260525
            SUMMARY:Renew documents
            STATUS:NEEDS-ACTION
            END:VTODO
            END:VCALENDAR
        """.trimIndent()

        val parsed = codec.parse(raw, "/tasks/", "/tasks/task-date-only.ics", 0xff176b5d.toInt())

        assertNotNull(parsed)
        assertEquals(ComponentType.Task, parsed!!.componentType)
        assertFalse(parsed.task!!.dueHasTime)
        assertEquals("Renew documents", parsed.task.title)
    }

    @Test
    fun serializingTimedStartWithStaleDateOnlyDueOmitsDue() {
        val start = LocalDate.of(2026, 5, 27).atTime(14, 15).atZone(zone).toInstant().toEpochMilli()
        val staleDue = LocalDate.of(2026, 5, 27).atStartOfDay(zone).toInstant().toEpochMilli()
        val task = TaskEntity(
            uid = "task-start-only",
            collectionHref = "/tasks/",
            resourceHref = "/tasks/task-start-only.ics",
            title = "Start-only task",
            notes = null,
            dueAtMillis = staleDue,
            dueHasTime = false,
            startAtMillis = start,
            startHasTime = true,
            completedAtMillis = null,
            isCompleted = false,
            priority = null,
            color = 0xff176b5d.toInt(),
        )

        val raw = codec.serializeTask(task)

        assertTrue(raw.contains("DTSTART:20260527T121500Z"))
        assertFalse(raw.contains("\nDUE"))
    }

    @Test
    fun serializingTimedDueWithStaleDateOnlyStartOmitsStart() {
        val staleStart = LocalDate.of(2026, 5, 27).atStartOfDay(zone).toInstant().toEpochMilli()
        val due = LocalDate.of(2026, 5, 27).atTime(14, 45).atZone(zone).toInstant().toEpochMilli()
        val task = TaskEntity(
            uid = "task-end-only",
            collectionHref = "/tasks/",
            resourceHref = "/tasks/task-end-only.ics",
            title = "End-only task",
            notes = null,
            dueAtMillis = due,
            dueHasTime = true,
            startAtMillis = staleStart,
            startHasTime = false,
            completedAtMillis = null,
            isCompleted = false,
            priority = null,
            color = 0xff176b5d.toInt(),
        )

        val raw = codec.serializeTask(task)

        assertFalse(raw.contains("\nDTSTART"))
        assertTrue(raw.contains("DUE:20260527T124500Z"))
    }

    @Test
    fun eventRoundTripKeepsTimeFields() {
        val start = LocalDate.of(2026, 5, 25).atTime(9, 30).atZone(zone).toInstant().toEpochMilli()
        val end = LocalDate.of(2026, 5, 25).atTime(10, 45).atZone(zone).toInstant().toEpochMilli()
        val event = EventEntity(
            uid = "event-2",
            collectionHref = "/cal/",
            resourceHref = "/cal/event-2.ics",
            title = "Review",
            description = "Calendar implementation",
            location = "Remote",
            startsAtMillis = start,
            endsAtMillis = end,
            allDay = false,
            recurrenceRule = null,
            isRecurring = false,
            color = 0xff176b5d.toInt(),
        )

        val raw = codec.serializeEvent(event)
        val parsed = codec.parse(raw, "/cal/", "/cal/event-2.ics", event.color)

        assertNotNull(parsed)
        assertFalse(parsed!!.event!!.allDay)
        assertEquals("Review", parsed.event.title)
        assertEquals("Remote", parsed.event.location)
        assertTrue(parsed.event.endsAtMillis > parsed.event.startsAtMillis)
    }

    @Test
    fun parsesQuotedPrintableDescriptionText() {
        val raw = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:event-qp
            DTSTART:20260525T090000Z
            DTEND:20260525T100000Z
            SUMMARY:Omnify Meeting
            DESCRIPTION;ENCODING=QUOTED-PRINTABLE;CHARSET=UTF-8:Gr=C3=BC=C3=9Fe aus K=C3=B6ln
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val parsed = codec.parse(raw, "/cal/", "/cal/event-qp.ics", 0xff176b5d.toInt())

        assertNotNull(parsed)
        assertEquals("Grüße aus Köln", parsed!!.event!!.description)
    }

    @Test
    fun parsesPercentEncodedHtmlDescriptionText() {
        val raw = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:event-html
            DTSTART:20260525T090000Z
            DTEND:20260525T100000Z
            SUMMARY:Omnify Meeting
            DESCRIPTION:text/html,Lukas%20Roth%20l%C3%A4dt%20ein.%3Cbr%3EThema%3A%20Omnify
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val parsed = codec.parse(raw, "/cal/", "/cal/event-html.ics", 0xff176b5d.toInt())

        assertNotNull(parsed)
        assertEquals("Lukas Roth lädt ein.\nThema: Omnify", parsed!!.event!!.description)
    }

    @Test
    fun parsesEventWithNestedValarmWithoutDroppingTrailingProperties() {
        val raw = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:event-alarm
            DTSTART:20260525T090000Z
            BEGIN:VALARM
            ACTION:DISPLAY
            TRIGGER:-PT15M
            END:VALARM
            DTEND:20260525T100000Z
            RRULE:FREQ=WEEKLY;BYDAY=MO
            SUMMARY:Standup
            DESCRIPTION:After the alarm block
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val parsed = codec.parse(raw, "/cal/", "/cal/event-alarm.ics", 0xff176b5d.toInt())

        assertNotNull(parsed)
        val event = parsed!!.event!!
        assertEquals("Standup", event.title)
        assertEquals("After the alarm block", event.description)
        assertEquals("FREQ=WEEKLY;BYDAY=MO", event.recurrenceRule)
        assertTrue(event.isRecurring)
        // DTEND was after VALARM, ensure it was kept (end > start).
        assertTrue(event.endsAtMillis > event.startsAtMillis)
    }

    @Test
    fun parsesMasterVeventWhenResourceContainsRecurrenceIdOverride() {
        val raw = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:event-series
            DTSTART:20260525T090000Z
            DTEND:20260525T100000Z
            RRULE:FREQ=WEEKLY;BYDAY=MO
            SUMMARY:Series master
            END:VEVENT
            BEGIN:VEVENT
            UID:event-series
            RECURRENCE-ID:20260601T090000Z
            DTSTART:20260601T110000Z
            DTEND:20260601T120000Z
            SUMMARY:Override instance
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val parsed = codec.parse(raw, "/cal/", "/cal/event-series.ics", 0xff176b5d.toInt())

        assertNotNull(parsed)
        assertEquals("Series master", parsed!!.event!!.title)
        assertEquals("FREQ=WEEKLY;BYDAY=MO", parsed.event.recurrenceRule)
    }

    @Test
    fun parsesExDatesFromEvent() {
        val raw = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:event-skip
            DTSTART:20260525T090000Z
            DTEND:20260525T100000Z
            RRULE:FREQ=WEEKLY;BYDAY=MO
            EXDATE:20260601T090000Z,20260608T090000Z
            SUMMARY:With skips
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val parsed = codec.parse(raw, "/cal/", "/cal/event-skip.ics", 0xff176b5d.toInt())

        assertNotNull(parsed)
        val ex = parsed!!.event!!.exDatesCsv!!.split(",").mapNotNull { it.toLongOrNull() }
        assertEquals(2, ex.size)
    }

    @Test
    fun ignoresTopLevelVtimezoneBlock() {
        val raw = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VTIMEZONE
            TZID:Europe/Berlin
            BEGIN:STANDARD
            DTSTART:19701025T030000
            END:STANDARD
            BEGIN:DAYLIGHT
            DTSTART:19700329T020000
            END:DAYLIGHT
            END:VTIMEZONE
            BEGIN:VEVENT
            UID:event-tz
            DTSTART:20260525T090000Z
            DTEND:20260525T100000Z
            SUMMARY:After timezone
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val parsed = codec.parse(raw, "/cal/", "/cal/event-tz.ics", 0xff176b5d.toInt())

        assertNotNull(parsed)
        assertEquals("After timezone", parsed!!.event!!.title)
    }

    @Test
    fun parsesValarmReminderOffsets() {
        val raw = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:event-alarm-offsets
            DTSTART:20260525T090000Z
            DTEND:20260525T100000Z
            SUMMARY:Meeting
            BEGIN:VALARM
            ACTION:DISPLAY
            TRIGGER:-PT15M
            END:VALARM
            BEGIN:VALARM
            ACTION:DISPLAY
            TRIGGER:-P1D
            END:VALARM
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val parsed = codec.parse(raw, "/cal/", "/cal/event-alarm-offsets.ics", 0xff176b5d.toInt())

        assertNotNull(parsed)
        val minutes = parsed!!.event!!.remindersCsv!!.split(",").mapNotNull { it.toIntOrNull() }.sorted()
        assertEquals(listOf(15, 1440), minutes)
    }

    @Test
    fun eventReminderRoundTrip() {
        val start = LocalDate.of(2026, 5, 25).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val event = EventEntity(
            uid = "event-reminders",
            collectionHref = "/cal/",
            resourceHref = "/cal/event-reminders.ics",
            title = "Standup",
            description = null,
            location = null,
            startsAtMillis = start,
            endsAtMillis = start + 3_600_000,
            allDay = false,
            recurrenceRule = null,
            isRecurring = false,
            remindersCsv = "0,30,1440",
            color = 0xff176b5d.toInt(),
        )

        val raw = codec.serializeEvent(event)
        assertTrue(raw.contains("BEGIN:VALARM"))
        val parsed = codec.parse(raw, "/cal/", event.resourceHref, event.color)
        val minutes = parsed!!.event!!.remindersCsv!!.split(",").mapNotNull { it.toIntOrNull() }.sorted()
        assertEquals(listOf(0, 30, 1440), minutes)
    }

    @Test
    fun eventMetadataRoundTripKeepsNextcloudFields() {
        val raw = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:event-nextcloud-meta
            DTSTART:20260525T090000Z
            DTEND:20260525T100000Z
            SUMMARY:Shared planning
            STATUS:TENTATIVE
            CLASS:CONFIDENTIAL
            TRANSP:TRANSPARENT
            CATEGORIES:Music,Work
            COLOR:#336699
            ORGANIZER;CN="Host Person":mailto:host@example.com
            ATTENDEE;CN="Alice";ROLE=REQ-PARTICIPANT;PARTSTAT=ACCEPTED;RSVP=TRUE:mailto:alice@example.com
            ATTENDEE;CN="Bob";ROLE=OPT-PARTICIPANT;PARTSTAT=NEEDS-ACTION;SCHEDULE-STATUS=3.7:mailto:bob@example.com
            BEGIN:VALARM
            ACTION:DISPLAY
            TRIGGER:-PT45M
            END:VALARM
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val parsed = codec.parse(raw, "/cal/", "/cal/event-nextcloud-meta.ics", 0xff176b5d.toInt())

        assertNotNull(parsed)
        val event = parsed!!.event!!
        assertEquals("TENTATIVE", event.status)
        assertEquals("CONFIDENTIAL", event.classification)
        assertEquals("TRANSPARENT", event.transparency)
        assertEquals("Music,Work", event.categories)
        assertEquals(0xff336699.toInt(), event.manualColor)
        assertEquals("45", event.remindersCsv)

        assertTrue(event.organizerJson!!.contains("\"name\":\"Host Person\""))
        assertTrue(event.organizerJson.contains("\"email\":\"host@example.com\""))
        assertTrue(event.attendeesJson!!.contains("\"name\":\"Alice\""))
        assertTrue(event.attendeesJson.contains("\"partstat\":\"ACCEPTED\""))
        assertTrue(event.attendeesJson.contains("\"rsvp\":\"TRUE\""))
        assertTrue(event.attendeesJson.contains("\"role\":\"OPT-PARTICIPANT\""))
        assertTrue(event.attendeesJson.contains("\"scheduleStatus\":\"3.7\""))

        val serialized = codec.serializeEvent(event)
        assertTrue(serialized.contains("STATUS:TENTATIVE"))
        assertTrue(serialized.contains("CLASS:CONFIDENTIAL"))
        assertTrue(serialized.contains("TRANSP:TRANSPARENT"))
        assertTrue(serialized.contains("CATEGORIES:Music,Work"))
        assertTrue(serialized.contains("COLOR:#336699"))
        assertTrue(serialized.contains("ORGANIZER"))
        assertTrue(serialized.contains("mailto:host@example.com"))
        assertTrue(serialized.contains("ATTENDEE"))
        assertTrue(serialized.contains("PARTSTAT=ACCEPTED"))
        assertTrue(serialized.replace("\r\n ", "").contains("SCHEDULE-STATUS=\"3.7\""))
        assertTrue(serialized.contains("TRIGGER:-PT45M"))
    }

    @Test
    fun taskRoundTripKeepsRecurrenceRule() {
        val due = LocalDate.of(2026, 5, 25).atTime(17, 0).atZone(zone).toInstant().toEpochMilli()
        val task = TaskEntity(
            uid = "task-recurring",
            collectionHref = "/tasks/",
            resourceHref = "/tasks/task-recurring.ics",
            title = "Practice",
            notes = null,
            dueAtMillis = due,
            startAtMillis = null,
            completedAtMillis = null,
            isCompleted = false,
            priority = 4,
            recurrenceRule = "FREQ=WEEKLY;BYDAY=MO",
            color = 0xff176b5d.toInt(),
        )

        val raw = codec.serializeTask(task)
        val parsed = codec.parse(raw, "/tasks/", task.resourceHref, task.color)

        assertNotNull(parsed)
        assertEquals("FREQ=WEEKLY;BYDAY=MO", parsed!!.task!!.recurrenceRule)
        assertTrue(raw.contains("RRULE:FREQ=WEEKLY;BYDAY=MO"))
    }

    @Test
    fun parsesTzidDurationRdateAndSequence() {
        val raw = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:event-interoperable
            SEQUENCE:4
            DTSTART;TZID=Europe/Berlin:20261024T090000
            DURATION:PT90M
            RRULE:FREQ=DAILY;COUNT=3
            RDATE;TZID=Europe/Berlin:20261030T090000
            SUMMARY:Timezone test
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val event = codec.parse(raw, "/cal/", "/cal/event.ics", 0xff176b5d.toInt())!!.event!!

        assertEquals("Europe/Berlin", event.timezoneId)
        assertEquals(4, event.sequence)
        assertEquals(90L * 60L * 1000L, event.endsAtMillis - event.startsAtMillis)
        assertNotNull(event.rDatesCsv)
    }

    @Test
    fun parsesAndExpandsRecurrenceIdOverride() {
        val raw = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:event-series-override
            DTSTART;TZID=Europe/Berlin:20261024T090000
            DTEND;TZID=Europe/Berlin:20261024T100000
            RRULE:FREQ=DAILY;COUNT=3
            SUMMARY:Master
            END:VEVENT
            BEGIN:VEVENT
            UID:event-series-override
            RECURRENCE-ID;TZID=Europe/Berlin:20261025T090000
            DTSTART;TZID=Europe/Berlin:20261025T120000
            DTEND;TZID=Europe/Berlin:20261025T130000
            SUMMARY:Moved occurrence
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        val event = codec.parse(raw, "/cal/", "/cal/event.ics", 0xff176b5d.toInt())!!.event!!
        val expander = com.kgs.calendar.data.recurrence.RecurrenceExpander(zone)
        val from = Instant.parse("2026-10-23T00:00:00Z").toEpochMilli()
        val to = Instant.parse("2026-10-28T00:00:00Z").toEpochMilli()

        val occurrences = expander.expand(event, from, to)

        assertEquals(3, occurrences.size)
        assertEquals("Moved occurrence", occurrences[1].title)
        assertEquals(12, Instant.ofEpochMilli(occurrences[1].startsAtMillis).atZone(zone).hour)
    }

    @Test
    fun editingPreservesUnknownPropertiesAndUnsupportedAlarm() {
        val raw = """
            BEGIN:VCALENDAR
            VERSION:2.0
            METHOD:PUBLISH
            BEGIN:VEVENT
            UID:event-preserve
            DTSTART:20260525T090000Z
            DTEND:20260525T100000Z
            SUMMARY:Before
            ATTACH;FMTTYPE=application/pdf:https://example.test/file.pdf
            CONFERENCE;VALUE=URI:https://meet.example.test/room
            X-SERVER-CUSTOM:keep-me
            BEGIN:VALARM
            ACTION:EMAIL
            TRIGGER:-PT30M
            ATTENDEE:mailto:alice@example.test
            SUMMARY:Mail reminder
            END:VALARM
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        val event = codec.parse(raw, "/cal/", "/cal/event.ics", 0xff176b5d.toInt())!!.event!!

        val serialized = codec.serializeEvent(event.copy(title = "After", sequence = 1), raw)

        assertTrue(serialized.contains("SUMMARY:After"))
        assertTrue(serialized.contains("METHOD:PUBLISH"))
        assertTrue(serialized.contains("ATTACH;FMTTYPE=application/pdf"))
        assertTrue(serialized.contains("CONFERENCE;VALUE=URI"))
        assertTrue(serialized.contains("X-SERVER-CUSTOM:keep-me"))
        assertTrue(serialized.contains("ACTION:EMAIL"))
    }

    @Test
    fun parsesAndSerializesVtodoRecurrenceOverride() {
        val raw = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VTODO
            UID:task-series
            DTSTART;TZID=Europe/Berlin:20260601T090000
            DUE;TZID=Europe/Berlin:20260601T100000
            RRULE:FREQ=DAILY;COUNT=2
            SUMMARY:Master task
            END:VTODO
            BEGIN:VTODO
            UID:task-series
            RECURRENCE-ID;TZID=Europe/Berlin:20260602T090000
            DTSTART;TZID=Europe/Berlin:20260602T120000
            DUE;TZID=Europe/Berlin:20260602T130000
            SUMMARY:Moved task
            END:VTODO
            END:VCALENDAR
        """.trimIndent()

        val task = codec.parse(raw, "/tasks/", "/tasks/task.ics", 0xff176b5d.toInt())!!.task!!
        val serialized = codec.serializeTask(task, raw)

        assertNotNull(task.recurrenceOverridesJson)
        assertTrue(serialized.contains("RECURRENCE-ID;TZID=Europe/Berlin:20260602T090000"))
        assertTrue(serialized.contains("SUMMARY:Moved task"))
    }

    @Test
    fun taskAlarmIsRelativeToDue() {
        val due = LocalDate.of(2026, 6, 1).atTime(17, 0).atZone(zone).toInstant().toEpochMilli()
        val task = TaskEntity(
            uid = "task-alarm",
            collectionHref = "/tasks/",
            resourceHref = "/tasks/task-alarm.ics",
            title = "Deadline",
            notes = null,
            dueAtMillis = due,
            startAtMillis = null,
            completedAtMillis = null,
            isCompleted = false,
            priority = null,
            remindersCsv = "30",
            timezoneId = zone.id,
            color = 0xff176b5d.toInt(),
        )

        val raw = codec.serializeTask(task)

        assertTrue(raw.contains("TRIGGER;RELATED=END:-PT30M"))
    }

    @Test
    fun serializedLinesAreUtf8FoldedAndRoundTrip() {
        val start = LocalDate.of(2026, 6, 1).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val title = "Very long title with umlauts äöü and emoji \uD83D\uDCC5 ".repeat(4)
        val event = EventEntity(
            uid = "event-folding",
            collectionHref = "/cal/",
            resourceHref = "/cal/event-folding.ics",
            title = title,
            description = null,
            location = null,
            startsAtMillis = start,
            endsAtMillis = start + 3_600_000,
            allDay = false,
            recurrenceRule = null,
            isRecurring = false,
            timezoneId = zone.id,
            color = 0xff176b5d.toInt(),
        )

        val raw = codec.serializeEvent(event)
        val parsed = codec.parse(raw, "/cal/", event.resourceHref, event.color)!!.event!!

        assertTrue(raw.replace("\r\n", "\n").lines().filter { it.isNotEmpty() }.all { it.toByteArray().size <= 75 })
        assertEquals(title, parsed.title)
    }

    @Test
    fun parsesQuotedParticipantParametersContainingSeparators() {
        val raw = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:event-quoted-params
            DTSTART:20260601T090000Z
            DTEND:20260601T100000Z
            SUMMARY:Meeting
            ATTENDEE;CN="Doe; Alice";DIR="https://directory.example.test/users:alice";PARTSTAT=ACCEPTED:mailto:alice@example.test
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val event = codec.parse(raw, "/cal/", "/cal/event.ics", 0xff176b5d.toInt())!!.event!!

        assertTrue(event.attendeesJson!!.contains("Doe; Alice"))
        assertTrue(event.attendeesJson.contains("alice@example.test"))
    }
}
