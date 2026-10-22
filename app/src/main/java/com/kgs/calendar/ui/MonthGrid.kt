@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.kgs.calendar.ui

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
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
import java.time.DayOfWeek
import java.time.YearMonth
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import com.kgs.calendar.ui.theme.calendarTextScale

@Composable
internal fun MonthBlock(
    month: YearMonth,
    firstDayOfWeek: DayOfWeek,
    showCalendarWeeks: Boolean,
    eventsByDay: Map<LocalDate, List<EventEntity>>,
    tasksByDay: Map<LocalDate, List<TaskEntity>>,
    taskColorMode: TaskColorMode,
    morphDay: LocalDate,
    onOpenDay: (LocalDate) -> Unit,
) {
    val firstDay = month.atDay(1)
    val leading = firstDay.leadingDaysFrom(firstDayOfWeek)
    val length = month.lengthOfMonth()
    val rowCount = ((leading + length + 6) / 7)
    val rowHeight = MonthDayRowHeight * calendarTextScale()
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
                .weight(1f),
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
    modifier: Modifier = Modifier,
) {
    val isToday = day == LocalCalendarTimeSnapshot.current.today
    val cellShape = RoundedCornerShape(12.dp)
    val cellColor = WarmGrid.copy(alpha = if (MaterialTheme.colorScheme.background.isDark()) 0.92f else 0.96f)
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
        Column(
            modifier = Modifier
                .matchParentSize()
                .clip(cellShape)
                .background(cellColor, cellShape)
                .then(
                    if (isToday) {
                        Modifier.border(1.dp, WarmBrown, cellShape)
                    } else {
                        Modifier
                    },
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 3.dp, vertical = 3.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(20.dp * calendarTextScale()),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                day.dayOfMonth.toString(),
                color = if (isToday) WarmBrown else WarmInk,
                fontSize = 12.sp,
                lineHeight = 13.sp,
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

private val MonthDayRowHeight = 108.dp
private const val MonthVisiblePillLanes = 4

private data class MonthRowPillCandidate(
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

private data class MonthRowPillSegment(
    val candidate: MonthRowPillCandidate,
    val visualStart: LocalDate,
    val visualEnd: LocalDate,
    val lane: Int,
    val fadeMode: MonthRowFadeMode,
)

private enum class MonthRowFadeMode {
    None,
    IntoNextDay,
    AtRowEnd,
}

private fun buildMonthRowPillSegments(
    rowDays: List<LocalDate>,
    rowStartDate: LocalDate,
    rowEndDate: LocalDate,
    eventsByDay: Map<LocalDate, List<EventEntity>>,
    tasksByDay: Map<LocalDate, List<TaskEntity>>,
    taskColorMode: TaskColorMode,
): Pair<List<MonthRowPillSegment>, Map<LocalDate, Int>> {
    val rowStart = rowDays.firstOrNull() ?: return emptyList<MonthRowPillSegment>() to emptyMap()
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
        val visibleFadeLane = if (candidate.isMultiDay && laneSegments.size >= MonthVisiblePillLanes) {
            laneSegments.indexOfFirst { lane -> canShareLaneWithFade(lane) }
                .takeIf { it in 0 until MonthVisiblePillLanes }
                ?: -1
        } else {
            -1
        }
        val targetLane = when {
            fullLane in 0 until MonthVisiblePillLanes -> fullLane
            laneSegments.size < MonthVisiblePillLanes -> laneSegments.size
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
        .filter { it.lane < MonthVisiblePillLanes && !it.visualEnd.isBefore(it.visualStart) }
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
        val active = candidates.count { day in it.start..it.end && it.id !in fadeRepresentedIds }
        val visible = visibleSegments.count { day in it.visualStart..it.visualEnd }
        (active - visible).coerceAtLeast(0)
    }.filterValues { it > 0 }
    return visibleSegments to hiddenByDay
}

@Composable
private fun MonthRowPillOverlay(
    rowDays: List<LocalDate>,
    rowStartDate: LocalDate,
    rowEndDate: LocalDate,
    eventsByDay: Map<LocalDate, List<EventEntity>>,
    tasksByDay: Map<LocalDate, List<TaskEntity>>,
    taskColorMode: TaskColorMode,
    modifier: Modifier = Modifier,
) {
    if (rowDays.isEmpty()) return
    val (segments, hiddenByDay) = remember(rowDays, rowStartDate, rowEndDate, eventsByDay, tasksByDay, taskColorMode) {
        buildMonthRowPillSegments(rowDays, rowStartDate, rowEndDate, eventsByDay, tasksByDay, taskColorMode)
    }
    BoxWithConstraints(modifier = modifier) {
        val spacing = 4.dp
        val cellWidth = (maxWidth - spacing * 6) / 7f
        val step = cellWidth + spacing
        // The pills grow with their text, below the day number.
        val textScale = calendarTextScale()
        val pillTop = 25.dp * textScale
        val pillHeight = 13.dp * textScale
        val laneStep = 15.dp * textScale

        segments.forEach { segment ->
            val startColumn = ChronoUnit.DAYS.between(rowStartDate, segment.visualStart).toInt().coerceIn(0, 6)
            val endColumn = ChronoUnit.DAYS.between(rowStartDate, segment.visualEnd).toInt().coerceIn(startColumn, 6)
            val span = endColumn - startColumn + 1
            val x = step * startColumn + 3.dp
            val bodyWidth = cellWidth * span + spacing * (span - 1) - 6.dp
            val availableTailWidth = (maxWidth - x - bodyWidth).coerceAtLeast(0.dp)
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
            MonthRowPillChip(
                segment = segment,
                leftSolidStart = leftSolidStart,
                rightSolidEnd = rightSolidEnd,
                modifier = Modifier
                    .offset(x = x, y = pillTop + laneStep * segment.lane)
                    .width(width)
                    .height(pillHeight),
            )
        }
        hiddenByDay.forEach { (day, count) ->
            val column = ChronoUnit.DAYS.between(rowStartDate, day).toInt()
            if (column !in 0..6) return@forEach
            Text(
                "+$count",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 9.sp,
                lineHeight = 10.sp,
                modifier = Modifier.offset(x = step * column + 6.dp, y = pillTop + laneStep * MonthVisiblePillLanes),
            )
        }
    }
}

@Composable
private fun MonthRowPillChip(
    segment: MonthRowPillSegment,
    leftSolidStart: Float,
    rightSolidEnd: Float,
    modifier: Modifier = Modifier,
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
    val fadeBase = WarmGrid.copy(alpha = if (MaterialTheme.colorScheme.background.isDark()) 0.92f else 0.96f)
    BoxWithConstraints(
        modifier = modifier
            .then(if (hasEdgeFade && !tentative) Modifier.background(fadeBase, RoundedCornerShape(6.dp)) else Modifier)
            .background(brush, RoundedCornerShape(6.dp))
            .then(if (tentative) Modifier.dashedBorder(bg.copy(alpha = 0.9f), 6.dp) else Modifier),
    ) {
        val titleStartPadding = 4.dp + maxWidth * leftSolidStart
        Text(
            candidate.title,
            color = textColor.copy(alpha = if (cancelled) 0.54f else if (candidate.completed) 0.64f else 1f),
            fontSize = 9.sp,
            lineHeight = 11.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            fontWeight = FontWeight.Medium,
            textDecoration = if (cancelled) TextDecoration.LineThrough else null,
            modifier = Modifier.padding(start = titleStartPadding, end = 4.dp, top = 1.dp, bottom = 0.dp),
        )
        PendingMutationBadge(candidate.event, candidate.task, Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-2).dp))
    }
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
