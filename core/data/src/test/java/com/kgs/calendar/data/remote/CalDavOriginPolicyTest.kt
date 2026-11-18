package com.kgs.calendar.data.remote

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.runBlocking

/** Two origins and synthetic credentials: an origin nobody approved must never see either. */
class CalDavOriginPolicyTest {
    private val entered = MockWebServer()
    private val other = MockWebServer()
    private val client = CalDavHttpClient(OkHttpClient.Builder().build())

    private val enteredUrl get() = entered.url("/").toString().trimEnd('/')
    private val otherUrl get() = other.url("/").toString().trimEnd('/')
    private val otherOrigin get() = canonicalOrigin(otherUrl)!!

    @Before
    fun setUp() {
        entered.start()
        other.start()
    }

    @After
    fun tearDown() {
        entered.shutdown()
        other.shutdown()
    }

    private fun serve(server: MockWebServer, answer: (RecordedRequest) -> MockResponse) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = answer(request)
        }
    }

    private fun multistatus(body: String) =
        MockResponse().setResponseCode(207).setHeader("Content-Type", "application/xml")
            .setBody("""<?xml version="1.0"?><d:multistatus xmlns:d="DAV:" xmlns:cal="urn:ietf:params:xml:ns:caldav">$body</d:multistatus>""")

    private fun principalAt(href: String) = multistatus(
        """<d:response><d:href>/</d:href><d:propstat><d:prop><d:current-user-principal><d:href>$href</d:href></d:current-user-principal></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>""",
    )

    private fun homeAt(href: String) = multistatus(
        """<d:response><d:href>/p/</d:href><d:propstat><d:prop><cal:calendar-home-set><d:href>$href</d:href></cal:calendar-home-set></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>""",
    )

    private fun assertOtherSawNothing() = assertEquals(0, other.requestCount)

    @Test
    fun metadataCannotPickANewRecipientForTheLogin() {
        serve(entered) { request -> if (request.path == "/") principalAt("$otherUrl/p/") else MockResponse().setResponseCode(404) }
        serve(other) { homeAt("/calendars/") }

        assertThrows(Exception::class.java) {
            runBlocking { client.discoverAccount(enteredUrl, "alice", "s3cret") }
        }

        assertOtherSawNothing()
    }

    @Test
    fun schemeRelativeHrefIsAnotherOriginToo() {
        serve(entered) { request ->
            if (request.path == "/") principalAt("//127.0.0.1:${other.port}/p/") else MockResponse().setResponseCode(404)
        }

        assertThrows(Exception::class.java) {
            runBlocking { client.discoverAccount(enteredUrl, "alice", "s3cret") }
        }

        assertOtherSawNothing()
    }

    @Test
    fun aRedirectDoesNotCarryCredentialsOrBodiesElsewhere() = runTest {
        serve(entered) { MockResponse().setResponseCode(307).setHeader("Location", "$otherUrl/a.ics") }

        assertThrows(CalDavOriginNotApprovedException::class.java) {
            runBlocking { client.putResource(enteredUrl, "/a.ics", "alice", "s3cret", "BEGIN:VCALENDAR", null) }
        }

        assertOtherSawNothing()
    }

    @Test
    fun aDifferentPortIsADifferentOrigin() {
        assertFalse(canonicalOrigin("https://dav.example:8443/") == canonicalOrigin("https://dav.example/"))
        assertEquals("https://dav.example:443", canonicalOrigin("https://DAV.example/remote.php"))
    }

    @Test
    fun anHttpsAccountCannotBeMovedToPlainHttp() {
        val refused = assertThrows(CalDavOriginNotApprovedException::class.java) {
            requireApprovedDavOrigin("http://dav.example/x", "https://dav.example", DavOrigins.None)
        }
        assertTrue(refused.isDowngrade)
    }

    @Test
    fun plainHttpMayUpgradeToHttpsOnTheSameHost() {
        requireApprovedDavOrigin("https://dav.example/x", "http://dav.example", DavOrigins.None)
    }

    @Test
    fun anApprovedOriginGetsTheLoginAndRootedHrefsStayThere() = runTest {
        var workDir = 0
        serve(other) { request ->
            when {
                request.path == "/home/" -> multistatus(
                    """<d:response><d:href>/home/work/</d:href><d:propstat><d:prop><d:resourcetype><d:collection/><cal:calendar/></d:resourcetype></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>""",
                )
                request.path == "/home/work/" -> {
                    workDir++
                    multistatus(
                        """<d:response><d:href>/home/work/1.ics</d:href><d:propstat><d:prop><d:getetag>"1"</d:getetag></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>""",
                    )
                }
                else -> MockResponse().setResponseCode(404)
            }
        }
        val origins = DavOrigins(approved = setOf(otherOrigin))
        val discovery = CalDavAccountDiscovery(
            serviceUrl = enteredUrl,
            enteredServerUrl = enteredUrl,
            principalUrl = null,
            calendarHomeUrl = "$otherUrl/home/",
        )

        val collection = client.discoverCollections(discovery, "alice", "s3cret", origins).single()
        assertEquals("/home/work/", collection.href)

        val listing = client.listResources(collection.href, enteredUrl, "alice", "s3cret", origins.copy(davOrigin = otherOrigin))

        assertEquals("/home/work/1.ics", listing.single().href)
        assertEquals(1, workDir)
        assertEquals(0, entered.requestCount)
        assertTrue(other.takeRequest().getHeader("Authorization")!!.startsWith("Basic "))
    }

    @Test
    fun aMemberOnAnotherOriginKeepsThatOrigin() = runTest {
        serve(entered) {
            multistatus(
                """<d:response><d:href>$otherUrl/elsewhere/1.ics</d:href><d:propstat><d:prop><d:getetag>"1"</d:getetag></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>""",
            )
        }

        val member = client.listResources("/cal/", enteredUrl, "alice", "s3cret").single()

        assertEquals("$otherUrl/elsewhere/1.ics", member.href)
        assertThrows(CalDavOriginNotApprovedException::class.java) {
            runBlocking { client.getResource(enteredUrl, member.href, "alice", "s3cret") }
        }
        assertOtherSawNothing()
    }

    @Test
    fun theSyncErrorNamesTheOriginToReview() {
        val error = CalDavOriginNotApprovedException("https://p42-caldav.example:443", isDowngrade = false)
        assertEquals("https://p42-caldav.example:443", "Source \"x\": ${error.message}".unapprovedDavOrigin())
        assertNull("Something else failed".unapprovedDavOrigin())
    }

    @Test
    fun truncatedSyncPageReportsItsToken() = runTest {
        serve(entered) {
            multistatus(
                """<d:response><d:href>/cal/1.ics</d:href><d:propstat><d:prop><d:getetag>"1"</d:getetag></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>""" +
                    """<d:response><d:href>/cal/</d:href><d:status>HTTP/1.1 507 Insufficient Storage</d:status></d:response><d:sync-token>page-2</d:sync-token>""",
            )
        }

        val page = client.syncCollection(enteredUrl, "/cal/", "page-1", "alice", "s3cret")!!

        assertTrue(page.truncated)
        assertEquals("page-2", page.syncToken)
        assertEquals(listOf("/cal/1.ics"), page.changedResources.map { it.href })
    }

    @Test
    fun aTruncatedPageWithoutProgressFallsBackToAListing() = runTest {
        serve(entered) {
            multistatus("""<d:response><d:href>/cal/</d:href><d:status>HTTP/1.1 507 Insufficient Storage</d:status></d:response><d:sync-token>page-1</d:sync-token>""")
        }

        assertNull(client.syncCollection(enteredUrl, "/cal/", "page-1", "alice", "s3cret"))
    }

    @Test
    fun aMemberWith507RemainsAFailure() {
        serve(entered) {
            multistatus("""<d:response><d:href>/cal/1.ics</d:href><d:status>HTTP/1.1 507 Insufficient Storage</d:status></d:response><d:sync-token>page-2</d:sync-token>""")
        }

        assertThrows(HttpStatusException::class.java) {
            runBlocking { client.syncCollection(enteredUrl, "/cal/", "page-1", "alice", "s3cret") }
        }
    }
}

class OriginApprovalNoticesTest {
    private fun account(id: String, syncError: String?) = com.kgs.calendar.data.local.entity.AccountEntity(
        id = id,
        serverUrl = "https://example.test",
        username = "alice",
        displayName = "Work",
        lastSyncAtMillis = null,
        syncError = syncError,
    )

    @Test
    fun onlyAccountsWaitingForApprovalAreAnnounced() {
        val refused = CalDavOriginNotApprovedException("https://dav.example.test:443", isDowngrade = false).message
        val downgraded = CalDavOriginNotApprovedException("http://dav.example.test:80", isDowngrade = true).message

        val notices = listOf(
            account("a", "Source \"Work\": $refused"),
            account("b", "Source \"Work\": $downgraded"),
            account("c", "Source \"Work\": offline"),
            account("d", null),
        ).originApprovalNotices()

        assertEquals(listOf(OriginApprovalNotice("a", "Work", "https://dav.example.test:443")), notices)
        assertEquals("a|https://dav.example.test:443", notices.single().key)
    }
}
