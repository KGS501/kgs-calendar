package com.kgs.calendar.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

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
    val componentType: String,
    val uid: String,
    val rawIcs: String,
    val syncError: String? = null,
)
