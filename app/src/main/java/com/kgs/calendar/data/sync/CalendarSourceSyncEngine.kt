package com.kgs.calendar.data.sync

import com.kgs.calendar.data.local.entity.AccountEntity

/** Options of one [SyncOrchestrator.syncNow] run, handed to every engine. */
data class SourceSyncOptions(
    val includeDisabledProviderCalendars: Boolean = false,
    val forceFullCalDavRefresh: Boolean = false,
)

/** Syncs one kind of calendar source. */
interface CalendarSourceSyncEngine {
    /** Whether this engine syncs [account]. The orchestrator asks its engines in order and uses the first match. */
    fun handles(account: AccountEntity): Boolean

    /**
     * Syncs [account] and records its sync state. Returns false when the account was skipped
     * without syncing; failures are thrown.
     */
    suspend fun sync(account: AccountEntity, options: SourceSyncOptions): Boolean
}
