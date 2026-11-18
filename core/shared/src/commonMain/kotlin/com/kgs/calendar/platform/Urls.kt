package com.kgs.calendar.platform

/**
 * URL helpers replacing java.net.URI, URLEncoder/URLDecoder and OkHttp's HttpUrl in code shared
 * with iOS.
 */
data class ParsedUri(
    val scheme: String?,
    /** userinfo@host:port, raw. */
    val authority: String?,
    val rawPath: String,
    val rawQuery: String?,
    val rawFragment: String?,
) {
    val isAbsolute: Boolean get() = scheme != null

    val host: String?
        get() = authority?.substringAfterLast('@')?.let { hostPort ->
            if (hostPort.startsWith("[")) hostPort.substringBefore(']') + "]" else hostPort.substringBefore(':')
        }?.takeIf { it.isNotEmpty() }

    /** The explicit port, or -1. */
    val port: Int
        get() {
            val hostPort = authority?.substringAfterLast('@') ?: return -1
            val afterHost = if (hostPort.startsWith("[")) hostPort.substringAfter(']', "") else hostPort
            val portText = if (afterHost.contains(':')) afterHost.substringAfterLast(':') else ""
            return portText.toIntOrNull() ?: -1
        }

    val userInfo: String? get() = authority?.takeIf { '@' in it }?.substringBeforeLast('@')

    /** The decoded path. */
    val path: String get() = percentDecode(rawPath)

    override fun toString(): String = buildString {
        if (scheme != null) append(scheme).append(':')
        if (authority != null) append("//").append(authority)
        append(rawPath)
        if (rawQuery != null) append('?').append(rawQuery)
        if (rawFragment != null) append('#').append(rawFragment)
    }

    /** RFC 3986 section 5.2 reference resolution. */
    fun resolve(reference: String): ParsedUri {
        val r = parseUri(reference)
        val (scheme, authority, path, query) = when {
            r.scheme != null -> Quad(r.scheme, r.authority, removeDotSegments(r.rawPath), r.rawQuery)
            r.authority != null -> Quad(this.scheme, r.authority, removeDotSegments(r.rawPath), r.rawQuery)
            r.rawPath.isEmpty() -> Quad(this.scheme, this.authority, this.rawPath, r.rawQuery ?: this.rawQuery)
            r.rawPath.startsWith("/") -> Quad(this.scheme, this.authority, removeDotSegments(r.rawPath), r.rawQuery)
            else -> {
                val merged = if (this.authority != null && this.rawPath.isEmpty()) {
                    "/" + r.rawPath
                } else {
                    val cut = this.rawPath.lastIndexOf('/')
                    if (cut >= 0) this.rawPath.substring(0, cut + 1) + r.rawPath else r.rawPath
                }
                Quad(this.scheme, this.authority, removeDotSegments(merged), r.rawQuery)
            }
        }
        return ParsedUri(scheme, authority, path, query, r.rawFragment)
    }

    private data class Quad(val a: String?, val b: String?, val c: String, val d: String?)
}

/** Parses any URI reference; never throws (an invalid reference becomes a path). */
fun parseUri(value: String): ParsedUri {
    // RFC 3986 appendix B.
    val match = Regex("^(([^:/?#]+):)?(//([^/?#]*))?([^?#]*)(\\?([^#]*))?(#(.*))?").find(value)!!
    val g = match.groupValues
    return ParsedUri(
        scheme = match.groups[2]?.value,
        authority = match.groups[4]?.value,
        rawPath = g[5],
        rawQuery = match.groups[7]?.value,
        rawFragment = match.groups[9]?.value,
    )
}

/** Like java.net.URI: throws [IllegalArgumentException] for text that is no valid URI reference. */
fun parseUriStrict(value: String): ParsedUri {
    require(value.none { it == ' ' || it.code < 0x20 || it == '"' || it == '<' || it == '>' || it == '\\' || it == '^' || it == '`' || it == '{' || it == '|' || it == '}' }) {
        "Illegal character in URI: $value"
    }
    require(!Regex("%(?![0-9A-Fa-f]{2})").containsMatchIn(value)) { "Malformed escape pair in URI: $value" }
    val parsed = parseUri(value)
    parsed.scheme?.let { require(Regex("[A-Za-z][A-Za-z0-9+.-]*").matches(it)) { "Illegal scheme in URI: $value" } }
    return parsed
}

/** Resolves [reference] against [base] (RFC 3986). */
fun resolveUri(base: String, reference: String): String = parseUri(base).resolve(reference).toString()

private fun removeDotSegments(path: String): String {
    if (!path.contains('.')) return path
    var input = path
    var output = ""
    fun dropLastSegment() {
        val cut = output.lastIndexOf('/')
        output = if (cut >= 0) output.substring(0, cut) else ""
    }
    while (input.isNotEmpty()) {
        when {
            input.startsWith("../") -> input = input.substring(3)
            input.startsWith("./") -> input = input.substring(2)
            input.startsWith("/./") -> input = input.substring(2)
            input == "/." -> input = "/"
            input.startsWith("/../") -> { input = input.substring(3); dropLastSegment() }
            input == "/.." -> { input = "/"; dropLastSegment() }
            input == "." || input == ".." -> input = ""
            else -> {
                val start = if (input.startsWith("/")) 1 else 0
                val next = input.indexOf('/', start).let { if (it < 0) input.length else it }
                output += input.substring(0, next)
                input = input.substring(next)
            }
        }
    }
    return output
}

/** Decodes %XX escapes as UTF-8; malformed escapes are left as they are. */
fun percentDecode(value: String, plusAsSpace: Boolean = false): String {
    if ('%' !in value && !(plusAsSpace && '+' in value)) return value
    val bytes = ArrayList<Byte>(value.length)
    var i = 0
    while (i < value.length) {
        val c = value[i]
        if (c == '%' && i + 2 < value.length) {
            val hex = value.substring(i + 1, i + 3).toIntOrNull(16)
            if (hex != null) {
                bytes.add(hex.toByte())
                i += 3
                continue
            }
        }
        if (c == '+' && plusAsSpace) {
            bytes.add(' '.code.toByte())
        } else {
            c.toString().encodeToByteArray().forEach(bytes::add)
        }
        i++
    }
    return bytes.toByteArray().decodeToString()
}

/** As java.net.URLEncoder.encode(value, UTF-8): form encoding, a space becomes '+'. */
fun formUrlEncode(value: String): String = buildString {
    for (byte in value.encodeToByteArray()) {
        val c = (byte.toInt() and 0xFF)
        val ch = c.toChar()
        when {
            ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' || ch == '.' || ch == '-' || ch == '*' || ch == '_' -> append(ch)
            ch == ' ' -> append('+')
            else -> append('%').append(HEX[c shr 4]).append(HEX[c and 0x0F])
        }
    }
}

/** As java.net.URLDecoder.decode(value, UTF-8). */
fun formUrlDecode(value: String): String = percentDecode(value, plusAsSpace = true)

/** One path segment, percent-encoded so it can't change the path's structure (a space as %20). */
fun encodePathSegment(value: String): String = formUrlEncode(value).replace("+", "%20")

private const val HEX = "0123456789ABCDEF"

/**
 * The parts of an http(s) URL this app reads, as OkHttp's HttpUrl; null when [value] isn't a valid
 * http or https URL with a host.
 */
data class HttpUrlParts(
    val scheme: String,
    val host: String,
    val port: Int,
    val encodedPath: String,
    val query: String?,
    val username: String,
    val password: String,
) {
    val pathSegments: List<String>
        get() = encodedPath.removePrefix("/").split('/').map { percentDecode(it) }

    fun queryParameter(name: String): String? = query?.split('&')?.firstNotNullOfOrNull { pair ->
        val key = formUrlDecode(pair.substringBefore('='))
        if (key == name) formUrlDecode(pair.substringAfter('=', "")) else null
    }

    override fun toString(): String = buildString {
        append(scheme).append("://")
        if (username.isNotEmpty() || password.isNotEmpty()) {
            append(username)
            if (password.isNotEmpty()) append(':').append(password)
            append('@')
        }
        append(if (':' in host) "[$host]" else host)
        if (port != defaultPort(scheme)) append(':').append(port)
        append(encodedPath)
        if (query != null) append('?').append(query)
    }

    companion object {
        fun defaultPort(scheme: String): Int = if (scheme == "https") 443 else 80
    }
}

fun String.toHttpUrlOrNull(): HttpUrlParts? {
    val trimmed = trim()
    val parsed = parseUri(trimmed)
    val scheme = parsed.scheme?.lowercase() ?: return null
    if (scheme != "http" && scheme != "https") return null
    val authority = parsed.authority ?: return null
    val userInfo = parsed.userInfo
    val hostPort = authority.substringAfterLast('@')
    val host = if (hostPort.startsWith("[")) hostPort.substringAfter('[').substringBefore(']')
    else hostPort.substringBefore(':')
    if (host.isEmpty() || host.any { it.isWhitespace() || it == '/' || it == '\\' }) return null
    val portText = if (hostPort.startsWith("[")) hostPort.substringAfter("]:", "") else hostPort.substringAfter(':', "")
    val port = if (portText.isEmpty()) HttpUrlParts.defaultPort(scheme) else portText.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
    return HttpUrlParts(
        scheme = scheme,
        host = host.lowercase(),
        port = port,
        encodedPath = parsed.rawPath.ifEmpty { "/" },
        query = parsed.rawQuery,
        username = userInfo?.substringBefore(':')?.let { percentDecode(it) }.orEmpty(),
        password = userInfo?.substringAfter(':', "")?.let { percentDecode(it) }.orEmpty(),
    )
}
