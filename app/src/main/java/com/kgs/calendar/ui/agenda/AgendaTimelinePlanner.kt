package com.kgs.calendar.ui.agenda

import com.kgs.calendar.domain.model.CalendarRange
import com.kgs.calendar.ui.AgendaBoundaryChanges
import com.kgs.calendar.ui.AgendaHeaderSignal
import com.kgs.calendar.ui.AgendaHeaderSignals
import com.kgs.calendar.ui.agendaBoundaryChanges
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * The semantic input to the Agenda module. Lazy-list positions are deliberately absent from this
 * interface: callers identify content by date and stable key, while the planner privately owns
 * row insertion and indexing.
 */
internal data class AgendaTimelineEntry<T>(
    val key: String,
    val date: LocalDate?,
    val sortMillis: Long?,
    val value: T,
)

internal sealed interface AgendaTimelineRow<out T> {
    val stableKey: String

    data class Boundary(
        val date: LocalDate,
        val changes: AgendaBoundaryChanges,
    ) : AgendaTimelineRow<Nothing> {
        override val stableKey: String = "agenda-boundary-$date"
    }

    /** A real chronological position for an explicitly requested date with no content. */
    data class DateAnchor(val date: LocalDate) : AgendaTimelineRow<Nothing> {
        override val stableKey: String = "agenda-date-anchor-$date"
    }

    data object PastFutureDivider : AgendaTimelineRow<Nothing> {
        override val stableKey: String = "agenda-past-future-divider"
    }

    data class Entry<T>(
        val entry: AgendaTimelineEntry<T>,
        val showDate: Boolean,
    ) : AgendaTimelineRow<T> {
        override val stableKey: String = entry.key
    }

    data object Footer : AgendaTimelineRow<Nothing> {
        override val stableKey: String = "agenda-timeline-footer"
    }
}

internal data class AgendaTimelineAnchor(
    val date: LocalDate,
    val rowKey: String,
    val rowIndex: Int,
)

internal data class AgendaTimelinePlan<T>(
    val rows: List<AgendaTimelineRow<T>>,
    val anchorsByDate: Map<LocalDate, AgendaTimelineAnchor>,
    val headerSignals: AgendaHeaderSignals,
)

internal object AgendaTimelinePlanner {
    fun <T> plan(
        entries: List<AgendaTimelineEntry<T>>,
        today: LocalDate,
        firstDayOfWeek: DayOfWeek,
        showCalendarWeeks: Boolean,
        requiredAnchorDate: LocalDate? = null,
    ): AgendaTimelinePlan<T> {
        val sortedEntries = entries.withIndex().sortedWith(
            compareBy<IndexedValue<AgendaTimelineEntry<T>>> { it.value.sortMillis == null }
                .thenBy { it.value.sortMillis ?: Long.MAX_VALUE }
                .thenBy { it.index },
        ).map(IndexedValue<AgendaTimelineEntry<T>>::value)
        val datedGroups = linkedMapOf<LocalDate, MutableList<AgendaTimelineEntry<T>>>()
        val undatedEntries = mutableListOf<AgendaTimelineEntry<T>>()
        sortedEntries.forEach { entry ->
            entry.date?.let { date -> datedGroups.getOrPut(date) { mutableListOf() }.add(entry) }
                ?: undatedEntries.add(entry)
        }
        requiredAnchorDate?.let { datedGroups.putIfAbsent(it, mutableListOf()) }

        val orderedDates = datedGroups.keys.sorted()
        val rows = mutableListOf<AgendaTimelineRow<T>>()
        val anchors = linkedMapOf<LocalDate, AgendaTimelineAnchor>()
        var previousDate: LocalDate? = null
        var dividerInserted = false

        orderedDates.forEach { date ->
            val changes = agendaBoundaryChanges(previousDate, date, firstDayOfWeek)
            if (changes.hasVisibleChange(showCalendarWeeks)) {
                rows += AgendaTimelineRow.Boundary(date, changes)
            }
            if (!dividerInserted && !date.isBefore(today)) {
                rows += AgendaTimelineRow.PastFutureDivider
                dividerInserted = true
            }

            val groupEntries = datedGroups.getValue(date)
            if (groupEntries.isEmpty()) {
                val anchorRow = AgendaTimelineRow.DateAnchor(date)
                val index = rows.size
                rows += anchorRow
                anchors[date] = AgendaTimelineAnchor(date, anchorRow.stableKey, index)
            } else {
                groupEntries.forEachIndexed { itemIndex, entry ->
                    val row = AgendaTimelineRow.Entry(entry, showDate = itemIndex == 0)
                    val index = rows.size
                    rows += row
                    if (itemIndex == 0) {
                        anchors[date] = AgendaTimelineAnchor(date, row.stableKey, index)
                    }
                }
            }
            previousDate = date
        }

        undatedEntries.forEach { entry ->
            rows += AgendaTimelineRow.Entry(entry, showDate = false)
        }
        rows += AgendaTimelineRow.Footer

        return AgendaTimelinePlan(
            rows = rows,
            anchorsByDate = anchors,
            headerSignals = buildHeaderSignals(rows),
        )
    }

    private fun <T> buildHeaderSignals(rows: List<AgendaTimelineRow<T>>): AgendaHeaderSignals {
        val days = mutableListOf<AgendaHeaderSignal>()
        val months = mutableListOf<AgendaHeaderSignal>()
        val years = mutableListOf<AgendaHeaderSignal>()
        val weeks = mutableListOf<AgendaHeaderSignal>()
        rows.forEachIndexed { index, row ->
            when (row) {
                is AgendaTimelineRow.Boundary -> {
                    val signal = AgendaHeaderSignal(index, row.stableKey, row.date)
                    if (row.changes.monthChanged) months += signal
                    if (row.changes.yearChanged) years += signal
                    if (row.changes.weekChanged) weeks += signal
                }
                is AgendaTimelineRow.DateAnchor -> {
                    days += AgendaHeaderSignal(index, row.stableKey, row.date)
                }
                is AgendaTimelineRow.Entry -> if (row.showDate) {
                    row.entry.date?.let { date ->
                        days += AgendaHeaderSignal(index, row.stableKey, date)
                    }
                }
                AgendaTimelineRow.Footer,
                AgendaTimelineRow.PastFutureDivider,
                -> Unit
            }
        }
        return AgendaHeaderSignals(days, months, years, weeks)
    }
}

internal data class AgendaNavigationRequest(
    val id: Long,
    val date: LocalDate,
)

internal data class AgendaNavigationSnapshot<T>(
    val loadedRange: CalendarRange,
    val requiredAnchorDate: LocalDate?,
    val plan: AgendaTimelinePlan<T>,
)

internal data class AgendaNavigationPlacement(
    val requestId: Long,
    val date: LocalDate,
    val rowKey: String,
    val rowIndex: Int,
)

internal enum class AgendaNavigationMotion {
    Instant,
    AnimateForward,
    AnimateBackward,
}

internal data class AgendaNavigationTravel(
    val distancePx: Int,
    val departureDurationMillis: Int,
    val arrivalDurationMillis: Int,
)

internal fun agendaNavigationTravel(
    viewportHeightPx: Int,
    minimumDistancePx: Int,
): AgendaNavigationTravel = AgendaNavigationTravel(
    distancePx = maxOf(
        minimumDistancePx.coerceAtLeast(1),
        (viewportHeightPx.coerceAtLeast(1) * AGENDA_NAVIGATION_VIEWPORT_FRACTION).toInt(),
    ),
    departureDurationMillis = 190,
    arrivalDurationMillis = 190,
)

private const val AGENDA_NAVIGATION_VIEWPORT_FRACTION = 0.65f

internal fun agendaNavigationMotion(
    lastHandledRequestId: Long,
    currentVisibleDate: LocalDate?,
    targetDate: LocalDate,
): AgendaNavigationMotion = when {
    lastHandledRequestId == Long.MIN_VALUE -> AgendaNavigationMotion.Instant
    currentVisibleDate == null -> AgendaNavigationMotion.Instant
    targetDate.isAfter(currentVisibleDate) -> AgendaNavigationMotion.AnimateForward
    targetDate.isBefore(currentVisibleDate) -> AgendaNavigationMotion.AnimateBackward
    else -> AgendaNavigationMotion.Instant
}

internal fun <T> resolveAgendaNavigation(
    request: AgendaNavigationRequest,
    lastHandledRequestId: Long,
    snapshot: AgendaNavigationSnapshot<T>,
): AgendaNavigationPlacement? {
    if (request.id <= lastHandledRequestId) return null
    if (snapshot.requiredAnchorDate != request.date) return null
    if (request.date.isBefore(snapshot.loadedRange.startDate)) return null
    if (!request.date.isBefore(snapshot.loadedRange.endExclusiveDate)) return null
    val anchor = snapshot.plan.anchorsByDate[request.date] ?: return null
    return AgendaNavigationPlacement(request.id, request.date, anchor.rowKey, anchor.rowIndex)
}
