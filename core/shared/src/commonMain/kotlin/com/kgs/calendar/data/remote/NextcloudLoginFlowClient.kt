package com.kgs.calendar.data.remote

import com.kgs.calendar.data.normalizeServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import com.kgs.calendar.platform.HttpRequestBuilder
import com.kgs.calendar.platform.HttpTransport
import com.kgs.calendar.platform.formBody
import com.kgs.calendar.platform.kgsPlatformName
import com.kgs.calendar.platform.toMediaType
import com.kgs.calendar.platform.toRequestBody
import com.kgs.calendar.platform.use
import kotlinx.coroutines.IO
import com.kgs.calendar.json.JSONException
import com.kgs.calendar.json.JSONObject

class NextcloudLoginFlowClient(
    private val transport: HttpTransport,
) {
    suspend fun start(serverUrl: String): LoginFlowStart = withContext(Dispatchers.IO) {
        val normalized = normalizeServer(serverUrl)
        val request = HttpRequestBuilder()
            .url("$normalized/index.php/login/v2")
            .post(formBody().toRequestBody(FORM))
            .header("User-Agent", USER_AGENT)
            .build()
        transport.execute(request).use { response ->
            if (!response.isSuccessful) error("Login flow failed: HTTP ${response.code}")
            val json = parseJsonObject(response.bodyText())
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
        val request = HttpRequestBuilder()
            .url(pollEndpoint)
            .post(formBody("token" to token).toRequestBody(FORM))
            .header("User-Agent", USER_AGENT)
            .build()
        transport.execute(request).use { response ->
            if (response.code == 404) return@withContext null
            if (!response.isSuccessful) error("Login polling failed: HTTP ${response.code}")
            val json = parseJsonObject(response.bodyText())
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
        val USER_AGENT = "KGS-Calendar-$kgsPlatformName/0.1"
        private val FORM = "application/x-www-form-urlencoded".toMediaType()
    }
}
