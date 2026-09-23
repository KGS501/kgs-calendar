package com.kgs.calendar.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.dp
import com.kgs.calendar.domain.model.CalendarViewMode
import com.kgs.calendar.ui.editor.EditorSchedulePreview
import com.kgs.calendar.ui.shell.CalendarShellUiState
import com.kgs.calendar.ui.shell.allDaySlotDraftPreview
import com.kgs.calendar.ui.shell.eventDraftColor
import com.kgs.calendar.ui.shell.timelineSlotDraftPreview
import java.time.LocalDate

/** The calendar with its create button, drawers and search overlay, blurred behind the create menu. */
@Composable
internal fun CalendarAppScaffold(
    viewModel: CalendarViewModel,
    state: CalendarUiState,
    renderState: CalendarUiState,
    shell: CalendarShellUiState,
    problemItems: List<ProblemItem>,
    today: LocalDate,
    defaultWireframeColor: Int,
    foregroundRecenterRequest: Int,
    onViewSelected: (CalendarViewMode) -> Unit,
    onCreateEvent: (LocalDate) -> Unit,
    onCreateTask: (date: LocalDate, useTaskDefaults: Boolean) -> Unit,
    onCloseSearch: () -> Unit,
) {
    val backgroundBlur by animateDpAsState(
        targetValue = if (shell.createMenuOpen) 8.dp else 0.dp,
        animationSpec = tween(180, easing = MotionStandard),
        label = "createMenuBackgroundBlur",
    )
    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .blur(backgroundBlur),
            ) {
                CalendarShell(
                    state = renderState,
                    onMenu = shell::openMenuDrawer,
                    onDateSelected = viewModel::selectDate,
                    onViewSelected = onViewSelected,
                    onMultiDayCountChanged = viewModel.settings::setMultiDayCount,
                    onTimelineHourHeightChanged = viewModel.settings::setTimelineHourHeight,
                    onToday = viewModel::today,
                    onSearch = shell::openSearch,
                    onTasks = shell::openTaskDrawer,
                    onTaskStatusChanged = viewModel.edits::setTaskStatus,
                    onEventMoved = viewModel.edits::moveTimedEvent,
                    onTaskMoved = viewModel.edits::moveTimedTask,
                    onEventMovedAllDay = viewModel.edits::moveAllDayEvent,
                    onTaskMovedAllDay = viewModel.edits::moveAllDayTask,
                    onSlotSelected = { date, start ->
                        shell.selectDraftSlot(
                            preview = timelineSlotDraftPreview(date, start, state.defaultEventDurationMinutes),
                            wireframeColor = state.collections.eventDraftColor(state.defaultEventCollectionHref, defaultWireframeColor),
                        )
                    },
                    onAllDaySlotSelected = { date ->
                        shell.selectDraftSlot(
                            preview = allDaySlotDraftPreview(date),
                            wireframeColor = state.collections.eventDraftColor(state.defaultEventCollectionHref, defaultWireframeColor),
                        )
                    },
                    draftEvent = shell.draftEventSelection(),
                    onDraftEventChanged = { draft ->
                        shell.moveDraft(EditorSchedulePreview(draft.date, draft.start, draft.end, draft.allDay))
                    },
                    onDraftInteraction = shell::onDraftInteraction,
                    onDraftTap = shell::requestCreationExpand,
                    timelineBottomInset = if (shell.editorWireframeMode) EditorTinyVisibleHeight else 0.dp,
                    onDetail = shell::openDetail,
                    overdueTasksExpanded = shell.overdueTasksExpanded,
                    onOverdueTasksExpandedChange = { shell.overdueTasksExpanded = it },
                    onLoadEarlierAgenda = viewModel::loadEarlierAgenda,
                    onLoadLaterAgenda = viewModel::loadLaterAgenda,
                    timelineViewportMemory = viewModel.timelineViewportMemory,
                    foregroundRecenterRequest = foregroundRecenterRequest,
                )
            }
            AnimatedVisibility(
                visible = shell.creationSheet == null,
                enter = fadeIn(animationSpec = tween(MotionShort, easing = MotionStandard)) +
                    scaleIn(initialScale = 0.92f, animationSpec = tween(MotionMedium, easing = MotionEmphasized)),
                exit = fadeOut(animationSpec = tween(MotionShort, easing = MotionStandardAccelerate)) +
                    scaleOut(targetScale = 0.9f, animationSpec = tween(MotionShort, easing = MotionStandardAccelerate)),
            ) {
                CreateFabMenu(
                    expanded = shell.createMenuOpen,
                    onExpandedChange = shell::setCreateMenuExpanded,
                    onCreateEvent = {
                        onCreateEvent(state.defaultFabCreationDate())
                    },
                    onCreateTask = {
                        onCreateTask(state.defaultFabCreationDate(), true)
                    },
                )
            }
            if (state.isManualSyncing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            CalendarDrawer(
                visible = shell.drawerOpen,
                state = renderState,
                onDismiss = shell::closeDrawer,
                onViewSelected = {
                    onViewSelected(it)
                    shell.closeDrawer()
                },
                onSync = {
                    viewModel.sources.syncNow()
                    shell.closeDrawer()
                },
                onCollectionVisibleInViews = viewModel.settings::setCollectionVisibleInViews,
                onCollectionSettings = shell::editCollection,
                onAppSettings = { shell.openSettings(SettingsDestination.Main) },
                problems = problemItems,
                onProblems = shell::openProblems,
            )
            TaskDrawer(
                visible = shell.taskDrawerOpen,
                state = renderState,
                onDismiss = shell::closeTaskDrawer,
                onTaskStatusChanged = viewModel.edits::setTaskStatus,
                onTaskClick = shell::openTaskDetail,
                onShowCompleted = shell::openCompletedTasks,
                onCreateTask = {
                    onCreateTask(today, false)
                },
            )
            CalendarSearchOverlay(
                visible = shell.searchOpen,
                query = renderState.searchQuery,
                searchMode = renderState.searchMode,
                results = renderState.searchResults,
                taskResults = renderState.searchTaskResults,
                allTasksForHierarchy = renderState.allTasks,
                taskColorMode = renderState.taskColorMode,
                subtasksExpandedByDefault = renderState.subtasksExpandedByDefault,
                onQueryChange = viewModel.search::setSearchQuery,
                onSearchModeChange = viewModel.search::setSearchMode,
                onLoadEarlierOccurrences = viewModel.search::loadEarlierSearchOccurrences,
                onLoadLaterOccurrences = viewModel.search::loadLaterSearchOccurrences,
                onTaskStatusChanged = viewModel.edits::setTaskStatus,
                onEventClick = {
                    shell.openDetail(DetailSheet.Event(it))
                },
                onTaskClick = shell::openTaskDetail,
                onClose = onCloseSearch,
                showCalendarWeeks = renderState.showCalendarWeeks,
                firstDayOfWeek = renderState.firstDayOfWeek,
            )
        }
    }
}
