package com.kgs.calendar.ui.desktop

import androidx.compose.animation.AnimatedContent
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.Flow
import com.kgs.calendar.ui.MotionStandard
import com.kgs.calendar.ui.monthMarkersFor
import com.kgs.calendar.ui.MonthDayMarkers
import com.kgs.calendar.domain.model.isMonthSurfaceTaskVisible
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.runtime.produceState
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kgs.calendar.data.local.entity.CollectionEntity
import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.domain.event.displayColor
import com.kgs.calendar.domain.event.endDateInclusive
import com.kgs.calendar.domain.task.displayColor
import com.kgs.calendar.domain.task.isOpen
import com.kgs.calendar.domain.time.toDate
import com.kgs.calendar.domain.time.toTimeText
import com.kgs.calendar.res.LocalAppLocale
import com.kgs.calendar.res.SharedR as R
import com.kgs.calendar.res.stringResource
import com.kgs.calendar.ui.CalendarSourceList
import com.kgs.calendar.ui.CalendarUiState
import com.kgs.calendar.ui.KgsLogoBurstButton
import com.kgs.calendar.ui.MotionEmphasized
import com.kgs.calendar.ui.MotionMedium
import com.kgs.calendar.ui.MotionShort
import com.kgs.calendar.ui.WarmBrown
import com.kgs.calendar.ui.WarmInk
import com.kgs.calendar.ui.WarmLine
import com.kgs.calendar.ui.isDark
import com.kgs.calendar.ui.model.taskDate
import com.kgs.calendar.ui.settingsControlColor
import com.kgs.calendar.ui.time.dayMonthFormatter
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/**
 * The desktop's permanent sidebar: the month with the shown days, the calendars with their
 * switches as in the Android app's drawer, and settings.
 */
@Composable
internal fun DesktopSidebar(
    state: CalendarUiState,
    yearViewOpen: Boolean,
    today: LocalDate,
    observeRange: (LocalDate, LocalDate) -> Flow<Pair<List<EventEntity>, List<TaskEntity>>>,
    onDateSelected: (LocalDate) -> Unit,
    onCollectionVisibleInViews: (String, Boolean) -> Unit,
    onCollectionSettings: (CollectionEntity) -> Unit,
    onGroupSettings: (String) -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxHeight()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(DesktopToolbarHeight)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            KgsLogoBurstButton()
            Text(
                "KGS Calendar",
                fontSize = 20.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.SansSerif,
                color = WarmInk,
                maxLines = 1,
                softWrap = false,
            )
        }
        val scroll = rememberScrollState()
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scroll)
                .padding(bottom = 12.dp),
        ) {
            MiniMonth(
                selectedDate = state.selectedDate,
                visibleDays = state.visibleDays(yearViewOpen),
                firstDayOfWeek = state.firstDayOfWeek,
                today = today,
                taskColorMode = state.taskColorMode,
                observeRange = observeRange,
                onDateSelected = onDateSelected,
                modifier = Modifier.padding(horizontal = 14.dp),
            )
            Spacer(Modifier.height(22.dp))
            SidebarHeading(stringResource(R.string.desktop_calendars))
            CalendarSourceList(
                state = state,
                onCollectionVisibleInViews = onCollectionVisibleInViews,
                onCollectionSettings = onCollectionSettings,
                onGroupSettings = onGroupSettings,
            )
        }
        HorizontalDivider(color = WarmLine.copy(alpha = if (scroll.canScrollForward) 1f else 0f))
        SidebarAction(stringResource(R.string.app_settings), Icons.Default.Settings, onSettings)
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun SidebarHeading(text: String) {
    Text(
        text.uppercase(LocalAppLocale.current),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 11.sp,
        letterSpacing = 0.8.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun SidebarAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (hovered) WarmInk.copy(alpha = 0.06f) else Color.Transparent)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = WarmInk, modifier = Modifier.size(22.dp))
        Text(label, color = WarmInk, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private val MiniMonthRowHeight = 36.dp

/**
 * A small month: the days the calendar shows are banded, and the band glides along as they
 * change; today is filled in the accent color; the dots under a day are its items in their
 * calendars' colors, as in the app's month overview. Its own arrows browse months without
 * changing the calendar; a click selects a day.
 */
@Composable
private fun MiniMonth(
    selectedDate: LocalDate,
    visibleDays: ClosedRange<LocalDate>?,
    firstDayOfWeek: java.time.DayOfWeek,
    today: LocalDate,
    taskColorMode: com.kgs.calendar.data.settings.TaskColorMode,
    observeRange: (LocalDate, LocalDate) -> Flow<Pair<List<EventEntity>, List<TaskEntity>>>,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalAppLocale.current
    var month by remember { mutableStateOf(YearMonth.from(selectedDate)) }
    var direction by remember { mutableStateOf(0) }
    LaunchedEffect(selectedDate) {
        val target = YearMonth.from(selectedDate)
        if (target != month) {
            direction = target.compareTo(month).coerceIn(-1, 1)
            month = target
        }
    }
    // The band keeps its last days while it fades out, e.g. when the year view opens.
    var lastVisibleDays by remember { mutableStateOf(visibleDays) }
    if (visibleDays != null) lastVisibleDays = visibleDays
    val bandAlpha by animateFloatAsState(
        targetValue = if (visibleDays != null) 1f else 0f,
        animationSpec = tween(MotionMedium, easing = MotionStandard),
        label = "miniMonthBandAlpha",
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(settingsControlColor())
            .padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)).replaceFirstChar { it.titlecase(locale) },
                color = WarmInk,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
            SmallIconButton(Icons.Default.ChevronLeft, stringResource(R.string.desktop_previous)) {
                direction = -1
                month = month.minusMonths(1)
            }
            SmallIconButton(Icons.Default.ChevronRight, stringResource(R.string.desktop_next)) {
                direction = 1
                month = month.plusMonths(1)
            }
        }
        Spacer(Modifier.height(6.dp))
        val weekdays = weekdaysFrom(firstDayOfWeek)
        Row(Modifier.fillMaxWidth()) {
            weekdays.forEach { day ->
                Text(
                    day.shortLabel(locale, narrow = true),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        AnimatedContent(
            targetState = month,
            transitionSpec = {
                (slideInHorizontally(tween(MotionMedium, easing = MotionEmphasized)) { it * direction / 4 } + fadeIn(tween(MotionShort))) togetherWith
                    (slideOutHorizontally(tween(MotionShort)) { -it * direction / 4 } + fadeOut(tween(MotionShort)))
            },
            label = "miniMonth",
        ) { shownMonth ->
            val gridStart = shownMonth.atDay(1).with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
            val items by produceState(emptyList<EventEntity>() to emptyList<TaskEntity>(), shownMonth) {
                observeRange(shownMonth.atDay(1), shownMonth.atEndOfMonth().plusDays(1)).collect { value = it }
            }
            val markers = remember(items, shownMonth, taskColorMode) {
                monthMarkersFor(
                    shownMonth,
                    items.first,
                    items.second.filter { isMonthSurfaceTaskVisible(it.isCompleted, it.status) },
                    taskColorMode,
                )
            }
            // The band in day indices of this grid, end exclusive; it moves smoothly between ranges.
            val range = lastVisibleDays
            val bandStart by animateFloatAsState(
                targetValue = range?.let { ChronoUnit.DAYS.between(gridStart, it.start).toFloat().coerceIn(-1f, 43f) } ?: 0f,
                animationSpec = tween(MotionMedium, easing = MotionEmphasized),
                label = "miniMonthBandStart",
            )
            val bandEnd by animateFloatAsState(
                targetValue = range?.let { (ChronoUnit.DAYS.between(gridStart, it.endInclusive) + 1).toFloat().coerceIn(-1f, 43f) } ?: 0f,
                animationSpec = tween(MotionMedium, easing = MotionEmphasized),
                label = "miniMonthBandEnd",
            )
            val bandColor = WarmBrown.copy(alpha = if (MaterialTheme.colorScheme.background.isDark()) 0.22f else 0.16f)
            Box {
                Canvas(Modifier.matchParentSize()) {
                    if (bandAlpha <= 0.001f || bandEnd <= bandStart) return@Canvas
                    val cell = size.width / 7f
                    val rowHeight = MiniMonthRowHeight.toPx()
                    val inset = 2.dp.toPx()
                    val radius = (rowHeight - inset * 2) / 2f
                    for (week in 0 until 6) {
                        val from = maxOf(bandStart, week * 7f)
                        val to = minOf(bandEnd, week * 7f + 7f)
                        if (to - from <= 0.01f) continue
                        val left = (from - week * 7f) * cell + inset
                        val right = (to - week * 7f) * cell - inset
                        drawRoundRect(
                            color = bandColor.copy(alpha = bandColor.alpha * bandAlpha),
                            topLeft = Offset(left, week * rowHeight + inset),
                            size = Size((right - left).coerceAtLeast(0f), rowHeight - inset * 2),
                            cornerRadius = CornerRadius(radius, radius),
                        )
                    }
                }
                Column {
                    for (week in 0 until 6) {
                        Row(Modifier.fillMaxWidth()) {
                            for (dayIndex in 0 until 7) {
                                val date = gridStart.plusDays(week * 7L + dayIndex)
                                MiniMonthDay(
                                    date = date,
                                    inMonth = YearMonth.from(date) == shownMonth,
                                    today = date == today,
                                    selected = date == selectedDate,
                                    markers = markers[date],
                                    onClick = { onDateSelected(date) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniMonthDay(
    date: LocalDate,
    inMonth: Boolean,
    today: Boolean,
    selected: Boolean,
    markers: MonthDayMarkers?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val accentText = if (WarmBrown.isDark()) Color.White else Color(0xFF1C1A18)
    Column(
        modifier = modifier
            .height(MiniMonthRowHeight)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(
                    when {
                        today -> WarmBrown
                        hovered -> WarmInk.copy(alpha = 0.08f)
                        else -> Color.Transparent
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                date.dayOfMonth.toString(),
                color = when {
                    today -> accentText
                    !inMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                    else -> WarmInk
                },
                fontSize = 13.sp,
                lineHeight = 15.sp,
                fontWeight = if (today || selected) FontWeight.Bold else FontWeight.Medium,
            )
        }
        // The day's items in their calendars' colors; a plus when there are more than fit.
        Row(
            modifier = Modifier.height(6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            markers?.colors?.forEach { color ->
                Box(Modifier.size(4.dp).clip(CircleShape).background(Color(color)))
            }
            if (markers?.hasMore == true) {
                Text(
                    "+",
                    color = WarmInk,
                    fontSize = 8.sp,
                    lineHeight = 8.sp,
                    modifier = Modifier.offset { IntOffset(0, -1) },
                )
            }
        }
    }
}

@Composable
private fun SmallIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(if (hovered) WarmInk.copy(alpha = 0.08f) else Color.Transparent)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = WarmInk, modifier = Modifier.size(20.dp))
    }
}
