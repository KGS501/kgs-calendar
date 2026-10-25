package com.kgs.calendar.domain.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.roundToInt

const val MINUTES_PER_DAY = 24 * 60

/**
 * Where an instant falls on [day]'s clock, in minutes after midnight, for placing items in a day
 * grid whose rows are labelled 00:00 to 23:00. This is clock time, not time elapsed since midnight:
 * on the 25-hour day when the clocks go back, 10:00 still sits on the 10:00 line instead of an
 * hour lower. Instants before the day give 0; instants after it give [MINUTES_PER_DAY].
 */
fun Long.clockMinuteOn(day: LocalDate, zoneId: ZoneId): Int {
    val local = Instant.ofEpochMilli(this).atZone(zoneId).toLocalDateTime()
    return when {
        local.toLocalDate().isBefore(day) -> 0
        local.toLocalDate().isAfter(day) -> MINUTES_PER_DAY
        else -> local.hour * 60 + local.minute
    }
}

/**
 * The clock minutes of [day] that the span from [startMillis] to [endMillis] covers; see
 * [clockMinuteOn]. The end is always after the start: in the hour that repeats when the clocks go
 * back, a later instant can read an earlier time, and the span then keeps its real length.
 */
fun clockMinutesOn(day: LocalDate, startMillis: Long, endMillis: Long, zoneId: ZoneId): Pair<Int, Int> {
    val start = startMillis.clockMinuteOn(day, zoneId)
    val end = endMillis.clockMinuteOn(day, zoneId)
    if (end > start) return start to end
    val elapsedMinutes = ((endMillis - startMillis) / 60_000.0).roundToInt()
    return start to (start + max(1, elapsedMinutes)).coerceAtMost(MINUTES_PER_DAY)
}
