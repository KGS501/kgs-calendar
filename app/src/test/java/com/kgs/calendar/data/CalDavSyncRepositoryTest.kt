package com.kgs.calendar.data

import com.kgs.calendar.data.local.entity.AccountEntity
import com.kgs.calendar.domain.model.ComponentType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class CalDavSyncRepositoryTest {
    private val harness = RepositoryHarness()
    private val repository = harness.repository
    private val server = harness.server

    @After
    fun tearDown() = harness.close()

    private fun seedRemote(): Pair<String, String> {
        val eventHref = server.putRemote(server.eventsHref, "kickoff.ics", SampleIcs.event("remote-event", "Kickoff"))
        val taskHref = server.putRemote(server.tasksHref, "todo.ics", SampleIcs.task("remote-task", "Write agenda"))
        return eventHref to taskHref
    }

    private fun collectionListings() = server.requests("PROPFIND")
        .filter { it.path == server.eventsHref || it.path == server.tasksHref }

    @Test
    fun saveManualAccountDiscoversAndStoresAccountAndCredentials() = runTest {
        val account = repository.saveManualAccount(" ${server.serverUrl} ", " alice ", "secret")

        val expectedServer = server.serverUrl.trimEnd('/')
        assertEquals(AccountEntity.PRIMARY_ID, account.id)
        assertEquals(account, harness.account(AccountEntity.PRIMARY_ID))
        assertEquals(expectedServer, account.serverUrl)
        assertEquals("alice", account.username)
        assertEquals("alice", account.displayName)
        assertEquals(SourceType.CalDav, account.sourceType)
        assertEquals(server.url(server.principalHref), account.principalUrl)
        assertEquals(server.url(server.homeHref), account.calendarHomeUrl)
        assertNull(account.lastSyncAtMillis)
        assertEquals(expectedServer, harness.credentials.get(AccountEntity.PRIMARY_ID)!!.serverUrl)
        assertEquals("secret", harness.credentials.get(AccountEntity.PRIMARY_ID)!!.appPassword)
        // NOTE: current behaviour - collections are validated but only persisted by the first syncNow.
        assertTrue(harness.database.collectionDao().all().isEmpty())
    }

    @Test
    fun saveManualAccountWithRejectedCredentialsPersistsNothing() = runTest {
        val error = expectFailure<IllegalStateException> { repository.saveManualAccount(server.serverUrl, "alice", "wrong") }

        assertTrue(error.message!!.startsWith("Could not verify this CalDAV login."))
        assertTrue(harness.database.accountDao().getAll().isEmpty())
        assertNull(harness.credentials.get(AccountEntity.PRIMARY_ID))
    }

    @Test
    fun firstSyncDiscoversCollectionsAndImportsEventsAndTasks() = runTest {
        val (eventHref, taskHref) = seedRemote()

        harness.addSyncedCalDavAccount()

        val events = harness.collection(server.eventsHref)!!
        assertEquals(SourceType.CalDav, events.sourceType)
        assertEquals(AccountEntity.PRIMARY_ID, events.accountId)
        assertEquals("Events", events.displayName)
        assertTrue(events.supportsEvents)
        assertFalse(events.supportsTasks)
        assertFalse(events.readOnly)
        assertNotNull(events.syncToken)
        assertNotNull(events.ctag)
        val tasks = harness.collection(server.tasksHref)!!
        assertFalse(tasks.supportsEvents)
        assertTrue(tasks.supportsTasks)

        val event = harness.event(eventHref)!!
        assertEquals("remote-event", event.uid)
        assertEquals("Kickoff", event.title)
        assertEquals(Instant.parse("2026-10-05T08:00:00Z").toEpochMilli(), event.startsAtMillis)
        assertEquals(Instant.parse("2026-10-05T09:00:00Z").toEpochMilli(), event.endsAtMillis)
        assertEquals(events.color, event.color)
        val eventResource = harness.resource(eventHref)!!
        assertEquals(server.stored(eventHref)!!.etag, eventResource.etag)
        assertEquals(ComponentType.Event, eventResource.componentType)
        // calendar-data comes out of the XML parser with LF line endings and trimmed.
        assertEquals(server.stored(eventHref)!!.ics.replace("\r\n", "\n").trim(), eventResource.rawIcs)

        val task = harness.task(taskHref)!!
        assertEquals("remote-task", task.uid)
        assertEquals("Write agenda", task.title)
        assertEquals(Instant.parse("2026-10-06T15:00:00Z").toEpochMilli(), task.dueAtMillis)
        assertEquals(server.stored(taskHref)!!.etag, harness.resource(taskHref)!!.etag)

        val account = harness.account(AccountEntity.PRIMARY_ID)!!
        assertEquals("idle", account.syncState)
        assertNull(account.syncError)
        assertNotNull(account.lastSyncAtMillis)
        assertTrue(harness.pendingMutations().isEmpty())
        assertEquals(setOf(server.eventsHref, server.tasksHref), collectionListings().map { it.path }.toSet())
        assertTrue(collectionListings().all { it.header("Depth") == "1" })
        assertTrue(server.requests("REPORT").all { "calendar-multiget" in it.body })
    }

    @Test
    fun secondSyncUsesStoredSyncTokenInsteadOfFullListing() = runTest {
        seedRemote()
        harness.addSyncedCalDavAccount()
        val storedToken = harness.collection(server.eventsHref)!!.syncToken!!
        server.clearRequests()

        repository.syncNow()

        val syncReports = server.requests("REPORT").filter { "sync-collection" in it.body }
        assertEquals(setOf(server.eventsHref, server.tasksHref), syncReports.map { it.path }.toSet())
        assertTrue(syncReports.single { it.path == server.eventsHref }.body.contains("<d:sync-token>$storedToken</d:sync-token>"))
        assertTrue(collectionListings().isEmpty())
        assertTrue(server.requests("REPORT").none { "calendar-multiget" in it.body })
        assertEquals("Kickoff", harness.eventsIn(server.eventsHref).single().title)
    }

    @Test
    fun incrementalSyncAppliesRemoteChangeAndDeletion() = runTest {
        val (eventHref, taskHref) = seedRemote()
        harness.addSyncedCalDavAccount()
        server.putRemote(
            server.eventsHref,
            "kickoff.ics",
            SampleIcs.event("remote-event", "Kickoff (moved)", start = "20261005T120000Z", end = "20261005T130000Z", sequence = 1),
        )
        server.deleteRemote(taskHref)
        server.clearRequests()

        repository.syncNow()

        val event = harness.event(eventHref)!!
        assertEquals("Kickoff (moved)", event.title)
        assertEquals(Instant.parse("2026-10-05T12:00:00Z").toEpochMilli(), event.startsAtMillis)
        assertEquals(server.stored(eventHref)!!.etag, harness.resource(eventHref)!!.etag)
        assertNull(harness.task(taskHref))
        assertNull(harness.resource(taskHref))
        assertTrue(collectionListings().isEmpty())
        val multiget = server.requests("REPORT").single { "calendar-multiget" in it.body }
        assertEquals(server.eventsHref, multiget.path)
        assertTrue(multiget.body.contains(eventHref))
    }

    @Test
    fun withoutSyncCollectionUnchangedCtagSkipsListingAndChangedCtagRelists() = runTest {
        server.supportsSyncCollection = false
        val (eventHref, taskHref) = seedRemote()
        val secondHref = server.putRemote(server.eventsHref, "review.ics", SampleIcs.event("remote-review", "Review"))
        harness.addSyncedCalDavAccount()
        assertNull(harness.collection(server.eventsHref)!!.syncToken)
        assertNotNull(harness.collection(server.eventsHref)!!.ctag)
        server.clearRequests()

        repository.syncNow()

        assertTrue(collectionListings().isEmpty())
        assertTrue(server.requests("REPORT").isEmpty())

        server.putRemote(server.eventsHref, "kickoff.ics", SampleIcs.event("remote-event", "Kickoff v2", sequence = 1))
        server.deleteRemote(secondHref)
        server.clearRequests()

        repository.syncNow()

        assertEquals(listOf(server.eventsHref), collectionListings().map { it.path })
        assertEquals("Kickoff v2", harness.event(eventHref)!!.title)
        assertNull(harness.event(secondHref))
        assertNull(harness.resource(secondHref))
        assertEquals("Write agenda", harness.task(taskHref)!!.title)
        val multiget = server.requests("REPORT").single()
        assertTrue(multiget.body.contains(eventHref))
        assertFalse(multiget.body.contains(taskHref))
    }

    @Test
    fun collectionWithoutWritePrivilegeIsReadOnlyAndRefusesEdits() = runTest {
        val birthdaysHref = "${server.homeHref}birthdays/"
        server.addCollection(birthdaysHref, "Birthdays", setOf("VEVENT"), readOnly = true)
        val eventHref = server.putRemote(birthdaysHref, "bday.ics", SampleIcs.event("bday", "Alice's birthday"))
        harness.addSyncedCalDavAccount()

        assertTrue(harness.collection(birthdaysHref)!!.readOnly)
        val error = expectFailure<IllegalStateException> {
            repository.updateEvent("bday", eventPayload("Renamed", LocalDate.of(2026, 10, 5)))
        }
        assertEquals("Read-only calendars cannot be edited.", error.message)
        repository.deleteEvent("bday")

        assertEquals("Alice's birthday", harness.event(eventHref)!!.title)
        assertTrue(harness.pendingMutations().isEmpty())
    }

    @Test
    fun forcedFullRefreshListsEverythingAndQueriesTaskCollections() = runTest {
        val (eventHref, _) = seedRemote()
        harness.addSyncedCalDavAccount()
        server.clearRequests()

        repository.syncNow(forceFullCalDavRefresh = true)

        assertEquals(setOf(server.eventsHref, server.tasksHref), collectionListings().map { it.path }.toSet())
        assertTrue(server.requests("REPORT").none { "sync-collection" in it.body })
        val query = server.requests("REPORT").single { "calendar-query" in it.body }
        assertEquals(server.tasksHref, query.path)
        assertEquals("Kickoff", harness.event(eventHref)!!.title)
    }
}
