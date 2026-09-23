package com.kgs.calendar.data.sync

import com.kgs.calendar.data.DEFAULT_COLORS
import com.kgs.calendar.data.LocalWriteSupport
import com.kgs.calendar.data.READ_ONLY_PREFIX
import com.kgs.calendar.data.describeSyncError
import com.kgs.calendar.data.ical.IcalCodec
import com.kgs.calendar.data.isTransientReadOnlySyncFailure
import com.kgs.calendar.data.local.KgsDatabase
import com.kgs.calendar.data.local.entity.AccountEntity
import com.kgs.calendar.data.local.entity.CollectionEntity
import com.kgs.calendar.data.looksLikeHtmlResponse
import com.kgs.calendar.data.remote.HttpStatusException
import com.kgs.calendar.domain.model.SourceType
import com.kgs.calendar.domain.model.SyncState
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.math.abs

/** Downloads read-only iCalendar URL subscriptions into a single read-only collection each. */
class ReadOnlyUrlSyncEngine internal constructor(
    private val database: KgsDatabase,
    private val localWrites: LocalWriteSupport,
    private val icalCodec: IcalCodec,
    private val readOnlyHttpClient: OkHttpClient,
) : CalendarSourceSyncEngine {
    override fun handles(account: AccountEntity): Boolean = account.id.startsWith(READ_ONLY_PREFIX)

    override suspend fun sync(account: AccountEntity, options: SourceSyncOptions): Boolean {
        sync(account)
        return true
    }

    suspend fun sync(account: AccountEntity) {
        database.accountDao().updateSyncState(SyncState.Syncing, null, account.lastSyncAtMillis, account.id)
        val collectionHref = "$READ_ONLY_PREFIX${account.id}"
        val existing = database.collectionDao().get(collectionHref)
        if (existing?.isEnabled == false) {
            database.accountDao().updateSyncState(SyncState.Idle, null, account.lastSyncAtMillis, account.id)
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
                if (!body.isSuccessful) throw HttpStatusException(body.code, "URL returned HTTP ${body.code}")
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
            val components = parsed.mapNotNull { component ->
                val resourceHref = component.event?.resourceHref ?: component.task?.resourceHref ?: return@mapNotNull null
                val rawComponent = when {
                    component.event != null -> icalCodec.serializeEvent(component.event)
                    component.task != null -> icalCodec.serializeTask(component.task)
                    else -> raw
                }
                Triple(resourceHref, component, rawComponent)
            }
            localWrites.writeTransaction {
                database.collectionDao().upsertAll(listOf(collection))
                val refreshedResourceHrefs = mutableSetOf<String>()
                components.forEach { (resourceHref, component, rawComponent) ->
                    refreshedResourceHrefs += resourceHref
                    localWrites.upsertLocalResource(collection.href, resourceHref, null, component.componentType, component.uid, rawComponent)
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
                database.accountDao().updateSyncState(SyncState.Idle, null, System.currentTimeMillis(), account.id)
            }
        } catch (error: Throwable) {
            if (existing != null && error.isTransientReadOnlySyncFailure()) {
                database.accountDao().updateSyncState(SyncState.Idle, null, account.lastSyncAtMillis, account.id)
                return
            }
            val syncError = account.describeSyncError(error)
            database.accountDao().updateSyncState(SyncState.Error, syncError, account.lastSyncAtMillis, account.id)
            throw IllegalStateException(syncError, error)
        }
    }

    private companion object {
        const val READ_ONLY_CALENDAR_ACCEPT = "text/calendar, application/calendar+ics, text/plain, */*"
    }
}
