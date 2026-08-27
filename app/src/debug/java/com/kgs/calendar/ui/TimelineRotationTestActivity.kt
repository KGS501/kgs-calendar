package com.kgs.calendar.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import com.kgs.calendar.KgsCalendarApplication
import com.kgs.calendar.data.settings.AppThemeMode
import com.kgs.calendar.domain.model.CalendarViewMode
import com.kgs.calendar.ui.theme.KgsCalendarTheme
import com.kgs.calendar.ui.time.CalendarTimeSnapshot
import com.kgs.calendar.ui.time.LocalCalendarTimeSnapshot
import java.time.LocalDate
import java.time.LocalTime

class TimelineRotationTestActivity : ComponentActivity() {
    private val viewportMemory
        get() = (application as KgsCalendarApplication).appGraph.timelineViewportMemory

    internal fun rememberedTopMinute(isLandscape: Boolean): Float =
        viewportMemory.topMinute(isLandscape)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val date = LocalDate.of(2026, 8, 21)
        setContent {
            CompositionLocalProvider(
                LocalCalendarTimeSnapshot provides CalendarTimeSnapshot(date, LocalTime.NOON),
            ) {
                KgsCalendarTheme(
                    themeMode = AppThemeMode.KgsBlue,
                    darkTheme = false,
                    priorityAnimationsEnabled = false,
                ) {
                    CalendarShell(
                        state = CalendarUiState(
                            selectedDate = date,
                            selectedView = CalendarViewMode.ThreeDay,
                            multiDayCount = 3,
                            priorityAnimationsEnabled = false,
                            portraitTimelineHourHeightDp = 72f,
                            landscapeTimelineHourHeightDp = 60f,
                        ),
                        onMenu = {},
                        onDateSelected = {},
                        onViewSelected = {},
                        onMultiDayCountChanged = {},
                        onToday = {},
                        onSearch = {},
                        onTasks = {},
                        onTaskStatusChanged = { _, _ -> },
                        onEventMoved = { _, _, _, _, _ -> },
                        onTaskMoved = { _, _, _, _, _ -> },
                        onEventMovedAllDay = { _, _, _ -> },
                        onTaskMovedAllDay = { _, _, _ -> },
                        onSlotSelected = { _, _ -> },
                        onAllDaySlotSelected = {},
                        draftEvent = null,
                        onDraftEventChanged = {},
                        onDraftInteraction = {},
                        onDraftTap = {},
                        timelineBottomInset = 0.dp,
                        onDetail = {},
                        overdueTasksExpanded = false,
                        onOverdueTasksExpandedChange = {},
                        timelineViewportMemory = viewportMemory,
                    )
                }
            }
        }
    }
}
