package com.kgs.calendar.desktop

import com.kgs.calendar.data.CalendarDataComponents
import com.kgs.calendar.data.CalendarRepository
import com.kgs.calendar.data.ical.IcalCodec
import com.kgs.calendar.data.local.KgsDatabase
import com.kgs.calendar.data.local.desktopDatabase
import com.kgs.calendar.data.provider.NoDeviceCalendarProviderClient
import com.kgs.calendar.data.remote.CalDavHttpClient
import com.kgs.calendar.data.remote.NextcloudLoginFlowClient
import com.kgs.calendar.data.secure.WindowsCredentialsStore
import com.kgs.calendar.data.settings.SettingsStore
import com.kgs.calendar.domain.model.CalendarOccurrenceId
import com.kgs.calendar.navigation.CalendarLaunchResolver
import com.kgs.calendar.reminder.TaskMutationCoordinator
import com.kgs.calendar.reminder.TaskNotificationReconciler
import com.kgs.calendar.res.AppStrings
import com.kgs.calendar.sync.SourceCalendarMutationCoordinator
import com.kgs.calendar.ui.AppLifecycleSignals
import com.kgs.calendar.ui.CalendarFileExporter
import com.kgs.calendar.ui.CalendarViewModel
import kotlinx.coroutines.CoroutineScope
import com.kgs.calendar.ui.PickedContentReader
import com.kgs.calendar.ui.ReminderRescheduler
import com.kgs.calendar.ui.UiStrings
import com.kgs.calendar.ui.WidgetRefresher
import com.kgs.calendar.ui.editor.EditorDraftFiles
import com.kgs.calendar.ui.editor.EditorDraftStore
import com.kgs.calendar.widget.KgsWidgetKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * The desktop app's object graph: the same data layer, sync engine and view model as on Android,
 * with desktop storage (a profile folder), Windows credential protection and desktop reminders.
 */
class DesktopGraph(val dataDirectory: File) {
    val database: KgsDatabase = desktopDatabase(File(dataDirectory, "kgs-calendar.db"))

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .writeTimeout(40, TimeUnit.SECONDS)
        .build()

    val settingsStore = SettingsStore(File(dataDirectory, "kgs_settings.preferences_pb"))
    private val calendarData = CalendarDataComponents(
        database = database,
        credentialsStore = WindowsCredentialsStore(File(dataDirectory, "credentials.properties")),
        loginFlowClient = NextcloudLoginFlowClient(okHttpClient),
        calDavClient = CalDavHttpClient(okHttpClient),
        androidCalendarProviderClient = NoDeviceCalendarProviderClient(),
        icalCodec = IcalCodec(),
        readOnlyHttpClient = okHttpClient,
        sourceImageDirectory = File(dataDirectory, "source-images"),
    )
    val syncOrchestrator = calendarData.syncOrchestrator

    val repository = CalendarRepository(
        queries = calendarData.queries,
        eventMutations = calendarData.eventMutations,
        taskMutations = calendarData.taskMutations,
        sources = calendarData.sources,
        syncOrchestrator = syncOrchestrator,
        repairs = calendarData.repairs,
        trash = calendarData.trash,
        conflicts = calendarData.conflicts,
    )

    /** The app language: the app makes it the default locale while it is shown (see KgsCalendarApp). */
    val appLocale: Locale get() = Locale.getDefault()

    val reminders = DesktopReminders(repository, settingsStore, File(dataDirectory, "delivered-reminders.txt")) { appLocale }

    val sync = DesktopSync(database, repository, syncOrchestrator, settingsStore, afterSync = { reminders.reschedule() })

    /** Wall-clock time of every time the window was brought to the front. */
    val windowActivatedAt = MutableSharedFlow<Long>(replay = 1)

    private val sourceCalendarMutationCoordinator = SourceCalendarMutationCoordinator(
        includeDisabledProviderCalendars = { settingsStore.showDisabledAndroidProviderCalendars.first() },
        fullRefresh = { includeDisabledProviderCalendars ->
            repository.syncNow(
                includeDisabledProviderCalendars = includeDisabledProviderCalendars,
                forceFullCalDavRefresh = true,
            )
        },
        reconcileLocalState = { reminders.reschedule() },
    )

    private val calendarLaunchResolver = CalendarLaunchResolver(
        eventByResource = repository::eventByResource,
        taskByResource = repository::taskByResource,
        expandEvents = repository::expandEventReminderOccurrences,
        expandTasks = repository::expandTaskReminderOccurrences,
    )

    private val taskMutationCoordinator = TaskMutationCoordinator(
        persistStatus = { resourceHref, status, occurrenceId ->
            if (occurrenceId == null) {
                repository.setTaskStatus(resourceHref, status)
            } else {
                repository.setTaskOccurrenceStatus(resourceHref, occurrenceId.recurrenceIdMillis, status)
            }
        },
        pushPendingChanges = repository::pushPendingChangesCreatedSince,
        notificationReconciler = object : TaskNotificationReconciler {
            override suspend fun cancelOccurrence(occurrenceId: CalendarOccurrenceId.Task) = reminders.dismiss(occurrenceId.resourceHref)
            override suspend fun cancelResource(resourceHref: String) = reminders.dismiss(resourceHref)
        },
        rescheduleReminders = { reminders.reschedule() },
        updateWidgets = {},
    )

    /** Starts sync and reminders for the life of [scope]. */
    fun start(scope: CoroutineScope) {
        reminders.scope = scope
        sync.start(scope, windowActivatedAt)
        scope.launch { runCatching { reminders.reschedule() } }
    }

    fun createViewModel(): CalendarViewModel = CalendarViewModel(
        repository = repository,
        settingsStore = settingsStore,
        sourceCalendarMutationCoordinator = sourceCalendarMutationCoordinator,
        taskMutationCoordinator = taskMutationCoordinator,
        calendarLaunchResolver = calendarLaunchResolver,
        widgetRefresher = object : WidgetRefresher {
            override fun updateAll() = Unit
            override fun update(kind: KgsWidgetKind, forceFullDayUpdate: Boolean) = Unit
        },
        reminderRescheduler = ReminderRescheduler { reminders.reschedule() },
        appLifecycleSignals = object : AppLifecycleSignals {
            override val processForegroundedAt = windowActivatedAt
            override fun registerAndroidCalendarObserverIfPermitted() = Unit
        },
        uiStrings = UiStrings { id -> AppStrings.get(id, appLocale) },
        pickedContentReader = PickedContentReader { path ->
            withContext(Dispatchers.IO) {
                runCatching { File(path).takeIf { it.length() <= MAX_PICKED_IMAGE_BYTES }?.readBytes() }.getOrNull()
            }
        },
        calendarFileExporter = CalendarFileExporter { collectionHrefs, target ->
            val result = calendarData.export.export(collectionHrefs)
            withContext(Dispatchers.IO) { File(target).writeText(result.rawIcs, Charsets.UTF_8) }
            result.items
        },
        editorDrafts = EditorDraftStore(EditorDraftFiles(File(dataDirectory, "editor-drafts"))),
    )

    private companion object {
        const val MAX_PICKED_IMAGE_BYTES = 25L * 1024 * 1024
    }
}
