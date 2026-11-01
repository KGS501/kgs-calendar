package com.kgs.calendar.data.search

import com.kgs.calendar.data.ical.EventRecurrenceOverride
import com.kgs.calendar.data.ical.RecurrenceOverrideCodec
import com.kgs.calendar.data.ical.TaskRecurrenceOverride
import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.local.entity.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.concurrent.CancellationException
import java.time.LocalDate
import java.time.ZoneId

class CalendarOccurrenceSearchTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val search = CalendarOccurrenceSearch(zone)

    @Test
    fun supersededSearchCanCancelBetweenCalendarMasters() {
        var checks = 0
        val masters = (1..4).map { index ->
            event(
                title = "Team sync $index",
                startDate = LocalDate.of(2026, 8, 17),
            )
        }

        assertThrows(CancellationException::class.java) {
            search.events(
                masters = masters,
                query = "team",
                mode = CalendarSearchMode.TextAndLabels,
                rangeStartMillis = startOfDay(LocalDate.of(2026, 8, 17)),
                rangeEndMillis = startOfDay(LocalDate.of(2026, 8, 21)),
                cancellationCheck = {
                    checks++
                    if (checks == 2) throw CancellationException("superseded")
                },
            )
        }
        assertEquals(2, checks)
    }

    @Test
    fun matchingRecurringEventReturnsEveryOccurrenceInLoadedWindow() {
        val master = event(
            title = "Team sync",
            startDate = LocalDate.of(2026, 8, 17),
            recurrenceRule = "FREQ=DAILY;COUNT=3",
        )

        val results = search.events(
            masters = listOf(master),
            query = "team",
            mode = CalendarSearchMode.TextAndLabels,
            rangeStartMillis = startOfDay(LocalDate.of(2026, 8, 17)),
            rangeEndMillis = startOfDay(LocalDate.of(2026, 8, 21)),
        )

        assertEquals(
            listOf(
                LocalDate.of(2026, 8, 17),
                LocalDate.of(2026, 8, 18),
                LocalDate.of(2026, 8, 19),
            ),
            results.map { LocalDate.ofInstant(java.time.Instant.ofEpochMilli(it.startsAtMillis), zone) },
        )
    }

    @Test
    fun matchingRecurringTaskReturnsEveryOccurrenceInLoadedWindow() {
        val master = task(
            title = "Water plants",
            startDate = LocalDate.of(2026, 8, 17),
            recurrenceRule = "FREQ=DAILY;COUNT=3",
        )

        val results = search.tasks(
            masters = listOf(master),
            query = "plants",
            mode = CalendarSearchMode.TextAndLabels,
            rangeStartMillis = startOfDay(LocalDate.of(2026, 8, 17)),
            rangeEndMillis = startOfDay(LocalDate.of(2026, 8, 21)),
        )

        assertEquals(
            listOf(
                LocalDate.of(2026, 8, 17),
                LocalDate.of(2026, 8, 18),
                LocalDate.of(2026, 8, 19),
            ),
            results.map { LocalDate.ofInstant(java.time.Instant.ofEpochMilli(it.startAtMillis!!), zone) },
        )
    }

    @Test
    fun matchingMovedOverrideIsFoundAtItsActualDate() {
        val originalDate = LocalDate.of(2026, 8, 17)
        val movedDate = LocalDate.of(2026, 8, 25)
        val master = event(
            title = "Team sync",
            startDate = originalDate,
            recurrenceRule = "FREQ=DAILY;COUNT=3",
        )
        val movedStart = movedDate.atTime(14, 0).atZone(zone).toInstant().toEpochMilli()
        val moved = master.copy(
            title = "Dentist",
            startsAtMillis = movedStart,
            endsAtMillis = movedStart + 30L * 60L * 1000L,
        )
        val withOverride = master.copy(
            recurrenceOverridesJson = RecurrenceOverrideCodec.encodeEvents(
                listOf(EventRecurrenceOverride.fromEvent(master.startsAtMillis, moved)),
            ),
        )

        val results = search.events(
            masters = listOf(withOverride),
            query = "dentist",
            mode = CalendarSearchMode.TextAndLabels,
            rangeStartMillis = startOfDay(LocalDate.of(2026, 8, 24)),
            rangeEndMillis = startOfDay(LocalDate.of(2026, 8, 27)),
        )

        assertEquals(listOf(movedStart), results.map(EventEntity::startsAtMillis))
    }

    @Test
    fun matchingMovedTaskOverrideIsFoundAtItsActualDate() {
        val master = task(
            title = "Weekly chores",
            startDate = LocalDate.of(2026, 8, 17),
            recurrenceRule = "FREQ=DAILY;COUNT=2",
        )
        val movedStart = LocalDate.of(2026, 8, 25).atTime(14, 0).atZone(zone).toInstant().toEpochMilli()
        val moved = master.copy(
            title = "Buy potting soil",
            startAtMillis = movedStart,
            dueAtMillis = movedStart + 30L * 60L * 1000L,
        )
        val withOverride = master.copy(
            recurrenceOverridesJson = RecurrenceOverrideCodec.encodeTasks(
                listOf(TaskRecurrenceOverride.fromTask(master.startAtMillis!!, moved)),
            ),
        )

        val results = search.tasks(
            masters = listOf(withOverride),
            query = "potting",
            mode = CalendarSearchMode.TextAndLabels,
            rangeStartMillis = startOfDay(LocalDate.of(2026, 8, 24)),
            rangeEndMillis = startOfDay(LocalDate.of(2026, 8, 27)),
        )

        assertEquals(listOf(movedStart), results.map(TaskEntity::startAtMillis))
    }

    @Test
    fun labelModesDistinguishTextFromLabelsForEventsAndTasks() {
        val textOnlyEvent = event("Work review", LocalDate.of(2026, 8, 20), categories = "Personal")
        val labelledEvent = event("Dentist", LocalDate.of(2026, 8, 21), categories = "Work,Health")
        val labelledTask = task("Buy soil", LocalDate.of(2026, 8, 22), categories = "Work")
        val from = startOfDay(LocalDate.of(2026, 8, 1))
        val to = startOfDay(LocalDate.of(2026, 9, 1))

        assertEquals(
            listOf(textOnlyEvent.resourceHref),
            search.events(listOf(textOnlyEvent, labelledEvent), "work", CalendarSearchMode.Text, from, to)
                .map(EventEntity::resourceHref),
        )
        assertEquals(
            listOf(labelledEvent.resourceHref),
            search.events(listOf(textOnlyEvent, labelledEvent), "work", CalendarSearchMode.LabelsOnly, from, to)
                .map(EventEntity::resourceHref),
        )
        assertEquals(
            listOf(textOnlyEvent.resourceHref, labelledEvent.resourceHref),
            search.events(listOf(textOnlyEvent, labelledEvent), "work", CalendarSearchMode.TextAndLabels, from, to)
                .map(EventEntity::resourceHref),
        )
        assertEquals(
            listOf(labelledTask.resourceHref),
            search.tasks(listOf(labelledTask), "work", CalendarSearchMode.LabelsOnly, from, to)
                .map(TaskEntity::resourceHref),
        )
    }

    @Test
    fun cancelledOverrideIsExcludedWhileTheSeriesContinues() {
        val master = event(
            title = "Team sync",
            startDate = LocalDate.of(2026, 8, 17),
            recurrenceRule = "FREQ=DAILY;COUNT=3",
        )
        val secondStart = LocalDate.of(2026, 8, 18).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val cancelled = master.copy(
            startsAtMillis = secondStart,
            endsAtMillis = secondStart + 60L * 60L * 1000L,
            status = "CANCELLED",
        )
        val withOverride = master.copy(
            recurrenceOverridesJson = RecurrenceOverrideCodec.encodeEvents(
                listOf(EventRecurrenceOverride.fromEvent(secondStart, cancelled)),
            ),
        )

        val results = search.events(
            listOf(withOverride),
            "team",
            CalendarSearchMode.TextAndLabels,
            startOfDay(LocalDate.of(2026, 8, 17)),
            startOfDay(LocalDate.of(2026, 8, 21)),
        )

        assertEquals(
            listOf(LocalDate.of(2026, 8, 17), LocalDate.of(2026, 8, 19)),
            results.map { LocalDate.ofInstant(java.time.Instant.ofEpochMilli(it.startsAtMillis), zone) },
        )
    }

    @Test
    fun cancelledTaskOverrideRemainsSearchableAsAnOccurrence() {
        val master = task(
            title = "Weekly chores",
            startDate = LocalDate.of(2026, 8, 17),
            recurrenceRule = "FREQ=DAILY;COUNT=3",
        )
        val secondStart = LocalDate.of(2026, 8, 18).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val cancelled = master.copy(
            title = "Water plants",
            startAtMillis = secondStart,
            dueAtMillis = secondStart + 60L * 60L * 1000L,
            status = "CANCELLED",
        )
        val withOverride = master.copy(
            recurrenceOverridesJson = RecurrenceOverrideCodec.encodeTasks(
                listOf(TaskRecurrenceOverride.fromTask(secondStart, cancelled)),
            ),
        )

        val results = search.tasks(
            listOf(withOverride),
            "PLANTS",
            CalendarSearchMode.TextAndLabels,
            startOfDay(LocalDate.of(2026, 8, 17)),
            startOfDay(LocalDate.of(2026, 8, 21)),
        )

        assertEquals(
            listOf("CANCELLED"),
            results.map { it.status },
        )
    }

    @Test
    fun undatedRecurringTaskRemainsSearchableAsOneTask() {
        val task = task(
            title = "Someday review",
            startDate = LocalDate.of(2026, 8, 20),
            recurrenceRule = "FREQ=WEEKLY",
        ).copy(startAtMillis = null, dueAtMillis = null)

        val results = search.tasks(
            listOf(task),
            "someday",
            CalendarSearchMode.TextAndLabels,
            startOfDay(LocalDate.of(2026, 8, 1)),
            startOfDay(LocalDate.of(2026, 9, 1)),
        )

        assertEquals(listOf(task), results)
    }

    private fun event(
        title: String,
        startDate: LocalDate,
        recurrenceRule: String? = null,
        categories: String? = null,
    ): EventEntity {
        val start = startDate.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        return EventEntity(
            uid = "event-$title",
            collectionHref = "/events/",
            resourceHref = "/events/$title.ics",
            title = title,
            description = null,
            location = null,
            startsAtMillis = start,
            endsAtMillis = start + 60L * 60L * 1000L,
            allDay = false,
            recurrenceRule = recurrenceRule,
            isRecurring = !recurrenceRule.isNullOrBlank(),
            timezoneId = zone.id,
            categories = categories,
            color = 0,
        )
    }

    private fun task(
        title: String,
        startDate: LocalDate,
        recurrenceRule: String? = null,
        categories: String? = null,
    ): TaskEntity {
        val start = startDate.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        return TaskEntity(
            uid = "task-$title",
            collectionHref = "/tasks/",
            resourceHref = "/tasks/$title.ics",
            title = title,
            notes = null,
            categories = categories,
            dueAtMillis = start + 60L * 60L * 1000L,
            startAtMillis = start,
            completedAtMillis = null,
            isCompleted = false,
            priority = null,
            recurrenceRule = recurrenceRule,
            timezoneId = zone.id,
            color = 0,
        )
    }

    private fun startOfDay(date: LocalDate): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()
}
