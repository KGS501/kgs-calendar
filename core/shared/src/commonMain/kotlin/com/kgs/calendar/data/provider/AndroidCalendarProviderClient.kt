package com.kgs.calendar.data.provider

import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.json.JSONObject

/**
 * The device's own calendars (Android's CalendarProvider). The Android app implements it in
 * `:core:data`; platforms without one use [None].
 */
interface AndroidCalendarProviderClient {
    fun hasCalendarPermissions(): Boolean

    suspend fun listCalendars(includeDisabled: Boolean = false): List<AndroidProviderCalendar>

    suspend fun listHiddenOrNotSyncedCalendars(): List<AndroidProviderCalendar>

    suspend fun listEvents(calendarIds: Set<Long>, syncStartMillis: Long, syncEndMillis: Long): List<AndroidProviderEvent>

    suspend fun insertEvent(calendarId: Long, event: EventEntity): Long

    /**
     * Updates an event. Status, visibility and availability are only written when they differ from
     * [previous], the event as last read.
     */
    suspend fun updateEvent(eventId: Long, calendarId: Long, event: EventEntity, previous: EventEntity?)

    suspend fun deleteEvent(eventId: Long)

    suspend fun cancelRecurringInstance(
        eventId: Long,
        calendarId: Long,
        occurrenceStartMillis: Long,
        occurrenceEndMillis: Long,
        allDay: Boolean,
    )

    fun toEntity(event: AndroidProviderEvent, collectionHref: String, color: Int, manualColor: Int? = null): EventEntity

    fun calendarHref(calendarId: Long): String = "$ANDROID_CALENDAR_PREFIX$calendarId"

    fun eventHref(eventId: Long): String = "$ANDROID_EVENT_PREFIX_FOR_MAPPING$eventId"

    fun eventIdFromHref(href: String): Long? =
        href.removePrefix(ANDROID_EVENT_PREFIX_FOR_MAPPING).takeIf { href.startsWith(ANDROID_EVENT_PREFIX_FOR_MAPPING) }?.toLongOrNull()

    fun calendarIdFromHref(href: String): Long? =
        href.removePrefix(ANDROID_CALENDAR_PREFIX).takeIf { href.startsWith(ANDROID_CALENDAR_PREFIX) }?.toLongOrNull()

    companion object {
        const val ANDROID_ACCOUNT_ID = "android-provider"
        const val ANDROID_ACCOUNT_SERVER_URL = "android://calendar-provider"
        const val ANDROID_ACCOUNT_USERNAME = "Android device"
        const val ANDROID_CALENDAR_PREFIX = "android://calendar/"
        const val ANDROID_EVENT_PREFIX_FOR_MAPPING = "android://event/"
    }

    /** No device calendars: nothing is listed and writes fail. */
    object None : AndroidCalendarProviderClient {
        override fun hasCalendarPermissions(): Boolean = false
        override suspend fun listCalendars(includeDisabled: Boolean): List<AndroidProviderCalendar> = emptyList()
        override suspend fun listHiddenOrNotSyncedCalendars(): List<AndroidProviderCalendar> = emptyList()
        override suspend fun listEvents(calendarIds: Set<Long>, syncStartMillis: Long, syncEndMillis: Long): List<AndroidProviderEvent> = emptyList()
        override suspend fun insertEvent(calendarId: Long, event: EventEntity): Long = error("No device calendars on this platform.")
        override suspend fun updateEvent(eventId: Long, calendarId: Long, event: EventEntity, previous: EventEntity?) = error("No device calendars on this platform.")
        override suspend fun deleteEvent(eventId: Long) = error("No device calendars on this platform.")
        override suspend fun cancelRecurringInstance(eventId: Long, calendarId: Long, occurrenceStartMillis: Long, occurrenceEndMillis: Long, allDay: Boolean) =
            error("No device calendars on this platform.")
        override fun toEntity(event: AndroidProviderEvent, collectionHref: String, color: Int, manualColor: Int?): EventEntity =
            error("No device calendars on this platform.")
    }
}

/** CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR */
private const val CAL_ACCESS_CONTRIBUTOR = 500

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
        get() = syncEvents && accessLevel >= CAL_ACCESS_CONTRIBUTOR

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
