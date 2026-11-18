package com.kgs.calendar.data.ical

import com.kgs.calendar.data.recurrence.RecurrenceExpander
import com.kgs.calendar.data.recurrence.TaskRecurrenceExpander
import com.kgs.calendar.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.system.measureTimeMillis

@RunWith(RobolectricTestRunner::class)
class AuditCodecRegressionTest {
    private val utc = ZoneId.of("UTC")
    private val codec = IcalCodec(utc)
    private val expander = RecurrenceExpander(utc)

    private fun calendar(vararg lines: String) =
        (listOf("BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//test//EN") + lines + "END:VCALENDAR").joinToString("\r\n", postfix = "\r\n")

    private fun millis(value: String) = kotlin.time.Instant.parse(value).toEpochMilliseconds()

    private fun parseEvent(raw: String) = codec.parse(raw, "/c/", "/c/r.ics", 0)!!.event!!

    private fun parseTask(raw: String) = codec.parse(raw, "/c/", "/c/r.ics", 0)!!.task!!

    private val master = arrayOf(
        "BEGIN:VEVENT", "UID:series", "DTSTAMP:20260101T000000Z", "DTSTART:20261102T090000Z", "DTEND:20261102T100000Z",
        "RRULE:FREQ=DAILY;COUNT=3", "SUMMARY:Series", "END:VEVENT",
    )
    private val exception = arrayOf(
        "BEGIN:VEVENT", "UID:series", "DTSTAMP:20260101T000000Z", "RECURRENCE-ID:20261103T090000Z",
        "DTSTART:20261103T120000Z", "DTEND:20261103T130000Z", "SUMMARY:Moved", "END:VEVENT",
    )

    // --- #71 / #75 ----------------------------------------------------------------------------------

    private fun assertSeriesKept(raw: String) {
        val parsed = codec.parseAll(raw, "/c/", "/c", 0).single().event!!
        val occurrences = expander.expand(parsed, millis("2026-11-02T00:00:00Z"), millis("2026-11-05T00:00:00Z"))
        assertEquals(
            listOf("2026-11-02T09:00:00Z", "2026-11-03T12:00:00Z", "2026-11-04T09:00:00Z").map(::millis),
            occurrences.map { it.startsAtMillis },
        )
    }

    @Test
    fun aFeedKeepsMasterAndExceptionInEitherOrder() {
        assertSeriesKept(calendar(*master, *exception))
        assertSeriesKept(calendar(*exception, *master))
    }

    @Test
    fun aFeedSeriesSurvivesBeingStoredAndReadBack() {
        val parsed = codec.parseAll(calendar(*master, *exception), "/c/", "/c", 0).single().event!!

        val reread = parseEvent(codec.serializeEvent(parsed))

        assertEquals(parsed.recurrenceRule, reread.recurrenceRule)
        assertEquals(1, RecurrenceOverrideCodec.decodeEvents(reread.recurrenceOverridesJson).size)
    }

    @Test
    fun aRecurringFeedTaskKeepsItsException() {
        val raw = calendar(
            "BEGIN:VTODO", "UID:todo", "DTSTAMP:20260101T000000Z", "DUE:20261102T090000Z", "RRULE:FREQ=DAILY;COUNT=2", "SUMMARY:Task", "END:VTODO",
            "BEGIN:VTODO", "UID:todo", "DTSTAMP:20260101T000000Z", "RECURRENCE-ID:20261103T090000Z", "DUE:20261103T100000Z", "SUMMARY:Later", "END:VTODO",
        )

        val task = codec.parseAll(raw, "/c/", "/c", 0).single().task!!

        assertEquals(1, RecurrenceOverrideCodec.decodeTasks(task.recurrenceOverridesJson).size)
    }

    private fun event(uid: String) = arrayOf(
        "BEGIN:VEVENT", "UID:$uid", "DTSTAMP:20260101T000000Z", "DTSTART:20261102T090000Z", "SUMMARY:x", "END:VEVENT",
    )

    @Test
    fun distinctUidsNeverShareAFeedKey() {
        val long = "x".repeat(120)
        val raw = calendar(*event("a@b"), *event("a_b"), *event(long + "1"), *event(long + "2"), *event("ü"), *event("ö"))

        val hrefs = codec.parseAll(raw, "/c/", "/c", 0).map { it.event!!.resourceHref }

        assertEquals(6, hrefs.toSet().size)
    }

    // --- #89 ------------------------------------------------------------------------------------

    private val rangeSeries = calendar(
        "BEGIN:VEVENT", "UID:range", "DTSTAMP:20260101T000000Z", "DTSTART:20261102T090000Z", "DTEND:20261102T100000Z",
        "RRULE:FREQ=DAILY;COUNT=4", "SUMMARY:Series", "END:VEVENT",
        "BEGIN:VEVENT", "UID:range", "DTSTAMP:20260101T000000Z", "RECURRENCE-ID;RANGE=THISANDFUTURE:20261103T090000Z",
        "DTSTART:20261103T110000Z", "DTEND:20261103T120000Z", "SUMMARY:Series", "END:VEVENT",
    )

    @Test
    fun rangeExceptionAppliesToLaterOccurrencesAndSurvivesSave() {
        val event = parseEvent(rangeSeries)

        val starts = expander.expand(event, millis("2026-11-02T00:00:00Z"), millis("2026-11-06T00:00:00Z")).map { it.startsAtMillis }

        assertEquals(
            listOf("2026-11-02T09:00:00Z", "2026-11-03T11:00:00Z", "2026-11-04T11:00:00Z", "2026-11-05T11:00:00Z").map(::millis),
            starts,
        )
        val saved = codec.serializeEvent(event.copy(title = "Renamed"), rangeSeries)
        assertTrue(saved.contains("RANGE=THISANDFUTURE"))
        assertEquals(starts, expander.expand(parseEvent(saved), millis("2026-11-02T00:00:00Z"), millis("2026-11-06T00:00:00Z")).map { it.startsAtMillis })
    }

    @Test
    fun aLaterExceptionWinsOverARangeAndIdentityStaysOriginal() {
        val raw = rangeSeries.replace(
            "END:VCALENDAR",
            listOf(
                "BEGIN:VEVENT", "UID:range", "DTSTAMP:20260101T000000Z", "RECURRENCE-ID:20261104T090000Z",
                "DTSTART:20261104T150000Z", "DTEND:20261104T160000Z", "SUMMARY:Single", "END:VEVENT", "END:VCALENDAR",
            ).joinToString("\r\n"),
        )

        val occurrences = expander.expandWithIdentity(parseEvent(raw), millis("2026-11-02T00:00:00Z"), millis("2026-11-06T00:00:00Z"))

        assertEquals(millis("2026-11-04T15:00:00Z"), occurrences[2].item.startsAtMillis)
        assertEquals(millis("2026-11-04T09:00:00Z"), occurrences[2].occurrenceId.recurrenceIdMillis)
        assertEquals(millis("2026-11-05T11:00:00Z"), occurrences[3].item.startsAtMillis)
        assertEquals(millis("2026-11-05T09:00:00Z"), occurrences[3].occurrenceId.recurrenceIdMillis)
    }

    @Test
    fun aCancelledRangeEndsTheSeries() {
        val raw = rangeSeries.replace("DTSTART:20261103T110000Z", "STATUS:CANCELLED\r\nDTSTART:20261103T110000Z")

        val starts = expander.expand(parseEvent(raw), millis("2026-11-02T00:00:00Z"), millis("2026-11-06T00:00:00Z")).map { it.startsAtMillis }

        assertEquals(listOf(millis("2026-11-02T09:00:00Z")), starts)
    }

    @Test
    fun aRangeKeepsItsWallClockAcrossDst() {
        val berlin = ZoneId.of("Europe/Berlin")
        val raw = calendar(
            "BEGIN:VEVENT", "UID:dst", "DTSTAMP:20260101T000000Z", "DTSTART;TZID=Europe/Berlin:20261023T090000",
            "DTEND;TZID=Europe/Berlin:20261023T100000", "RRULE:FREQ=WEEKLY;COUNT=3", "SUMMARY:Weekly", "END:VEVENT",
            "BEGIN:VEVENT", "UID:dst", "DTSTAMP:20260101T000000Z", "RECURRENCE-ID;TZID=Europe/Berlin;RANGE=THISANDFUTURE:20261023T090000",
            "DTSTART;TZID=Europe/Berlin:20261023T110000", "DTEND;TZID=Europe/Berlin:20261023T120000", "SUMMARY:Weekly", "END:VEVENT",
        )

        val starts = RecurrenceExpander(berlin).expand(parseEvent(raw), millis("2026-10-20T00:00:00Z"), millis("2026-11-10T00:00:00Z"))
            .map { it.startsAtMillis }

        // 11:00 in Berlin: CEST (UTC+2) in October, CET (UTC+1) in November.
        assertEquals(listOf("2026-10-23T09:00:00Z", "2026-10-30T10:00:00Z", "2026-11-06T10:00:00Z").map(::millis), starts)
    }

    @Test
    fun aRangeOnATaskMovesItsLaterOccurrences() {
        val raw = calendar(
            "BEGIN:VTODO", "UID:todo", "DTSTAMP:20260101T000000Z", "DUE:20261102T090000Z", "RRULE:FREQ=DAILY;COUNT=3", "SUMMARY:Task", "END:VTODO",
            "BEGIN:VTODO", "UID:todo", "DTSTAMP:20260101T000000Z", "RECURRENCE-ID;RANGE=THISANDFUTURE:20261103T090000Z", "DUE:20261103T110000Z", "SUMMARY:Task", "END:VTODO",
        )

        val due = TaskRecurrenceExpander(expander).expand(parseTask(raw), millis("2026-11-02T00:00:00Z"), millis("2026-11-06T00:00:00Z"))
            .map { it.dueAtMillis }

        assertEquals(listOf("2026-11-02T09:00:00Z", "2026-11-03T11:00:00Z", "2026-11-04T11:00:00Z").map(::millis), due)
    }

    // --- #90 / #91 / #92 ---------------------------------------------------------------------------

    private fun attendeeCopy(organizer: String) = calendar(
        "BEGIN:VEVENT", "UID:invite", "DTSTAMP:20260101T000000Z", "DTSTART:20261102T090000Z", "DTEND:20261102T100000Z",
        "SUMMARY:Meeting", organizer, "END:VEVENT",
    )

    @Test
    fun organizerSchedulingParametersSurviveUnrelatedEdit() {
        listOf("CLIENT", "NONE", "SERVER").forEach { agent ->
            val raw = attendeeCopy("ORGANIZER;CN=Host;SCHEDULE-AGENT=$agent;SCHEDULE-STATUS=2.0:mailto:host@example.test")

            val saved = codec.serializeEvent(parseEvent(raw).copy(description = "Changed"), raw).replace("\r\n ", "")

            assertTrue(saved, saved.contains("SCHEDULE-AGENT=$agent"))
            assertTrue(saved, saved.contains("SCHEDULE-STATUS=2.0"))
        }
    }

    @Test
    fun aDifferentOrganizerDoesNotInheritSchedulingParameters() {
        val raw = attendeeCopy("ORGANIZER;SCHEDULE-AGENT=CLIENT:mailto:host@example.test")
        val other = com.kgs.calendar.domain.model.ParticipantJson.encodeOrganizer(
            com.kgs.calendar.domain.model.Organizer(email = "new@example.test", name = null, sentBy = null, directory = null, language = null),
        )

        val saved = codec.serializeEvent(parseEvent(raw).copy(organizerJson = other), raw)

        assertFalse(saved, saved.contains("SCHEDULE-AGENT"))
    }

    private val relatedTask = calendar(
        "BEGIN:VTODO", "UID:todo", "DTSTAMP:20260101T000000Z", "SUMMARY:Task",
        "RELATED-TO;RELTYPE=PARENT:parent", "RELATED-TO;RELTYPE=CHILD:child", "RELATED-TO;RELTYPE=SIBLING:sibling",
        "RELATED-TO;RELTYPE=X-CUSTOM;X-P=1:other", "END:VTODO",
    )

    @Test
    fun nonParentTaskRelationshipsSurviveUnrelatedEdit() {
        val saved = codec.serializeTask(parseTask(relatedTask).copy(title = "Renamed"), relatedTask)

        listOf("RELTYPE=PARENT:parent", "RELTYPE=CHILD:child", "RELTYPE=SIBLING:sibling", "RELTYPE=X-CUSTOM;X-P=1:other").forEach {
            assertTrue(saved, saved.contains("RELATED-TO;$it"))
        }
    }

    @Test
    fun removingTheParentLeavesOtherRelationships() {
        val saved = codec.serializeTask(parseTask(relatedTask).copy(parentUid = null), relatedTask)

        assertFalse(saved, saved.contains("RELTYPE=PARENT"))
        assertTrue(saved, saved.contains("RELTYPE=CHILD:child"))
    }

    @Test
    fun timedEventWithoutEndHasZeroDuration() {
        val raw = calendar("BEGIN:VEVENT", "UID:z", "DTSTAMP:20260101T000000Z", "DTSTART:20261102T090000Z", "SUMMARY:Point", "END:VEVENT")

        val event = parseEvent(raw)
        val reread = parseEvent(codec.serializeEvent(event))

        assertEquals(event.startsAtMillis, event.endsAtMillis)
        assertEquals(reread.startsAtMillis, reread.endsAtMillis)
        assertEquals(listOf(event.startsAtMillis), expander.expand(event, event.startsAtMillis, event.startsAtMillis + 1).map { it.startsAtMillis })
    }

    @Test
    fun aDateOnlyEventWithoutEndStaysOneDay() {
        val raw = calendar("BEGIN:VEVENT", "UID:z", "DTSTAMP:20260101T000000Z", "DTSTART;VALUE=DATE:20261102", "SUMMARY:Day", "END:VEVENT")

        val event = parseEvent(raw)

        assertEquals(86_400_000L, event.endsAtMillis - event.startsAtMillis)
    }

    @Test
    fun theImportTakesZeroDurationEvents() {
        val raw = calendar("BEGIN:VEVENT", "UID:z", "DTSTAMP:20260101T000000Z", "DTSTART:20261102T090000Z", "SUMMARY:Point", "END:VEVENT")

        assertEquals(1, IcsEventReader(codec).read(raw).size)
    }

    // --- #101 -----------------------------------------------------------------------------------

    private fun physicalLinesFit(ics: String) =
        ics.split("\r\n").all { it.encodeToByteArray().size <= 75 }

    @Test
    fun foldingKeepsLinesWithinTheLimitAndRoundTrips() {
        val text = "Grüße 😀 " .repeat(4_000)
        val event = parseEvent(calendar("BEGIN:VEVENT", "UID:f", "DTSTAMP:20260101T000000Z", "DTSTART:20261102T090000Z", "SUMMARY:x", "END:VEVENT"))
            .copy(description = text)

        val raw = codec.serializeEvent(event)

        assertTrue(physicalLinesFit(raw))
        assertEquals(text.trimEnd(), parseEvent(raw).description?.trimEnd())
    }

    @Test(timeout = 30_000)
    fun hostileTextStaysFastInEveryDirection() {
        val angleBrackets = "<b>" + "<".repeat(400_000)
        val raw = calendar("BEGIN:VEVENT", "UID:h", "DTSTAMP:20260101T000000Z", "DTSTART:20261102T090000Z", "SUMMARY:x", "DESCRIPTION:$angleBrackets", "END:VEVENT")
        val folded = "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:x\r\nBEGIN:VEVENT\r\nUID:h\r\nDTSTAMP:20260101T000000Z\r\nDTSTART:20261102T090000Z\r\nSUMMARY:x\r\nDESCRIPTION:" +
            "a\r\n b".repeat(150_000) + "\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n"

        val elapsed = measureTimeMillis {
            val event = parseEvent(raw)
            codec.serializeEvent(event.copy(description = "x".repeat(2_000_000)))
            IcsEventReader(codec).read(folded)
        }

        assertTrue("Took $elapsed ms", elapsed < 25_000)
        assertNotEquals(0, raw.length)
    }
}
