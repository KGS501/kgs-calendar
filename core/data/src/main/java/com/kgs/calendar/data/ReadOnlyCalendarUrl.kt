package com.kgs.calendar.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal fun normalizeReadOnlyCalendarUrl(value: String): String {
    val trimmed = value.trim()
    val httpValue = if (trimmed.startsWith("webcal://", ignoreCase = true)) {
        "https://${trimmed.substringAfter("://")}"
    } else {
        trimmed
    }
    val parsed = httpValue.toHttpUrlOrNull()
    require(parsed != null && (parsed.scheme == "http" || parsed.scheme == "https")) {
        "Enter a valid http(s) or webcal URL."
    }
    return parsed.toString()
}

fun isSupportedReadOnlyCalendarUrl(value: String): Boolean =
    runCatching { normalizeReadOnlyCalendarUrl(value) }.isSuccess
