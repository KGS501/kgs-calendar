@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.kgs.calendar.ui

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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.kgs.calendar.R
import com.kgs.calendar.domain.model.CalendarViewMode
import com.kgs.calendar.ui.calendar.overviewPanelHeight
import com.kgs.calendar.ui.time.LocalCalendarTimeSnapshot
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun CalendarShell(
    state: CalendarUiState,
    onMenu: () -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onViewSelected: (CalendarViewMode) -> Unit,
    onMultiDayCountChanged: (Int) -> Unit,
    onToday: () -> Unit,
    onSearch: () -> Unit,
    onTasks: () -> Unit,
    onTaskStatusChanged: (String, String) -> Unit,
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
    val dayTimeScroll = rememberScrollState()
    var dayHourHeightDp by rememberSaveable { mutableStateOf(DefaultHourRowHeightDp) }
    var dayInitialScrollApplied by rememberSaveable { mutableStateOf(false) }
    var agendaTodayScrollRequest by remember { mutableStateOf(0) }
    var agendaScrollTargetDate by remember { mutableStateOf<LocalDate?>(null) }
    var monthJumpRequest by remember { mutableStateOf<YearMonth?>(null) }
    var handledForegroundRecenterSerial by rememberSaveable { mutableStateOf(0) }
    val today = LocalCalendarTimeSnapshot.current.today

    LaunchedEffect(state.foregroundRecenterSerial) {
        if (state.foregroundRecenterSerial <= handledForegroundRecenterSerial) return@LaunchedEffect
        handledForegroundRecenterSerial = state.foregroundRecenterSerial
        when (state.selectedView) {
            CalendarViewMode.Agenda -> {
                agendaScrollTargetDate = today
                agendaTodayScrollRequest++
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
    val monthOverviewHeight by animateDpAsState(
        targetValue = if (monthOverviewVisible) overviewMonth.overviewPanelHeight(state.firstDayOfWeek) else 0.dp,
        animationSpec = tween(220, easing = MotionEmphasized),
        label = "monthOverviewHeight",
    )
    val monthOverviewAlpha by animateFloatAsState(
        targetValue = if (monthOverviewVisible) 1f else 0f,
        animationSpec = tween(160, easing = MotionStandard),
        label = "monthOverviewAlpha",
    )
    val monthOverviewHeightPx = with(density) { monthOverviewHeight.toPx() }.coerceAtLeast(1f)
    val monthOverviewVisibleHeight = with(density) {
        (monthOverviewHeight.toPx() + monthOverviewDismissDragPx).coerceAtLeast(0f).toDp()
    }
    val monthDismissThresholdPx = with(density) { 58.dp.toPx() }
    val monthDismissProgress = (-monthOverviewDismissDragPx / (monthOverviewHeightPx * 0.55f)).coerceIn(0f, 1f)

    fun applyMonthOverviewDismissDrag(deltaY: Float) {
        monthOverviewGestureClosing = false
        monthOverviewDismissDragPx = (monthOverviewDismissDragPx + deltaY).coerceIn(-monthOverviewHeightPx, 0f)
    }

    fun settleMonthOverviewDismissDrag() {
        val shouldClose = monthOverviewDismissDragPx <= -monthDismissThresholdPx
        scope.launch {
            if (shouldClose) monthOverviewGestureClosing = true
            animate(
                initialValue = monthOverviewDismissDragPx,
                targetValue = if (shouldClose) -monthOverviewHeightPx else 0f,
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
                monthOverviewDismissDragPx = -monthOverviewHeightPx
                delay(240)
                if (!monthOverviewOpen && !monthOverviewGestureClosing) {
                    monthOverviewDismissDragPx = 0f
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        CalendarToolbar(
            state = state,
            onMenu = onMenu,
            onToday = {
                if (state.selectedView == CalendarViewMode.Agenda) {
                    agendaScrollTargetDate = today
                    agendaTodayScrollRequest++
                }
                onToday()
                if (isMonthView) monthJumpRequest = YearMonth.from(today)
            },
            onSearch = onSearch,
            onTasks = onTasks,
            monthOverviewOpen = if (isMonthView) yearStripOpen else monthOverviewOpen,
            onMonthClick = {
                if (isMonthView) {
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
                .clipToBounds()
                .graphicsLayer {
                    alpha = monthOverviewAlpha * (1f - monthDismissProgress * 0.35f)
                },
        ) {
            MonthOverview(
                month = overviewMonth,
                state = state,
                firstDayOfWeek = state.firstDayOfWeek,
                onDaySelected = { day ->
                    overviewMonthText = YearMonth.from(day).toString()
                    if (state.selectedView == CalendarViewMode.Agenda) {
                        agendaScrollTargetDate = day
                        agendaTodayScrollRequest++
                    }
                    onDateSelected(day)
                },
                onMonthSelected = { month ->
                    overviewMonthText = month.toString()
                    onDateSelected(month.atDay(1))
                },
                onMonthOffset = { offset ->
                    val next = overviewMonth.plusMonths(offset)
                    overviewMonthText = next.toString()
                    onDateSelected(next.atDay(1))
                },
                onDismissDrag = ::applyMonthOverviewDismissDrag,
                onDismissDragEnd = ::settleMonthOverviewDismissDrag,
                onDismiss = {
                    monthOverviewGestureClosing = false
                    monthOverviewDismissDragPx = 0f
                    monthOverviewOpen = false
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
                                    state = state,
                                    selectedView = state.selectedView,
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
                                    timeScroll = dayTimeScroll,
                                    hourHeightDp = dayHourHeightDp,
                                    onHourHeightChange = { dayHourHeightDp = it },
                                    initialTimeScrollApplied = dayInitialScrollApplied,
                                    onInitialTimeScrollApplied = { dayInitialScrollApplied = true },
                                    monthMorphDay = monthMorphDay,
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
                                    state,
                                    onTaskStatusChanged,
                                    onDetail,
                                    agendaTodayScrollRequest,
                                    agendaScrollTargetDate,
                                )
                                CalendarViewGroup.Tasks -> TaskInbox(state, onTaskStatusChanged, onDetail)
                            }
                        }
                    }
                }
            }
            if (monthOverviewOpen && !isMonthView) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .zIndex(200f),
                )
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
    onMonthClick: () -> Unit,
) {
    val quietInteraction = remember { MutableInteractionSource() }
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val hasOpenTasks = remember(state.inboxTasks, state.scheduledOpenTasks, state.datedTasks) {
        (state.inboxTasks + state.scheduledOpenTasks + state.datedTasks)
            .distinctBy { it.resourceHref }
            .any { !it.isInactive() }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(30f)
            .height(58.dp + statusTop)
            .background(MaterialTheme.colorScheme.background)
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
                .clip(RoundedCornerShape(22.dp))
                .clickable(onClick = onMonthClick)
                .padding(horizontal = 4.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            AnimatedContent(
                targetState = if (state.selectedView == CalendarViewMode.Month) {
                    state.selectedDate.year.toString()
                } else {
                    state.selectedDate.format(DateTimeFormatter.ofPattern("MMMM", LocalAppLocale.current))
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
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(WarmBrown)
                            .border(1.5.dp, MaterialTheme.colorScheme.background, CircleShape),
                    )
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
