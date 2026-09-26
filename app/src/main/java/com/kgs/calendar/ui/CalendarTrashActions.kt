package com.kgs.calendar.ui

import com.kgs.calendar.data.CalendarRepository
import com.kgs.calendar.data.local.entity.TrashedItemEntity
import com.kgs.calendar.data.trash.TrashRestoreResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The "Recently deleted" screen: the trashed items and the outcome of the last restore that needs a word. */
data class TrashUiState(
    val items: List<TrashedItemEntity> = emptyList(),
    val notice: TrashNotice? = null,
)

/** A restore outcome the user has to be told about; plain successes need none. */
sealed interface TrashNotice {
    val title: String

    /** The original calendar was gone or read-only, so the item went to [collectionName]. */
    data class RestoredElsewhere(override val title: String, val collectionName: String) : TrashNotice

    data class NoWritableCalendar(override val title: String) : TrashNotice

    data class AlreadyExists(override val title: String) : TrashNotice

    data class Unreadable(override val title: String) : TrashNotice

    /** A Nextcloud trash bin item the server no longer has, e.g. because its retention ran out. */
    data class GoneFromServer(override val title: String) : TrashNotice

    /** The server refused to restore a Nextcloud trash bin item with HTTP [statusCode]. */
    data class ServerRefused(override val title: String, val statusCode: Int) : TrashNotice

    data class Failed(override val title: String, val reason: String) : TrashNotice
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

    val state: StateFlow<TrashUiState> = combine(repository.observeTrashedItems(), notice, ::TrashUiState)
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), TrashUiState())

    init {
        // Sync purges expired items too; this covers devices that never sync.
        scope.launch { runCatching { repository.purgeExpiredTrash() } }
    }

    fun restore(item: TrashedItemEntity) {
        scope.launch {
            val startedAt = System.currentTimeMillis()
            val result = runCatching { repository.restoreTrashedItem(item.id) }
                .getOrElse { error ->
                    notice.value = TrashNotice.Failed(item.title, error.message.orEmpty())
                    return@launch
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
            if (result !is TrashRestoreResult.Restored) return@launch
            runCatching { repository.pushPendingChangesCreatedSince(startedAt) }
                .onSuccess { message.value = null }
                .onFailure { message.value = it.message ?: "Could not save changes." }
            widgetRefresher.updateAll()
            runCatching { reminderRescheduler.reschedule() }
        }
    }

    /** Re-reads the Nextcloud trash bins; call when the trash is opened. */
    fun refresh() {
        scope.launch { runCatching { repository.refreshTrash() } }
    }

    fun deletePermanently(item: TrashedItemEntity) {
        scope.launch { runCatching { repository.deleteTrashedItemPermanently(item.id) } }
    }

    fun emptyTrash() {
        scope.launch { runCatching { repository.emptyTrash() } }
    }

    fun dismissNotice() {
        notice.value = null
    }
}
