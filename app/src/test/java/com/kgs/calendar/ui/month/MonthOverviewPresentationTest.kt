package com.kgs.calendar.ui.month

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class MonthOverviewPresentationTest {
    @Test
    fun selectedDayHighlightDoesNotUseAnOutline() {
        assertEquals(0.dp, monthOverviewDayChrome.selectedDayBorderWidth)
    }

    @Test
    fun portraitStacksTheMonthStripBelowTheGrid() {
        assertEquals(
            MonthOverviewPresentation.Stacked,
            monthOverviewPresentation(isLandscape = false),
        )
    }

    @Test
    fun landscapePlacesAVerticalMonthStripBesideTheGrid() {
        assertEquals(
            MonthOverviewPresentation.SideBySide,
            monthOverviewPresentation(isLandscape = true),
        )
        assertEquals(
            MonthStripAxis.Vertical,
            monthOverviewPresentation(isLandscape = true).monthStripAxis,
        )
    }
}
