package com.kgs.calendar.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer

/** [HttpTransport] over the app's OkHttp client: redirects, timeouts and TLS are OkHttp's. */
class OkHttpTransport(private val client: OkHttpClient) : HttpTransport {
    override suspend fun execute(request: HttpRequest): HttpResponse = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(request.url)
        request.headers.forEach { (name, value) -> builder.addHeader(name, value) }
        val body = request.body?.toRequestBody(request.contentType?.toMediaTypeOrNull())
        builder.method(request.method, body)
        client.newCall(builder.build()).execute().use { response ->
            val bytes = response.body?.let { responseBody ->
                val declared = responseBody.contentLength()
                if (declared > request.maxResponseBytes) throw ResponseTooLargeException(request.maxResponseBytes)
                val buffer = Buffer()
                responseBody.source().use { source ->
                    while (source.read(buffer, 8_192) != -1L) {
                        if (buffer.size > request.maxResponseBytes) throw ResponseTooLargeException(request.maxResponseBytes)
                    }
                }
                buffer.readByteArray()
            } ?: ByteArray(0)
            val headers = (0 until response.headers.size).map { response.headers.name(it) to response.headers.value(it) }
            // OkHttp decodes the declared charset; keep it visible to HttpResponse.bodyText().
            HttpResponse(response.code, headers, bytes, response.request.url.toString())
        }
    }
}

actual fun platformHttpTransport(connectTimeoutSeconds: Long, readTimeoutSeconds: Long): HttpTransport =
    OkHttpTransport(
        OkHttpClient.Builder()
            .connectTimeout(connectTimeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(readTimeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)
            .build(),
    )
