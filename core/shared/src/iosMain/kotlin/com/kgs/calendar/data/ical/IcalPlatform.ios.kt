package com.kgs.calendar.data.ical

import com.kgs.calendar.time.ZoneId
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDate
import platform.Foundation.NSTimeZone
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.timeZoneWithName
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

internal actual object IcalPlatform {
    actual fun validateCalendar(text: String) = CommonIcal.validateCalendar(text)

    actual fun vtimezoneOffsetsSeconds(block: String, samplesMillis: List<Long>): List<Int>? =
        CommonIcal.vtimezoneOffsetsSeconds(block, samplesMillis)

    /**
     * A VTIMEZONE built from the system's rules: the transitions of the next two years as
     * STANDARD/DAYLIGHT observances with explicit RDATEs (one per transition), which every
     * client reads and which the codec trims to the item's range anyway.
     */
    @OptIn(ExperimentalForeignApi::class)
    actual fun vtimezoneText(zoneId: String): String? {
        val zone = NSTimeZone.timeZoneWithName(zoneId) ?: return null
        val nowSeconds = NSDate().timeIntervalSince1970
        val start = NSDate.dateWithTimeIntervalSince1970(nowSeconds - 400.0 * 86_400)
        val transitions = mutableListOf<NSDate>()
        var cursor: NSDate = start
        while (transitions.size < 8) {
            val next = zone.nextDaylightSavingTimeTransitionAfterDate(cursor) ?: break
            if (next.timeIntervalSince1970 > nowSeconds + 800.0 * 86_400) break
            transitions += next
            cursor = next
        }
        fun offsetText(seconds: Long): String {
            val sign = if (seconds < 0) "-" else "+"
            val abs = kotlin.math.abs(seconds)
            return sign + (abs / 3600).toString().padStart(2, '0') + ((abs % 3600) / 60).toString().padStart(2, '0')
        }
        fun localStamp(epochSeconds: Long, offsetSeconds: Long): String {
            val local = Instant.fromEpochSeconds(epochSeconds).toLocalDateTime(UtcOffset(seconds = offsetSeconds.toInt()).asTimeZoneCompat())
            return "${local.year.toString().padStart(4, '0')}${local.month.ordinal.plus(1).toString().padStart(2, '0')}${local.day.toString().padStart(2, '0')}" +
                "T${local.hour.toString().padStart(2, '0')}${local.minute.toString().padStart(2, '0')}${local.second.toString().padStart(2, '0')}"
        }
        val lines = mutableListOf("BEGIN:VTIMEZONE", "TZID:$zoneId")
        if (transitions.isEmpty()) {
            val offset = zone.secondsFromGMTForDate(start)
            lines += listOf("BEGIN:STANDARD", "DTSTART:19700101T000000", "TZOFFSETFROM:${offsetText(offset)}", "TZOFFSETTO:${offsetText(offset)}", "END:STANDARD")
        } else {
            for (transition in transitions) {
                val at = transition.timeIntervalSince1970.toLong()
                val before = zone.secondsFromGMTForDate(NSDate.dateWithTimeIntervalSince1970(at - 1.0))
                val after = zone.secondsFromGMTForDate(transition)
                val kind = if (zone.isDaylightSavingTimeForDate(transition)) "DAYLIGHT" else "STANDARD"
                lines += listOf(
                    "BEGIN:$kind",
                    "DTSTART:${localStamp(at, before)}",
                    "TZOFFSETFROM:${offsetText(before)}",
                    "TZOFFSETTO:${offsetText(after)}",
                    "END:$kind",
                )
            }
        }
        lines += "END:VTIMEZONE"
        return lines.joinToString("\r\n")
    }

    actual fun recurrenceStarts(
        rule: String,
        allDay: Boolean,
        seedMillis: Long,
        zone: ZoneId,
        searchStartMillis: Long,
        rangeEndMillis: Long,
    ): List<Long>? = CommonIcal.recurrenceStarts(rule, allDay, seedMillis, zone, searchStartMillis, rangeEndMillis)
}

private fun UtcOffset.asTimeZoneCompat(): TimeZone = kotlinx.datetime.FixedOffsetTimeZone(this)
