@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.kgs.calendar.ui

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.input.key.key
import androidx.compose.ui.zIndex
import com.kgs.calendar.R
import com.kgs.calendar.domain.model.isMonthSurfaceTaskVisible
import com.kgs.calendar.ui.calendar.MonthStripPageCount
import com.kgs.calendar.ui.calendar.YearStripBase
import com.kgs.calendar.ui.calendar.YearStripPageCount
import com.kgs.calendar.ui.calendar.leadingDaysFrom
import com.kgs.calendar.ui.calendar.monthGridHeight
import com.kgs.calendar.ui.calendar.monthGridRowCount
import com.kgs.calendar.ui.calendar.shortMonthLabel
import com.kgs.calendar.ui.calendar.toMonth
import com.kgs.calendar.ui.calendar.toMonthPage
import com.kgs.calendar.ui.calendar.weekHeaderLabels
import com.kgs.calendar.ui.month.MonthOverviewRowHeight
import com.kgs.calendar.ui.month.monthOverviewDayCircleSize
import com.kgs.calendar.ui.month.monthOverviewFitRowHeight
import com.kgs.calendar.ui.month.MonthGestureAxis
import com.kgs.calendar.ui.month.MonthOverviewGestureReducer
import com.kgs.calendar.ui.month.MonthOverviewGestureState
import com.kgs.calendar.ui.month.MonthOverviewPresentation
import com.kgs.calendar.ui.month.MonthSettleTarget
import com.kgs.calendar.ui.month.MonthStripAxis
import com.kgs.calendar.ui.month.monthOverviewDayChrome
import com.kgs.calendar.ui.month.monthOverviewPresentation
import com.kgs.calendar.ui.time.LocalCalendarTimeSnapshot
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.YearMonth
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt
import kotlin.math.abs


@Composable
internal fun MonthOverview(
    month: YearMonth,
    state: CalendarUiState,
    firstDayOfWeek: DayOfWeek,
    sideBySide: Boolean = false,
    fillHeight: Boolean = false,
    onVerticalDismissDrag: ((Float) -> Unit)? = null,
    onVerticalDismissEnd: (() -> Unit)? = null,
    onDaySelected: (LocalDate) -> Unit,
    onMonthSelected: (YearMonth) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val dragX = remember { Animatable(0f) }
    var displayedMonthText by rememberSaveable { mutableStateOf(month.toString()) }
    val displayedMonth = remember(displayedMonthText) { YearMonth.parse(displayedMonthText) }
    var transitionTargetMonthText by remember { mutableStateOf<String?>(null) }
    val transitionTargetMonth = remember(transitionTargetMonthText) {
        transitionTargetMonthText?.let(YearMonth::parse)
    }
    var transitionDirection by remember { mutableFloatStateOf(1f) }
    var suppressNextExternalMonthAnimation by remember { mutableStateOf(false) }
    var monthViewportWidthPx by remember { mutableFloatStateOf(0f) }
    var monthDragVelocityX by remember { mutableFloatStateOf(0f) }
    var lastMonthDragNanos by remember { mutableStateOf(0L) }
    var monthGestureMoved by remember { mutableStateOf(false) }
    var gestureState by remember { mutableStateOf(MonthOverviewGestureState()) }
    var interruptedSettle by remember { mutableStateOf<MonthSettleTarget?>(null) }
    val gestureReducer = remember { MonthOverviewGestureReducer() }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val locale = LocalAppLocale.current
    val monthFormatter = remember(locale) { DateTimeFormatter.ofPattern("MMM", locale) }
    val calendarTasks = remember(state.datedTasks) {
        state.datedTasks.filter { task ->
            isMonthSurfaceTaskVisible(task.isCompleted, task.status)
        }
    }

    LaunchedEffect(month) {
        if (month.toString() == displayedMonthText) return@LaunchedEffect
        if (suppressNextExternalMonthAnimation || monthViewportWidthPx <= 0f) {
            suppressNextExternalMonthAnimation = false
            displayedMonthText = month.toString()
            dragX.snapTo(0f)
            return@LaunchedEffect
        }
        val direction = if (ChronoUnit.MONTHS.between(displayedMonth, month) >= 0) 1f else -1f
        transitionDirection = direction
        transitionTargetMonthText = month.toString()
        dragX.stop()
        dragX.snapTo(0f)
        dragX.animateTo(
            targetValue = -direction * monthViewportWidthPx,
            animationSpec = tween(230, easing = MotionEmphasized),
        )
        displayedMonthText = month.toString()
        transitionTargetMonthText = null
        dragX.snapTo(0f)
    }

    val presentation = monthOverviewPresentation(sideBySide)
    val gridContent: @Composable (Modifier) -> Unit = { gridModifier ->
        Column(
            modifier = gridModifier
                .testTag("month-overview-grid")
                .pointerInput(monthViewportWidthPx) {
                    detectDragGestures(
                        onDragStart = {
                            settleJob?.cancel()
                            val displayedAtStart = YearMonth.parse(displayedMonthText)
                            val pendingTarget = transitionTargetMonthText?.let(YearMonth::parse)
                            interruptedSettle = pendingTarget?.let { target ->
                                MonthSettleTarget(displayedAtStart, target)
                            }
                            pendingTarget?.let { target ->
                                displayedMonthText = target.toString()
                                transitionTargetMonthText = null
                                suppressNextExternalMonthAnimation = true
                                onMonthSelected(target)
                            }
                            gestureState = MonthOverviewGestureState()
                            monthDragVelocityX = 0f
                            monthGestureMoved = false
                            lastMonthDragNanos = System.nanoTime()
                            scope.launch { dragX.stop(); dragX.snapTo(0f) }
                        },
                        onDrag = { change, dragAmount ->
                            gestureState = gestureReducer.update(
                                delta = dragAmount,
                                touchSlop = viewConfiguration.touchSlop,
                                state = gestureState,
                            )
                            if (gestureState.axis == MonthGestureAxis.Undecided) return@detectDragGestures
                            change.consume()
                            monthGestureMoved = true
                            if (gestureState.axis == MonthGestureAxis.Horizontal) {
                                val now = System.nanoTime()
                                val seconds = ((now - lastMonthDragNanos).coerceAtLeast(1L) / 1_000_000_000f)
                                    .coerceAtLeast(0.001f)
                                monthDragVelocityX = dragAmount.x / seconds
                                lastMonthDragNanos = now
                                scope.launch {
                                    dragX.snapTo(
                                        (dragX.value + dragAmount.x)
                                            .coerceIn(-monthViewportWidthPx, monthViewportWidthPx),
                                    )
                                }
                            } else if (gestureState.axis == MonthGestureAxis.Vertical) {
                                onVerticalDismissDrag?.invoke(dragAmount.y)
                            }
                        },
                        onDragEnd = {
                            if (gestureState.axis == MonthGestureAxis.Vertical) {
                                onVerticalDismissEnd?.invoke()
                                interruptedSettle = null
                                monthGestureMoved = false
                                settleJob = scope.launch {
                                    dragX.animateTo(0f, tween(MotionShort, easing = MotionStandard))
                                }
                            } else {
                                val threshold = (monthViewportWidthPx * 0.18f).coerceAtLeast(32f)
                                val targetOffset = when {
                                    monthDragVelocityX < -620f || dragX.value < -threshold -> -monthViewportWidthPx
                                    monthDragVelocityX > 620f || dragX.value > threshold -> monthViewportWidthPx
                                    else -> 0f
                                }
                                val monthDelta = when {
                                    targetOffset < 0f -> 1L
                                    targetOffset > 0f -> -1L
                                    else -> 0L
                                }
                                val settleAtStart = interruptedSettle
                                val targetMonth = when {
                                    monthDelta == 0L -> null
                                    settleAtStart != null -> gestureReducer.interrupt(
                                        settling = settleAtStart,
                                        deltaMonths = monthDelta,
                                    ).targetMonth
                                    else -> YearMonth.parse(displayedMonthText).plusMonths(monthDelta)
                                }
                                interruptedSettle = null
                                transitionTargetMonthText = targetMonth?.toString()
                                transitionDirection = if (targetOffset < 0f) 1f else -1f
                                settleJob = scope.launch {
                                    dragX.animateTo(targetOffset, tween(MotionMedium, easing = MotionEmphasized))
                                    if (targetMonth != null) {
                                        displayedMonthText = targetMonth.toString()
                                        suppressNextExternalMonthAnimation = true
                                        onMonthSelected(targetMonth)
                                    }
                                    transitionTargetMonthText = null
                                    dragX.snapTo(0f)
                                    monthGestureMoved = false
                                }
                            }
                        },
                        onDragCancel = {
                            if (gestureState.axis == MonthGestureAxis.Vertical) {
                                onVerticalDismissEnd?.invoke()
                            }
                            interruptedSettle = null
                            monthGestureMoved = false
                            settleJob = scope.launch {
                                dragX.animateTo(0f, tween(MotionShort, easing = MotionStandard))
                            }
                        },
                    )
                },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (state.showCalendarWeeks) {
                    Text(
                        text = stringResource(R.string.calendar_week_abbreviation),
                        modifier = Modifier.width(34.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                weekHeaderLabels(firstDayOfWeek, locale).forEach { label ->
                    Text(
                        text = label,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        // In the tabletop posture the overview is exactly the top half: the rows share its height.
                        if (fillHeight) Modifier.weight(1f) else Modifier.height(month.monthGridHeight(firstDayOfWeek)),
                    )
                    .clipToBounds(),
            ) {
                val gridHeight = maxHeight
                val density = LocalDensity.current
                val widthPx = with(density) { maxWidth.toPx() }
                LaunchedEffect(widthPx) { monthViewportWidthPx = widthPx }
                val visibleMonths = transitionTargetMonth?.let { target ->
                    listOf(displayedMonth to 0f, target to transitionDirection)
                } ?: listOf(
                    displayedMonth.minusMonths(1) to -1f,
                    displayedMonth to 0f,
                    displayedMonth.plusMonths(1) to 1f,
                )
                visibleMonths.forEach { (visibleMonth, slotOffset) ->
                    Box(
                        modifier = Modifier
                            .width(maxWidth)
                            .offset { IntOffset((slotOffset * widthPx + dragX.value).roundToInt(), 0) },
                    ) {
                        MonthGrid(
                            month = visibleMonth,
                            markersByDay = monthMarkersFor(
                                visibleMonth,
                                state.events,
                                calendarTasks,
                                state.taskColorMode,
                            ),
                            firstDayOfWeek = firstDayOfWeek,
                            showCalendarWeeks = state.showCalendarWeeks,
                            selectedStart = state.selectedDate,
                            rowHeight = if (fillHeight) {
                                monthOverviewFitRowHeight(gridHeight, visibleMonth.monthGridRowCount(firstDayOfWeek))
                            } else {
                                MonthOverviewRowHeight
                            },
                            daysEnabled = !monthGestureMoved &&
                                abs(dragX.value) < 1f &&
                                transitionTargetMonth == null,
                            onDaySelected = onDaySelected,
                        )
                    }
                }
            }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(25f)
            .background(MaterialTheme.colorScheme.background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            )
            .padding(top = 8.dp, bottom = 8.dp),
    ) {
        when (presentation) {
            MonthOverviewPresentation.Stacked -> Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                gridContent(Modifier.fillMaxWidth().then(if (fillHeight) Modifier.weight(1f) else Modifier))
                Spacer(Modifier.height(8.dp))
                MonthStrip(
                    month = month,
                    formatter = monthFormatter,
                    axis = presentation.monthStripAxis,
                    onMonthSelected = onMonthSelected,
                )
            }

            MonthOverviewPresentation.SideBySide -> Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                gridContent(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
                MonthStrip(
                    month = month,
                    formatter = monthFormatter,
                    axis = presentation.monthStripAxis,
                    onMonthSelected = onMonthSelected,
                    modifier = Modifier
                        .width(218.dp)
                        .fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    markersByDay: Map<LocalDate, MonthDayMarkers>,
    firstDayOfWeek: DayOfWeek,
    showCalendarWeeks: Boolean,
    selectedStart: LocalDate,
    rowHeight: Dp = MonthOverviewRowHeight,
    daysEnabled: Boolean = true,
    onDaySelected: (LocalDate) -> Unit,
) {
    val firstDay = month.atDay(1)
    val leadingEmptyDays = firstDay.leadingDaysFrom(firstDayOfWeek)
    val rowCount = month.monthGridRowCount(firstDayOfWeek)
    val calendarWeekRows = remember(month, firstDayOfWeek) {
        monthCalendarWeekRows(month, firstDayOfWeek)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        repeat(rowCount) { row ->
            Row(Modifier.fillMaxWidth()) {
                if (showCalendarWeeks) {
                    Box(
                        modifier = Modifier.width(34.dp).height(rowHeight),
                        contentAlignment = Alignment.Center,
                    ) {
                        CalendarWeekNumberPill(
                            weekNumber = calendarWeekRows[row].weekNumber,
                            backgroundColor = multiDayCountRailColor(),
                        )
                    }
                }
                repeat(7) { column ->
                    val dayIndex = row * 7 + column - leadingEmptyDays + 1
                    if (dayIndex in 1..month.lengthOfMonth()) {
                        val day = month.atDay(dayIndex)
                        val markers = markersByDay[day]
                        MonthDayCell(
                            day = day,
                            colors = markers?.colors.orEmpty().map(::Color),
                            hasMore = markers?.hasMore == true,
                            isToday = day == LocalCalendarTimeSnapshot.current.today,
                            isSelectedStart = day == selectedStart,
                            enabled = daysEnabled,
                            onClick = { onDaySelected(day) },
                            rowHeight = rowHeight,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(Modifier.weight(1f).height(rowHeight))
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthDayCell(
    day: LocalDate,
    colors: List<Color>,
    hasMore: Boolean,
    isToday: Boolean,
    isSelectedStart: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    rowHeight: Dp = MonthOverviewRowHeight,
    modifier: Modifier = Modifier,
) {
    val background = when {
            isToday -> WarmBrown
            isSelectedStart -> WarmPeach
            else -> Color.Transparent
    }
    val textColor = if (isToday) accentContainerContentColor() else WarmInk
    Column(
        modifier = modifier
            .testTag("month-overview-day-$day")
            .height(rowHeight)
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(monthOverviewDayCircleSize(rowHeight))
                .clip(CircleShape)
                .background(background)
                .then(
                    if (
                        isSelectedStart &&
                        !isToday &&
                        monthOverviewDayChrome.selectedDayBorderWidth > 0.dp
                    ) {
                        Modifier.border(
                            monthOverviewDayChrome.selectedDayBorderWidth,
                            WarmBrown.copy(alpha = 0.58f),
                            CircleShape,
                        )
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = day.dayOfMonth.toString(),
                color = textColor,
                fontSize = 14.sp,
                lineHeight = 16.sp,
                fontWeight = if (isToday || isSelectedStart) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
        Row(
            modifier = Modifier.height(8.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            colors.forEachIndexed { index, color ->
                Box(
                    Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(color)
                        .testTag("month-overview-dot-$day-$index"),
                )
            }
            if (hasMore) {
                Text(
                    "+",
                    modifier = Modifier
                        .offset { IntOffset(0, -1) }
                        .testTag("month-overview-more-$day"),
                    color = WarmInk,
                    fontSize = 8.sp,
                    lineHeight = 8.sp,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MonthStrip(
    month: YearMonth,
    formatter: DateTimeFormatter,
    axis: MonthStripAxis,
    onMonthSelected: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedPage = month.toMonthPage()
    val locale = LocalAppLocale.current
    val density = LocalDensity.current
    val edgeScrollOffsetPx = with(density) { 20.dp.roundToPx() }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (selectedPage - 2).coerceIn(0, MonthStripPageCount - 1),
        initialFirstVisibleItemScrollOffset = edgeScrollOffsetPx,
    )

    LaunchedEffect(selectedPage, edgeScrollOffsetPx) {
        val target = (selectedPage - 2).coerceIn(0, MonthStripPageCount - 1)
        if (listState.firstVisibleItemIndex != target || abs(listState.firstVisibleItemScrollOffset - edgeScrollOffsetPx) > 2) {
            listState.animateScrollToItem(
                index = target,
                scrollOffset = edgeScrollOffsetPx,
            )
        }
    }

    when (axis) {
        MonthStripAxis.Horizontal -> LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            contentPadding = PaddingValues(horizontal = 0.dp),
            modifier = modifier
                .testTag("month-overview-strip")
                .fillMaxWidth()
                .height(44.dp)
                .padding(top = 5.dp, bottom = 5.dp)
                .horizontalEdgeFade(edgeWidth = 8.dp),
        ) {
            items(MonthStripPageCount, key = { it }) { page ->
                val itemMonth = page.toMonth()
                MonthStripItem(
                    itemMonth = itemMonth,
                    label = itemMonth.shortMonthLabel(formatter, locale),
                    selected = itemMonth == month,
                    axis = axis,
                    onClick = { onMonthSelected(itemMonth) },
                )
            }
        }

        MonthStripAxis.Vertical -> LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(5.dp),
            contentPadding = PaddingValues(
                top = 2.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 2.dp,
            ),
            modifier = modifier
                .testTag("month-overview-strip")
                .padding(start = 6.dp, end = 86.dp),
        ) {
            items(MonthStripPageCount, key = { it }) { page ->
                val itemMonth = page.toMonth()
                MonthStripItem(
                    itemMonth = itemMonth,
                    label = itemMonth.shortMonthLabel(formatter, locale),
                    selected = itemMonth == month,
                    axis = axis,
                    onClick = { onMonthSelected(itemMonth) },
                )
            }
        }
    }
}

@Composable
private fun MonthStripItem(
    itemMonth: YearMonth,
    label: String,
    selected: Boolean,
    axis: MonthStripAxis,
    onClick: () -> Unit,
) {
    val itemModifier = Modifier.testTag("month-overview-month-$itemMonth")
    if (axis == MonthStripAxis.Horizontal) {
        Row(
            modifier = itemModifier.height(34.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        ) {
            if (itemMonth.monthValue == 1) {
                Text(
                    itemMonth.year.toString(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
            MonthStripButton(label, selected, onClick)
        }
    } else {
        Column(
            modifier = itemModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (itemMonth.monthValue == 1) {
                Text(
                    itemMonth.year.toString(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
            MonthStripButton(
                label = label,
                selected = selected,
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun MonthStripButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(33.dp),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) WarmPeach else MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                color = WarmInk,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
internal fun YearStrip(
    selectedYear: Int,
    onYearSelected: (Int) -> Unit,
) {
    val selectedIndex = (selectedYear - YearStripBase).coerceIn(0, YearStripPageCount - 1)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (selectedIndex - 2).coerceAtLeast(0))
    LaunchedEffect(selectedIndex) {
        listState.animateScrollToItem((selectedIndex - 2).coerceAtLeast(0))
    }
    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(MaterialTheme.colorScheme.background)
            .horizontalEdgeFade(edgeWidth = 12.dp),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(YearStripPageCount, key = { it }) { index ->
            val year = YearStripBase + index
            val selected = year == selectedYear
            Surface(
                onClick = { onYearSelected(year) },
                modifier = Modifier.height(36.dp),
                shape = RoundedCornerShape(18.dp),
                color = if (selected) WarmPeach else MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                border = if (calendarInformationChrome.yearStripItemsHaveBorders) {
                    androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (selected) WarmBrown.copy(alpha = 0.42f) else WarmLine.copy(alpha = 0.62f),
                    )
                } else {
                    null
                },
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 13.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        year.toString(),
                        color = WarmInk,
                        fontSize = 14.sp,
                        lineHeight = 17.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}
