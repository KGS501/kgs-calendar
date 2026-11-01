package com.kgs.calendar.ui.agenda

import com.kgs.calendar.domain.model.CalendarRange
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureTimeMillis

class AgendaTimelinePlannerTest {
    private val today = LocalDate.of(2026, 8, 23)

    @Test
    fun sparseDatesResolveToTheirOwnRowsWithoutRawIndexCompensation() {
        val december = LocalDate.of(2025, 12, 1)
        val january = LocalDate.of(2026, 1, 1)
        val june = LocalDate.of(2026, 6, 12)
        val plan = AgendaTimelinePlanner.plan(
            entries = listOf(entry(december), entry(january), entry(june), entry(today)),
            today = today,
            firstDayOfWeek = DayOfWeek.MONDAY,
            showCalendarWeeks = true,
        )

        val januaryAnchor = requireNotNull(plan.anchorsByDate[january])
        val januaryRow = plan.rows[januaryAnchor.rowIndex]

        assertEquals("item-$january", januaryAnchor.rowKey)
        assertTrue(januaryRow is AgendaTimelineRow.Entry)
        assertEquals(january, (januaryRow as AgendaTimelineRow.Entry).entry.date)
    }

    @Test
    fun requestedEmptyDateGetsAnExactChronologicalAnchor() {
        val december = LocalDate.of(2025, 12, 1)
        val january = LocalDate.of(2026, 1, 1)
        val may = LocalDate.of(2026, 5, 1)
        val plan = AgendaTimelinePlanner.plan(
            entries = listOf(entry(december), entry(may)),
            today = today,
            firstDayOfWeek = DayOfWeek.MONDAY,
            showCalendarWeeks = true,
            requiredAnchorDate = january,
        )

        val anchor = requireNotNull(plan.anchorsByDate[january])
        assertTrue(plan.rows[anchor.rowIndex] is AgendaTimelineRow.DateAnchor)
        assertTrue(plan.anchorsByDate.getValue(december).rowIndex < anchor.rowIndex)
        assertTrue(anchor.rowIndex < plan.anchorsByDate.getValue(may).rowIndex)
        assertTrue(plan.headerSignals.days.any { it.key == anchor.rowKey && it.date == january })
    }

    @Test
    fun navigationWaitsForTheSnapshotBuiltForItsTargetAndRunsOnlyOnce() {
        val january = LocalDate.of(2026, 1, 1)
        val request = AgendaNavigationRequest(id = 7L, date = january)
        val oldPlan = AgendaTimelinePlanner.plan(
            entries = listOf(entry(today)),
            today = today,
            firstDayOfWeek = DayOfWeek.MONDAY,
            showCalendarWeeks = false,
            requiredAnchorDate = today,
        )
        val newPlan = AgendaTimelinePlanner.plan(
            entries = emptyList<AgendaTimelineEntry<String>>(),
            today = today,
            firstDayOfWeek = DayOfWeek.MONDAY,
            showCalendarWeeks = false,
            requiredAnchorDate = january,
        )

        assertNull(
            resolveAgendaNavigation(
                request = request,
                lastHandledRequestId = 6L,
                snapshot = AgendaNavigationSnapshot(
                    loadedRange = CalendarRange(today.minusMonths(2), today.plusMonths(2)),
                    requiredAnchorDate = today,
                    plan = oldPlan,
                ),
            ),
        )
        val placement = resolveAgendaNavigation(
            request = request,
            lastHandledRequestId = 6L,
            snapshot = AgendaNavigationSnapshot(
                loadedRange = CalendarRange(january.minusMonths(2), january.plusMonths(2)),
                requiredAnchorDate = january,
                plan = newPlan,
            ),
        )
        assertNotNull(placement)
        assertEquals(january, placement?.date)
        assertNull(
            resolveAgendaNavigation(
                request = request,
                lastHandledRequestId = request.id,
                snapshot = AgendaNavigationSnapshot(
                    loadedRange = CalendarRange(january.minusMonths(2), january.plusMonths(2)),
                    requiredAnchorDate = january,
                    plan = newPlan,
                ),
            ),
        )
    }

    @Test
    fun laterDateNavigationKeepsTheDirectionNeededForAVisualScrollCue() {
        assertEquals(
            AgendaNavigationMotion.Instant,
            agendaNavigationMotion(
                lastHandledRequestId = Long.MIN_VALUE,
                currentVisibleDate = LocalDate.of(2026, 1, 1),
                targetDate = LocalDate.of(2026, 8, 1),
            ),
        )
        assertEquals(
            AgendaNavigationMotion.AnimateForward,
            agendaNavigationMotion(
                lastHandledRequestId = 1L,
                currentVisibleDate = LocalDate.of(2026, 1, 1),
                targetDate = LocalDate.of(2026, 8, 1),
            ),
        )
        assertEquals(
            AgendaNavigationMotion.AnimateBackward,
            agendaNavigationMotion(
                lastHandledRequestId = 2L,
                currentVisibleDate = LocalDate.of(2026, 8, 1),
                targetDate = LocalDate.of(2026, 1, 1),
            ),
        )
    }

    @Test
    fun longDistanceNavigationMovesMoreThanHalfAViewportWithContinuousDepartureAndArrival() {
        val travel = agendaNavigationTravel(
            viewportHeightPx = 1_000,
            minimumDistancePx = 240,
        )

        assertEquals(650, travel.distancePx)
        assertEquals(
            "Equal phase lengths preserve velocity across the hidden midpoint swap",
            travel.departureDurationMillis,
            travel.arrivalDurationMillis,
        )
        assertEquals(190, travel.departureDurationMillis)
        assertEquals(190, travel.arrivalDurationMillis)
    }

    @Test
    fun multiYearPlannerFixtureStaysWithinAnInteractiveBudget() {
        val first = LocalDate.of(2020, 1, 1)
        val entries = List(6_000) { index -> entry(first.plusDays(index.toLong())) }
        lateinit var plan: AgendaTimelinePlan<String>

        val elapsedMillis = measureTimeMillis {
            plan = AgendaTimelinePlanner.plan(
                entries = entries,
                today = today,
                firstDayOfWeek = DayOfWeek.MONDAY,
                showCalendarWeeks = true,
                requiredAnchorDate = today,
            )
        }

        assertEquals(6_000, plan.anchorsByDate.size)
        assertTrue("Planner took ${elapsedMillis}ms", elapsedMillis < 1_000L)
    }

    private fun entry(date: LocalDate): AgendaTimelineEntry<String> = AgendaTimelineEntry(
        key = "item-$date",
        date = date,
        sortMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        value = date.toString(),
    )
}
