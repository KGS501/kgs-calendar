package com.kgs.calendar.data.remote

import com.kgs.calendar.data.normalizeServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject

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
            val json = parseJsonObject(response.body?.string().orEmpty())
            val poll = json.optJSONObject("poll") ?: json
            LoginFlowStart(
                loginUrl = json.requireString("login"),
                pollEndpoint = poll.requireString("endpoint"),
                token = poll.requireString("token"),
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
            val json = parseJsonObject(response.body?.string().orEmpty())
            LoginFlowResult(
                serverUrl = normalizeServer(json.requireString("server")),
                loginName = json.requireString("loginName"),
                appPassword = json.requireString("appPassword"),
            )
        }
    }

    // A real JSON parser: Nextcloud escapes non-ASCII login names (e.g. "\u00e9") and tags.
    private fun parseJsonObject(body: String): JSONObject =
        try {
            JSONObject(body)
        } catch (error: JSONException) {
            throw IllegalStateException("Nextcloud returned an invalid login response.", error)
        }

    private fun JSONObject.requireString(key: String): String =
        optString(key).takeIf { has(key) && !isNull(key) && it.isNotEmpty() }
            ?: error("Missing '$key' in Nextcloud response.")

    companion object {
        const val USER_AGENT = "KGS-Calendar-Android/0.1"
    }
}
