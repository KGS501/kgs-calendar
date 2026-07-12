package com.kgs.calendar.data

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
