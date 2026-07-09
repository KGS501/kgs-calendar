package com.kgs.calendar.data

import android.graphics.Color
import com.kgs.calendar.data.SourceType
import com.kgs.calendar.data.ical.IcalCodec
import com.kgs.calendar.data.ical.EventRecurrenceOverride
import com.kgs.calendar.data.ical.RecurrenceOverrideCodec
import com.kgs.calendar.data.ical.TaskRecurrenceOverride
import com.kgs.calendar.data.local.KgsDatabase
import com.kgs.calendar.data.local.entity.AccountEntity
import com.kgs.calendar.data.local.entity.CalendarResourceEntity
import com.kgs.calendar.data.local.entity.CollectionEntity
import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.local.entity.PendingMutationEntity
import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.data.local.entity.withValidIcalSchedule
import com.kgs.calendar.data.provider.AndroidCalendarProviderClient
import com.kgs.calendar.data.recurrence.RecurrenceExpander
import com.kgs.calendar.data.recurrence.TaskRecurrenceExpander
import com.kgs.calendar.data.remote.CalDavHttpClient
import com.kgs.calendar.data.remote.CalDavAccountDiscovery
import com.kgs.calendar.data.remote.RemoteCollection
import com.kgs.calendar.data.remote.RemoteResource
import com.kgs.calendar.data.remote.NextcloudLoginFlowClient
import com.kgs.calendar.data.secure.CredentialsStore
import com.kgs.calendar.data.secure.StoredCredentials
import com.kgs.calendar.domain.model.ComponentType
import com.kgs.calendar.domain.model.EventEditPayload
import com.kgs.calendar.domain.model.MutationAction
import com.kgs.calendar.domain.model.TaskEditPayload
import com.kgs.calendar.domain.model.normalizedReminderOffsets
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URI
import java.net.UnknownHostException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

class CalendarRepository(
    private val database: KgsDatabase,
    private val credentialsStore: CredentialsStore,
    private val loginFlowClient: NextcloudLoginFlowClient,
    private val calDavClient: CalDavHttpClient,
    private val androidCalendarProviderClient: AndroidCalendarProviderClient,
    private val icalCodec: IcalCodec,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val recurrenceExpander: RecurrenceExpander = RecurrenceExpander(zoneId),
) {
    private val taskRecurrenceExpander = TaskRecurrenceExpander(recurrenceExpander)
    private val readOnlyHttpClient = OkHttpClient()
    private val androidProviderSyncMutex = Mutex()
    private val recentCalDavLocalWrites = ConcurrentHashMap<String, Long>()
    private val recentAndroidLocalWrites = ConcurrentHashMap<String, Long>()
    private val recentAndroidLocalDeletes = ConcurrentHashMap<String, Long>()

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

    fun searchEvents(query: String): Flow<List<EventEntity>> =
        database.eventDao().search(query)

    fun searchTasks(query: String): Flow<List<TaskEntity>> =
        database.taskDao().search(query)

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

    fun observeCompletedTasks(): Flow<List<TaskEntity>> = database.taskDao().observeCompleted()

    fun observePendingMutationCount(): Flow<Int> = database.pendingMutationDao().observeCount()

    fun observePendingMutations(): Flow<List<PendingMutationEntity>> = database.pendingMutationDao().observeAll()

    fun observeProblemResources(): Flow<List<CalendarResourceEntity>> = database.resourceDao().observeSyncErrors()

    fun observeProblemEvents(): Flow<List<EventEntity>> = database.eventDao().observeProblemEvents()

    fun observeProblemTasks(): Flow<List<TaskEntity>> = database.taskDao().observeProblemTasks()

    suspend fun startLoginFlow(serverUrl: String) = loginFlowClient.start(serverUrl)

    suspend fun completeLoginFlow(pollEndpoint: String, token: String): AccountEntity {
        val result = loginFlowClient.pollUntilComplete(pollEndpoint, token)
        return saveAccount(result.serverUrl, result.loginName, result.appPassword)
    }

    suspend fun saveManualAccount(serverUrl: String, username: String, appPassword: String): AccountEntity =
        saveAccount(normalizeServer(serverUrl), username.trim(), appPassword)

    suspend fun addReadOnlyCalendar(url: String, displayName: String? = null): AccountEntity {
        val normalizedUrl = url.trim()
        require(normalizedUrl.startsWith("http://", ignoreCase = true) || normalizedUrl.startsWith("https://", ignoreCase = true)) {
            "Bitte eine http(s)-URL eingeben."
        }
        val account = AccountEntity(
            id = readOnlyAccountId(normalizedUrl),
            serverUrl = normalizedUrl,
            username = READ_ONLY_USERNAME,
            displayName = displayName?.ifBlank { null } ?: normalizedUrl.substringAfter("://").substringBefore('/'),
            lastSyncAtMillis = null,
            sourceType = SourceType.ReadOnlyUrl,
        )
        database.accountDao().upsert(account)
        syncReadOnlyUrl(account)
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
        syncAndroidProvider(account, removeStale = true, includeDisabledProviderCalendars = includeDisabledProviderCalendars)
        return account
    }

    suspend fun refreshAndroidCalendarsIfEnabled(
        removeStale: Boolean = false,
        includeDisabledProviderCalendars: Boolean = false,
    ) {
        val account = database.accountDao().get(AndroidCalendarProviderClient.ANDROID_ACCOUNT_ID) ?: return
        if (androidCalendarProviderClient.hasCalendarPermissions()) {
            syncAndroidProvider(
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

    suspend fun ensureLocalCalendar() {
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

    /**
     * Re-parses every locally cached iCal payload through the current codec and writes
     * the result back to the events/tasks tables. Used after parser fixes ship so that
     * already-synced data picks up corrections without forcing a full network re-fetch.
     */
    suspend fun reparseLocalResources() {
        reparseResources(database.resourceDao().all())
    }

    suspend fun reparseLocalTaskResources() {
        reparseResources(database.resourceDao().forComponentType(ComponentType.Task))
    }

    private suspend fun reparseResources(resources: List<CalendarResourceEntity>) {
        resources.forEach { resource ->
            val collection = database.collectionDao().get(resource.collectionHref) ?: return@forEach
            val parsed = icalCodec.parse(resource.rawIcs, collection.href, resource.href, collection.color) ?: return@forEach
            parsed.event?.let { freshEvent ->
                val existing = database.eventDao().byResource(resource.href)
                database.eventDao().upsert(freshEvent.copy(manualColor = existing?.manualColor))
            }
            parsed.task?.let { freshTask ->
                val existing = database.taskDao().byResource(resource.href)
                database.taskDao().upsert(freshTask.copy(manualColor = existing?.manualColor))
            }
        }
    }

    suspend fun syncNow(
        includeDisabledProviderCalendars: Boolean = false,
        forceFullCalDavRefresh: Boolean = false,
    ) {
        repairInvalidTaskSchedules()
        repairPendingTaskMutations()
        repairDuplicateCalDavResources()
        discardSupersededPendingMutations()
        var successfulAccounts = 0
        var firstError: Throwable? = null
        database.accountDao().getAll().forEach { account ->
            try {
                if (account.id == LOCAL_ACCOUNT_ID) return@forEach
                if (account.sourceType == SourceType.AndroidProvider || account.id == AndroidCalendarProviderClient.ANDROID_ACCOUNT_ID) {
                    syncAndroidProvider(
                        account = account,
                        removeStale = true,
                        includeDisabledProviderCalendars = includeDisabledProviderCalendars,
                    )
                    successfulAccounts++
                    return@forEach
                }
                if (account.id.startsWith(READ_ONLY_PREFIX)) {
                    syncReadOnlyUrl(account)
                    successfulAccounts++
                    return@forEach
                }
                val credentials = credentialsStore.get(account.id) ?: return@forEach
                database.accountDao().updateSyncState("syncing", null, account.lastSyncAtMillis, account.id)
                repairMissingCalDavEtags(credentials, account.id)
                pushPending(credentials, account.id)
                val discovery = calDavClient.discoverAccount(
                    serverUrl = credentials.serverUrl,
                    username = credentials.username,
                    appPassword = credentials.appPassword,
                )
                database.accountDao().updateCalDavDiscovery(
                    id = account.id,
                    principalUrl = discovery.principalUrl,
                    calendarHomeUrl = discovery.calendarHomeUrl,
                    capabilitiesJson = discovery.toCapabilitiesJson(),
                )
                val remoteCollections = calDavClient.discoverCollections(
                    discovery = discovery,
                    username = credentials.username,
                    appPassword = credentials.appPassword,
                )
                val previousCollectionsByHref = database.collectionDao().forAccount(account.id).associateBy { it.href }
                val collectionEntities = remoteCollections.mapIndexed { index, remote ->
                    val existing = previousCollectionsByHref[remote.href]
                    val automaticColor = remote.color
                        ?: existing?.automaticColor
                        ?: existing?.color?.takeIf { existing.customColor == null }
                        ?: DEFAULT_COLORS[index % DEFAULT_COLORS.size]
                    CollectionEntity(
                        href = remote.href,
                        accountId = account.id,
                        displayName = existing?.customDisplayName ?: remote.displayName,
                        color = existing?.customColor ?: automaticColor,
                        supportsEvents = remote.supportsEvents,
                        supportsTasks = remote.supportsTasks,
                        // These are local cursors. Advancing them before a successful sync can
                        // permanently skip changes returned with the advertised server token.
                        syncToken = existing?.syncToken,
                        ctag = existing?.ctag,
                        isEnabled = existing?.isEnabled ?: true,
                        sortOrder = existing?.sortOrder ?: index,
                        readOnly = remote.readOnly || existing?.readOnly == true,
                        remoteDisplayName = remote.displayName,
                        customDisplayName = existing?.customDisplayName,
                        automaticColor = automaticColor,
                        sourceColor = remote.color,
                        customColor = existing?.customColor,
                        sourceType = SourceType.CalDav,
                        externalId = remote.href,
                        capabilitiesJson = remote.capabilities.toJson(),
                    )
                }
                database.collectionDao().upsertAll(collectionEntities)
                collectionEntities.forEach { collection ->
                    database.eventDao().updateColorForCollection(collection.href, collection.color)
                    database.taskDao().updateColorForCollection(collection.href, collection.color)
                }
                removeStaleRemoteCollections(account.id, collectionEntities.map { it.href }.toSet())
                collectionEntities
                    .filter { it.isEnabled }
                    .forEach { collection ->
                        val remote = remoteCollections.firstOrNull { it.href.davHrefKey() == collection.href.davHrefKey() }
                        syncCollection(credentials, collection, remote, forceFullRefresh = forceFullCalDavRefresh)
                    }
                database.accountDao().updateSyncState("idle", null, System.currentTimeMillis(), account.id)
                successfulAccounts++
            } catch (error: Throwable) {
                val syncError = account.describeSyncError(error)
                database.accountDao().updateSyncState("error", syncError, account.lastSyncAtMillis, account.id)
                firstError = firstError ?: IllegalStateException(syncError, error)
            }
        }
        if (successfulAccounts == 0) firstError?.let { throw it }
    }

    suspend fun pushPendingChangesCreatedSince(startedAtMillis: Long) {
        val threshold = startedAtMillis - TARGETED_SYNC_CLOCK_SKEW_MILLIS
        repairPendingTaskMutations(database.pendingMutationDao().createdSince(threshold))
        pushPendingMutations(database.pendingMutationDao().createdSince(threshold))
    }

    suspend fun createEvent(payload: EventEditPayload) {
        val collection = payload.collectionHref?.let { database.collectionDao().get(it) }
            ?.takeUnless { it.isReadOnlyCollection() || !it.canCreateResources() }
            ?: database.collectionDao().eventCollections().firstOrNull { !it.isReadOnlyCollection() && it.canCreateResources() }
            ?: error("No writable event calendar has been synced yet.")
        val uid = newUid()
        val resourceHref = collection.newResourceHref(uid)
        val endDate = payload.endDate ?: payload.date
        val start = if (payload.allDay) {
            payload.date.atStartOfDay(zoneId).toInstant().toEpochMilli()
        } else {
            payload.date.atTime(payload.startTime ?: LocalTime.of(9, 0)).atZone(zoneId).toInstant().toEpochMilli()
        }
        val end = if (payload.allDay) {
            endDate.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        } else {
            endDate.atTime(payload.endTime ?: (payload.startTime ?: LocalTime.of(9, 0)).plusHours(1)).atZone(zoneId).toInstant().toEpochMilli()
        }
        val event = EventEntity(
            uid = uid,
            collectionHref = collection.href,
            resourceHref = resourceHref,
            title = payload.title.ifBlank { "Untitled event" },
            description = payload.description?.ifBlank { null },
            location = payload.location?.ifBlank { null },
            locationMapVerified = payload.location?.takeIf { it.isNotBlank() }?.let { payload.locationMapVerified },
            startsAtMillis = start,
            endsAtMillis = if (end > start) {
                end
            } else if (payload.allDay) {
                payload.date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
            } else {
                start + 60L * 60L * 1000L
            },
            allDay = payload.allDay,
            recurrenceRule = payload.recurrenceRule?.ifBlank { null },
            isRecurring = !payload.recurrenceRule.isNullOrBlank(),
            timezoneId = if (payload.allDay) null else zoneId.id,
            remindersCsv = payload.reminderMinutes.normalizedReminderOffsets().takeIf { it.isNotEmpty() }?.joinToString(","),
            status = payload.status?.ifBlank { null },
            classification = payload.classification?.ifBlank { null },
            transparency = payload.transparency?.ifBlank { null },
            categories = payload.categories?.ifBlank { null },
            organizerJson = payload.organizerJson,
            attendeesJson = payload.attendeesJson,
            color = collection.color,
            manualColor = payload.manualColor,
        ).sanitizedFor(collection)
        if (collection.isAndroidProviderCollection()) {
            val calendarId = collection.androidCalendarId()
            val eventId = androidCalendarProviderClient.insertEvent(calendarId, event)
            val androidEvent = event.copy(
                uid = "android-event-$eventId",
                resourceHref = androidCalendarProviderClient.eventHref(eventId),
            )
            upsertLocalResource(collection.href, androidEvent.resourceHref, null, ComponentType.Event, androidEvent.uid, "android-provider:$eventId")
            database.eventDao().upsert(androidEvent)
            markRecentAndroidLocalWrite(androidEvent.resourceHref)
            return
        }
        val raw = icalCodec.serializeEvent(event)
        upsertLocalResource(collection.href, resourceHref, null, ComponentType.Event, uid, raw)
        database.eventDao().upsert(event)
        enqueuePut(collection.href, resourceHref, ComponentType.Event, raw, null)
    }

    suspend fun updateEventManualColor(uid: String, manualColor: Int?) {
        val existing = database.eventDao().get(uid) ?: return
        database.eventDao().upsert(existing.copy(manualColor = manualColor))
    }

    suspend fun updateEvent(uid: String, payload: EventEditPayload) {
        val existing = database.eventDao().get(uid) ?: return
        val resource = database.resourceDao().get(existing.resourceHref)
        val existingCollection = database.collectionDao().get(existing.collectionHref)
            ?: error("Calendar not found.")
        val targetCollection = payload.collectionHref?.let { database.collectionDao().get(it) }
            ?: database.collectionDao().get(existing.collectionHref)
            ?: error("Calendar not found.")
        if (targetCollection.isReadOnlyCollection()) error("Read-only calendars cannot be edited.")
        val moved = targetCollection.href != existing.collectionHref
        val targetIsAndroid = targetCollection.isAndroidProviderCollection()
        val existingIsAndroid = existingCollection.isAndroidProviderCollection()
        val resourceHref = when {
            targetIsAndroid -> existing.resourceHref
            moved -> targetCollection.newResourceHref(existing.uid)
            else -> existing.resourceHref
        }
        val endDate = payload.endDate ?: payload.date
        val start = if (payload.allDay) {
            payload.date.atStartOfDay(zoneId).toInstant().toEpochMilli()
        } else {
            payload.date.atTime(payload.startTime ?: LocalTime.of(9, 0)).atZone(zoneId).toInstant().toEpochMilli()
        }
        val end = if (payload.allDay) {
            endDate.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        } else {
            endDate.atTime(payload.endTime ?: (payload.startTime ?: LocalTime.of(9, 0)).plusHours(1)).atZone(zoneId).toInstant().toEpochMilli()
        }
        val updated = existing.copy(
            collectionHref = targetCollection.href,
            resourceHref = resourceHref,
            title = payload.title.ifBlank { "Untitled event" },
            description = payload.description?.ifBlank { null },
            location = payload.location?.ifBlank { null },
            locationMapVerified = payload.location?.takeIf { it.isNotBlank() }?.let { payload.locationMapVerified },
            startsAtMillis = start,
            endsAtMillis = if (end > start) {
                end
            } else if (payload.allDay) {
                payload.date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
            } else {
                start + 60L * 60L * 1000L
            },
            allDay = payload.allDay,
            recurrenceRule = payload.recurrenceRule?.ifBlank { null },
            isRecurring = !payload.recurrenceRule.isNullOrBlank() || !existing.rDatesCsv.isNullOrBlank(),
            timezoneId = if (payload.allDay) null else existing.timezoneId ?: zoneId.id,
            remindersCsv = payload.reminderMinutes.normalizedReminderOffsets().takeIf { it.isNotEmpty() }?.joinToString(","),
            status = payload.status?.ifBlank { null },
            classification = payload.classification?.ifBlank { null },
            transparency = payload.transparency?.ifBlank { null },
            categories = payload.categories?.ifBlank { null },
            organizerJson = payload.organizerJson,
            attendeesJson = payload.attendeesJson,
            color = targetCollection.color,
            manualColor = payload.manualColor,
            sequence = existing.sequence + 1,
        ).sanitizedFor(targetCollection)
        if (existingIsAndroid && targetIsAndroid) {
            val eventId = androidCalendarProviderClient.eventIdFromHref(existing.resourceHref)
                ?: error("Android event id is missing.")
            val androidUpdated = updated.copy(resourceHref = existing.resourceHref, uid = existing.uid)
            androidCalendarProviderClient.updateEvent(eventId, targetCollection.androidCalendarId(), androidUpdated)
            upsertLocalResource(androidUpdated.collectionHref, androidUpdated.resourceHref, null, ComponentType.Event, androidUpdated.uid, "android-provider:$eventId")
            database.eventDao().upsert(androidUpdated)
            markRecentAndroidLocalWrite(androidUpdated.resourceHref)
            return
        }
        if (!existingIsAndroid && targetIsAndroid) {
            val eventId = androidCalendarProviderClient.insertEvent(targetCollection.androidCalendarId(), updated)
            val androidEvent = updated.copy(
                uid = "android-event-$eventId",
                resourceHref = androidCalendarProviderClient.eventHref(eventId),
            )
            upsertLocalResource(androidEvent.collectionHref, androidEvent.resourceHref, null, ComponentType.Event, androidEvent.uid, "android-provider:$eventId")
            database.eventDao().upsert(androidEvent)
            markRecentAndroidLocalWrite(androidEvent.resourceHref)
            if (existing.collectionHref.isLocalCollectionHref()) {
                database.eventDao().deleteByResource(existing.resourceHref)
                database.resourceDao().delete(existing.resourceHref)
            } else {
                enqueueDelete(existing.collectionHref, existing.resourceHref, ComponentType.Event, resource?.etag)
                database.eventDao().deleteByResource(existing.resourceHref)
                database.resourceDao().delete(existing.resourceHref)
            }
            return
        }
        if (existingIsAndroid && !targetIsAndroid) {
            val eventId = androidCalendarProviderClient.eventIdFromHref(existing.resourceHref)
                ?: error("Android event id is missing.")
            androidCalendarProviderClient.deleteEvent(eventId)
            database.eventDao().deleteByResource(existing.resourceHref)
            database.resourceDao().delete(existing.resourceHref)
            markRecentAndroidLocalDelete(existing.resourceHref)
        }
        val raw = icalCodec.serializeEvent(updated, originalRawIcs = resource?.rawIcs.takeUnless { moved })
        if (moved) {
            database.eventDao().deleteByResource(existing.resourceHref)
            database.resourceDao().delete(existing.resourceHref)
            enqueueDelete(existing.collectionHref, existing.resourceHref, ComponentType.Event, resource?.etag)
            upsertLocalResource(updated.collectionHref, updated.resourceHref, null, ComponentType.Event, updated.uid, raw)
            enqueuePut(updated.collectionHref, updated.resourceHref, ComponentType.Event, raw, null)
        } else {
            upsertLocalResource(updated.collectionHref, updated.resourceHref, resource?.etag, ComponentType.Event, updated.uid, raw)
            enqueuePut(updated.collectionHref, updated.resourceHref, ComponentType.Event, raw, resource?.etag)
        }
        database.eventDao().upsert(updated)
    }

    suspend fun updateEventOccurrence(uid: String, occurrenceStartMillis: Long, payload: EventEditPayload) {
        val existing = database.eventDao().get(uid) ?: return
        if (existing.recurrenceRule.isNullOrBlank()) {
            updateEvent(uid, payload)
            return
        }
        if (isReadOnlyCollectionHref(existing.collectionHref)) error("Read-only calendars cannot be edited.")
        val targetCollection = payload.collectionHref?.let { database.collectionDao().get(it) }
            ?: database.collectionDao().get(existing.collectionHref)
            ?: error("Calendar not found.")
        if (targetCollection.href != existing.collectionHref || targetCollection.isAndroidProviderCollection()) {
            deleteEventOccurrence(uid, occurrenceStartMillis)
            createEvent(payload.copy(recurrenceRule = null))
            return
        }
        val resource = database.resourceDao().get(existing.resourceHref)
        val endDate = payload.endDate ?: payload.date
        val start = if (payload.allDay) {
            payload.date.atStartOfDay(zoneId).toInstant().toEpochMilli()
        } else {
            payload.date.atTime(payload.startTime ?: LocalTime.of(9, 0)).atZone(zoneId).toInstant().toEpochMilli()
        }
        val end = if (payload.allDay) {
            endDate.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        } else {
            endDate.atTime(payload.endTime ?: (payload.startTime ?: LocalTime.of(9, 0)).plusHours(1))
                .atZone(zoneId).toInstant().toEpochMilli()
        }
        val replacement = existing.copy(
            title = payload.title.ifBlank { "Untitled event" },
            description = payload.description?.ifBlank { null },
            location = payload.location?.ifBlank { null },
            startsAtMillis = start,
            endsAtMillis = if (end > start) end else start + HOUR_MILLIS,
            allDay = payload.allDay,
            recurrenceRule = null,
            isRecurring = false,
            remindersCsv = payload.reminderMinutes.normalizedReminderOffsets().takeIf { it.isNotEmpty() }?.joinToString(","),
            status = payload.status?.ifBlank { null },
            classification = payload.classification?.ifBlank { null },
            transparency = payload.transparency?.ifBlank { null },
            categories = payload.categories?.ifBlank { null },
            organizerJson = payload.organizerJson,
            attendeesJson = payload.attendeesJson,
            timezoneId = if (payload.allDay) null else existing.timezoneId ?: zoneId.id,
            manualColor = payload.manualColor,
        )
        val exDates = existing.exDatesCsv
            ?.split(',')
            ?.mapNotNull { it.trim().toLongOrNull() }
            ?.filterNot { it == occurrenceStartMillis }
        val updated = existing.copy(
            exDatesCsv = exDates?.takeIf { it.isNotEmpty() }?.joinToString(","),
            recurrenceOverridesJson = RecurrenceOverrideCodec.upsertEvent(
                existing.recurrenceOverridesJson,
                EventRecurrenceOverride.fromEvent(occurrenceStartMillis, replacement),
            ),
            sequence = existing.sequence + 1,
        )
        val raw = icalCodec.serializeEvent(updated, resource?.rawIcs)
        upsertLocalResource(updated.collectionHref, updated.resourceHref, resource?.etag, ComponentType.Event, updated.uid, raw)
        database.eventDao().upsert(updated)
        enqueuePut(updated.collectionHref, updated.resourceHref, ComponentType.Event, raw, resource?.etag)
    }

    suspend fun updateEventFollowing(uid: String, occurrenceStartMillis: Long, payload: EventEditPayload) {
        val existing = database.eventDao().get(uid) ?: return
        if (existing.recurrenceRule.isNullOrBlank() || occurrenceStartMillis <= existing.startsAtMillis) {
            updateEvent(uid, payload)
            return
        }
        deleteEventFollowing(uid, occurrenceStartMillis)
        createEvent(payload)
    }

    suspend fun moveTimedEvent(uid: String, date: LocalDate, startTime: LocalTime, endTime: LocalTime) {
        val existing = database.eventDao().get(uid) ?: return
        moveTimedEvent(uid, existing.startsAtMillis, date, startTime, endTime)
    }

    suspend fun moveTimedEvent(uid: String, occurrenceStartMillis: Long, date: LocalDate, startTime: LocalTime, endTime: LocalTime) {
        val existing = database.eventDao().get(uid) ?: return
        val payload = EventEditPayload(
            title = existing.title,
            collectionHref = existing.collectionHref,
            date = date,
            endDate = date,
            startTime = startTime,
            endTime = endTime,
            allDay = false,
            description = existing.description,
            location = existing.location,
            locationMapVerified = existing.locationMapVerified,
            manualColor = existing.manualColor,
            recurrenceRule = if (existing.recurrenceRule.isNullOrBlank()) existing.recurrenceRule else null,
            reminderMinutes = existing.remindersCsv.toMinutesList(),
            status = existing.status,
            classification = existing.classification,
            transparency = existing.transparency,
            categories = existing.categories,
            organizerJson = existing.organizerJson,
            attendeesJson = existing.attendeesJson,
        )
        if (existing.recurrenceRule.isNullOrBlank()) {
            updateEvent(uid, payload)
        } else {
            updateEventOccurrence(uid, occurrenceStartMillis, payload)
        }
    }

    suspend fun moveAllDayEvent(uid: String, occurrenceStartMillis: Long, date: LocalDate) {
        val existing = database.eventDao().get(uid) ?: return
        val currentStart = existing.startsAtMillis.toDate()
        val currentEnd = existing.endDateInclusive().coerceAtLeast(currentStart)
        val spanDays = ChronoUnit.DAYS.between(currentStart, currentEnd).coerceAtLeast(0L)
        val payload = EventEditPayload(
            title = existing.title,
            collectionHref = existing.collectionHref,
            date = date,
            endDate = date.plusDays(spanDays),
            startTime = null,
            endTime = null,
            allDay = true,
            description = existing.description,
            location = existing.location,
            locationMapVerified = existing.locationMapVerified,
            manualColor = existing.manualColor,
            recurrenceRule = if (existing.recurrenceRule.isNullOrBlank()) existing.recurrenceRule else null,
            reminderMinutes = existing.remindersCsv.toMinutesList(),
            status = existing.status,
            classification = existing.classification,
            transparency = existing.transparency,
            categories = existing.categories,
            organizerJson = existing.organizerJson,
            attendeesJson = existing.attendeesJson,
        )
        if (existing.recurrenceRule.isNullOrBlank()) {
            updateEvent(uid, payload)
        } else {
            updateEventOccurrence(uid, occurrenceStartMillis, payload)
        }
    }

    suspend fun setEventParticipation(uid: String, attendeeEmails: List<String>, partstat: String) {
        val existing = database.eventDao().get(uid) ?: return
        if (isReadOnlyCollectionHref(existing.collectionHref) || isAndroidProviderCollectionHref(existing.collectionHref)) return
        val resource = database.resourceDao().get(existing.resourceHref)
        val updatedAttendees = existing.attendeesJson.updateAttendeePartstat(attendeeEmails, partstat)
            ?: return
        val updated = existing.copy(attendeesJson = updatedAttendees, sequence = existing.sequence + 1)
        val raw = icalCodec.serializeEvent(updated, resource?.rawIcs)
        upsertLocalResource(updated.collectionHref, updated.resourceHref, resource?.etag, ComponentType.Event, updated.uid, raw)
        database.eventDao().upsert(updated)
        enqueuePut(updated.collectionHref, updated.resourceHref, ComponentType.Event, raw, resource?.etag)
    }

    suspend fun copyEventTo(uid: String, collectionHref: String) {
        val existing = database.eventDao().get(uid) ?: return
        val targetCollection = database.collectionDao().get(collectionHref) ?: return
        if (!targetCollection.supportsEvents || targetCollection.isReadOnlyCollection() || !targetCollection.canCreateResources()) return
        val newUid = newUid()
        val resourceHref = targetCollection.newResourceHref(newUid)
        val event = existing.copy(
            uid = newUid,
            collectionHref = targetCollection.href,
            resourceHref = resourceHref,
            color = targetCollection.color,
            syncError = null,
        ).sanitizedFor(targetCollection)
        if (targetCollection.isAndroidProviderCollection()) {
            val eventId = androidCalendarProviderClient.insertEvent(targetCollection.androidCalendarId(), event)
            val androidEvent = event.copy(
                uid = "android-event-$eventId",
                resourceHref = androidCalendarProviderClient.eventHref(eventId),
            )
            upsertLocalResource(androidEvent.collectionHref, androidEvent.resourceHref, null, ComponentType.Event, androidEvent.uid, "android-provider:$eventId")
            database.eventDao().upsert(androidEvent)
            markRecentAndroidLocalWrite(androidEvent.resourceHref)
            return
        }
        val raw = icalCodec.serializeEvent(event)
        upsertLocalResource(event.collectionHref, event.resourceHref, null, ComponentType.Event, event.uid, raw)
        database.eventDao().upsert(event)
        enqueuePut(event.collectionHref, event.resourceHref, ComponentType.Event, raw, null)
    }

    suspend fun createTask(payload: TaskEditPayload) {
        val collection = payload.collectionHref?.let { database.collectionDao().get(it) }
            ?.takeUnless { it.isReadOnlyCollection() || !it.canCreateResources() }
            ?: database.collectionDao().taskCollections().firstOrNull { !it.isReadOnlyCollection() && it.canCreateResources() }
            ?: error("No writable task list has been synced yet.")
        val uid = newUid()
        val resourceHref = collection.newResourceHref(uid)
        val parentUid = validatedParentUid(collection.href, payload.parentUid, taskUid = uid)
        val task = TaskEntity(
            uid = uid,
            collectionHref = collection.href,
            resourceHref = resourceHref,
            title = payload.title.ifBlank { "Untitled task" },
            notes = payload.notes?.ifBlank { null },
            location = payload.location?.ifBlank { null },
            locationMapVerified = payload.location?.takeIf { it.isNotBlank() }?.let { payload.locationMapVerified },
            url = payload.url?.ifBlank { null },
            categories = payload.categories?.ifBlank { null },
            dueAtMillis = payload.dueDate.toTaskMillis(payload.dueTime, payload.dueHasTime, LocalTime.of(17, 0)),
            dueHasTime = payload.dueDate != null && payload.dueHasTime,
            startAtMillis = payload.startDate.toTaskMillis(payload.startTime, payload.startHasTime, LocalTime.of(9, 0)),
            startHasTime = payload.startDate != null && payload.startHasTime,
            completedAtMillis = if (payload.isCompleted) Instant.now().toEpochMilli() else null,
            isCompleted = payload.isCompleted,
            status = payload.status?.takeIf { it.isNotBlank() }
                ?: if (payload.isCompleted) "COMPLETED" else "NEEDS-ACTION",
            priority = payload.priority?.coerceIn(1, 9),
            percentComplete = payload.percentComplete?.coerceIn(0, 100),
            parentUid = parentUid,
            recurrenceRule = payload.recurrenceRule?.ifBlank { null },
            remindersCsv = payload.reminderMinutes.normalizedReminderOffsets().takeIf { it.isNotEmpty() }?.joinToString(","),
            timezoneId = if (
                (payload.startDate != null && payload.startHasTime) ||
                (payload.dueDate != null && payload.dueHasTime)
            ) zoneId.id else null,
            color = collection.color,
            manualColor = payload.manualColor,
        ).withValidIcalSchedule()
        val raw = icalCodec.serializeTask(task)
        upsertLocalResource(collection.href, resourceHref, null, ComponentType.Task, uid, raw)
        database.taskDao().upsert(task)
        enqueuePut(collection.href, resourceHref, ComponentType.Task, raw, null)
    }

    suspend fun updateTaskManualColor(uid: String, manualColor: Int?) {
        val existing = database.taskDao().get(uid) ?: return
        database.taskDao().upsert(existing.copy(manualColor = manualColor))
    }

    suspend fun updateTask(uid: String, payload: TaskEditPayload) {
        val existing = database.taskDao().get(uid) ?: return
        val resource = database.resourceDao().get(existing.resourceHref)
        val targetCollection = payload.collectionHref?.let { database.collectionDao().get(it) }
            ?: database.collectionDao().get(existing.collectionHref)
            ?: error("Task list not found.")
        if (targetCollection.isReadOnlyCollection()) error("Read-only task lists cannot be edited.")
        val moved = targetCollection.href != existing.collectionHref
        if (moved && database.taskDao().children(existing.collectionHref, existing.uid).isNotEmpty()) {
            error("Move or detach this task's subtasks before changing its task list.")
        }
        val parentUid = validatedParentUid(targetCollection.href, payload.parentUid, existing.uid)
        val resourceHref = if (moved) targetCollection.newResourceHref(existing.uid) else existing.resourceHref
        val updated = existing.copy(
            collectionHref = targetCollection.href,
            resourceHref = resourceHref,
            title = payload.title.ifBlank { "Untitled task" },
            notes = payload.notes?.ifBlank { null },
            location = payload.location?.ifBlank { null },
            locationMapVerified = payload.location?.takeIf { it.isNotBlank() }?.let { payload.locationMapVerified },
            url = payload.url?.ifBlank { null },
            categories = payload.categories?.ifBlank { null },
            dueAtMillis = payload.dueDate.toTaskMillis(payload.dueTime, payload.dueHasTime, LocalTime.of(17, 0)),
            dueHasTime = payload.dueDate != null && payload.dueHasTime,
            startAtMillis = payload.startDate.toTaskMillis(payload.startTime, payload.startHasTime, LocalTime.of(9, 0)),
            startHasTime = payload.startDate != null && payload.startHasTime,
            completedAtMillis = when {
                payload.isCompleted && existing.completedAtMillis == null -> Instant.now().toEpochMilli()
                payload.isCompleted -> existing.completedAtMillis
                else -> null
            },
            isCompleted = payload.isCompleted,
            status = payload.status?.takeIf { it.isNotBlank() }
                ?: existing.status?.takeIf {
                    // Preserve IN-PROCESS / CANCELLED when the editor didn't override it
                    // and the completion state hasn't changed.
                    payload.isCompleted == existing.isCompleted
                }
                ?: if (payload.isCompleted) "COMPLETED" else "NEEDS-ACTION",
            priority = payload.priority?.coerceIn(1, 9),
            percentComplete = payload.percentComplete?.coerceIn(0, 100),
            parentUid = parentUid,
            recurrenceRule = payload.recurrenceRule?.ifBlank { null },
            remindersCsv = payload.reminderMinutes.normalizedReminderOffsets().takeIf { it.isNotEmpty() }?.joinToString(","),
            timezoneId = if (
                (payload.startDate != null && payload.startHasTime) ||
                (payload.dueDate != null && payload.dueHasTime)
            ) existing.timezoneId ?: zoneId.id else null,
            color = targetCollection.color,
            manualColor = payload.manualColor,
            sequence = existing.sequence + 1,
        ).withValidIcalSchedule()
        val raw = icalCodec.serializeTask(updated, originalRawIcs = resource?.rawIcs.takeUnless { moved })
        if (moved) {
            database.taskDao().deleteByResource(existing.resourceHref)
            database.resourceDao().delete(existing.resourceHref)
            enqueueDelete(existing.collectionHref, existing.resourceHref, ComponentType.Task, resource?.etag)
            upsertLocalResource(updated.collectionHref, updated.resourceHref, null, ComponentType.Task, updated.uid, raw)
            enqueuePut(updated.collectionHref, updated.resourceHref, ComponentType.Task, raw, null)
        } else {
            upsertLocalResource(updated.collectionHref, updated.resourceHref, resource?.etag, ComponentType.Task, updated.uid, raw)
            enqueuePut(updated.collectionHref, updated.resourceHref, ComponentType.Task, raw, resource?.etag)
        }
        database.taskDao().upsert(updated)
    }

    suspend fun updateTaskOccurrence(uid: String, occurrenceStartMillis: Long, payload: TaskEditPayload) {
        val existing = database.taskDao().get(uid) ?: return
        if (existing.recurrenceRule.isNullOrBlank()) {
            updateTask(uid, payload)
            return
        }
        if (isReadOnlyCollectionHref(existing.collectionHref)) error("Read-only task lists cannot be edited.")
        val targetCollection = payload.collectionHref?.let { database.collectionDao().get(it) }
            ?: database.collectionDao().get(existing.collectionHref)
            ?: error("Task list not found.")
        if (targetCollection.href != existing.collectionHref) {
            val resource = database.resourceDao().get(existing.resourceHref)
            val exSet = existing.exDatesCsv
                ?.split(',')
                ?.mapNotNull { it.trim().toLongOrNull() }
                ?.toMutableSet()
                ?: mutableSetOf()
            exSet += occurrenceStartMillis
            val updatedMaster = existing.copy(
                exDatesCsv = exSet.sorted().joinToString(","),
                sequence = existing.sequence + 1,
            ).withValidIcalSchedule()
            val raw = icalCodec.serializeTask(updatedMaster, resource?.rawIcs)
            upsertLocalResource(updatedMaster.collectionHref, updatedMaster.resourceHref, resource?.etag, ComponentType.Task, updatedMaster.uid, raw)
            enqueuePut(updatedMaster.collectionHref, updatedMaster.resourceHref, ComponentType.Task, raw, resource?.etag)
            database.taskDao().upsert(updatedMaster)
            createTask(payload.copy(recurrenceRule = null))
            return
        }
        val resource = database.resourceDao().get(existing.resourceHref)
        val exDates = existing.exDatesCsv
            ?.split(',')
            ?.mapNotNull { it.trim().toLongOrNull() }
            ?.filterNot { it == occurrenceStartMillis }
        val replacement = existing.copy(
            title = payload.title.ifBlank { "Untitled task" },
            notes = payload.notes?.ifBlank { null },
            location = payload.location?.ifBlank { null },
            url = payload.url?.ifBlank { null },
            categories = payload.categories?.ifBlank { null },
            dueAtMillis = payload.dueDate.toTaskMillis(payload.dueTime, payload.dueHasTime, LocalTime.of(17, 0)),
            dueHasTime = payload.dueDate != null && payload.dueHasTime,
            startAtMillis = payload.startDate.toTaskMillis(payload.startTime, payload.startHasTime, LocalTime.of(9, 0)),
            startHasTime = payload.startDate != null && payload.startHasTime,
            completedAtMillis = if (payload.isCompleted) existing.completedAtMillis ?: Instant.now().toEpochMilli() else null,
            isCompleted = payload.isCompleted,
            status = payload.status?.takeIf { it.isNotBlank() }
                ?: if (payload.isCompleted) "COMPLETED" else "NEEDS-ACTION",
            priority = payload.priority?.coerceIn(1, 9),
            percentComplete = payload.percentComplete?.coerceIn(0, 100),
            recurrenceRule = null,
            exDatesCsv = null,
            rDatesCsv = null,
            recurrenceOverridesJson = null,
            remindersCsv = payload.reminderMinutes.normalizedReminderOffsets().takeIf { it.isNotEmpty() }?.joinToString(","),
            timezoneId = if (
                (payload.startDate != null && payload.startHasTime) ||
                (payload.dueDate != null && payload.dueHasTime)
            ) existing.timezoneId ?: zoneId.id else null,
        ).withValidIcalSchedule()
        val updatedMaster = existing.copy(
            exDatesCsv = exDates?.takeIf { it.isNotEmpty() }?.joinToString(","),
            recurrenceOverridesJson = RecurrenceOverrideCodec.upsertTask(
                existing.recurrenceOverridesJson,
                TaskRecurrenceOverride.fromTask(occurrenceStartMillis, replacement),
            ),
            sequence = existing.sequence + 1,
        ).withValidIcalSchedule()
        val raw = icalCodec.serializeTask(updatedMaster, resource?.rawIcs)
        upsertLocalResource(updatedMaster.collectionHref, updatedMaster.resourceHref, resource?.etag, ComponentType.Task, updatedMaster.uid, raw)
        enqueuePut(updatedMaster.collectionHref, updatedMaster.resourceHref, ComponentType.Task, raw, resource?.etag)
        database.taskDao().upsert(updatedMaster)
    }

    suspend fun updateTaskFollowing(uid: String, occurrenceStartMillis: Long, payload: TaskEditPayload) {
        val existing = database.taskDao().get(uid) ?: return
        if (existing.recurrenceRule.isNullOrBlank()) {
            updateTask(uid, payload)
            return
        }
        val masterStart = existing.startAtMillis ?: existing.dueAtMillis ?: Long.MAX_VALUE
        if (occurrenceStartMillis <= masterStart) {
            updateTask(uid, payload)
            return
        }
        if (isReadOnlyCollectionHref(existing.collectionHref)) error("Read-only task lists cannot be edited.")
        val resource = database.resourceDao().get(existing.resourceHref)
        val newRule = existing.recurrenceRule.withRecurrenceUntilBefore(occurrenceStartMillis, existing.startHasTime.not() && existing.dueHasTime.not())
        val keptEx = existing.exDatesCsv
            ?.split(',')
            ?.mapNotNull { it.trim().toLongOrNull() }
            ?.filter { it < occurrenceStartMillis }
        val keptRDates = existing.rDatesCsv
            ?.split(',')
            ?.mapNotNull { it.trim().toLongOrNull() }
            ?.filter { it < occurrenceStartMillis }
        val updatedMaster = existing.copy(
            recurrenceRule = newRule,
            exDatesCsv = keptEx?.takeIf { it.isNotEmpty() }?.joinToString(","),
            rDatesCsv = keptRDates?.takeIf { it.isNotEmpty() }?.joinToString(","),
            recurrenceOverridesJson = RecurrenceOverrideCodec.encodeTasks(
                RecurrenceOverrideCodec.decodeTasks(existing.recurrenceOverridesJson)
                    .filter { it.recurrenceIdMillis < occurrenceStartMillis },
            ),
            sequence = existing.sequence + 1,
        ).withValidIcalSchedule()
        val raw = icalCodec.serializeTask(updatedMaster, resource?.rawIcs)
        upsertLocalResource(updatedMaster.collectionHref, updatedMaster.resourceHref, resource?.etag, ComponentType.Task, updatedMaster.uid, raw)
        enqueuePut(updatedMaster.collectionHref, updatedMaster.resourceHref, ComponentType.Task, raw, resource?.etag)
        database.taskDao().upsert(updatedMaster)
        createTask(payload)
    }

    suspend fun setTaskCompleted(uid: String, completed: Boolean) {
        setTaskStatus(uid, if (completed) "COMPLETED" else "NEEDS-ACTION")
    }

    /**
     * Sets the iCal STATUS (NEEDS-ACTION / IN-PROCESS / COMPLETED / CANCELLED) of a
     * task and keeps the derived [TaskEntity.isCompleted] / completedAtMillis fields
     * in sync. COMPLETED is the only status that counts as "done".
     */
    suspend fun setTaskStatus(uid: String, status: String) {
        val existing = database.taskDao().get(uid) ?: return
        if (isReadOnlyCollectionHref(existing.collectionHref)) return
        val resource = database.resourceDao().get(existing.resourceHref)
        val completed = status.equals("COMPLETED", ignoreCase = true)
        val updated = existing.copy(
            isCompleted = completed,
            completedAtMillis = when {
                completed && existing.completedAtMillis != null -> existing.completedAtMillis
                completed -> Instant.now().toEpochMilli()
                else -> null
            },
            status = status.uppercase(),
            sequence = existing.sequence + 1,
        ).withValidIcalSchedule()
        val raw = icalCodec.serializeTask(updated, resource?.rawIcs)
        upsertLocalResource(updated.collectionHref, updated.resourceHref, resource?.etag, ComponentType.Task, updated.uid, raw)
        database.taskDao().upsert(updated)
        enqueuePut(updated.collectionHref, updated.resourceHref, ComponentType.Task, raw, resource?.etag)
    }

    suspend fun setTaskPriority(uid: String, priority: Int) {
        val existing = database.taskDao().get(uid) ?: return
        if (isReadOnlyCollectionHref(existing.collectionHref)) return
        val resource = database.resourceDao().get(existing.resourceHref)
        val updated = existing.copy(
            priority = priority.coerceIn(1, 9),
            sequence = existing.sequence + 1,
        ).withValidIcalSchedule()
        val raw = icalCodec.serializeTask(updated, resource?.rawIcs)
        upsertLocalResource(updated.collectionHref, updated.resourceHref, resource?.etag, ComponentType.Task, updated.uid, raw)
        database.taskDao().upsert(updated)
        enqueuePut(updated.collectionHref, updated.resourceHref, ComponentType.Task, raw, resource?.etag)
    }

    suspend fun setTaskProgress(uid: String, progress: Int) {
        val existing = database.taskDao().get(uid) ?: return
        if (isReadOnlyCollectionHref(existing.collectionHref)) return
        val resource = database.resourceDao().get(existing.resourceHref)
        val updated = existing.copy(
            percentComplete = progress.coerceIn(0, 100),
            sequence = existing.sequence + 1,
        ).withValidIcalSchedule()
        val raw = icalCodec.serializeTask(updated, resource?.rawIcs)
        upsertLocalResource(updated.collectionHref, updated.resourceHref, resource?.etag, ComponentType.Task, updated.uid, raw)
        database.taskDao().upsert(updated)
        enqueuePut(updated.collectionHref, updated.resourceHref, ComponentType.Task, raw, resource?.etag)
    }

    suspend fun moveTimedTask(uid: String, date: LocalDate, startTime: LocalTime, endTime: LocalTime) {
        val existing = database.taskDao().get(uid) ?: return
        moveTimedTask(uid, existing.startAtMillis ?: existing.dueAtMillis ?: System.currentTimeMillis(), date, startTime, endTime)
    }

    suspend fun moveTimedTask(uid: String, occurrenceStartMillis: Long, date: LocalDate, startTime: LocalTime, endTime: LocalTime) {
        val existing = database.taskDao().get(uid) ?: return
        val payload = TaskEditPayload(
            title = existing.title,
            collectionHref = existing.collectionHref,
            notes = existing.notes,
            location = existing.location,
            locationMapVerified = existing.locationMapVerified,
            manualColor = existing.manualColor,
            url = existing.url,
            categories = existing.categories,
            startDate = date,
            startTime = startTime,
            startHasTime = true,
            dueDate = date,
            dueTime = endTime,
            dueHasTime = true,
            priority = existing.priority,
            percentComplete = existing.percentComplete,
            isCompleted = existing.isCompleted,
            recurrenceRule = if (existing.recurrenceRule.isNullOrBlank()) existing.recurrenceRule else null,
            parentUid = existing.parentUid,
            status = existing.status,
            reminderMinutes = existing.remindersCsv.toMinutesList(),
        )
        if (existing.recurrenceRule.isNullOrBlank()) {
            updateTask(uid, payload)
        } else {
            updateTaskOccurrence(uid, occurrenceStartMillis, payload)
        }
    }

    suspend fun moveAllDayTask(uid: String, occurrenceStartMillis: Long, date: LocalDate) {
        val existing = database.taskDao().get(uid) ?: return
        val currentStart = existing.startAtMillis?.toDate() ?: existing.dueAtMillis?.toDate() ?: date
        val currentEnd = (existing.dueAtMillis?.toDate() ?: existing.startAtMillis?.toDate() ?: currentStart).coerceAtLeast(currentStart)
        val spanDays = ChronoUnit.DAYS.between(currentStart, currentEnd).coerceAtLeast(0L)
        val payload = TaskEditPayload(
            title = existing.title,
            collectionHref = existing.collectionHref,
            notes = existing.notes,
            location = existing.location,
            locationMapVerified = existing.locationMapVerified,
            manualColor = existing.manualColor,
            url = existing.url,
            categories = existing.categories,
            startDate = date,
            startTime = null,
            startHasTime = false,
            dueDate = date.plusDays(spanDays),
            dueTime = null,
            dueHasTime = false,
            priority = existing.priority,
            percentComplete = existing.percentComplete,
            isCompleted = existing.isCompleted,
            recurrenceRule = if (existing.recurrenceRule.isNullOrBlank()) existing.recurrenceRule else null,
            parentUid = existing.parentUid,
            status = existing.status,
            reminderMinutes = existing.remindersCsv.toMinutesList(),
        )
        if (existing.recurrenceRule.isNullOrBlank()) {
            updateTask(uid, payload)
        } else {
            updateTaskOccurrence(uid, occurrenceStartMillis, payload)
        }
    }

    suspend fun copyTaskTo(uid: String, collectionHref: String) {
        val existing = database.taskDao().get(uid) ?: return
        val targetCollection = database.collectionDao().get(collectionHref) ?: return
        if (!targetCollection.supportsTasks || targetCollection.isReadOnlyCollection() || !targetCollection.canCreateResources()) return
        val newUid = newUid()
        val resourceHref = targetCollection.newResourceHref(newUid)
        val task = existing.copy(
            uid = newUid,
            collectionHref = targetCollection.href,
            resourceHref = resourceHref,
            parentUid = existing.parentUid
                ?.takeIf { database.taskDao().byUidInCollection(targetCollection.href, it) != null },
            color = targetCollection.color,
            syncError = null,
        ).withValidIcalSchedule()
        val raw = icalCodec.serializeTask(task)
        upsertLocalResource(task.collectionHref, task.resourceHref, null, ComponentType.Task, task.uid, raw)
        database.taskDao().upsert(task)
        enqueuePut(task.collectionHref, task.resourceHref, ComponentType.Task, raw, null)
    }

    suspend fun deleteTask(uid: String) {
        val task = database.taskDao().get(uid) ?: return
        val collection = database.collectionDao().get(task.collectionHref) ?: return
        if (collection.isReadOnlyCollection() || !collection.canDeleteResources()) return
        reparentTaskChildren(task)
        val resource = database.resourceDao().get(task.resourceHref)
        if (task.collectionHref.isLocalCollectionHref()) {
            database.taskDao().deleteByResource(task.resourceHref)
            database.resourceDao().delete(task.resourceHref)
        } else {
            enqueueDelete(task.collectionHref, task.resourceHref, ComponentType.Task, resource?.etag)
        }
    }

    private suspend fun validatedParentUid(
        collectionHref: String,
        requestedParentUid: String?,
        taskUid: String,
    ): String? {
        val parentUid = requestedParentUid?.trim()?.takeIf { it.isNotBlank() } ?: return null
        require(parentUid != taskUid) { "A task cannot be its own parent." }
        require(database.taskDao().byUidInCollection(collectionHref, parentUid) != null) {
            "The selected parent task is not in this task list."
        }
        var cursor: String? = parentUid
        val visited = mutableSetOf(taskUid)
        while (cursor != null && visited.add(cursor)) {
            val parent = database.taskDao().byUidInCollection(collectionHref, cursor) ?: break
            cursor = parent.parentUid
        }
        require(cursor == null || cursor !in visited) { "This parent would create a task cycle." }
        return parentUid
    }

    private suspend fun reparentTaskChildren(parent: TaskEntity) {
        database.taskDao().children(parent.collectionHref, parent.uid).forEach { child ->
            val resource = database.resourceDao().get(child.resourceHref)
            val updated = child.copy(
                parentUid = parent.parentUid,
                sequence = child.sequence + 1,
            )
            val raw = icalCodec.serializeTask(updated, resource?.rawIcs)
            upsertLocalResource(updated.collectionHref, updated.resourceHref, resource?.etag, ComponentType.Task, updated.uid, raw)
            database.taskDao().upsert(updated)
            if (!updated.collectionHref.isLocalCollectionHref()) {
                enqueuePut(updated.collectionHref, updated.resourceHref, ComponentType.Task, raw, resource?.etag)
            }
        }
    }

    suspend fun deleteEvent(uid: String) {
        val event = database.eventDao().get(uid) ?: return
        val collection = database.collectionDao().get(event.collectionHref) ?: return
        if (collection.isReadOnlyCollection() || !collection.canDeleteResources()) return
        if (isAndroidProviderCollectionHref(event.collectionHref)) {
            val eventId = androidCalendarProviderClient.eventIdFromHref(event.resourceHref) ?: return
            androidCalendarProviderClient.deleteEvent(eventId)
            database.eventDao().deleteByResource(event.resourceHref)
            database.resourceDao().delete(event.resourceHref)
            markRecentAndroidLocalDelete(event.resourceHref)
            return
        }
        val resource = database.resourceDao().get(event.resourceHref)
        if (event.collectionHref.isLocalCollectionHref()) {
            database.eventDao().deleteByResource(event.resourceHref)
            database.resourceDao().delete(event.resourceHref)
        } else {
            enqueueDelete(event.collectionHref, event.resourceHref, ComponentType.Event, resource?.etag)
        }
    }

    /**
     * Deletes a single occurrence of a recurring event by adding its start to the master's
     * EXDATE set. The rest of the series is untouched. Falls back to deleting the whole event
     * when it isn't actually recurring.
     */
    suspend fun deleteEventOccurrence(uid: String, occurrenceStartMillis: Long) {
        val existing = database.eventDao().get(uid) ?: return
        if (existing.recurrenceRule.isNullOrBlank()) {
            deleteEvent(uid)
            return
        }
        if (isReadOnlyCollectionHref(existing.collectionHref)) error("Read-only calendars cannot be edited.")
        val resource = database.resourceDao().get(existing.resourceHref)
        val exSet = existing.exDatesCsv
            ?.split(',')
            ?.mapNotNull { it.trim().toLongOrNull() }
            ?.toMutableSet()
            ?: mutableSetOf()
        exSet += occurrenceStartMillis
        val updated = existing.copy(
            exDatesCsv = exSet.sorted().joinToString(","),
            sequence = existing.sequence + 1,
        )
        if (isAndroidProviderCollectionHref(existing.collectionHref)) {
            cancelAndroidEventOccurrence(updated, occurrenceStartMillis)
            return
        }
        val raw = icalCodec.serializeEvent(updated, resource?.rawIcs)
        upsertLocalResource(updated.collectionHref, updated.resourceHref, resource?.etag, ComponentType.Event, updated.uid, raw)
        enqueuePut(updated.collectionHref, updated.resourceHref, ComponentType.Event, raw, resource?.etag)
        database.eventDao().upsert(updated)
    }

    /**
     * Deletes the given occurrence and every later one by capping the RRULE with an UNTIL just
     * before this occurrence (and stripping any COUNT). When the cut lands on or before the very
     * first occurrence, the whole event is removed instead.
     */
    suspend fun deleteEventFollowing(uid: String, occurrenceStartMillis: Long) {
        val existing = database.eventDao().get(uid) ?: return
        if (existing.recurrenceRule.isNullOrBlank() || occurrenceStartMillis <= existing.startsAtMillis) {
            deleteEvent(uid)
            return
        }
        if (isReadOnlyCollectionHref(existing.collectionHref)) error("Read-only calendars cannot be edited.")
        val resource = database.resourceDao().get(existing.resourceHref)
        val newRule = existing.recurrenceRule.withRecurrenceUntilBefore(occurrenceStartMillis, existing.allDay)
        val keptEx = existing.exDatesCsv
            ?.split(',')
            ?.mapNotNull { it.trim().toLongOrNull() }
            ?.filter { it < occurrenceStartMillis }
        val keptRDates = existing.rDatesCsv
            ?.split(',')
            ?.mapNotNull { it.trim().toLongOrNull() }
            ?.filter { it < occurrenceStartMillis }
        val updated = existing.copy(
            recurrenceRule = newRule,
            isRecurring = !newRule.isNullOrBlank() || !keptRDates.isNullOrEmpty(),
            exDatesCsv = keptEx?.takeIf { it.isNotEmpty() }?.joinToString(","),
            rDatesCsv = keptRDates?.takeIf { it.isNotEmpty() }?.joinToString(","),
            recurrenceOverridesJson = RecurrenceOverrideCodec.encodeEvents(
                RecurrenceOverrideCodec.decodeEvents(existing.recurrenceOverridesJson)
                    .filter { it.recurrenceIdMillis < occurrenceStartMillis },
            ),
            sequence = existing.sequence + 1,
        )
        if (isAndroidProviderCollectionHref(existing.collectionHref)) {
            persistAndroidEventUpdate(updated)
            return
        }
        val raw = icalCodec.serializeEvent(updated, resource?.rawIcs)
        upsertLocalResource(updated.collectionHref, updated.resourceHref, resource?.etag, ComponentType.Event, updated.uid, raw)
        enqueuePut(updated.collectionHref, updated.resourceHref, ComponentType.Event, raw, resource?.etag)
        database.eventDao().upsert(updated)
    }

    /** Returns this RRULE with COUNT/UNTIL removed and a new UNTIL set just before [cutMillis]. */
    private fun String.withRecurrenceUntilBefore(cutMillis: Long, allDay: Boolean): String {
        val kept = split(';')
            .filter { it.isNotBlank() }
            .filterNot {
                val key = it.substringBefore('=').trim().uppercase(Locale.US)
                key == "UNTIL" || key == "COUNT"
            }
        val untilValue = if (allDay) {
            Instant.ofEpochMilli(cutMillis).atZone(zoneId).toLocalDate().minusDays(1)
                .format(DateTimeFormatter.BASIC_ISO_DATE)
        } else {
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
                .withZone(ZoneOffset.UTC)
                .format(Instant.ofEpochMilli(cutMillis - 1000L))
        }
        return (kept + "UNTIL=$untilValue").joinToString(";")
    }

    /**
     * Repairs VTODO rows written by early prototypes before uploading them.
     * Nextcloud rejects DTSTART / DUE pairs when one is DATE and the other is
     * DATE-TIME. A mixed pair represents a timed start-only or end-only task in
     * the UI, so the stale date-only counterpart is removed.
     */
    suspend fun repairInvalidTaskSchedules() {
        database.taskDao().all().forEach { task ->
            val repaired = task.withValidIcalSchedule()
            if (repaired == task) return@forEach
            val resource = database.resourceDao().get(task.resourceHref)
            val raw = icalCodec.serializeTask(repaired, resource?.rawIcs)
            upsertLocalResource(
                collectionHref = repaired.collectionHref,
                resourceHref = repaired.resourceHref,
                etag = resource?.etag,
                componentType = ComponentType.Task,
                uid = repaired.uid,
                rawIcs = raw,
            )
            database.taskDao().upsert(repaired)
            if (!isReadOnlyCollectionHref(repaired.collectionHref)) {
                enqueuePut(repaired.collectionHref, repaired.resourceHref, ComponentType.Task, raw, resource?.etag)
            }
        }
    }

    /**
     * Older debug builds could queue many retries for the same task resource.
     * Keep the newest PUT and rewrite its payload through the validated codec
     * before any CalDAV request is made.
     */
    private suspend fun repairPendingTaskMutations(
        pendingMutations: List<PendingMutationEntity>? = null,
    ) {
        (pendingMutations ?: database.pendingMutationDao().all())
            .filter { it.action == MutationAction.Put && it.componentType == ComponentType.Task }
            .groupBy { it.resourceHref }
            .values
            .forEach { mutations ->
                val newest = mutations.maxBy { it.id }
                mutations.filterNot { it.id == newest.id }.forEach { database.pendingMutationDao().delete(it) }
                val normalizedPayload = normalizedPendingTaskPayload(newest)
                if (normalizedPayload != newest.payloadIcs) {
                    database.pendingMutationDao().updatePayload(newest.id, normalizedPayload)
                }
            }
    }

    /**
     * Some CalDAV servers, including mailbox.org/Open-Xchange, return a canonical
     * percent-encoded object href such as `uid%40kgs-calendar.ics` even when an older
     * local build had queued the same UID under another filename. Keep the server href
     * and retarget the newest local edit to it so a stuck create does not stay behind as
     * an orange sync issue or duplicate task.
     */
    private suspend fun repairDuplicateCalDavResources() {
        val collectionsByHref = database.collectionDao().all()
            .associateBy { it.href }
        val resources = database.resourceDao().duplicateCalDavCandidates()
        if (resources.isEmpty()) return

        val pendingByResource = database.pendingMutationDao().all()
            .groupBy { it.resourceHref }

        resources
            .groupBy { Triple(it.collectionHref, it.componentType, it.uid) }
            .values
            .filter { it.size > 1 }
            .forEach { duplicates ->
                val collection = collectionsByHref[duplicates.first().collectionHref] ?: return@forEach
                val canonical = duplicates.maxWithOrNull(
                    compareBy<CalendarResourceEntity> { if (it.etag != null && it.syncError == null) 1 else 0 }
                        .thenBy { if ('%' in it.href) 1 else 0 }
                        .thenByDescending { it.href.length },
                ) ?: return@forEach

                val newestPendingPut = duplicates
                    .flatMap { pendingByResource[it.href].orEmpty() }
                    .filter { it.action == MutationAction.Put }
                    .maxWithOrNull(compareBy<PendingMutationEntity> { it.createdAtMillis }.thenBy { it.id })

                if (newestPendingPut != null && newestPendingPut.resourceHref != canonical.href) {
                    val pendingRaw = newestPendingPut.payloadIcs
                        ?: duplicates.firstOrNull { it.href == newestPendingPut.resourceHref }?.rawIcs
                    if (!pendingRaw.isNullOrBlank()) {
                        val parsed = icalCodec.parse(
                            rawIcs = pendingRaw,
                            collectionHref = canonical.collectionHref,
                            resourceHref = canonical.href,
                            collectionColor = collection.color,
                        )
                        val componentType = parsed?.componentType ?: canonical.componentType
                        upsertLocalResource(
                            collectionHref = canonical.collectionHref,
                            resourceHref = canonical.href,
                            etag = canonical.etag,
                            componentType = componentType,
                            uid = canonical.uid,
                            rawIcs = pendingRaw,
                        )
                        parsed?.event?.let { event ->
                            val existing = database.eventDao().byResource(canonical.href)
                            database.eventDao().upsert(event.copy(manualColor = event.manualColor ?: existing?.manualColor))
                            database.taskDao().deleteByResource(canonical.href)
                        }
                        parsed?.task?.let { task ->
                            val existing = database.taskDao().byResource(canonical.href)
                            database.taskDao().upsert(task.copy(manualColor = existing?.manualColor).withValidIcalSchedule())
                            database.eventDao().deleteByResource(canonical.href)
                        }
                        database.pendingMutationDao().delete(newestPendingPut)
                        enqueuePut(
                            collectionHref = canonical.collectionHref,
                            resourceHref = canonical.href,
                            componentType = componentType,
                            rawIcs = pendingRaw,
                            baseEtag = canonical.etag,
                        )
                    }
                }

                duplicates
                    .filterNot { it.href == canonical.href }
                    .forEach { stale ->
                        database.pendingMutationDao().deleteForResource(stale.href)
                        database.eventDao().deleteByResource(stale.href)
                        database.taskDao().deleteByResource(stale.href)
                        database.resourceDao().delete(stale.href)
                    }
            }
    }

    /**
     * Early prototype builds could leave PUT mutations behind after a later pull had
     * already replaced the local resource with the server version. Those mutations
     * can no longer be applied conditionally because their base ETag is stale, so
     * they only keep the "pending changes" counter stuck.
     */
    private suspend fun discardSupersededPendingMutations() {
        val now = System.currentTimeMillis()
        database.pendingMutationDao().all()
            .filter { it.action == MutationAction.Put }
            .forEach { mutation ->
                val resource = database.resourceDao().get(mutation.resourceHref)
                val payload = mutation.payloadIcs?.normalizedIcsText()
                val local = resource?.rawIcs?.normalizedIcsText()
                val staleMissingLocalCreate = resource == null &&
                    mutation.baseEtag == null &&
                    now - mutation.createdAtMillis > 60L * 60L * 1000L
                val staleMissingEditedResource = resource == null && mutation.baseEtag != null
                val staleConflict = resource != null &&
                    mutation.baseEtag != null &&
                    (
                        (resource.etag != null && resource.etag != mutation.baseEtag) ||
                            resource.syncError?.contains("Conflict", ignoreCase = true) == true
                    ) &&
                    payload != null &&
                    local != null &&
                    payload != local
                if (staleMissingLocalCreate || staleMissingEditedResource || staleConflict) {
                    database.pendingMutationDao().delete(mutation)
                    if (resource?.syncError != null) {
                        database.resourceDao().markSynced(resource.href, resource.etag)
                    }
                }
            }
    }

    private suspend fun normalizedPendingTaskPayload(mutation: PendingMutationEntity): String {
        val raw = mutation.payloadIcs ?: error("Missing payload")
        database.taskDao().byResource(mutation.resourceHref)?.let { return icalCodec.serializeTask(it, raw) }
        val color = database.collectionDao().get(mutation.collectionHref)?.color ?: DEFAULT_COLORS.first()
        val parsedTask = icalCodec.parse(raw, mutation.collectionHref, mutation.resourceHref, color)?.task
        return parsedTask?.let { icalCodec.serializeTask(it, raw) } ?: raw
    }

    private fun String.normalizedIcsText(): String =
        replace("\r\n", "\n").replace('\r', '\n').trim()

    suspend fun setCollectionEnabled(href: String, enabled: Boolean) {
        database.collectionDao().updateEnabled(href, enabled)
    }

    /**
     * Persists a new manual ordering of all collections. Caller supplies the hrefs
     * in the order they should appear; we write the index as sortOrder for each.
     */
    suspend fun applyCollectionOrder(hrefs: List<String>) {
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
        database.collectionDao().updateAppearance(href, normalizedName, customDisplayName, effectiveColor, customColor)
        database.eventDao().updateColorForCollection(href, effectiveColor)
        database.taskDao().updateColorForCollection(href, effectiveColor)
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
        database.pendingMutationDao().deleteForCollection(collection.href)
        database.collectionDao().delete(collection.href)
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

    private suspend fun syncReadOnlyUrl(account: AccountEntity) {
        database.accountDao().updateSyncState("syncing", null, account.lastSyncAtMillis, account.id)
        val collectionHref = "$READ_ONLY_PREFIX${account.id}"
        val existing = database.collectionDao().get(collectionHref)
        if (existing?.isEnabled == false) {
            database.accountDao().updateSyncState("idle", null, account.lastSyncAtMillis, account.id)
            return
        }
        try {
            var contentType: String? = null
            val response = readOnlyHttpClient.newCall(
                Request.Builder()
                    .url(account.serverUrl)
                    .header("Accept", READ_ONLY_CALENDAR_ACCEPT)
                    .get()
                    .build(),
            ).execute()
            val raw = response.use { body ->
                if (!body.isSuccessful) error("URL returned HTTP ${body.code}")
                contentType = body.header("Content-Type")
                body.body?.string() ?: error("Empty calendar response.")
            }
            if (raw.looksLikeHtmlResponse(contentType)) {
                error("URL returned a web page instead of an iCalendar feed.")
            }
            val fallbackColor = DEFAULT_COLORS[abs(account.id.hashCode()) % DEFAULT_COLORS.size]
            val automaticColor = existing?.automaticColor
                ?: existing?.color?.takeIf { existing.customColor == null }
                ?: fallbackColor
            val color = existing?.customColor ?: automaticColor
            val parsed = icalCodec.parseAll(
                rawIcs = raw,
                collectionHref = collectionHref,
                resourceHrefPrefix = collectionHref.trimEnd('/'),
                collectionColor = color,
            )
            val collection = CollectionEntity(
                href = collectionHref,
                accountId = account.id,
                displayName = existing?.displayName ?: account.displayName ?: "Read-only calendar",
                color = color,
                supportsEvents = parsed.any { it.event != null },
                supportsTasks = parsed.any { it.task != null },
                syncToken = null,
                ctag = null,
                isEnabled = existing?.isEnabled ?: true,
                sortOrder = existing?.sortOrder ?: 0,
                readOnly = true,
                automaticColor = automaticColor,
                sourceColor = existing?.sourceColor,
                customColor = existing?.customColor,
                sourceType = SourceType.ReadOnlyUrl,
                externalId = account.serverUrl,
            )
            database.collectionDao().upsertAll(listOf(collection))
            val refreshedResourceHrefs = mutableSetOf<String>()
            parsed.forEach { component ->
                val rawComponent = when {
                    component.event != null -> icalCodec.serializeEvent(component.event)
                    component.task != null -> icalCodec.serializeTask(component.task)
                    else -> raw
                }
                val resourceHref = component.event?.resourceHref ?: component.task?.resourceHref ?: return@forEach
                refreshedResourceHrefs += resourceHref
                upsertLocalResource(collection.href, resourceHref, null, component.componentType, component.uid, rawComponent)
                component.event?.let {
                    val current = database.eventDao().byResource(resourceHref)
                    database.eventDao().upsert(it.copy(manualColor = current?.manualColor))
                }
                component.task?.let {
                    val current = database.taskDao().byResource(resourceHref)
                    database.taskDao().upsert(it.copy(manualColor = current?.manualColor))
                }
            }
            database.resourceDao().forCollection(collection.href)
                .filterNot { it.href in refreshedResourceHrefs }
                .forEach { stale ->
                    database.eventDao().deleteByResource(stale.href)
                    database.taskDao().deleteByResource(stale.href)
                    database.resourceDao().delete(stale.href)
                }
            if (existing?.color != collection.color) {
                database.eventDao().updateColorForCollection(collection.href, collection.color)
                database.taskDao().updateColorForCollection(collection.href, collection.color)
            }
            database.accountDao().updateSyncState("idle", null, System.currentTimeMillis(), account.id)
        } catch (error: Throwable) {
            if (existing != null && error.isTransientReadOnlySyncFailure()) {
                database.accountDao().updateSyncState("idle", null, account.lastSyncAtMillis, account.id)
                return
            }
            val syncError = account.describeSyncError(error)
            database.accountDao().updateSyncState("error", syncError, account.lastSyncAtMillis, account.id)
            throw IllegalStateException(syncError, error)
        }
    }

    private suspend fun syncAndroidProvider(
        account: AccountEntity,
        removeStale: Boolean,
        includeDisabledProviderCalendars: Boolean,
    ) = androidProviderSyncMutex.withLock {
        database.accountDao().updateSyncState("syncing", null, account.lastSyncAtMillis, account.id)
        try {
            if (!androidCalendarProviderClient.hasCalendarPermissions()) {
                error("Android calendar permission is required.")
            }
            val calendars = androidCalendarProviderClient.listCalendars(includeDisabled = includeDisabledProviderCalendars)
            val previousCollectionsByHref = database.collectionDao().forAccount(account.id).associateBy { it.href }
            val collectionEntities = calendars.mapIndexed { index, calendar ->
                val href = androidCalendarProviderClient.calendarHref(calendar.id)
                val existing = previousCollectionsByHref[href]
                val automaticColor = calendar.color
                    .takeIf { it != 0 }
                    ?: existing?.automaticColor
                    ?: existing?.color?.takeIf { existing.customColor == null }
                    ?: DEFAULT_COLORS[index % DEFAULT_COLORS.size]
                CollectionEntity(
                    href = href,
                    accountId = account.id,
                    displayName = existing?.customDisplayName ?: calendar.displayName,
                    color = existing?.customColor ?: automaticColor,
                    supportsEvents = true,
                    supportsTasks = false,
                    syncToken = null,
                    ctag = null,
                    isEnabled = existing?.isEnabled ?: calendar.syncEvents,
                    sortOrder = existing?.sortOrder ?: index,
                    readOnly = !calendar.writable || !calendar.syncEvents,
                    remoteDisplayName = calendar.displayName,
                    customDisplayName = existing?.customDisplayName,
                    automaticColor = automaticColor,
                    sourceColor = calendar.color,
                    customColor = existing?.customColor,
                    sourceType = SourceType.AndroidProvider,
                    externalId = calendar.id.toString(),
                    capabilitiesJson = calendar.capabilitiesJson(),
                )
            }
            database.collectionDao().upsertAll(collectionEntities)
            collectionEntities.forEach { collection ->
                val previous = previousCollectionsByHref[collection.href]
                if (previous?.color != collection.color) {
                    database.eventDao().updateColorForCollection(collection.href, collection.color)
                    database.taskDao().updateColorForCollection(collection.href, collection.color)
                }
            }
            if (removeStale) {
                removeStaleRemoteCollections(account.id, collectionEntities.map { it.href }.toSet())
            }

            val providerSyncedCalendarIds = calendars.filter { it.syncEvents }.map { it.id }.toSet()
            val collectionByCalendarId = collectionEntities
                .filter { it.isEnabled }
                .mapNotNull { collection ->
                    val id = collection.externalId?.toLongOrNull() ?: androidCalendarProviderClient.calendarIdFromHref(collection.href)
                    id?.takeIf { it in providerSyncedCalendarIds }?.let { it to collection }
                }.toMap()
            val now = System.currentTimeMillis()
            val events = androidCalendarProviderClient.listEvents(
                calendarIds = collectionByCalendarId.keys,
                syncStartMillis = now - ANDROID_SYNC_LOOKBACK_MILLIS,
                syncEndMillis = now + ANDROID_SYNC_LOOKAHEAD_MILLIS,
            )
            val refreshedResourceHrefsByCollection = mutableMapOf<String, MutableSet<String>>()
            events.forEach { androidEvent ->
                val collection = collectionByCalendarId[androidEvent.calendarId] ?: return@forEach
                val resourceHref = androidCalendarProviderClient.eventHref(androidEvent.id)
                if (shouldIgnoreRecentAndroidLocalDelete(resourceHref)) return@forEach
                val existing = database.eventDao().byResource(resourceHref)
                val event = androidCalendarProviderClient.toEntity(
                    event = androidEvent,
                    collectionHref = collection.href,
                    color = collection.color,
                    manualColor = existing?.manualColor,
                )
                refreshedResourceHrefsByCollection.getOrPut(collection.href) { mutableSetOf() } += resourceHref
                if (shouldKeepRecentAndroidLocalWrite(resourceHref, existing, event)) return@forEach
                upsertLocalResource(
                    collectionHref = collection.href,
                    resourceHref = event.resourceHref,
                    etag = null,
                    componentType = ComponentType.Event,
                    uid = event.uid,
                    rawIcs = "android-provider:${androidEvent.id}",
                )
                database.eventDao().upsert(event)
                database.taskDao().deleteByResource(resourceHref)
            }
            if (removeStale) {
                collectionByCalendarId.values.forEach { collection ->
                    val refreshed = refreshedResourceHrefsByCollection[collection.href].orEmpty()
                    database.resourceDao().forCollection(collection.href)
                        .filterNot { it.href in refreshed }
                        .forEach { stale ->
                            database.eventDao().deleteByResource(stale.href)
                            database.taskDao().deleteByResource(stale.href)
                            database.resourceDao().delete(stale.href)
                        }
                }
            }
            database.accountDao().updateSyncState("idle", null, System.currentTimeMillis(), account.id)
        } catch (error: Throwable) {
            val syncError = account.describeSyncError(error)
            database.accountDao().updateSyncState("error", syncError, account.lastSyncAtMillis, account.id)
            throw IllegalStateException(syncError, error)
        }
    }

    private suspend fun removeStaleRemoteCollections(accountId: String, remoteHrefs: Set<String>) {
        database.collectionDao().forAccount(accountId)
            .filterNot { it.href in remoteHrefs }
            .filterNot { it.href.isLocalCollectionHref() || it.href.startsWith(READ_ONLY_PREFIX) }
            .forEach { stale ->
                database.pendingMutationDao().deleteForCollection(stale.href)
                database.collectionDao().delete(stale.href)
            }
    }

    private suspend fun syncCollection(
        credentials: StoredCredentials,
        collection: CollectionEntity,
        discovered: RemoteCollection?,
        forceFullRefresh: Boolean = false,
    ) {
        val localResources = database.resourceDao().forCollection(collection.href)
        val localByKey = localResources.associateBy { it.href.davHrefKey() }
        val pendingDeletes = database.pendingMutationDao().allForAccount(collection.accountId)
            .asSequence()
            .filter { it.collectionHref == collection.href && it.action == MutationAction.Delete }
            .map { it.resourceHref.davHrefKey() }
            .toSet()

        val incremental = if (
            !forceFullRefresh &&
            collection.syncToken != null &&
            discovered?.capabilities?.supportsSyncCollection == true
        ) {
            calDavClient.syncCollection(
                serverUrl = credentials.serverUrl,
                collectionHref = collection.href,
                previousSyncToken = collection.syncToken,
                username = credentials.username,
                appPassword = credentials.appPassword,
            )
        } else {
            null
        }

        if (
            !forceFullRefresh &&
            incremental == null &&
            collection.ctag != null &&
            discovered?.ctag != null &&
            collection.ctag == discovered.ctag &&
            localResources.none { it.syncError != null }
        ) {
            return
        }

        val queriedResources = if (
            incremental == null &&
            forceFullRefresh &&
            (collection.supportsTasks || collection.isCalDavScheduleInbox()) &&
            collection.sourceType == SourceType.CalDav
        ) {
            runCatching {
                calDavClient.queryResources(
                    serverUrl = credentials.serverUrl,
                    collectionHref = collection.href,
                    componentName = ComponentType.Task,
                    username = credentials.username,
                    appPassword = credentials.appPassword,
                )
            }.getOrElse { emptyList() }
        } else {
            emptyList()
        }
        val listedResources = incremental?.changedResources ?: calDavClient.listResources(
            collectionHref = collection.href,
            serverUrl = credentials.serverUrl,
            username = credentials.username,
            appPassword = credentials.appPassword,
        )
        val remoteResources = (listedResources + queriedResources.map { RemoteResource(it.href, it.etag) })
            .distinctBy { it.href.davHrefKey() }
        val remoteByKey = remoteResources.associateBy { it.href.davHrefKey() }
        val changedResources = remoteResources.filter { remote ->
            val key = remote.href.davHrefKey()
            if (key in pendingDeletes) return@filter false
            val local = localByKey[key]
            incremental != null || local == null || local.etag != remote.etag || local.syncError != null
        }
        val queriedByHref = queriedResources.associateBy { it.href.davHrefKey() }
        val multigetByHref = changedResources
            .filterNot { it.href.davHrefKey() in queriedByHref }
            .chunked(RESOURCE_MULTIGET_BATCH_SIZE)
            .flatMap { batch ->
                runCatching {
                    calDavClient.multigetResources(
                        serverUrl = credentials.serverUrl,
                        collectionHref = collection.href,
                        hrefs = batch.map { it.href },
                        username = credentials.username,
                        appPassword = credentials.appPassword,
                    )
                }.getOrElse { emptyList() }
            }
            .associateBy { it.href.davHrefKey() }
        val fetchedByHref = multigetByHref + queriedByHref

        changedResources.forEach { remote ->
            val local = localByKey[remote.href.davHrefKey()]
            val localHref = local?.href ?: remote.href
            try {
                val fetched = fetchedByHref[remote.href.davHrefKey()]
                val raw = fetched?.calendarData
                    ?: calDavClient.getResource(credentials.serverUrl, remote.href, credentials.username, credentials.appPassword)
                if (shouldKeepRecentCalDavLocalWrite(localHref, local, raw)) return@forEach
                val effectiveEtag = fetched?.etag ?: remote.etag
                val parsed = icalCodec.parse(raw, collection.href, localHref, collection.color)
                if (parsed == null) {
                    upsertFailedResource(
                        collectionHref = collection.href,
                        resourceHref = localHref,
                        etag = effectiveEtag,
                        rawIcs = raw,
                        existing = local,
                        error = "Import failed: no supported VEVENT or VTODO component was found.",
                    )
                    return@forEach
                }
                upsertLocalResource(collection.href, localHref, effectiveEtag, parsed.componentType, parsed.uid, raw)
                parsed.event?.let {
                    val existingEvent = database.eventDao().byResource(localHref)
                    database.eventDao().upsert(it.copy(manualColor = it.manualColor ?: existingEvent?.manualColor))
                    database.taskDao().deleteByResource(localHref)
                }
                parsed.task?.let {
                    val mergedTask = it.preserveLocalTimedFields(local?.rawIcs, database.taskDao().byResource(localHref))
                        .withValidIcalSchedule()
                    if (mergedTask != it) {
                        upsertLocalResource(collection.href, localHref, effectiveEtag, parsed.componentType, parsed.uid, icalCodec.serializeTask(mergedTask))
                    }
                    database.taskDao().upsert(mergedTask)
                    database.eventDao().deleteByResource(localHref)
                }
            } catch (error: Throwable) {
                upsertFailedResource(
                    collectionHref = collection.href,
                    resourceHref = localHref,
                    etag = remote.etag ?: local?.etag,
                    rawIcs = local?.rawIcs.orEmpty(),
                    existing = local,
                    error = "Import failed: ${error.message ?: error::class.java.simpleName}",
                )
            }
        }

        val deletedKeys = incremental?.deletedHrefs
            ?.map { it.davHrefKey() }
            ?.toSet()
            ?: localByKey.keys.minus(remoteByKey.keys)
        deletedKeys.mapNotNull(localByKey::get).forEach { deletedResource ->
            val deletedHref = deletedResource.href
            if (shouldKeepRecentCalDavMissingResource(deletedHref)) return@forEach
            database.eventDao().deleteByResource(deletedHref)
            database.taskDao().deleteByResource(deletedHref)
            database.resourceDao().delete(deletedHref)
        }
        database.collectionDao().updateSyncMarkers(
            href = collection.href,
            syncToken = incremental?.syncToken ?: discovered?.syncToken,
            ctag = discovered?.ctag,
        )
    }

    private suspend fun pushPending(credentials: StoredCredentials, accountId: String) {
        val activeCollectionHrefs = database.collectionDao().forAccount(accountId)
            .filter { it.isEnabled }
            .map { it.href }
            .toSet()
        pushPendingMutations(
            credentials,
            database.pendingMutationDao().allForAccount(accountId)
                .filter { it.collectionHref in activeCollectionHrefs },
        )
    }

    private suspend fun repairMissingCalDavEtags(credentials: StoredCredentials, accountId: String) {
        database.collectionDao().forAccount(accountId)
            .filter { it.sourceType == SourceType.CalDav && it.isEnabled }
            .forEach { collection ->
                database.resourceDao().missingEtagForCollection(collection.href)
                    .forEach { resource ->
                        val etag = runCatching {
                            calDavClient.getResourceEtag(
                                serverUrl = credentials.serverUrl,
                                href = resource.href,
                                username = credentials.username,
                                appPassword = credentials.appPassword,
                            )
                        }.getOrNull()
                        if (etag != null) {
                            database.resourceDao().markSynced(resource.href, etag)
                        }
                    }
            }
    }

    private suspend fun pushPendingMutations(mutations: List<PendingMutationEntity>) {
        mutations
            .groupBy { it.accountId }
            .forEach { (accountId, accountMutations) ->
                val credentials = credentialsStore.get(accountId) ?: return@forEach
                pushPendingMutations(credentials, accountMutations)
            }
    }

    private suspend fun pushPendingMutations(credentials: StoredCredentials, mutations: List<PendingMutationEntity>) {
        mutations.forEach { mutation ->
            try {
                when (mutation.action) {
                    MutationAction.Put -> {
                        val raw = if (mutation.componentType == ComponentType.Task) {
                            normalizedPendingTaskPayload(mutation)
                        } else {
                            mutation.payloadIcs ?: error("Missing payload")
                        }
                        val effectiveBaseEtag = mutation.baseEtag
                            ?: database.resourceDao().get(mutation.resourceHref)?.etag
                        val result = calDavClient.putResource(
                            serverUrl = credentials.serverUrl,
                            href = mutation.resourceHref,
                            username = credentials.username,
                            appPassword = credentials.appPassword,
                            rawIcs = raw,
                            baseEtag = effectiveBaseEtag,
                        )
                        val uploadedEtag = result.etag
                            ?: runCatching {
                                calDavClient.getResourceEtag(
                                    serverUrl = credentials.serverUrl,
                                    href = result.href,
                                    username = credentials.username,
                                    appPassword = credentials.appPassword,
                                )
                            }.getOrNull()
                            ?: effectiveBaseEtag
                        database.resourceDao().markSynced(result.href, uploadedEtag)
                        markRecentCalDavLocalWrite(result.href)
                    }
                    MutationAction.Delete -> {
                        calDavClient.deleteResource(
                            serverUrl = credentials.serverUrl,
                            href = mutation.resourceHref,
                            username = credentials.username,
                            appPassword = credentials.appPassword,
                            baseEtag = mutation.baseEtag,
                        )
                        when (mutation.componentType) {
                            ComponentType.Event -> database.eventDao().deleteByResource(mutation.resourceHref)
                            ComponentType.Task -> database.taskDao().deleteByResource(mutation.resourceHref)
                        }
                        database.resourceDao().delete(mutation.resourceHref)
                    }
                }
                database.pendingMutationDao().delete(mutation)
            } catch (error: Throwable) {
                database.resourceDao().setSyncError(mutation.resourceHref, error.message ?: "Upload failed")
            }
        }
    }

    private suspend fun upsertLocalResource(
        collectionHref: String,
        resourceHref: String,
        etag: String?,
        componentType: String,
        uid: String,
        rawIcs: String,
    ) {
        database.resourceDao().upsert(
            CalendarResourceEntity(
                href = resourceHref,
                collectionHref = collectionHref,
                etag = etag,
                componentType = componentType,
                uid = uid,
                rawIcs = rawIcs,
            ),
        )
    }

    private suspend fun upsertFailedResource(
        collectionHref: String,
        resourceHref: String,
        etag: String?,
        rawIcs: String,
        existing: CalendarResourceEntity?,
        error: String,
    ) {
        val retainedRaw = rawIcs.ifBlank { existing?.rawIcs.orEmpty() }
        database.resourceDao().upsert(
            CalendarResourceEntity(
                href = resourceHref,
                collectionHref = collectionHref,
                etag = etag ?: existing?.etag,
                componentType = retainedRaw.inferComponentType() ?: existing?.componentType ?: UNKNOWN_COMPONENT_TYPE,
                uid = retainedRaw.inferUid() ?: existing?.uid ?: resourceHref.substringAfterLast('/').ifBlank { resourceHref },
                rawIcs = retainedRaw,
                syncError = error.take(MAX_SYNC_ERROR_LENGTH),
            ),
        )
    }

    private suspend fun enqueuePut(
        collectionHref: String,
        resourceHref: String,
        componentType: String,
        rawIcs: String,
        baseEtag: String?,
    ) {
        if (isReadOnlyCollectionHref(collectionHref) || collectionHref.isLocalCollectionHref() || isAndroidProviderCollectionHref(collectionHref)) return
        database.pendingMutationDao().deleteForResourceAndAction(resourceHref, MutationAction.Put)
        database.pendingMutationDao().insert(
            PendingMutationEntity(
                accountId = database.collectionDao().get(collectionHref)?.accountId ?: AccountEntity.PRIMARY_ID,
                collectionHref = collectionHref,
                resourceHref = resourceHref,
                componentType = componentType,
                action = MutationAction.Put,
                payloadIcs = rawIcs,
                baseEtag = baseEtag,
                createdAtMillis = System.currentTimeMillis(),
            ),
        )
    }

    private suspend fun enqueueDelete(
        collectionHref: String,
        resourceHref: String,
        componentType: String,
        baseEtag: String?,
    ) {
        if (isReadOnlyCollectionHref(collectionHref) || collectionHref.isLocalCollectionHref() || isAndroidProviderCollectionHref(collectionHref)) return
        database.pendingMutationDao().deleteForResourceAndAction(resourceHref, MutationAction.Put)
        database.pendingMutationDao().deleteForResourceAndAction(resourceHref, MutationAction.Delete)
        database.pendingMutationDao().insert(
            PendingMutationEntity(
                accountId = database.collectionDao().get(collectionHref)?.accountId ?: AccountEntity.PRIMARY_ID,
                collectionHref = collectionHref,
                resourceHref = resourceHref,
                componentType = componentType,
                action = MutationAction.Delete,
                payloadIcs = null,
                baseEtag = baseEtag,
                createdAtMillis = System.currentTimeMillis(),
            ),
        )
    }

    private fun normalizeServer(serverUrl: String): String {
        val trimmed = serverUrl.trim().trimEnd('/')
        return when {
            trimmed.startsWith("http://", ignoreCase = true) -> trimmed
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            else -> "https://$trimmed"
        }
    }

    private fun accountId(serverUrl: String, username: String): String =
        "account-" + UUID.nameUUIDFromBytes("$serverUrl\n$username".toByteArray(StandardCharsets.UTF_8)).toString()

    private fun readOnlyAccountId(url: String): String =
        READ_ONLY_PREFIX + UUID.nameUUIDFromBytes(url.toByteArray(StandardCharsets.UTF_8)).toString()

    private fun CollectionEntity.isReadOnlyCollection(): Boolean =
        readOnly || href.startsWith(READ_ONLY_PREFIX)

    private fun CollectionEntity.capability(name: String, defaultValue: Boolean): Boolean =
        runCatching {
            val json = capabilitiesJson?.let(::JSONObject) ?: return@runCatching defaultValue
            if (json.has(name) && !json.isNull(name)) json.optBoolean(name, defaultValue) else defaultValue
        }.getOrDefault(defaultValue)

    private fun CollectionEntity.canCreateResources(): Boolean =
        sourceType != SourceType.CalDav || capability("canCreateResources", !readOnly)

    private fun CollectionEntity.canDeleteResources(): Boolean =
        sourceType != SourceType.CalDav || capability("canDeleteResources", !readOnly)

    private fun CollectionEntity.canWriteProperties(): Boolean =
        sourceType == SourceType.CalDav && capability("canWriteProperties", !readOnly)

    private fun CollectionEntity.isCalDavScheduleInbox(): Boolean =
        sourceType == SourceType.CalDav &&
            capabilitiesJson
                ?.let { runCatching { JSONObject(it).optBoolean("isScheduleInbox", false) }.getOrDefault(false) } == true

    private suspend fun isReadOnlyCollectionHref(href: String): Boolean =
        href.startsWith(READ_ONLY_PREFIX) || database.collectionDao().get(href)?.readOnly == true

    private fun CollectionEntity.isAndroidProviderCollection(): Boolean =
        sourceType == SourceType.AndroidProvider || href.startsWith(AndroidCalendarProviderClient.ANDROID_CALENDAR_PREFIX)

    private suspend fun isAndroidProviderCollectionHref(href: String): Boolean =
        href.startsWith(AndroidCalendarProviderClient.ANDROID_CALENDAR_PREFIX) ||
            database.collectionDao().get(href)?.isAndroidProviderCollection() == true

    private fun CollectionEntity.androidCalendarId(): Long =
        externalId?.toLongOrNull()
            ?: androidCalendarProviderClient.calendarIdFromHref(href)
            ?: error("Android calendar id is missing.")

    private fun String.isLocalCollectionHref(): Boolean =
        startsWith(LOCAL_COLLECTION_PREFIX)

    private fun EventEntity.sanitizedFor(collection: CollectionEntity): EventEntity =
        if (!collection.isAndroidProviderCollection()) {
            this
        } else {
            copy(
                categories = null,
                organizerJson = null,
                attendeesJson = null,
            )
        }

    private suspend fun persistAndroidEventUpdate(event: EventEntity) {
        val collection = database.collectionDao().get(event.collectionHref)
            ?: error("Android calendar not found.")
        val eventId = androidCalendarProviderClient.eventIdFromHref(event.resourceHref)
            ?: error("Android event id is missing.")
        val sanitized = event.sanitizedFor(collection)
        androidCalendarProviderClient.updateEvent(eventId, collection.androidCalendarId(), sanitized)
        upsertLocalResource(sanitized.collectionHref, sanitized.resourceHref, null, ComponentType.Event, sanitized.uid, "android-provider:$eventId")
        database.eventDao().upsert(sanitized)
        markRecentAndroidLocalWrite(sanitized.resourceHref)
    }

    private suspend fun cancelAndroidEventOccurrence(eventWithExDate: EventEntity, occurrenceStartMillis: Long) {
        val collection = database.collectionDao().get(eventWithExDate.collectionHref)
            ?: error("Android calendar not found.")
        val eventId = androidCalendarProviderClient.eventIdFromHref(eventWithExDate.resourceHref)
            ?: error("Android event id is missing.")
        val durationMillis = (eventWithExDate.endsAtMillis - eventWithExDate.startsAtMillis)
            .coerceAtLeast(60L * 60L * 1000L)
        androidCalendarProviderClient.cancelRecurringInstance(
            eventId = eventId,
            calendarId = collection.androidCalendarId(),
            occurrenceStartMillis = occurrenceStartMillis,
            occurrenceEndMillis = occurrenceStartMillis + durationMillis,
            allDay = eventWithExDate.allDay,
        )
        val sanitized = eventWithExDate.sanitizedFor(collection)
        upsertLocalResource(sanitized.collectionHref, sanitized.resourceHref, null, ComponentType.Event, sanitized.uid, "android-provider:$eventId")
        database.eventDao().upsert(sanitized)
        markRecentAndroidLocalWrite(sanitized.resourceHref)
    }

    private fun markRecentCalDavLocalWrite(resourceHref: String) {
        recentCalDavLocalWrites[resourceHref] = System.currentTimeMillis()
    }

    private fun shouldKeepRecentCalDavLocalWrite(resourceHref: String, local: CalendarResourceEntity?, remoteRawIcs: String): Boolean {
        val writtenAt = recentCalDavLocalWrites[resourceHref] ?: return false
        if (System.currentTimeMillis() - writtenAt > RECENT_REMOTE_WRITE_SHIELD_MILLIS) {
            recentCalDavLocalWrites.remove(resourceHref)
            return false
        }
        if (local == null || local.rawIcs.normalizedIcsText() == remoteRawIcs.normalizedIcsText()) {
            recentCalDavLocalWrites.remove(resourceHref)
            return false
        }
        return true
    }

    private fun shouldKeepRecentCalDavMissingResource(resourceHref: String): Boolean {
        val writtenAt = recentCalDavLocalWrites[resourceHref] ?: return false
        if (System.currentTimeMillis() - writtenAt <= RECENT_REMOTE_WRITE_SHIELD_MILLIS) return true
        recentCalDavLocalWrites.remove(resourceHref)
        return false
    }

    private fun markRecentAndroidLocalWrite(resourceHref: String) {
        recentAndroidLocalWrites[resourceHref] = System.currentTimeMillis()
        recentAndroidLocalDeletes.remove(resourceHref)
    }

    private fun markRecentAndroidLocalDelete(resourceHref: String) {
        recentAndroidLocalDeletes[resourceHref] = System.currentTimeMillis()
        recentAndroidLocalWrites.remove(resourceHref)
    }

    private fun shouldKeepRecentAndroidLocalWrite(resourceHref: String, existing: EventEntity?, providerEvent: EventEntity): Boolean {
        val writtenAt = recentAndroidLocalWrites[resourceHref] ?: return false
        if (System.currentTimeMillis() - writtenAt > RECENT_ANDROID_WRITE_SHIELD_MILLIS) {
            recentAndroidLocalWrites.remove(resourceHref)
            return false
        }
        if (existing == null || existing.hasSameAndroidProviderFields(providerEvent)) {
            recentAndroidLocalWrites.remove(resourceHref)
            return false
        }
        return true
    }

    private fun shouldIgnoreRecentAndroidLocalDelete(resourceHref: String): Boolean {
        val deletedAt = recentAndroidLocalDeletes[resourceHref] ?: return false
        if (System.currentTimeMillis() - deletedAt <= RECENT_ANDROID_WRITE_SHIELD_MILLIS) return true
        recentAndroidLocalDeletes.remove(resourceHref)
        return false
    }

    private fun EventEntity.hasSameAndroidProviderFields(other: EventEntity): Boolean =
        title == other.title &&
            description == other.description &&
            location == other.location &&
            startsAtMillis == other.startsAtMillis &&
            endsAtMillis == other.endsAtMillis &&
            allDay == other.allDay &&
            recurrenceRule == other.recurrenceRule &&
            exDatesCsv == other.exDatesCsv &&
            remindersCsv == other.remindersCsv &&
            collectionHref == other.collectionHref

    private fun TaskEntity.preserveLocalTimedFields(localRawIcs: String?, localTask: TaskEntity?): TaskEntity {
        if (localTask == null || localTask.uid != uid) return this
        val keepStart = localTask.startHasTime &&
            localRawIcs.hasTimedIcalProperty("DTSTART") &&
            (!startHasTime && localTask.startAtMillis.sameLocalDateOrMissing(startAtMillis))
        val keepDue = localTask.dueHasTime &&
            localRawIcs.hasTimedIcalProperty("DUE") &&
            (!dueHasTime && localTask.dueAtMillis.sameLocalDateOrMissing(dueAtMillis))
        if (!keepStart && !keepDue) return copy(manualColor = localTask.manualColor)
        return copy(
            startAtMillis = if (keepStart) localTask.startAtMillis else startAtMillis,
            startHasTime = if (keepStart) true else startHasTime,
            dueAtMillis = if (keepDue) localTask.dueAtMillis else dueAtMillis,
            dueHasTime = if (keepDue) true else dueHasTime,
            manualColor = localTask.manualColor,
        )
    }

    private fun String?.hasTimedIcalProperty(name: String): Boolean =
        this?.lineSequence()?.any { line ->
            (line.startsWith("$name:", ignoreCase = true) && line.substringAfter(':').contains('T')) ||
                (line.startsWith("$name;", ignoreCase = true) && line.substringAfter(':', "").contains('T'))
        } == true

    private fun Long?.sameLocalDateOrMissing(other: Long?): Boolean =
        other == null || (this != null && Instant.ofEpochMilli(this).atZone(zoneId).toLocalDate() == Instant.ofEpochMilli(other).atZone(zoneId).toLocalDate())

    private fun String?.toMinutesList(): List<Int> =
        this?.split(',')?.mapNotNull { it.trim().toIntOrNull() }.orEmpty()

    private fun String?.updateAttendeePartstat(attendeeEmails: List<String>, partstat: String): String? {
        val normalizedPartstat = partstat.trim().uppercase()
        if (normalizedPartstat !in setOf("ACCEPTED", "DECLINED", "TENTATIVE", "NEEDS-ACTION")) return null
        val matches = attendeeEmails
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .toSet()
        if (matches.isEmpty()) return null
        val attendees = runCatching { JSONArray(this.orEmpty()) }.getOrNull() ?: return null
        var changed = false
        repeat(attendees.length()) { index ->
            val obj = attendees.optJSONObject(index) ?: return@repeat
            val email = obj.optString("email").trim().lowercase()
            if (email in matches) {
                obj.put("partstat", normalizedPartstat)
                changed = true
            }
        }
        return attendees.takeIf { changed }?.toString()
    }

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

    fun expandTaskReminders(master: TaskEntity, fromMillis: Long, toMillis: Long): List<TaskEntity> =
        taskRecurrenceExpander.expand(master, fromMillis, toMillis)

    private fun newUid(): String = "${UUID.randomUUID()}@kgs-calendar"

    private fun CollectionEntity.resolvedAutomaticColor(): Int =
        automaticColor ?: sourceColor ?: color

    private fun Throwable.isTransientReadOnlySyncFailure(): Boolean {
        if (findCause<IOException>() != null) return true
        val statusCode = message
            ?.substringAfter("URL returned HTTP ", "")
            ?.takeWhile(Char::isDigit)
            ?.toIntOrNull()
            ?: return false
        return statusCode == 408 || statusCode == 425 || statusCode == 429 || statusCode >= 500
    }

    private fun String.looksLikeHtmlResponse(contentType: String?): Boolean {
        if (contentType?.contains("text/html", ignoreCase = true) == true) return true
        val prefix = trimStart().take(256)
        return prefix.startsWith("<!DOCTYPE html", ignoreCase = true) ||
            prefix.startsWith("<html", ignoreCase = true)
    }

    private fun AccountEntity.describeSyncError(error: Throwable): String {
        val source = displayName?.takeIf { it.isNotBlank() } ?: username
        error.message?.takeIf { it.startsWith("Source \"$source\":") }?.let { return it }
        val unknownHost = error.findCause<UnknownHostException>()
        if (unknownHost != null) {
            val host = runCatching { URI(serverUrl).host }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: unknownHost.message
                ?: serverUrl
            return "Source \"$source\": DNS lookup for \"$host\" failed. Check the internet connection, Private DNS/VPN, and server address."
        }
        return "Source \"$source\": ${error.message ?: "Sync failed."}"
    }

    private inline fun <reified T : Throwable> Throwable.findCause(): T? {
        var current: Throwable? = this
        while (current != null) {
            if (current is T) return current
            current = current.cause
        }
        return null
    }

    private fun LocalDate?.toTaskMillis(time: LocalTime?, hasTime: Boolean, defaultTime: LocalTime): Long? {
        val date = this ?: return null
        val localTime = if (hasTime) time ?: defaultTime else LocalTime.MIDNIGHT
        return date.atTime(localTime).atZone(zoneId).toInstant().toEpochMilli()
    }

    companion object {
        private const val READ_ONLY_USERNAME = "Read-only URL"
        private const val READ_ONLY_PREFIX = "readonly-"
        private const val READ_ONLY_CALENDAR_ACCEPT = "text/calendar, application/calendar+ics, text/plain, */*"
        private const val LOCAL_ACCOUNT_ID = "local"
        private const val LOCAL_USERNAME = "Local device"
        private const val LOCAL_SERVER_URL = "local://device"
        private const val LOCAL_COLLECTION_PREFIX = "local://"
        private const val LOCAL_COLLECTION_HREF = "${LOCAL_COLLECTION_PREFIX}kgs-calendar/default"
        private const val RESOURCE_MULTIGET_BATCH_SIZE = 50
        private const val ANDROID_SYNC_LOOKBACK_MILLIS = 366L * 24L * 60L * 60L * 1000L
        private const val ANDROID_SYNC_LOOKAHEAD_MILLIS = 730L * 24L * 60L * 60L * 1000L
        private const val TARGETED_SYNC_CLOCK_SKEW_MILLIS = 1000L
        private const val RECENT_ANDROID_WRITE_SHIELD_MILLIS = 90L * 1000L
        private const val RECENT_REMOTE_WRITE_SHIELD_MILLIS = 90L * 1000L
        private const val UNKNOWN_COMPONENT_TYPE = "UNKNOWN"
        private const val MAX_SYNC_ERROR_LENGTH = 500
        private const val HOUR_MILLIS = 60L * 60L * 1000L
        private val DEFAULT_COLORS = listOf(
            Color.rgb(23, 107, 93),
            Color.rgb(26, 115, 232),
            Color.rgb(185, 81, 64),
            Color.rgb(120, 85, 190),
            Color.rgb(238, 147, 45),
        )
    }
}

private fun String.davHrefKey(): String =
    runCatching {
        val uri = URI(this)
        uri.path ?: uri.rawPath ?: this
    }.getOrDefault(this).trimEnd('/')

private fun String.inferComponentType(): String? =
    when {
        contains("BEGIN:VEVENT", ignoreCase = true) -> ComponentType.Event
        contains("BEGIN:VTODO", ignoreCase = true) -> ComponentType.Task
        else -> null
    }

private fun String.inferUid(): String? {
    val normalized = replace("\r\n", "\n").replace('\r', '\n')
    val lines = normalized.lines()
    val unfolded = buildList {
        val current = StringBuilder()
        for (line in lines) {
            if (line.startsWith(" ") || line.startsWith("\t")) {
                current.append(line.drop(1))
            } else {
                if (current.isNotEmpty()) add(current.toString())
                current.clear()
                current.append(line)
            }
        }
        if (current.isNotEmpty()) add(current.toString())
    }
    return unfolded.firstOrNull { it.startsWith("UID", ignoreCase = true) }
        ?.substringAfter(':', "")
        ?.trim()
        ?.takeIf { it.isNotBlank() }
}

private fun Long.toDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

private fun EventEntity.endDateInclusive(): LocalDate =
    Instant.ofEpochMilli((endsAtMillis - 1).coerceAtLeast(startsAtMillis)).atZone(ZoneId.systemDefault()).toLocalDate()

private fun CollectionEntity.newResourceHref(uid: String): String =
    href.trimEnd('/') + "/" + uid.calendarObjectPathSegment() + ".ics"

private fun String.calendarObjectPathSegment(): String =
    URLEncoder.encode(trim(), StandardCharsets.UTF_8.name())
        .replace("+", "%20")
        .ifBlank { UUID.randomUUID().toString() }
