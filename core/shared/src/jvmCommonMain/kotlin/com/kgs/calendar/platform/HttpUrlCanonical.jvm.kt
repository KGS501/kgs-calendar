package com.kgs.calendar.platform

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

actual fun canonicalHttpUrlOrNull(url: String): String? = url.toHttpUrlOrNull()?.toString()

actual fun registrableDomainOrHost(url: String): String? {
    val httpUrl = url.toHttpUrlOrNull() ?: return null
    return httpUrl.topPrivateDomain() ?: httpUrl.host
}
