package com.kgs.calendar.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Preserve subscription schemes in settings; translate them only at the HTTP boundary. */
internal fun normalizeReadOnlyCalendarUrl(value: String): String {
    val trimmed = value.trim()
    val scheme = trimmed.substringBefore("://").lowercase()
    val transportScheme = when (scheme) {
        "webcal" -> "http"
        "webcals" -> "https"
        else -> scheme
    }
    val parsed = "$transportScheme://${trimmed.substringAfter("://", "")}".toHttpUrlOrNull()
    require(parsed != null && scheme in setOf("http", "https", "webcal", "webcals")) {
        "Enter a valid http(s) or webcal(s) URL."
    }
    return if (scheme == "webcal" || scheme == "webcals") {
        "$scheme://${parsed.toString().substringAfter("://")}"
    } else {
        parsed.toString()
    }
}

internal fun readOnlyCalendarTransportUrl(value: String): String {
    val normalized = normalizeReadOnlyCalendarUrl(value)
    return when {
        normalized.startsWith("webcal://") -> "http://${normalized.substringAfter("://")}"
        normalized.startsWith("webcals://") -> "https://${normalized.substringAfter("://")}"
        else -> normalized
    }
}

fun isSupportedReadOnlyCalendarUrl(value: String): Boolean =
    runCatching { normalizeReadOnlyCalendarUrl(value) }.isSuccess
