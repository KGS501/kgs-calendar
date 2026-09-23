package com.kgs.calendar.sync

import com.kgs.calendar.data.remote.HttpStatusException
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.MalformedURLException
import java.net.SocketTimeoutException
import java.net.URISyntaxException
import java.net.UnknownHostException

class SyncFailurePolicyTest {
    @Test
    fun authenticationFailuresFailImmediately() {
        assertEquals(SyncFailureOutcome.Fail, classify(wrapped(http(401))))
        assertEquals(SyncFailureOutcome.Fail, classify(wrapped(http(403))))
    }

    @Test
    fun otherClientErrorsFail() {
        assertEquals(SyncFailureOutcome.Fail, classify(wrapped(http(400))))
        assertEquals(SyncFailureOutcome.Fail, classify(wrapped(http(404))))
    }

    @Test
    fun serverErrorsAndThrottlingRetry() {
        assertEquals(SyncFailureOutcome.Retry, classify(wrapped(http(500))))
        assertEquals(SyncFailureOutcome.Retry, classify(wrapped(http(503))))
        assertEquals(SyncFailureOutcome.Retry, classify(wrapped(http(429))))
        assertEquals(SyncFailureOutcome.Retry, classify(wrapped(http(408))))
    }

    @Test
    fun networkFailuresRetry() {
        assertEquals(SyncFailureOutcome.Retry, classify(wrapped(IOException("reset"))))
        assertEquals(SyncFailureOutcome.Retry, classify(wrapped(SocketTimeoutException("timeout"))))
        assertEquals(SyncFailureOutcome.Retry, classify(wrapped(UnknownHostException("example.com"))))
    }

    @Test
    fun invalidUrlsFail() {
        assertEquals(SyncFailureOutcome.Fail, classify(wrapped(MalformedURLException("no protocol"))))
        assertEquals(SyncFailureOutcome.Fail, classify(wrapped(URISyntaxException("::", "bad"))))
        assertEquals(
            SyncFailureOutcome.Fail,
            classify(wrapped(IllegalArgumentException("Expected URL scheme 'http' or 'https'"))),
        )
    }

    @Test
    fun unrecognisedFailuresFail() {
        assertEquals(SyncFailureOutcome.Fail, classify(IllegalStateException("Android calendar permission is required.")))
    }

    @Test
    fun deeplyNestedCausesAreInspected() {
        val error = IllegalStateException("outer", RuntimeException("middle", SocketTimeoutException("timeout")))
        assertEquals(SyncFailureOutcome.Retry, classify(error))
    }

    @Test
    fun transientFailuresStopRetryingAtTheAttemptCap() {
        val error = wrapped(http(503))
        assertEquals(SyncFailureOutcome.Retry, classifySyncFailure(error, runAttemptCount = MAX_SYNC_RUN_ATTEMPTS - 1))
        assertEquals(SyncFailureOutcome.Fail, classifySyncFailure(error, runAttemptCount = MAX_SYNC_RUN_ATTEMPTS))
    }

    private fun classify(error: Throwable): SyncFailureOutcome = classifySyncFailure(error, runAttemptCount = 0)

    private fun http(code: Int) = HttpStatusException(code, "PROPFIND https://example.com/ failed: HTTP $code")

    // Mirrors SyncOrchestrator.syncNowLocked, which wraps source errors with a readable message.
    private fun wrapped(cause: Throwable) = IllegalStateException("Source \"user\": ${cause.message}", cause)
}
