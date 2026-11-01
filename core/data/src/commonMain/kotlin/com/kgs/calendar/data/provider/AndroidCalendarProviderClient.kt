package com.kgs.calendar.data.provider

import com.kgs.calendar.domain.time.orDeviceZone
import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.domain.model.EventClassification
import com.kgs.calendar.domain.model.EventStatus
import com.kgs.calendar.domain.model.EventTransparency
import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.max

/**
 * The device's own calendars (Android's calendar provider). The provider itself is reached by a
 * platform implementation; platforms without one use a client that has no calendars.
 */
abstract class AndroidCalendarProviderClient protected constructor(
    /** Null follows the device's zone (see [orDeviceZone]). */
    private val fixedZoneId: ZoneId? = null,
) {
    protected val zoneId: ZoneId get() = fixedZoneId.orDeviceZone()

    abstract fun hasCalendarPermissions(): Boolean

    abstract suspend fun listCalendars(includeDisabled: Boolean = false): List<AndroidProviderCalendar>

    suspend fun listHiddenOrNotSyncedCalendars(): List<AndroidProviderCalendar> =
        listCalendars(includeDisabled = true)
            .filter { !it.syncEvents || !it.visible }
            .sortedWith(compareBy<AndroidProviderCalendar> { it.accountName.orEmpty().lowercase() }.thenBy { it.displayName.lowercase() })

    abstract suspend fun listEvents(calendarIds: Set<Long>, syncStartMillis: Long, syncEndMillis: Long): List<AndroidProviderEvent>

    abstract suspend fun insertEvent(calendarId: Long, event: EventEntity): Long

    /**
     * Updates an event. Status, visibility and availability are only written when they differ from
     * [previous], the event as last read, so values the app can't show (like the calendar's default
     * visibility) are left alone.
     */
    abstract suspend fun updateEvent(eventId: Long, calendarId: Long, event: EventEntity, previous: EventEntity?)

    abstract suspend fun deleteEvent(eventId: Long)

    abstract suspend fun cancelRecurringInstance(eventId: Long, calendarId: Long, occurrenceStartMillis: Long, occurrenceEndMillis: Long, allDay: Boolean)

    fun toEntity(event: AndroidProviderEvent, collectionHref: String, color: Int, manualColor: Int? = null): EventEntity =
        androidProviderEventToEntity(event, collectionHref, color, manualColor, zoneId)

    fun calendarHref(calendarId: Long): String = "$ANDROID_CALENDAR_PREFIX$calendarId"

    fun eventHref(eventId: Long): String = androidEventHref(eventId)

    fun eventIdFromHref(href: String): Long? =
        href.removePrefix(ANDROID_EVENT_PREFIX).takeIf { href.startsWith(ANDROID_EVENT_PREFIX) }?.toLongOrNull()

    fun calendarIdFromHref(href: String): Long? =
        href.removePrefix(ANDROID_CALENDAR_PREFIX).takeIf { href.startsWith(ANDROID_CALENDAR_PREFIX) }?.toLongOrNull()

    protected fun fallbackColor(id: Long): Int =
        DEFAULT_COLORS[(id % DEFAULT_COLORS.size).toInt()]

    protected fun androidEventHref(eventId: Long): String = "$ANDROID_EVENT_PREFIX$eventId"


    companion object {
        const val ANDROID_ACCOUNT_ID = "android-provider"
        const val ANDROID_ACCOUNT_SERVER_URL = "android://calendar-provider"
        const val ANDROID_ACCOUNT_USERNAME = "Android device"
        const val ANDROID_CALENDAR_PREFIX = "android://calendar/"
        internal const val ANDROID_EVENT_PREFIX_FOR_MAPPING = "android://event/"
        internal const val ANDROID_EVENT_PREFIX = ANDROID_EVENT_PREFIX_FOR_MAPPING
        private val DEFAULT_COLORS = listOf(
            rgb(23, 107, 93),
            rgb(26, 115, 232),
            rgb(185, 81, 64),
            rgb(120, 85, 190),
            rgb(238, 147, 45),
        )
    }
}

internal fun parseAndroidCalendarDurationMillis(raw: String?): Long? {
    val value = raw?.trim()?.uppercase()?.takeIf(String::isNotEmpty) ?: return null
    runCatching { Duration.parse(value).toMillis() }.getOrNull()?.let { return it }

    val weekMatch = ANDROID_CALENDAR_WEEK_DURATION.matchEntire(value)
    if (weekMatch != null) {
        val sign = if (weekMatch.groupValues[1] == "-") -1L else 1L
        val weeks = weekMatch.groupValues[2].toLongOrNull() ?: return null
        return runCatching {
            Math.multiplyExact(
                Math.multiplyExact(weeks, 7L),
                Duration.ofDays(1).toMillis(),
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
        Duration.ofDays(days)
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
        allDay -> startsAtMillis + Duration.ofDays(1).toMillis()
        else -> startsAtMillis + Duration.ofHours(1).toMillis()
    }
    return max(resolved, startsAtMillis + 1L)
}

private val ANDROID_CALENDAR_WEEK_DURATION = Regex("^([+-])?P(\\d+)W$")
private val ANDROID_CALENDAR_COMPONENT_DURATION =
    Regex("^([+-])?P(?:(\\d+)D)?(?:T)?(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?$")

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
        get() = syncEvents && accessLevel >= ProviderContract.CAL_ACCESS_CONTRIBUTOR

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
    val timezoneId: String? = null,
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

private fun Int?.toIcalStatus(): String? =
    when (this) {
        ProviderContract.STATUS_TENTATIVE -> EventStatus.Tentative.value
        ProviderContract.STATUS_CANCELED -> EventStatus.Cancelled.value
        ProviderContract.STATUS_CONFIRMED -> EventStatus.Confirmed.value
        else -> null
    }

private fun Int?.toIcalClass(): String? =
    when (this) {
        ProviderContract.ACCESS_PRIVATE -> EventClassification.Private.value
        ProviderContract.ACCESS_CONFIDENTIAL -> EventClassification.Confidential.value
        ProviderContract.ACCESS_PUBLIC -> EventClassification.Public.value
        else -> null
    }

private fun Int?.toIcalTransparency(): String? =
    when (this) {
        ProviderContract.AVAILABILITY_FREE -> EventTransparency.Transparent.value
        ProviderContract.AVAILABILITY_BUSY,
        ProviderContract.AVAILABILITY_TENTATIVE,
        -> EventTransparency.Opaque.value
        else -> null
    }

internal fun String?.toAndroidStatus(): Int =
    when (val status = EventStatus.from(this)) {
        EventStatus.Tentative -> ProviderContract.STATUS_TENTATIVE
        EventStatus.Cancelled -> ProviderContract.STATUS_CANCELED
        is EventStatus.Other ->
            if (status.value.equals("CANCELED", ignoreCase = true)) {
                ProviderContract.STATUS_CANCELED
            } else {
                ProviderContract.STATUS_CONFIRMED
            }
        else -> ProviderContract.STATUS_CONFIRMED
    }

internal fun String?.toAndroidAccessLevel(): Int =
    when (EventClassification.from(this)) {
        EventClassification.Private -> ProviderContract.ACCESS_PRIVATE
        EventClassification.Confidential -> ProviderContract.ACCESS_CONFIDENTIAL
        EventClassification.Public -> ProviderContract.ACCESS_PUBLIC
        else -> ProviderContract.ACCESS_DEFAULT
    }

/** The calendar's default visibility counts as public, which is what the app shows for it. */
internal fun Int.orPublic(): Int =
    if (this == ProviderContract.ACCESS_DEFAULT) ProviderContract.ACCESS_PUBLIC else this

internal fun String?.toAndroidAvailability(): Int =
    when (EventTransparency.from(this)) {
        EventTransparency.Transparent -> ProviderContract.AVAILABILITY_FREE
        else -> ProviderContract.AVAILABILITY_BUSY
    }

private fun rgb(red: Int, green: Int, blue: Int): Int =
    (0xFF shl 24) or (red shl 16) or (green shl 8) or blue

/** The calendar provider's column values (`android.provider.CalendarContract`), which are stable platform API. */
internal object ProviderContract {
    const val STATUS_TENTATIVE = 0
    const val STATUS_CONFIRMED = 1
    const val STATUS_CANCELED = 2
    const val ACCESS_DEFAULT = 0
    const val ACCESS_CONFIDENTIAL = 1
    const val ACCESS_PRIVATE = 2
    const val ACCESS_PUBLIC = 3
    const val AVAILABILITY_BUSY = 0
    const val AVAILABILITY_FREE = 1
    const val AVAILABILITY_TENTATIVE = 2
    const val CAL_ACCESS_CONTRIBUTOR = 500
}
