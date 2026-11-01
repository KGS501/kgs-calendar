package com.kgs.calendar.ui.time

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

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

/**
 * Today and the current minute, refreshed every minute and whenever the clock, date or time zone
 * changes, so views roll over at midnight while open.
 */
@Composable
expect fun rememberCalendarTimeState(): CalendarTimeSnapshot
