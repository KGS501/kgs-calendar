package com.kgs.calendar.data.sync

import com.kgs.calendar.data.local.KgsDatabase
import com.kgs.calendar.data.remote.CalDavHttpClient
import com.kgs.calendar.data.remote.HttpStatusException
import com.kgs.calendar.data.secure.CredentialsStore

/**
 * Settles an item whose upload the server refused because another device changed it first. The
 * user keeps either this device's version, which then replaces the server's, or the server's,
 * which replaces this device's unsent change.
 */
class SyncConflictResolver internal constructor(
    private val database: KgsDatabase,
    private val credentialsStore: CredentialsStore,
    private val calDavClient: CalDavHttpClient,
    private val uploader: PendingMutationUploader,
    private val engine: CalDavSyncEngine,
    private val remoteSyncLock: RemoteSyncLock,
) {
    /** Uploads this device's change over the server's current version of [resourceHref]. */
    suspend fun keepLocal(resourceHref: String) = remoteSyncLock.withLock {
        val pending = database.pendingMutationDao().forResource(resourceHref)
        val accountId = pending.firstOrNull()?.accountId ?: return@withLock
        val credentials = credentialsStore.get(accountId) ?: error("Missing account credentials. Reconnect this calendar account.")
        // The version this change now replaces; none when another device deleted the item.
        val serverEtag = try {
            calDavClient.getResourceWithEtag(credentials.serverUrl, resourceHref, credentials.username, credentials.appPassword).etag
        } catch (error: HttpStatusException) {
            if (error.statusCode != 404 && error.statusCode != 410) throw error
            null
        }
        database.resourceDao().markSynced(resourceHref, serverEtag)
        pending.forEach { database.pendingMutationDao().updateBaseEtag(it.id, serverEtag) }
        uploader.pushPendingMutations(credentials, database.pendingMutationDao().forResource(resourceHref))
    }

    /** Drops this device's unsent change to [resourceHref] and loads the server's version. */
    suspend fun useServer(resourceHref: String) = remoteSyncLock.withLock {
        val pending = database.pendingMutationDao().forResource(resourceHref)
        val collectionHref = pending.firstOrNull()?.collectionHref
            ?: database.resourceDao().get(resourceHref)?.collectionHref
            ?: return@withLock
        database.pendingMutationDao().deleteForResource(resourceHref)
        engine.refreshResource(collectionHref, resourceHref)
    }
}
