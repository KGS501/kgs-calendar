package com.kgs.calendar.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class WidgetMonthModelTest {
    @Test
    fun bucketSelectionMatchesPlannedBreakpoints() {
        assertEquals(WidgetSizeBucket.Tiny, WidgetSizeBucket.from(WidgetSize(180, 220)))
        assertEquals(WidgetSizeBucket.Mini, WidgetSizeBucket.from(WidgetSize(220, 270)))
        assertEquals(WidgetSizeBucket.Compact, WidgetSizeBucket.from(WidgetSize(260, 320)))
        assertEquals(WidgetSizeBucket.Comfortable, WidgetSizeBucket.from(WidgetSize(330, 500)))
        assertEquals(WidgetSizeBucket.Expanded, WidgetSizeBucket.from(WidgetSize(430, 580)))
        assertEquals(WidgetSizeBucket.Max, WidgetSizeBucket.from(WidgetSize(430, 680)))
        assertEquals(WidgetSizeBucket.Standard, WidgetSizeBucket.from(WidgetSize(648, 358)))
        assertEquals(WidgetSizeBucket.Max, WidgetSizeBucket.from(WidgetSize(368, 684)))
        assertEquals(WidgetSizeBucket.Expanded, WidgetSizeBucket.from(WidgetSize(368, 684), rowCount = 6))
    }

    @Test
    fun monthPageUsesFiveRowsWhenTheMonthFitsAndHonorsFirstDayOfWeek() {
        val month = YearMonth.of(2026, 6)
        val start = WidgetMonthModel.gridStart(month, DayOfWeek.MONDAY)
        val rowCount = WidgetMonthModel.rowCount(month, DayOfWeek.MONDAY)
        val page = WidgetMonthModel.page(month, start, rowCount, WidgetMonthLayout(emptyMap(), emptyMap()))

        assertEquals(LocalDate.of(2026, 6, 1), start)
        assertEquals(5, rowCount)
        assertEquals(42, page.cells.size)
        assertTrue(page.cells.first().inCurrentMonth)
        assertFalse(page.cells[35].inCurrentMonth)
        assertEquals(LocalDate.of(2026, 7, 12), page.cells.last().date)
    }

    @Test
    fun monthPageUsesSixRowsWhenTheMonthNeedsThem() {
        val month = YearMonth.of(2026, 8)
        val start = WidgetMonthModel.gridStart(month, DayOfWeek.MONDAY)
        val rowCount = WidgetMonthModel.rowCount(month, DayOfWeek.MONDAY)
        val page = WidgetMonthModel.page(month, start, rowCount, WidgetMonthLayout(emptyMap(), emptyMap()))

        assertEquals(6, rowCount)
        assertEquals(42, page.cells.size)
        assertEquals(LocalDate.of(2026, 9, 6), page.cells.last().date)
    }

    @Test
    fun monthPageMarksLeadingDaysOutsideCurrentMonth() {
        val month = YearMonth.of(2026, 8)
        val start = WidgetMonthModel.gridStart(month, DayOfWeek.MONDAY)
        val rowCount = WidgetMonthModel.rowCount(month, DayOfWeek.MONDAY)
        val page = WidgetMonthModel.page(month, start, rowCount, WidgetMonthLayout(emptyMap(), emptyMap()))

        assertEquals(LocalDate.of(2026, 7, 27), start)
        assertFalse(page.cells[0].inCurrentMonth)
        assertFalse(page.cells[4].inCurrentMonth)
        assertTrue(page.cells[5].inCurrentMonth)
    }

    @Test
    fun bucketCapsVisibleItemsAndReportsOverflow() {
        assertEquals(3, WidgetSizeBucket.Tiny.visibleItemCount(7))
        assertEquals(4, WidgetSizeBucket.Tiny.overflowCount(7))
        assertEquals(6, WidgetSizeBucket.Mini.visibleItemCount(7))
        assertEquals(1, WidgetSizeBucket.Mini.overflowCount(7))
        assertEquals(6, WidgetSizeBucket.Mini.visibleItemCount(12))
        assertEquals(6, WidgetSizeBucket.Mini.overflowCount(12))
        assertEquals(1, WidgetSizeBucket.Compact.visibleItemCount(7))
        assertEquals(6, WidgetSizeBucket.Compact.overflowCount(7))
        assertEquals(2, WidgetSizeBucket.Standard.visibleItemCount(7))
        assertEquals(5, WidgetSizeBucket.Standard.overflowCount(7))
        assertEquals(3, WidgetSizeBucket.Comfortable.visibleItemCount(7))
        assertEquals(4, WidgetSizeBucket.Comfortable.overflowCount(7))
        assertEquals(4, WidgetSizeBucket.Expanded.visibleItemCount(7))
        assertEquals(3, WidgetSizeBucket.Expanded.overflowCount(7))
        assertEquals(5, WidgetSizeBucket.Max.visibleItemCount(7))
        assertEquals(2, WidgetSizeBucket.Max.overflowCount(7))
    }

    @Test
    fun monthLayoutKeepsMultiDayItemsInOneLaneWithContinuationFlags() {
        val month = YearMonth.of(2026, 6)
        val start = WidgetMonthModel.gridStart(month, DayOfWeek.MONDAY)
        val rowCount = WidgetMonthModel.rowCount(month, DayOfWeek.MONDAY)
        val layout = WidgetMonthModel.layout(
            month = month,
            gridStart = start,
            rowCount = rowCount,
            candidates = listOf(
                WidgetMonthCandidate(
                    id = "event:test",
                    title = "Trip",
                    color = 0xFF1A73E8.toInt(),
                    sortMillis = 0L,
                    start = LocalDate.of(2026, 6, 3),
                    end = LocalDate.of(2026, 6, 5),
                    completed = false,
                ),
            ),
            locale = java.util.Locale.US,
        )

        val first = layout.itemsByDay.getValue(LocalDate.of(2026, 6, 3)).single()
        val middle = layout.itemsByDay.getValue(LocalDate.of(2026, 6, 4)).single()
        val last = layout.itemsByDay.getValue(LocalDate.of(2026, 6, 5)).single()

        assertEquals(0, first.lane)
        assertEquals(0, middle.lane)
        assertEquals(0, last.lane)
        assertFalse(first.continuesFromPrevious)
        assertTrue(first.continuesToNext)
        assertTrue(middle.continuesFromPrevious)
        assertTrue(middle.continuesToNext)
        assertTrue(last.continuesFromPrevious)
        assertFalse(last.continuesToNext)
        assertEquals("", middle.title)
    }

    @Test
    fun monthLayoutKeepsEarlierVisibleMultiDayItemsAheadOfLaterLongItems() {
        val month = YearMonth.of(2026, 7)
        val start = WidgetMonthModel.gridStart(month, DayOfWeek.MONDAY)
        val rowCount = WidgetMonthModel.rowCount(month, DayOfWeek.MONDAY)
        val layout = WidgetMonthModel.layout(
            month = month,
            gridStart = start,
            rowCount = rowCount,
            candidates = listOf(
                WidgetMonthCandidate(
                    id = "single:20",
                    title = "Single",
                    color = 0xFF1A73E8.toInt(),
                    sortMillis = 0L,
                    start = LocalDate.of(2026, 7, 20),
                    end = LocalDate.of(2026, 7, 20),
                    completed = false,
                ),
                WidgetMonthCandidate(
                    id = "school",
                    title = "Schulferien",
                    color = 0xFF1A73E8.toInt(),
                    sortMillis = 1L,
                    start = LocalDate.of(2026, 7, 20),
                    end = LocalDate.of(2026, 9, 2),
                    completed = false,
                ),
                WidgetMonthCandidate(
                    id = "semester",
                    title = "Semesterferien",
                    color = 0xFF1A73E8.toInt(),
                    sortMillis = 2L,
                    start = LocalDate.of(2026, 7, 25),
                    end = LocalDate.of(2026, 10, 1),
                    completed = false,
                ),
            ),
            locale = java.util.Locale.US,
        )

        val schoolJuly20 = layout.itemsByDay.getValue(LocalDate.of(2026, 7, 20)).single { it.id == "school" }
        val schoolJuly23 = layout.itemsByDay.getValue(LocalDate.of(2026, 7, 23)).single { it.id == "school" }
        val semesterJuly25 = layout.itemsByDay.getValue(LocalDate.of(2026, 7, 25)).single { it.id == "semester" }

        assertEquals(0, schoolJuly20.lane)
        assertEquals(0, schoolJuly23.lane)
        assertTrue(semesterJuly25.lane > schoolJuly23.lane)
    }
}
