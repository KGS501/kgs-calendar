package com.kgs.calendar

import android.app.Application
import android.database.ContentObserver
import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import androidx.room.InvalidationTracker
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.kgs.calendar.reminder.ReminderScheduler
import com.kgs.calendar.sync.SyncWorker
import com.kgs.calendar.widget.KgsWidgetUpdateScheduler
import com.kgs.calendar.widget.WidgetDataGeneration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class KgsCalendarApplication : Application() {
    lateinit var appGraph: AppGraph
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _processForegroundedAt = MutableSharedFlow<Long>(replay = 1)
    val processForegroundedAt: SharedFlow<Long> = _processForegroundedAt.asSharedFlow()
    private val processLifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            _processForegroundedAt.tryEmit(System.currentTimeMillis())
        }

        override fun onStop(owner: LifecycleOwner) {
            val backgroundedAt = System.currentTimeMillis()
            scope.launch {
                appGraph.settingsStore.setLastBackgroundedAtMillis(backgroundedAt)
            }
        }
    }
    private var androidCalendarRefreshJob: Job? = null
    private var androidCalendarObserver: ContentObserver? = null
    private var widgetRefreshJob: Job? = null
    private val widgetInvalidationObserver = object : InvalidationTracker.Observer(
        "events",
        "tasks",
        "collections",
        "accounts",
        "pending_mutations",
    ) {
        override fun onInvalidated(tables: Set<String>) {
            WidgetDataGeneration.increment()
            scheduleWidgetRefresh()
        }
    }

    override fun onCreate() {
        super.onCreate()
        appGraph = AppGraph(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(processLifecycleObserver)
        ReminderScheduler.ensureChannel(this)
        registerWidgetRefreshHooks()
        registerAndroidCalendarObserverIfPermitted()
        SyncWorker.schedulePeriodic(this)
        SyncWorker.enqueueImmediate(this)
        scope.launch {
            runCatching { ReminderScheduler.reschedule(this@KgsCalendarApplication) }
        }
        scope.launch {
            // After parser fixes for nested VALARM blocks, recurrence, attendee and category metadata shipped,
            // any rows synced under the previous version may have truncated/incorrect data.
            // Re-parse the cached raw iCal once so existing events are corrected without
            // forcing a full re-download.
            val current = appGraph.settingsStore.parserReparseVersion.first()
            if (current < PARSER_REPARSE_VERSION) {
                runCatching {
                    if (current >= FULL_REPARSE_VERSION) {
                        appGraph.repository.reparseLocalTaskResources()
                    } else {
                        appGraph.repository.reparseLocalResources()
                    }
                }
                appGraph.settingsStore.setParserReparseVersion(PARSER_REPARSE_VERSION)
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        KgsWidgetUpdateScheduler.updateAll(this)
    }

    fun registerAndroidCalendarObserverIfPermitted() {
        if (androidCalendarObserver != null) return
        if (!appGraph.repository.hasAndroidCalendarPermissions()) return
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                scheduleAndroidCalendarRefresh()
            }

            override fun onChange(selfChange: Boolean, uri: Uri?) {
                scheduleAndroidCalendarRefresh()
            }
        }
        runCatching {
            contentResolver.registerContentObserver(CalendarContract.Calendars.CONTENT_URI, true, observer)
            contentResolver.registerContentObserver(CalendarContract.Events.CONTENT_URI, true, observer)
            contentResolver.registerContentObserver(CalendarContract.Reminders.CONTENT_URI, true, observer)
            androidCalendarObserver = observer
        }
    }

    private fun registerWidgetRefreshHooks() {
        appGraph.database.invalidationTracker.addObserver(widgetInvalidationObserver)
        scope.launch {
            combine(
                appGraph.settingsStore.hiddenCollectionHrefs,
                appGraph.settingsStore.firstDayOfWeek,
                appGraph.settingsStore.showCompletedTasksInCalendar,
                appGraph.settingsStore.themeMode,
                appGraph.settingsStore.colorMode,
                appGraph.settingsStore.taskColorMode,
                appGraph.settingsStore.languageMode,
            ) { values -> values.toList() }
                .distinctUntilChanged()
                .collect {
                    WidgetDataGeneration.increment()
                    scheduleWidgetRefresh()
                }
        }
        scheduleWidgetRefresh()
    }

    private fun scheduleWidgetRefresh() {
        widgetRefreshJob?.cancel()
        widgetRefreshJob = scope.launch {
            delay(600)
            KgsWidgetUpdateScheduler.updateAll(this@KgsCalendarApplication)
        }
    }

    private fun scheduleAndroidCalendarRefresh() {
        androidCalendarRefreshJob?.cancel()
        androidCalendarRefreshJob = scope.launch {
            delay(1500)
            if (appGraph.repository.isAndroidProviderEnabled()) {
                val includeDisabledProviderCalendars = appGraph.settingsStore.showDisabledAndroidProviderCalendars.first()
                runCatching {
                    appGraph.repository.refreshAndroidCalendarsIfEnabled(
                        includeDisabledProviderCalendars = includeDisabledProviderCalendars,
                    )
                }
                runCatching { ReminderScheduler.reschedule(this@KgsCalendarApplication) }
            }
        }
    }

    companion object {
        private const val FULL_REPARSE_VERSION = 5
        private const val PARSER_REPARSE_VERSION = 6
        fun graph(context: Context): AppGraph =
            (context.applicationContext as KgsCalendarApplication).appGraph
    }
}
