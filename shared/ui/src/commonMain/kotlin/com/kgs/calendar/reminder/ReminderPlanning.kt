package com.kgs.calendar.reminder

import com.kgs.calendar.data.CalendarRepository
import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.data.settings.SettingsStore
import com.kgs.calendar.domain.event.remindsUser
import com.kgs.calendar.domain.model.isSupportedReminderOffset
import com.kgs.calendar.navigation.htmlToPlainText
import com.kgs.calendar.platform.urlDecode
import com.kgs.calendar.time.AppLocale
import com.kgs.calendar.time.DateTimeFormatter
import com.kgs.calendar.time.ZoneId
import com.kgs.calendar.time.atZone
import com.kgs.calendar.time.format
import com.kgs.calendar.time.ofEpochMilli
import com.kgs.calendar.time.systemDefault
import com.kgs.calendar.ui.time.dayMonthFormatter
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/** The reminders due in the next [windowDays] days, and recent ones that may have been missed. */
class ReminderPlanning(val plans: List<ReminderAlarmPlan>, val nowMillis: Long, val windowEndMillis: Long)

/**
 * Plans every reminder the way Android's ReminderScheduler does: hidden calendars and items never
 * notify, cancelled events and declined invitations stay quiet, and each plan carries the same
 * notification text (date when it isn't today, times, plain notes).
 */
suspend fun planReminders(
    repository: CalendarRepository,
    settingsStore: SettingsStore,
    untitledTask: String,
    locale: AppLocale,
    nowMillis: Long,
    windowDays: Long = REMINDER_WINDOW_DAYS,
    zone: ZoneId = ZoneId.systemDefault(),
): ReminderPlanning {
    val (events, tasks) = repository.reminderCandidates(settingsStore.collectionVisibility.first())
    val windowEnd = nowMillis + windowDays * 24 * 60 * 60 * 1000
    val planFrom = nowMillis - MissedReminders.GRACE_MILLIS
    val ownAddresses = repository.ownAddressesByCollection()
    val planner = ReminderPlanner()
    val bodies = ReminderBodies(locale, zone)
    val plans = mutableListOf<ReminderAlarmPlan>()
    events.forEach { master ->
        val offsets = master.remindersCsv.reminderMinutes()
        if (offsets.isEmpty()) return@forEach
        val addresses = ownAddresses[master.collectionHref].orEmpty()
        repository.expandEventReminderOccurrences(master, planFrom, windowEnd + offsets.leadMillis()).forEach { occurrence ->
            val event = occurrence.item
            if (!event.remindsUser(addresses)) return@forEach
            val reminderOccurrence = ReminderOccurrence(
                occurrenceId = occurrence.occurrenceId,
                startAtMillis = event.startsAtMillis,
                endAtMillis = event.endsAtMillis,
                defaultAnchorAtMillis = event.startsAtMillis,
                title = event.title,
                body = null,
                notifyUntilMillis = event.endsAtMillis,
            )
            plans += planner.plan(reminderOccurrence, offsets).map { it.copy(body = bodies.event(event, it.triggerAtMillis)) }
        }
    }
    tasks.forEach { master ->
        val offsets = master.remindersCsv.reminderMinutes()
        if (offsets.isEmpty()) return@forEach
        repository.expandTaskReminderOccurrences(master, planFrom, windowEnd + offsets.leadMillis()).forEach { occurrence ->
            val task = occurrence.item
            val reminderOccurrence = ReminderOccurrence(
                occurrenceId = occurrence.occurrenceId,
                startAtMillis = task.startAtMillis,
                endAtMillis = task.dueAtMillis,
                defaultAnchorAtMillis = task.dueAtMillis ?: task.startAtMillis,
                title = task.title.takeIf { it.isNotBlank() } ?: untitledTask,
                body = null,
            )
            plans += planner.plan(reminderOccurrence, offsets).map { it.copy(body = bodies.task(task, it.triggerAtMillis)) }
        }
    }
    return ReminderPlanning(plans, nowMillis, windowEnd)
}

const val REMINDER_WINDOW_DAYS = 21L

private fun String?.reminderMinutes(): List<Int> =
    this?.split(',')?.mapNotNull { it.trim().toIntOrNull() }?.filter { it.isSupportedReminderOffset() }.orEmpty()

/** How far past the window occurrences must be expanded: offsets go up to 30 days before. */
private fun List<Int>.leadMillis(): Long = (maxOrNull() ?: 0).coerceAtLeast(0) * 60_000L

private class ReminderBodies(private val locale: AppLocale, private val zone: ZoneId) {
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun event(event: EventEntity, triggerAt: Long): String? {
        val start = event.startsAtMillis.zoned()
        val end = event.endsAtMillis.zoned()
        val date = start.toLocalDate().takeUnless { triggerAt.isSameLocalDate(it) }?.format(dayMonthFormatter(locale, weekday = "EEE"))
        val time = if (event.allDay) null else "${start.format(timeFormatter)} - ${end.format(timeFormatter)}"
        return body(listOfNotNull(date, time).joinToString(" | "), event.description.reminderPlainText())
    }

    fun task(task: TaskEntity, triggerAt: Long): String? {
        val start = task.startAtMillis?.zoned()
        val due = task.dueAtMillis?.zoned()
        val taskDate = start?.toLocalDate() ?: due?.toLocalDate()
        val date = taskDate?.takeUnless { triggerAt.isSameLocalDate(it) }?.format(dayMonthFormatter(locale, weekday = "EEE"))
        val time = when {
            start != null && task.startHasTime && due != null && task.dueHasTime -> "${start.format(timeFormatter)} - ${due.format(timeFormatter)}"
            start != null && task.startHasTime -> start.format(timeFormatter)
            due != null && task.dueHasTime -> due.format(timeFormatter)
            else -> null
        }
        return body(listOfNotNull(date, time).joinToString(" | "), task.notes.reminderPlainText())
    }

    private fun body(schedule: String, notes: String?): String? =
        listOf(schedule, notes.orEmpty()).filter { it.isNotBlank() }.joinToString("\n").takeIf { it.isNotBlank() }

    private fun Long.zoned() = Instant.ofEpochMilli(this).atZone(zone)

    private fun Long.isSameLocalDate(date: LocalDate): Boolean = Instant.ofEpochMilli(this).atZone(zone).toLocalDate() == date
}

/** Plain text of event/task notes for a notification; HTML (also as a data URL) is flattened. */
fun String?.reminderPlainText(): String? {
    val raw = this?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val htmlPayload = when {
        raw.startsWith("data:text/html", ignoreCase = true) && "," in raw -> raw.substringAfter(',')
        raw.startsWith("text/html,", ignoreCase = true) -> raw.substringAfter(',')
        else -> null
    }
    val decoded = htmlPayload?.let { payload -> runCatching { urlDecode(payload) }.getOrDefault(payload) } ?: raw
    val plain = if ('<' in decoded && '>' in decoded) htmlToPlainText(decoded) else decoded
    return plain
        .replace(Regex("[\\t\\x0B\\f\\r]+"), " ")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
        .takeIf { it.isNotBlank() }
}
