package com.kgs.calendar.ui.layout

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kgs.calendar.domain.model.CalendarViewMode

/**
 * The calendar shell in the tabletop posture, in dp from the shell's top: the toolbar, then the month
 * overview filling the rest of the top half, the hinge band (never used for content), and the selected
 * view below it, whose date header therefore starts right at the hinge's bottom edge.
 */
internal data class TabletopShellLayout(
    val monthOverviewHeight: Dp,
    val hingeHeight: Dp,
)

internal fun tabletopShellLayout(hingeTop: Dp, hingeBottom: Dp, toolbarBottom: Dp): TabletopShellLayout =
    TabletopShellLayout(
        monthOverviewHeight = (hingeTop - toolbarBottom).coerceAtLeast(0.dp),
        hingeHeight = (hingeBottom - hingeTop).coerceAtLeast(0.dp),
    )

/**
 * Day, Multiple days and Agenda sit below the hinge with the month overview above it. The Month view is a
 * month overview itself and keeps its regular full-screen grid, as does the task list.
 */
internal fun CalendarViewMode.usesTabletopSplit(): Boolean = when (this) {
    CalendarViewMode.Day,
    CalendarViewMode.ThreeDay,
    CalendarViewMode.Agenda,
    -> true
    CalendarViewMode.Month,
    CalendarViewMode.Tasks,
    -> false
}

/**
 * How far the keyboard reaches into a top panel that ends at [panelBottomPx]. The keyboard normally fits
 * into the bottom half, so the panel keeps its full height; only a keyboard taller than the bottom half
 * pushes the panel's content up, by the part that overlaps it.
 */
internal fun tabletopPanelImeOverlapPx(imeHeightPx: Float, windowHeightPx: Float, panelBottomPx: Float): Float =
    (imeHeightPx - (windowHeightPx - panelBottomPx)).coerceAtLeast(0f)
