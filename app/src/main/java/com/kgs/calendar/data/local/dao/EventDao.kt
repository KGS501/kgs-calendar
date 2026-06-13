package com.kgs.calendar.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.kgs.calendar.data.local.entity.EventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Query(
        """
        SELECT events.* FROM events
        INNER JOIN collections ON collections.href = events.collectionHref
        WHERE collections.isEnabled = 1
          AND (events.recurrenceRule IS NULL OR events.recurrenceRule = '')
          AND startsAtMillis < :rangeEndMillis
          AND endsAtMillis > :rangeStartMillis
        ORDER BY startsAtMillis ASC
        """,
    )
    fun observeNonRecurringBetween(rangeStartMillis: Long, rangeEndMillis: Long): Flow<List<EventEntity>>

    @Query(
        """
        SELECT events.* FROM events
        INNER JOIN collections ON collections.href = events.collectionHref
        WHERE collections.isEnabled = 1
          AND (events.recurrenceRule IS NULL OR events.recurrenceRule = '')
          AND startsAtMillis < :rangeEndMillis
          AND endsAtMillis > :rangeStartMillis
        ORDER BY startsAtMillis ASC
        """,
    )
    suspend fun snapshotNonRecurringBetween(rangeStartMillis: Long, rangeEndMillis: Long): List<EventEntity>

    @Query(
        """
        SELECT events.* FROM events
        INNER JOIN collections ON collections.href = events.collectionHref
        WHERE collections.isEnabled = 1
          AND events.recurrenceRule IS NOT NULL
          AND events.recurrenceRule != ''
          AND startsAtMillis < :rangeEndMillis
        ORDER BY startsAtMillis ASC
        """,
    )
    fun observeRecurringMasters(rangeEndMillis: Long): Flow<List<EventEntity>>

    @Query(
        """
        SELECT events.* FROM events
        INNER JOIN collections ON collections.href = events.collectionHref
        WHERE collections.isEnabled = 1
          AND events.recurrenceRule IS NOT NULL
          AND events.recurrenceRule != ''
          AND startsAtMillis < :rangeEndMillis
        ORDER BY startsAtMillis ASC
        """,
    )
    suspend fun snapshotRecurringMasters(rangeEndMillis: Long): List<EventEntity>

    @Query(
        """
        SELECT events.* FROM events
        INNER JOIN collections ON collections.href = events.collectionHref
        WHERE collections.isEnabled = 1
          AND (
            LOWER(events.title) LIKE '%' || LOWER(:query) || '%'
            OR LOWER(COALESCE(events.description, '')) LIKE '%' || LOWER(:query) || '%'
            OR LOWER(COALESCE(events.location, '')) LIKE '%' || LOWER(:query) || '%'
            OR LOWER(COALESCE(events.categories, '')) LIKE '%' || LOWER(:query) || '%'
            OR LOWER(COALESCE(events.organizerJson, '')) LIKE '%' || LOWER(:query) || '%'
            OR LOWER(COALESCE(events.attendeesJson, '')) LIKE '%' || LOWER(:query) || '%'
          )
        ORDER BY startsAtMillis ASC
        LIMIT 200
        """,
    )
    fun search(query: String): Flow<List<EventEntity>>

    @Query(
        """
        SELECT events.* FROM events
        LEFT JOIN calendar_resources ON calendar_resources.href = events.resourceHref
        WHERE (
            events.syncError IS NOT NULL
            AND events.syncError != ''
          )
          OR (
            calendar_resources.syncError IS NOT NULL
            AND calendar_resources.syncError != ''
          )
          OR EXISTS (
            SELECT 1 FROM pending_mutations
            WHERE pending_mutations.resourceHref = events.resourceHref
          )
        ORDER BY events.startsAtMillis ASC, events.title COLLATE NOCASE ASC
        """,
    )
    fun observeProblemEvents(): Flow<List<EventEntity>>

    @Query(
        """
        SELECT events.* FROM events
        INNER JOIN collections ON collections.href = events.collectionHref
        WHERE collections.isEnabled = 1
          AND events.remindersCsv IS NOT NULL
          AND events.remindersCsv != ''
        """,
    )
    suspend fun withReminders(): List<EventEntity>

    @Query(
        """
        SELECT events.* FROM events
        INNER JOIN collections ON collections.href = events.collectionHref
        WHERE collections.isEnabled = 1
          AND startsAtMillis < :windowEndMillis
          AND (
            endsAtMillis > :nowMillis
            OR (
              events.recurrenceRule IS NOT NULL
              AND events.recurrenceRule != ''
            )
          )
        ORDER BY startsAtMillis ASC
        """,
    )
    suspend fun notificationCandidates(nowMillis: Long, windowEndMillis: Long): List<EventEntity>

    @Query(
        """
        SELECT * FROM events
        WHERE resourceHref = :id OR uid = :id
        ORDER BY CASE WHEN resourceHref = :id THEN 0 ELSE 1 END
        LIMIT 1
        """,
    )
    suspend fun get(id: String): EventEntity?

    @Query("SELECT * FROM events WHERE resourceHref = :resourceHref")
    suspend fun byResource(resourceHref: String): EventEntity?

    @Upsert
    suspend fun upsert(event: EventEntity)

    @Query("DELETE FROM events WHERE resourceHref = :resourceHref")
    suspend fun deleteByResource(resourceHref: String)

    @Query("UPDATE events SET color = :color WHERE collectionHref = :collectionHref")
    suspend fun updateColorForCollection(collectionHref: String, color: Int)
}
