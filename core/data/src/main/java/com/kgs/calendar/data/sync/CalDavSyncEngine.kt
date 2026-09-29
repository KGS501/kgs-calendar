package com.kgs.calendar.data.sync

import android.database.SQLException
import com.kgs.calendar.data.DEFAULT_COLORS
import com.kgs.calendar.data.LocalWriteSupport
import com.kgs.calendar.data.ical.IcalCodec
import com.kgs.calendar.data.ical.ParsedCalendarComponent
import com.kgs.calendar.data.isCalDavScheduleInbox
import com.kgs.calendar.data.local.KgsDatabase
import com.kgs.calendar.data.local.entity.AccountEntity
import com.kgs.calendar.data.local.entity.CalendarResourceEntity
import com.kgs.calendar.data.local.entity.CollectionEntity
import com.kgs.calendar.data.local.entity.withValidIcalSchedule
import com.kgs.calendar.data.normalizedIcsText
import com.kgs.calendar.data.remote.CalDavCollectionCapabilities
import com.kgs.calendar.data.remote.CalDavHttpClient
import com.kgs.calendar.data.remote.CalDavTrashBinSupport
import com.kgs.calendar.data.remote.RemoteCollection
import com.kgs.calendar.data.remote.RemoteResource
import com.kgs.calendar.data.remote.RemoteResourceData
import com.kgs.calendar.data.remote.isTransientFailure
import com.kgs.calendar.data.remote.isConnectionFailure
import com.kgs.calendar.data.remote.HttpStatusException
import com.kgs.calendar.data.secure.CredentialsStore
import com.kgs.calendar.data.secure.StoredCredentials
import com.kgs.calendar.domain.model.ComponentType
import com.kgs.calendar.domain.model.MutationAction
import org.json.JSONObject
import java.net.URI
import kotlin.coroutines.cancellation.CancellationException
import java.time.ZoneId
import com.kgs.calendar.domain.model.SourceType
import com.kgs.calendar.domain.model.SyncState

/** Two-way CalDAV sync of one account: ETag repair, upload of queued changes, discovery and collection pulls. */
class CalDavSyncEngine internal constructor(
    private val database: KgsDatabase,
    private val credentialsStore: CredentialsStore,
    private val calDavClient: CalDavHttpClient,
    private val icalCodec: IcalCodec,
    private val localWrites: LocalWriteSupport,
    private val uploader: PendingMutationUploader,
    private val zoneId: ZoneId,
) : CalendarSourceSyncEngine {
    /** Every account the other engines don't claim is a CalDAV account, so this engine must be asked last. */
    override fun handles(account: AccountEntity): Boolean = true

    override suspend fun sync(account: AccountEntity, options: SourceSyncOptions): Boolean {
        val credentials = credentialsStore.get(account.id) ?: error("Missing account credentials. Reconnect this calendar account.")
        database.accountDao().updateSyncState(SyncState.Syncing, null, account.lastSyncAtMillis, account.id)
        repairMissingCalDavEtags(credentials, account.id)
        // A rejected upload stays queued and marked on its resource; it must not keep remote changes away.
        val uploadFailure = try {
            uploader.pushPending(credentials, account.id)
            null
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            if (error.isConnectionFailure()) throw error
            PendingUploadException(error)
        }
        try {
            pullAccount(account, credentials, options)
        } catch (pullError: Throwable) {
            uploadFailure?.let(pullError::addSuppressed)
            throw pullError
        }
        uploadFailure?.let { throw it }
        database.accountDao().updateSyncState(SyncState.Idle, null, System.currentTimeMillis(), account.id)
        return true
    }

    /**
     * Pulls one stored calendar without discovery, e.g. right after an item was restored on the
     * server. It goes incremental when the calendar supports sync-collection and lists all ETags
     * otherwise. Returns false when the calendar isn't a synced CalDAV calendar or has no login.
     */
    internal suspend fun syncStoredCollection(collectionHref: String): Boolean {
        val collection = database.collectionDao().get(collectionHref)
            ?.takeIf { it.sourceType == SourceType.CalDav && it.isEnabled }
            ?: return false
        val credentials = credentialsStore.get(collection.accountId) ?: return false
        val supportsSyncCollection = collection.capabilitiesJson
            ?.let { runCatching { JSONObject(it).optBoolean("supportsSyncCollection", false) }.getOrNull() } == true
        val stored = RemoteCollection(
            href = collection.href,
            displayName = collection.remoteDisplayName ?: collection.displayName,
            color = collection.sourceColor,
            supportsEvents = collection.supportsEvents,
            supportsTasks = collection.supportsTasks,
            syncToken = collection.syncToken,
            // No ctag: this pull must not be skipped as unchanged. The next full sync stores a fresh one.
            ctag = null,
            readOnly = collection.readOnly,
            capabilities = CalDavCollectionCapabilities(
                supportedReports = if (supportsSyncCollection) setOf("sync-collection") else emptySet(),
            ),
        )
        syncCollection(credentials, collection, stored)
        return true
    }

    private suspend fun pullAccount(account: AccountEntity, credentials: StoredCredentials, options: SourceSyncOptions) {
        val discovery = calDavClient.discoverAccount(
            serverUrl = credentials.serverUrl,
            username = credentials.username,
            appPassword = credentials.appPassword,
        )
        database.accountDao().updateCalDavDiscovery(
            id = account.id,
            principalUrl = discovery.principalUrl,
            calendarHomeUrl = discovery.calendarHomeUrl,
            // The trash bin check is not part of discovery; keep it until it is due again.
            capabilitiesJson = CalDavTrashBinSupport.carryOver(
                previousJson = database.accountDao().get(account.id)?.capabilitiesJson ?: account.capabilitiesJson,
                into = discovery.toCapabilitiesJson(),
            ),
        )
        val discoveredCollections = calDavClient.discoverCollections(
            discovery = discovery,
            username = credentials.username,
            appPassword = credentials.appPassword,
        )
        val previousCollectionsByHref = database.collectionDao().forAccount(account.id).associateBy { it.href }
        val previousByPath = previousCollectionsByHref.values.associateBy { it.href.davHrefKey() }
        val remoteCollections = discoveredCollections.map { remote ->
            val owned = previousByPath[remote.href.davHrefKey()]
            val taken = database.collectionDao().get(remote.href)?.accountId?.let { it != account.id } == true
            val href = owned?.href ?: if (taken) URI(credentials.serverUrl).resolve(remote.href).toString() else remote.href
            check(database.collectionDao().get(href)?.accountId?.let { it != account.id } != true) {
                "This calendar is already connected through another account."
            }
            remote.copy(href = href)
        }
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
                readOnly = remote.readOnly,
                remoteDisplayName = remote.displayName,
                customDisplayName = existing?.customDisplayName,
                automaticColor = automaticColor,
                sourceColor = remote.color,
                customColor = existing?.customColor,
                sourceType = SourceType.CalDav,
                externalId = remote.href,
                capabilitiesJson = JSONObject(remote.capabilities.toJson())
                    .put(RECONCILIATION_VERSION_KEY, existing?.reconciliationVersion() ?: 0).toString(),
            )
        }
        localWrites.writeTransaction {
            database.collectionDao().upsertAll(collectionEntities)
            collectionEntities.forEach { collection ->
                database.eventDao().updateColorForCollection(collection.href, collection.color)
                database.taskDao().updateColorForCollection(collection.href, collection.color)
            }
            localWrites.removeStaleRemoteCollections(account.id, collectionEntities.map { it.href }.toSet())
        }
        var firstCollectionError: Throwable? = null
        collectionEntities
            .filter { it.isEnabled }
            .forEach { collection ->
                val remote = remoteCollections.firstOrNull { it.href.davHrefKey() == collection.href.davHrefKey() }
                try {
                    syncCollection(credentials, collection, remote, forceFullRefresh = options.forceFullCalDavRefresh)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    if (error.isConnectionFailure()) throw error
                    if (firstCollectionError == null) firstCollectionError = error else firstCollectionError!!.addSuppressed(error)
                }
            }
        firstCollectionError?.let { throw it }
    }

    private suspend fun syncCollection(
        credentials: StoredCredentials,
        collection: CollectionEntity,
        discovered: RemoteCollection?,
        forceFullRefresh: Boolean = false,
    ) {
        val localResources = database.resourceDao().forCollection(collection.href)
        val reconcileFull = forceFullRefresh || (collection.reconciliationVersion() < RECONCILIATION_VERSION &&
            (collection.syncToken != null || collection.ctag != null || localResources.isNotEmpty()))
        val localByKey = localResources.associateBy { it.href.davHrefKey() }
        val pendingMutations = database.pendingMutationDao().allForAccount(collection.accountId)
            .filter { it.collectionHref == collection.href }
        val pendingDeletes = pendingMutations
            .asSequence()
            .filter { it.action == MutationAction.Delete }
            .map { it.resourceHref.davHrefKey() }
            .toSet()
        val pendingPuts = pendingMutations
            .asSequence()
            .filter { it.action == MutationAction.Put }
            .map { it.resourceHref.davHrefKey() }
            .toSet()

        val incremental = if (
            !reconcileFull &&
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
            !reconcileFull &&
            incremental == null &&
            // An invalid sync token requires a full listing even if the ctag is unchanged.
            !(collection.syncToken != null && discovered?.capabilities?.supportsSyncCollection == true) &&
            collection.ctag != null &&
            discovered?.ctag != null &&
            collection.ctag == discovered.ctag &&
            localResources.none { it.syncError != null }
        ) {
            return
        }

        val queriedResources = if (
            incremental == null &&
            reconcileFull &&
            (collection.supportsTasks || collection.isCalDavScheduleInbox()) &&
            collection.sourceType == SourceType.CalDav
        ) {
            try {
                calDavClient.queryResources(
                    serverUrl = credentials.serverUrl,
                    collectionHref = collection.href,
                    componentName = ComponentType.Task.value,
                    username = credentials.username,
                    appPassword = credentials.appPassword,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (error.preventsBatchFallback()) throw error
                emptyList()
            }
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
            .map { remote ->
                if (URI(collection.href).isAbsolute) remote.copy(href = URI(collection.href).resolve(remote.href).toString()) else remote
            }
            .distinctBy { it.href.davHrefKey() }
        val remoteByKey = remoteResources.associateBy { it.href.davHrefKey() }
        val changedResources = remoteResources.filter { remote ->
            val key = remote.href.davHrefKey()
            if (key in pendingDeletes || !shouldApplyRemoteCalDavState(key, pendingPuts)) return@filter false
            val local = localByKey[key]
            // After the upgrade reconciliation, unchanged ETags avoid downloading our own uploads.
            reconcileFull || local == null || local.etag == null || local.syncError != null || remote.etag == null || local.etag != remote.etag
        }
        val queriedByHref = queriedResources.associateBy { it.href.davHrefKey() }
        val multigetByHref = changedResources
            .filterNot { it.href.davHrefKey() in queriedByHref }
            .chunked(RESOURCE_MULTIGET_BATCH_SIZE)
            .flatMap { batch ->
                // Unsupported batch reports may fall back to GET; outages must not fan out.
                try {
                    calDavClient.multigetResources(
                        serverUrl = credentials.serverUrl,
                        collectionHref = collection.href,
                        hrefs = batch.map { it.href },
                        username = credentials.username,
                        appPassword = credentials.appPassword,
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    if (error.preventsBatchFallback()) throw error
                    emptyList()
                }
            }
            .associateBy { it.href.davHrefKey() }
        val fetchedByHref = multigetByHref + queriedByHref

        val downloads = changedResources.mapNotNull { remote ->
            val local = localByKey[remote.href.davHrefKey()]
            val fetched = fetchedByHref[remote.href.davHrefKey()]
            if (!reconcileFull && local != null && local.syncError == null && fetched?.etag != null && fetched.etag == local.etag) return@mapNotNull null
            val localHref = local?.href ?: remote.href
            val outcome = downloadOutcome(credentials, collection, remote.href, localHref, fetched)
            RemoteCalDavDownload(remote, local, localHref, fetched?.etag ?: remote.etag, outcome)
        }
        val deletedKeys = incremental?.deletedHrefs
            ?.map { it.davHrefKey() }
            ?.toSet()
            ?: localByKey.keys.minus(remoteByKey.keys)

        // Everything above talks to the server; the batch and the sync markers that cover it are stored atomically.
        localWrites.writeTransaction {
            downloads.forEach { download -> applyRemoteCalDavDownload(collection, download) }
            deletedKeys.mapNotNull(localByKey::get).forEach { deletedResource ->
                val deletedHref = deletedResource.href
                if (!shouldApplyRemoteCalDavState(deletedHref.davHrefKey(), pendingPuts)) return@forEach
                if (database.pendingMutationDao().forResource(deletedHref).any { it.action == MutationAction.Put }) return@forEach
                database.eventDao().deleteByResource(deletedHref)
                database.taskDao().deleteByResource(deletedHref)
                database.resourceDao().delete(deletedHref)
            }
            // A resource that could not be downloaded for now is only listed again while the old markers stand.
            if (downloads.none { (it.outcome as? DownloadOutcome.Failed)?.retryable == true }) {
                database.collectionDao().updateCapabilitiesJson(
                    collection.href,
                    JSONObject(collection.capabilitiesJson ?: "{}")
                        .put(RECONCILIATION_VERSION_KEY, RECONCILIATION_VERSION).toString(),
                )
                database.collectionDao().updateSyncMarkers(
                    href = collection.href,
                    syncToken = incremental?.syncToken ?: discovered?.syncToken,
                    ctag = discovered?.ctag,
                )
            }
        }
        val failures = downloads.mapNotNull { (it.outcome as? DownloadOutcome.Failed)?.takeIf { it.retryable }?.error }
        failures.firstOrNull()?.let { first ->
            failures.drop(1).filter { it !== first }.forEach(first::addSuppressed)
            throw first
        }
    }

    /** Falls back to a plain GET for resources the batch requests did not return. */
    private suspend fun downloadOutcome(
        credentials: StoredCredentials,
        collection: CollectionEntity,
        remoteHref: String,
        localHref: String,
        fetched: RemoteResourceData?,
    ): DownloadOutcome {
        val response = fetched ?: try {
            calDavClient.getResourceWithEtag(credentials.serverUrl, remoteHref, credentials.username, credentials.appPassword)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            if (error.isConnectionFailure()) throw error
            return DownloadOutcome.Failed(error, retryable = error.isRetryableFetchFailure())
        }
        return parsedDownload(response.calendarData, collection, localHref, response.etag)
    }

    private fun parsedDownload(raw: String, collection: CollectionEntity, localHref: String, etag: String? = null): DownloadOutcome =
        try {
            DownloadOutcome.Parsed(raw, icalCodec.parse(raw, collection.href, localHref, collection.color), etag)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            DownloadOutcome.Failed(error, retryable = false)
        }

    private suspend fun applyRemoteCalDavDownload(collection: CollectionEntity, download: RemoteCalDavDownload) {
        val local = download.local
        val localHref = download.localHref
        // A local edit queued while this batch was downloading wins; the push path reconciles it with the server.
        if (database.pendingMutationDao().forResource(localHref).isNotEmpty()) return
        // A temporary fetch failure says nothing about the cached item's validity. Keep both its
        // body and matching ETag intact; the held collection cursor and account error trigger retry.
        if ((download.outcome as? DownloadOutcome.Failed)?.retryable == true) return
        try {
            val (raw, parsed) = when (val outcome = download.outcome) {
                is DownloadOutcome.Parsed -> outcome.raw to outcome.parsed
                is DownloadOutcome.Failed -> throw outcome.error
            }
            val effectiveEtag = (download.outcome as? DownloadOutcome.Parsed)?.etag
            if (parsed == null) {
                upsertFailedResource(
                    collectionHref = collection.href,
                    resourceHref = localHref,
                    etag = effectiveEtag,
                    rawIcs = raw,
                    existing = local,
                    error = "Import failed: no supported VEVENT or VTODO component was found.",
                )
                return
            }
            localWrites.upsertLocalResource(collection.href, localHref, effectiveEtag, parsed.componentType, parsed.uid, raw)
            parsed.event?.let {
                val existingEvent = database.eventDao().byResource(localHref)
                database.eventDao().upsert(it.copy(manualColor = it.manualColor ?: existingEvent?.manualColor))
                database.taskDao().deleteByResource(localHref)
            }
            parsed.task?.let {
                val mergedTask = it.copy(manualColor = database.taskDao().byResource(localHref)?.manualColor)
                    .withValidIcalSchedule()
                if (mergedTask != it) {
                    localWrites.upsertLocalResource(collection.href, localHref, effectiveEtag, parsed.componentType, parsed.uid, icalCodec.serializeTask(mergedTask))
                }
                database.taskDao().upsert(mergedTask)
                database.eventDao().deleteByResource(localHref)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: SQLException) {
            // A failed write must abort the whole batch instead of committing it as an import error.
            throw error
        } catch (error: Throwable) {
            upsertFailedResource(
                collectionHref = collection.href,
                resourceHref = localHref,
                etag = download.remote.etag ?: local?.etag,
                rawIcs = local?.rawIcs.orEmpty(),
                existing = local,
                error = "Import failed: ${error.message ?: error::class.java.simpleName}",
            )
        }
    }

    /**
     * Fills in the ETag of resources whose upload returned none. The body is fetched with it: when it
     * no longer matches our local copy, another client changed the resource after our upload, so it is
     * applied like any other remote change instead of being marked as already held.
     */
    private suspend fun repairMissingCalDavEtags(credentials: StoredCredentials, accountId: String) {
        database.collectionDao().forAccount(accountId)
            .filter { it.sourceType == SourceType.CalDav && it.isEnabled }
            .forEach { collection ->
                database.resourceDao().missingEtagForCollection(collection.href)
                    .forEach { resource ->
                        // A remote ETag cannot become the base of an unsent local edit.
                        if (database.pendingMutationDao().forResource(resource.href).isNotEmpty()) return@forEach
                        val fetched = try {
                            calDavClient.getResourceWithEtag(
                                serverUrl = credentials.serverUrl,
                                href = resource.href,
                                username = credentials.username,
                                appPassword = credentials.appPassword,
                            )
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Throwable) {
                            if (error.isTransientFailure()) throw error
                            return@forEach
                        }
                        val etag = fetched.etag ?: return@forEach
                        val holdsFetchedContent = fetched.calendarData.normalizedIcsText() == resource.rawIcs.normalizedIcsText()
                        if (holdsFetchedContent) {
                            localWrites.writeTransaction {
                                if (database.pendingMutationDao().forResource(resource.href).isEmpty()) {
                                    database.resourceDao().markSynced(resource.href, etag)
                                }
                            }
                            return@forEach
                        }
                        val download = RemoteCalDavDownload(
                            remote = RemoteResource(resource.href, etag),
                            local = resource,
                            localHref = resource.href,
                            etag = etag,
                            outcome = parsedDownload(fetched.calendarData, collection, resource.href, etag),
                        )
                        localWrites.writeTransaction { applyRemoteCalDavDownload(collection, download) }
                    }
            }
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
                componentType = retainedRaw.inferComponentType() ?: existing?.componentType ?: ComponentType.Unknown,
                uid = retainedRaw.inferUid() ?: existing?.uid ?: resourceHref.substringAfterLast('/').ifBlank { resourceHref },
                rawIcs = retainedRaw,
                syncError = error.take(MAX_SYNC_ERROR_LENGTH),
            ),
        )
    }

    private fun CollectionEntity.reconciliationVersion(): Int =
        runCatching { JSONObject(capabilitiesJson ?: "{}").optInt(RECONCILIATION_VERSION_KEY, 0) }.getOrDefault(0)

    private companion object {
        const val RECONCILIATION_VERSION_KEY = "kgsReconciliationVersion"
        const val RECONCILIATION_VERSION = 1
        const val RESOURCE_MULTIGET_BATCH_SIZE = 50
        const val MAX_SYNC_ERROR_LENGTH = 500
    }
}

private fun String.davHrefKey(): String =
    runCatching {
        val uri = URI(this)
        uri.path ?: uri.rawPath ?: this
    }.getOrDefault(this).trimEnd('/')

private fun String.inferComponentType(): ComponentType? =
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

private class RemoteCalDavDownload(
    val remote: RemoteResource,
    val local: CalendarResourceEntity?,
    val localHref: String,
    val etag: String?,
    val outcome: DownloadOutcome,
)

/** The final result of downloading and parsing one resource. */
private sealed interface DownloadOutcome {
    class Parsed(val raw: String, val parsed: ParsedCalendarComponent?, val etag: String?) : DownloadOutcome

    /** [retryable] failures may succeed unchanged later; the others (bad data, gone) will not. */
    class Failed(val error: Throwable, val retryable: Boolean) : DownloadOutcome
}

/**
 * Network trouble, timeouts, server errors, rate limits and missing access can all clear up without
 * the resource changing. Unlike a whole account sync, a single resource denied with 401/403 is retried.
 */
internal fun Throwable.isRetryableFetchFailure(): Boolean = isTransientFailure(alsoTransientStatuses = FETCH_ACCESS_STATUSES)

private val FETCH_ACCESS_STATUSES = setOf(401, 403)

/** Rejected queued uploads of an account, reported only after its pull has run. */
internal class PendingUploadException(cause: Throwable) :
    Exception("Upload failed: ${cause.message ?: cause::class.java.simpleName}", cause)

internal fun shouldApplyRemoteCalDavState(
    resourceKey: String,
    pendingPutResourceKeys: Set<String>,
): Boolean = resourceKey !in pendingPutResourceKeys

/** 501 means the server does not implement this REPORT; normal GET/listing remains usable. */
private fun Throwable.preventsBatchFallback(): Boolean =
    isTransientFailure() && (this as? HttpStatusException)?.statusCode != 501
