package com.kgs.calendar.data

import com.kgs.calendar.domain.model.SyncState
import kotlinx.coroutines.test.runTest
import okhttp3.ConnectionPool
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okio.Buffer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.UnknownHostException
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
class CalDavOutageRepositoryTest {
    private val offline = AtomicBoolean(false)
    private val failedLookups = AtomicInteger()
    @Volatile private var failWhen: ((Request) -> Boolean)? = null
    private val http = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(0, 1, TimeUnit.NANOSECONDS))
        .retryOnConnectionFailure(false)
        .dns(object : Dns {
            override fun lookup(host: String): List<java.net.InetAddress> {
                if (offline.get()) {
                    failedLookups.incrementAndGet()
                    throw UnknownHostException("Unable to resolve host \"$host\": No address associated with hostname")
                }
                return Dns.SYSTEM.lookup(host)
            }
        })
        .addInterceptor { chain ->
            if (failWhen?.invoke(chain.request()) == true) offline.set(true)
            chain.proceed(chain.request())
        }.build()
    private val harness = RepositoryHarness(httpClient = http)
    private val server = harness.server
    private val repository = harness.repository

    @After fun close() = harness.close()

    private suspend fun connect() {
        // A hostname and no pooled connections ensure failures come from the real OkHttp DNS path.
        repository.saveManualAccount(server.serverUrl.replace("127.0.0.1", "localhost"), server.username, server.password)
        repository.syncNow()
    }

    private fun online() { failWhen = null; offline.set(false) }
    private fun Request.isMultiget(): Boolean = method == "REPORT" && Buffer().also { body?.writeTo(it) }.readUtf8().contains("calendar-multiget")

    @Test fun dnsFailureDuringUpgradeRefreshDoesNotFanOutOrMarkCachedEventsAsBroken() = runTest {
        repeat(60) { server.putRemote(server.eventsHref, "$it.ics", SampleIcs.event("event-$it", "Event $it")) }
        connect()
        server.setFeed("/healthy.ics", SampleIcs.event("healthy", "Before"))
        val healthy = repository.addReadOnlyCalendar(server.url("/healthy.ics").replace("localhost", "127.0.0.1"), "Healthy")
        server.setFeed("/healthy.ics", SampleIcs.event("healthy", "After"))
        val before = harness.database.resourceDao().forCollection(server.eventsHref).associateBy { it.href }
        val markers = harness.collection(server.eventsHref)!!
        harness.database.collectionDao().updateCapabilitiesJson(server.eventsHref,
            JSONObject(markers.capabilitiesJson!!).apply { remove("kgsReconciliationVersion") }.toString())
        failWhen = { it.isMultiget() }
        expectFailure<IllegalStateException> { repository.syncNow() }
        assertEquals("One DNS outage must stop the account, not fan out across every event", 1, failedLookups.get())
        assertEquals(before, harness.database.resourceDao().forCollection(server.eventsHref).associateBy { it.href })
        assertEquals(markers.syncToken, harness.collection(server.eventsHref)!!.syncToken)
        assertEquals(markers.ctag, harness.collection(server.eventsHref)!!.ctag)
        assertEquals(SyncState.Error, harness.account("primary")!!.syncState)
        assertEquals("After", harness.eventsIn("readonly-${healthy.id}").single().title)
        online()
        repository.syncNow()
        assertEquals(60, harness.eventsIn(server.eventsHref).size)
        assertNull(harness.account("primary")!!.syncError)
        assertTrue(harness.database.resourceDao().all().all { it.syncError == null })
    }

    @Test fun dnsFailureDuringFallbackGetRetainsTheOldBodyAndEtag() = runTest {
        val href = server.putRemote(server.eventsHref, "event.ics", SampleIcs.event("event", "Before"))
        connect()
        val before = harness.resource(href)
        server.putRemote(server.eventsHref, "event.ics", SampleIcs.event("event", "After"))
        server.respondNext("REPORT", server.eventsHref, bodyContains = "calendar-multiget") { MockResponse().setResponseCode(405) }
        failWhen = { it.method == "GET" && it.url.encodedPath == href }
        expectFailure<IllegalStateException> { repository.syncNow() }
        assertEquals(before, harness.resource(href))
        assertEquals(1, failedLookups.get())
        online()
        repository.syncNow()
        assertEquals("After", harness.event(href)!!.title)
        assertNull(harness.resource(href)!!.syncError)
    }

    @Test fun offlineUploadStopsOnceAndKeepsEveryPendingEditWithoutPerEventDnsWarnings() = runTest {
        repeat(10) { server.putRemote(server.eventsHref, "$it.ics", SampleIcs.event("event-$it", "Event $it")) }
        connect()
        repeat(10) { repository.updateEvent("event-$it", eventPayload("Edited $it", LocalDate.of(2026, 10, 5))) }
        val pending = harness.pendingMutations()
        failWhen = { it.method == "PUT" }
        expectFailure<UnknownHostException> { harness.components.syncOrchestrator.pushAllPendingChanges() }
        assertEquals(1, failedLookups.get())
        assertEquals(pending, harness.pendingMutations())
        assertTrue(harness.database.resourceDao().all().all { it.syncError == null })
        assertEquals(SyncState.Error, harness.account("primary")!!.syncState)
        online()
        harness.components.syncOrchestrator.pushAllPendingChanges()
        repository.syncNow()
        assertTrue(harness.pendingMutations().isEmpty())
        assertNull(harness.account("primary")!!.syncError)
        repeat(10) { assertTrue(server.stored("${server.eventsHref}$it.ics")!!.ics.contains("Edited $it")) }
    }

    @Test fun oldImportDnsWarningsClearAfterSuccessfulDownloadWithoutUploadingCachedEvents() = runTest {
        repeat(10) { server.putRemote(server.eventsHref, "$it.ics", SampleIcs.event("event-$it", "Event $it")) }
        connect()
        // A failed download keeps the previous collection cursor while the server has newer revisions.
        repeat(10) { server.putRemote(server.eventsHref, "$it.ics", SampleIcs.event("event-$it", "Event $it")) }
        harness.database.resourceDao().all().forEach {
            harness.database.resourceDao().setSyncError(it.href, "Import failed: Unable to resolve host \"localhost\": No address associated with hostname")
        }
        server.clearRequests()
        repository.syncNow()
        assertTrue(harness.database.resourceDao().all().all { it.syncError == null })
        assertEquals(10, harness.eventsIn(server.eventsHref).size)
        assertTrue(server.requests("PUT").isEmpty())
        assertTrue(server.requests("DELETE").isEmpty())
    }

    @Test fun dnsFailureDuringMissingEtagRepairStopsBeforeTryingEveryResource() = runTest {
        repeat(3) { server.putRemote(server.eventsHref, "$it.ics", SampleIcs.event("event-$it", "Event $it")) }
        connect()
        harness.database.resourceDao().all().forEach { harness.database.resourceDao().markSynced(it.href, null) }
        failWhen = { it.method == "GET" }
        expectFailure<IllegalStateException> { repository.syncNow() }
        assertEquals(1, failedLookups.get())
        assertTrue(harness.database.resourceDao().all().all { it.etag == null && it.syncError == null })
        online()
        repository.syncNow()
        assertTrue(harness.database.resourceDao().all().all { it.etag != null && it.syncError == null })
    }

    @Test fun unavailableBatchEndpointDoesNotTriggerAGetForEveryEvent() = runTest {
        repeat(3) { server.putRemote(server.eventsHref, "$it.ics", SampleIcs.event("event-$it", "Event $it")) }
        connect()
        val before = harness.database.resourceDao().forCollection(server.eventsHref).associateBy { it.href }
        repeat(3) { server.putRemote(server.eventsHref, "$it.ics", SampleIcs.event("event-$it", "Changed $it")) }
        server.respondNext("REPORT", server.eventsHref, bodyContains = "calendar-multiget") { MockResponse().setResponseCode(503) }
        server.clearRequests()
        expectFailure<IllegalStateException> { repository.syncNow() }
        assertTrue(server.requests("GET").isEmpty())
        assertEquals(before, harness.database.resourceDao().forCollection(server.eventsHref).associateBy { it.href })
        repository.syncNow()
        repeat(3) { assertEquals("Changed $it", harness.event("event-$it")!!.title) }
    }
}
