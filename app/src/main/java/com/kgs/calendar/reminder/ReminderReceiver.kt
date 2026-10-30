package com.kgs.calendar.reminder

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.kgs.calendar.KgsCalendarApplication
import com.kgs.calendar.R
import com.kgs.calendar.domain.model.CalendarOccurrenceId
import com.kgs.calendar.navigation.CalendarLaunchTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Fired by AlarmManager when a reminder is due. Posts a notification for the event/task.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_MARK_TASK_DONE) {
            markTaskDone(context, intent)
            return
        }
        if (intent.action == ACTION_SNOOZE) {
            snooze(context, intent)
            return
        }
        val title = intent.getStringExtra(EXTRA_TITLE) ?: context.getString(R.string.reminder_channel_name)
        val body = intent.getStringExtra(EXTRA_BODY).orEmpty()
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, title.hashCode())
        val notificationTag = intent.getStringExtra(EXTRA_NOTIFICATION_TAG) ?: "kgs-reminder"
        val target = CalendarLaunchTarget.readFrom(intent) ?: return
        val notificationKey = ReminderNotificationKey(notificationTag, notificationId)
        val alarmRequestCode = intent.takeIf { it.hasExtra(EXTRA_ALARM_REQUEST_CODE) }?.getIntExtra(EXTRA_ALARM_REQUEST_CODE, 0)
        val snoozed = intent.getBooleanExtra(EXTRA_SNOOZED, false)

        ReminderScheduler.ensureChannel(context)

        val contentIntent = ReminderIntents.contentPendingIntent(context, target, notificationKey)

        val notificationBuilder = NotificationCompat.Builder(context, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)

        if (body.isNotBlank()) {
            notificationBuilder
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        }
        notificationBuilder.addAction(
            NotificationCompat.Action.Builder(
                R.drawable.ic_notification,
                context.getString(R.string.reminder_snooze),
                ReminderIntents.snoozeActionPendingIntent(context, title, body.takeIf { it.isNotBlank() }, notificationKey, target),
            ).build(),
        )
        val taskOccurrence = target.occurrence as? CalendarOccurrenceId.Task
        if (taskOccurrence != null) {
            notificationBuilder.addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_notification,
                    context.getString(R.string.mark_done),
                    ReminderIntents.markDonePendingIntent(context, taskOccurrence, notificationKey),
                ).build(),
            )
        }
        val notification = notificationBuilder.build()
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Recorded first, so the alarm is never caught up again as a missed reminder.
                runCatching {
                    val registry = KgsCalendarApplication.graph(appContext).reminderRegistry
                    alarmRequestCode?.let { registry.markDelivered(listOf(it), System.currentTimeMillis()) }
                    if (snoozed && alarmRequestCode != null) registry.removeSnoozed(alarmRequestCode)
                }
                // The alarm may predate hiding or deactivating the item's calendar.
                val occurrence = target.occurrence
                val stillVisible = occurrence == null ||
                    runCatching { ReminderScheduler.isStillVisible(appContext, occurrence) }.getOrDefault(true)
                if (stillVisible) postNotification(appContext, target, notificationKey, notification)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun postNotification(
        context: Context,
        target: CalendarLaunchTarget,
        notificationKey: ReminderNotificationKey,
        notification: Notification,
    ) {
        // POST_NOTIFICATIONS may be revoked; notify() is a no-op then rather than crashing.
        runCatching {
            if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                NotificationManagerCompat.from(context).notify(notificationKey.tag, notificationKey.id, notification)
                runCatching {
                    KgsCalendarApplication.graph(context).reminderRegistry.recordNotification(
                        ActiveReminderNotification(target.occurrence ?: return@runCatching, notificationKey),
                    )
                }
            }
        }
    }

    /** Puts the reminder off by [SNOOZE_MINUTES]; the snooze is kept so a restart sets it again. */
    private fun snooze(context: Context, intent: Intent) {
        val title = intent.getStringExtra(EXTRA_TITLE) ?: return
        val encodedTarget = intent.getStringExtra(CalendarLaunchTarget.EXTRA_TARGET) ?: return
        val notificationTag = intent.getStringExtra(EXTRA_NOTIFICATION_TAG) ?: "kgs-reminder"
        val notificationKey = ReminderNotificationKey(notificationTag, intent.getIntExtra(EXTRA_NOTIFICATION_ID, title.hashCode()))
        NotificationManagerCompat.from(context).cancel(notificationKey.tag, notificationKey.id)
        val record = SnoozedReminder(
            alarmRequestCode = ReminderIntents.snoozedRequestCode(notificationKey),
            triggerAtMillis = System.currentTimeMillis() + SNOOZE_MINUTES * 60_000L,
            title = title,
            body = intent.getStringExtra(EXTRA_BODY),
            notificationKey = notificationKey,
            encodedTarget = encodedTarget,
        )
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                runCatching { KgsCalendarApplication.graph(appContext).reminderRegistry.addSnoozed(record) }
                ReminderScheduler.scheduleSnoozed(appContext, record)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun markTaskDone(context: Context, intent: Intent) {
        val occurrenceId = (CalendarLaunchTarget.readFrom(intent)?.occurrence as? CalendarOccurrenceId.Task) ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, occurrenceId.stableKey.hashCode())
        val notificationTag = intent.getStringExtra(EXTRA_NOTIFICATION_TAG) ?: "kgs-reminder"
        NotificationManagerCompat.from(context).cancel(notificationTag, notificationId)
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                runCatching {
                    val graph = KgsCalendarApplication.graph(appContext)
                    graph.taskMutationCoordinator.setStatus(occurrenceId.resourceHref, "COMPLETED", occurrenceId)
                }
                NotificationManagerCompat.from(appContext).cancel(notificationTag, notificationId)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_MARK_TASK_DONE = "com.kgs.calendar.reminder.MARK_TASK_DONE"
        const val ACTION_SNOOZE = "com.kgs.calendar.reminder.SNOOZE"
        const val EXTRA_ALARM_REQUEST_CODE = "extra_alarm_request_code"
        const val EXTRA_SNOOZED = "extra_snoozed"
        const val SNOOZE_MINUTES = 10L
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_BODY = "extra_body"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val EXTRA_NOTIFICATION_TAG = "extra_notification_tag"
    }
}
