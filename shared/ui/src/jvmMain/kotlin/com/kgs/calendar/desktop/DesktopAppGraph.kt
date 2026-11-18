package com.kgs.calendar.desktop

import com.kgs.calendar.data.CalendarDataComponents
import com.kgs.calendar.data.CalendarRepository
import com.kgs.calendar.data.account.AwtSquareImageEncoder
import com.kgs.calendar.data.account.DesktopSourceImageFiles
import com.kgs.calendar.data.account.SourceImageStore
import com.kgs.calendar.data.ical.IcalCodec
import com.kgs.calendar.data.local.KgsDatabase
import com.kgs.calendar.data.local.buildDesktopKgsDatabase
import com.kgs.calendar.data.provider.AndroidCalendarProviderClient
import com.kgs.calendar.data.remote.CalDavHttpClient
import com.kgs.calendar.data.remote.NextcloudLoginFlowClient
import com.kgs.calendar.data.secure.WindowsCredentialsStore
import com.kgs.calendar.data.settings.AppColorMode
import com.kgs.calendar.data.settings.AppThemeMode
import com.kgs.calendar.data.settings.desktopSettingsStore
import com.kgs.calendar.domain.model.CalendarOccurrenceId
import com.kgs.calendar.navigation.CalendarLaunchResolver
import com.kgs.calendar.platform.platformHttpTransport
import com.kgs.calendar.reminder.TaskMutationCoordinator
import com.kgs.calendar.reminder.TaskNotificationReconciler
import com.kgs.calendar.sync.SourceCalendarMutationCoordinator
import com.kgs.calendar.time.defaultAppLocale
import com.kgs.calendar.ui.AppLifecycleSignals
import com.kgs.calendar.ui.CalendarFileExporter
import com.kgs.calendar.ui.CalendarViewModel
import com.kgs.calendar.ui.PickedContentReader
import com.kgs.calendar.ui.ReminderRescheduler
import com.kgs.calendar.ui.UiStrings
import com.kgs.calendar.ui.WidgetRefresher
import com.kgs.calendar.ui.editor.EditorDraftFiles
import com.kgs.calendar.ui.editor.EditorDraftStore
import com.kgs.calendar.ui.timeline.TimelineOrientationViewportMemory
import com.kgs.calendar.widget.KgsWidgetKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The desktop app's object graph, the counterpart of Android's AppGraph and [com.kgs.calendar.ios.IosAppGraph]
 * on iOS: the same data layer, sync and view model, with a profile folder ([dataDirectory]), passwords
 * protected by Windows, and reminders while the app runs.
 */
class DesktopAppGraph(val dataDirectory: File) {
    val database: KgsDatabase = buildDesktopKgsDatabase(File(dataDirectory, "kgs-calendar.db"))
    private val http = platformHttpTransport(connectTimeoutSeconds = 20, readTimeoutSeconds = 40)
    val settingsStore = desktopSettingsStore(File(dataDirectory, "kgs_settings.preferences_pb"))
    val timelineViewportMemory = TimelineOrientationViewportMemory()
    private val calendarData = CalendarDataComponents(
        database = database,
        credentialsStore = WindowsCredentialsStore(File(dataDirectory, "credentials.properties")),
        loginFlowClient = NextcloudLoginFlowClient(http),
        calDavClient = CalDavHttpClient(http),
        androidCalendarProviderClient = AndroidCalendarProviderClient.None,
        icalCodec = IcalCodec(),
        readOnlyHttpClient = http,
        sourceImages = SourceImageStore(DesktopSourceImageFiles(File(dataDirectory, "source-images")), AwtSquareImageEncoder),
        // Off the window's thread, so the app stays responsive while it syncs.
        syncContext = Dispatchers.Default,
    )

    val repository = CalendarRepository(
        queries = calendarData.queries,
        eventMutations = calendarData.eventMutations,
        taskMutations = calendarData.taskMutations,
        sources = calendarData.sources,
        syncOrchestrator = calendarData.syncOrchestrator,
        repairs = calendarData.repairs,
        trash = calendarData.trash,
        conflicts = calendarData.conflicts,
    )

    val reminders = DesktopReminders(repository, settingsStore, File(dataDirectory, "delivered-reminders.txt"))

    val sync = DesktopSync(database, repository, calendarData.syncOrchestrator, settingsStore, afterSync = { reminders.reschedule() })

    /** Wall-clock time of every time the window was brought to the front. */
    val windowActivatedAt = MutableSharedFlow<Long>(replay = 1)

    private val sourceCalendarMutationCoordinator = SourceCalendarMutationCoordinator(
        includeDisabledProviderCalendars = { false },
        fullRefresh = { _ -> repository.syncNow(includeDisabledProviderCalendars = false, forceFullCalDavRefresh = true) },
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
        timelineViewportMemory = timelineViewportMemory,
        // Home-screen widgets are Android only.
        widgetRefresher = object : WidgetRefresher {
            override fun updateAll() = Unit
            override fun update(kind: KgsWidgetKind, forceFullDayUpdate: Boolean) = Unit
        },
        reminderRescheduler = ReminderRescheduler { reminders.reschedule() },
        appLifecycleSignals = object : AppLifecycleSignals {
            override val processForegroundedAt = windowActivatedAt
            override fun registerAndroidCalendarObserverIfPermitted() = Unit
        },
        uiStrings = UiStrings { id -> id.text(defaultAppLocale()) },
        pickedContentReader = PickedContentReader { uri ->
            withContext(Dispatchers.IO) {
                runCatching { File(uri.value).takeIf { it.length() <= MAX_PICKED_IMAGE_BYTES }?.readBytes() }.getOrNull()
            }
        },
        calendarFileExporter = CalendarFileExporter { collectionHrefs, target ->
            val result = calendarData.export.export(collectionHrefs)
            withContext(Dispatchers.IO) { File(target.value).writeText(result.rawIcs, Charsets.UTF_8) }
            result
        },
        editorDrafts = EditorDraftStore(EditorDraftFiles(File(dataDirectory, "editor-drafts"))),
        // Read up front: this runs before the window appears, which then opens in the user's colours.
        initialThemeMode = runCatching { runBlocking { settingsStore.themeMode.first() } }.getOrDefault(AppThemeMode.KgsBlue),
        initialColorMode = runCatching { runBlocking { settingsStore.colorMode.first() } }.getOrDefault(AppColorMode.Auto),
    )

    private companion object {
        const val MAX_PICKED_IMAGE_BYTES = 25L * 1024 * 1024
    }
}
