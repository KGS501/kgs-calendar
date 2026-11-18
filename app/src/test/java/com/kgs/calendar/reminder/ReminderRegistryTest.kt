package com.kgs.calendar.reminder

import com.kgs.calendar.domain.model.CalendarOccurrenceId
import com.kgs.calendar.navigation.CalendarLaunchAction
import com.kgs.calendar.navigation.CalendarLaunchTarget
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderRegistryTest {
    private val firstId = CalendarOccurrenceId.Task("tasks/42.ics", 1_700_000_000_000)
    private val secondId = CalendarOccurrenceId.Task("tasks/42.ics", 1_700_086_400_000)
    private val firstKey = ReminderNotificationKey("first", 1)
    private val secondKey = ReminderNotificationKey("second", 2)
    private val storage = InMemoryReminderRegistryStorage()
    private val cancelledAlarms = mutableListOf<Int>()
    private val cancelledNotifications = mutableListOf<ReminderNotificationKey>()
    private val registry = ReminderRegistry(
        storage = storage,
        cancelAlarm = cancelledAlarms::add,
        cancelNotification = cancelledNotifications::add,
    )

    @Test
    fun occurrenceCancellationLeavesOtherOccurrencesOfSameResource() = runTest {
        registry.replaceAllScheduled(
            listOf(
                ScheduledReminderRecord(11, firstId, firstKey),
                ScheduledReminderRecord(22, secondId, secondKey),
            ),
        )
        registry.recordNotification(ActiveReminderNotification(firstId, firstKey))
        registry.recordNotification(ActiveReminderNotification(secondId, secondKey))

        registry.cancelOccurrence(firstId)

        assertEquals(listOf(11), cancelledAlarms)
        assertEquals(listOf(firstKey), cancelledNotifications)
        assertEquals(listOf(22), storage.scheduled.map { it.alarmRequestCode })
        assertEquals(listOf(secondId), storage.active.map { it.occurrenceId })
    }

    @Test
    fun resourceCancellationRemovesEveryOccurrence() = runTest {
        registry.replaceAllScheduled(
            listOf(
                ScheduledReminderRecord(11, firstId, firstKey),
                ScheduledReminderRecord(22, secondId, secondKey),
            ),
        )
        registry.recordNotification(ActiveReminderNotification(firstId, firstKey))
        registry.recordNotification(ActiveReminderNotification(secondId, secondKey))

        registry.cancelResource("tasks/42.ics")

        assertEquals(listOf(11, 22), cancelledAlarms)
        assertEquals(listOf(firstKey, secondKey), cancelledNotifications)
        assertEquals(emptyList<ScheduledReminderRecord>(), storage.scheduled)
        assertEquals(emptyList<ActiveReminderNotification>(), storage.active)
    }

    @Test
    fun notificationsOfItemsFinishedElsewhereAreTakenDown() = runTest {
        val otherTask = CalendarOccurrenceId.Task("tasks/7.ics", 1_700_000_000_000)
        registry.recordNotification(ActiveReminderNotification(firstId, firstKey))
        registry.recordNotification(ActiveReminderNotification(otherTask, secondKey))
        registry.addSnoozed(snoozed(33, firstId))

        // Task 42 was checked off on another device; task 7 is still open.
        registry.dismissEnded { it != firstId }

        assertEquals(listOf(firstKey), cancelledNotifications)
        assertEquals(listOf(otherTask), storage.active.map { it.occurrenceId })
        assertEquals(emptyList<SnoozedReminder>(), storage.snoozed)
    }

    @Test
    fun nothingIsTakenDownWhileEverythingIsStillDue() = runTest {
        registry.recordNotification(ActiveReminderNotification(firstId, firstKey))

        registry.dismissEnded { true }

        assertEquals(emptyList<ReminderNotificationKey>(), cancelledNotifications)
        assertEquals(listOf(firstId), storage.active.map { it.occurrenceId })
    }

    private fun snoozed(alarmCode: Int, occurrence: CalendarOccurrenceId) = SnoozedReminder(
        alarmRequestCode = alarmCode,
        triggerAtMillis = 1_700_000_600_000,
        title = "Task",
        body = null,
        notificationKey = firstKey,
        encodedTarget = CalendarLaunchTarget(action = CalendarLaunchAction.OpenOccurrence, occurrence = occurrence).encode(),
    )

    @Test
    fun finishingAnOccurrenceTakesItsSnoozeAlong() = runTest {
        registry.addSnoozed(snoozed(33, firstId))
        registry.addSnoozed(snoozed(44, secondId))

        registry.cancelOccurrence(firstId)

        assertEquals(listOf(33), cancelledAlarms)
        assertEquals(listOf(44), storage.snoozed.map { it.alarmRequestCode })
    }

    @Test
    fun removingAResourceTakesEverySnoozeAlong() = runTest {
        registry.addSnoozed(snoozed(33, firstId))
        registry.addSnoozed(snoozed(44, secondId))
        registry.addSnoozed(snoozed(55, CalendarOccurrenceId.Task("tasks/other.ics", 1)))

        registry.cancelResource("tasks/42.ics")

        assertEquals(listOf(33, 44), cancelledAlarms)
        assertEquals(listOf(55), storage.snoozed.map { it.alarmRequestCode })
    }

    @Test
    fun deliveredAlarmsAreTrackedFromTheFirstRecordAndOldOnesExpire() = runTest {
        assertEquals(null, registry.deliveredCodes())

        val now = 1_700_000_000_000
        registry.markDelivered(listOf(11), now - 3 * MissedReminders.GRACE_MILLIS)
        registry.markDelivered(listOf(22), now)

        assertEquals(setOf(22), registry.deliveredCodes())
    }

    @Test
    fun snoozedRemindersArePendingUntilTheyAreLongPast() = runTest {
        val now = 1_700_000_000_000
        val soon = SnoozedReminder(1, now + 600_000, "Soon", null, firstKey, "{}")
        val old = SnoozedReminder(2, now - 2 * MissedReminders.GRACE_MILLIS, "Old", "Body", secondKey, "{}")
        registry.addSnoozed(soon)
        registry.addSnoozed(old)

        assertEquals(listOf(soon), registry.pendingSnoozed(now))

        registry.removeSnoozed(1)
        assertEquals(emptyList<SnoozedReminder>(), registry.pendingSnoozed(now))
    }

    private class InMemoryReminderRegistryStorage : ReminderRegistryStorage {
        var scheduled: List<ScheduledReminderRecord> = emptyList()
        var active: List<ActiveReminderNotification> = emptyList()

        override fun readScheduled(): List<ScheduledReminderRecord> = scheduled
        override fun writeScheduled(records: List<ScheduledReminderRecord>) {
            scheduled = records
        }

        override fun readActive(): List<ActiveReminderNotification> = active
        override fun writeActive(records: List<ActiveReminderNotification>) {
            active = records
        }

        var delivered: Map<Int, Long>? = null
        var snoozed: List<SnoozedReminder> = emptyList()

        override fun readDelivered(): Map<Int, Long>? = delivered
        override fun writeDelivered(delivered: Map<Int, Long>) {
            this.delivered = delivered
        }

        override fun readSnoozed(): List<SnoozedReminder> = snoozed
        override fun writeSnoozed(records: List<SnoozedReminder>) {
            snoozed = records
        }
    }
}
