package com.kgs.calendar.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "events",
    foreignKeys = [
        ForeignKey(
            entity = CollectionEntity::class,
            parentColumns = ["href"],
            childColumns = ["collectionHref"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("collectionHref"),
        Index("startsAtMillis"),
        Index("endsAtMillis"),
        Index("uid"),
    ],
)
data class EventEntity(
    val uid: String,
    val collectionHref: String,
    @PrimaryKey
    val resourceHref: String,
    val title: String,
    val description: String?,
    val location: String?,
    val locationMapVerified: Boolean? = null,
    val startsAtMillis: Long,
    val endsAtMillis: Long,
    val allDay: Boolean,
    val recurrenceRule: String?,
    val isRecurring: Boolean,
    val exDatesCsv: String? = null,
    /** Explicit recurrence inclusions from RFC 5545 RDATE values. */
    val rDatesCsv: String? = null,
    /** IANA TZID used by the source for floating/local DTSTART values. */
    val timezoneId: String? = null,
    /** JSON representation of RECURRENCE-ID VEVENT components in this resource. */
    val recurrenceOverridesJson: String? = null,
    /** iCalendar SEQUENCE used by scheduling-aware servers and clients. */
    val sequence: Int = 0,
    /** Comma-separated reminder offsets in minutes-before-start (e.g. "0,15,60"). */
    val remindersCsv: String? = null,
    /** Raw iCal STATUS, typically CONFIRMED / TENTATIVE / CANCELLED. */
    val status: String? = null,
    /** Raw iCal CLASS, typically PUBLIC / CONFIDENTIAL / PRIVATE. */
    val classification: String? = null,
    /** Raw iCal TRANSP, OPAQUE for busy or TRANSPARENT for free. */
    val transparency: String? = null,
    /** Comma-separated iCal CATEGORIES. */
    val categories: String? = null,
    /** JSON object with organizer name/email. */
    val organizerJson: String? = null,
    /** JSON array with attendee name/email/partstat/role/rsvp. */
    val attendeesJson: String? = null,
    val color: Int,
    val manualColor: Int? = null,
    val syncError: String? = null,
)
