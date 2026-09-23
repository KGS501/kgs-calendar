package com.kgs.calendar.data

import android.graphics.Color
import com.kgs.calendar.data.local.entity.AccountEntity
import com.kgs.calendar.data.local.entity.CollectionEntity
import com.kgs.calendar.data.local.entity.EventEntity
import org.json.JSONArray
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.UnknownHostException
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

internal fun normalizeServer(serverUrl: String): String {
    val trimmed = serverUrl.trim().trimEnd('/')
    return when {
        trimmed.startsWith("http://", ignoreCase = true) -> trimmed
        trimmed.startsWith("https://", ignoreCase = true) -> trimmed
        else -> "https://$trimmed"
    }
}

internal fun accountId(serverUrl: String, username: String): String =
    "account-" + UUID.nameUUIDFromBytes("$serverUrl\n$username".toByteArray(StandardCharsets.UTF_8)).toString()

internal fun String?.hasTimedIcalProperty(name: String): Boolean =
    this?.lineSequence()?.any { line ->
        (line.startsWith("$name:", ignoreCase = true) && line.substringAfter(':').contains('T')) ||
            (line.startsWith("$name;", ignoreCase = true) && line.substringAfter(':', "").contains('T'))
    } == true

internal fun String?.toMinutesList(): List<Int> =
    this?.split(',')?.mapNotNull { it.trim().toIntOrNull() }.orEmpty()

internal fun String.looksLikeHtmlResponse(contentType: String?): Boolean {
    if (contentType?.contains("text/html", ignoreCase = true) == true) return true
    val prefix = trimStart().take(256)
    return prefix.startsWith("<!DOCTYPE html", ignoreCase = true) ||
        prefix.startsWith("<html", ignoreCase = true)
}

internal fun String?.updateAttendeePartstat(attendeeEmails: List<String>, partstat: String): String? {
    val normalizedPartstat = partstat.trim().uppercase()
    if (normalizedPartstat !in setOf("ACCEPTED", "DECLINED", "TENTATIVE", "NEEDS-ACTION")) return null
    val matches = attendeeEmails
        .map { it.trim().lowercase() }
        .filter { it.isNotBlank() }
        .toSet()
    if (matches.isEmpty()) return null
    val attendees = runCatching { JSONArray(this.orEmpty()) }.getOrNull() ?: return null
    var changed = false
    repeat(attendees.length()) { index ->
        val obj = attendees.optJSONObject(index) ?: return@repeat
        val email = obj.optString("email").trim().lowercase()
        if (email in matches) {
            obj.put("partstat", normalizedPartstat)
            changed = true
        }
    }
    return attendees.takeIf { changed }?.toString()
}

internal fun newUid(): String = "${UUID.randomUUID()}@kgs-calendar"

internal fun CollectionEntity.resolvedAutomaticColor(): Int =
    automaticColor ?: sourceColor ?: color

internal fun Throwable.isTransientReadOnlySyncFailure(): Boolean {
    if (findCause<IOException>() != null) return true
    val statusCode = message
        ?.substringAfter("URL returned HTTP ", "")
        ?.takeWhile(Char::isDigit)
        ?.toIntOrNull()
        ?: return false
    return statusCode == 408 || statusCode == 425 || statusCode == 429 || statusCode >= 500
}

internal inline fun <reified T : Throwable> Throwable.findCause(): T? {
    var current: Throwable? = this
    while (current != null) {
        if (current is T) return current
        current = current.cause
    }
    return null
}

internal const val READ_ONLY_USERNAME = "Read-only URL"
internal const val READ_ONLY_PREFIX = "readonly-"
internal const val LOCAL_ACCOUNT_ID = "local"
internal const val LOCAL_COLLECTION_PREFIX = "local://"

internal val DEFAULT_COLORS = listOf(
    Color.rgb(23, 107, 93),
    Color.rgb(26, 115, 232),
    Color.rgb(185, 81, 64),
    Color.rgb(120, 85, 190),
    Color.rgb(238, 147, 45),
)

internal fun String.isLocalCollectionHref(): Boolean =
    startsWith(LOCAL_COLLECTION_PREFIX)

internal fun String.normalizedIcsText(): String =
    replace("\r\n", "\n").replace('\r', '\n').trim()

/** Returns this RRULE with COUNT/UNTIL removed and a new UNTIL set just before [cutMillis]. */
internal fun String.withRecurrenceUntilBefore(cutMillis: Long, allDay: Boolean, zoneId: ZoneId): String {
    val kept = split(';')
        .filter { it.isNotBlank() }
        .filterNot {
            val key = it.substringBefore('=').trim().uppercase(Locale.US)
            key == "UNTIL" || key == "COUNT"
        }
    val untilValue = if (allDay) {
        Instant.ofEpochMilli(cutMillis).atZone(zoneId).toLocalDate().minusDays(1)
            .format(DateTimeFormatter.BASIC_ISO_DATE)
    } else {
        DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC)
            .format(Instant.ofEpochMilli(cutMillis - 1000L))
    }
    return (kept + "UNTIL=$untilValue").joinToString(";")
}

internal fun AccountEntity.describeSyncError(error: Throwable): String {
    val source = displayName?.takeIf { it.isNotBlank() } ?: username
    error.message?.takeIf { it.startsWith("Source \"$source\":") }?.let { return it }
    val unknownHost = error.findCause<UnknownHostException>()
    if (unknownHost != null) {
        val host = runCatching { URI(serverUrl).host }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: unknownHost.message
            ?: serverUrl
        return "Source \"$source\": DNS lookup for \"$host\" failed. Check the internet connection, Private DNS/VPN, and server address."
    }
    return "Source \"$source\": ${error.message ?: "Sync failed."}"
}

internal fun Long.toDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

internal fun EventEntity.endDateInclusive(): LocalDate =
    Instant.ofEpochMilli((endsAtMillis - 1).coerceAtLeast(startsAtMillis)).atZone(ZoneId.systemDefault()).toLocalDate()

internal fun CollectionEntity.newResourceHref(uid: String): String =
    href.trimEnd('/') + "/" + uid.calendarObjectPathSegment() + ".ics"

private fun String.calendarObjectPathSegment(): String =
    URLEncoder.encode(trim(), StandardCharsets.UTF_8.name())
        .replace("+", "%20")
        .ifBlank { UUID.randomUUID().toString() }
