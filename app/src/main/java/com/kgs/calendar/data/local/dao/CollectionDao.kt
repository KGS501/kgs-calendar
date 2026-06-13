package com.kgs.calendar.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.kgs.calendar.data.local.entity.CollectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CollectionDao {
    @Query("SELECT * FROM collections ORDER BY sortOrder, displayName")
    fun observeAll(): Flow<List<CollectionEntity>>

    @Query("SELECT * FROM collections WHERE isEnabled = 1 ORDER BY sortOrder, displayName")
    fun observeEnabled(): Flow<List<CollectionEntity>>

    @Query("SELECT * FROM collections ORDER BY sortOrder, displayName")
    suspend fun all(): List<CollectionEntity>

    @Query("SELECT * FROM collections WHERE supportsEvents = 1 AND isEnabled = 1 ORDER BY sortOrder, displayName")
    suspend fun eventCollections(): List<CollectionEntity>

    @Query("SELECT * FROM collections WHERE supportsTasks = 1 AND isEnabled = 1 ORDER BY sortOrder, displayName")
    suspend fun taskCollections(): List<CollectionEntity>

    @Query("UPDATE collections SET sortOrder = :order WHERE href = :href")
    suspend fun updateSortOrder(href: String, order: Int)

    @Query("SELECT * FROM collections WHERE href = :href")
    suspend fun get(href: String): CollectionEntity?

    @Query("SELECT * FROM collections WHERE accountId = :accountId")
    suspend fun forAccount(accountId: String): List<CollectionEntity>

    @Upsert
    suspend fun upsertAll(collections: List<CollectionEntity>)

    @Query("DELETE FROM collections WHERE href = :href")
    suspend fun delete(href: String)

    @Query("UPDATE collections SET syncToken = :syncToken, ctag = :ctag WHERE href = :href")
    suspend fun updateSyncMarkers(href: String, syncToken: String?, ctag: String?)

    @Query("UPDATE collections SET isEnabled = :enabled WHERE href = :href")
    suspend fun updateEnabled(href: String, enabled: Boolean)

    @Query("UPDATE collections SET displayName = :displayName, customDisplayName = :customDisplayName, color = :color, customColor = :customColor WHERE href = :href")
    suspend fun updateAppearance(href: String, displayName: String, customDisplayName: String?, color: Int, customColor: Int?)
}
