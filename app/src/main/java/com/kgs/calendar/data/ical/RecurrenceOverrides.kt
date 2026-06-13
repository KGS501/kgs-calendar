package com.kgs.calendar.data.ical

import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.local.entity.TaskEntity
import org.json.JSONArray
import org.json.JSONObject

data class EventRecurrenceOverride(
    val recurrenceIdMillis: Long,
    val startsAtMillis: Long,
    val endsAtMillis: Long,
    val allDay: Boolean,
    val title: String,
    val description: String?,
    val location: String?,
    val remindersCsv: String?,
    val status: String?,
    val classification: String?,
    val transparency: String?,
    val categories: String?,
    val organizerJson: String?,
    val attendeesJson: String?,
    val timezoneId: String?,
) {
    fun applyTo(master: EventEntity): EventEntity = master.copy(
        startsAtMillis = startsAtMillis,
        endsAtMillis = endsAtMillis,
        allDay = allDay,
        title = title,
        description = description,
        location = location,
        remindersCsv = remindersCsv,
        status = status,
        classification = classification,
        transparency = transparency,
        categories = categories,
        organizerJson = organizerJson,
        attendeesJson = attendeesJson,
        timezoneId = timezoneId,
    )

    companion object {
        fun fromEvent(recurrenceIdMillis: Long, event: EventEntity): EventRecurrenceOverride =
            EventRecurrenceOverride(
                recurrenceIdMillis = recurrenceIdMillis,
                startsAtMillis = event.startsAtMillis,
                endsAtMillis = event.endsAtMillis,
                allDay = event.allDay,
                title = event.title,
                description = event.description,
                location = event.location,
                remindersCsv = event.remindersCsv,
                status = event.status,
                classification = event.classification,
                transparency = event.transparency,
                categories = event.categories,
                organizerJson = event.organizerJson,
                attendeesJson = event.attendeesJson,
                timezoneId = event.timezoneId,
            )
    }
}

data class TaskRecurrenceOverride(
    val recurrenceIdMillis: Long,
    val title: String,
    val notes: String?,
    val location: String?,
    val url: String?,
    val categories: String?,
    val dueAtMillis: Long?,
    val dueHasTime: Boolean,
    val startAtMillis: Long?,
    val startHasTime: Boolean,
    val completedAtMillis: Long?,
    val isCompleted: Boolean,
    val status: String?,
    val priority: Int?,
    val percentComplete: Int?,
    val remindersCsv: String?,
    val timezoneId: String?,
) {
    fun applyTo(master: TaskEntity): TaskEntity = master.copy(
        title = title,
        notes = notes,
        location = location,
        url = url,
        categories = categories,
        dueAtMillis = dueAtMillis,
        dueHasTime = dueHasTime,
        startAtMillis = startAtMillis,
        startHasTime = startHasTime,
        completedAtMillis = completedAtMillis,
        isCompleted = isCompleted,
        status = status,
        priority = priority,
        percentComplete = percentComplete,
        remindersCsv = remindersCsv,
        timezoneId = timezoneId,
    )

    companion object {
        fun fromTask(recurrenceIdMillis: Long, task: TaskEntity): TaskRecurrenceOverride =
            TaskRecurrenceOverride(
                recurrenceIdMillis = recurrenceIdMillis,
                title = task.title,
                notes = task.notes,
                location = task.location,
                url = task.url,
                categories = task.categories,
                dueAtMillis = task.dueAtMillis,
                dueHasTime = task.dueHasTime,
                startAtMillis = task.startAtMillis,
                startHasTime = task.startHasTime,
                completedAtMillis = task.completedAtMillis,
                isCompleted = task.isCompleted,
                status = task.status,
                priority = task.priority,
                percentComplete = task.percentComplete,
                remindersCsv = task.remindersCsv,
                timezoneId = task.timezoneId,
            )
    }
}

object RecurrenceOverrideCodec {
    fun decodeEvents(value: String?): List<EventRecurrenceOverride> {
        if (value.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(value)
            buildList {
                repeat(array.length()) { index ->
                    val obj = array.optJSONObject(index) ?: return@repeat
                    val recurrenceId = obj.optLong("recurrenceIdMillis", Long.MIN_VALUE)
                    val start = obj.optLong("startsAtMillis", Long.MIN_VALUE)
                    val end = obj.optLong("endsAtMillis", Long.MIN_VALUE)
                    if (recurrenceId == Long.MIN_VALUE || start == Long.MIN_VALUE || end == Long.MIN_VALUE) return@repeat
                    add(
                        EventRecurrenceOverride(
                            recurrenceIdMillis = recurrenceId,
                            startsAtMillis = start,
                            endsAtMillis = end,
                            allDay = obj.optBoolean("allDay"),
                            title = obj.optString("title", "Untitled event"),
                            description = obj.nullableString("description"),
                            location = obj.nullableString("location"),
                            remindersCsv = obj.nullableString("remindersCsv"),
                            status = obj.nullableString("status"),
                            classification = obj.nullableString("classification"),
                            transparency = obj.nullableString("transparency"),
                            categories = obj.nullableString("categories"),
                            organizerJson = obj.nullableString("organizerJson"),
                            attendeesJson = obj.nullableString("attendeesJson"),
                            timezoneId = obj.nullableString("timezoneId"),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun encodeEvents(overrides: Collection<EventRecurrenceOverride>): String? {
        if (overrides.isEmpty()) return null
        val array = JSONArray()
        overrides.sortedBy { it.recurrenceIdMillis }.forEach { override ->
            array.put(
                JSONObject()
                    .put("recurrenceIdMillis", override.recurrenceIdMillis)
                    .put("startsAtMillis", override.startsAtMillis)
                    .put("endsAtMillis", override.endsAtMillis)
                    .put("allDay", override.allDay)
                    .put("title", override.title)
                    .putNullable("description", override.description)
                    .putNullable("location", override.location)
                    .putNullable("remindersCsv", override.remindersCsv)
                    .putNullable("status", override.status)
                    .putNullable("classification", override.classification)
                    .putNullable("transparency", override.transparency)
                    .putNullable("categories", override.categories)
                    .putNullable("organizerJson", override.organizerJson)
                    .putNullable("attendeesJson", override.attendeesJson)
                    .putNullable("timezoneId", override.timezoneId),
            )
        }
        return array.toString()
    }

    fun upsertEvent(value: String?, override: EventRecurrenceOverride): String? {
        val updated = decodeEvents(value)
            .filterNot { it.recurrenceIdMillis == override.recurrenceIdMillis }
            .plus(override)
        return encodeEvents(updated)
    }

    fun decodeTasks(value: String?): List<TaskRecurrenceOverride> {
        if (value.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(value)
            buildList {
                repeat(array.length()) { index ->
                    val obj = array.optJSONObject(index) ?: return@repeat
                    val recurrenceId = obj.optLong("recurrenceIdMillis", Long.MIN_VALUE)
                    if (recurrenceId == Long.MIN_VALUE) return@repeat
                    add(
                        TaskRecurrenceOverride(
                            recurrenceIdMillis = recurrenceId,
                            title = obj.optString("title", "Untitled task"),
                            notes = obj.nullableString("notes"),
                            location = obj.nullableString("location"),
                            url = obj.nullableString("url"),
                            categories = obj.nullableString("categories"),
                            dueAtMillis = obj.nullableLong("dueAtMillis"),
                            dueHasTime = obj.optBoolean("dueHasTime"),
                            startAtMillis = obj.nullableLong("startAtMillis"),
                            startHasTime = obj.optBoolean("startHasTime"),
                            completedAtMillis = obj.nullableLong("completedAtMillis"),
                            isCompleted = obj.optBoolean("isCompleted"),
                            status = obj.nullableString("status"),
                            priority = obj.nullableInt("priority"),
                            percentComplete = obj.nullableInt("percentComplete"),
                            remindersCsv = obj.nullableString("remindersCsv"),
                            timezoneId = obj.nullableString("timezoneId"),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun encodeTasks(overrides: Collection<TaskRecurrenceOverride>): String? {
        if (overrides.isEmpty()) return null
        val array = JSONArray()
        overrides.sortedBy { it.recurrenceIdMillis }.forEach { override ->
            array.put(
                JSONObject()
                    .put("recurrenceIdMillis", override.recurrenceIdMillis)
                    .put("title", override.title)
                    .putNullable("notes", override.notes)
                    .putNullable("location", override.location)
                    .putNullable("url", override.url)
                    .putNullable("categories", override.categories)
                    .putNullable("dueAtMillis", override.dueAtMillis)
                    .put("dueHasTime", override.dueHasTime)
                    .putNullable("startAtMillis", override.startAtMillis)
                    .put("startHasTime", override.startHasTime)
                    .putNullable("completedAtMillis", override.completedAtMillis)
                    .put("isCompleted", override.isCompleted)
                    .putNullable("status", override.status)
                    .putNullable("priority", override.priority)
                    .putNullable("percentComplete", override.percentComplete)
                    .putNullable("remindersCsv", override.remindersCsv)
                    .putNullable("timezoneId", override.timezoneId),
            )
        }
        return array.toString()
    }

    fun upsertTask(value: String?, override: TaskRecurrenceOverride): String? =
        encodeTasks(
            decodeTasks(value)
                .filterNot { it.recurrenceIdMillis == override.recurrenceIdMillis }
                .plus(override),
        )
}

private fun JSONObject.nullableString(name: String): String? =
    if (isNull(name)) null else optString(name).takeIf { it.isNotBlank() }

private fun JSONObject.putNullable(name: String, value: String?): JSONObject =
    put(name, value ?: JSONObject.NULL)

private fun JSONObject.putNullable(name: String, value: Long?): JSONObject =
    put(name, value ?: JSONObject.NULL)

private fun JSONObject.putNullable(name: String, value: Int?): JSONObject =
    put(name, value ?: JSONObject.NULL)

private fun JSONObject.nullableLong(name: String): Long? =
    if (isNull(name) || !has(name)) null else optLong(name)

private fun JSONObject.nullableInt(name: String): Int? =
    if (isNull(name) || !has(name)) null else optInt(name)
