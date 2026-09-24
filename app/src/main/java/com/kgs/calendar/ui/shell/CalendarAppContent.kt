package com.kgs.calendar.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import android.content.Intent
import android.net.Uri
import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.domain.model.CalendarViewMode
import com.kgs.calendar.domain.model.MutationAction
import com.kgs.calendar.ui.model.occurrenceStartForEdit
import com.kgs.calendar.ui.shell.eventDraftColor
import com.kgs.calendar.ui.shell.newEventSchedule
import com.kgs.calendar.ui.shell.newTaskSchedule
import com.kgs.calendar.ui.shell.rememberCalendarShellUiState
import com.kgs.calendar.ui.shell.taskDraftColor
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The themed app body: derives the render state, owns the shell navigation state and composes
 * the calendar scaffold, sheets and overlays in their drawing order.
 */
@Composable
internal fun CalendarAppContent(
    viewModel: CalendarViewModel,
    state: CalendarUiState,
    today: LocalDate,
) {
    val defaultWireframeColor = WarmBrown.toArgb()
    val context = LocalContext.current
    val shell = rememberCalendarShellUiState(
        state = state,
        today = today,
        defaultWireframeColor = defaultWireframeColor,
        editorDrafts = viewModel.editorDrafts,
    )
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        viewModel.editorDrafts.flush()
    }
    val searchScope = rememberCoroutineScope()
    var foregroundRecenterRequest by rememberSaveable { mutableStateOf(0) }
    val deleteFadeResourceHrefs = remember { mutableStateMapOf<String, Unit>() }
    val pendingDeleteHrefs = remember(state.pendingMutationItems) {
        state.pendingMutationItems
            .filter { it.action == MutationAction.Delete }
            .map { it.resourceHref }
            .toSet()
    }
    LaunchedEffect(pendingDeleteHrefs) {
        pendingDeleteHrefs.forEach { href ->
            deleteFadeResourceHrefs[href] = Unit
            launch {
                delay(30 * 60 * 1000L)
                deleteFadeResourceHrefs.remove(href)
            }
        }
    }
    val retainedDeleteHrefs = deleteFadeResourceHrefs.keys.toSet() + pendingDeleteHrefs
    val smoothEvents = rememberSmoothRemoval(
        state.events,
        EventEntity::smoothRemovalKey,
        EventEntity::resourceHref,
        retainedDeleteHrefs,
    )
    val smoothSearchEvents = rememberSmoothRemoval(
        state.searchResults,
        { "${it.resourceHref}:${it.occurrenceStartForEdit()}" },
        EventEntity::resourceHref,
        retainedDeleteHrefs,
    )
    val smoothDatedTasks = rememberSmoothRemoval(
        state.datedTasks,
        TaskEntity::smoothRemovalKey,
        TaskEntity::resourceHref,
        retainedDeleteHrefs,
    )
    val smoothInboxTasks = rememberSmoothRemoval(
        state.inboxTasks,
        TaskEntity::smoothRemovalKey,
        TaskEntity::resourceHref,
        retainedDeleteHrefs,
    )
    val smoothScheduledOpenTasks = rememberSmoothRemoval(
        state.scheduledOpenTasks,
        TaskEntity::smoothRemovalKey,
        TaskEntity::resourceHref,
        retainedDeleteHrefs,
    )
    val smoothCompletedTasks = rememberSmoothRemoval(
        state.completedTasks,
        TaskEntity::smoothRemovalKey,
        TaskEntity::resourceHref,
        retainedDeleteHrefs,
    )
    val smoothSearchTasks = rememberSmoothRemoval(
        state.searchTaskResults,
        { "${it.resourceHref}:${it.occurrenceStartForEdit()}" },
        TaskEntity::resourceHref,
        retainedDeleteHrefs,
    )
    val renderState = state.copy(
        events = smoothEvents.items,
        searchResults = smoothSearchEvents.items,
        searchTaskResults = smoothSearchTasks.items,
        datedTasks = smoothDatedTasks.items,
        inboxTasks = smoothInboxTasks.items,
        scheduledOpenTasks = smoothScheduledOpenTasks.items,
        completedTasks = smoothCompletedTasks.items,
    )
    val exitingResourceHrefs = smoothEvents.exitingResourceHrefs +
        smoothSearchEvents.exitingResourceHrefs +
        smoothDatedTasks.exitingResourceHrefs +
        smoothInboxTasks.exitingResourceHrefs +
        smoothScheduledOpenTasks.exitingResourceHrefs +
        smoothCompletedTasks.exitingResourceHrefs +
        smoothSearchTasks.exitingResourceHrefs
    val problemItems = state.problemItems()
    // Hand the local functions that read `state` to children as lambdas, never as `::` references:
    // a reference to a local function equals every earlier one, so Compose would keep the first
    // instance and its state from the first composition.
    fun showHiddenSaveNotice(collectionHref: String?, kind: HiddenSaveKind) {
        shell.showHiddenSaveNotice(collectionHref, kind, state)
    }

    // Android back navigation: close the topmost open overlay, otherwise step back
    // through the view history (e.g. Day -> Month -> Multiple days), and only let the system
    // close the app when there is nothing left to go back to.
    fun selectCalendarView(viewMode: CalendarViewMode) {
        if (viewMode != state.selectedView) {
            shell.recordViewChange(state.selectedView)
            viewModel.selectView(viewMode)
        }
    }
    fun closeSearch() {
        shell.closeSearch()
        searchScope.launch {
            delay(MotionMedium.toLong())
            if (!shell.searchOpen) viewModel.search.setSearchQuery("")
        }
    }
    BackHandler(enabled = shell.canNavigateBack) {
        shell.navigateBack(closeSearch = ::closeSearch, selectView = viewModel::selectView)
    }

    LaunchedEffect(state.externalLoginUrl) {
        val url = state.externalLoginUrl ?: return@LaunchedEffect
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        viewModel.sources.externalLoginUrlConsumed()
    }
    LaunchedEffect(shell.creationSheet) {
        shell.onCreationSheetClosed()
    }
    LaunchedEffect(shell.detailSheet) {
        shell.onDetailSheetClosed()
    }

    fun openEventCreation(date: LocalDate) {
        shell.openEventCreation(
            schedule = newEventSchedule(date, LocalTime.now(), state.defaultEventDurationMinutes),
            wireframeColor = state.collections.eventDraftColor(state.defaultEventCollectionHref, defaultWireframeColor),
        )
    }

    fun openTaskCreation(date: LocalDate, scheduledForDay: Boolean, useTaskDefaults: Boolean = false) {
        shell.openTaskCreation(
            schedule = newTaskSchedule(
                date = date,
                now = LocalTime.now(),
                scheduledForDay = scheduledForDay,
                useTaskDefaults = useTaskDefaults,
                defaultTaskHasDate = state.defaultTaskHasDate,
                defaultTaskHasTime = state.defaultTaskHasTime,
                defaultEventDurationMinutes = state.defaultEventDurationMinutes,
            ),
            wireframeColor = state.collections.taskDraftColor(state.defaultTaskCollectionHref, defaultWireframeColor),
        )
    }

    LaunchedEffect(shell.settingsOpen) {
        if (shell.settingsOpen) viewModel.sources.refreshAndroidProviderDiagnostics()
    }
    CalendarLaunchEventsEffect(
        events = viewModel.uiEvents,
        onCreateEvent = { openEventCreation(it) },
        onCreateTask = { date, scheduledForDay -> openTaskCreation(date, scheduledForDay) },
        onOpenDetail = shell::openLaunchedDetail,
        onForegroundRecentered = { foregroundRecenterRequest += 1 },
    )

    CompositionLocalProvider(
        LocalPendingMutations provides state.pendingMutationItems,
        LocalExitingResourceHrefs provides exitingResourceHrefs,
    ) {
        CalendarAppScaffold(
            viewModel = viewModel,
            state = state,
            renderState = renderState,
            shell = shell,
            problemItems = problemItems,
            today = today,
            defaultWireframeColor = defaultWireframeColor,
            foregroundRecenterRequest = foregroundRecenterRequest,
            onViewSelected = { selectCalendarView(it) },
            onCreateEvent = { openEventCreation(it) },
            onCreateTask = { date, useTaskDefaults ->
                openTaskCreation(date, scheduledForDay = false, useTaskDefaults = useTaskDefaults)
            },
            onCloseSearch = ::closeSearch,
        )
    }

    ProblemsOverlay(shell = shell, problemItems = problemItems)
    CreationSheetHost(
        viewModel = viewModel,
        state = state,
        shell = shell,
        today = today,
        showHiddenSaveNotice = { href, kind -> showHiddenSaveNotice(href, kind) },
    )
    HiddenSaveNoticeDialog(viewModel = viewModel, state = state, shell = shell)
    CompletedTasksOverlay(viewModel = viewModel, renderState = renderState, shell = shell)
    DetailSheetHost(
        viewModel = viewModel,
        state = state,
        renderState = renderState,
        shell = shell,
        today = today,
    )
    SettingsOverlay(viewModel = viewModel, state = state, shell = shell)
    WelcomeOverlay(
        viewModel = viewModel,
        welcomeCompleted = state.welcomeCompleted,
        onConnectCalendars = shell::openAddCalendarSources,
    )
    CollectionSettingsHost(viewModel = viewModel, state = state, shell = shell)
    RecurringSaveDialogHost(
        viewModel = viewModel,
        shell = shell,
        showHiddenSaveNotice = { href, kind -> showHiddenSaveNotice(href, kind) },
    )
    StartupOverlay(visible = !state.initialDataLoaded)
}
