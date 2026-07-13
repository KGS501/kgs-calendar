package com.kgs.calendar.ui.timeline

import com.kgs.calendar.ui.calendar.DayPagerBaseDate
import java.time.DayOfWeek

internal fun weekStartPageOffset(firstDayOfWeek: DayOfWeek): Int =
    Math.floorMod(firstDayOfWeek.value - DayPagerBaseDate.dayOfWeek.value, 7)

internal fun fullWeekTargetPage(
    startPage: Int,
    suggestedTargetPage: Int,
    pageCount: Int,
    weekStartPageOffset: Int,
): Int {
    val alignedStart = startPage - Math.floorMod(startPage - weekStartPageOffset, 7)
    val minimumAlignedPage = weekStartPageOffset.coerceIn(0, pageCount - 1)
    val lastPage = pageCount - 1
    val maximumAlignedPage = lastPage - Math.floorMod(lastPage - weekStartPageOffset, 7)
    val target = when {
        suggestedTargetPage > startPage -> 7
        suggestedTargetPage < startPage -> -7
        else -> 0
    }
    return (alignedStart + target).coerceIn(minimumAlignedPage, maximumAlignedPage)
}
