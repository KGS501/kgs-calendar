package com.kgs.calendar.reminder

import com.kgs.calendar.domain.model.CalendarOccurrenceId
import com.kgs.calendar.domain.model.REMINDER_AT_END
import com.kgs.calendar.domain.model.REMINDER_AT_START
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ReminderPlannerTest {
    private val planner = ReminderPlanner()
    private val taskOccurrence = ReminderOccurrence(
        occurrenceId = CalendarOccurrenceId.Task("tasks/42.ics", 1_700_000_000_000),
        startAtMillis = 2_000_000L,
        endAtMillis = 2_600_000L,
        defaultAnchorAtMillis = 2_600_000L,
        title = "Ship release",
        body = "Review checklist",
    )

    @Test
    fun alarmsAreUniqueButNotificationsReplaceWithinOccurrence() {
        val plans = planner.plan(taskOccurrence, offsets = listOf(30, 10))

        assertNotEquals(plans[0].alarmRequestCode, plans[1].alarmRequestCode)
        assertEquals(plans[0].notificationKey, plans[1].notificationKey)
    }

    @Test
    fun separateOccurrencesUseSeparateNotificationKeys() {
        val second = taskOccurrence.copy(
            occurrenceId = CalendarOccurrenceId.Task("tasks/42.ics", 1_700_086_400_000),
        )

        assertNotEquals(planner.key(taskOccurrence), planner.key(second))
    }

    @Test
    fun startAndEndOffsetsUseTheirExactAnchors() {
        val plans = planner.plan(taskOccurrence, offsets = listOf(REMINDER_AT_START, REMINDER_AT_END))

        assertEquals(2_000_000L, plans[0].triggerAtMillis)
        assertEquals(2_600_000L, plans[1].triggerAtMillis)
    }
}
