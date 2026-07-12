package com.kgs.calendar.navigation

import android.content.Intent
import android.net.Uri
import com.kgs.calendar.domain.model.CalendarOccurrenceId
import com.kgs.calendar.domain.model.CalendarViewMode
import java.time.LocalDate
import org.json.JSONObject

enum class CalendarLaunchAction(val wireName: String) {
    OpenOccurrence("open_occurrence"),
    OpenDate("open_date"),
    CreateEvent("create_event"),
    CreateTask("create_task"),
    ;

    companion object {
        fun fromWireName(value: String): CalendarLaunchAction? = entries.firstOrNull { it.wireName == value }
    }
}

data class CalendarLaunchTarget(
    val date: LocalDate? = null,
    val viewMode: CalendarViewMode? = null,
    val action: CalendarLaunchAction,
    val occurrence: CalendarOccurrenceId? = null,
) {
    fun encode(): String = JSONObject()
        .put(KEY_VERSION, CODEC_VERSION)
        .put(KEY_ACTION, action.wireName)
        .putNullable(KEY_DATE, date?.toString())
        .putNullable(KEY_VIEW_MODE, viewMode?.name)
        .putNullable(KEY_OCCURRENCE, occurrence?.toJson())
        .toString()

    fun writeTo(intent: Intent) {
        val payload = encode()
        intent.putExtra(EXTRA_TARGET, payload)
        intent.data = Uri.Builder()
            .scheme(URI_SCHEME)
            .authority(URI_AUTHORITY)
            .appendPath(action.wireName)
            .appendQueryParameter(URI_PAYLOAD, payload)
            .build()
    }

    companion object {
        private const val CODEC_VERSION = 1
        private const val KEY_VERSION = "version"
        private const val KEY_ACTION = "action"
        private const val KEY_DATE = "date"
        private const val KEY_VIEW_MODE = "viewMode"
        private const val KEY_OCCURRENCE = "occurrence"
        private const val KEY_KIND = "kind"
        private const val KEY_RESOURCE_HREF = "resourceHref"
        private const val KEY_RECURRENCE_ID_MILLIS = "recurrenceIdMillis"

        const val EXTRA_TARGET = "com.kgs.calendar.extra.LAUNCH_TARGET"
        private const val URI_SCHEME = "kgs-calendar"
        private const val URI_AUTHORITY = "launch"
        private const val URI_PAYLOAD = "payload"

        fun decode(value: String): CalendarLaunchTarget? = runCatching {
            val json = JSONObject(value)
            if (json.optInt(KEY_VERSION, -1) != CODEC_VERSION) return null
            val action = CalendarLaunchAction.fromWireName(json.getString(KEY_ACTION)) ?: return null
            val date = json.stringOrNull(KEY_DATE)?.let(LocalDate::parse)
            val viewMode = json.stringOrNull(KEY_VIEW_MODE)?.let { encoded ->
                CalendarViewMode.entries.firstOrNull { it.name == encoded } ?: return null
            }
            val occurrence = json.objectOrNull(KEY_OCCURRENCE)?.toOccurrenceId() ?: run {
                if (json.has(KEY_OCCURRENCE) && !json.isNull(KEY_OCCURRENCE)) return null
                null
            }
            CalendarLaunchTarget(date, viewMode, action, occurrence)
        }.getOrNull()

        fun readFrom(intent: Intent): CalendarLaunchTarget? {
            val payload = intent.getStringExtra(EXTRA_TARGET)
                ?: intent.data?.getQueryParameter(URI_PAYLOAD)
                ?: return null
            return decode(payload)
        }

        private fun CalendarOccurrenceId.toJson(): JSONObject = JSONObject()
            .put(KEY_KIND, kind)
            .put(KEY_RESOURCE_HREF, resourceHref)
            .put(KEY_RECURRENCE_ID_MILLIS, recurrenceIdMillis)

        private fun JSONObject.toOccurrenceId(): CalendarOccurrenceId? {
            val resourceHref = getString(KEY_RESOURCE_HREF)
            val recurrenceIdMillis = getLong(KEY_RECURRENCE_ID_MILLIS)
            return when (getString(KEY_KIND)) {
                "event" -> CalendarOccurrenceId.Event(resourceHref, recurrenceIdMillis)
                "task" -> CalendarOccurrenceId.Task(resourceHref, recurrenceIdMillis)
                else -> null
            }
        }

        private fun JSONObject.stringOrNull(name: String): String? =
            if (has(name) && !isNull(name)) getString(name) else null

        private fun JSONObject.objectOrNull(name: String): JSONObject? =
            if (has(name) && !isNull(name)) optJSONObject(name) else null

        private fun JSONObject.putNullable(name: String, value: Any?): JSONObject =
            put(name, value ?: JSONObject.NULL)
    }
}
