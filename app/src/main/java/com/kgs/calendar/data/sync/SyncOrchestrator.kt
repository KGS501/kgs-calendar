package com.kgs.calendar.data.sync

import com.kgs.calendar.data.LOCAL_ACCOUNT_ID
import com.kgs.calendar.data.describeSyncError
import com.kgs.calendar.data.local.KgsDatabase
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.kgs.calendar.domain.model.SyncState

/**
 * Runs a full sync: the local repairs, then every account through the first engine that handles
 * it. Holds the remote-sync lock shared by full syncs and targeted uploads.
 */
class SyncOrchestrator(
    private val database: KgsDatabase,
    private val repairs: SyncRepairs,
    private val uploader: PendingMutationUploader,
    private val engines: List<CalendarSourceSyncEngine>,
) {
    private val remoteSyncMutex = Mutex()

    suspend fun syncNow(
        includeDisabledProviderCalendars: Boolean = false,
        forceFullCalDavRefresh: Boolean = false,
    ) = remoteSyncMutex.withLock {
        syncNowLocked(SourceSyncOptions(includeDisabledProviderCalendars, forceFullCalDavRefresh))
    }

    private suspend fun syncNowLocked(options: SourceSyncOptions) {
        repairs.repairInvalidTaskSchedules()
        repairs.repairPendingTaskMutations()
        repairs.repairDuplicateCalDavResources()
        repairs.discardSupersededPendingMutations()
        var successfulAccounts = 0
        var firstError: Throwable? = null
        database.accountDao().getAll().forEach { account ->
            try {
                if (account.id == LOCAL_ACCOUNT_ID) return@forEach
                val engine = engines.firstOrNull { it.handles(account) } ?: return@forEach
                if (engine.sync(account, options)) successfulAccounts++
            } catch (error: Throwable) {
                val syncError = account.describeSyncError(error)
                database.accountDao().updateSyncState(SyncState.Error, syncError, account.lastSyncAtMillis, account.id)
                firstError = firstError ?: IllegalStateException(syncError, error)
            }
        }
        if (successfulAccounts == 0) firstError?.let { throw it }
    }

    suspend fun pushPendingChangesCreatedSince(startedAtMillis: Long) {
        remoteSyncMutex.withLock {
            val threshold = startedAtMillis - TARGETED_SYNC_CLOCK_SKEW_MILLIS
            val pendingMutations = database.pendingMutationDao().createdSince(threshold)
            repairs.repairPendingTaskMutations(pendingMutations)
            uploader.pushPendingMutations(database.pendingMutationDao().createdSince(threshold))
        }
    }

    private companion object {
        const val TARGETED_SYNC_CLOCK_SKEW_MILLIS = 1000L
    }
}
