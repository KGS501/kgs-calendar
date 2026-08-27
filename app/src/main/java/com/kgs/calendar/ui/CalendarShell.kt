@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.kgs.calendar.ui

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.kgs.calendar.R
import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.domain.model.CalendarViewMode
import com.kgs.calendar.ui.agenda.AgendaNavigationRequest
import com.kgs.calendar.ui.calendar.DayStartHour
import com.kgs.calendar.ui.calendar.overviewPanelHeight
import com.kgs.calendar.ui.month.MonthGestureAxis
import com.kgs.calendar.ui.month.MonthOverviewGestureReducer
import com.kgs.calendar.ui.month.MonthOverviewGestureState
import com.kgs.calendar.ui.time.LocalCalendarTimeSnapshot
import com.kgs.calendar.ui.timeline.TimelineOrientationZoom
import com.kgs.calendar.ui.timeline.TimelineOrientationViewportMemory
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

internal data class TimelineToolbarMonthOffset(
    val x: Dp,
    val y: Dp,
)

private const val TimelineZoomPersistenceDebounceMillis = 250L

internal fun timelineToolbarMonthOffset(showCalendarWeeks: Boolean): TimelineToolbarMonthOffset =
    if (showCalendarWeeks) {
        TimelineToolbarMonthOffset(x = (-8).dp, y = 0.dp)
    } else {
        TimelineToolbarMonthOffset(x = 0.dp, y = 0.dp)
    }

@Composable
internal fun CalendarShell(
    state: CalendarUiState,
    onMenu: () -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onViewSelected: (CalendarViewMode) -> Unit,
    onMultiDayCountChanged: (Int) -> Unit,
    onTimelineHourHeightChanged: (Boolean, Float) -> Unit = { _, _ -> },
    onToday: () -> Unit,
    onSearch: () -> Unit,
    onTasks: () -> Unit,
    onTaskStatusChanged: (TaskEntity, String) -> Unit,
    onEventMoved: (String, Long, LocalDate, java.time.LocalTime, java.time.LocalTime) -> Unit,
    onTaskMoved: (String, Long, LocalDate, java.time.LocalTime, java.time.LocalTime) -> Unit,
    onEventMovedAllDay: (String, Long, LocalDate) -> Unit,
    onTaskMovedAllDay: (String, Long, LocalDate) -> Unit,
    onSlotSelected: (LocalDate, java.time.LocalTime) -> Unit,
    onAllDaySlotSelected: (LocalDate) -> Unit,
    draftEvent: DraftEventSelection?,
    onDraftEventChanged: (DraftEventSelection) -> Unit,
    onDraftInteraction: () -> Unit,
    onDraftTap: () -> Unit,
    timelineBottomInset: Dp,
    onDetail: (DetailSheet) -> Unit,
    overdueTasksExpanded: Boolean,
    onOverdueTasksExpandedChange: (Boolean) -> Unit,
    onLoadEarlierAgenda: () -> Unit = {},
    onLoadLaterAgenda: () -> Unit = {},
    timelineViewportMemory: TimelineOrientationViewportMemory? = null,
) {
    val scope = rememberCoroutineScope()
    val density = androidx.compose.ui.platform.LocalDensity.current
    var monthOverviewOpen by rememberSaveable { mutableStateOf(false) }
    var yearStripOpen by rememberSaveable { mutableStateOf(false) }
    var monthOverviewDismissDragPx by remember { mutableFloatStateOf(0f) }
    var monthOverviewGestureClosing by remember { mutableStateOf(false) }
    var overviewMonthText by rememberSaveable { mutableStateOf(YearMonth.from(state.selectedDate).toString()) }
    var monthMorphDayText by rememberSaveable { mutableStateOf(state.selectedDate.toString()) }
    var monthDayTransitionPending by remember { mutableStateOf(false) }
    val overviewMonth = remember(overviewMonthText) { YearMonth.parse(overviewMonthText) }
    val monthMorphDay = remember(monthMorphDayText) { LocalDate.parse(monthMorphDayText) }
    val isMonthView = state.selectedView == CalendarViewMode.Month
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val fallbackTimelineViewportMemory = remember { TimelineOrientationViewportMemory() }
    val viewportMemory = timelineViewportMemory ?: fallbackTimelineViewportMemory
    val portraitTimeScroll = rememberScrollState()
    val landscapeTimeScroll = rememberScrollState()
    val dayTimeScroll = if (isLandscape) landscapeTimeScroll else portraitTimeScroll
    var portraitInitialScrollApplied by remember { mutableStateOf(false) }
    var landscapeInitialScrollApplied by remember { mutableStateOf(false) }
    val dayInitialScrollApplied = if (isLandscape) {
        landscapeInitialScrollApplied
    } else {
        portraitInitialScrollApplied
    }
    var agendaNavigationSerial by rememberSaveable { mutableLongStateOf(0L) }
    var agendaNavigationRequest by remember {
        mutableStateOf(AgendaNavigationRequest(agendaNavigationSerial, state.selectedDate))
    }
    fun requestAgendaNavigation(date: LocalDate) {
        agendaNavigationSerial += 1L
        agendaNavigationRequest = AgendaNavigationRequest(agendaNavigationSerial, date)
    }
    var monthJumpRequest by remember { mutableStateOf<YearMonth?>(null) }
    var handledForegroundRecenterSerial by rememberSaveable { mutableStateOf(0) }
    var landscapeTimelineCompactRequested by rememberSaveable { mutableStateOf(false) }
    var portraitHourHeightDp by rememberSaveable {
        mutableFloatStateOf(state.portraitTimelineHourHeightDp)
    }
    var landscapeHourHeightDp by rememberSaveable {
        mutableFloatStateOf(state.landscapeTimelineHourHeightDp)
    }
    var lastObservedPortraitHourHeightDp by remember {
        mutableFloatStateOf(state.portraitTimelineHourHeightDp)
    }
    var lastObservedLandscapeHourHeightDp by remember {
        mutableFloatStateOf(state.landscapeTimelineHourHeightDp)
    }
    val orientationZoom = TimelineOrientationZoom(
        portraitHourHeightDp = portraitHourHeightDp,
        landscapeHourHeightDp = landscapeHourHeightDp,
    )
    val requestedHourHeightDp = orientationZoom.hourHeightDp(isLandscape)

    LaunchedEffect(state.portraitTimelineHourHeightDp) {
        val localValueWasClean =
            abs(portraitHourHeightDp - lastObservedPortraitHourHeightDp) <= 0.001f
        lastObservedPortraitHourHeightDp = state.portraitTimelineHourHeightDp
        if (localValueWasClean) {
            portraitHourHeightDp = state.portraitTimelineHourHeightDp
        }
    }
    LaunchedEffect(state.landscapeTimelineHourHeightDp) {
        val localValueWasClean =
            abs(landscapeHourHeightDp - lastObservedLandscapeHourHeightDp) <= 0.001f
        lastObservedLandscapeHourHeightDp = state.landscapeTimelineHourHeightDp
        if (localValueWasClean) {
            landscapeHourHeightDp = state.landscapeTimelineHourHeightDp
        }
    }
    LaunchedEffect(portraitHourHeightDp) {
        delay(TimelineZoomPersistenceDebounceMillis)
        if (abs(portraitHourHeightDp - state.portraitTimelineHourHeightDp) > 0.001f) {
            onTimelineHourHeightChanged(false, portraitHourHeightDp)
        }
    }
    LaunchedEffect(landscapeHourHeightDp) {
        delay(TimelineZoomPersistenceDebounceMillis)
        if (abs(landscapeHourHeightDp - state.landscapeTimelineHourHeightDp) > 0.001f) {
            onTimelineHourHeightChanged(true, landscapeHourHeightDp)
        }
    }
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val landscapeHeaderPresentation = timelineHeaderPresentation(
        isLandscape = true,
        landscapeCompactRequested = landscapeTimelineCompactRequested,
        showCalendarWeeks = state.showCalendarWeeks,
    )
    val landscapeMultiDayControlsTopOffset by animateDpAsState(
        targetValue = landscapeHeaderPresentation.multiDayControlsTopOffset,
        animationSpec = tween(340, easing = MotionEmphasized),
        label = "landscapeMultiDayControlsTopOffset",
    )
    val showLandscapeTimelineControl = isLandscape &&
        state.selectedView.group() == CalendarViewGroup.Timeline &&
        !monthOverviewOpen &&
        !monthOverviewGestureClosing
    val showLandscapeMultiDayControls = showLandscapeTimelineControl &&
        state.selectedView == CalendarViewMode.ThreeDay &&
        !state.weekViewEnabled &&
        state.multiDaySidebarControlsEnabled
    val today = LocalCalendarTimeSnapshot.current.today

    LaunchedEffect(state.foregroundRecenterSerial) {
        if (state.foregroundRecenterSerial <= handledForegroundRecenterSerial) return@LaunchedEffect
        handledForegroundRecenterSerial = state.foregroundRecenterSerial
        when (state.selectedView) {
            CalendarViewMode.Agenda -> {
                requestAgendaNavigation(today)
            }
            CalendarViewMode.Month -> monthJumpRequest = YearMonth.from(today)
            else -> Unit
        }
    }

    LaunchedEffect(state.selectedDate) {
        overviewMonthText = YearMonth.from(state.selectedDate).toString()
        if (!monthDayTransitionPending) monthMorphDayText = state.selectedDate.toString()
    }
    LaunchedEffect(isMonthView) {
        if (isMonthView) {
            monthOverviewOpen = false
            monthOverviewGestureClosing = false
            monthOverviewDismissDragPx = 0f
        } else {
            yearStripOpen = false
        }
    }
    val monthOverviewVisible = (monthOverviewOpen || monthOverviewGestureClosing) && !isMonthView
    val monthOverviewExpandedHeight = if (isLandscape) {
        val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        (configuration.screenHeightDp.dp - 58.dp - statusTop).coerceAtLeast(0.dp)
    } else {
        overviewMonth.overviewPanelHeight(state.firstDayOfWeek)
    }
    val monthOverviewHeight by animateDpAsState(
        targetValue = if (monthOverviewVisible) monthOverviewExpandedHeight else 0.dp,
        animationSpec = tween(220, easing = MotionEmphasized),
        label = "monthOverviewHeight",
    )
    val monthOverviewAlpha by animateFloatAsState(
        targetValue = if (monthOverviewVisible) 1f else 0f,
        animationSpec = tween(160, easing = MotionStandard),
        label = "monthOverviewAlpha",
    )
    val monthOverviewDismissRangePx = with(density) { monthOverviewExpandedHeight.toPx() }.coerceAtLeast(1f)
    val currentMonthOverviewDismissRangePx = rememberUpdatedState(monthOverviewDismissRangePx)
    val monthOverviewVisibleHeight = with(density) {
        (monthOverviewHeight.toPx() + monthOverviewDismissDragPx).coerceAtLeast(0f).toDp()
    }
    val monthDismissThresholdPx = with(density) { 58.dp.toPx() }
    val monthDismissProgress = (-monthOverviewDismissDragPx / (monthOverviewDismissRangePx * 0.55f)).coerceIn(0f, 1f)
    fun applyMonthOverviewDismissDrag(deltaY: Float) {
        monthOverviewGestureClosing = false
        monthOverviewDismissDragPx = (monthOverviewDismissDragPx + deltaY)
            .coerceIn(-currentMonthOverviewDismissRangePx.value, 0f)
    }

    fun settleMonthOverviewDismissDrag() {
        val shouldClose = monthOverviewDismissDragPx <= -monthDismissThresholdPx
        val dismissRangePx = currentMonthOverviewDismissRangePx.value
        scope.launch {
            if (shouldClose) monthOverviewGestureClosing = true
            animate(
                initialValue = monthOverviewDismissDragPx,
                targetValue = if (shouldClose) -dismissRangePx else 0f,
                animationSpec = tween(
                    durationMillis = if (shouldClose) 170 else MotionMedium,
                    easing = if (shouldClose) MotionStandardAccelerate else MotionEmphasized,
                ),
            ) { value, _ ->
                monthOverviewDismissDragPx = value
            }
            if (shouldClose) {
                monthOverviewOpen = false
                monthOverviewGestureClosing = false
                monthOverviewDismissDragPx = -dismissRangePx
                delay(240)
                if (!monthOverviewOpen && !monthOverviewGestureClosing) {
                    monthOverviewDismissDragPx = 0f
                }
            }
        }
    }

    // AnimatedContent keeps the outgoing screen alive. Retain the last coherent data snapshot for
    // each expensive view so entering Agenda never makes the outgoing timeline process Agenda's
    // wider range, and leaving Agenda never makes it rebuild against the timeline range.
    val retainedTimelineState = remember { arrayOfNulls<CalendarUiState>(1) }
    val retainedAgendaState = remember { arrayOfNulls<CalendarUiState>(1) }
    val currentGroup = state.selectedView.group()
    val dataSnapshotCoherent = state.loadedDataRange == state.requestedDataRange
    if (dataSnapshotCoherent) {
        when (currentGroup) {
            CalendarViewGroup.Timeline -> retainedTimelineState[0] = state
            CalendarViewGroup.Agenda -> retainedAgendaState[0] = state
            else -> Unit
        }
    }
    val timelineRenderState = retainedTimelineState[0] ?: state.copy(
        events = emptyList(),
        datedTasks = emptyList(),
        loadedDataRange = null,
    )
    val agendaRenderState = retainedAgendaState[0] ?: state.copy(
        events = emptyList(),
        datedTasks = emptyList(),
        loadedDataRange = null,
    )

    Box(
        Modifier
            .fillMaxSize()
            // Calendar chrome owns the solid backing. The toolbar itself is a transparent
            // foreground layer so the calendar-week band can occupy its intentional 10 dp
            // overlap without being painted over by an opaque sibling.
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .monthOverviewTimelineDismissGesture(
                    enabled = monthOverviewOpen && !isMonthView && !isLandscape,
                    onVerticalDrag = ::applyMonthOverviewDismissDrag,
                    onVerticalEnd = ::settleMonthOverviewDismissDrag,
                ),
        ) {
        CalendarToolbar(
            state = state,
            onMenu = {
                if (overdueTasksExpanded) onOverdueTasksExpandedChange(false) else onMenu()
            },
            onToday = {
                if (overdueTasksExpanded) {
                    onOverdueTasksExpandedChange(false)
                } else {
                    if (state.selectedView == CalendarViewMode.Agenda) {
                        requestAgendaNavigation(today)
                    }
                    onToday()
                    if (isMonthView) monthJumpRequest = YearMonth.from(today)
                }
            },
            onSearch = {
                if (overdueTasksExpanded) onOverdueTasksExpandedChange(false) else onSearch()
            },
            onTasks = {
                if (overdueTasksExpanded) onOverdueTasksExpandedChange(false) else onTasks()
            },
            monthOverviewOpen = if (isMonthView) yearStripOpen else monthOverviewOpen,
            showTimelineCompactControl = showLandscapeTimelineControl,
            timelineCompact = landscapeTimelineCompactRequested,
            onTimelineCompactToggle = {
                landscapeTimelineCompactRequested = !landscapeTimelineCompactRequested
            },
            onMonthClick = {
                if (overdueTasksExpanded) {
                    onOverdueTasksExpandedChange(false)
                } else if (isMonthView) {
                    yearStripOpen = !yearStripOpen
                } else {
                    monthOverviewGestureClosing = false
                    monthOverviewDismissDragPx = 0f
                    monthOverviewOpen = !monthOverviewOpen
                }
            },
        )
        AnimatedVisibility(
            visible = isMonthView && yearStripOpen,
            enter = expandVertically(animationSpec = tween(MotionMedium, easing = MotionEmphasized)) + fadeIn(tween(MotionShort)),
            exit = shrinkVertically(animationSpec = tween(MotionMedium, easing = MotionStandardAccelerate)) + fadeOut(tween(MotionShort)),
        ) {
            YearStrip(
                selectedYear = state.selectedDate.year,
                onYearSelected = { year ->
                    onDateSelected(LocalDate.of(year, 1, 1))
                    monthJumpRequest = YearMonth.of(year, 1)
                },
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(monthOverviewVisibleHeight)
                .testTag("calendar-month-overview-container")
                .clipToBounds()
                .graphicsLayer {
                    alpha = monthOverviewAlpha * (1f - monthDismissProgress * 0.35f)
                },
        ) {
            MonthOverview(
                month = overviewMonth,
                state = state,
                firstDayOfWeek = state.firstDayOfWeek,
                isLandscape = isLandscape,
                onVerticalDismissDrag = if (isLandscape) ::applyMonthOverviewDismissDrag else null,
                onVerticalDismissEnd = if (isLandscape) ::settleMonthOverviewDismissDrag else null,
                onDaySelected = { day ->
                    overviewMonthText = YearMonth.from(day).toString()
                    if (state.selectedView == CalendarViewMode.Agenda) {
                        requestAgendaNavigation(day)
                    }
                    onDateSelected(day)
                },
                onMonthSelected = { month ->
                    val selection = monthOverviewSelectionPlan(month, state.selectedView)
                    overviewMonthText = month.toString()
                    selection.agendaScrollTargetDate?.let { targetDate ->
                        requestAgendaNavigation(targetDate)
                    }
                    onDateSelected(selection.selectedDate)
                },
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            SharedTransitionLayout(modifier = Modifier.matchParentSize()) {
                CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                    AnimatedContent(
                        targetState = state.selectedView.group(),
                        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                        transitionSpec = {
                            val timelineMonth =
                                (initialState == CalendarViewGroup.Timeline && targetState == CalendarViewGroup.MonthGrid) ||
                                    (initialState == CalendarViewGroup.MonthGrid && targetState == CalendarViewGroup.Timeline)
                            if (timelineMonth) {
                                fadeIn(animationSpec = tween(MorphDurationMs, easing = MorphEasing)) togetherWith
                                    fadeOut(animationSpec = tween(MorphDurationMs, easing = MorphEasing))
                            } else {
                                (
                                    fadeIn(animationSpec = tween(MotionMedium, delayMillis = 90, easing = MotionStandard)) +
                                        scaleIn(
                                            initialScale = 0.985f,
                                            animationSpec = tween(MotionMedium, delayMillis = 90, easing = MotionStandard),
                                        )
                                    ) togetherWith (
                                    fadeOut(animationSpec = tween(90, easing = MotionStandardAccelerate)) +
                                        scaleOut(
                                            targetScale = 0.995f,
                                            animationSpec = tween(90, easing = MotionStandardAccelerate),
                                        )
                                    )
                            }.using(SizeTransform(clip = false))
                        },
                        label = "calendarViewMorph",
                    ) { group ->
                        val morphScope = this
                        CompositionLocalProvider(LocalMorphAnimatedVisibilityScope provides morphScope) {
                            when (group) {
                                CalendarViewGroup.Timeline -> TimelineView(
                                    state = timelineRenderState,
                                    selectedView = timelineRenderState.selectedView,
                                    onDateSelected = onDateSelected,
                                    onViewSelected = onViewSelected,
                                    onMultiDayCountChanged = onMultiDayCountChanged,
                                    onTaskStatusChanged = onTaskStatusChanged,
                                    onEventMoved = onEventMoved,
                                    onTaskMoved = onTaskMoved,
                                    onEventMovedAllDay = onEventMovedAllDay,
                                    onTaskMovedAllDay = onTaskMovedAllDay,
                                    onSlotSelected = onSlotSelected,
                                    onAllDaySlotSelected = onAllDaySlotSelected,
                                    draftEvent = draftEvent,
                                    onDraftEventChanged = onDraftEventChanged,
                                    onDraftInteraction = onDraftInteraction,
                                    onDraftTap = onDraftTap,
                                    timelineBottomInset = timelineBottomInset,
                                    onDetail = onDetail,
                                    overdueTasksExpanded = overdueTasksExpanded,
                                    onOverdueTasksExpandedChange = onOverdueTasksExpandedChange,
                                    timeScroll = dayTimeScroll,
                                    hourHeightDp = requestedHourHeightDp,
                                    onHourHeightChange = { changed ->
                                        if (isLandscape) {
                                            landscapeHourHeightDp = changed
                                        } else {
                                            portraitHourHeightDp = changed
                                        }
                                    },
                                    onTimeScrollChanged = { scrollPx ->
                                        val hourHeightPx = with(density) {
                                            requestedHourHeightDp.dp.toPx()
                                        }.coerceAtLeast(0.001f)
                                        viewportMemory.updateTopMinute(
                                            isLandscape = isLandscape,
                                            topMinute = DayStartHour * 60f +
                                                scrollPx / hourHeightPx * 60f,
                                        )
                                    },
                                    initialTimeScrollMinute = viewportMemory.topMinute(isLandscape),
                                    initialTimeScrollApplied = dayInitialScrollApplied,
                                    onInitialTimeScrollApplied = {
                                        if (isLandscape) {
                                            landscapeInitialScrollApplied = true
                                        } else {
                                            portraitInitialScrollApplied = true
                                        }
                                    },
                                    monthMorphDay = monthMorphDay,
                                    isLandscape = isLandscape,
                                    landscapeCompactRequested = landscapeTimelineCompactRequested,
                                )
                                CalendarViewGroup.MonthGrid -> MonthView(
                                    state = state,
                                    onMonthChanged = { month ->
                                        if (YearMonth.from(state.selectedDate) != month) onDateSelected(month.atDay(1))
                                    },
                                    onOpenDay = { day ->
                                        if (!monthDayTransitionPending) {
                                            monthDayTransitionPending = true
                                            monthMorphDayText = day.toString()
                                            scope.launch {
                                                withFrameNanos { }
                                                onDateSelected(day)
                                                withFrameNanos { }
                                                onViewSelected(CalendarViewMode.Day)
                                                monthDayTransitionPending = false
                                            }
                                        }
                                    },
                                    morphDay = monthMorphDay,
                                    jumpRequest = monthJumpRequest,
                                    onJumpConsumed = { monthJumpRequest = null },
                                    onDetail = onDetail,
                                )
                                CalendarViewGroup.Agenda -> AgendaList(
                                    state = agendaRenderState,
                                    onTaskStatusChanged = onTaskStatusChanged,
                                    onDetail = onDetail,
                                    navigationRequest = agendaNavigationRequest,
                                    onLoadEarlier = onLoadEarlierAgenda,
                                    onLoadLater = onLoadLaterAgenda,
                                )
                                CalendarViewGroup.Tasks -> TaskInbox(state, onTaskStatusChanged, onDetail)
                            }
                        }
                    }
                }
            }
        }
        }
        AnimatedVisibility(
            visible = showLandscapeMultiDayControls,
            enter = slideInHorizontally(
                initialOffsetX = { -it },
                animationSpec = tween(280, easing = MotionEmphasized),
            ) + fadeIn(animationSpec = tween(180, easing = MotionStandard)),
            exit = slideOutHorizontally(
                targetOffsetX = { -it },
                animationSpec = tween(280, easing = MotionEmphasized),
            ) + fadeOut(animationSpec = tween(180, easing = MotionStandard)),
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(y = statusTop + 58.dp + landscapeMultiDayControlsTopOffset)
                .width(TimeSidebarWidth)
                .height(landscapeHeaderPresentation.multiDayControlsHeight)
                .zIndex(40f),
        ) {
            MultiDayCountControls(
                dayCount = state.multiDayCount,
                onDayCountChanged = onMultiDayCountChanged,
            )
        }
    }
}

@Composable
internal fun Modifier.monthOverviewTimelineDismissGesture(
    enabled: Boolean,
    onVerticalDrag: (Float) -> Unit,
    onVerticalEnd: () -> Unit,
): Modifier {
    val currentVerticalDrag by rememberUpdatedState(onVerticalDrag)
    val currentVerticalEnd by rememberUpdatedState(onVerticalEnd)
    if (!enabled) return this
    return pointerInput(enabled) {
        val reducer = MonthOverviewGestureReducer()
        awaitEachGesture {
            var gesture = MonthOverviewGestureState()
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val dragAmount = event.changes.firstOrNull()?.let { change ->
                    change.position - change.previousPosition
                } ?: Offset.Zero
                if (dragAmount != Offset.Zero) {
                    gesture = reducer.update(
                        delta = dragAmount,
                        touchSlop = viewConfiguration.touchSlop,
                        state = gesture,
                    )
                }
                if (gesture.axis == MonthGestureAxis.Vertical) {
                    event.changes.forEach { it.consume() }
                    currentVerticalDrag(dragAmount.y)
                }
                if (event.changes.none { it.pressed }) {
                    when (gesture.axis) {
                        MonthGestureAxis.Vertical -> currentVerticalEnd()
                        MonthGestureAxis.Undecided -> Unit
                        MonthGestureAxis.Horizontal -> Unit
                    }
                    break
                }
            }
        }
    }
}

@Composable
private fun CalendarToolbar(
    state: CalendarUiState,
    onMenu: () -> Unit,
    onToday: () -> Unit,
    onSearch: () -> Unit,
    onTasks: () -> Unit,
    monthOverviewOpen: Boolean,
    showTimelineCompactControl: Boolean,
    timelineCompact: Boolean,
    onTimelineCompactToggle: () -> Unit,
    onMonthClick: () -> Unit,
) {
    val quietInteraction = remember { MutableInteractionSource() }
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val today = LocalCalendarTimeSnapshot.current.today
    val hasOpenTasks = remember(state.scheduledOpenTasks, state.datedTasks, today) {
        hasTaskToolbarAttention(
            tasks = state.scheduledOpenTasks + state.datedTasks,
            today = today,
            zoneId = ZoneId.systemDefault(),
        )
    }
    val monthOffset = timelineToolbarMonthOffset(
        showCalendarWeeks = state.selectedView.group() == CalendarViewGroup.Timeline &&
            state.showCalendarWeeks,
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("calendar-toolbar")
            .zIndex(30f)
            .height(58.dp + statusTop)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .clickable(interactionSource = quietInteraction, indication = null, onClick = {})
                .padding(start = 4.dp, top = statusTop, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            IconButton(onClick = onMenu, modifier = Modifier.size(42.dp)) {
                Icon(
                    Icons.Default.Menu,
                    contentDescription = androidx.compose.ui.res.stringResource(R.string.menu),
                    tint = WarmInk,
                    modifier = Modifier.size(25.dp),
                )
            }
            Row(
                modifier = Modifier
                    .testTag("calendar-toolbar-month")
                    .offset(x = monthOffset.x, y = monthOffset.y)
                    .clip(RoundedCornerShape(22.dp))
                    .clickable(onClick = onMonthClick)
                    .padding(horizontal = 4.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                AnimatedContent(
                    targetState = when (state.selectedView) {
                        CalendarViewMode.Month -> state.selectedDate.year.toString()
                        CalendarViewMode.Agenda -> androidx.compose.ui.res.stringResource(R.string.agenda)
                        else -> state.selectedDate.format(
                            DateTimeFormatter.ofPattern("MMMM", LocalAppLocale.current),
                        )
                    },
                    transitionSpec = {
                        ((
                            slideInVertically(animationSpec = tween(MotionMedium, easing = MotionStandard)) { it / 3 } +
                                fadeIn(animationSpec = tween(MotionMedium, easing = MotionStandard))
                            ) togetherWith (
                            slideOutVertically(animationSpec = tween(MotionShort, easing = MotionStandardAccelerate)) { -it / 3 } +
                                fadeOut(animationSpec = tween(MotionShort, easing = MotionStandardAccelerate))
                            )).using(SizeTransform(clip = false))
                    },
                    label = "toolbarMonth",
                ) { month ->
                    Text(
                        text = month,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = WarmInk,
                        fontSize = 26.sp,
                        lineHeight = 30.sp,
                    )
                }
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = WarmInk,
                    modifier = Modifier
                        .size(20.dp)
                        .scale(scaleX = 1f, scaleY = if (monthOverviewOpen) -1f else 1f),
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onSearch, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = androidx.compose.ui.res.stringResource(R.string.search),
                    tint = WarmInk,
                    modifier = Modifier.size(24.dp),
                )
            }
            IconButton(onClick = onToday, modifier = Modifier.size(40.dp)) {
                TodayDateIcon(day = LocalCalendarTimeSnapshot.current.today.dayOfMonth, modifier = Modifier.size(24.dp))
            }
            IconButton(onClick = onTasks, modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = androidx.compose.ui.res.stringResource(R.string.tasks),
                        tint = WarmInk,
                        modifier = Modifier.size(24.dp),
                    )
                    if (hasOpenTasks) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 2.dp, y = (-1).dp)
                                .size(6.dp)
                                .clip(CircleShape)
                                .testTag("tasks-open-indicator")
                                .background(WarmBrown),
                        )
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .height(58.dp),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(
                visible = showTimelineCompactControl,
                enter = slideInVertically(
                    initialOffsetY = { -it * 2 },
                    animationSpec = tween(360, easing = MotionEmphasized),
                ) + fadeIn(tween(220, delayMillis = 70, easing = MotionStandard)),
                exit = slideOutVertically(
                    targetOffsetY = { -it * 2 },
                    animationSpec = tween(240, easing = MotionStandardAccelerate),
                ) + fadeOut(tween(150, easing = MotionStandardAccelerate)),
            ) {
                AnimatedContent(
                    targetState = timelineCompact,
                    transitionSpec = {
                        (scaleIn(initialScale = 0.92f, animationSpec = tween(220, easing = MotionEmphasized)) + fadeIn(tween(160))) togetherWith
                            (scaleOut(targetScale = 0.92f, animationSpec = tween(180, easing = MotionStandardAccelerate)) + fadeOut(tween(120)))
                    },
                    label = "landscapeTimelineCompactAction",
                ) { compact ->
                    val actionLabel = androidx.compose.ui.res.stringResource(
                        if (compact) R.string.show_timeline_all_day_section else R.string.hide_timeline_all_day_section,
                    )
                    val pillShape = RoundedCornerShape(percent = 50)
                    val pillBackground = multiDayCountRailColor()
                    val pillContent = if (pillBackground.isDark()) {
                        androidx.compose.ui.graphics.Color.White
                    } else {
                        androidx.compose.ui.graphics.Color(0xFF1C1A18)
                    }
                    Row(
                        modifier = Modifier
                            .testTag("landscapeTimelineCompactToggle")
                            .height(28.dp)
                            .clip(pillShape)
                            .background(pillBackground)
                            .clickable(onClick = onTimelineCompactToggle)
                            .padding(horizontal = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(
                            imageVector = if (compact) Icons.Default.UnfoldMore else Icons.Default.UnfoldLess,
                            contentDescription = actionLabel,
                            tint = pillContent,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = actionLabel,
                            color = pillContent,
                            fontSize = 12.sp,
                            lineHeight = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayDateIcon(day: Int, modifier: Modifier = Modifier) {
    val iconColor = WarmInk
    Box(
        modifier = modifier.drawBehind {
            val stroke = 1.9.dp.toPx()
            drawRoundRect(
                color = iconColor,
                topLeft = Offset(stroke / 2, stroke / 2),
                size = Size(size.width - stroke, size.height - stroke),
                cornerRadius = CornerRadius(4.5.dp.toPx(), 4.5.dp.toPx()),
                style = Stroke(width = stroke),
            )
            drawLine(
                color = iconColor,
                start = Offset(stroke * 2.2f, size.height * 0.28f),
                end = Offset(size.width - stroke * 2.2f, size.height * 0.28f),
                strokeWidth = stroke,
            )
        },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = day.toString(),
            color = WarmInk,
            fontSize = 11.sp,
            lineHeight = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.offset(y = 2.dp),
        )
    }
}

private enum class CalendarViewGroup { Timeline, MonthGrid, Agenda, Tasks }

private fun CalendarViewMode.group(): CalendarViewGroup = when (this) {
    CalendarViewMode.Day, CalendarViewMode.ThreeDay -> CalendarViewGroup.Timeline
    CalendarViewMode.Month -> CalendarViewGroup.MonthGrid
    CalendarViewMode.Agenda -> CalendarViewGroup.Agenda
    CalendarViewMode.Tasks -> CalendarViewGroup.Tasks
}
