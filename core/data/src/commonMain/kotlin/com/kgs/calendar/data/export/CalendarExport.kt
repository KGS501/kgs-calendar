package com.kgs.calendar.data.export

import com.kgs.calendar.data.ical.IcalCodec
import com.kgs.calendar.data.local.KgsDatabase
import com.kgs.calendar.domain.source.isAndroidProviderCollection
import java.util.Locale

/** What an export wrote. */
data class CalendarExportResult(val rawIcs: String, val items: Int)

/**
 * Writes calendars as one iCalendar file, e.g. to back up the local calendar before deleting it.
 * Stored resources are copied as they are, including this device's unsent changes and everything
 * the app doesn't show; device calendars are written from their events.
 */
class CalendarExport internal constructor(
    private val database: KgsDatabase,
    private val icalCodec: IcalCodec,
) {
    suspend fun export(collectionHrefs: List<String>): CalendarExportResult {
        val collections = collectionHrefs.mapNotNull { database.collectionDao().get(it) }
        val resources = collections.flatMap { collection ->
            if (collection.isAndroidProviderCollection()) {
                database.eventDao().forCollection(collection.href).map { icalCodec.serializeEvent(it) }
            } else {
                database.resourceDao().forCollection(collection.href).map { it.rawIcs }
            }
        }
        val timezones = linkedMapOf<String, List<String>>()
        val components = mutableListOf<List<String>>()
        resources.forEach { raw ->
            topLevelComponents(raw).forEach { block ->
                when (block.first().uppercase(Locale.ROOT)) {
                    "BEGIN:VTIMEZONE" -> {
                        val tzid = block.firstOrNull { it.startsWith("TZID", ignoreCase = true) }
                            ?.substringAfter(':')?.trim()?.lowercase(Locale.ROOT)
                        if (tzid != null) timezones.putIfAbsent(tzid, block)
                    }
                    "BEGIN:VEVENT", "BEGIN:VTODO", "BEGIN:VJOURNAL" -> components += block
                }
            }
        }
        val name = collections.singleOrNull()?.displayName
        val lines = buildList {
            add("BEGIN:VCALENDAR")
            add("VERSION:2.0")
            add("PRODID:-//KGS Calendar//Android//EN")
            add("CALSCALE:GREGORIAN")
            name?.takeIf { it.isNotBlank() }?.let { add("X-WR-CALNAME:${it.replace("\\", "\\\\").replace(",", "\\,").replace(";", "\\;")}") }
            timezones.values.forEach(::addAll)
            components.forEach(::addAll)
            add("END:VCALENDAR")
        }
        val uids = components.mapNotNull { block -> block.firstOrNull { it.startsWith("UID:", ignoreCase = true) } }.toSet()
        return CalendarExportResult(fold(lines), uids.size)
    }

    /** The VCALENDAR's direct children, each as unfolded lines from BEGIN to END. */
    private fun topLevelComponents(raw: String): List<List<String>> {
        val lines = mutableListOf<String>()
        raw.replace("\r\n", "\n").replace('\r', '\n').lines().forEach { line ->
            if ((line.startsWith(' ') || line.startsWith('\t')) && lines.isNotEmpty()) {
                lines[lines.lastIndex] += line.drop(1)
            } else if (line.isNotEmpty()) {
                lines += line
            }
        }
        val blocks = mutableListOf<List<String>>()
        var current: MutableList<String>? = null
        var depth = 0
        lines.forEach { line ->
            val upper = line.uppercase(Locale.ROOT)
            when {
                upper == "BEGIN:VCALENDAR" || upper == "END:VCALENDAR" -> Unit
                upper.startsWith("BEGIN:") -> {
                    if (depth == 0) current = mutableListOf()
                    current?.add(line)
                    depth++
                }
                upper.startsWith("END:") && depth > 0 -> {
                    current?.add(line)
                    depth--
                    if (depth == 0) current?.let { blocks += it.toList() }
                }
                depth > 0 -> current?.add(line)
            }
        }
        return blocks
    }

    /** Folds lines to 75 octets as RFC 5545 requires, without splitting UTF-8 characters. */
    private fun fold(lines: List<String>): String = buildString {
        lines.forEach { line ->
            var remaining = line
            var limit = 75
            while (remaining.toByteArray(Charsets.UTF_8).size > limit) {
                var split = remaining.length
                while (split > 1 && remaining.substring(0, split).toByteArray(Charsets.UTF_8).size > limit) split--
                if (split in 1 until remaining.length && Character.isHighSurrogate(remaining[split - 1])) split--
                append(remaining, 0, split).append("\r\n ")
                remaining = remaining.substring(split)
                limit = 74
            }
            append(remaining).append("\r\n")
        }
    }
}
