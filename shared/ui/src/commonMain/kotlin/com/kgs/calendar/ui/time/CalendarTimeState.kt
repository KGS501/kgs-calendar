package com.kgs.calendar.ui.time

import com.kgs.calendar.time.*
import kotlinx.datetime.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import com.kgs.calendar.time.ZonedDateTime

data class CalendarTimeSnapshot(
    val today: LocalDate,
    val nowMinute: LocalTime,
    val revision: Long = 0,
) {
    companion object {
        fun now(revision: Long = 0): CalendarTimeSnapshot {
            val now = ZonedDateTime.now()
            return CalendarTimeSnapshot(
                today = now.toLocalDate(),
                nowMinute = now.toLocalTime().withSecond(0).withNano(0),
                revision = revision,
            )
        }
    }
}

val LocalCalendarTimeSnapshot = compositionLocalOf { CalendarTimeSnapshot.now() }

/** The current day and minute, refreshed every minute and whenever the clock, date or zone changes. */
@Composable
expect fun rememberCalendarTimeState(): CalendarTimeSnapshot
