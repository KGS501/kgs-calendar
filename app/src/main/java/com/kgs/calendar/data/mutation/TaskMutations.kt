package com.kgs.calendar.data.mutation

import com.kgs.calendar.data.LocalWriteSupport
import com.kgs.calendar.data.canCreateResources
import com.kgs.calendar.data.canDeleteResources
import com.kgs.calendar.data.ical.IcalCodec
import com.kgs.calendar.data.ical.RecurrenceOverrideCodec
import com.kgs.calendar.data.ical.TaskRecurrenceOverride
import com.kgs.calendar.data.isLocalCollectionHref
import com.kgs.calendar.data.isReadOnlyCollection
import com.kgs.calendar.data.local.KgsDatabase
import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.data.local.entity.withValidIcalSchedule
import com.kgs.calendar.data.newResourceHref
import com.kgs.calendar.data.newUid
import com.kgs.calendar.data.toDate
import com.kgs.calendar.data.toMinutesList
import com.kgs.calendar.data.withRecurrenceUntilBefore
import com.kgs.calendar.domain.model.ComponentType
import com.kgs.calendar.domain.model.TaskEditPayload
import com.kgs.calendar.domain.model.normalizedReminderOffsets
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Local task edits; changes to remote task lists are queued for upload. */
class TaskMutations internal constructor(
    private val database: KgsDatabase,
    private val localWrites: LocalWriteSupport,
    private val icalCodec: IcalCodec,
    private val zoneId: ZoneId,
) {
    suspend fun createTask(payload: TaskEditPayload): Unit = localWrites.writeTransaction {
        val collection = payload.collectionHref?.let { database.collectionDao().get(it) }
            ?.takeUnless { it.isReadOnlyCollection() || !it.canCreateResources() }
            ?: database.collectionDao().taskCollections().firstOrNull { !it.isReadOnlyCollection() && it.canCreateResources() }
            ?: error("No writable task list has been synced yet.")
        val uid = newUid()
        val resourceHref = collection.newResourceHref(uid)
        val parentUid = validatedParentUid(collection.href, payload.parentUid, taskUid = uid)
        val task = TaskEntity(
            uid = uid,
            collectionHref = collection.href,
            resourceHref = resourceHref,
            title = payload.title.ifBlank { "Untitled task" },
            notes = payload.notes?.ifBlank { null },
            location = payload.location?.ifBlank { null },
            locationMapVerified = payload.location?.takeIf { it.isNotBlank() }?.let { payload.locationMapVerified },
            url = payload.url?.ifBlank { null },
            categories = payload.categories?.ifBlank { null },
            dueAtMillis = payload.dueDate.toTaskMillis(payload.dueTime, payload.dueHasTime, LocalTime.of(17, 0)),
            dueHasTime = payload.dueDate != null && payload.dueHasTime,
            startAtMillis = payload.startDate.toTaskMillis(payload.startTime, payload.startHasTime, LocalTime.of(9, 0)),
            startHasTime = payload.startDate != null && payload.startHasTime,
            completedAtMillis = if (payload.isCompleted) Instant.now().toEpochMilli() else null,
            isCompleted = payload.isCompleted,
            status = payload.status?.takeIf { it.isNotBlank() }
                ?: if (payload.isCompleted) "COMPLETED" else "NEEDS-ACTION",
            priority = payload.priority?.coerceIn(1, 9),
            percentComplete = payload.percentComplete?.coerceIn(0, 100),
            parentUid = parentUid,
            recurrenceRule = payload.recurrenceRule?.ifBlank { null },
            remindersCsv = payload.reminderMinutes.normalizedReminderOffsets().takeIf { it.isNotEmpty() }?.joinToString(","),
            timezoneId = if (
                (payload.startDate != null && payload.startHasTime) ||
                (payload.dueDate != null && payload.dueHasTime)
            ) zoneId.id else null,
            color = collection.color,
            manualColor = payload.manualColor,
        ).withValidIcalSchedule()
        val raw = icalCodec.serializeTask(task)
        localWrites.upsertLocalResource(collection.href, resourceHref, null, ComponentType.Task, uid, raw)
        database.taskDao().upsert(task)
        localWrites.enqueuePut(collection.href, resourceHref, ComponentType.Task, raw, null)
    }

    suspend fun updateTaskManualColor(uid: String, manualColor: Int?): Unit = localWrites.writeTransaction {
        val existing = database.taskDao().get(uid) ?: return@writeTransaction
        database.taskDao().upsert(existing.copy(manualColor = manualColor))
    }

    suspend fun updateTask(uid: String, payload: TaskEditPayload): Unit = localWrites.writeTransaction {
        val existing = database.taskDao().get(uid) ?: return@writeTransaction
        val resource = database.resourceDao().get(existing.resourceHref)
        val targetCollection = payload.collectionHref?.let { database.collectionDao().get(it) }
            ?: database.collectionDao().get(existing.collectionHref)
            ?: error("Task list not found.")
        if (targetCollection.isReadOnlyCollection()) error("Read-only task lists cannot be edited.")
        val moved = targetCollection.href != existing.collectionHref
        if (moved && database.taskDao().children(existing.collectionHref, existing.uid).isNotEmpty()) {
            error("Move or detach this task's subtasks before changing its task list.")
        }
        val parentUid = validatedParentUid(targetCollection.href, payload.parentUid, existing.uid)
        val resourceHref = if (moved) targetCollection.newResourceHref(existing.uid) else existing.resourceHref
        val updated = existing.copy(
            collectionHref = targetCollection.href,
            resourceHref = resourceHref,
            title = payload.title.ifBlank { "Untitled task" },
            notes = payload.notes?.ifBlank { null },
            location = payload.location?.ifBlank { null },
            locationMapVerified = payload.location?.takeIf { it.isNotBlank() }?.let { payload.locationMapVerified },
            url = payload.url?.ifBlank { null },
            categories = payload.categories?.ifBlank { null },
            dueAtMillis = payload.dueDate.toTaskMillis(payload.dueTime, payload.dueHasTime, LocalTime.of(17, 0)),
            dueHasTime = payload.dueDate != null && payload.dueHasTime,
            startAtMillis = payload.startDate.toTaskMillis(payload.startTime, payload.startHasTime, LocalTime.of(9, 0)),
            startHasTime = payload.startDate != null && payload.startHasTime,
            completedAtMillis = when {
                payload.isCompleted && existing.completedAtMillis == null -> Instant.now().toEpochMilli()
                payload.isCompleted -> existing.completedAtMillis
                else -> null
            },
            isCompleted = payload.isCompleted,
            status = payload.status?.takeIf { it.isNotBlank() }
                ?: existing.status?.takeIf {
                    // Preserve IN-PROCESS / CANCELLED when the editor didn't override it
                    // and the completion state hasn't changed.
                    payload.isCompleted == existing.isCompleted
                }
                ?: if (payload.isCompleted) "COMPLETED" else "NEEDS-ACTION",
            priority = payload.priority?.coerceIn(1, 9),
            percentComplete = payload.percentComplete?.coerceIn(0, 100),
            parentUid = parentUid,
            recurrenceRule = payload.recurrenceRule?.ifBlank { null },
            remindersCsv = payload.reminderMinutes.normalizedReminderOffsets().takeIf { it.isNotEmpty() }?.joinToString(","),
            timezoneId = if (
                (payload.startDate != null && payload.startHasTime) ||
                (payload.dueDate != null && payload.dueHasTime)
            ) existing.timezoneId ?: zoneId.id else null,
            color = targetCollection.color,
            manualColor = payload.manualColor,
            sequence = existing.sequence + 1,
        ).withValidIcalSchedule()
        val raw = icalCodec.serializeTask(updated, originalRawIcs = resource?.rawIcs.takeUnless { moved })
        if (moved) {
            database.taskDao().deleteByResource(existing.resourceHref)
            database.resourceDao().delete(existing.resourceHref)
            localWrites.enqueueDelete(existing.collectionHref, existing.resourceHref, ComponentType.Task, resource?.etag)
            localWrites.upsertLocalResource(updated.collectionHref, updated.resourceHref, null, ComponentType.Task, updated.uid, raw)
            localWrites.enqueuePut(updated.collectionHref, updated.resourceHref, ComponentType.Task, raw, null)
        } else {
            localWrites.upsertLocalResource(updated.collectionHref, updated.resourceHref, resource?.etag, ComponentType.Task, updated.uid, raw)
            localWrites.enqueuePut(updated.collectionHref, updated.resourceHref, ComponentType.Task, raw, resource?.etag)
        }
        database.taskDao().upsert(updated)
    }

    suspend fun updateTaskOccurrence(uid: String, occurrenceStartMillis: Long, payload: TaskEditPayload): Unit = localWrites.writeTransaction {
        val existing = database.taskDao().get(uid) ?: return@writeTransaction
        if (existing.recurrenceRule.isNullOrBlank()) {
            updateTask(uid, payload)
            return@writeTransaction
        }
        if (localWrites.isReadOnlyCollectionHref(existing.collectionHref)) error("Read-only task lists cannot be edited.")
        val targetCollection = payload.collectionHref?.let { database.collectionDao().get(it) }
            ?: database.collectionDao().get(existing.collectionHref)
            ?: error("Task list not found.")
        if (targetCollection.href != existing.collectionHref) {
            val resource = database.resourceDao().get(existing.resourceHref)
            val exSet = existing.exDatesCsv
                ?.split(',')
                ?.mapNotNull { it.trim().toLongOrNull() }
                ?.toMutableSet()
                ?: mutableSetOf()
            exSet += occurrenceStartMillis
            val updatedMaster = existing.copy(
                exDatesCsv = exSet.sorted().joinToString(","),
                sequence = existing.sequence + 1,
            ).withValidIcalSchedule()
            val raw = icalCodec.serializeTask(updatedMaster, resource?.rawIcs)
            localWrites.upsertLocalResource(updatedMaster.collectionHref, updatedMaster.resourceHref, resource?.etag, ComponentType.Task, updatedMaster.uid, raw)
            localWrites.enqueuePut(updatedMaster.collectionHref, updatedMaster.resourceHref, ComponentType.Task, raw, resource?.etag)
            database.taskDao().upsert(updatedMaster)
            createTask(payload.copy(recurrenceRule = null))
            return@writeTransaction
        }
        val resource = database.resourceDao().get(existing.resourceHref)
        val exDates = existing.exDatesCsv
            ?.split(',')
            ?.mapNotNull { it.trim().toLongOrNull() }
            ?.filterNot { it == occurrenceStartMillis }
        val replacement = existing.copy(
            title = payload.title.ifBlank { "Untitled task" },
            notes = payload.notes?.ifBlank { null },
            location = payload.location?.ifBlank { null },
            url = payload.url?.ifBlank { null },
            categories = payload.categories?.ifBlank { null },
            dueAtMillis = payload.dueDate.toTaskMillis(payload.dueTime, payload.dueHasTime, LocalTime.of(17, 0)),
            dueHasTime = payload.dueDate != null && payload.dueHasTime,
            startAtMillis = payload.startDate.toTaskMillis(payload.startTime, payload.startHasTime, LocalTime.of(9, 0)),
            startHasTime = payload.startDate != null && payload.startHasTime,
            completedAtMillis = if (payload.isCompleted) existing.completedAtMillis ?: Instant.now().toEpochMilli() else null,
            isCompleted = payload.isCompleted,
            status = payload.status?.takeIf { it.isNotBlank() }
                ?: if (payload.isCompleted) "COMPLETED" else "NEEDS-ACTION",
            priority = payload.priority?.coerceIn(1, 9),
            percentComplete = payload.percentComplete?.coerceIn(0, 100),
            recurrenceRule = null,
            exDatesCsv = null,
            rDatesCsv = null,
            recurrenceOverridesJson = null,
            remindersCsv = payload.reminderMinutes.normalizedReminderOffsets().takeIf { it.isNotEmpty() }?.joinToString(","),
            timezoneId = if (
                (payload.startDate != null && payload.startHasTime) ||
                (payload.dueDate != null && payload.dueHasTime)
            ) existing.timezoneId ?: zoneId.id else null,
        ).withValidIcalSchedule()
        val updatedMaster = existing.copy(
            exDatesCsv = exDates?.takeIf { it.isNotEmpty() }?.joinToString(","),
            recurrenceOverridesJson = RecurrenceOverrideCodec.upsertTask(
                existing.recurrenceOverridesJson,
                TaskRecurrenceOverride.fromTask(occurrenceStartMillis, replacement),
            ),
            sequence = existing.sequence + 1,
        ).withValidIcalSchedule()
        val raw = icalCodec.serializeTask(updatedMaster, resource?.rawIcs)
        localWrites.upsertLocalResource(updatedMaster.collectionHref, updatedMaster.resourceHref, resource?.etag, ComponentType.Task, updatedMaster.uid, raw)
        localWrites.enqueuePut(updatedMaster.collectionHref, updatedMaster.resourceHref, ComponentType.Task, raw, resource?.etag)
        database.taskDao().upsert(updatedMaster)
    }

    suspend fun updateTaskFollowing(uid: String, occurrenceStartMillis: Long, payload: TaskEditPayload): Unit = localWrites.writeTransaction {
        val existing = database.taskDao().get(uid) ?: return@writeTransaction
        if (existing.recurrenceRule.isNullOrBlank()) {
            updateTask(uid, payload)
            return@writeTransaction
        }
        val masterStart = existing.startAtMillis ?: existing.dueAtMillis ?: Long.MAX_VALUE
        if (occurrenceStartMillis <= masterStart) {
            updateTask(uid, payload)
            return@writeTransaction
        }
        if (localWrites.isReadOnlyCollectionHref(existing.collectionHref)) error("Read-only task lists cannot be edited.")
        val resource = database.resourceDao().get(existing.resourceHref)
        val newRule = existing.recurrenceRule.withRecurrenceUntilBefore(occurrenceStartMillis, existing.startHasTime.not() && existing.dueHasTime.not(), zoneId)
        val keptEx = existing.exDatesCsv
            ?.split(',')
            ?.mapNotNull { it.trim().toLongOrNull() }
            ?.filter { it < occurrenceStartMillis }
        val keptRDates = existing.rDatesCsv
            ?.split(',')
            ?.mapNotNull { it.trim().toLongOrNull() }
            ?.filter { it < occurrenceStartMillis }
        val updatedMaster = existing.copy(
            recurrenceRule = newRule,
            exDatesCsv = keptEx?.takeIf { it.isNotEmpty() }?.joinToString(","),
            rDatesCsv = keptRDates?.takeIf { it.isNotEmpty() }?.joinToString(","),
            recurrenceOverridesJson = RecurrenceOverrideCodec.encodeTasks(
                RecurrenceOverrideCodec.decodeTasks(existing.recurrenceOverridesJson)
                    .filter { it.recurrenceIdMillis < occurrenceStartMillis },
            ),
            sequence = existing.sequence + 1,
        ).withValidIcalSchedule()
        val raw = icalCodec.serializeTask(updatedMaster, resource?.rawIcs)
        localWrites.upsertLocalResource(updatedMaster.collectionHref, updatedMaster.resourceHref, resource?.etag, ComponentType.Task, updatedMaster.uid, raw)
        localWrites.enqueuePut(updatedMaster.collectionHref, updatedMaster.resourceHref, ComponentType.Task, raw, resource?.etag)
        database.taskDao().upsert(updatedMaster)
        createTask(payload)
    }

    suspend fun setTaskCompleted(resourceHref: String, completed: Boolean) {
        setTaskStatus(resourceHref, if (completed) "COMPLETED" else "NEEDS-ACTION")
    }

    /**
     * Sets the iCal STATUS (NEEDS-ACTION / IN-PROCESS / COMPLETED / CANCELLED) of a
     * task and keeps the derived [TaskEntity.isCompleted] / completedAtMillis fields
     * in sync. COMPLETED is the only status that counts as "done".
     */
    suspend fun setTaskStatus(resourceHref: String, status: String): Unit = localWrites.writeTransaction {
        val existing = database.taskDao().byResource(resourceHref) ?: return@writeTransaction
        if (localWrites.isReadOnlyCollectionHref(existing.collectionHref)) return@writeTransaction
        val resource = database.resourceDao().get(existing.resourceHref)
        val completed = status.equals("COMPLETED", ignoreCase = true)
        val updated = existing.copy(
            isCompleted = completed,
            completedAtMillis = when {
                completed && existing.completedAtMillis != null -> existing.completedAtMillis
                completed -> Instant.now().toEpochMilli()
                else -> null
            },
            status = status.uppercase(),
            sequence = existing.sequence + 1,
        ).withValidIcalSchedule()
        val raw = icalCodec.serializeTask(updated, resource?.rawIcs)
        localWrites.upsertLocalResource(updated.collectionHref, updated.resourceHref, resource?.etag, ComponentType.Task, updated.uid, raw)
        database.taskDao().upsert(updated)
        localWrites.enqueuePut(updated.collectionHref, updated.resourceHref, ComponentType.Task, raw, resource?.etag)
    }

    /** Updates one generated occurrence without changing the recurring task master. */
    suspend fun setTaskOccurrenceStatus(resourceHref: String, occurrenceStartMillis: Long, status: String): Unit = localWrites.writeTransaction {
        val existing = database.taskDao().byResource(resourceHref) ?: return@writeTransaction
        if (existing.recurrenceRule.isNullOrBlank() && existing.rDatesCsv.isNullOrBlank()) {
            setTaskStatus(resourceHref, status)
            return@writeTransaction
        }
        if (localWrites.isReadOnlyCollectionHref(existing.collectionHref)) return@writeTransaction

        val recurrenceAnchor = existing.startAtMillis ?: existing.dueAtMillis ?: return@writeTransaction
        val shift = occurrenceStartMillis - recurrenceAnchor
        val generatedOccurrence = existing.copy(
            startAtMillis = existing.startAtMillis?.plus(shift),
            dueAtMillis = existing.dueAtMillis?.plus(shift),
        )
        val occurrence = RecurrenceOverrideCodec.decodeTasks(existing.recurrenceOverridesJson)
            .firstOrNull { it.recurrenceIdMillis == occurrenceStartMillis }
            ?.applyTo(generatedOccurrence)
            ?: generatedOccurrence
        val completed = status.equals("COMPLETED", ignoreCase = true)
        val updatedOccurrence = occurrence.copy(
            isCompleted = completed,
            completedAtMillis = when {
                completed && occurrence.completedAtMillis != null -> occurrence.completedAtMillis
                completed -> Instant.now().toEpochMilli()
                else -> null
            },
            status = status.uppercase(),
            recurrenceRule = null,
            exDatesCsv = null,
            rDatesCsv = null,
            recurrenceOverridesJson = null,
        ).withValidIcalSchedule()
        val resource = database.resourceDao().get(existing.resourceHref)
        val updatedMaster = existing.copy(
            recurrenceOverridesJson = RecurrenceOverrideCodec.upsertTask(
                existing.recurrenceOverridesJson,
                TaskRecurrenceOverride.fromTask(occurrenceStartMillis, updatedOccurrence),
            ),
            sequence = existing.sequence + 1,
        ).withValidIcalSchedule()
        val raw = icalCodec.serializeTask(updatedMaster, resource?.rawIcs)
        localWrites.upsertLocalResource(updatedMaster.collectionHref, updatedMaster.resourceHref, resource?.etag, ComponentType.Task, updatedMaster.uid, raw)
        database.taskDao().upsert(updatedMaster)
        localWrites.enqueuePut(updatedMaster.collectionHref, updatedMaster.resourceHref, ComponentType.Task, raw, resource?.etag)
    }

    suspend fun setTaskPriority(uid: String, priority: Int): Unit = localWrites.writeTransaction {
        val existing = database.taskDao().get(uid) ?: return@writeTransaction
        if (localWrites.isReadOnlyCollectionHref(existing.collectionHref)) return@writeTransaction
        val resource = database.resourceDao().get(existing.resourceHref)
        val updated = existing.copy(
            priority = priority.coerceIn(1, 9),
            sequence = existing.sequence + 1,
        ).withValidIcalSchedule()
        val raw = icalCodec.serializeTask(updated, resource?.rawIcs)
        localWrites.upsertLocalResource(updated.collectionHref, updated.resourceHref, resource?.etag, ComponentType.Task, updated.uid, raw)
        database.taskDao().upsert(updated)
        localWrites.enqueuePut(updated.collectionHref, updated.resourceHref, ComponentType.Task, raw, resource?.etag)
    }

    suspend fun setTaskProgress(uid: String, progress: Int): Unit = localWrites.writeTransaction {
        val existing = database.taskDao().get(uid) ?: return@writeTransaction
        if (localWrites.isReadOnlyCollectionHref(existing.collectionHref)) return@writeTransaction
        val resource = database.resourceDao().get(existing.resourceHref)
        val updated = existing.copy(
            percentComplete = progress.coerceIn(0, 100),
            sequence = existing.sequence + 1,
        ).withValidIcalSchedule()
        val raw = icalCodec.serializeTask(updated, resource?.rawIcs)
        localWrites.upsertLocalResource(updated.collectionHref, updated.resourceHref, resource?.etag, ComponentType.Task, updated.uid, raw)
        database.taskDao().upsert(updated)
        localWrites.enqueuePut(updated.collectionHref, updated.resourceHref, ComponentType.Task, raw, resource?.etag)
    }

    suspend fun moveTimedTask(uid: String, date: LocalDate, startTime: LocalTime, endTime: LocalTime) {
        val existing = database.taskDao().get(uid) ?: return
        moveTimedTask(uid, existing.startAtMillis ?: existing.dueAtMillis ?: System.currentTimeMillis(), date, startTime, endTime)
    }

    suspend fun moveTimedTask(uid: String, occurrenceStartMillis: Long, date: LocalDate, startTime: LocalTime, endTime: LocalTime): Unit = localWrites.writeTransaction {
        val existing = database.taskDao().get(uid) ?: return@writeTransaction
        val payload = TaskEditPayload(
            title = existing.title,
            collectionHref = existing.collectionHref,
            notes = existing.notes,
            location = existing.location,
            locationMapVerified = existing.locationMapVerified,
            manualColor = existing.manualColor,
            url = existing.url,
            categories = existing.categories,
            startDate = date,
            startTime = startTime,
            startHasTime = true,
            dueDate = date,
            dueTime = endTime,
            dueHasTime = true,
            priority = existing.priority,
            percentComplete = existing.percentComplete,
            isCompleted = existing.isCompleted,
            recurrenceRule = if (existing.recurrenceRule.isNullOrBlank()) existing.recurrenceRule else null,
            parentUid = existing.parentUid,
            status = existing.status,
            reminderMinutes = existing.remindersCsv.toMinutesList(),
        )
        if (existing.recurrenceRule.isNullOrBlank()) {
            updateTask(uid, payload)
        } else {
            updateTaskOccurrence(uid, occurrenceStartMillis, payload)
        }
    }

    suspend fun moveAllDayTask(uid: String, occurrenceStartMillis: Long, date: LocalDate): Unit = localWrites.writeTransaction {
        val existing = database.taskDao().get(uid) ?: return@writeTransaction
        val currentStart = existing.startAtMillis?.toDate() ?: existing.dueAtMillis?.toDate() ?: date
        val currentEnd = (existing.dueAtMillis?.toDate() ?: existing.startAtMillis?.toDate() ?: currentStart).coerceAtLeast(currentStart)
        val spanDays = ChronoUnit.DAYS.between(currentStart, currentEnd).coerceAtLeast(0L)
        val payload = TaskEditPayload(
            title = existing.title,
            collectionHref = existing.collectionHref,
            notes = existing.notes,
            location = existing.location,
            locationMapVerified = existing.locationMapVerified,
            manualColor = existing.manualColor,
            url = existing.url,
            categories = existing.categories,
            startDate = date,
            startTime = null,
            startHasTime = false,
            dueDate = date.plusDays(spanDays),
            dueTime = null,
            dueHasTime = false,
            priority = existing.priority,
            percentComplete = existing.percentComplete,
            isCompleted = existing.isCompleted,
            recurrenceRule = if (existing.recurrenceRule.isNullOrBlank()) existing.recurrenceRule else null,
            parentUid = existing.parentUid,
            status = existing.status,
            reminderMinutes = existing.remindersCsv.toMinutesList(),
        )
        if (existing.recurrenceRule.isNullOrBlank()) {
            updateTask(uid, payload)
        } else {
            updateTaskOccurrence(uid, occurrenceStartMillis, payload)
        }
    }

    suspend fun copyTaskTo(uid: String, collectionHref: String): Unit = localWrites.writeTransaction {
        val existing = database.taskDao().get(uid) ?: return@writeTransaction
        val targetCollection = database.collectionDao().get(collectionHref) ?: return@writeTransaction
        if (!targetCollection.supportsTasks || targetCollection.isReadOnlyCollection() || !targetCollection.canCreateResources()) return@writeTransaction
        val newUid = newUid()
        val resourceHref = targetCollection.newResourceHref(newUid)
        val task = existing.copy(
            uid = newUid,
            collectionHref = targetCollection.href,
            resourceHref = resourceHref,
            parentUid = existing.parentUid
                ?.takeIf { database.taskDao().byUidInCollection(targetCollection.href, it) != null },
            color = targetCollection.color,
            syncError = null,
        ).withValidIcalSchedule()
        val raw = icalCodec.serializeTask(task)
        localWrites.upsertLocalResource(task.collectionHref, task.resourceHref, null, ComponentType.Task, task.uid, raw)
        database.taskDao().upsert(task)
        localWrites.enqueuePut(task.collectionHref, task.resourceHref, ComponentType.Task, raw, null)
    }

    suspend fun deleteTask(uid: String): Unit = localWrites.writeTransaction {
        val task = database.taskDao().get(uid) ?: return@writeTransaction
        val collection = database.collectionDao().get(task.collectionHref) ?: return@writeTransaction
        if (collection.isReadOnlyCollection() || !collection.canDeleteResources()) return@writeTransaction
        reparentTaskChildren(task)
        val resource = database.resourceDao().get(task.resourceHref)
        if (task.collectionHref.isLocalCollectionHref()) {
            database.taskDao().deleteByResource(task.resourceHref)
            database.resourceDao().delete(task.resourceHref)
        } else {
            localWrites.enqueueDelete(task.collectionHref, task.resourceHref, ComponentType.Task, resource?.etag)
        }
    }

    private suspend fun validatedParentUid(
        collectionHref: String,
        requestedParentUid: String?,
        taskUid: String,
    ): String? {
        val parentUid = requestedParentUid?.trim()?.takeIf { it.isNotBlank() } ?: return null
        require(parentUid != taskUid) { "A task cannot be its own parent." }
        require(database.taskDao().byUidInCollection(collectionHref, parentUid) != null) {
            "The selected parent task is not in this task list."
        }
        var cursor: String? = parentUid
        val visited = mutableSetOf(taskUid)
        while (cursor != null && visited.add(cursor)) {
            val parent = database.taskDao().byUidInCollection(collectionHref, cursor) ?: break
            cursor = parent.parentUid
        }
        require(cursor == null || cursor !in visited) { "This parent would create a task cycle." }
        return parentUid
    }

    private suspend fun reparentTaskChildren(parent: TaskEntity) {
        database.taskDao().children(parent.collectionHref, parent.uid).forEach { child ->
            val resource = database.resourceDao().get(child.resourceHref)
            val updated = child.copy(
                parentUid = parent.parentUid,
                sequence = child.sequence + 1,
            )
            val raw = icalCodec.serializeTask(updated, resource?.rawIcs)
            localWrites.upsertLocalResource(updated.collectionHref, updated.resourceHref, resource?.etag, ComponentType.Task, updated.uid, raw)
            database.taskDao().upsert(updated)
            if (!updated.collectionHref.isLocalCollectionHref()) {
                localWrites.enqueuePut(updated.collectionHref, updated.resourceHref, ComponentType.Task, raw, resource?.etag)
            }
        }
    }

    private fun LocalDate?.toTaskMillis(time: LocalTime?, hasTime: Boolean, defaultTime: LocalTime): Long? {
        val date = this ?: return null
        val localTime = if (hasTime) time ?: defaultTime else LocalTime.MIDNIGHT
        return date.atTime(localTime).atZone(zoneId).toInstant().toEpochMilli()
    }
}
