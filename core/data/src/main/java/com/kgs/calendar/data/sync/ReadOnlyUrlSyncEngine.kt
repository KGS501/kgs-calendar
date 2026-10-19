package com.kgs.calendar.data.sync

import com.kgs.calendar.data.DEFAULT_COLORS
import com.kgs.calendar.data.LocalWriteSupport
import com.kgs.calendar.data.READ_ONLY_PREFIX
import com.kgs.calendar.data.describeSyncError
import com.kgs.calendar.data.account.SourceImageKeys
import com.kgs.calendar.data.account.SourceImageStore
import com.kgs.calendar.data.ical.IcalCodec
import com.kgs.calendar.data.ical.IcsCalendarProperties
import com.kgs.calendar.data.local.KgsDatabase
import com.kgs.calendar.data.local.entity.AccountEntity
import com.kgs.calendar.data.local.entity.CollectionEntity
import com.kgs.calendar.data.readOnlyCalendarTransportUrl
import com.kgs.calendar.data.looksLikeHtmlResponse
import com.kgs.calendar.data.remote.HttpStatusException
import com.kgs.calendar.domain.model.SourceType
import com.kgs.calendar.domain.model.SyncState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import kotlin.math.abs
import kotlin.coroutines.cancellation.CancellationException

/** Downloads read-only iCalendar URL subscriptions into a single read-only collection each. */
class ReadOnlyUrlSyncEngine internal constructor(
    private val database: KgsDatabase,
    private val localWrites: LocalWriteSupport,
    private val icalCodec: IcalCodec,
    private val readOnlyHttpClient: OkHttpClient,
    private val sourceImages: SourceImageStore = SourceImageStore(null),
) : CalendarSourceSyncEngine {
    override fun handles(account: AccountEntity): Boolean = account.id.startsWith(READ_ONLY_PREFIX)

    override suspend fun sync(account: AccountEntity, options: SourceSyncOptions): Boolean {
        sync(account)
        return true
    }

    /**
     * Creates the feed's calendar before its first download, so a URL that is slow or failing
     * still shows up (with its sync state) instead of not appearing anywhere.
     */
    suspend fun ensureCollection(account: AccountEntity) {
        val collectionHref = collectionHref(account)
        if (database.collectionDao().get(collectionHref) != null) return
        val color = DEFAULT_COLORS[abs(account.id.hashCode()) % DEFAULT_COLORS.size]
        database.collectionDao().upsertAll(
            listOf(
                CollectionEntity(
                    href = collectionHref,
                    accountId = account.id,
                    displayName = account.displayName ?: "Read-only calendar",
                    color = color,
                    supportsEvents = true,
                    supportsTasks = false,
                    syncToken = null,
                    ctag = null,
                    sortOrder = database.collectionDao().forAccount(account.id).size,
                    readOnly = true,
                    automaticColor = color,
                    sourceType = SourceType.ReadOnlyUrl,
                    externalId = null,
                ),
            ),
        )
    }

    // OkHttp's execute() blocks: callers include the UI's add flow on the main thread, where Android
    // refuses network access, so the whole sync runs on the IO dispatcher.
    suspend fun sync(account: AccountEntity): Unit = withContext(Dispatchers.IO) { syncOnIo(account) }

    private suspend fun syncOnIo(account: AccountEntity) {
        database.accountDao().updateSyncState(SyncState.Syncing, null, account.lastSyncAtMillis, account.id)
        val collectionHref = collectionHref(account)
        val existing = database.collectionDao().get(collectionHref)
        if (existing?.isEnabled == false) {
            database.accountDao().updateSyncState(SyncState.Idle, null, account.lastSyncAtMillis, account.id)
            return
        }
        try {
            var contentType: String? = null
            val response = readOnlyHttpClient.newCall(
                Request.Builder()
                    .url(readOnlyCalendarTransportUrl(account.serverUrl))
                    .header("Accept", READ_ONLY_CALENDAR_ACCEPT)
                    .apply {
                        if (existing?.externalId == account.serverUrl) {
                            existing.ctag?.let { header("If-None-Match", it) }
                            existing.syncToken?.let { header("If-Modified-Since", it) }
                        }
                    }
                    .get()
                    .build(),
            ).execute()
            val feedEtag = response.header("ETag")
            val feedLastModified = response.header("Last-Modified")
            val raw = response.use { body ->
                if (body.code == 304 && existing?.externalId == account.serverUrl) {
                    database.accountDao().updateSyncState(SyncState.Idle, null, System.currentTimeMillis(), account.id)
                    return
                }
                if (!body.isSuccessful) throw HttpStatusException(body.code, "URL returned HTTP ${body.code}")
                contentType = body.header("Content-Type")
                body.body?.string() ?: error("Empty calendar response.")
            }
            if (raw.looksLikeHtmlResponse(contentType)) {
                error("URL returned a web page instead of an iCalendar feed.")
            }
            require(raw.contains("BEGIN:VCALENDAR", ignoreCase = true) && raw.contains("END:VCALENDAR", ignoreCase = true)) {
                "URL returned an invalid iCalendar feed."
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
            val feedProperties = IcsCalendarProperties.parse(raw)
            val collection = CollectionEntity(
                href = collectionHref,
                accountId = account.id,
                displayName = existing?.customDisplayName ?: feedProperties.name ?: account.displayName ?: "Read-only calendar",
                color = color,
                // A feed without items yet is still an (empty) event calendar, not a calendar of nothing.
                supportsEvents = parsed.any { it.event != null } || parsed.none { it.task != null },
                supportsTasks = parsed.any { it.task != null },
                // Read-only feeds use the existing cursor fields for HTTP cache validators.
                syncToken = feedLastModified,
                ctag = feedEtag,
                isEnabled = existing?.isEnabled ?: true,
                sortOrder = existing?.sortOrder ?: 0,
                readOnly = true,
                remoteDisplayName = feedProperties.name,
                customDisplayName = existing?.customDisplayName,
                automaticColor = automaticColor,
                sourceColor = existing?.sourceColor,
                customColor = existing?.customColor,
                sourceType = SourceType.ReadOnlyUrl,
                externalId = account.serverUrl,
                capabilitiesJson = JSONObject(existing?.capabilitiesJson ?: "{}")
                    .put(FEED_ITEM_COUNT_KEY, parsed.size)
                    .toString(),
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
            refreshFeedImage(account, feedProperties)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            val syncError = account.describeSyncError(error)
            database.accountDao().updateSyncState(SyncState.Error, syncError, account.lastSyncAtMillis, account.id)
            throw IllegalStateException(syncError, error)
        }
    }

    /** The feed's IMAGE as the source image, checked once a day; a missing image never fails the sync. */
    private fun refreshFeedImage(account: AccountEntity, properties: IcsCalendarProperties) {
        val key = SourceImageKeys.account(account.id)
        val now = System.currentTimeMillis()
        if (!sourceImages.isRefreshDue(key, now, IMAGE_REFRESH_INTERVAL_MILLIS)) return
        runCatching {
            val bytes = properties.imageBytes ?: properties.imageUrl?.let(::downloadImage)
            when {
                bytes != null -> sourceImages.save(key, bytes)
                properties.imageUrl == null -> sourceImages.delete(key)
            }
            sourceImages.markChecked(key, now)
        }
    }

    private fun downloadImage(url: String): ByteArray? =
        readOnlyHttpClient.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body ?: return null
            if (body.contentLength() > MAX_IMAGE_BYTES) return null
            body.byteStream().use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    if (output.size() > MAX_IMAGE_BYTES) return null
                }
                output.toByteArray()
            }
        }

    internal companion object {
        private const val READ_ONLY_CALENDAR_ACCEPT = "text/calendar, application/calendar+ics, text/plain, */*"
        private const val IMAGE_REFRESH_INTERVAL_MILLIS = 24L * 60L * 60L * 1000L
        private const val MAX_IMAGE_BYTES = 2 * 1024 * 1024

        /** How many events and tasks the feed held at its last successful download (in capabilitiesJson). */
        const val FEED_ITEM_COUNT_KEY = "feedItemCount"

        fun collectionHref(account: AccountEntity): String = "$READ_ONLY_PREFIX${account.id}"
    }
}
