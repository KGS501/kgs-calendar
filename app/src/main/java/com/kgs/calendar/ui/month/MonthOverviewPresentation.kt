package com.kgs.calendar.ui.month

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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

internal fun monthOverviewPresentation(isLandscape: Boolean): MonthOverviewPresentation =
    if (isLandscape) MonthOverviewPresentation.SideBySide else MonthOverviewPresentation.Stacked
