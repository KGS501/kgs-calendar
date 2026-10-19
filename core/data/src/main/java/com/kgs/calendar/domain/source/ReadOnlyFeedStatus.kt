package com.kgs.calendar.domain.source

import com.kgs.calendar.data.local.entity.AccountEntity
import com.kgs.calendar.data.local.entity.CollectionEntity
import com.kgs.calendar.data.sync.ReadOnlyUrlSyncEngine
import com.kgs.calendar.domain.model.SourceType
import com.kgs.calendar.domain.model.SyncState
import org.json.JSONObject

/** Why a subscribed iCalendar URL shows no items, if it does not. */
enum class ReadOnlyFeedStatus {
    /** The first download is running. */
    Loading,

    /** The URL has never delivered a calendar; the source's sync error says why. */
    NoDataYet,

    /** The URL delivered a valid calendar without events or tasks. */
    Empty,

    /** The URL delivered items before, but the latest update failed; the old items stay. */
    UpdateFailed,
}

/** The [ReadOnlyFeedStatus] of a read-only URL [collection] of [account]; null when it is fine or not a feed. */
fun readOnlyFeedStatus(account: AccountEntity?, collection: CollectionEntity): ReadOnlyFeedStatus? {
    if (collection.sourceType != SourceType.ReadOnlyUrl || account == null) return null
    val itemCount = collection.capabilitiesJson
        ?.let { runCatching { JSONObject(it) }.getOrNull() }
        ?.takeIf { it.has(ReadOnlyUrlSyncEngine.FEED_ITEM_COUNT_KEY) }
        ?.optInt(ReadOnlyUrlSyncEngine.FEED_ITEM_COUNT_KEY)
    // Feeds synced before the count was recorded have loaded at least once.
    val everLoaded = itemCount != null || account.lastSyncAtMillis != null
    return when {
        !everLoaded && account.syncState == SyncState.Syncing -> ReadOnlyFeedStatus.Loading
        !everLoaded -> ReadOnlyFeedStatus.NoDataYet
        !account.syncError.isNullOrBlank() -> ReadOnlyFeedStatus.UpdateFailed
        itemCount == 0 -> ReadOnlyFeedStatus.Empty
        else -> null
    }
}
