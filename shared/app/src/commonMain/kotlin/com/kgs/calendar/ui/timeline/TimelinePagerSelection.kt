package com.kgs.calendar.ui.timeline

import com.kgs.calendar.ui.calendar.toDayDate
import java.time.LocalDate

/**
 * Returns the date a settled timeline pager page should select, or null when the settle only
 * confirms the current selection.
 *
 * In the aligned week view the pager rests on the week's first day, not on the selected date.
 * Choosing 1 October (a Thursday) from the month panel makes the pager settle on Monday
 * 28 September. That settle is the echo of the programmatic move and must not select the Monday:
 * doing so dragged the month panel back to September (#28). A settle on the anchor page of the
 * current selection therefore never changes the selected date.
 */
internal fun timelineSettledPageSelection(
    settledPage: Int,
    selectedAnchorPage: Int,
    selectedDate: LocalDate,
): LocalDate? {
    if (settledPage == selectedAnchorPage) return null
    return settledPage.toDayDate().takeIf { it != selectedDate }
}
