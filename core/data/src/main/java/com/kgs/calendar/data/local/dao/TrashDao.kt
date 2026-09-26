package com.kgs.calendar.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.kgs.calendar.data.local.entity.TrashedItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrashDao {
    @Query("SELECT * FROM trashed_items ORDER BY deletedAtMillis DESC, id DESC")
    fun observeAll(): Flow<List<TrashedItemEntity>>

    @Query("SELECT * FROM trashed_items ORDER BY deletedAtMillis DESC, id DESC")
    suspend fun all(): List<TrashedItemEntity>

    @Query("SELECT * FROM trashed_items WHERE id = :id")
    suspend fun get(id: Long): TrashedItemEntity?

    @Insert
    suspend fun insert(item: TrashedItemEntity): Long

    @Query("DELETE FROM trashed_items WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM trashed_items")
    suspend fun deleteAll()

    @Query("DELETE FROM trashed_items WHERE deletedAtMillis < :cutoffMillis")
    suspend fun deleteDeletedBefore(cutoffMillis: Long): Int
}
