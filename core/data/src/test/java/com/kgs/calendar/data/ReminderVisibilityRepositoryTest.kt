package com.kgs.calendar.data

import com.kgs.calendar.domain.model.CalendarOccurrenceId
import com.kgs.calendar.domain.source.CollectionVisibility
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
class ReminderVisibilityRepositoryTest {
    private val harness = RepositoryHarness()
    private val repository = harness.repository
    private val day = LocalDate.now(TEST_ZONE).plusDays(2)

    @Before
    fun setUp() = runTest {
        repository.ensureLocalCalendar()
        val local = harness.collection(LOCAL_COLLECTION)!!
        harness.database.collectionDao().upsertAll(
            listOf(local.copy(href = SECOND_COLLECTION, displayName = "Second", sortOrder = 1)),
        )
        listOf(LOCAL_COLLECTION, SECOND_COLLECTION).forEach { href ->
            repository.createEvent(
                eventPayload("Event $href", day, collectionHref = href).copy(reminderMinutes = listOf(15)),
            )
            repository.createTask(
                taskPayload("Task $href", collectionHref = href, dueDate = day, dueTime = LocalTime.NOON)
                    .copy(reminderMinutes = listOf(15)),
            )
        }
    }

    @After
    fun tearDown() = harness.close()

    @Test
    fun byDefaultEveryCalendarWithRemindersIsPlanned() = runTest {
        val (events, tasks) = repository.reminderCandidates()

        assertEquals(setOf(LOCAL_COLLECTION, SECOND_COLLECTION), events.map { it.collectionHref }.toSet())
        assertEquals(setOf(LOCAL_COLLECTION, SECOND_COLLECTION), tasks.map { it.collectionHref }.toSet())
    }

    @Test
    fun aHiddenCalendarPlansNoReminders() = runTest {
        val (events, tasks) = repository.reminderCandidates(
            CollectionVisibility(hiddenCollectionHrefs = setOf(LOCAL_COLLECTION)),
        )

        assertEquals(listOf(SECOND_COLLECTION), events.map { it.collectionHref })
        assertEquals(listOf(SECOND_COLLECTION), tasks.map { it.collectionHref })
    }

    @Test
    fun hiddenTasksPlanNoRemindersWhileTheEventsOfTheCalendarStillDo() = runTest {
        val (events, tasks) = repository.reminderCandidates(
            CollectionVisibility(tasksHiddenIn = setOf(LOCAL_COLLECTION)),
        )

        assertEquals(setOf(LOCAL_COLLECTION, SECOND_COLLECTION), events.map { it.collectionHref }.toSet())
        assertEquals(listOf(SECOND_COLLECTION), tasks.map { it.collectionHref })
    }

    @Test
    fun hiddenEventsPlanNoRemindersWhileTheTasksOfTheCalendarStillDo() = runTest {
        val (events, tasks) = repository.reminderCandidates(
            CollectionVisibility(eventsHiddenIn = setOf(SECOND_COLLECTION)),
        )

        assertEquals(listOf(LOCAL_COLLECTION), events.map { it.collectionHref })
        assertEquals(setOf(LOCAL_COLLECTION, SECOND_COLLECTION), tasks.map { it.collectionHref }.toSet())
    }

    @Test
    fun aDisabledCalendarPlansNoReminders() = runTest {
        repository.setCollectionEnabled(SECOND_COLLECTION, false)

        val (events, tasks) = repository.reminderCandidates()

        assertEquals(listOf(LOCAL_COLLECTION), events.map { it.collectionHref })
        assertEquals(listOf(LOCAL_COLLECTION), tasks.map { it.collectionHref })
    }

    @Test
    fun aDueReminderIsCheckedAgainstTheCurrentVisibility() = runTest {
        val (events, tasks) = repository.reminderCandidates()
        val localEvent = events.single { it.collectionHref == LOCAL_COLLECTION }
        val localTask = tasks.single { it.collectionHref == LOCAL_COLLECTION }
        val secondTask = tasks.single { it.collectionHref == SECOND_COLLECTION }
        val eventId = CalendarOccurrenceId.Event(localEvent.resourceHref, localEvent.startsAtMillis)
        val taskId = CalendarOccurrenceId.Task(localTask.resourceHref, localTask.dueAtMillis!!)
        val tasksHidden = CollectionVisibility(tasksHiddenIn = setOf(LOCAL_COLLECTION))

        assertEquals(true, repository.isReminderStillVisible(eventId, tasksHidden))
        assertEquals(false, repository.isReminderStillVisible(taskId, tasksHidden))
        assertEquals(
            false,
            repository.isReminderStillVisible(eventId, CollectionVisibility(hiddenCollectionHrefs = setOf(LOCAL_COLLECTION))),
        )

        repository.setCollectionEnabled(SECOND_COLLECTION, false)
        val secondTaskId = CalendarOccurrenceId.Task(secondTask.resourceHref, secondTask.dueAtMillis!!)
        assertEquals(false, repository.isReminderStillVisible(secondTaskId, CollectionVisibility()))
        assertNull(repository.isReminderStillVisible(CalendarOccurrenceId.Task("missing.ics", 0L), CollectionVisibility()))
    }

    private companion object {
        const val SECOND_COLLECTION = "local://kgs-calendar/second"
    }
}
