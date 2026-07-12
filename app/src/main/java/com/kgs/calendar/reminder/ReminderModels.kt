package com.kgs.calendar.reminder

import com.kgs.calendar.domain.model.CalendarOccurrenceId

data class ReminderOccurrence(
    val occurrenceId: CalendarOccurrenceId,
    val startAtMillis: Long?,
    val endAtMillis: Long?,
    val defaultAnchorAtMillis: Long?,
    val title: String,
    val body: String?,
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
