package com.kgs.calendar.widget

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private const val WIDGET_MONTH_MAX_LANES = 10

internal object WidgetMonthModel {
    fun gridStart(month: YearMonth, firstDayOfWeek: DayOfWeek): LocalDate {
        val first = month.atDay(1)
        val offset = (first.dayOfWeek.value - firstDayOfWeek.value + 7) % 7
        return first.minusDays(offset.toLong())
    }

    fun rowCount(month: YearMonth, firstDayOfWeek: DayOfWeek): Int {
        val leadingDays = (month.atDay(1).dayOfWeek.value - firstDayOfWeek.value + 7) % 7
        return maxOf(5, (leadingDays + month.lengthOfMonth() + 6) / 7)
    }

    fun page(
        month: YearMonth,
        start: LocalDate,
        rowCount: Int,
        monthLayout: WidgetMonthLayout,
    ): WidgetMonthPage {
        val cells = (0 until 42).map { offset ->
            val date = start.plusDays(offset.toLong())
            WidgetMonthCellContent(
                date = date,
                inCurrentMonth = YearMonth.from(date) == month,
                items = monthLayout.itemsByDay[date].orEmpty(),
                totalItemCount = monthLayout.totalByDay[date] ?: 0,
            )
        }
        return WidgetMonthPage(month, rowCount, cells)
    }

    fun layout(
        month: YearMonth,
        gridStart: LocalDate,
        rowCount: Int,
        candidates: List<WidgetMonthCandidate>,
        locale: Locale,
    ): WidgetMonthLayout {
        val itemsByDay = mutableMapOf<LocalDate, MutableList<WidgetMonthItem>>()
        val totalByDay = mutableMapOf<LocalDate, Int>()

        repeat(rowCount) { row ->
            val rowStartDate = gridStart.plusDays((row * 7).toLong())
            val rowEndDate = rowStartDate.plusDays(6)
            val rowDays = (0 until 7)
                .map { rowStartDate.plusDays(it.toLong()) }
                .filter { YearMonth.from(it) == month }
            if (rowDays.isEmpty()) return@repeat
            val rowStart = rowDays.first()
            val rowEnd = rowDays.last()
            val rowCandidates = candidates
                .filter { it.end >= rowStart && it.start <= rowEnd }
                .distinctBy { it.id }
            rowDays.forEach { day ->
                totalByDay[day] = rowCandidates.count { day in it.start..it.end }
            }

            data class MutableSegment(
                val candidate: WidgetMonthCandidate,
                val visualStart: LocalDate,
                val visualEnd: LocalDate,
                val lane: Int,
            )

            val laneSegments = mutableListOf<MutableList<MutableSegment>>()
            val segments = mutableListOf<MutableSegment>()
            val sortedCandidates = rowCandidates.sortedWith { left, right ->
                val leftVisibleStart = maxOf(left.start, rowStart)
                val rightVisibleStart = maxOf(right.start, rowStart)
                val visibleStartComparison = leftVisibleStart.compareTo(rightVisibleStart)
                if (visibleStartComparison != 0) {
                    visibleStartComparison
                } else {
                    val spanComparison = right.spanDays.compareTo(left.spanDays)
                    if (spanComparison != 0) {
                        spanComparison
                    } else {
                        val timeComparison = if (left.spanDays == 1L && right.spanDays == 1L) {
                            left.sortMillis.compareTo(right.sortMillis)
                        } else {
                            0
                        }
                        if (timeComparison != 0) {
                            timeComparison
                        } else {
                            left.title.lowercase(locale).compareTo(right.title.lowercase(locale))
                        }
                    }
                }
            }

            sortedCandidates.forEach { candidate ->
                fun doesNotOverlap(existing: MutableSegment): Boolean =
                    existing.candidate.end < candidate.start || candidate.end < existing.candidate.start

                val existingLane = laneSegments.indexOfFirst { lane -> lane.all(::doesNotOverlap) }
                val targetLane = if (existingLane >= 0) existingLane else laneSegments.size
                val segment = MutableSegment(
                    candidate = candidate,
                    visualStart = maxOf(candidate.start, rowStart),
                    visualEnd = minOf(candidate.end, rowEnd),
                    lane = targetLane,
                )
                if (targetLane < laneSegments.size) {
                    laneSegments[targetLane] += segment
                } else {
                    laneSegments += mutableListOf(segment)
                }
                segments += segment
            }

            segments
                .filter { it.lane < WIDGET_MONTH_MAX_LANES && !it.visualEnd.isBefore(it.visualStart) }
                .forEach { segment ->
                    var day = segment.visualStart
                    while (!day.isAfter(segment.visualEnd)) {
                        val continuesFromPrevious = segment.candidate.start < day
                        val continuesToNext = segment.candidate.end > day
                        val fadesFromPrevious = day == segment.visualStart && segment.candidate.start < segment.visualStart
                        val fadesToNext = day == segment.visualEnd && segment.candidate.end > segment.visualEnd
                        itemsByDay.getOrPut(day) { mutableListOf() } += WidgetMonthItem(
                            id = segment.candidate.id,
                            title = if (day == segment.visualStart) segment.candidate.title else "",
                            color = segment.candidate.color,
                            sortMillis = segment.candidate.sortMillis,
                            lane = segment.lane,
                            continuesFromPrevious = continuesFromPrevious,
                            continuesToNext = continuesToNext,
                            fadesFromPrevious = fadesFromPrevious,
                            fadesToNext = fadesToNext,
                            completed = segment.candidate.completed,
                        )
                        day = day.plusDays(1)
                    }
                }
        }

        return WidgetMonthLayout(
            itemsByDay = itemsByDay.mapValues { (_, items) ->
                items.sortedWith(
                    compareBy<WidgetMonthItem> { it.lane }
                        .thenBy { it.sortMillis }
                        .thenBy { it.title.lowercase(locale) },
                )
            },
            totalByDay = totalByDay,
        )
    }
}

internal data class WidgetMonthPage(
    val month: YearMonth,
    val rowCount: Int,
    val cells: List<WidgetMonthCellContent>,
) {
    fun title(locale: Locale): String =
        month.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))
}

internal fun WidgetMonthPage.loadingSkeleton(color: Int): WidgetMonthPage = copy(
    cells = cells.map { cell ->
        if (!cell.inCurrentMonth) {
            cell.copy(items = emptyList(), totalItemCount = 0)
        } else {
            val laneCount = if (cell.date.dayOfMonth % 4 == 0) 2 else 1
            val placeholders = (0 until laneCount).map { lane ->
                WidgetMonthItem(
                    id = "month-skeleton:${cell.date}:$lane",
                    title = "",
                    color = color,
                    sortMillis = Long.MAX_VALUE,
                    lane = lane,
                    continuesFromPrevious = false,
                    continuesToNext = false,
                    fadesFromPrevious = false,
                    fadesToNext = false,
                    completed = false,
                )
            }
            cell.copy(items = placeholders, totalItemCount = placeholders.size)
        }
    },
)

internal data class WidgetMonthCellContent(
    val date: LocalDate,
    val inCurrentMonth: Boolean,
    val items: List<WidgetMonthItem>,
    val totalItemCount: Int,
)

internal data class WidgetMonthLayout(
    val itemsByDay: Map<LocalDate, List<WidgetMonthItem>>,
    val totalByDay: Map<LocalDate, Int>,
)

internal data class WidgetMonthCandidate(
    val id: String,
    val title: String,
    val color: Int,
    val sortMillis: Long,
    val start: LocalDate,
    val end: LocalDate,
    val completed: Boolean,
) {
    val spanDays: Long = ChronoUnit.DAYS.between(start, end).coerceAtLeast(0) + 1
}

internal data class WidgetMonthItem(
    val id: String,
    val title: String,
    val color: Int,
    val sortMillis: Long,
    val lane: Int,
    val continuesFromPrevious: Boolean,
    val continuesToNext: Boolean,
    val fadesFromPrevious: Boolean,
    val fadesToNext: Boolean,
    val completed: Boolean,
)

internal data class WidgetMonthWeekSegment(
    val startColumn: Int,
    val endColumn: Int,
    val title: String,
    val item: WidgetMonthItem,
) {
    val columnSpan: Int = endColumn - startColumn + 1
}

internal fun List<WidgetMonthCellContent>.monthWeekSegments(lane: Int): List<WidgetMonthWeekSegment> {
    val segments = mutableListOf<WidgetMonthWeekSegment>()
    var column = 0
    while (column < size) {
        if (!this[column].inCurrentMonth) {
            column += 1
            continue
        }
        val firstItem = this[column].items.firstOrNull { it.lane == lane } ?: run {
            column += 1
            continue
        }

        var endColumn = column
        var last = firstItem
        while (endColumn + 1 < size) {
            val next = this[endColumn + 1].items.firstOrNull { it.lane == lane }
            if (next == null || !this[endColumn + 1].inCurrentMonth || !last.visuallyContinuesInto(next)) {
                break
            }
            endColumn += 1
            last = next
        }

        val title = (column..endColumn)
            .asSequence()
            .mapNotNull { index ->
                this[index].items.firstOrNull { it.lane == lane }?.title?.takeIf(String::isNotBlank)
            }
            .firstOrNull()
            .orEmpty()
        segments += WidgetMonthWeekSegment(
            startColumn = column,
            endColumn = endColumn,
            title = title,
            item = firstItem.copy(
                title = title,
                continuesToNext = last.continuesToNext,
                fadesToNext = last.fadesToNext,
            ),
        )
        column = endColumn + 1
    }
    return segments
}

internal fun WidgetMonthItem.visuallyContinuesInto(next: WidgetMonthItem): Boolean =
    id == next.id &&
        lane == next.lane &&
        color == next.color &&
        continuesToNext &&
        next.continuesFromPrevious

internal fun List<WidgetMonthCellContent>.monthBottomFadeSegments(
    textItemCapacity: Int,
): List<WidgetMonthWeekSegment> {
    if (textItemCapacity <= 0) return emptyList()
    val bottomLane = textItemCapacity - 1
    val candidates = monthWeekSegments(bottomLane)
        .asSequence()
        .filter { segment ->
            val item = segment.item
            segment.columnSpan > 1 ||
                item.continuesFromPrevious ||
                item.continuesToNext ||
                item.fadesFromPrevious ||
                item.fadesToNext
        }
        .sortedWith(compareBy<WidgetMonthWeekSegment> { it.startColumn }.thenByDescending { it.columnSpan })
        .toList()

    val result = mutableListOf<WidgetMonthWeekSegment>()
    var nextFreeColumn = 0
    candidates.forEach { segment ->
        if (segment.startColumn >= nextFreeColumn) {
            result += segment
            nextFreeColumn = segment.endColumn + 1
        }
    }
    return result
}
