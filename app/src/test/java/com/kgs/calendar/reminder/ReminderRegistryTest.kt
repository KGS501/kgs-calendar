package com.kgs.calendar.reminder

import com.kgs.calendar.domain.model.CalendarOccurrenceId
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
    }
}
