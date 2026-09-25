package com.kgs.calendar.widget.update

import com.kgs.calendar.data.ical.RecurrenceOverrideCodec
import com.kgs.calendar.data.ical.TaskRecurrenceOverride
import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.data.recurrence.occurrenceAt
import com.kgs.calendar.domain.model.CalendarOccurrenceId
import com.kgs.calendar.reminder.TaskMutationCoordinator
import com.kgs.calendar.reminder.TaskNotificationReconciler
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class WidgetTaskToggleTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val today = LocalDate.of(2026, 9, 25)
    private val statusWrites = mutableListOf<Triple<String, String, CalendarOccurrenceId.Task?>>()
    private val cancelledOccurrences = mutableListOf<CalendarOccurrenceId.Task>()
    private val cancelledResources = mutableListOf<String>()
    private val tasks = mutableMapOf<String, TaskEntity>()
    private var currentOccurrenceStart: Long? = null

    private val coordinator = TaskMutationCoordinator(
        persistStatus = { resourceHref, status, occurrenceId -> statusWrites += Triple(resourceHref, status, occurrenceId) },
        pushPendingChanges = {},
        notificationReconciler = object : TaskNotificationReconciler {
            override suspend fun cancelOccurrence(occurrenceId: CalendarOccurrenceId.Task) {
                cancelledOccurrences += occurrenceId
            }

            override suspend fun cancelResource(resourceHref: String) {
                cancelledResources += resourceHref
            }
        },
        rescheduleReminders = {},
        updateWidgets = {},
    )

    private val toggle = WidgetTaskToggle(
        taskByResource = { tasks[it] },
        currentOccurrence = { master -> currentOccurrenceStart?.let(master::occurrenceAt) },
        coordinator = coordinator,
    )

    @Test
    fun recurringRowCompletesOnlyTheTappedOccurrence() = runTest {
        val master = recurring().also { tasks[it.resourceHref] = it }

        toggle.toggle(master.resourceHref, at(today))

        val occurrence = CalendarOccurrenceId.Task(master.resourceHref, at(today))
        assertEquals(listOf(Triple(master.resourceHref, "COMPLETED", occurrence)), statusWrites)
        assertEquals(listOf(occurrence), cancelledOccurrences)
        assertEquals(emptyList<String>(), cancelledResources)
    }

    @Test
    fun completedOccurrenceIsReopenedWithoutTouchingTheSeries() = runTest {
        val master = recurring()
        val done = master.occurrenceAt(at(today)).copy(isCompleted = true, status = "COMPLETED", completedAtMillis = at(today))
        tasks[master.resourceHref] = master.copy(
            recurrenceOverridesJson = RecurrenceOverrideCodec.encodeTasks(listOf(TaskRecurrenceOverride.fromTask(at(today), done))),
        )

        toggle.toggle(master.resourceHref, at(today))
        toggle.toggle(master.resourceHref, at(today.plusDays(1)))

        assertEquals(
            listOf(
                Triple(master.resourceHref, "NEEDS-ACTION", CalendarOccurrenceId.Task(master.resourceHref, at(today))),
                Triple(master.resourceHref, "COMPLETED", CalendarOccurrenceId.Task(master.resourceHref, at(today.plusDays(1)))),
            ),
            statusWrites,
        )
    }

    @Test
    fun rowWithoutAnOccurrenceUsesTheListedOneAndNeverTheWholeSeries() = runTest {
        val master = recurring().also { tasks[it.resourceHref] = it }

        currentOccurrenceStart = null
        toggle.toggle(master.resourceHref, occurrenceMillis = null)
        assertEquals(emptyList<Any>(), statusWrites)

        currentOccurrenceStart = at(today.plusDays(2))
        toggle.toggle(master.resourceHref, occurrenceMillis = null)
        assertEquals(
            listOf(Triple(master.resourceHref, "COMPLETED", CalendarOccurrenceId.Task(master.resourceHref, at(today.plusDays(2))))),
            statusWrites,
        )
    }

    @Test
    fun singleTaskStillTogglesAsAWhole() = runTest {
        val single = recurring().copy(resourceHref = "/tasks/single.ics", recurrenceRule = null)
        tasks[single.resourceHref] = single
        tasks["/tasks/done.ics"] = single.copy(resourceHref = "/tasks/done.ics", isCompleted = true, status = "COMPLETED")

        toggle.toggle(single.resourceHref, occurrenceMillis = null)
        toggle.toggle("/tasks/done.ics", occurrenceMillis = null)
        toggle.toggle("/tasks/missing.ics", occurrenceMillis = null)

        assertEquals(
            listOf(
                Triple("/tasks/single.ics", "COMPLETED", null),
                Triple("/tasks/done.ics", "NEEDS-ACTION", null),
            ),
            statusWrites,
        )
        assertEquals(listOf("/tasks/single.ics"), cancelledResources)
    }

    private fun at(date: LocalDate): Long = date.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()

    private fun recurring() = TaskEntity(
        uid = "daily",
        collectionHref = "/tasks/",
        resourceHref = "/tasks/daily.ics",
        title = "Daily",
        notes = null,
        dueAtMillis = at(today.minusDays(3)) + 60 * 60 * 1000L,
        startAtMillis = at(today.minusDays(3)),
        completedAtMillis = null,
        isCompleted = false,
        status = "NEEDS-ACTION",
        priority = null,
        recurrenceRule = "FREQ=DAILY",
        timezoneId = zone.id,
        color = 0,
    )
}
