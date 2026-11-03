package com.kgs.calendar.port

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.kgs.calendar.data.settings.AppThemeMode
import com.kgs.calendar.importing.IcsImportScreen
import com.kgs.calendar.importing.IcsImportViewModel
import com.kgs.calendar.domain.model.CalendarViewMode
import com.kgs.calendar.ui.platform.PlatformUri
import com.kgs.calendar.ui.theme.KgsCalendarTheme
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import platform.Foundation.NSFileManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kgs.calendar.ios.IosAppGraph
import com.kgs.calendar.ios.IosReminderScheduler
import com.kgs.calendar.ios.IosSync
import com.kgs.calendar.time.IosAppLanguage
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import com.kgs.calendar.navigation.CalendarLaunchTarget
import com.kgs.calendar.navigation.SharedEventDraft
import com.kgs.calendar.time.nowMillis
import com.kgs.calendar.ui.CalendarViewModel
import com.kgs.calendar.ui.KgsCalendarApp
import com.kgs.calendar.ui.layout.ProvideFoldPosture
import com.kgs.calendar.ui.layout.rememberWindowFoldFeatures
import com.kgs.calendar.ui.theme.CappedFontScale
import com.kgs.calendar.ui.theme.MaxAppFontScale
import kotlinx.coroutines.launch
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIViewController
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationDefaultActionIdentifier
import platform.UserNotifications.UNNotificationPresentationOptionBanner
import platform.UserNotifications.UNNotificationPresentationOptionList
import platform.UserNotifications.UNNotificationPresentationOptionSound
import platform.UserNotifications.UNNotificationPresentationOptions
import platform.UserNotifications.UNNotificationResponse
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNUserNotificationCenterDelegateProtocol
import platform.darwin.NSObject

private val notificationDelegate = NotificationDelegate()

/** The view model of the one calendar screen, kept for notification taps that arrive later. */
private var activeViewModel: CalendarViewModel? = null
private var pendingLaunch: CalendarLaunchTarget? = null

private var pendingSharedEvent: SharedEventDraft? = null

/** Text shared with the KgsShare extension: the event editor opens prefilled, as on Android. */
fun openSharedText(subject: String?, text: String?) {
    val draft = SharedEventDraft.fromShare(subject = subject, text = text, htmlText = null)
    activeViewModel?.openFromShare(draft) ?: run { pendingSharedEvent = draft }
}

/** A calendar file opened with the app, shown over the calendar until it is closed. */
private val pendingIcsFile = mutableStateOf<String?>(null)

/** Called from the Swift app for files opened with it (onOpenURL). */
fun openFile(path: String) {
    pendingIcsFile.value?.let { NSFileManager.defaultManager.removeItemAtPath(it, null) }
    pendingIcsFile.value = path
}

private class NotificationDelegate : NSObject(), UNUserNotificationCenterDelegateProtocol {
    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        willPresentNotification: UNNotification,
        withCompletionHandler: (UNNotificationPresentationOptions) -> Unit,
    ) {
        val show = UNNotificationPresentationOptionBanner or UNNotificationPresentationOptionList or UNNotificationPresentationOptionSound
        val occurrence = (willPresentNotification.request.content.userInfo[IosReminderScheduler.KEY_TARGET] as? String)
            ?.let(CalendarLaunchTarget::decode)?.occurrence
        if (occurrence == null) {
            withCompletionHandler(show)
            return
        }
        // As Android's receiver: the item's calendar may have been hidden after the reminder was planned.
        val graph = IosAppGraph.shared
        graph.scope.launch {
            val visible = runCatching {
                graph.repository.isReminderStillVisible(occurrence, graph.settingsStore.collectionVisibility.first())
            }.getOrNull() ?: true
            withCompletionHandler(if (visible) show else 0u)
        }
    }

    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        didReceiveNotificationResponse: UNNotificationResponse,
        withCompletionHandler: () -> Unit,
    ) {
        val notification = didReceiveNotificationResponse.notification
        val graph = IosAppGraph.shared
        when (didReceiveNotificationResponse.actionIdentifier) {
            IosReminderScheduler.ACTION_SNOOZE -> graph.reminders.snooze(notification, withCompletionHandler)
            IosReminderScheduler.ACTION_MARK_DONE -> graph.scope.launch {
                runCatching { graph.reminders.markDone(notification.request.content) }
                withCompletionHandler()
            }
            UNNotificationDefaultActionIdentifier -> {
                val encoded = notification.request.content.userInfo[IosReminderScheduler.KEY_TARGET] as? String
                encoded?.let(CalendarLaunchTarget::decode)?.let { target ->
                    activeViewModel?.openFromCalendarLaunch(target) ?: run { pendingLaunch = target }
                }
                withCompletionHandler()
            }
            else -> withCompletionHandler()
        }
    }
}

@Composable
private fun CalendarRootContent(graph: IosAppGraph) {
    val viewModel: CalendarViewModel = viewModel {
        CalendarViewModel(
            repository = graph.repository,
            settingsStore = graph.settingsStore,
            sourceCalendarMutationCoordinator = graph.sourceCalendarMutationCoordinator,
            taskMutationCoordinator = graph.taskMutationCoordinator,
            calendarLaunchResolver = graph.calendarLaunchResolver,
            timelineViewportMemory = graph.timelineViewportMemory,
            widgetRefresher = graph.widgetRefresher,
            reminderRescheduler = graph.reminderRescheduler,
            appLifecycleSignals = graph.appLifecycleSignals,
            uiStrings = graph.uiStrings,
            pickedContentReader = graph.pickedContentReader,
            calendarFileExporter = graph.calendarFileExporter,
            initialCalendarLaunchTarget = pendingLaunch.also { pendingLaunch = null },
            initialSharedEvent = pendingSharedEvent.also { pendingSharedEvent = null },
            editorDrafts = graph.newEditorDraftStore(),
        )
    }
    remember(viewModel) { activeViewModel = viewModel }
    val foldFeatures by rememberWindowFoldFeatures()
    CappedFontScale(MaxAppFontScale) {
        ProvideFoldPosture(foldFeatures) {
            KgsCalendarApp(viewModel = viewModel)
            pendingIcsFile.value?.let { path ->
                IcsImportOverlay(graph, path, onDone = { firstStart ->
                    pendingIcsFile.value = null
                    NSFileManager.defaultManager.removeItemAtPath(path, null)
                    firstStart?.let { start ->
                        val date = Instant.fromEpochMilliseconds(start).toLocalDateTime(TimeZone.currentSystemDefault()).date
                        viewModel.openFromWidget(date = date, viewMode = CalendarViewMode.Day)
                    }
                })
            }
        }
    }
}

/** Android's IcsImportActivity: the same screen, over the calendar. [onDone] gets the start to show, if any. */
@Composable
private fun IcsImportOverlay(graph: IosAppGraph, path: String, onDone: (Long?) -> Unit) {
    val importViewModel: IcsImportViewModel = viewModel(key = "ics-import:$path") { graph.newIcsImportViewModel() }
    LaunchedEffect(importViewModel) { importViewModel.load(PlatformUri(path)) }
    val state by importViewModel.state.collectAsState()
    val theme by graph.settingsStore.themeMode.collectAsState(AppThemeMode.KgsBlue)
    KgsCalendarTheme(themeMode = theme) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            IcsImportScreen(
                state = state,
                onSelectCalendar = importViewModel::selectCalendar,
                onImport = importViewModel::importEvents,
                onClose = { onDone(null) },
                onOpenCalendar = { onDone(state.events.firstOrNull()?.event?.startsAtMillis ?: nowMillis()) },
            )
        }
    }
}

/** Called from the Swift app's init, before launching finishes, as BGTaskScheduler requires. */
/**
 * Called from the Swift app's init, before launching finishes: BGTaskScheduler handlers and the
 * notification delegate must be set by then, also for launches in the background (a background
 * refresh, a reminder's Snooze or Mark done button).
 */
fun onAppLaunch() {
    IosSync.registerBackgroundTasks()
    UNUserNotificationCenter.currentNotificationCenter().delegate = notificationDelegate
    val graph = IosAppGraph.shared
    graph.registerDeviceCalendarObserverIfPermitted()
    graph.scope.launch {
        // The app's language, as Android's Locale.setDefault: reminders and messages follow it.
        graph.settingsStore.languageMode.distinctUntilChanged().collect { mode ->
            IosAppLanguage.languageTag = mode.localeTag
            graph.reminders.registerActions()
            runCatching { graph.reminders.reschedule() }
        }
    }
}

fun MainViewController(): UIViewController {
    val graph = IosAppGraph.shared
    graph.reminders.requestPermission()
    graph.sync.start()
    // The app may already be active when the screen is built, so the first activation can come too early to observe.
    graph.sync.onForeground()
    NSNotificationCenter.defaultCenter.addObserverForName(UIApplicationDidBecomeActiveNotification, `object` = null, queue = NSOperationQueue.mainQueue) { _ ->
        graph.onForegrounded(nowMillis())
        graph.sync.onForeground()
        graph.scope.launch { runCatching { graph.reminders.reschedule() } }
    }
    NSNotificationCenter.defaultCenter.addObserverForName(UIApplicationDidEnterBackgroundNotification, `object` = null, queue = NSOperationQueue.mainQueue) { _ ->
        graph.sync.onBackground()
    }
    return ComposeUIViewController { CalendarRootContent(graph) }
}
