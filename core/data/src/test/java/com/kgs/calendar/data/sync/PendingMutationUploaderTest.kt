package com.kgs.calendar.data.sync

import com.kgs.calendar.data.RepositoryHarness
import com.kgs.calendar.data.SampleIcs
import com.kgs.calendar.data.eventPayload
import com.kgs.calendar.data.expectFailure
import com.kgs.calendar.data.local.entity.AccountEntity
import com.kgs.calendar.data.remote.HttpStatusException
import com.kgs.calendar.data.unfoldedIcs
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class PendingMutationUploaderTest {
    private val harness = RepositoryHarness()
    private val repository = harness.repository
    private val uploader = harness.components.uploader
    private val server = harness.server
    private val day = LocalDate.of(2026, 10, 12)
    private lateinit var eventHref: String

    @Before
    fun setUp() = runTest {
        eventHref = server.putRemote(server.eventsHref, "kickoff.ics", SampleIcs.event("remote-event", "Kickoff"))
        harness.addSyncedCalDavAccount()
        server.clearRequests()
    }

    @After
    fun tearDown() = harness.close()

    private suspend fun createEvent(title: String): String {
        repository.createEvent(eventPayload(title, day, collectionHref = server.eventsHref))
        return harness.eventsIn(server.eventsHref).single { it.title == title }.resourceHref
    }

    @Test
    fun failedMutationDoesNotStopLaterOnesAndFirstFailureIsRethrown() = runTest {
        val failingHref = createEvent("First")
        val laterHref = createEvent("Second")
        server.respondNext("PUT", failingHref) { MockResponse().setResponseCode(500) }

        val error = expectFailure<HttpStatusException> { uploader.pushPendingMutations(harness.pendingMutations()) }

        assertEquals("PUT $failingHref failed: HTTP 500", error.message)
        assertEquals(listOf(failingHref, laterHref), server.requests("PUT").map { it.path })
        assertEquals(failingHref, harness.pendingMutations().single().resourceHref)
        assertEquals("PUT $failingHref failed: HTTP 500", harness.resource(failingHref)!!.syncError)
        assertEquals(server.stored(laterHref)!!.etag, harness.resource(laterHref)!!.etag)
        assertNull(harness.resource(laterHref)!!.syncError)
    }

    @Test
    fun editQueuedDuringUploadIsRebasedOnTheUploadedEtag() = runTest {
        repository.updateEvent("remote-event", eventPayload("Local edit 1", day))
        val firstUpload = harness.pendingMutations().single()
        server.beforeResponse = { request ->
            if (request.method == "PUT" && request.path == eventHref) {
                server.beforeResponse = null
                runBlocking { repository.updateEvent("remote-event", eventPayload("Local edit 2", day)) }
            }
        }

        uploader.pushPendingMutations(listOf(firstUpload))

        val uploadedEtag = server.stored(eventHref)!!.etag
        assertTrue(server.stored(eventHref)!!.ics.unfoldedIcs().contains("SUMMARY:Local edit 1"))
        assertEquals(uploadedEtag, harness.resource(eventHref)!!.etag)
        val queued = harness.pendingMutations().single()
        assertEquals(uploadedEtag, queued.baseEtag)
        assertTrue(queued.payloadIcs!!.unfoldedIcs().contains("SUMMARY:Local edit 2"))

        uploader.pushPendingMutations(harness.pendingMutations())

        assertEquals(uploadedEtag, server.requests("PUT").last().header("If-Match"))
        assertTrue(server.stored(eventHref)!!.ics.unfoldedIcs().contains("SUMMARY:Local edit 2"))
        assertTrue(harness.pendingMutations().isEmpty())
    }

    @Test
    fun putAnsweredWithoutEtagStoresEtagLookedUpAfterwards() = runTest {
        val href = createEvent("Planning")
        server.answerNextPutWithoutEtag(href)

        uploader.pushPendingMutations(harness.pendingMutations())

        assertTrue(server.requests("PROPFIND").any { it.path == href && it.header("Depth") == "0" })
        assertEquals(server.stored(href)!!.etag, harness.resource(href)!!.etag)
        assertTrue(harness.pendingMutations().isEmpty())
    }

    @Test
    fun withoutAnyServerEtagTheSubmittedBaseEtagIsStored() = runTest {
        val originalEtag = harness.resource(eventHref)!!.etag
        repository.updateEvent("remote-event", eventPayload("Local edit", day))
        server.answerNextPutWithoutEtag(eventHref)
        server.respondNext("PROPFIND", eventHref) { MockResponse().setResponseCode(500) }

        uploader.pushPendingMutations(harness.pendingMutations())

        assertEquals(originalEtag, server.requests("PUT").single().header("If-Match"))
        // NOTE: current behaviour - the pre-upload ETag is kept, so the next pull re-downloads our own upload.
        assertEquals(originalEtag, harness.resource(eventHref)!!.etag)
        assertTrue(harness.pendingMutations().isEmpty())
    }

    @Test
    fun mutationsOfAccountsWithoutCredentialsAreSkipped() = runTest {
        val href = createEvent("Planning")
        harness.credentials.clear(AccountEntity.PRIMARY_ID)

        uploader.pushPendingMutations(harness.pendingMutations())

        assertTrue(server.requests("PUT").isEmpty())
        assertEquals(href, harness.pendingMutations().single().resourceHref)
        assertNull(harness.resource(href)!!.syncError)
    }

    @Test
    fun pushPendingSkipsMutationsOfDisabledCollections() = runTest {
        val href = createEvent("Planning")
        repository.setCollectionEnabled(server.eventsHref, false)
        val credentials = harness.credentials.get(AccountEntity.PRIMARY_ID)!!

        uploader.pushPending(credentials, AccountEntity.PRIMARY_ID)

        assertTrue(server.requests("PUT").isEmpty())
        assertNotNull(harness.pendingMutations().singleOrNull { it.resourceHref == href })

        repository.setCollectionEnabled(server.eventsHref, true)
        uploader.pushPending(credentials, AccountEntity.PRIMARY_ID)

        assertEquals(href, server.requests("PUT").single().path)
        assertTrue(harness.pendingMutations().isEmpty())
    }
}
