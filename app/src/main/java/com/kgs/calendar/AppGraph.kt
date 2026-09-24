package com.kgs.calendar

import android.content.Context
import androidx.room.Room
import com.kgs.calendar.data.CalendarDataComponents
import com.kgs.calendar.data.CalendarRepository
import com.kgs.calendar.data.account.CalendarSourceManager
import com.kgs.calendar.data.ical.IcalCodec
import com.kgs.calendar.data.local.KgsDatabase
import com.kgs.calendar.data.local.KgsDatabaseMigrations
import com.kgs.calendar.data.mutation.EventMutations
import com.kgs.calendar.data.mutation.TaskMutations
import com.kgs.calendar.data.provider.AndroidCalendarProviderClient
import com.kgs.calendar.data.query.CalendarQueries
import com.kgs.calendar.data.remote.CalDavHttpClient
import com.kgs.calendar.data.remote.NextcloudLoginFlowClient
import com.kgs.calendar.data.secure.CredentialsStore
import com.kgs.calendar.data.secure.EncryptedCredentialsStore
import com.kgs.calendar.data.settings.SettingsStore
import com.kgs.calendar.data.sync.SyncOrchestrator
import com.kgs.calendar.data.sync.SyncRepairs
import com.kgs.calendar.reminder.ReminderRegistry
import com.kgs.calendar.reminder.ReminderScheduler
import com.kgs.calendar.reminder.TaskMutationCoordinator
import com.kgs.calendar.navigation.CalendarLaunchResolver
import com.kgs.calendar.sync.SourceCalendarMutationCoordinator
import com.kgs.calendar.ui.AppLifecycleSignals
import com.kgs.calendar.ui.ReminderRescheduler
import com.kgs.calendar.ui.UiStrings
import com.kgs.calendar.ui.WidgetRefresher
import com.kgs.calendar.ui.timeline.TimelineOrientationViewportMemory
import com.kgs.calendar.widget.KgsWidgetKind
import com.kgs.calendar.widget.update.KgsWidgetUpdateScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AppGraph(context: Context) {
    val appContext: Context = context.applicationContext

    val database: KgsDatabase = Room.databaseBuilder(appContext, KgsDatabase::class.java, "kgs-calendar.db")
        .addMigrations(*KgsDatabaseMigrations.ALL)
        .build()

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .writeTimeout(40, TimeUnit.SECONDS)
        .build()

    val settingsStore = SettingsStore(appContext)
    internal val timelineViewportMemory = TimelineOrientationViewportMemory()
    private val credentialsStore: CredentialsStore = EncryptedCredentialsStore(appContext)
    private val loginFlowClient = NextcloudLoginFlowClient(okHttpClient)
    private val calDavHttpClient = CalDavHttpClient(okHttpClient)
    private val androidCalendarProviderClient = AndroidCalendarProviderClient(appContext)
    private val icalCodec = IcalCodec()

    private val calendarData = CalendarDataComponents(
        database = database,
        credentialsStore = credentialsStore,
        loginFlowClient = loginFlowClient,
        calDavClient = calDavHttpClient,
        androidCalendarProviderClient = androidCalendarProviderClient,
        icalCodec = icalCodec,
        readOnlyHttpClient = okHttpClient,
    )
    val calendarQueries: CalendarQueries = calendarData.queries
    val eventMutations: EventMutations = calendarData.eventMutations
    val taskMutations: TaskMutations = calendarData.taskMutations
    val calendarSources: CalendarSourceManager = calendarData.sources
    val syncOrchestrator: SyncOrchestrator = calendarData.syncOrchestrator
    val syncRepairs: SyncRepairs = calendarData.repairs

    val repository = CalendarRepository(
        queries = calendarQueries,
        eventMutations = eventMutations,
        taskMutations = taskMutations,
        sources = calendarSources,
        syncOrchestrator = syncOrchestrator,
        repairs = syncRepairs,
    )

    val sourceCalendarMutationCoordinator = SourceCalendarMutationCoordinator(
        includeDisabledProviderCalendars = { settingsStore.showDisabledAndroidProviderCalendars.first() },
        fullRefresh = { includeDisabledProviderCalendars ->
            repository.syncNow(
                includeDisabledProviderCalendars = includeDisabledProviderCalendars,
                forceFullCalDavRefresh = true,
            )
        },
        reconcileLocalState = {
            ReminderScheduler.reschedule(appContext)
            KgsWidgetUpdateScheduler.updateAll(appContext)
        },
    )

    val reminderRegistry = ReminderRegistry.create(appContext)

    val calendarLaunchResolver = CalendarLaunchResolver(
        eventByResource = repository::eventByResource,
        taskByResource = repository::taskByResource,
        expandEvents = repository::expandEventReminderOccurrences,
        expandTasks = repository::expandTaskReminderOccurrences,
    )

    val taskMutationCoordinator = TaskMutationCoordinator(
        persistStatus = { resourceHref, status, occurrenceId ->
            if (occurrenceId == null) {
                repository.setTaskStatus(resourceHref, status)
            } else {
                repository.setTaskOccurrenceStatus(resourceHref, occurrenceId.recurrenceIdMillis, status)
            }
        },
        pushPendingChanges = repository::pushPendingChangesCreatedSince,
        notificationReconciler = reminderRegistry,
        rescheduleReminders = { ReminderScheduler.reschedule(appContext) },
        updateWidgets = { KgsWidgetUpdateScheduler.updateAll(appContext) },
    )

    val widgetRefresher: WidgetRefresher = object : WidgetRefresher {
        override fun updateAll() {
            KgsWidgetUpdateScheduler.updateAll(appContext)
        }

        override fun update(kind: KgsWidgetKind, forceFullDayUpdate: Boolean) {
            KgsWidgetUpdateScheduler.update(appContext, kind, forceFullDayUpdate = forceFullDayUpdate)
        }
    }

    val reminderRescheduler = ReminderRescheduler { ReminderScheduler.reschedule(appContext) }

    val appLifecycleSignals: AppLifecycleSignals = object : AppLifecycleSignals {
        override val processForegroundedAt: Flow<Long>
            get() = (appContext as? KgsCalendarApplication)?.processForegroundedAt ?: emptyFlow()

        override fun registerAndroidCalendarObserverIfPermitted() {
            (appContext as? KgsCalendarApplication)?.registerAndroidCalendarObserverIfPermitted()
        }
    }

    val uiStrings = UiStrings { id -> appContext.getString(id) }
}
