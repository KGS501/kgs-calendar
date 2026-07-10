package com.kgs.calendar.reminder

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.kgs.calendar.MainActivity
import com.kgs.calendar.domain.model.CalendarOccurrenceId
import com.kgs.calendar.domain.model.CalendarViewMode
import com.kgs.calendar.navigation.CalendarLaunchAction
import com.kgs.calendar.navigation.CalendarLaunchTarget
import java.time.Instant
import java.time.ZoneId

object ReminderIntents {
    fun contentIntent(
        context: Context,
        plan: ReminderAlarmPlan,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Intent = contentIntent(context, launchTarget(plan, zoneId))

    fun contentIntent(context: Context, target: CalendarLaunchTarget): Intent =
        Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            target.writeTo(this)
        }

    fun alarmPendingIntent(context: Context, plan: ReminderAlarmPlan): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            plan.alarmRequestCode,
            alarmIntent(context, plan),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun contentPendingIntent(
        context: Context,
        target: CalendarLaunchTarget,
        notificationKey: ReminderNotificationKey,
    ): PendingIntent = PendingIntent.getActivity(
        context,
        notificationKey.id,
        contentIntent(context, target),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun markDonePendingIntent(
        context: Context,
        occurrenceId: CalendarOccurrenceId.Task,
        notificationKey: ReminderNotificationKey,
    ): PendingIntent {
        val target = CalendarLaunchTarget(
            action = CalendarLaunchAction.OpenOccurrence,
            occurrence = occurrenceId,
        )
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_MARK_TASK_DONE
            putExtra(CalendarLaunchTarget.EXTRA_TARGET, target.encode())
            putExtra(ReminderReceiver.EXTRA_NOTIFICATION_TAG, notificationKey.tag)
            putExtra(ReminderReceiver.EXTRA_NOTIFICATION_ID, notificationKey.id)
        }
        return PendingIntent.getBroadcast(
            context,
            notificationKey.id xor occurrenceId.stableKey.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun alarmIntent(context: Context, plan: ReminderAlarmPlan): Intent {
        val target = launchTarget(plan, ZoneId.systemDefault())
        return Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_TITLE, plan.title)
            plan.body?.let { putExtra(ReminderReceiver.EXTRA_BODY, it) }
            putExtra(ReminderReceiver.EXTRA_NOTIFICATION_TAG, plan.notificationKey.tag)
            putExtra(ReminderReceiver.EXTRA_NOTIFICATION_ID, plan.notificationKey.id)
            putExtra(CalendarLaunchTarget.EXTRA_TARGET, target.encode())
        }
    }

    private fun launchTarget(plan: ReminderAlarmPlan, zoneId: ZoneId): CalendarLaunchTarget =
        CalendarLaunchTarget(
            date = Instant.ofEpochMilli(plan.occurrenceId.recurrenceIdMillis).atZone(zoneId).toLocalDate(),
            viewMode = CalendarViewMode.Day,
            action = CalendarLaunchAction.OpenOccurrence,
            occurrence = plan.occurrenceId,
        )
}
