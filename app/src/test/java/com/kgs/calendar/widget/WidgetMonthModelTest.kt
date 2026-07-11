package com.kgs.calendar.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

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
    fun renderSpecKeepsOverflowInHeaderWhenTextRowsAreTight() {
        val tightStandard = WidgetMonthRenderSpec.from(WidgetSize(350, 357), rowCount = 5)
        val roomyStandard = WidgetMonthRenderSpec.from(WidgetSize(350, 432), rowCount = 5)
        val tightRow = rowWithItems(3)
        val roomyRow = rowWithItems(4)

        assertEquals(WidgetSizeBucket.Standard, tightStandard.bucket)
        assertEquals(WidgetSizeBucket.Standard, roomyStandard.bucket)
        assertEquals(2, tightStandard.textItemCapacityFor(tightRow))
        assertTrue(tightStandard.showTextOverflow(textItemCapacity = 2))
        assertEquals(3, roomyStandard.textItemCapacityFor(roomyRow))
        assertTrue(roomyStandard.showTextOverflow(textItemCapacity = 3))
    }

    @Test
    fun renderSpecOnlyShowsTextRowsThatFitChipLayer() {
        val justTooShortForFourRows = WidgetMonthRenderSpec(WidgetSizeBucket.Standard, weekCellHeightDp = 74)
        val firstHeightForFourRows = WidgetMonthRenderSpec(WidgetSizeBucket.Comfortable, weekCellHeightDp = 75)
        val row = rowWithItems(5)

        assertEquals(3, justTooShortForFourRows.textItemCapacityFor(row))
        assertEquals(4, firstHeightForFourRows.textItemCapacityFor(row))
    }

    @Test
    fun renderSpecCanUseAllMonthLanesWhenTheCellIsTallEnough() {
        val tallCell = WidgetMonthRenderSpec(WidgetSizeBucket.Max, weekCellHeightDp = 168)
        val row = rowWithItems(12)

        assertEquals(10, tallCell.textItemCapacityFor(row))
    }

    @Test
    fun renderSpecShrinksMiniDotsToAvailableCellHeight() {
        val dayOnly = WidgetMonthRenderSpec(WidgetSizeBucket.Tiny, weekCellHeightDp = 27)
        val dotsOnly = WidgetMonthRenderSpec(WidgetSizeBucket.Tiny, weekCellHeightDp = 28)
        val oneDotRowWithOverflow = WidgetMonthRenderSpec(WidgetSizeBucket.Tiny, weekCellHeightDp = 36)
        val twoDotRowsWithOverflow = WidgetMonthRenderSpec(WidgetSizeBucket.Tiny, weekCellHeightDp = 37)

        assertEquals(0, dayOnly.miniDotCapacityFor(6))
        assertTrue(dayOnly.showMiniOverflow(hiddenItemCount = 6))
        assertEquals(5, dotsOnly.miniDotCapacityFor(6))
        assertTrue(dotsOnly.showMiniOverflow(hiddenItemCount = 1))
        assertEquals(5, oneDotRowWithOverflow.miniDotCapacityFor(11))
        assertTrue(oneDotRowWithOverflow.showMiniOverflow(hiddenItemCount = 6))
        assertEquals(10, twoDotRowsWithOverflow.miniDotCapacityFor(11))
        assertTrue(twoDotRowsWithOverflow.showMiniOverflow(hiddenItemCount = 1))
    }

    @Test
    fun renderSpecShrinksMiniDotsToAvailableCellWidth() {
        val narrow = WidgetMonthRenderSpec(
            bucket = WidgetSizeBucket.Tiny,
            weekCellHeightDp = 37,
            cellContentWidthDp = 27f,
        )
        val tooNarrow = narrow.copy(cellContentWidthDp = 8f)

        assertEquals(2, narrow.miniDotsPerRow())
        assertEquals(4, narrow.miniDotCapacityFor(10))
        assertEquals(2 to 2, narrow.miniDotRowCountsFor(10))
        assertEquals(0, tooNarrow.miniDotsPerRow())
        assertEquals(0, tooNarrow.miniDotCapacityFor(10))
        assertEquals(0 to 0, tooNarrow.miniDotRowCountsFor(10))
    }

    @Test
    fun renderSpecUsesDotsWhenTextCellsCannotFitItems() {
        val compactFallback = WidgetMonthRenderSpec(WidgetSizeBucket.Compact, weekCellHeightDp = 45)

        assertTrue(compactFallback.usesDotCells)
        assertEquals(10, compactFallback.miniDotCapacityFor(11))
        assertTrue(compactFallback.showMiniOverflow(hiddenItemCount = 1))
    }

    @Test
    fun monthLayoutKeepsTenLanesAvailableForDotRendering() {
        val month = YearMonth.of(2026, 6)
        val start = WidgetMonthModel.gridStart(month, DayOfWeek.MONDAY)
        val rowCount = WidgetMonthModel.rowCount(month, DayOfWeek.MONDAY)
        val day = LocalDate.of(2026, 6, 3)
        val layout = WidgetMonthModel.layout(
            month = month,
            gridStart = start,
            rowCount = rowCount,
            candidates = (0 until 11).map { index ->
                WidgetMonthCandidate(
                    id = "event:$index",
                    title = "Event $index",
                    color = 0xFF1A73E8.toInt(),
                    sortMillis = index.toLong(),
                    start = day,
                    end = day,
                    completed = false,
                )
            },
            locale = java.util.Locale.US,
        )

        assertEquals(11, layout.totalByDay.getValue(day))
        assertEquals((0 until 10).toList(), layout.itemsByDay.getValue(day).map { it.lane })
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
        assertEquals(7, WidgetSizeBucket.Compact.visibleItemCount(7))
        assertEquals(0, WidgetSizeBucket.Compact.overflowCount(7))
        assertEquals(7, WidgetSizeBucket.Standard.visibleItemCount(7))
        assertEquals(0, WidgetSizeBucket.Standard.overflowCount(7))
        assertEquals(7, WidgetSizeBucket.Comfortable.visibleItemCount(7))
        assertEquals(0, WidgetSizeBucket.Comfortable.overflowCount(7))
        assertEquals(7, WidgetSizeBucket.Expanded.visibleItemCount(7))
        assertEquals(0, WidgetSizeBucket.Expanded.overflowCount(7))
        assertEquals(7, WidgetSizeBucket.Max.visibleItemCount(7))
        assertEquals(0, WidgetSizeBucket.Max.overflowCount(7))
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
    fun monthLayoutShowsMultiDayTitleAtEachNewRowStart() {
        val month = YearMonth.of(2026, 6)
        val start = WidgetMonthModel.gridStart(month, DayOfWeek.MONDAY)
        val rowCount = WidgetMonthModel.rowCount(month, DayOfWeek.MONDAY)
        val layout = WidgetMonthModel.layout(
            month = month,
            gridStart = start,
            rowCount = rowCount,
            candidates = listOf(
                WidgetMonthCandidate(
                    id = "event:trip",
                    title = "Trip",
                    color = 0xFF1A73E8.toInt(),
                    sortMillis = 0L,
                    start = LocalDate.of(2026, 6, 5),
                    end = LocalDate.of(2026, 6, 10),
                    completed = false,
                ),
            ),
            locale = java.util.Locale.US,
        )

        val firstRowStart = layout.itemsByDay.getValue(LocalDate.of(2026, 6, 5)).single()
        val secondRowStart = layout.itemsByDay.getValue(LocalDate.of(2026, 6, 8)).single()
        val secondRowMiddle = layout.itemsByDay.getValue(LocalDate.of(2026, 6, 9)).single()

        assertEquals("Trip", firstRowStart.title)
        assertEquals("Trip", secondRowStart.title)
        assertTrue(secondRowStart.fadesFromPrevious)
        assertEquals("", secondRowMiddle.title)
    }

    @Test
    fun monthWeekSegmentsMergeVisuallyConnectedAdjacentItemsWithDifferentIds() {
        val date = LocalDate.of(2026, 5, 1)
        val row = listOf(
            WidgetMonthCellContent(
                date = date,
                inCurrentMonth = true,
                items = listOf(
                    widgetMonthItem(0).copy(
                        id = "event:first-piece",
                        title = "Two Day Event",
                        continuesToNext = true,
                    ),
                ),
                totalItemCount = 1,
            ),
            WidgetMonthCellContent(
                date = date.plusDays(1),
                inCurrentMonth = true,
                items = listOf(
                    widgetMonthItem(0).copy(
                        id = "event:second-piece",
                        title = "",
                        continuesFromPrevious = true,
                    ),
                ),
                totalItemCount = 1,
            ),
        ) + (2 until 7).map { offset ->
            WidgetMonthCellContent(
                date = date.plusDays(offset.toLong()),
                inCurrentMonth = true,
                items = emptyList(),
                totalItemCount = 0,
            )
        }

        val segments = row.monthWeekSegments(lane = 0)

        assertEquals(1, segments.size)
        assertEquals(0, segments.single().startColumn)
        assertEquals(1, segments.single().endColumn)
        assertEquals("Two Day Event", segments.single().title)
    }

    @Test
    fun monthBottomFadeSegmentsIgnoreMultiDayItemsAboveBottomLane() {
        val date = LocalDate.of(2026, 5, 1)
        val row = listOf(
            WidgetMonthCellContent(
                date = date,
                inCurrentMonth = true,
                items = listOf(
                    widgetMonthItem(0).copy(id = "single:first", title = "Single"),
                    widgetMonthItem(1).copy(id = "event:span", title = "Trip", continuesToNext = true),
                ),
                totalItemCount = 2,
            ),
            WidgetMonthCellContent(
                date = date.plusDays(1),
                inCurrentMonth = true,
                items = listOf(
                    widgetMonthItem(1).copy(id = "event:span", title = "", continuesFromPrevious = true),
                ),
                totalItemCount = 1,
            ),
        ) + (2 until 7).map { offset ->
            WidgetMonthCellContent(
                date = date.plusDays(offset.toLong()),
                inCurrentMonth = true,
                items = emptyList(),
                totalItemCount = 0,
            )
        }

        assertTrue(row.monthBottomFadeSegments(textItemCapacity = 4).isEmpty())
    }

    @Test
    fun monthBottomFadeSegmentsUseMultiDayItemsOnBottomLane() {
        val date = LocalDate.of(2026, 5, 1)
        val row = listOf(
            WidgetMonthCellContent(
                date = date,
                inCurrentMonth = true,
                items = listOf(
                    widgetMonthItem(0).copy(id = "single:first", title = "Single"),
                    widgetMonthItem(1).copy(id = "event:span", title = "Trip", continuesToNext = true),
                ),
                totalItemCount = 2,
            ),
            WidgetMonthCellContent(
                date = date.plusDays(1),
                inCurrentMonth = true,
                items = listOf(
                    widgetMonthItem(1).copy(id = "event:span", title = "", continuesFromPrevious = true),
                ),
                totalItemCount = 1,
            ),
        ) + (2 until 7).map { offset ->
            WidgetMonthCellContent(
                date = date.plusDays(offset.toLong()),
                inCurrentMonth = true,
                items = emptyList(),
                totalItemCount = 0,
            )
        }

        val segments = row.monthBottomFadeSegments(textItemCapacity = 2)

        assertEquals(1, segments.size)
        assertEquals(0, segments.single().startColumn)
        assertEquals(1, segments.single().endColumn)
        assertEquals(1, segments.single().item.lane)
    }

    @Test
    fun monthBottomFadeSegmentsIgnoreMultiDayItemsWithVisibleItemsBelow() {
        val date = LocalDate.of(2026, 5, 1)
        val row = listOf(
            WidgetMonthCellContent(
                date = date,
                inCurrentMonth = true,
                items = listOf(
                    widgetMonthItem(1).copy(id = "event:span", title = "Trip", continuesToNext = true),
                    widgetMonthItem(2).copy(id = "single:below", title = "Below"),
                ),
                totalItemCount = 2,
            ),
            WidgetMonthCellContent(
                date = date.plusDays(1),
                inCurrentMonth = true,
                items = listOf(
                    widgetMonthItem(1).copy(id = "event:span", title = "", continuesFromPrevious = true),
                ),
                totalItemCount = 1,
            ),
        ) + (2 until 7).map { offset ->
            WidgetMonthCellContent(
                date = date.plusDays(offset.toLong()),
                inCurrentMonth = true,
                items = emptyList(),
                totalItemCount = 0,
            )
        }

        assertTrue(row.monthBottomFadeSegments(textItemCapacity = 4).isEmpty())
    }

    @Test
    fun singleDayItemsUseOccurrenceTimeBeforeTitle() {
        val month = YearMonth.of(2026, 7)
        val day = LocalDate.of(2026, 7, 9)
        val layout = WidgetMonthModel.layout(
            month = month,
            gridStart = WidgetMonthModel.gridStart(month, DayOfWeek.MONDAY),
            rowCount = WidgetMonthModel.rowCount(month, DayOfWeek.MONDAY),
            candidates = listOf(
                WidgetMonthCandidate("late", "Alpha", 0, 2_000L, day, day, false),
                WidgetMonthCandidate("early", "Zulu", 0, 1_000L, day, day, false),
            ),
            locale = Locale.US,
        )

        assertEquals(listOf("early", "late"), layout.itemsByDay.getValue(day).map { it.id })
    }

    @Test
    fun singleDayItemsWithSameTimeUseLocalizedTitleTieBreak() {
        val month = YearMonth.of(2026, 7)
        val day = LocalDate.of(2026, 7, 9)
        val layout = WidgetMonthModel.layout(
            month = month,
            gridStart = WidgetMonthModel.gridStart(month, DayOfWeek.MONDAY),
            rowCount = WidgetMonthModel.rowCount(month, DayOfWeek.MONDAY),
            candidates = listOf(
                WidgetMonthCandidate("zulu", "Zulu", 0, 1_000L, day, day, false),
                WidgetMonthCandidate("alpha", "Alpha", 0, 1_000L, day, day, false),
            ),
            locale = Locale.US,
        )

        assertEquals(listOf("alpha", "zulu"), layout.itemsByDay.getValue(day).map { it.id })
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

    @Test
    fun loadingSkeletonUsesOnlyCurrentMonthCells() {
        val month = YearMonth.of(2026, 2)
        val source = WidgetMonthModel.page(
            month = month,
            start = WidgetMonthModel.gridStart(month, java.time.DayOfWeek.MONDAY),
            rowCount = WidgetMonthModel.rowCount(month, java.time.DayOfWeek.MONDAY),
            monthLayout = WidgetMonthLayout(emptyMap(), emptyMap()),
        )

        val skeleton = source.loadingSkeleton(0xFF777777.toInt())

        assertTrue(skeleton.cells.filter { it.inCurrentMonth }.all { it.items.isNotEmpty() })
        assertTrue(skeleton.cells.filterNot { it.inCurrentMonth }.all { it.items.isEmpty() })
        assertTrue(skeleton.cells.flatMap { it.items }.all { it.id.startsWith("month-skeleton:") })
    }

    private fun rowWithItems(count: Int): List<WidgetMonthCellContent> {
        val date = LocalDate.of(2026, 6, 1)
        return listOf(
            WidgetMonthCellContent(
                date = date,
                inCurrentMonth = true,
                items = (0 until count).map { lane -> widgetMonthItem(lane) },
                totalItemCount = count,
            ),
        ) + (1 until 7).map { offset ->
            WidgetMonthCellContent(
                date = date.plusDays(offset.toLong()),
                inCurrentMonth = true,
                items = emptyList(),
                totalItemCount = 0,
            )
        }
    }

    private fun widgetMonthItem(lane: Int): WidgetMonthItem =
        WidgetMonthItem(
            id = "item:$lane",
            title = "Item $lane",
            color = 0xFF1A73E8.toInt(),
            sortMillis = lane.toLong(),
            lane = lane,
            continuesFromPrevious = false,
            continuesToNext = false,
            fadesFromPrevious = false,
            fadesToNext = false,
            completed = false,
        )
}
