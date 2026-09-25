package com.kgs.calendar.data.mutation

import com.kgs.calendar.data.LocalWriteSupport
import com.kgs.calendar.data.canCreateResources
import com.kgs.calendar.data.canDeleteResources
import com.kgs.calendar.data.ical.EventRecurrenceOverride
import com.kgs.calendar.data.ical.IcalCodec
import com.kgs.calendar.data.ical.RecurrenceOverrideCodec
import com.kgs.calendar.data.local.KgsDatabase
import com.kgs.calendar.data.local.entity.CollectionEntity
import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.newResourceHref
import com.kgs.calendar.data.newUid
import com.kgs.calendar.data.provider.AndroidCalendarProviderClient
import com.kgs.calendar.data.provider.AndroidProviderWriteShield
import com.kgs.calendar.data.toMinutesList
import com.kgs.calendar.data.withRecurrenceUntilBefore
import com.kgs.calendar.domain.event.endDateInclusive
import com.kgs.calendar.domain.model.ComponentType
import com.kgs.calendar.domain.model.EventEditPayload
import com.kgs.calendar.domain.model.ParticipantJson
import com.kgs.calendar.domain.model.normalizedReminderOffsets
import com.kgs.calendar.domain.source.isAndroidProviderCollection
import com.kgs.calendar.domain.source.isLocalCollectionHref
import com.kgs.calendar.domain.source.isReadOnlyCollection
import com.kgs.calendar.domain.time.toDate
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Local event edits, routed to the Android calendar provider or queued for CalDAV upload. */
class EventMutations internal constructor(
    private val database: KgsDatabase,
    private val localWrites: LocalWriteSupport,
    private val androidCalendarProviderClient: AndroidCalendarProviderClient,
    private val icalCodec: IcalCodec,
    private val androidWriteShield: AndroidProviderWriteShield,
    private val zoneId: ZoneId,
) {
    private suspend fun writableEventCollectionOrNull(requestedHref: String?): CollectionEntity? =
        requestedHref?.let { database.collectionDao().get(it) }
            ?.takeUnless { it.isReadOnlyCollection() || !it.canCreateResources() }
            ?: database.collectionDao().eventCollections().firstOrNull { !it.isReadOnlyCollection() && it.canCreateResources() }

    suspend fun createEvent(payload: EventEditPayload) {
        val collection = writableEventCollectionOrNull(payload.collectionHref)
            ?: error("No writable event calendar has been synced yet.")
        val uid = newUid()
        val resourceHref = collection.newResourceHref(uid)
        val endDate = payload.endDate ?: payload.date
        val start = if (payload.allDay) {
            payload.date.atStartOfDay(zoneId).toInstant().toEpochMilli()
        } else {
            payload.date.atTime(payload.startTime ?: LocalTime.of(9, 0)).atZone(zoneId).toInstant().toEpochMilli()
        }
        val end = if (payload.allDay) {
            endDate.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        } else {
            endDate.atTime(payload.endTime ?: (payload.startTime ?: LocalTime.of(9, 0)).plusHours(1)).atZone(zoneId).toInstant().toEpochMilli()
        }
        val event = EventEntity(
            uid = uid,
            collectionHref = collection.href,
            resourceHref = resourceHref,
            title = payload.title.ifBlank { "Untitled event" },
            description = payload.description?.ifBlank { null },
            location = payload.location?.ifBlank { null },
            locationMapVerified = payload.location?.takeIf { it.isNotBlank() }?.let { payload.locationMapVerified },
            startsAtMillis = start,
            endsAtMillis = if (end > start) {
                end
            } else if (payload.allDay) {
                payload.date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
            } else {
                start + 60L * 60L * 1000L
            },
            allDay = payload.allDay,
            recurrenceRule = payload.recurrenceRule?.ifBlank { null },
            isRecurring = !payload.recurrenceRule.isNullOrBlank(),
            timezoneId = if (payload.allDay) null else zoneId.id,
            remindersCsv = payload.reminderMinutes.normalizedReminderOffsets().takeIf { it.isNotEmpty() }?.joinToString(","),
            status = payload.status?.ifBlank { null },
            classification = payload.classification?.ifBlank { null },
            transparency = payload.transparency?.ifBlank { null },
            categories = payload.categories?.ifBlank { null },
            organizerJson = payload.organizerJson,
            attendeesJson = payload.attendeesJson,
            color = collection.color,
            manualColor = payload.manualColor,
        ).sanitizedFor(collection)
        if (collection.isAndroidProviderCollection()) {
            val calendarId = collection.androidCalendarId()
            val eventId = androidCalendarProviderClient.insertEvent(calendarId, event)
            val androidEvent = event.copy(
                uid = "android-event-$eventId",
                resourceHref = androidCalendarProviderClient.eventHref(eventId),
            )
            localWrites.writeTransaction {
                localWrites.upsertLocalResource(collection.href, androidEvent.resourceHref, null, ComponentType.Event, androidEvent.uid, "android-provider:$eventId")
                database.eventDao().upsert(androidEvent)
            }
            androidWriteShield.markLocalWrite(androidEvent.resourceHref)
            return
        }
        val raw = icalCodec.serializeEvent(event)
        localWrites.writeTransaction {
            localWrites.upsertLocalResource(collection.href, resourceHref, null, ComponentType.Event, uid, raw)
            database.eventDao().upsert(event)
            localWrites.enqueuePut(collection.href, resourceHref, ComponentType.Event, raw, null)
        }
    }

    suspend fun updateEventManualColor(uid: String, manualColor: Int?): Unit = localWrites.writeTransaction {
        val existing = database.eventDao().get(uid) ?: return@writeTransaction
        database.eventDao().upsert(existing.copy(manualColor = manualColor))
    }

    suspend fun updateEvent(uid: String, payload: EventEditPayload) {
        val existingCollectionHref = database.eventDao().get(uid)?.collectionHref ?: return
        localWrites.localWriteUnit(existingCollectionHref, payload.collectionHref) { updateEventUnit(uid, payload) }
    }

    private suspend fun updateEventUnit(uid: String, payload: EventEditPayload) {
        val existing = database.eventDao().get(uid) ?: return
        val resource = database.resourceDao().get(existing.resourceHref)
        val existingCollection = database.collectionDao().get(existing.collectionHref)
            ?: error("Calendar not found.")
        val targetCollection = payload.collectionHref?.let { database.collectionDao().get(it) }
            ?: database.collectionDao().get(existing.collectionHref)
            ?: error("Calendar not found.")
        if (targetCollection.isReadOnlyCollection()) error("Read-only calendars cannot be edited.")
        val moved = targetCollection.href != existing.collectionHref
        val targetIsAndroid = targetCollection.isAndroidProviderCollection()
        val existingIsAndroid = existingCollection.isAndroidProviderCollection()
        val resourceHref = when {
            targetIsAndroid -> existing.resourceHref
            moved -> targetCollection.newResourceHref(existing.uid)
            else -> existing.resourceHref
        }
        val endDate = payload.endDate ?: payload.date
        val start = if (payload.allDay) {
            payload.date.atStartOfDay(zoneId).toInstant().toEpochMilli()
        } else {
            payload.date.atTime(payload.startTime ?: LocalTime.of(9, 0)).atZone(zoneId).toInstant().toEpochMilli()
        }
        val end = if (payload.allDay) {
            endDate.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        } else {
            endDate.atTime(payload.endTime ?: (payload.startTime ?: LocalTime.of(9, 0)).plusHours(1)).atZone(zoneId).toInstant().toEpochMilli()
        }
        val updated = existing.copy(
            collectionHref = targetCollection.href,
            resourceHref = resourceHref,
            title = payload.title.ifBlank { "Untitled event" },
            description = payload.description?.ifBlank { null },
            location = payload.location?.ifBlank { null },
            locationMapVerified = payload.location?.takeIf { it.isNotBlank() }?.let { payload.locationMapVerified },
            startsAtMillis = start,
            endsAtMillis = if (end > start) {
                end
            } else if (payload.allDay) {
                payload.date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
            } else {
                start + 60L * 60L * 1000L
            },
            allDay = payload.allDay,
            recurrenceRule = payload.recurrenceRule?.ifBlank { null },
            isRecurring = !payload.recurrenceRule.isNullOrBlank() || !existing.rDatesCsv.isNullOrBlank(),
            timezoneId = if (payload.allDay) null else existing.timezoneId ?: zoneId.id,
            remindersCsv = payload.reminderMinutes.normalizedReminderOffsets().takeIf { it.isNotEmpty() }?.joinToString(","),
            status = payload.status?.ifBlank { null },
            classification = payload.classification?.ifBlank { null },
            transparency = payload.transparency?.ifBlank { null },
            categories = payload.categories?.ifBlank { null },
            organizerJson = payload.organizerJson,
            attendeesJson = payload.attendeesJson,
            color = targetCollection.color,
            manualColor = payload.manualColor,
            sequence = existing.sequence + 1,
        ).sanitizedFor(targetCollection)
        if (existingIsAndroid && targetIsAndroid) {
            val eventId = androidCalendarProviderClient.eventIdFromHref(existing.resourceHref)
                ?: error("Android event id is missing.")
            val androidUpdated = updated.copy(resourceHref = existing.resourceHref, uid = existing.uid)
            androidCalendarProviderClient.updateEvent(eventId, targetCollection.androidCalendarId(), androidUpdated)
            localWrites.writeTransaction {
                localWrites.upsertLocalResource(androidUpdated.collectionHref, androidUpdated.resourceHref, null, ComponentType.Event, androidUpdated.uid, "android-provider:$eventId")
                database.eventDao().upsert(androidUpdated)
            }
            androidWriteShield.markLocalWrite(androidUpdated.resourceHref)
            return
        }
        if (!existingIsAndroid && targetIsAndroid) {
            val eventId = androidCalendarProviderClient.insertEvent(targetCollection.androidCalendarId(), updated)
            val androidEvent = updated.copy(
                uid = "android-event-$eventId",
                resourceHref = androidCalendarProviderClient.eventHref(eventId),
            )
            localWrites.writeTransaction {
                localWrites.upsertLocalResource(androidEvent.collectionHref, androidEvent.resourceHref, null, ComponentType.Event, androidEvent.uid, "android-provider:$eventId")
                database.eventDao().upsert(androidEvent)
                if (!existing.collectionHref.isLocalCollectionHref()) {
                    localWrites.enqueueDelete(existing.collectionHref, existing.resourceHref, ComponentType.Event, resource?.etag)
                }
                database.eventDao().deleteByResource(existing.resourceHref)
                database.resourceDao().delete(existing.resourceHref)
            }
            androidWriteShield.markLocalWrite(androidEvent.resourceHref)
            return
        }
        if (existingIsAndroid && !targetIsAndroid) {
            val eventId = androidCalendarProviderClient.eventIdFromHref(existing.resourceHref)
                ?: error("Android event id is missing.")
            androidCalendarProviderClient.deleteEvent(eventId)
        }
        val raw = icalCodec.serializeEvent(updated, originalRawIcs = resource?.rawIcs.takeUnless { moved })
        localWrites.writeTransaction {
            if (moved) {
                database.eventDao().deleteByResource(existing.resourceHref)
                database.resourceDao().delete(existing.resourceHref)
                localWrites.enqueueDelete(existing.collectionHref, existing.resourceHref, ComponentType.Event, resource?.etag)
                localWrites.upsertLocalResource(updated.collectionHref, updated.resourceHref, null, ComponentType.Event, updated.uid, raw)
                localWrites.enqueuePut(updated.collectionHref, updated.resourceHref, ComponentType.Event, raw, null)
            } else {
                localWrites.upsertLocalResource(updated.collectionHref, updated.resourceHref, resource?.etag, ComponentType.Event, updated.uid, raw)
                localWrites.enqueuePut(updated.collectionHref, updated.resourceHref, ComponentType.Event, raw, resource?.etag)
            }
            database.eventDao().upsert(updated)
        }
        if (existingIsAndroid) androidWriteShield.markLocalDelete(existing.resourceHref)
    }

    suspend fun updateEventOccurrence(uid: String, occurrenceStartMillis: Long, payload: EventEditPayload) {
        val existingCollectionHref = database.eventDao().get(uid)?.collectionHref ?: return
        localWrites.localWriteUnit(
            existingCollectionHref,
            payload.collectionHref,
            writableEventCollectionOrNull(payload.collectionHref)?.href,
        ) { updateEventOccurrenceUnit(uid, occurrenceStartMillis, payload) }
    }

    private suspend fun updateEventOccurrenceUnit(uid: String, occurrenceStartMillis: Long, payload: EventEditPayload) {
        val existing = database.eventDao().get(uid) ?: return
        if (existing.recurrenceRule.isNullOrBlank()) {
            updateEvent(uid, payload)
            return
        }
        if (localWrites.isReadOnlyCollectionHref(existing.collectionHref)) error("Read-only calendars cannot be edited.")
        val targetCollection = payload.collectionHref?.let { database.collectionDao().get(it) }
            ?: database.collectionDao().get(existing.collectionHref)
            ?: error("Calendar not found.")
        if (targetCollection.href != existing.collectionHref || targetCollection.isAndroidProviderCollection()) {
            deleteEventOccurrence(uid, occurrenceStartMillis)
            createEvent(payload.copy(recurrenceRule = null))
            return
        }
        val resource = database.resourceDao().get(existing.resourceHref)
        val endDate = payload.endDate ?: payload.date
        val start = if (payload.allDay) {
            payload.date.atStartOfDay(zoneId).toInstant().toEpochMilli()
        } else {
            payload.date.atTime(payload.startTime ?: LocalTime.of(9, 0)).atZone(zoneId).toInstant().toEpochMilli()
        }
        val end = if (payload.allDay) {
            endDate.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        } else {
            endDate.atTime(payload.endTime ?: (payload.startTime ?: LocalTime.of(9, 0)).plusHours(1))
                .atZone(zoneId).toInstant().toEpochMilli()
        }
        val replacement = existing.copy(
            title = payload.title.ifBlank { "Untitled event" },
            description = payload.description?.ifBlank { null },
            location = payload.location?.ifBlank { null },
            startsAtMillis = start,
            endsAtMillis = if (end > start) end else start + HOUR_MILLIS,
            allDay = payload.allDay,
            recurrenceRule = null,
            isRecurring = false,
            remindersCsv = payload.reminderMinutes.normalizedReminderOffsets().takeIf { it.isNotEmpty() }?.joinToString(","),
            status = payload.status?.ifBlank { null },
            classification = payload.classification?.ifBlank { null },
            transparency = payload.transparency?.ifBlank { null },
            categories = payload.categories?.ifBlank { null },
            organizerJson = payload.organizerJson,
            attendeesJson = payload.attendeesJson,
            timezoneId = if (payload.allDay) null else existing.timezoneId ?: zoneId.id,
            manualColor = payload.manualColor,
        )
        val exDates = existing.exDatesCsv
            ?.split(',')
            ?.mapNotNull { it.trim().toLongOrNull() }
            ?.filterNot { it == occurrenceStartMillis }
        val updated = existing.copy(
            exDatesCsv = exDates?.takeIf { it.isNotEmpty() }?.joinToString(","),
            recurrenceOverridesJson = RecurrenceOverrideCodec.upsertEvent(
                existing.recurrenceOverridesJson,
                EventRecurrenceOverride.fromEvent(occurrenceStartMillis, replacement),
            ),
            sequence = existing.sequence + 1,
        )
        val raw = icalCodec.serializeEvent(updated, resource?.rawIcs)
        localWrites.writeTransaction {
            localWrites.upsertLocalResource(updated.collectionHref, updated.resourceHref, resource?.etag, ComponentType.Event, updated.uid, raw)
            database.eventDao().upsert(updated)
            localWrites.enqueuePut(updated.collectionHref, updated.resourceHref, ComponentType.Event, raw, resource?.etag)
        }
    }

    suspend fun updateEventFollowing(uid: String, occurrenceStartMillis: Long, payload: EventEditPayload) {
        val existingCollectionHref = database.eventDao().get(uid)?.collectionHref ?: return
        localWrites.localWriteUnit(
            existingCollectionHref,
            payload.collectionHref,
            writableEventCollectionOrNull(payload.collectionHref)?.href,
        ) {
            val existing = database.eventDao().get(uid) ?: return@localWriteUnit
            if (existing.recurrenceRule.isNullOrBlank() || occurrenceStartMillis <= existing.startsAtMillis) {
                updateEvent(uid, payload)
                return@localWriteUnit
            }
            deleteEventFollowing(uid, occurrenceStartMillis)
            createEvent(payload)
        }
    }

    suspend fun moveTimedEvent(uid: String, date: LocalDate, startTime: LocalTime, endTime: LocalTime) {
        val existing = database.eventDao().get(uid) ?: return
        moveTimedEvent(uid, existing.startsAtMillis, date, startTime, endTime)
    }

    suspend fun moveTimedEvent(uid: String, occurrenceStartMillis: Long, date: LocalDate, startTime: LocalTime, endTime: LocalTime) {
        val existing = database.eventDao().get(uid) ?: return
        val payload = EventEditPayload(
            title = existing.title,
            collectionHref = existing.collectionHref,
            date = date,
            endDate = date,
            startTime = startTime,
            endTime = endTime,
            allDay = false,
            description = existing.description,
            location = existing.location,
            locationMapVerified = existing.locationMapVerified,
            manualColor = existing.manualColor,
            recurrenceRule = if (existing.recurrenceRule.isNullOrBlank()) existing.recurrenceRule else null,
            reminderMinutes = existing.remindersCsv.toMinutesList(),
            status = existing.status,
            classification = existing.classification,
            transparency = existing.transparency,
            categories = existing.categories,
            organizerJson = existing.organizerJson,
            attendeesJson = existing.attendeesJson,
        )
        if (existing.recurrenceRule.isNullOrBlank()) {
            updateEvent(uid, payload)
        } else {
            updateEventOccurrence(uid, occurrenceStartMillis, payload)
        }
    }

    suspend fun moveAllDayEvent(uid: String, occurrenceStartMillis: Long, date: LocalDate) {
        val existing = database.eventDao().get(uid) ?: return
        val currentStart = existing.startsAtMillis.toDate()
        val currentEnd = existing.endDateInclusive().coerceAtLeast(currentStart)
        val spanDays = ChronoUnit.DAYS.between(currentStart, currentEnd).coerceAtLeast(0L)
        val payload = EventEditPayload(
            title = existing.title,
            collectionHref = existing.collectionHref,
            date = date,
            endDate = date.plusDays(spanDays),
            startTime = null,
            endTime = null,
            allDay = true,
            description = existing.description,
            location = existing.location,
            locationMapVerified = existing.locationMapVerified,
            manualColor = existing.manualColor,
            recurrenceRule = if (existing.recurrenceRule.isNullOrBlank()) existing.recurrenceRule else null,
            reminderMinutes = existing.remindersCsv.toMinutesList(),
            status = existing.status,
            classification = existing.classification,
            transparency = existing.transparency,
            categories = existing.categories,
            organizerJson = existing.organizerJson,
            attendeesJson = existing.attendeesJson,
        )
        if (existing.recurrenceRule.isNullOrBlank()) {
            updateEvent(uid, payload)
        } else {
            updateEventOccurrence(uid, occurrenceStartMillis, payload)
        }
    }

    suspend fun setEventParticipation(uid: String, attendeeEmails: List<String>, partstat: String): Unit = localWrites.writeTransaction {
        val existing = database.eventDao().get(uid) ?: return@writeTransaction
        if (localWrites.isReadOnlyCollectionHref(existing.collectionHref) || localWrites.isAndroidProviderCollectionHref(existing.collectionHref)) return@writeTransaction
        val resource = database.resourceDao().get(existing.resourceHref)
        val updatedAttendees = ParticipantJson.withPartstat(existing.attendeesJson, attendeeEmails, partstat)
            ?: return@writeTransaction
        val updated = existing.copy(attendeesJson = updatedAttendees, sequence = existing.sequence + 1)
        val raw = icalCodec.serializeEvent(updated, resource?.rawIcs)
        localWrites.upsertLocalResource(updated.collectionHref, updated.resourceHref, resource?.etag, ComponentType.Event, updated.uid, raw)
        database.eventDao().upsert(updated)
        localWrites.enqueuePut(updated.collectionHref, updated.resourceHref, ComponentType.Event, raw, resource?.etag)
    }

    suspend fun copyEventTo(uid: String, collectionHref: String) {
        val existing = database.eventDao().get(uid) ?: return
        val targetCollection = database.collectionDao().get(collectionHref) ?: return
        if (!targetCollection.supportsEvents || targetCollection.isReadOnlyCollection() || !targetCollection.canCreateResources()) return
        val newUid = newUid()
        val resourceHref = targetCollection.newResourceHref(newUid)
        val event = existing.copy(
            uid = newUid,
            collectionHref = targetCollection.href,
            resourceHref = resourceHref,
            color = targetCollection.color,
            syncError = null,
        ).sanitizedFor(targetCollection)
        if (targetCollection.isAndroidProviderCollection()) {
            val eventId = androidCalendarProviderClient.insertEvent(targetCollection.androidCalendarId(), event)
            val androidEvent = event.copy(
                uid = "android-event-$eventId",
                resourceHref = androidCalendarProviderClient.eventHref(eventId),
            )
            localWrites.writeTransaction {
                localWrites.upsertLocalResource(androidEvent.collectionHref, androidEvent.resourceHref, null, ComponentType.Event, androidEvent.uid, "android-provider:$eventId")
                database.eventDao().upsert(androidEvent)
            }
            androidWriteShield.markLocalWrite(androidEvent.resourceHref)
            return
        }
        val raw = icalCodec.serializeEvent(event)
        localWrites.writeTransaction {
            localWrites.upsertLocalResource(event.collectionHref, event.resourceHref, null, ComponentType.Event, event.uid, raw)
            database.eventDao().upsert(event)
            localWrites.enqueuePut(event.collectionHref, event.resourceHref, ComponentType.Event, raw, null)
        }
    }

    suspend fun deleteEvent(uid: String) {
        val collectionHref = database.eventDao().get(uid)?.collectionHref ?: return
        localWrites.localWriteUnit(collectionHref) { deleteEventUnit(uid) }
    }

    private suspend fun deleteEventUnit(uid: String) {
        val event = database.eventDao().get(uid) ?: return
        val collection = database.collectionDao().get(event.collectionHref) ?: return
        if (collection.isReadOnlyCollection() || !collection.canDeleteResources()) return
        if (localWrites.isAndroidProviderCollectionHref(event.collectionHref)) {
            val eventId = androidCalendarProviderClient.eventIdFromHref(event.resourceHref) ?: return
            androidCalendarProviderClient.deleteEvent(eventId)
            localWrites.writeTransaction {
                database.eventDao().deleteByResource(event.resourceHref)
                database.resourceDao().delete(event.resourceHref)
            }
            androidWriteShield.markLocalDelete(event.resourceHref)
            return
        }
        val resource = database.resourceDao().get(event.resourceHref)
        if (event.collectionHref.isLocalCollectionHref()) {
            database.eventDao().deleteByResource(event.resourceHref)
            database.resourceDao().delete(event.resourceHref)
        } else {
            localWrites.enqueueDelete(event.collectionHref, event.resourceHref, ComponentType.Event, resource?.etag)
        }
    }

    /**
     * Deletes a single occurrence of a recurring event by adding its start to the master's
     * EXDATE set. The rest of the series is untouched. Falls back to deleting the whole event
     * when it isn't actually recurring.
     */
    suspend fun deleteEventOccurrence(uid: String, occurrenceStartMillis: Long) {
        val collectionHref = database.eventDao().get(uid)?.collectionHref ?: return
        localWrites.localWriteUnit(collectionHref) { deleteEventOccurrenceUnit(uid, occurrenceStartMillis) }
    }

    private suspend fun deleteEventOccurrenceUnit(uid: String, occurrenceStartMillis: Long) {
        val existing = database.eventDao().get(uid) ?: return
        if (existing.recurrenceRule.isNullOrBlank()) {
            deleteEvent(uid)
            return
        }
        if (localWrites.isReadOnlyCollectionHref(existing.collectionHref)) error("Read-only calendars cannot be edited.")
        val resource = database.resourceDao().get(existing.resourceHref)
        val exSet = existing.exDatesCsv
            ?.split(',')
            ?.mapNotNull { it.trim().toLongOrNull() }
            ?.toMutableSet()
            ?: mutableSetOf()
        exSet += occurrenceStartMillis
        val updated = existing.copy(
            exDatesCsv = exSet.sorted().joinToString(","),
            sequence = existing.sequence + 1,
        )
        if (localWrites.isAndroidProviderCollectionHref(existing.collectionHref)) {
            cancelAndroidEventOccurrence(updated, occurrenceStartMillis)
            return
        }
        val raw = icalCodec.serializeEvent(updated, resource?.rawIcs)
        localWrites.upsertLocalResource(updated.collectionHref, updated.resourceHref, resource?.etag, ComponentType.Event, updated.uid, raw)
        localWrites.enqueuePut(updated.collectionHref, updated.resourceHref, ComponentType.Event, raw, resource?.etag)
        database.eventDao().upsert(updated)
    }

    /**
     * Deletes the given occurrence and every later one by capping the RRULE with an UNTIL just
     * before this occurrence (and stripping any COUNT). When the cut lands on or before the very
     * first occurrence, the whole event is removed instead.
     */
    suspend fun deleteEventFollowing(uid: String, occurrenceStartMillis: Long) {
        val collectionHref = database.eventDao().get(uid)?.collectionHref ?: return
        localWrites.localWriteUnit(collectionHref) { deleteEventFollowingUnit(uid, occurrenceStartMillis) }
    }

    private suspend fun deleteEventFollowingUnit(uid: String, occurrenceStartMillis: Long) {
        val existing = database.eventDao().get(uid) ?: return
        if (existing.recurrenceRule.isNullOrBlank() || occurrenceStartMillis <= existing.startsAtMillis) {
            deleteEvent(uid)
            return
        }
        if (localWrites.isReadOnlyCollectionHref(existing.collectionHref)) error("Read-only calendars cannot be edited.")
        val resource = database.resourceDao().get(existing.resourceHref)
        val newRule = existing.recurrenceRule.withRecurrenceUntilBefore(occurrenceStartMillis, existing.allDay, zoneId)
        val keptEx = existing.exDatesCsv
            ?.split(',')
            ?.mapNotNull { it.trim().toLongOrNull() }
            ?.filter { it < occurrenceStartMillis }
        val keptRDates = existing.rDatesCsv
            ?.split(',')
            ?.mapNotNull { it.trim().toLongOrNull() }
            ?.filter { it < occurrenceStartMillis }
        val updated = existing.copy(
            recurrenceRule = newRule,
            isRecurring = !newRule.isNullOrBlank() || !keptRDates.isNullOrEmpty(),
            exDatesCsv = keptEx?.takeIf { it.isNotEmpty() }?.joinToString(","),
            rDatesCsv = keptRDates?.takeIf { it.isNotEmpty() }?.joinToString(","),
            recurrenceOverridesJson = RecurrenceOverrideCodec.encodeEvents(
                RecurrenceOverrideCodec.decodeEvents(existing.recurrenceOverridesJson)
                    .filter { it.recurrenceIdMillis < occurrenceStartMillis },
            ),
            sequence = existing.sequence + 1,
        )
        if (localWrites.isAndroidProviderCollectionHref(existing.collectionHref)) {
            persistAndroidEventUpdate(updated)
            return
        }
        val raw = icalCodec.serializeEvent(updated, resource?.rawIcs)
        localWrites.upsertLocalResource(updated.collectionHref, updated.resourceHref, resource?.etag, ComponentType.Event, updated.uid, raw)
        localWrites.enqueuePut(updated.collectionHref, updated.resourceHref, ComponentType.Event, raw, resource?.etag)
        database.eventDao().upsert(updated)
    }

    private fun CollectionEntity.androidCalendarId(): Long =
        externalId?.toLongOrNull()
            ?: androidCalendarProviderClient.calendarIdFromHref(href)
            ?: error("Android calendar id is missing.")

    private fun EventEntity.sanitizedFor(collection: CollectionEntity): EventEntity =
        if (!collection.isAndroidProviderCollection()) {
            this
        } else {
            copy(
                categories = null,
                organizerJson = null,
                attendeesJson = null,
            )
        }

    private suspend fun persistAndroidEventUpdate(event: EventEntity) {
        val collection = database.collectionDao().get(event.collectionHref)
            ?: error("Android calendar not found.")
        val eventId = androidCalendarProviderClient.eventIdFromHref(event.resourceHref)
            ?: error("Android event id is missing.")
        val sanitized = event.sanitizedFor(collection)
        androidCalendarProviderClient.updateEvent(eventId, collection.androidCalendarId(), sanitized)
        localWrites.writeTransaction {
            localWrites.upsertLocalResource(sanitized.collectionHref, sanitized.resourceHref, null, ComponentType.Event, sanitized.uid, "android-provider:$eventId")
            database.eventDao().upsert(sanitized)
        }
        androidWriteShield.markLocalWrite(sanitized.resourceHref)
    }

    private suspend fun cancelAndroidEventOccurrence(eventWithExDate: EventEntity, occurrenceStartMillis: Long) {
        val collection = database.collectionDao().get(eventWithExDate.collectionHref)
            ?: error("Android calendar not found.")
        val eventId = androidCalendarProviderClient.eventIdFromHref(eventWithExDate.resourceHref)
            ?: error("Android event id is missing.")
        val durationMillis = (eventWithExDate.endsAtMillis - eventWithExDate.startsAtMillis)
            .coerceAtLeast(60L * 60L * 1000L)
        androidCalendarProviderClient.cancelRecurringInstance(
            eventId = eventId,
            calendarId = collection.androidCalendarId(),
            occurrenceStartMillis = occurrenceStartMillis,
            occurrenceEndMillis = occurrenceStartMillis + durationMillis,
            allDay = eventWithExDate.allDay,
        )
        val sanitized = eventWithExDate.sanitizedFor(collection)
        localWrites.writeTransaction {
            localWrites.upsertLocalResource(sanitized.collectionHref, sanitized.resourceHref, null, ComponentType.Event, sanitized.uid, "android-provider:$eventId")
            database.eventDao().upsert(sanitized)
        }
        androidWriteShield.markLocalWrite(sanitized.resourceHref)
    }

    private companion object {
        const val HOUR_MILLIS = 60L * 60L * 1000L
    }
}
