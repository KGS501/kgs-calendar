package com.kgs.calendar.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineHeaderPresentationTest {
    @Test
    fun portraitAlwaysUsesTheNormalStackedHeaderAndKeepsAllDayVisible() {
        val presentation = timelineHeaderPresentation(
            isLandscape = false,
            landscapeCompactRequested = true,
        )

        assertEquals(TimelineDayHeaderMode.Portrait, presentation.mode)
        assertEquals(56.dp, presentation.height)
        assertEquals(56.dp, presentation.multiDayControlsHeight)
        assertEquals(0.dp, presentation.multiDayControlsTopOffset)
        assertTrue(presentation.showAllDaySection)
    }

    @Test
    fun normalLandscapePlacesDayAndDateInline() {
        val presentation = timelineHeaderPresentation(
            isLandscape = true,
            landscapeCompactRequested = false,
        )

        assertEquals(TimelineDayHeaderMode.Landscape, presentation.mode)
        assertEquals(44.dp, presentation.height)
        assertEquals(56.dp, presentation.multiDayControlsHeight)
        assertEquals((-4).dp, presentation.multiDayControlsTopOffset)
        assertTrue(presentation.showAllDaySection)
    }

    @Test
    fun compactLandscapeKeepsTheInlinePairButHidesAllDay() {
        val presentation = timelineHeaderPresentation(
            isLandscape = true,
            landscapeCompactRequested = true,
        )

        assertEquals(TimelineDayHeaderMode.LandscapeCompact, presentation.mode)
        assertEquals(30.dp, presentation.height)
        assertEquals(56.dp, presentation.multiDayControlsHeight)
        assertEquals((-4).dp, presentation.multiDayControlsTopOffset)
        assertFalse(presentation.showAllDaySection)
    }

    @Test
    fun calendarWeeksAddASeparateBandToEveryTimelineHeader() {
        val portrait = timelineHeaderPresentation(
            isLandscape = false,
            landscapeCompactRequested = false,
            showCalendarWeeks = true,
        )
        val landscape = timelineHeaderPresentation(
            isLandscape = true,
            landscapeCompactRequested = false,
            showCalendarWeeks = true,
        )

        assertEquals(28.dp, portrait.weekLabelHeight)
        assertEquals((-10).dp, portrait.weekLabelTopOffset)
        assertTrue(portrait.allowTopOverflow)
        assertEquals(84.dp, portrait.height)
        assertEquals(26.dp, landscape.weekLabelHeight)
        assertEquals((-10).dp, landscape.weekLabelTopOffset)
        assertTrue(landscape.allowTopOverflow)
        assertEquals(70.dp, landscape.height)
        assertEquals(16f, portrait.weekLabelFontSize)
        assertEquals(16f, landscape.weekLabelFontSize)
        assertEquals(28.dp, portrait.multiDayControlsTopOffset)
        assertEquals(14.dp, landscape.multiDayControlsTopOffset)
    }

    @Test
    fun compactLandscapeShrinksTheCalendarWeekBandButNotTheSidebarControls() {
        val presentation = timelineHeaderPresentation(
            isLandscape = true,
            landscapeCompactRequested = true,
            showCalendarWeeks = true,
        )

        assertEquals(23.dp, presentation.weekLabelHeight)
        assertEquals((-10).dp, presentation.weekLabelTopOffset)
        assertTrue(presentation.allowTopOverflow)
        assertEquals(53.dp, presentation.height)
        assertEquals(15f, presentation.weekLabelFontSize)
        assertEquals(56.dp, presentation.multiDayControlsHeight)
        assertEquals((-6).dp, presentation.multiDayControlsTopOffset)
        assertFalse(presentation.showAllDaySection)
    }
}
