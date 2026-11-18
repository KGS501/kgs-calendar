package com.kgs.calendar.platform

import kotlin.io.encoding.Base64

/**
 * The HTTP calls the data layer makes. Android implements it with OkHttp (the client the app has
 * always used); iOS with NSURLSession. Redirects follow OkHttp's rules on both: PROPFIND keeps its
 * method and body, other methods with a body become GET on 301/302/303, and credentials are dropped
 * when a redirect leaves the host.
 */
interface HttpTransport {
    /**
     * Performs [request]. Throws [IOException] when no response arrives or its
     * [HttpRequest.totalTimeoutMillis] runs out. Cancelling the caller aborts the exchange.
     */
    suspend fun execute(request: HttpRequest): HttpResponse
}

/** The platform's HTTP stack (OkHttp on Android, NSURLSession on iOS) with these timeouts. */
expect fun platformHttpTransport(connectTimeoutSeconds: Long = 20, readTimeoutSeconds: Long = 40): HttpTransport

class HttpRequest(
    val method: String,
    val url: String,
    val headers: List<Pair<String, String>> = emptyList(),
    val body: ByteArray? = null,
    val contentType: String? = null,
    /** A larger body fails with [ResponseTooLargeException] before it is read completely. */
    val maxResponseBytes: Long = DEFAULT_MAX_RESPONSE_BYTES,
    /** False hands every 3xx back to the caller, which then decides where the request may go next. */
    val followRedirects: Boolean = true,
    /** Budget for the whole exchange including redirects and the body; per-read timeouts don't bound a slow drip. */
    val totalTimeoutMillis: Long = DEFAULT_TOTAL_TIMEOUT_MILLIS,
) {
    fun header(name: String): String? = headers.lastOrNull { it.first.equals(name, ignoreCase = true) }?.second

    companion object {
        const val DEFAULT_MAX_RESPONSE_BYTES = 64L * 1024 * 1024
        const val DEFAULT_TOTAL_TIMEOUT_MILLIS = 180_000L
    }
}

class HttpResponse(
    val code: Int,
    val headers: List<Pair<String, String>>,
    val body: ByteArray,
    /** The URL that answered, after redirects. */
    val url: String,
) {
    val isSuccessful: Boolean get() = code in 200..299

    fun header(name: String): String? = headers.lastOrNull { it.first.equals(name, ignoreCase = true) }?.second

    fun headers(name: String): List<String> = headers.filter { it.first.equals(name, ignoreCase = true) }.map { it.second }

    /** The body as text in the charset of its Content-Type (UTF-8 when none is named). */
    fun bodyText(): String {
        val charset = header("Content-Type")?.split(';')?.drop(1)
            ?.map { it.trim() }?.firstOrNull { it.startsWith("charset=", ignoreCase = true) }
            ?.substringAfter('=')?.trim('"', ' ')?.lowercase()
        return when (charset) {
            "iso-8859-1", "latin1", "us-ascii", "ascii", "windows-1252" -> CharArray(body.size) { (body[it].toInt() and 0xFF).toChar() }.concatToString()
            else -> body.decodeToString()
        }
    }
}

class ResponseTooLargeException(maxBytes: Long) :
    IllegalStateException("The server's response is larger than ${maxBytes / (1024 * 1024)} MB.")

/** As OkHttp's Credentials.basic: ISO-8859-1, so characters beyond it become '?'. */
fun basicAuthorization(username: String, password: String): String {
    val text = "$username:$password"
    val bytes = ByteArray(text.length) { i -> text[i].code.let { if (it <= 0xFF) it.toByte() else '?'.code.toByte() } }
    return "Basic " + Base64.encode(bytes)
}

/** application/x-www-form-urlencoded body, as OkHttp's FormBody. */
fun formBody(vararg fields: Pair<String, String>): ByteArray =
    fields.joinToString("&") { (name, value) -> formComponent(name) + "=" + formComponent(value) }.encodeToByteArray()

private fun formComponent(value: String): String = buildString {
    // OkHttp's FORM_ENCODE_SET: everything but unreserved characters is escaped; a space becomes '+'.
    for (byte in value.encodeToByteArray()) {
        val c = byte.toInt() and 0xFF
        val ch = c.toChar()
        when {
            ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' || ch == '-' || ch == '.' || ch == '_' || ch == '*' -> append(ch)
            ch == ' ' -> append('+')
            else -> append('%').append("0123456789ABCDEF"[c shr 4]).append("0123456789ABCDEF"[c and 0xF])
        }
    }
}

// ---- OkHttp-shaped request building, so the CalDAV code reads as before.

class MediaType(private val value: String) {
    override fun toString(): String = value
}

fun String.toMediaType(): MediaType = MediaType(this)

class RequestBody(val bytes: ByteArray, val contentType: MediaType?)

fun String.toRequestBody(contentType: MediaType? = null): RequestBody = RequestBody(encodeToByteArray(), contentType)
fun ByteArray.toRequestBody(contentType: MediaType? = null): RequestBody = RequestBody(this, contentType)

class HttpRequestBuilder {
    private var url: String? = null
    private var method: String = "GET"
    private var body: RequestBody? = null
    private val headers = mutableListOf<Pair<String, String>>()
    private var maxResponseBytes: Long = HttpRequest.DEFAULT_MAX_RESPONSE_BYTES
    private var followRedirects: Boolean = true
    private var totalTimeoutMillis: Long = HttpRequest.DEFAULT_TOTAL_TIMEOUT_MILLIS

    fun url(url: String) = apply { this.url = url }
    fun header(name: String, value: String) = apply {
        headers.removeAll { it.first.equals(name, ignoreCase = true) }
        headers += name to value
    }
    fun addHeader(name: String, value: String) = apply { headers += name to value }
    fun removeHeader(name: String) = apply { headers.removeAll { it.first.equals(name, ignoreCase = true) } }
    fun method(method: String, body: RequestBody?) = apply {
        this.method = method
        this.body = body
    }
    fun get() = method("GET", null)
    fun head() = method("HEAD", null)
    fun put(body: RequestBody) = method("PUT", body)
    fun post(body: RequestBody) = method("POST", body)
    fun delete(body: RequestBody? = null) = method("DELETE", body)
    fun maxResponseBytes(limit: Long) = apply { maxResponseBytes = limit }
    fun followRedirects(follow: Boolean) = apply { followRedirects = follow }
    fun totalTimeoutMillis(limit: Long) = apply { totalTimeoutMillis = limit }

    fun build(): HttpRequest = HttpRequest(
        method = method,
        url = requireNotNull(url) { "url == null" },
        headers = headers.toList(),
        body = body?.bytes ?: if (method == "PUT" || method == "POST" || method == "PROPPATCH" || method == "REPORT") ByteArray(0) else null,
        contentType = body?.contentType?.toString(),
        maxResponseBytes = maxResponseBytes,
        followRedirects = followRedirects,
        totalTimeoutMillis = totalTimeoutMillis,
    )
}

/** The response is fully read; this mirrors OkHttp's `response.use { }` blocks. */
inline fun <T> HttpResponse.use(block: (HttpResponse) -> T): T = block(this)
