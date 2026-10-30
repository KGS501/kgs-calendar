package com.kgs.calendar.data.ical

import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.model.component.VTimeZone
import java.io.StringReader
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Maps the TZID values found in calendar data to Java zones. Besides IANA ids this understands
 * prefixed ids (`/mozilla.org/20050126_1/Europe/Berlin`), Windows zone names written by Outlook and
 * Exchange (`W. Europe Standard Time`), and as a last resort any zone whose `VTIMEZONE` definition
 * in the same calendar matches a known zone's offsets. Unknown ids resolve to null; callers decide
 * on a fallback.
 */
internal class TimeZoneResolver private constructor(
    private val definitions: Map<String, String>,
) {
    fun resolve(tzid: String?): ZoneId? {
        val id = tzid?.trim()?.removeSurrounding("\"")?.takeIf { it.isNotBlank() } ?: return null
        return resolveKnownId(id) ?: definitions[id.lowercase(Locale.ROOT)]?.let { matchDefinition(id, it) }
    }

    companion object {
        val Empty = TimeZoneResolver(emptyMap())

        /** A resolver that also knows the `VTIMEZONE` definitions of [rawIcs]. */
        fun forCalendar(rawIcs: String?): TimeZoneResolver {
            if (rawIcs.isNullOrBlank() || !rawIcs.contains("BEGIN:VTIMEZONE", ignoreCase = true)) return Empty
            return TimeZoneResolver(vtimezoneBlocks(rawIcs))
        }

        /** Ids that need no `VTIMEZONE`: IANA ids, prefixed IANA ids and Windows zone names. */
        fun resolveKnownId(id: String): ZoneId? {
            val trimmed = id.trim().removeSurrounding("\"")
            if (trimmed.isBlank()) return null
            zoneOrNull(trimmed)?.let { return it }
            if ('/' in trimmed) {
                val segments = trimmed.split('/').filter { it.isNotBlank() }
                for (count in minOf(3, segments.size) downTo 1) {
                    zoneOrNull(segments.takeLast(count).joinToString("/"))?.let { return it }
                }
            }
            return WindowsZones.ids[trimmed.lowercase(Locale.ROOT)]?.let(::zoneOrNull)
        }

        private fun zoneOrNull(id: String): ZoneId? {
            // ZoneId.of also accepts bare offsets like "+01:00", which are no zone a client could mean.
            if (id.firstOrNull()?.isLetter() != true) return null
            return runCatching { ZoneId.of(id) }.getOrNull()
        }

        private val definitionMatches = ConcurrentHashMap<String, ZoneIdHolder>()

        private class ZoneIdHolder(val zone: ZoneId?)

        private fun matchDefinition(tzid: String, block: String): ZoneId? =
            definitionMatches.getOrPut("$tzid\n$block") { ZoneIdHolder(findMatchingZone(tzid, block)) }.zone

        /**
         * Finds a zone with the same UTC offsets as [block] over the last, current and next year.
         * Among equal candidates one named in the TZID wins (Outlook ids list cities), then a zone in
         * a common region, so `(UTC+01:00) Amsterdam, Berlin, …` resolves to `Europe/Amsterdam`.
         */
        private fun findMatchingZone(tzid: String, block: String): ZoneId? {
            val definition = runCatching {
                val calendar = CalendarBuilder().build(
                    StringReader("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//KGS//TZ//EN\r\n$block\r\nEND:VCALENDAR\r\n"),
                )
                val component = calendar.getComponent<VTimeZone>("VTIMEZONE") ?: return null
                net.fortuna.ical4j.model.TimeZone(component)
            }.getOrNull() ?: return null
            val year = LocalDate.now(ZoneOffset.UTC).year
            val samples = (year - 1..year + 1).flatMap { y ->
                (1..12).flatMap { month ->
                    listOf(1, 15).map { day ->
                        LocalDate.of(y, month, day).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
                    }
                }
            }
            val expected = samples.map { definition.getOffset(it) / 1000 }
            val needle = tzid.lowercase(Locale.ROOT)
            return ZoneId.getAvailableZoneIds()
                .asSequence()
                .filter { '/' in it && !it.startsWith("Etc/") && !it.startsWith("SystemV/") }
                .mapNotNull(::zoneOrNull)
                .filter { zone ->
                    val rules = zone.rules
                    samples.indices.all { index ->
                        rules.getOffset(java.time.Instant.ofEpochMilli(samples[index])).totalSeconds == expected[index]
                    }
                }
                .sortedWith(
                    compareBy<ZoneId> { zone ->
                        val city = zone.id.substringAfterLast('/').replace('_', ' ').lowercase(Locale.ROOT)
                        if (city in needle) 0 else 1
                    }
                        .thenBy { zone -> PREFERRED_REGIONS.indexOfFirst { zone.id.startsWith(it) }.let { if (it < 0) PREFERRED_REGIONS.size else it } }
                        .thenBy { it.id },
                )
                .firstOrNull()
        }

        private val PREFERRED_REGIONS = listOf("Europe/", "America/", "Asia/", "Australia/", "Africa/", "Pacific/", "Atlantic/", "Indian/")

        private fun vtimezoneBlocks(rawIcs: String): Map<String, String> {
            val result = mutableMapOf<String, String>()
            var block: MutableList<String>? = null
            var depth = 0
            unfoldLines(rawIcs).forEach { line ->
                val upper = line.uppercase(Locale.ROOT)
                val current = block
                if (current == null) {
                    if (upper == "BEGIN:VTIMEZONE") {
                        block = mutableListOf(line)
                        depth = 1
                    }
                    return@forEach
                }
                current += line
                if (upper.startsWith("BEGIN:")) depth++
                if (upper.startsWith("END:")) depth--
                if (depth == 0) {
                    current.firstOrNull { it.substringBefore(':').substringBefore(';').equals("TZID", true) }
                        ?.substringAfter(':')?.trim()?.removeSurrounding("\"")
                        ?.takeIf { it.isNotBlank() }
                        ?.let { result[it.lowercase(Locale.ROOT)] = current.joinToString("\r\n") }
                    block = null
                }
            }
            return result
        }

        private fun unfoldLines(rawIcs: String): List<String> {
            val result = mutableListOf<String>()
            rawIcs.replace("\r\n", "\n").replace('\r', '\n').lines().forEach { line ->
                if ((line.startsWith(' ') || line.startsWith('\t')) && result.isNotEmpty()) {
                    result[result.lastIndex] += line.drop(1)
                } else if (line.isNotEmpty()) {
                    result += line
                }
            }
            return result
        }
    }
}

/** CLDR `windowsZones` defaults (territory 001), keyed by lower-case Windows zone name. */
internal object WindowsZones {
    val ids: Map<String, String> = mapOf(
        "Dateline Standard Time" to "Etc/GMT+12",
        "UTC-11" to "Etc/GMT+11",
        "Aleutian Standard Time" to "America/Adak",
        "Hawaiian Standard Time" to "Pacific/Honolulu",
        "Marquesas Standard Time" to "Pacific/Marquesas",
        "Alaskan Standard Time" to "America/Anchorage",
        "UTC-09" to "Etc/GMT+9",
        "Pacific Standard Time (Mexico)" to "America/Tijuana",
        "UTC-08" to "Etc/GMT+8",
        "Pacific Standard Time" to "America/Los_Angeles",
        "US Mountain Standard Time" to "America/Phoenix",
        "Mountain Standard Time (Mexico)" to "America/Mazatlan",
        "Mountain Standard Time" to "America/Denver",
        "Yukon Standard Time" to "America/Whitehorse",
        "Central America Standard Time" to "America/Guatemala",
        "Central Standard Time" to "America/Chicago",
        "Easter Island Standard Time" to "Pacific/Easter",
        "Central Standard Time (Mexico)" to "America/Mexico_City",
        "Canada Central Standard Time" to "America/Regina",
        "SA Pacific Standard Time" to "America/Bogota",
        "Eastern Standard Time (Mexico)" to "America/Cancun",
        "Eastern Standard Time" to "America/New_York",
        "Haiti Standard Time" to "America/Port-au-Prince",
        "Cuba Standard Time" to "America/Havana",
        "US Eastern Standard Time" to "America/Indiana/Indianapolis",
        "Turks And Caicos Standard Time" to "America/Grand_Turk",
        "Paraguay Standard Time" to "America/Asuncion",
        "Atlantic Standard Time" to "America/Halifax",
        "Venezuela Standard Time" to "America/Caracas",
        "Central Brazilian Standard Time" to "America/Cuiaba",
        "SA Western Standard Time" to "America/La_Paz",
        "Pacific SA Standard Time" to "America/Santiago",
        "Newfoundland Standard Time" to "America/St_Johns",
        "Tocantins Standard Time" to "America/Araguaina",
        "E. South America Standard Time" to "America/Sao_Paulo",
        "SA Eastern Standard Time" to "America/Cayenne",
        "Argentina Standard Time" to "America/Argentina/Buenos_Aires",
        "Greenland Standard Time" to "America/Godthab",
        "Montevideo Standard Time" to "America/Montevideo",
        "Magallanes Standard Time" to "America/Punta_Arenas",
        "Saint Pierre Standard Time" to "America/Miquelon",
        "Bahia Standard Time" to "America/Bahia",
        "UTC-02" to "Etc/GMT+2",
        "Mid-Atlantic Standard Time" to "Etc/GMT+2",
        "Azores Standard Time" to "Atlantic/Azores",
        "Cape Verde Standard Time" to "Atlantic/Cape_Verde",
        "UTC" to "Etc/UTC",
        "Coordinated Universal Time" to "Etc/UTC",
        "GMT Standard Time" to "Europe/London",
        "Greenwich Standard Time" to "Atlantic/Reykjavik",
        "Sao Tome Standard Time" to "Africa/Sao_Tome",
        "Morocco Standard Time" to "Africa/Casablanca",
        "W. Europe Standard Time" to "Europe/Berlin",
        "Central Europe Standard Time" to "Europe/Budapest",
        "Romance Standard Time" to "Europe/Paris",
        "Central European Standard Time" to "Europe/Warsaw",
        "W. Central Africa Standard Time" to "Africa/Lagos",
        "Jordan Standard Time" to "Asia/Amman",
        "GTB Standard Time" to "Europe/Bucharest",
        "Middle East Standard Time" to "Asia/Beirut",
        "Egypt Standard Time" to "Africa/Cairo",
        "E. Europe Standard Time" to "Europe/Chisinau",
        "Syria Standard Time" to "Asia/Damascus",
        "West Bank Standard Time" to "Asia/Hebron",
        "South Africa Standard Time" to "Africa/Johannesburg",
        "FLE Standard Time" to "Europe/Kiev",
        "Israel Standard Time" to "Asia/Jerusalem",
        "South Sudan Standard Time" to "Africa/Juba",
        "Kaliningrad Standard Time" to "Europe/Kaliningrad",
        "Sudan Standard Time" to "Africa/Khartoum",
        "Libya Standard Time" to "Africa/Tripoli",
        "Namibia Standard Time" to "Africa/Windhoek",
        "Arabic Standard Time" to "Asia/Baghdad",
        "Turkey Standard Time" to "Europe/Istanbul",
        "Arab Standard Time" to "Asia/Riyadh",
        "Belarus Standard Time" to "Europe/Minsk",
        "Russian Standard Time" to "Europe/Moscow",
        "E. Africa Standard Time" to "Africa/Nairobi",
        "Volgograd Standard Time" to "Europe/Volgograd",
        "Iran Standard Time" to "Asia/Tehran",
        "Arabian Standard Time" to "Asia/Dubai",
        "Astrakhan Standard Time" to "Europe/Astrakhan",
        "Azerbaijan Standard Time" to "Asia/Baku",
        "Russia Time Zone 3" to "Europe/Samara",
        "Mauritius Standard Time" to "Indian/Mauritius",
        "Saratov Standard Time" to "Europe/Saratov",
        "Georgian Standard Time" to "Asia/Tbilisi",
        "Caucasus Standard Time" to "Asia/Yerevan",
        "Afghanistan Standard Time" to "Asia/Kabul",
        "West Asia Standard Time" to "Asia/Tashkent",
        "Ekaterinburg Standard Time" to "Asia/Yekaterinburg",
        "Pakistan Standard Time" to "Asia/Karachi",
        "Qyzylorda Standard Time" to "Asia/Qyzylorda",
        "India Standard Time" to "Asia/Kolkata",
        "Sri Lanka Standard Time" to "Asia/Colombo",
        "Nepal Standard Time" to "Asia/Kathmandu",
        "Central Asia Standard Time" to "Asia/Almaty",
        "Bangladesh Standard Time" to "Asia/Dhaka",
        "Omsk Standard Time" to "Asia/Omsk",
        "Myanmar Standard Time" to "Asia/Yangon",
        "SE Asia Standard Time" to "Asia/Bangkok",
        "Altai Standard Time" to "Asia/Barnaul",
        "W. Mongolia Standard Time" to "Asia/Hovd",
        "North Asia Standard Time" to "Asia/Krasnoyarsk",
        "N. Central Asia Standard Time" to "Asia/Novosibirsk",
        "Tomsk Standard Time" to "Asia/Tomsk",
        "China Standard Time" to "Asia/Shanghai",
        "North Asia East Standard Time" to "Asia/Irkutsk",
        "Singapore Standard Time" to "Asia/Singapore",
        "W. Australia Standard Time" to "Australia/Perth",
        "Taipei Standard Time" to "Asia/Taipei",
        "Ulaanbaatar Standard Time" to "Asia/Ulaanbaatar",
        "Aus Central W. Standard Time" to "Australia/Eucla",
        "Transbaikal Standard Time" to "Asia/Chita",
        "Tokyo Standard Time" to "Asia/Tokyo",
        "North Korea Standard Time" to "Asia/Pyongyang",
        "Korea Standard Time" to "Asia/Seoul",
        "Yakutsk Standard Time" to "Asia/Yakutsk",
        "Cen. Australia Standard Time" to "Australia/Adelaide",
        "AUS Central Standard Time" to "Australia/Darwin",
        "E. Australia Standard Time" to "Australia/Brisbane",
        "AUS Eastern Standard Time" to "Australia/Sydney",
        "West Pacific Standard Time" to "Pacific/Port_Moresby",
        "Tasmania Standard Time" to "Australia/Hobart",
        "Vladivostok Standard Time" to "Asia/Vladivostok",
        "Lord Howe Standard Time" to "Australia/Lord_Howe",
        "Bougainville Standard Time" to "Pacific/Bougainville",
        "Russia Time Zone 10" to "Asia/Srednekolymsk",
        "Magadan Standard Time" to "Asia/Magadan",
        "Norfolk Standard Time" to "Pacific/Norfolk",
        "Sakhalin Standard Time" to "Asia/Sakhalin",
        "Central Pacific Standard Time" to "Pacific/Guadalcanal",
        "Russia Time Zone 11" to "Asia/Kamchatka",
        "New Zealand Standard Time" to "Pacific/Auckland",
        "UTC+12" to "Etc/GMT-12",
        "Fiji Standard Time" to "Pacific/Fiji",
        "Chatham Islands Standard Time" to "Pacific/Chatham",
        "UTC+13" to "Etc/GMT-13",
        "Tonga Standard Time" to "Pacific/Tongatapu",
        "Samoa Standard Time" to "Pacific/Apia",
        "Line Islands Standard Time" to "Pacific/Kiritimati",
    ).mapKeys { (name, _) -> name.lowercase(Locale.ROOT) }
}
