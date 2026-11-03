package com.kgs.calendar.ui.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kgs.calendar.domain.model.CalendarViewMode
import com.kgs.calendar.ui.CalendarUiState
import com.kgs.calendar.ui.CalendarViewModel
import com.kgs.calendar.ui.MotionEmphasized
import com.kgs.calendar.ui.MotionMedium
import com.kgs.calendar.ui.MotionShort
import com.kgs.calendar.ui.SettingsDestination
import com.kgs.calendar.ui.TaskPane
import com.kgs.calendar.ui.shell.CalendarShellUiState
import kotlinx.datetime.LocalDate
import com.kgs.calendar.time.*
import kotlinx.datetime.*

/** Below this window width the desktop uses the phone layout: the sidebar would leave the calendar too little room. */
internal const val DesktopFrameMinWidthDp = 760

/**
 * The desktop window: the permanent sidebar on the left, the toolbar above the calendar
 * ([content], which shows the year view as well), and the task pane on the right while it is
 * pinned. The seams between them can be dragged. Sheets and overlays stay above it as on Android.
 */
@Composable
internal fun DesktopFrame(
    viewModel: CalendarViewModel,
    state: CalendarUiState,
    shell: CalendarShellUiState,
    today: LocalDate,
    tasksNeedAttention: Boolean,
    onViewSelected: (CalendarViewMode) -> Unit,
    onCreateEvent: () -> Unit,
    onCreateTask: () -> Unit,
    onCreateTaskInPane: () -> Unit,
    onCloseSearch: () -> Unit,
    content: @Composable () -> Unit,
) {
    val panes = LocalPaneLayout.current
    val yearViewOpen = shell.yearViewOpen
    var searchAnchor by remember { mutableStateOf<Rect?>(null) }
    fun showDate(date: LocalDate) {
        viewModel.selectDate(date)
        shell.requestDate(date)
    }
    Row(Modifier.fillMaxSize()) {
        DesktopSidebar(
            state = state,
            yearViewOpen = yearViewOpen,
            today = today,
            observeRange = viewModel::observeRange,
            onDateSelected = { date ->
                shell.yearViewOpen = false
                showDate(date)
            },
            onCollectionVisibleInViews = viewModel.settings::setCollectionVisibleInViews,
            onCollectionSettings = shell::editCollection,
            onGroupSettings = shell::editGroup,
            onSettings = { shell.openSettings(SettingsDestination.Main) },
            modifier = Modifier.width(panes.sidebarWidthDp.dp),
        )
        PaneSeam(onDrag = panes::dragSidebar, onDragEnd = panes::commit)
        Box(Modifier.weight(1f)) {
        Column(Modifier.fillMaxSize()) {
            DesktopToolbar(
                state = state,
                yearViewOpen = yearViewOpen,
                tasksNeedAttention = tasksNeedAttention,
                tasksPinned = panes.tasksPanePinned,
                searchOpen = shell.searchOpen,
                onSearchBounds = { searchAnchor = it },
                onStep = { direction -> showDate(state.steppedDate(direction, yearViewOpen)) },
                onToday = {
                    viewModel.today()
                    shell.requestDate(LocalDate.now())
                },
                onViewSelected = { view ->
                    shell.yearViewOpen = view == DesktopView.Year
                    when (view) {
                        DesktopView.Day -> onViewSelected(CalendarViewMode.Day)
                        DesktopView.Week -> onViewSelected(CalendarViewMode.ThreeDay)
                        DesktopView.Month -> onViewSelected(CalendarViewMode.Month)
                        DesktopView.Agenda -> onViewSelected(CalendarViewMode.Agenda)
                        DesktopView.Year -> Unit
                    }
                },
                onSearch = shell::openSearch,
                // A pinned task pane is already there: the button puts it away again.
                onTasks = { if (panes.tasksPanePinned) panes.pinTasksPane(false) else shell.openTaskDrawer() },
                onCreateEvent = onCreateEvent,
                onCreateTask = onCreateTask,
            )
            content()
        }
            // The search covers the calendar's column; the sidebars stay usable beside it.
            DesktopSearchPanel(
                visible = shell.searchOpen,
                anchor = searchAnchor,
                viewModel = viewModel,
                state = state,
                shell = shell,
                onClose = onCloseSearch,
            )
        }
        AnimatedVisibility(
            visible = panes.tasksPanePinned,
            enter = expandHorizontally(tween(MotionMedium, easing = MotionEmphasized), expandFrom = Alignment.Start) + fadeIn(tween(MotionMedium)),
            exit = shrinkHorizontally(tween(MotionMedium, easing = MotionEmphasized), shrinkTowards = Alignment.Start) + fadeOut(tween(MotionShort)),
        ) {
            Row(Modifier.fillMaxHeight()) {
                PaneSeam(onDrag = panes::dragTasksPane, onDragEnd = panes::commit)
                TaskPane(
                    state = state,
                    onTaskStatusChanged = viewModel.edits::setTaskStatus,
                    onTaskClick = { shell.openTaskDetail(it) },
                    onShowCompleted = shell::openCompletedTasks,
                    onCreateTask = onCreateTaskInPane,
                    showDivider = false,
                    pinned = true,
                    onPinnedChange = panes::pinTasksPane,
                    completedInline = true,
                    modifier = Modifier.width(panes.tasksPaneWidthDp.dp),
                )
            }
        }
    }
}

/** The year view inside the calendar's view switcher, with the data of the years on screen. */
@Composable
internal fun DesktopYearPane(
    viewModel: CalendarViewModel,
    state: CalendarUiState,
    shell: CalendarShellUiState,
    today: LocalDate,
    morphDay: LocalDate,
    onOpenDay: (LocalDate) -> Unit,
) {
    DesktopYearView(
        selectedDate = state.selectedDate,
        firstDayOfWeek = state.firstDayOfWeek,
        today = today,
        taskColorMode = state.taskColorMode,
        morphDay = morphDay,
        dateRequest = shell.dateRequest,
        scrollStep = shell.scrollStep,
        observeRange = viewModel::observeRange,
        onYearShown = { year ->
            // The toolbar follows the year in view; the day stays, so the other views come back to it.
            if (year != state.selectedDate.year) viewModel.selectDate(state.selectedDate.withYear(year))
        },
        onOpenDay = onOpenDay,
        remembered = shell.yearViewScroll,
        onRemember = { shell.yearViewScroll = it },
    )
}

/**
 * The views [IdleWarmUp] renders once after the start: all the views the toolbar switches between, so
 * that the first visit to each is as smooth as later ones. They only look: none of them changes the
 * selection or the loaded range.
 */
internal fun desktopWarmUpViews(
    viewModel: CalendarViewModel,
    state: CalendarUiState,
    today: LocalDate,
): List<@Composable () -> Unit> = listOf(
    { WarmUpTimeline(state, CalendarViewMode.ThreeDay) },
    { WarmUpTimeline(state, CalendarViewMode.Day) },
    {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            com.kgs.calendar.ui.AgendaList(
                state = state,
                onTaskStatusChanged = { _, _ -> },
                onDetail = {},
                navigationRequest = remember { com.kgs.calendar.ui.agenda.AgendaNavigationRequest(0L, state.selectedDate) },
                onLoadEarlier = {},
                onLoadLater = {},
            )
        }
    },
    {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            com.kgs.calendar.ui.MonthView(
                state = state,
                onMonthChanged = {},
                onOpenDay = {},
                morphDay = state.selectedDate,
                jumpRequest = null,
                onJumpConsumed = {},
                onDetail = {},
            )
        }
    },
    {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            DesktopYearView(
                selectedDate = state.selectedDate,
                firstDayOfWeek = state.firstDayOfWeek,
                today = today,
                taskColorMode = state.taskColorMode,
                morphDay = state.selectedDate,
                dateRequest = null,
                scrollStep = null,
                observeRange = viewModel::observeRange,
                onYearShown = {},
                onOpenDay = {},
                remembered = null,
                onRemember = {},
            )
        }
    },
)

@Composable
private fun WarmUpTimeline(state: CalendarUiState, view: CalendarViewMode) {
    val windowLayout = com.kgs.calendar.ui.layout.currentCalendarWindowLayout()
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        com.kgs.calendar.ui.TimelineView(
            state = state.copy(selectedView = view),
            selectedView = view,
            onDateSelected = {},
            onViewSelected = {},
            onMultiDayCountChanged = {},
            onTaskStatusChanged = { _, _ -> },
            onEventMoved = { _, _, _, _, _ -> },
            onTaskMoved = { _, _, _, _, _ -> },
            onEventMovedAllDay = { _, _, _ -> },
            onTaskMovedAllDay = { _, _, _ -> },
            onSlotSelected = { _, _ -> },
            onAllDaySlotSelected = {},
            draftEvent = null,
            onDraftEventChanged = {},
            onDraftInteraction = {},
            onDraftTap = {},
            timelineBottomInset = 0.dp,
            onDetail = {},
            timeScroll = androidx.compose.foundation.rememberScrollState(),
            hourHeightDp = if (windowLayout.isLandscape) state.landscapeTimelineHourHeightDp else state.portraitTimelineHourHeightDp,
            onHourHeightChange = {},
            initialTimeScrollApplied = false,
            onInitialTimeScrollApplied = {},
            monthMorphDay = state.selectedDate,
            isLandscape = windowLayout.isLandscape,
            landscapeCompactRequested = state.landscapeTimelineCompact,
        )
    }
}
