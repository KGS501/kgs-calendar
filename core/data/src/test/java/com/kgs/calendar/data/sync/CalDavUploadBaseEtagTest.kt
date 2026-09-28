package com.kgs.calendar.data.sync

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

}
