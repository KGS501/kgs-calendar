@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.kgs.calendar.ui

import com.kgs.calendar.ui.LocalAppLocale
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.RippleConfiguration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.pointerInput
import com.kgs.calendar.ui.desktop.LocalPaneLayout
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.input.key.key
import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.data.settings.TaskColorMode
import com.kgs.calendar.domain.event.displayColor
import com.kgs.calendar.domain.event.monthOccurrenceKey
import com.kgs.calendar.domain.model.isMonthSurfaceTaskVisible
import com.kgs.calendar.domain.task.displayColor
import com.kgs.calendar.domain.time.toDate
import com.kgs.calendar.ui.calendar.MonthViewBase
import com.kgs.calendar.ui.calendar.MonthViewPageCount
import com.kgs.calendar.ui.calendar.leadingDaysFrom
import com.kgs.calendar.ui.calendar.toMonthViewPage
import com.kgs.calendar.ui.calendar.weekHeaderLabels
import com.kgs.calendar.ui.month.MonthRowOrderComparator
import com.kgs.calendar.ui.month.MonthRowOrderItem
import com.kgs.calendar.ui.time.LocalCalendarTimeSnapshot
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.YearMonth
import kotlinx.datetime.LocalDate
import kotlin.math.abs
import com.kgs.calendar.ui.theme.calendarTextScale
import com.kgs.calendar.time.*
import kotlinx.datetime.*

@Composable
fun MonthBlock(
    month: YearMonth,
    firstDayOfWeek: DayOfWeek,
    showCalendarWeeks: Boolean,
    eventsByDay: Map<LocalDate, List<EventEntity>>,
    tasksByDay: Map<LocalDate, List<TaskEntity>>,
    taskColorMode: TaskColorMode,
    morphDay: LocalDate,
    onOpenDay: (LocalDate) -> Unit,
    /** The user's zoom of the week rows (Ctrl and the mouse wheel on the desktop); 1 is the usual height. */
    rowScale: Float = 1f,
) {
    val firstDay = month.atDay(1)
    val leading = firstDay.leadingDaysFrom(firstDayOfWeek)
    val length = month.lengthOfMonth()
    val rowCount = ((leading + length + 6) / 7)
    val cards = monthCardMetrics()
    val textScale = calendarTextScale()
    val rowHeight = cards.rowHeight * textScale * rowScale
    // Taller rows show more items before the "+n"; lower ones fewer.
    val visibleLanes = if (rowScale == 1f) {
        MonthVisiblePillLanes
    } else {
        ((rowHeight - cards.cardTop * textScale - cards.moreSize.value.dp * 1.5f) / (cards.cardStep * textScale)).toInt().coerceIn(1, 14)
    }
    Column(Modifier.fillMaxWidth()) {
        // Breathing room + big month name where one month ends and the next begins.
        Text(
            month.atDay(1).format(DateTimeFormatter.ofPattern("MMMM", LocalAppLocale.current)),
            color = WarmBrown,
            fontSize = 22.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(
                start = 12.dp,
                top = 22.dp,
                bottom = if (showCalendarWeeks) 2.dp else 8.dp,
            ),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            repeat(rowCount) { row ->
                val rowStartDate = firstDay.plusDays((row * 7 - leading).toLong())
                val rowEndDate = rowStartDate.plusDays(6)
                val rowDays = (0 until 7).mapNotNull { column ->
                    val dayIndex = row * 7 + column - leading + 1
                    if (dayIndex in 1..length) month.atDay(dayIndex) else null
                }
                Column(Modifier.fillMaxWidth()) {
                    if (showCalendarWeeks) {
                        CalendarWeekLabelSpacer(
                            weekNumber = rowStartDate.calendarWeekNumber(firstDayOfWeek),
                            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                        )
                    }
                    // The cells show their hover tint themselves, so the pills' fades over a hovered day
                    // can fade into the same tint.
                    val hoverSources = remember { List(7) { MutableInteractionSource() } }
                    val hoveredColumns = hoverSources.map { it.collectIsHoveredAsState() }
                    Box(
                        modifier = Modifier.fillMaxWidth().height(rowHeight),
                    ) {
                        Row(
                            modifier = Modifier.matchParentSize(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            for (column in 0 until 7) {
                                val dayIndex = row * 7 + column - leading + 1
                                if (dayIndex in 1..length) {
                                    val day = month.atDay(dayIndex)
                                    MonthDayCard(
                                        day = day,
                                        firstDayOfWeek = firstDayOfWeek,
                                        events = eventsByDay[day].orEmpty(),
                                        tasks = tasksByDay[day].orEmpty(),
                                        taskColorMode = taskColorMode,
                                        morphEnabled = day == morphDay,
                                        showInlinePills = false,
                                        onClick = { onOpenDay(day) },
                                        interactionSource = hoverSources[column],
                                        hovered = { hoveredColumns[column].value },
                                        modifier = Modifier.weight(1f).fillMaxHeight(),
                                    )
                                } else {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                        MonthRowPillOverlay(
                            rowDays = rowDays,
                            rowStartDate = rowStartDate,
                            rowEndDate = rowEndDate,
                            eventsByDay = eventsByDay,
                            tasksByDay = tasksByDay,
                            taskColorMode = taskColorMode,
                            visibleLanes = visibleLanes,
                            hoveredColumn = { hoveredColumns.indexOfFirst { it.value } },
                            modifier = Modifier.matchParentSize(),
                        )
                    }
                }
            }
        }
    }
}


@Composable
internal fun MonthView(
    state: CalendarUiState,
    onMonthChanged: (YearMonth) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    morphDay: LocalDate,
    jumpRequest: YearMonth?,
    onJumpConsumed: () -> Unit,
    onDetail: (DetailSheet) -> Unit,
) {
    val firstDayOfWeek = state.firstDayOfWeek
    val locale = LocalAppLocale.current
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // One LazyColumn item per month. Each month is a self-contained block with a big
    // header and breathing room above it, so month boundaries are obvious.
    val months = remember { (0 until MonthViewPageCount).map { MonthViewBase.plusMonths(it.toLong()) } }
    val initialIndex = remember { YearMonth.from(state.selectedDate).toMonthViewPage() }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val panes = LocalPaneLayout.current
    // Zooming glides between heights, like the year view's.
    val rowScale by animateFloatAsState(
        targetValue = panes.monthRowScale,
        animationSpec = tween(MotionMedium, easing = MotionEmphasized),
        label = "monthRowScale",
    )

    val calendarTasks = remember(state.datedTasks) {
        state.datedTasks.filter { task ->
            isMonthSurfaceTaskVisible(task.isCompleted, task.status)
        }
    }
    val eventsByDay = remember(state.events) { state.events.indexEventsByDay() }
    val tasksByDay = remember(calendarTasks) { calendarTasks.indexTasksByDay() }

    // Recenter the loaded data window on the month at the top of the viewport.
    LaunchedEffect(listState, months) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .map { months.getOrNull(it) ?: YearMonth.from(state.selectedDate) }
            .distinctUntilChanged()
            .collect { onMonthChanged(it) }
    }
    // Consume explicit jump requests (year strip / Today). For long jumps, stage close to the
    // target first, then animate the final few months so Today feels fluid without sweeping the
    // viewport through years of intermediate state.
    LaunchedEffect(jumpRequest) {
        val target = jumpRequest?.toMonthViewPage() ?: return@LaunchedEffect
        val distance = target - listState.firstVisibleItemIndex
        if (abs(distance) > 5) {
            listState.scrollToItem(target - distance.coerceIn(-3, 3))
        }
        listState.animateScrollToItem(target)
        onJumpConsumed()
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
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
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                // Ctrl and the mouse wheel make the week rows taller or lower, like zooming.
                .pointerInput(panes) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.type == PointerEventType.Scroll && event.keyboardModifiers.isCtrlPressed) {
                                val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                                if (delta != 0f) panes.zoomMonthRows(if (delta < 0f) 1 else -1)
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                },
            contentPadding = PaddingValues(bottom = navBottom + 24.dp),
        ) {
            items(months, key = { it.toString() }) { month ->
                MonthBlock(
                    month = month,
                    firstDayOfWeek = firstDayOfWeek,
                    showCalendarWeeks = state.showCalendarWeeks,
                    eventsByDay = eventsByDay,
                    tasksByDay = tasksByDay,
                    taskColorMode = state.taskColorMode,
                    morphDay = morphDay,
                    onOpenDay = onOpenDay,
                    rowScale = rowScale,
                )
            }
        }
    }
}

@Composable
private fun MonthDayCard(
    day: LocalDate,
    firstDayOfWeek: DayOfWeek,
    events: List<EventEntity>,
    tasks: List<TaskEntity>,
    taskColorMode: TaskColorMode,
    morphEnabled: Boolean,
    showInlinePills: Boolean = true,
    onClick: () -> Unit,
    interactionSource: MutableInteractionSource? = null,
    /** Whether the pointer is over the cell, read while drawing. */
    hovered: () -> Boolean = { false },
    modifier: Modifier = Modifier,
) {
    val isToday = day == LocalCalendarTimeSnapshot.current.today
    val cellShape = RoundedCornerShape(12.dp)
    val cellColor = monthCellColor()
    val hoverColor = monthCellHoverColor(cellColor)
    val source = interactionSource ?: remember { MutableInteractionSource() }
    // Keep the visible month cell in the month scene. Only an invisible anchor participates as
    // the source of the container transform. Otherwise Compose lifts the solid cell into the
    // transition overlay immediately, briefly covering row-spanning pills before the entering
    // day surface has faded in. The destination timeline still grows from these exact bounds.
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .morphBounds(
                    "dayblock-$day",
                    enabled = morphEnabled,
                    enter = MorphCellEnter,
                    exit = MorphCellExit,
                ),
        )
        // The ripple shows presses only; the hover tint is the cell's own colour (see hoverColor).
        CompositionLocalProvider(LocalRippleConfiguration provides MonthCellRipple) {
        Column(
            modifier = Modifier
                .matchParentSize()
                .clip(cellShape)
                .drawBehind { drawRect(if (hovered()) hoverColor else cellColor) }
                .then(
                    if (isToday) {
                        Modifier.border(1.dp, WarmBrown, cellShape)
                    } else {
                        Modifier
                    },
                )
                .clickable(interactionSource = source, indication = androidx.compose.foundation.LocalIndication.current, onClick = onClick)
                .padding(horizontal = 3.dp, vertical = 3.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(monthCardMetrics().dayNumberBox * calendarTextScale()),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                day.dayOfMonth.toString(),
                color = if (isToday) WarmBrown else WarmInk,
                fontSize = monthCardMetrics().dayNumberSize,
                lineHeight = monthCardMetrics().dayNumberSize * 1.1f,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
            )
        }
        val pills = buildList {
            val startsMonthRow = day.dayOfMonth == 1 || day.dayOfWeek == firstDayOfWeek
            val nextDay = day.plusDays(1)
            val endsMonthRow = nextDay.month != day.month || nextDay.dayOfWeek == firstDayOfWeek
            events.forEach {
                val starts = it.startsAtMillis.toDate()
                val ends = (it.endsAtMillis - 1).toDate()
                val showTitle = day == starts || (starts.isBefore(day) && startsMonthRow)
                add(
                    MonthPill(
                        uid = it.uid,
                        event = it,
                        title = if (showTitle) it.title else "",
                        color = it.displayColor(),
                        completed = false,
                        eventStatus = it.status,
                        continuesFromPrevious = starts.isBefore(day) && !startsMonthRow,
                        continuesToNext = ends.isAfter(day) && !endsMonthRow,
                    ),
                )
            }
            tasks.forEach {
                val start = it.startAtMillis?.toDate() ?: it.dueAtMillis?.toDate() ?: day
                val end = it.dueAtMillis?.toDate() ?: it.startAtMillis?.toDate() ?: day
                val showTitle = day == start || (start.isBefore(day) && startsMonthRow)
                add(
                    MonthPill(
                        uid = it.uid,
                        task = it,
                        title = if (showTitle) it.title else "",
                        color = it.displayColor(taskColorMode),
                        completed = it.isCompleted,
                        continuesFromPrevious = start.isBefore(day) && !startsMonthRow,
                        continuesToNext = end.isAfter(day) && !endsMonthRow,
                    ),
                )
            }
        }
        val maxPills = MonthVisiblePillLanes
        if (showInlinePills) {
            pills.take(maxPills).forEach { pill -> MonthPillChip(pill, morphEnabled = morphEnabled) }
        }
            if (showInlinePills && pills.size > maxPills) {
                Text(
                    "+${pills.size - maxPills}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.sp,
                    lineHeight = 10.sp,
                    modifier = Modifier.padding(start = 2.dp),
                )
            }
        }
        }
    }
}

@Composable
private fun monthCellColor(): Color = WarmGrid.copy(alpha = if (MaterialTheme.colorScheme.background.isDark()) 0.92f else 0.96f)

@Composable
private fun monthCellHoverColor(cellColor: Color): Color = cellColor.blendWith(WarmInk, 0.07f)

/** Material's ripple without its hover layer (the month cells tint themselves while hovered). */
private val MonthCellRipple = RippleConfiguration(
    rippleAlpha = RippleAlpha(draggedAlpha = 0.16f, focusedAlpha = 0.1f, hoveredAlpha = 0f, pressedAlpha = 0.1f),
)

/** The sizes of the month grid's day cells and item cards: compact on phones, larger on the desktop. */
internal data class MonthCardMetrics(
    val rowHeight: Dp,
    val dayNumberBox: Dp,
    val dayNumberSize: TextUnit,
    val cardTop: Dp,
    val cardHeight: Dp,
    val cardStep: Dp,
    val fontSize: TextUnit,
    val lineHeight: TextUnit,
    val corner: Dp,
    val textPadding: Dp,
    val moreSize: TextUnit,
)

private val PhoneMonthCards = MonthCardMetrics(
    rowHeight = 108.dp, dayNumberBox = 20.dp, dayNumberSize = 12.sp,
    cardTop = 25.dp, cardHeight = 13.dp, cardStep = 15.dp,
    fontSize = 9.sp, lineHeight = 11.sp, corner = 6.dp, textPadding = 4.dp, moreSize = 9.sp,
)

/** On the desktop the cards are as large as the all-day cards of the day views. */
private val DesktopMonthCards = MonthCardMetrics(
    rowHeight = 168.dp, dayNumberBox = 30.dp, dayNumberSize = 15.sp,
    cardTop = 36.dp, cardHeight = 24.dp, cardStep = 27.dp,
    fontSize = 12.sp, lineHeight = 15.sp, corner = 8.dp, textPadding = 7.dp, moreSize = 11.sp,
)

@Composable
internal fun monthCardMetrics(): MonthCardMetrics =
    if (com.kgs.calendar.ui.desktop.LocalCalendarChrome.current == com.kgs.calendar.ui.desktop.CalendarChrome.Desktop) DesktopMonthCards else PhoneMonthCards
private const val MonthVisiblePillLanes = 4

internal data class MonthRowPillCandidate(
    val id: String,
    val uid: String?,
    /** The occurrence this pill shows, for its pending-sync indicator. */
    val event: EventEntity? = null,
    val task: TaskEntity? = null,
    override val title: String,
    val color: Int,
    val completed: Boolean,
    val eventStatus: String?,
    override val start: LocalDate,
    val end: LocalDate,
    override val occurrenceSortMillis: Long,
) : MonthRowOrderItem {
    override val spanDays: Long = ChronoUnit.DAYS.between(start, end).coerceAtLeast(0) + 1
    val isMultiDay: Boolean = spanDays > 1
}

internal data class MonthRowPillSegment(
    val candidate: MonthRowPillCandidate,
    val visualStart: LocalDate,
    val visualEnd: LocalDate,
    val lane: Int,
    val fadeMode: MonthRowFadeMode,
)

internal enum class MonthRowFadeMode {
    None,
    IntoNextDay,
    AtRowEnd,
}

/** A row's pills in their lanes, and per day the items that found no room in the row's lanes. */
internal data class MonthRowPillLayout(
    val segments: List<MonthRowPillSegment>,
    val hiddenByDay: Map<LocalDate, List<MonthRowPillCandidate>>,
)

/**
 * Lays out the items of [rowDays] (a week of the month view, a month of the desktop's year view)
 * in [visibleLanes] lanes; items that break at the row's end fade out there.
 */
internal fun buildMonthRowPillSegments(
    rowDays: List<LocalDate>,
    rowStartDate: LocalDate,
    rowEndDate: LocalDate,
    eventsByDay: Map<LocalDate, List<EventEntity>>,
    tasksByDay: Map<LocalDate, List<TaskEntity>>,
    taskColorMode: TaskColorMode,
    visibleLanes: Int = MonthVisiblePillLanes,
): MonthRowPillLayout {
    val rowStart = rowDays.firstOrNull() ?: return MonthRowPillLayout(emptyList(), emptyMap())
    val rowEnd = rowDays.lastOrNull() ?: rowStart
    val candidates = buildList {
        rowDays
            .flatMap { eventsByDay[it].orEmpty() }
            .distinctBy { it.monthOccurrenceKey() }
            .forEach { event ->
                val start = event.startsAtMillis.toDate()
                val end = (event.endsAtMillis - 1).toDate().coerceAtLeast(start)
                if (end < rowStart || start > rowEnd) return@forEach
                add(
                    MonthRowPillCandidate(
                        id = "event:${event.monthOccurrenceKey()}",
                        uid = event.uid,
                        event = event,
                        title = event.title,
                        color = event.displayColor(),
                        completed = false,
                        eventStatus = event.status,
                        start = start,
                        end = end,
                        occurrenceSortMillis = event.startsAtMillis,
                    ),
                )
            }
        rowDays
            .flatMap { tasksByDay[it].orEmpty() }
            .distinctBy { it.resourceHref ?: it.uid }
            .forEach { task ->
                val start = task.startAtMillis?.toDate() ?: task.dueAtMillis?.toDate() ?: rowStart
                val end = (task.dueAtMillis?.toDate() ?: task.startAtMillis?.toDate() ?: start).coerceAtLeast(start)
                if (end < rowStart || start > rowEnd) return@forEach
                add(
                    MonthRowPillCandidate(
                        id = "task:${task.resourceHref ?: task.uid}",
                        uid = task.uid,
                        task = task,
                        title = task.title,
                        color = task.displayColor(taskColorMode),
                        completed = task.isCompleted,
                        eventStatus = null,
                        start = start,
                        end = end,
                        occurrenceSortMillis = task.startAtMillis ?: task.dueAtMillis ?: Long.MAX_VALUE,
                    ),
                )
            }
    }

    data class MutableSegment(
        val candidate: MonthRowPillCandidate,
        val visualStart: LocalDate,
        var visualEnd: LocalDate,
        val actualEnd: LocalDate,
        val lane: Int,
        var fadeMode: MonthRowFadeMode = MonthRowFadeMode.None,
    )

    val laneSegments = mutableListOf<MutableList<MutableSegment>>()
    val segments = mutableListOf<MutableSegment>()
    // Reserve the visible lanes for the longest spans first. A chronological greedy pass lets
    // small items early in the week occupy all three visible lanes and can hide a long event that
    // starts later in the same row (for example a holiday beginning on Monday). Duration-first
    // packing keeps the visually important continuous bars visible while still stacking unrelated
    // same-day items in additional lanes.
    val sortedCandidates = candidates.sortedWith(MonthRowOrderComparator)
    sortedCandidates.forEach { candidate ->
        val visualStart = maxOf(candidate.start, rowStart)
        val visualEnd = minOf(candidate.end, rowEnd)
        fun doesNotOverlap(existing: MutableSegment): Boolean =
            existing.actualEnd < candidate.start ||
                candidate.end < existing.candidate.start

        fun isMultiDayJunction(existing: MutableSegment): Boolean =
            existing.candidate.isMultiDay &&
                candidate.isMultiDay &&
                (
                    existing.candidate.start < candidate.start &&
                        existing.actualEnd == candidate.start &&
                        candidate.start in rowStart..rowEnd
                    ) ||
                (
                    candidate.start < existing.candidate.start &&
                        candidate.end == existing.candidate.start &&
                        existing.candidate.start in rowStart..rowEnd
                    )

        fun canPlaceFully(lane: List<MutableSegment>): Boolean =
            lane.all(::doesNotOverlap)

        fun canShareLaneWithFade(lane: List<MutableSegment>): Boolean =
            lane.all { existing -> doesNotOverlap(existing) || isMultiDayJunction(existing) }

        val fullLane = laneSegments.indexOfFirst(::canPlaceFully)
        val visibleFadeLane = if (candidate.isMultiDay && laneSegments.size >= visibleLanes) {
            laneSegments.indexOfFirst { lane -> canShareLaneWithFade(lane) }
                .takeIf { it in 0 until visibleLanes }
                ?: -1
        } else {
            -1
        }
        val targetLane = when {
            fullLane in 0 until visibleLanes -> fullLane
            laneSegments.size < visibleLanes -> laneSegments.size
            visibleFadeLane >= 0 -> visibleFadeLane
            fullLane >= 0 -> fullLane
            else -> laneSegments.size
        }
        val segment = MutableSegment(
            candidate = candidate,
            visualStart = visualStart,
            visualEnd = visualEnd,
            actualEnd = candidate.end,
            lane = targetLane,
        )
        if (targetLane < laneSegments.size) {
            laneSegments[targetLane].forEach { existing ->
                when {
                    isMultiDayJunction(existing) &&
                        existing.candidate.start < candidate.start &&
                        existing.actualEnd == candidate.start -> {
                        existing.visualEnd = minOf(existing.visualEnd, candidate.start.minusDays(1))
                        existing.fadeMode = MonthRowFadeMode.IntoNextDay
                    }
                    isMultiDayJunction(existing) &&
                        candidate.start < existing.candidate.start &&
                        candidate.end == existing.candidate.start -> {
                        segment.visualEnd = minOf(segment.visualEnd, existing.candidate.start.minusDays(1))
                        segment.fadeMode = MonthRowFadeMode.IntoNextDay
                    }
                }
            }
            laneSegments[targetLane] += segment
        } else {
            laneSegments += mutableListOf(segment)
        }
        segments += segment
    }
    val nextRowFirstDay = rowEnd.plusDays(1)
    val startsOnNextRowFirstDay =
        eventsByDay[nextRowFirstDay].orEmpty().any { it.startsAtMillis.toDate() == nextRowFirstDay } ||
            tasksByDay[nextRowFirstDay].orEmpty().any {
                val start = it.startAtMillis?.toDate() ?: it.dueAtMillis?.toDate()
                start == nextRowFirstDay
            }
    if (startsOnNextRowFirstDay) {
        segments
            .filter { it.actualEnd == nextRowFirstDay && it.visualEnd == rowEnd && !it.visualEnd.isBefore(it.visualStart) }
            .forEach { it.fadeMode = MonthRowFadeMode.AtRowEnd }
    }

    val visibleSegments = segments
        .filter { it.lane < visibleLanes && !it.visualEnd.isBefore(it.visualStart) }
        .map {
            MonthRowPillSegment(
                candidate = it.candidate,
                visualStart = it.visualStart,
                visualEnd = it.visualEnd,
                lane = it.lane,
                fadeMode = it.fadeMode,
            )
        }
    val hiddenByDay = rowDays.associateWith { day ->
        val fadeRepresentedIds = segments
            .filter { it.fadeMode != MonthRowFadeMode.None && it.actualEnd == day }
            .map { it.candidate.id }
            .toSet()
        val visibleIds = visibleSegments.filter { day in it.visualStart..it.visualEnd }.mapTo(hashSetOf()) { it.candidate.id }
        sortedCandidates.filter { day in it.start..it.end && it.id !in fadeRepresentedIds && it.id !in visibleIds }
    }.filterValues { it.isNotEmpty() }
    return MonthRowPillLayout(visibleSegments, hiddenByDay)
}

@Composable
private fun MonthRowPillOverlay(
    rowDays: List<LocalDate>,
    rowStartDate: LocalDate,
    rowEndDate: LocalDate,
    eventsByDay: Map<LocalDate, List<EventEntity>>,
    tasksByDay: Map<LocalDate, List<TaskEntity>>,
    taskColorMode: TaskColorMode,
    visibleLanes: Int = MonthVisiblePillLanes,
    /** The column (0-6) under the pointer, or -1; read while drawing. */
    hoveredColumn: () -> Int = { -1 },
    modifier: Modifier = Modifier,
) {
    if (rowDays.isEmpty()) return
    val cellColor = monthCellColor()
    val hoverColor = monthCellHoverColor(cellColor)
    val (segments, hiddenByDay) = remember(rowDays, rowStartDate, rowEndDate, eventsByDay, tasksByDay, taskColorMode, visibleLanes) {
        buildMonthRowPillSegments(rowDays, rowStartDate, rowEndDate, eventsByDay, tasksByDay, taskColorMode, visibleLanes = visibleLanes)
    }.let { it.segments to it.hiddenByDay.mapValues { (_, items) -> items.size } }
    BoxWithConstraints(modifier = modifier) {
        val spacing = 4.dp
        val cellWidth = (maxWidth - spacing * 6) / 7f
        val step = cellWidth + spacing
        // The pills grow with their text, below the day number.
        val textScale = calendarTextScale()
        val cards = monthCardMetrics()
        val pillTop = cards.cardTop * textScale
        val pillHeight = cards.cardHeight * textScale
        val laneStep = cards.cardStep * textScale

        segments.forEach { segment ->
            val geometry = monthRowPillGeometry(segment, rowStartDate, columns = 7, cellWidth = cellWidth, spacing = spacing, rowWidth = maxWidth)
            MonthRowPillChip(
                segment = segment,
                leftSolidStart = geometry.leftSolidStart,
                rightSolidEnd = geometry.rightSolidEnd,
                fontSize = cards.fontSize,
                lineHeight = cards.lineHeight,
                corner = cards.corner,
                textPadding = cards.textPadding,
                fadeBase = cellColor,
                // Over a hovered day, the pill fades into the day's hover tint.
                fadeBaseOverlay = {
                    val column = hoveredColumn()
                    if (column >= 0) {
                        val left = (step * column - geometry.x).toPx()
                        drawRect(hoverColor, topLeft = Offset(left, 0f), size = Size(cellWidth.toPx(), size.height))
                    }
                },
                modifier = Modifier
                    .offset(x = geometry.x, y = pillTop + laneStep * segment.lane)
                    .width(geometry.width)
                    .height(pillHeight),
            )
        }
        hiddenByDay.forEach { (day, count) ->
            val column = ChronoUnit.DAYS.between(rowStartDate, day).toInt()
            if (column !in 0..6) return@forEach
            Text(
                "+$count",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = cards.moreSize,
                lineHeight = cards.moreSize * 1.15f,
                modifier = Modifier.offset(x = step * column + 6.dp, y = pillTop + laneStep * visibleLanes),
            )
        }
    }
}

internal data class MonthRowPillGeometry(val x: Dp, val width: Dp, val leftSolidStart: Float, val rightSolidEnd: Float)

/**
 * Where a pill of a row of [columns] cells starting at [rowStartDate] goes, and which part of it
 * is solid: pills that continue beyond the row fade at that end, and a pill handing over to
 * another one in the same lane fades into the next day.
 */
internal fun monthRowPillGeometry(
    segment: MonthRowPillSegment,
    rowStartDate: LocalDate,
    columns: Int,
    cellWidth: Dp,
    spacing: Dp,
    rowWidth: Dp,
    inset: Dp = 3.dp,
): MonthRowPillGeometry {
    val step = cellWidth + spacing
    val startColumn = ChronoUnit.DAYS.between(rowStartDate, segment.visualStart).toInt().coerceIn(0, columns - 1)
    val endColumn = ChronoUnit.DAYS.between(rowStartDate, segment.visualEnd).toInt().coerceIn(startColumn, columns - 1)
    val span = endColumn - startColumn + 1
    val x = step * startColumn + inset
    val bodyWidth = cellWidth * span + spacing * (span - 1) - inset * 2
    val availableTailWidth = (rowWidth - x - bodyWidth).coerceAtLeast(0.dp)
    val tailWidth = when (segment.fadeMode) {
        MonthRowFadeMode.IntoNextDay -> minOf(cellWidth * 0.34f + spacing, availableTailWidth)
        else -> 0.dp
    }
    val width = bodyWidth + tailWidth
    val leftSolidStart = if (segment.candidate.start < segment.visualStart && width > 0.dp) {
        val fadeWidth = minOf(cellWidth * 0.30f, width)
        (fadeWidth.value / width.value).coerceIn(0.02f, 0.36f)
    } else {
        0f
    }
    val rightSolidEnd = when {
        segment.fadeMode == MonthRowFadeMode.IntoNextDay && width > 0.dp ->
            (bodyWidth.value / width.value).coerceIn(0.05f, 0.98f)
        (segment.fadeMode == MonthRowFadeMode.AtRowEnd || segment.candidate.end > segment.visualEnd) && bodyWidth > 0.dp -> {
            val fadeWidth = minOf(cellWidth * 0.34f, bodyWidth)
            ((bodyWidth - fadeWidth).value / width.value).coerceIn(leftSolidStart + 0.04f, 0.98f)
        }
        else -> 1f
    }
    return MonthRowPillGeometry(x, width, leftSolidStart, rightSolidEnd)
}

@Composable
internal fun MonthRowPillChip(
    segment: MonthRowPillSegment,
    leftSolidStart: Float,
    rightSolidEnd: Float,
    modifier: Modifier = Modifier,
    /** Painted under a fading pill so that what is behind it doesn't show through; null for none. */
    fadeBase: Color? = monthCellColor(),
    /** Drawn over [fadeBase], within the pill's shape, e.g. a hovered day's tint. */
    fadeBaseOverlay: (DrawScope.() -> Unit)? = null,
    fontSize: TextUnit = 9.sp,
    lineHeight: TextUnit = 11.sp,
    corner: Dp = 6.dp,
    textPadding: Dp = 4.dp,
) {
    val candidate = segment.candidate
    val bg = Color(candidate.color)
    val tentative = candidate.eventStatus.equals("TENTATIVE", ignoreCase = true)
    val cancelled = candidate.eventStatus.equals("CANCELLED", ignoreCase = true)
    val fill = when {
        cancelled -> bg.greyedOut(0.58f).copy(alpha = 0.34f)
        candidate.completed -> bg.greyedOut(0.58f).copy(alpha = 0.46f)
        tentative -> Color.Transparent
        else -> bg
    }
    val textColor = when {
        tentative && CurrentDarkPalette -> Color.White
        tentative -> Color(0xFF17202A)
        bg.isDark() && !cancelled && !candidate.completed -> Color.White
        else -> Color(0xFF1C1A18)
    }
    val hasEdgeFade = leftSolidStart > 0.005f || rightSolidEnd < 0.995f
    val brush = if (hasEdgeFade) {
        val start = leftSolidStart.coerceIn(0f, 0.45f)
        val end = rightSolidEnd.coerceIn((start + 0.04f).coerceAtMost(0.96f), 1f)
        val leftTransparentEnd = (start * 0.18f).coerceIn(0f, start)
        val rightTransparentStart = (end + (1f - end) * 0.78f).coerceIn(end, 1f)
        Brush.horizontalGradient(
            0f to fill.copy(alpha = 0f),
            leftTransparentEnd to fill.copy(alpha = 0f),
            start to fill,
            end to fill,
            rightTransparentStart to fill.copy(alpha = 0f),
            1f to fill.copy(alpha = 0f),
        )
    } else {
        Brush.horizontalGradient(0f to fill, 1f to fill)
    }
    BoxWithConstraints(
        modifier = modifier
            .then(if (hasEdgeFade && !tentative && fadeBase != null) Modifier.background(fadeBase, RoundedCornerShape(corner)) else Modifier)
            .then(
                if (hasEdgeFade && !tentative && fadeBase != null && fadeBaseOverlay != null) {
                    Modifier.drawBehind {
                        val radius = CornerRadius(corner.toPx())
                        clipPath(Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, radius)) }) { fadeBaseOverlay() }
                    }
                } else {
                    Modifier
                },
            )
            .background(brush, RoundedCornerShape(corner))
            .then(if (tentative) Modifier.dashedBorder(bg.copy(alpha = 0.9f), corner) else Modifier),
    ) {
        val titleStartPadding = textPadding + maxWidth * leftSolidStart
        Text(
            candidate.title,
            color = textColor.copy(alpha = if (cancelled) 0.54f else if (candidate.completed) 0.64f else 1f),
            fontSize = fontSize,
            lineHeight = lineHeight,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            fontWeight = if (fontSize >= 11.sp) FontWeight.SemiBold else FontWeight.Medium,
            textDecoration = if (cancelled) TextDecoration.LineThrough else null,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = titleStartPadding, end = textPadding)
                // Where the pill fades into the next day (a following item, or the row's end), the
                // title fades out before that point instead of running into what comes next.
                .then(if (rightSolidEnd < 0.995f) Modifier.fadeTextBefore(maxWidth * rightSolidEnd - titleStartPadding) else Modifier),
        )
        PendingMutationBadge(candidate.event, candidate.task, Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-2).dp))
    }
}

/** Fades content out over the [fadeWidth] before [end] (measured from its start) and hides it after. */
private fun Modifier.fadeTextBefore(end: Dp, fadeWidth: Dp = 14.dp): Modifier =
    graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val endPx = end.toPx().coerceAtLeast(0f)
            val startPx = (endPx - fadeWidth.toPx()).coerceAtLeast(0f)
            drawRect(
                Brush.horizontalGradient(0f to Color.Black, 1f to Color.Transparent, startX = startPx, endX = endPx.coerceAtLeast(startPx + 1f)),
                blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
            )
        }

private data class MonthPill(
    val uid: String?,
    /** The occurrence this pill shows, for its pending-sync indicators. */
    val event: EventEntity? = null,
    val task: TaskEntity? = null,
    val title: String,
    val color: Int,
    val completed: Boolean,
    val eventStatus: String? = null,
    val continuesFromPrevious: Boolean = false,
    val continuesToNext: Boolean = false,
)

@Composable
private fun MonthPillChip(pill: MonthPill, morphEnabled: Boolean = false) {
    val bg = Color(pill.color)
    val tentative = pill.eventStatus.equals("TENTATIVE", ignoreCase = true)
    val cancelled = pill.eventStatus.equals("CANCELLED", ignoreCase = true)
    val fill = when {
        cancelled -> bg.greyedOut(0.58f).copy(alpha = 0.34f)
        pill.completed -> bg.greyedOut(0.58f).copy(alpha = 0.46f)
        tentative -> Color.Transparent
        else -> bg
    }
    val textColor = when {
        tentative && CurrentDarkPalette -> Color.White
        tentative -> Color(0xFF17202A)
        bg.isDark() && !cancelled && !pill.completed -> Color.White
        else -> Color(0xFF1C1A18)
    }
    val shape = continuationShape(pill.continuesFromPrevious, pill.continuesToNext)
    val pendingAlpha = pendingDeleteAlpha(pill.event, pill.task)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // Option B: this pill is the source of a per-item morph into its full 1-day card.
            .morphBounds(
                key = "item-${pill.uid}",
                enabled = EnablePerItemMorph && morphEnabled && pill.uid != null,
                enter = MorphItemPillEnter,
                exit = MorphItemPillExit,
                boundsTransform = MorphItemBoundsTransform,
            )
            .alpha(pendingAlpha)
            .drawContinuationBridge(fill, pill.continuesFromPrevious, pill.continuesToNext, bridgeWidth = DayColumnSpacing + 8.dp)
            .background(fill, shape)
            .then(if (tentative) Modifier.dashedBorder(bg.copy(alpha = 0.9f), 6.dp) else Modifier)
            .padding(horizontal = 4.dp, vertical = 1.dp),
    ) {
        Text(
            pill.title,
            color = textColor.copy(alpha = if (cancelled) 0.54f else if (pill.completed) 0.64f else 1f),
            fontSize = 9.sp,
            lineHeight = 11.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            fontWeight = FontWeight.Medium,
            textDecoration = if (cancelled) TextDecoration.LineThrough else null,
        )
        PendingMutationBadge(pill.event, pill.task, Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-2).dp))
    }
}
