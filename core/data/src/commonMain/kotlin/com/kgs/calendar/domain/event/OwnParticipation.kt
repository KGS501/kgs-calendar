package com.kgs.calendar.domain.event

import com.kgs.calendar.data.local.entity.AccountEntity
import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.domain.model.ParticipantJson
import org.json.JSONObject
import java.util.Locale

/**
 * Addresses that stand for the user in an account's invitations: the calendar user addresses the
 * server reported, and the login when it is an e-mail address. Lower case, without `mailto:`.
 */
fun AccountEntity.ownCalendarAddresses(): Set<String> {
    val reported = runCatching {
        val addresses = JSONObject(capabilitiesJson.orEmpty()).optJSONArray("calendarUserAddresses")
        (0 until (addresses?.length() ?: 0)).map { addresses!!.getString(it) }
    }.getOrDefault(emptyList())
    return (reported + username)
        .map { it.trim().removePrefix("mailto:").removePrefix("MAILTO:").lowercase(Locale.ROOT) }
        .filter { '@' in it }
        .toSet()
}

/** Whether the user declined this event or occurrence as one of its attendees. */
fun EventEntity.isDeclinedBy(ownAddresses: Set<String>): Boolean {
    if (ownAddresses.isEmpty()) return false
    return ParticipantJson.decodeAttendees(attendeesJson).any { attendee ->
        attendee.email.trim().removePrefix("mailto:").lowercase(Locale.ROOT) in ownAddresses &&
            attendee.partstat.equals("DECLINED", ignoreCase = true)
    }
}

/** Cancelled events and events the user declined don't remind. */
fun EventEntity.remindsUser(ownAddresses: Set<String>): Boolean = !isCancelled() && !isDeclinedBy(ownAddresses)

/**
 * An invitation someone else organizes: edits change only the user's own copy, and the organizer's
 * next update replaces them. False when the user's addresses are unknown.
 */
fun EventEntity.isInvitationFromOthers(ownAddresses: Set<String>): Boolean {
    if (ownAddresses.isEmpty() || ParticipantJson.decodeAttendees(attendeesJson).isEmpty()) return false
    val organizer = ParticipantJson.decodeOrganizer(organizerJson)?.email
        ?.trim()?.removePrefix("mailto:")?.lowercase(Locale.ROOT)
        ?.takeIf { it.isNotBlank() }
        ?: return false
    return organizer !in ownAddresses
}
