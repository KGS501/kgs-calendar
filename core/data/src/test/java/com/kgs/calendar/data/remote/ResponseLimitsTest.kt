package com.kgs.calendar.data.remote

import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.asResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ResponseLimitsTest {
    @Test
    fun smallBodiesAreReadAsText() {
        assertEquals("Grüße", "Grüße".toResponseBody("text/calendar; charset=utf-8".toMediaType()).textWithin(100))
    }

    @Test(expected = ResponseTooLargeException::class)
    fun declaredLengthOverTheLimitIsRefused() {
        "x".repeat(101).toResponseBody().textWithin(100)
    }

    @Test
    fun bodiesWithoutALengthAreCutOffAtTheLimit() {
        // A chunked body has no declared length; reading stops once it passes the limit.
        val body = Buffer().writeUtf8("x".repeat(50_000)).asResponseBody(null, -1)
        val failure = runCatching { body.textWithin(10_000) }.exceptionOrNull()

        assertEquals(ResponseTooLargeException::class, failure!!::class)
        assertFalse(failure.isTransientFailure())
    }
}
