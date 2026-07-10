package com.kgs.calendar.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.text.Html
import com.kgs.calendar.KgsCalendarApplication
import com.kgs.calendar.R
import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.domain.model.isSupportedReminderOffset
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Schedules local notifications for event/task reminders via AlarmManager.
 *
 * Strategy: on each call we cancel everything we previously scheduled (request codes are
 * persisted) and re-schedule all reminders that fall within the next [WINDOW_DAYS] days.
 * This keeps things consistent after edits/sync without needing to diff individual alarms.
 */
object ReminderScheduler {
    const val CHANNEL_ID = "kgs_reminders"
    private const val PREFS = "kgs_reminder_alarms"
    private const val KEY_CODES = "scheduled_codes"
    private const val KEY_ACTIVE_TASK_CODES_PREFIX = "active_task_codes:"
    private const val WINDOW_DAYS = 21L
    private val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yy")
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        context.getString(R.string.reminder_channel_name),
                        NotificationManager.IMPORTANCE_HIGH,
                    ).apply { description = context.getString(R.string.reminder_channel_description) },
                )
            }
        }
    }

    /** Reschedules all reminders. Safe to call repeatedly (e.g. after every sync). */
    suspend fun reschedule(context: Context) {
        ensureChannel(context)
        val graph = KgsCalendarApplication.graph(context)
        val repository = graph.repository
        val (events, tasks) = repository.reminderCandidates()
        val now = System.currentTimeMillis()
        val windowEnd = now + WINDOW_DAYS * 24 * 60 * 60 * 1000
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        cancelLegacySchedules(context, alarmManager)
        val planner = ReminderPlanner()
        val plans = mutableListOf<ReminderAlarmPlan>()

        events.forEach { master ->
            val offsets = master.remindersCsv.minutes()
            if (offsets.isEmpty()) return@forEach
            repository.expandEventReminderOccurrences(master, now, windowEnd).forEach { occurrence ->
                val event = occurrence.item
                val reminderOccurrence = ReminderOccurrence(
                    occurrenceId = occurrence.occurrenceId,
                    startAtMillis = event.startsAtMillis,
                    endAtMillis = event.endsAtMillis,
                    defaultAnchorAtMillis = event.startsAtMillis,
                    title = event.title,
                    body = null,
                )
                plans += planner.plan(reminderOccurrence, offsets).map { plan ->
                    plan.copy(body = eventNotificationBody(event, plan.triggerAtMillis))
                }
            }
        }

        tasks.forEach { master ->
            repository.expandTaskReminderOccurrences(master, now, windowEnd).forEach { occurrence ->
                val task = occurrence.item
                val reminderOccurrence = ReminderOccurrence(
                    occurrenceId = occurrence.occurrenceId,
                    startAtMillis = task.startAtMillis,
                    endAtMillis = task.dueAtMillis,
                    defaultAnchorAtMillis = task.dueAtMillis ?: task.startAtMillis,
                    title = task.title.takeIf { it.isNotBlank() } ?: context.getString(R.string.untitled_task),
                    body = null,
                )
                plans += planner.plan(reminderOccurrence, task.remindersCsv.minutes()).map { plan ->
                    plan.copy(body = taskNotificationBody(task, plan.triggerAtMillis))
                }
            }
        }

        val activePlans = plans
            .filter { it.triggerAtMillis in now..windowEnd }
            .distinctBy { it.alarmRequestCode }
        graph.reminderRegistry.replaceAllScheduled(
            activePlans.map { ScheduledReminderRecord(it.alarmRequestCode, it.occurrenceId, it.notificationKey) },
        )
        activePlans.forEach { plan ->
            scheduleExactCompat(alarmManager, plan.triggerAtMillis, ReminderIntents.alarmPendingIntent(context, plan))
        }
    }

    private fun String?.minutes(): List<Int> =
        this?.split(',')?.mapNotNull { it.trim().toIntOrNull() }?.filter { it.isSupportedReminderOffset() }.orEmpty()

    private fun cancelLegacySchedules(context: Context, alarmManager: AlarmManager) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val codes = prefs.getStringSet(KEY_CODES, emptySet()).orEmpty().mapNotNull(String::toIntOrNull)
        codes.forEach { code ->
            PendingIntent.getBroadcast(
                context,
                code,
                android.content.Intent(context, ReminderReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )?.let(alarmManager::cancel)
        }
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        prefs.all
            .filterKeys { it.startsWith(KEY_ACTIVE_TASK_CODES_PREFIX) }
            .values
            .filterIsInstance<Set<*>>()
            .flatMap { it.mapNotNull { value -> value.toString().toIntOrNull() } }
            .forEach { notificationId -> notificationManager.cancel(notificationId) }
        prefs.edit().clear().apply()
    }

    private fun scheduleExactCompat(alarmManager: AlarmManager, triggerAt: Long, pi: PendingIntent) {
        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
        runCatching {
            if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                alarmManager.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, 60_000L, pi)
            }
        }.onFailure {
            // Exact alarm permission may be revoked at runtime — fall back to inexact.
            runCatching { alarmManager.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, 60_000L, pi) }
        }
    }

    private fun eventNotificationBody(event: EventEntity, triggerAt: Long): String? {
        val start = event.startsAtMillis.localDateTime()
        val end = event.endsAtMillis.localDateTime()
        val date = start.toLocalDate().takeUnless { triggerAt.isSameLocalDate(it) }?.format(dateFormatter)
        val time = if (event.allDay) null else "${start.format(timeFormatter)} - ${end.format(timeFormatter)}"
        return notificationBody(listOfNotNull(date, time).joinToString(" | "), event.description.notificationText())
    }

    private fun taskNotificationBody(task: TaskEntity, triggerAt: Long): String? {
        val start = task.startAtMillis?.localDateTime()
        val due = task.dueAtMillis?.localDateTime()
        val taskDate = start?.toLocalDate() ?: due?.toLocalDate()
        val date = taskDate?.takeUnless { triggerAt.isSameLocalDate(it) }?.format(dateFormatter)
        val time = when {
            start != null && task.startHasTime && due != null && task.dueHasTime ->
                "${start.format(timeFormatter)} - ${due.format(timeFormatter)}"
            start != null && task.startHasTime -> start.format(timeFormatter)
            due != null && task.dueHasTime -> due.format(timeFormatter)
            else -> null
        }
        return notificationBody(listOfNotNull(date, time).joinToString(" | "), task.notes.notificationText())
    }

    private fun notificationBody(schedule: String, notes: String?): String? =
        listOf(schedule, notes.orEmpty())
            .filter { it.isNotBlank() }
            .joinToString("\n")
            .takeIf { it.isNotBlank() }

    private fun Long.localDateTime() =
        Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDateTime()

    private fun Long.isSameLocalDate(date: LocalDate): Boolean =
        Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate() == date

    private fun String?.notificationText(): String? {
        val raw = this?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val withoutHtmlPayload = when {
            raw.startsWith("data:text/html", ignoreCase = true) && "," in raw -> raw.substringAfter(',')
            raw.startsWith("text/html,", ignoreCase = true) -> raw.substringAfter(',')
            else -> raw
        }
        val decoded = runCatching { java.net.URLDecoder.decode(withoutHtmlPayload, Charsets.UTF_8.name()) }
            .getOrDefault(withoutHtmlPayload)
        val plain = if ('<' in decoded && '>' in decoded) {
            Html.fromHtml(decoded, Html.FROM_HTML_MODE_LEGACY).toString()
        } else {
            decoded
        }
        return plain
            .replace(Regex("[\\t\\x0B\\f\\r]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
            .takeIf { it.isNotBlank() }
    }
}
