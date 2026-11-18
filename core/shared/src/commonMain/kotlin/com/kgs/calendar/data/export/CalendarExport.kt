package com.kgs.calendar.data.export

import com.kgs.calendar.platform.kgsPlatformName
import com.kgs.calendar.data.ical.IcalCodec
import com.kgs.calendar.data.ical.IcalPlatform
import com.kgs.calendar.data.ical.IcsEventReader
import com.kgs.calendar.data.ical.TimeZoneResolver
import com.kgs.calendar.data.local.KgsDatabase
import com.kgs.calendar.domain.model.MutationAction
import com.kgs.calendar.domain.source.isAndroidProviderCollection

/** What an export wrote. [mergedCopies] identical items from other calendars were written once. */
data class CalendarExportResult(val rawIcs: String, val items: Int, val mergedCopies: Int = 0)

/**
 * A single file can't hold what was asked for. One VCALENDAR has one namespace for UIDs and TZIDs, so
 * it would change what a restore gets. Thrown before anything is reported as exported; exporting the
 * calendars one at a time keeps them apart. [name] is the UID or TZID in question.
 */
class CalendarExportConflictException(val kind: Kind, val name: String) :
    IllegalStateException("$kind conflict on \"$name\": a single file can't keep these apart. Nothing was exported.") {
    enum class Kind { SharedUid, TimeZone }
}

/**
 * Writes calendars as one iCalendar file, e.g. to back up the local calendar before deleting it.
 * Stored resources are copied as they are, including this device's unsent changes and everything
 * the app doesn't show; items waiting for their delete upload are gone for the user and stay out.
 * Device calendars are written from their events.
 */
class CalendarExport internal constructor(
    private val database: KgsDatabase,
    private val icalCodec: IcalCodec,
) {
    suspend fun export(collectionHrefs: List<String>): CalendarExportResult {
        val collections = collectionHrefs.mapNotNull { database.collectionDao().get(it) }
        val queuedDeletes = database.pendingMutationDao().all()
            .filter { it.action == MutationAction.Delete }
            .mapTo(HashSet()) { it.resourceHref }
        val timezones = linkedMapOf<String, List<String>>()
        // The components of one resource stay together: a series is its master and its exceptions.
        val series = linkedMapOf<String, List<List<String>>>()
        var mergedCopies = 0
        collections.forEach { collection ->
            val resources = if (collection.isAndroidProviderCollection()) {
                database.eventDao().forCollection(collection.href).map { icalCodec.serializeEvent(it) }
            } else {
                database.resourceDao().forCollection(collection.href)
                    .filterNot { it.href in queuedDeletes }
                    .map { it.rawIcs }
            }
            val inCollection = linkedMapOf<String, MutableList<List<String>>>()
            resources.forEach { raw ->
                topLevelComponents(raw).forEach { block ->
                    when (block.first().uppercase()) {
                        "BEGIN:VTIMEZONE" -> addTimezone(block, timezones)
                        "BEGIN:VEVENT", "BEGIN:VTODO", "BEGIN:VJOURNAL" ->
                            inCollection.getOrPut(block.seriesKey()) { mutableListOf() } += block
                    }
                }
            }
            inCollection.forEach { (key, blocks) ->
                val existing = series[key]
                when {
                    existing == null -> series[key] = blocks
                    existing.unchangingBlocks() == blocks.unchangingBlocks() -> mergedCopies++
                    else -> throw CalendarExportConflictException(CalendarExportConflictException.Kind.SharedUid, key.substringAfter('|'))
                }
            }
        }
        val components = series.values.flatten()
        val name = collections.singleOrNull()?.displayName
        val lines = buildList {
            add("BEGIN:VCALENDAR")
            add("VERSION:2.0")
            add("PRODID:-//KGS Calendar//$kgsPlatformName//EN")
            add("CALSCALE:GREGORIAN")
            name?.takeIf { it.isNotBlank() }?.let { add("X-WR-CALNAME:${it.replace("\\", "\\\\").replace(",", "\\,").replace(";", "\\;")}") }
            timezones.values.forEach(::addAll)
            components.forEach(::addAll)
            add("END:VCALENDAR")
        }
        return CalendarExportResult(fold(lines), series.size, mergedCopies)
    }

    /**
     * Keeps one definition per TZID. Servers and clients write the same IANA zone in many trimmed
     * forms, so those are shared. Two custom zones that disagree on any offset can't be: every
     * reference to the name would then mean the first one.
     */
    private fun addTimezone(block: List<String>, timezones: MutableMap<String, List<String>>) {
        val tzid = block.firstOrNull { it.startsWith("TZID", ignoreCase = true) }
            ?.substringAfter(':')?.trim()
            ?: return
        val existing = timezones[tzid]
        if (existing == null) {
            timezones[tzid] = block
            return
        }
        if (existing == block || TimeZoneResolver.resolveKnownId(tzid) != null) return
        val first = IcalPlatform.vtimezoneOffsetsSeconds(existing.joinToString("\r\n"), TIMEZONE_SAMPLES_MILLIS)
        val second = IcalPlatform.vtimezoneOffsetsSeconds(block.joinToString("\r\n"), TIMEZONE_SAMPLES_MILLIS)
        if (first == null || first != second) {
            throw CalendarExportConflictException(CalendarExportConflictException.Kind.TimeZone, tzid)
        }
    }

    private fun List<String>.seriesKey(): String {
        val type = first().substringAfter(':').trim().uppercase()
        val uid = firstOrNull { it.startsWith("UID:", ignoreCase = true) || it.startsWith("UID;", ignoreCase = true) }
            ?.substringAfter(':')?.trim()
        return "$type|${uid ?: hashCode()}"
    }

    /** A component without the stamps every write renews, for telling copies from different items. */
    private fun List<List<String>>.unchangingBlocks(): List<List<String>> =
        map { block -> block.filterNot { it.startsWith("DTSTAMP", ignoreCase = true) || it.startsWith("LAST-MODIFIED", ignoreCase = true) } }

    /** The VCALENDAR's direct children, each as unfolded lines from BEGIN to END. */
    private fun topLevelComponents(raw: String): List<List<String>> {
        val lines = IcsEventReader.unfoldedLines(raw)
        val blocks = mutableListOf<List<String>>()
        var current: MutableList<String>? = null
        var depth = 0
        lines.forEach { line ->
            val upper = line.uppercase()
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
            while (remaining.encodeToByteArray().size > limit) {
                var split = remaining.length
                while (split > 1 && remaining.substring(0, split).encodeToByteArray().size > limit) split--
                if (split in 1 until remaining.length && remaining[split - 1].isHighSurrogate()) split--
                append(remaining, 0, split).append("\r\n ")
                remaining = remaining.substring(split)
                limit = 74
            }
            append(remaining).append("\r\n")
        }
    }

    private companion object {
        /** Twice a year from 1990 to 2050, so that both sides of a daylight saving rule are compared. */
        val TIMEZONE_SAMPLES_MILLIS: List<Long> = (0L until 120L).map { 631_152_000_000L + it * 15_778_800_000L }
    }
}
