package com.kgs.calendar.desktop

import com.kgs.calendar.data.CalendarRepository
import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.data.settings.SettingsStore
import com.kgs.calendar.domain.event.remindsUser
import com.kgs.calendar.reminder.MissedReminders
import com.kgs.calendar.reminder.ReminderAlarmPlan
import com.kgs.calendar.reminder.ReminderOccurrence
import com.kgs.calendar.reminder.ReminderPlanner
import com.kgs.calendar.res.AppStrings
import com.kgs.calendar.res.SharedR as R
import com.kgs.calendar.ui.cleanCalendarDisplayText
import com.kgs.calendar.ui.time.dayMonthFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Reminders while the app runs (also minimized to the tray), planned exactly like Android's: the
 * same offsets, anchors, hidden calendars and declined invitations. Reminders missed while the app
 * was closed are caught up for twelve hours, as on Android after a restart.
 */
class DesktopReminders(
    private val repository: CalendarRepository,
    private val settingsStore: SettingsStore,
    private val deliveredFile: File,
    private val locale: () -> Locale,
) {
    /** Shows a reminder; set by the window once the tray icon exists. */
    @Volatile
    var notifier: (title: String, body: String?) -> Unit = { _, _ -> }

    @Volatile
    var scope: CoroutineScope? = null

    private val lock = Mutex()
    private var timer: Job? = null
    private var pending: List<ReminderAlarmPlan> = emptyList()

    suspend fun reschedule() = lock.withLock {
        val scope = scope ?: return@withLock
        val visibility = settingsStore.collectionVisibility.first()
        val (events, tasks) = repository.reminderCandidates(visibility)
        val now = System.currentTimeMillis()
        val windowEnd = now + WINDOW_DAYS * DAY_MILLIS
        val planFrom = now - MissedReminders.GRACE_MILLIS
        val ownAddresses = repository.ownAddressesByCollection()
        val planner = ReminderPlanner()
        val plans = mutableListOf<ReminderAlarmPlan>()

        events.forEach { master ->
            val offsets = master.remindersCsv.minutes()
            if (offsets.isEmpty()) return@forEach
            val addresses = ownAddresses[master.collectionHref].orEmpty()
            repository.expandEventReminderOccurrences(master, planFrom, windowEnd + offsets.leadMillis()).forEach { occurrence ->
                val event = occurrence.item
                if (!event.remindsUser(addresses)) return@forEach
                val reminder = ReminderOccurrence(
                    occurrenceId = occurrence.occurrenceId,
                    startAtMillis = event.startsAtMillis,
                    endAtMillis = event.endsAtMillis,
                    defaultAnchorAtMillis = event.startsAtMillis,
                    title = event.title,
                    body = null,
                    notifyUntilMillis = event.endsAtMillis,
                )
                plans += planner.plan(reminder, offsets).map { it.copy(body = eventBody(event, it.triggerAtMillis)) }
            }
        }
        tasks.forEach { master ->
            val offsets = master.remindersCsv.minutes()
            if (offsets.isEmpty()) return@forEach
            repository.expandTaskReminderOccurrences(master, planFrom, windowEnd + offsets.leadMillis()).forEach { occurrence ->
                val task = occurrence.item
                val reminder = ReminderOccurrence(
                    occurrenceId = occurrence.occurrenceId,
                    startAtMillis = task.startAtMillis,
                    endAtMillis = task.dueAtMillis,
                    defaultAnchorAtMillis = task.dueAtMillis ?: task.startAtMillis,
                    title = task.title.takeIf { it.isNotBlank() } ?: AppStrings.get(R.string.untitled_task, locale()),
                    body = null,
                )
                plans += planner.plan(reminder, offsets).map { it.copy(body = taskBody(task, it.triggerAtMillis)) }
            }
        }

        val delivered = readDelivered()
        val missed = if (delivered == null) {
            // The first run counts what is already due as shown, instead of notifying all of it.
            writeDelivered(plans.filter { it.triggerAtMillis < now }.map { it.alarmRequestCode }.toSet())
            emptyList()
        } else {
            MissedReminders.select(plans, delivered, now).distinctBy { it.alarmRequestCode }
        }
        pending = (missed + plans.filter { it.triggerAtMillis in now..windowEnd })
            .distinctBy { it.alarmRequestCode }
            .sortedBy { it.triggerAtMillis }
        timer?.cancel()
        timer = scope.launch { deliverInOrder() }
    }

    /** Forgets the reminders of an item that was completed, cancelled or deleted. */
    suspend fun dismiss(resourceHref: String) = lock.withLock {
        pending = pending.filterNot { it.occurrenceId.resourceHref == resourceHref }
    }

    private suspend fun deliverInOrder() {
        while (true) {
            val next = lock.withLock { pending.firstOrNull() } ?: return
            val wait = next.triggerAtMillis - System.currentTimeMillis()
            // Wake at least every minute so sleep and clock changes don't delay a reminder.
            if (wait > 0) {
                delay(wait.coerceAtMost(60_000L))
                continue
            }
            lock.withLock {
                if (pending.firstOrNull()?.alarmRequestCode != next.alarmRequestCode) return@withLock
                pending = pending.drop(1)
                notifier(next.title, next.body)
                writeDelivered((readDelivered().orEmpty() + next.alarmRequestCode).toSet())
            }
        }
    }

    private fun eventBody(event: EventEntity, triggerAt: Long): String? {
        val start = event.startsAtMillis.localDateTime()
        val end = event.endsAtMillis.localDateTime()
        val date = start.toLocalDate().takeUnless { triggerAt.isSameLocalDate(it) }
            ?.format(dayMonthFormatter(locale(), weekday = "EEE"))
        val time = if (event.allDay) null else "${start.format(TimeFormatter)} - ${end.format(TimeFormatter)}"
        return body(listOfNotNull(date, time).joinToString(" | "), event.description)
    }

    private fun taskBody(task: TaskEntity, triggerAt: Long): String? {
        val start = task.startAtMillis?.localDateTime()
        val due = task.dueAtMillis?.localDateTime()
        val date = (start?.toLocalDate() ?: due?.toLocalDate())?.takeUnless { triggerAt.isSameLocalDate(it) }
            ?.format(dayMonthFormatter(locale(), weekday = "EEE"))
        val time = when {
            start != null && task.startHasTime && due != null && task.dueHasTime -> "${start.format(TimeFormatter)} - ${due.format(TimeFormatter)}"
            start != null && task.startHasTime -> start.format(TimeFormatter)
            due != null && task.dueHasTime -> due.format(TimeFormatter)
            else -> null
        }
        return body(listOfNotNull(date, time).joinToString(" | "), task.notes)
    }

    private fun body(schedule: String, notes: String?): String? =
        listOf(schedule, notes?.cleanCalendarDisplayText().orEmpty())
            .filter { it.isNotBlank() }
            .joinToString("\n")
            .takeIf { it.isNotBlank() }

    private fun readDelivered(): Set<Int>? =
        if (!deliveredFile.isFile) null else runCatching {
            deliveredFile.readLines().mapNotNull { it.trim().toIntOrNull() }.toSet()
        }.getOrNull()

    private fun writeDelivered(codes: Set<Int>) {
        deliveredFile.parentFile?.mkdirs()
        // Only recent codes matter; keep the file small.
        deliveredFile.writeText(codes.toList().takeLast(MAX_DELIVERED).joinToString("\n"))
    }

    private fun String?.minutes(): List<Int> = this?.split(',')?.mapNotNull { it.trim().toIntOrNull() }.orEmpty()

    private fun List<Int>.leadMillis(): Long = (maxOrNull() ?: 0).coerceAtLeast(0) * 60_000L

    private fun Long.localDateTime() = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDateTime()

    private fun Long.isSameLocalDate(date: LocalDate): Boolean =
        Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate() == date

    private companion object {
        const val WINDOW_DAYS = 14L
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
        const val MAX_DELIVERED = 2_000
        val TimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
