package com.kgs.calendar.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.text.Html
import com.kgs.calendar.KgsCalendarApplication
import com.kgs.calendar.R
import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.domain.model.REMINDER_AT_END
import com.kgs.calendar.domain.model.REMINDER_AT_START
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
    private const val KEY_TASK_CODES_PREFIX = "task_codes:"
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
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        // Cancel previously scheduled alarms.
        prefs.getStringSet(KEY_CODES, emptySet()).orEmpty().forEach { codeStr ->
            codeStr.toIntOrNull()?.let { code ->
                alarmManager.cancel(pendingIntent(context, code, null, null, null))
            }
        }

        val newCodes = mutableSetOf<String>()
        val taskCodes = mutableMapOf<String, MutableSet<String>>()

        fun schedule(triggerAt: Long, title: String, body: String?, taskResourceHref: String? = null) {
            if (triggerAt < now || triggerAt > windowEnd) return
            val code = ((taskResourceHref ?: "") + "|" + title + "|" + body + "|" + triggerAt).hashCode()
            val pi = pendingIntent(context, code, title, body, taskResourceHref)
            scheduleExactCompat(alarmManager, triggerAt, pi)
            newCodes += code.toString()
            taskResourceHref?.let { taskCodes.getOrPut(it) { mutableSetOf() } += code.toString() }
        }

        // Events (expand recurring ones within the window).
        events.forEach { master ->
            val offsets = master.remindersCsv.minutes()
            if (offsets.isEmpty()) return@forEach
            val occurrences: List<EventEntity> = if (!master.recurrenceRule.isNullOrBlank()) {
                repository.expandEventReminders(master, now, windowEnd)
            } else {
                listOf(master)
            }
            occurrences.forEach { occ ->
                offsets.forEach { offset ->
                    val triggerAt = when (offset) {
                        REMINDER_AT_END -> occ.endsAtMillis
                        REMINDER_AT_START -> occ.startsAtMillis
                        else -> occ.startsAtMillis - offset * 60_000L
                    }
                    schedule(triggerAt, occ.title, eventNotificationBody(occ, triggerAt))
                }
            }
        }

        // Tasks (use due, else start).
        tasks.forEach { master ->
            val occurrences = if (!master.recurrenceRule.isNullOrBlank() || !master.rDatesCsv.isNullOrBlank()) {
                repository.expandTaskReminders(master, now, windowEnd)
            } else {
                listOf(master)
            }
            occurrences.forEach { task ->
                task.remindersCsv.minutes().forEach { offset ->
                    val anchor = task.dueAtMillis ?: task.startAtMillis
                    val triggerAt = when (offset) {
                        REMINDER_AT_END -> task.dueAtMillis
                        REMINDER_AT_START -> task.startAtMillis
                        else -> anchor?.minus(offset * 60_000L)
                    } ?: return@forEach
                    val title = task.title.takeIf { it.isNotBlank() } ?: context.getString(R.string.untitled_task)
                    schedule(triggerAt, title, taskNotificationBody(task, triggerAt), task.resourceHref)
                }
            }
        }

        prefs.edit().apply {
            prefs.all.keys.filter { it.startsWith(KEY_TASK_CODES_PREFIX) }.forEach(::remove)
            putStringSet(KEY_CODES, newCodes)
            taskCodes.forEach { (taskResourceHref, codes) ->
                putStringSet(taskCodesKey(taskResourceHref), codes)
            }
        }.apply()
    }

    private fun String?.minutes(): List<Int> =
        this?.split(',')?.mapNotNull { it.trim().toIntOrNull() }?.filter { it.isSupportedReminderOffset() }.orEmpty()

    fun cancelTaskNotifications(context: Context, taskResourceHref: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val codes = (
            prefs.getStringSet(taskCodesKey(taskResourceHref), emptySet()).orEmpty() +
                prefs.getStringSet(activeTaskCodesKey(taskResourceHref), emptySet()).orEmpty()
            )
            .mapNotNull { it.toIntOrNull() }
        if (codes.isEmpty()) return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        codes.forEach { code ->
            alarmManager.cancel(pendingIntent(context, code, null, null, taskResourceHref))
            notificationManager.cancel(code)
        }
        val remainingCodes = prefs.getStringSet(KEY_CODES, emptySet()).orEmpty() - codes.map { it.toString() }.toSet()
        prefs.edit()
            .putStringSet(KEY_CODES, remainingCodes)
            .remove(taskCodesKey(taskResourceHref))
            .remove(activeTaskCodesKey(taskResourceHref))
            .apply()
    }

    fun recordTaskNotification(context: Context, taskResourceHref: String, notificationId: Int) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = activeTaskCodesKey(taskResourceHref)
        val current = prefs.getStringSet(key, emptySet()).orEmpty()
        prefs.edit().putStringSet(key, current + notificationId.toString()).apply()
    }

    private fun taskCodesKey(taskResourceHref: String): String =
        KEY_TASK_CODES_PREFIX + taskResourceHref

    private fun activeTaskCodesKey(taskResourceHref: String): String =
        KEY_ACTIVE_TASK_CODES_PREFIX + taskResourceHref

    private fun pendingIntent(context: Context, code: Int, title: String?, body: String?, taskResourceHref: String?): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            if (title != null) putExtra(ReminderReceiver.EXTRA_TITLE, title)
            if (body != null) putExtra(ReminderReceiver.EXTRA_BODY, body)
            if (taskResourceHref != null) putExtra(ReminderReceiver.EXTRA_TASK_RESOURCE_HREF, taskResourceHref)
            putExtra(ReminderReceiver.EXTRA_NOTIFICATION_ID, code)
        }
        return PendingIntent.getBroadcast(
            context,
            code,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
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
