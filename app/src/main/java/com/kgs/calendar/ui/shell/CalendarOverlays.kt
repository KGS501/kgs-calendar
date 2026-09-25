package com.kgs.calendar.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.kgs.calendar.R
import com.kgs.calendar.domain.source.isLocalCollectionHref
import com.kgs.calendar.ui.model.occurrenceStartForEdit
import com.kgs.calendar.ui.shell.CalendarShellUiState
import com.kgs.calendar.widget.KgsWidgetKind

// The full-screen pages and dialogs of the shell. KgsCalendarApp composes them in a fixed order
// between the sheets, which decides what draws on top of what.

@Composable
internal fun ProblemsOverlay(
    shell: CalendarShellUiState,
    problemItems: List<ProblemItem>,
) {
    if (shell.problemsOpen) {
        ProblemsPage(
            problems = problemItems,
            onEventClick = shell::openProblemEventDetail,
            onTaskClick = shell::openProblemTaskDetail,
            onClose = shell::closeProblems,
        )
    }
}

@Composable
internal fun HiddenSaveNoticeDialog(
    viewModel: CalendarViewModel,
    state: CalendarUiState,
    shell: CalendarShellUiState,
) {
    val currentHiddenSaveNotice = shell.hiddenSaveNotice
    val currentHiddenSaveCollection = currentHiddenSaveNotice?.let { notice ->
        state.collections.firstOrNull { it.href == notice.collectionHref }
    }
    if (currentHiddenSaveNotice != null && currentHiddenSaveCollection == null) {
        LaunchedEffect(currentHiddenSaveNotice) {
            shell.dismissHiddenSaveNotice()
        }
    }
    if (currentHiddenSaveNotice != null && currentHiddenSaveCollection != null) {
        HiddenCalendarCreationDialog(
            collection = currentHiddenSaveCollection,
            itemLabel = stringResource(
                when (currentHiddenSaveNotice.kind) {
                    HiddenSaveKind.Event -> R.string.event
                    HiddenSaveKind.Task -> R.string.task
                },
            ),
            onDismiss = shell::dismissHiddenSaveNotice,
            onUnhide = {
                viewModel.settings.setCollectionVisibleInViews(currentHiddenSaveCollection.href, true)
                shell.dismissHiddenSaveNotice()
            },
        )
    }
}

// Composed before the detail sheet so that tapping a task here lets the detail
// sheet open *over* this full-screen list rather than behind it.
@Composable
internal fun CompletedTasksOverlay(
    viewModel: CalendarViewModel,
    renderState: CalendarUiState,
    shell: CalendarShellUiState,
) {
    if (shell.completedTasksOpen) {
        CompletedTasksView(
            tasks = remember(renderState.taskHierarchyTasks) {
                renderState.taskHierarchyTasks.partitionByRootActivity().inactiveRootTasks
            },
            taskColorMode = renderState.taskColorMode,
            subtasksExpandedByDefault = renderState.subtasksExpandedByDefault,
            onTaskStatusChanged = viewModel.edits::setTaskStatus,
            onTaskClick = shell::openTaskDetail,
            onClose = shell::closeCompletedTasks,
        )
    }
}

@Composable
internal fun SettingsOverlay(
    viewModel: CalendarViewModel,
    state: CalendarUiState,
    shell: CalendarShellUiState,
) {
    if (shell.settingsOpen) {
        SettingsPage(
            state = state,
            initialDestination = shell.settingsStartDestination,
            onViewSelected = viewModel::selectView,
            onThemeSelected = viewModel.settings::setThemeMode,
            onColorModeSelected = viewModel.settings::setColorMode,
            onMonthWidgetThemeSelected = { viewModel.settings.setWidgetThemeMode(KgsWidgetKind.Month, it) },
            onMonthWidgetColorModeSelected = { viewModel.settings.setWidgetColorMode(KgsWidgetKind.Month, it) },
            onAgendaWidgetThemeSelected = { viewModel.settings.setWidgetThemeMode(KgsWidgetKind.Agenda, it) },
            onAgendaWidgetColorModeSelected = { viewModel.settings.setWidgetColorMode(KgsWidgetKind.Agenda, it) },
            onTasksWidgetThemeSelected = { viewModel.settings.setWidgetThemeMode(KgsWidgetKind.Tasks, it) },
            onTasksWidgetColorModeSelected = { viewModel.settings.setWidgetColorMode(KgsWidgetKind.Tasks, it) },
            onDayWidgetThemeSelected = { viewModel.settings.setWidgetThemeMode(KgsWidgetKind.Day, it) },
            onDayWidgetColorModeSelected = { viewModel.settings.setWidgetColorMode(KgsWidgetKind.Day, it) },
            onMultiWidgetThemeSelected = { viewModel.settings.setWidgetThemeMode(KgsWidgetKind.Multi, it) },
            onMultiWidgetColorModeSelected = { viewModel.settings.setWidgetColorMode(KgsWidgetKind.Multi, it) },
            onMultiWidgetMonthPercentChanged = viewModel.settings::setMultiWidgetMonthPercent,
            onTasksWidgetDisplayModeSelected = viewModel.settings::setTasksWidgetDisplayMode,
            onTasksWidgetIncludeOverdueChanged = viewModel.settings::setTasksWidgetIncludeOverdue,
            onTasksWidgetCreateModeSelected = viewModel.settings::setTasksWidgetCreateMode,
            onTasksWidgetSubtaskDefaultModeSelected = viewModel.settings::setTasksWidgetSubtaskDefaultMode,
            onDayWidgetScaleChanged = viewModel.settings::setDayWidgetScalePercent,
            onDayWidgetStartHourChanged = viewModel.settings::setDayWidgetStartHour,
            onDayWidgetStartAtCurrentHourChanged = viewModel.settings::setDayWidgetStartAtCurrentHour,
            onLanguageSelected = viewModel.settings::setLanguageMode,
            onTaskColorModeSelected = viewModel.settings::setTaskColorMode,
            onPriorityAnimationsChanged = viewModel.settings::setPriorityAnimationsEnabled,
            onOverdueSummaryPriorityAnimationChanged = viewModel.settings::setOverdueSummaryPriorityAnimationEnabled,
            onSubtasksExpandedByDefaultChanged = viewModel.settings::setSubtasksExpandedByDefault,
            onAutoLoadMapPreviewsChanged = viewModel.settings::setAutoLoadMapPreviews,
            onMaxVisibleAllDayItemsChanged = viewModel.settings::setMaxVisibleAllDayItems,
            onMultiDaySidebarControlsChanged = viewModel.settings::setMultiDaySidebarControlsEnabled,
            onPortraitMultiDayCountChanged = viewModel.settings::setPortraitMultiDayCount,
            onLandscapeMultiDayCountChanged = viewModel.settings::setLandscapeMultiDayCount,
            onWeekViewEnabledChanged = viewModel.settings::setWeekViewEnabled,
            onFullWeekSwipeEnabledChanged = viewModel.settings::setFullWeekSwipeEnabled,
            onFocusTitleOnCreateChanged = viewModel.settings::setFocusTitleOnCreate,
            onFirstDayOfWeekSelected = viewModel.settings::setFirstDayOfWeek,
            onShowCompletedTasksChanged = viewModel.settings::setShowCompletedTasksInCalendar,
            onShowCalendarWeeksChanged = viewModel.settings::setShowCalendarWeeks,
            onDefaultEventDurationChanged = viewModel.settings::setDefaultEventDurationMinutes,
            onDefaultTaskHasDateChanged = viewModel.settings::setDefaultTaskHasDate,
            onDefaultTaskHasTimeChanged = viewModel.settings::setDefaultTaskHasTime,
            onDefaultEventRemindersChanged = viewModel.settings::setDefaultEventReminderMinutes,
            onDefaultTaskRemindersChanged = viewModel.settings::setDefaultTaskReminderMinutes,
            onTaskStartNotificationsChanged = viewModel.settings::setTaskStartNotificationsEnabled,
            onTaskEndNotificationsChanged = viewModel.settings::setTaskEndNotificationsEnabled,
            onEventStartNotificationsChanged = viewModel.settings::setEventStartNotificationsEnabled,
            onEventEndNotificationsChanged = viewModel.settings::setEventEndNotificationsEnabled,
            onDefaultEventCollectionSelected = viewModel.settings::setDefaultEventCollectionHref,
            onDefaultTaskCollectionSelected = viewModel.settings::setDefaultTaskCollectionHref,
            onEventFieldOrderChanged = viewModel.settings::setEventFieldOrder,
            onTaskFieldOrderChanged = viewModel.settings::setTaskFieldOrder,
            onCollectionsReordered = viewModel.sources::applyCollectionOrder,
            onManualLogin = { serverUrl, username, password, onResult ->
                viewModel.sources.manualLogin(serverUrl, username, password, onResult)
            },
            onBrowserLogin = viewModel.sources::startBrowserLogin,
            onAddReadOnlyCalendar = viewModel.sources::addReadOnlyCalendar,
            onAddAndroidCalendars = viewModel.sources::addAndroidDeviceCalendars,
            onDisabledAndroidProviderCalendarsVisibleChanged = viewModel.sources::setDisabledAndroidProviderCalendarsVisible,
            onUpdateAccount = viewModel.sources::updateAccount,
            onDeleteAccount = viewModel.sources::deleteAccount,
            onCreateCalDavCalendar = viewModel.sources::createCalDavCalendar,
            onSync = viewModel.sources::syncNow,
            onCollectionSettings = shell::editCollection,
            onLocalCalendarEnabledChanged = { enabled ->
                state.collections.firstOrNull { it.href.isLocalCollectionHref() }?.let { local ->
                    viewModel.sources.setCollectionEnabled(local.href, enabled)
                }
            },
            onClose = shell::closeSettings,
        )
    }
}

@Composable
internal fun WelcomeOverlay(
    viewModel: CalendarViewModel,
    welcomeCompleted: Boolean,
    onConnectCalendars: () -> Unit,
) {
    if (!welcomeCompleted) {
        WelcomeScreen(
            onStartFresh = viewModel.settings::completeWelcome,
            onConnectCalendars = {
                viewModel.settings.completeWelcome()
                onConnectCalendars()
            },
        )
    }
}

@Composable
internal fun CollectionSettingsHost(
    viewModel: CalendarViewModel,
    state: CalendarUiState,
    shell: CalendarShellUiState,
) {
    shell.editingCollection?.let { collection ->
        KgsModalBottomSheet(
            onDismissRequest = shell::closeCollectionEditor,
            modifier = Modifier.zIndex(80f),
            initialSnap = SheetSnap.Quarter,
            initialContentHeight = collection.estimatedSettingsHeight(),
        ) {
            CollectionSettingsSheet(
                collection = collection,
                visibleInViews = collection.href !in state.hiddenCollectionHrefs,
                onSave = { name, color ->
                    viewModel.sources.updateCollectionAppearance(collection.href, name, color)
                    shell.closeCollectionEditor()
                },
                onEnabledChanged = { enabled ->
                    viewModel.sources.setCollectionEnabled(collection.href, enabled)
                    shell.setEditingCollectionEnabled(enabled)
                },
                onVisibleInViewsChanged = { visible ->
                    viewModel.settings.setCollectionVisibleInViews(collection.href, visible)
                },
                onDelete = if (collection.canDeleteFromServerForUi()) {
                    {
                        viewModel.sources.deleteCalDavCalendar(collection.href)
                        shell.closeCollectionEditor()
                    }
                } else {
                    null
                },
                onClose = shell::closeCollectionEditor,
            )
        }
    }
}

@Composable
internal fun RecurringSaveDialogHost(
    viewModel: CalendarViewModel,
    shell: CalendarShellUiState,
    showHiddenSaveNotice: (collectionHref: String?, kind: HiddenSaveKind) -> Unit,
) {
    shell.recurringSaveRequest?.let { request ->
        RecurringSaveScopeDialog(
            itemLabel = when (request) {
                is RecurringSaveRequest.Event -> stringResource(R.string.event)
                is RecurringSaveRequest.Task -> stringResource(R.string.task)
            },
            onDismiss = shell::dismissRecurringSave,
            onSaveThis = {
                when (request) {
                    is RecurringSaveRequest.Event -> viewModel.edits.updateEventOccurrence(
                        request.event.resourceHref,
                        request.event.occurrenceStartForEdit(),
                        request.payload,
                    )
                    is RecurringSaveRequest.Task -> viewModel.edits.updateTaskOccurrence(request.task.resourceHref, request.task.occurrenceStartForEdit(), request.payload)
                }
                when (request) {
                    is RecurringSaveRequest.Event -> showHiddenSaveNotice(request.payload.collectionHref, HiddenSaveKind.Event)
                    is RecurringSaveRequest.Task -> showHiddenSaveNotice(request.payload.collectionHref, HiddenSaveKind.Task)
                }
                shell.finishRecurringSave()
            },
            onSaveFollowing = {
                when (request) {
                    is RecurringSaveRequest.Event -> viewModel.edits.updateEventFollowing(
                        request.event.resourceHref,
                        request.event.occurrenceStartForEdit(),
                        request.payload,
                    )
                    is RecurringSaveRequest.Task -> viewModel.edits.updateTaskFollowing(request.task.resourceHref, request.task.occurrenceStartForEdit(), request.payload)
                }
                when (request) {
                    is RecurringSaveRequest.Event -> showHiddenSaveNotice(request.payload.collectionHref, HiddenSaveKind.Event)
                    is RecurringSaveRequest.Task -> showHiddenSaveNotice(request.payload.collectionHref, HiddenSaveKind.Task)
                }
                shell.finishRecurringSave()
            },
            onSaveAll = {
                when (request) {
                    is RecurringSaveRequest.Event -> viewModel.edits.updateEvent(request.event.resourceHref, request.payload)
                    is RecurringSaveRequest.Task -> viewModel.edits.updateTask(request.task.resourceHref, request.payload)
                }
                when (request) {
                    is RecurringSaveRequest.Event -> showHiddenSaveNotice(request.payload.collectionHref, HiddenSaveKind.Event)
                    is RecurringSaveRequest.Task -> showHiddenSaveNotice(request.payload.collectionHref, HiddenSaveKind.Task)
                }
                shell.finishRecurringSave()
            },
        )
    }
}

@Composable
internal fun StartupOverlay(visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier
            .fillMaxSize()
            .zIndex(1000f),
        enter = fadeIn(animationSpec = tween(90, easing = MotionStandard)),
        exit = fadeOut(animationSpec = tween(180, easing = MotionStandardAccelerate)),
    ) {
        StartupDataOverlay()
    }
}

@Composable
private fun StartupDataOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.size(124.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 4.dp,
            )
            Image(
                painter = painterResource(R.drawable.kgs_logo_vector),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier.size(82.dp),
                contentScale = ContentScale.Fit,
            )
        }
    }
}
