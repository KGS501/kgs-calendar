package com.kgs.calendar.data

import kotlinx.coroutines.runBlocking
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.URI
import java.util.UUID

/** Opt-in check against a disposable local server; no production credentials or calendars. */
@RunWith(RobolectricTestRunner::class)
class LiveNextcloudCalendarDeletionTest {
    @Test fun aCalendarDeletedInTheAppStaysDeletedAfterTheNextSync() = runBlocking {
        val base = System.getenv("KGS_TEST_NEXTCLOUD_URL")
        assumeTrue("Needs a disposable local Nextcloud", base != null)
        require(URI(base!!).host in setOf("localhost", "127.0.0.1"))
        val user = requireNotNull(System.getenv("KGS_TEST_NEXTCLOUD_USER"))
        val password = requireNotNull(System.getenv("KGS_TEST_NEXTCLOUD_PASSWORD"))
        RepositoryHarness().use { harness ->
            val repository = harness.repository
            val account = repository.saveManualAccount(base, user, password)
            val name = "kgs-delete-test-${UUID.randomUUID()}"
            repository.createCalDavCalendar(account.id, name, null, supportsEvents = true, supportsTasks = true)
            repository.syncNow()
            val collection = harness.database.collectionDao().forAccount(account.id).first { it.displayName == name }

            repository.deleteCalDavCalendar(collection.href)
            repository.syncNow()

            assertNull(
                "The deleted calendar came back after a sync",
                harness.database.collectionDao().forAccount(account.id).firstOrNull { it.displayName == name },
            )
            val home = harness.database.accountDao().get(account.id)!!.calendarHomeUrl!!
            val listing = harness.httpClient.newCall(
                Request.Builder()
                    .url(URI(base).resolve(home).toString())
                    .header("Authorization", Credentials.basic(user, password))
                    .header("Depth", "1")
                    .method("PROPFIND", PROPFIND.toRequestBody("application/xml".toMediaType()))
                    .build(),
            ).execute().use { it.body!!.string() }
            // Nextcloud keeps the calendar in its trash bin and lists it as a deleted calendar.
            val slug = collection.href.trimEnd('/').substringAfterLast('/')
            val entry = listing.split("response>").firstOrNull { slug in it }
            assertTrue("The server did not delete the calendar", entry == null || "deleted-calendar" in entry)
        }
    }

    @Test fun theSchedulingInboxAndOutboxAreNotListedAsCalendars() = runBlocking {
        val base = System.getenv("KGS_TEST_NEXTCLOUD_URL")
        assumeTrue("Needs a disposable local Nextcloud", base != null)
        require(URI(base!!).host in setOf("localhost", "127.0.0.1"))
        val user = requireNotNull(System.getenv("KGS_TEST_NEXTCLOUD_USER"))
        val password = requireNotNull(System.getenv("KGS_TEST_NEXTCLOUD_PASSWORD"))
        RepositoryHarness().use { harness ->
            val repository = harness.repository
            val account = repository.saveManualAccount(base, user, password)
            repository.syncNow()

            val hrefs = harness.database.collectionDao().forAccount(account.id).map { it.href.trimEnd('/').substringAfterLast('/') }
            assertTrue("No calendar was found: $hrefs", hrefs.isNotEmpty())
            assertTrue("The scheduling mailboxes were listed: $hrefs", hrefs.none { it == "inbox" || it == "outbox" })
        }
    }

    private companion object {
        const val PROPFIND = """<?xml version="1.0"?><d:propfind xmlns:d="DAV:"><d:prop><d:displayname/><d:resourcetype/></d:prop></d:propfind>"""
    }
}
