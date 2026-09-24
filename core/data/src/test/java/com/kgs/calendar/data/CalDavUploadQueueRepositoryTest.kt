package com.kgs.calendar.data

import com.kgs.calendar.data.local.entity.AccountEntity
import com.kgs.calendar.data.remote.HttpStatusException
import com.kgs.calendar.domain.model.ComponentType
import com.kgs.calendar.domain.model.MutationAction
import com.kgs.calendar.domain.model.SyncState
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
class CalDavUploadQueueRepositoryTest {
    private val harness = RepositoryHarness()
    private val repository = harness.repository
    private val server = harness.server
    private val day = LocalDate.of(2026, 10, 12)
    private lateinit var eventHref: String
    private lateinit var taskHref: String

    @Before
    fun setUp() = runTest {
        eventHref = server.putRemote(server.eventsHref, "kickoff.ics", SampleIcs.event("remote-event", "Kickoff"))
        taskHref = server.putRemote(server.tasksHref, "todo.ics", SampleIcs.task("remote-task", "Write agenda"))
        harness.addSyncedCalDavAccount()
        server.clearRequests()
    }

    @After
    fun tearDown() = harness.close()

    private fun puts() = server.requests("PUT")

    /** Creates an event whose upload the server stored, but without the client learning its ETag. */
    private suspend fun createEventUploadedWithoutEtag(): String {
        repository.createEvent(eventPayload("Planning", day, collectionHref = server.eventsHref))
        val href = harness.eventsIn(server.eventsHref).single { it.title == "Planning" }.resourceHref
        server.answerNextPutWithoutEtag(href)
        server.respondNext("PROPFIND", href) { MockResponse().setResponseCode(500) }
        repository.pushPendingChangesCreatedSince(0)
        assertNotNull(server.stored(href))
        assertNull(harness.resource(href)!!.etag)
        assertTrue(harness.pendingMutations().isEmpty())
        return href
    }

    @Test
    fun createEventInCalDavCollectionEnqueuesCreatePut() = runTest {
        repository.createEvent(eventPayload("Planning", day, collectionHref = server.eventsHref))

        val event = harness.eventsIn(server.eventsHref).single { it.title == "Planning" }
        assertEquals("${server.eventsHref}${event.uid.replace("@", "%40")}.ics", event.resourceHref)
        val resource = harness.resource(event.resourceHref)!!
        assertNull(resource.etag)
        val mutation = harness.pendingMutations().single()
        assertEquals(AccountEntity.PRIMARY_ID, mutation.accountId)
        assertEquals(server.eventsHref, mutation.collectionHref)
        assertEquals(event.resourceHref, mutation.resourceHref)
        assertEquals(ComponentType.Event, mutation.componentType)
        assertEquals(MutationAction.Put, mutation.action)
        assertNull(mutation.baseEtag)
        assertEquals(resource.rawIcs, mutation.payloadIcs)
        assertTrue(puts().isEmpty())
    }

    @Test
    fun syncNowUploadsCreateWithIfNoneMatchAndStoresServerEtag() = runTest {
        repository.createEvent(eventPayload("Planning", day, collectionHref = server.eventsHref))
        val event = harness.eventsIn(server.eventsHref).single { it.title == "Planning" }
        val payload = harness.pendingMutations().single().payloadIcs

        repository.syncNow()

        val put = puts().single()
        assertEquals(event.resourceHref, put.path)
        assertEquals("*", put.header("If-None-Match"))
        assertNull(put.header("If-Match"))
        assertTrue(put.header("Content-Type")!!.startsWith("text/calendar"))
        assertEquals(payload, put.body)
        assertTrue(harness.pendingMutations().isEmpty())
        val stored = server.stored(event.resourceHref)!!
        assertEquals(stored.etag, harness.resource(event.resourceHref)!!.etag)
        assertNull(harness.resource(event.resourceHref)!!.syncError)
        assertEquals("Planning", harness.event(event.resourceHref)!!.title)
    }

    @Test
    fun editOfSyncedEventReplacesQueuedPutAndUploadsWithIfMatch() = runTest {
        val originalEtag = harness.resource(eventHref)!!.etag!!

        repository.updateEvent("remote-event", eventPayload("Kickoff v2", day, LocalTime.of(9, 0), LocalTime.of(10, 0)))
        repository.updateEvent("remote-event", eventPayload("Kickoff v3", day, LocalTime.of(9, 0), LocalTime.of(10, 0)))

        val mutation = harness.pendingMutations().single()
        assertEquals(MutationAction.Put, mutation.action)
        assertEquals(eventHref, mutation.resourceHref)
        assertEquals(originalEtag, mutation.baseEtag)
        assertEquals(harness.resource(eventHref)!!.rawIcs, mutation.payloadIcs)
        assertTrue(mutation.payloadIcs!!.unfoldedIcs().contains("SUMMARY:Kickoff v3"))
        assertEquals(2, harness.event(eventHref)!!.sequence)

        repository.syncNow()

        val put = puts().single()
        assertEquals(eventHref, put.path)
        assertEquals(originalEtag, put.header("If-Match"))
        assertNull(put.header("If-None-Match"))
        val stored = server.stored(eventHref)!!
        assertNotEquals(originalEtag, stored.etag)
        assertEquals(stored.etag, harness.resource(eventHref)!!.etag)
        assertTrue(stored.ics.unfoldedIcs().contains("SUMMARY:Kickoff v3"))
        assertTrue(harness.pendingMutations().isEmpty())
    }

    @Test
    fun preconditionFailureRetriesOnceWithCurrentServerEtag() = runTest {
        val originalEtag = harness.resource(eventHref)!!.etag!!
        repository.updateEvent("remote-event", eventPayload("Local edit", day))
        server.putRemote(server.eventsHref, "kickoff.ics", SampleIcs.event("remote-event", "Remote edit", sequence = 1))
        val remoteEtag = server.stored(eventHref)!!.etag

        repository.syncNow()

        assertEquals(listOf(originalEtag, remoteEtag), puts().map { it.header("If-Match") })
        assertTrue(server.requests("PROPFIND").any { it.path == eventHref && it.header("Depth") == "0" })
        assertTrue(harness.pendingMutations().isEmpty())
        val stored = server.stored(eventHref)!!
        assertEquals(stored.etag, harness.resource(eventHref)!!.etag)
        // NOTE: current behaviour - the conflict retry overwrites the concurrent remote edit (last writer wins).
        assertTrue(stored.ics.unfoldedIcs().contains("SUMMARY:Local edit"))
        assertEquals("Local edit", harness.event(eventHref)!!.title)
        assertEquals(SyncState.Idle, harness.account(AccountEntity.PRIMARY_ID)!!.syncState)
    }

    @Test
    fun deleteOfSyncedEventEnqueuesDeleteAndSyncRemovesIt() = runTest {
        val etag = harness.resource(eventHref)!!.etag

        repository.deleteEvent("remote-event")

        val mutation = harness.pendingMutations().single()
        assertEquals(MutationAction.Delete, mutation.action)
        assertEquals(eventHref, mutation.resourceHref)
        assertEquals(etag, mutation.baseEtag)
        assertNull(mutation.payloadIcs)
        // NOTE: current behaviour - the local row stays until the DELETE has been uploaded.
        assertNotNull(harness.event(eventHref))

        repository.syncNow()

        val delete = server.requests("DELETE").single()
        assertEquals(eventHref, delete.path)
        assertEquals(etag, delete.header("If-Match"))
        assertNull(server.stored(eventHref))
        assertNull(harness.event(eventHref))
        assertNull(harness.resource(eventHref))
        assertTrue(harness.pendingMutations().isEmpty())
    }

    @Test
    fun deletingNeverUploadedEventReplacesPutWithUnconditionalDelete() = runTest {
        repository.createEvent(eventPayload("Scratch", day, collectionHref = server.eventsHref))
        val event = harness.eventsIn(server.eventsHref).single { it.title == "Scratch" }

        repository.deleteEvent(event.uid)

        val mutation = harness.pendingMutations().single()
        assertEquals(MutationAction.Delete, mutation.action)
        assertNull(mutation.baseEtag)

        repository.syncNow()

        assertTrue(puts().isEmpty())
        val delete = server.requests("DELETE").single()
        assertEquals(event.resourceHref, delete.path)
        assertNull(delete.header("If-Match"))
        assertNull(harness.event(event.resourceHref))
        assertTrue(harness.pendingMutations().isEmpty())
    }

    @Test
    fun taskStatusChangeIsQueuedAgainstEtagAndUploaded() = runTest {
        val etag = harness.resource(taskHref)!!.etag

        repository.setTaskStatus(taskHref, "COMPLETED")

        val mutation = harness.pendingMutations().single()
        assertEquals(ComponentType.Task, mutation.componentType)
        assertEquals(etag, mutation.baseEtag)
        assertTrue(mutation.payloadIcs!!.unfoldedIcs().contains("STATUS:COMPLETED"))

        repository.syncNow()

        assertEquals(etag, puts().single().header("If-Match"))
        assertTrue(server.stored(taskHref)!!.ics.unfoldedIcs().contains("STATUS:COMPLETED"))
        assertTrue(harness.task(taskHref)!!.isCompleted)
        assertTrue(harness.pendingMutations().isEmpty())
    }

    @Test
    fun deletingSyncedParentTaskQueuesChildReparentPutAndParentDelete() = runTest {
        val parentEtag = harness.resource(taskHref)!!.etag
        repository.createTask(taskPayload("Sub step", collectionHref = server.tasksHref, parentUid = "remote-task"))
        repository.pushPendingChangesCreatedSince(0)
        val child = harness.tasksIn(server.tasksHref).single { it.title == "Sub step" }
        val childEtag = harness.resource(child.resourceHref)!!.etag!!
        server.clearRequests()

        repository.deleteTask("remote-task")

        // As for events, the parent row stays until the DELETE is uploaded.
        assertNotNull(harness.task(taskHref))
        assertNull(harness.task(child.resourceHref)!!.parentUid)
        val mutations = harness.pendingMutations().associateBy { it.resourceHref }
        assertEquals(setOf(taskHref, child.resourceHref), mutations.keys)
        assertEquals(MutationAction.Delete, mutations.getValue(taskHref).action)
        assertEquals(parentEtag, mutations.getValue(taskHref).baseEtag)
        assertEquals(MutationAction.Put, mutations.getValue(child.resourceHref).action)
        assertEquals(childEtag, mutations.getValue(child.resourceHref).baseEtag)
        assertFalse(mutations.getValue(child.resourceHref).payloadIcs!!.unfoldedIcs().contains("RELATED-TO"))

        repository.syncNow()

        assertEquals(childEtag, puts().single().header("If-Match"))
        assertEquals(taskHref, server.requests("DELETE").single().path)
        assertNull(server.stored(taskHref))
        assertNull(harness.task(taskHref))
        assertFalse(server.stored(child.resourceHref)!!.ics.unfoldedIcs().contains("RELATED-TO"))
        assertTrue(harness.pendingMutations().isEmpty())
    }

    @Test
    fun deletingOccurrenceOfSyncedSeriesUploadsMasterWithExdate() = runTest {
        val seriesHref = server.putRemote(
            server.eventsHref,
            "series.ics",
            SampleIcs.event("remote-series", "Weekly", rrule = "FREQ=WEEKLY;COUNT=4"),
        )
        repository.syncNow()
        val etag = harness.resource(seriesHref)!!.etag
        val secondOccurrence = Instant.parse("2026-10-12T08:00:00Z").toEpochMilli()
        server.clearRequests()

        repository.deleteEventOccurrence("remote-series", secondOccurrence)

        val mutation = harness.pendingMutations().single()
        assertEquals(seriesHref, mutation.resourceHref)
        assertEquals(etag, mutation.baseEtag)
        assertTrue(mutation.payloadIcs!!.unfoldedIcs().lines().any { it.startsWith("EXDATE") })

        repository.syncNow()

        assertEquals(etag, puts().single().header("If-Match"))
        assertTrue(server.stored(seriesHref)!!.ics.unfoldedIcs().lines().any { it.startsWith("EXDATE") })
        assertEquals(secondOccurrence.toString(), harness.event(seriesHref)!!.exDatesCsv)
        assertTrue(harness.pendingMutations().isEmpty())
    }

    @Test
    fun uploadFailureKeepsMutationMarksResourceAndFailsAccountSyncAfterPulling() = runTest {
        repository.createEvent(eventPayload("Planning", day, collectionHref = server.eventsHref))
        val event = harness.eventsIn(server.eventsHref).single { it.title == "Planning" }
        server.respondNext("PUT", server.eventsHref, times = 5) { MockResponse().setResponseCode(500) }
        val lastSyncBefore = harness.account(AccountEntity.PRIMARY_ID)!!.lastSyncAtMillis

        val error = expectFailure<IllegalStateException> { repository.syncNow() }

        assertEquals("Source \"alice\": Upload failed: PUT ${event.resourceHref} failed: HTTP 500", error.message)
        assertEquals(500, error.findCause<HttpStatusException>()!!.statusCode)
        val mutation = harness.pendingMutations().single()
        assertEquals(event.resourceHref, mutation.resourceHref)
        assertEquals("PUT ${event.resourceHref} failed: HTTP 500", harness.resource(event.resourceHref)!!.syncError)
        assertNull(harness.resource(event.resourceHref)!!.etag)
        assertEquals("Planning", harness.event(event.resourceHref)!!.title)
        val account = harness.account(AccountEntity.PRIMARY_ID)!!
        assertEquals(SyncState.Error, account.syncState)
        assertEquals(error.message, account.syncError)
        assertEquals(lastSyncBefore, account.lastSyncAtMillis)
        assertTrue(server.requests("PROPFIND").any { it.path == server.homeHref })
    }

    @Test
    fun rejectedUploadStillPullsOtherRemoteChanges() = runTest {
        val originalEtag = harness.resource(eventHref)!!.etag
        repository.updateEvent("remote-event", eventPayload("Local edit", day))
        server.respondNext("PUT", eventHref, times = 5) { MockResponse().setResponseCode(403) }
        val reviewHref = server.putRemote(server.eventsHref, "review.ics", SampleIcs.event("remote-review", "Review"))
        server.putRemote(server.tasksHref, "todo.ics", SampleIcs.task("remote-task", "Write agenda v2"))

        val error = expectFailure<IllegalStateException> { repository.syncNow() }

        assertEquals("Source \"alice\": Upload failed: PUT $eventHref failed: HTTP 403", error.message)
        assertEquals(403, error.findCause<HttpStatusException>()!!.statusCode)
        assertEquals("Review", harness.event(reviewHref)!!.title)
        assertEquals(server.stored(reviewHref)!!.etag, harness.resource(reviewHref)!!.etag)
        assertEquals("Write agenda v2", harness.task(taskHref)!!.title)
        assertEquals("Local edit", harness.event(eventHref)!!.title)
        assertEquals(originalEtag, harness.resource(eventHref)!!.etag)
        assertEquals("PUT $eventHref failed: HTTP 403", harness.resource(eventHref)!!.syncError)
        val mutation = harness.pendingMutations().single()
        assertEquals(eventHref, mutation.resourceHref)
        assertEquals(MutationAction.Put, mutation.action)
        val account = harness.account(AccountEntity.PRIMARY_ID)!!
        assertEquals(SyncState.Error, account.syncState)
        assertEquals(error.message, account.syncError)
    }

    @Test
    fun rejectedUploadKeepsLocalVersionWhenSameResourceChangedRemotely() = runTest {
        val originalEtag = harness.resource(eventHref)!!.etag
        repository.updateEvent("remote-event", eventPayload("Local edit", day))
        server.respondNext("PUT", eventHref, times = 5) { MockResponse().setResponseCode(403) }
        server.putRemote(server.eventsHref, "kickoff.ics", SampleIcs.event("remote-event", "Other client edit", sequence = 5))

        expectFailure<IllegalStateException> { repository.syncNow() }

        assertTrue(server.requests("REPORT").any { "sync-collection" in it.body && it.path == server.eventsHref })
        assertEquals("Local edit", harness.event(eventHref)!!.title)
        assertTrue(harness.resource(eventHref)!!.rawIcs.unfoldedIcs().contains("SUMMARY:Local edit"))
        assertEquals(originalEtag, harness.resource(eventHref)!!.etag)
        assertEquals("PUT $eventHref failed: HTTP 403", harness.resource(eventHref)!!.syncError)
        assertEquals(eventHref, harness.pendingMutations().single().resourceHref)
        assertEquals(SyncState.Error, harness.account(AccountEntity.PRIMARY_ID)!!.syncState)
    }

    @Test
    fun pullFailureAfterRejectedUploadIsReportedWithUploadFailureSuppressed() = runTest {
        repository.updateEvent("remote-event", eventPayload("Local edit", day))
        server.respondNext("PUT", eventHref, times = 5) { MockResponse().setResponseCode(403) }
        server.respondNext("PROPFIND", server.homeHref, times = 5) { MockResponse().setResponseCode(500) }

        val error = expectFailure<IllegalStateException> { repository.syncNow() }

        val pullError = error.cause!!
        assertEquals(500, (pullError as HttpStatusException).statusCode)
        assertEquals(harness.account(AccountEntity.PRIMARY_ID)!!.syncError, error.message)
        assertFalse(error.message!!.contains("Upload failed"))
        val uploadError = pullError.suppressed.single()
        assertEquals("Upload failed: PUT $eventHref failed: HTTP 403", uploadError.message)
        assertEquals(403, (uploadError.cause as HttpStatusException).statusCode)
        assertEquals(SyncState.Error, harness.account(AccountEntity.PRIMARY_ID)!!.syncState)
        assertEquals("PUT $eventHref failed: HTTP 403", harness.resource(eventHref)!!.syncError)
        assertEquals(eventHref, harness.pendingMutations().single().resourceHref)
    }

    @Test
    fun rejectedUploadInOneAccountDoesNotFailSyncOrBlockOtherAccount() = runTest {
        FakeCalDavServer(username = "bob", password = "hunter2").use { otherServer ->
            otherServer.start()
            val otherEventHref = otherServer.putRemote(otherServer.eventsHref, "standup.ics", SampleIcs.event("bob-event", "Standup"))
            val other = repository.saveManualAccount(otherServer.serverUrl, otherServer.username, otherServer.password)
            repository.syncNow()
            assertEquals("Standup", harness.event(otherEventHref)!!.title)
            repository.updateEvent("remote-event", eventPayload("Local edit", day))
            server.respondNext("PUT", eventHref, times = 5) { MockResponse().setResponseCode(403) }
            val reviewHref = server.putRemote(server.eventsHref, "review.ics", SampleIcs.event("remote-review", "Review"))
            otherServer.putRemote(otherServer.eventsHref, "standup.ics", SampleIcs.event("bob-event", "Standup moved", sequence = 1))

            repository.syncNow()

            assertEquals("Review", harness.event(reviewHref)!!.title)
            assertEquals("Local edit", harness.event(eventHref)!!.title)
            assertEquals(SyncState.Error, harness.account(AccountEntity.PRIMARY_ID)!!.syncState)
            assertEquals("Standup moved", harness.event(otherEventHref)!!.title)
            val otherAccount = harness.account(other.id)!!
            assertEquals(SyncState.Idle, otherAccount.syncState)
            assertNull(otherAccount.syncError)
        }
    }

    @Test
    fun pushPendingChangesCreatedSinceOnlyUploadsRecentMutationsWithoutPulling() = runTest {
        val before = System.currentTimeMillis()
        repository.createEvent(eventPayload("Planning", day, collectionHref = server.eventsHref))
        val event = harness.eventsIn(server.eventsHref).single { it.title == "Planning" }

        repository.pushPendingChangesCreatedSince(System.currentTimeMillis() + 60_000)

        assertTrue(puts().isEmpty())
        assertEquals(1, harness.pendingMutations().size)

        repository.pushPendingChangesCreatedSince(before)

        assertEquals(event.resourceHref, puts().single().path)
        assertTrue(harness.pendingMutations().isEmpty())
        assertEquals(server.stored(event.resourceHref)!!.etag, harness.resource(event.resourceHref)!!.etag)
        assertTrue(server.requests("PROPFIND").none { it.path == server.homeHref || it.path == server.eventsHref })
        assertTrue(server.requests("REPORT").isEmpty())
    }

    @Test
    fun remoteChangeRightAfterLocalUploadIsAppliedOnNextSync() = runTest {
        repository.updateEvent("remote-event", eventPayload("Local edit", day))
        repository.pushPendingChangesCreatedSince(0)
        val uploadedEtag = harness.resource(eventHref)!!.etag
        server.putRemote(server.eventsHref, "kickoff.ics", SampleIcs.event("remote-event", "Other client edit", sequence = 5))
        val remoteEtag = server.stored(eventHref)!!.etag
        assertNotEquals(uploadedEtag, remoteEtag)

        repository.syncNow()

        assertEquals("Other client edit", harness.event(eventHref)!!.title)
        assertEquals(remoteEtag, harness.resource(eventHref)!!.etag)
        assertTrue(harness.pendingMutations().isEmpty())
    }

    @Test
    fun uploadWithoutEtagGetsServerEtagOnNextSyncAndKeepsOwnContent() = runTest {
        val href = createEventUploadedWithoutEtag()
        val payload = harness.resource(href)!!.rawIcs

        repository.syncNow()

        assertEquals(server.stored(href)!!.etag, harness.resource(href)!!.etag)
        assertEquals(payload, harness.resource(href)!!.rawIcs)
        assertEquals("Planning", harness.event(href)!!.title)
        assertTrue(harness.pendingMutations().isEmpty())
    }

    @Test
    fun remoteChangeAfterUploadWithoutEtagIsDownloadedOnNextSync() = runTest {
        val href = createEventUploadedWithoutEtag()
        val uid = harness.event(href)!!.uid
        server.putRemote(server.eventsHref, href.removePrefix(server.eventsHref), SampleIcs.event(uid, "Other client edit", sequence = 5))
        val remoteEtag = server.stored(href)!!.etag

        repository.syncNow()

        assertEquals("Other client edit", harness.event(href)!!.title)
        assertEquals(remoteEtag, harness.resource(href)!!.etag)
        assertTrue(harness.resource(href)!!.rawIcs.unfoldedIcs().contains("SUMMARY:Other client edit"))
        assertTrue(server.stored(href)!!.ics.unfoldedIcs().contains("SUMMARY:Other client edit"))
        assertTrue(harness.pendingMutations().isEmpty())
    }

    @Test
    fun localEditQueuedAfterUploadWithoutEtagWinsOverRemoteChange() = runTest {
        val href = createEventUploadedWithoutEtag()
        val uid = harness.event(href)!!.uid
        server.putRemote(server.eventsHref, href.removePrefix(server.eventsHref), SampleIcs.event(uid, "Other client edit", sequence = 5))
        repository.updateEvent(uid, eventPayload("Local edit", day))

        repository.syncNow()

        assertEquals("Local edit", harness.event(href)!!.title)
        assertTrue(server.stored(href)!!.ics.unfoldedIcs().contains("SUMMARY:Local edit"))
        assertEquals(server.stored(href)!!.etag, harness.resource(href)!!.etag)
        assertTrue(harness.pendingMutations().isEmpty())
    }

    @Test
    fun ownUploadListedBySyncCollectionIsRecognisedByEtagAndNotRefetched() = runTest {
        repository.updateEvent("remote-event", eventPayload("Local edit", day))
        val payload = harness.pendingMutations().single().payloadIcs

        repository.syncNow()

        // The sync-collection report lists our own PUT, but its ETag matches the one stored after the upload.
        assertTrue(server.requests("REPORT").any { "sync-collection" in it.body && it.path == server.eventsHref })
        assertTrue(server.requests("REPORT").none { "calendar-multiget" in it.body })
        assertEquals(server.stored(eventHref)!!.etag, harness.resource(eventHref)!!.etag)
        // The resource still holds the uploaded payload rather than the server's re-serialised copy.
        assertEquals(payload, harness.resource(eventHref)!!.rawIcs)
        assertEquals("Local edit", harness.event(eventHref)!!.title)
    }

    @Test
    fun fetchedResourceWhoseEtagMatchesStoredEtagIsNotReapplied() = runTest {
        val stored = harness.event(eventHref)!!
        harness.database.eventDao().upsert(stored.copy(title = "Local state"))
        val syncToken = harness.collection(server.eventsHref)!!.syncToken
        server.respondNext("REPORT", server.eventsHref) {
            MockResponse()
                .setResponseCode(207)
                .addHeader("Content-Type", "application/xml; charset=utf-8")
                .setBody(CalDavXml.multistatus(CalDavXml.etag(eventHref, "\"listing-etag\""), syncToken = syncToken))
        }

        repository.syncNow()

        assertTrue(server.requests("REPORT").any { "calendar-multiget" in it.body && eventHref in it.body })
        assertEquals("Local state", harness.event(eventHref)!!.title)
        assertEquals(server.stored(eventHref)!!.etag, harness.resource(eventHref)!!.etag)
    }

    @Test
    fun localEditQueuedWhileRemoteChangeDownloadsKeepsLocalVersion() = runTest {
        val originalEtag = harness.resource(eventHref)!!.etag
        server.putRemote(server.eventsHref, "kickoff.ics", SampleIcs.event("remote-event", "Other client edit", sequence = 1))
        server.beforeResponse = { request ->
            if (request.method == "REPORT" && "calendar-multiget" in request.body && request.path == server.eventsHref) {
                server.beforeResponse = null
                runBlocking { repository.updateEvent("remote-event", eventPayload("Local edit", day)) }
            }
        }

        repository.syncNow()

        assertEquals("Local edit", harness.event(eventHref)!!.title)
        assertEquals(originalEtag, harness.resource(eventHref)!!.etag)
        val mutation = harness.pendingMutations().single()
        assertEquals(eventHref, mutation.resourceHref)
        assertEquals(originalEtag, mutation.baseEtag)
    }

    @Test
    fun remoteDeletionWhileLocalEditIsQueuedKeepsLocalResource() = runTest {
        server.beforeResponse = { request ->
            if (request.method == "REPORT" && "sync-collection" in request.body && request.path == server.eventsHref) {
                server.beforeResponse = null
                server.deleteRemote(eventHref)
                runBlocking { repository.updateEvent("remote-event", eventPayload("Local edit", day)) }
            }
        }

        repository.syncNow()

        assertNull(server.stored(eventHref))
        assertEquals("Local edit", harness.event(eventHref)!!.title)
        assertNotNull(harness.resource(eventHref))
        assertEquals(MutationAction.Put, harness.pendingMutations().single().action)
    }

    @Test
    fun remoteDeletionRightAfterLocalUploadRemovesLocalResource() = runTest {
        repository.createEvent(eventPayload("Planning", day, collectionHref = server.eventsHref))
        val event = harness.eventsIn(server.eventsHref).single { it.title == "Planning" }
        repository.pushPendingChangesCreatedSince(0)
        server.deleteRemote(event.resourceHref)

        repository.syncNow()

        assertNull(harness.event(event.resourceHref))
        assertNull(harness.resource(event.resourceHref))
        assertTrue(harness.pendingMutations().isEmpty())
    }

    @Test
    fun invalidTaskScheduleIsRepairedOnceAndQueuedForUpload() = runTest {
        val task = harness.task(taskHref)!!
        harness.database.taskDao().upsert(
            task.copy(startAtMillis = task.dueAtMillis!! - 3_600_000, startHasTime = false, dueHasTime = true),
        )

        repository.repairInvalidTaskSchedules()

        val repaired = harness.task(taskHref)!!
        assertNull(repaired.startAtMillis)
        assertEquals(task.dueAtMillis, repaired.dueAtMillis)
        assertTrue(repaired.dueHasTime)
        val mutation = harness.pendingMutations().single()
        assertEquals(taskHref, mutation.resourceHref)
        assertEquals(harness.resource(taskHref)!!.etag, mutation.baseEtag)
        assertEquals(harness.resource(taskHref)!!.rawIcs, mutation.payloadIcs)

        repository.repairInvalidTaskSchedules()

        assertEquals(mutation, harness.pendingMutations().single())
    }
}
