package com.kgs.calendar.domain.event

import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.domain.model.EventClassification
import com.kgs.calendar.domain.model.EventStatus
import com.kgs.calendar.domain.model.EventTransparency
import com.kgs.calendar.domain.time.toDate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal val EventEntity.eventStatus: EventStatus?
    get() = EventStatus.from(status)

internal val EventEntity.eventClassification: EventClassification?
    get() = EventClassification.from(classification)

internal val EventEntity.eventTransparency: EventTransparency?
    get() = EventTransparency.from(transparency)

internal fun EventEntity.isTentative(): Boolean = eventStatus == EventStatus.Tentative

internal fun EventEntity.isCancelled(): Boolean = eventStatus == EventStatus.Cancelled

internal fun EventEntity.displayColor(): Int = manualColor ?: color

/**
 * Identity of one occurrence in a month layout. The app keys by resource href only; the widget
 * falls back to the UID when the href is blank ([uidFallbackForBlankHref]).
 */
internal fun EventEntity.monthOccurrenceKey(uidFallbackForBlankHref: Boolean = false): String {
    val resource = if (uidFallbackForBlankHref) resourceHref.ifBlank { uid } else resourceHref
    return "$resource:$startsAtMillis"
}

internal fun EventEntity.endDateInclusive(zoneId: ZoneId = ZoneId.systemDefault()): LocalDate =
    Instant.ofEpochMilli((endsAtMillis - 1).coerceAtLeast(startsAtMillis)).atZone(zoneId).toLocalDate()

internal fun EventEntity.occursOn(date: LocalDate, zoneId: ZoneId = ZoneId.systemDefault()): Boolean {
    val start = startsAtMillis.toDate(zoneId)
    val end = endDateInclusive(zoneId)
    return !date.isBefore(start) && !date.isAfter(end)
}

internal fun EventEntity.isTimedMultiDay(zoneId: ZoneId = ZoneId.systemDefault()): Boolean =
    !allDay && startsAtMillis.toDate(zoneId).isBefore(endDateInclusive(zoneId))

internal fun EventEntity.isTimedMultiDayMiddleOn(date: LocalDate, zoneId: ZoneId = ZoneId.systemDefault()): Boolean {
    if (!isTimedMultiDay(zoneId)) return false
    val start = startsAtMillis.toDate(zoneId)
    val end = endDateInclusive(zoneId)
    return date.isAfter(start) && date.isBefore(end)
}

/** All-day events on their dates, and the full middle days of timed multi-day events. */
internal fun EventEntity.isAllDayTopItemOn(date: LocalDate, zoneId: ZoneId = ZoneId.systemDefault()): Boolean =
    if (allDay) occursOn(date, zoneId) else isTimedMultiDayMiddleOn(date, zoneId)
