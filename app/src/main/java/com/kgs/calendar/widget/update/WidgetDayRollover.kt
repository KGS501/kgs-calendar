package com.kgs.calendar.widget.update

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.kgs.calendar.widget.KgsWidgetRefreshReceiver
import java.time.ZonedDateTime

/**
 * Wakes the widgets shortly after local midnight so "today" moves on even when no data changes.
 * The alarm is not a wakeup alarm: a sleeping device gets it as soon as it wakes, which is when a
 * home screen can be seen again. Scheduling replaces the pending alarm, so it is safe to repeat.
 */
internal object WidgetDayRollover {
    const val ACTION_DAY_ROLLOVER = "com.kgs.calendar.widget.DAY_ROLLOVER"

    /** A little past midnight, so the renderer's LocalDate.now() is already the new day. */
    private const val ROLLOVER_DELAY_MILLIS = 5_000L
    private const val ROLLOVER_WINDOW_MILLIS = 10L * 60L * 1000L

    fun schedule(context: Context, now: ZonedDateTime = ZonedDateTime.now()) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        runCatching {
            // set() may be deferred by up to an hour on Android 12+; 10 minutes is the shortest
            // window an inexact alarm gets.
            alarmManager.setWindow(
                AlarmManager.RTC,
                nextRolloverAtMillis(now),
                ROLLOVER_WINDOW_MILLIS,
                pendingIntent(context.applicationContext),
            )
        }
    }

    internal fun nextRolloverAtMillis(now: ZonedDateTime): Long =
        now.toLocalDate()
            .plusDays(1)
            .atStartOfDay(now.zone)
            .toInstant()
            .toEpochMilli() + ROLLOVER_DELAY_MILLIS

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, KgsWidgetRefreshReceiver::class.java).setAction(ACTION_DAY_ROLLOVER),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
