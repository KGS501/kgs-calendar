package com.kgs.calendar.data.mutation

import com.kgs.calendar.data.LocalWriteSupport
import com.kgs.calendar.data.canCreateResources
import com.kgs.calendar.data.ical.IcsEventReader
import com.kgs.calendar.data.local.KgsDatabase
import com.kgs.calendar.data.local.entity.CollectionEntity
import com.kgs.calendar.data.newResourceHref
import com.kgs.calendar.domain.model.ComponentType
import com.kgs.calendar.domain.source.isAndroidProviderCollection
import com.kgs.calendar.domain.source.isReadOnlyCollection

data class EventImportResult(val imported: Int, val skipped: Int)

/** Imports complete resources into calendars owned by KGS, using the normal CalDAV outbox. */
class EventImports internal constructor(
    private val database: KgsDatabase,
    private val localWrites: LocalWriteSupport,
    val reader: IcsEventReader,
) {
    fun canImportInto(collection: CollectionEntity): Boolean = collection.supportsEvents && collection.isEnabled &&
        !collection.isReadOnlyCollection() && !collection.isAndroidProviderCollection() && collection.canCreateResources()

    suspend fun importEvents(rawIcs: String, collectionHref: String): EventImportResult {
        val events = reader.read(rawIcs)
        return localWrites.writeTransaction {
            val collection = requireNotNull(database.collectionDao().get(collectionHref))
            require(canImportInto(collection)) { "Choose a writable local or CalDAV calendar" }
            val existing = database.resourceDao().forCollection(collection.href)
            val existingUids = existing.map { it.uid }.toMutableSet()
            val existingHrefs = existing.map { it.href }.toMutableSet()
            var imported = 0
            events.forEach { item ->
                val event = item.event.copy(
                    collectionHref = collection.href,
                    resourceHref = collection.newResourceHref(item.event.uid),
                    color = collection.color,
                )
                // Reopening an attachment must never overwrite an existing or pending local event.
                if (existingUids.add(event.uid) && existingHrefs.add(event.resourceHref)) {
                    localWrites.upsertLocalResource(collection.href, event.resourceHref, null, ComponentType.Event, event.uid, item.rawIcs)
                    database.eventDao().upsert(event)
                    localWrites.enqueuePut(collection.href, event.resourceHref, ComponentType.Event, item.rawIcs, null)
                    imported++
                }
            }
            EventImportResult(imported, events.size - imported)
        }
    }
}
