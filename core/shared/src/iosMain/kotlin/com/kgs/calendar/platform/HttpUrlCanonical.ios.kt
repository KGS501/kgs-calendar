package com.kgs.calendar.platform

actual fun canonicalHttpUrlOrNull(url: String): String? = url.toHttpUrlOrNull()?.toString()

/** Second-level public suffixes common enough to matter for calendar feeds. */
private val MULTI_PART_SUFFIXES = setOf(
    "co.uk", "org.uk", "ac.uk", "gov.uk", "me.uk", "ltd.uk", "plc.uk", "net.uk",
    "com.au", "net.au", "org.au", "edu.au", "gov.au", "co.nz", "org.nz", "net.nz", "govt.nz",
    "co.jp", "ne.jp", "or.jp", "ac.jp", "go.jp", "co.kr", "or.kr", "com.br", "net.br", "org.br",
    "com.cn", "net.cn", "org.cn", "gov.cn", "com.tw", "org.tw", "com.hk", "org.hk", "com.sg",
    "com.mx", "com.ar", "com.tr", "co.za", "org.za", "co.in", "net.in", "org.in", "gv.at", "ac.at", "co.at", "or.at",
    "com.pl", "net.pl", "org.pl", "co.il", "org.il", "com.es", "com.ru", "co.id", "or.id", "com.my", "com.ph",
    "github.io", "gitlab.io", "blogspot.com", "herokuapp.com", "appspot.com", "netlify.app", "vercel.app", "pages.dev",
)

actual fun registrableDomainOrHost(url: String): String? {
    val host = url.toHttpUrlOrNull()?.host ?: return null
    if (host.all { it.isDigit() || it == '.' } || ':' in host) return host
    val labels = host.trimEnd('.').split('.')
    if (labels.size < 2) return host
    val lastTwo = labels.takeLast(2).joinToString(".")
    return when {
        lastTwo in MULTI_PART_SUFFIXES -> if (labels.size >= 3) labels.takeLast(3).joinToString(".") else host
        labels.last().length >= 2 -> lastTwo
        else -> host
    }
}
