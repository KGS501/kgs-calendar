package com.kgs.calendar.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class SyncSchedulingTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var manager: WorkManager

    @Before fun setup() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        manager = WorkManager.getInstance(context)
    }

    @After fun close() {
        manager.cancelAllWork().result.get(5, TimeUnit.SECONDS)
        WorkManagerTestInitHelper.closeWorkDatabase()
    }

    @Test fun newLocalEditsAppendWithoutCancellingTheExistingUpload() {
        SyncWorker.enqueueImmediate(context)
        val first = work("kgs_immediate_sync").single()
        SyncWorker.enqueueImmediate(context)
        val requests = work("kgs_immediate_sync")
        assertEquals(2, requests.size)
        assertEquals(WorkInfo.State.ENQUEUED, requests.single { it.id == first.id }.state)
        assertEquals(WorkInfo.State.BLOCKED, requests.single { it.id != first.id }.state)
        assertTrue(requests.all { it.constraints.requiredNetworkType == NetworkType.CONNECTED })
        // A cancelled/failed old chain cannot poison a later edit's recovery request.
        manager.cancelUniqueWork("kgs_immediate_sync").result.get(5, TimeUnit.SECONDS)
        SyncWorker.enqueueImmediate(context)
        assertTrue(work("kgs_immediate_sync").any { it.state == WorkInfo.State.ENQUEUED })
    }

    @Test fun changingIntervalUpdatesTheSamePeriodicWork() {
        SyncWorker.schedulePeriodic(context, 15)
        val first = work("kgs_periodic_sync").single()
        SyncWorker.schedulePeriodic(context, 60)
        val updated = work("kgs_periodic_sync").single()
        assertEquals(first.id, updated.id)
        assertEquals(3_600_000L, updated.periodicityInfo!!.repeatIntervalMillis)
        assertEquals(NetworkType.CONNECTED, updated.constraints.requiredNetworkType)
    }

    @Test fun foregroundRequestsCoalesceAndEnqueueDoesNotPretendToBeSuccess() {
        SyncWorker.enqueueForegroundRefreshIfStale(context)
        SyncWorker.enqueueForegroundRefreshIfStale(context)
        assertEquals(1, work("kgs_foreground_sync").size)
        assertEquals(0L, context.getSharedPreferences("kgs_sync_worker", Context.MODE_PRIVATE)
            .getLong("last_sync_activity_at", 0))
        context.getSharedPreferences("kgs_sync_worker", Context.MODE_PRIVATE).edit()
            .putLong("last_sync_activity_at", System.currentTimeMillis()).commit()
        manager.cancelUniqueWork("kgs_foreground_sync").result.get(5, TimeUnit.SECONDS)
        SyncWorker.enqueueForegroundRefreshIfStale(context)
        assertTrue(work("kgs_foreground_sync").all { it.state == WorkInfo.State.CANCELLED })
    }

    private fun work(name: String) = manager.getWorkInfosForUniqueWork(name).get(5, TimeUnit.SECONDS)
}
