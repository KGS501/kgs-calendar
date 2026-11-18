package com.kgs.calendar.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kgs.calendar.domain.model.ComponentType
import com.kgs.calendar.domain.model.SourceType
import com.kgs.calendar.domain.trash.TrashOrigin
import com.kgs.calendar.domain.trash.TrashRetention

/**
 * A whole event or task in "Recently deleted" (see [com.kgs.calendar.data.trash.TrashBin]): either a
 * snapshot the app wrote when the user deleted it ([TrashOrigin.LocalSnapshot]), or a cached object
 * of a Nextcloud account's server-side trash bin ([TrashOrigin.ServerTrashBin]), which may have
 * been deleted on another device. It has no foreign keys on purpose: a snapshot must outlive the
 * collection and the account it came from.
 */
@Entity(
    tableName = "trashed_items",
    indices = [
        Index("deletedAtMillis"),
        Index(value = ["accountId", "serverHref"], unique = true),
    ],
)
data class TrashedItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val componentType: ComponentType,
    val uid: String,
    /** The calendar the item was deleted from. */
    val collectionHref: String,
    val accountId: String,
    val sourceType: SourceType,
    /**
     * The href the item had when it was deleted. Server trash items don't expose it, so they carry
     * the one of the local snapshot they replaced, or else [serverHref].
     */
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
    @ColumnInfo(defaultValue = "local")
    val origin: TrashOrigin = TrashOrigin.LocalSnapshot,
    /** The object's href in the server trash bin (`…/trashbin/objects/<id>.ics`); null for local snapshots. */
    val serverHref: String? = null,
    /**
     * When the item leaves the trash: [TrashRetention.DAYS] after the delete for local snapshots, the
     * server's retention after the delete for server trash items.
     */
    @ColumnInfo(defaultValue = "0")
    val expiresAtMillis: Long = TrashRetention.expiresAtMillis(deletedAtMillis),
)
