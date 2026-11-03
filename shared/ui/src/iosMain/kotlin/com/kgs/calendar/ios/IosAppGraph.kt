package com.kgs.calendar.ios

import com.kgs.calendar.data.CalendarDataComponents
import com.kgs.calendar.data.CalendarRepository
import com.kgs.calendar.data.account.IosSourceImageFiles
import com.kgs.calendar.data.account.SourceImageStore
import com.kgs.calendar.data.account.UIKitSquareImageEncoder
import com.kgs.calendar.data.ical.IcalCodec
import com.kgs.calendar.data.local.buildIosKgsDatabase
import com.kgs.calendar.data.provider.EventKitCalendarClient
import com.kgs.calendar.data.provider.IosEventStore
import com.kgs.calendar.data.remote.CalDavHttpClient
import com.kgs.calendar.data.remote.NextcloudLoginFlowClient
import com.kgs.calendar.data.secure.KeychainCredentialsStore
import com.kgs.calendar.data.settings.iosSettingsStore
import com.kgs.calendar.domain.model.CalendarOccurrenceId
import com.kgs.calendar.importing.IcsImportViewModel
import com.kgs.calendar.navigation.CalendarLaunchResolver
import com.kgs.calendar.platform.platformHttpTransport
import com.kgs.calendar.platform.toByteArray
import com.kgs.calendar.platform.toNSData
import com.kgs.calendar.reminder.TaskMutationCoordinator
import com.kgs.calendar.reminder.TaskNotificationReconciler
import com.kgs.calendar.sync.SourceCalendarMutationCoordinator
import com.kgs.calendar.time.defaultAppLocale
import com.kgs.calendar.ui.AppLifecycleSignals
import com.kgs.calendar.ui.CalendarFileExporter
import com.kgs.calendar.ui.PickedContentReader
import com.kgs.calendar.ui.ReminderRescheduler
import com.kgs.calendar.ui.UiStrings
import com.kgs.calendar.ui.WidgetRefresher
import com.kgs.calendar.ui.editor.EditorDraftStore
import com.kgs.calendar.ui.editor.IosDraftFiles
import com.kgs.calendar.ui.timeline.TimelineOrientationViewportMemory
import com.kgs.calendar.widget.KgsWidgetKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import platform.EventKit.EKEventStoreChangedNotification
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.writeToFile

/** The iOS counterpart of Android's AppGraph: one per process. */
class IosAppGraph private constructor() {
    val appSupportDirectory: String = run {
        val url = NSFileManager.defaultManager.URLsForDirectory(NSApplicationSupportDirectory, NSUserDomainMask).first() as NSURL
        val path = requireNotNull(url.path) + "/KGS Calendar"
        NSFileManager.defaultManager.createDirectoryAtPath(path, withIntermediateDirectories = true, attributes = null, error = null)
        path
    }

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val database = buildIosKgsDatabase()
    private val http = platformHttpTransport(connectTimeoutSeconds = 20, readTimeoutSeconds = 40)
    val settingsStore = iosSettingsStore(appSupportDirectory)
    val timelineViewportMemory = TimelineOrientationViewportMemory()
    private val credentialsStore = KeychainCredentialsStore()
    private val calendarData = CalendarDataComponents(
        database = database,
        credentialsStore = credentialsStore,
        loginFlowClient = NextcloudLoginFlowClient(http),
        calDavClient = CalDavHttpClient(http),
        androidCalendarProviderClient = EventKitCalendarClient("$appSupportDirectory/device-calendar-ids.json"),
        icalCodec = IcalCodec(),
        readOnlyHttpClient = http,
        sourceImages = SourceImageStore(IosSourceImageFiles("$appSupportDirectory/source-images"), UIKitSquareImageEncoder),
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

    val reminders = IosReminderScheduler(this)

    val sourceCalendarMutationCoordinator = SourceCalendarMutationCoordinator(
        includeDisabledProviderCalendars = { false },
        fullRefresh = { _ -> repository.syncNow(includeDisabledProviderCalendars = false, forceFullCalDavRefresh = true) },
        reconcileLocalState = { reminders.reschedule() },
    )

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
        notificationReconciler = object : TaskNotificationReconciler {
            override suspend fun cancelOccurrence(occurrenceId: CalendarOccurrenceId.Task) = reminders.cancelFor(occurrenceId.stableKey)
            override suspend fun cancelResource(resourceHref: String) = reminders.cancelForResource(resourceHref)
        },
        rescheduleReminders = { reminders.reschedule() },
        updateWidgets = {},
    )

    /** Home-screen widgets are Android only for now. */
    val widgetRefresher = object : WidgetRefresher {
        override fun updateAll() = Unit
        override fun update(kind: KgsWidgetKind, forceFullDayUpdate: Boolean) = Unit
    }

    val reminderRescheduler = ReminderRescheduler { reminders.reschedule() }

    val sync = IosSync(this)

    private val foregrounded = MutableSharedFlow<Long>(extraBufferCapacity = 1)

    /** Called by the host view controller when the app comes to the foreground. */
    fun onForegrounded(nowMillis: Long) {
        foregrounded.tryEmit(nowMillis)
    }

    val appLifecycleSignals = object : AppLifecycleSignals {
        override val processForegroundedAt: Flow<Long> get() = foregrounded.asSharedFlow()
        override fun registerAndroidCalendarObserverIfPermitted() = registerDeviceCalendarObserverIfPermitted()
    }

    private var deviceCalendarObserver: Any? = null
    private var deviceCalendarRefresh: Job? = null

    /** As Android's calendar provider observer: changes made in other apps are read again. */
    fun registerDeviceCalendarObserverIfPermitted() {
        if (deviceCalendarObserver != null || !IosEventStore.hasFullAccess()) return
        deviceCalendarObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            EKEventStoreChangedNotification,
            `object` = IosEventStore.store,
            queue = NSOperationQueue.mainQueue,
        ) { _ ->
            deviceCalendarRefresh?.cancel()
            deviceCalendarRefresh = scope.launch {
                delay(1500)
                if (repository.isAndroidProviderEnabled()) {
                    runCatching { repository.refreshAndroidCalendarsIfEnabled() }
                    runCatching { reminders.reschedule() }
                }
            }
        }
    }

    val uiStrings = UiStrings { id -> id.text(defaultAppLocale()) }

    val calendarFileExporter = CalendarFileExporter { collectionHrefs, target ->
        val result = calendarData.export.export(collectionHrefs)
        withContext(Dispatchers.IO) {
            val written = result.rawIcs.encodeToByteArray().toNSData().writeToFile(target.value, atomically = true)
            check(written) { "The file can't be written." }
        }
        withContext(Dispatchers.Main) { IosDocumentExport.offer(target.value) }
        result.items
    }

    val pickedContentReader = PickedContentReader { uri ->
        withContext(Dispatchers.IO) {
            NSData.create(contentsOfFile = uri.value)?.toByteArray()?.takeIf { it.size <= MAX_PICKED_IMAGE_BYTES }
        }
    }

    /** The screen for a calendar file opened with the app (Files, Mail, the share sheet). */
    fun newIcsImportViewModel(): IcsImportViewModel = IcsImportViewModel(
        imports = calendarData.eventImports,
        queries = calendarData.queries,
        sources = calendarData.sources,
        settings = settingsStore,
        readAttachment = { uri ->
            val bytes = requireNotNull(NSData.create(contentsOfFile = uri.value)?.toByteArray()) { "The file can't be read." }
            require(bytes.size <= MAX_ICS_BYTES) { "Calendar file is too large" }
            bytes.decodeToString()
        },
        onImported = { reminders.reschedule() },
    )

    fun newEditorDraftStore(): EditorDraftStore = EditorDraftStore(IosDraftFiles("$appSupportDirectory/editor-drafts"))

    companion object {
        private const val MAX_PICKED_IMAGE_BYTES = 25 * 1024 * 1024
        private const val MAX_ICS_BYTES = 2 * 1024 * 1024
        val shared: IosAppGraph by lazy { IosAppGraph() }
    }
}
