package com.kgs.calendar.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.kgs.calendar.data.local.entity.PendingMutationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingMutationDao {
    @Query("SELECT COUNT(*) FROM pending_mutations")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM pending_mutations ORDER BY createdAtMillis ASC, id ASC")
    fun observeAll(): Flow<List<PendingMutationEntity>>

    @Query("SELECT * FROM pending_mutations ORDER BY createdAtMillis ASC, id ASC")
    suspend fun all(): List<PendingMutationEntity>

    @Query(
        """
        SELECT * FROM pending_mutations
        WHERE createdAtMillis >= :createdAtMillis
        ORDER BY createdAtMillis ASC, id ASC
        """,
    )
    suspend fun createdSince(createdAtMillis: Long): List<PendingMutationEntity>

    @Query("SELECT * FROM pending_mutations WHERE accountId = :accountId ORDER BY createdAtMillis ASC, id ASC")
    suspend fun allForAccount(accountId: String): List<PendingMutationEntity>

    @Insert
    suspend fun insert(mutation: PendingMutationEntity): Long

    @Query("DELETE FROM pending_mutations WHERE resourceHref = :resourceHref AND action = :action")
    suspend fun deleteForResourceAndAction(resourceHref: String, action: String)

    @Query("DELETE FROM pending_mutations WHERE resourceHref = :resourceHref")
    suspend fun deleteForResource(resourceHref: String)

    @Query("DELETE FROM pending_mutations WHERE collectionHref = :collectionHref")
    suspend fun deleteForCollection(collectionHref: String)

    @Query("UPDATE pending_mutations SET payloadIcs = :payloadIcs WHERE id = :id")
    suspend fun updatePayload(id: Long, payloadIcs: String)

    @Delete
    suspend fun delete(mutation: PendingMutationEntity)
}
