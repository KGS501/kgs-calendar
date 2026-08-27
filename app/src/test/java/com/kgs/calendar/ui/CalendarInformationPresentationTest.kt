package com.kgs.calendar.ui

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import androidx.compose.ui.unit.dp
import com.kgs.calendar.domain.model.CalendarViewMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarInformationPresentationTest {
    @Test
    fun calendarWeeksMoveTheWeekBandUpWithoutMovingTheToolbarMonthDown() {
        assertEquals(0.dp, timelineToolbarMonthOffset(showCalendarWeeks = true).y)
        assertEquals((-8).dp, timelineToolbarMonthOffset(showCalendarWeeks = true).x)
    }

    @Test
    fun agendaChronologyLeavesSpaceBetweenReplacingDayStacksAndFitsFullLabels() {
        assertEquals(6.dp, agendaChronologyPresentation.datePushGap)
        assertEquals(52.dp, resolvedAgendaDateStackHeight(fontScale = 1.2f))
        assertEquals(58.dp, resolvedAgendaDatePushDistance(fontScale = 1.2f))
        assertTrue(agendaChronologyPresentation.monthLaneWidth >= 120.dp)
        assertTrue(agendaChronologyPresentation.yearLaneWidth >= 60.dp)
    }

    @Test
    fun explicitAgendaJumpsChooseDirectPositioning() {
        assertEquals(
            AgendaJumpBehavior.Direct,
            agendaJumpBehavior(
                initialAutoScrollHandled = true,
                explicitAgendaRequest = true,
            ),
        )
    }

    @Test
    fun agendaResultUpdatesDoNotReplaceAnAlreadyHandledExplicitJump() {
        assertEquals(
            AgendaJumpBehavior.None,
            agendaJumpBehavior(
                initialAutoScrollHandled = true,
                explicitAgendaRequest = false,
            ),
        )
    }
    @Test
    fun agendaViewportOnlyReadsSignalsNearTheVisibleWindow() {
        val firstDate = LocalDate.of(2020, 1, 1)
        val backing = List(5_500) { index ->
            AgendaHeaderSignal(
                lazyIndex = index,
                key = "day-$index",
                date = firstDate.plusDays(index.toLong()),
            )
        }
        var reads = 0
        val countedSignals = object : AbstractList<AgendaHeaderSignal>() {
            override val size: Int = backing.size

            override fun get(index: Int): AgendaHeaderSignal {
                reads++
                return backing[index]
            }
        }
        val firstVisibleIndex = 2_750

        agendaStickyHeaderViewport(
            signals = AgendaHeaderSignals(
                days = countedSignals,
                months = emptyList(),
                years = emptyList(),
                weeks = emptyList(),
            ),
            visibleItems = (firstVisibleIndex until firstVisibleIndex + 12).map { index ->
                AgendaVisibleHeaderItem(
                    key = "day-$index",
                    offset = (index - firstVisibleIndex) * 60,
                    lazyIndex = index,
                )
            },
            firstVisibleItemIndex = firstVisibleIndex,
            informationContentTopInset = 5,
            dayElementHeight = 40,
            informationElementHeight = 22,
        )

        assertTrue("Agenda scroll frame read $reads chronology signals", reads < 100)
    }

    @Test
    fun calendarChromeUsesBorderlessYearItemsAndAnOpaqueAgendaHeader() {
        assertFalse(calendarInformationChrome.yearStripItemsHaveBorders)
        assertEquals(1f, calendarInformationChrome.agendaStickyHeaderAlpha)
    }

    @Test
    fun agendaChronologyValuesHaveExactlyOneRenderer() {
        assertFalse(agendaChronologyRenderingPolicy.renderValuesInsideListRows)
        assertEquals(true, agendaChronologyRenderingPolicy.renderValuesInChronologyLayer)
    }

    @Test
    fun selectingAMonthPlansAnExplicitAgendaJumpToItsFirstDay() {
        val month = YearMonth.of(2027, 3)

        assertEquals(
            MonthOverviewSelectionPlan(
                selectedDate = LocalDate.of(2027, 3, 1),
                agendaScrollTargetDate = LocalDate.of(2027, 3, 1),
            ),
            monthOverviewSelectionPlan(month, CalendarViewMode.Agenda),
        )
        assertEquals(
            MonthOverviewSelectionPlan(
                selectedDate = LocalDate.of(2027, 3, 1),
                agendaScrollTargetDate = null,
            ),
            monthOverviewSelectionPlan(month, CalendarViewMode.ThreeDay),
        )
    }

    @Test
    fun agendaOverlayTranslatesLazyOffsetsIntoViewportCoordinates() {
        assertEquals(
            68,
            agendaOverlayNaturalOffset(
                lazyItemOffset = 12,
                viewportStartOffset = -56,
                contentTopInset = 0,
            ),
        )
    }

    @Test
    fun agendaViewportKeepsNaturalValuesAlignedWithTheirVisibleListItems() {
        val date = LocalDate.of(2026, 2, 2)
        val viewport = agendaStickyHeaderViewport(
            signals = AgendaHeaderSignals(
                days = listOf(AgendaHeaderSignal(3, "day", date)),
                months = listOf(AgendaHeaderSignal(2, "boundary", date)),
                years = emptyList(),
                weeks = emptyList(),
            ),
            visibleItems = listOf(
                AgendaVisibleHeaderItem("boundary", -20, 2),
                AgendaVisibleHeaderItem("day", 12, 3),
            ),
            firstVisibleItemIndex = 2,
            viewportStartOffset = -56,
            informationContentTopInset = 5,
            dayElementHeight = 40,
            informationElementHeight = 22,
        )

        assertEquals(
            listOf(AgendaStickyFieldElement("day", date, 68)),
            viewport.day.elements,
        )
        assertEquals(
            listOf(AgendaStickyFieldElement("boundary", date, 41)),
            viewport.month.elements,
        )
    }

    @Test
    fun monthRowsUseTheConfiguredFirstDayForTheirWeekNumbers() {
        val mondayRows = monthCalendarWeekRows(
            month = YearMonth.of(2026, 8),
            firstDayOfWeek = DayOfWeek.MONDAY,
        )
        val sundayRows = monthCalendarWeekRows(
            month = YearMonth.of(2026, 8),
            firstDayOfWeek = DayOfWeek.SUNDAY,
        )

        assertEquals(LocalDate.of(2026, 7, 27), mondayRows.first().weekStart)
        assertEquals(31, mondayRows.first().weekNumber)
        assertEquals(LocalDate.of(2026, 7, 26), sundayRows.first().weekStart)
        assertEquals(30, sundayRows.first().weekNumber)
    }

    @Test
    fun agendaGroupsResultsByDaySoTheTopDateCanAdvanceWithoutAMonthBoundary() {
        val firstDay = LocalDate.of(2026, 8, 24)
        val nextDay = firstDay.plusDays(1)

        assertEquals(firstDay.toString(), agendaDayGroupKey(firstDay))
        assertEquals(nextDay.toString(), agendaDayGroupKey(nextDay))
        assertNotEquals(agendaDayGroupKey(firstDay), agendaDayGroupKey(nextDay))
    }

    @Test
    fun agendaUsesANarrowGutterRegardlessOfWhetherCalendarWeeksAreShown() {
        assertEquals(38.dp, agendaChronologyPresentation.eventGutterWidth)
        assertEquals(28.dp, agendaChronologyPresentation.topHeaderHeight)
        assertEquals(44.dp, agendaChronologyPresentation.dateStackHeight)
        assertEquals(18f, agendaChronologyPresentation.informationFontSizeSp)
        assertEquals(18f, agendaChronologyPresentation.weekFontSizeSp)
    }

    @Test
    fun agendaBoundaryOnlyCarriesValuesThatActuallyChangeOnThatDay() {
        val ordinaryDay = agendaBoundaryChanges(
            previousDate = LocalDate.of(2026, 1, 1),
            date = LocalDate.of(2026, 1, 2),
            firstDayOfWeek = DayOfWeek.MONDAY,
        )
        val weekBoundary = agendaBoundaryChanges(
            previousDate = LocalDate.of(2026, 1, 4),
            date = LocalDate.of(2026, 1, 5),
            firstDayOfWeek = DayOfWeek.MONDAY,
        )
        val monthBoundary = agendaBoundaryChanges(
            previousDate = LocalDate.of(2026, 1, 31),
            date = LocalDate.of(2026, 2, 1),
            firstDayOfWeek = DayOfWeek.MONDAY,
        )
        val yearBoundary = agendaBoundaryChanges(
            previousDate = LocalDate.of(2025, 12, 31),
            date = LocalDate.of(2026, 1, 1),
            firstDayOfWeek = DayOfWeek.MONDAY,
        )

        assertEquals(AgendaBoundaryChanges(false, false, false), ordinaryDay)
        assertEquals(AgendaBoundaryChanges(false, false, true), weekBoundary)
        assertEquals(AgendaBoundaryChanges(true, false, false), monthBoundary)
        assertEquals(AgendaBoundaryChanges(true, true, false), yearBoundary)
    }

    @Test
    fun agendaHeaderWaitsUntilTheIncomingValueReachesTheHeaderBeforeReplacingIt() {
        val january = LocalDate.of(2026, 1, 31)
        val february = LocalDate.of(2026, 2, 1)
        val viewport = agendaStickyHeaderViewport(
            signals = AgendaHeaderSignals(
                days = listOf(AgendaHeaderSignal(3, "january-event", january)),
                months = listOf(
                    AgendaHeaderSignal(0, "january-boundary", january),
                    AgendaHeaderSignal(4, "february-boundary", february),
                ),
                years = listOf(AgendaHeaderSignal(0, "january-boundary", january)),
                weeks = emptyList(),
            ),
            visibleItems = listOf(
                AgendaVisibleHeaderItem(key = "january-event", offset = -100, lazyIndex = 3),
                AgendaVisibleHeaderItem(key = "february-boundary", offset = 70, lazyIndex = 4),
            ),
            firstVisibleItemIndex = 3,
            informationContentTopInset = 0,
            dayElementHeight = 40,
            informationElementHeight = 22,
        )

        assertEquals(
            listOf(
                AgendaStickyFieldElement("january-boundary", january, 0),
                AgendaStickyFieldElement("february-boundary", february, 70),
            ),
            viewport.month.elements,
        )
    }

    @Test
    fun agendaMonthCanBeginPushingAtItsBoundaryWithoutAdvancingTheDayEarly() {
        val january31 = LocalDate.of(2026, 1, 31)
        val february1 = LocalDate.of(2026, 2, 1)
        val boundaryKey = "february-boundary"
        val dayKey = "february-day"
        val viewport = agendaStickyHeaderViewport(
            signals = AgendaHeaderSignals(
                days = listOf(
                    AgendaHeaderSignal(2, "january-day", january31),
                    AgendaHeaderSignal(4, dayKey, february1),
                ),
                months = listOf(
                    AgendaHeaderSignal(0, "january-boundary", january31),
                    AgendaHeaderSignal(3, boundaryKey, february1),
                ),
                years = listOf(AgendaHeaderSignal(0, "january-boundary", january31)),
                weeks = emptyList(),
            ),
            visibleItems = listOf(
                AgendaVisibleHeaderItem("january-day", -30, 2),
                AgendaVisibleHeaderItem(boundaryKey, 12, 3),
                AgendaVisibleHeaderItem(dayKey, 58, 4),
            ),
            firstVisibleItemIndex = 2,
            informationContentTopInset = 0,
            dayElementHeight = 40,
            informationElementHeight = 22,
        )

        assertEquals(
            listOf(
                AgendaStickyFieldElement("january-day", january31, 0),
                AgendaStickyFieldElement(dayKey, february1, 58),
            ),
            viewport.day.elements,
        )
        assertEquals(
            listOf(
                AgendaStickyFieldElement("january-boundary", january31, -10),
                AgendaStickyFieldElement(boundaryKey, february1, 12),
            ),
            viewport.month.elements,
        )
        assertEquals(
            listOf(AgendaStickyFieldElement("january-boundary", january31, 0)),
            viewport.year.elements,
        )
        assertEquals(emptyList<AgendaStickyFieldElement>(), viewport.week.elements)
    }

    @Test
    fun agendaIncomingValuePushesThePinnedValueByTheExactScrollDistanceAfterContact() {
        val placement = agendaStickyPushPlacement(
            incomingNaturalOffset = 12,
            elementHeight = 20,
        )

        assertEquals(12, placement.incomingOffset)
        assertEquals(-8, placement.outgoingOffset)
    }

    @Test
    fun paddedAgendaDateStackKeepsItsOuterGapWhileBeingPushed() {
        val stackHeight = resolvedAgendaDateStackHeight(fontScale = 1.2f).value.toInt()
        val pushDistance = resolvedAgendaDatePushDistance(fontScale = 1.2f).value.toInt()
        val incomingOffset = 30
        val placement = agendaStickyPushPlacement(
            incomingNaturalOffset = incomingOffset,
            elementHeight = pushDistance,
        )

        val visibleGap = incomingOffset - (placement.outgoingOffset + stackHeight)
        assertEquals(agendaChronologyPresentation.datePushGap.value.toInt(), visibleGap)
    }

    @Test
    fun agendaStickyPushIsContinuousAtContactAndReplacementInEitherScrollDirection() {
        assertEquals(
            AgendaStickyPushPlacement(outgoingOffset = 0, incomingOffset = 21),
            agendaStickyPushPlacement(incomingNaturalOffset = 21, elementHeight = 20),
        )
        assertEquals(
            AgendaStickyPushPlacement(outgoingOffset = 0, incomingOffset = 20),
            agendaStickyPushPlacement(incomingNaturalOffset = 20, elementHeight = 20),
        )
        assertEquals(
            AgendaStickyPushPlacement(outgoingOffset = -19, incomingOffset = 1),
            agendaStickyPushPlacement(incomingNaturalOffset = 1, elementHeight = 20),
        )
        assertEquals(
            AgendaStickyPushPlacement(outgoingOffset = -20, incomingOffset = 0),
            agendaStickyPushPlacement(incomingNaturalOffset = 0, elementHeight = 20),
        )
    }
}
