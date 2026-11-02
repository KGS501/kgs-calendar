package com.kgs.calendar.ui.desktop

import androidx.compose.animation.AnimatedContent
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

internal val DesktopSidebarWidth = 296.dp

/**
 * The desktop's permanent sidebar: the month with the shown days, what is coming up in the next
 * days, the calendars with their switches, and settings.
 */
@Composable
internal fun DesktopSidebar(
    state: CalendarUiState,
    yearViewOpen: Boolean,
    today: LocalDate,
    upcomingEvents: List<EventEntity>,
    upcomingTasks: List<TaskEntity>,
    markedDays: Set<LocalDate>,
    onDateSelected: (LocalDate) -> Unit,
    onEventClick: (EventEntity) -> Unit,
    onTaskClick: (TaskEntity) -> Unit,
    onCollectionVisibleInViews: (String, Boolean) -> Unit,
    onCollectionSettings: (CollectionEntity) -> Unit,
    onGroupSettings: (String) -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(DesktopSidebarWidth)
            .fillMaxHeight(),
    ) {
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
                markedDays = markedDays,
                onDateSelected = onDateSelected,
                modifier = Modifier.padding(horizontal = 14.dp),
            )
            Spacer(Modifier.height(22.dp))
            SidebarHeading(stringResource(R.string.desktop_upcoming))
            UpcomingList(
                today = today,
                events = upcomingEvents,
                tasks = upcomingTasks,
                taskColorMode = state.taskColorMode,
                onEventClick = onEventClick,
                onTaskClick = onTaskClick,
            )
            Spacer(Modifier.height(18.dp))
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
        Text(label, color = WarmInk, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

/**
 * A small month: the shown days are banded, today is filled in the accent color, days with items
 * have a dot. Its own arrows browse months without changing the calendar; a click selects a day.
 */
@Composable
private fun MiniMonth(
    selectedDate: LocalDate,
    visibleDays: ClosedRange<LocalDate>?,
    firstDayOfWeek: java.time.DayOfWeek,
    today: LocalDate,
    markedDays: Set<LocalDate>,
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
                                band = visibleDays?.let { range -> date in range }?.let { inRange ->
                                    if (!inRange) null else Band(
                                        start = date == visibleDays.start || dayIndex == 0,
                                        end = date == visibleDays.endInclusive || dayIndex == 6,
                                    )
                                },
                                marked = date in markedDays,
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

private class Band(val start: Boolean, val end: Boolean)

@Composable
private fun MiniMonthDay(
    date: LocalDate,
    inMonth: Boolean,
    today: Boolean,
    selected: Boolean,
    band: Band?,
    marked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val bandColor = WarmBrown.copy(alpha = if (MaterialTheme.colorScheme.background.isDark()) 0.22f else 0.16f)
    Box(
        modifier = modifier
            .height(34.dp)
            .padding(vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (band != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(start = if (band.start) 3.dp else 0.dp, end = if (band.end) 3.dp else 0.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = if (band.start) 15.dp else 0.dp,
                            bottomStart = if (band.start) 15.dp else 0.dp,
                            topEnd = if (band.end) 15.dp else 0.dp,
                            bottomEnd = if (band.end) 15.dp else 0.dp,
                        ),
                    )
                    .background(bandColor),
            )
        }
        val accentText = if (WarmBrown.isDark()) Color.White else Color(0xFF1C1A18)
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(
                    when {
                        today -> WarmBrown
                        hovered -> WarmInk.copy(alpha = 0.08f)
                        else -> Color.Transparent
                    },
                )
                .hoverable(interaction)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick),
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
                fontWeight = if (today || selected) FontWeight.Bold else FontWeight.Medium,
            )
            if (marked && !today) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 3.dp)
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(if (inMonth) WarmBrown else WarmBrown.copy(alpha = 0.4f)),
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

/** The next days with their all-day items, timed events and open tasks, like a short agenda. */
@Composable
private fun UpcomingList(
    today: LocalDate,
    events: List<EventEntity>,
    tasks: List<TaskEntity>,
    taskColorMode: com.kgs.calendar.data.settings.TaskColorMode,
    onEventClick: (EventEntity) -> Unit,
    onTaskClick: (TaskEntity) -> Unit,
) {
    val locale = LocalAppLocale.current
    val days = remember(today, events, tasks) {
        (0L until UpcomingDays).map { offset ->
            val day = today.plusDays(offset)
            val dayEvents = events
                .filter { event -> !day.isBefore(event.startsAtMillis.toDate()) && !day.isAfter(event.endDateInclusive()) }
                .sortedWith(compareBy<EventEntity> { !it.allDay }.thenBy { it.startsAtMillis }.thenBy { it.title })
            val dayTasks = tasks.filter { it.isOpen() && it.taskDate() == day }.sortedBy { it.dueAtMillis ?: it.startAtMillis }
            Triple(day, dayEvents, dayTasks)
        }.filter { (day, dayEvents, dayTasks) -> day == today || dayEvents.isNotEmpty() || dayTasks.isNotEmpty() }
    }
    if (days.all { (_, dayEvents, dayTasks) -> dayEvents.isEmpty() && dayTasks.isEmpty() }) {
        Text(
            stringResource(R.string.desktop_nothing_upcoming),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
        )
        return
    }
    val dateFormatter = remember(locale) { dayMonthFormatter(locale, weekday = "EEE") }
    days.forEach { (day, dayEvents, dayTasks) ->
        val prefix = when (day) {
            today -> stringResource(R.string.today)
            today.plusDays(1) -> stringResource(R.string.desktop_tomorrow)
            else -> null
        }
        Row(
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 10.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (prefix != null) {
                Text(prefix, color = if (day == today) WarmBrown else WarmInk, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Text(day.format(dateFormatter), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        dayEvents.forEach { event -> UpcomingEventRow(event, day, onClick = { onEventClick(event) }) }
        dayTasks.forEach { task -> UpcomingTaskRow(task, taskColorMode, onClick = { onTaskClick(task) }) }
    }
}

private const val UpcomingDays = 7L

@Composable
private fun UpcomingEventRow(event: EventEntity, day: LocalDate, onClick: () -> Unit) {
    val color = Color(event.displayColor())
    val timedOnThisDay = !event.allDay && event.startsAtMillis.toDate() == day
    UpcomingRow(onClick = onClick) {
        if (timedOnThisDay) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Column(Modifier.weight(1f)) {
                Text(event.title, color = WarmInk, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${event.startsAtMillis.toTimeText()} – ${event.endsAtMillis.toTimeText()}" +
                        event.location?.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            val onColor = if (color.isDark()) Color.White else Color(0xFF1C1A18)
            Text(
                event.title,
                color = onColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(color)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

@Composable
private fun UpcomingTaskRow(task: TaskEntity, taskColorMode: com.kgs.calendar.data.settings.TaskColorMode, onClick: () -> Unit) {
    UpcomingRow(onClick = onClick) {
        Icon(
            Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = Color(task.displayColor(taskColorMode)),
            modifier = Modifier.size(15.dp),
        )
        Text(task.title, color = WarmInk, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        val due = task.dueAtMillis
        if (task.dueHasTime && due != null) {
            Text(due.toTimeText(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
    }
}

@Composable
private fun UpcomingRow(onClick: () -> Unit, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (hovered) WarmInk.copy(alpha = 0.06f) else Color.Transparent)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}
