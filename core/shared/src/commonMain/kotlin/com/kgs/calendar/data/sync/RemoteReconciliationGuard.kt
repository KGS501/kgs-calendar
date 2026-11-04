package com.kgs.calendar.data.sync

import com.kgs.calendar.data.local.KgsDatabase
import com.kgs.calendar.data.local.entity.CalendarResourceEntity
import com.kgs.calendar.data.local.entity.CollectionEntity
import com.kgs.calendar.data.trash.TrashSnapshots
import com.kgs.calendar.domain.model.ComponentType

/** More cached items would go than a server hiccup should be able to take at once. */
class ReconciliationHeldException(collectionName: String, count: Int) : IllegalStateException(
    "The server stopped listing $count items of \"$collectionName\". They were kept on this device; check the server, then sync again.",
)

/**
 * CalDAV has no way to tell a calendar that was emptied from a server that answered with a valid but
 * incomplete listing, so removals inferred from a listing can't be refused. Single deletions pass as
 * they are. Before a large share of a calendar disappears this way, its items go to "Recently
 * deleted" as byte-exact local snapshots: they stay there for the usual retention, and are only sent
 * to the server when the user restores them. Beyond [MAX_SNAPSHOTS] the removal is held instead,
 * which aborts the transaction and keeps the data and the sync cursors.
 *
 * Tombstones from an incremental sync are explicit deletions and don't come through here.
 */
internal class RemoteReconciliationGuard(
    private val database: KgsDatabase,
    private val trashSnapshots: TrashSnapshots,
) {
    /** Call in the transaction that removes [resources] of the [totalResources] in [collection]. */
    suspend fun beforeRemoving(collection: CollectionEntity, resources: List<CalendarResourceEntity>, totalResources: Int) {
        if (resources.size < MIN_SUSPICIOUS_REMOVALS || resources.size * 2 < totalResources) return
        if (resources.size > MAX_SNAPSHOTS) throw ReconciliationHeldException(collection.displayName, resources.size)
        resources.forEach { resource ->
            when (resource.componentType) {
                ComponentType.Event -> database.eventDao().byResource(resource.href)
                    ?.let { trashSnapshots.recordEvent(it, collection, resource.rawIcs) }
                ComponentType.Task -> database.taskDao().byResource(resource.href)
                    ?.let { trashSnapshots.recordTask(it, collection, resource.rawIcs) }
                ComponentType.Unknown -> Unit
            }
        }
    }

    private companion object {
        const val MIN_SUSPICIOUS_REMOVALS = 20
        const val MAX_SNAPSHOTS = 2_000
    }
}
