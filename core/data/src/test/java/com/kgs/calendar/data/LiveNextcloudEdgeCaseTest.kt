package com.kgs.calendar.data

import com.kgs.calendar.data.remote.CalDavHttpClient
import com.kgs.calendar.data.remote.isSyncConflictError
import kotlinx.coroutines.runBlocking
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.URI
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

/**
 * Edge cases against a disposable local Nextcloud, where a fake server would only repeat our own
 * assumptions. Nextcloud limits how many calendars a user may create per hour, so the tests share
 * one calendar and tell their items apart by UID and title. The runner deletes the test user.
 */
@RunWith(RobolectricTestRunner::class)
class LiveNextcloudEdgeCaseTest {
    private lateinit var base: String
    private lateinit var user: String
    private lateinit var password: String
    private lateinit var harness: RepositoryHarness
    private lateinit var client: CalDavHttpClient
    private lateinit var calendarHref: String
    private val repository get() = harness.repository

    @Before fun setUp() = runBlocking {
        val url = System.getenv("KGS_TEST_NEXTCLOUD_URL")
        assumeTrue("Needs a disposable local Nextcloud", url != null)
        base = url!!
        require(URI(base).host in setOf("localhost", "127.0.0.1"))
        user = requireNotNull(System.getenv("KGS_TEST_NEXTCLOUD_USER"))
        password = requireNotNull(System.getenv("KGS_TEST_NEXTCLOUD_PASSWORD"))
        harness = RepositoryHarness()
        client = CalDavHttpClient(harness.httpClient)
        val account = repository.saveManualAccount(base, user, password)
        val name = sharedCalendarName ?: "kgs-edge-${UUID.randomUUID()}".also { created ->
            repository.createCalDavCalendar(account.id, created, null, supportsEvents = true, supportsTasks = true)
            sharedCalendarName = created
        }
        repository.syncNow()
        calendarHref = harness.database.collectionDao().forAccount(account.id).first { it.displayName == name }.href
    }

    @After fun tearDown() {
        if (::harness.isInitialized) harness.close()
    }

    @Test fun anExternalSeriesWithAMovedAndACancelledOccurrenceExpandsAsNextcloudStoresIt() = runBlocking {
        put(
            "series.ics",
            SampleIcs.calendar(
                "BEGIN:VEVENT",
                "UID:series",
                "DTSTAMP:20260901T000000Z",
                "DTSTART;TZID=Europe/Berlin:20261012T090000",
                "DTEND;TZID=Europe/Berlin:20261012T100000",
                "RRULE:FREQ=DAILY;COUNT=5",
                "EXDATE;TZID=Europe/Berlin:20261014T090000",
                "SUMMARY:Standup",
                "END:VEVENT",
                "BEGIN:VEVENT",
                "UID:series",
                "DTSTAMP:20260901T000000Z",
                "RECURRENCE-ID;TZID=Europe/Berlin:20261013T090000",
                "DTSTART;TZID=Europe/Berlin:20261013T150000",
                "DTEND;TZID=Europe/Berlin:20261013T153000",
                "SUMMARY:Standup (moved)",
                "END:VEVENT",
            ),
        )
        repository.syncNow()

        val week = occurrences("series", LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 19))
        assertEquals(
            listOf("12 09:00 Standup", "13 15:00 Standup (moved)", "15 09:00 Standup", "16 09:00 Standup"),
            week.map { "${it.first.dayOfMonth} ${it.first.toLocalTime()} ${it.second}" },
        )
    }

    @Test fun aTimeInAnotherZoneKeepsItsInstant() = runBlocking {
        put(
            "ny.ics",
            SampleIcs.calendar(
                "BEGIN:VTIMEZONE",
                "TZID:America/New_York",
                "BEGIN:DAYLIGHT",
                "TZOFFSETFROM:-0500",
                "TZOFFSETTO:-0400",
                "TZNAME:EDT",
                "DTSTART:19700308T020000",
                "RRULE:FREQ=YEARLY;BYMONTH=3;BYDAY=2SU",
                "END:DAYLIGHT",
                "BEGIN:STANDARD",
                "TZOFFSETFROM:-0400",
                "TZOFFSETTO:-0500",
                "TZNAME:EST",
                "DTSTART:19701101T020000",
                "RRULE:FREQ=YEARLY;BYMONTH=11;BYDAY=1SU",
                "END:STANDARD",
                "END:VTIMEZONE",
                "BEGIN:VEVENT",
                "UID:ny",
                "DTSTAMP:20260901T000000Z",
                "DTSTART;TZID=America/New_York:20261030T090000",
                "DTEND;TZID=America/New_York:20261030T100000",
                "SUMMARY:Call with New York",
                "END:VEVENT",
            ),
        )
        repository.syncNow()

        val event = harness.event("ny")!!
        val expected = ZonedDateTime.of(2026, 10, 30, 9, 0, 0, 0, ZoneId.of("America/New_York")).toInstant().toEpochMilli()
        assertEquals(expected, event.startsAtMillis)
        assertEquals(expected + 3_600_000, event.endsAtMillis)
    }

    @Test fun aMultiDayAllDayEventStaysAllDayWhenEditedInTheApp() = runBlocking {
        put(
            "trip.ics",
            SampleIcs.calendar(
                "BEGIN:VEVENT",
                "UID:trip",
                "DTSTAMP:20260901T000000Z",
                "DTSTART;VALUE=DATE:20261010",
                "DTEND;VALUE=DATE:20261013",
                "SUMMARY:Trip",
                "END:VEVENT",
            ),
        )
        repository.syncNow()
        val trip = harness.event("trip")!!
        assertTrue(trip.allDay)
        assertEquals(harness.startOfDay(LocalDate.of(2026, 10, 10)), trip.startsAtMillis)
        assertEquals(harness.startOfDay(LocalDate.of(2026, 10, 13)), trip.endsAtMillis)

        repository.updateEvent(
            "trip",
            eventPayload("Trip to the sea", LocalDate.of(2026, 10, 10), start = null, end = null, allDay = true, collectionHref = calendarHref)
                .copy(endDate = LocalDate.of(2026, 10, 12)),
        )
        push()

        val remote = get("trip.ics")
        assertTrue(remote, "DTSTART;VALUE=DATE:20261010" in remote)
        assertTrue(remote, "DTEND;VALUE=DATE:20261013" in remote)
        assertTrue(remote, "SUMMARY:Trip to the sea" in remote)
    }

    @Test fun textWithSpecialCharactersSurvivesARoundTrip() = runBlocking {
        val title = "Review, Q4; \"Ümläute\" \\ 🎉"
        val notes = "Line one\nLine two, with comma; and semicolon"
        val location = "Room 1, Building B; Floor 2"
        repository.createEvent(
            eventPayload(title, LocalDate.of(2026, 10, 20), collectionHref = calendarHref, description = notes)
                .copy(location = location),
        )
        push()
        val created = harness.eventsIn(calendarHref).single { it.title == title }

        // A second device reads it back.
        RepositoryHarness().use { other ->
            other.repository.saveManualAccount(base, user, password)
            other.repository.syncNow()
            val copy = other.database.eventDao().get(created.uid)!!
            assertEquals(title, copy.title)
            assertEquals(notes, copy.description)
            assertEquals(location, copy.location)
        }
    }

    @Test fun editingAnExternalEventKeepsWhatTheAppDoesNotShow() = runBlocking {
        put(
            "rich.ics",
            SampleIcs.calendar(
                "BEGIN:VEVENT",
                "UID:rich",
                "DTSTAMP:20260901T000000Z",
                "DTSTART:20261021T080000Z",
                "DTEND:20261021T090000Z",
                "SUMMARY:Planning",
                "URL:https://example.com/agenda",
                "ATTACH;FMTTYPE=application/pdf:https://example.com/agenda.pdf",
                "X-KGS-TEST-PROPERTY:keep me",
                "ORGANIZER;CN=Ann:mailto:ann@example.com",
                "ATTENDEE;CN=Ben;PARTSTAT=ACCEPTED:mailto:ben@example.com",
                "BEGIN:VALARM",
                "ACTION:EMAIL",
                "TRIGGER:-PT1H",
                "SUMMARY:Mail reminder",
                "DESCRIPTION:Mail reminder",
                "ATTENDEE:mailto:ann@example.com",
                "END:VALARM",
                "END:VEVENT",
            ),
        )
        repository.syncNow()
        val event = harness.event("rich")!!

        repository.updateEvent(
            "rich",
            eventPayload("Planning (moved room)", LocalDate.of(2026, 10, 21), LocalTime.of(10, 0), LocalTime.of(11, 0), collectionHref = calendarHref)
                .copy(organizerJson = event.organizerJson, attendeesJson = event.attendeesJson),
        )
        push()

        val remote = get("rich.ics")
        assertTrue(remote, "SUMMARY:Planning (moved room)" in remote)
        assertTrue(remote, "URL:https://example.com/agenda" in remote)
        assertTrue(remote, "ATTACH;FMTTYPE=application/pdf:https://example.com/agenda.pdf" in remote)
        assertTrue(remote, "X-KGS-TEST-PROPERTY:keep me" in remote)
        assertTrue(remote, "mailto:ben@example.com" in remote)
        assertTrue(remote, "ACTION:EMAIL" in remote)
    }

    @Test fun oneOccurrenceEditedAndAnotherDeletedInTheAppReachTheServer() = runBlocking {
        repository.createEvent(
            eventPayload("Gym", LocalDate.of(2026, 10, 12), LocalTime.of(18, 0), LocalTime.of(19, 0), collectionHref = calendarHref, recurrenceRule = "FREQ=DAILY;COUNT=4"),
        )
        push()
        val uid = harness.eventsIn(calendarHref).single { it.title == "Gym" }.uid
        val second = harness.millis(LocalDate.of(2026, 10, 13), LocalTime.of(18, 0))
        val third = harness.millis(LocalDate.of(2026, 10, 14), LocalTime.of(18, 0))

        repository.updateEventOccurrence(
            uid,
            second,
            eventPayload("Gym (late)", LocalDate.of(2026, 10, 13), LocalTime.of(20, 0), LocalTime.of(21, 0), collectionHref = calendarHref),
        )
        repository.deleteEventOccurrence(uid, third)
        push()

        RepositoryHarness().use { other ->
            other.repository.saveManualAccount(base, user, password)
            other.repository.syncNow()
            val seen = other.repository.eventsSnapshot(
                other.startOfDay(LocalDate.of(2026, 10, 12)),
                other.startOfDay(LocalDate.of(2026, 10, 17)),
            ).filter { it.uid == uid }
            assertEquals(
                listOf("12 18:00 Gym", "13 20:00 Gym (late)", "15 18:00 Gym"),
                seen.map {
                    val start = java.time.Instant.ofEpochMilli(it.startsAtMillis).atZone(other.zone)
                    "${start.dayOfMonth} ${start.toLocalTime()} ${it.title}"
                },
            )
        }
    }

    @Test fun remindersRoundTripInBothDirections() = runBlocking {
        put(
            "remind.ics",
            SampleIcs.calendar(
                "BEGIN:VEVENT",
                "UID:remind",
                "DTSTAMP:20260901T000000Z",
                "DTSTART:20261022T080000Z",
                "DTEND:20261022T090000Z",
                "SUMMARY:Dentist",
                "BEGIN:VALARM",
                "ACTION:DISPLAY",
                "DESCRIPTION:Dentist",
                "TRIGGER:-PT30M",
                "END:VALARM",
                "END:VEVENT",
            ),
        )
        repository.syncNow()
        assertEquals("30", harness.event("remind")!!.remindersCsv)

        repository.updateEvent(
            "remind",
            eventPayload("Dentist", LocalDate.of(2026, 10, 22), LocalTime.of(10, 0), LocalTime.of(11, 0), collectionHref = calendarHref)
                .copy(reminderMinutes = listOf(10, 1440)),
        )
        push()
        val remote = get("remind.ics")
        assertTrue(remote, "TRIGGER:-PT10M" in remote || "TRIGGER;RELATED=START:-PT10M" in remote)
        assertTrue(remote, "-P1D" in remote || "-PT1440M" in remote || "-PT24H" in remote)
        assertFalse(remote, "-PT30M" in remote)
    }

    @Test fun aSubtaskKeepsItsParentAndCompletionSyncsBack() = runBlocking {
        repository.createTask(taskPayload("Move house", collectionHref = calendarHref))
        push()
        val parent = harness.tasksIn(calendarHref).single { it.title == "Move house" }
        repository.createTask(taskPayload("Pack boxes", collectionHref = calendarHref, parentUid = parent.uid))
        push()
        val child = harness.tasksIn(calendarHref).single { it.title == "Pack boxes" }
        val childIcs = get(child.resourceHref.substringAfterLast('/'))
        assertTrue(childIcs, "RELATED-TO" in childIcs && parent.uid in childIcs)

        // Completed on another device.
        val completed = childIcs.replace("STATUS:NEEDS-ACTION", "STATUS:COMPLETED")
            .let { if ("STATUS:COMPLETED" in it) it else it.replace("END:VTODO", "STATUS:COMPLETED\r\nEND:VTODO") }
        put(child.resourceHref.substringAfterLast('/'), completed)
        repository.syncNow()
        assertTrue(harness.task(child.uid)!!.isCompleted)
        assertEquals(parent.uid, harness.task(child.uid)!!.parentUid)
    }

    @Test fun aConflictWithAnotherDeviceWaitsForTheUserAndSettlesEitherWay() = runBlocking {
        for ((uid, keepLocal) in listOf("conflict-mine" to true, "conflict-theirs" to false)) {
            put("$uid.ics", SampleIcs.event(uid, "Original", "20261023T080000Z", "20261023T090000Z"))
            repository.syncNow()
            // Another device renames it; this device has not synced since.
            put("$uid.ics", SampleIcs.event(uid, "Renamed elsewhere", "20261023T080000Z", "20261023T090000Z", sequence = 1))
            repository.updateEvent(uid, eventPayload("Renamed here", LocalDate.of(2026, 10, 23), collectionHref = calendarHref))
            repository.syncNow()
            val href = harness.event(uid)!!.resourceHref
            assertTrue(harness.resource(href)!!.syncError.isSyncConflictError())
            assertTrue(get("$uid.ics"), "SUMMARY:Renamed elsewhere" in get("$uid.ics"))

            if (keepLocal) repository.resolveConflictKeepingLocal(href) else repository.resolveConflictUsingServer(href)
            repository.syncNow()

            val expected = if (keepLocal) "Renamed here" else "Renamed elsewhere"
            assertEquals(expected, harness.event(uid)!!.title)
            assertTrue(get("$uid.ics"), "SUMMARY:$expected" in get("$uid.ics"))
            assertTrue(harness.pendingMutations().none { it.resourceHref == href })
            assertNull(harness.resource(href)!!.syncError)
        }
    }

    @Test fun movingAnEventToAnotherCalendarMovesItOnTheServer() = runBlocking {
        val accountId = harness.database.collectionDao().get(calendarHref)!!.accountId
        val otherName = "kgs-edge-target-${UUID.randomUUID()}"
        repository.createCalDavCalendar(accountId, otherName, null, supportsEvents = true, supportsTasks = true)
        repository.syncNow()
        val otherHref = harness.database.collectionDao().forAccount(accountId).first { it.displayName == otherName }.href
        try {
            repository.createEvent(eventPayload("Wandering", LocalDate.of(2026, 10, 24), collectionHref = calendarHref))
            push()
            val event = harness.eventsIn(calendarHref).single { it.title == "Wandering" }
            val name = event.resourceHref.substringAfterLast('/')
            repository.updateEvent(event.uid, eventPayload("Wandering", LocalDate.of(2026, 10, 24), collectionHref = otherHref))
            push()
            repository.syncNow()

            assertTrue(harness.eventsIn(calendarHref).none { it.uid == event.uid })
            assertEquals(listOf("Wandering"), harness.eventsIn(otherHref).map { it.title })
            assertFalse(list(calendarHref).any { it.endsWith(name) })
            assertEquals(1, list(otherHref).size)
        } finally {
            runCatching { repository.deleteCalDavCalendar(otherHref) }
        }
    }

    @Test fun anEventDeletedOnTheServerDisappearsAndOneDeletedHereLeavesTheServer() = runBlocking {
        put("gone.ics", SampleIcs.event("gone", "Gone elsewhere", "20261025T080000Z", "20261025T090000Z"))
        put("stay.ics", SampleIcs.event("stay", "Deleted here", "20261025T100000Z", "20261025T110000Z"))
        repository.syncNow()
        client.deleteResource(base, "$calendarHref/gone.ics".replace("//", "/"), user, password, null)
        repository.deleteEvent("stay", moveToTrash = true)
        push()
        repository.syncNow()

        assertNull(harness.event("gone"))
        assertNull(harness.event("stay"))
        assertTrue(list(calendarHref).none { it.endsWith("/gone.ics") || it.endsWith("/stay.ics") })
    }

    @Test fun aLargeCalendarSyncsCompletely() = runBlocking {
        val body = buildString {
            repeat(300) { index ->
                append(
                    SampleIcs.calendar(
                        "BEGIN:VEVENT",
                        "UID:bulk-$index",
                        "DTSTAMP:20260901T000000Z",
                        "DTSTART:202611%02dT%02d0000Z".format(index % 28 + 1, index % 20),
                        "DTEND:202611%02dT%02d3000Z".format(index % 28 + 1, index % 20),
                        "SUMMARY:Bulk $index",
                        "END:VEVENT",
                    ),
                )
                append('\u0000')
            }
        }
        body.split('\u0000').filter { it.isNotBlank() }.forEachIndexed { index, ics -> put("bulk-$index.ics", ics) }
        repository.syncNow()
        assertEquals(300, harness.eventsIn(calendarHref).count { it.uid.startsWith("bulk-") })
        assertTrue(harness.database.resourceDao().all().none { it.syncError != null })
    }

    @Test fun aPublishedCalendarCanBeSubscribedReadOnly() = runBlocking {
        put("public.ics", SampleIcs.event("public", "Public event", "20261026T080000Z", "20261026T090000Z"))
        val publicUrl = publish(calendarHref)
        assumeTrue("Publishing is not available on this server", publicUrl != null)
        val feed = repository.addReadOnlyCalendar(publicUrl!!, "Subscribed")
        val readOnlyHref = harness.database.collectionDao().forAccount(feed.id).single().href
        repository.syncNow()
        val subscribed = harness.eventsIn(readOnlyHref)
        assertTrue(subscribed.map { it.title }.toString(), subscribed.any { it.title == "Public event" })
        assertTrue(harness.database.collectionDao().get(readOnlyHref)!!.readOnly || readOnlyHref.startsWith(READ_ONLY_PREFIX))
    }

    private companion object {
        var sharedCalendarName: String? = null
    }

    private suspend fun push() = harness.components.syncOrchestrator.pushAllPendingChanges()

    private fun occurrences(uid: String, from: LocalDate, until: LocalDate) = runBlocking {
        repository.eventsSnapshot(harness.startOfDay(from), harness.startOfDay(until))
            .filter { it.uid == uid }
            .map { java.time.Instant.ofEpochMilli(it.startsAtMillis).atZone(harness.zone).toLocalDateTime() to it.title }
    }

    private fun url(path: String) = URI(base).resolve(path).toString()

    private fun resourceUrl(name: String) = url(calendarHref.trimEnd('/') + "/" + name)

    /** Stores [ics] as another client would; a busy shared test server may answer a 5xx now and then. */
    private fun put(name: String, ics: String) {
        var code = 0
        for (attempt in 1..3) {
            code = harness.httpClient.newCall(
                Request.Builder().url(resourceUrl(name)).header("Authorization", Credentials.basic(user, password))
                    .put(ics.toRequestBody("text/calendar; charset=utf-8".toMediaType())).build(),
            ).execute().use { it.code }
            if (code < 500) break
            Thread.sleep(500L * attempt)
        }
        assertTrue("PUT $name failed: $code", code in 200..299)
    }

    private fun get(name: String): String = harness.httpClient.newCall(
        Request.Builder().url(resourceUrl(name)).header("Authorization", Credentials.basic(user, password)).build(),
    ).execute().use { response ->
        assertTrue("GET $name failed: ${response.code}", response.isSuccessful)
        response.body!!.string().unfoldedIcs()
    }

    /** The .ics names stored in [collectionHref] on the server. */
    private fun list(collectionHref: String): List<String> = harness.httpClient.newCall(
        Request.Builder().url(url(collectionHref)).header("Authorization", Credentials.basic(user, password)).header("Depth", "1")
            .method("PROPFIND", """<?xml version="1.0"?><d:propfind xmlns:d="DAV:"><d:prop><d:getetag/></d:prop></d:propfind>""".toRequestBody("application/xml".toMediaType()))
            .build(),
    ).execute().use { response ->
        Regex("<d:href>([^<]+\\.ics)</d:href>").findAll(response.body!!.string()).map { it.groupValues[1] }.toList()
    }

    /** Publishes [collectionHref] through Nextcloud's CalDAV publishing and returns its public .ics URL. */
    private fun publish(collectionHref: String): String? {
        val published = harness.httpClient.newCall(
            Request.Builder().url(url(collectionHref)).header("Authorization", Credentials.basic(user, password))
                .post("""<?xml version="1.0"?><cs:publish-calendar xmlns:cs="http://calendarserver.org/ns/"/>""".toRequestBody("application/xml".toMediaType()))
                .build(),
        ).execute().use { it.isSuccessful }
        if (!published) return null
        val body = harness.httpClient.newCall(
            Request.Builder().url(url(collectionHref)).header("Authorization", Credentials.basic(user, password)).header("Depth", "0")
                .method("PROPFIND", """<?xml version="1.0"?><d:propfind xmlns:d="DAV:" xmlns:cs="http://calendarserver.org/ns/"><d:prop><cs:publish-url/></d:prop></d:propfind>""".toRequestBody("application/xml".toMediaType()))
                .build(),
        ).execute().use { it.body!!.string() }
        val publicUrl = Regex("<cs:publish-url><d:href>([^<]+)</d:href>").find(body)?.groupValues?.get(1) ?: return null
        return "$publicUrl?export"
    }
}
