package com.kgs.calendar.ui

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarWeekPresentationTest {
    @Test
    fun narrowTimelineDaysUseTheCompactWeekNumberPill() {
        assertEquals(
            TimelineCalendarWeekLabelMode.Compact,
            timelineCalendarWeekLabelMode(
                dayWidthPx = 64f,
                minimumFullLabelWidthPx = 88f,
            ),
        )
        assertEquals(
            TimelineCalendarWeekLabelMode.Full,
            timelineCalendarWeekLabelMode(
                dayWidthPx = 100f,
                minimumFullLabelWidthPx = 88f,
            ),
        )
    }

    @Test
    fun mondayBasedWeekOneCanStartInThePreviousCalendarYear() {
        assertEquals(52, LocalDate.of(2025, 12, 28).calendarWeekNumber(DayOfWeek.MONDAY))
        assertEquals(1, LocalDate.of(2025, 12, 29).calendarWeekNumber(DayOfWeek.MONDAY))
        assertEquals(1, LocalDate.of(2025, 12, 31).calendarWeekNumber(DayOfWeek.MONDAY))
        assertEquals(1, LocalDate.of(2026, 1, 4).calendarWeekNumber(DayOfWeek.MONDAY))
        assertEquals(2, LocalDate.of(2026, 1, 5).calendarWeekNumber(DayOfWeek.MONDAY))
    }

    @Test
    fun weekNumberUsesTheConfiguredFirstDayAndFourDayFirstWeekRule() {
        assertEquals(
            1,
            LocalDate.of(2025, 12, 29).calendarWeekNumber(DayOfWeek.MONDAY),
        )
        assertEquals(
            53,
            LocalDate.of(2025, 12, 28).calendarWeekNumber(DayOfWeek.SUNDAY),
        )
        assertEquals(
            1,
            LocalDate.of(2026, 1, 4).calendarWeekNumber(DayOfWeek.SUNDAY),
        )
    }

    @Test
    fun labelMovesWithItsFirstDayWhileThatDayIsOnScreen() {
        val placements = timelineCalendarWeekPlacements(
            visibleDays = listOf(
                VisibleTimelineDay(LocalDate.of(2026, 8, 24), leftPx = 30f),
                VisibleTimelineDay(LocalDate.of(2026, 8, 25), leftPx = 130f),
            ),
            dayWidthPx = 100f,
            viewportWidthPx = 300f,
            firstDayOfWeek = DayOfWeek.MONDAY,
        )

        assertEquals(1, placements.size)
        assertEquals(LocalDate.of(2026, 8, 24), placements.single().weekStart)
        assertEquals(30f, placements.single().leftPx)
        assertFalse(placements.single().pinned)
    }

    @Test
    fun labelPinsAtTheLeftWhileLaterDaysOfItsWeekRemainVisible() {
        val placements = timelineCalendarWeekPlacements(
            visibleDays = listOf(
                VisibleTimelineDay(LocalDate.of(2026, 8, 24), leftPx = -35f),
                VisibleTimelineDay(LocalDate.of(2026, 8, 25), leftPx = 65f),
                VisibleTimelineDay(LocalDate.of(2026, 8, 26), leftPx = 165f),
            ),
            dayWidthPx = 100f,
            viewportWidthPx = 300f,
            firstDayOfWeek = DayOfWeek.MONDAY,
        )

        assertEquals(0f, placements.first().leftPx)
        assertTrue(placements.first().pinned)
        assertEquals(LocalDate.of(2026, 8, 24), placements.first().weekStart)
    }

    @Test
    fun nextWeekAppearsAtItsConfiguredFirstDayBeforeBecomingPinned() {
        val placements = timelineCalendarWeekPlacements(
            visibleDays = listOf(
                VisibleTimelineDay(LocalDate.of(2026, 8, 23), leftPx = -80f),
                VisibleTimelineDay(LocalDate.of(2026, 8, 24), leftPx = 20f),
                VisibleTimelineDay(LocalDate.of(2026, 8, 25), leftPx = 120f),
            ),
            dayWidthPx = 100f,
            viewportWidthPx = 300f,
            firstDayOfWeek = DayOfWeek.MONDAY,
        )

        assertEquals(
            listOf(LocalDate.of(2026, 8, 17), LocalDate.of(2026, 8, 24)),
            placements.map { it.weekStart },
        )
        assertEquals(listOf(-80f, 20f), placements.map { it.leftPx })
        assertEquals(listOf(true, false), placements.map { it.pinned })
    }

    @Test
    fun incomingWeekPushesThePinnedWeekOutWithoutOverlappingItsLabelSlot() {
        val placements = timelineCalendarWeekPlacements(
            visibleDays = listOf(
                VisibleTimelineDay(LocalDate.of(2026, 8, 23), leftPx = -40f),
                VisibleTimelineDay(LocalDate.of(2026, 8, 24), leftPx = 60f),
                VisibleTimelineDay(LocalDate.of(2026, 8, 25), leftPx = 160f),
            ),
            dayWidthPx = 100f,
            viewportWidthPx = 300f,
            firstDayOfWeek = DayOfWeek.MONDAY,
        )

        val outgoing = placements[0]
        val incoming = placements[1]
        assertEquals(-40f, outgoing.leftPx)
        assertEquals(60f, incoming.leftPx)
        assertEquals(incoming.leftPx, outgoing.leftPx + 100f)
        assertEquals(0.6f, outgoing.visualAlpha, 0.001f)
        assertEquals(1f, incoming.visualAlpha, 0.001f)
    }

    @Test
    fun nextWeekTakesThePinnedPositionOnceThePreviousWeekLeaves() {
        val placements = timelineCalendarWeekPlacements(
            visibleDays = listOf(
                VisibleTimelineDay(LocalDate.of(2026, 8, 23), leftPx = -101f),
                VisibleTimelineDay(LocalDate.of(2026, 8, 24), leftPx = 0f),
                VisibleTimelineDay(LocalDate.of(2026, 8, 25), leftPx = 100f),
            ),
            dayWidthPx = 100f,
            viewportWidthPx = 300f,
            firstDayOfWeek = DayOfWeek.MONDAY,
        )

        assertEquals(1, placements.size)
        assertEquals(LocalDate.of(2026, 8, 24), placements.single().weekStart)
        assertEquals(0f, placements.single().leftPx)
    }

    @Test
    fun sundaySettingMovesTheWeekBoundaryToSunday() {
        val placements = timelineCalendarWeekPlacements(
            visibleDays = listOf(
                VisibleTimelineDay(LocalDate.of(2026, 8, 22), leftPx = -25f),
                VisibleTimelineDay(LocalDate.of(2026, 8, 23), leftPx = 75f),
                VisibleTimelineDay(LocalDate.of(2026, 8, 24), leftPx = 175f),
            ),
            dayWidthPx = 100f,
            viewportWidthPx = 300f,
            firstDayOfWeek = DayOfWeek.SUNDAY,
        )

        assertEquals(
            listOf(LocalDate.of(2026, 8, 16), LocalDate.of(2026, 8, 23)),
            placements.map { it.weekStart },
        )
        assertEquals(listOf(-25f, 75f), placements.map { it.leftPx })
    }
}
