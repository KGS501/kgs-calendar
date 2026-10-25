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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kgs.calendar.R
import com.kgs.calendar.domain.source.isLocalCollectionHref
import com.kgs.calendar.ui.model.occurrenceStartForEdit
import com.kgs.calendar.ui.shell.CalendarShellUiState
import com.kgs.calendar.domain.source.CalendarSourceGroupKind
import com.kgs.calendar.ui.sources.CalendarGroupPickerDialog
import com.kgs.calendar.ui.sources.avatarColorKey
import com.kgs.calendar.ui.sources.canRestoreImage
import com.kgs.calendar.ui.sources.CalendarGroupSettingsDialog
import com.kgs.calendar.ui.sources.displayName
import com.kgs.calendar.ui.sources.groupOf
import com.kgs.calendar.widget.KgsWidgetKind
import kotlinx.coroutines.delay

// The full-screen pages and dialogs of the shell. KgsCalendarApp composes them in a fixed order
// between the sheets, which decides what draws on top of what.

@Composable
internal fun ProblemsOverlay(
    viewModel: CalendarViewModel,
    shell: CalendarShellUiState,
    problemItems: List<ProblemItem>,
) {
    if (shell.problemsOpen) {
        ProblemsPage(
            problems = problemItems,
            onEventClick = shell::openProblemEventDetail,
            onTaskClick = shell::openProblemTaskDetail,
            onClose = shell::closeProblems,
            onResolveConflict = viewModel.edits::resolveConflict,
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
                val href = currentHiddenSaveCollection.href
                viewModel.settings.setCollectionVisibleInViews(href, true)
                when (currentHiddenSaveNotice.kind) {
                    HiddenSaveKind.Event -> viewModel.settings.setCollectionEventsVisible(href, true)
                    HiddenSaveKind.Task -> viewModel.settings.setCollectionTasksVisible(href, true)
                }
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
            onTaskClick = { shell.openTaskDetail(it) },
            onClose = shell::closeCompletedTasks,
        )
    }
}

// Composed before the detail sheet as well: tapping an item opens its detail over the trash list.
@Composable
internal fun RecentlyDeletedOverlay(
    viewModel: CalendarViewModel,
    state: CalendarUiState,
    shell: CalendarShellUiState,
) {
    if (shell.trashOpen) {
        val trash by viewModel.trash.state.collectAsStateWithLifecycle()
        LaunchedEffect(trash.entries) {
            shell.resolvePendingTrashedDetail(trash.entries)
        }
        RecentlyDeletedPage(
            trash = trash,
            taskColorMode = state.taskColorMode,
            onItemClick = shell::openTrashedDetail,
            onEmptyTrash = viewModel.trash::emptyTrash,
            onDismissNotice = viewModel.trash::dismissNotice,
            onOpened = viewModel.trash::refresh,
            onClose = shell::closeTrash,
        )
    }
}

@Composable
internal fun SettingsOverlay(
    viewModel: CalendarViewModel,
    state: CalendarUiState,
    shell: CalendarShellUiState,
) {
    // "Recently deleted" opens from the settings and covers them; closing it returns there.
    if (shell.settingsOpen && !shell.trashOpen) {
        val trash by viewModel.trash.state.collectAsStateWithLifecycle()
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
            onHapticFeedbackChanged = viewModel.settings::setHapticFeedbackEnabled,
            onMaxVisibleAllDayItemsChanged = viewModel.settings::setMaxVisibleAllDayItems,
            onMultiDaySidebarControlsChanged = viewModel.settings::setMultiDaySidebarControlsEnabled,
            onMultiDayCountChanged = viewModel.settings::setMultiDayCount,
            onWeekViewEnabledChanged = viewModel.settings::setWeekViewEnabled,
            onFullWeekSwipeEnabledChanged = viewModel.settings::setFullWeekSwipeEnabled,
            onFocusTitleOnCreateChanged = viewModel.settings::setFocusTitleOnCreate,
            onFirstDayOfWeekSelected = viewModel.settings::setFirstDayOfWeek,
            onShowCompletedTasksChanged = viewModel.settings::setShowCompletedTasksInCalendar,
            onShowCalendarWeeksChanged = viewModel.settings::setShowCalendarWeeks,
            onDefaultEventDurationChanged = viewModel.settings::setDefaultEventDurationMinutes,
            onDefaultTaskDatesSelected = viewModel.settings::setDefaultTaskDates,
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
            onSyncIntervalChanged = viewModel.settings::setSyncIntervalMinutes,
            onCollectionSettings = shell::editCollection,
            onGroupSettings = shell::editGroup,
            onCreateGroup = viewModel.sources::createGroup,
            onRestoreAccountImage = viewModel.sources::restoreAccountImage,
            trashCount = trash.entries.size,
            onRecentlyDeleted = shell::openTrash,
            onDeleteLocalCalendar = viewModel.sources::deleteLocalCalendar,
            onPickAccountImage = viewModel.sources::setAccountImage,
            onRemoveAccountImage = viewModel.sources::removeAccountImage,
            onAvatarColorSelected = viewModel.sources::setAvatarColor,
            onAddLocalCalendar = viewModel.sources::addLocalCalendar,
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
        var pickingGroup by remember(collection.href) { mutableStateOf(false) }
        KgsModalBottomSheet(
            onDismissRequest = shell::closeCollectionEditor,
            modifier = Modifier.zIndex(80f),
            initialSnap = SheetSnap.Quarter,
            initialContentHeight = collection.estimatedSettingsHeight(),
        ) {
            CollectionSettingsSheet(
                collection = collection,
                visibleInViews = collection.href !in state.hiddenCollectionHrefs,
                eventsVisible = collection.href !in state.collectionVisibility.eventsHiddenIn,
                tasksVisible = collection.href !in state.collectionVisibility.tasksHiddenIn,
                categoriesSynced = state.categorySync.usesCategoriesOf(collection),
                onCategoriesSyncedChanged = { synced ->
                    viewModel.settings.setCollectionCategorySync(collection, synced)
                },
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
                onEventsVisibleChanged = { visible ->
                    viewModel.settings.setCollectionEventsVisible(collection.href, visible)
                },
                onTasksVisibleChanged = { visible ->
                    viewModel.settings.setCollectionTasksVisible(collection.href, visible)
                },
                onDelete = if (collection.canDeleteFromServerForUi()) {
                    {
                        viewModel.sources.deleteCalDavCalendar(collection.href)
                        shell.closeCollectionEditor()
                    }
                } else {
                    null
                },
                groupName = state.calendarGroups.groupOf(collection)?.displayName(),
                onChooseGroup = { pickingGroup = true },
                onClose = shell::closeCollectionEditor,
            )
        }
        if (pickingGroup) {
            CalendarGroupPickerDialog(
                collection = collection,
                groups = state.calendarGroups,
                accounts = state.accounts,
                images = state.sourceImages,
                settings = state.calendarGroupSettings,
                onSelect = { groupKey -> viewModel.sources.moveCollectionToGroup(collection.href, groupKey) },
                onCreate = { name -> viewModel.sources.moveCollectionToNewGroup(collection.href, name) },
                onDismiss = { pickingGroup = false },
            )
        }
    }
}

/** Renames a sidebar group and sets its picture (opened from the sidebar or the source list). */
@Composable
internal fun CalendarGroupDialogHost(
    viewModel: CalendarViewModel,
    state: CalendarUiState,
    shell: CalendarShellUiState,
) {
    val groupKey = shell.editingGroupKey ?: return
    val group = state.calendarGroups.firstOrNull { it.key == groupKey }
    if (group == null) {
        // A group just created is saved a moment later; any other group is gone.
        LaunchedEffect(groupKey) {
            delay(1_000)
            shell.closeGroupEditor()
        }
        return
    }
    val owner = group.owner
    val isCustom = group.kind == CalendarSourceGroupKind.Custom
    CalendarGroupSettingsDialog(
        group = group,
        images = state.sourceImages,
        settings = state.calendarGroupSettings,
        onRename = { name -> viewModel.sources.renameGroup(group.key, name) },
        onPickImage = { uri -> viewModel.sources.setGroupImage(group.key, uri) },
        onRemoveImage = { viewModel.sources.removeGroupImage(group.key) },
        onRestoreImage = owner?.takeIf { it.canRestoreImage(state.sourceImages, state.calendarGroupSettings) }
            ?.let { { viewModel.sources.restoreAccountImage(it.id) } },
        onColorSelected = { color -> viewModel.sources.setAvatarColor(group.avatarColorKey(), color) },
        calendars = if (isCustom) state.collections.filter { it.isVisibleInSettingsCalendarList() } else emptyList(),
        groups = state.calendarGroups,
        onCalendarsChanged = { added, removed ->
            added.forEach { viewModel.sources.moveCollectionToGroup(it, group.key) }
            removed.forEach { viewModel.sources.moveCollectionToGroup(it, null) }
        },
        onDelete = if (isCustom) ({ viewModel.sources.deleteGroup(group.key) }) else null,
        onDismiss = shell::closeGroupEditor,
    )
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
