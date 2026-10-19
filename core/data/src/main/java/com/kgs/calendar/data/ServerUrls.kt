package com.kgs.calendar.data

// Kept apart from CalendarRepositoryHelpers.kt, whose Android color constants cannot load in plain
// JVM tests of the HTTP clients.

/** Trims the entered server address and defaults it to HTTPS when it has no scheme. */
internal fun normalizeServer(serverUrl: String): String {
    val trimmed = serverUrl.trim().trimEnd('/')
    return when {
        trimmed.startsWith("http://", ignoreCase = true) -> trimmed
        trimmed.startsWith("https://", ignoreCase = true) -> trimmed
        else -> "https://$trimmed"
    }
}
