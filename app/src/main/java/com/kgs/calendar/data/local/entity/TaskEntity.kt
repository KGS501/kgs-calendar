package com.kgs.calendar.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tasks",
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
        Index("uid"),
        Index("dueAtMillis"),
        Index("startAtMillis"),
        Index("isCompleted"),
        Index("parentUid"),
    ],
)
data class TaskEntity(
    val uid: String,
    val collectionHref: String,
    @PrimaryKey
    val resourceHref: String,
    val title: String,
    val notes: String?,
    val location: String? = null,
    val locationMapVerified: Boolean? = null,
    val url: String? = null,
    val categories: String? = null,
    val dueAtMillis: Long?,
    val dueHasTime: Boolean = true,
    val startAtMillis: Long?,
    val startHasTime: Boolean = true,
    val completedAtMillis: Long?,
    val isCompleted: Boolean,
    /**
     * Raw iCal STATUS value preserved verbatim from the server. One of
     * NEEDS-ACTION / IN-PROCESS / COMPLETED / CANCELLED. Null when the
     * server omitted STATUS — in that case [isCompleted] determines the
     * effective state. Stored in addition to [isCompleted] so that
     * editing IN-PROCESS or CANCELLED round-trips correctly.
     */
    val status: String? = null,
    val priority: Int?,
    val percentComplete: Int? = null,
    /** UID referenced by RELATED-TO;RELTYPE=PARENT for CalDAV task hierarchies. */
    val parentUid: String? = null,
    val recurrenceRule: String? = null,
    val exDatesCsv: String? = null,
    val rDatesCsv: String? = null,
    val timezoneId: String? = null,
    val recurrenceOverridesJson: String? = null,
    val sequence: Int = 0,
    /** Comma-separated reminder offsets in minutes-before-due (e.g. "0,15,60"). */
    val remindersCsv: String? = null,
    val color: Int,
    val manualColor: Int? = null,
    val syncError: String? = null,
)

/**
 * CalDAV requires DTSTART and DUE to use the same value type when both are
 * present. The task editor models start-only and end-only timed tasks, so drop
 * the date-only counterpart when stale local data contains a mixed pair.
 */
fun TaskEntity.withValidIcalSchedule(): TaskEntity {
    val normalizedStartHasTime = startAtMillis != null && startHasTime
    val normalizedDueHasTime = dueAtMillis != null && dueHasTime
    return when {
        startAtMillis != null && dueAtMillis != null && normalizedStartHasTime != normalizedDueHasTime ->
            if (normalizedStartHasTime) {
                copy(startHasTime = true, dueAtMillis = null, dueHasTime = false)
            } else {
                copy(startAtMillis = null, startHasTime = false, dueHasTime = true)
            }

        else -> copy(
            startHasTime = normalizedStartHasTime,
            dueHasTime = normalizedDueHasTime,
        )
    }
}
