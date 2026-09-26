package com.kgs.calendar.ui.month

import androidx.compose.ui.unit.dp
import com.kgs.calendar.domain.model.CalendarWindowLayout
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
            monthOverviewPresentation(sideBySide = false),
        )
        assertEquals(
            MonthOverviewPresentation.Stacked,
            monthOverviewPresentation(CalendarWindowLayout.PhonePortrait),
        )
    }

    @Test
    fun landscapePlacesAVerticalMonthStripBesideTheGrid() {
        assertEquals(
            MonthOverviewPresentation.SideBySide,
            monthOverviewPresentation(sideBySide = true),
        )
        assertEquals(
            MonthStripAxis.Vertical,
            monthOverviewPresentation(sideBySide = true).monthStripAxis,
        )
        assertEquals(
            MonthOverviewPresentation.SideBySide,
            monthOverviewPresentation(CalendarWindowLayout.PhoneLandscape),
        )
    }

    @Test
    fun largeScreensStackTheStripEvenWhenRotated() {
        val tabletLandscape = CalendarWindowLayout(isLandscape = true, widthDp = 1280, heightDp = 800)
        val unfoldedFoldable = CalendarWindowLayout(isLandscape = true, widthDp = 841, heightDp = 701)
        assertEquals(MonthOverviewPresentation.Stacked, monthOverviewPresentation(tabletLandscape))
        assertEquals(MonthOverviewPresentation.Stacked, monthOverviewPresentation(unfoldedFoldable))
    }
}
