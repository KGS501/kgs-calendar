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
