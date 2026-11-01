package com.kgs.calendar.desktop

import com.kgs.calendar.data.CalendarRepository
import com.kgs.calendar.data.local.KgsDatabase
import com.kgs.calendar.data.settings.SettingsStore
import com.kgs.calendar.data.sync.SyncOrchestrator
import com.kgs.calendar.domain.source.isLocalAccount
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Background synchronization while the app runs, with Android's triggers: the chosen sync
 * interval, every local change (uploaded half a second after the last edit), and a refresh when
 * the window comes to the front after at least a minute.
 */
class DesktopSync(
    private val database: KgsDatabase,
    private val repository: CalendarRepository,
    private val syncOrchestrator: SyncOrchestrator,
    private val settingsStore: SettingsStore,
    private val afterSync: suspend () -> Unit,
) {
    private val lock = Mutex()
    @Volatile
    private var lastFullSyncAt = 0L

    @OptIn(FlowPreview::class)
    fun start(scope: CoroutineScope, windowActivatedAt: Flow<Long>) {
        scope.launch {
            settingsStore.syncIntervalMinutes.distinctUntilChanged().collectLatest { minutes ->
                while (true) {
                    delay(minutes.coerceAtLeast(MIN_INTERVAL_MINUTES) * 60_000L)
                    fullSync()
                }
            }
        }
        scope.launch {
            // The initial emission also uploads edits left over from the last session.
            database.pendingMutationDao().observeAll()
                .map { rows -> rows.map { it.id } }
                .distinctUntilChanged()
                .debounce(500)
                .collect { ids -> if (ids.isNotEmpty()) attempt { syncOrchestrator.pushAllPendingChanges() } }
        }
        scope.launch {
            windowActivatedAt.collect { now ->
                if (now - lastFullSyncAt >= FOREGROUND_REFRESH_MILLIS) fullSync()
            }
        }
    }

    private suspend fun fullSync() {
        if (database.accountDao().getAll().none { !it.isLocalAccount() }) return
        lastFullSyncAt = System.currentTimeMillis()
        attempt {
            repository.syncNow(includeDisabledProviderCalendars = settingsStore.showDisabledAndroidProviderCalendars.first())
        }
    }

    /** Runs [sync], then the follow-ups whatever its outcome: a late failure may already have stored new items. */
    private suspend fun attempt(sync: suspend () -> Unit) = lock.withLock {
        try {
            sync()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            // The repository records sync problems for the Problems page.
        }
        runCatching { afterSync() }
    }

    private companion object {
        const val MIN_INTERVAL_MINUTES = 5
        const val FOREGROUND_REFRESH_MILLIS = 60_000L
    }
}
