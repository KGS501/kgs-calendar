package com.kgs.calendar.data.trash

import com.kgs.calendar.data.DEFAULT_COLORS
import com.kgs.calendar.data.LocalWriteSupport
import com.kgs.calendar.data.ical.IcalCodec
import com.kgs.calendar.data.local.KgsDatabase
import com.kgs.calendar.data.local.entity.AccountEntity
import com.kgs.calendar.data.local.entity.CollectionEntity
import com.kgs.calendar.data.local.entity.TrashedItemEntity
import com.kgs.calendar.data.remote.CalDavHttpClient
import com.kgs.calendar.data.remote.CalDavTrashBinSupport
import com.kgs.calendar.data.remote.HttpStatusException
import com.kgs.calendar.data.remote.RemoteTrashedObject
import com.kgs.calendar.data.secure.CredentialsStore
import com.kgs.calendar.data.secure.StoredCredentials
import com.kgs.calendar.data.sync.CalDavSyncEngine
import com.kgs.calendar.data.sync.RemoteSyncLock
import com.kgs.calendar.domain.model.ComponentType
import com.kgs.calendar.domain.model.MutationAction
import com.kgs.calendar.domain.model.SourceType
import com.kgs.calendar.domain.trash.TrashOrigin
import com.kgs.calendar.domain.trash.TrashRetention
import java.net.URI
import kotlin.coroutines.cancellation.CancellationException

/**
 * The Nextcloud side of "Recently deleted": each CalDAV account whose server has a CalDAV trash bin
 * (Nextcloud 22+, `{calendar home}/trashbin/`) gets its deleted objects cached as
 * [TrashOrigin.ServerTrashBin] items, including the ones deleted on other devices or on the web.
 *
 * Deduplication: once the app's queued DELETE of a resource has been uploaded, the server keeps the
 * object in its trash bin. A local snapshot of the same account is dropped when the listing holds an
 * object with the same UID, component type and calendar and the snapshot's DELETE is no longer
 * queued (also when the object was trashed elsewhere first and our DELETE found it gone); the server
 * item takes over its original href and its colour. Snapshots whose DELETE is still queued, and
 * snapshots of accounts whose server keeps no trash (none, or a retention of 0), stay local.
 */
internal class ServerTrashBin(
    private val database: KgsDatabase,
    private val localWrites: LocalWriteSupport,
    private val credentialsStore: CredentialsStore,
    private val calDavClient: CalDavHttpClient,
    private val icalCodec: IcalCodec,
    private val calDavSyncEngine: CalDavSyncEngine,
    private val syncLock: RemoteSyncLock,
) {
    /**
     * Checks the account's trash bin when that is due and replaces its cached items with the
     * server's listing. The caller holds [syncLock]. Returns false when the server couldn't be
     * asked; the cached items then stay as they were.
     */
    suspend fun refreshAccount(accountId: String): Boolean {
        val account = database.accountDao().get(accountId)
        if (account == null || account.sourceType != SourceType.CalDav) {
            database.trashDao().deleteForAccount(accountId, TrashOrigin.ServerTrashBin)
            return true
        }
        val credentials = credentialsStore.get(accountId) ?: return false
        return try {
            val support = trashBinSupport(account, credentials) ?: return false
            val trashBin = support.trashBin
            if (trashBin == null) {
                database.trashDao().deleteForAccount(accountId, TrashOrigin.ServerTrashBin)
                return true
            }
            val objects = calDavClient.listTrashBinObjects(trashBin, credentials.username, credentials.appPassword)
            val withData = objects.map { it.withCalendarData(credentials) }
            storeListing(account, withData, trashBin.retentionSeconds)
            true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            false
        }
    }

    /** Restores [item] on the server (MOVE onto `trashbin/restore/`), then pulls its calendar. */
    suspend fun restore(item: TrashedItemEntity): TrashRestoreResult {
        val serverHref = item.serverHref ?: return TrashRestoreResult.NotFound
        val credentials = credentialsStore.get(item.accountId)
        if (credentials == null || database.accountDao().get(item.accountId) == null) {
            database.trashDao().delete(item.id)
            return TrashRestoreResult.NotFound
        }
        try {
            calDavClient.restoreTrashBinObject(credentials.serverUrl, serverHref, credentials.username, credentials.appPassword)
        } catch (error: HttpStatusException) {
            val result = when (error.statusCode) {
                404, 410 -> {
                    database.trashDao().delete(item.id)
                    TrashRestoreResult.GoneFromServer
                }
                in 400..499 -> if (error.statusCode == 401) throw error else TrashRestoreResult.ServerRefused(error.statusCode)
                else -> throw error
            }
            // Whatever made the server refuse may have changed more of its trash bin.
            syncLock.withLock { refreshAccount(item.accountId) }
            return result
        }
        database.trashDao().delete(item.id)
        // The object is back under its original name; pull the calendar so it shows up right away.
        // When that fails, the next sync brings it.
        try {
            syncLock.withLock {
                if (calDavSyncEngine.syncStoredCollection(item.collectionHref)) restoreManualColor(item)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
        }
        val collection = database.collectionDao().get(item.collectionHref)
        return TrashRestoreResult.Restored(
            collectionHref = item.collectionHref,
            collectionName = collection?.displayName ?: item.collectionName,
            inOriginalCalendar = true,
        )
    }

    /** Deletes [item] from the server trash bin for good; one that is gone already counts as deleted. */
    suspend fun deletePermanently(item: TrashedItemEntity) {
        val serverHref = item.serverHref
        val credentials = credentialsStore.get(item.accountId)
        if (serverHref != null && credentials != null) {
            calDavClient.deleteTrashBinObject(credentials.serverUrl, serverHref, credentials.username, credentials.appPassword)
        }
        database.trashDao().delete(item.id)
    }

    /**
     * The account's trash bin check, probing the server when the cached one is missing or due.
     * Null when the server couldn't be asked and nothing usable is cached.
     */
    private suspend fun trashBinSupport(account: AccountEntity, credentials: StoredCredentials): CalDavTrashBinSupport? {
        val home = account.calendarHomeUrl ?: return null
        val now = System.currentTimeMillis()
        val cached = CalDavTrashBinSupport.readFrom(account.capabilitiesJson)?.takeIf { it.calendarHomeUrl == home }
        if (cached != null && !cached.supported && cached.isCurrentFor(home, now)) return cached
        val found = try {
            calDavClient.findTrashBin(home, credentials.username, credentials.appPassword)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            return cached
        }
        // With a retention of 0 Nextcloud deletes right away and keeps nothing to restore.
        val support = CalDavTrashBinSupport(home, found?.takeIf { it.retentionSeconds != 0L }, now)
        val latestJson = database.accountDao().get(account.id)?.capabilitiesJson ?: account.capabilitiesJson
        database.accountDao().updateCapabilitiesJson(account.id, support.writeInto(latestJson))
        return support
    }

    private suspend fun RemoteTrashedObject.withCalendarData(credentials: StoredCredentials): RemoteTrashedObject {
        if (calendarData != null) return this
        val fetched = try {
            calDavClient.getResource(credentials.serverUrl, href, credentials.username, credentials.appPassword)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            null
        }
        return copy(calendarData = fetched)
    }

    private suspend fun storeListing(account: AccountEntity, objects: List<RemoteTrashedObject>, retentionSeconds: Long?) {
        val now = System.currentTimeMillis()
        val collections = database.collectionDao().forAccount(account.id)
        localWrites.writeTransaction {
            val trashDao = database.trashDao()
            val cached = trashDao.forAccount(account.id, TrashOrigin.ServerTrashBin)
            val cachedByHref = cached.associateBy { it.serverHref.orEmpty().hrefKey() }
            val snapshots = trashDao.forAccount(account.id, TrashOrigin.LocalSnapshot)
                .sortedBy { it.deletedAtMillis }
                .toMutableList()
            val listed = objects
                .mapNotNull { remote ->
                    remote.toTrashedItem(account, collections, cachedByHref[remote.href.hrefKey()], retentionSeconds, now)
                }
                .filter { it.expiresAtMillis >= now }
                .sortedBy { it.deletedAtMillis }
                .map { item ->
                    val snapshot = snapshots.firstOrNull { it.isUploadedDeleteOf(item) } ?: return@map item
                    snapshots.remove(snapshot)
                    trashDao.delete(snapshot.id)
                    item.copy(resourceHref = snapshot.resourceHref, manualColor = item.manualColor ?: snapshot.manualColor)
                }
            val listedIds = listed.mapNotNull { it.id.takeIf { id -> id != 0L } }.toSet()
            trashDao.delete(cached.map { it.id }.filterNot { it in listedIds })
            listed.forEach { trashDao.upsert(it) }
        }
    }

    private suspend fun TrashedItemEntity.isUploadedDeleteOf(serverItem: TrashedItemEntity): Boolean =
        uid == serverItem.uid &&
            componentType == serverItem.componentType &&
            collectionHref.hrefKey() == serverItem.collectionHref.hrefKey() &&
            database.pendingMutationDao().forResource(resourceHref).none { it.action == MutationAction.Delete }

    private fun RemoteTrashedObject.toTrashedItem(
        account: AccountEntity,
        collections: List<CollectionEntity>,
        cached: TrashedItemEntity?,
        retentionSeconds: Long?,
        now: Long,
    ): TrashedItemEntity? {
        val raw = calendarData?.takeIf { it.contains("BEGIN:VCALENDAR", ignoreCase = true) } ?: return null
        val collection = calendarUri?.let { uri -> collections.firstOrNull { it.href.lastPathSegment() == uri } }
        val collectionHref = collection?.href
            ?: cached?.collectionHref
            ?: calendarUri?.let { account.calendarHomePath()?.plus("$it/") }
            ?: return null
        val color = collection?.color ?: cached?.collectionColor ?: DEFAULT_COLORS.first()
        val parsed = runCatching { icalCodec.parse(raw, collectionHref, href, color) }.getOrNull() ?: return null
        val deletedAt = deletedAtMillis ?: cached?.deletedAtMillis ?: now
        val base = TrashedItemEntity(
            id = cached?.id ?: 0,
            componentType = parsed.componentType,
            uid = parsed.uid,
            collectionHref = collectionHref,
            accountId = account.id,
            sourceType = SourceType.CalDav,
            resourceHref = cached?.resourceHref ?: href,
            rawIcs = raw,
            title = "",
            startMillis = null,
            hasTime = false,
            collectionName = collection?.displayName ?: cached?.collectionName ?: calendarUri.orEmpty(),
            collectionColor = color,
            manualColor = cached?.manualColor,
            deletedAtMillis = deletedAt,
            origin = TrashOrigin.ServerTrashBin,
            serverHref = href,
            // Nextcloud's default retention is 30 days, the same as the local one.
            expiresAtMillis = retentionSeconds?.let { deletedAt + it * 1000 } ?: TrashRetention.expiresAtMillis(deletedAt),
        )
        return when (parsed.componentType) {
            ComponentType.Event -> parsed.event?.let { base.copy(title = it.title, startMillis = it.startsAtMillis, hasTime = !it.allDay) }
            ComponentType.Task -> parsed.task?.let { base.copy(title = it.title, startMillis = it.trashStartMillis(), hasTime = it.trashHasTime()) }
            ComponentType.Unknown -> null
        }
    }

    /** The restored item came back through the sync without its app-only colour. */
    private suspend fun restoreManualColor(item: TrashedItemEntity) {
        val color = item.manualColor ?: return
        when (item.componentType) {
            ComponentType.Event -> database.eventDao().byUidInCollection(item.collectionHref, item.uid)
                ?.takeIf { it.manualColor == null }
                ?.let { database.eventDao().upsert(it.copy(manualColor = color)) }
            ComponentType.Task -> database.taskDao().byUidInCollection(item.collectionHref, item.uid)
                ?.takeIf { it.manualColor == null }
                ?.let { database.taskDao().upsert(it.copy(manualColor = color)) }
            ComponentType.Unknown -> Unit
        }
    }
}

private fun AccountEntity.calendarHomePath(): String? =
    calendarHomeUrl?.let { runCatching { URI(it).rawPath }.getOrNull() }?.let { if (it.endsWith('/')) it else "$it/" }

private fun String.hrefKey(): String =
    runCatching { URI(this).path ?: this }.getOrDefault(this).trimEnd('/')

/** The decoded last path segment, which is what Nextcloud reports as `calendar-uri`. */
private fun String.lastPathSegment(): String = hrefKey().substringAfterLast('/')
