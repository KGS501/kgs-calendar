package com.kgs.calendar.data.query

import com.kgs.calendar.data.local.KgsDatabase
import com.kgs.calendar.data.local.entity.AccountEntity
import com.kgs.calendar.data.local.entity.CalendarResourceEntity
import com.kgs.calendar.data.local.entity.CollectionEntity
import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.local.entity.PendingMutationEntity
import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.data.recurrence.RecurrenceExpander
import com.kgs.calendar.data.recurrence.TaskRecurrenceExpander
import com.kgs.calendar.data.search.CalendarOccurrenceSearch
import com.kgs.calendar.data.search.CalendarSearchMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import java.time.ZoneId

/** Read side of the calendar data: observed and snapshot queries, search and reminder candidates. */
class CalendarQueries(
    private val database: KgsDatabase,
    private val recurrenceExpander: RecurrenceExpander,
    zoneId: ZoneId,
) {
    private val taskRecurrenceExpander = TaskRecurrenceExpander(recurrenceExpander)
    private val occurrenceSearch = CalendarOccurrenceSearch(zoneId)

    fun observeAccount(): Flow<AccountEntity?> = database.accountDao().observeAll().map { it.firstOrNull() }

    fun observeAccounts(): Flow<List<AccountEntity>> = database.accountDao().observeAll()

    fun observeCollections(): Flow<List<CollectionEntity>> = database.collectionDao().observeAll()

    fun observeEvents(startMillis: Long, endMillis: Long): Flow<List<EventEntity>> =
        combine(
            database.eventDao().observeNonRecurringBetween(startMillis, endMillis),
            database.eventDao().observeRecurringMasters(endMillis),
        ) { simple, recurringMasters ->
            val expanded = recurringMasters.flatMap { master ->
                recurrenceExpander.expand(master, startMillis, endMillis)
            }
            (simple + expanded).sortedBy { it.startsAtMillis }
        }.flowOn(Dispatchers.Default)

    suspend fun eventsSnapshot(startMillis: Long, endMillis: Long): List<EventEntity> {
        val simple = database.eventDao().snapshotNonRecurringBetween(startMillis, endMillis)
        val recurringMasters = database.eventDao().snapshotRecurringMasters(endMillis)
        val expanded = recurringMasters.flatMap { master ->
            recurrenceExpander.expand(master, startMillis, endMillis)
        }
        return (simple + expanded).sortedBy { it.startsAtMillis }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun searchEvents(
        query: String,
        mode: CalendarSearchMode,
        rangeStartMillis: Long,
        rangeEndMillis: Long,
    ): Flow<List<EventEntity>> = database.eventDao().observeSearchCandidates()
        .mapLatest { masters ->
            val coroutineContext = currentCoroutineContext()
            occurrenceSearch.events(
                masters = masters,
                query = query,
                mode = mode,
                rangeStartMillis = rangeStartMillis,
                rangeEndMillis = rangeEndMillis,
                cancellationCheck = { coroutineContext.ensureActive() },
            )
        }
        .flowOn(Dispatchers.Default)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun searchTasks(
        query: String,
        mode: CalendarSearchMode,
        rangeStartMillis: Long,
        rangeEndMillis: Long,
    ): Flow<List<TaskEntity>> = database.taskDao().observeSearchCandidates()
        .mapLatest { masters ->
            val coroutineContext = currentCoroutineContext()
            occurrenceSearch.tasks(
                masters = masters,
                query = query,
                mode = mode,
                rangeStartMillis = rangeStartMillis,
                rangeEndMillis = rangeEndMillis,
                cancellationCheck = { coroutineContext.ensureActive() },
            )
        }
        .flowOn(Dispatchers.Default)

    fun observeDatedTasks(startMillis: Long, endMillis: Long): Flow<List<TaskEntity>> =
        combine(
            database.taskDao().observeDatedBetween(startMillis, endMillis),
            database.taskDao().observeRecurringMasters(endMillis),
        ) { simple, recurring ->
            (simple + recurring.flatMap { taskRecurrenceExpander.expand(it, startMillis, endMillis) })
                .sortedBy { it.startAtMillis ?: it.dueAtMillis ?: Long.MAX_VALUE }
        }.flowOn(Dispatchers.Default)

    suspend fun datedTasksSnapshot(startMillis: Long, endMillis: Long): List<TaskEntity> {
        val simple = database.taskDao().snapshotDatedBetween(startMillis, endMillis)
        val recurring = database.taskDao().snapshotRecurringMasters(endMillis)
        return (simple + recurring.flatMap { taskRecurrenceExpander.expand(it, startMillis, endMillis) })
            .sortedBy { it.startAtMillis ?: it.dueAtMillis ?: Long.MAX_VALUE }
    }

    fun observeInboxTasks(): Flow<List<TaskEntity>> = database.taskDao().observeInbox()

    fun observeScheduledOpenTasks(): Flow<List<TaskEntity>> = database.taskDao().observeScheduledOpen()

    suspend fun inboxTasksSnapshot(): List<TaskEntity> = database.taskDao().snapshotInbox()

    suspend fun scheduledOpenTasksSnapshot(): List<TaskEntity> = database.taskDao().snapshotScheduledOpen()

    suspend fun allTasksSnapshot(): List<TaskEntity> = database.taskDao().all()

    fun observeCompletedTasks(): Flow<List<TaskEntity>> =
        combine(
            database.taskDao().observeCompleted(),
            database.taskDao().observeRecurringMasters(Long.MAX_VALUE),
        ) { storedTasks, recurringMasters ->
            (storedTasks + recurringMasters.flatMap(taskRecurrenceExpander::inactiveOverrides))
                .distinctBy { task ->
                    Triple(
                        task.resourceHref,
                        task.startAtMillis ?: task.dueAtMillis,
                        task.completedAtMillis,
                    )
                }
                .sortedByDescending { it.completedAtMillis ?: it.dueAtMillis ?: it.startAtMillis ?: Long.MIN_VALUE }
        }.flowOn(Dispatchers.Default)

    fun observePendingMutationCount(): Flow<Int> = database.pendingMutationDao().observeCount()

    fun observePendingMutations(): Flow<List<PendingMutationEntity>> = database.pendingMutationDao().observeAll()

    fun observeProblemResources(): Flow<List<CalendarResourceEntity>> = database.resourceDao().observeSyncErrors()

    fun observeProblemEvents(): Flow<List<EventEntity>> = database.eventDao().observeProblemEvents()

    fun observeProblemTasks(): Flow<List<TaskEntity>> = database.taskDao().observeProblemTasks()

    /**
     * Returns all events and tasks (master rows) that carry at least one reminder, used
     * by the reminder scheduler. Recurrence expansion for reminders is handled by the
     * scheduler using the recurrence rule + expander.
     */
    suspend fun reminderCandidates(): Pair<List<EventEntity>, List<TaskEntity>> {
        val events = database.eventDao().withReminders()
        val tasks = database.taskDao().withReminders()
        return events to tasks
    }

    suspend fun notificationCandidates(nowMillis: Long, windowEndMillis: Long): Pair<List<EventEntity>, List<TaskEntity>> {
        val events = database.eventDao().notificationCandidates(nowMillis, windowEndMillis)
        val tasks = database.taskDao().notificationCandidates(nowMillis, windowEndMillis)
        return events to tasks
    }

    fun expandEventReminders(master: EventEntity, fromMillis: Long, toMillis: Long): List<EventEntity> =
        recurrenceExpander.expand(master, fromMillis, toMillis)

    fun expandEventReminderOccurrences(master: EventEntity, fromMillis: Long, toMillis: Long) =
        recurrenceExpander.expandWithIdentity(master, fromMillis, toMillis)

    fun expandTaskReminders(master: TaskEntity, fromMillis: Long, toMillis: Long): List<TaskEntity> =
        taskRecurrenceExpander.expand(master, fromMillis, toMillis)

    fun expandTaskReminderOccurrences(master: TaskEntity, fromMillis: Long, toMillis: Long) =
        taskRecurrenceExpander.expandWithIdentity(master, fromMillis, toMillis)

    suspend fun eventByResource(resourceHref: String): EventEntity? = database.eventDao().byResource(resourceHref)

    suspend fun taskByResource(resourceHref: String): TaskEntity? = database.taskDao().byResource(resourceHref)
}
