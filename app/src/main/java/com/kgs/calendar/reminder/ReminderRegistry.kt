package com.kgs.calendar.reminder

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.kgs.calendar.domain.model.CalendarOccurrenceId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

interface ReminderRegistryStorage {
    fun readScheduled(): List<ScheduledReminderRecord>
    fun writeScheduled(records: List<ScheduledReminderRecord>)
    fun readActive(): List<ActiveReminderNotification>
    fun writeActive(records: List<ActiveReminderNotification>)
}

class ReminderRegistry(
    private val storage: ReminderRegistryStorage,
    private val cancelAlarm: (Int) -> Unit,
    private val cancelNotification: (ReminderNotificationKey) -> Unit,
    private val mutex: Mutex = Mutex(),
) : TaskNotificationReconciler {
    suspend fun replaceAllScheduled(records: List<ScheduledReminderRecord>) = mutex.withLock {
        storage.readScheduled().forEach { cancelAlarm(it.alarmRequestCode) }
        storage.writeScheduled(records.distinctBy { it.alarmRequestCode })
    }

    suspend fun recordNotification(notification: ActiveReminderNotification) = mutex.withLock {
        val remaining = storage.readActive().filterNot { it.occurrenceId == notification.occurrenceId }
        storage.writeActive(remaining + notification)
    }

    override suspend fun cancelOccurrence(occurrenceId: CalendarOccurrenceId.Task) = mutex.withLock {
        cancelMatching { it == occurrenceId }
    }

    override suspend fun cancelResource(resourceHref: String) = mutex.withLock {
        cancelMatching { it.resourceHref == resourceHref }
    }

    private fun cancelMatching(matches: (CalendarOccurrenceId) -> Boolean) {
        val scheduled = storage.readScheduled()
        scheduled.filter { matches(it.occurrenceId) }.forEach { cancelAlarm(it.alarmRequestCode) }
        storage.writeScheduled(scheduled.filterNot { matches(it.occurrenceId) })

        val active = storage.readActive()
        active.filter { matches(it.occurrenceId) }.forEach { cancelNotification(it.notificationKey) }
        storage.writeActive(active.filterNot { matches(it.occurrenceId) })
    }

    companion object {
        fun create(context: Context): ReminderRegistry {
            val appContext = context.applicationContext
            val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            return ReminderRegistry(
                storage = SharedPreferencesReminderRegistryStorage(appContext),
                cancelAlarm = { requestCode ->
                    val pendingIntent = PendingIntent.getBroadcast(
                        appContext,
                        requestCode,
                        Intent(appContext, ReminderReceiver::class.java),
                        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
                    )
                    if (pendingIntent != null) alarmManager.cancel(pendingIntent)
                },
                cancelNotification = { key -> notificationManager.cancel(key.tag, key.id) },
            )
        }
    }
}

private class SharedPreferencesReminderRegistryStorage(context: Context) : ReminderRegistryStorage {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun readScheduled(): List<ScheduledReminderRecord> =
        parseArray(preferences.getString(KEY_SCHEDULED, null), ::scheduledFromJson)

    override fun writeScheduled(records: List<ScheduledReminderRecord>) {
        preferences.edit().putString(KEY_SCHEDULED, JSONArray(records.map(::scheduledToJson)).toString()).apply()
    }

    override fun readActive(): List<ActiveReminderNotification> =
        parseArray(preferences.getString(KEY_ACTIVE, null), ::activeFromJson)

    override fun writeActive(records: List<ActiveReminderNotification>) {
        preferences.edit().putString(KEY_ACTIVE, JSONArray(records.map(::activeToJson)).toString()).apply()
    }

    private fun scheduledToJson(record: ScheduledReminderRecord): JSONObject = JSONObject()
        .put("alarmRequestCode", record.alarmRequestCode)
        .put("occurrence", occurrenceToJson(record.occurrenceId))
        .put("notification", keyToJson(record.notificationKey))

    private fun scheduledFromJson(json: JSONObject): ScheduledReminderRecord? = ScheduledReminderRecord(
        alarmRequestCode = json.getInt("alarmRequestCode"),
        occurrenceId = occurrenceFromJson(json.getJSONObject("occurrence")) ?: return null,
        notificationKey = keyFromJson(json.getJSONObject("notification")),
    )

    private fun activeToJson(record: ActiveReminderNotification): JSONObject = JSONObject()
        .put("occurrence", occurrenceToJson(record.occurrenceId))
        .put("notification", keyToJson(record.notificationKey))

    private fun activeFromJson(json: JSONObject): ActiveReminderNotification? = ActiveReminderNotification(
        occurrenceId = occurrenceFromJson(json.getJSONObject("occurrence")) ?: return null,
        notificationKey = keyFromJson(json.getJSONObject("notification")),
    )

    private fun occurrenceToJson(id: CalendarOccurrenceId): JSONObject = JSONObject()
        .put("kind", id.kind)
        .put("resourceHref", id.resourceHref)
        .put("recurrenceIdMillis", id.recurrenceIdMillis)

    private fun occurrenceFromJson(json: JSONObject): CalendarOccurrenceId? = when (json.getString("kind")) {
        "event" -> CalendarOccurrenceId.Event(json.getString("resourceHref"), json.getLong("recurrenceIdMillis"))
        "task" -> CalendarOccurrenceId.Task(json.getString("resourceHref"), json.getLong("recurrenceIdMillis"))
        else -> null
    }

    private fun keyToJson(key: ReminderNotificationKey): JSONObject = JSONObject()
        .put("tag", key.tag)
        .put("id", key.id)

    private fun keyFromJson(json: JSONObject): ReminderNotificationKey =
        ReminderNotificationKey(json.getString("tag"), json.getInt("id"))

    private fun <T> parseArray(value: String?, decode: (JSONObject) -> T?): List<T> = runCatching {
        val array = JSONArray(value ?: "[]")
        buildList {
            repeat(array.length()) { index -> decode(array.getJSONObject(index))?.let(::add) }
        }
    }.getOrDefault(emptyList())

    private companion object {
        const val PREFERENCES_NAME = "kgs_reminder_registry"
        const val KEY_SCHEDULED = "scheduled"
        const val KEY_ACTIVE = "active"
    }
}
