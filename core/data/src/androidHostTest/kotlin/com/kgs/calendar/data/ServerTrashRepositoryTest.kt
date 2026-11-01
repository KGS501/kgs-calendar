package com.kgs.calendar.data

import com.kgs.calendar.data.local.entity.AccountEntity
import com.kgs.calendar.data.local.entity.TrashedItemEntity
import com.kgs.calendar.data.remote.CalDavTrashBinSupport
import com.kgs.calendar.data.remote.HttpStatusException
import com.kgs.calendar.data.trash.TrashRestoreResult
import com.kgs.calendar.domain.model.ComponentType
import com.kgs.calendar.domain.model.MutationAction
import com.kgs.calendar.domain.model.SourceType
import com.kgs.calendar.domain.trash.TrashOrigin
import com.kgs.calendar.domain.trash.TrashRetention
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/** "Recently deleted" against a Nextcloud-style CalDAV trash bin on the fake server. */
@RunWith(RobolectricTestRunner::class)
class ServerTrashRepositoryTest {
    private val harness = RepositoryHarness()
    private val repository = harness.repository
    private val server = harness.server

    @After
    fun tearDown() = harness.close()

    private suspend fun trash(): List<TrashedItemEntity> = harness.database.trashDao().all()

    private suspend fun trashSupport(): CalDavTrashBinSupport? =
        CalDavTrashBinSupport.readFrom(harness.account(AccountEntity.PRIMARY_ID)!!.capabilitiesJson)

    private fun trashBinProbes() = server.requests("PROPFIND").filter { it.path == server.trashBinHref }

    private fun trashBinListings() = server.requests("REPORT").filter { it.path == server.trashObjectsHref }

    // --- Detection ------------------------------------------------------------------------------

    @Test
    fun nextcloud34CollectionOnlyResponseSupportsRemoteTrashAndActions() = runTest {
        server.trashBinEnabled = true
        val response = checkNotNull(javaClass.getResource("/nextcloud34-trashbin-propfind.xml")).readText()
            .replace("/remote.php/dav/calendars/kgs-test/trashbin/", server.trashBinHref)
        server.respondNext("PROPFIND", "/trashbin/") {
            MockResponse().setResponseCode(207).setBody(response)
        }
        val href = server.putRemote(server.eventsHref, "remote.ics", SampleIcs.event("nc34", "Deleted on server"))
        server.trashRemote(href)
        harness.addSyncedCalDavAccount()
        assertTrue(trashSupport()!!.supported)
        val item = trash().single()
        assertEquals(TrashOrigin.ServerTrashBin, item.origin)
        assertTrue(repository.restoreTrashedItem(item.id) is TrashRestoreResult.Restored)
        assertNotNull(harness.event(href))
        server.trashRemote(href)
        assertTrue(repository.refreshTrash())
        repository.deleteTrashedItemPermanently(trash().single().id)
        assertTrue(repository.refreshTrash())
        assertTrue(trash().isEmpty())
    }

    @Test
    fun oldIncorrectUnsupportedCacheIsRecheckedImmediately() = runTest {
        harness.addSyncedCalDavAccount()
        val account = harness.account(AccountEntity.PRIMARY_ID)!!
        val oldCache = CalDavTrashBinSupport(account.calendarHomeUrl!!, null,
            System.currentTimeMillis(), probeVersion = 0)
        harness.database.accountDao().updateCapabilitiesJson(account.id, oldCache.writeInto(account.capabilitiesJson))
        server.trashBinEnabled = true
        server.clearRequests()
        assertTrue(repository.refreshTrash())
        assertTrue(trashSupport()!!.supported)
        assertEquals(1, trashBinProbes().size)
    }

    @Test
    fun ordinaryDavCollectionDoesNotAdvertiseTrashSupport() = runTest {
        server.respondNext("PROPFIND", "/trashbin/") {
            MockResponse().setResponseCode(207).setBody(CalDavXml.multistatus(
                CalDavXml.trashBin(server.trashBinHref, null).replace("<nc:trash-bin />", "")))
        }
        harness.addSyncedCalDavAccount()
        assertFalse(trashSupport()!!.supported)
    }

    @Test
    fun syncDetectsTheTrashBinAndCachesItInTheAccountCapabilities() = runTest {
        server.trashBinEnabled = true
        server.trashBinRetentionSeconds = 14L * 24 * 60 * 60

        harness.addSyncedCalDavAccount()

        val support = trashSupport()!!
        assertTrue(support.supported)
        assertEquals(server.url(server.trashBinHref), support.trashBin!!.url)
        assertEquals(14L * 24 * 60 * 60, support.trashBin!!.retentionSeconds)
        assertEquals("0", trashBinProbes().single().header("Depth"))
        assertTrue(trashBinProbes().single().body.contains("trash-bin-retention-duration"))
        assertEquals("1", trashBinListings().single().header("Depth"))
        // The trash bin is a child of the calendar home but not a calendar.
        assertNull(harness.collection(server.trashBinHref))

        // A later sync keeps the cached check across discovery and reads the trash bin again.
        server.clearRequests()
        repository.syncNow()
        assertTrue(trashSupport()!!.supported)
        assertEquals(1, trashBinListings().size)
    }

    @Test
    fun serverWithoutTrashBinIsRememberedAndNotProbedOnEverySync() = runTest {
        harness.addSyncedCalDavAccount()

        val support = trashSupport()!!
        assertFalse(support.supported)
        assertNull(support.trashBin)
        assertEquals(1, trashBinProbes().size)
        assertTrue(trashBinListings().isEmpty())

        server.clearRequests()
        repository.syncNow()
        assertTrue(repository.refreshTrash())

        assertTrue(trashBinProbes().isEmpty())
        assertTrue(trashBinListings().isEmpty())
        assertFalse(trashSupport()!!.supported)
    }

    @Test
    fun trashBinWithZeroRetentionCountsAsUnsupported() = runTest {
        // Nextcloud deletes right away with a retention of 0, so there is nothing to restore there.
        server.trashBinEnabled = true
        server.trashBinRetentionSeconds = 0
        val eventHref = server.putRemote(server.eventsHref, "kickoff.ics", SampleIcs.event("remote-event", "Kickoff"))

        harness.addSyncedCalDavAccount()
        assertFalse(trashSupport()!!.supported)

        repository.deleteEvent("remote-event")
        repository.syncNow()

        val item = trash().single()
        assertEquals(TrashOrigin.LocalSnapshot, item.origin)
        assertEquals(eventHref, item.resourceHref)
        assertTrue(trashBinListings().isEmpty())
    }

    // --- Listing --------------------------------------------------------------------------------

    @Test
    fun itemsDeletedElsewhereAreListedWithTheNextcloudProperties() = runTest {
        server.trashBinEnabled = true
        server.trashBinRetentionSeconds = 7L * 24 * 60 * 60
        val eventHref = server.putRemote(server.eventsHref, "kickoff.ics", SampleIcs.event("remote-event", "Kickoff"))
        val taskHref = server.putRemote(server.tasksHref, "todo.ics", SampleIcs.task("remote-task", "Write agenda"))
        harness.addSyncedCalDavAccount()
        assertTrue(trash().isEmpty())
        val deletedAt = System.currentTimeMillis() / 1000 - 3600
        // Deleted in the Nextcloud web UI or on another device.
        val eventObject = server.trashRemote(eventHref, deletedAt)
        val taskObject = server.trashRemote(taskHref, deletedAt - 60)
        server.clearRequests()

        assertTrue(repository.refreshTrash())

        val listing = trashBinListings().single()
        assertTrue(listing.body.contains("calendar-query"))
        assertTrue(listing.body.contains("deleted-at"))
        assertTrue(listing.body.contains("calendar-uri"))
        val items = trash()
        assertEquals(2, items.size)
        val event = items.single { it.componentType == ComponentType.Event }
        assertEquals(TrashOrigin.ServerTrashBin, event.origin)
        assertEquals(eventObject, event.serverHref)
        assertEquals("remote-event", event.uid)
        assertEquals("Kickoff", event.title)
        assertEquals(server.eventsHref, event.collectionHref)
        assertEquals("Events", event.collectionName)
        assertEquals(harness.collection(server.eventsHref)!!.color, event.collectionColor)
        assertEquals(AccountEntity.PRIMARY_ID, event.accountId)
        assertEquals(SourceType.CalDav, event.sourceType)
        assertEquals(deletedAt * 1000, event.deletedAtMillis)
        assertEquals((deletedAt + 7L * 24 * 60 * 60) * 1000, event.expiresAtMillis)
        assertEquals(7, TrashRetention.daysLeft(event.expiresAtMillis, System.currentTimeMillis()))
        assertTrue(event.rawIcs.unfoldedIcs().contains("SUMMARY:Kickoff"))
        assertTrue(event.hasTime)
        val task = items.single { it.componentType == ComponentType.Task }
        assertEquals(taskObject, task.serverHref)
        assertEquals("remote-task", task.uid)
        assertEquals("Write agenda", task.title)
        assertEquals(server.tasksHref, task.collectionHref)
        assertEquals((deletedAt - 60) * 1000, task.deletedAtMillis)
        // Newest delete first.
        assertEquals(listOf(event.id, task.id), repository.observeTrashedItems().first().map { it.id })

        // A second refresh updates the cached rows in place instead of adding new ones.
        assertTrue(repository.refreshTrash())
        assertEquals(items.map { it.id }.toSet(), trash().map { it.id }.toSet())
    }

    @Test
    fun syncListsItemsDeletedElsewhereAndDropsOnesTheServerNoLongerHas() = runTest {
        server.trashBinEnabled = true
        val eventHref = server.putRemote(server.eventsHref, "kickoff.ics", SampleIcs.event("remote-event", "Kickoff"))
        harness.addSyncedCalDavAccount()
        val objectHref = server.trashRemote(eventHref)

        repository.syncNow()

        assertNull(harness.event(eventHref))
        assertEquals(objectHref, trash().single().serverHref)

        // The server's retention job purged it, or it was deleted for good on another device.
        server.expireTrashed(objectHref)
        repository.syncNow()

        assertTrue(trash().isEmpty())
    }

    @Test
    fun failedRefreshKeepsTheCachedItems() = runTest {
        server.trashBinEnabled = true
        val eventHref = server.putRemote(server.eventsHref, "kickoff.ics", SampleIcs.event("remote-event", "Kickoff"))
        harness.addSyncedCalDavAccount()
        server.trashRemote(eventHref)
        assertTrue(repository.refreshTrash())
        val cached = trash().single()

        server.respondNext("REPORT", "/trashbin/objects/") { MockResponse().setResponseCode(503) }

        assertFalse(repository.refreshTrash())
        assertEquals(listOf(cached), trash())
    }

    @Test
    fun serverItemsPastTheirRetentionArePurgedLocally() = runTest {
        server.trashBinEnabled = true
        server.trashBinRetentionSeconds = 2L * 24 * 60 * 60
        val eventHref = server.putRemote(server.eventsHref, "kickoff.ics", SampleIcs.event("remote-event", "Kickoff"))
        harness.addSyncedCalDavAccount()
        server.trashRemote(eventHref)
        assertTrue(repository.refreshTrash())
        val item = trash().single()

        assertEquals(0, harness.components.trash.purgeExpired(item.expiresAtMillis - 1))
        assertEquals(1, harness.components.trash.purgeExpired(item.expiresAtMillis + 1))
        assertTrue(trash().isEmpty())
    }

    // --- Deduplication --------------------------------------------------------------------------

    @Test
    fun localSnapshotGivesWayToTheServerItemOnceTheDeleteIsUploaded() = runTest {
        server.trashBinEnabled = true
        val eventHref = server.putRemote(server.eventsHref, "kickoff.ics", SampleIcs.event("remote-event", "Kickoff"))
        harness.addSyncedCalDavAccount()
        repository.updateEventManualColor("remote-event", 0xFF336699.toInt())

        repository.deleteEvent("remote-event")

        // Until the DELETE is uploaded only the local snapshot exists, even after a refresh.
        assertTrue(repository.refreshTrash())
        val snapshot = trash().single()
        assertEquals(TrashOrigin.LocalSnapshot, snapshot.origin)
        assertEquals(MutationAction.Delete, harness.pendingMutations().single().action)

        repository.syncNow()

        assertTrue(harness.pendingMutations().isEmpty())
        val trashed = server.trashedObjects().single()
        assertEquals(eventHref, trashed.originalHref)
        val item = trash().single()
        assertEquals(TrashOrigin.ServerTrashBin, item.origin)
        assertEquals(trashed.objectHref, item.serverHref)
        assertEquals("remote-event", item.uid)
        assertEquals(eventHref, item.resourceHref)
        assertEquals(0xFF336699.toInt(), item.manualColor)
    }

    @Test
    fun snapshotOfAnItemAlreadyTrashedElsewhereIsNotListedTwice() = runTest {
        server.trashBinEnabled = true
        val eventHref = server.putRemote(server.eventsHref, "kickoff.ics", SampleIcs.event("remote-event", "Kickoff"))
        harness.addSyncedCalDavAccount()
        // Deleted on the web first; the app still shows the event and the user deletes it too.
        val objectHref = server.trashRemote(eventHref)
        assertTrue(repository.refreshTrash())
        assertNotNull(harness.event(eventHref))
        repository.deleteEvent("remote-event")
        assertEquals(2, trash().size)

        // The DELETE finds the resource gone; the server's trash item stays the one listed.
        repository.syncNow()

        val item = trash().single()
        assertEquals(TrashOrigin.ServerTrashBin, item.origin)
        assertEquals(objectHref, item.serverHref)
    }

    // --- Restore --------------------------------------------------------------------------------

    @Test
    fun restoreMovesTheObjectOntoTheRestoreCollectionAndPullsItBack() = runTest {
        server.trashBinEnabled = true
        val eventHref = server.putRemote(server.eventsHref, "kickoff.ics", SampleIcs.event("remote-event", "Kickoff"))
        harness.addSyncedCalDavAccount()
        repository.updateEventManualColor("remote-event", 0xFF336699.toInt())
        repository.deleteEvent("remote-event")
        repository.syncNow()
        assertNull(harness.event(eventHref))
        val item = trash().single()
        assertEquals(TrashOrigin.ServerTrashBin, item.origin)
        server.clearRequests()

        val result = repository.restoreTrashedItem(item.id)

        assertEquals(TrashRestoreResult.Restored(server.eventsHref, "Events", inOriginalCalendar = true), result)
        val move = server.requests("MOVE").single()
        assertEquals(item.serverHref, move.path)
        val objectName = item.serverHref!!.substringAfterLast('/')
        assertEquals(server.url("${server.trashBinHref}restore/$objectName"), move.header("Destination"))
        assertNotNull(server.stored(eventHref))
        assertTrue(server.trashedObjects().isEmpty())
        // No create is queued: the server restored the object itself, and the calendar was pulled.
        assertTrue(harness.pendingMutations().isEmpty())
        assertTrue(server.requests("PUT").isEmpty())
        val restored = harness.event(eventHref)!!
        assertEquals("remote-event", restored.uid)
        assertEquals("Kickoff", restored.title)
        assertEquals(0xFF336699.toInt(), restored.manualColor)
        assertEquals(server.stored(eventHref)!!.etag, harness.resource(eventHref)!!.etag)
        assertTrue(trash().isEmpty())
    }

    @Test
    fun restoreOfATaskDeletedElsewhereBringsItBack() = runTest {
        server.trashBinEnabled = true
        val taskHref = server.putRemote(server.tasksHref, "todo.ics", SampleIcs.task("remote-task", "Write agenda"))
        harness.addSyncedCalDavAccount()
        server.trashRemote(taskHref)
        repository.syncNow()
        assertNull(harness.task(taskHref))

        val result = repository.restoreTrashedItem(trash().single().id)

        assertEquals(TrashRestoreResult.Restored(server.tasksHref, "Tasks", inOriginalCalendar = true), result)
        assertEquals("Write agenda", harness.task(taskHref)!!.title)
        assertTrue(trash().isEmpty())
    }

    @Test
    fun restoreOfAnExpiredObjectReportsItGoneAndDropsIt() = runTest {
        server.trashBinEnabled = true
        val eventHref = server.putRemote(server.eventsHref, "kickoff.ics", SampleIcs.event("remote-event", "Kickoff"))
        harness.addSyncedCalDavAccount()
        val objectHref = server.trashRemote(eventHref)
        repository.syncNow()
        val item = trash().single()
        server.expireTrashed(objectHref)

        assertEquals(TrashRestoreResult.GoneFromServer, repository.restoreTrashedItem(item.id))

        assertEquals(item.serverHref, server.requests("MOVE").single().path)
        assertTrue(trash().isEmpty())
        assertNull(harness.event(eventHref))
    }

    @Test
    fun restoreIntoACalendarThatIsGoneIsRefusedAndTheItemStays() = runTest {
        server.trashBinEnabled = true
        val eventHref = server.putRemote(server.eventsHref, "kickoff.ics", SampleIcs.event("remote-event", "Kickoff"))
        harness.addSyncedCalDavAccount()
        server.trashRemote(eventHref)
        repository.syncNow()
        val item = trash().single()
        server.removeCollection(server.eventsHref)
        server.clearRequests()

        assertEquals(TrashRestoreResult.ServerRefused(409), repository.restoreTrashedItem(item.id))

        // The trash bin is read again after the refusal.
        assertEquals(1, trashBinListings().size)
        assertEquals(item.serverHref, trash().single().serverHref)
    }

    @Test
    fun restoreOntoATakenNameIsRefusedAndTheItemStays() = runTest {
        server.trashBinEnabled = true
        val eventHref = server.putRemote(server.eventsHref, "kickoff.ics", SampleIcs.event("remote-event", "Kickoff"))
        harness.addSyncedCalDavAccount()
        server.trashRemote(eventHref)
        repository.syncNow()
        val item = trash().single()
        server.putRemote(server.eventsHref, "kickoff.ics", SampleIcs.event("other-event", "Replacement"))

        assertEquals(TrashRestoreResult.ServerRefused(403), repository.restoreTrashedItem(item.id))

        assertEquals(item.serverHref, trash().single().serverHref)
        assertEquals(1, server.trashedObjects().size)
    }

    // --- Delete permanently ---------------------------------------------------------------------

    @Test
    fun deletePermanentlyDeletesTheTrashBinObject() = runTest {
        server.trashBinEnabled = true
        val eventHref = server.putRemote(server.eventsHref, "kickoff.ics", SampleIcs.event("remote-event", "Kickoff"))
        harness.addSyncedCalDavAccount()
        val objectHref = server.trashRemote(eventHref)
        assertTrue(repository.refreshTrash())
        server.clearRequests()

        repository.deleteTrashedItemPermanently(trash().single().id)

        assertEquals(objectHref, server.requests("DELETE").single().path)
        assertTrue(server.trashedObjects().isEmpty())
        assertTrue(trash().isEmpty())
    }

    @Test
    fun failedPermanentDeleteKeepsTheItem() = runTest {
        server.trashBinEnabled = true
        val eventHref = server.putRemote(server.eventsHref, "kickoff.ics", SampleIcs.event("remote-event", "Kickoff"))
        harness.addSyncedCalDavAccount()
        server.trashRemote(eventHref)
        assertTrue(repository.refreshTrash())
        // Nextcloud refuses read-only sharees.
        server.respondNext("DELETE", "/trashbin/objects/") { MockResponse().setResponseCode(403) }

        val id = trash().single().id
        val error = expectFailure<HttpStatusException> { repository.deleteTrashedItemPermanently(id) }

        assertEquals(403, error.statusCode)
        assertEquals(1, trash().size)
        assertEquals(1, server.trashedObjects().size)
    }

    @Test
    fun emptyTrashDeletesServerObjectsAndLocalSnapshots() = runTest {
        server.trashBinEnabled = true
        val first = server.putRemote(server.eventsHref, "kickoff.ics", SampleIcs.event("remote-event", "Kickoff"))
        val second = server.putRemote(server.tasksHref, "todo.ics", SampleIcs.task("remote-task", "Write agenda"))
        harness.addSyncedCalDavAccount()
        repository.ensureLocalCalendar()
        repository.createEvent(eventPayload("Dentist", LocalDate.of(2026, 10, 12)))
        repository.deleteEvent(harness.eventsIn(LOCAL_COLLECTION).single().uid)
        server.trashRemote(first)
        server.trashRemote(second)
        assertTrue(repository.refreshTrash())
        assertEquals(3, trash().size)
        server.clearRequests()

        repository.emptyTrash()

        assertEquals(2, server.requests("DELETE").count { it.path.startsWith(server.trashObjectsHref) })
        assertTrue(server.trashedObjects().isEmpty())
        assertTrue(trash().isEmpty())
    }

    // --- Fallback and accounts ------------------------------------------------------------------

    @Test
    fun serverWithoutTrashBinKeepsTheLocalSnapshotAndItsRecreateRestore() = runTest {
        val eventHref = server.putRemote(server.eventsHref, "kickoff.ics", SampleIcs.event("remote-event", "Kickoff"))
        harness.addSyncedCalDavAccount()
        repository.deleteEvent("remote-event")
        repository.syncNow()
        assertNull(server.stored(eventHref))
        assertTrue(repository.refreshTrash())
        val item = trash().single()
        assertEquals(TrashOrigin.LocalSnapshot, item.origin)
        assertNull(item.serverHref)
        server.clearRequests()

        assertTrue(repository.restoreTrashedItem(item.id) is TrashRestoreResult.Restored)
        repository.pushPendingChangesCreatedSince(0)

        assertTrue(server.requests("MOVE").isEmpty())
        assertEquals("*", server.requests("PUT").single().header("If-None-Match"))
        assertNotNull(server.stored(eventHref))
    }

    @Test
    fun removingTheAccountDropsItsServerItemsButKeepsSnapshots() = runTest {
        server.trashBinEnabled = true
        val first = server.putRemote(server.eventsHref, "kickoff.ics", SampleIcs.event("remote-event", "Kickoff"))
        server.putRemote(server.eventsHref, "review.ics", SampleIcs.event("review-event", "Review"))
        harness.addSyncedCalDavAccount()
        server.trashRemote(first)
        repository.deleteEvent("review-event")
        assertTrue(repository.refreshTrash())
        assertEquals(setOf(TrashOrigin.ServerTrashBin, TrashOrigin.LocalSnapshot), trash().map { it.origin }.toSet())

        repository.deleteAccount(AccountEntity.PRIMARY_ID)

        val item = trash().single()
        assertEquals(TrashOrigin.LocalSnapshot, item.origin)
        assertEquals("review-event", item.uid)
    }
}
