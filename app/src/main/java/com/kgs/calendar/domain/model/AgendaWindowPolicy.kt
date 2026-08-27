package com.kgs.calendar.domain.model

import java.time.LocalDate

/** Bounded Agenda data windows that can grow without rebuilding a fifteen-year query. */
internal object AgendaWindowPolicy {
    private const val MonthsBeforeAnchor = 12L
    private const val MonthsAfterAnchor = 24L
    private const val ExtensionMonths = 12L

    fun around(anchor: LocalDate): CalendarRange {
        val month = anchor.withDayOfMonth(1)
        return CalendarRange(
            startDate = month.minusMonths(MonthsBeforeAnchor),
            endExclusiveDate = month.plusMonths(MonthsAfterAnchor + 1L),
        )
    }

    fun recenterIfNeeded(range: CalendarRange, target: LocalDate): CalendarRange =
        if (range.contains(target)) range else around(target)

    fun extendEarlier(range: CalendarRange): CalendarRange = range.copy(
        startDate = range.startDate.minusMonths(ExtensionMonths),
    )

    fun extendLater(range: CalendarRange): CalendarRange = range.copy(
        endExclusiveDate = range.endExclusiveDate.plusMonths(ExtensionMonths),
    )
}

internal fun CalendarRange.contains(date: LocalDate): Boolean =
    !date.isBefore(startDate) && date.isBefore(endExclusiveDate)
