package com.kgs.calendar.data.remote

import com.kgs.calendar.platform.OkHttpTransport
import okhttp3.OkHttpClient

/** The CalDAV client over [okHttpClient] (the app's and tests' call shape). */
fun CalDavHttpClient(okHttpClient: OkHttpClient): CalDavHttpClient = CalDavHttpClient(OkHttpTransport(okHttpClient))

fun NextcloudLoginFlowClient(okHttpClient: OkHttpClient): NextcloudLoginFlowClient =
    NextcloudLoginFlowClient(OkHttpTransport(okHttpClient))
