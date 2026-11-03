package com.kgs.calendar.ios

import com.kgs.calendar.domain.source.isLocalAccount
import com.kgs.calendar.sync.reparseCachedIcalIfNeeded
import com.kgs.calendar.sync.reparseForZoneChangeIfNeeded
import com.kgs.calendar.time.ZoneId
import com.kgs.calendar.time.nowMillis
import com.kgs.calendar.time.systemDefault
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.BackgroundTasks.BGAppRefreshTaskRequest
import platform.BackgroundTasks.BGTask
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSDate
import platform.Foundation.NSLog
import platform.Foundation.dateWithTimeIntervalSinceNow

/**
 * What Android's KgsCalendarApplication and SyncWorker do: upload the outbox as soon as it has
 * rows, refresh while the app is open (at most once a minute), and refresh in the background
 * through BGTaskScheduler, which, like WorkManager, decides itself when that really runs.
 */
@OptIn(FlowPreview::class)
class IosSync internal constructor(private val graph: IosAppGraph) {
    private val syncLock = Mutex()
    private var lastSyncActivityAt = 0L
    private var foregroundJob: Job? = null
    private var started = false

    /** Starts the outbox upload and the start-up repairs; called once the UI is shown. */
    fun start() {
        if (started) return
        started = true
        graph.scope.launch {
            // Covers edits from every screen; the first emission recovers work left by a killed app.
            graph.database.pendingMutationDao().observeAll()
                .map { rows -> rows.map { it.id } }
                .distinctUntilChanged()
                .debounce(500)
                .collect { ids ->
                    if (ids.isEmpty()) return@collect
                    val error = attempt { syncLock.withLock { graph.repository.pushAllPendingChanges() } }
                    log("upload of ${ids.size} changes ${error ?: "ok"}")
                }
        }
        graph.scope.launch {
            reparseCachedIcalIfNeeded(
                storedVersion = graph.settingsStore.parserReparseVersion.first(),
                reparseTaskResources = graph.repository::reparseLocalTaskResources,
                reparseAllResources = graph.repository::reparseLocalResources,
                recordVersion = graph.settingsStore::setParserReparseVersion,
            )
            val reparsed = reparseForZoneChangeIfNeeded(
                storedZoneId = graph.settingsStore.parsedZoneId.first(),
                currentZoneId = ZoneId.systemDefault().id,
                reparseAllResources = graph.repository::reparseLocalResources,
                reloadDeviceCalendars = {},
                recordZone = graph.settingsStore::setParsedZoneId,
            )
            if (reparsed) attempt { graph.reminders.reschedule() }
        }
        graph.scope.launch {
            graph.settingsStore.syncIntervalMinutes.distinctUntilChanged().collect { scheduleBackgroundRefresh(it) }
        }
    }

    fun onForeground() {
        foregroundJob?.cancel()
        foregroundJob = graph.scope.launch {
            while (isActive) {
                if (hasRemoteSources() && foregroundRefreshDue(lastSyncActivityAt, nowMillis())) syncAll()
                delay(FOREGROUND_SYNC_THROTTLE_MILLIS)
            }
        }
    }

    fun onBackground() {
        foregroundJob?.cancel()
        foregroundJob = null
        val backgroundedAt = nowMillis()
        graph.scope.launch {
            graph.settingsStore.setLastBackgroundedAtMillis(backgroundedAt)
            scheduleBackgroundRefresh(graph.settingsStore.syncIntervalMinutes.first())
        }
    }

    /** A full sync, then reminders are planned again whatever the outcome. Returns the sync error. */
    suspend fun syncAll(): Throwable? {
        val error = attempt { syncLock.withLock { graph.repository.syncNow() } }
        log("sync ${error ?: "ok"}")
        if (error == null) lastSyncActivityAt = nowMillis()
        attempt { graph.reminders.reschedule() }
        return error
    }

    private suspend fun hasRemoteSources(): Boolean = graph.database.accountDao().getAll().any { !it.isLocalAccount() }

    internal fun runBackgroundRefresh(task: BGTask) {
        val job = graph.scope.launch {
            val error = if (hasRemoteSources()) syncAll() else null
            scheduleBackgroundRefresh(graph.settingsStore.syncIntervalMinutes.first())
            task.setTaskCompletedWithSuccess(error == null)
        }
        task.expirationHandler = {
            job.cancel()
            task.setTaskCompletedWithSuccess(false)
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun scheduleBackgroundRefresh(intervalMinutes: Int) {
        if (!registered) return
        val request = BGAppRefreshTaskRequest(REFRESH_TASK_ID)
        request.earliestBeginDate = NSDate.dateWithTimeIntervalSinceNow(intervalMinutes.coerceAtLeast(15) * 60.0)
        BGTaskScheduler.sharedScheduler.cancelTaskRequestWithIdentifier(REFRESH_TASK_ID)
        BGTaskScheduler.sharedScheduler.submitTaskRequest(request, error = null)
    }

    companion object {
        /** Listed under BGTaskSchedulerPermittedIdentifiers in Info.plist. */
        const val REFRESH_TASK_ID = "com.kgs501.kgscalendar.refresh"
        const val FOREGROUND_SYNC_THROTTLE_MILLIS = 60L * 1000L
        private var registered = false

        /** Must run before the app finishes launching (the Swift app's init does this). */
        fun registerBackgroundTasks() {
            if (registered) return
            registered = BGTaskScheduler.sharedScheduler.registerForTaskWithIdentifier(REFRESH_TASK_ID, usingQueue = null) { task ->
                task?.let { IosAppGraph.shared.sync.runBackgroundRefresh(it) }
            }
        }

        fun foregroundRefreshDue(lastSuccess: Long, now: Long): Boolean =
            lastSuccess <= 0 || now < lastSuccess || now - lastSuccess >= FOREGROUND_SYNC_THROTTLE_MILLIS
    }
}

/** To the system log (Console, `log show`); variadic NSLog arguments don't take Kotlin strings. */
private fun log(message: String) = NSLog("KgsSync: " + message.replace("%", "%%"))

private suspend fun attempt(block: suspend () -> Unit): Throwable? =
    try {
        block()
        null
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        error
    }
