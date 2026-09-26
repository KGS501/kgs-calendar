package com.kgs.calendar.data.trash

import com.kgs.calendar.data.LocalWriteSupport
import com.kgs.calendar.data.canCreateResources
import com.kgs.calendar.data.ical.IcalCodec
import com.kgs.calendar.data.isCalDavScheduleInbox
import com.kgs.calendar.data.local.KgsDatabase
import com.kgs.calendar.data.local.entity.CollectionEntity
import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.data.local.entity.TrashedItemEntity
import com.kgs.calendar.data.local.entity.withValidIcalSchedule
import com.kgs.calendar.data.mutation.EventMutations
import com.kgs.calendar.data.newResourceHref
import com.kgs.calendar.data.sync.RemoteSyncLock
import com.kgs.calendar.domain.model.ComponentType
import com.kgs.calendar.domain.model.MutationAction
import com.kgs.calendar.domain.model.SourceType
import com.kgs.calendar.domain.source.isAndroidProviderCollection
import com.kgs.calendar.domain.source.isReadOnlyCollection
import com.kgs.calendar.domain.trash.TrashOrigin
import com.kgs.calendar.domain.trash.TrashRetention
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

/** Outcome of [TrashBin.restore]. */
sealed interface TrashRestoreResult {
    /**
     * The item is back in [collectionName]. [inOriginalCalendar] is false when its calendar no
     * longer exists or became read-only and the item went to another writable calendar instead.
     */
    data class Restored(
        val collectionHref: String,
        val collectionName: String,
        val inOriginalCalendar: Boolean,
    ) : TrashRestoreResult

    /** The item is no longer in the trash. */
    data object NotFound : TrashRestoreResult

    /** There is no writable calendar (or task list) left to restore the item into. */
    data object NoWritableCalendar : TrashRestoreResult

    /** The target calendar already contains an item with the same UID. */
    data object AlreadyExists : TrashRestoreResult

    /** The stored iCalendar data can't be read back. */
    data object Unreadable : TrashRestoreResult

    /**
     * The item was in a Nextcloud trash bin but the server no longer has it (HTTP 404), usually
     * because its retention ran out or it was purged elsewhere. It has been removed from the list.
     */
    data object GoneFromServer : TrashRestoreResult

    /**
     * The server refused to restore a Nextcloud trash bin item with [statusCode], e.g. 403 when its
     * calendar already holds a resource with the same name or the calendar is shared read-only,
     * or 409 when the calendar is gone. The item stays in the list.
     */
    data class ServerRefused(val statusCode: Int) : TrashRestoreResult
}

/**
 * "Recently deleted", one list over two kinds of items ([TrashedItemEntity.origin]):
 * - [TrashOrigin.LocalSnapshot]: whole events and tasks the user deleted in the app, for every
 *   source. The deletes themselves write the snapshots ([TrashSnapshots]); remote deletes found by
 *   a sync never become snapshots. They are purged [TrashRetention.DAYS] days after the delete.
 * - [TrashOrigin.ServerTrashBin]: the objects of Nextcloud CalDAV trash bins ([ServerTrashBin]),
 *   including ones deleted elsewhere. The server expires them; the cache follows on refresh.
 * A snapshot is dropped in favour of the server's copy once its DELETE has been uploaded.
 */
class TrashBin internal constructor(
    private val database: KgsDatabase,
    private val localWrites: LocalWriteSupport,
    private val icalCodec: IcalCodec,
    private val eventMutations: EventMutations,
    private val serverTrash: ServerTrashBin,
    private val syncLock: RemoteSyncLock,
) {
    /** Every item, newest delete first. */
    fun observeItems(): Flow<List<TrashedItemEntity>> = database.trashDao().observeAll()

    suspend fun items(): List<TrashedItemEntity> = database.trashDao().all()

    suspend fun item(id: Long): TrashedItemEntity? = database.trashDao().get(id)

    /**
     * Re-reads the trash bins of all CalDAV accounts whose server has one (checking servers whose
     * support is unknown or due), e.g. when the trash is opened. Returns false when a server
     * couldn't be reached; its cached items stay listed.
     */
    suspend fun refresh(): Boolean = syncLock.withLock {
        database.trashDao().deleteServerItemsWithoutAccount()
        database.accountDao().getAll()
            .filter { it.sourceType == SourceType.CalDav }
            .map { serverTrash.refreshAccount(it.id) }
            .all { it }
    }

    /** The sync's refresh of one account; the sync already holds the remote sync lock. */
    internal suspend fun refreshAccountLocked(accountId: String): Boolean = serverTrash.refreshAccount(accountId)

    /**
     * Removes the item for good: server trash items are deleted on the server first, and stay listed
     * when that fails (the error is thrown).
     */
    suspend fun deletePermanently(id: Long) {
        val item = database.trashDao().get(id) ?: return
        when (item.origin) {
            TrashOrigin.LocalSnapshot -> database.trashDao().delete(id)
            TrashOrigin.ServerTrashBin -> serverTrash.deletePermanently(item)
        }
    }

    /**
     * Removes every item; server trash items are deleted on their servers. Items whose server delete
     * fails stay listed, and the first failure is thrown after all others were tried.
     */
    suspend fun empty() {
        database.trashDao().deleteWithOrigin(TrashOrigin.LocalSnapshot)
        var firstFailure: Throwable? = null
        database.trashDao().withOrigin(TrashOrigin.ServerTrashBin).forEach { item ->
            try {
                serverTrash.deletePermanently(item)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                val first = firstFailure
                if (first == null) firstFailure = error else first.addSuppressed(error)
            }
        }
        firstFailure?.let { throw it }
    }

    /**
     * Removes local snapshots deleted more than [TrashRetention.DAYS] days before [nowMillis], server
     * trash items past their server's retention (the server purges them itself) and server items of
     * removed accounts.
     */
    suspend fun purgeExpired(nowMillis: Long = System.currentTimeMillis()): Int {
        val dao = database.trashDao()
        return dao.deleteDeletedBefore(TrashRetention.cutoffMillis(nowMillis)) +
            dao.deleteServerItemsExpiredBefore(nowMillis) +
            dao.deleteServerItemsWithoutAccount()
    }

    /**
     * Server trash items are restored on the server (a MOVE), which puts them back into their calendar
     * with their original name; the calendar is then pulled so they show up right away.
     *
     * Local snapshots are recreated in their original calendar, or, when that calendar is gone or
     * read-only, in a writable calendar of the same account, then of the same source type, then any.
     * CalDAV items are queued as a create (If-None-Match) with the same UID; a queued DELETE that
     * hasn't been uploaded yet is withdrawn instead. Device calendar events are inserted into the
     * provider again.
     */
    suspend fun restore(id: Long): TrashRestoreResult {
        val item = database.trashDao().get(id) ?: return TrashRestoreResult.NotFound
        if (item.origin == TrashOrigin.ServerTrashBin) return serverTrash.restore(item)
        val target = restoreTarget(item) ?: return TrashRestoreResult.NoWritableCalendar
        val result = if (target.isAndroidProviderCollection()) {
            restoreIntoAndroidCalendar(item, target)
        } else {
            restoreIntoStoredCalendar(item, target)
        }
        return result ?: TrashRestoreResult.Restored(
            collectionHref = target.href,
            collectionName = target.displayName,
            inOriginalCalendar = target.href == item.collectionHref,
        )
    }

    private suspend fun restoreTarget(item: TrashedItemEntity): CollectionEntity? {
        val collections = database.collectionDao().all()
        collections.firstOrNull { it.href == item.collectionHref && it.acceptsRestoreOf(item.componentType) }
            ?.let { return it }
        val candidates = collections.filter { it.isEnabled && it.acceptsRestoreOf(item.componentType) }
        return candidates.firstOrNull { it.accountId == item.accountId }
            ?: candidates.firstOrNull { it.sourceType == item.sourceType }
            ?: candidates.firstOrNull()
    }

    private fun CollectionEntity.acceptsRestoreOf(componentType: ComponentType): Boolean {
        val supportsType = when (componentType) {
            ComponentType.Event -> supportsEvents
            ComponentType.Task -> supportsTasks && !isAndroidProviderCollection()
            ComponentType.Unknown -> false
        }
        return supportsType && !isReadOnlyCollection() && canCreateResources() && !isCalDavScheduleInbox()
    }

    /** Local and CalDAV calendars: local rows, the resource and (CalDAV only) a queued PUT. Null on success. */
    private suspend fun restoreIntoStoredCalendar(
        item: TrashedItemEntity,
        target: CollectionEntity,
    ): TrashRestoreResult? = localWrites.writeTransaction {
        val sameCalendar = target.href == item.collectionHref
        val remainingResource = database.resourceDao().get(item.resourceHref)
            ?.takeIf { it.collectionHref == target.href }
        val withdrawsQueuedDelete = sameCalendar &&
            remainingResource != null &&
            database.pendingMutationDao().forResource(item.resourceHref).any { it.action == MutationAction.Delete }
        if (!withdrawsQueuedDelete && uidExistsIn(target.href, item.uid, item.componentType)) {
            return@writeTransaction TrashRestoreResult.AlreadyExists
        }
        val resourceHref = when {
            withdrawsQueuedDelete -> item.resourceHref
            sameCalendar && database.resourceDao().get(item.resourceHref) == null -> item.resourceHref
            else -> freeResourceHref(target, item.uid)
        }
        val parsed = icalCodec.parse(item.rawIcs, target.href, resourceHref, target.color)
            ?: return@writeTransaction TrashRestoreResult.Unreadable
        // Withdrawing a queued DELETE: the server still has the item, so the PUT replaces it (If-Match).
        val baseEtag = remainingResource?.takeIf { withdrawsQueuedDelete }?.etag
        if (withdrawsQueuedDelete) {
            database.pendingMutationDao().deleteForResourceAndAction(resourceHref, MutationAction.Delete)
        }
        when (item.componentType) {
            ComponentType.Event -> {
                val event = parsed.event ?: return@writeTransaction TrashRestoreResult.Unreadable
                localWrites.upsertLocalResource(target.href, resourceHref, baseEtag, ComponentType.Event, event.uid, item.rawIcs)
                database.eventDao().upsert(event.restoredColor(item))
            }
            ComponentType.Task -> {
                val task = parsed.task ?: return@writeTransaction TrashRestoreResult.Unreadable
                localWrites.upsertLocalResource(target.href, resourceHref, baseEtag, ComponentType.Task, task.uid, item.rawIcs)
                database.taskDao().upsert(task.restoredColor(item).withValidIcalSchedule())
            }
            ComponentType.Unknown -> return@writeTransaction TrashRestoreResult.Unreadable
        }
        localWrites.enqueuePut(target.href, resourceHref, item.componentType, item.rawIcs, baseEtag)
        database.trashDao().delete(item.id)
        null
    }

    /** Device calendars: a new provider event (new id), then the local rows. Null on success. */
    private suspend fun restoreIntoAndroidCalendar(
        item: TrashedItemEntity,
        target: CollectionEntity,
    ): TrashRestoreResult? {
        if (item.componentType != ComponentType.Event) return TrashRestoreResult.NoWritableCalendar
        val event = icalCodec.parse(item.rawIcs, target.href, item.resourceHref, target.color)?.event
            ?: return TrashRestoreResult.Unreadable
        eventMutations.insertAndroidEvent(target, event.restoredColor(item)) {
            database.trashDao().delete(item.id)
        }
        return null
    }

    private suspend fun uidExistsIn(collectionHref: String, uid: String, componentType: ComponentType): Boolean =
        when (componentType) {
            ComponentType.Event -> database.eventDao().byUidInCollection(collectionHref, uid) != null
            ComponentType.Task -> database.taskDao().byUidInCollection(collectionHref, uid) != null
            ComponentType.Unknown -> false
        }

    private suspend fun freeResourceHref(collection: CollectionEntity, uid: String): String {
        val preferred = collection.newResourceHref(uid)
        if (database.resourceDao().get(preferred) == null) return preferred
        return collection.newResourceHref(UUID.randomUUID().toString())
    }

    private fun EventEntity.restoredColor(item: TrashedItemEntity): EventEntity =
        copy(manualColor = item.manualColor ?: manualColor)

    private fun TaskEntity.restoredColor(item: TrashedItemEntity): TaskEntity =
        copy(manualColor = item.manualColor ?: manualColor)
}

/** Writes the trash snapshots for the event and task deletes; call inside their local transaction. */
internal class TrashSnapshots(
    private val database: KgsDatabase,
    private val icalCodec: IcalCodec,
) {
    /** [rawIcs] is the cached resource; device calendar events have none and are serialized instead. */
    suspend fun recordEvent(
        event: EventEntity,
        collection: CollectionEntity,
        rawIcs: String?,
        providerEventId: Long? = null,
    ): Long = database.trashDao().insert(
        TrashedItemEntity(
            componentType = ComponentType.Event,
            uid = event.uid,
            collectionHref = collection.href,
            accountId = collection.accountId,
            sourceType = collection.sourceType,
            resourceHref = event.resourceHref,
            providerEventId = providerEventId,
            rawIcs = rawIcs.usableIcs() ?: icalCodec.serializeEvent(event),
            title = event.title,
            startMillis = event.startsAtMillis,
            hasTime = !event.allDay,
            collectionName = collection.displayName,
            collectionColor = collection.color,
            manualColor = event.manualColor,
            deletedAtMillis = System.currentTimeMillis(),
        ),
    )

    suspend fun recordTask(task: TaskEntity, collection: CollectionEntity, rawIcs: String?): Long =
        database.trashDao().insert(
            TrashedItemEntity(
                componentType = ComponentType.Task,
                uid = task.uid,
                collectionHref = collection.href,
                accountId = collection.accountId,
                sourceType = collection.sourceType,
                resourceHref = task.resourceHref,
                rawIcs = rawIcs.usableIcs() ?: icalCodec.serializeTask(task),
                title = task.title,
                startMillis = task.trashStartMillis(),
                hasTime = task.trashHasTime(),
                collectionName = collection.displayName,
                collectionColor = collection.color,
                manualColor = task.manualColor,
                deletedAtMillis = System.currentTimeMillis(),
            ),
        )

    private fun String?.usableIcs(): String? = this?.takeIf { it.contains("BEGIN:VCALENDAR", ignoreCase = true) }
}

/** The date a trashed task is listed with: its due date, else its start. */
internal fun TaskEntity.trashStartMillis(): Long? = dueAtMillis ?: startAtMillis

internal fun TaskEntity.trashHasTime(): Boolean = if (dueAtMillis != null) dueHasTime else startHasTime
