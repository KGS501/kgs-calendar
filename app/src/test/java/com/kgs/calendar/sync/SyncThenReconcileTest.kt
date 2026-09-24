package com.kgs.calendar.sync

import com.kgs.calendar.data.remote.HttpStatusException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class SyncThenReconcileTest {
    private val calls = mutableListOf<String>()

    private suspend fun run(
        syncError: Throwable? = null,
        reminderError: Throwable? = null,
        widgetError: Throwable? = null,
    ): Throwable? = syncThenReconcile(
        sync = {
            calls += "sync"
            syncError?.let { throw it }
        },
        rescheduleReminders = {
            calls += "reminders"
            reminderError?.let { throw it }
        },
        refreshWidgets = {
            calls += "widgets"
            widgetError?.let { throw it }
        },
    )

    @Test
    fun successfulSyncReschedulesRemindersAndRefreshesWidgets() = runTest {
        assertNull(run())

        assertEquals(listOf("sync", "reminders", "widgets"), calls)
    }

    @Test
    fun failedSyncStillReschedulesRemindersAndRefreshesWidgets() = runTest {
        val uploadRejected = IllegalStateException("Upload failed: HTTP 403", HttpStatusException(403, "PUT failed"))

        val error = run(syncError = uploadRejected)

        assertSame(uploadRejected, error)
        assertEquals(listOf("sync", "reminders", "widgets"), calls)
        assertEquals(SyncFailureOutcome.Fail, classifySyncFailure(error!!, runAttemptCount = 0))
    }

    @Test
    fun reminderFailureNeitherBlocksWidgetsNorReplacesTheSyncOutcome() = runTest {
        assertNull(run(reminderError = IllegalStateException("alarm manager")))
        assertEquals(listOf("sync", "reminders", "widgets"), calls)

        val serverError = IllegalStateException("sync", HttpStatusException(503, "PROPFIND failed"))
        val error = run(syncError = serverError, reminderError = IllegalStateException("alarm manager"))
        assertSame(serverError, error)
        assertEquals(SyncFailureOutcome.Retry, classifySyncFailure(error!!, runAttemptCount = 0))
    }

    @Test
    fun widgetFailureDoesNotReplaceTheSyncOutcome() = runTest {
        assertNull(run(widgetError = IllegalStateException("widget render")))

        val serverError = IllegalStateException("sync", HttpStatusException(503, "PROPFIND failed"))
        assertSame(serverError, run(syncError = serverError, widgetError = IllegalStateException("widget render")))
    }

    @Test(expected = CancellationException::class)
    fun cancelledSyncPropagates() = runTest {
        run(syncError = CancellationException("worker stopped"))
    }

    @Test
    fun cancelledSyncSkipsTheFollowUps() = runTest {
        try {
            run(syncError = CancellationException("worker stopped"))
        } catch (expected: CancellationException) {
        }

        assertEquals(listOf("sync"), calls)
    }

    @Test(expected = CancellationException::class)
    fun cancellationDuringReminderReschedulingPropagates() = runTest {
        run(reminderError = CancellationException("worker stopped"))
    }
}
