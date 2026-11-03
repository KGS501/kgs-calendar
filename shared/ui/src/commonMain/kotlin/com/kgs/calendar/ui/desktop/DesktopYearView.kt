package com.kgs.calendar.ui.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.kgs.calendar.ui.MonthRowPillLayout
import com.kgs.calendar.ui.shell.YearViewScroll
import androidx.compose.runtime.DisposableEffect
import com.kgs.calendar.ui.res.stringResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.data.settings.TaskColorMode
import com.kgs.calendar.domain.model.isMonthSurfaceTaskVisible
import com.kgs.calendar.ui.LocalAppLocale
import com.kgs.calendar.ui.MonthRowPillCandidate
import com.kgs.calendar.ui.MonthRowPillChip
import com.kgs.calendar.ui.MorphCellEnter
import com.kgs.calendar.ui.MorphCellExit
import com.kgs.calendar.ui.MotionMedium
import com.kgs.calendar.ui.MotionEmphasized
import com.kgs.calendar.ui.WarmBrown
import com.kgs.calendar.ui.WarmInk
import com.kgs.calendar.ui.blendWith
import com.kgs.calendar.ui.buildMonthRowPillSegments
import com.kgs.calendar.ui.indexEventsByDay
import com.kgs.calendar.ui.indexTasksByDay
import com.kgs.calendar.ui.isDark
import com.kgs.calendar.ui.monthRowPillGeometry
import com.kgs.calendar.ui.morphBounds
import com.kgs.calendar.ui.settingsControlColor
import com.kgs.calendar.ui.shell.DateRequest
import com.kgs.calendar.ui.shell.ScrollStepRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.datetime.DayOfWeek
import kotlin.math.roundToInt
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import com.kgs.calendar.time.*
import kotlinx.datetime.*
import com.kgs.calendar.ui.res.Res

private const val YearViewFirstYear = 1800
private const val YearViewYearCount = 401
/** Each year is a heading followed by its twelve months. */
private const val YearViewItemsPerYear = 13

private fun yearHeadingIndex(year: Int): Int =
    (year - YearViewFirstYear).coerceIn(0, YearViewYearCount - 1) * YearViewItemsPerYear

/** The month of the list item at [index], or null for a year's heading. */
private fun monthAt(index: Int): YearMonth? {
    val inYear = index % YearViewItemsPerYear
    if (inYear == 0) return null
    return YearMonth.of(YearViewFirstYear + index / YearViewItemsPerYear, inYear)
}

private fun yearAt(index: Int): Int = YearViewFirstYear + index / YearViewItemsPerYear

private fun monthRowIndex(month: YearMonth): Int = yearHeadingIndex(month.year) + month.monthValue

/**
 * The first item and how far it is scrolled so that the item at [index] (a month row [rowPx] tall) sits
 * in the middle of a [viewportPx] tall view; at the top if it is taller than the view.
 */
internal fun centeredPosition(index: Int, rowPx: Float, headingPx: Float, viewportPx: Float): Pair<Int, Int> {
    var above = ((viewportPx - rowPx) / 2f).coerceAtLeast(0f)
    var first = index
    while (above > 0f && first > 0) {
        first--
        above -= if (first % YearViewItemsPerYear == 0) headingPx else rowPx
    }
    return first to (-above).coerceAtLeast(0f).toInt()
}

/**
 * The desktop's year view: a row per month with the days in weekday-aligned columns, so the same
 * weekday lines up through the year, and the years one after another as the view scrolls. A day
 * shows its items as the month view does, as many as fit, with the rest as dots; a click opens the
 * day the way the month view does.
 */
@Composable
internal fun DesktopYearView(
    selectedDate: LocalDate,
    firstDayOfWeek: DayOfWeek,
    today: LocalDate,
    taskColorMode: TaskColorMode,
    morphDay: LocalDate,
    dateRequest: DateRequest?,
    scrollStep: ScrollStepRequest?,
    observeRange: (LocalDate, LocalDate) -> Flow<Pair<List<EventEntity>, List<TaskEntity>>>,
    onYearShown: (Int) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    /** Where the user left the view earlier in this run; it opens there again if still in the selected year. */
    remembered: YearViewScroll?,
    onRemember: (YearViewScroll) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalAppLocale.current
    val density = LocalDensity.current
    val resume = remember { remembered?.takeIf { it.centerYear == selectedDate.year } }
    // Without a place to resume, the selected day's month (normally today's) ends up in the middle; see below.
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = resume?.index ?: monthRowIndex(YearMonth.from(selectedDate)),
        initialFirstVisibleItemScrollOffset = resume?.offset ?: 0,
    )
    val currentOnRemember by rememberUpdatedState(onRemember)
    DisposableEffect(listState) {
        onDispose {
            currentOnRemember(
                YearViewScroll(
                    index = listState.firstVisibleItemIndex,
                    offset = listState.firstVisibleItemScrollOffset,
                    centerYear = listState.centerYear() ?: selectedDate.year,
                ),
            )
        }
    }
    val weekdays = weekdaysFrom(firstDayOfWeek)
    // The years on screen, and the items of them; the last ones stay while the next load.
    val shownYears by remember(listState) {
        androidx.compose.runtime.derivedStateOf {
            val visible = listState.layoutInfo.visibleItemsInfo
            val first = visible.firstOrNull()?.index ?: listState.firstVisibleItemIndex
            val last = visible.lastOrNull()?.index ?: first
            yearAt(first)..yearAt(last)
        }
    }
    val items by produceState(emptyList<EventEntity>() to emptyList<TaskEntity>(), shownYears) {
        observeRange(LocalDate.of(shownYears.first, 1, 1), LocalDate.of(shownYears.last + 1, 1, 1)).collect { value = it }
    }
    val eventsByDay = remember(items) { items.first.indexEventsByDay() }
    val tasksByDay = remember(items) {
        items.second.filter { isMonthSurfaceTaskVisible(it.isCompleted, it.status) }.indexTasksByDay()
    }
    val currentOnYearShown by rememberUpdatedState(onYearShown)
    // The toolbar names the year that fills most of the view.
    LaunchedEffect(listState) {
        snapshotFlow { listState.centerYear() }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { currentOnYearShown(it) }
    }
    BoxWithConstraints(modifier = modifier.fillMaxSize().padding(start = 12.dp, end = 16.dp)) {
        val labelWidth = 60.dp
        val cellWidth = (maxWidth - labelWidth) / YearColumns
        // The rows are as tall as the user sets them: low enough for a whole year on screen, or
        // tall enough for more items before they turn into dots. The list scrolls through the years.
        val panes = LocalPaneLayout.current
        val targetRowHeight = panes.yearRowHeightDp.dp
        // Zooming glides between heights instead of jumping.
        val rowHeight by androidx.compose.animation.core.animateDpAsState(
            targetValue = targetRowHeight,
            animationSpec = androidx.compose.animation.core.tween(MotionMedium, easing = MotionEmphasized),
            label = "yearRowHeight",
        )
        val rowHeightPx = with(density) { rowHeight.toPx() }
        val viewportPx = with(density) { (maxHeight - YearWeekdayHeaderHeight).toPx() }
        val headingPx = with(density) { YearHeadingHeight.toPx() }
        /** The first item and its offset that put [month]'s row in the middle of the view. */
        fun centered(month: YearMonth, rowPx: Float): Pair<Int, Int> =
            centeredPosition(monthRowIndex(month), rowPx, headingPx, viewportPx)
        if (resume == null) {
            // Once, before the first frame: the month in the middle rather than at the top.
            remember {
                val (index, offset) = centered(YearMonth.from(selectedDate), with(density) { targetRowHeight.toPx() })
                listState.requestScrollToItem(index, offset)
            }
        }
        // Today puts today's month in the middle; the toolbar's arrows bring a year's January to the
        // top. Requests from before the view opened are already shown.
        var handledDateRequest by remember { mutableStateOf(dateRequest?.serial) }
        LaunchedEffect(dateRequest) {
            val request = dateRequest ?: return@LaunchedEffect
            if (request.serial == handledDateRequest) return@LaunchedEffect
            handledDateRequest = request.serial
            val (target, offset) = if (request.date == today) {
                centered(YearMonth.from(today), rowHeightPx)
            } else {
                yearHeadingIndex(request.date.year) to 0
            }
            val distance = target - listState.firstVisibleItemIndex
            if (kotlin.math.abs(distance) > 2 * YearViewItemsPerYear) {
                listState.scrollToItem(target - distance.coerceIn(-YearViewItemsPerYear, YearViewItemsPerYear))
            }
            listState.animateScrollToItem(target, offset)
        }
        var handledScrollStep by remember { mutableStateOf(scrollStep?.serial) }
        LaunchedEffect(scrollStep) {
            val request = scrollStep ?: return@LaunchedEffect
            if (request.serial == handledScrollStep) return@LaunchedEffect
            handledScrollStep = request.serial
            listState.animateScrollBy(rowHeightPx * request.direction)
        }
        // Growing rows gain lanes as the room appears; shrinking rows give them up at once, so no
        // card ever spills into the next month while the rows move.
        val laneHeight = minOf(rowHeight, targetRowHeight)
        val lanes = ((laneHeight - YearPillTop - YearDotsHeight + YearPillGap) / YearPillStep).toInt().coerceAtLeast(0)
        // Opening the view builds all its rows at once, which can hold up the animations around it for
        // a moment. So the rows first show their days, and their items follow one row per frame.
        val firstRowIndex = remember { listState.firstVisibleItemIndex }
        val revealedRows = remember { androidx.compose.runtime.mutableIntStateOf(0) }
        LaunchedEffect(Unit) {
            while (revealedRows.intValue < RevealedRowsAtOnce) {
                androidx.compose.runtime.withFrameNanos { }
                revealedRows.intValue++
            }
        }
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.height(YearWeekdayHeaderHeight)) {
                YearRowHeightControl(
                    onSmaller = { panes.zoomYearRows(-1) },
                    onLarger = { panes.zoomYearRows(1) },
                    modifier = Modifier.width(labelWidth),
                )
                for (column in 0 until YearColumns) {
                    val day = weekdays[column % 7]
                    Text(
                        day.shortLabel(locale, narrow = cellWidth < 26.dp),
                        color = if (day.isWeekend()) WarmBrown else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.width(cellWidth).padding(top = 10.dp),
                    )
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    // Ctrl and the mouse wheel make the rows taller or lower, like zooming.
                    .pointerInput(panes) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                if (event.type == PointerEventType.Scroll && event.keyboardModifiers.isCtrlPressed) {
                                    val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                                    if (delta != 0f) panes.zoomYearRows(if (delta < 0f) 1 else -1)
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        }
                    },
            ) {
                items(YearViewYearCount * YearViewItemsPerYear, key = { it }) { index ->
                    val month = monthAt(index)
                    if (month == null) {
                        YearHeading(yearAt(index), current = yearAt(index) == today.year, labelWidth = labelWidth)
                    } else {
                        val order = index - firstRowIndex
                        val showItems by remember(order) {
                            androidx.compose.runtime.derivedStateOf { revealedRows.intValue > order || revealedRows.intValue >= RevealedRowsAtOnce }
                        }
                        YearMonthRow(
                            month = month,
                            showItems = showItems,
                            eventsByDay = eventsByDay,
                            tasksByDay = tasksByDay,
                            taskColorMode = taskColorMode,
                            firstDayOfWeek = firstDayOfWeek,
                            today = today,
                            labelWidth = labelWidth,
                            cellWidth = cellWidth,
                            rowHeight = rowHeight,
                            lanes = lanes,
                            morphDay = morphDay,
                            onOpenDay = onOpenDay,
                        )
                    }
                }
            }
        }
    }
}

/** Lower or taller month rows (also Ctrl and the mouse wheel). */
@Composable
private fun YearRowHeightControl(onSmaller: () -> Unit, onLarger: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxHeight(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
    ) {
        YearZoomButton(Icons.Default.Remove, stringResource(Res.string.desktop_year_rows_lower), onSmaller)
        YearZoomButton(Icons.Default.Add, stringResource(Res.string.desktop_year_rows_taller), onLarger)
    }
}

@Composable
private fun YearZoomButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(if (hovered) settingsControlColor().blendWith(WarmInk, 0.08f) else settingsControlColor())
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = WarmInk, modifier = Modifier.size(16.dp))
    }
}

/** The year whose rows cover the middle of the view. */
private fun LazyListState.centerYear(): Int? {
    val info = layoutInfo
    if (info.visibleItemsInfo.isEmpty()) return null
    val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
    val item = info.visibleItemsInfo.firstOrNull { center in it.offset until it.offset + it.size }
        ?: info.visibleItemsInfo.first()
    return yearAt(item.index)
}

private const val YearColumns = 37

private val NoRowItems = MonthRowPillLayout(emptyList(), emptyMap())

/** After this many frames every row shows its items, also those that only scroll in then. */
private const val RevealedRowsAtOnce = 16
private val YearWeekdayHeaderHeight = 32.dp
private val YearHeadingHeight = 56.dp
private val YearPillTop = 23.dp
private val YearPillHeight = 20.dp
private val YearPillGap = 3.dp
private val YearPillStep = YearPillHeight + YearPillGap
private val YearDotsHeight = 13.dp
private val YearDotSize = 6.dp
private val YearDotGap = 2.dp

private fun DayOfWeek.isWeekend(): Boolean = this == DayOfWeek.SATURDAY || this == DayOfWeek.SUNDAY

/**
 * Where one year ends and the next begins: the year's name in the middle, with a line reaching out
 * on either side that fades before the edges.
 */
@Composable
private fun YearHeading(year: Int, current: Boolean, labelWidth: Dp) {
    val color = if (current) WarmBrown else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(YearHeadingHeight)
            .padding(start = labelWidth),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        YearHeadingLine(color, fadeTowardsStart = true)
        Text(
            year.toString(),
            color = color,
            fontSize = 22.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(horizontal = 18.dp),
        )
        YearHeadingLine(color, fadeTowardsStart = false)
    }
}

@Composable
private fun YearHeadingLine(color: Color, fadeTowardsStart: Boolean) {
    val solid = color.copy(alpha = 0.45f)
    val clear = color.copy(alpha = 0f)
    Box(
        Modifier
            .width(180.dp)
            .height(1.5.dp)
            .clip(CircleShape)
            .background(
                androidx.compose.ui.graphics.Brush.horizontalGradient(
                    if (fadeTowardsStart) listOf(clear, solid) else listOf(solid, clear),
                ),
            ),
    )
}

@Composable
private fun YearMonthRow(
    month: YearMonth,
    /** False while the row only shows its days, as the view opens. */
    showItems: Boolean,
    eventsByDay: Map<LocalDate, List<EventEntity>>,
    tasksByDay: Map<LocalDate, List<TaskEntity>>,
    taskColorMode: TaskColorMode,
    firstDayOfWeek: DayOfWeek,
    today: LocalDate,
    labelWidth: Dp,
    cellWidth: Dp,
    rowHeight: Dp,
    lanes: Int,
    morphDay: LocalDate,
    onOpenDay: (LocalDate) -> Unit,
) {
    val locale = LocalAppLocale.current
    val leading = (month.atDay(1).dayOfWeek.value - firstDayOfWeek.value + 7) % 7
    val firstColumnDate = month.atDay(1).minusDays(leading.toLong())
    val days = remember(month) { (1..month.lengthOfMonth()).map(month::atDay) }
    // The month is one row, so items break where a month ends, as they do at a week's end in the month view.
    val layout = remember(month, eventsByDay, tasksByDay, taskColorMode, lanes, showItems) {
        if (!showItems) return@remember NoRowItems
        buildMonthRowPillSegments(
            rowDays = days,
            rowStartDate = days.first(),
            rowEndDate = days.last(),
            eventsByDay = eventsByDay,
            tasksByDay = tasksByDay,
            taskColorMode = taskColorMode,
            visibleLanes = lanes,
        )
    }
    val isCurrentMonth = YearMonth.from(today) == month
    Row(Modifier.fillMaxWidth().height(rowHeight)) {
        Box(Modifier.width(labelWidth).height(rowHeight), contentAlignment = Alignment.TopStart) {
            Text(
                month.month.getDisplayName(com.kgs.calendar.time.TextStyle.SHORT_STANDALONE, locale).trimEnd('.').replaceFirstChar { it.titlecase() },
                color = if (isCurrentMonth) WarmBrown else WarmInk,
                fontSize = 17.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 6.dp, top = 4.dp),
            )
        }
        Box(Modifier.width(cellWidth * YearColumns).height(rowHeight)) {
            days.forEach { date ->
                YearDayCell(
                    date = date,
                    today = date == today,
                    hidden = layout.hiddenByDay[date].orEmpty(),
                    width = cellWidth,
                    height = rowHeight,
                    morphEnabled = date == morphDay,
                    modifier = Modifier.offset(x = cellWidth * (leading + date.dayOfMonth - 1)),
                    onClick = { onOpenDay(date) },
                )
            }
            val rowWidth = cellWidth * YearColumns
            layout.segments.forEach { segment ->
                val geometry = monthRowPillGeometry(
                    segment = segment,
                    rowStartDate = firstColumnDate,
                    columns = YearColumns,
                    cellWidth = cellWidth,
                    spacing = 0.dp,
                    rowWidth = rowWidth,
                    inset = 2.dp,
                )
                MonthRowPillChip(
                    segment = segment,
                    leftSolidStart = geometry.leftSolidStart,
                    rightSolidEnd = geometry.rightSolidEnd,
                    fadeBase = null,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    corner = 6.dp,
                    textPadding = 5.dp,
                    modifier = Modifier
                        .offset(x = geometry.x, y = YearPillTop + YearPillStep * segment.lane)
                        .width(geometry.width)
                        .height(YearPillHeight),
                )
            }
        }
    }
}

@Composable
private fun YearDayCell(
    date: LocalDate,
    today: Boolean,
    hidden: List<MonthRowPillCandidate>,
    width: Dp,
    height: Dp,
    morphEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val dark = MaterialTheme.colorScheme.background.isDark()
    val background = when {
        date.dayOfWeek.isWeekend() -> settingsControlColor().blendWith(WarmBrown, if (dark) 0.16f else 0.10f)
        else -> settingsControlColor()
    }
    val shape = RoundedCornerShape(6.dp)
    // Two digits still fit when the window, or a pinned pane, leaves the days narrow.
    val dayNumberSize = (width.value * 0.42f).coerceIn(8f, 12f).sp
    Box(modifier = modifier.size(width, height).padding(1.dp)) {
        // Only an invisible anchor takes part in the day's morph, as in the month view: the
        // opening day grows out of these bounds while the tile itself stays in the year.
        Box(
            Modifier
                .matchParentSize()
                .morphBounds("dayblock-$date", enabled = morphEnabled, enter = MorphCellEnter, exit = MorphCellExit),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(shape)
                .background(if (hovered) background.blendWith(WarmInk, 0.10f) else background)
                .then(if (today) Modifier.border(1.5.dp, WarmBrown, shape) else Modifier)
                .hoverable(interaction)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        ) {
            val accentText = if (WarmBrown.isDark()) Color.White else Color(0xFF1C1A18)
            Text(
                date.dayOfMonth.toString(),
                color = if (today) accentText else WarmInk,
                fontSize = dayNumberSize,
                lineHeight = dayNumberSize * 1.25f,
                fontWeight = if (today) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .padding(start = 2.dp, top = 2.dp)
                    .then(
                        if (today) Modifier.clip(RoundedCornerShape(4.dp)).background(WarmBrown).padding(horizontal = 3.dp) else Modifier,
                    )
                    .padding(start = if (today) 0.dp else 1.dp),
            )
            if (hidden.isNotEmpty()) {
                HiddenItemDots(
                    hidden = hidden,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 3.dp, end = 2.dp, bottom = 1.dp)
                        .fillMaxWidth()
                        .height(YearDotsHeight - 1.dp),
                )
            }
        }
    }
}

/**
 * The items that found no room as dots in their colors (rings for tasks), as many as fit; the
 * rest are counted after them. A count always stands for at least two items, since one more dot
 * would take no more room than "+1".
 */
@Composable
private fun HiddenItemDots(hidden: List<MonthRowPillCandidate>, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val countStyle = TextStyle(
        fontFamily = com.kgs.calendar.ui.theme.LocalAppFontFamily.current,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
    )
    val countColor = MaterialTheme.colorScheme.onSurfaceVariant
    val inkCenter = rememberCountInkCenter(measurer, countStyle)
    // Dots and count are drawn on one center line, so the count sits level with the dots.
    androidx.compose.foundation.Canvas(modifier) {
        val dot = YearDotSize.toPx()
        val step = dot + YearDotGap.toPx()
        val (dots, rest) = hiddenDotLayout(hidden.size, size.width, dot, step) { count ->
            measurer.measure("+$count", countStyle).size.width.toFloat()
        }
        // The count is drawn at a whole-pixel offset, and the dots take the center of the ink it
        // actually leaves there (measured once, see rememberCountInkCenter): one center line.
        val layout = if (rest > 0) measurer.measure("+$rest", countStyle.copy(color = countColor)) else null
        val textTop: Float
        val centerY: Float
        if (layout != null) {
            textTop = (size.height / 2f - inkCenter).roundToInt().toFloat()
            centerY = textTop + inkCenter
        } else {
            textTop = 0f
            centerY = size.height / 2f
        }
        hidden.take(dots).forEachIndexed { index, item ->
            val color = Color(item.color)
            val center = androidx.compose.ui.geometry.Offset(index * step + dot / 2f, centerY)
            if (item.task != null) {
                drawCircle(color, radius = dot / 2f - 0.6.dp.toPx(), center = center, style = androidx.compose.ui.graphics.drawscope.Stroke(1.2.dp.toPx()))
            } else {
                drawCircle(color, radius = dot / 2f, center = center)
            }
        }
        if (layout != null) {
            drawText(layout, topLeft = androidx.compose.ui.geometry.Offset((dots * step).roundToInt().toFloat(), textTop))
        }
    }
}

/**
 * How far below the top of its layout the ink of a count ("+8": the digits' full height) is
 * centered, measured by drawing it once: font metrics alone don't say where the renderer puts it.
 */
@Composable
private fun rememberCountInkCenter(measurer: androidx.compose.ui.text.TextMeasurer, style: TextStyle): Float {
    val density = LocalDensity.current
    return remember(style, density) {
        val layout = measurer.measure("+8", style.copy(color = Color.Black))
        val width = layout.size.width.coerceAtLeast(1)
        val height = layout.size.height.coerceAtLeast(1)
        val bitmap = androidx.compose.ui.graphics.ImageBitmap(width, height)
        androidx.compose.ui.graphics.drawscope.CanvasDrawScope().draw(
            density,
            androidx.compose.ui.unit.LayoutDirection.Ltr,
            androidx.compose.ui.graphics.Canvas(bitmap),
            androidx.compose.ui.geometry.Size(width.toFloat(), height.toFloat()),
        ) { drawText(layout) }
        val pixels = IntArray(width * height)
        bitmap.readPixels(pixels)
        var top = -1
        var bottom = -1
        for (y in 0 until height) {
            val inked = (0 until width).any { x -> (pixels[y * width + x] ushr 24) > 110 }
            if (inked) {
                if (top < 0) top = y
                bottom = y
            }
        }
        if (top < 0) layout.firstBaseline - layout.size.height / 4f else (top + bottom + 1) / 2f
    }
}

/**
 * How many of [count] hidden items get a dot in [available] px, and how many the count after them
 * stands for: all dots if they fit, otherwise as many as leave room for the count.
 */
internal fun hiddenDotLayout(
    count: Int,
    available: Float,
    dotSize: Float,
    dotStep: Float,
    countWidth: (Int) -> Float,
): Pair<Int, Int> {
    if (count <= 0) return 0 to 0
    val capacity = if (available < dotSize) 0 else 1 + ((available - dotSize) / dotStep).toInt()
    if (count <= capacity) return count to 0
    for (dots in (capacity - 1) downTo 0) {
        val rest = count - dots
        if (dots * dotStep + countWidth(rest) <= available) return dots to rest
    }
    return 0 to 0
}
