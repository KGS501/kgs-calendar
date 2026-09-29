package com.kgs.calendar.data.remote

import org.json.JSONObject

/**
 * A Nextcloud CalDAV trash bin (Nextcloud 22+), the `trashbin` child of the calendar home.
 * [url] is absolute and ends with a slash; [retentionSeconds] is the server's
 * `{http://nextcloud.com/ns}trash-bin-retention-duration`, null when not reported.
 */
data class RemoteTrashBin(
    val url: String,
    val retentionSeconds: Long?,
) {
    val objectsUrl: String get() = "${url}objects/"
}

/**
 * One deleted calendar object listed in `trashbin/objects/`. [href] is the trash object
 * (`…/trashbin/objects/<id>.ics`), not the original resource; [calendarUri] is the last path
 * segment of the calendar it was deleted from.
 */
data class RemoteTrashedObject(
    val href: String,
    val etag: String?,
    val calendarData: String?,
    val deletedAtMillis: Long?,
    val calendarUri: String?,
)

/**
 * Whether an account's server has a usable Nextcloud trash bin, cached in the account's
 * `capabilitiesJson` under [KEY] so servers without one aren't probed on every sync.
 */
data class CalDavTrashBinSupport(
    /** The calendar home the check was made for; a different home means it has to be checked again. */
    val calendarHomeUrl: String,
    val trashBin: RemoteTrashBin?,
    val checkedAtMillis: Long,
    val probeVersion: Int = CURRENT_PROBE_VERSION,
) {
    val supported: Boolean get() = trashBin != null

    fun isCurrentFor(calendarHomeUrl: String, nowMillis: Long): Boolean =
        probeVersion == CURRENT_PROBE_VERSION && this.calendarHomeUrl == calendarHomeUrl &&
            // Found trash bins are refreshed together with their listing anyway; missing ones are checked weekly.
            (supported || (nowMillis - checkedAtMillis) in 0 until UNSUPPORTED_RECHECK_MILLIS)

    fun writeInto(capabilitiesJson: String?): String {
        val json = capabilitiesJson?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()
        val entry = JSONObject()
            .put("calendarHomeUrl", calendarHomeUrl)
            .put("supported", supported)
            .put("checkedAtMillis", checkedAtMillis)
            .put("probeVersion", probeVersion)
        trashBin?.let {
            entry.put("url", it.url)
            it.retentionSeconds?.let { seconds -> entry.put("retentionSeconds", seconds) }
        }
        return json.put(KEY, entry).toString()
    }

    companion object {
        const val KEY = "nextcloudTrashBin"
        const val CURRENT_PROBE_VERSION = 2
        private const val UNSUPPORTED_RECHECK_MILLIS = 7L * 24 * 60 * 60 * 1000

        fun readFrom(capabilitiesJson: String?): CalDavTrashBinSupport? = runCatching {
            val entry = capabilitiesJson?.let(::JSONObject)?.optJSONObject(KEY) ?: return@runCatching null
            val trashBin = entry.optString("url").takeIf { entry.optBoolean("supported") && it.isNotBlank() }?.let { url ->
                RemoteTrashBin(url, entry.optLong("retentionSeconds", -1).takeIf { it >= 0 })
            }
            CalDavTrashBinSupport(
                calendarHomeUrl = entry.getString("calendarHomeUrl"),
                trashBin = trashBin,
                checkedAtMillis = entry.optLong("checkedAtMillis"),
                probeVersion = entry.optInt("probeVersion", 0),
            )
        }.getOrNull()

        /** Keeps the cached check of [previousJson] in a freshly discovered capabilities JSON. */
        fun carryOver(previousJson: String?, into: String): String =
            readFrom(previousJson)?.writeInto(into) ?: into
    }
}
