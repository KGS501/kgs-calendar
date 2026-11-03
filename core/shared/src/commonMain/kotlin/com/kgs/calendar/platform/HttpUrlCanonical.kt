package com.kgs.calendar.platform

/**
 * The canonical form of an http(s) URL, or null when it isn't one. Android keeps OkHttp's form,
 * which stored ids were derived from.
 */
expect fun canonicalHttpUrlOrNull(url: String): String?

/** `foo.bar.example.co.uk` → `example.co.uk` (public suffix list); the host for IPs and unknown suffixes. */
expect fun registrableDomainOrHost(url: String): String?
