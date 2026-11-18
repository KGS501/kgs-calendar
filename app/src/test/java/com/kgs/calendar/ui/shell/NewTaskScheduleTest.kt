package com.kgs.calendar.ui.shell

import com.kgs.calendar.time.*
import kotlinx.datetime.*
import com.kgs.calendar.data.settings.TaskDefaultDates
import com.kgs.calendar.ui.editor.EditorScheduleState
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class NewTaskScheduleTest {
    private val today = LocalDate.of(2026, 10, 20)

    private fun schedule(dates: TaskDefaultDates, withTime: Boolean, scheduledForDay: Boolean = false): EditorScheduleState =
        newTaskSchedule(
            date = today,
            now = LocalTime.of(9, 10),
            scheduledForDay = scheduledForDay,
            useTaskDefaults = true,
            defaultTaskDates = dates,
            defaultTaskHasTime = withTime,
            defaultEventDurationMinutes = 60,
        )

    /** start date, start time, end date, end time, all day */
    private fun EditorScheduleState.shape() = listOf(hasStartDate, hasStartTime, hasEndDate, hasEndTime, allDay)

    @Test
    fun eachDefaultGivesNewTasksTheChosenDates() {
        assertEquals(listOf(false, false, false, false, false), schedule(TaskDefaultDates.None, withTime = true).shape())
        assertEquals(listOf(true, false, false, false, true), schedule(TaskDefaultDates.Start, withTime = false).shape())
        assertEquals(listOf(true, true, false, false, false), schedule(TaskDefaultDates.Start, withTime = true).shape())
        assertEquals(listOf(false, false, true, false, true), schedule(TaskDefaultDates.Due, withTime = false).shape())
        assertEquals(listOf(false, false, true, true, false), schedule(TaskDefaultDates.Due, withTime = true).shape())
        assertEquals(listOf(true, false, true, false, true), schedule(TaskDefaultDates.StartAndDue, withTime = false).shape())
        assertEquals(listOf(true, true, true, true, false), schedule(TaskDefaultDates.StartAndDue, withTime = true).shape())
    }

    @Test
    fun aTaskForATappedDayStartsThatDayWithoutTime() {
        assertEquals(
            listOf(true, false, false, false, true),
            schedule(TaskDefaultDates.Due, withTime = true, scheduledForDay = true).shape(),
        )
    }
}
