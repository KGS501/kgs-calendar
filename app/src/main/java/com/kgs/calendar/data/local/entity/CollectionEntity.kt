package com.kgs.calendar.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kgs.calendar.data.SourceType

@Entity(
    tableName = "collections",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("accountId")],
)
data class CollectionEntity(
    @PrimaryKey val href: String,
    val accountId: String,
    val displayName: String,
    val color: Int,
    val supportsEvents: Boolean,
    val supportsTasks: Boolean,
    val syncToken: String?,
    val ctag: String?,
    val isEnabled: Boolean = true,
    val sortOrder: Int = 0,
    val readOnly: Boolean = false,
    val remoteDisplayName: String? = null,
    val customDisplayName: String? = null,
    val automaticColor: Int? = null,
    val sourceColor: Int? = null,
    val customColor: Int? = null,
    val sourceType: String = SourceType.CalDav,
    val externalId: String? = null,
    val capabilitiesJson: String? = null,
)
