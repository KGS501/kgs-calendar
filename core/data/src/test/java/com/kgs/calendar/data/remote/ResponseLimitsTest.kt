package com.kgs.calendar.data.remote

import com.kgs.calendar.platform.HttpRequestBuilder
import com.kgs.calendar.platform.OkHttpTransport
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

/** The response size limit, now enforced by the OkHttp transport while it reads the body. */
class ResponseLimitsTest {
    private lateinit var server: MockWebServer
    private val transport = OkHttpTransport(OkHttpClient())

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun fetch(limit: Long) = runBlocking {
        transport.execute(HttpRequestBuilder().url(server.url("/").toString()).maxResponseBytes(limit).build())
    }

    @Test
    fun smallBodiesAreReadAsText() {
        server.enqueue(MockResponse().setHeader("Content-Type", "text/calendar; charset=utf-8").setBody("Grüße"))
        assertEquals("Grüße", fetch(100).bodyText())
    }

    @Test(expected = ResponseTooLargeException::class)
    fun declaredLengthOverTheLimitIsRefused() {
        server.enqueue(MockResponse().setBody("x".repeat(101)))
        fetch(100)
    }

    @Test
    fun bodiesWithoutALengthAreCutOffAtTheLimit() {
        // A chunked body has no declared length; reading stops once it passes the limit.
        server.enqueue(MockResponse().setChunkedBody(Buffer().writeUtf8("x".repeat(50_000)), 1024))
        val failure = runCatching { fetch(10_000) }.exceptionOrNull()

        assertEquals(ResponseTooLargeException::class, failure!!::class)
        assertFalse(failure.isTransientFailure())
    }
}
