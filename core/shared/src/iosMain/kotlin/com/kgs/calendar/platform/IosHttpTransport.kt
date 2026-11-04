package com.kgs.calendar.platform

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import platform.Foundation.NSData
import platform.Foundation.create
import platform.Foundation.NSError
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.NSURLErrorCannotFindHost
import platform.Foundation.NSURLErrorDNSLookupFailed
import platform.Foundation.NSURLErrorDomain
import platform.Foundation.NSURLErrorTimedOut
import platform.Foundation.NSURLErrorUnsupportedURL
import platform.Foundation.NSURLErrorBadURL
import platform.Foundation.NSURLRequest
import platform.Foundation.NSURLRequestReloadIgnoringLocalCacheData
import platform.Foundation.NSURLResponse
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionDataTask
import platform.Foundation.NSURLSessionTask
import platform.Foundation.NSURLSessionTaskDelegateProtocol
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.setHTTPBody
import platform.Foundation.setHTTPMethod
import platform.Foundation.setValue
import platform.darwin.NSObject
import platform.posix.memcpy
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * [HttpTransport] over NSURLSession. Redirects are followed here with OkHttp's rules (the Android
 * client), and nothing is cached, so conditional requests see their 304s.
 */
class IosHttpTransport(
    private val timeoutSeconds: Double = 30.0,
    private val maxRedirects: Int = 20,
) : HttpTransport {
    private val redirectBlocker = RedirectBlocker()

    private val session: NSURLSession by lazy {
        val configuration = NSURLSessionConfiguration.ephemeralSessionConfiguration
        configuration.setURLCache(null)
        configuration.setRequestCachePolicy(NSURLRequestReloadIgnoringLocalCacheData)
        configuration.setTimeoutIntervalForRequest(timeoutSeconds)
        configuration.setHTTPShouldSetCookies(false)
        NSURLSession.sessionWithConfiguration(configuration, redirectBlocker, null)
    }

    override suspend fun execute(request: HttpRequest): HttpResponse =
        withTimeoutOrNull(request.totalTimeoutMillis) { executeFollowingRedirects(request) }
            ?: throw SocketTimeoutException("The request took longer than ${request.totalTimeoutMillis / 1000} s.")

    private suspend fun executeFollowingRedirects(request: HttpRequest): HttpResponse {
        var current = request
        repeat(maxRedirects + 1) {
            val response = executeOnce(current)
            if (!request.followRedirects) return response
            val location = response.header("Location")
            if (response.code !in setOf(300, 301, 302, 303, 307, 308) || location.isNullOrBlank()) return response
            current = followUp(current, response.code, resolveUri(response.url, location))
        }
        throw IOException("Too many follow-up requests: ${maxRedirects + 1}")
    }

    private fun followUp(previous: HttpRequest, code: Int, target: String): HttpRequest {
        val method = previous.method
        val keepsBody = method == "PROPFIND" || code == 307 || code == 308
        val toGet = method != "PROPFIND" && method != "GET" && method != "HEAD" && code != 307 && code != 308
        var headers = previous.headers
        if (toGet || !keepsBody) headers = headers.filterNot { (name, _) ->
            name.equals("Content-Type", true) || name.equals("Content-Length", true) || name.equals("Transfer-Encoding", true)
        }
        // As OkHttp: credentials stay with the host they were meant for.
        if (parseUri(previous.url).host?.lowercase() != parseUri(target).host?.lowercase()) {
            headers = headers.filterNot { it.first.equals("Authorization", true) }
        }
        return HttpRequest(
            method = if (toGet) "GET" else method,
            url = target,
            headers = headers,
            body = if (keepsBody && !toGet) previous.body else null,
            contentType = if (keepsBody && !toGet) previous.contentType else null,
            maxResponseBytes = previous.maxResponseBytes,
            totalTimeoutMillis = previous.totalTimeoutMillis,
        )
    }

    private suspend fun executeOnce(request: HttpRequest): HttpResponse {
        val url = NSURL.URLWithString(request.url) ?: throw MalformedURLException("Invalid URL: ${request.url}")
        val nsRequest = NSMutableURLRequest.requestWithURL(url)
        nsRequest.setHTTPMethod(request.method)
        nsRequest.setTimeoutInterval(timeoutSeconds)
        nsRequest.setCachePolicy(NSURLRequestReloadIgnoringLocalCacheData)
        request.headers.forEach { (name, value) -> nsRequest.setValue(value, forHTTPHeaderField = name) }
        if (request.contentType != null && request.header("Content-Type") == null) {
            nsRequest.setValue(request.contentType, forHTTPHeaderField = "Content-Type")
        }
        request.body?.let { nsRequest.setHTTPBody(it.toNSData()) }
        return suspendCancellableCoroutine { continuation ->
            val task: NSURLSessionDataTask = session.dataTaskWithRequest(nsRequest) { data: NSData?, response: NSURLResponse?, error: NSError? ->
                when {
                    error != null -> continuation.resumeWithException(error.toIoException())
                    response !is NSHTTPURLResponse -> continuation.resumeWithException(IOException("No HTTP response"))
                    else -> {
                        val bytes = data?.toByteArray() ?: ByteArray(0)
                        if (bytes.size > request.maxResponseBytes) {
                            continuation.resumeWithException(ResponseTooLargeException(request.maxResponseBytes))
                        } else {
                            @Suppress("UNCHECKED_CAST")
                            val headers = (response.allHeaderFields as Map<Any?, Any?>).map { (k, v) -> k.toString() to v.toString() }
                            continuation.resume(
                                HttpResponse(
                                    code = response.statusCode.toInt(),
                                    headers = headers,
                                    body = bytes,
                                    url = response.URL?.absoluteString ?: request.url,
                                ),
                            )
                        }
                    }
                }
            }
            continuation.invokeOnCancellation { task.cancel() }
            task.resume()
        }
    }

    /** Stops NSURLSession's own redirect handling, so [execute] can apply OkHttp's rules. */
    private class RedirectBlocker : NSObject(), NSURLSessionTaskDelegateProtocol {
        override fun URLSession(
            session: NSURLSession,
            task: NSURLSessionTask,
            willPerformHTTPRedirection: NSHTTPURLResponse,
            newRequest: NSURLRequest,
            completionHandler: (NSURLRequest?) -> Unit,
        ) {
            completionHandler(null)
        }
    }
}

private fun NSError.toIoException(): IOException {
    val message = localizedDescription
    if (domain == NSURLErrorDomain) {
        when (code) {
            NSURLErrorCannotFindHost, NSURLErrorDNSLookupFailed -> return UnknownHostException(message)
            NSURLErrorTimedOut -> return SocketTimeoutException(message)
            NSURLErrorBadURL, NSURLErrorUnsupportedURL -> return MalformedURLException(message)
        }
    }
    return IOException(message)
}

fun ByteArray.toNSData(): NSData = if (isEmpty()) NSData() else usePinned { pinned ->
    NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
}

fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    val result = ByteArray(size)
    result.usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length) }
    return result
}

actual fun platformHttpTransport(connectTimeoutSeconds: Long, readTimeoutSeconds: Long): HttpTransport =
    IosHttpTransport(timeoutSeconds = maxOf(connectTimeoutSeconds, readTimeoutSeconds).toDouble())
