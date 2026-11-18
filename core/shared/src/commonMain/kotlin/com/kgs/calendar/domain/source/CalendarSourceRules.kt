package com.kgs.calendar.domain.source

import com.kgs.calendar.data.LOCAL_COLLECTION_PREFIX
import com.kgs.calendar.data.LOCAL_ACCOUNT_ID
import com.kgs.calendar.data.READ_ONLY_PREFIX
import com.kgs.calendar.data.local.entity.AccountEntity
import com.kgs.calendar.data.local.entity.CollectionEntity
import com.kgs.calendar.data.provider.AndroidCalendarProviderClient
import com.kgs.calendar.domain.model.SourceType
import com.kgs.calendar.json.JSONObject

fun String.isLocalCollectionHref(): Boolean = startsWith(LOCAL_COLLECTION_PREFIX)

fun AccountEntity.isLocalAccount(): Boolean = id == LOCAL_ACCOUNT_ID

fun String.isReadOnlyCollectionHref(): Boolean = startsWith(READ_ONLY_PREFIX)

fun CollectionEntity.isReadOnlyCollection(): Boolean = readOnly || href.isReadOnlyCollectionHref()

fun CollectionEntity.isAndroidProviderCollection(): Boolean =
    sourceType == SourceType.AndroidProvider || href.startsWith(AndroidCalendarProviderClient.ANDROID_CALENDAR_PREFIX)

fun AccountEntity.isAndroidProviderAccount(): Boolean =
    sourceType == SourceType.AndroidProvider || id == AndroidCalendarProviderClient.ANDROID_ACCOUNT_ID

/**
 * The read-only address others can subscribe to this calendar at: a feed's own URL, or the address a
 * CalDAV server publishes it at. Null when it has none; publishing stays the server's business.
 */
fun CollectionEntity.publicCalendarUrl(): String? {
    val url = when (sourceType) {
        SourceType.ReadOnlyUrl -> externalId
        SourceType.CalDav -> capabilitiesJson
            ?.let { runCatching { JSONObject(it).optString("publicUrl") }.getOrNull() }
        else -> null
    }
    return url?.takeIf { PUBLIC_URL_SCHEMES.any { scheme -> it.startsWith(scheme, ignoreCase = true) } }
}

private val PUBLIC_URL_SCHEMES = listOf("https://", "http://", "webcal://", "webcals://")
