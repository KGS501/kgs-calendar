package com.kgs.calendar.data.remote

import com.kgs.calendar.data.local.entity.CollectionEntity
import com.kgs.calendar.domain.model.SourceType
import com.kgs.calendar.domain.source.publicCalendarUrl
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalDavPublicUrlTest {
    private fun collection(type: SourceType, externalId: String? = null, capabilities: String? = null) = CollectionEntity(
        href = "/c/",
        accountId = "a",
        displayName = "Calendar",
        color = 0,
        supportsEvents = true,
        supportsTasks = false,
        syncToken = null,
        ctag = null,
        sourceType = type,
        externalId = externalId,
        capabilitiesJson = capabilities,
    )

    private suspend fun discovered(publishXml: String): CalDavCollectionCapabilities {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setResponseCode(207).setBody(
                """<?xml version="1.0"?><d:multistatus xmlns:d="DAV:" xmlns:cs="http://calendarserver.org/ns/" xmlns:cal="urn:ietf:params:xml:ns:caldav">
                <d:response><d:href>/home/work/</d:href>
                <d:propstat><d:prop><d:displayname>Work</d:displayname><d:resourcetype><d:collection/><cal:calendar/></d:resourcetype>$publishXml</d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat>
                </d:response></d:multistatus>""",
            ),
        )
        server.start()
        try {
            val url = server.url("/").toString().trimEnd('/')
            val discovery = CalDavAccountDiscovery(serviceUrl = url, enteredServerUrl = url, principalUrl = null, calendarHomeUrl = "$url/home/")
            val found = CalDavHttpClient(OkHttpClient()).discoverCollections(discovery, "alice", "s3cret").single()
            assertTrue(server.takeRequest().body.readUtf8().contains("publish-url"))
            return found.capabilities
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun aNextcloudCalendarIsSharedAtItsPublicPage() = runTest {
        val capabilities = discovered("<cs:publish-url><d:href>https://cloud.example/remote.php/dav/public-calendars/RP4bpRHmb3PdY8dH</d:href></cs:publish-url>")

        assertEquals("https://cloud.example/apps/calendar/p/RP4bpRHmb3PdY8dH", capabilities.publicUrl)
        assertEquals(capabilities.publicUrl, collection(SourceType.CalDav, capabilities = capabilities.toJson()).publicCalendarUrl())
    }

    @Test
    fun aNextcloudInASubfolderKeepsItsPath() = runTest {
        val capabilities = discovered("<cs:publish-url><d:href>https://example.test/nextcloud/remote.php/dav/public-calendars/TOKEN</d:href></cs:publish-url>")

        assertEquals("https://example.test/nextcloud/apps/calendar/p/TOKEN", capabilities.publicUrl)
    }

    @Test
    fun otherServersKeepTheAddressTheyPublish() = runTest {
        val capabilities = discovered("<cs:publish-url><d:href>https://dav.example.test/published/work.ics</d:href></cs:publish-url>")

        assertEquals("https://dav.example.test/published/work.ics", capabilities.publicUrl)
    }

    @Test
    fun anUnpublishedCalendarHasNoLink() = runTest {
        val capabilities = discovered("")

        assertNull(capabilities.publicUrl)
        assertNull(collection(SourceType.CalDav, capabilities = capabilities.toJson()).publicCalendarUrl())
    }

    @Test
    fun aFeedIsSharedAtItsOwnAddress() {
        assertEquals("https://example.test/feed.ics", collection(SourceType.ReadOnlyUrl, "https://example.test/feed.ics").publicCalendarUrl())
    }

    @Test
    fun otherSourcesAndOtherSchemesHaveNoLink() {
        assertNull(collection(SourceType.ReadOnlyUrl, "javascript:alert(1)").publicCalendarUrl())
        assertNull(collection(SourceType.CalDav).publicCalendarUrl())
        assertNull(collection(SourceType.CalDav, capabilities = """{"publicUrl":"file:///etc/passwd"}""").publicCalendarUrl())
        assertNull(collection(SourceType.Local, "https://example.test/x").publicCalendarUrl())
    }
}
