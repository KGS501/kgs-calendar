package com.kgs.calendar.data.export

import com.kgs.calendar.data.LOCAL_COLLECTION
import com.kgs.calendar.data.RepositoryHarness
import com.kgs.calendar.data.eventPayload
import com.kgs.calendar.data.ical.IcalCodec
import com.kgs.calendar.data.ical.IcsEventReader
import com.kgs.calendar.data.taskPayload
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
class CalendarExportTest {
    private val harness = RepositoryHarness()
    private val repository = harness.repository

    @After
    fun tearDown() = harness.close()

    @Test
    fun exportsEventsAndTasksOfTheLocalCalendarAsOneFile() = runTest {
        repository.ensureLocalCalendar()
        val day = LocalDate.of(2026, 11, 5)
        repository.createEvent(eventPayload("Dentist", day, LocalTime.of(9, 0), LocalTime.of(10, 0), description = "Bring card"))
        repository.createEvent(eventPayload("Standup", day, LocalTime.of(10, 0), LocalTime.of(10, 15), recurrenceRule = "FREQ=DAILY;COUNT=3"))
        repository.createTask(taskPayload("Pay invoice"))

        val result = harness.components.export.export(listOf(LOCAL_COLLECTION))
        val raw = result.rawIcs

        assertEquals(3, result.items)
        assertTrue(raw.startsWith("BEGIN:VCALENDAR\r\n"))
        assertTrue(raw.endsWith("END:VCALENDAR\r\n"))
        assertEquals(2, Regex("BEGIN:VEVENT").findAll(raw).count())
        assertEquals(1, Regex("BEGIN:VTODO").findAll(raw).count())
        // One definition per zone, however many items use it.
        assertEquals(1, Regex("BEGIN:VTIMEZONE").findAll(raw).count())
        assertTrue(raw.lines().all { it.toByteArray().size <= 75 })

        // The exported events can be imported again.
        val events = IcsEventReader(IcalCodec(harness.zone)).read(raw.replace(Regex("BEGIN:VTODO.*?END:VTODO\r\n", RegexOption.DOT_MATCHES_ALL), ""))
        assertEquals(setOf("Dentist", "Standup"), events.map { it.event.title }.toSet())
        assertEquals("Bring card", events.single { it.event.title == "Dentist" }.event.description)
    }
}
