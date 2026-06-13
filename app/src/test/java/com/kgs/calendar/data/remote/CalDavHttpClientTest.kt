package com.kgs.calendar.data.remote

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CalDavHttpClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: CalDavHttpClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = CalDavHttpClient(OkHttpClient())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun discoversPrincipalHomeSchedulingAndCollectionCapabilities() = runTest {
        server.enqueue(xmlResponse(discoveryResponse("/principals/users/alice/")))
        server.enqueue(xmlResponse(principalResponse("/calendars/alice/")))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("DAV", "1, 3, calendar-access, calendar-schedule"),
        )
        server.enqueue(xmlResponse(collectionResponse()))

        val discovery = client.discoverAccount(server.url("/").toString(), "alice", "secret")
        val collections = client.discoverCollections(discovery, "alice", "secret")

        assertEquals(server.url("/principals/users/alice/").toString(), discovery.principalUrl)
        assertEquals(server.url("/calendars/alice/").toString(), discovery.calendarHomeUrl)
        assertEquals(server.url("/calendars/alice/inbox/").toString(), discovery.scheduleInboxUrl)
        assertTrue(discovery.supportsScheduling)
        assertEquals(1, collections.size)
        val calendar = collections.single()
        assertEquals("/calendars/alice/work/", calendar.href)
        assertTrue(calendar.supportsEvents)
        assertTrue(calendar.supportsTasks)
        assertFalse(calendar.readOnly)
        assertTrue(calendar.capabilities.canWriteContent)
        assertTrue(calendar.capabilities.canCreateResources)
        assertTrue(calendar.capabilities.canDeleteResources)
        assertTrue(calendar.capabilities.supportsSyncCollection)
    }

    @Test
    fun incrementalSyncReturnsChangedDeletedAndNewToken() = runTest {
        server.enqueue(
            xmlResponse(
                """
                <d:multistatus xmlns:d="DAV:">
                  <d:sync-token>token-2</d:sync-token>
                  <d:response>
                    <d:href>/cal/work/changed.ics</d:href>
                    <d:propstat><d:prop><d:getetag>"two"</d:getetag></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat>
                  </d:response>
                  <d:response>
                    <d:href>/cal/work/deleted.ics</d:href>
                    <d:status>HTTP/1.1 404 Not Found</d:status>
                  </d:response>
                </d:multistatus>
                """.trimIndent(),
            ),
        )

        val result = client.syncCollection(
            serverUrl = server.url("/").toString(),
            collectionHref = "/cal/work/",
            previousSyncToken = "token-1",
            username = "alice",
            appPassword = "secret",
        )!!

        assertEquals("token-2", result.syncToken)
        assertEquals(listOf("/cal/work/changed.ics"), result.changedResources.map { it.href })
        assertEquals(setOf("/cal/work/deleted.ics"), result.deletedHrefs)
        val request = server.takeRequest()
        assertEquals("REPORT", request.method)
        assertTrue(request.body.readUtf8().contains("<d:sync-token>token-1</d:sync-token>"))
    }

    @Test
    fun discoversScheduleInboxAsReadOnlyTaskCollection() = runTest {
        server.enqueue(xmlResponse(discoveryResponse("/principals/users/alice/")))
        server.enqueue(xmlResponse(principalResponse("/calendars/alice/")))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("DAV", "1, 3, calendar-access, calendar-schedule"),
        )
        server.enqueue(xmlResponse(scheduleInboxCollectionResponse()))

        val discovery = client.discoverAccount(server.url("/").toString(), "alice", "secret")
        val collections = client.discoverCollections(discovery, "alice", "secret")

        val inbox = collections.single { it.href == "/calendars/alice/inbox/" }
        assertTrue(inbox.supportsTasks)
        assertFalse(inbox.supportsEvents)
        assertTrue(inbox.readOnly)
        assertTrue(inbox.capabilities.isScheduleInbox)
    }

    @Test
    fun calendarQueryReturnsVtodoResourceData() = runTest {
        server.enqueue(
            xmlResponse(
                """
                <d:multistatus xmlns:d="DAV:" xmlns:cal="urn:ietf:params:xml:ns:caldav">
                  <d:response>
                    <d:href>/cal/tasks/task-1.ics</d:href>
                    <d:propstat><d:prop>
                      <d:getetag>"task-etag"</d:getetag>
                      <cal:calendar-data><![CDATA[BEGIN:VCALENDAR
                VERSION:2.0
                BEGIN:VTODO
                UID:task-1
                SUMMARY:Queried task
                END:VTODO
                END:VCALENDAR]]></cal:calendar-data>
                    </d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat>
                  </d:response>
                </d:multistatus>
                """.trimIndent(),
            ),
        )

        val resources = client.queryResources(
            serverUrl = server.url("/").toString(),
            collectionHref = "/cal/tasks/",
            componentName = "VTODO",
            username = "alice",
            appPassword = "secret",
        )

        assertEquals(1, resources.size)
        assertEquals("/cal/tasks/task-1.ics", resources.single().href)
        assertEquals("\"task-etag\"", resources.single().etag)
        assertTrue(resources.single().calendarData.contains("BEGIN:VTODO"))
        val request = server.takeRequest()
        assertEquals("REPORT", request.method)
        assertEquals("1", request.getHeader("Depth"))
        val body = request.body.readUtf8()
        assertTrue(body.contains("calendar-query"))
        assertTrue(body.contains("""<cal:comp-filter name="VTODO" />"""))
    }

    private fun xmlResponse(body: String) = MockResponse()
        .setResponseCode(207)
        .addHeader("Content-Type", "application/xml")
        .setBody(body)

    private fun discoveryResponse(principal: String) = """
        <d:multistatus xmlns:d="DAV:" xmlns:cal="urn:ietf:params:xml:ns:caldav">
          <d:response>
            <d:href>/</d:href>
            <d:propstat><d:prop>
              <d:current-user-principal><d:href>$principal</d:href></d:current-user-principal>
            </d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat>
          </d:response>
        </d:multistatus>
    """.trimIndent()

    private fun principalResponse(home: String) = """
        <d:multistatus xmlns:d="DAV:" xmlns:cal="urn:ietf:params:xml:ns:caldav">
          <d:response>
            <d:href>/principals/users/alice/</d:href>
            <d:propstat><d:prop>
              <cal:calendar-home-set><d:href>$home</d:href></cal:calendar-home-set>
              <cal:calendar-user-address-set><d:href>mailto:alice@example.test</d:href></cal:calendar-user-address-set>
              <cal:schedule-inbox-URL><d:href>/calendars/alice/inbox/</d:href></cal:schedule-inbox-URL>
              <cal:schedule-outbox-URL><d:href>/calendars/alice/outbox/</d:href></cal:schedule-outbox-URL>
            </d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat>
          </d:response>
        </d:multistatus>
    """.trimIndent()

    private fun collectionResponse() = """
        <d:multistatus xmlns:d="DAV:" xmlns:cal="urn:ietf:params:xml:ns:caldav">
          <d:response>
            <d:href>/calendars/alice/</d:href>
            <d:propstat><d:prop><d:resourcetype><d:collection /></d:resourcetype></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat>
          </d:response>
          <d:response>
            <d:href>/calendars/alice/work/</d:href>
            <d:propstat><d:prop>
              <d:displayname>Work</d:displayname>
              <d:resourcetype><d:collection /><cal:calendar /></d:resourcetype>
              <d:current-user-privilege-set>
                <d:privilege><d:read /></d:privilege>
                <d:privilege><d:write-content /></d:privilege>
                <d:privilege><d:write-properties /></d:privilege>
                <d:privilege><d:bind /></d:privilege>
                <d:privilege><d:unbind /></d:privilege>
              </d:current-user-privilege-set>
              <d:supported-report-set>
                <d:supported-report><d:report><d:sync-collection /></d:report></d:supported-report>
                <d:supported-report><d:report><cal:calendar-multiget /></d:report></d:supported-report>
              </d:supported-report-set>
              <cal:supported-calendar-component-set><cal:comp name="VEVENT" /><cal:comp name="VTODO" /></cal:supported-calendar-component-set>
              <d:sync-token>token-1</d:sync-token>
            </d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat>
            </d:response>
        </d:multistatus>
    """.trimIndent()

    private fun scheduleInboxCollectionResponse() = """
        <d:multistatus xmlns:d="DAV:" xmlns:cal="urn:ietf:params:xml:ns:caldav">
          <d:response>
            <d:href>/calendars/alice/</d:href>
            <d:propstat><d:prop><d:resourcetype><d:collection /></d:resourcetype></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat>
          </d:response>
          <d:response>
            <d:href>/calendars/alice/inbox/</d:href>
            <d:propstat><d:prop>
              <d:displayname>Schedule Inbox</d:displayname>
              <d:resourcetype><d:collection /><cal:schedule-inbox /></d:resourcetype>
              <d:current-user-privilege-set>
                <d:privilege><d:read /></d:privilege>
              </d:current-user-privilege-set>
            </d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat>
          </d:response>
        </d:multistatus>
    """.trimIndent()
}
