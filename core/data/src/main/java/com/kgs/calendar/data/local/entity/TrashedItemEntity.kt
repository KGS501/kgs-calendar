package com.kgs.calendar.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kgs.calendar.domain.model.ComponentType
import com.kgs.calendar.domain.model.SourceType

/**
 * A whole event or task the user deleted in the app, kept for "Recently deleted" (see
 * [com.kgs.calendar.data.trash.TrashBin]). It has no foreign keys on purpose: the snapshot must
 * outlive the collection and the account it came from.
 */
@Entity(
    tableName = "trashed_items",
    indices = [Index("deletedAtMillis")],
)
data class TrashedItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val componentType: ComponentType,
    val uid: String,
    val collectionHref: String,
    val accountId: String,
    val sourceType: SourceType,
    /** The href the item had when it was deleted. */
    val resourceHref: String,
    /** The calendar provider event id for Android device calendar events. */
    val providerEventId: Long? = null,
    /** The resource's iCalendar data; generated from the stored item for device calendar events. */
    val rawIcs: String,
    val title: String,
    /** Event start, or task start/due, for display. */
    val startMillis: Long?,
    /** False for all-day events and date-only tasks. */
    val hasTime: Boolean,
    val collectionName: String,
    val collectionColor: Int,
    /** The item's own colour, which lives outside the iCalendar data. */
    val manualColor: Int? = null,
    val deletedAtMillis: Long,
)
