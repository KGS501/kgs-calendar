package com.kgs.calendar.data

import com.kgs.calendar.data.account.CalendarSourceManager
import com.kgs.calendar.data.local.entity.AccountEntity
import com.kgs.calendar.data.local.entity.CalendarResourceEntity
import com.kgs.calendar.data.local.entity.CollectionEntity
import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.local.entity.PendingMutationEntity
import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.data.mutation.EventMutations
import com.kgs.calendar.data.mutation.TaskMutations
import com.kgs.calendar.data.query.CalendarQueries
import com.kgs.calendar.data.search.CalendarSearchMode
import com.kgs.calendar.data.sync.SyncOrchestrator
import com.kgs.calendar.data.sync.SyncRepairs
import com.kgs.calendar.domain.model.EventEditPayload
import com.kgs.calendar.domain.model.TaskEditPayload
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalTime

/**
 * Facade over the calendar data components. It keeps the historical API for existing callers;
 * new code should depend on the specific component instead.
 */
class CalendarRepository(
    private val queries: CalendarQueries,
    private val eventMutations: EventMutations,
    private val taskMutations: TaskMutations,
    private val sources: CalendarSourceManager,
    private val syncOrchestrator: SyncOrchestrator,
    private val repairs: SyncRepairs,
) {
    fun observeAccount(): Flow<AccountEntity?> = queries.observeAccount()

    fun observeAccounts(): Flow<List<AccountEntity>> = queries.observeAccounts()

    fun observeCollections(): Flow<List<CollectionEntity>> = queries.observeCollections()

    fun observeEvents(startMillis: Long, endMillis: Long): Flow<List<EventEntity>> =
        queries.observeEvents(startMillis, endMillis)

    suspend fun eventsSnapshot(startMillis: Long, endMillis: Long): List<EventEntity> =
        queries.eventsSnapshot(startMillis, endMillis)

    fun searchEvents(
        query: String,
        mode: CalendarSearchMode,
        rangeStartMillis: Long,
        rangeEndMillis: Long,
    ): Flow<List<EventEntity>> = queries.searchEvents(query, mode, rangeStartMillis, rangeEndMillis)

    fun searchTasks(
        query: String,
        mode: CalendarSearchMode,
        rangeStartMillis: Long,
        rangeEndMillis: Long,
    ): Flow<List<TaskEntity>> = queries.searchTasks(query, mode, rangeStartMillis, rangeEndMillis)

    fun observeDatedTasks(startMillis: Long, endMillis: Long): Flow<List<TaskEntity>> =
        queries.observeDatedTasks(startMillis, endMillis)

    suspend fun datedTasksSnapshot(startMillis: Long, endMillis: Long): List<TaskEntity> =
        queries.datedTasksSnapshot(startMillis, endMillis)

    fun observeInboxTasks(): Flow<List<TaskEntity>> = queries.observeInboxTasks()

    fun observeScheduledOpenTasks(): Flow<List<TaskEntity>> = queries.observeScheduledOpenTasks()

    suspend fun inboxTasksSnapshot(): List<TaskEntity> = queries.inboxTasksSnapshot()

    suspend fun scheduledOpenTasksSnapshot(): List<TaskEntity> = queries.scheduledOpenTasksSnapshot()

    suspend fun allTasksSnapshot(): List<TaskEntity> = queries.allTasksSnapshot()

    fun observeCompletedTasks(): Flow<List<TaskEntity>> = queries.observeCompletedTasks()

    fun observePendingMutationCount(): Flow<Int> = queries.observePendingMutationCount()

    fun observePendingMutations(): Flow<List<PendingMutationEntity>> = queries.observePendingMutations()

    fun observeProblemResources(): Flow<List<CalendarResourceEntity>> = queries.observeProblemResources()

    fun observeProblemEvents(): Flow<List<EventEntity>> = queries.observeProblemEvents()

    fun observeProblemTasks(): Flow<List<TaskEntity>> = queries.observeProblemTasks()

    suspend fun startLoginFlow(serverUrl: String) = sources.startLoginFlow(serverUrl)

    suspend fun completeLoginFlow(pollEndpoint: String, token: String): AccountEntity =
        sources.completeLoginFlow(pollEndpoint, token)

    suspend fun saveManualAccount(serverUrl: String, username: String, appPassword: String): AccountEntity =
        sources.saveManualAccount(serverUrl, username, appPassword)

    suspend fun addReadOnlyCalendar(url: String, displayName: String? = null): AccountEntity =
        sources.addReadOnlyCalendar(url, displayName)

    fun hasAndroidCalendarPermissions(): Boolean = sources.hasAndroidCalendarPermissions()

    suspend fun enableAndroidCalendars(includeDisabledProviderCalendars: Boolean = false): AccountEntity =
        sources.enableAndroidCalendars(includeDisabledProviderCalendars)

    suspend fun refreshAndroidCalendarsIfEnabled(
        removeStale: Boolean = false,
        includeDisabledProviderCalendars: Boolean = false,
    ) = sources.refreshAndroidCalendarsIfEnabled(removeStale, includeDisabledProviderCalendars)

    suspend fun hiddenOrNotSyncedAndroidCalendars(): List<String> = sources.hiddenOrNotSyncedAndroidCalendars()

    suspend fun isAndroidProviderEnabled(): Boolean = sources.isAndroidProviderEnabled()

    suspend fun shouldBlockInitialAndroidProviderRefresh(): Boolean = sources.shouldBlockInitialAndroidProviderRefresh()

    suspend fun ensureLocalCalendar() = sources.ensureLocalCalendar()

    suspend fun renameAccount(accountId: String, displayName: String) = sources.renameAccount(accountId, displayName)

    suspend fun updateAccount(accountId: String, displayName: String, serverUrl: String, username: String, appPassword: String?) =
        sources.updateAccount(accountId, displayName, serverUrl, username, appPassword)

    suspend fun deleteAccount(accountId: String) = sources.deleteAccount(accountId)

    suspend fun reparseLocalResources() = repairs.reparseLocalResources()

    suspend fun reparseLocalTaskResources() = repairs.reparseLocalTaskResources()

    suspend fun syncNow(
        includeDisabledProviderCalendars: Boolean = false,
        forceFullCalDavRefresh: Boolean = false,
    ) = syncOrchestrator.syncNow(includeDisabledProviderCalendars, forceFullCalDavRefresh)

    suspend fun pushPendingChangesCreatedSince(startedAtMillis: Long) =
        syncOrchestrator.pushPendingChangesCreatedSince(startedAtMillis)

    suspend fun createEvent(payload: EventEditPayload) = eventMutations.createEvent(payload)

    suspend fun updateEventManualColor(uid: String, manualColor: Int?) = eventMutations.updateEventManualColor(uid, manualColor)

    suspend fun updateEvent(uid: String, payload: EventEditPayload) = eventMutations.updateEvent(uid, payload)

    suspend fun updateEventOccurrence(uid: String, occurrenceStartMillis: Long, payload: EventEditPayload) =
        eventMutations.updateEventOccurrence(uid, occurrenceStartMillis, payload)

    suspend fun updateEventFollowing(uid: String, occurrenceStartMillis: Long, payload: EventEditPayload) =
        eventMutations.updateEventFollowing(uid, occurrenceStartMillis, payload)

    suspend fun moveTimedEvent(uid: String, date: LocalDate, startTime: LocalTime, endTime: LocalTime) =
        eventMutations.moveTimedEvent(uid, date, startTime, endTime)

    suspend fun moveTimedEvent(uid: String, occurrenceStartMillis: Long, date: LocalDate, startTime: LocalTime, endTime: LocalTime) =
        eventMutations.moveTimedEvent(uid, occurrenceStartMillis, date, startTime, endTime)

    suspend fun moveAllDayEvent(uid: String, occurrenceStartMillis: Long, date: LocalDate) =
        eventMutations.moveAllDayEvent(uid, occurrenceStartMillis, date)

    suspend fun setEventParticipation(uid: String, attendeeEmails: List<String>, partstat: String) =
        eventMutations.setEventParticipation(uid, attendeeEmails, partstat)

    suspend fun copyEventTo(uid: String, collectionHref: String) = eventMutations.copyEventTo(uid, collectionHref)

    suspend fun createTask(payload: TaskEditPayload) = taskMutations.createTask(payload)

    suspend fun updateTaskManualColor(uid: String, manualColor: Int?) = taskMutations.updateTaskManualColor(uid, manualColor)

    suspend fun updateTask(uid: String, payload: TaskEditPayload) = taskMutations.updateTask(uid, payload)

    suspend fun updateTaskOccurrence(uid: String, occurrenceStartMillis: Long, payload: TaskEditPayload) =
        taskMutations.updateTaskOccurrence(uid, occurrenceStartMillis, payload)

    suspend fun updateTaskFollowing(uid: String, occurrenceStartMillis: Long, payload: TaskEditPayload) =
        taskMutations.updateTaskFollowing(uid, occurrenceStartMillis, payload)

    suspend fun setTaskCompleted(resourceHref: String, completed: Boolean) = taskMutations.setTaskCompleted(resourceHref, completed)

    suspend fun setTaskStatus(resourceHref: String, status: String) = taskMutations.setTaskStatus(resourceHref, status)

    suspend fun setTaskOccurrenceStatus(resourceHref: String, occurrenceStartMillis: Long, status: String) =
        taskMutations.setTaskOccurrenceStatus(resourceHref, occurrenceStartMillis, status)

    suspend fun setTaskPriority(uid: String, priority: Int) = taskMutations.setTaskPriority(uid, priority)

    suspend fun setTaskProgress(uid: String, progress: Int) = taskMutations.setTaskProgress(uid, progress)

    suspend fun moveTimedTask(uid: String, date: LocalDate, startTime: LocalTime, endTime: LocalTime) =
        taskMutations.moveTimedTask(uid, date, startTime, endTime)

    suspend fun moveTimedTask(uid: String, occurrenceStartMillis: Long, date: LocalDate, startTime: LocalTime, endTime: LocalTime) =
        taskMutations.moveTimedTask(uid, occurrenceStartMillis, date, startTime, endTime)

    suspend fun moveAllDayTask(uid: String, occurrenceStartMillis: Long, date: LocalDate) =
        taskMutations.moveAllDayTask(uid, occurrenceStartMillis, date)

    suspend fun copyTaskTo(uid: String, collectionHref: String) = taskMutations.copyTaskTo(uid, collectionHref)

    suspend fun deleteTask(uid: String) = taskMutations.deleteTask(uid)

    suspend fun deleteEvent(uid: String) = eventMutations.deleteEvent(uid)

    suspend fun deleteEventOccurrence(uid: String, occurrenceStartMillis: Long) =
        eventMutations.deleteEventOccurrence(uid, occurrenceStartMillis)

    suspend fun deleteEventFollowing(uid: String, occurrenceStartMillis: Long) =
        eventMutations.deleteEventFollowing(uid, occurrenceStartMillis)

    suspend fun repairInvalidTaskSchedules() = repairs.repairInvalidTaskSchedules()

    suspend fun setCollectionEnabled(href: String, enabled: Boolean) = sources.setCollectionEnabled(href, enabled)

    suspend fun applyCollectionOrder(hrefs: List<String>) = sources.applyCollectionOrder(hrefs)

    suspend fun updateCollectionAppearance(href: String, displayName: String, customColor: Int?) =
        sources.updateCollectionAppearance(href, displayName, customColor)

    suspend fun createCalDavCalendar(
        accountId: String,
        displayName: String,
        color: Int?,
        supportsEvents: Boolean,
        supportsTasks: Boolean,
    ) = sources.createCalDavCalendar(accountId, displayName, color, supportsEvents, supportsTasks)

    suspend fun deleteCalDavCalendar(href: String) = sources.deleteCalDavCalendar(href)

    suspend fun reminderCandidates(): Pair<List<EventEntity>, List<TaskEntity>> = queries.reminderCandidates()

    suspend fun notificationCandidates(nowMillis: Long, windowEndMillis: Long): Pair<List<EventEntity>, List<TaskEntity>> =
        queries.notificationCandidates(nowMillis, windowEndMillis)

    fun expandEventReminders(master: EventEntity, fromMillis: Long, toMillis: Long): List<EventEntity> =
        queries.expandEventReminders(master, fromMillis, toMillis)

    fun expandEventReminderOccurrences(master: EventEntity, fromMillis: Long, toMillis: Long) =
        queries.expandEventReminderOccurrences(master, fromMillis, toMillis)

    fun expandTaskReminders(master: TaskEntity, fromMillis: Long, toMillis: Long): List<TaskEntity> =
        queries.expandTaskReminders(master, fromMillis, toMillis)

    fun expandTaskReminderOccurrences(master: TaskEntity, fromMillis: Long, toMillis: Long) =
        queries.expandTaskReminderOccurrences(master, fromMillis, toMillis)

    suspend fun eventByResource(resourceHref: String): EventEntity? = queries.eventByResource(resourceHref)

    suspend fun taskByResource(resourceHref: String): TaskEntity? = queries.taskByResource(resourceHref)
}
