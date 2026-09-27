package com.kgs.calendar.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kgs.calendar.domain.model.CalendarViewMode
import com.kgs.calendar.ui.editor.EditorSchedulePreview
import com.kgs.calendar.ui.layout.BookPane
import com.kgs.calendar.ui.layout.FoldPosture
import com.kgs.calendar.ui.layout.LocalFoldPosture
import com.kgs.calendar.ui.layout.SheetOrigin
import com.kgs.calendar.ui.layout.bookCalendarWidthPx
import com.kgs.calendar.ui.layout.paneBounds
import com.kgs.calendar.ui.shell.CalendarShellUiState
import com.kgs.calendar.ui.shell.allDaySlotDraftPreview
import com.kgs.calendar.ui.shell.eventDraftColor
import com.kgs.calendar.ui.shell.timelineSlotDraftPreview
import java.time.LocalDate
import kotlin.math.roundToInt

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
    onCreateTask: (date: LocalDate, useTaskDefaults: Boolean, origin: SheetOrigin) -> Unit,
    onCloseSearch: () -> Unit,
) {
    val backgroundBlur by animateDpAsState(
        targetValue = if (shell.createMenuOpen) 8.dp else 0.dp,
        animationSpec = tween(180, easing = MotionStandard),
        label = "createMenuBackgroundBlur",
    )
    val foldPosture = LocalFoldPosture.current
    // Book posture (half-opened, vertical hinge): the calendar left of the hinge and the task sidebar as a
    // permanent pane right of it. The last hinge stays known while the panes animate away after unfolding.
    val bookPosture = foldPosture as? FoldPosture.Book
    var lastBookPosture by remember { mutableStateOf<FoldPosture.Book?>(null) }
    SideEffect { if (bookPosture != null) lastBookPosture = bookPosture }
    val bookLayout = bookPosture ?: lastBookPosture
    val bookProgress by animateFloatAsState(
        targetValue = if (bookPosture != null) 1f else 0f,
        animationSpec = tween(MotionMedium, easing = MotionEmphasized),
        label = "bookLayoutProgress",
    )
    val bookPanesShown = bookLayout != null && bookProgress > 0f
    // The task pane replaces the task drawer, so an open drawer becomes the pane.
    LaunchedEffect(bookPosture != null) {
        if (bookPosture != null) shell.closeTaskDrawer()
    }
    val density = LocalDensity.current
    val quietInteraction = remember { MutableInteractionSource() }
    // The drawer shows how many items "Recently deleted" holds.
    val trash by viewModel.trash.state.collectAsStateWithLifecycle()
    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
        ) {
            val rootWidthPx = constraints.maxWidth.toFloat()
            // The calendar pane: the whole window, or left of the hinge in the book posture.
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .then(
                        if (bookLayout != null && bookPanesShown) {
                            Modifier.width(
                                with(density) { bookCalendarWidthPx(rootWidthPx, bookLayout.hingeLeftPx, bookProgress).toDp() },
                            )
                        } else {
                            Modifier.fillMaxWidth()
                        },
                    ),
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
                    // In the tabletop and book postures the editor is a panel beside the calendar, not a low sheet over it.
                    timelineBottomInset = if (shell.editorWireframeMode && foldPosture == FoldPosture.Normal) {
                        EditorTinyVisibleHeight
                    } else {
                        0.dp
                    },
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
                        onCreateTask(state.defaultFabCreationDate(), true, SheetOrigin.Calendar)
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
                trashCount = trash.entries.size,
                onRecentlyDeleted = shell::openTrash,
                problems = problemItems,
                onProblems = shell::openProblems,
            )
            }
            if (bookLayout != null && bookPanesShown) {
                val pane = bookLayout.paneBounds(BookPane.Right, rootWidthPx)
                Box(
                    modifier = Modifier
                        .offset { IntOffset((pane.leftPx + pane.widthPx * (1f - bookProgress)).roundToInt(), 0) }
                        .width(with(density) { pane.widthPx.toDp() })
                        .fillMaxHeight(),
                ) {
                    TaskPane(
                        state = renderState,
                        onTaskStatusChanged = viewModel.edits::setTaskStatus,
                        onTaskClick = { shell.openTaskDetail(it, SheetOrigin.TaskPane) },
                        onShowCompleted = shell::openCompletedTasks,
                        onCreateTask = {
                            onCreateTask(today, false, SheetOrigin.TaskPane)
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(backgroundBlur),
                    )
                    if (shell.createMenuOpen) {
                        // Like the create menu's own backdrop: a tap beside it closes the menu.
                        Box(
                            Modifier
                                .fillMaxSize()
                                .clickable(
                                    interactionSource = quietInteraction,
                                    indication = null,
                                    onClick = { shell.setCreateMenuExpanded(false) },
                                ),
                        )
                    }
                }
            }
            TaskDrawer(
                visible = shell.taskDrawerOpen,
                state = renderState,
                onDismiss = shell::closeTaskDrawer,
                onTaskStatusChanged = viewModel.edits::setTaskStatus,
                onTaskClick = { shell.openTaskDetail(it) },
                onShowCompleted = shell::openCompletedTasks,
                onCreateTask = {
                    onCreateTask(today, false, SheetOrigin.Calendar)
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
                onTaskClick = { shell.openTaskDetail(it) },
                onClose = onCloseSearch,
                showCalendarWeeks = renderState.showCalendarWeeks,
                firstDayOfWeek = renderState.firstDayOfWeek,
            )
        }
    }
}
