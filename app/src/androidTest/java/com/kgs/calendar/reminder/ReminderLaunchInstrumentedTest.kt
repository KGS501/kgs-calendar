package com.kgs.calendar.reminder

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kgs.calendar.domain.model.CalendarOccurrenceId
import com.kgs.calendar.navigation.CalendarLaunchAction
import com.kgs.calendar.navigation.CalendarLaunchTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderLaunchInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun taskReminderIntentCarriesExactOccurrence() {
        val occurrenceId = CalendarOccurrenceId.Task("tasks/42.ics", 1_700_000_000_000)
        val plan = ReminderAlarmPlan(
            occurrenceId = occurrenceId,
            triggerAtMillis = 1_700_000_100_000,
            reminderOffsetMinutes = 10,
            alarmRequestCode = 42,
            notificationKey = ReminderNotificationKey("task", 7),
            title = "Task",
            body = "Body",
        )

        val intent = ReminderIntents.contentIntent(context, plan)
        val target = CalendarLaunchTarget.readFrom(intent)

        assertNotNull(intent.data)
        assertEquals(occurrenceId, target?.occurrence)
        assertEquals(CalendarLaunchAction.OpenOccurrence, target?.action)
    }
}
