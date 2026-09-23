package com.kgs.calendar.data.sync

import com.kgs.calendar.data.DEFAULT_COLORS
import com.kgs.calendar.data.LocalWriteSupport
import com.kgs.calendar.data.SourceType
import com.kgs.calendar.data.describeSyncError
import com.kgs.calendar.data.local.KgsDatabase
import com.kgs.calendar.data.local.entity.AccountEntity
import com.kgs.calendar.data.local.entity.CollectionEntity
import com.kgs.calendar.data.provider.AndroidCalendarProviderClient
import com.kgs.calendar.data.provider.AndroidProviderWriteShield
import com.kgs.calendar.domain.model.ComponentType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Mirrors the Android calendar provider's calendars and events into the local database. */
class AndroidProviderSyncEngine internal constructor(
    private val database: KgsDatabase,
    private val localWrites: LocalWriteSupport,
    private val androidCalendarProviderClient: AndroidCalendarProviderClient,
    private val androidWriteShield: AndroidProviderWriteShield,
) : CalendarSourceSyncEngine {
    private val syncMutex = Mutex()

    override fun handles(account: AccountEntity): Boolean =
        account.sourceType == SourceType.AndroidProvider || account.id == AndroidCalendarProviderClient.ANDROID_ACCOUNT_ID

    override suspend fun sync(account: AccountEntity, options: SourceSyncOptions): Boolean {
        sync(
            account = account,
            removeStale = true,
            includeDisabledProviderCalendars = options.includeDisabledProviderCalendars,
        )
        return true
    }

    suspend fun sync(
        account: AccountEntity,
        removeStale: Boolean,
        includeDisabledProviderCalendars: Boolean,
    ) = syncMutex.withLock {
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
            // The provider reads above stay outside the transaction; only the local writes are atomic.
            localWrites.writeTransaction {
                database.collectionDao().upsertAll(collectionEntities)
                collectionEntities.forEach { collection ->
                    val previous = previousCollectionsByHref[collection.href]
                    if (previous?.color != collection.color) {
                        database.eventDao().updateColorForCollection(collection.href, collection.color)
                        database.taskDao().updateColorForCollection(collection.href, collection.color)
                    }
                }
                if (removeStale) {
                    localWrites.removeStaleRemoteCollections(account.id, collectionEntities.map { it.href }.toSet())
                }
                val refreshedResourceHrefsByCollection = mutableMapOf<String, MutableSet<String>>()
                events.forEach { androidEvent ->
                    val collection = collectionByCalendarId[androidEvent.calendarId] ?: return@forEach
                    val resourceHref = androidCalendarProviderClient.eventHref(androidEvent.id)
                    if (androidWriteShield.shouldIgnoreLocalDelete(resourceHref)) return@forEach
                    val existing = database.eventDao().byResource(resourceHref)
                    val event = androidCalendarProviderClient.toEntity(
                        event = androidEvent,
                        collectionHref = collection.href,
                        color = collection.color,
                        manualColor = existing?.manualColor,
                    )
                    refreshedResourceHrefsByCollection.getOrPut(collection.href) { mutableSetOf() } += resourceHref
                    if (androidWriteShield.shouldKeepLocalWrite(resourceHref, existing, event)) return@forEach
                    localWrites.upsertLocalResource(
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
            }
        } catch (error: Throwable) {
            val syncError = account.describeSyncError(error)
            database.accountDao().updateSyncState("error", syncError, account.lastSyncAtMillis, account.id)
            throw IllegalStateException(syncError, error)
        }
    }

    private companion object {
        const val ANDROID_SYNC_LOOKBACK_MILLIS = 366L * 24L * 60L * 60L * 1000L
        const val ANDROID_SYNC_LOOKAHEAD_MILLIS = 730L * 24L * 60L * 60L * 1000L
    }
}
