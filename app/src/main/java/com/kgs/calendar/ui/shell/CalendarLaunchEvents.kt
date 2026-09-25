package com.kgs.calendar.ui

import androidx.compose.runtime.Composable
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** Routes widget, notification and foreground launch commands to the shell. */
@Composable
internal fun CalendarLaunchEventsEffect(
    events: Flow<CalendarUiEvent>,
    onCreateEvent: (LocalDate) -> Unit,
    onCreateTask: (date: LocalDate, scheduledForDay: Boolean) -> Unit,
    onOpenDetail: (DetailSheet) -> Unit,
    onForegroundRecentered: () -> Unit,
) {
    CollectCalendarUiEvents(events) { event ->
        when (event) {
            is CalendarUiEvent.CreateEvent -> onCreateEvent(event.date)
            is CalendarUiEvent.CreateTask -> onCreateTask(event.date, event.scheduledForDay)
            is CalendarUiEvent.OpenEvent -> onOpenDetail(DetailSheet.Event(event.event))
            is CalendarUiEvent.OpenTask -> onOpenDetail(DetailSheet.Task(event.task))
            CalendarUiEvent.ForegroundRecentered -> onForegroundRecentered()
        }
    }
}
