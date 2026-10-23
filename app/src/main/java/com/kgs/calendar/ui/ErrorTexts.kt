package com.kgs.calendar.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.kgs.calendar.R

/**
 * Sync and save errors are recorded as technical English text, such as `PUT … failed: HTTP 403`.
 * This turns the common ones into a sentence in the app's language; anything else stays as it is.
 */
@Composable
internal fun friendlyErrorText(raw: String): String {
    // The problem's title names the source already.
    val text = raw.removePrefix(SourcePrefix.find(raw)?.value.orEmpty()).trim()
    val lower = text.lowercase()
    val status = HttpStatus.find(text)?.groupValues?.get(1)?.toIntOrNull()
    return when {
        text in GenericFallbacks -> stringResource(R.string.error_generic)
        "dns lookup" in lower || "unable to resolve host" in lower || "failed to connect" in lower ||
            "connection refused" in lower || "connection reset" in lower || "network is unreachable" in lower ->
            stringResource(R.string.error_unreachable)
        "timeout" in lower || "timed out" in lower -> stringResource(R.string.error_timeout)
        "credentials" in lower && "missing" in lower -> stringResource(R.string.error_credentials_missing)
        "returned a web page" in lower -> stringResource(R.string.error_feed_web_page)
        "invalid icalendar" in lower -> stringResource(R.string.error_feed_invalid)
        status != null -> when (status) {
            401 -> stringResource(R.string.error_http_login, status)
            403 -> stringResource(R.string.error_http_forbidden, status)
            404, 410 -> stringResource(R.string.error_http_not_found, status)
            413 -> stringResource(R.string.error_http_too_large, status)
            423 -> stringResource(R.string.error_http_locked, status)
            429 -> stringResource(R.string.error_http_rate_limited, status)
            507 -> stringResource(R.string.error_http_storage_full, status)
            in 500..599 -> stringResource(R.string.error_http_server, status)
            else -> text
        }
        else -> text
    }
}

private val SourcePrefix = Regex("^Source \"[^\"]*\":\\s*")
private val HttpStatus = Regex("HTTP (\\d{3})\\b")
private val GenericFallbacks = setOf(
    "Could not save changes.",
    "Could not update calendar settings.",
    "Could not update notifications.",
    "Something went wrong.",
)
