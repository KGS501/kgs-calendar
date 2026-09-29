package com.kgs.calendar.sync

import android.content.Context
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.Constraints
import androidx.work.OutOfQuotaPolicy
import androidx.work.workDataOf
import com.kgs.calendar.KgsCalendarApplication
import com.kgs.calendar.reminder.ReminderScheduler
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val graph = KgsCalendarApplication.graph(applicationContext)
        if (inputData.getBoolean(KEY_UPLOAD_ONLY, false) && graph.database.pendingMutationDao().all().isEmpty()) {
            return Result.success()
        }
        val syncError = syncThenReconcile(
            sync = {
                val includeDisabledProviderCalendars = graph.settingsStore.showDisabledAndroidProviderCalendars.first()
                if (inputData.getBoolean(KEY_UPLOAD_ONLY, false)) {
                    graph.syncOrchestrator.pushAllPendingChanges()
                } else {
                    graph.repository.syncNow(includeDisabledProviderCalendars = includeDisabledProviderCalendars)
                }
            },
            rescheduleReminders = { ReminderScheduler.reschedule(applicationContext) },
            refreshWidgets = { KgsCalendarApplication.graph(applicationContext).widgets.scheduler.updateAllAndAwait() },
        )
        if (syncError == null) {
            if (!inputData.getBoolean(KEY_UPLOAD_ONLY, false)) markRecentSyncActivity(applicationContext)
            return Result.success()
        }
        return when (classifySyncFailure(syncError, runAttemptCount)) {
            SyncFailureOutcome.Retry -> Result.retry()
            SyncFailureOutcome.Fail -> Result.failure()
        }
    }

    companion object {
        private const val UNIQUE_PERIODIC_SYNC = "kgs_periodic_sync"
        private const val UNIQUE_IMMEDIATE_SYNC = "kgs_immediate_sync"
        private const val UNIQUE_FOREGROUND_SYNC = "kgs_foreground_sync"
        private const val SYNC_PREFS = "kgs_sync_worker"
        private const val KEY_LAST_SYNC_ACTIVITY_AT = "last_sync_activity_at"
        internal const val FOREGROUND_SYNC_THROTTLE_MILLIS = 60L * 1000L
        private const val KEY_UPLOAD_ONLY = "upload_only"

        fun schedulePeriodic(context: Context, intervalMinutes: Int = 15) {
            // WorkManager enforces 15 minutes as the minimum periodic interval. Faster
            // refreshes are handled opportunistically while the app is in the foreground.
            val request = PeriodicWorkRequestBuilder<SyncWorker>(intervalMinutes.coerceAtLeast(15).toLong(), TimeUnit.MINUTES)
                .setConstraints(networkConstraints())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_SYNC,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun enqueueImmediate(context: Context) {
            val request = immediateRequest(uploadOnly = true)
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_IMMEDIATE_SYNC,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request,
            )
        }

        fun enqueueForegroundRefreshIfStale(context: Context) {
            val appContext = context.applicationContext
            val now = System.currentTimeMillis()
            val prefs = appContext.getSharedPreferences(SYNC_PREFS, Context.MODE_PRIVATE)
            val lastSyncActivityAt = prefs.getLong(KEY_LAST_SYNC_ACTIVITY_AT, 0L)
            if (!foregroundRefreshDue(lastSyncActivityAt, now)) return
            val request = immediateRequest(uploadOnly = false)
            WorkManager.getInstance(appContext).enqueueUniqueWork(
                UNIQUE_FOREGROUND_SYNC,
                ExistingWorkPolicy.KEEP,
                request,
            )
        }

        private fun immediateRequest(uploadOnly: Boolean) = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInputData(workDataOf(KEY_UPLOAD_ONLY to uploadOnly))
            .setConstraints(networkConstraints())
            .apply {
                // Android 12+ runs expedited jobs without a foreground service notification.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                }
            }
            .build()

        internal fun foregroundRefreshDue(lastSuccess: Long, now: Long): Boolean =
            lastSuccess <= 0 || now < lastSuccess || now - lastSuccess >= FOREGROUND_SYNC_THROTTLE_MILLIS

        private fun networkConstraints(): Constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        private fun markRecentSyncActivity(context: Context, now: Long = System.currentTimeMillis()) {
            context.applicationContext
                .getSharedPreferences(SYNC_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_LAST_SYNC_ACTIVITY_AT, now)
                .apply()
        }
    }
}

/**
 * Runs [sync], then reschedules reminders and refreshes widgets whatever its outcome: a sync that
 * fails late (e.g. a rejected upload) may already have stored new events. The two follow-ups are
 * independent, and neither can replace the sync error, which is returned (null on success).
 * Cancellation is never swallowed.
 */
internal suspend fun syncThenReconcile(
    sync: suspend () -> Unit,
    rescheduleReminders: suspend () -> Unit,
    refreshWidgets: suspend () -> Unit,
): Throwable? {
    val syncError = attempt(sync)
    attempt(rescheduleReminders)
    attempt(refreshWidgets)
    return syncError
}

private suspend fun attempt(block: suspend () -> Unit): Throwable? =
    try {
        block()
        null
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        error
    }
