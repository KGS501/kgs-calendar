package com.kgs.calendar.ui.editor

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EditorScheduleStateTest {
    private val date = LocalDate.of(2026, 7, 10)
    private val initialPreview = EditorSchedulePreview(
        date = date,
        start = LocalTime.of(9, 0),
        end = LocalTime.of(10, 0),
        allDay = false,
    )
    private val initial = EditorScheduleState(
        startDateText = date.toString(),
        endDateText = date.toString(),
        startTimeText = "09:00",
        endTimeText = "10:00",
        hasStartDate = true,
        hasEndDate = true,
        hasStartTime = true,
        hasEndTime = true,
        allDay = false,
        lastValidPreview = initialPreview,
    )

    @Test
    fun timelineMoveUpdatesEditorFields() {
        val moved = initial.applyTimelineChange(
            EditorSchedulePreview(
                date = date.plusDays(1),
                start = LocalTime.of(10, 15),
                end = LocalTime.of(11, 15),
                allDay = false,
            ),
        )

        assertEquals("2026-07-11", moved.startDateText)
        assertEquals("2026-07-11", moved.endDateText)
        assertEquals("10:15", moved.startTimeText)
        assertEquals("11:15", moved.endTimeText)
        assertEquals(moved.lastValidPreview, moved.recalculatePreview().lastValidPreview)
    }

    @Test
    fun invalidFieldTextKeepsLastValidPreview() {
        val invalid = initial.copy(startTimeText = "10:").recalculatePreview()

        assertEquals(initial.lastValidPreview, invalid.lastValidPreview)
    }

    @Test
    fun invalidRangeKeepsLastValidPreview() {
        val invalid = initial.copy(startTimeText = "11:00", endTimeText = "10:00").recalculatePreview()

        assertEquals(initial.lastValidPreview, invalid.lastValidPreview)
    }

    @Test
    fun allDayTimelineChangeNormalizesFlagsAndTimes() {
        val moved = initial.applyTimelineChange(
            EditorSchedulePreview(
                date = date.plusDays(2),
                start = LocalTime.MIDNIGHT,
                end = LocalTime.of(23, 59),
                allDay = true,
            ),
        )

        assertEquals(true, moved.hasStartDate)
        assertEquals(true, moved.hasEndDate)
        assertEquals(false, moved.hasStartTime)
        assertEquals(false, moved.hasEndTime)
        assertEquals("00:00", moved.startTimeText)
        assertEquals("23:59", moved.endTimeText)
    }

    @Test
    fun validUnscheduledStateClearsPreview() {
        val unscheduled = initial.copy(
            hasStartDate = false,
            hasEndDate = false,
            hasStartTime = false,
            hasEndTime = false,
        ).recalculatePreview()

        assertNull(unscheduled.lastValidPreview)
    }
}
