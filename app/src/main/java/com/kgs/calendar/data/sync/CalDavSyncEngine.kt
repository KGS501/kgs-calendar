package com.kgs.calendar.data.sync

import com.kgs.calendar.data.DEFAULT_COLORS
import com.kgs.calendar.data.LocalWriteSupport
import com.kgs.calendar.data.SourceType
import com.kgs.calendar.data.hasTimedIcalProperty
import com.kgs.calendar.data.ical.IcalCodec
import com.kgs.calendar.data.ical.ParsedCalendarComponent
import com.kgs.calendar.data.isCalDavScheduleInbox
import com.kgs.calendar.data.local.KgsDatabase
import com.kgs.calendar.data.local.entity.AccountEntity
import com.kgs.calendar.data.local.entity.CalendarResourceEntity
import com.kgs.calendar.data.local.entity.CollectionEntity
import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.data.local.entity.withValidIcalSchedule
import com.kgs.calendar.data.remote.CalDavHttpClient
import com.kgs.calendar.data.remote.RemoteCollection
import com.kgs.calendar.data.remote.RemoteResource
import com.kgs.calendar.data.secure.CredentialsStore
import com.kgs.calendar.data.secure.StoredCredentials
import com.kgs.calendar.domain.model.ComponentType
import com.kgs.calendar.domain.model.MutationAction
import java.net.URI
import java.time.Instant
import java.time.ZoneId

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
        val credentials = credentialsStore.get(account.id) ?: return false
        database.accountDao().updateSyncState("syncing", null, account.lastSyncAtMillis, account.id)
        repairMissingCalDavEtags(credentials, account.id)
        uploader.pushPending(credentials, account.id)
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
        localWrites.writeTransaction {
            database.collectionDao().upsertAll(collectionEntities)
            collectionEntities.forEach { collection ->
                database.eventDao().updateColorForCollection(collection.href, collection.color)
                database.taskDao().updateColorForCollection(collection.href, collection.color)
            }
            localWrites.removeStaleRemoteCollections(account.id, collectionEntities.map { it.href }.toSet())
        }
        collectionEntities
            .filter { it.isEnabled }
            .forEach { collection ->
                val remote = remoteCollections.firstOrNull { it.href.davHrefKey() == collection.href.davHrefKey() }
                syncCollection(credentials, collection, remote, forceFullRefresh = options.forceFullCalDavRefresh)
            }
        database.accountDao().updateSyncState("idle", null, System.currentTimeMillis(), account.id)
        return true
    }

    private suspend fun syncCollection(
        credentials: StoredCredentials,
        collection: CollectionEntity,
        discovered: RemoteCollection?,
        forceFullRefresh: Boolean = false,
    ) {
        val localResources = database.resourceDao().forCollection(collection.href)
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
            if (key in pendingDeletes || !shouldApplyRemoteCalDavState(key, pendingPuts)) return@filter false
            val local = localByKey[key]
            // An unchanged ETag means we already hold this version, typically our own upload.
            local == null || local.syncError != null || (incremental != null && remote.etag == null) || local.etag != remote.etag
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

        val downloads = changedResources.mapNotNull { remote ->
            val local = localByKey[remote.href.davHrefKey()]
            val fetched = fetchedByHref[remote.href.davHrefKey()]
            if (local != null && local.syncError == null && fetched?.etag != null && fetched.etag == local.etag) return@mapNotNull null
            val localHref = local?.href ?: remote.href
            val download = runCatching {
                val raw = fetched?.calendarData
                    ?: calDavClient.getResource(credentials.serverUrl, remote.href, credentials.username, credentials.appPassword)
                raw to icalCodec.parse(raw, collection.href, localHref, collection.color)
            }
            RemoteCalDavDownload(remote, local, localHref, fetched?.etag ?: remote.etag, download)
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
            database.collectionDao().updateSyncMarkers(
                href = collection.href,
                syncToken = incremental?.syncToken ?: discovered?.syncToken,
                ctag = discovered?.ctag,
            )
        }
    }

    private suspend fun applyRemoteCalDavDownload(collection: CollectionEntity, download: RemoteCalDavDownload) {
        val local = download.local
        val localHref = download.localHref
        // A local edit queued while this batch was downloading wins; the push path reconciles it with the server.
        if (database.pendingMutationDao().forResource(localHref).isNotEmpty()) return
        try {
            val (raw, parsed) = download.result.getOrThrow()
            val effectiveEtag = download.etag
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
                val mergedTask = it.preserveLocalTimedFields(local?.rawIcs, database.taskDao().byResource(localHref))
                    .withValidIcalSchedule()
                if (mergedTask != it) {
                    localWrites.upsertLocalResource(collection.href, localHref, effectiveEtag, parsed.componentType, parsed.uid, icalCodec.serializeTask(mergedTask))
                }
                database.taskDao().upsert(mergedTask)
                database.eventDao().deleteByResource(localHref)
            }
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

    private fun Long?.sameLocalDateOrMissing(other: Long?): Boolean =
        other == null || (this != null && Instant.ofEpochMilli(this).atZone(zoneId).toLocalDate() == Instant.ofEpochMilli(other).atZone(zoneId).toLocalDate())

    private companion object {
        const val RESOURCE_MULTIGET_BATCH_SIZE = 50
        const val UNKNOWN_COMPONENT_TYPE = "UNKNOWN"
        const val MAX_SYNC_ERROR_LENGTH = 500
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

private class RemoteCalDavDownload(
    val remote: RemoteResource,
    val local: CalendarResourceEntity?,
    val localHref: String,
    val etag: String?,
    val result: Result<Pair<String, ParsedCalendarComponent?>>,
)

internal fun shouldApplyRemoteCalDavState(
    resourceKey: String,
    pendingPutResourceKeys: Set<String>,
): Boolean = resourceKey !in pendingPutResourceKeys
