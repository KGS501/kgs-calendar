package com.kgs.calendar.data.provider

import com.kgs.calendar.data.local.entity.EventEntity

/** The desktop has no device calendar provider: it has no calendars and accepts no writes. */
class NoDeviceCalendarProviderClient : AndroidCalendarProviderClient() {
    override fun hasCalendarPermissions(): Boolean = false

    override suspend fun listCalendars(includeDisabled: Boolean): List<AndroidProviderCalendar> = emptyList()

    override suspend fun listEvents(calendarIds: Set<Long>, syncStartMillis: Long, syncEndMillis: Long): List<AndroidProviderEvent> =
        emptyList()

    override suspend fun insertEvent(calendarId: Long, event: EventEntity): Long = unavailable()

    override suspend fun updateEvent(eventId: Long, calendarId: Long, event: EventEntity, previous: EventEntity?) = unavailable()

    override suspend fun deleteEvent(eventId: Long) = unavailable()

    override suspend fun cancelRecurringInstance(
        eventId: Long,
        calendarId: Long,
        occurrenceStartMillis: Long,
        occurrenceEndMillis: Long,
        allDay: Boolean,
    ) = unavailable()

    private fun unavailable(): Nothing = error("This device has no calendar provider.")
}
