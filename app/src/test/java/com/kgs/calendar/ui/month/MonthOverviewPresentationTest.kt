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

    @Test
    fun regularRowsKeepTheirHeightAndCircle() {
        assertEquals(44.dp, MonthOverviewRowHeight)
        assertEquals(34.dp, monthOverviewDayCircleSize(MonthOverviewRowHeight))
    }

    @Test
    fun tabletopRowsShareTheTopHalf() {
        // 224.8 dp for six rows with 2 dp gaps.
        assertEquals(35.8f, monthOverviewFitRowHeight(224.8.dp, rows = 6).value, 0.01f)
        assertEquals(25.8f, monthOverviewDayCircleSize(monthOverviewFitRowHeight(224.8.dp, rows = 6)).value, 0.01f)
    }

    @Test
    fun tabletopRowsNeverGrowBeyondTheRegularHeightOrBelowTheMinimum() {
        assertEquals(44.dp, monthOverviewFitRowHeight(600.dp, rows = 5))
        assertEquals(MonthOverviewMinRowHeight, monthOverviewFitRowHeight(100.dp, rows = 6))
    }

    @Test
    fun aWideTabletopTopHalfPutsTheMonthStripBesideTheGrid() {
        // A book-style foldable turned sideways: 841 x 255 dp above the hinge.
        assertEquals(MonthOverviewPresentation.SideBySide, tabletopMonthOverviewPresentation(841f, 255f))
        // A flip phone: 412 x 360 dp above the hinge.
        assertEquals(MonthOverviewPresentation.Stacked, tabletopMonthOverviewPresentation(412f, 360f))
    }
}
