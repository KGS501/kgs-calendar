package com.kgs.calendar.data.sync

import com.kgs.calendar.data.remote.CalDavConflictException
import com.kgs.calendar.data.remote.PutResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalDavUploadBaseEtagTest {
    @Test
    fun matchingLocalPayloadUsesNewestKnownResourceEtag() {
        assertEquals(
            "etag-after-first-upload",
            resolveCalDavUploadBaseEtag(
                queuedBaseEtag = "etag-before-first-upload",
                currentResourceEtag = "etag-after-first-upload",
                queuedPayload = "BEGIN:VCALENDAR\r\nSUMMARY:Latest edit\r\nEND:VCALENDAR\r\n",
                currentRawIcs = "BEGIN:VCALENDAR\nSUMMARY:Latest edit\nEND:VCALENDAR",
            ),
        )
    }

    @Test
    fun differentLocalPayloadPreservesConflictBase() {
        assertEquals(
            "etag-at-edit-time",
            resolveCalDavUploadBaseEtag(
                queuedBaseEtag = "etag-at-edit-time",
                currentResourceEtag = "etag-from-remote-pull",
                queuedPayload = "BEGIN:VCALENDAR\nSUMMARY:Local edit\nEND:VCALENDAR",
                currentRawIcs = "BEGIN:VCALENDAR\nSUMMARY:Remote edit\nEND:VCALENDAR",
            ),
        )
    }

    @Test
    fun missingQueuedBaseUsesCurrentResourceEtag() {
        assertEquals(
            "current-etag",
            resolveCalDavUploadBaseEtag(
                queuedBaseEtag = null,
                currentResourceEtag = "current-etag",
                queuedPayload = "local",
                currentRawIcs = "different",
            ),
        )
    }

    @Test
    fun pendingPutBlocksRemotePullFromReplacingOptimisticEdit() {
        assertFalse(
            shouldApplyRemoteCalDavState(
                resourceKey = "/calendar/event.ics",
                pendingPutResourceKeys = setOf("/calendar/event.ics"),
            ),
        )
    }

    @Test
    fun unrelatedPendingPutDoesNotBlockRemotePull() {
        assertTrue(
            shouldApplyRemoteCalDavState(
                resourceKey = "/calendar/event.ics",
                pendingPutResourceKeys = setOf("/calendar/other.ics"),
            ),
        )
    }

    @Test
    fun conflictRefreshesTheEtagAndRetriesTheOfflineEditOnce() = runTest {
        val attemptedEtags = mutableListOf<String?>()

        val result = putCalDavResourceWithConflictRetry(
            initialBaseEtag = "etag-before-outage",
            put = { etag ->
                attemptedEtags += etag
                if (attemptedEtags.size == 1) {
                    throw CalDavConflictException("PUT", "/calendar/event.ics")
                }
                PutResult("/calendar/event.ics", "etag-after-retry")
            },
            resolveCurrentEtag = { "etag-after-outage" },
        )

        assertEquals(listOf("etag-before-outage", "etag-after-outage"), attemptedEtags)
        assertEquals("etag-after-retry", result.result.etag)
        assertEquals("etag-after-outage", result.submittedBaseEtag)
    }

    @Test
    fun unresolvedConflictRemainsPendingInsteadOfRetryingForever() = runTest {
        var attempts = 0
        var thrown: Throwable? = null

        try {
            putCalDavResourceWithConflictRetry(
                initialBaseEtag = "unchanged-etag",
                put = {
                    attempts++
                    throw CalDavConflictException("PUT", "/calendar/event.ics")
                },
                resolveCurrentEtag = { "unchanged-etag" },
            )
        } catch (error: Throwable) {
            thrown = error
        }

        assertTrue(thrown is CalDavConflictException)
        assertEquals(1, attempts)
    }
}
