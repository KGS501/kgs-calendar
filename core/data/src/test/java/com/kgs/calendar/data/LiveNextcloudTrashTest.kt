package com.kgs.calendar.data

import com.kgs.calendar.data.trash.TrashRestoreResult
import com.kgs.calendar.domain.trash.TrashOrigin
import kotlinx.coroutines.runBlocking
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.URI
import java.util.UUID

/** Opt-in integration check against a disposable local Nextcloud, never a user's server. */
@RunWith(RobolectricTestRunner::class)
class LiveNextcloudTrashTest {
    @Test
    fun externalAndAppDeletesRestoreAndPurgeOnTheActualServer() = runBlocking {
        val base = System.getenv("KGS_TEST_NEXTCLOUD_URL")
        assumeTrue("Needs a disposable local Nextcloud", base != null)
        require(URI(base!!).host in setOf("localhost", "127.0.0.1"))
        val user = requireNotNull(System.getenv("KGS_TEST_NEXTCLOUD_USER"))
        val password = requireNotNull(System.getenv("KGS_TEST_NEXTCLOUD_PASSWORD"))
        RepositoryHarness().use { harness ->
            val repository = harness.repository
            val account = repository.saveManualAccount(base, user, password)
            repository.syncNow()
            val calendar = harness.database.collectionDao().forAccount(account.id)
                .first { it.href.trimEnd('/').endsWith("/personal") }
            val uid = "kgs-trash-test-${UUID.randomUUID()}"
            val href = "${calendar.href.trimEnd('/')}/$uid.ics"
            fun request(method: String, resource: String, body: String? = null): Int {
                val url = URI(base).resolve(resource).toString()
                return harness.httpClient.newCall(Request.Builder().url(url)
                    .header("Authorization", Credentials.basic(user, password))
                    .method(method, body?.toRequestBody("text/calendar".toMediaType())).build())
                    .execute().use { it.code }
            }
            suspend fun listing() = harness.database.trashDao().all().filter { it.uid == uid }
            try {
                assertEquals(201, request("PUT", href, SampleIcs.event(uid, "Live Nextcloud regression")))
                repository.syncNow()
                assertNotNull(harness.event(href))
                // Simulate deletion through Nextcloud's web UI / another client.
                assertEquals(204, request("DELETE", href))
                repository.syncNow()
                val remote = listing().single()
                assertEquals(TrashOrigin.ServerTrashBin, remote.origin)
                assertTrue(repository.restoreTrashedItem(remote.id) is TrashRestoreResult.Restored)
                assertEquals(200, request("GET", href))
                assertNotNull(harness.event(href))
                assertTrue(repository.refreshTrash())
                assertTrue(listing().isEmpty())
                // Now delete in KGS: its local snapshot must become the server entry.
                repository.deleteEvent(uid)
                repository.syncNow()
                val appDeleted = listing().single()
                assertEquals(TrashOrigin.ServerTrashBin, appDeleted.origin)
                assertEquals(200, request("GET", appDeleted.serverHref!!))
                repository.deleteTrashedItemPermanently(appDeleted.id)
                assertEquals(404, request("GET", appDeleted.serverHref!!))
                assertTrue(repository.refreshTrash())
                assertTrue(listing().isEmpty())
            } finally {
                request("DELETE", href)
                repository.refreshTrash()
                listing().forEach { it.serverHref?.let { serverHref -> request("DELETE", serverHref) } }
            }
        }
    }
}
