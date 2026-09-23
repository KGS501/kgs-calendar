package com.kgs.calendar.data

import androidx.room.withTransaction
import com.kgs.calendar.data.ical.IcalCodec
import com.kgs.calendar.data.local.KgsDatabase
import com.kgs.calendar.data.local.entity.AccountEntity
import com.kgs.calendar.data.local.entity.CalendarResourceEntity
import com.kgs.calendar.data.local.entity.CollectionEntity
import com.kgs.calendar.data.local.entity.PendingMutationEntity
import com.kgs.calendar.data.provider.AndroidCalendarProviderClient
import com.kgs.calendar.domain.model.MutationAction
import org.json.JSONObject

/**
 * Local write plumbing shared by the mutation and sync components: transactions, cached
 * resources and the pending-mutation queue drained by the uploader.
 */
internal class LocalWriteSupport(
    private val database: KgsDatabase,
    private val icalCodec: IcalCodec,
) {
    suspend fun <T> writeTransaction(block: suspend () -> T): T = database.withTransaction(block)

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

    suspend fun enqueuePut(
        collectionHref: String,
        resourceHref: String,
        componentType: String,
        rawIcs: String,
        baseEtag: String?,
    ) {
        if (isReadOnlyCollectionHref(collectionHref) || collectionHref.isLocalCollectionHref() || isAndroidProviderCollectionHref(collectionHref)) return
        writeTransaction {
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
    }

    suspend fun enqueueDelete(
        collectionHref: String,
        resourceHref: String,
        componentType: String,
        baseEtag: String?,
    ) {
        if (isReadOnlyCollectionHref(collectionHref) || collectionHref.isLocalCollectionHref() || isAndroidProviderCollectionHref(collectionHref)) return
        writeTransaction {
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
    }

    suspend fun isReadOnlyCollectionHref(href: String): Boolean =
        href.startsWith(READ_ONLY_PREFIX) || database.collectionDao().get(href)?.readOnly == true

    suspend fun isAndroidProviderCollectionHref(href: String): Boolean =
        href.startsWith(AndroidCalendarProviderClient.ANDROID_CALENDAR_PREFIX) ||
            database.collectionDao().get(href)?.isAndroidProviderCollection() == true

    suspend fun removeStaleRemoteCollections(accountId: String, remoteHrefs: Set<String>) {
        database.collectionDao().forAccount(accountId)
            .filterNot { it.href in remoteHrefs }
            .filterNot { it.href.isLocalCollectionHref() || it.href.startsWith(READ_ONLY_PREFIX) }
            .forEach { stale ->
                database.pendingMutationDao().deleteForCollection(stale.href)
                database.collectionDao().delete(stale.href)
            }
    }

    suspend fun normalizedPendingTaskPayload(mutation: PendingMutationEntity): String {
        val raw = mutation.payloadIcs ?: error("Missing payload")
        database.taskDao().byResource(mutation.resourceHref)?.let { return icalCodec.serializeTask(it, raw) }
        val color = database.collectionDao().get(mutation.collectionHref)?.color ?: DEFAULT_COLORS.first()
        val parsedTask = icalCodec.parse(raw, mutation.collectionHref, mutation.resourceHref, color)?.task
        return parsedTask?.let { icalCodec.serializeTask(it, raw) } ?: raw
    }
}

internal fun CollectionEntity.isReadOnlyCollection(): Boolean =
    readOnly || href.startsWith(READ_ONLY_PREFIX)

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

internal fun CollectionEntity.isAndroidProviderCollection(): Boolean =
    sourceType == SourceType.AndroidProvider || href.startsWith(AndroidCalendarProviderClient.ANDROID_CALENDAR_PREFIX)
