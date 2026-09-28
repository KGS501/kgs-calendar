package com.kgs.calendar.ui.layout

import androidx.compose.ui.unit.dp
import com.kgs.calendar.domain.model.CalendarViewMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TabletopLayoutTest {
    @Test
    fun monthOverviewFillsTheTopHalfBelowTheToolbar() {
        // 7.6" foldable turned sideways: hinge at 336.8 dp, 24 dp status bar + 58 dp toolbar.
        val layout = tabletopShellLayout(hingeTop = 336.8.dp, hingeBottom = 336.8.dp, toolbarBottom = 82.dp)

        assertEquals(254.8f, layout.monthOverviewHeight.value, 0.01f)
        assertEquals(0f, layout.hingeHeight.value, 0.01f)
    }

    @Test
    fun anOccludingHingeIsSkipped() {
        val layout = tabletopShellLayout(hingeTop = 320.dp, hingeBottom = 340.dp, toolbarBottom = 82.dp)

        assertEquals(238f, layout.monthOverviewHeight.value, 0.01f)
        assertEquals(20f, layout.hingeHeight.value, 0.01f)
    }

    @Test
    fun aHingeAboveTheToolbarLeavesNoOverview() {
        val layout = tabletopShellLayout(hingeTop = 60.dp, hingeBottom = 60.dp, toolbarBottom = 82.dp)

        assertEquals(0f, layout.monthOverviewHeight.value, 0.01f)
    }

    @Test
    fun dayMultipleDaysAndAgendaSplitAtTheHinge() {
        assertTrue(CalendarViewMode.Day.usesTabletopSplit())
        assertTrue(CalendarViewMode.ThreeDay.usesTabletopSplit())
        assertTrue(CalendarViewMode.Agenda.usesTabletopSplit())
        assertFalse(CalendarViewMode.Month.usesTabletopSplit())
        assertFalse(CalendarViewMode.Tasks.usesTabletopSplit())
    }

    @Test
    fun aKeyboardInsideTheBottomHalfLeavesTheTopPanelAlone() {
        // Window 1768 px, panel ends at the hinge at 884 px, keyboard 700 px tall.
        assertEquals(0f, tabletopPanelImeOverlapPx(imeHeightPx = 700f, windowHeightPx = 1768f, panelBottomPx = 884f))
        assertEquals(0f, tabletopPanelImeOverlapPx(imeHeightPx = 884f, windowHeightPx = 1768f, panelBottomPx = 884f))
        assertEquals(0f, tabletopPanelImeOverlapPx(imeHeightPx = 0f, windowHeightPx = 1768f, panelBottomPx = 884f))
    }

    @Test
    fun aKeyboardTallerThanTheBottomHalfPadsOnlyItsOverlap() {
        assertEquals(116f, tabletopPanelImeOverlapPx(imeHeightPx = 1000f, windowHeightPx = 1768f, panelBottomPx = 884f))
    }
}
