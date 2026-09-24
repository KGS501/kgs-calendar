package com.kgs.calendar.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kgs.calendar.domain.model.ComponentType
import com.kgs.calendar.domain.model.MutationAction

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
    val componentType: ComponentType,
    val action: MutationAction,
    val payloadIcs: String?,
    val baseEtag: String?,
    val createdAtMillis: Long,
)
