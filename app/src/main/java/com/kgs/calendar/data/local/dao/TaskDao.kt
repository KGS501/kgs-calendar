package com.kgs.calendar.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.kgs.calendar.data.local.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query(
        """
        SELECT tasks.* FROM tasks
        INNER JOIN collections ON collections.href = tasks.collectionHref
        WHERE collections.isEnabled = 1
          AND (recurrenceRule IS NULL OR recurrenceRule = '')
          AND (rDatesCsv IS NULL OR rDatesCsv = '')
          AND (
            (
              startAtMillis IS NOT NULL
              AND startAtMillis < :rangeEndMillis
              AND COALESCE(dueAtMillis, startAtMillis + 1800000) > :rangeStartMillis
            )
            OR (
              startAtMillis IS NULL
              AND dueAtMillis IS NOT NULL
              AND dueAtMillis >= :rangeStartMillis
              AND dueAtMillis < :rangeEndMillis
            )
          )
        ORDER BY isCompleted ASC, COALESCE(startAtMillis, dueAtMillis) ASC
        """,
    )
    fun observeDatedBetween(rangeStartMillis: Long, rangeEndMillis: Long): Flow<List<TaskEntity>>

    @Query(
        """
        SELECT tasks.* FROM tasks
        INNER JOIN collections ON collections.href = tasks.collectionHref
        WHERE collections.isEnabled = 1
          AND (recurrenceRule IS NULL OR recurrenceRule = '')
          AND (rDatesCsv IS NULL OR rDatesCsv = '')
          AND (
            (
              startAtMillis IS NOT NULL
              AND startAtMillis < :rangeEndMillis
              AND COALESCE(dueAtMillis, startAtMillis + 1800000) > :rangeStartMillis
            )
            OR (
              startAtMillis IS NULL
              AND dueAtMillis IS NOT NULL
              AND dueAtMillis >= :rangeStartMillis
              AND dueAtMillis < :rangeEndMillis
            )
          )
        ORDER BY isCompleted ASC, COALESCE(startAtMillis, dueAtMillis) ASC
        """,
    )
    suspend fun snapshotDatedBetween(rangeStartMillis: Long, rangeEndMillis: Long): List<TaskEntity>

    @Query(
        """
        SELECT tasks.* FROM tasks
        INNER JOIN collections ON collections.href = tasks.collectionHref
        WHERE collections.isEnabled = 1
          AND (
            (recurrenceRule IS NOT NULL AND recurrenceRule != '')
            OR (rDatesCsv IS NOT NULL AND rDatesCsv != '')
          )
          AND COALESCE(startAtMillis, dueAtMillis, 0) < :rangeEndMillis
        ORDER BY COALESCE(startAtMillis, dueAtMillis) ASC
        """,
    )
    fun observeRecurringMasters(rangeEndMillis: Long): Flow<List<TaskEntity>>

    @Query(
        """
        SELECT tasks.* FROM tasks
        INNER JOIN collections ON collections.href = tasks.collectionHref
        WHERE collections.isEnabled = 1
          AND (
            (recurrenceRule IS NOT NULL AND recurrenceRule != '')
            OR (rDatesCsv IS NOT NULL AND rDatesCsv != '')
          )
          AND COALESCE(startAtMillis, dueAtMillis, 0) < :rangeEndMillis
        ORDER BY COALESCE(startAtMillis, dueAtMillis) ASC
        """,
    )
    suspend fun snapshotRecurringMasters(rangeEndMillis: Long): List<TaskEntity>

    @Query(
        """
        SELECT tasks.* FROM tasks
        INNER JOIN collections ON collections.href = tasks.collectionHref
        WHERE collections.isEnabled = 1
          AND dueAtMillis IS NULL
          AND startAtMillis IS NULL
        ORDER BY isCompleted ASC, title COLLATE NOCASE ASC
        """,
    )
    fun observeInbox(): Flow<List<TaskEntity>>

    @Query(
        """
        SELECT tasks.* FROM tasks
        INNER JOIN collections ON collections.href = tasks.collectionHref
        WHERE collections.isEnabled = 1
          AND dueAtMillis IS NULL
          AND startAtMillis IS NULL
        ORDER BY isCompleted ASC, title COLLATE NOCASE ASC
        """,
    )
    suspend fun snapshotInbox(): List<TaskEntity>

    @Query(
        """
        SELECT tasks.* FROM tasks
        INNER JOIN collections ON collections.href = tasks.collectionHref
        WHERE collections.isEnabled = 1
          AND tasks.isCompleted = 0
          AND (
            tasks.startAtMillis IS NOT NULL
            OR tasks.dueAtMillis IS NOT NULL
          )
        ORDER BY COALESCE(tasks.startAtMillis, tasks.dueAtMillis) ASC, tasks.title COLLATE NOCASE ASC
        """,
    )
    fun observeScheduledOpen(): Flow<List<TaskEntity>>

    @Query(
        """
        SELECT tasks.* FROM tasks
        INNER JOIN collections ON collections.href = tasks.collectionHref
        WHERE collections.isEnabled = 1
          AND tasks.isCompleted = 0
          AND (
            tasks.startAtMillis IS NOT NULL
            OR tasks.dueAtMillis IS NOT NULL
          )
        ORDER BY COALESCE(tasks.startAtMillis, tasks.dueAtMillis) ASC, tasks.title COLLATE NOCASE ASC
        """,
    )
    suspend fun snapshotScheduledOpen(): List<TaskEntity>

    @Query(
        """
        SELECT tasks.* FROM tasks
        INNER JOIN collections ON collections.href = tasks.collectionHref
        WHERE collections.isEnabled = 1
          AND (
            LOWER(tasks.title) LIKE '%' || LOWER(:query) || '%'
            OR LOWER(COALESCE(tasks.notes, '')) LIKE '%' || LOWER(:query) || '%'
            OR LOWER(COALESCE(tasks.location, '')) LIKE '%' || LOWER(:query) || '%'
            OR LOWER(COALESCE(tasks.url, '')) LIKE '%' || LOWER(:query) || '%'
            OR LOWER(COALESCE(tasks.categories, '')) LIKE '%' || LOWER(:query) || '%'
          )
        ORDER BY isCompleted ASC, COALESCE(tasks.startAtMillis, tasks.dueAtMillis) ASC, tasks.title COLLATE NOCASE ASC
        LIMIT 200
        """,
    )
    fun search(query: String): Flow<List<TaskEntity>>

    @Query(
        """
        SELECT tasks.* FROM tasks
        LEFT JOIN calendar_resources ON calendar_resources.href = tasks.resourceHref
        WHERE (
            tasks.syncError IS NOT NULL
            AND tasks.syncError != ''
          )
          OR (
            calendar_resources.syncError IS NOT NULL
            AND calendar_resources.syncError != ''
          )
          OR EXISTS (
            SELECT 1 FROM pending_mutations
            WHERE pending_mutations.resourceHref = tasks.resourceHref
          )
        ORDER BY tasks.isCompleted ASC, COALESCE(tasks.startAtMillis, tasks.dueAtMillis) ASC, tasks.title COLLATE NOCASE ASC
        """,
    )
    fun observeProblemTasks(): Flow<List<TaskEntity>>

    @Query(
        """
        SELECT tasks.* FROM tasks
        INNER JOIN collections ON collections.href = tasks.collectionHref
        WHERE collections.isEnabled = 1
          AND (tasks.isCompleted = 1 OR UPPER(COALESCE(tasks.status, '')) = 'CANCELLED')
        ORDER BY COALESCE(tasks.completedAtMillis, tasks.dueAtMillis, tasks.startAtMillis) DESC, tasks.title COLLATE NOCASE ASC
        """,
    )
    fun observeCompleted(): Flow<List<TaskEntity>>

    @Query(
        """
        SELECT tasks.* FROM tasks
        INNER JOIN collections ON collections.href = tasks.collectionHref
        WHERE collections.isEnabled = 1
          AND tasks.isCompleted = 0
          AND UPPER(COALESCE(tasks.status, '')) != 'CANCELLED'
          AND tasks.remindersCsv IS NOT NULL
          AND tasks.remindersCsv != ''
        """,
    )
    suspend fun withReminders(): List<TaskEntity>

    @Query(
        """
        SELECT tasks.* FROM tasks
        INNER JOIN collections ON collections.href = tasks.collectionHref
        WHERE collections.isEnabled = 1
          AND tasks.isCompleted = 0
          AND UPPER(COALESCE(tasks.status, '')) != 'CANCELLED'
          AND (
            (tasks.recurrenceRule IS NOT NULL AND tasks.recurrenceRule != '')
            OR (tasks.rDatesCsv IS NOT NULL AND tasks.rDatesCsv != '')
            OR
            (
              tasks.startAtMillis IS NOT NULL
              AND tasks.startAtMillis >= :nowMillis
              AND tasks.startAtMillis <= :windowEndMillis
            )
            OR (
              tasks.dueAtMillis IS NOT NULL
              AND tasks.dueAtMillis >= :nowMillis
              AND tasks.dueAtMillis <= :windowEndMillis
            )
          )
        ORDER BY COALESCE(tasks.startAtMillis, tasks.dueAtMillis) ASC, tasks.title COLLATE NOCASE ASC
        """,
    )
    suspend fun notificationCandidates(nowMillis: Long, windowEndMillis: Long): List<TaskEntity>

    @Query(
        """
        SELECT * FROM tasks
        WHERE resourceHref = :id OR uid = :id
        ORDER BY CASE WHEN resourceHref = :id THEN 0 ELSE 1 END
        LIMIT 1
        """,
    )
    suspend fun get(id: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE resourceHref = :resourceHref")
    suspend fun byResource(resourceHref: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE collectionHref = :collectionHref AND uid = :uid LIMIT 1")
    suspend fun byUidInCollection(collectionHref: String, uid: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE collectionHref = :collectionHref AND parentUid = :parentUid")
    suspend fun children(collectionHref: String, parentUid: String): List<TaskEntity>

    @Query("SELECT * FROM tasks")
    suspend fun all(): List<TaskEntity>

    @Upsert
    suspend fun upsert(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE resourceHref = :resourceHref")
    suspend fun deleteByResource(resourceHref: String)

    @Query("UPDATE tasks SET color = :color WHERE collectionHref = :collectionHref")
    suspend fun updateColorForCollection(collectionHref: String, color: Int)
}
