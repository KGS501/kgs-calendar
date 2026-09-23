package com.kgs.calendar.data

import com.kgs.calendar.data.ical.IcalCodec
import com.kgs.calendar.data.local.KgsDatabase
import com.kgs.calendar.data.local.entity.AccountEntity
import com.kgs.calendar.data.local.entity.CalendarResourceEntity
import com.kgs.calendar.data.local.entity.CollectionEntity
import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.local.entity.PendingMutationEntity
import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.data.mutation.EventMutations
import com.kgs.calendar.data.mutation.TaskMutations
import com.kgs.calendar.data.provider.AndroidCalendarProviderClient
import com.kgs.calendar.data.provider.AndroidProviderWriteShield
import com.kgs.calendar.data.query.CalendarQueries
import com.kgs.calendar.data.recurrence.RecurrenceExpander
import com.kgs.calendar.data.remote.CalDavHttpClient
import com.kgs.calendar.data.remote.NextcloudLoginFlowClient
import com.kgs.calendar.data.search.CalendarSearchMode
import com.kgs.calendar.data.secure.CredentialsStore
import com.kgs.calendar.data.secure.StoredCredentials
import com.kgs.calendar.data.sync.AndroidProviderSyncEngine
import com.kgs.calendar.data.sync.CalDavSyncEngine
import com.kgs.calendar.data.sync.PendingMutationUploader
import com.kgs.calendar.data.sync.ReadOnlyUrlSyncEngine
import com.kgs.calendar.data.sync.SyncOrchestrator
import com.kgs.calendar.data.sync.SyncRepairs
import com.kgs.calendar.domain.model.EventEditPayload
import com.kgs.calendar.domain.model.TaskEditPayload
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okhttp3.OkHttpClient
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

class CalendarRepository(
    private val database: KgsDatabase,
    private val credentialsStore: CredentialsStore,
    private val loginFlowClient: NextcloudLoginFlowClient,
    private val calDavClient: CalDavHttpClient,
    private val androidCalendarProviderClient: AndroidCalendarProviderClient,
    private val icalCodec: IcalCodec,
    private val readOnlyHttpClient: OkHttpClient,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val recurrenceExpander: RecurrenceExpander = RecurrenceExpander(zoneId),
) {
    private val localWrites = LocalWriteSupport(database, icalCodec)
    private val androidWriteShield = AndroidProviderWriteShield()
    private val queries = CalendarQueries(database, recurrenceExpander, zoneId)
    private val eventMutations = EventMutations(database, localWrites, androidCalendarProviderClient, icalCodec, androidWriteShield, zoneId)
    private val taskMutations = TaskMutations(database, localWrites, icalCodec, zoneId)
    private val repairs = SyncRepairs(database, localWrites, icalCodec)
    private val uploader = PendingMutationUploader(database, credentialsStore, calDavClient, localWrites)
    private val readOnlyUrlSyncEngine = ReadOnlyUrlSyncEngine(database, localWrites, icalCodec, readOnlyHttpClient)
    private val androidProviderSyncEngine = AndroidProviderSyncEngine(database, localWrites, androidCalendarProviderClient, androidWriteShield)
    private val syncOrchestrator = SyncOrchestrator(
        database = database,
        repairs = repairs,
        uploader = uploader,
        engines = listOf(
            androidProviderSyncEngine,
            readOnlyUrlSyncEngine,
            CalDavSyncEngine(database, credentialsStore, calDavClient, icalCodec, localWrites, uploader, zoneId),
        ),
    )

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

    suspend fun startLoginFlow(serverUrl: String) = loginFlowClient.start(serverUrl)

    suspend fun completeLoginFlow(pollEndpoint: String, token: String): AccountEntity {
        val result = loginFlowClient.pollUntilComplete(pollEndpoint, token)
        return saveAccount(result.serverUrl, result.loginName, result.appPassword)
    }

    suspend fun saveManualAccount(serverUrl: String, username: String, appPassword: String): AccountEntity =
        saveAccount(normalizeServer(serverUrl), username.trim(), appPassword)

    suspend fun addReadOnlyCalendar(url: String, displayName: String? = null): AccountEntity {
        val normalizedUrl = normalizeReadOnlyCalendarUrl(url)
        val account = AccountEntity(
            id = readOnlyAccountId(normalizedUrl),
            serverUrl = normalizedUrl,
            username = READ_ONLY_USERNAME,
            displayName = displayName?.ifBlank { null } ?: normalizedUrl.substringAfter("://").substringBefore('/'),
            lastSyncAtMillis = null,
            sourceType = SourceType.ReadOnlyUrl,
        )
        database.accountDao().upsert(account)
        readOnlyUrlSyncEngine.sync(account)
        return account
    }

    fun hasAndroidCalendarPermissions(): Boolean =
        androidCalendarProviderClient.hasCalendarPermissions()

    suspend fun enableAndroidCalendars(includeDisabledProviderCalendars: Boolean = false): AccountEntity {
        if (!androidCalendarProviderClient.hasCalendarPermissions()) {
            error("Android calendar permission is required.")
        }
        val existing = database.accountDao().get(AndroidCalendarProviderClient.ANDROID_ACCOUNT_ID)
        val account = existing?.copy(
            serverUrl = AndroidCalendarProviderClient.ANDROID_ACCOUNT_SERVER_URL,
            username = AndroidCalendarProviderClient.ANDROID_ACCOUNT_USERNAME,
            displayName = existing.displayName ?: "Android device calendars",
            sourceType = SourceType.AndroidProvider,
        ) ?: AccountEntity(
            id = AndroidCalendarProviderClient.ANDROID_ACCOUNT_ID,
            serverUrl = AndroidCalendarProviderClient.ANDROID_ACCOUNT_SERVER_URL,
            username = AndroidCalendarProviderClient.ANDROID_ACCOUNT_USERNAME,
            displayName = "Android device calendars",
            lastSyncAtMillis = null,
            sourceType = SourceType.AndroidProvider,
        )
        database.accountDao().upsert(account)
        androidProviderSyncEngine.sync(account, removeStale = true, includeDisabledProviderCalendars = includeDisabledProviderCalendars)
        return account
    }

    suspend fun refreshAndroidCalendarsIfEnabled(
        removeStale: Boolean = false,
        includeDisabledProviderCalendars: Boolean = false,
    ) {
        val account = database.accountDao().get(AndroidCalendarProviderClient.ANDROID_ACCOUNT_ID) ?: return
        if (androidCalendarProviderClient.hasCalendarPermissions()) {
            androidProviderSyncEngine.sync(
                account = account,
                removeStale = removeStale,
                includeDisabledProviderCalendars = includeDisabledProviderCalendars,
            )
        }
    }

    suspend fun hiddenOrNotSyncedAndroidCalendars(): List<String> {
        if (!androidCalendarProviderClient.hasCalendarPermissions()) return emptyList()
        return androidCalendarProviderClient.listHiddenOrNotSyncedCalendars()
            .map { calendar ->
                val account = calendar.accountName?.takeIf { it.isNotBlank() }
                if (account == null) calendar.displayName else "${calendar.displayName} ($account)"
            }
            .distinct()
    }

    suspend fun isAndroidProviderEnabled(): Boolean =
        database.accountDao().get(AndroidCalendarProviderClient.ANDROID_ACCOUNT_ID) != null

    suspend fun shouldBlockInitialAndroidProviderRefresh(): Boolean {
        val account = database.accountDao().get(AndroidCalendarProviderClient.ANDROID_ACCOUNT_ID) ?: return false
        if (!androidCalendarProviderClient.hasCalendarPermissions()) return false
        if (account.lastSyncAtMillis != null) return false
        return database.eventDao().countForCollectionSource(SourceType.AndroidProvider) == 0
    }

    suspend fun ensureLocalCalendar(): Unit = localWrites.writeTransaction {
        val account = database.accountDao().get(LOCAL_ACCOUNT_ID) ?: AccountEntity(
            id = LOCAL_ACCOUNT_ID,
            serverUrl = LOCAL_SERVER_URL,
            username = LOCAL_USERNAME,
            displayName = "Local calendar",
            lastSyncAtMillis = null,
            sourceType = SourceType.Local,
        )
        database.accountDao().upsert(account)

        val existingCollection = database.collectionDao().get(LOCAL_COLLECTION_HREF)
        val automaticColor = existingCollection?.automaticColor
            ?: existingCollection?.color
            ?: DEFAULT_COLORS.first()
        database.collectionDao().upsertAll(
            listOf(
                CollectionEntity(
                    href = LOCAL_COLLECTION_HREF,
                    accountId = LOCAL_ACCOUNT_ID,
                    displayName = existingCollection?.displayName ?: "Lokal",
                    color = existingCollection?.customColor ?: automaticColor,
                    supportsEvents = true,
                    supportsTasks = true,
                    syncToken = null,
                    ctag = null,
                    isEnabled = existingCollection?.isEnabled ?: true,
                    sortOrder = existingCollection?.sortOrder ?: -10_000,
                    readOnly = false,
                    remoteDisplayName = existingCollection?.remoteDisplayName,
                    customDisplayName = existingCollection?.customDisplayName,
                    automaticColor = automaticColor,
                    customColor = existingCollection?.customColor,
                    sourceType = SourceType.Local,
                    externalId = LOCAL_COLLECTION_HREF,
                ),
            ),
        )
    }

    suspend fun renameAccount(accountId: String, displayName: String) {
        database.accountDao().updateDisplayName(accountId, displayName.trim().ifBlank { "Calendar source" })
    }

    suspend fun updateAccount(accountId: String, displayName: String, serverUrl: String, username: String, appPassword: String?) {
        val existing = database.accountDao().get(accountId) ?: return
        if (existing.sourceType == SourceType.AndroidProvider || existing.id == AndroidCalendarProviderClient.ANDROID_ACCOUNT_ID) {
            database.accountDao().upsert(existing.copy(displayName = displayName.trim().ifBlank { existing.displayName ?: "Android device calendars" }))
            return
        }
        val normalizedServer = if (existing.username == READ_ONLY_USERNAME) serverUrl.trim() else normalizeServer(serverUrl)
        val normalizedUsername = username.trim().ifBlank { existing.username }
        val normalizedDisplayName = displayName.trim().ifBlank { existing.displayName ?: existing.username }
        val password = appPassword?.takeIf { it.isNotBlank() } ?: credentialsStore.get(accountId)?.appPassword
        database.accountDao().upsert(
            existing.copy(
                displayName = normalizedDisplayName,
                serverUrl = normalizedServer,
                username = normalizedUsername,
                principalUrl = if (normalizedServer == existing.serverUrl && normalizedUsername == existing.username) existing.principalUrl else null,
                calendarHomeUrl = if (normalizedServer == existing.serverUrl && normalizedUsername == existing.username) existing.calendarHomeUrl else null,
                capabilitiesJson = if (normalizedServer == existing.serverUrl && normalizedUsername == existing.username) existing.capabilitiesJson else null,
            ),
        )
        if (existing.username != READ_ONLY_USERNAME && password != null) {
            credentialsStore.save(accountId, StoredCredentials(normalizedServer, normalizedUsername, password))
        }
    }

    suspend fun deleteAccount(accountId: String) {
        credentialsStore.clear(accountId)
        database.accountDao().delete(accountId)
    }

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

    suspend fun setCollectionEnabled(href: String, enabled: Boolean) {
        database.collectionDao().updateEnabled(href, enabled)
    }

    /**
     * Persists a new manual ordering of all collections. Caller supplies the hrefs
     * in the order they should appear; we write the index as sortOrder for each.
     */
    suspend fun applyCollectionOrder(hrefs: List<String>): Unit = localWrites.writeTransaction {
        hrefs.forEachIndexed { index, href ->
            database.collectionDao().updateSortOrder(href, index)
        }
    }

    suspend fun updateCollectionAppearance(href: String, displayName: String, customColor: Int?) {
        val collection = database.collectionDao().get(href) ?: return
        val normalizedName = displayName.trim().ifBlank { collection.remoteDisplayName ?: "Calendar" }
        val customDisplayName = normalizedName.takeUnless { it == collection.remoteDisplayName }
        val effectiveColor = customColor ?: collection.resolvedAutomaticColor()
        if (collection.sourceType == SourceType.CalDav && collection.canWriteProperties()) {
            val account = database.accountDao().get(collection.accountId)
            val credentials = credentialsStore.get(collection.accountId)
            if (account != null && credentials != null) {
                calDavClient.updateCalendarProperties(
                    serverUrl = credentials.serverUrl,
                    collectionHref = collection.href,
                    username = credentials.username,
                    appPassword = credentials.appPassword,
                    displayName = normalizedName,
                    color = effectiveColor,
                )
            }
        }
        localWrites.writeTransaction {
            database.collectionDao().updateAppearance(href, normalizedName, customDisplayName, effectiveColor, customColor)
            database.eventDao().updateColorForCollection(href, effectiveColor)
            database.taskDao().updateColorForCollection(href, effectiveColor)
        }
    }

    suspend fun createCalDavCalendar(
        accountId: String,
        displayName: String,
        color: Int?,
        supportsEvents: Boolean,
        supportsTasks: Boolean,
    ) {
        require(supportsEvents || supportsTasks) { "Choose events, tasks, or both." }
        val account = database.accountDao().get(accountId) ?: error("Calendar source not found.")
        require(account.sourceType == SourceType.CalDav) { "This source is not a CalDAV account." }
        val credentials = credentialsStore.get(accountId) ?: error("CalDAV credentials are missing.")
        val discovery = calDavClient.discoverAccount(
            serverUrl = credentials.serverUrl,
            username = credentials.username,
            appPassword = credentials.appPassword,
        )
        calDavClient.createCalendar(
            discovery = discovery,
            username = credentials.username,
            appPassword = credentials.appPassword,
            displayName = displayName.trim().ifBlank { "New calendar" },
            color = color,
            supportsEvents = supportsEvents,
            supportsTasks = supportsTasks,
        )
    }

    suspend fun deleteCalDavCalendar(href: String) {
        val collection = database.collectionDao().get(href) ?: return
        require(collection.sourceType == SourceType.CalDav) { "Only CalDAV calendars can be removed from the server." }
        val credentials = credentialsStore.get(collection.accountId) ?: error("CalDAV credentials are missing.")
        calDavClient.deleteCalendar(
            serverUrl = credentials.serverUrl,
            collectionHref = collection.href,
            username = credentials.username,
            appPassword = credentials.appPassword,
        )
        localWrites.writeTransaction {
            database.pendingMutationDao().deleteForCollection(collection.href)
            database.collectionDao().delete(collection.href)
        }
    }

    private suspend fun saveAccount(serverUrl: String, username: String, appPassword: String): AccountEntity {
        val normalizedServer = normalizeServer(serverUrl)
        val normalizedUsername = username.trim()
        val validation = runCatching {
            val discovery = calDavClient.discoverAccount(
                serverUrl = normalizedServer,
                username = normalizedUsername,
                appPassword = appPassword,
            )
            val collections = calDavClient.discoverCollections(
                discovery = discovery,
                username = normalizedUsername,
                appPassword = appPassword,
            )
            require(collections.isNotEmpty()) { "No CalDAV calendars or task lists were found for this account." }
            discovery
        }.getOrElse { error ->
            throw IllegalStateException(
                "Could not verify this CalDAV login. Check the server URL, username, and password/app password.",
                error,
            )
        }
        val accounts = database.accountDao().getAll()
        val existing = accounts.firstOrNull { it.serverUrl == normalizedServer && it.username == normalizedUsername }
        val remoteAccounts = accounts.filterNot { it.id == LOCAL_ACCOUNT_ID }
        val accountId = existing?.id ?: if (remoteAccounts.isEmpty()) AccountEntity.PRIMARY_ID else accountId(normalizedServer, normalizedUsername)
        val account = AccountEntity(
            id = accountId,
            serverUrl = normalizedServer,
            username = normalizedUsername,
            displayName = normalizedUsername,
            lastSyncAtMillis = existing?.lastSyncAtMillis,
            sourceType = SourceType.CalDav,
            principalUrl = validation.principalUrl,
            calendarHomeUrl = validation.calendarHomeUrl,
            capabilitiesJson = validation.toCapabilitiesJson(),
        )
        credentialsStore.save(account.id, StoredCredentials(account.serverUrl, normalizedUsername, appPassword))
        if (account.id == AccountEntity.PRIMARY_ID) {
            credentialsStore.save(StoredCredentials(account.serverUrl, normalizedUsername, appPassword))
        }
        database.accountDao().upsert(account)
        return account
    }

    private fun readOnlyAccountId(url: String): String =
        READ_ONLY_PREFIX + UUID.nameUUIDFromBytes(url.toByteArray(StandardCharsets.UTF_8)).toString()

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

    companion object {
        private const val LOCAL_USERNAME = "Local device"
        private const val LOCAL_SERVER_URL = "local://device"
        private const val LOCAL_COLLECTION_HREF = "${LOCAL_COLLECTION_PREFIX}kgs-calendar/default"
    }
}
