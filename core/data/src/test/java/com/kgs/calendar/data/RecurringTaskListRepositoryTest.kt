package com.kgs.calendar.data

import com.kgs.calendar.data.recurrence.occurrenceAt
import com.kgs.calendar.domain.task.occurrenceIdOrNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDate

/** Task lists show an open recurring series once, as its current occurrence (issue #20). */
@RunWith(RobolectricTestRunner::class)
class RecurringTaskListRepositoryTest {
    private val harness = RepositoryHarness()
    private val repository = harness.repository
    private val today = LocalDate.of(2026, 9, 25)
    private val todayStart = harness.startOfDay(today)

    @After
    fun tearDown() = harness.close()

    @Test
    fun completingTheListedOccurrenceCompletesOnlyThatDayAndListsTheNextOne() = runTest {
        repository.ensureLocalCalendar()
        repository.createTask(taskPayload("Water plants", dueDate = today.minusDays(3)).copy(recurrenceRule = "FREQ=DAILY"))
        repository.createTask(taskPayload("Single", dueDate = today.minusDays(1)))

        val listed = repository.observeScheduledOpenTasks(flowOf(todayStart)).first()
        assertEquals(listOf("Single", "Water plants"), listed.map { it.title })
        val occurrence = listed.single { it.title == "Water plants" }
        assertEquals(today, occurrence.dueDate())
        val occurrenceId = occurrence.occurrenceIdOrNull()!!
        assertEquals(null, listed.single { it.title == "Single" }.occurrenceIdOrNull())

        repository.setTaskOccurrenceStatus(occurrenceId.resourceHref, occurrenceId.recurrenceIdMillis, "COMPLETED")

        val master = repository.taskByResource(occurrence.resourceHref)!!
        assertFalse(master.isCompleted)
        assertTrue(master.occurrenceAt(occurrenceId.recurrenceIdMillis).isCompleted)
        val next = repository.observeScheduledOpenTasks(flowOf(todayStart)).first().single { it.title == "Water plants" }
        assertEquals(today.plusDays(1), next.dueDate())
        assertFalse(next.isCompleted)

        val calendarDays = repository.datedTasksSnapshot(todayStart, harness.startOfDay(today.plusDays(2)))
            .filter { it.title == "Water plants" }
        assertEquals(listOf(today to true, today.plusDays(1) to false), calendarDays.map { it.dueDate() to it.isCompleted })

        val widgetList = repository.taskListSnapshot(todayStart).filter { it.title == "Water plants" }
        assertEquals(listOf(today.plusDays(1)), widgetList.map { it.dueDate() })
    }

    @Test
    fun missedOccurrencesStayInTheOverdueListUntilCompletedWhileTheTaskListShowsTheNextOne() = runTest {
        repository.ensureLocalCalendar()
        repository.createTask(taskPayload("Water plants", dueDate = today.minusDays(3)).copy(recurrenceRule = "FREQ=DAILY"))
        repository.createTask(taskPayload("Single", dueDate = today.minusDays(1)))

        val missed = repository.observeMissedTaskOccurrences(flowOf(todayStart)).first()
        assertEquals(listOf(3L, 2L, 1L).map { today.minusDays(it) }, missed.map { it.dueDate() })
        assertTrue(missed.all { it.title == "Water plants" && !it.isCompleted })
        assertEquals(missed, repository.missedTaskOccurrencesSnapshot(todayStart))

        val completed = missed[1].occurrenceIdOrNull()!!
        repository.setTaskOccurrenceStatus(completed.resourceHref, completed.recurrenceIdMillis, "COMPLETED")

        val remaining = repository.observeMissedTaskOccurrences(flowOf(todayStart)).first()
        assertEquals(listOf(today.minusDays(3), today.minusDays(1)), remaining.map { it.dueDate() })
        val listed = repository.observeScheduledOpenTasks(flowOf(todayStart)).first()
        assertEquals(listOf("Single" to today.minusDays(1), "Water plants" to today), listed.map { it.title to it.dueDate() })
        assertFalse(repository.taskByResource(completed.resourceHref)!!.isCompleted)
    }

    private fun com.kgs.calendar.data.local.entity.TaskEntity.dueDate(): LocalDate =
        Instant.ofEpochMilli(dueAtMillis!!).atZone(TEST_ZONE).toLocalDate()
}
