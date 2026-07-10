package com.kgs.calendar.reminder

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.kgs.calendar.KgsCalendarApplication
import com.kgs.calendar.MainActivity
import com.kgs.calendar.R
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
        val taskResourceHref = intent.getStringExtra(EXTRA_TASK_RESOURCE_HREF)

        ReminderScheduler.ensureChannel(context)

        val contentIntent = android.app.PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )

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
        if (!taskResourceHref.isNullOrBlank()) {
            val doneIntent = Intent(context, ReminderReceiver::class.java).apply {
                action = ACTION_MARK_TASK_DONE
                putExtra(EXTRA_TASK_RESOURCE_HREF, taskResourceHref)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            }
            val donePendingIntent = android.app.PendingIntent.getBroadcast(
                context,
                notificationId xor taskResourceHref.hashCode(),
                doneIntent,
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
            )
            notificationBuilder.addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_notification,
                    context.getString(R.string.mark_done),
                    donePendingIntent,
                ).build(),
            )
        }
        val notification = notificationBuilder.build()

        // POST_NOTIFICATIONS may be revoked; notify() is a no-op then rather than crashing.
        runCatching {
            if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                taskResourceHref?.takeIf { it.isNotBlank() }?.let {
                    ReminderScheduler.recordTaskNotification(context, it, notificationId)
                }
                NotificationManagerCompat.from(context).notify(notificationId, notification)
            }
        }
    }

    private fun markTaskDone(context: Context, intent: Intent) {
        val taskResourceHref = intent.getStringExtra(EXTRA_TASK_RESOURCE_HREF)?.takeIf { it.isNotBlank() } ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, taskResourceHref.hashCode())
        NotificationManagerCompat.from(context).cancel(notificationId)
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val graph = KgsCalendarApplication.graph(appContext)
                graph.taskMutationCoordinator.setStatus(taskResourceHref, "COMPLETED")
            }
            NotificationManagerCompat.from(appContext).cancel(notificationId)
            pendingResult.finish()
        }
    }

    companion object {
        const val ACTION_MARK_TASK_DONE = "com.kgs.calendar.reminder.MARK_TASK_DONE"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_BODY = "extra_body"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val EXTRA_TASK_RESOURCE_HREF = "extra_task_resource_href"

        @Suppress("unused")
        private fun manager(context: Context): NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }
}
