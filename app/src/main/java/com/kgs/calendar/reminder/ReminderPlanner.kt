package com.kgs.calendar.reminder

import com.kgs.calendar.domain.model.REMINDER_AT_END
import com.kgs.calendar.domain.model.REMINDER_AT_START

class ReminderPlanner {
    fun plan(occurrence: ReminderOccurrence, offsets: List<Int>): List<ReminderAlarmPlan> =
        offsets.mapNotNull { offset ->
            val triggerAtMillis = when (offset) {
                REMINDER_AT_START -> occurrence.startAtMillis
                REMINDER_AT_END -> occurrence.endAtMillis
                else -> occurrence.defaultAnchorAtMillis?.minus(offset * MILLIS_PER_MINUTE)
            } ?: return@mapNotNull null
            ReminderAlarmPlan(
                occurrenceId = occurrence.occurrenceId,
                triggerAtMillis = triggerAtMillis,
                reminderOffsetMinutes = offset,
                alarmRequestCode = alarmRequestCode(occurrence, triggerAtMillis, offset),
                notificationKey = key(occurrence),
                title = occurrence.title,
                body = occurrence.body,
                notifyUntilMillis = occurrence.notifyUntilMillis,
            )
        }

    fun key(occurrence: ReminderOccurrence): ReminderNotificationKey {
        val stableKey = occurrence.occurrenceId.stableKey
        return ReminderNotificationKey(
            tag = "$NOTIFICATION_TAG_PREFIX$stableKey",
            id = stableKey.hashCode(),
        )
    }

    private fun alarmRequestCode(
        occurrence: ReminderOccurrence,
        triggerAtMillis: Long,
        offset: Int,
    ): Int = "${occurrence.occurrenceId.stableKey}:$triggerAtMillis:$offset".hashCode()

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000L
        const val NOTIFICATION_TAG_PREFIX = "kgs-reminder:"
    }
}

/**
 * Reminders whose alarm never went off: the device was off or restarting, or the item arrived by
 * sync after its reminder time. Alarms are only replanned from now on, so these would otherwise
 * be lost. Only recent ones are caught up, and only while their event hasn't ended.
 */
internal object MissedReminders {
    const val GRACE_MILLIS = 12L * 60L * 60L * 1000L

    fun select(
        plans: List<ReminderAlarmPlan>,
        deliveredCodes: Set<Int>,
        nowMillis: Long,
    ): List<ReminderAlarmPlan> =
        plans.filter { plan ->
            plan.triggerAtMillis < nowMillis &&
                plan.triggerAtMillis >= nowMillis - GRACE_MILLIS &&
                plan.alarmRequestCode !in deliveredCodes &&
                (plan.notifyUntilMillis == null || plan.notifyUntilMillis > nowMillis)
        }
}
