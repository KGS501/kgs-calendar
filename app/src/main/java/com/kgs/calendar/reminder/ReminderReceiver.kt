package com.kgs.calendar.reminder

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
        val title = intent.getStringExtra(EXTRA_TITLE) ?: context.getString(R.string.reminder_channel_name)
        val body = intent.getStringExtra(EXTRA_BODY).orEmpty()
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, title.hashCode())
        val notificationTag = intent.getStringExtra(EXTRA_NOTIFICATION_TAG) ?: "kgs-reminder"
        val target = CalendarLaunchTarget.readFrom(intent) ?: return
        val notificationKey = ReminderNotificationKey(notificationTag, notificationId)

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

        // POST_NOTIFICATIONS may be revoked; notify() is a no-op then rather than crashing.
        runCatching {
            if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                NotificationManagerCompat.from(context).notify(notificationTag, notificationId, notification)
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    runCatching {
                        KgsCalendarApplication.graph(context.applicationContext).reminderRegistry.recordNotification(
                            ActiveReminderNotification(target.occurrence ?: return@runCatching, notificationKey),
                        )
                    }
                    pendingResult.finish()
                }
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
            runCatching {
                val graph = KgsCalendarApplication.graph(appContext)
                graph.taskMutationCoordinator.setStatus(occurrenceId.resourceHref, "COMPLETED", occurrenceId)
            }
            NotificationManagerCompat.from(appContext).cancel(notificationTag, notificationId)
            pendingResult.finish()
        }
    }

    companion object {
        const val ACTION_MARK_TASK_DONE = "com.kgs.calendar.reminder.MARK_TASK_DONE"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_BODY = "extra_body"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val EXTRA_NOTIFICATION_TAG = "extra_notification_tag"
    }
}
