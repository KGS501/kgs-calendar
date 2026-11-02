package com.kgs.calendar.ui.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.data.settings.TaskColorMode
import com.kgs.calendar.domain.event.displayColor
import com.kgs.calendar.domain.event.endDateInclusive
import com.kgs.calendar.domain.task.displayColor
import com.kgs.calendar.domain.time.toDate
import com.kgs.calendar.res.LocalAppLocale
import com.kgs.calendar.ui.WarmBrown
import com.kgs.calendar.ui.WarmInk
import com.kgs.calendar.ui.blendWith
import com.kgs.calendar.ui.isDark
import com.kgs.calendar.ui.model.taskDate
import com.kgs.calendar.ui.settingsControlColor
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle

/** An item of the year view: an event or a dated task over one or more days of a month row. */
internal data class YearBar(
    val key: String,
    val title: String,
    val color: Int,
    val start: LocalDate,
    val end: LocalDate,
    val event: EventEntity?,
    val task: TaskEntity?,
    val completed: Boolean,
)

internal data class PlacedYearBar(val bar: YearBar, val start: LocalDate, val end: LocalDate, val lane: Int)

/**
 * Places the month's bars in lanes: earlier starts first, then longer ones, each in the lowest
 * free lane, like the timeline's all-day rows. Bars are cut at the month's edges.
 */
internal fun layoutYearMonth(month: YearMonth, bars: List<YearBar>): List<PlacedYearBar> {
    val first = month.atDay(1)
    val last = month.atEndOfMonth()
    val lanes = mutableListOf<MutableList<PlacedYearBar>>()
    return bars
        .filter { !it.end.isBefore(first) && !it.start.isAfter(last) }
        .sortedWith(compareBy<YearBar> { it.start }.thenByDescending { it.end }.thenBy { it.title }.thenBy { it.key })
        .map { bar ->
            val start = maxOf(bar.start, first)
            val end = minOf(bar.end, last)
            val lane = lanes.indexOfFirst { placed -> placed.none { !(end.isBefore(it.start) || start.isAfter(it.end)) } }
                .takeIf { it >= 0 } ?: lanes.size.also { lanes += mutableListOf<PlacedYearBar>() }
            PlacedYearBar(bar, start, end, lane).also { lanes[lane] += it }
        }
}

/** All-day and multi-day events, which the year view shows as bars across their days. */
internal fun yearBars(events: List<EventEntity>, tasks: List<TaskEntity>, taskColorMode: TaskColorMode): List<YearBar> =
    yearItems(events, tasks, taskColorMode).filter { it.isBar() }

/** Timed events of a single day and dated tasks, which the year view shows as dots on their day. */
internal fun yearDots(events: List<EventEntity>, tasks: List<TaskEntity>, taskColorMode: TaskColorMode): Map<LocalDate, List<YearBar>> =
    yearItems(events, tasks, taskColorMode).filterNot { it.isBar() }.groupBy { it.start }

private fun YearBar.isBar(): Boolean = task == null && (event?.allDay == true || end.isAfter(start))

private fun yearItems(events: List<EventEntity>, tasks: List<TaskEntity>, taskColorMode: TaskColorMode): List<YearBar> =
    events.map { event ->
        YearBar(
            key = "event:${event.resourceHref}:${event.startsAtMillis}",
            title = event.title,
            color = event.displayColor(),
            start = event.startsAtMillis.toDate(),
            end = maxOf(event.endDateInclusive(), event.startsAtMillis.toDate()),
            event = event,
            task = null,
            completed = false,
        )
    } + tasks.mapNotNull { task ->
        val date = task.taskDate() ?: return@mapNotNull null
        YearBar(
            key = "task:${task.resourceHref}:${task.dueAtMillis ?: task.startAtMillis}",
            title = task.title,
            color = task.displayColor(taskColorMode),
            start = date,
            end = date,
            event = null,
            task = task,
            completed = task.isCompleted,
        )
    }

/**
 * The desktop's year view: a row per month with the days in weekday-aligned columns, so the same
 * weekday lines up through the year, and events as bars across their days. Weekends are tinted.
 */
@Composable
internal fun DesktopYearView(
    year: Int,
    firstDayOfWeek: DayOfWeek,
    today: LocalDate,
    events: List<EventEntity>,
    tasks: List<TaskEntity>,
    taskColorMode: TaskColorMode,
    onOpenDay: (LocalDate) -> Unit,
    onEventClick: (EventEntity) -> Unit,
    onTaskClick: (TaskEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalAppLocale.current
    val bars = remember(events, tasks, taskColorMode) { yearBars(events, tasks, taskColorMode) }
    val dots = remember(events, tasks, taskColorMode) { yearDots(events, tasks, taskColorMode) }
    val months = remember(year) { (1..12).map { YearMonth.of(year, it) } }
    val placedByMonth = remember(months, bars) { months.associateWith { layoutYearMonth(it, bars) } }
    val weekdays = weekdaysFrom(firstDayOfWeek)
    BoxWithConstraints(modifier = modifier.fillMaxSize().padding(start = 12.dp, end = 16.dp, bottom = 12.dp)) {
        val labelWidth = 52.dp
        val headerHeight = 26.dp
        val cellWidth = (maxWidth - labelWidth) / YearColumns
        val rowHeight = max(YearMinRowHeight, (maxHeight - headerHeight) / 12)
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.height(headerHeight).padding(start = labelWidth)) {
                for (column in 0 until YearColumns) {
                    val day = weekdays[column % 7]
                    Text(
                        day.shortLabel(locale, narrow = cellWidth < 26.dp),
                        color = if (day.isWeekend()) WarmBrown else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.width(cellWidth),
                    )
                }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                months.forEach { month ->
                    YearMonthRow(
                        month = month,
                        placed = placedByMonth.getValue(month),
                        dots = dots,
                        firstDayOfWeek = firstDayOfWeek,
                        today = today,
                        labelWidth = labelWidth,
                        cellWidth = cellWidth,
                        rowHeight = rowHeight,
                        onOpenDay = onOpenDay,
                        onEventClick = onEventClick,
                        onTaskClick = onTaskClick,
                    )
                }
            }
        }
    }
}

private const val YearColumns = 37
private val YearMinRowHeight = 46.dp
private val YearBarHeight = 14.dp
private val YearBarGap = 2.dp
private val YearDayNumberHeight = 17.dp
private val YearDotsHeight = 11.dp
private const val YearMaxDots = 5

private fun DayOfWeek.isWeekend(): Boolean = this == DayOfWeek.SATURDAY || this == DayOfWeek.SUNDAY

@Composable
private fun YearMonthRow(
    month: YearMonth,
    placed: List<PlacedYearBar>,
    dots: Map<LocalDate, List<YearBar>>,
    firstDayOfWeek: DayOfWeek,
    today: LocalDate,
    labelWidth: Dp,
    cellWidth: Dp,
    rowHeight: Dp,
    onOpenDay: (LocalDate) -> Unit,
    onEventClick: (EventEntity) -> Unit,
    onTaskClick: (TaskEntity) -> Unit,
) {
    val locale = LocalAppLocale.current
    val leading = (month.atDay(1).dayOfWeek.value - firstDayOfWeek.value + 7) % 7
    val laneCapacity = ((rowHeight - YearDayNumberHeight - YearDotsHeight) / (YearBarHeight + YearBarGap)).toInt().coerceAtLeast(1)
    val isCurrentMonth = YearMonth.from(today) == month
    Row(Modifier.fillMaxWidth().height(rowHeight)) {
        Box(Modifier.width(labelWidth).height(rowHeight), contentAlignment = Alignment.TopStart) {
            Text(
                month.month.getDisplayName(TextStyle.SHORT_STANDALONE, locale).trimEnd('.').replaceFirstChar { it.titlecase(locale) },
                color = if (isCurrentMonth) WarmBrown else WarmInk,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 6.dp, top = 6.dp),
            )
        }
        Box(Modifier.width(cellWidth * YearColumns).height(rowHeight)) {
            // Day tiles.
            for (day in 1..month.lengthOfMonth()) {
                val date = month.atDay(day)
                val hidden = placed.count { it.lane >= laneCapacity && date in it.start..it.end }
                YearDayCell(
                    date = date,
                    today = date == today,
                    dots = dots[date].orEmpty(),
                    hiddenCount = hidden,
                    width = cellWidth,
                    height = rowHeight,
                    modifier = Modifier.offset(x = cellWidth * (leading + day - 1)),
                    onClick = { onOpenDay(date) },
                )
            }
            // Bars on top of the tiles.
            placed.filter { it.lane < laneCapacity }.forEach { piece ->
                val startColumn = leading + piece.start.dayOfMonth - 1
                val span = piece.end.dayOfMonth - piece.start.dayOfMonth + 1
                YearBarView(
                    piece = piece,
                    continuesBefore = piece.bar.start.isBefore(piece.start),
                    continuesAfter = piece.bar.end.isAfter(piece.end),
                    modifier = Modifier
                        .offset(
                            x = cellWidth * startColumn + 2.dp,
                            y = YearDayNumberHeight + (YearBarHeight + YearBarGap) * piece.lane,
                        )
                        .width(cellWidth * span - 4.dp)
                        .height(YearBarHeight),
                    onClick = {
                        piece.bar.event?.let(onEventClick)
                        piece.bar.task?.let(onTaskClick)
                    },
                )
            }
        }
    }
}

@Composable
private fun YearDayCell(
    date: LocalDate,
    today: Boolean,
    dots: List<YearBar>,
    hiddenCount: Int,
    width: Dp,
    height: Dp,
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
    Box(
        modifier = modifier
            .size(width, height)
            .padding(1.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (hovered) background.blendWith(WarmInk, 0.10f) else background)
            .then(if (today) Modifier.border(1.5.dp, WarmBrown, RoundedCornerShape(6.dp)) else Modifier)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        val accentText = if (WarmBrown.isDark()) Color.White else Color(0xFF1C1A18)
        Text(
            date.dayOfMonth.toString(),
            color = if (today) accentText else WarmInk,
            fontSize = 10.sp,
            fontWeight = if (today) FontWeight.Bold else FontWeight.SemiBold,
            modifier = Modifier
                .padding(start = 3.dp, top = 2.dp)
                .then(
                    if (today) Modifier.clip(RoundedCornerShape(5.dp)).background(WarmBrown).padding(horizontal = 3.dp) else Modifier,
                ),
        )
        val extra = hiddenCount + (dots.size - YearMaxDots).coerceAtLeast(0)
        if (dots.isNotEmpty() || extra > 0) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(YearDotsHeight)
                    .padding(horizontal = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp),
            ) {
                dots.take(YearMaxDots).forEach { dot ->
                    val color = Color(dot.color)
                    Box(
                        Modifier
                            .size(5.dp)
                            .clip(RoundedCornerShape(50))
                            .then(
                                if (dot.task != null) {
                                    Modifier.border(1.2.dp, color, RoundedCornerShape(50))
                                } else {
                                    Modifier.background(color)
                                },
                            ),
                    )
                }
                if (extra > 0) {
                    Text(
                        "+$extra",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 8.sp,
                        lineHeight = 9.sp,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun YearBarView(
    piece: PlacedYearBar,
    continuesBefore: Boolean,
    continuesAfter: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = Color(piece.bar.color)
    val faded = piece.bar.completed
    val onColor = if (color.isDark()) Color.White else Color(0xFF1C1A18)
    val shape = RoundedCornerShape(
        topStart = if (continuesBefore) 0.dp else 4.dp,
        bottomStart = if (continuesBefore) 0.dp else 4.dp,
        topEnd = if (continuesAfter) 0.dp else 4.dp,
        bottomEnd = if (continuesAfter) 0.dp else 4.dp,
    )
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (faded) color.copy(alpha = 0.45f) else color)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            (if (piece.bar.task != null) "○ " else "") + piece.bar.title,
            color = onColor,
            fontSize = 9.sp,
            lineHeight = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}
