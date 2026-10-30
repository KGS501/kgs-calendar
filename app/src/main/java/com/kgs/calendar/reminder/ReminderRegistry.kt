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

    /** Alarm request codes that went off, with when; null until the first alarm was tracked. */
    fun readDelivered(): Map<Int, Long>?
    fun writeDelivered(delivered: Map<Int, Long>)
    fun readSnoozed(): List<SnoozedReminder>
    fun writeSnoozed(records: List<SnoozedReminder>)
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

    /** Records that an alarm went off, so it isn't caught up again as a missed reminder. */
    suspend fun markDelivered(alarmRequestCodes: Collection<Int>, nowMillis: Long) = mutex.withLock {
        val recent = storage.readDelivered().orEmpty().filterValues { it >= nowMillis - DELIVERED_RETENTION_MILLIS }
        storage.writeDelivered(recent + alarmRequestCodes.associateWith { nowMillis })
    }

    /** Codes of alarms that went off, or null when nothing was ever tracked (a fresh install or update). */
    suspend fun deliveredCodes(): Set<Int>? = mutex.withLock { storage.readDelivered()?.keys }

    suspend fun addSnoozed(record: SnoozedReminder) = mutex.withLock {
        storage.writeSnoozed(storage.readSnoozed().filterNot { it.alarmRequestCode == record.alarmRequestCode } + record)
    }

    suspend fun removeSnoozed(alarmRequestCode: Int) = mutex.withLock {
        storage.writeSnoozed(storage.readSnoozed().filterNot { it.alarmRequestCode == alarmRequestCode })
    }

    /** Snoozed reminders still to come or missed only recently; older ones are dropped. */
    suspend fun pendingSnoozed(nowMillis: Long): List<SnoozedReminder> = mutex.withLock {
        val pending = storage.readSnoozed().filter { it.triggerAtMillis >= nowMillis - MissedReminders.GRACE_MILLIS }
        storage.writeSnoozed(pending)
        pending
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
        private const val DELIVERED_RETENTION_MILLIS = 2 * MissedReminders.GRACE_MILLIS

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

    override fun readDelivered(): Map<Int, Long>? {
        val value = preferences.getString(KEY_DELIVERED, null) ?: return null
        return runCatching {
            val json = JSONObject(value)
            json.keys().asSequence().associate { it.toInt() to json.getLong(it) }
        }.getOrDefault(emptyMap())
    }

    override fun writeDelivered(delivered: Map<Int, Long>) {
        val json = JSONObject()
        delivered.forEach { (code, at) -> json.put(code.toString(), at) }
        preferences.edit().putString(KEY_DELIVERED, json.toString()).apply()
    }

    override fun readSnoozed(): List<SnoozedReminder> =
        parseArray(preferences.getString(KEY_SNOOZED, null), ::snoozedFromJson)

    override fun writeSnoozed(records: List<SnoozedReminder>) {
        preferences.edit().putString(KEY_SNOOZED, JSONArray(records.map(::snoozedToJson)).toString()).apply()
    }

    private fun snoozedToJson(record: SnoozedReminder): JSONObject = JSONObject()
        .put("alarmRequestCode", record.alarmRequestCode)
        .put("triggerAtMillis", record.triggerAtMillis)
        .put("title", record.title)
        .put("body", record.body)
        .put("notification", keyToJson(record.notificationKey))
        .put("target", record.encodedTarget)

    private fun snoozedFromJson(json: JSONObject): SnoozedReminder = SnoozedReminder(
        alarmRequestCode = json.getInt("alarmRequestCode"),
        triggerAtMillis = json.getLong("triggerAtMillis"),
        title = json.getString("title"),
        body = json.optString("body").takeIf { json.has("body") && !json.isNull("body") },
        notificationKey = keyFromJson(json.getJSONObject("notification")),
        encodedTarget = json.getString("target"),
    )

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
        const val KEY_DELIVERED = "delivered"
        const val KEY_SNOOZED = "snoozed"
    }
}
