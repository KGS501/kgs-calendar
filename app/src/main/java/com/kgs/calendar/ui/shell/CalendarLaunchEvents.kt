package com.kgs.calendar.ui

import androidx.compose.runtime.Composable
import com.kgs.calendar.navigation.SharedEventDraft
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** Routes widget, notification, share and foreground launch commands to the shell. */
@Composable
internal fun CalendarLaunchEventsEffect(
    events: Flow<CalendarUiEvent>,
    onCreateEvent: (LocalDate) -> Unit,
    onCreateSharedEvent: (SharedEventDraft) -> Unit,
    onCreateTask: (date: LocalDate, scheduledForDay: Boolean) -> Unit,
    onOpenDetail: (DetailSheet) -> Unit,
    onForegroundRecentered: () -> Unit,
) {
    CollectCalendarUiEvents(events) { event ->
        when (event) {
            is CalendarUiEvent.CreateEvent -> onCreateEvent(event.date)
            is CalendarUiEvent.CreateSharedEvent -> onCreateSharedEvent(event.draft)
            is CalendarUiEvent.CreateTask -> onCreateTask(event.date, event.scheduledForDay)
            is CalendarUiEvent.OpenEvent -> onOpenDetail(DetailSheet.Event(event.event))
            is CalendarUiEvent.OpenTask -> onOpenDetail(DetailSheet.Task(event.task))
            CalendarUiEvent.ForegroundRecentered -> onForegroundRecentered()
        }
    }
}
