package com.kgs.calendar.data.provider

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.kgs.calendar.data.local.entity.EventEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.TimeZone
import kotlin.math.max

class AndroidCalendarProviderClient(
    private val context: Context,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    private val resolver: ContentResolver
        get() = context.contentResolver

    fun hasCalendarPermissions(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    suspend fun listCalendars(includeDisabled: Boolean = false): List<AndroidProviderCalendar> = withContext(Dispatchers.IO) {
        requirePermissions()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.OWNER_ACCOUNT,
            CalendarContract.Calendars.ALLOWED_REMINDERS,
            CalendarContract.Calendars.ALLOWED_AVAILABILITY,
            CalendarContract.Calendars.SYNC_EVENTS,
            CalendarContract.Calendars.VISIBLE,
        )
        resolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            if (includeDisabled) null else "${CalendarContract.Calendars.SYNC_EVENTS} = 1",
            null,
            "${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} COLLATE NOCASE ASC",
        )?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val accessLevel = cursor.getInt(3)
                    val color = cursor.getInt(2).takeIf { it != 0 } ?: fallbackColor(id)
                    add(
                        AndroidProviderCalendar(
                            id = id,
                            displayName = cursor.getString(1)?.takeIf { it.isNotBlank() } ?: "Android calendar",
                            color = color,
                            accessLevel = accessLevel,
                            accountName = cursor.getString(4),
                            accountType = cursor.getString(5),
                            ownerAccount = cursor.getString(6),
                            allowsReminders = cursor.getString(7)?.isNotBlank() != false,
                            allowedAvailability = cursor.getString(8),
                            syncEvents = cursor.getInt(9) == 1,
                            visible = cursor.getInt(10) == 1,
                        ),
                    )
                }
            }
        }.orEmpty()
    }

    suspend fun listHiddenOrNotSyncedCalendars(): List<AndroidProviderCalendar> =
        listCalendars(includeDisabled = true)
            .filter { !it.syncEvents || !it.visible }
            .sortedWith(compareBy<AndroidProviderCalendar> { it.accountName.orEmpty().lowercase() }.thenBy { it.displayName.lowercase() })

    suspend fun listEvents(
        calendarIds: Set<Long>,
        syncStartMillis: Long,
        syncEndMillis: Long,
    ): List<AndroidProviderEvent> = withContext(Dispatchers.IO) {
        requirePermissions()
        if (calendarIds.isEmpty()) return@withContext emptyList()
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.CALENDAR_ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.DURATION,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.RRULE,
            CalendarContract.Events.EXDATE,
            CalendarContract.Events.STATUS,
            CalendarContract.Events.ACCESS_LEVEL,
            CalendarContract.Events.AVAILABILITY,
            CalendarContract.Events.ORGANIZER,
            CalendarContract.Events.ORIGINAL_ID,
            CalendarContract.Events.ORIGINAL_INSTANCE_TIME,
            CalendarContract.Events.EVENT_COLOR,
        )
        val rawEvents = mutableListOf<AndroidProviderEvent>()
        calendarIds.chunked(MAX_SELECTION_ARGS).forEach { chunk ->
            val placeholders = chunk.joinToString(",") { "?" }
            resolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                """
                ${CalendarContract.Events.CALENDAR_ID} IN ($placeholders)
                    AND COALESCE(deleted, 0) = 0
                    AND (
                        (${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?)
                        OR (${CalendarContract.Events.DTEND} >= ? AND ${CalendarContract.Events.DTEND} <= ?)
                        OR (${CalendarContract.Events.RRULE} IS NOT NULL AND ${CalendarContract.Events.RRULE} != '')
                        OR (${CalendarContract.Events.ORIGINAL_ID} IS NOT NULL)
                    )
                """.trimIndent(),
                (chunk.map(Long::toString) + listOf(syncStartMillis, syncEndMillis, syncStartMillis, syncEndMillis).map(Long::toString)).toTypedArray(),
                "${CalendarContract.Events.DTSTART} ASC",
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val calendarId = cursor.getLong(1)
                    val providerStartsAt = cursor.getLong(5)
                    val dtEnd = if (cursor.isNull(6)) null else cursor.getLong(6)
                    val durationMillis = cursor.getString(7)?.parseCalendarDurationMillis()
                    val allDay = cursor.getInt(8) == 1
                    val providerEndsAt = dtEnd
                        ?: durationMillis?.let { providerStartsAt + it }
                        ?: if (allDay) providerStartsAt + Duration.ofDays(1).toMillis() else providerStartsAt + Duration.ofHours(1).toMillis()
                    val startsAt = if (allDay) androidAllDayProviderMillisToLocalMillis(providerStartsAt, zoneId) else providerStartsAt
                    val endsAt = if (allDay) androidAllDayProviderMillisToLocalMillis(providerEndsAt, zoneId) else providerEndsAt
                    rawEvents += AndroidProviderEvent(
                        id = id,
                        calendarId = calendarId,
                        title = cursor.getString(2),
                        description = cursor.getString(3),
                        location = cursor.getString(4),
                        startsAtMillis = startsAt,
                        endsAtMillis = max(endsAt, startsAt + 1L),
                        allDay = allDay,
                        recurrenceRule = cursor.getString(9),
                        exDates = cursor.getString(10),
                        status = cursor.getIntOrNull(11),
                        accessLevel = cursor.getIntOrNull(12),
                        availability = cursor.getIntOrNull(13),
                        organizer = cursor.getString(14),
                        originalId = cursor.getLongOrNull(15),
                        originalInstanceTime = cursor.getLongOrNull(16),
                        eventColor = cursor.getIntOrNull(17),
                    )
                }
            }
        }
        val exceptionDatesByOriginalId = rawEvents
            .filter { it.originalId != null && it.originalInstanceTime != null }
            .groupBy { it.originalId!! }
            .mapValues { (_, exceptions) -> exceptions.mapNotNull { it.originalInstanceTime } }
        val displayEvents = rawEvents.filterNot {
            it.originalId != null && it.status == CalendarContract.Events.STATUS_CANCELED
        }
        val remindersByEventId = listReminders(displayEvents.map { it.id })
        displayEvents.map { event ->
            val exceptionDates = exceptionDatesByOriginalId[event.id].orEmpty()
            event.copy(
                exDates = mergeAndroidExDates(event.exDates, exceptionDates, event.allDay),
                reminderMinutes = remindersByEventId[event.id].orEmpty(),
            )
        }
    }

    suspend fun insertEvent(calendarId: Long, event: EventEntity): Long = withContext(Dispatchers.IO) {
        requirePermissions()
        val uri = resolver.insert(CalendarContract.Events.CONTENT_URI, event.toContentValues(calendarId))
            ?: error("Android calendar provider did not return an event URI.")
        val eventId = ContentUris.parseId(uri)
        replaceReminders(eventId, event.remindersCsv.toMinutes())
        eventId
    }

    suspend fun updateEvent(eventId: Long, calendarId: Long, event: EventEntity) = withContext(Dispatchers.IO) {
        requirePermissions()
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        val updated = resolver.update(uri, event.toContentValues(calendarId), null, null)
        if (updated <= 0) error("Android event $eventId could not be updated.")
        replaceReminders(eventId, event.remindersCsv.toMinutes())
    }

    suspend fun deleteEvent(eventId: Long) = withContext(Dispatchers.IO) {
        requirePermissions()
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        resolver.delete(uri, null, null)
    }

    suspend fun cancelRecurringInstance(
        eventId: Long,
        calendarId: Long,
        occurrenceStartMillis: Long,
        occurrenceEndMillis: Long,
        allDay: Boolean,
    ) = withContext(Dispatchers.IO) {
        requirePermissions()
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.ORIGINAL_ID, eventId)
            val providerOccurrenceStart = if (allDay) androidAllDayLocalMillisToProviderMillis(occurrenceStartMillis, zoneId) else occurrenceStartMillis
            val providerOccurrenceEnd = if (allDay) androidAllDayLocalMillisToProviderMillis(occurrenceEndMillis, zoneId) else occurrenceEndMillis
            put(CalendarContract.Events.ORIGINAL_INSTANCE_TIME, providerOccurrenceStart)
            put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CANCELED)
            put(CalendarContract.Events.DTSTART, providerOccurrenceStart)
            put(CalendarContract.Events.DTEND, max(providerOccurrenceEnd, providerOccurrenceStart + 1L))
            put(CalendarContract.Events.EVENT_TIMEZONE, if (allDay) "UTC" else TimeZone.getDefault().id)
            put(CalendarContract.Events.ALL_DAY, if (allDay) 1 else 0)
        }
        resolver.insert(CalendarContract.Events.CONTENT_URI, values)
            ?: error("Android calendar provider did not accept the recurring exception.")
    }

    fun toEntity(event: AndroidProviderEvent, collectionHref: String, color: Int, manualColor: Int? = null): EventEntity =
        androidProviderEventToEntity(event, collectionHref, color, manualColor, zoneId)

    fun calendarHref(calendarId: Long): String = "$ANDROID_CALENDAR_PREFIX$calendarId"

    fun eventHref(eventId: Long): String = androidEventHref(eventId)

    fun eventIdFromHref(href: String): Long? =
        href.removePrefix(ANDROID_EVENT_PREFIX).takeIf { href.startsWith(ANDROID_EVENT_PREFIX) }?.toLongOrNull()

    fun calendarIdFromHref(href: String): Long? =
        href.removePrefix(ANDROID_CALENDAR_PREFIX).takeIf { href.startsWith(ANDROID_CALENDAR_PREFIX) }?.toLongOrNull()

    private fun EventEntity.toContentValues(calendarId: Long): ContentValues =
        ContentValues().apply {
            val providerStartsAtMillis = if (allDay) androidAllDayLocalMillisToProviderMillis(startsAtMillis, zoneId) else startsAtMillis
            val providerEndsAtMillis = if (allDay) androidAllDayLocalMillisToProviderMillis(endsAtMillis, zoneId) else endsAtMillis
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, title.ifBlank { "Untitled event" })
            put(CalendarContract.Events.DESCRIPTION, description?.ifBlank { null })
            put(CalendarContract.Events.EVENT_LOCATION, location?.ifBlank { null })
            put(CalendarContract.Events.DTSTART, providerStartsAtMillis)
            put(CalendarContract.Events.EVENT_TIMEZONE, if (allDay) "UTC" else TimeZone.getDefault().id)
            put(CalendarContract.Events.ALL_DAY, if (allDay) 1 else 0)
            val recurrence = recurrenceRule?.ifBlank { null }
            if (recurrence != null) {
                put(CalendarContract.Events.RRULE, recurrence)
                put(CalendarContract.Events.DURATION, formatCalendarDuration(providerEndsAtMillis - providerStartsAtMillis, allDay))
                putNull(CalendarContract.Events.DTEND)
            } else {
                putNull(CalendarContract.Events.RRULE)
                putNull(CalendarContract.Events.DURATION)
                put(CalendarContract.Events.DTEND, providerEndsAtMillis)
            }
            put(CalendarContract.Events.EXDATE, exDatesCsv.toAndroidExDate(allDay))
        }

    private fun listReminders(eventIds: List<Long>): Map<Long, List<Int>> {
        if (eventIds.isEmpty()) return emptyMap()
        val result = mutableMapOf<Long, MutableList<Int>>()
        val projection = arrayOf(CalendarContract.Reminders.EVENT_ID, CalendarContract.Reminders.MINUTES)
        eventIds.chunked(MAX_SELECTION_ARGS).forEach { chunk ->
            val placeholders = chunk.joinToString(",") { "?" }
            resolver.query(
                CalendarContract.Reminders.CONTENT_URI,
                projection,
                "${CalendarContract.Reminders.EVENT_ID} IN ($placeholders)",
                chunk.map(Long::toString).toTypedArray(),
                null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val eventId = cursor.getLong(0)
                    val minutes = cursor.getInt(1)
                    result.getOrPut(eventId) { mutableListOf() } += minutes
                }
            }
        }
        return result
    }

    private fun replaceReminders(eventId: Long, minutes: List<Int>) {
        resolver.delete(CalendarContract.Reminders.CONTENT_URI, "${CalendarContract.Reminders.EVENT_ID} = ?", arrayOf(eventId.toString()))
        minutes.distinct().sorted().forEach { minute ->
            val values = ContentValues().apply {
                put(CalendarContract.Reminders.EVENT_ID, eventId)
                put(CalendarContract.Reminders.MINUTES, minute)
                put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_DEFAULT)
            }
            runCatching { resolver.insert(CalendarContract.Reminders.CONTENT_URI, values) }
        }
    }

    private fun requirePermissions() {
        if (!hasCalendarPermissions()) error("Android calendar permission is required.")
    }

    private fun fallbackColor(id: Long): Int =
        DEFAULT_COLORS[(id % DEFAULT_COLORS.size).toInt()]

    private fun androidEventHref(eventId: Long): String = "$ANDROID_EVENT_PREFIX$eventId"

    private fun String?.toMinutes(): List<Int> =
        this?.split(',')?.mapNotNull { it.trim().toIntOrNull() }?.filter { it >= 0 }.orEmpty()

    private fun androidDateForMillis(millis: Long, allDay: Boolean): String =
        if (allDay) {
            Instant.ofEpochMilli(millis).atZone(zoneId).toLocalDate().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE)
        } else {
            java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
                .withZone(java.time.ZoneOffset.UTC)
                .format(Instant.ofEpochMilli(millis))
        }

    private fun String?.toAndroidExDate(allDay: Boolean): String? =
        this?.split(',')
            ?.mapNotNull { it.trim().toLongOrNull() }
            ?.map { androidDateForMillis(it, allDay) }
            ?.takeIf { it.isNotEmpty() }
            ?.joinToString(",")

    private fun String?.toEpochMillisCsv(allDay: Boolean): String? =
        this?.split(',')
            ?.mapNotNull { raw -> raw.trim().androidExDateToMillis(allDay) }
            ?.takeIf { it.isNotEmpty() }
            ?.joinToString(",")

    private fun String.androidExDateToMillis(allDay: Boolean): Long? =
        runCatching {
            if (allDay || length == 8) {
                java.time.LocalDate.parse(this, java.time.format.DateTimeFormatter.BASIC_ISO_DATE)
                    .atStartOfDay(zoneId)
                    .toInstant()
                    .toEpochMilli()
            } else {
                java.time.LocalDateTime.parse(
                    removeSuffix("Z"),
                    java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"),
                ).atZone(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
            }
        }.getOrNull()

    private fun String.parseCalendarDurationMillis(): Long? =
        runCatching { Duration.parse(this).toMillis() }.getOrNull()

    private fun mergeAndroidExDates(existing: String?, exceptionMillis: List<Long>, allDay: Boolean): String? {
        val merged = existing
            ?.split(',')
            ?.mapNotNull { it.trim().takeIf(String::isNotBlank) }
            .orEmpty() + exceptionMillis.map { androidDateForMillis(it, allDay) }
        return merged.distinct().takeIf { it.isNotEmpty() }?.joinToString(",")
    }

    private fun formatCalendarDuration(durationMillis: Long, allDay: Boolean): String {
        val safeMillis = durationMillis.coerceAtLeast(Duration.ofMinutes(1).toMillis())
        if (allDay) {
            val days = (safeMillis / Duration.ofDays(1).toMillis()).coerceAtLeast(1)
            return "P${days}D"
        }
        return "PT${safeMillis / 1000L}S"
    }

    companion object {
        const val ANDROID_ACCOUNT_ID = "android-provider"
        const val ANDROID_ACCOUNT_SERVER_URL = "android://calendar-provider"
        const val ANDROID_ACCOUNT_USERNAME = "Android device"
        const val ANDROID_CALENDAR_PREFIX = "android://calendar/"
        internal const val ANDROID_EVENT_PREFIX_FOR_MAPPING = "android://event/"
        private const val ANDROID_EVENT_PREFIX = ANDROID_EVENT_PREFIX_FOR_MAPPING
        private const val MAX_SELECTION_ARGS = 450
        private val DEFAULT_COLORS = listOf(
            Color.rgb(23, 107, 93),
            Color.rgb(26, 115, 232),
            Color.rgb(185, 81, 64),
            Color.rgb(120, 85, 190),
            Color.rgb(238, 147, 45),
        )
    }
}

data class AndroidProviderCalendar(
    val id: Long,
    val displayName: String,
    val color: Int,
    val accessLevel: Int,
    val accountName: String?,
    val accountType: String?,
    val ownerAccount: String?,
    val allowsReminders: Boolean,
    val allowedAvailability: String?,
    val syncEvents: Boolean = true,
    val visible: Boolean = true,
) {
    val writable: Boolean
        get() = syncEvents && accessLevel >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR

    fun capabilitiesJson(): String =
        JSONObject()
            .put("events", true)
            .put("tasks", false)
            .put("reminders", allowsReminders)
            .put("participants", false)
            .put("categories", false)
            .put("androidAccessLevel", accessLevel)
            .put("androidWritable", writable)
            .put("androidAvailability", allowedAvailability)
            .put("androidSyncEvents", syncEvents)
            .put("androidVisible", visible)
            .toString()
}

data class AndroidProviderEvent(
    val id: Long,
    val calendarId: Long,
    val title: String?,
    val description: String?,
    val location: String?,
    val startsAtMillis: Long,
    val endsAtMillis: Long,
    val allDay: Boolean,
    val recurrenceRule: String?,
    val exDates: String?,
    val status: Int?,
    val accessLevel: Int?,
    val availability: Int?,
    val organizer: String?,
    val originalId: Long? = null,
    val originalInstanceTime: Long? = null,
    val eventColor: Int? = null,
    val reminderMinutes: List<Int> = emptyList(),
)

internal fun androidProviderEventToEntity(
    event: AndroidProviderEvent,
    collectionHref: String,
    color: Int,
    manualColor: Int? = null,
    zoneId: ZoneId = ZoneId.systemDefault(),
): EventEntity =
    EventEntity(
        uid = "android-event-${event.id}",
        collectionHref = collectionHref,
        resourceHref = "${AndroidCalendarProviderClient.ANDROID_EVENT_PREFIX_FOR_MAPPING}${event.id}",
        title = event.title?.ifBlank { null } ?: "Untitled event",
        description = event.description?.ifBlank { null },
        location = event.location?.ifBlank { null },
        locationMapVerified = null,
        startsAtMillis = event.startsAtMillis,
        endsAtMillis = event.endsAtMillis,
        allDay = event.allDay,
        recurrenceRule = event.recurrenceRule?.ifBlank { null },
        isRecurring = !event.recurrenceRule.isNullOrBlank(),
        exDatesCsv = event.exDates.androidExDatesToEpochMillisCsv(event.allDay, zoneId),
        remindersCsv = event.reminderMinutes.takeIf { it.isNotEmpty() }?.distinct()?.sorted()?.joinToString(","),
        status = null,
        classification = null,
        transparency = null,
        categories = null,
        organizerJson = null,
        attendeesJson = null,
        color = color,
        manualColor = event.eventColor ?: manualColor,
    )

private fun String?.androidExDatesToEpochMillisCsv(allDay: Boolean, zoneId: ZoneId): String? =
    this?.split(',')
        ?.mapNotNull { raw -> raw.trim().androidExDateToMillisForMapping(allDay, zoneId) }
        ?.takeIf { it.isNotEmpty() }
        ?.joinToString(",")

private fun String.androidExDateToMillisForMapping(allDay: Boolean, zoneId: ZoneId): Long? =
    runCatching {
        if (allDay || length == 8) {
            java.time.LocalDate.parse(this, java.time.format.DateTimeFormatter.BASIC_ISO_DATE)
                .atStartOfDay(zoneId)
                .toInstant()
                .toEpochMilli()
        } else {
            java.time.LocalDateTime.parse(
                removeSuffix("Z"),
                java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"),
            ).atZone(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        }
    }.getOrNull()

internal fun androidAllDayProviderMillisToLocalMillis(millis: Long, zoneId: ZoneId): Long =
    Instant.ofEpochMilli(millis)
        .atZone(ZoneOffset.UTC)
        .toLocalDate()
        .atStartOfDay(zoneId)
        .toInstant()
        .toEpochMilli()

internal fun androidAllDayLocalMillisToProviderMillis(millis: Long, zoneId: ZoneId): Long =
    Instant.ofEpochMilli(millis)
        .atZone(zoneId)
        .toLocalDate()
        .atStartOfDay(ZoneOffset.UTC)
        .toInstant()
        .toEpochMilli()

private fun android.database.Cursor.getIntOrNull(index: Int): Int? =
    if (isNull(index)) null else getInt(index)

private fun android.database.Cursor.getLongOrNull(index: Int): Long? =
    if (isNull(index)) null else getLong(index)

private fun Int?.toIcalStatus(): String? =
    when (this) {
        CalendarContract.Events.STATUS_TENTATIVE -> "TENTATIVE"
        CalendarContract.Events.STATUS_CANCELED -> "CANCELLED"
        CalendarContract.Events.STATUS_CONFIRMED -> "CONFIRMED"
        else -> null
    }

private fun Int?.toIcalClass(): String? =
    when (this) {
        CalendarContract.Events.ACCESS_PRIVATE -> "PRIVATE"
        CalendarContract.Events.ACCESS_CONFIDENTIAL -> "CONFIDENTIAL"
        CalendarContract.Events.ACCESS_PUBLIC -> "PUBLIC"
        else -> null
    }

private fun Int?.toIcalTransparency(): String? =
    when (this) {
        CalendarContract.Events.AVAILABILITY_FREE -> "TRANSPARENT"
        CalendarContract.Events.AVAILABILITY_BUSY,
        CalendarContract.Events.AVAILABILITY_TENTATIVE,
        -> "OPAQUE"
        else -> null
    }

private fun String?.toAndroidStatus(): Int =
    when (this?.uppercase()) {
        "TENTATIVE" -> CalendarContract.Events.STATUS_TENTATIVE
        "CANCELLED", "CANCELED" -> CalendarContract.Events.STATUS_CANCELED
        else -> CalendarContract.Events.STATUS_CONFIRMED
    }

private fun String?.toAndroidAccessLevel(): Int =
    when (this?.uppercase()) {
        "PRIVATE" -> CalendarContract.Events.ACCESS_PRIVATE
        "CONFIDENTIAL" -> CalendarContract.Events.ACCESS_CONFIDENTIAL
        "PUBLIC" -> CalendarContract.Events.ACCESS_PUBLIC
        else -> CalendarContract.Events.ACCESS_DEFAULT
    }

private fun String?.toAndroidAvailability(): Int =
    when (this?.uppercase()) {
        "TRANSPARENT" -> CalendarContract.Events.AVAILABILITY_FREE
        else -> CalendarContract.Events.AVAILABILITY_BUSY
    }
