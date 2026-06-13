package com.kgs.calendar.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pending_mutations",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("accountId"),
        Index("resourceHref"),
        Index("collectionHref"),
    ],
)
data class PendingMutationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: String,
    val collectionHref: String,
    val resourceHref: String,
    val componentType: String,
    val action: String,
    val payloadIcs: String?,
    val baseEtag: String?,
    val createdAtMillis: Long,
)
