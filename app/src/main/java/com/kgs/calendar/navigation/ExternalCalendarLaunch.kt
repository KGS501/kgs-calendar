package com.kgs.calendar.navigation

import java.net.URI
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal fun externalCalendarLaunchDate(
    action: String?,
    uriValue: String?,
    beginTimeMillis: Long?,
    zoneId: ZoneId = ZoneId.systemDefault(),
): LocalDate? {
    if (action != "android.intent.action.VIEW") return null
    val uriMillis = runCatching {
        val uri = URI(uriValue ?: return@runCatching null)
        if (uri.scheme != "content" || uri.host != "com.android.calendar") return@runCatching null
        val pathSegments = uri.path.orEmpty().split('/').filter(String::isNotBlank)
        pathSegments.lastOrNull()?.toLongOrNull().takeIf { pathSegments.firstOrNull() == "time" }
    }.getOrNull()
    val millis = uriMillis ?: beginTimeMillis ?: return null
    return Instant.ofEpochMilli(millis).atZone(zoneId).toLocalDate()
}
