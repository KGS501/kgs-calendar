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
