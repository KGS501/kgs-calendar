package com.kgs.calendar.ui.month

import java.time.LocalDate

internal interface MonthRowOrderItem {
    val spanDays: Long
    val start: LocalDate
    val occurrenceSortMillis: Long
    val title: String
}

internal val MonthRowOrderComparator = Comparator<MonthRowOrderItem> { left, right ->
    right.spanDays.compareTo(left.spanDays)
        .takeIf { it != 0 }
        ?: left.start.compareTo(right.start).takeIf { it != 0 }
        ?: if (left.spanDays == 1L && right.spanDays == 1L) {
            left.occurrenceSortMillis.compareTo(right.occurrenceSortMillis).takeIf { it != 0 }
        } else {
            null
        }
        ?: left.title.compareTo(right.title)
}
