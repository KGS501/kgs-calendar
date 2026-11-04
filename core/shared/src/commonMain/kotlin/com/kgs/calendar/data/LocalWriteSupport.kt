package com.kgs.calendar.data

import com.kgs.calendar.time.*
import kotlinx.datetime.*
import com.kgs.calendar.data.local.inWriteTransaction
import com.kgs.calendar.data.ical.IcalCodec
import com.kgs.calendar.data.local.KgsDatabase
import com.kgs.calendar.data.local.entity.AccountEntity
import com.kgs.calendar.data.local.entity.CalendarResourceEntity
import com.kgs.calendar.data.local.entity.CollectionEntity
import com.kgs.calendar.data.local.entity.PendingMutationEntity
import com.kgs.calendar.data.local.entity.withValidIcalSchedule
import com.kgs.calendar.data.sync.RemoteReconciliationGuard
import com.kgs.calendar.data.provider.AndroidCalendarProviderClient
import com.kgs.calendar.domain.model.ComponentType
import com.kgs.calendar.domain.model.MutationAction
import com.kgs.calendar.domain.model.SourceType
import com.kgs.calendar.domain.source.isAndroidProviderCollection
import com.kgs.calendar.domain.source.isLocalCollectionHref
import com.kgs.calendar.domain.sync.PendingOccurrenceScope
import com.kgs.calendar.json.JSONObject

/**
 * Local write plumbing shared by the mutation and sync components: transactions, cached
 * resources and the pending-mutation queue drained by the uploader.
 */
internal class LocalWriteSupport(
    private val database: KgsDatabase,
    private val icalCodec: IcalCodec,
) {
    suspend fun <T> writeTransaction(block: suspend () -> T): T = database.inWriteTransaction(block)

    /**
     * Runs [block] as one transaction unless one of [collectionHrefs] is an Android provider calendar.
     * Provider calls must not run inside a transaction, so those paths wrap only their local writes.
     */
    suspend fun <T> localWriteUnit(vararg collectionHrefs: String?, block: suspend () -> T): T =
        if (collectionHrefs.any { it != null && isAndroidProviderCollectionHref(it) }) block() else writeTransaction(block)

    suspend fun upsertLocalResource(
        collectionHref: String,
        resourceHref: String,
        etag: String?,
        componentType: ComponentType,
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

    /**
     * Queues [rawIcs] for upload, replacing any PUT still queued for [resourceHref].
     * [occurrenceScope] records which occurrences the change covers for the pending-sync
     * indicators only ([PendingOccurrenceScope]; null = the whole resource). A replaced PUT's
     * scope is merged in because the new payload carries its changes too.
     */
    suspend fun enqueuePut(
        collectionHref: String,
        resourceHref: String,
        componentType: ComponentType,
        rawIcs: String,
        baseEtag: String?,
        occurrenceScope: String? = null,
    ): Long? {
        if (isReadOnlyCollectionHref(collectionHref) || collectionHref.isLocalCollectionHref() || isAndroidProviderCollectionHref(collectionHref)) return null
        return writeTransaction {
            val replaced = database.pendingMutationDao().forResource(resourceHref)
                .filter { it.action == MutationAction.Put }
            val replacedScopes = replaced.map { it.occurrenceScope }
            database.pendingMutationDao().deleteForResourceAndAction(resourceHref, MutationAction.Put)
            val id = database.pendingMutationDao().insert(
                PendingMutationEntity(
                    accountId = database.collectionDao().get(collectionHref)?.accountId ?: AccountEntity.PRIMARY_ID,
                    collectionHref = collectionHref,
                    resourceHref = resourceHref,
                    componentType = componentType,
                    action = MutationAction.Put,
                    payloadIcs = rawIcs,
                    baseEtag = baseEtag,
                    createdAtMillis = nowMillis(),
                    occurrenceScope = PendingOccurrenceScope.merge(replacedScopes, occurrenceScope),
                ),
            )
            // A move's source keeps waiting for the destination, now under its newest version.
            replaced.forEach { database.pendingMutationDao().replaceDependency(it.id, id) }
            id
        }
    }

    suspend fun enqueueDelete(
        collectionHref: String,
        resourceHref: String,
        componentType: ComponentType,
        baseEtag: String?,
        dependsOnMutationId: Long? = null,
    ) {
        if (isReadOnlyCollectionHref(collectionHref) || collectionHref.isLocalCollectionHref() || isAndroidProviderCollectionHref(collectionHref)) return
        writeTransaction {
            // A moved item deleted before its upload: nothing is left to wait for at the destination.
            database.pendingMutationDao().forResource(resourceHref)
                .filter { it.action == MutationAction.Put }
                .forEach { database.pendingMutationDao().replaceDependency(it.id, null) }
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
                    createdAtMillis = nowMillis(),
                    dependsOnMutationId = dependsOnMutationId,
                ),
            )
        }
    }

    /**
     * Calls off the removal of a move's source because its destination was given up: the source
     * stayed on the server, so its cached resource becomes a normal item again.
     */
    suspend fun cancelSourceRemoval(mutation: PendingMutationEntity) {
        writeTransaction {
            val queued = database.pendingMutationDao().get(mutation.id) ?: return@writeTransaction
            database.pendingMutationDao().delete(queued)
            val resource = database.resourceDao().get(queued.resourceHref) ?: return@writeTransaction
            val collection = database.collectionDao().get(resource.collectionHref) ?: return@writeTransaction
            val parsed = runCatching {
                icalCodec.parse(resource.rawIcs, collection.href, resource.href, collection.color)
            }.getOrNull() ?: return@writeTransaction
            parsed.event?.let { database.eventDao().upsert(it) }
            parsed.task?.let { database.taskDao().upsert(it.withValidIcalSchedule()) }
        }
    }

    suspend fun isReadOnlyCollectionHref(href: String): Boolean =
        href.startsWith(READ_ONLY_PREFIX) || database.collectionDao().get(href)?.readOnly == true

    suspend fun isAndroidProviderCollectionHref(href: String): Boolean =
        href.startsWith(AndroidCalendarProviderClient.ANDROID_CALENDAR_PREFIX) ||
            database.collectionDao().get(href)?.isAndroidProviderCollection() == true

    suspend fun removeStaleRemoteCollections(
        accountId: String,
        remoteHrefs: Set<String>,
        guard: RemoteReconciliationGuard? = null,
    ) {
        database.collectionDao().forAccount(accountId)
            .filterNot { it.href in remoteHrefs }
            .filterNot { it.href.isLocalCollectionHref() || it.href.startsWith(READ_ONLY_PREFIX) }
            .forEach { stale ->
                // Losing access to a remote collection must not discard unsent local work.
                // Keep the collection visible so its upload error can be inspected/recovered.
                if (database.pendingMutationDao().allForAccount(accountId).none { it.collectionHref == stale.href }) {
                    val resources = database.resourceDao().forCollection(stale.href)
                    guard?.beforeRemoving(stale, resources, totalResources = resources.size)
                    database.collectionDao().delete(stale.href)
                }
            }
    }

    suspend fun normalizedPendingTaskPayload(mutation: PendingMutationEntity): String {
        val raw = mutation.payloadIcs ?: error("Missing payload")
        val color = database.collectionDao().get(mutation.collectionHref)?.color ?: DEFAULT_COLORS.first()
        val parsedTask = icalCodec.parse(raw, mutation.collectionHref, mutation.resourceHref, color)?.task
        return parsedTask?.let { icalCodec.serializeTask(it, raw) } ?: raw
    }
}

private fun CollectionEntity.capability(name: String, defaultValue: Boolean): Boolean =
    runCatching {
        val json = capabilitiesJson?.let(::JSONObject) ?: return@runCatching defaultValue
        if (json.has(name) && !json.isNull(name)) json.optBoolean(name, defaultValue) else defaultValue
    }.getOrDefault(defaultValue)

internal fun CollectionEntity.canCreateResources(): Boolean =
    sourceType != SourceType.CalDav || capability("canCreateResources", !readOnly)

internal fun CollectionEntity.canDeleteResources(): Boolean =
    sourceType != SourceType.CalDav || capability("canDeleteResources", !readOnly)

internal fun CollectionEntity.canWriteProperties(): Boolean =
    sourceType == SourceType.CalDav && capability("canWriteProperties", !readOnly)

internal fun CollectionEntity.isCalDavScheduleInbox(): Boolean =
    sourceType == SourceType.CalDav &&
        capabilitiesJson
            ?.let { runCatching { JSONObject(it).optBoolean("isScheduleInbox", false) }.getOrDefault(false) } == true
