package com.kgs.calendar.navigation

import com.kgs.calendar.time.*
import kotlinx.datetime.*
import com.kgs.calendar.platform.parseUriStrict
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import com.kgs.calendar.time.ZoneId

fun externalCalendarLaunchDate(
    action: String?,
    uriValue: String?,
    beginTimeMillis: Long?,
    zoneId: ZoneId = ZoneId.systemDefault(),
): LocalDate? {
    if (action != "android.intent.action.VIEW") return null
    val uriMillis = runCatching {
        val uri = parseUriStrict(uriValue ?: return@runCatching null)
        if (uri.scheme != "content" || uri.host != "com.android.calendar") return@runCatching null
        val pathSegments = uri.path.split('/').filter(String::isNotBlank)
        pathSegments.lastOrNull()?.toLongOrNull().takeIf { pathSegments.firstOrNull() == "time" }
    }.getOrNull()
    val millis = uriMillis ?: beginTimeMillis ?: return null
    return Instant.ofEpochMilli(millis).atZone(zoneId).toLocalDate()
}
