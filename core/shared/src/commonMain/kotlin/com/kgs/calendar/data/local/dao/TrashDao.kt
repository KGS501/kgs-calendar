package com.kgs.calendar.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.kgs.calendar.data.local.entity.TrashedItemEntity
import com.kgs.calendar.domain.trash.TrashOrigin
import kotlinx.coroutines.flow.Flow

@Dao
interface TrashDao {
    @Query("SELECT * FROM trashed_items ORDER BY deletedAtMillis DESC, id DESC")
    fun observeAll(): Flow<List<TrashedItemEntity>>

    @Query("SELECT * FROM trashed_items ORDER BY deletedAtMillis DESC, id DESC")
    suspend fun all(): List<TrashedItemEntity>

    @Query("SELECT * FROM trashed_items WHERE id = :id")
    suspend fun get(id: Long): TrashedItemEntity?

    @Query("SELECT * FROM trashed_items WHERE accountId = :accountId AND origin = :origin")
    suspend fun forAccount(accountId: String, origin: TrashOrigin): List<TrashedItemEntity>

    @Query("SELECT * FROM trashed_items WHERE origin = :origin ORDER BY deletedAtMillis DESC, id DESC")
    suspend fun withOrigin(origin: TrashOrigin): List<TrashedItemEntity>

    @Insert
    suspend fun insert(item: TrashedItemEntity): Long

    @Upsert
    suspend fun upsert(item: TrashedItemEntity): Long

    @Query("DELETE FROM trashed_items WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM trashed_items WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)

    @Query("DELETE FROM trashed_items")
    suspend fun deleteAll()

    @Query("DELETE FROM trashed_items WHERE origin = :origin")
    suspend fun deleteWithOrigin(origin: TrashOrigin)

    @Query("DELETE FROM trashed_items WHERE accountId = :accountId AND origin = :origin")
    suspend fun deleteForAccount(accountId: String, origin: TrashOrigin)

    /** Local snapshots deleted before [cutoffMillis]. */
    @Query("DELETE FROM trashed_items WHERE origin = 'local' AND deletedAtMillis < :cutoffMillis")
    suspend fun deleteDeletedBefore(cutoffMillis: Long): Int

    /** Server trash items past the server's retention; the server purges them on its own. */
    @Query("DELETE FROM trashed_items WHERE origin = 'server' AND expiresAtMillis < :nowMillis")
    suspend fun deleteServerItemsExpiredBefore(nowMillis: Long): Int

    /** Server trash items of accounts that were removed; they can't be restored any more. */
    @Query("DELETE FROM trashed_items WHERE origin = 'server' AND accountId NOT IN (SELECT id FROM accounts)")
    suspend fun deleteServerItemsWithoutAccount(): Int
}
