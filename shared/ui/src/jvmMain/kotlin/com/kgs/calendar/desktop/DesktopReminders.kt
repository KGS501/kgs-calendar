package com.kgs.calendar.desktop

import com.kgs.calendar.data.CalendarRepository
import com.kgs.calendar.data.settings.SettingsStore
import com.kgs.calendar.reminder.MissedReminders
import com.kgs.calendar.reminder.ReminderAlarmPlan
import com.kgs.calendar.reminder.planReminders
import com.kgs.calendar.time.defaultAppLocale
import com.kgs.calendar.time.nowMillis
import com.kgs.calendar.ui.res.Res
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * Reminders while the app runs (also in the notification area), planned exactly as on Android and
 * iOS (see [planReminders]). Reminders missed while the app was closed are caught up for twelve
 * hours, as on Android after a restart.
 */
class DesktopReminders(
    private val repository: CalendarRepository,
    private val settingsStore: SettingsStore,
    private val deliveredFile: File,
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
        val now = nowMillis()
        val locale = defaultAppLocale()
        val planning = planReminders(
            repository = repository,
            settingsStore = settingsStore,
            untitledTask = Res.string.untitled_task.text(locale),
            locale = locale,
            nowMillis = now,
        )
        val plans = planning.plans
        val delivered = readDelivered()
        val missed = if (delivered == null) {
            // The first run counts what is already due as shown, instead of notifying all of it.
            writeDelivered(plans.filter { it.triggerAtMillis < now }.map { it.alarmRequestCode }.toSet())
            emptyList()
        } else {
            MissedReminders.select(plans, delivered, now).distinctBy { it.alarmRequestCode }
        }
        pending = (missed + plans.filter { it.triggerAtMillis in now..planning.windowEndMillis })
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
            val wait = next.triggerAtMillis - nowMillis()
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

    private fun readDelivered(): Set<Int>? =
        if (!deliveredFile.isFile) null else runCatching {
            deliveredFile.readLines().mapNotNull { it.trim().toIntOrNull() }.toSet()
        }.getOrNull()

    private fun writeDelivered(codes: Set<Int>) {
        deliveredFile.parentFile?.mkdirs()
        // Only recent codes matter; keep the file small.
        deliveredFile.writeText(codes.toList().takeLast(MAX_DELIVERED).joinToString("\n"))
    }

    private companion object {
        const val MAX_DELIVERED = 2_000
    }
}
