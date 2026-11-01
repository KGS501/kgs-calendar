package com.kgs.calendar.ui.month

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class MonthRowOrderingTest {
    private val day = LocalDate.of(2026, 7, 11)

    @Test fun singleDayItemsUseOccurrenceTimeBeforeTitle() {
        val lateAlphabeticallyFirst = item("Alpha", spanDays = 1, millis = 18 * 60L)
        val earlyAlphabeticallyLast = item("Zulu", spanDays = 1, millis = 8 * 60L)

        assertEquals(
            listOf("Zulu", "Alpha"),
            listOf(lateAlphabeticallyFirst, earlyAlphabeticallyLast)
                .sortedWith(MonthRowOrderComparator)
                .map { it.title },
        )
    }

    @Test fun multiDayItemsKeepDurationThenStartThenTitleOrdering() {
        val short = item("A short", spanDays = 2, millis = 1)
        val longZulu = item("Zulu long", spanDays = 4, millis = 999)
        val longAlpha = item("Alpha long", spanDays = 4, millis = 1)

        assertEquals(
            listOf("Alpha long", "Zulu long", "A short"),
            listOf(short, longZulu, longAlpha).sortedWith(MonthRowOrderComparator).map { it.title },
        )
    }

    private fun item(title: String, spanDays: Long, millis: Long) = object : MonthRowOrderItem {
        override val spanDays = spanDays
        override val start = day
        override val occurrenceSortMillis = millis
        override val title = title
    }
}
