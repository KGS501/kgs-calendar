package com.kgs.calendar.data

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class ReadOnlyCalendarRepositoryTest {
    private val harness = RepositoryHarness()
    private val repository = harness.repository
    private val server = harness.server
    private val feedPath = "/feeds/holidays.ics"

    @After
    fun tearDown() = harness.close()

    private fun feed(vararg events: Pair<String, String>) = SampleIcs.calendar(
        *events.flatMap { (uid, summary) ->
            listOf(
                "BEGIN:VEVENT",
                "UID:$uid",
                "DTSTAMP:20260901T000000Z",
                "DTSTART;VALUE=DATE:20261003",
                "DTEND;VALUE=DATE:20261004",
                "SUMMARY:$summary",
                "END:VEVENT",
            )
        }.toTypedArray(),
    )

    private suspend fun subscribe(): String {
        server.setFeed(feedPath, feed("unity-day" to "Unity Day", "harvest" to "Harvest festival"))
        val account = repository.addReadOnlyCalendar(server.url(feedPath), "Holidays")
        return "readonly-${account.id}"
    }

    @Test
    fun addReadOnlyCalendarImportsFeedIntoReadOnlyCollection() = runTest {
        server.setFeed(feedPath, feed("unity-day" to "Unity Day", "harvest" to "Harvest festival"))

        val account = repository.addReadOnlyCalendar(server.url(feedPath), "Holidays")

        assertTrue(account.id.startsWith("readonly-"))
        assertEquals(SourceType.ReadOnlyUrl, account.sourceType)
        assertEquals(server.url(feedPath), account.serverUrl)
        assertEquals("Read-only URL", account.username)
        val stored = harness.account(account.id)!!
        assertEquals("idle", stored.syncState)
        assertNotNull(stored.lastSyncAtMillis)
        // NOTE: current behaviour - the collection href repeats the prefix: "readonly-readonly-<uuid>".
        val collectionHref = "readonly-${account.id}"
        val collection = harness.collection(collectionHref)!!
        assertTrue(collection.readOnly)
        assertEquals(SourceType.ReadOnlyUrl, collection.sourceType)
        assertEquals("Holidays", collection.displayName)
        assertTrue(collection.supportsEvents)
        assertFalse(collection.supportsTasks)
        assertEquals(account.serverUrl, collection.externalId)
        val events = harness.eventsIn(collectionHref).sortedBy { it.title }
        assertEquals(listOf("Harvest festival", "Unity Day"), events.map { it.title })
        assertEquals("$collectionHref/unity-day.ics", events.single { it.uid == "unity-day" }.resourceHref)
        assertTrue(events.all { it.allDay })
        assertEquals(harness.startOfDay(LocalDate.of(2026, 10, 3)), events.first().startsAtMillis)
        assertTrue(harness.pendingMutations().isEmpty())
        val request = server.requests("GET").single()
        assertEquals("text/calendar, application/calendar+ics, text/plain, */*", request.header("Accept"))
    }

    @Test
    fun writesToReadOnlyCalendarAreRefusedOrIgnored() = runTest {
        val collectionHref = subscribe()
        val event = harness.eventsIn(collectionHref).single { it.uid == "unity-day" }

        val editError = expectFailure<IllegalStateException> {
            repository.updateEvent(event.uid, eventPayload("Hacked", LocalDate.of(2026, 10, 3)))
        }
        assertEquals("Read-only calendars cannot be edited.", editError.message)
        val createError = expectFailure<IllegalStateException> {
            repository.createEvent(eventPayload("New", LocalDate.of(2026, 10, 3), collectionHref = collectionHref))
        }
        assertEquals("No writable event calendar has been synced yet.", createError.message)
        repository.deleteEvent(event.uid)
        repository.deleteEventOccurrence(event.uid, event.startsAtMillis)

        assertEquals("Unity Day", harness.event(event.resourceHref)!!.title)
        assertEquals(2, harness.eventsIn(collectionHref).size)
        assertTrue(harness.pendingMutations().isEmpty())
    }

    @Test
    fun createEventTargetingReadOnlyCalendarFallsBackToWritableCalendar() = runTest {
        val collectionHref = subscribe()
        repository.ensureLocalCalendar()

        repository.createEvent(eventPayload("Redirected", LocalDate.of(2026, 10, 3), collectionHref = collectionHref))

        assertEquals(listOf("Redirected"), harness.eventsIn(LOCAL_COLLECTION).map { it.title })
        assertEquals(2, harness.eventsIn(collectionHref).size)
    }

    @Test
    fun syncNowRefreshesFeedAndDropsRemovedEvents() = runTest {
        val collectionHref = subscribe()
        server.setFeed(feedPath, feed("unity-day" to "German Unity Day"))

        repository.syncNow()

        assertEquals(listOf("German Unity Day"), harness.eventsIn(collectionHref).map { it.title })
        assertEquals(null, harness.event("$collectionHref/harvest.ics"))
        assertEquals(null, harness.resource("$collectionHref/harvest.ics"))
    }

    @Test
    fun htmlResponseFailsSubscriptionButKeepsAccountInErrorState() = runTest {
        server.setFeed(feedPath, "<!DOCTYPE html><html><body>Login</body></html>", contentType = "text/html")
        val url = server.url(feedPath)

        val error = expectFailure<IllegalStateException> { repository.addReadOnlyCalendar(url, "Broken") }

        assertEquals("Source \"Broken\": URL returned a web page instead of an iCalendar feed.", error.message)
        // NOTE: current behaviour - the account row is persisted before the first fetch and stays behind in error state.
        val account = harness.database.accountDao().getAll().single()
        assertEquals(url, account.serverUrl)
        assertEquals("error", account.syncState)
        assertEquals("Source \"Broken\": URL returned a web page instead of an iCalendar feed.", account.syncError)
        assertTrue(harness.database.collectionDao().all().isEmpty())
    }
}
