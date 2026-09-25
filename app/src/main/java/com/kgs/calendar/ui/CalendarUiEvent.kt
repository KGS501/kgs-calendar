package com.kgs.calendar.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.local.entity.TaskEntity
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * One-off commands from widget, notification, external and foreground launches. Delivered through
 * [CalendarViewModel.uiEvents] and handled exactly once by the single collector in [KgsCalendarApp].
 */
sealed interface CalendarUiEvent {
    data class CreateEvent(val date: LocalDate) : CalendarUiEvent

    data class CreateTask(val date: LocalDate, val scheduledForDay: Boolean) : CalendarUiEvent

    data class OpenEvent(val event: EventEntity) : CalendarUiEvent

    data class OpenTask(val task: TaskEntity) : CalendarUiEvent

    /** The app came back after a long background period and recentred on today. */
    data object ForegroundRecentered : CalendarUiEvent
}

/**
 * Collects [events] while the UI is at least started, like the state collection. Collecting on
 * [Dispatchers.Main.immediate] hands every received event to [onEvent] before a lifecycle stop can
 * cancel the collector, so none is lost; unreceived events stay buffered in the channel.
 */
@Composable
internal fun CollectCalendarUiEvents(
    events: Flow<CalendarUiEvent>,
    onEvent: (CalendarUiEvent) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnEvent by rememberUpdatedState(onEvent)
    LaunchedEffect(events, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            withContext(Dispatchers.Main.immediate) {
                events.collect { currentOnEvent(it) }
            }
        }
    }
}
