package com.kgs.calendar.ui

import com.kgs.calendar.data.CalendarRepository
import com.kgs.calendar.data.local.entity.TrashedItemEntity
import com.kgs.calendar.data.remote.HttpStatusException
import com.kgs.calendar.data.trash.TrashRestoreResult
import com.kgs.calendar.data.trash.TrashedItemPreview
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * The "Recently deleted" page: the trashed items, each with its event or task read back for the
 * usual cards, and the outcome of the last action that needs a word. Items whose restore or
 * permanent delete is still running are left out, so they leave the list right away; they come
 * back if the action fails.
 */
data class TrashUiState(
    val entries: List<TrashedItemPreview> = emptyList(),
    val notice: TrashNotice? = null,
) {
    val items: List<TrashedItemEntity> get() = entries.map { it.item }
}

/** An outcome the user has to be told about; plain successes need none. */
sealed interface TrashNotice {
    /** The original calendar was gone or read-only, so the item went to [collectionName]. */
    data class RestoredElsewhere(val title: String, val collectionName: String) : TrashNotice

    data class NoWritableCalendar(val title: String) : TrashNotice

    data class AlreadyExists(val title: String) : TrashNotice

    data class Unreadable(val title: String) : TrashNotice

    /** A Nextcloud trash bin item the server no longer has, e.g. because its retention ran out. */
    data class GoneFromServer(val title: String) : TrashNotice

    /** The server refused to restore a Nextcloud trash bin item with HTTP [statusCode]. */
    data class ServerRefused(val title: String, val statusCode: Int) : TrashNotice

    data class Failed(val title: String, val reason: String) : TrashNotice

    /**
     * The server refused to delete a Nextcloud trash bin item for good, with HTTP [statusCode]
     * (e.g. 403 for a calendar shared read-only), or couldn't be reached ([statusCode] null, with
     * [reason]). The item stays in the list.
     */
    data class DeleteFailed(val title: String, val statusCode: Int?, val reason: String) : TrashNotice

    /**
     * Emptying the trash left items that their server refused to delete ([statusCode] of the first
     * refusal) or couldn't be reached for ([statusCode] null, with [reason]). They stay listed.
     */
    data class EmptyTrashFailed(val statusCode: Int?, val reason: String) : TrashNotice
}

/**
 * Restores and purges "Recently deleted" items. A restore takes the usual edit path afterwards:
 * queued uploads are pushed, then widgets and reminders are refreshed.
 */
class CalendarTrashActions internal constructor(
    private val scope: CoroutineScope,
    private val repository: CalendarRepository,
    private val widgetRefresher: WidgetRefresher,
    private val reminderRescheduler: ReminderRescheduler,
    private val message: MutableStateFlow<String?>,
) {
    private val notice = MutableStateFlow<TrashNotice?>(null)

    /** Items being restored or deleted for good, and ones that are gone but may still be listed. */
    private val hidden = MutableStateFlow<Set<Long>>(emptySet())

    val state: StateFlow<TrashUiState> = combine(repository.observeTrashedItemPreviews(), notice, hidden) { entries, notice, hiddenIds ->
        TrashUiState(entries = entries.filterNot { it.item.id in hiddenIds }, notice = notice)
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), TrashUiState())

    init {
        // Sync purges expired items too; this covers devices that never sync.
        scope.launch { runCatching { repository.purgeExpiredTrash() } }
    }

    fun restore(item: TrashedItemEntity) {
        scope.launch {
            hideWhile(item) {
                val startedAt = System.currentTimeMillis()
                val result = try {
                    repository.restoreTrashedItem(item.id)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    notice.value = TrashNotice.Failed(item.title, error.message.orEmpty())
                    return@hideWhile false
                }
                notice.value = when (result) {
                    is TrashRestoreResult.Restored -> result.takeUnless { it.inOriginalCalendar }
                        ?.let { TrashNotice.RestoredElsewhere(item.title, it.collectionName) }
                    TrashRestoreResult.NotFound -> null
                    TrashRestoreResult.NoWritableCalendar -> TrashNotice.NoWritableCalendar(item.title)
                    TrashRestoreResult.AlreadyExists -> TrashNotice.AlreadyExists(item.title)
                    TrashRestoreResult.Unreadable -> TrashNotice.Unreadable(item.title)
                    TrashRestoreResult.GoneFromServer -> TrashNotice.GoneFromServer(item.title)
                    is TrashRestoreResult.ServerRefused -> TrashNotice.ServerRefused(item.title, result.statusCode)
                }
                if (result !is TrashRestoreResult.Restored) {
                    return@hideWhile result == TrashRestoreResult.NotFound || result == TrashRestoreResult.GoneFromServer
                }
                runCatching { repository.pushPendingChangesCreatedSince(startedAt) }
                    .onSuccess { message.value = null }
                    .onFailure { message.value = it.message ?: "Could not save changes." }
                widgetRefresher.updateAll()
                runCatching { reminderRescheduler.reschedule() }
                true
            }
        }
    }

    /** Re-reads the Nextcloud trash bins; call when the trash is opened. */
    fun refresh() {
        scope.launch { runCatching { repository.refreshTrash() } }
    }

    /** Server trash items that their server refuses to delete stay listed, with a notice. */
    fun deletePermanently(item: TrashedItemEntity) {
        scope.launch {
            hideWhile(item) {
                try {
                    repository.deleteTrashedItemPermanently(item.id)
                    true
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    notice.value = TrashNotice.DeleteFailed(item.title, error.httpStatusCode(), error.message.orEmpty())
                    false
                }
            }
        }
    }

    /** Items that their server refuses to delete stay listed, with a notice. */
    fun emptyTrash() {
        scope.launch {
            try {
                repository.emptyTrash()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                notice.value = TrashNotice.EmptyTrashFailed(error.httpStatusCode(), error.message.orEmpty())
            }
        }
    }

    fun dismissNotice() {
        notice.value = null
    }

    /**
     * Hides [item] while [action] runs. It stays hidden when [action] reports it gone (ids are never
     * reused), so it doesn't flash back before the list catches up, and returns when it stays.
     */
    private suspend fun hideWhile(item: TrashedItemEntity, action: suspend () -> Boolean) {
        hidden.update { it + item.id }
        var gone = false
        try {
            gone = action()
        } finally {
            if (!gone) hidden.update { it - item.id }
        }
    }
}

private fun Throwable.httpStatusCode(): Int? = (this as? HttpStatusException)?.statusCode
