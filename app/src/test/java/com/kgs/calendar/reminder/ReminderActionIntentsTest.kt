package com.kgs.calendar.reminder

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.kgs.calendar.domain.model.CalendarOccurrenceId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ReminderActionIntentsTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val first = CalendarOccurrenceId.Task("tasks/a.ics", 1_000)
    private val second = CalendarOccurrenceId.Task("tasks/b.ics", 2_000)

    private fun key(id: CalendarOccurrenceId.Task) = ReminderNotificationKey("kgs-reminder:${id.stableKey}", id.stableKey.hashCode())

    @Test
    fun simultaneousNotificationsKeepTheirOwnMarkDoneTargetInBothOrders() {
        listOf(first to second, second to first).forEach { (shownFirst, shownSecond) ->
            val earlier = ReminderIntents.markDonePendingIntent(context, shownFirst, key(shownFirst))
            val later = ReminderIntents.markDonePendingIntent(context, shownSecond, key(shownSecond))

            assertNotEquals(earlier, later)
            val earlierIntent = shadowOf(earlier).savedIntent
            assertTrue(earlierIntent.getStringExtra("extra_notification_tag")!!.contains(shownFirst.resourceHref))
            assertFalse(earlierIntent.filterEquals(shadowOf(later).savedIntent))
        }
    }

    @Test
    fun occurrencesOfOneSeriesHaveTheirOwnAction() {
        val today = CalendarOccurrenceId.Task("tasks/a.ics", 1_000)
        val tomorrow = CalendarOccurrenceId.Task("tasks/a.ics", 2_000)

        val actions = listOf(today, tomorrow).map { ReminderIntents.markDonePendingIntent(context, it, key(it)) }

        assertNotEquals(actions[0], actions[1])
    }
}
