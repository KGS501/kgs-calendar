package com.kgs.calendar.ui.time

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.ZoneId
import java.util.TimeZone

/**
 * The desktop has no clock broadcasts: a ticker aligned to the minute refreshes the snapshot, and a
 * short check every few seconds notices a changed clock or time zone (e.g. after sleep or travel).
 */
@Composable
actual fun rememberCalendarTimeState(): CalendarTimeSnapshot =
    produceState(initialValue = CalendarTimeSnapshot.now()) {
        var lastZone = ZoneId.systemDefault()
        var lastMinute = System.currentTimeMillis() / 60_000L
        while (isActive) {
            delay(CHECK_INTERVAL_MILLIS)
            // Java caches the default zone; forget it so a changed Windows time zone is picked up.
            TimeZone.setDefault(null)
            val zone = ZoneId.systemDefault()
            val minute = System.currentTimeMillis() / 60_000L
            if (zone != lastZone || minute != lastMinute) {
                lastZone = zone
                lastMinute = minute
                value = CalendarTimeSnapshot.now(value.revision + 1)
            }
        }
    }.value

private const val CHECK_INTERVAL_MILLIS = 2_000L
