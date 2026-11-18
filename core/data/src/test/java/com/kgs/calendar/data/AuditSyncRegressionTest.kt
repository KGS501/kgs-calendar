package com.kgs.calendar.data

import com.kgs.calendar.data.remote.isSyncConflictError
import com.kgs.calendar.domain.model.MutationAction
import com.kgs.calendar.domain.trash.TrashOrigin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import com.kgs.calendar.time.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

@RunWith(RobolectricTestRunner::class)
class AuditSyncRegressionTest {
    private val harness = RepositoryHarness()
    private val repository = harness.repository
    private val server = harness.server
    private val day = LocalDate.of(2026, 10, 12)
    private lateinit var href: String
    private val otherCalendar get() = "${server.homeHref}other/"

    @Before
    fun setUp() = runTest {
        server.addCollection(otherCalendar, "Other", setOf("VEVENT", "VTODO"))
        href = server.putRemote(server.eventsHref, "kickoff.ics", SampleIcs.event("remote-event", "Kickoff"))
        harness.addSyncedCalDavAccount()
        server.clearRequests()
    }

    @After
    fun tearDown() = harness.close()

    private suspend fun assertConflictMarked(resource: String = href) =
        assertTrue(harness.resource(resource)?.syncError.isSyncConflictError())

    // --- #104: already absent deletes and restores ------------------------------------------------

    @Test
    fun deleteOfAnItemDeletedElsewhereLeavesNoOrphan() = runTest {
        repository.deleteEvent("remote-event")
        server.deleteRemote(href)

        repository.syncNow()

        assertTrue(harness.pendingMutations().isEmpty())
        assertNull(harness.resource(href))
    }

    @Test
    fun deleteWhoseReplyWasLostSettlesOnTheNextSync() = runTest {
        repository.deleteEvent("remote-event")
        server.respondNext("DELETE", href) {
            server.deleteRemote(href)
            MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AFTER_REQUEST)
        }
        runCatching { repository.syncNow() }

        repository.syncNow()

        assertTrue(harness.pendingMutations().isEmpty())
    }

    @Test
    fun aChangedServerItemStaysAConflictForADelete() = runTest {
        repository.deleteEvent("remote-event")
        server.putRemote(server.eventsHref, "kickoff.ics", SampleIcs.event("remote-event", "Changed elsewhere"))

        repository.syncNow()

        assertEquals(MutationAction.Delete, harness.pendingMutations().single().action)
        assertConflictMarked()
    }

    @Test
    fun aRestoredItemSurvivesFurtherSyncs() = runTest {
        repository.deleteEvent("remote-event")
        server.deleteRemote(href)
        repository.syncNow()
        val snapshot = repository.observeTrashedItems().first().single()

        repository.restoreTrashedItem(snapshot.id)
        repository.syncNow()
        repository.syncNow()

        assertNotNull(server.stored(href))
        assertNotNull(harness.event(href))
        assertTrue(harness.pendingMutations().isEmpty())
    }

    // --- #105: edit versus remote delete ----------------------------------------------------------

    @Test
    fun editOfAnItemDeletedElsewhereIsAConflictNotAnError() = runTest {
        repository.updateEvent("remote-event", eventPayload("Local edit", day))
        server.deleteRemote(href)

        repository.syncNow()

        assertConflictMarked()
        assertEquals(MutationAction.Put, harness.pendingMutations().single().action)
    }

    @Test
    fun keepMineRecreatesAnItemDeletedElsewhere() = runTest {
        repository.updateEvent("remote-event", eventPayload("Local edit", day))
        server.deleteRemote(href)
        repository.syncNow()

        repository.resolveConflictKeepingLocal(href)

        assertTrue(server.stored(href)!!.ics.unfoldedIcs().contains("SUMMARY:Local edit"))
        assertTrue(harness.pendingMutations().isEmpty())
    }

    @Test
    fun aServerFailureOfTheProbeIsNoConflict() = runTest {
        repository.updateEvent("remote-event", eventPayload("Local edit", day))
        server.putRemote(server.eventsHref, "kickoff.ics", SampleIcs.event("remote-event", "Elsewhere"))
        server.respondNext("GET", href) { MockResponse().setResponseCode(500) }

        runCatching { repository.syncNow() }

        assertFalse(harness.resource(href)?.syncError.isSyncConflictError())
        assertEquals(1, harness.pendingMutations().size)
    }

    // --- #95: Use server ------------------------------------------------------------------------

    @Test
    fun useServerKeepsTheConflictWhenTheDownloadFails() = runTest {
        repository.updateEvent("remote-event", eventPayload("Local edit", day))
        server.putRemote(server.eventsHref, "kickoff.ics", SampleIcs.event("remote-event", "Elsewhere"))
        repository.syncNow()
        val queued = harness.pendingMutations().single()
        server.respondNext("GET", href) { MockResponse().setResponseCode(503) }

        try {
            repository.resolveConflictUsingServer(href)
            fail("The failed download must be reported")
        } catch (_: Exception) {
        }

        assertEquals(queued, harness.pendingMutations().single())
        assertEquals("Local edit", harness.event(href)!!.title)
        repository.resolveConflictKeepingLocal(href)
        assertTrue(server.stored(href)!!.ics.unfoldedIcs().contains("SUMMARY:Local edit"))
    }

    @Test
    fun useServerKeepsTheConflictWhenTheServerVersionIsUnreadable() = runTest {
        repository.updateEvent("remote-event", eventPayload("Local edit", day))
        server.putRemote(server.eventsHref, "kickoff.ics", SampleIcs.event("remote-event", "Elsewhere"))
        repository.syncNow()
        server.putRemote(server.eventsHref, "kickoff.ics", "BEGIN:VCALENDAR\r\nEND:VCALENDAR\r\n")

        runCatching { repository.resolveConflictUsingServer(href) }

        assertEquals(1, harness.pendingMutations().size)
        assertEquals("Local edit", harness.event(href)!!.title)
    }

    @Test
    fun useServerAppliesTheServerVersionAtomically() = runTest {
        repository.updateEvent("remote-event", eventPayload("Local edit", day))
        server.putRemote(server.eventsHref, "kickoff.ics", SampleIcs.event("remote-event", "Elsewhere"))
        repository.syncNow()

        repository.resolveConflictUsingServer(href)

        assertTrue(harness.pendingMutations().isEmpty())
        assertEquals("Elsewhere", harness.event(href)!!.title)
    }

    // --- #106: unreadable remote versions -----------------------------------------------------------

    @Test
    fun anEditCannotOverwriteAnUnreadableRemoteVersion() = runTest {
        val unreadable = SampleIcs.event("remote-event", "New remote", start = "not-a-date")
        server.putRemote(server.eventsHref, "kickoff.ics", unreadable)
        repository.syncNow(forceFullCalDavRefresh = true)
        val quarantined = harness.resource(href)!!
        assertEquals("Kickoff", harness.event(href)!!.title)
        assertTrue(quarantined.syncError!!.startsWith("Import failed"))
        assertEquals(unreadable.replace("\r\n", "\n").trim(), quarantined.quarantinedRawIcs?.replace("\r\n", "\n")?.trim())

        repository.updateEvent("remote-event", eventPayload("Stale edit", day))
        repository.syncNow()

        assertEquals(unreadable, server.stored(href)!!.ics)
        assertConflictMarked()
    }

    @Test
    fun aReadableVersionLaterClearsTheQuarantine() = runTest {
        server.putRemote(server.eventsHref, "kickoff.ics", SampleIcs.event("remote-event", "New remote", start = "not-a-date"))
        repository.syncNow(forceFullCalDavRefresh = true)

        server.putRemote(server.eventsHref, "kickoff.ics", SampleIcs.event("remote-event", "Fixed remote"))
        repository.syncNow()

        assertEquals("Fixed remote", harness.event(href)!!.title)
        assertNull(harness.resource(href)!!.quarantinedRawIcs)
        assertNull(harness.resource(href)!!.syncError)
    }

    // --- #84: unsent edits in the trash ---------------------------------------------------------

    @Test
    fun anUnsentEditStaysRecoverableAfterTheServerTrashedTheOlderVersion() = runTest {
        server.trashBinEnabled = true
        repository.updateEvent("remote-event", eventPayload("Unsent edit", day))
        repository.deleteEvent("remote-event")

        repository.syncNow()
        repository.refreshTrash()

        val snapshot = repository.observeTrashedItems().first().single { it.origin == TrashOrigin.LocalSnapshot }
        assertTrue(snapshot.rawIcs.unfoldedIcs().contains("SUMMARY:Unsent edit"))
        repository.restoreTrashedItem(snapshot.id)
        repository.syncNow()
        assertTrue(server.stored(href)!!.ics.unfoldedIcs().contains("SUMMARY:Unsent edit"))
    }

    // --- #70: moves -----------------------------------------------------------------------------

    private suspend fun moveToOtherCalendar(): String {
        repository.updateEvent(
            "remote-event",
            eventPayload("Kickoff", day, LocalTime.of(9, 0), LocalTime.of(10, 0), collectionHref = otherCalendar),
        )
        return harness.eventsIn(otherCalendar).single().resourceHref
    }

    @Test
    fun aRefusedDestinationKeepsTheSourceOnTheServer() = runTest {
        val destination = moveToOtherCalendar()
        server.respondNext("PUT", destination, times = 5) { MockResponse().setResponseCode(507) }

        runCatching { repository.syncNow() }

        assertNotNull(server.stored(href))
        assertTrue(server.requests("DELETE").isEmpty())
        assertEquals(2, harness.pendingMutations().size)
    }

    @Test
    fun theSourceGoesOnceTheDestinationIsAcknowledged() = runTest {
        val destination = moveToOtherCalendar()

        repository.syncNow()

        assertNotNull(server.stored(destination))
        assertNull(server.stored(href))
        assertTrue(harness.pendingMutations().isEmpty())
        assertNull(harness.resource(href))
    }

    @Test
    fun aMoveKeepsWhatTheAppDoesNotModel() = runTest {
        val raw = SampleIcs.event("remote-event", "Kickoff").replace("SEQUENCE:0", "SEQUENCE:0\r\nX-CUSTOM-THING:keep me")
        server.putRemote(server.eventsHref, "kickoff.ics", raw)
        repository.syncNow(forceFullCalDavRefresh = true)

        val destination = moveToOtherCalendar()
        repository.syncNow()

        assertTrue(server.stored(destination)!!.ics.unfoldedIcs().contains("X-CUSTOM-THING:keep me"))
    }

    @Test
    fun givingUpTheDestinationBringsTheSourceBack() = runTest {
        val destination = moveToOtherCalendar()
        server.respondNext("PUT", destination, times = 5) { MockResponse().setResponseCode(507) }
        runCatching { repository.syncNow() }

        repository.resolveConflictUsingServer(destination)
        repository.syncNow()

        assertTrue(server.requests("DELETE").isEmpty())
        assertNotNull(server.stored(href))
        assertEquals("Kickoff", harness.event(href)!!.title)
        assertTrue(harness.pendingMutations().isEmpty())
    }

    // --- #96 / #107 -------------------------------------------------------------------------------

    @Test
    fun aListingThatLostMostOfACalendarLeavesRecoverableSnapshots() = runTest {
        val hrefs = (1..24).map { server.putRemote(server.eventsHref, "e$it.ics", SampleIcs.event("e$it", "Event $it")) }
        repository.syncNow()
        hrefs.forEach { server.deleteRemote(it) }

        repository.syncNow(forceFullCalDavRefresh = true)

        assertTrue(hrefs.all { harness.resource(it) == null })
        assertEquals(hrefs.size, repository.observeTrashedItems().first().count { it.title.startsWith("Event ") })
        assertTrue(harness.pendingMutations().isEmpty())
    }

    @Test
    fun aFewDeletionsPassWithoutSnapshots() = runTest {
        server.deleteRemote(href)

        repository.syncNow(forceFullCalDavRefresh = true)

        assertNull(harness.resource(href))
        assertTrue(repository.observeTrashedItems().first().isEmpty())
    }
}
