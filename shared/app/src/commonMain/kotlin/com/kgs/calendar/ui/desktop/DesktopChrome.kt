package com.kgs.calendar.ui.desktop

import com.kgs.calendar.ui.MorphingDropdown
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import com.kgs.calendar.ui.time.LocalCalendarTimeSnapshot
import com.kgs.calendar.ui.blendWith
import com.kgs.calendar.ui.accentContainerContentColor
import com.kgs.calendar.ui.TodayDateIcon
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.Layout
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
 * The desktop toolbar: back, today and forward as three round buttons on the left, search in the
 * middle, and the views, "create" and the tasks on the right. The sidebar's month names the shown
 * period, so the toolbar doesn't. The search field hides while the search is open: it has become
 * the search's own field ([onSearchBounds] tells where it is, for that morph).
 */
@Composable
internal fun DesktopToolbar(
    state: CalendarUiState,
    yearViewOpen: Boolean,
    tasksNeedAttention: Boolean,
    tasksPinned: Boolean,
    searchOpen: Boolean,
    onStep: (Int) -> Unit,
    onToday: () -> Unit,
    onViewSelected: (DesktopView) -> Unit,
    onSearch: () -> Unit,
    onSearchBounds: (androidx.compose.ui.geometry.Rect) -> Unit,
    onTasks: () -> Unit,
    onCreateEvent: () -> Unit,
    onCreateTask: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(DesktopToolbarHeight)) {
        // Narrower windows get narrower view segments and an icon-only create button first.
        val wide = maxWidth >= 1000.dp
        val segmentWidth = if (maxWidth >= 1150.dp) 66.dp else 60.dp
        // Beside a pinned task pane or in a small window the views become a drop-down.
        val compact = maxWidth < 760.dp
        DesktopToolbarLayout(
            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
            navigation = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    RoundToolbarButton(stringResource(R.string.desktop_previous), onClick = { onStep(-1) }) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = WarmInk, modifier = Modifier.size(22.dp))
                    }
                    // Like the app's today button: the calendar icon with today's date.
                    RoundToolbarButton(stringResource(R.string.today), onClick = onToday) {
                        TodayDateIcon(day = LocalCalendarTimeSnapshot.current.today.dayOfMonth, modifier = Modifier.size(22.dp))
                    }
                    RoundToolbarButton(stringResource(R.string.desktop_next), onClick = { onStep(1) }) {
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = WarmInk, modifier = Modifier.size(22.dp))
                    }
                }
            },
            search = {
                Box(
                    Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { onSearchBounds(it.boundsInRoot()) }
                        .graphicsLayer { alpha = if (searchOpen) 0f else 1f },
                ) {
                    SearchField(onClick = onSearch)
                }
            },
            actions = {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    val weekLabel = stringResource(if (state.weekViewEnabled) R.string.week else R.string.desktop_days, state.multiDayCount)
                    if (compact) {
                        CompactViewSwitcher(selected = state.desktopView(yearViewOpen), weekLabel = weekLabel, onSelected = onViewSelected)
                    } else {
                        DesktopViewSwitcher(
                            segmentWidth = segmentWidth,
                            selected = state.desktopView(yearViewOpen),
                            weekLabel = weekLabel,
                            onSelected = onViewSelected,
                        )
                    }
                    CreateButton(onCreateEvent = onCreateEvent, onCreateTask = onCreateTask, showLabel = wide)
                    // A pinned task pane is open already; its own pin closes it.
                    if (!tasksPinned) Box {
                        RoundToolbarButton(stringResource(R.string.tasks), onClick = onTasks, background = Color.Transparent) {
                            Icon(Icons.Default.TaskAlt, contentDescription = null, tint = WarmInk, modifier = Modifier.size(22.dp))
                        }
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
            },
        )
    }
}

/**
 * Places the toolbar's parts: [navigation] at the start, [actions] at the end and [search]
 * centered in the toolbar. When the middle has no room for the search field it fills the space
 * between them instead, down to a search button.
 */
@Composable
private fun DesktopToolbarLayout(
    navigation: @Composable () -> Unit,
    search: @Composable () -> Unit,
    actions: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Layout(contents = listOf(navigation, search, actions), modifier = modifier) { measurables, constraints ->
        val (navigationM, searchM, actionsM) = measurables
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val gap = 16.dp.roundToPx()
        val loose = Constraints(maxHeight = height)
        val nav = navigationM.first().measure(loose)
        val acts = actionsM.first().measure(loose)
        val start = nav.width + gap
        val actionsStart = width - acts.width
        val center = width / 2
        val centeredHalf = minOf(center - start, actionsStart - gap - center)
        val searchMax = SearchMaxWidth.roundToPx()
        val searchMin = SearchMinWidth.roundToPx()
        val searchWidth: Int
        val searchX: Int
        if (centeredHalf * 2 >= searchMin) {
            searchWidth = minOf(searchMax, centeredHalf * 2)
            searchX = center - searchWidth / 2
        } else {
            val free = actionsStart - gap - start
            searchWidth = if (free >= searchMin) minOf(searchMax, free) else SearchButtonSize.roundToPx()
            searchX = (actionsStart - gap - searchWidth).coerceAtLeast(start)
        }
        val searchPlaceable = searchM.first().measure(Constraints.fixed(searchWidth, SearchHeight.roundToPx()))
        layout(width, height) {
            nav.placeRelative(0, (height - nav.height) / 2)
            searchPlaceable.placeRelative(searchX, (height - searchPlaceable.height) / 2)
            acts.placeRelative(actionsStart, (height - acts.height) / 2)
        }
    }
}

private val SearchMaxWidth = 420.dp
internal val SearchMinWidth = 160.dp
private val SearchHeight = 40.dp
private val SearchButtonSize = 40.dp

internal val DesktopToolbarHeight: Dp = 68.dp

/** A round toolbar button with [content] centered, darker while hovered. */
@Composable
private fun RoundToolbarButton(
    description: String,
    onClick: () -> Unit,
    background: Color = settingsControlColor(),
    size: Dp = 40.dp,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val fill = when {
        background == Color.Transparent && hovered -> WarmInk.copy(alpha = 0.08f)
        hovered -> background.blendWith(WarmInk, 0.08f)
        else -> background
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(fill)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        content()
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
    val shown = selected
    fun select(option: DesktopView) {
        if (option != selected) onSelected(option)
    }
    val index = options.indexOf(shown)
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
                val isSelected = option == shown
                val selectedText = if (accentContainerColor().isDark()) Color.White else Color(0xFF1C1A18)
                Box(
                    modifier = Modifier
                        .width(segmentWidth)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { select(option) },
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
private fun DesktopView.label(weekLabel: String): String = when (this) {
    DesktopView.Day -> stringResource(R.string.day)
    DesktopView.Week -> weekLabel
    DesktopView.Month -> stringResource(R.string.month)
    DesktopView.Year -> stringResource(R.string.desktop_year)
    DesktopView.Agenda -> stringResource(R.string.agenda)
}

/** The views as a drop-down showing the selected one, where the segmented switch has no room. */
@Composable
private fun CompactViewSwitcher(selected: DesktopView, weekLabel: String, onSelected: (DesktopView) -> Unit) {
    var open by remember { mutableStateOf(false) }
    MorphingDropdown(
        expanded = open,
        onDismissRequest = { open = false },
        buttonShape = RoundedCornerShape(20.dp),
        buttonColor = settingsControlColor(),
        alignEnd = true,
        onClick = { open = true },
        menuMinWidth = 160.dp,
        button = {
            Row(
                modifier = Modifier.height(40.dp).padding(start = 16.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(selected.label(weekLabel), color = WarmInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                Icon(Icons.Default.ExpandMore, contentDescription = null, tint = WarmInk, modifier = Modifier.size(20.dp))
            }
        },
        menu = {
            DesktopView.entries.forEach { view ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            open = false
                            onSelected(view)
                        }
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        view.label(weekLabel),
                        color = if (view == selected) WarmBrown else WarmInk,
                        fontSize = 15.sp,
                        fontWeight = if (view == selected) FontWeight.SemiBold else FontWeight.Medium,
                    )
                }
            }
        },
    )
}

/** "New" with the choice of event or task, in the app's morphing drop-down. */
@Composable
private fun CreateButton(onCreateEvent: () -> Unit, onCreateTask: () -> Unit, showLabel: Boolean) {
    var open by remember { mutableStateOf(false) }
    val onAccent = if (WarmBrown.isDark()) Color.White else Color(0xFF1C1A18)
    MorphingDropdown(
        expanded = open,
        onDismissRequest = { open = false },
        buttonShape = RoundedCornerShape(20.dp),
        buttonColor = WarmBrown,
        alignEnd = true,
        onClick = { open = true },
        onClickLabel = stringResource(R.string.create),
        role = androidx.compose.ui.semantics.Role.Button,
        menuMinWidth = 180.dp,
        button = {
            Row(
                modifier = Modifier.height(40.dp).padding(start = 12.dp, end = if (showLabel) 16.dp else 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = onAccent, modifier = Modifier.size(20.dp))
                if (showLabel) {
                    Text(
                        stringResource(R.string.create),
                        color = onAccent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        },
        menu = {
            CreateMenuRow(Icons.Default.Event, stringResource(R.string.new_event)) {
                open = false
                onCreateEvent()
            }
            CreateMenuRow(Icons.Default.TaskAlt, stringResource(R.string.new_task)) {
                open = false
                onCreateTask()
            }
        },
    )
}

@Composable
private fun CreateMenuRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = WarmBrown, modifier = Modifier.size(20.dp))
        Text(label, color = WarmInk, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SearchField(onClick: () -> Unit) {
    BoxWithConstraints {
        if (maxWidth < SearchMinWidth) {
            RoundToolbarButton(stringResource(R.string.search), onClick = onClick, background = Color.Transparent) {
                Icon(Icons.Default.Search, contentDescription = null, tint = WarmInk, modifier = Modifier.size(22.dp))
            }
            return@BoxWithConstraints
        }
        val interaction = remember { MutableInteractionSource() }
        val hovered by interaction.collectIsHoveredAsState()
        Row(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(20.dp))
                .background(if (hovered) settingsControlColor().blendWith(WarmInk, 0.05f) else settingsControlColor())
                .hoverable(interaction)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(19.dp))
            Text(
                stringResource(R.string.search),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                "Ctrl F",
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(WarmLine.copy(alpha = 0.5f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

/** The weekdays in display order, starting with [firstDayOfWeek]. */
internal fun weekdaysFrom(firstDayOfWeek: DayOfWeek): List<DayOfWeek> = (0L until 7L).map { firstDayOfWeek.plus(it) }

/** A short weekday name without trailing period, e.g. "Mo" or "Mon". */
internal fun DayOfWeek.shortLabel(locale: java.util.Locale, narrow: Boolean = false): String =
    getDisplayName(if (narrow) TextStyle.NARROW_STANDALONE else TextStyle.SHORT_STANDALONE, locale).trimEnd('.')
