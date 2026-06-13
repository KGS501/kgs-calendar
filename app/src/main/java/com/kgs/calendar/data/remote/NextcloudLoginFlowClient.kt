package com.kgs.calendar.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

class NextcloudLoginFlowClient(
    private val okHttpClient: OkHttpClient,
) {
    suspend fun start(serverUrl: String): LoginFlowStart = withContext(Dispatchers.IO) {
        val normalized = normalizeServer(serverUrl)
        val request = Request.Builder()
            .url("$normalized/index.php/login/v2")
            .post(FormBody.Builder().build())
            .header("User-Agent", USER_AGENT)
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Login flow failed: HTTP ${response.code}")
            val body = response.body?.string().orEmpty()
            LoginFlowStart(
                loginUrl = requireJsonString(body, "login"),
                pollEndpoint = requireJsonString(body, "endpoint"),
                token = requireJsonString(body, "token"),
            )
        }
    }

    suspend fun pollUntilComplete(
        pollEndpoint: String,
        token: String,
        maxAttempts: Int = 120,
        delayMillis: Long = 2_000,
    ): LoginFlowResult = withContext(Dispatchers.IO) {
        repeat(maxAttempts) {
            val result = pollOnce(pollEndpoint, token)
            if (result != null) return@withContext result
            delay(delayMillis)
        }
        error("Nextcloud login timed out before credentials were granted.")
    }

    suspend fun pollOnce(pollEndpoint: String, token: String): LoginFlowResult? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(pollEndpoint)
            .post(FormBody.Builder().add("token", token).build())
            .header("User-Agent", USER_AGENT)
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (response.code == 404) return@withContext null
            if (!response.isSuccessful) error("Login polling failed: HTTP ${response.code}")
            val body = response.body?.string().orEmpty()
            LoginFlowResult(
                serverUrl = normalizeServer(requireJsonString(body, "server")),
                loginName = requireJsonString(body, "loginName"),
                appPassword = requireJsonString(body, "appPassword"),
            )
        }
    }

    private fun requireJsonString(json: String, key: String): String {
        val regex = Regex(""""$key"\s*:\s*"((?:\\.|[^"])*)"""")
        val match = regex.find(json) ?: error("Missing '$key' in Nextcloud response.")
        return match.groupValues[1].replace("\\/", "/").replace("\\\"", "\"")
    }

    private fun normalizeServer(serverUrl: String): String {
        val trimmed = serverUrl.trim().trimEnd('/')
        return when {
            trimmed.startsWith("http://", ignoreCase = true) -> trimmed
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            else -> "https://$trimmed"
        }
    }

    companion object {
        const val USER_AGENT = "KGS-Calendar-Android/0.1"
    }
}
