package com.kgs.calendar.data.sync

import com.kgs.calendar.data.LocalWriteSupport
import com.kgs.calendar.data.ical.IcalCodec
import com.kgs.calendar.data.ical.ParsedCalendarComponent
import com.kgs.calendar.data.local.KgsDatabase
import com.kgs.calendar.data.local.entity.CalendarResourceEntity
import com.kgs.calendar.data.local.entity.PendingMutationEntity
import com.kgs.calendar.data.local.entity.withValidIcalSchedule
import com.kgs.calendar.domain.model.ComponentType
import com.kgs.calendar.domain.model.MutationAction
import java.net.URI

/** Local repair routines run before every sync, plus re-parsing of the cached iCal payloads. */
class SyncRepairs internal constructor(
    private val database: KgsDatabase,
    private val localWrites: LocalWriteSupport,
    private val icalCodec: IcalCodec,
) {
    /**
     * Re-parses every locally cached iCal payload through the current codec and writes
     * the result back to the events/tasks tables. Used after parser fixes ship so that
     * already-synced data picks up corrections without forcing a full network re-fetch.
     */
    suspend fun reparseLocalResources() {
        reparseResources(database.resourceDao().all())
    }

    suspend fun reparseLocalTaskResources() {
        reparseResources(database.resourceDao().forComponentType(ComponentType.Task))
    }

    /**
     * Parses outside the write transaction, then applies each result only if its resource is still
     * the one that was parsed: a concurrent sync or local edit (queued as a pending mutation) wins.
     * A payload the parser rejects is skipped, since reparsing it again would fail the same way.
     */
    internal suspend fun reparseResources(resources: List<CalendarResourceEntity>) {
        val collectionsByHref = database.collectionDao().all().associateBy { it.href }
        val reparsed = resources.mapNotNull { resource ->
            val collection = collectionsByHref[resource.collectionHref] ?: return@mapNotNull null
            val parsed = runCatching {
                icalCodec.parse(resource.rawIcs, collection.href, resource.href, collection.color)
            }.getOrNull() ?: return@mapNotNull null
            resource to parsed
        }
        if (reparsed.isEmpty()) return
        localWrites.writeTransaction {
            reparsed.forEach { (snapshot, parsed) -> applyReparsed(snapshot, parsed) }
        }
    }

    private suspend fun applyReparsed(snapshot: CalendarResourceEntity, parsed: ParsedCalendarComponent) {
        val current = database.resourceDao().get(snapshot.href) ?: return
        if (current.rawIcs != snapshot.rawIcs || current.collectionHref != snapshot.collectionHref) return
        if (database.pendingMutationDao().forResource(current.href).isNotEmpty()) return
        val collection = database.collectionDao().get(current.collectionHref) ?: return
        parsed.event?.let { freshEvent ->
            val existing = database.eventDao().byResource(current.href)
            database.eventDao().upsert(freshEvent.copy(color = collection.color, manualColor = existing?.manualColor))
        }
        parsed.task?.let { freshTask ->
            val existing = database.taskDao().byResource(current.href)
            database.taskDao().upsert(freshTask.copy(color = collection.color, manualColor = existing?.manualColor))
        }
    }

    /**
     * Repairs VTODO rows written by early prototypes before uploading them.
     * Nextcloud rejects DTSTART / DUE pairs when one is DATE and the other is
     * DATE-TIME. A mixed pair represents a timed start-only or end-only task in
     * the UI, so the stale date-only counterpart is removed.
     */
    suspend fun repairInvalidTaskSchedules() {
        if (database.taskDao().invalidIcalSchedules().isEmpty()) return
        localWrites.writeTransaction { repairInvalidTaskSchedulesInTransaction() }
    }

    private suspend fun repairInvalidTaskSchedulesInTransaction() {
        database.taskDao().invalidIcalSchedules().forEach { task ->
            val repaired = task.withValidIcalSchedule()
            if (repaired == task) return@forEach
            val resource = database.resourceDao().get(task.resourceHref)
            val raw = icalCodec.serializeTask(repaired, resource?.rawIcs)
            localWrites.upsertLocalResource(
                collectionHref = repaired.collectionHref,
                resourceHref = repaired.resourceHref,
                etag = resource?.etag,
                componentType = ComponentType.Task,
                uid = repaired.uid,
                rawIcs = raw,
            )
            database.taskDao().upsert(repaired)
            if (!localWrites.isReadOnlyCollectionHref(repaired.collectionHref)) {
                localWrites.enqueuePut(repaired.collectionHref, repaired.resourceHref, ComponentType.Task, raw, resource?.etag)
            }
        }
    }

    /**
     * Older debug builds could queue many retries for the same task resource.
     * Keep the newest PUT and rewrite its payload through the validated codec
     * before any CalDAV request is made.
     */
    internal suspend fun repairPendingTaskMutations(
        pendingMutations: List<PendingMutationEntity>? = null,
    ) {
        val taskPutsByResource = (pendingMutations ?: database.pendingMutationDao().all())
            .filter { it.action == MutationAction.Put && it.componentType == ComponentType.Task }
            .groupBy { it.resourceHref }
        if (taskPutsByResource.isEmpty()) return
        localWrites.writeTransaction {
            taskPutsByResource.values.forEach { mutations ->
                val newest = mutations.maxBy { it.id }
                mutations.filterNot { it.id == newest.id }.forEach { database.pendingMutationDao().delete(it) }
                val normalizedPayload = localWrites.normalizedPendingTaskPayload(newest)
                if (normalizedPayload != newest.payloadIcs) {
                    database.pendingMutationDao().updatePayload(newest.id, normalizedPayload)
                }
            }
        }
    }

    /**
     * Some CalDAV servers, including mailbox.org/Open-Xchange, return a canonical
     * percent-encoded object href such as `uid%40kgs-calendar.ics` even when an older
     * local build used an equivalent unescaped path. Merge only these path aliases,
     * preserving the queued edit's precondition. Distinct paths with the same UID and
     * pending deletions require explicit resolution; automatic repair must not lose work.
     */
    internal suspend fun repairDuplicateCalDavResources() {
        if (database.resourceDao().duplicateCalDavCandidates().isEmpty()) return
        localWrites.writeTransaction { repairDuplicateCalDavResourcesInTransaction() }
    }

    private suspend fun repairDuplicateCalDavResourcesInTransaction() {
        val resources = database.resourceDao().duplicateCalDavCandidates()
        if (resources.isEmpty()) return
        val collectionsByHref = database.collectionDao().all()
            .associateBy { it.href }
        val pendingByResource = database.pendingMutationDao().all()
            .groupBy { it.resourceHref }

        resources
            .groupBy { Triple(it.collectionHref, it.componentType, it.uid) }
            .values
            .filter { it.size > 1 }
            // Matching UIDs at different paths are not proof that one resource supersedes another.
            .filter { duplicates -> duplicates.map { URI(it.href).path }.distinct().size == 1 }
            .filterNot { duplicates -> duplicates.any { resource -> pendingByResource[resource.href].orEmpty().any { it.action == MutationAction.Delete } } }
            .forEach { duplicates ->
                val collection = collectionsByHref[duplicates.first().collectionHref] ?: return@forEach
                val canonical = duplicates.maxWithOrNull(
                    compareBy<CalendarResourceEntity> { if (it.etag != null && it.syncError == null) 1 else 0 }
                        .thenBy { if ('%' in it.href) 1 else 0 }
                        .thenByDescending { it.href.length },
                ) ?: return@forEach

                val newestPendingPut = duplicates
                    .flatMap { pendingByResource[it.href].orEmpty() }
                    .filter { it.action == MutationAction.Put }
                    .maxWithOrNull(compareBy<PendingMutationEntity> { it.createdAtMillis }.thenBy { it.id })

                if (newestPendingPut != null && newestPendingPut.resourceHref != canonical.href) {
                    val pendingRaw = newestPendingPut.payloadIcs
                        ?: duplicates.firstOrNull { it.href == newestPendingPut.resourceHref }?.rawIcs
                    if (!pendingRaw.isNullOrBlank()) {
                        val parsed = icalCodec.parse(
                            rawIcs = pendingRaw,
                            collectionHref = canonical.collectionHref,
                            resourceHref = canonical.href,
                            collectionColor = collection.color,
                        )
                        val componentType = parsed?.componentType ?: canonical.componentType
                        localWrites.upsertLocalResource(
                            collectionHref = canonical.collectionHref,
                            resourceHref = canonical.href,
                            etag = newestPendingPut.baseEtag,
                            componentType = componentType,
                            uid = canonical.uid,
                            rawIcs = pendingRaw,
                        )
                        parsed?.event?.let { event ->
                            val existing = database.eventDao().byResource(canonical.href)
                            database.eventDao().upsert(event.copy(manualColor = event.manualColor ?: existing?.manualColor))
                            database.taskDao().deleteByResource(canonical.href)
                        }
                        parsed?.task?.let { task ->
                            val existing = database.taskDao().byResource(canonical.href)
                            database.taskDao().upsert(task.copy(manualColor = existing?.manualColor).withValidIcalSchedule())
                            database.eventDao().deleteByResource(canonical.href)
                        }
                        database.pendingMutationDao().delete(newestPendingPut)
                        localWrites.enqueuePut(
                            collectionHref = canonical.collectionHref,
                            resourceHref = canonical.href,
                            componentType = componentType,
                            rawIcs = pendingRaw,
                            baseEtag = newestPendingPut.baseEtag,
                        )
                    }
                }

                duplicates
                    .filterNot { it.href == canonical.href }
                    .forEach { stale ->
                        database.pendingMutationDao().deleteForResource(stale.href)
                        database.eventDao().deleteByResource(stale.href)
                        database.taskDao().deleteByResource(stale.href)
                        database.resourceDao().delete(stale.href)
                    }
            }
    }

}
