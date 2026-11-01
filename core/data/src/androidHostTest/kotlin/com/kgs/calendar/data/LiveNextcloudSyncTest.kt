package com.kgs.calendar.data

import com.kgs.calendar.data.remote.CalDavHttpClient
import kotlinx.coroutines.runBlocking
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.URI
import java.util.UUID

/** Opt-in check against a disposable local server; no production credentials or calendars. */
@RunWith(RobolectricTestRunner::class)
class LiveNextcloudSyncTest {
    @Test fun taskCompletionAndExternalAllDayChangeConvergeInBothDirections() = runBlocking {
        val base = System.getenv("KGS_TEST_NEXTCLOUD_URL")
        assumeTrue("Needs a disposable local Nextcloud", base != null)
        require(URI(base!!).host in setOf("localhost", "127.0.0.1"))
        val user = requireNotNull(System.getenv("KGS_TEST_NEXTCLOUD_USER"))
        val password = requireNotNull(System.getenv("KGS_TEST_NEXTCLOUD_PASSWORD"))
        RepositoryHarness().use { harness ->
            val repository = harness.repository
            val account = repository.saveManualAccount(base, user, password)
            val uid = "kgs-sync-test-${UUID.randomUUID()}"
            repository.createCalDavCalendar(account.id, uid, null, supportsEvents = false, supportsTasks = true)
            repository.syncNow()
            val collection = harness.database.collectionDao().forAccount(account.id).first { it.displayName == uid }
            val href = "${collection.href.trimEnd('/')}/$uid.ics"
            val client = CalDavHttpClient(harness.httpClient)
            fun put(raw: String): Int = harness.httpClient.newCall(Request.Builder()
                .url(URI(base).resolve(href).toString()).header("Authorization", Credentials.basic(user, password))
                .put(raw.toRequestBody("text/calendar".toMediaType())).build()).execute().use { it.code }
            try {
                assertEquals(201, put(SampleIcs.task(uid, "Live sync task")))
                repository.syncNow()
                assertTrue(harness.task(href)!!.dueHasTime)
                repository.setTaskStatus(href, "COMPLETED")
                harness.components.syncOrchestrator.pushAllPendingChanges()
                val remote = client.getResourceWithEtag(base, href, user, password)
                assertTrue(remote.calendarData.contains("STATUS:COMPLETED"))
                assertTrue(harness.pendingMutations().isEmpty())
                assertEquals(204, put(SampleIcs.task(uid, "Edited in Nextcloud")
                    .replace("DUE:20261006T150000Z", "DUE;VALUE=DATE:20261006")))
                repository.syncNow()
                assertEquals("Edited in Nextcloud", harness.task(href)!!.title)
                assertFalse(harness.task(href)!!.isCompleted)
                assertFalse(harness.task(href)!!.dueHasTime)
            } finally {
                client.deleteResource(base, href, user, password, null)
                repository.refreshTrash()
                harness.database.trashDao().all().filter { it.uid == uid }.forEach { repository.deleteTrashedItemPermanently(it.id) }
                repository.deleteCalDavCalendar(collection.href)
            }
        }
    }
}
