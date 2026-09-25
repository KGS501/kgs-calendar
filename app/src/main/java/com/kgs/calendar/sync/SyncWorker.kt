package com.kgs.calendar.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.Constraints
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
        val syncError = syncThenReconcile(
            sync = {
                val graph = KgsCalendarApplication.graph(applicationContext)
                val includeDisabledProviderCalendars = graph.settingsStore.showDisabledAndroidProviderCalendars.first()
                graph.repository.syncNow(includeDisabledProviderCalendars = includeDisabledProviderCalendars)
            },
            rescheduleReminders = { ReminderScheduler.reschedule(applicationContext) },
            refreshWidgets = { KgsCalendarApplication.graph(applicationContext).widgets.scheduler.updateAllAndAwait() },
        )
        if (syncError == null) {
            markRecentSyncActivity(applicationContext)
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
        private const val FOREGROUND_SYNC_THROTTLE_MILLIS = 10L * 60L * 1000L
        private const val BACKGROUND_SYNC_INTERVAL_MINUTES = 15L

        fun schedulePeriodic(context: Context) {
            // WorkManager enforces 15 minutes as the minimum periodic interval. Faster
            // refreshes are handled opportunistically while the app is in the foreground.
            val request = PeriodicWorkRequestBuilder<SyncWorker>(BACKGROUND_SYNC_INTERVAL_MINUTES, TimeUnit.MINUTES)
                .setConstraints(networkConstraints())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_SYNC,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun enqueueImmediate(context: Context) {
            markRecentSyncActivity(context)
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(networkConstraints())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_IMMEDIATE_SYNC,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        fun enqueueForegroundRefreshIfStale(context: Context) {
            val appContext = context.applicationContext
            val now = System.currentTimeMillis()
            val prefs = appContext.getSharedPreferences(SYNC_PREFS, Context.MODE_PRIVATE)
            val lastSyncActivityAt = prefs.getLong(KEY_LAST_SYNC_ACTIVITY_AT, 0L)
            if (now - lastSyncActivityAt < FOREGROUND_SYNC_THROTTLE_MILLIS) return
            markRecentSyncActivity(appContext, now)
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(networkConstraints())
                .build()
            WorkManager.getInstance(appContext).enqueueUniqueWork(
                UNIQUE_FOREGROUND_SYNC,
                ExistingWorkPolicy.KEEP,
                request,
            )
        }

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
