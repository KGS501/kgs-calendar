package com.kgs.calendar.reminder

import com.kgs.calendar.domain.model.CalendarOccurrenceId

data class ReminderOccurrence(
    val occurrenceId: CalendarOccurrenceId,
    val startAtMillis: Long?,
    val endAtMillis: Long?,
    val defaultAnchorAtMillis: Long?,
    val title: String,
    val body: String?,
    /** A reminder missed while the device was off is still shown until then; null for always. */
    val notifyUntilMillis: Long? = null,
)

data class ReminderNotificationKey(
    val tag: String,
    val id: Int,
)

data class ReminderAlarmPlan(
    val occurrenceId: CalendarOccurrenceId,
    val triggerAtMillis: Long,
    val reminderOffsetMinutes: Int,
    val alarmRequestCode: Int,
    val notificationKey: ReminderNotificationKey,
    val title: String,
    val body: String?,
    val notifyUntilMillis: Long? = null,
)

data class ScheduledReminderRecord(
    val alarmRequestCode: Int,
    val occurrenceId: CalendarOccurrenceId,
    val notificationKey: ReminderNotificationKey,
)

data class ActiveReminderNotification(
    val occurrenceId: CalendarOccurrenceId,
    val notificationKey: ReminderNotificationKey,
)

/** A reminder put off from its notification, kept so it is set again after a restart. */
data class SnoozedReminder(
    val alarmRequestCode: Int,
    val triggerAtMillis: Long,
    val title: String,
    val body: String?,
    val notificationKey: ReminderNotificationKey,
    val encodedTarget: String,
)
