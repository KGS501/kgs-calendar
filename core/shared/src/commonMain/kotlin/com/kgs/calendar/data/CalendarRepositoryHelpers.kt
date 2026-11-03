package com.kgs.calendar.data

import com.kgs.calendar.time.*
import kotlinx.datetime.*
import com.kgs.calendar.data.local.entity.AccountEntity
import com.kgs.calendar.data.local.entity.CollectionEntity
import com.kgs.calendar.platform.IOException
import com.kgs.calendar.platform.UnknownHostException
import com.kgs.calendar.platform.argbOf
import com.kgs.calendar.platform.encodePathSegment
import com.kgs.calendar.platform.nameUuidFromBytes
import com.kgs.calendar.platform.parseUri
import com.kgs.calendar.platform.randomUuidString
import kotlin.time.Instant
import com.kgs.calendar.time.ZoneId
import com.kgs.calendar.time.ZoneOffset
import com.kgs.calendar.time.DateTimeFormatter

internal fun accountId(serverUrl: String, username: String): String =
    "account-" + nameUuidFromBytes("$serverUrl\n$username".encodeToByteArray())

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

internal fun newUid(): String = "${randomUuidString()}@kgs-calendar"

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

inline fun <reified T : Throwable> Throwable.findCause(): T? {
    var current: Throwable? = this
    while (current != null) {
        if (current is T) return current
        current = current.cause
    }
    return null
}

internal const val READ_ONLY_USERNAME = "Read-only URL"
const val READ_ONLY_PREFIX = "readonly-"
internal const val LOCAL_ACCOUNT_ID = "local"
internal const val LOCAL_COLLECTION_PREFIX = "local://"

internal val DEFAULT_COLORS = listOf(
    argbOf(23, 107, 93),
    argbOf(26, 115, 232),
    argbOf(185, 81, 64),
    argbOf(120, 85, 190),
    argbOf(238, 147, 45),
)

internal fun String.normalizedIcsText(): String =
    replace("\r\n", "\n").replace('\r', '\n').trim()

/** Returns this RRULE with COUNT/UNTIL removed and a new UNTIL set just before [cutMillis]. */
internal fun String.withRecurrenceUntilBefore(cutMillis: Long, allDay: Boolean, zoneId: ZoneId): String {
    val kept = split(';')
        .filter { it.isNotBlank() }
        .filterNot {
            val key = it.substringBefore('=').trim().uppercase()
            key == "UNTIL" || key == "COUNT"
        }
    val untilValue = if (allDay) {
        Instant.ofEpochMilli(cutMillis).atZone(zoneId).toLocalDate().minusDays(1)
            .format(DateTimeFormatter.BASIC_ISO_DATE)
    } else {
        Instant.ofEpochMilli(cutMillis - 1000L).atZone(ZoneOffset.UTC)
            .format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"))
    }
    return (kept + "UNTIL=$untilValue").joinToString(";")
}

internal fun AccountEntity.describeSyncError(error: Throwable): String {
    val source = displayName?.takeIf { it.isNotBlank() } ?: username
    error.message?.takeIf { it.startsWith("Source \"$source\":") }?.let { return it }
    val unknownHost = error.findCause<UnknownHostException>()
    if (unknownHost != null) {
        val host = runCatching { parseUri(serverUrl).host }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: unknownHost.message
            ?: serverUrl
        return "Source \"$source\": DNS lookup for \"$host\" failed. Check the internet connection, Private DNS/VPN, and server address."
    }
    return "Source \"$source\": ${error.message ?: "Sync failed."}"
}

internal fun CollectionEntity.newResourceHref(uid: String): String =
    href.trimEnd('/') + "/" + uid.calendarObjectPathSegment() + ".ics"

private fun String.calendarObjectPathSegment(): String =
    encodePathSegment(trim())
        .ifBlank { randomUuidString() }
