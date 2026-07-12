package com.kgs.calendar.data

import com.kgs.calendar.data.local.entity.CollectionEntity
import org.json.JSONArray
import java.io.IOException
import java.nio.charset.StandardCharsets
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
