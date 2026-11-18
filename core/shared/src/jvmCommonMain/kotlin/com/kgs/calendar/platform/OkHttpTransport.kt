package com.kgs.calendar.platform

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.Buffer
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * [HttpTransport] over the app's OkHttp client: redirects, timeouts and TLS are OkHttp's. Calls run
 * on OkHttp's dispatcher, so cancelling the coroutine reaches the socket through [Call.cancel]
 * instead of waiting for a blocking read to return.
 */
class OkHttpTransport(private val client: OkHttpClient) : HttpTransport {
    private val nonRedirectingClient: OkHttpClient by lazy {
        client.newBuilder().followRedirects(false).followSslRedirects(false).build()
    }

    override suspend fun execute(request: HttpRequest): HttpResponse {
        val builder = Request.Builder().url(request.url)
        request.headers.forEach { (name, value) -> builder.addHeader(name, value) }
        val body = request.body?.toRequestBody(request.contentType?.toMediaTypeOrNull())
        builder.method(request.method, body)
        val call = (if (request.followRedirects) client else nonRedirectingClient).newCall(builder.build())
        call.timeout().timeout(request.totalTimeoutMillis, TimeUnit.MILLISECONDS)
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching { response.use { it.toHttpResponse(request.maxResponseBytes) } }
                    result.fold(continuation::resume, continuation::resumeWithException)
                }
            })
        }
    }

    private fun Response.toHttpResponse(maxResponseBytes: Long): HttpResponse {
        val bytes = body?.let { responseBody ->
            val declared = responseBody.contentLength()
            if (declared > maxResponseBytes) throw ResponseTooLargeException(maxResponseBytes)
            val buffer = Buffer()
            responseBody.source().use { source ->
                while (source.read(buffer, 8_192) != -1L) {
                    if (buffer.size > maxResponseBytes) throw ResponseTooLargeException(maxResponseBytes)
                }
            }
            buffer.readByteArray()
        } ?: ByteArray(0)
        val pairs = (0 until headers.size).map { headers.name(it) to headers.value(it) }
        // OkHttp decodes the declared charset; keep it visible to HttpResponse.bodyText().
        return HttpResponse(code, pairs, bytes, this.request.url.toString())
    }
}

actual fun platformHttpTransport(connectTimeoutSeconds: Long, readTimeoutSeconds: Long): HttpTransport =
    OkHttpTransport(
        OkHttpClient.Builder()
            .connectTimeout(connectTimeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(readTimeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)
            .build(),
    )
