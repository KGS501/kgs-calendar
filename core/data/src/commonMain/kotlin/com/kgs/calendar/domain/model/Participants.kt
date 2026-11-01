package com.kgs.calendar.domain.model

import org.json.JSONArray
import org.json.JSONObject

/** ORGANIZER of an event as stored in `EventEntity.organizerJson`. */
data class Organizer(
    val email: String,
    val name: String? = null,
    val sentBy: String? = null,
    val directory: String? = null,
    val language: String? = null,
)

/** One ATTENDEE of an event as stored in `EventEntity.attendeesJson`. Values are raw iCalendar text. */
data class Attendee(
    val email: String,
    val name: String? = null,
    val partstat: String? = null,
    val role: String? = null,
    val rsvp: String? = null,
    val calendarUserType: String? = null,
    val member: String? = null,
    val delegatedTo: String? = null,
    val delegatedFrom: String? = null,
    val sentBy: String? = null,
    val directory: String? = null,
    val language: String? = null,
    val scheduleAgent: String? = null,
    val scheduleForceSend: String? = null,
    val scheduleStatus: String? = null,
)

/**
 * JSON codec for the organizer/attendee columns. Objects are flat string maps; absent fields are
 * written as explicit nulls, and missing keys, JSON nulls and malformed input all decode as absent.
 */
object ParticipantJson {
    fun encodeOrganizer(organizer: Organizer): String =
        JSONObject()
            .putValue("name", organizer.name)
            .putValue("email", organizer.email)
            .putValue("sentBy", organizer.sentBy)
            .putValue("directory", organizer.directory)
            .putValue("language", organizer.language)
            .toString()

    /** Null for blank or malformed input. The email may be blank. */
    fun decodeOrganizer(json: String?): Organizer? {
        val obj = json.parseOrNull { JSONObject(it) } ?: return null
        return Organizer(
            email = obj.stringOrNull("email").orEmpty(),
            name = obj.stringOrNull("name"),
            sentBy = obj.stringOrNull("sentBy"),
            directory = obj.stringOrNull("directory"),
            language = obj.stringOrNull("language"),
        )
    }

    /** Null for an empty list, matching an event without attendees. */
    fun encodeAttendees(attendees: List<Attendee>): String? {
        if (attendees.isEmpty()) return null
        val array = JSONArray()
        attendees.forEach { attendee ->
            array.put(
                JSONObject()
                    .putValue("name", attendee.name)
                    .putValue("email", attendee.email)
                    .putValue("partstat", attendee.partstat)
                    .putValue("role", attendee.role)
                    .putValue("rsvp", attendee.rsvp)
                    .putValue("calendarUserType", attendee.calendarUserType)
                    .putValue("member", attendee.member)
                    .putValue("delegatedTo", attendee.delegatedTo)
                    .putValue("delegatedFrom", attendee.delegatedFrom)
                    .putValue("sentBy", attendee.sentBy)
                    .putValue("directory", attendee.directory)
                    .putValue("language", attendee.language)
                    .putValue("scheduleAgent", attendee.scheduleAgent)
                    .putValue("scheduleForceSend", attendee.scheduleForceSend)
                    .putValue("scheduleStatus", attendee.scheduleStatus),
            )
        }
        return array.toString()
    }

    /**
     * Attendees with a non-blank email, in stored order. Accepts an array of objects or a single
     * object; other array entries are skipped.
     */
    fun decodeAttendees(json: String?): List<Attendee> {
        val objects = json.parseOrNull { text ->
            if (text.trimStart().startsWith("{")) {
                listOf(JSONObject(text))
            } else {
                val array = JSONArray(text)
                (0 until array.length()).mapNotNull { array.optJSONObject(it) }
            }
        } ?: return emptyList()
        return objects.mapNotNull { obj ->
            val email = obj.stringOrNull("email")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Attendee(
                email = email,
                name = obj.stringOrNull("name"),
                partstat = obj.stringOrNull("partstat"),
                role = obj.stringOrNull("role"),
                rsvp = obj.stringOrNull("rsvp"),
                calendarUserType = obj.stringOrNull("calendarUserType"),
                member = obj.stringOrNull("member"),
                delegatedTo = obj.stringOrNull("delegatedTo"),
                delegatedFrom = obj.stringOrNull("delegatedFrom"),
                sentBy = obj.stringOrNull("sentBy"),
                directory = obj.stringOrNull("directory"),
                language = obj.stringOrNull("language"),
                scheduleAgent = obj.stringOrNull("scheduleAgent"),
                scheduleForceSend = obj.stringOrNull("scheduleForceSend"),
                scheduleStatus = obj.stringOrNull("scheduleStatus"),
            )
        }
    }

    /**
     * Sets PARTSTAT for every attendee whose email matches one of [attendeeEmails], keeping all
     * other stored fields. Null when the status is not a reply status or nothing changed.
     */
    fun withPartstat(attendeesJson: String?, attendeeEmails: List<String>, partstat: String): String? {
        val normalizedPartstat = partstat.trim().uppercase()
        if (normalizedPartstat !in setOf("ACCEPTED", "DECLINED", "TENTATIVE", "NEEDS-ACTION")) return null
        val matches = attendeeEmails
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .toSet()
        if (matches.isEmpty()) return null
        val attendees = runCatching { JSONArray(attendeesJson.orEmpty()) }.getOrNull() ?: return null
        var changed = false
        repeat(attendees.length()) { index ->
            val obj = attendees.optJSONObject(index) ?: return@repeat
            val email = obj.stringOrNull("email").orEmpty().trim().lowercase()
            if (email in matches) {
                obj.put("partstat", normalizedPartstat)
                changed = true
            }
        }
        return attendees.takeIf { changed }?.toString()
    }

    private fun JSONObject.putValue(key: String, value: String?): JSONObject =
        put(key, value ?: JSONObject.NULL)

    // opt() instead of optString(): Android's optString() turns a JSON null into "null".
    private fun JSONObject.stringOrNull(key: String): String? =
        when (val value = opt(key)) {
            null, JSONObject.NULL -> null
            is String -> value
            else -> value.toString()
        }

    private inline fun <T> String?.parseOrNull(parse: (String) -> T): T? {
        if (isNullOrBlank()) return null
        return runCatching { parse(this) }.getOrNull()
    }
}
