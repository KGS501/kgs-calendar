package com.kgs.calendar.data.account

import com.kgs.calendar.data.DEFAULT_COLORS
import com.kgs.calendar.data.LOCAL_ACCOUNT_ID
import com.kgs.calendar.data.LOCAL_COLLECTION_PREFIX
import com.kgs.calendar.data.LocalWriteSupport
import com.kgs.calendar.data.READ_ONLY_PREFIX
import com.kgs.calendar.data.READ_ONLY_USERNAME
import com.kgs.calendar.data.SourceType
import com.kgs.calendar.data.accountId
import com.kgs.calendar.data.canWriteProperties
import com.kgs.calendar.data.local.KgsDatabase
import com.kgs.calendar.data.local.entity.AccountEntity
import com.kgs.calendar.data.local.entity.CollectionEntity
import com.kgs.calendar.data.normalizeReadOnlyCalendarUrl
import com.kgs.calendar.data.normalizeServer
import com.kgs.calendar.data.provider.AndroidCalendarProviderClient
import com.kgs.calendar.data.remote.CalDavHttpClient
import com.kgs.calendar.data.remote.NextcloudLoginFlowClient
import com.kgs.calendar.data.resolvedAutomaticColor
import com.kgs.calendar.data.secure.CredentialsStore
import com.kgs.calendar.data.secure.StoredCredentials
import com.kgs.calendar.data.sync.AndroidProviderSyncEngine
import com.kgs.calendar.data.sync.ReadOnlyUrlSyncEngine
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Calendar sources and their collections: login flow, CalDAV/read-only/Android/local accounts,
 * collection visibility, order and appearance, and creating or deleting CalDAV calendars.
 */
class CalendarSourceManager internal constructor(
    private val database: KgsDatabase,
    private val credentialsStore: CredentialsStore,
    private val loginFlowClient: NextcloudLoginFlowClient,
    private val calDavClient: CalDavHttpClient,
    private val androidCalendarProviderClient: AndroidCalendarProviderClient,
    private val localWrites: LocalWriteSupport,
    private val readOnlyUrlSyncEngine: ReadOnlyUrlSyncEngine,
    private val androidProviderSyncEngine: AndroidProviderSyncEngine,
) {
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

    private companion object {
        const val LOCAL_USERNAME = "Local device"
        const val LOCAL_SERVER_URL = "local://device"
        const val LOCAL_COLLECTION_HREF = "${LOCAL_COLLECTION_PREFIX}kgs-calendar/default"
    }
}
