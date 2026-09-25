package com.kgs.calendar.data.remote

import com.kgs.calendar.data.sync.isRetryableFetchFailure
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.MalformedURLException
import java.net.SocketTimeoutException

class TransientFailuresTest {
    @Test
    fun transientStatusesAreTimeoutsTooEarlyThrottlingAndServerErrors() {
        listOf(408, 425, 429, 500, 503, 599).forEach { assertTrue("$it", http(it).isTransientFailure()) }
        listOf(400, 401, 403, 404, 410, 412).forEach { assertFalse("$it", http(it).isTransientFailure()) }
    }

    @Test
    fun wrappedNetworkFailuresAreTransient() {
        assertTrue(IllegalStateException("outer", RuntimeException("middle", SocketTimeoutException("t"))).isTransientFailure())
        assertTrue(IllegalStateException("outer", http(408)).isTransientFailure())
    }

    @Test
    fun invalidUrlsAndUnrecognisedFailuresAreNotTransient() {
        assertFalse(MalformedURLException("no protocol").isTransientFailure())
        assertFalse(IllegalStateException("outer", IllegalArgumentException("bad scheme")).isTransientFailure())
        assertFalse(IllegalStateException("boom").isTransientFailure())
    }

    @Test
    fun cyclicCauseChainsEnd() {
        val first = RuntimeException("first")
        val second = RuntimeException("second")
        first.initCause(second)
        second.initCause(first)
        assertFalse(first.isTransientFailure())
    }

    @Test
    fun resourceFetchAlsoRetriesDeniedAccessButAccountRulesDoNot() {
        listOf(401, 403, 408, 425, 429, 502).forEach { assertTrue("$it", http(it).isRetryableFetchFailure()) }
        listOf(400, 404, 410).forEach { assertFalse("$it", http(it).isRetryableFetchFailure()) }
        assertFalse(http(401).isTransientFailure())
        assertFalse(http(403).isTransientFailure())
    }

    @Test
    fun wrappedIoExceptionIsARetryableFetchFailure() {
        assertTrue(IllegalStateException("GET failed", IOException("unexpected end of stream")).isRetryableFetchFailure())
        assertFalse(IllegalStateException("GET failed", MalformedURLException("bad href")).isRetryableFetchFailure())
    }

    private fun http(code: Int) = HttpStatusException(code, "GET https://example.com/a.ics failed: HTTP $code")
}
