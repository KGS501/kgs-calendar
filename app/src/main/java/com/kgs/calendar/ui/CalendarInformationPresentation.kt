package com.kgs.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kgs.calendar.R
import com.kgs.calendar.domain.model.CalendarViewMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

internal data class CalendarInformationChrome(
    val yearStripItemsHaveBorders: Boolean,
    val agendaStickyHeaderAlpha: Float,
)

internal val calendarInformationChrome = CalendarInformationChrome(
    yearStripItemsHaveBorders = false,
    agendaStickyHeaderAlpha = 1f,
)

internal data class AgendaChronologyRenderingPolicy(
    val renderValuesInsideListRows: Boolean,
    val renderValuesInChronologyLayer: Boolean,
)

internal val agendaChronologyRenderingPolicy = AgendaChronologyRenderingPolicy(
    renderValuesInsideListRows = false,
    renderValuesInChronologyLayer = true,
)

internal data class MonthOverviewSelectionPlan(
    val selectedDate: LocalDate,
    val agendaScrollTargetDate: LocalDate?,
)

internal fun monthOverviewSelectionPlan(
    month: YearMonth,
    selectedView: CalendarViewMode,
): MonthOverviewSelectionPlan = MonthOverviewSelectionPlan(
    selectedDate = month.atDay(1),
    agendaScrollTargetDate = month.atDay(1).takeIf { selectedView == CalendarViewMode.Agenda },
)

internal data class AgendaChronologyPresentation(
    val eventGutterWidth: androidx.compose.ui.unit.Dp,
    val topHeaderHeight: androidx.compose.ui.unit.Dp,
    val dateStackHeight: androidx.compose.ui.unit.Dp,
    val datePushGap: androidx.compose.ui.unit.Dp,
    val dateLineHeightSp: Float,
    val dateHorizontalPadding: androidx.compose.ui.unit.Dp,
    val dateVerticalPadding: androidx.compose.ui.unit.Dp,
    val monthLaneWidth: androidx.compose.ui.unit.Dp,
    val yearLaneWidth: androidx.compose.ui.unit.Dp,
    val informationFontSizeSp: Float,
    val weekFontSizeSp: Float,
)

internal val agendaChronologyPresentation = AgendaChronologyPresentation(
    eventGutterWidth = 38.dp,
    topHeaderHeight = 28.dp,
    dateStackHeight = 44.dp,
    datePushGap = 6.dp,
    dateLineHeightSp = 20f,
    dateHorizontalPadding = 2.dp,
    dateVerticalPadding = 2.dp,
    monthLaneWidth = 120.dp,
    yearLaneWidth = 64.dp,
    informationFontSizeSp = 18f,
    weekFontSizeSp = 18f,
)

internal fun resolvedAgendaDateStackHeight(fontScale: Float): androidx.compose.ui.unit.Dp =
    agendaChronologyPresentation.dateStackHeight +
        (
            agendaChronologyPresentation.dateLineHeightSp * 2f *
                (fontScale.coerceAtLeast(0.5f) - 1f)
            ).dp

internal fun resolvedAgendaDatePushDistance(fontScale: Float): androidx.compose.ui.unit.Dp =
    resolvedAgendaDateStackHeight(fontScale) + agendaChronologyPresentation.datePushGap

internal data class MonthCalendarWeekRow(
    val weekStart: LocalDate,
    val weekNumber: Int,
)

internal fun monthCalendarWeekRows(
    month: YearMonth,
    firstDayOfWeek: DayOfWeek,
): List<MonthCalendarWeekRow> {
    val firstWeekStart = month.atDay(1).startOfCalendarWeek(firstDayOfWeek)
    val coveredDays = ChronoUnit.DAYS.between(firstWeekStart, month.atEndOfMonth()).toInt() + 1
    val rowCount = ((coveredDays + 6) / 7).coerceAtLeast(5)
    return (0 until rowCount).map { row ->
        val weekStart = firstWeekStart.plusWeeks(row.toLong())
        MonthCalendarWeekRow(
            weekStart = weekStart,
            weekNumber = weekStart.calendarWeekNumber(firstDayOfWeek),
        )
    }
}

internal fun agendaDayGroupKey(date: LocalDate): String = date.toString()

internal fun agendaBoundaryKey(date: LocalDate): String = "agenda-boundary-$date"

internal data class AgendaVisibleHeaderItem(
    val key: Any,
    val offset: Int,
    val lazyIndex: Int,
)

internal data class AgendaHeaderSignal(
    val lazyIndex: Int,
    val key: Any,
    val date: LocalDate,
)

internal data class AgendaHeaderSignals(
    val days: List<AgendaHeaderSignal>,
    val months: List<AgendaHeaderSignal>,
    val years: List<AgendaHeaderSignal>,
    val weeks: List<AgendaHeaderSignal>,
)

internal data class AgendaStickyPushPlacement(
    val outgoingOffset: Int,
    val incomingOffset: Int,
)

internal fun agendaStickyPushPlacement(
    incomingNaturalOffset: Int,
    elementHeight: Int,
): AgendaStickyPushPlacement = AgendaStickyPushPlacement(
    outgoingOffset = minOf(0, incomingNaturalOffset - elementHeight),
    incomingOffset = incomingNaturalOffset,
)

internal data class AgendaStickyFieldElement(
    val key: Any,
    val date: LocalDate,
    val offset: Int,
)

internal data class AgendaStickyFieldViewport(
    val elements: List<AgendaStickyFieldElement>,
)

internal data class AgendaStickyHeaderViewport(
    val day: AgendaStickyFieldViewport,
    val month: AgendaStickyFieldViewport,
    val year: AgendaStickyFieldViewport,
    val week: AgendaStickyFieldViewport,
)

private fun agendaSignalNaturalOffset(
    signal: AgendaHeaderSignal,
    visibleItemsByKey: Map<Any, AgendaVisibleHeaderItem>,
    firstVisibleItemIndex: Int,
    viewportStartOffset: Int,
    contentTopInset: Int,
): Int {
    val visibleOffset = visibleItemsByKey[signal.key]?.offset
    return when {
        visibleOffset != null -> agendaOverlayNaturalOffset(
            lazyItemOffset = visibleOffset,
            viewportStartOffset = viewportStartOffset,
            contentTopInset = contentTopInset,
        )
        signal.lazyIndex < firstVisibleItemIndex -> Int.MIN_VALUE
        else -> Int.MAX_VALUE
    }
}

internal fun agendaOverlayNaturalOffset(
    lazyItemOffset: Int,
    viewportStartOffset: Int,
    contentTopInset: Int,
): Int = lazyItemOffset - viewportStartOffset + contentTopInset

private fun agendaStickyFieldViewport(
    signals: List<AgendaHeaderSignal>,
    visibleItemsByKey: Map<Any, AgendaVisibleHeaderItem>,
    firstVisibleItemIndex: Int,
    lastVisibleItemIndex: Int,
    viewportStartOffset: Int,
    contentTopInset: Int,
    elementHeight: Int,
): AgendaStickyFieldViewport {
    if (signals.isEmpty()) {
        return AgendaStickyFieldViewport(emptyList())
    }
    var low = 0
    var high = signals.size
    while (low < high) {
        val middle = (low + high) ushr 1
        if (signals[middle].lazyIndex < firstVisibleItemIndex) {
            low = middle + 1
        } else {
            high = middle
        }
    }
    val firstVisibleSignalIndex = low
    var crossedIndex = firstVisibleSignalIndex - 1
    var visibleSignalEndExclusive = firstVisibleSignalIndex
    while (
        visibleSignalEndExclusive < signals.size &&
        signals[visibleSignalEndExclusive].lazyIndex <= lastVisibleItemIndex
    ) {
        val signal = signals[visibleSignalEndExclusive]
        if (
            agendaSignalNaturalOffset(
                signal = signal,
                visibleItemsByKey = visibleItemsByKey,
                firstVisibleItemIndex = firstVisibleItemIndex,
                viewportStartOffset = viewportStartOffset,
                contentTopInset = contentTopInset,
            ) <= 0
        ) {
            crossedIndex = visibleSignalEndExclusive
        }
        visibleSignalEndExclusive++
    }
    return AgendaStickyFieldViewport(
        elements = buildList {
            if (crossedIndex >= 0) {
                val current = signals[crossedIndex]
                val incomingOffset = signals.getOrNull(crossedIndex + 1)?.let { signal ->
                    agendaSignalNaturalOffset(
                        signal = signal,
                        visibleItemsByKey = visibleItemsByKey,
                        firstVisibleItemIndex = firstVisibleItemIndex,
                        viewportStartOffset = viewportStartOffset,
                        contentTopInset = contentTopInset,
                    )
                } ?: Int.MAX_VALUE
                add(
                    AgendaStickyFieldElement(
                        key = current.key,
                        date = current.date,
                        offset = agendaStickyPushPlacement(
                            incomingNaturalOffset = incomingOffset,
                            elementHeight = elementHeight,
                        ).outgoingOffset,
                    ),
                )
            }
            val firstNaturalIndex = maxOf(crossedIndex + 1, firstVisibleSignalIndex)
            for (index in firstNaturalIndex until visibleSignalEndExclusive) {
                val signal = signals[index]
                val visibleItem = visibleItemsByKey[signal.key] ?: continue
                add(
                    AgendaStickyFieldElement(
                        key = signal.key,
                        date = signal.date,
                        offset = agendaOverlayNaturalOffset(
                            lazyItemOffset = visibleItem.offset,
                            viewportStartOffset = viewportStartOffset,
                            contentTopInset = contentTopInset,
                        ),
                    ),
                )
            }
        },
    )
}

internal fun agendaStickyHeaderViewport(
    signals: AgendaHeaderSignals,
    visibleItems: List<AgendaVisibleHeaderItem>,
    firstVisibleItemIndex: Int,
    viewportStartOffset: Int = 0,
    informationContentTopInset: Int,
    dayElementHeight: Int,
    informationElementHeight: Int,
): AgendaStickyHeaderViewport {
    val visibleItemsByKey = visibleItems.associateBy(AgendaVisibleHeaderItem::key)
    val lastVisibleItemIndex = visibleItems.maxOfOrNull(AgendaVisibleHeaderItem::lazyIndex)
        ?: firstVisibleItemIndex
    return AgendaStickyHeaderViewport(
        day = agendaStickyFieldViewport(
            signals = signals.days,
            visibleItemsByKey = visibleItemsByKey,
            firstVisibleItemIndex = firstVisibleItemIndex,
            lastVisibleItemIndex = lastVisibleItemIndex,
            viewportStartOffset = viewportStartOffset,
            contentTopInset = 0,
            elementHeight = dayElementHeight,
        ),
        month = agendaStickyFieldViewport(
            signals = signals.months,
            visibleItemsByKey = visibleItemsByKey,
            firstVisibleItemIndex = firstVisibleItemIndex,
            lastVisibleItemIndex = lastVisibleItemIndex,
            viewportStartOffset = viewportStartOffset,
            contentTopInset = informationContentTopInset,
            elementHeight = informationElementHeight,
        ),
        year = agendaStickyFieldViewport(
            signals = signals.years,
            visibleItemsByKey = visibleItemsByKey,
            firstVisibleItemIndex = firstVisibleItemIndex,
            lastVisibleItemIndex = lastVisibleItemIndex,
            viewportStartOffset = viewportStartOffset,
            contentTopInset = informationContentTopInset,
            elementHeight = informationElementHeight,
        ),
        week = agendaStickyFieldViewport(
            signals = signals.weeks,
            visibleItemsByKey = visibleItemsByKey,
            firstVisibleItemIndex = firstVisibleItemIndex,
            lastVisibleItemIndex = lastVisibleItemIndex,
            viewportStartOffset = viewportStartOffset,
            contentTopInset = informationContentTopInset,
            elementHeight = informationElementHeight,
        ),
    )
}

internal data class AgendaBoundaryChanges(
    val monthChanged: Boolean,
    val yearChanged: Boolean,
    val weekChanged: Boolean,
) {
    val hasAnyChange: Boolean
        get() = monthChanged || yearChanged || weekChanged

    fun hasVisibleChange(showCalendarWeeks: Boolean): Boolean =
        monthChanged || yearChanged || (showCalendarWeeks && weekChanged)
}

internal fun agendaBoundaryChanges(
    previousDate: LocalDate?,
    date: LocalDate,
    firstDayOfWeek: DayOfWeek,
): AgendaBoundaryChanges = AgendaBoundaryChanges(
    monthChanged = previousDate == null || YearMonth.from(previousDate) != YearMonth.from(date),
    yearChanged = previousDate == null || previousDate.year != date.year,
    weekChanged = previousDate == null ||
        previousDate.startOfCalendarWeek(firstDayOfWeek) != date.startOfCalendarWeek(firstDayOfWeek),
)

@Composable
internal fun CalendarWeekNumberPill(
    weekNumber: Int,
    modifier: Modifier = Modifier,
    muted: Boolean = false,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    Box(
        modifier = modifier
            .width(30.dp)
            .height(22.dp)
            .alpha(if (muted) 0.58f else 1f)
            .clip(RoundedCornerShape(11.dp))
            .background(backgroundColor),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = weekNumber.toString(),
            color = WarmInk,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
internal fun CalendarWeekLabelSpacer(
    weekNumber: Int,
    modifier: Modifier = Modifier,
) {
    Text(
        text = stringResource(R.string.calendar_week_label, weekNumber),
        modifier = modifier,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
    )
}
