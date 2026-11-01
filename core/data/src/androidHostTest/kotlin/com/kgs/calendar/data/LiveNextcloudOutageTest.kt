package com.kgs.calendar.data

import com.kgs.calendar.data.remote.CalDavHttpClient
import kotlinx.coroutines.runBlocking
import okhttp3.ConnectionPool
import okhttp3.Credentials
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.URI
import java.net.UnknownHostException
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Real Nextcloud DAV responses with a controlled resolver outage between listing and multiget. */
@RunWith(RobolectricTestRunner::class)
class LiveNextcloudOutageTest {
    @Test fun upgradeRefreshSurvivesDnsLossAndClearsLegacyWarningsOnRecovery() = runBlocking {
        val configured = System.getenv("KGS_TEST_NEXTCLOUD_URL")
        assumeTrue("Needs a disposable local Nextcloud", configured != null)
        val uri = URI(configured!!)
        require(uri.host in setOf("localhost", "127.0.0.1"))
        val base = URI(uri.scheme, null, "localhost", uri.port, uri.path, null, null).toString()
        val user = requireNotNull(System.getenv("KGS_TEST_NEXTCLOUD_USER"))
        val password = requireNotNull(System.getenv("KGS_TEST_NEXTCLOUD_PASSWORD"))
        val failBatch = AtomicBoolean(false)
        val offline = AtomicBoolean(false)
        val failures = AtomicInteger()
        val http = OkHttpClient.Builder()
            .connectionPool(ConnectionPool(0, 1, TimeUnit.NANOSECONDS))
            .retryOnConnectionFailure(false)
            .dns(object : Dns {
                override fun lookup(host: String): List<java.net.InetAddress> {
                    if (offline.get()) {
                        failures.incrementAndGet()
                        throw UnknownHostException("Unable to resolve host \"$host\": No address associated with hostname")
                    }
                    return Dns.SYSTEM.lookup(host)
                }
            })
            .addInterceptor { chain ->
                val request = chain.request()
                if (failBatch.get() && request.method == "REPORT" &&
                    Buffer().also { request.body?.writeTo(it) }.readUtf8().contains("calendar-multiget")) offline.set(true)
                chain.proceed(request)
            }.build()
        RepositoryHarness(httpClient = http).use { harness ->
            val repository = harness.repository
            val account = repository.saveManualAccount(base, user, password)
            val id = "kgs-outage-${UUID.randomUUID()}"
            repository.createCalDavCalendar(account.id, id, null, supportsEvents = true, supportsTasks = false)
            repository.syncNow()
            val collection = harness.database.collectionDao().forAccount(account.id).single { it.displayName == id }
            val href = "${collection.href.trimEnd('/')}/event.ics"
            fun put(title: String): Int = http.newCall(Request.Builder()
                .url(URI(base).resolve(href).toString()).header("Authorization", Credentials.basic(user, password))
                .put(SampleIcs.event(id, title).toRequestBody("text/calendar".toMediaType())).build()).execute().use { it.code }
            try {
                assertEquals(201, put("Before outage"))
                repository.syncNow()
                val before = harness.resource(href)!!
                val markers = harness.collection(collection.href)!!
                assertEquals(204, put("Changed on Nextcloud"))
                harness.database.collectionDao().updateCapabilitiesJson(collection.href,
                    JSONObject(markers.capabilitiesJson!!).apply { remove("kgsReconciliationVersion") }.toString())
                failBatch.set(true)
                expectFailure<IllegalStateException> { repository.syncNow() }
                assertEquals(1, failures.get())
                assertEquals(before, harness.resource(href))
                assertEquals(markers.syncToken, harness.collection(collection.href)!!.syncToken)
                assertEquals(markers.ctag, harness.collection(collection.href)!!.ctag)
                failBatch.set(false)
                offline.set(false)
                // V.1.4.0-3 persisted these warnings; successful recovery must also repair that state.
                harness.database.resourceDao().setSyncError(href,
                    "Import failed: Unable to resolve host \"localhost\": No address associated with hostname")
                repository.syncNow()
                assertEquals("Changed on Nextcloud", harness.event(href)!!.title)
                assertNull(harness.resource(href)!!.syncError)
                assertNull(harness.account(account.id)!!.syncError)
                assertTrue(harness.pendingMutations().isEmpty())
                val remote = CalDavHttpClient(http).getResourceWithEtag(base, href, user, password)
                assertEquals(remote.etag, harness.resource(href)!!.etag)
                assertTrue(remote.calendarData.contains("Changed on Nextcloud"))
            } finally {
                failBatch.set(false)
                offline.set(false)
                repository.deleteCalDavCalendar(collection.href)
            }
        }
    }
}
