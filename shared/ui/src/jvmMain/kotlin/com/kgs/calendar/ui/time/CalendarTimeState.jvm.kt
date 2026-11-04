package com.kgs.calendar.ui.time

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import com.kgs.calendar.time.nowMillis
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.TimeZone

/**
 * The desktop has no clock broadcasts: a check every few seconds notices the next minute, and a
 * changed clock or time zone (after sleep or travel).
 */
@Composable
actual fun rememberCalendarTimeState(): CalendarTimeSnapshot =
    produceState(initialValue = CalendarTimeSnapshot.now()) {
        var lastZone = TimeZone.getDefault().id
        var lastMinute = nowMillis() / 60_000L
        while (isActive) {
            delay(CHECK_INTERVAL_MILLIS)
            // Java caches the default zone; forget it so a changed Windows time zone is picked up.
            TimeZone.setDefault(null)
            val zone = TimeZone.getDefault().id
            val minute = nowMillis() / 60_000L
            if (zone != lastZone || minute != lastMinute) {
                lastZone = zone
                lastMinute = minute
                value = CalendarTimeSnapshot.now(value.revision + 1)
            }
        }
    }.value

private const val CHECK_INTERVAL_MILLIS = 2_000L
