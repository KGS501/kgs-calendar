package com.kgs.calendar.data

import com.kgs.calendar.domain.model.MutationAction
import com.kgs.calendar.domain.model.SyncState
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class SyncReliabilityRepositoryTest {
    private val harness = RepositoryHarness()
    private val repository = harness.repository
    private val server = harness.server
    private val day = LocalDate.of(2026, 10, 5)

    @After fun close() = harness.close()

    @Test fun aFailedCollectionDoesNotStarveTasksInTheSameAccount() = runTest {
        val task = server.putRemote(server.tasksHref, "task.ics", SampleIcs.task("task", "Before"))
        harness.addSyncedCalDavAccount()
        server.putRemote(server.tasksHref, "task.ics", SampleIcs.task("task", "After"))
        server.respondNext("REPORT", server.eventsHref, bodyContains = "sync-collection") { MockResponse().setResponseCode(503) }
        expectFailure<IllegalStateException> { repository.syncNow() }
        assertEquals("After", harness.task(task)!!.title)
    }

    @Test fun transientDownloadFailureIsReportedEvenWithAHealthyAccount() = runTest {
        val event = server.putRemote(server.eventsHref, "event.ics", SampleIcs.event("event", "Before"))
        harness.addSyncedCalDavAccount()
        server.setFeed("/healthy.ics", SampleIcs.event("healthy", "Healthy"))
        repository.addReadOnlyCalendar(server.url("/healthy.ics"), "Healthy")
        server.putRemote(server.eventsHref, "event.ics", SampleIcs.event("event", "After"))
        server.respondNext("REPORT", server.eventsHref, bodyContains = "calendar-multiget") { MockResponse().setResponseCode(503) }
        server.respondNext("GET", event) { MockResponse().setResponseCode(503) }
        expectFailure<IllegalStateException> { repository.syncNow() }
        assertEquals(SyncState.Error, harness.account("primary")!!.syncState)
        repository.syncNow()
        assertEquals("After", harness.event(event)!!.title)
    }

    @Test fun deletingWhilePutIsInFlightUsesItsAcknowledgedEtag() = runTest {
        val href = server.putRemote(server.eventsHref, "event.ics", SampleIcs.event("event", "Before"))
        harness.addSyncedCalDavAccount()
        repository.updateEvent("event", eventPayload("Edited", day))
        server.beforeResponse = { request ->
            if (request.method == "PUT" && request.path == href) {
                server.beforeResponse = null
                runBlocking { repository.deleteEvent("event") }
            }
        }
        repository.pushPendingChangesCreatedSince(0)
        val deletion = harness.pendingMutations().single()
        assertEquals(MutationAction.Delete, deletion.action)
        assertEquals(server.stored(href)!!.etag, deletion.baseEtag)
        repository.pushPendingChangesCreatedSince(0)
        assertNull(server.stored(href))
        assertTrue(harness.pendingMutations().isEmpty())
    }

    @Test fun anUploadSnapshotNeverSendsAnEditThatWasDeletedBeforeItsTurn() = runTest {
        val first = server.putRemote(server.eventsHref, "first.ics", SampleIcs.event("first", "First"))
        val second = server.putRemote(server.eventsHref, "second.ics", SampleIcs.event("second", "Second"))
        harness.addSyncedCalDavAccount()
        repository.updateEvent("first", eventPayload("First edited", day))
        repository.updateEvent("second", eventPayload("Second edited", day))
        server.clearRequests()
        server.beforeResponse = { request ->
            if (request.method == "PUT" && request.path == first) {
                server.beforeResponse = null
                runBlocking { repository.deleteEvent("second") }
            }
        }
        repository.pushPendingChangesCreatedSince(0)
        assertFalse(server.requests("PUT").any { it.path == second })
        repository.pushPendingChangesCreatedSince(0)
        assertNull(server.stored(second))
    }

    @Test fun remoteTaskChangingFromTimedToAllDayIsAppliedExactly() = runTest {
        val href = server.putRemote(server.tasksHref, "task.ics", SampleIcs.task("task", "Task"))
        harness.addSyncedCalDavAccount()
        assertTrue(harness.task(href)!!.dueHasTime)
        server.putRemote(server.tasksHref, "task.ics", SampleIcs.task("task", "Task")
            .replace("DUE:20261006T150000Z", "DUE;VALUE=DATE:20261006"))
        repository.syncNow()
        assertFalse(harness.task(href)!!.dueHasTime)
    }

    @Test fun disappearingCollectionDoesNotDestroyUnsentLocalWork() = runTest {
        val href = server.putRemote(server.eventsHref, "event.ics", SampleIcs.event("event", "Before"))
        harness.addSyncedCalDavAccount()
        repository.updateEvent("event", eventPayload("Unsent work", day))
        server.removeCollection(server.eventsHref)
        expectFailure<IllegalStateException> { repository.syncNow() }
        assertNotNull(harness.pendingMutations().singleOrNull { it.resourceHref == href })
        assertEquals("Unsent work", harness.event(href)!!.title)
    }
    @Test fun aMissingEtagPropertyInAnIncrementalReportIsNotADeletion() = runTest {
        val href = server.putRemote(server.eventsHref, "event.ics", SampleIcs.event("event", "Before"))
        harness.addSyncedCalDavAccount()
        server.putRemote(server.eventsHref, "event.ics", SampleIcs.event("event", "After"))
        val response = "<d:response><d:href>$href</d:href><d:propstat><d:prop><d:getetag/></d:prop>" +
            "<d:status>HTTP/1.1 404 Not Found</d:status></d:propstat></d:response>"
        server.respondNext("REPORT", server.eventsHref, bodyContains = "sync-collection") {
            MockResponse().setResponseCode(207).setBody(CalDavXml.multistatus(response, syncToken = "new-token"))
        }
        repository.syncNow()
        assertEquals("After", harness.event(href)!!.title)
    }

    @Test fun invalidSyncTokenForcesAListingEvenWhenCtagIsUnchanged() = runTest {
        val href = server.putRemote(server.eventsHref, "event.ics", SampleIcs.event("event", "Still here"))
        harness.addSyncedCalDavAccount()
        val oldToken = harness.collection(server.eventsHref)!!.syncToken
        server.respondNext("REPORT", server.eventsHref, bodyContains = "sync-collection") { MockResponse().setResponseCode(403) }
        server.clearRequests()
        repository.syncNow()
        assertTrue(server.requests("PROPFIND").any { it.path == server.eventsHref && it.header("Depth") == "1" })
        assertEquals("Still here", harness.event(href)!!.title)
        assertEquals(oldToken, harness.collection(server.eventsHref)!!.syncToken)
    }

    @Test fun anEmptyMalformedListingCannotEraseTheCache() = runTest {
        val href = server.putRemote(server.eventsHref, "event.ics", SampleIcs.event("event", "Retain me"))
        harness.addSyncedCalDavAccount()
        server.respondNext("PROPFIND", server.eventsHref) { MockResponse().setResponseCode(207).setBody("") }
        expectFailure<IllegalStateException> { repository.syncNow(forceFullCalDavRefresh = true) }
        assertEquals("Retain me", harness.event(href)!!.title)
    }

    @Test fun aLostPutAcknowledgementIsRecoveredWithoutOverwritingAnything() = runTest {
        val href = server.putRemote(server.eventsHref, "event.ics", SampleIcs.event("event", "Before"))
        harness.addSyncedCalDavAccount()
        repository.updateEvent("event", eventPayload("Edited", day))
        // Simulate an accepted request whose response was lost before local acknowledgement.
        val payload = harness.pendingMutations().single().payloadIcs!!
        server.putRemote(server.eventsHref, "event.ics", payload)
        repository.pushPendingChangesCreatedSince(0)
        assertTrue(harness.pendingMutations().isEmpty())
        assertEquals(server.stored(href)!!.etag, harness.resource(href)!!.etag)
        assertEquals(payload, server.stored(href)!!.ics)
    }

    @Test fun readOnlyFeedUsesConditionalRequestsAndKeepsCacheOnFailure() = runTest {
        val path = "/conditional.ics"
        server.setFeed(path, SampleIcs.event("feed", "Cached"))
        server.respondNext("GET", path) {
            MockResponse().setResponseCode(200).setHeader("ETag", "feed-v1").setBody(SampleIcs.event("feed", "Cached"))
        }
        val account = repository.addReadOnlyCalendar(server.url(path), "Feed")
        server.respondNext("GET", path) { MockResponse().setResponseCode(304) }
        repository.syncNow()
        assertEquals("feed-v1", server.requests("GET").last().header("If-None-Match"))
        server.respondNext("GET", path) { MockResponse().setResponseCode(503) }
        expectFailure<IllegalStateException> { repository.syncNow() }
        assertEquals("Cached", harness.eventsIn("readonly-${account.id}").single().title)
        assertEquals(SyncState.Error, harness.account(account.id)!!.syncState)
    }

    @Test fun anOldQueuedEditSurvivesAChangedLocalCacheAndStaysAConflict() = runTest {
        val href = server.putRemote(server.eventsHref, "event.ics", SampleIcs.event("event", "Before"))
        harness.addSyncedCalDavAccount()
        repository.updateEvent("event", eventPayload("Unsent edit", day))
        server.putRemote(server.eventsHref, "event.ics", SampleIcs.event("event", "Other client"))
        val remote = server.stored(href)!!
        // Simulate a cache written by an older app while an unsent edit remained in the outbox.
        harness.database.resourceDao().upsert(harness.resource(href)!!.copy(etag = remote.etag, rawIcs = remote.ics))
        expectFailure<IllegalStateException> { repository.syncNow() }
        assertTrue(harness.pendingMutations().single().payloadIcs!!.contains("Unsent edit"))
        assertEquals(remote.ics, server.stored(href)!!.ics)
    }

    @Test fun sameUidAtDifferentServerPathsDoesNotAuthorizeAnOverwrite() = runTest {
        val first = server.putRemote(server.eventsHref, "one.ics", SampleIcs.event("same", "One"))
        val second = server.putRemote(server.eventsHref, "two.ics", SampleIcs.event("same", "Two"))
        harness.addSyncedCalDavAccount()
        harness.components.repairs.repairDuplicateCalDavResources()
        assertNotNull(harness.resource(first))
        assertNotNull(harness.resource(second))
        assertTrue(server.requests("PUT").isEmpty())
    }

    @Test fun aLaterServerEditCannotBeCertifiedWithAnEtagOnlyLookup() = runTest {
        val href = server.putRemote(server.eventsHref, "event.ics", SampleIcs.event("event", "Before"))
        harness.addSyncedCalDavAccount()
        repository.updateEvent("event", eventPayload("Our edit", day))
        server.answerNextPutWithoutEtag(href)
        server.beforeResponse = { request ->
            if (request.method == "GET" && request.path == href) {
                server.beforeResponse = null
                server.putRemote(server.eventsHref, "event.ics", SampleIcs.event("event", "Later server edit"))
            }
        }
        repository.pushPendingChangesCreatedSince(0)
        assertNull(harness.resource(href)!!.etag)
        repository.syncNow()
        assertEquals("Later server edit", harness.event(href)!!.title)
    }

    @Test fun deleteWithoutAnEtagDoesNotRemoveAnotherClientsEdit() = runTest {
        val href = server.putRemote(server.eventsHref, "event.ics", SampleIcs.event("event", "Before"))
        harness.addSyncedCalDavAccount()
        harness.database.resourceDao().markSynced(href, null)
        repository.deleteEvent("event")
        server.putRemote(server.eventsHref, "event.ics", SampleIcs.event("event", "Other client edit"))
        expectFailure<IllegalStateException> { repository.syncNow() }
        assertTrue(server.stored(href)!!.ics.contains("Other client edit"))
        assertTrue(server.requests("DELETE").isEmpty())
        assertEquals(MutationAction.Delete, harness.pendingMutations().single().action)
    }

    @Test fun equalDavPathsOnDifferentServersStayInTheirOwnAccounts() = runTest {
        val firstHref = server.putRemote(server.tasksHref, "task.ics", SampleIcs.task("same-uid", "First server"))
        harness.addSyncedCalDavAccount()
        FakeCalDavServer().use { second ->
            second.start()
            val secondHref = second.putRemote(second.tasksHref, "task.ics", SampleIcs.task("same-uid", "Second server"))
            val secondAccount = repository.saveManualAccount(second.serverUrl, second.username, second.password)
            repository.syncNow()
            assertEquals("First server", harness.task(firstHref)!!.title)
            val secondCollection = harness.database.collectionDao().forAccount(secondAccount.id).single { it.supportsTasks }
            val task = harness.tasksIn(secondCollection.href).single()
            assertEquals("Second server", task.title)
            assertNotEquals(firstHref, task.resourceHref)
            repository.setTaskStatus(task.resourceHref, "COMPLETED")
            harness.components.syncOrchestrator.pushAllPendingChanges()
            assertTrue(second.stored(secondHref)!!.ics.contains("STATUS:COMPLETED"))
            assertFalse(server.stored(firstHref)!!.ics.contains("STATUS:COMPLETED"))
            repository.syncNow()
            assertEquals("First server", harness.task(firstHref)!!.title)
            assertEquals(task.resourceHref, harness.tasksIn(secondCollection.href).single().resourceHref)
        }
    }

    @Test fun olderCachesAreReconciledOnceEvenWhenTheServerEtagDidNotChange() = runTest {
        val href = server.putRemote(server.eventsHref, "event.ics", SampleIcs.event("event", "Authoritative title"))
        harness.addSyncedCalDavAccount()
        harness.database.eventDao().upsert(harness.event(href)!!.copy(title = "Old drift"))
        val capabilities = org.json.JSONObject(harness.collection(server.eventsHref)!!.capabilitiesJson!!)
        capabilities.remove("kgsReconciliationVersion")
        harness.database.collectionDao().updateCapabilitiesJson(server.eventsHref, capabilities.toString())
        repository.syncNow()
        assertEquals("Authoritative title", harness.event(href)!!.title)
        server.clearRequests()
        repository.syncNow()
        assertTrue(server.requests("GET").isEmpty())
        assertTrue(server.requests("REPORT").none { "calendar-multiget" in it.body || "calendar-query" in it.body })
    }

    @Test fun changingServerBindingPreservesUnsentWorkAndDoesNotReuseOldCache() = runTest {
        val href = server.putRemote(server.eventsHref, "event.ics", SampleIcs.event("event", "Before"))
        harness.addSyncedCalDavAccount()
        val account = harness.account("primary")!!
        repository.updateEvent("event", eventPayload("Unsent", day))
        expectFailure<IllegalStateException> {
            repository.updateAccount(account.id, "New", "https://new.example.test", "new-user", "new-password")
        }
        assertEquals(account.serverUrl, harness.account(account.id)!!.serverUrl)
        assertEquals("Unsent", harness.event(href)!!.title)
        harness.components.syncOrchestrator.pushAllPendingChanges()
        repository.updateAccount(account.id, "New", "https://new.example.test", "new-user", "new-password")
        assertTrue(harness.database.collectionDao().forAccount(account.id).isEmpty())
        assertNull(harness.resource(href))
    }

}
