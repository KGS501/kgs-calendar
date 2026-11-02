package com.kgs.calendar.ui.desktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kgs.calendar.domain.model.CalendarViewMode
import com.kgs.calendar.domain.time.toDate
import com.kgs.calendar.domain.event.endDateInclusive
import com.kgs.calendar.ui.CalendarUiState
import com.kgs.calendar.ui.CalendarViewModel
import com.kgs.calendar.ui.DetailSheet
import com.kgs.calendar.ui.MotionMedium
import com.kgs.calendar.ui.MotionShort
import com.kgs.calendar.ui.MotionStandard
import com.kgs.calendar.ui.SettingsDestination
import com.kgs.calendar.ui.WarmLine
import com.kgs.calendar.ui.model.taskDate
import com.kgs.calendar.ui.shell.CalendarShellUiState
import java.time.LocalDate
import java.time.YearMonth

/** Below this window width the desktop uses the phone layout: the sidebar would leave the calendar too little room. */
internal const val DesktopFrameMinWidthDp = 760

/**
 * The desktop window: the permanent sidebar on the left, the toolbar above the calendar, and the
 * calendar itself ([content]) or the year view. Sheets and overlays stay above it as on Android.
 */
@Composable
internal fun DesktopFrame(
    viewModel: CalendarViewModel,
    state: CalendarUiState,
    shell: CalendarShellUiState,
    today: LocalDate,
    tasksNeedAttention: Boolean,
    onViewSelected: (CalendarViewMode) -> Unit,
    content: @Composable () -> Unit,
) {
    val yearViewOpen = shell.yearViewOpen
    fun showDate(date: LocalDate) {
        viewModel.selectDate(date)
        shell.requestDate(date)
    }
    val upcoming by remember(today) { viewModel.observeRange(today, today.plusDays(7)) }
        .collectAsState(initial = emptyList<com.kgs.calendar.data.local.entity.EventEntity>() to emptyList())
    val markedMonth = YearMonth.from(state.selectedDate)
    val marked by remember(markedMonth) {
        viewModel.observeRange(markedMonth.atDay(1).minusDays(7), markedMonth.atEndOfMonth().plusDays(15))
    }.collectAsState(initial = emptyList<com.kgs.calendar.data.local.entity.EventEntity>() to emptyList())
    val markedDays = remember(marked) {
        buildSet {
            marked.first.forEach { event ->
                var day = event.startsAtMillis.toDate()
                val end = event.endDateInclusive()
                while (!day.isAfter(end) && size < 400) {
                    add(day)
                    day = day.plusDays(1)
                }
            }
            marked.second.forEach { task -> task.taskDate()?.let(::add) }
        }
    }
    Row(Modifier.fillMaxSize()) {
        DesktopSidebar(
            state = state,
            yearViewOpen = yearViewOpen,
            today = today,
            upcomingEvents = upcoming.first,
            upcomingTasks = upcoming.second,
            markedDays = markedDays,
            onDateSelected = { date ->
                shell.yearViewOpen = false
                showDate(date)
            },
            onEventClick = { shell.openDetail(DetailSheet.Event(it)) },
            onTaskClick = { shell.openTaskDetail(it) },
            onCollectionVisibleInViews = viewModel.settings::setCollectionVisibleInViews,
            onCollectionSettings = shell::editCollection,
            onGroupSettings = shell::editGroup,
            onSettings = { shell.openSettings(SettingsDestination.Main) },
        )
        VerticalDivider(color = WarmLine.copy(alpha = 0.6f), modifier = Modifier.fillMaxHeight().width(1.dp))
        Column(Modifier.weight(1f)) {
            DesktopToolbar(
                state = state,
                yearViewOpen = yearViewOpen,
                tasksNeedAttention = tasksNeedAttention,
                onStep = { direction -> showDate(state.steppedDate(direction, yearViewOpen)) },
                onToday = {
                    viewModel.today()
                    shell.requestDate(LocalDate.now())
                },
                onViewSelected = { view ->
                    shell.yearViewOpen = view == DesktopView.Year
                    when (view) {
                        DesktopView.Day -> onViewSelected(CalendarViewMode.Day)
                        DesktopView.Week -> onViewSelected(CalendarViewMode.ThreeDay)
                        DesktopView.Month -> onViewSelected(CalendarViewMode.Month)
                        DesktopView.Agenda -> onViewSelected(CalendarViewMode.Agenda)
                        DesktopView.Year -> Unit
                    }
                },
                onSearch = shell::openSearch,
                onTasks = shell::openTaskDrawer,
            )
            Box(Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = yearViewOpen,
                    transitionSpec = {
                        (fadeIn(tween(MotionMedium, easing = MotionStandard)) + scaleIn(tween(MotionMedium, easing = MotionStandard), initialScale = 0.985f)) togetherWith
                            fadeOut(tween(MotionShort))
                    },
                    label = "desktopYearView",
                ) { showYear ->
                    if (showYear) {
                        val year = state.selectedDate.year
                        val yearItems by remember(year) {
                            viewModel.observeRange(LocalDate.of(year, 1, 1), LocalDate.of(year + 1, 1, 1))
                        }.collectAsState(initial = emptyList<com.kgs.calendar.data.local.entity.EventEntity>() to emptyList())
                        DesktopYearView(
                            year = year,
                            firstDayOfWeek = state.firstDayOfWeek,
                            today = today,
                            events = yearItems.first,
                            tasks = yearItems.second,
                            taskColorMode = state.taskColorMode,
                            onOpenDay = { date ->
                                shell.yearViewOpen = false
                                viewModel.selectDate(date)
                                onViewSelected(CalendarViewMode.Day)
                            },
                            onEventClick = { shell.openDetail(DetailSheet.Event(it)) },
                            onTaskClick = { shell.openTaskDetail(it) },
                        )
                    } else {
                        content()
                    }
                }
            }
        }
    }
}
