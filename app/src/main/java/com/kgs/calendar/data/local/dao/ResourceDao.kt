package com.kgs.calendar.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.kgs.calendar.data.local.entity.CalendarResourceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ResourceDao {
    @Query("SELECT * FROM calendar_resources WHERE collectionHref = :collectionHref")
    suspend fun forCollection(collectionHref: String): List<CalendarResourceEntity>

    @Query("SELECT * FROM calendar_resources")
    suspend fun all(): List<CalendarResourceEntity>

    @Query("SELECT * FROM calendar_resources WHERE componentType = :componentType")
    suspend fun forComponentType(componentType: String): List<CalendarResourceEntity>

    @Query(
        """
        SELECT * FROM calendar_resources
        WHERE collectionHref = :collectionHref
          AND etag IS NULL
          AND (syncError IS NULL OR syncError = '')
        """,
    )
    suspend fun missingEtagForCollection(collectionHref: String): List<CalendarResourceEntity>

    @Query(
        """
        SELECT resource.* FROM calendar_resources AS resource
        INNER JOIN collections AS collection ON collection.href = resource.collectionHref
        WHERE collection.sourceType = 'caldav'
          AND resource.uid != ''
          AND EXISTS (
            SELECT 1 FROM calendar_resources AS duplicate
            WHERE duplicate.collectionHref = resource.collectionHref
              AND duplicate.componentType = resource.componentType
              AND duplicate.uid = resource.uid
              AND duplicate.href != resource.href
          )
        """,
    )
    suspend fun duplicateCalDavCandidates(): List<CalendarResourceEntity>

    @Query("SELECT * FROM calendar_resources WHERE syncError IS NOT NULL AND syncError != '' ORDER BY collectionHref ASC, href ASC")
    fun observeSyncErrors(): Flow<List<CalendarResourceEntity>>

    @Query("SELECT * FROM calendar_resources WHERE href = :href")
    suspend fun get(href: String): CalendarResourceEntity?

    @Upsert
    suspend fun upsert(resource: CalendarResourceEntity)

    @Query("DELETE FROM calendar_resources WHERE href = :href")
    suspend fun delete(href: String)

    @Query("UPDATE calendar_resources SET etag = :etag, syncError = NULL WHERE href = :href")
    suspend fun markSynced(href: String, etag: String?)

    @Query("UPDATE calendar_resources SET syncError = :error WHERE href = :href")
    suspend fun setSyncError(href: String, error: String)
}
