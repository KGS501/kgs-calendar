package com.kgs.calendar.ui.desktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kgs.calendar.domain.model.CalendarViewMode
import com.kgs.calendar.domain.model.coerceMultiDayCount
import com.kgs.calendar.res.LocalAppLocale
import com.kgs.calendar.res.SharedR as R
import com.kgs.calendar.res.stringResource
import com.kgs.calendar.ui.CalendarUiState
import com.kgs.calendar.ui.MotionEmphasized
import com.kgs.calendar.ui.MotionMedium
import com.kgs.calendar.ui.MotionShort
import com.kgs.calendar.ui.MotionStandard
import com.kgs.calendar.ui.WarmBrown
import com.kgs.calendar.ui.WarmInk
import com.kgs.calendar.ui.WarmLine
import com.kgs.calendar.ui.accentContainerColor
import com.kgs.calendar.ui.isDark
import com.kgs.calendar.ui.settingsControlColor
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters

/** How the calendar is framed: the phone and tablet layout, or the desktop's sidebar and toolbar. */
enum class CalendarChrome { Mobile, Desktop }

val LocalCalendarChrome = staticCompositionLocalOf { CalendarChrome.Mobile }

/** The views of the desktop toolbar: the app's views plus the desktop's year view. */
internal enum class DesktopView { Day, Week, Month, Year, Agenda }

internal fun CalendarUiState.desktopView(yearViewOpen: Boolean): DesktopView = when {
    yearViewOpen -> DesktopView.Year
    selectedView == CalendarViewMode.Day -> DesktopView.Day
    selectedView == CalendarViewMode.Month -> DesktopView.Month
    selectedView == CalendarViewMode.Agenda -> DesktopView.Agenda
    else -> DesktopView.Week
}

/** The selected date one page of the current view earlier (-1) or later (+1), as a swipe would show. */
internal fun CalendarUiState.steppedDate(direction: Int, yearViewOpen: Boolean = false): LocalDate = when {
    yearViewOpen -> selectedDate.plusYears(direction.toLong())
    selectedView == CalendarViewMode.Month -> selectedDate.plusMonths(direction.toLong())
    selectedView == CalendarViewMode.Day -> selectedDate.plusDays(direction.toLong())
    selectedView == CalendarViewMode.Agenda -> selectedDate.plusWeeks(direction.toLong())
    else -> selectedDate.plusDays(direction.toLong() * visibleDayCount())
}

internal fun CalendarUiState.visibleDayCount(): Int = when (selectedView) {
    CalendarViewMode.Day -> 1
    CalendarViewMode.ThreeDay -> if (weekViewEnabled) 7 else multiDayCount.coerceMultiDayCount()
    else -> 1
}

/** The days the main view shows, highlighted in the sidebar's month. */
internal fun CalendarUiState.visibleDays(yearViewOpen: Boolean): ClosedRange<LocalDate>? = when {
    yearViewOpen || selectedView == CalendarViewMode.Agenda -> null
    selectedView == CalendarViewMode.Month -> YearMonth.from(selectedDate).let { it.atDay(1)..it.atEndOfMonth() }
    selectedView == CalendarViewMode.ThreeDay && weekViewEnabled -> {
        val start = selectedDate.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
        start..start.plusDays(6)
    }
    else -> selectedDate..selectedDate.plusDays(visibleDayCount() - 1L)
}

/**
 * The desktop toolbar: today and paging on the left with the shown period, the views in the
 * middle, search and tasks on the right. It replaces the phone's toolbar and month drop-down.
 */
@Composable
internal fun DesktopToolbar(
    state: CalendarUiState,
    yearViewOpen: Boolean,
    tasksNeedAttention: Boolean,
    onStep: (Int) -> Unit,
    onToday: () -> Unit,
    onViewSelected: (DesktopView) -> Unit,
    onSearch: () -> Unit,
    onTasks: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalAppLocale.current
    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(DesktopToolbarHeight)) {
    // The title keeps its room: narrower windows get narrower view segments and an icon for search.
    val wide = maxWidth >= 1000.dp
    val segmentWidth = when {
        wide -> 72.dp
        maxWidth >= 760.dp -> 62.dp
        else -> 54.dp
    }
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier
                .height(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(settingsControlColor()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolbarIconButton(Icons.Default.ChevronLeft, stringResource(R.string.desktop_previous)) { onStep(-1) }
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .clickable(onClick = onToday)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.today), color = WarmInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            ToolbarIconButton(Icons.Default.ChevronRight, stringResource(R.string.desktop_next)) { onStep(1) }
        }
        val title = remember(state.selectedDate, state.selectedView, state.multiDayCount, state.weekViewEnabled, yearViewOpen, locale) {
            state.desktopTitle(yearViewOpen, locale)
        }
        AnimatedContent(
            targetState = title,
            transitionSpec = {
                (slideInVertically(tween(MotionMedium, easing = MotionEmphasized)) { it / 3 } + fadeIn(tween(MotionShort))) togetherWith
                    (slideOutVertically(tween(MotionShort, easing = MotionStandard)) { -it / 3 } + fadeOut(tween(MotionShort)))
            },
            label = "desktopTitle",
            modifier = Modifier.weight(1f),
        ) { text ->
            Text(
                text,
                color = WarmInk,
                fontSize = 24.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DesktopViewSwitcher(
            segmentWidth = segmentWidth,
            selected = state.desktopView(yearViewOpen),
            weekLabel = stringResource(if (state.weekViewEnabled) R.string.week else R.string.desktop_days, state.multiDayCount),
            onSelected = onViewSelected,
        )
        if (wide) {
            SearchPill(onClick = onSearch)
        } else {
            ToolbarIconButton(Icons.Default.Search, stringResource(R.string.search), onClick = onSearch)
        }
        Box {
            ToolbarIconButton(Icons.Default.TaskAlt, stringResource(R.string.tasks), size = 40.dp, onClick = onTasks)
            if (tasksNeedAttention) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-6).dp, y = 6.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(WarmBrown),
                )
            }
        }
    }
    }
}

internal val DesktopToolbarHeight: Dp = 68.dp

private fun CalendarUiState.desktopTitle(yearViewOpen: Boolean, locale: java.util.Locale): String {
    if (yearViewOpen) return selectedDate.year.toString()
    val monthYear = DateTimeFormatter.ofPattern("LLLL yyyy", locale)
    val range = visibleDays(yearViewOpen = false)
    if (range == null || YearMonth.from(range.start) == YearMonth.from(range.endInclusive)) {
        return selectedDate.format(monthYear).replaceFirstChar { it.titlecase(locale) }
    }
    val start = range.start
    val end = range.endInclusive
    val startMonth = start.month.getDisplayName(TextStyle.SHORT_STANDALONE, locale).trimEnd('.')
    val endMonth = end.month.getDisplayName(TextStyle.SHORT_STANDALONE, locale).trimEnd('.')
    return if (start.year == end.year) {
        "$startMonth – $endMonth ${end.year}"
    } else {
        "$startMonth ${start.year} – $endMonth ${end.year}"
    }
}

@Composable
private fun ToolbarIconButton(icon: ImageVector, description: String, size: Dp = 40.dp, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (hovered) WarmInk.copy(alpha = 0.08f) else Color.Transparent)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = WarmInk, modifier = Modifier.size(22.dp))
    }
}

/** A segmented switch whose highlight slides to the selected view. */
@Composable
private fun DesktopViewSwitcher(
    segmentWidth: Dp,
    selected: DesktopView,
    weekLabel: String,
    onSelected: (DesktopView) -> Unit,
) {
    val options = DesktopView.entries
    val index = options.indexOf(selected)
    val highlightOffset by animateDpAsState(
        targetValue = segmentWidth * index,
        animationSpec = tween(MotionMedium, easing = MotionEmphasized),
        label = "desktopViewHighlight",
    )
    Box(
        modifier = Modifier
            .height(40.dp)
            .width(segmentWidth * options.size + 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(settingsControlColor())
            .padding(4.dp),
    ) {
        Box(
            Modifier
                .offset(x = highlightOffset)
                .width(segmentWidth)
                .fillMaxHeight()
                .clip(RoundedCornerShape(16.dp))
                .background(accentContainerColor()),
        )
        Row {
            options.forEach { option ->
                val label = when (option) {
                    DesktopView.Day -> stringResource(R.string.day)
                    DesktopView.Week -> weekLabel
                    DesktopView.Month -> stringResource(R.string.month)
                    DesktopView.Year -> stringResource(R.string.desktop_year)
                    DesktopView.Agenda -> stringResource(R.string.agenda)
                }
                val isSelected = option == selected
                val selectedText = if (accentContainerColor().isDark()) Color.White else Color(0xFF1C1A18)
                Box(
                    modifier = Modifier
                        .width(segmentWidth)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onSelected(option) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        color = if (isSelected) selectedText else WarmInk,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchPill(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        modifier = Modifier
            .height(40.dp)
            .widthIn(min = 180.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (hovered) settingsControlColor().copy(alpha = 0.8f) else settingsControlColor())
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(19.dp))
        Text(stringResource(R.string.search), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.weight(1f, fill = false))
        Spacer(Modifier.width(18.dp))
        Text(
            "Ctrl F",
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(WarmLine.copy(alpha = 0.5f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/** The weekdays in display order, starting with [firstDayOfWeek]. */
internal fun weekdaysFrom(firstDayOfWeek: DayOfWeek): List<DayOfWeek> = (0L until 7L).map { firstDayOfWeek.plus(it) }

/** A short weekday name without trailing period, e.g. "Mo" or "Mon". */
internal fun DayOfWeek.shortLabel(locale: java.util.Locale, narrow: Boolean = false): String =
    getDisplayName(if (narrow) TextStyle.NARROW_STANDALONE else TextStyle.SHORT_STANDALONE, locale).trimEnd('.')
