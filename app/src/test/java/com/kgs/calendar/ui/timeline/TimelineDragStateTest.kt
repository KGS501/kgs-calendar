package com.kgs.calendar.ui.timeline

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineDragStateTest {
    private val date = LocalDate.of(2026, 7, 10)
    private val timedTarget = TimelineDropTarget.Timed(
        date = date,
        startMinute = 9 * 60,
        endMinute = 10 * 60,
    )
    private val allDayTarget = TimelineDropTarget.AllDay(date = date, lane = 0)
    private val reducer = TimelineDragReducer(entryMarginPx = 12f, exitMarginPx = 16f)

    @Test
    fun emptyAllDayHoverCreatesAndRemovesReservationWithHysteresis() {
        val timed = TimelineDragSession(target = timedTarget)

        val entered = reducer.update(
            pointerY = 80f,
            boundaryY = 100f,
            timedTarget = timedTarget,
            allDayTarget = allDayTarget,
            previous = timed,
        )
        assertTrue(entered.target is TimelineDropTarget.AllDay)
        assertEquals(AllDayReservation(date, lane = 0), entered.reservation)

        val retained = reducer.update(
            pointerY = 106f,
            boundaryY = 100f,
            timedTarget = timedTarget,
            allDayTarget = allDayTarget,
            previous = entered,
        )
        assertTrue(retained.target is TimelineDropTarget.AllDay)

        val exited = reducer.update(
            pointerY = 124f,
            boundaryY = 100f,
            timedTarget = timedTarget,
            allDayTarget = allDayTarget,
            previous = retained,
        )
        assertEquals(timedTarget, exited.target)
        assertNull(exited.reservation)
    }

    @Test
    fun timedHoverMustCrossEntryMarginBeforeReservingAllDayLane() {
        val previous = TimelineDragSession(target = timedTarget)

        val nearBoundary = reducer.update(
            pointerY = 94f,
            boundaryY = 100f,
            timedTarget = timedTarget,
            allDayTarget = TimelineDropTarget.AllDay(date, lane = 2),
            previous = previous,
        )

        assertEquals(timedTarget, nearBoundary.target)
        assertNull(nearBoundary.reservation)
    }

    @Test
    fun reservationUsesTheCurrentDateAndCandidateLane() {
        val nextDate = date.plusDays(1)
        val candidate = TimelineDropTarget.AllDay(nextDate, lane = 3)

        val entered = reducer.update(
            pointerY = 60f,
            boundaryY = 100f,
            timedTarget = timedTarget,
            allDayTarget = candidate,
            previous = TimelineDragSession(target = timedTarget),
        )

        assertEquals(candidate, entered.target)
        assertEquals(AllDayReservation(nextDate, lane = 3), entered.reservation)
    }
}
