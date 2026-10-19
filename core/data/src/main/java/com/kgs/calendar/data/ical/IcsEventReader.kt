package com.kgs.calendar.data.ical

import com.kgs.calendar.data.local.entity.EventEntity
import net.fortuna.ical4j.data.CalendarBuilder
import java.io.StringReader
import java.time.ZoneId
import java.util.Locale

data class IcsEvent(val event: EventEntity, val rawIcs: String)

/** Splits a calendar by UID, keeping each series with its exceptions and time zones. */
class IcsEventReader(private val codec: IcalCodec) {
    fun read(rawIcs: String, collectionHref: String = "", color: Int = 0): List<IcsEvent> {
        val lines = mutableListOf<String>()
        rawIcs.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n').lines().forEach { line ->
            if ((line.startsWith(' ') || line.startsWith('\t')) && lines.isNotEmpty()) {
                lines[lines.lastIndex] += line.drop(1)
            } else if (line.isNotBlank()) {
                lines += line
            }
        }
        require(lines.firstOrNull().equals("BEGIN:VCALENDAR", true) && lines.lastOrNull().equals("END:VCALENDAR", true))
        val stack = mutableListOf("VCALENDAR")
        val properties = mutableListOf<String>()
        val timezones = mutableListOf<List<String>>()
        val events = linkedMapOf<String, MutableList<List<String>>>()
        var block = mutableListOf<String>()
        for (line in lines.drop(1).dropLast(1)) {
            val upper = line.uppercase(Locale.ROOT)
            when {
                upper.startsWith("BEGIN:") -> {
                    require(upper != "BEGIN:VCALENDAR")
                    if (stack.size == 1) block = mutableListOf()
                    stack += upper.substringAfter(':')
                    block += line
                }
                upper.startsWith("END:") -> {
                    require(stack.size > 1 && stack.last() == upper.substringAfter(':'))
                    val name = stack.removeAt(stack.lastIndex)
                    block += line
                    if (stack.size == 1) {
                        when (name) {
                            "VTIMEZONE" -> timezones += block.toList()
                            "VEVENT" -> {
                                val uid = eventProperty(block, "UID")?.substringAfter(':')?.takeIf { it.isNotBlank() }
                                    ?: throw IllegalArgumentException("Event UID is missing")
                                events.getOrPut(uid) { mutableListOf() } += block.toList()
                            }
                            else -> throw IllegalArgumentException("Only event calendars can be imported")
                        }
                    }
                }
                stack.size == 1 -> {
                    // METHOD describes the incoming scheduling message, rather than the stored resource.
                    if (!upper.startsWith("METHOD:")) properties += line
                }
                else -> block += line
            }
        }
        require(stack.size == 1 && events.isNotEmpty())
        require(events.values.sumOf { it.size } <= MAX_EVENTS)
        try {
            CalendarBuilder().build(StringReader(lines.joinToString("\r\n", postfix = "\r\n")))
        } catch (error: Exception) {
            throw IllegalArgumentException("Invalid calendar data", error)
        }
        var totalResourceChars = 0
        return events.map { (uid, blocks) ->
            require(blocks.count { eventProperty(it, "RECURRENCE-ID") == null } == 1)
            val raw = (listOf("BEGIN:VCALENDAR") + properties + timezones.flatten() + blocks.flatten() + "END:VCALENDAR")
                .joinToString("\r\n", postfix = "\r\n")
            totalResourceChars += raw.length
            require(totalResourceChars <= 8 * 1024 * 1024)
            // Validate every occurrence, rather than allowing the codec to skip a malformed exception.
            blocks.forEach { eventBlock ->
                val parsed = codec.parse((listOf("BEGIN:VCALENDAR") + properties + timezones.flatten() + eventBlock + "END:VCALENDAR").joinToString("\r\n"), collectionHref, uid, color)?.event
                require(parsed != null && parsed.endsAtMillis > parsed.startsAtMillis)
                // An unknown TZID would otherwise fall back to the device zone and shift the event.
                parsed.timezoneId?.let { require(runCatching { ZoneId.of(it) }.isSuccess) { "Unsupported time zone" } }
            }
            val event = requireNotNull(codec.parse(raw, collectionHref, uid, color)?.event)
            IcsEvent(event, raw)
        }
    }

    private fun eventProperty(block: List<String>, property: String): String? {
        var depth = 0
        return block.firstOrNull { line ->
            when {
                line.startsWith("BEGIN:", true) -> { depth++; false }
                line.startsWith("END:", true) -> { depth--; false }
                else -> depth == 1 && line.substringBefore(':').substringBefore(';').equals(property, true)
            }
        }
    }

    companion object {
        const val MAX_EVENTS = 500
    }
}
