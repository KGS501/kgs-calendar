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
    /**
     * Which occurrences this change covers, for the pending-sync indicators only (see
     * [com.kgs.calendar.domain.sync.PendingOccurrenceScope]); null means the whole resource.
     */
    val occurrenceScope: String? = null,
    /**
     * The mutation that must be acknowledged by its server first: a calendar move's DELETE of its
     * source waits for the PUT of its destination. The acknowledgement clears this, so a set value
     * pointing at a row that no longer exists means the prerequisite was given up, not uploaded.
     */
    val dependsOnMutationId: Long? = null,
)
