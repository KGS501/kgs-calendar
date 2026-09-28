package com.kgs.calendar.ui.month

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kgs.calendar.domain.model.CalendarWindowLayout

internal data class MonthOverviewDayChrome(
    val selectedDayBorderWidth: Dp,
)

internal val monthOverviewDayChrome = MonthOverviewDayChrome(
    selectedDayBorderWidth = 0.dp,
)

internal enum class MonthStripAxis {
    Horizontal,
    Vertical,
}

internal enum class MonthOverviewPresentation(
    val monthStripAxis: MonthStripAxis,
) {
    Stacked(MonthStripAxis.Horizontal),
    SideBySide(MonthStripAxis.Vertical),
}

internal fun monthOverviewPresentation(sideBySide: Boolean): MonthOverviewPresentation =
    if (sideBySide) MonthOverviewPresentation.SideBySide else MonthOverviewPresentation.Stacked

/** Side by side only in low windows (rotated phones); large screens stack the strip below the grid. */
internal fun monthOverviewPresentation(windowLayout: CalendarWindowLayout): MonthOverviewPresentation =
    monthOverviewPresentation(sideBySide = windowLayout.usesSideBySideMonthOverview)

/** A week row of the month overview: a 34 dp day circle over its 8 dp event dots, with breathing room. */
internal val MonthOverviewRowHeight = 44.dp

/** Rows never shrink below this, so a day still takes a comfortable tap. */
internal val MonthOverviewMinRowHeight = 30.dp

private val MonthOverviewRowGap = 2.dp
private val MonthOverviewDayCircleMax = 34.dp
private val MonthOverviewDayCircleMin = 22.dp

/**
 * The row height when the grid shares [available] height between its [rows] (the tabletop posture, where
 * the overview is exactly the top half). Never taller than the regular [MonthOverviewRowHeight].
 */
internal fun monthOverviewFitRowHeight(available: Dp, rows: Int): Dp {
    if (rows <= 0) return MonthOverviewRowHeight
    val shared = (available - MonthOverviewRowGap * (rows - 1)) / rows
    return shared.coerceIn(MonthOverviewMinRowHeight, MonthOverviewRowHeight)
}

/** The day circle keeps the regular 10 dp for its dots and spacing; 34 dp in a regular 44 dp row. */
internal fun monthOverviewDayCircleSize(rowHeight: Dp): Dp =
    (rowHeight - 10.dp).coerceIn(MonthOverviewDayCircleMin, MonthOverviewDayCircleMax)

/**
 * In the tabletop posture the month overview fills the top half. A wide, low half (a book-style foldable
 * turned sideways) puts the vertical month strip beside the grid; a squarer one (a flip phone) stacks it.
 */
internal fun tabletopMonthOverviewPresentation(widthDp: Float, heightDp: Float): MonthOverviewPresentation =
    monthOverviewPresentation(sideBySide = widthDp >= heightDp * 2f)
