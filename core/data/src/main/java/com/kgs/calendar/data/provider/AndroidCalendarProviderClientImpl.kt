package com.kgs.calendar.data.provider

import com.kgs.calendar.time.*
import kotlinx.datetime.*
import com.kgs.calendar.domain.time.orDeviceZone
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
import com.kgs.calendar.domain.model.EventClassification
import com.kgs.calendar.domain.model.EventStatus
import com.kgs.calendar.domain.model.EventTransparency
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.kgs.calendar.json.JSONObject
import kotlin.time.Duration
import kotlin.time.Instant
import com.kgs.calendar.time.ZoneId
import com.kgs.calendar.time.ZoneOffset
import java.util.TimeZone
import kotlin.math.max

/** The Android implementation; `AndroidCalendarProviderClient(context)` keeps the old call shape. */
fun AndroidCalendarProviderClient(context: Context, fixedZoneId: ZoneId? = null): AndroidCalendarProviderClient =
    AndroidCalendarProviderClientImpl(context, fixedZoneId)

class AndroidCalendarProviderClientImpl(
    private val context: Context,
    /** Null follows the device's zone (see [orDeviceZone]). */
    private val fixedZoneId: ZoneId? = null,
) : AndroidCalendarProviderClient {
    private val zoneId: ZoneId get() = fixedZoneId.orDeviceZone()

    private val resolver: ContentResolver
        get() = context.contentResolver

    override fun hasCalendarPermissions(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    override suspend fun listCalendars(includeDisabled: Boolean): List<AndroidProviderCalendar> = withContext(Dispatchers.IO) {
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

    override suspend fun listHiddenOrNotSyncedCalendars(): List<AndroidProviderCalendar> =
        listCalendars(includeDisabled = true)
            .filter { !it.syncEvents || !it.visible }
            .sortedWith(compareBy<AndroidProviderCalendar> { it.accountName.orEmpty().lowercase() }.thenBy { it.displayName.lowercase() })

    override suspend fun listEvents(
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
            CalendarContract.Events.EVENT_TIMEZONE,
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
                    val duration = cursor.getString(7)
                    val allDay = cursor.getInt(8) == 1
                    val recurrenceRule = cursor.getString(9)
                    val providerEndsAt = resolveAndroidProviderEventEndMillis(
                        startsAtMillis = providerStartsAt,
                        dtEndMillis = dtEnd,
                        duration = duration,
                        allDay = allDay,
                        recurrenceRule = recurrenceRule,
                    )
                    val startsAt = if (allDay) androidAllDayProviderMillisToLocalMillis(providerStartsAt, zoneId) else providerStartsAt
                    val endsAt = if (allDay) androidAllDayProviderMillisToLocalMillis(providerEndsAt, zoneId) else providerEndsAt
                    rawEvents += AndroidProviderEvent(
                        id = id,
                        calendarId = calendarId,
                        title = cursor.getString(2),
                        description = cursor.getString(3),
                        location = cursor.getString(4),
                        startsAtMillis = startsAt,
                        endsAtMillis = endsAt,
                        allDay = allDay,
                        recurrenceRule = recurrenceRule,
                        exDates = cursor.getString(10),
                        status = cursor.getIntOrNull(11),
                        accessLevel = cursor.getIntOrNull(12),
                        availability = cursor.getIntOrNull(13),
                        organizer = cursor.getString(14),
                        originalId = cursor.getLongOrNull(15),
                        originalInstanceTime = cursor.getLongOrNull(16),
                        eventColor = cursor.getIntOrNull(17),
                        timezoneId = cursor.getString(18),
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

    override suspend fun insertEvent(calendarId: Long, event: EventEntity): Long = withContext(Dispatchers.IO) {
        requirePermissions()
        val uri = resolver.insert(CalendarContract.Events.CONTENT_URI, event.toContentValues(calendarId, previous = null))
            ?: error("Android calendar provider did not return an event URI.")
        val eventId = ContentUris.parseId(uri)
        replaceReminders(eventId, event.remindersCsv.toMinutes())
        eventId
    }

    /**
     * Updates an event. Status, visibility and availability are only written when they differ from
     * [previous], the event as last read, so values the app can't show (like the calendar's default
     * visibility) are left alone.
     */
    override suspend fun updateEvent(eventId: Long, calendarId: Long, event: EventEntity, previous: EventEntity?): Unit = withContext(Dispatchers.IO) {
        requirePermissions()
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        val updated = resolver.update(uri, event.toContentValues(calendarId, previous), null, null)
        if (updated <= 0) error("Android event $eventId could not be updated.")
        replaceReminders(eventId, event.remindersCsv.toMinutes())
    }

    override suspend fun deleteEvent(eventId: Long): Unit = withContext(Dispatchers.IO) {
        requirePermissions()
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        resolver.delete(uri, null, null)
        Unit
    }

    override suspend fun cancelRecurringInstance(
        eventId: Long,
        calendarId: Long,
        occurrenceStartMillis: Long,
        occurrenceEndMillis: Long,
        allDay: Boolean,
    ): Unit = withContext(Dispatchers.IO) {
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
        Unit
    }

    override fun toEntity(event: AndroidProviderEvent, collectionHref: String, color: Int, manualColor: Int?): EventEntity =
        androidProviderEventToEntity(event, collectionHref, color, manualColor, zoneId)

    private fun EventEntity.toContentValues(calendarId: Long, previous: EventEntity?): ContentValues =
        ContentValues().apply {
            val providerStartsAtMillis = if (allDay) androidAllDayLocalMillisToProviderMillis(startsAtMillis, zoneId) else startsAtMillis
            val providerEndsAtMillis = if (allDay) androidAllDayLocalMillisToProviderMillis(endsAtMillis, zoneId) else endsAtMillis
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, title.ifBlank { "Untitled event" })
            put(CalendarContract.Events.DESCRIPTION, description?.ifBlank { null })
            put(CalendarContract.Events.EVENT_LOCATION, location?.ifBlank { null })
            put(CalendarContract.Events.DTSTART, providerStartsAtMillis)
            // A timed event keeps its own zone, so a recurring one follows that zone's daylight saving.
            val eventZone = timezoneId?.let { id -> runCatching { ZoneId.of(id).id }.getOrNull() }
            put(CalendarContract.Events.EVENT_TIMEZONE, if (allDay) "UTC" else eventZone ?: TimeZone.getDefault().id)
            put(CalendarContract.Events.ALL_DAY, if (allDay) 1 else 0)
            if (previous == null) {
                put(CalendarContract.Events.STATUS, status.toAndroidStatus())
                // New events use the calendar's default visibility unless one was chosen.
                put(
                    CalendarContract.Events.ACCESS_LEVEL,
                    if (EventClassification.from(classification) == EventClassification.Public) {
                        CalendarContract.Events.ACCESS_DEFAULT
                    } else {
                        classification.toAndroidAccessLevel()
                    },
                )
                put(CalendarContract.Events.AVAILABILITY, transparency.toAndroidAvailability())
            } else {
                if (status.toAndroidStatus() != previous.status.toAndroidStatus()) {
                    put(CalendarContract.Events.STATUS, status.toAndroidStatus())
                }
                if (classification.toAndroidAccessLevel().orPublic() != previous.classification.toAndroidAccessLevel().orPublic()) {
                    put(CalendarContract.Events.ACCESS_LEVEL, classification.toAndroidAccessLevel())
                }
                if (transparency.toAndroidAvailability() != previous.transparency.toAndroidAvailability()) {
                    put(CalendarContract.Events.AVAILABILITY, transparency.toAndroidAvailability())
                }
            }
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
                    // -1 is the provider's "calendar default", not the app's "at the end".
                    if (minutes >= 0) result.getOrPut(eventId) { mutableListOf() } += minutes
                }
            }
        }
        return result
    }

    /**
     * Makes the event's reminders match [minutes]. Rows that stay are left as they are, which keeps
     * their method (e-mail, alarm, ...), and rows with the provider's default offset are kept.
     */
    private fun replaceReminders(eventId: Long, minutes: List<Int>) {
        val wanted = minutes.distinct().sorted()
        val existing = mutableListOf<Pair<Long, Int>>()
        resolver.query(
            CalendarContract.Reminders.CONTENT_URI,
            arrayOf(CalendarContract.Reminders._ID, CalendarContract.Reminders.MINUTES),
            "${CalendarContract.Reminders.EVENT_ID} = ?",
            arrayOf(eventId.toString()),
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) existing += cursor.getLong(0) to cursor.getInt(1)
        }
        val kept = mutableSetOf<Int>()
        existing.forEach { (id, minute) ->
            if (minute < 0) return@forEach
            if (minute in wanted && kept.add(minute)) return@forEach
            resolver.delete(ContentUris.withAppendedId(CalendarContract.Reminders.CONTENT_URI, id), null, null)
        }
        wanted.filterNot { it in kept }.forEach { minute ->
            val values = ContentValues().apply {
                put(CalendarContract.Reminders.EVENT_ID, eventId)
                put(CalendarContract.Reminders.MINUTES, minute)
                put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_DEFAULT)
            }
            resolver.insert(CalendarContract.Reminders.CONTENT_URI, values)
                ?: error("Android calendar provider did not accept the reminder.")
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
            Instant.ofEpochMilli(millis).atZone(zoneId).toLocalDate().format(com.kgs.calendar.time.DateTimeFormatter.BASIC_ISO_DATE)
        } else {
            Instant.ofEpochMilli(millis).atZone(com.kgs.calendar.time.ZoneOffset.UTC)
                .format(com.kgs.calendar.time.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"))
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
                kotlinx.datetime.LocalDate.parse(this, com.kgs.calendar.time.DateTimeFormatter.BASIC_ISO_DATE)
                    .atStartOfDay(zoneId)
                    .toInstant()
                    .toEpochMilli()
            } else {
                kotlinx.datetime.LocalDateTime.parse(
                    removeSuffix("Z"),
                    com.kgs.calendar.time.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"),
                ).atZone(com.kgs.calendar.time.ZoneOffset.UTC).toInstant().toEpochMilli()
            }
        }.getOrNull()

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
            val days = (safeMillis / java.time.Duration.ofDays(1).toMillis()).coerceAtLeast(1)
            return "P${days}D"
        }
        return "PT${safeMillis / 1000L}S"
    }

    private companion object {
        const val ANDROID_CALENDAR_PREFIX = AndroidCalendarProviderClient.ANDROID_CALENDAR_PREFIX
        const val ANDROID_EVENT_PREFIX = AndroidCalendarProviderClient.ANDROID_EVENT_PREFIX_FOR_MAPPING
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

internal fun parseAndroidCalendarDurationMillis(raw: String?): Long? {
    val value = raw?.trim()?.uppercase()?.takeIf(String::isNotEmpty) ?: return null
    runCatching { java.time.Duration.parse(value).toMillis() }.getOrNull()?.let { return it }

    val weekMatch = ANDROID_CALENDAR_WEEK_DURATION.matchEntire(value)
    if (weekMatch != null) {
        val sign = if (weekMatch.groupValues[1] == "-") -1L else 1L
        val weeks = weekMatch.groupValues[2].toLongOrNull() ?: return null
        return runCatching {
            Math.multiplyExact(
                Math.multiplyExact(weeks, 7L),
                java.time.Duration.ofDays(1).toMillis(),
            ) * sign
        }.getOrNull()
    }

    val componentMatch = ANDROID_CALENDAR_COMPONENT_DURATION.matchEntire(value) ?: return null
    val parts = componentMatch.groupValues.drop(2)
    if (parts.none(String::isNotEmpty)) return null
    val sign = if (componentMatch.groupValues[1] == "-") -1L else 1L
    return runCatching {
        val days = parts[0].ifEmpty { "0" }.toLong()
        val hours = parts[1].ifEmpty { "0" }.toLong()
        val minutes = parts[2].ifEmpty { "0" }.toLong()
        val seconds = parts[3].ifEmpty { "0" }.toLong()
        java.time.Duration.ofDays(days)
            .plusHours(hours)
            .plusMinutes(minutes)
            .plusSeconds(seconds)
            .toMillis() * sign
    }.getOrNull()
}

internal fun resolveAndroidProviderEventEndMillis(
    startsAtMillis: Long,
    dtEndMillis: Long?,
    duration: String?,
    allDay: Boolean,
    recurrenceRule: String?,
): Long {
    val durationMillis = parseAndroidCalendarDurationMillis(duration)?.takeIf { it > 0L }
    val durationEnd = durationMillis?.let { parsedDuration ->
        runCatching { Math.addExact(startsAtMillis, parsedDuration) }.getOrNull()
    }
    val validDtEnd = dtEndMillis?.takeIf { it > startsAtMillis }
    val resolved = when {
        !recurrenceRule.isNullOrBlank() && durationEnd != null -> durationEnd
        recurrenceRule.isNullOrBlank() && validDtEnd != null -> validDtEnd
        durationEnd != null -> durationEnd
        validDtEnd != null -> validDtEnd
        allDay -> startsAtMillis + java.time.Duration.ofDays(1).toMillis()
        else -> startsAtMillis + java.time.Duration.ofHours(1).toMillis()
    }
    return max(resolved, startsAtMillis + 1L)
}

private val ANDROID_CALENDAR_WEEK_DURATION = Regex("^([+-])?P(\\d+)W$")
private val ANDROID_CALENDAR_COMPONENT_DURATION =
    Regex("^([+-])?P(?:(\\d+)D)?(?:T)?(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?$")

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
        // Timed provider events always have a zone; without one they'd be written as floating times.
        timezoneId = if (event.allDay) null else event.timezoneId?.takeIf { it.isNotBlank() } ?: zoneId.id,
        remindersCsv = event.reminderMinutes.filter { it >= 0 }.takeIf { it.isNotEmpty() }?.distinct()?.sorted()?.joinToString(","),
        status = event.status.toIcalStatus(),
        classification = event.accessLevel.toIcalClass(),
        transparency = event.availability.toIcalTransparency(),
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
            kotlinx.datetime.LocalDate.parse(this, com.kgs.calendar.time.DateTimeFormatter.BASIC_ISO_DATE)
                .atStartOfDay(zoneId)
                .toInstant()
                .toEpochMilli()
        } else {
            kotlinx.datetime.LocalDateTime.parse(
                removeSuffix("Z"),
                com.kgs.calendar.time.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"),
            ).atZone(com.kgs.calendar.time.ZoneOffset.UTC).toInstant().toEpochMilli()
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
        CalendarContract.Events.STATUS_TENTATIVE -> EventStatus.Tentative.value
        CalendarContract.Events.STATUS_CANCELED -> EventStatus.Cancelled.value
        CalendarContract.Events.STATUS_CONFIRMED -> EventStatus.Confirmed.value
        else -> null
    }

private fun Int?.toIcalClass(): String? =
    when (this) {
        CalendarContract.Events.ACCESS_PRIVATE -> EventClassification.Private.value
        CalendarContract.Events.ACCESS_CONFIDENTIAL -> EventClassification.Confidential.value
        CalendarContract.Events.ACCESS_PUBLIC -> EventClassification.Public.value
        else -> null
    }

private fun Int?.toIcalTransparency(): String? =
    when (this) {
        CalendarContract.Events.AVAILABILITY_FREE -> EventTransparency.Transparent.value
        CalendarContract.Events.AVAILABILITY_BUSY,
        CalendarContract.Events.AVAILABILITY_TENTATIVE,
        -> EventTransparency.Opaque.value
        else -> null
    }

private fun String?.toAndroidStatus(): Int =
    when (val status = EventStatus.from(this)) {
        EventStatus.Tentative -> CalendarContract.Events.STATUS_TENTATIVE
        EventStatus.Cancelled -> CalendarContract.Events.STATUS_CANCELED
        is EventStatus.Other ->
            if (status.value.equals("CANCELED", ignoreCase = true)) {
                CalendarContract.Events.STATUS_CANCELED
            } else {
                CalendarContract.Events.STATUS_CONFIRMED
            }
        else -> CalendarContract.Events.STATUS_CONFIRMED
    }

private fun String?.toAndroidAccessLevel(): Int =
    when (EventClassification.from(this)) {
        EventClassification.Private -> CalendarContract.Events.ACCESS_PRIVATE
        EventClassification.Confidential -> CalendarContract.Events.ACCESS_CONFIDENTIAL
        EventClassification.Public -> CalendarContract.Events.ACCESS_PUBLIC
        else -> CalendarContract.Events.ACCESS_DEFAULT
    }

/** The calendar's default visibility counts as public, which is what the app shows for it. */
private fun Int.orPublic(): Int =
    if (this == CalendarContract.Events.ACCESS_DEFAULT) CalendarContract.Events.ACCESS_PUBLIC else this

private fun String?.toAndroidAvailability(): Int =
    when (EventTransparency.from(this)) {
        EventTransparency.Transparent -> CalendarContract.Events.AVAILABILITY_FREE
        else -> CalendarContract.Events.AVAILABILITY_BUSY
    }
