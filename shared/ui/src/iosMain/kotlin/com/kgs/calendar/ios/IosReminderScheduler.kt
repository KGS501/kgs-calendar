package com.kgs.calendar.ios

import com.kgs.calendar.domain.model.CalendarOccurrenceId
import com.kgs.calendar.navigation.CalendarLaunchAction
import com.kgs.calendar.navigation.CalendarLaunchTarget
import com.kgs.calendar.reminder.planReminders
import com.kgs.calendar.time.defaultAppLocale
import com.kgs.calendar.time.nowMillis
import com.kgs.calendar.ui.res.Res
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationAction
import platform.UserNotifications.UNNotificationActionOptionNone
import platform.UserNotifications.UNNotificationCategory
import platform.UserNotifications.UNNotificationCategoryOptionNone
import platform.UserNotifications.UNNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume

/**
 * Reminders as local notifications, the iOS side of Android's ReminderScheduler and
 * ReminderReceiver. iOS keeps at most 64 pending, so the earliest ones in the next three weeks
 * are scheduled; every reschedule (after a sync, an edit, a foreground) replaces them. Snoozed
 * reminders are kept apart and survive reschedules. Like on Android, a notification offers
 * Snooze (10 minutes), tasks also Mark done, and tapping one opens its event or task.
 */
class IosReminderScheduler(private val graph: IosAppGraph) {
    private val mutex = Mutex()
    private val center get() = UNUserNotificationCenter.currentNotificationCenter()

    fun requestPermission() {
        center.requestAuthorizationWithOptions(UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge) { _, _ -> }
    }

    /** The Snooze and Mark done buttons, labelled in the app's language. */
    fun registerActions() {
        val locale = defaultAppLocale()
        val snooze = UNNotificationAction.actionWithIdentifier(ACTION_SNOOZE, Res.string.reminder_snooze.text(locale), UNNotificationActionOptionNone)
        val done = UNNotificationAction.actionWithIdentifier(ACTION_MARK_DONE, Res.string.mark_done.text(locale), UNNotificationActionOptionNone)
        center.setNotificationCategories(
            setOf(
                UNNotificationCategory.categoryWithIdentifier(CATEGORY_EVENT, listOf(snooze), emptyList<String>(), UNNotificationCategoryOptionNone),
                UNNotificationCategory.categoryWithIdentifier(CATEGORY_TASK, listOf(snooze, done), emptyList<String>(), UNNotificationCategoryOptionNone),
            ),
        )
    }

    suspend fun reschedule() = mutex.withLock {
        val now = nowMillis()
        val locale = defaultAppLocale()
        val planning = planReminders(
            repository = graph.repository,
            settingsStore = graph.settingsStore,
            untitledTask = Res.string.untitled_task.text(locale),
            locale = locale,
            nowMillis = now,
        )
        val pending = pendingRequests()
        // Everything but snoozed reminders is planned again (older builds used bare numbers as ids).
        val planned = pending.filterNot { it.startsWith(SNOOZED_PREFIX) }
        if (planned.isNotEmpty()) center.removePendingNotificationRequestsWithIdentifiers(planned)
        val room = (IOS_PENDING_LIMIT - (pending.size - planned.size)).coerceIn(0, MAX_PENDING)
        planning.plans
            .filter { it.triggerAtMillis in (now + 1000)..planning.windowEndMillis }
            .distinctBy { it.alarmRequestCode }
            .sortedBy { it.triggerAtMillis }
            .take(room)
            .forEach { plan ->
                val content = UNMutableNotificationContent()
                content.setTitle(plan.title)
                plan.body?.let { content.setBody(it) }
                content.setSound(UNNotificationSound.defaultSound)
                content.setThreadIdentifier(plan.notificationKey.tag)
                content.setCategoryIdentifier(if (plan.occurrenceId is CalendarOccurrenceId.Task) CATEGORY_TASK else CATEGORY_EVENT)
                val target = CalendarLaunchTarget(action = CalendarLaunchAction.OpenOccurrence, occurrence = plan.occurrenceId)
                content.setUserInfo(mapOf(KEY_TARGET to target.encode(), KEY_OCCURRENCE to plan.occurrenceId.stableKey, KEY_TAG to plan.notificationKey.tag))
                add(PLANNED_PREFIX + plan.alarmRequestCode, content, ((plan.triggerAtMillis - now) / 1000.0).coerceAtLeast(1.0))
            }
    }

    /** Shows [notification] again in [SNOOZE_MINUTES]; one snooze per reminder, like on Android. */
    fun snooze(notification: UNNotification, onDone: () -> Unit) {
        val original = notification.request.content
        val content = original.mutableCopy() as UNMutableNotificationContent
        val tag = original.userInfo[KEY_TAG] as? String ?: notification.request.identifier
        add(SNOOZED_PREFIX + tag, content, SNOOZE_MINUTES * 60.0, onDone)
    }

    /** Completes the task occurrence of a reminder's Mark done button. */
    suspend fun markDone(content: UNNotificationContent) {
        val encoded = content.userInfo[KEY_TARGET] as? String ?: return
        val occurrence = CalendarLaunchTarget.decode(encoded)?.occurrence as? CalendarOccurrenceId.Task ?: return
        graph.taskMutationCoordinator.setStatus(occurrence.resourceHref, "COMPLETED", occurrence)
    }

    private fun add(identifier: String, content: UNNotificationContent, seconds: Double, onDone: (() -> Unit)? = null) {
        val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(seconds, repeats = false)
        center.addNotificationRequest(UNNotificationRequest.requestWithIdentifier(identifier, content, trigger)) { _ -> onDone?.invoke() }
    }

    private suspend fun pendingRequests(): List<String> = suspendCancellableCoroutine { continuation ->
        center.getPendingNotificationRequestsWithCompletionHandler { requests ->
            continuation.resume(requests.orEmpty().mapNotNull { (it as? UNNotificationRequest)?.identifier })
        }
    }

    /** Removes shown notifications of a task occurrence that was completed or cancelled. */
    fun cancelFor(occurrenceKey: String) = removeDelivered { key -> key == occurrenceKey }

    fun cancelForResource(resourceHref: String) = removeDelivered { key -> key.contains(resourceHref) }

    private fun removeDelivered(matches: (String) -> Boolean) {
        center.getDeliveredNotificationsWithCompletionHandler { delivered ->
            val ids = delivered.orEmpty().mapNotNull { notification ->
                val request = (notification as? UNNotification)?.request ?: return@mapNotNull null
                val key = request.content.userInfo[KEY_OCCURRENCE] as? String ?: return@mapNotNull null
                request.identifier.takeIf { matches(key) }
            }
            if (ids.isNotEmpty()) center.removeDeliveredNotificationsWithIdentifiers(ids)
        }
        // A snoozed reminder of a finished task must not come back either.
        center.getPendingNotificationRequestsWithCompletionHandler { requests ->
            val ids = requests.orEmpty().mapNotNull { request ->
                (request as? UNNotificationRequest)?.takeIf { it.identifier.startsWith(SNOOZED_PREFIX) }
                    ?.takeIf { (it.content.userInfo[KEY_OCCURRENCE] as? String)?.let(matches) == true }
                    ?.identifier
            }
            if (ids.isNotEmpty()) center.removePendingNotificationRequestsWithIdentifiers(ids)
        }
    }

    companion object {
        /** iOS drops pending notifications beyond 64; a few places stay free for snoozes. */
        const val MAX_PENDING = 60
        private const val IOS_PENDING_LIMIT = 64
        const val SNOOZE_MINUTES = 10L
        const val KEY_TARGET = "kgsLaunchTarget"
        const val KEY_OCCURRENCE = "kgsOccurrence"
        private const val KEY_TAG = "kgsTag"
        const val CATEGORY_EVENT = "kgs-event-reminder"
        const val CATEGORY_TASK = "kgs-task-reminder"
        const val ACTION_SNOOZE = "kgs-snooze"
        const val ACTION_MARK_DONE = "kgs-mark-done"
        private const val PLANNED_PREFIX = "kgs-r:"
        private const val SNOOZED_PREFIX = "kgs-s:"
    }
}
