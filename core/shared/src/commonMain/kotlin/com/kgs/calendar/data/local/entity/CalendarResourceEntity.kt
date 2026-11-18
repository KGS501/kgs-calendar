package com.kgs.calendar.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kgs.calendar.domain.model.ComponentType

@Entity(
    tableName = "calendar_resources",
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
        Index("componentType"),
    ],
)
data class CalendarResourceEntity(
    @PrimaryKey val href: String,
    val collectionHref: String,
    val etag: String?,
    val componentType: ComponentType,
    val uid: String,
    val rawIcs: String,
    val syncError: String? = null,
    /**
     * The server's newer version when it could not be imported. [etag], [rawIcs] and the event or
     * task row stay at the last version that could, so a local edit based on them fails its If-Match
     * instead of silently overwriting a version nobody saw here.
     */
    val quarantinedEtag: String? = null,
    val quarantinedRawIcs: String? = null,
)
