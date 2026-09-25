package com.kgs.calendar.data.remote

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class NextcloudLoginFlowClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: NextcloudLoginFlowClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = NextcloudLoginFlowClient(OkHttpClient())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun startsLoginFlowAndParsesUrls() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "poll": {
                        "token": "abc",
                        "endpoint": "${server.url("/poll")}"
                      },
                      "login": "${server.url("/login")}"
                    }
                    """.trimIndent(),
                ),
        )

        val result = client.start(server.url("/").toString())

        assertEquals(server.url("/login").toString(), result.loginUrl)
        assertEquals(server.url("/poll").toString(), result.pollEndpoint)
        assertEquals("abc", result.token)
    }

    @Test
    fun pollOnceReturnsCredentialsWhenReady() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "server": "${server.url("/")}",
                      "loginName": "fromb",
                      "appPassword": "secret"
                    }
                    """.trimIndent(),
                ),
        )

        val result = client.pollOnce(server.url("/poll").toString(), "abc")

        assertEquals("fromb", result!!.loginName)
        assertEquals("secret", result.appPassword)
    }
}
