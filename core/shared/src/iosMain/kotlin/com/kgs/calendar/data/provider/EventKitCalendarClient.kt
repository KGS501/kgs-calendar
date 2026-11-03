package com.kgs.calendar.data.provider

import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.recurrence.RecurrenceExpander
import com.kgs.calendar.domain.model.EventStatus
import com.kgs.calendar.domain.model.EventTransparency
import com.kgs.calendar.json.JSONObject
import com.kgs.calendar.platform.KgsLock
import com.kgs.calendar.platform.toByteArray
import com.kgs.calendar.platform.toNSData
import com.kgs.calendar.platform.withLock
import com.kgs.calendar.time.ZoneId
import com.kgs.calendar.time.systemDefault
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.DoubleVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import platform.EventKit.EKAlarm
import platform.EventKit.EKAuthorizationStatusFullAccess
import platform.EventKit.EKCalendar
import platform.EventKit.EKEntityType
import platform.EventKit.EKEvent
import platform.EventKit.EKEventAvailabilityBusy
import platform.EventKit.EKEventAvailabilityFree
import platform.EventKit.EKEventAvailabilityNotSupported
import platform.EventKit.EKEventStatusCanceled
import platform.EventKit.EKEventStatusConfirmed
import platform.EventKit.EKEventStatusTentative
import platform.EventKit.EKEventStore
import platform.EventKit.EKRecurrenceDayOfWeek
import platform.EventKit.EKRecurrenceEnd
import platform.EventKit.EKRecurrenceFrequency
import platform.EventKit.EKRecurrenceRule
import platform.EventKit.EKSpan
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSNumber
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSTimeZone
import platform.Foundation.create
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.timeZoneWithName
import platform.Foundation.writeToFile
import platform.UIKit.UIColor
import kotlin.coroutines.resume
import kotlin.math.roundToLong
import kotlin.time.Instant

/** The one event store of the app; EventKit wants it shared and long-lived. */
object IosEventStore {
    val store: EKEventStore by lazy { EKEventStore() }

    fun hasFullAccess(): Boolean =
        EKEventStore.authorizationStatusForEntityType(EKEntityType.EKEntityTypeEvent) == EKAuthorizationStatusFullAccess

    /** Asks for full access to the device's calendars (iOS 17 has its own request for that). */
    fun requestAccess(onResult: (Boolean) -> Unit) {
        val completion: (Boolean, NSError?) -> Unit = { granted, _ -> onResult(granted) }
        if (NSProcessInfo.processInfo.isOperatingSystemAtLeastVersion(iosVersion(17))) {
            store.requestFullAccessToEventsWithCompletion(completion)
        } else {
            @Suppress("DEPRECATION")
            store.requestAccessToEntityType(EKEntityType.EKEntityTypeEvent, completion)
        }
    }

    suspend fun requestAccess(): Boolean = suspendCancellableCoroutine { continuation ->
        requestAccess { granted -> continuation.resume(granted) }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun iosVersion(major: Long) = kotlinx.cinterop.cValue<platform.Foundation.NSOperatingSystemVersion> {
    majorVersion = major
    minorVersion = 0
    patchVersion = 0
}

/**
 * The iPhone's calendars through EventKit, in the shape the app knows from Android's calendar
 * provider: a recurring series is one event with its RRULE and EXDATEs, a changed occurrence is an
 * event of its own, and reminders are the event's alarms. EventKit names items by strings, the
 * app by numbers, so the numbers are kept in [idFile].
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class EventKitCalendarClient(private val idFile: String) : AndroidCalendarProviderClient {
    private val store get() = IosEventStore.store
    private val ids = IdMap(idFile)

    override fun hasCalendarPermissions(): Boolean = IosEventStore.hasFullAccess()

    override suspend fun listCalendars(includeDisabled: Boolean): List<AndroidProviderCalendar> = withContext(Dispatchers.IO) {
        requireAccess()
        eventCalendars().map { calendar ->
            AndroidProviderCalendar(
                id = ids.idFor(CALENDAR + calendar.calendarIdentifier),
                displayName = calendar.title.ifBlank { "Calendar" },
                color = calendar.argb(),
                accessLevel = if (calendar.allowsContentModifications) ACCESS_OWNER else ACCESS_READ,
                accountName = calendar.source?.title,
                accountType = calendar.source?.sourceType?.name,
                ownerAccount = null,
                allowsReminders = true,
                allowedAvailability = null,
            )
        }.sortedBy { it.displayName.lowercase() }
    }

    /** iOS has no hidden or unsynced calendars of its own. */
    override suspend fun listHiddenOrNotSyncedCalendars(): List<AndroidProviderCalendar> = emptyList()

    override suspend fun listEvents(calendarIds: Set<Long>, syncStartMillis: Long, syncEndMillis: Long): List<AndroidProviderEvent> =
        withContext(Dispatchers.IO) {
            requireAccess()
            val calendars = calendarIds.mapNotNull { id -> calendarFor(id)?.let { id to it } }.toMap()
            if (calendars.isEmpty()) return@withContext emptyList()
            val calendarIdByIdentifier = calendars.entries.associate { (id, calendar) -> calendar.calendarIdentifier to id }
            val occurrences = eventsBetween(syncStartMillis, syncEndMillis, calendars.values.toList())
            val zone = ZoneId.systemDefault()
            val result = mutableListOf<AndroidProviderEvent>()
            occurrences.filterNot { it.isDetached }.groupBy { it.calendarItemIdentifier }.forEach { (itemId, items) ->
                val first = items.minBy { it.startMillis() }
                val calendarId = calendarIdByIdentifier[first.calendar?.calendarIdentifier] ?: return@forEach
                val rule = (first.recurrenceRules?.firstOrNull() as? EKRecurrenceRule)
                if (rule == null) {
                    result += first.toProviderEvent(ids.idFor(EVENT + itemId), calendarId, null, null, zone)
                    return@forEach
                }
                // The series starts with its first occurrence, which may lie before the window.
                val seriesStart = first.eventIdentifier?.let { store.eventWithIdentifier(it) } ?: first
                val rrule = rule.toRRule(first.allDay, zone)
                val series = first.toProviderEvent(ids.idFor(EVENT + itemId), calendarId, rrule, null, zone)
                    .copy(
                        startsAtMillis = seriesStart.originalStartMillis(),
                        endsAtMillis = seriesStart.originalStartMillis() + first.durationMillis(),
                    )
                // EventKit has no EXDATE: occurrences the series should have in the window but
                // doesn't (deleted, or changed into an event of their own) are the exceptions.
                val present = items.map { it.originalStartMillis() }.toSet()
                val exDates = expectedStarts(series, syncStartMillis, syncEndMillis, zone)
                    .filterNot { expected -> present.any { it.sameOccurrence(expected, first.allDay, zone) } }
                result += series.copy(exDates = exDates.takeIf { it.isNotEmpty() }?.joinToString(","))
            }
            occurrences.filter { it.isDetached }.forEach { event ->
                val calendarId = calendarIdByIdentifier[event.calendar?.calendarIdentifier] ?: return@forEach
                val key = DETACHED + event.calendarItemIdentifier + "@" + event.originalStartMillis()
                result += event.toProviderEvent(ids.idFor(key), calendarId, null, null, zone)
            }
            ids.save()
            result
        }

    override suspend fun insertEvent(calendarId: Long, event: EventEntity): Long = withContext(Dispatchers.IO) {
        requireAccess()
        val calendar = calendarFor(calendarId) ?: error("This iPhone calendar is no longer available.")
        val ekEvent = EKEvent.eventWithEventStore(store)
        ekEvent.calendar = calendar
        ekEvent.apply(event, previous = null)
        save(ekEvent, EKSpan.EKSpanThisEvent)
        val id = ids.idFor(EVENT + ekEvent.calendarItemIdentifier)
        ids.save()
        id
    }

    override suspend fun updateEvent(eventId: Long, calendarId: Long, event: EventEntity, previous: EventEntity?) = withContext(Dispatchers.IO) {
        requireAccess()
        val key = ids.keyFor(eventId) ?: error("This iPhone event is no longer available.")
        if (key.startsWith(DETACHED)) {
            val detached = findDetached(key) ?: error("This iPhone event is no longer available.")
            calendarFor(calendarId)?.let { detached.calendar = it }
            detached.apply(event, previous)
            save(detached, EKSpan.EKSpanThisEvent)
            return@withContext
        }
        val itemId = key.removePrefix(EVENT)
        // Occurrences removed in the app (new EXDATEs) are removed from the series first.
        val removed = event.exDatesCsv.millisSet() - previous?.exDatesCsv.millisSet()
        removed.forEach { start -> occurrenceOf(itemId, start, event.allDay)?.let { remove(it, EKSpan.EKSpanThisEvent) } }
        val series = seriesStart(itemId) ?: error("This iPhone event is no longer available.")
        calendarFor(calendarId)?.let { series.calendar = it }
        series.apply(event, previous)
        save(series, if (series.hasRecurrenceRules || !event.recurrenceRule.isNullOrBlank()) EKSpan.EKSpanFutureEvents else EKSpan.EKSpanThisEvent)
    }

    override suspend fun deleteEvent(eventId: Long) = withContext(Dispatchers.IO) {
        requireAccess()
        val key = ids.keyFor(eventId) ?: return@withContext
        if (key.startsWith(DETACHED)) {
            findDetached(key)?.let { remove(it, EKSpan.EKSpanThisEvent) }
        } else {
            seriesStart(key.removePrefix(EVENT))?.let { remove(it, EKSpan.EKSpanFutureEvents) }
        }
        Unit
    }

    override suspend fun cancelRecurringInstance(
        eventId: Long,
        calendarId: Long,
        occurrenceStartMillis: Long,
        occurrenceEndMillis: Long,
        allDay: Boolean,
    ) = withContext(Dispatchers.IO) {
        requireAccess()
        val key = ids.keyFor(eventId)?.takeIf { it.startsWith(EVENT) } ?: error("This iPhone event is no longer available.")
        val occurrence = occurrenceOf(key.removePrefix(EVENT), occurrenceStartMillis, allDay)
            ?: error("This occurrence is no longer in the iPhone calendar.")
        remove(occurrence, EKSpan.EKSpanThisEvent)
    }

    override fun toEntity(event: AndroidProviderEvent, collectionHref: String, color: Int, manualColor: Int?): EventEntity =
        EventEntity(
            uid = "android-event-${event.id}",
            collectionHref = collectionHref,
            resourceHref = eventHref(event.id),
            title = event.title?.ifBlank { null } ?: "Untitled event",
            description = event.description?.ifBlank { null },
            location = event.location?.ifBlank { null },
            locationMapVerified = null,
            startsAtMillis = event.startsAtMillis,
            endsAtMillis = event.endsAtMillis,
            allDay = event.allDay,
            recurrenceRule = event.recurrenceRule?.ifBlank { null },
            isRecurring = !event.recurrenceRule.isNullOrBlank(),
            exDatesCsv = event.exDates,
            timezoneId = if (event.allDay) null else event.timezoneId ?: ZoneId.systemDefault().id,
            remindersCsv = event.reminderMinutes.filter { it >= 0 }.distinct().sorted().takeIf { it.isNotEmpty() }?.joinToString(","),
            status = when (event.status?.toLong()) {
                EKEventStatusTentative -> EventStatus.Tentative.value
                EKEventStatusCanceled -> EventStatus.Cancelled.value
                EKEventStatusConfirmed -> EventStatus.Confirmed.value
                else -> null
            },
            classification = null,
            transparency = when (event.availability?.toLong()) {
                EKEventAvailabilityFree -> EventTransparency.Transparent.value
                null, EKEventAvailabilityNotSupported -> null
                else -> EventTransparency.Opaque.value
            },
            categories = null,
            organizerJson = null,
            attendeesJson = null,
            color = color,
            manualColor = manualColor,
        )

    // --- Reading ---

    private fun eventCalendars(): List<EKCalendar> =
        store.calendarsForEntityType(EKEntityType.EKEntityTypeEvent).filterIsInstance<EKCalendar>()

    private fun calendarFor(id: Long): EKCalendar? =
        ids.keyFor(id)?.takeIf { it.startsWith(CALENDAR) }?.let { store.calendarWithIdentifier(it.removePrefix(CALENDAR)) }

    /** EventKit answers at most four years at a time, so longer spans are read in parts. */
    private fun eventsBetween(startMillis: Long, endMillis: Long, calendars: List<EKCalendar>?): List<EKEvent> {
        val events = mutableListOf<EKEvent>()
        var from = startMillis
        while (from < endMillis) {
            val to = minOf(endMillis, from + MAX_QUERY_MILLIS)
            val predicate = store.predicateForEventsWithStartDate(from.toNSDate(), to.toNSDate(), calendars)
            events += store.eventsMatchingPredicate(predicate).filterIsInstance<EKEvent>()
            from = to
        }
        // An event spanning a part boundary comes twice.
        return events.distinctBy { it.calendarItemIdentifier + "@" + it.originalStartMillis() + "@" + it.isDetached }
    }

    private fun EKEvent.toProviderEvent(id: Long, calendarId: Long, rrule: String?, exDates: String?, zone: TimeZone): AndroidProviderEvent {
        val start = startMillis()
        return AndroidProviderEvent(
            id = id,
            calendarId = calendarId,
            title = title,
            description = notes,
            location = location,
            startsAtMillis = start,
            endsAtMillis = start + durationMillis(),
            allDay = allDay,
            recurrenceRule = rrule,
            exDates = exDates,
            status = status.toInt(),
            accessLevel = null,
            availability = availability.toInt(),
            organizer = organizer?.name,
            reminderMinutes = alarms.orEmpty().mapNotNull { alarm ->
                (alarm as? EKAlarm)?.takeIf { it.absoluteDate == null && it.relativeOffset <= 0.0 }
                    ?.let { (-it.relativeOffset / 60.0).roundToLong().toInt() }
            },
            timezoneId = if (allDay) null else timeZone?.name ?: zone.id,
        )
    }

    private fun EKEvent.startMillis(): Long = startDate?.toMillis() ?: 0L

    /** Where the occurrence was planned, also for one that was moved. */
    private fun EKEvent.originalStartMillis(): Long = (if (isDetached) occurrenceDate else null)?.toMillis() ?: startMillis()

    /** All-day events end at the next midnight in the app; EventKit's end is the last day's last second. */
    private fun EKEvent.durationMillis(): Long {
        val start = startMillis()
        val end = endDate?.toMillis() ?: start
        if (!allDay) return (end - start).coerceAtLeast(60_000L)
        val zone = ZoneId.systemDefault()
        val firstDay = Instant.fromEpochMilliseconds(start).toLocalDateTime(zone).date
        val lastDay = Instant.fromEpochMilliseconds(maxOf(end, start)).toLocalDateTime(zone).date
        return lastDay.plus(DatePeriod(days = 1)).atStartOfDayIn(zone).toEpochMilliseconds() - firstDay.atStartOfDayIn(zone).toEpochMilliseconds()
    }

    private fun expectedStarts(series: AndroidProviderEvent, fromMillis: Long, toMillis: Long, zone: TimeZone): List<Long> =
        runCatching {
            RecurrenceExpander(zone).expand(toEntity(series, "", 0), fromMillis, toMillis).map { it.startsAtMillis }
        }.getOrDefault(emptyList())

    private fun Long.sameOccurrence(other: Long, allDay: Boolean, zone: TimeZone): Boolean =
        if (allDay) {
            Instant.fromEpochMilliseconds(this).toLocalDateTime(zone).date == Instant.fromEpochMilliseconds(other).toLocalDateTime(zone).date
        } else {
            this == other
        }

    private fun seriesStart(itemId: String): EKEvent? {
        val item = store.calendarItemWithIdentifier(itemId) as? EKEvent ?: return null
        if (!item.hasRecurrenceRules) return item
        return item.eventIdentifier?.let { store.eventWithIdentifier(it) } ?: item
    }

    private fun occurrenceOf(itemId: String, startMillis: Long, allDay: Boolean): EKEvent? {
        val zone = ZoneId.systemDefault()
        return eventsBetween(startMillis - DAY_MILLIS, startMillis + 2 * DAY_MILLIS, null)
            .firstOrNull { it.calendarItemIdentifier == itemId && !it.isDetached && it.originalStartMillis().sameOccurrence(startMillis, allDay, zone) }
    }

    private fun findDetached(key: String): EKEvent? {
        val itemId = key.removePrefix(DETACHED).substringBeforeLast('@')
        val original = key.substringAfterLast('@').toLongOrNull() ?: return null
        // A changed occurrence can have moved anywhere; it is looked for around its planned time.
        return eventsBetween(original - YEAR_MILLIS, original + YEAR_MILLIS, null)
            .firstOrNull { it.calendarItemIdentifier == itemId && it.isDetached && it.originalStartMillis() == original }
    }

    // --- Writing ---

    private fun EKEvent.apply(event: EventEntity, previous: EventEntity?) {
        title = event.title.ifBlank { "Untitled event" }
        notes = event.description?.ifBlank { null }
        location = event.location?.ifBlank { null }
        allDay = event.allDay
        if (event.allDay) {
            timeZone = null
            startDate = event.startsAtMillis.toNSDate()
            // The last day's last second, as EventKit stores all-day events.
            endDate = (maxOf(event.endsAtMillis, event.startsAtMillis + DAY_MILLIS) - 1000L).toNSDate()
        } else {
            timeZone = event.timezoneId?.let { NSTimeZone.timeZoneWithName(it) } ?: NSTimeZone.timeZoneWithName(ZoneId.systemDefault().id)
            startDate = event.startsAtMillis.toNSDate()
            endDate = maxOf(event.endsAtMillis, event.startsAtMillis + 60_000L).toNSDate()
        }
        val wantedAlarms = event.remindersCsv.minutes()
        val currentAlarms = alarms.orEmpty().mapNotNull { alarm ->
            (alarm as? EKAlarm)?.takeIf { it.absoluteDate == null && it.relativeOffset <= 0.0 }?.let { (-it.relativeOffset / 60.0).roundToLong().toInt() }
        }.toSet()
        if (previous == null || wantedAlarms.toSet() != currentAlarms) {
            alarms = wantedAlarms.map { EKAlarm.alarmWithRelativeOffset(-it * 60.0) }
        }
        val rule = event.recurrenceRule?.ifBlank { null }
        if (rule != previous?.recurrenceRule?.ifBlank { null } || previous == null) {
            recurrenceRules = rule?.let { parseRRule(it, event.allDay, event.timezoneId) }?.let { listOf(it) }
        }
        val supportsAvailability = calendar?.supportedEventAvailabilities?.let { it != 0uL } ?: false
        if (supportsAvailability && (previous == null || event.transparency != previous.transparency)) {
            availability = if (EventTransparency.from(event.transparency) == EventTransparency.Transparent) EKEventAvailabilityFree else EKEventAvailabilityBusy
        }
    }

    private fun save(event: EKEvent, span: EKSpan) = memScoped {
        val error = alloc<ObjCObjectVar<NSError?>>()
        if (!store.saveEvent(event, span, true, error.ptr)) error(error.value?.localizedDescription ?: "The iPhone calendar did not save the event.")
    }

    private fun remove(event: EKEvent, span: EKSpan) = memScoped {
        val error = alloc<ObjCObjectVar<NSError?>>()
        if (!store.removeEvent(event, span, true, error.ptr)) error(error.value?.localizedDescription ?: "The iPhone calendar did not delete the event.")
    }

    private fun requireAccess() {
        if (!hasCalendarPermissions()) error("Calendar access is required.")
    }

    private fun EKCalendar.argb(): Int = memScoped {
        val color = CGColor?.let { UIColor.colorWithCGColor(it) } ?: return@memScoped DEFAULT_COLOR
        val r = alloc<DoubleVar>()
        val g = alloc<DoubleVar>()
        val b = alloc<DoubleVar>()
        val a = alloc<DoubleVar>()
        if (!color.getRed(r.ptr, g.ptr, b.ptr, a.ptr)) return@memScoped DEFAULT_COLOR
        fun channel(value: Double) = (value.coerceIn(0.0, 1.0) * 255).roundToLong().toInt()
        (0xFF shl 24) or (channel(r.value) shl 16) or (channel(g.value) shl 8) or channel(b.value)
    }

    private companion object {
        const val CALENDAR = "c:"
        const val EVENT = "e:"
        const val DETACHED = "d:"
        /** Android's CAL_ACCESS_OWNER and CAL_ACCESS_READ, which decide what the app may write. */
        const val ACCESS_OWNER = 700
        const val ACCESS_READ = 200
        const val DAY_MILLIS = 24L * 60L * 60L * 1000L
        const val YEAR_MILLIS = 366L * DAY_MILLIS
        const val MAX_QUERY_MILLIS = 4L * 365L * DAY_MILLIS
        val DEFAULT_COLOR = 0xFF2563A8.toInt()
    }
}

private fun Long.toNSDate(): NSDate = NSDate.dateWithTimeIntervalSince1970(this / 1000.0)

private fun NSDate.toMillis(): Long = (timeIntervalSince1970 * 1000.0).roundToLong()

private fun String?.minutes(): List<Int> =
    this?.split(',')?.mapNotNull { it.trim().toIntOrNull() }?.filter { it >= 0 }?.distinct()?.sorted().orEmpty()

private fun String?.millisSet(): Set<Long> =
    this?.split(',')?.mapNotNull { it.trim().toLongOrNull() }?.toSet().orEmpty()

private fun Any?.asInt(): Int? = when (this) {
    is Number -> toInt()
    is NSNumber -> intValue
    else -> null
}

// --- RRULE <-> EKRecurrenceRule ---

private val WEEKDAYS = listOf("SU", "MO", "TU", "WE", "TH", "FR", "SA")

@OptIn(ExperimentalForeignApi::class)
internal fun EKRecurrenceRule.toRRule(allDay: Boolean, zone: TimeZone): String {
    val parts = mutableListOf(
        "FREQ=" + when (frequency) {
            EKRecurrenceFrequency.EKRecurrenceFrequencyDaily -> "DAILY"
            EKRecurrenceFrequency.EKRecurrenceFrequencyWeekly -> "WEEKLY"
            EKRecurrenceFrequency.EKRecurrenceFrequencyMonthly -> "MONTHLY"
            else -> "YEARLY"
        },
    )
    if (interval > 1) parts += "INTERVAL=$interval"
    recurrenceEnd?.let { end ->
        val endDate = end.endDate
        if (endDate != null) {
            val instant = Instant.fromEpochMilliseconds(endDate.toMillis())
            parts += if (allDay) {
                val date = instant.toLocalDateTime(zone).date
                "UNTIL=" + date.toString().replace("-", "")
            } else {
                val utc = instant.toLocalDateTime(TimeZone.UTC)
                "UNTIL=" + utc.date.toString().replace("-", "") + "T" +
                    utc.hour.pad() + utc.minute.pad() + utc.second.pad() + "Z"
            }
        } else if (end.occurrenceCount > 0u) {
            parts += "COUNT=${end.occurrenceCount}"
        }
    }
    daysOfTheWeek?.mapNotNull { it as? EKRecurrenceDayOfWeek }?.takeIf { it.isNotEmpty() }?.let { days ->
        parts += "BYDAY=" + days.joinToString(",") { day ->
            val name = WEEKDAYS[((day.dayOfTheWeek - 1).toInt()).coerceIn(0, 6)]
            if (day.weekNumber != 0L) "${day.weekNumber}$name" else name
        }
    }
    fun list(name: String, values: List<*>?) {
        values?.mapNotNull { it.asInt() }?.takeIf { it.isNotEmpty() }?.let { parts += "$name=" + it.joinToString(",") }
    }
    list("BYMONTHDAY", daysOfTheMonth)
    list("BYYEARDAY", daysOfTheYear)
    list("BYWEEKNO", weeksOfTheYear)
    list("BYMONTH", monthsOfTheYear)
    list("BYSETPOS", setPositions)
    if (firstDayOfTheWeek in 1L..7L) parts += "WKST=" + WEEKDAYS[(firstDayOfTheWeek - 1).toInt()]
    return parts.joinToString(";")
}

private fun Int.pad() = toString().padStart(2, '0')

/** The parts EventKit can store; others (BYHOUR, BYMINUTE, ...) are left out. */
@OptIn(ExperimentalForeignApi::class)
internal fun parseRRule(rrule: String, allDay: Boolean, timezoneId: String?): EKRecurrenceRule? {
    val fields = rrule.removePrefix("RRULE:").split(';').mapNotNull { part ->
        part.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].uppercase() to it[1] }
    }.toMap()
    val frequency = when (fields["FREQ"]?.uppercase()) {
        "DAILY" -> EKRecurrenceFrequency.EKRecurrenceFrequencyDaily
        "WEEKLY" -> EKRecurrenceFrequency.EKRecurrenceFrequencyWeekly
        "MONTHLY" -> EKRecurrenceFrequency.EKRecurrenceFrequencyMonthly
        "YEARLY" -> EKRecurrenceFrequency.EKRecurrenceFrequencyYearly
        else -> return null
    }
    val interval = fields["INTERVAL"]?.toLongOrNull()?.coerceAtLeast(1) ?: 1L
    fun ints(name: String): List<Int>? = fields[name]?.split(',')?.mapNotNull { it.trim().toIntOrNull() }?.takeIf { it.isNotEmpty() }
    val days = fields["BYDAY"]?.split(',')?.mapNotNull { raw ->
        val value = raw.trim().uppercase()
        val index = WEEKDAYS.indexOf(value.takeLast(2)).takeIf { it >= 0 } ?: return@mapNotNull null
        val week = value.dropLast(2).toLongOrNull() ?: 0L
        EKRecurrenceDayOfWeek.dayOfWeek((index + 1).toLong(), weekNumber = week)
    }?.takeIf { it.isNotEmpty() }
    val zone = timezoneId?.let { runCatching { TimeZone.of(it) }.getOrNull() } ?: ZoneId.systemDefault()
    val end = fields["UNTIL"]?.let { untilMillis(it, allDay, zone) }?.let { EKRecurrenceEnd.recurrenceEndWithEndDate(it.toNSDate()) }
        ?: fields["COUNT"]?.toULongOrNull()?.takeIf { it > 0u }?.let { EKRecurrenceEnd.recurrenceEndWithOccurrenceCount(it) }
    return EKRecurrenceRule(
        recurrenceWithFrequency = frequency,
        interval = interval,
        daysOfTheWeek = days,
        daysOfTheMonth = ints("BYMONTHDAY"),
        monthsOfTheYear = ints("BYMONTH"),
        weeksOfTheYear = ints("BYWEEKNO"),
        daysOfTheYear = ints("BYYEARDAY"),
        setPositions = ints("BYSETPOS"),
        end = end,
    )
}

/** A date-only UNTIL includes that whole day; a time without Z is in the event's zone. */
private fun untilMillis(value: String, allDay: Boolean, zone: TimeZone): Long? = runCatching {
    val text = value.trim()
    val date = LocalDate(text.substring(0, 4).toInt(), text.substring(4, 6).toInt(), text.substring(6, 8).toInt())
    if (text.length == 8) {
        date.plus(DatePeriod(days = 1)).atStartOfDayIn(zone).toEpochMilliseconds() - 1000L
    } else {
        val time = LocalDateTime(date.year, date.month, date.day, text.substring(9, 11).toInt(), text.substring(11, 13).toInt(), text.substring(13, 15).toInt())
        time.toInstant(if (text.endsWith("Z")) TimeZone.UTC else zone).toEpochMilliseconds()
    }
}.getOrNull()

/** Stable numbers for EventKit's identifiers, kept across launches. */
@OptIn(BetaInteropApi::class)
private class IdMap(private val path: String) {
    private val lock = KgsLock()
    private val byKey = mutableMapOf<String, Long>()
    private val byId = mutableMapOf<Long, String>()
    private var next = 1L
    private var dirty = false

    init {
        runCatching {
            val text = NSData.create(contentsOfFile = path)?.toByteArray()?.decodeToString() ?: return@runCatching
            val json = JSONObject(text)
            next = json.optLong("next", 1L)
            val map = json.getJSONObject("ids")
            map.keys().forEach { key ->
                val id = map.getLong(key)
                byKey[key] = id
                byId[id] = key
            }
        }
    }

    fun idFor(key: String): Long = lock.withLock {
        byKey[key] ?: next++.also { id ->
            byKey[key] = id
            byId[id] = key
            dirty = true
        }
    }

    fun keyFor(id: Long): String? = lock.withLock { byId[id] }

    fun save() = lock.withLock {
        if (!dirty) return@withLock
        val map = JSONObject()
        byKey.forEach { (key, id) -> map.put(key, id) }
        val text = JSONObject().put("next", next).put("ids", map).toString()
        val directory = path.substringBeforeLast('/')
        NSFileManager.defaultManager.createDirectoryAtPath(directory, withIntermediateDirectories = true, attributes = null, error = null)
        if (text.encodeToByteArray().toNSData().writeToFile(path, atomically = true)) dirty = false
    }
}
