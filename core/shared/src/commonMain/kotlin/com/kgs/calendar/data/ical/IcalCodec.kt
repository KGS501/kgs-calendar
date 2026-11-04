package com.kgs.calendar.data.ical

import com.kgs.calendar.platform.kgsPlatformName
import kotlin.math.roundToLong
import com.kgs.calendar.time.*
import kotlinx.datetime.*
import com.kgs.calendar.domain.time.orDeviceZone
import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.data.local.entity.withValidIcalSchedule
import com.kgs.calendar.domain.model.Attendee
import com.kgs.calendar.domain.model.ComponentType
import com.kgs.calendar.domain.model.Organizer
import com.kgs.calendar.domain.model.ParticipantJson
import com.kgs.calendar.domain.model.MAX_REMINDER_MINUTES
import com.kgs.calendar.domain.model.REMINDER_AT_END
import com.kgs.calendar.domain.model.REMINDER_AT_START
import com.kgs.calendar.domain.model.isSupportedReminderOffset
import com.kgs.calendar.domain.model.normalizedReminderOffsets
import com.kgs.calendar.platform.TextCharset
import com.kgs.calendar.platform.md5
import com.kgs.calendar.platform.urlDecode
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import com.kgs.calendar.time.ZoneId
import com.kgs.calendar.time.ZoneOffset
import kotlin.time.Duration
import com.kgs.calendar.time.DateTimeFormatter

class IcalCodec(
    /** Null follows the device's zone (see [orDeviceZone]). */
    private val fixedZoneId: ZoneId? = null,
) {
    private val zoneId: ZoneId get() = fixedZoneId.orDeviceZone()

    fun parse(
        rawIcs: String,
        collectionHref: String,
        resourceHref: String,
        collectionColor: Int,
    ): ParsedCalendarComponent? {
        val zones = TimeZoneResolver.forCalendar(rawIcs)
        val components = components(rawIcs)
        val eventBlocks = components[ComponentType.Event].orEmpty()
        if (eventBlocks.isNotEmpty()) return parseEventSeries(eventBlocks, collectionHref, resourceHref, collectionColor, zones)
        val taskBlocks = components[ComponentType.Task].orEmpty()
        if (taskBlocks.isNotEmpty()) return parseTaskSeries(taskBlocks, collectionHref, resourceHref, collectionColor, zones)
        return null
    }

    /** [blocks] are the components of one UID: a master, if any, and its exceptions. */
    private fun parseEventSeries(
        blocks: List<ComponentBlock>,
        collectionHref: String,
        resourceHref: String,
        collectionColor: Int,
        zones: TimeZoneResolver,
    ): ParsedCalendarComponent? {
        val master = blocks.firstOrNull { block -> block.lines.none { it.name == "RECURRENCE-ID" } } ?: blocks.first()
        val parsedMaster = parseEvent(master, collectionHref, resourceHref, collectionColor, zones) ?: return null
        val masterEvent = parsedMaster.event ?: return parsedMaster
        val overrides = blocks
            .filter { it !== master }
            .mapNotNull { block ->
                val recurrenceLine = block.lines.firstOrNull { it.name == "RECURRENCE-ID" } ?: return@mapNotNull null
                val recurrenceId = parseIcalDate(recurrenceLine, zones)?.epochMillis ?: return@mapNotNull null
                val parsedOverride = parseEvent(block, collectionHref, resourceHref, collectionColor, zones)?.event
                    ?: return@mapNotNull null
                EventRecurrenceOverride.fromEvent(recurrenceId, parsedOverride, recurrenceLine.isThisAndFuture())
            }
        return parsedMaster.copy(
            event = masterEvent.copy(recurrenceOverridesJson = RecurrenceOverrideCodec.encodeEvents(overrides)),
        )
    }

    /** [blocks] are the components of one UID: a master, if any, and its exceptions. */
    private fun parseTaskSeries(
        blocks: List<ComponentBlock>,
        collectionHref: String,
        resourceHref: String,
        collectionColor: Int,
        zones: TimeZoneResolver,
    ): ParsedCalendarComponent? {
        val master = blocks.firstOrNull { block -> block.lines.none { it.name == "RECURRENCE-ID" } } ?: blocks.first()
        val parsedMaster = parseTask(master, collectionHref, resourceHref, collectionColor, zones) ?: return null
        val masterTask = parsedMaster.task ?: return parsedMaster
        val overrides = blocks
            .filter { it !== master }
            .mapNotNull { block ->
                val recurrenceLine = block.lines.firstOrNull { it.name == "RECURRENCE-ID" } ?: return@mapNotNull null
                val recurrenceId = parseIcalDate(recurrenceLine, zones)?.epochMillis ?: return@mapNotNull null
                val parsed = parseTask(block, collectionHref, resourceHref, collectionColor, zones)?.task
                    ?: return@mapNotNull null
                val names = block.lines.map { it.name }.toSet()
                val masterAnchor = masterTask.startAtMillis ?: masterTask.dueAtMillis ?: recurrenceId
                val shift = recurrenceId - masterAnchor
                val parsedOverride = parsed.copy(
                    title = if ("SUMMARY" in names) parsed.title else masterTask.title,
                    notes = if ("DESCRIPTION" in names) parsed.notes else masterTask.notes,
                    location = if ("LOCATION" in names) parsed.location else masterTask.location,
                    url = if ("URL" in names) parsed.url else masterTask.url,
                    categories = if ("CATEGORIES" in names) parsed.categories else masterTask.categories,
                    startAtMillis = if ("DTSTART" in names) parsed.startAtMillis else masterTask.startAtMillis?.plus(shift),
                    startHasTime = if ("DTSTART" in names) parsed.startHasTime else masterTask.startHasTime,
                    dueAtMillis = if ("DUE" in names) parsed.dueAtMillis else masterTask.dueAtMillis?.plus(shift),
                    dueHasTime = if ("DUE" in names) parsed.dueHasTime else masterTask.dueHasTime,
                    priority = if ("PRIORITY" in names) parsed.priority else masterTask.priority,
                    percentComplete = if ("PERCENT-COMPLETE" in names) parsed.percentComplete else masterTask.percentComplete,
                    remindersCsv = if (block.alarms.isNotEmpty()) parsed.remindersCsv else masterTask.remindersCsv,
                )
                TaskRecurrenceOverride.fromTask(recurrenceId, parsedOverride, recurrenceLine.isThisAndFuture())
            }
        return parsedMaster.copy(
            task = masterTask.copy(recurrenceOverridesJson = RecurrenceOverrideCodec.encodeTasks(overrides)),
        )
    }

    private fun IcalLine.isThisAndFuture(): Boolean =
        params["RANGE"]?.unquoteParam().equals("THISANDFUTURE", ignoreCase = true)

    /**
     * Every series of a whole calendar, as subscription feeds deliver them. A UID's master and
     * exceptions become one item; each item gets a key that is unique to its full UID. [checkpoint]
     * runs between items, so a caller can stop a large feed.
     */
    fun parseAll(
        rawIcs: String,
        collectionHref: String,
        resourceHrefPrefix: String,
        collectionColor: Int,
        checkpoint: () -> Unit = {},
    ): List<ParsedCalendarComponent> {
        val zones = TimeZoneResolver.forCalendar(rawIcs)
        val components = components(rawIcs)
        fun series(blocks: List<ComponentBlock>): List<Pair<String, List<ComponentBlock>>> = blocks
            .filter { block -> block.lines.any { it.name == "UID" } }
            .groupBy { block -> block.lines.first { it.name == "UID" }.value }
            .toList()
        val events = series(components[ComponentType.Event].orEmpty()).mapNotNull { (uid, blocks) ->
            checkpoint()
            val href = "$resourceHrefPrefix/${feedResourceName(ComponentType.Event, uid)}.ics"
            parseEventSeries(blocks, collectionHref, href, collectionColor, zones)
        }
        val tasks = series(components[ComponentType.Task].orEmpty()).mapNotNull { (uid, blocks) ->
            checkpoint()
            val href = "$resourceHrefPrefix/${feedResourceName(ComponentType.Task, uid)}.ics"
            parseTaskSeries(blocks, collectionHref, href, collectionColor, zones)
        }
        return events + tasks
    }

    private fun md5Hex(input: ByteArray): String =
        md5(input).joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

    /** Readable, but the digest of the whole UID is what keeps two UIDs from sharing a row. */
    private fun feedResourceName(type: ComponentType, uid: String): String =
        "${uid.safeResourceName().take(48)}-${md5Hex("${type.value}\n$uid".encodeToByteArray())}"

    fun serializeEvent(event: EventEntity, originalRawIcs: String? = null): String {
        val now = utcStamp(kotlin.time.Clock.System.now().toEpochMilli())
        val overrides = RecurrenceOverrideCodec.decodeEvents(event.recurrenceOverridesJson)
        val generated = buildString {
            appendCalendarHeader()
            appendTimezoneComponents(
                zoneIds = listOf(event.timezoneId) + overrides.map { it.timezoneId },
                earliestMillis = (listOf(event.startsAtMillis) + overrides.map { minOf(it.recurrenceIdMillis, it.startsAtMillis) }).min(),
            )
            appendLine("BEGIN:VEVENT")
            appendLine("UID:${escape(event.uid)}")
            appendLine("DTSTAMP:$now")
            if (originalRawIcs.isNullOrBlank()) appendLine("CREATED:$now")
            appendLine("LAST-MODIFIED:$now")
            appendLine("SEQUENCE:${event.sequence.coerceAtLeast(0)}")
            appendDateProperty("DTSTART", event.startsAtMillis, event.allDay, event.timezoneId)
            // DTEND has to lie after DTSTART, so a zero-length event is written without one.
            if (event.endsAtMillis > event.startsAtMillis) {
                appendDateProperty("DTEND", event.endsAtMillis, event.allDay, event.timezoneId)
            }
            appendLine("SUMMARY:${escape(event.title)}")
            event.status?.takeIf { it.isNotBlank() }?.let { appendLine("STATUS:${it.uppercase()}") }
            event.classification?.takeIf { it.isNotBlank() }?.let { appendLine("CLASS:${it.uppercase()}") }
            event.transparency?.takeIf { it.isNotBlank() }?.let { appendLine("TRANSP:${it.uppercase()}") }
            event.description?.takeIf { it.isNotBlank() }?.let { appendLine("DESCRIPTION:${escape(it)}") }
            event.location?.takeIf { it.isNotBlank() }?.let { appendLine("LOCATION:${escape(it)}") }
            event.categories?.takeIf { it.isNotBlank() }?.let { appendLine("CATEGORIES:${categoryValue(it)}") }
            event.manualColor?.let { appendLine("COLOR:${it.toColorText()}") }
            event.recurrenceRule?.takeIf { it.isNotBlank() }?.let { appendLine("RRULE:$it") }
            // Excluded occurrences (single-occurrence deletions of a recurring event). Without
            // writing these back out, a deleted occurrence would reappear on the next sync.
            event.exDatesCsv?.split(',')?.mapNotNull { it.trim().toLongOrNull() }?.forEach { millis ->
                appendDateProperty("EXDATE", millis, event.allDay, event.timezoneId)
            }
            event.rDatesCsv?.split(',')?.mapNotNull { it.trim().toLongOrNull() }?.forEach { millis ->
                appendDateProperty("RDATE", millis, event.allDay, event.timezoneId)
            }
            appendOrganizer(event.organizerJson)
            appendAttendees(event.attendeesJson)
            appendReminders(event.remindersCsv, event.title)
            appendLine("END:VEVENT")
            overrides.forEach { override ->
                appendEventOverride(event, override, now)
            }
            appendCalendarFooter()
        }
        return foldIcal(preserveUnsupportedData(originalRawIcs, generated, ComponentType.Event))
    }

    private fun StringBuilder.appendOrganizer(organizerJson: String?) {
        val organizer = ParticipantJson.decodeOrganizer(organizerJson) ?: return
        val email = organizer.email.trim()
        if (email.isBlank()) return
        val name = organizer.name?.trim().orEmpty()
        val params = buildString {
            if (name.isNotBlank()) append(";CN=${escapeParam(name)}")
            organizer.sentBy?.takeIf { it.isNotBlank() }?.let { append(";SENT-BY=${escapeParam(it)}") }
            organizer.directory?.takeIf { it.isNotBlank() }?.let { append(";DIR=${escapeParam(it)}") }
            organizer.language?.takeIf { it.isNotBlank() }?.let { append(";LANGUAGE=${escapeParam(it)}") }
        }
        appendLine("ORGANIZER$params:${calendarUserAddress(email)}")
    }

    private fun StringBuilder.appendAttendees(attendeesJson: String?) {
        ParticipantJson.decodeAttendees(attendeesJson).forEach { attendee ->
            val email = attendee.email.trim()
            if (email.isBlank()) return@forEach
            val name = attendee.name?.trim().orEmpty()
            val partstat = attendee.partstat?.takeIf { it.isNotBlank() }
                ?.uppercase() ?: "NEEDS-ACTION"
            val role = attendee.role?.takeIf { it.isNotBlank() }
                ?.uppercase() ?: "REQ-PARTICIPANT"
            val rsvp = attendee.rsvp?.trim().orEmpty()
            val scheduleStatus = attendee.scheduleStatus?.trim().orEmpty()
            val params = buildString {
                if (name.isNotBlank()) append(";CN=${escapeParam(name)}")
                attendee.calendarUserType?.takeIf { it.isNotBlank() }?.let { append(";CUTYPE=${it.uppercase()}") }
                attendee.member?.takeIf { it.isNotBlank() }?.let { append(";MEMBER=${escapeParam(it)}") }
                if (role.isNotBlank()) append(";ROLE=$role")
                if (partstat.isNotBlank()) append(";PARTSTAT=$partstat")
                if (rsvp.isNotBlank()) append(";RSVP=${rsvp.uppercase()}")
                attendee.delegatedTo?.takeIf { it.isNotBlank() }?.let { append(";DELEGATED-TO=${escapeParam(it)}") }
                attendee.delegatedFrom?.takeIf { it.isNotBlank() }?.let { append(";DELEGATED-FROM=${escapeParam(it)}") }
                attendee.sentBy?.takeIf { it.isNotBlank() }?.let { append(";SENT-BY=${escapeParam(it)}") }
                attendee.directory?.takeIf { it.isNotBlank() }?.let { append(";DIR=${escapeParam(it)}") }
                attendee.language?.takeIf { it.isNotBlank() }?.let { append(";LANGUAGE=${escapeParam(it)}") }
                attendee.scheduleAgent?.takeIf { it.isNotBlank() }?.let { append(";SCHEDULE-AGENT=${it.uppercase()}") }
                attendee.scheduleForceSend?.takeIf { it.isNotBlank() }?.let { append(";SCHEDULE-FORCE-SEND=${it.uppercase()}") }
                if (scheduleStatus.isNotBlank()) append(";SCHEDULE-STATUS=${escapeParam(scheduleStatus)}")
            }
            appendLine("ATTENDEE$params:${calendarUserAddress(email)}")
        }
    }

    fun serializeTask(task: TaskEntity, originalRawIcs: String? = null): String {
        val now = utcStamp(kotlin.time.Clock.System.now().toEpochMilli())
        val normalizedTask = task.withValidIcalSchedule()
        val overrides = RecurrenceOverrideCodec.decodeTasks(normalizedTask.recurrenceOverridesJson)
        val generated = buildString {
            appendCalendarHeader()
            appendTimezoneComponents(
                zoneIds = listOf(normalizedTask.timezoneId) + overrides.map { it.timezoneId },
                earliestMillis = (
                    listOfNotNull(normalizedTask.startAtMillis, normalizedTask.dueAtMillis) +
                        overrides.map { it.recurrenceIdMillis }
                ).minOrNull() ?: kotlin.time.Clock.System.now().toEpochMilli(),
            )
            appendLine("BEGIN:VTODO")
            appendLine("UID:${escape(normalizedTask.uid)}")
            appendLine("DTSTAMP:$now")
            if (originalRawIcs.isNullOrBlank()) appendLine("CREATED:$now")
            appendLine("LAST-MODIFIED:$now")
            appendLine("SEQUENCE:${normalizedTask.sequence.coerceAtLeast(0)}")
            normalizedTask.startAtMillis?.let {
                appendDateProperty("DTSTART", it, !normalizedTask.startHasTime, normalizedTask.timezoneId)
            }
            normalizedTask.dueAtMillis?.let {
                appendDateProperty("DUE", it, !normalizedTask.dueHasTime, normalizedTask.timezoneId)
            }
            normalizedTask.completedAtMillis?.let { appendLine("COMPLETED:${utcStamp(it)}") }
            // Prefer the explicit status field so IN-PROCESS / CANCELLED round-trip;
            // fall back to the legacy isCompleted boolean when status was never set.
            val effectiveStatus = normalizedTask.status?.takeIf { it.isNotBlank() }
                ?: if (normalizedTask.isCompleted) "COMPLETED" else "NEEDS-ACTION"
            appendLine("STATUS:$effectiveStatus")
            normalizedTask.priority?.let { appendLine("PRIORITY:$it") }
            normalizedTask.percentComplete?.let { appendLine("PERCENT-COMPLETE:${it.coerceIn(0, 100)}") }
            normalizedTask.parentUid?.takeIf { it.isNotBlank() }?.let {
                appendLine("RELATED-TO;RELTYPE=PARENT:${escape(it)}")
            }
            appendLine("SUMMARY:${escape(normalizedTask.title)}")
            normalizedTask.notes?.takeIf { it.isNotBlank() }?.let { appendLine("DESCRIPTION:${escape(it)}") }
            normalizedTask.location?.takeIf { it.isNotBlank() }?.let { appendLine("LOCATION:${escape(it)}") }
            normalizedTask.url?.takeIf { it.isNotBlank() }?.let { appendLine("URL:${escape(it)}") }
            normalizedTask.categories?.takeIf { it.isNotBlank() }?.let { appendLine("CATEGORIES:${categoryValue(it)}") }
            normalizedTask.recurrenceRule?.takeIf { it.isNotBlank() }?.let { appendLine("RRULE:$it") }
            normalizedTask.exDatesCsv?.split(',')?.mapNotNull { it.trim().toLongOrNull() }?.forEach { millis ->
                appendDateProperty("EXDATE", millis, !(normalizedTask.startHasTime || normalizedTask.dueHasTime), normalizedTask.timezoneId)
            }
            normalizedTask.rDatesCsv?.split(',')?.mapNotNull { it.trim().toLongOrNull() }?.forEach { millis ->
                appendDateProperty("RDATE", millis, !(normalizedTask.startHasTime || normalizedTask.dueHasTime), normalizedTask.timezoneId)
            }
            appendReminders(
                normalizedTask.remindersCsv,
                normalizedTask.title,
                positiveRelatedToEnd = normalizedTask.dueAtMillis != null,
            )
            appendLine("END:VTODO")
            overrides.forEach { override ->
                appendTaskOverride(normalizedTask, override, now)
            }
            appendCalendarFooter()
        }
        return foldIcal(preserveUnsupportedData(originalRawIcs, generated, ComponentType.Task))
    }

    private fun StringBuilder.appendReminders(
        remindersCsv: String?,
        summary: String,
        positiveRelatedToEnd: Boolean = false,
    ) {
        val minutes = remindersCsv?.split(',')?.mapNotNull { it.trim().toIntOrNull() }
            ?.filter { it.isSupportedReminderOffset() }
            ?.distinct()
            .orEmpty()
        minutes.forEach { offset ->
            val relatedToEnd = offset == REMINDER_AT_END || (positiveRelatedToEnd && offset > 0)
            val triggerOffset = if (offset == REMINDER_AT_END) 0 else offset
            appendLine("BEGIN:VALARM")
            appendLine("ACTION:DISPLAY")
            appendLine("DESCRIPTION:${escape(summary)}")
            appendLine(
                if (relatedToEnd) {
                    "TRIGGER;RELATED=END:${triggerDuration(triggerOffset)}"
                } else {
                    "TRIGGER:${triggerDuration(triggerOffset)}"
                },
            )
            appendLine("END:VALARM")
        }
    }

    /** Formats minutes-before into an iCal duration string, e.g. 0 → -PT0M, 90 → -PT1H30M, 1440 → -P1D. */
    private fun triggerDuration(minutesBefore: Int): String {
        if (minutesBefore <= 0) return "-PT0M"
        val days = minutesBefore / (24 * 60)
        val rem = minutesBefore % (24 * 60)
        val hours = rem / 60
        val mins = rem % 60
        val builder = StringBuilder("-P")
        if (days > 0) builder.append("${days}D")
        if (hours > 0 || mins > 0) {
            builder.append("T")
            if (hours > 0) builder.append("${hours}H")
            if (mins > 0) builder.append("${mins}M")
        }
        return builder.toString()
    }

    private fun parseEvent(
        block: ComponentBlock,
        collectionHref: String,
        resourceHref: String,
        color: Int,
        zones: TimeZoneResolver,
    ): ParsedCalendarComponent? {
        val lines = block.lines
        val values = lines.associateBy { it.name }
        val uid = values["UID"]?.value ?: return null
        val startLine = values["DTSTART"] ?: values["RECURRENCE-ID"] ?: return null
        val start = parseIcalDate(startLine, zones) ?: return null
        val end = values["DTEND"]?.let { parseIcalDate(it, zones) }
            ?: values["DURATION"]?.value?.let { duration ->
                parseDurationMillis(duration)?.let { start.plusDuration(it) }
            }
            ?: start.plusDefaultDuration(startLine.isDateOnly)
        val title = values["SUMMARY"]?.textValue()?.ifBlank { "Untitled event" } ?: "Untitled event"
        val rrule = values["RRULE"]?.value?.takeIf { it.isNotBlank() }
        // EXDATE can appear multiple times and each value may be comma-separated.
        val exDateMillis = lines.asSequence()
            .filter { it.name == "EXDATE" }
            .flatMap { line ->
                line.value.split(',').asSequence().map { v ->
                    parseIcalDate(IcalLine("EXDATE", line.params, v.trim()), zones)?.epochMillis
                }
            }
            .filterNotNull()
            .toList()
        val rDateMillis = lines.asSequence()
            .filter { it.name == "RDATE" }
            .flatMap { line ->
                line.value.split(',').asSequence().map { value ->
                    parseIcalDate(IcalLine("RDATE", line.params, value.trim().substringBefore('/')), zones)?.epochMillis
                }
            }
            .filterNotNull()
            .toList()
        val event = EventEntity(
            uid = uid,
            collectionHref = collectionHref,
            resourceHref = resourceHref,
            title = title,
            description = values["DESCRIPTION"]?.textValue(),
            location = values["LOCATION"]?.textValue(),
            startsAtMillis = start.epochMillis,
            endsAtMillis = end.epochMillis,
            allDay = startLine.isDateOnly,
            recurrenceRule = rrule,
            isRecurring = rrule != null || rDateMillis.isNotEmpty(),
            exDatesCsv = exDateMillis.takeIf { it.isNotEmpty() }?.joinToString(","),
            rDatesCsv = rDateMillis.takeIf { it.isNotEmpty() }?.joinToString(","),
            timezoneId = sourceZoneId(startLine, zones),
            sequence = values["SEQUENCE"]?.value?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
            remindersCsv = block.reminderOffsets(AlarmAnchor.Event),
            status = values["STATUS"]?.value?.trim()?.uppercase()?.takeIf { it.isNotBlank() },
            classification = values["CLASS"]?.value?.trim()?.uppercase()?.takeIf { it.isNotBlank() },
            transparency = values["TRANSP"]?.value?.trim()?.uppercase()?.takeIf { it.isNotBlank() },
            categories = lines.categoryText(),
            organizerJson = values["ORGANIZER"]?.toOrganizerJson(),
            attendeesJson = lines.filter { it.name == "ATTENDEE" }.toAttendeesJson(),
            color = color,
            manualColor = values["COLOR"]?.value?.parseColorInt()
                ?: values["X-APPLE-CALENDAR-COLOR"]?.value?.parseColorInt()
                ?: values["X-COLOR"]?.value?.parseColorInt(),
        )
        return ParsedCalendarComponent(uid, ComponentType.Event, event, null)
    }

    private fun parseTask(
        block: ComponentBlock,
        collectionHref: String,
        resourceHref: String,
        color: Int,
        zones: TimeZoneResolver,
    ): ParsedCalendarComponent? {
        val lines = block.lines
        val values = lines.associateBy { it.name }
        val uid = values["UID"]?.value ?: return null
        val completedAt = values["COMPLETED"]?.let { parseIcalDate(it, zones)?.epochMillis }
        val rawStatus = values["STATUS"]?.value?.trim()?.uppercase()?.takeIf { it.isNotBlank() }
        val normalizedStatus = when (rawStatus) {
            "NEEDS-ACTION", "IN-PROCESS", "COMPLETED", "CANCELLED" -> rawStatus
            else -> rawStatus // keep unknown values as-is rather than dropping them
        }
        val statusCompleted = normalizedStatus == "COMPLETED"
        val title = values["SUMMARY"]?.textValue()?.ifBlank { "Untitled task" } ?: "Untitled task"
        val due = values["DUE"]?.let { parseIcalDate(it, zones) }
        val start = values["DTSTART"]?.let { parseIcalDate(it, zones) }
        val exDateMillis = lines.asSequence()
            .filter { it.name == "EXDATE" }
            .flatMap { line ->
                line.value.split(',').asSequence().map { v ->
                    parseIcalDate(IcalLine("EXDATE", line.params, v.trim()), zones)?.epochMillis
                }
            }
            .filterNotNull()
            .toList()
        val rDateMillis = lines.asSequence()
            .filter { it.name == "RDATE" }
            .flatMap { line ->
                line.value.split(',').asSequence().map { value ->
                    parseIcalDate(IcalLine("RDATE", line.params, value.trim().substringBefore('/')), zones)?.epochMillis
                }
            }
            .filterNotNull()
            .toList()
        // A task's times share one zone; the first timed value names it.
        val sourceTimeZone = listOfNotNull(values["DTSTART"], values["DUE"])
            .firstOrNull { !it.isDateOnly && it.value.length != 8 }
            ?.let { sourceZoneId(it, zones) }
        val task = TaskEntity(
            uid = uid,
            collectionHref = collectionHref,
            resourceHref = resourceHref,
            title = title,
            notes = values["DESCRIPTION"]?.textValue(),
            location = values["LOCATION"]?.textValue(),
            url = values["URL"]?.textValue(),
            categories = lines.categoryText(),
            dueAtMillis = due?.epochMillis,
            dueHasTime = due?.isDateOnly == false,
            startAtMillis = start?.epochMillis,
            startHasTime = start?.isDateOnly == false,
            completedAtMillis = completedAt,
            isCompleted = completedAt != null || statusCompleted,
            status = normalizedStatus,
            priority = values["PRIORITY"]?.value?.toIntOrNull(),
            percentComplete = values["PERCENT-COMPLETE"]?.value?.toIntOrNull()?.coerceIn(0, 100),
            parentUid = lines.firstOrNull { line ->
                line.name == "RELATED-TO" &&
                    line.params["RELTYPE"]?.unquoteParam()?.uppercase().let { it == null || it == "PARENT" }
            }?.textValue()?.takeIf { it.isNotBlank() },
            recurrenceRule = values["RRULE"]?.value,
            exDatesCsv = exDateMillis.takeIf { it.isNotEmpty() }?.joinToString(","),
            rDatesCsv = rDateMillis.takeIf { it.isNotEmpty() }?.joinToString(","),
            timezoneId = sourceTimeZone,
            sequence = values["SEQUENCE"]?.value?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
            remindersCsv = block.reminderOffsets(if (due != null) AlarmAnchor.TaskWithDue else AlarmAnchor.TaskStartOnly),
            color = color,
        ).withValidIcalSchedule()
        return ParsedCalendarComponent(uid, ComponentType.Task, null, task)
    }

    private fun components(rawIcs: String): Map<ComponentType, List<ComponentBlock>> {
        val result = mutableMapOf<ComponentType, MutableList<ComponentBlock>>()
        var activeName: ComponentType? = null
        var activeLines = mutableListOf<IcalLine>()
        var activeAlarms = mutableListOf<List<IcalLine>>()
        var alarm: MutableList<IcalLine>? = null
        // Depth of nested BEGIN blocks (VALARM, VTIMEZONE, STANDARD, DAYLIGHT, ...) within
        // the currently active VEVENT/VTODO. Lines inside nested blocks must not be collected
        // and their END must not close the outer component. The properties of a VALARM directly
        // inside the component are kept apart to read its reminder.
        var nestedDepth = 0
        for (line in unfold(rawIcs)) {
            val upper = line.uppercase()
            when {
                activeName == null && (upper == "BEGIN:VEVENT" || upper == "BEGIN:VTODO") -> {
                    activeName = if (upper == "BEGIN:VEVENT") ComponentType.Event else ComponentType.Task
                    activeLines = mutableListOf()
                    activeAlarms = mutableListOf()
                    alarm = null
                    nestedDepth = 0
                }
                activeName != null && upper.startsWith("BEGIN:") -> {
                    nestedDepth++
                    if (nestedDepth == 1 && upper == "BEGIN:VALARM") alarm = mutableListOf()
                }
                activeName != null && nestedDepth > 0 && upper.startsWith("END:") -> {
                    if (nestedDepth == 1) {
                        alarm?.let { activeAlarms += it.toList() }
                        alarm = null
                    }
                    nestedDepth--
                }
                activeName != null && nestedDepth == 0 &&
                    (upper == "END:VEVENT" || upper == "END:VTODO") -> {
                    result.getOrPut(activeName) { mutableListOf() }
                        .add(ComponentBlock(activeLines, activeAlarms.toList()))
                    activeName = null
                }
                activeName != null && nestedDepth == 1 && alarm != null -> {
                    parseLine(line)?.let { alarm?.add(it) }
                }
                activeName != null && nestedDepth == 0 -> {
                    parseLine(line)?.let(activeLines::add)
                }
                // else: lines outside a VEVENT/VTODO (e.g. VCALENDAR header props,
                // top-level VTIMEZONE) are ignored.
            }
        }
        return result
    }

    /** What a VTODO's or VEVENT's relative reminders count from in the app's reminder model. */
    private enum class AlarmAnchor { Event, TaskWithDue, TaskStartOnly }

    private fun ComponentBlock.reminderOffsets(anchor: AlarmAnchor): String? =
        alarms.mapNotNull { alarmOffset(it, anchor) }
            .normalizedReminderOffsets()
            .takeIf { it.isNotEmpty() }
            ?.joinToString(",")

    /**
     * The app's reminder offset for an alarm, or null when the app can't show and write it back
     * unchanged: e-mail alarms, absolute times, reminders after the anchor, seconds, offsets
     * beyond the supported range, and reminders before an event's end. Such alarms are kept
     * as they are in the stored data.
     */
    private fun alarmOffset(alarm: List<IcalLine>, anchor: AlarmAnchor): Int? {
        val action = alarm.firstOrNull { it.name == "ACTION" }?.value?.trim()?.uppercase()
        if (action != "DISPLAY" && action != "AUDIO") return null
        val trigger = alarm.firstOrNull { it.name == "TRIGGER" } ?: return null
        if (trigger.params["VALUE"].equals("DATE-TIME", ignoreCase = true)) return null
        val raw = trigger.value.trim().uppercase()
        val negative = raw.startsWith("-")
        val body = raw.trimStart('-', '+')
        val match = TRIGGER_DURATION.matchEntire(body) ?: return null
        val (w, d, h, m, sec) = match.destructured
        if ((sec.toLongOrNull() ?: 0L) != 0L) return null
        val minutes = (w.toLongOrNull() ?: 0) * 7 * 24 * 60 +
            (d.toLongOrNull() ?: 0) * 24 * 60 +
            (h.toLongOrNull() ?: 0) * 60 +
            (m.toLongOrNull() ?: 0)
        if (minutes != 0L && !negative) return null
        if (minutes > MAX_REMINDER_MINUTES) return null
        val relatedToEnd = trigger.params["RELATED"]?.unquoteParam().equals("END", ignoreCase = true)
        return when (anchor) {
            // Event offsets count back from the start; only the exact end is supported.
            AlarmAnchor.Event -> when {
                relatedToEnd -> REMINDER_AT_END.takeIf { minutes == 0L }
                else -> minutes.toInt()
            }
            // A task's offsets count back from its due time; the start is supported exactly.
            AlarmAnchor.TaskWithDue -> when {
                relatedToEnd -> if (minutes == 0L) REMINDER_AT_END else minutes.toInt()
                else -> REMINDER_AT_START.takeIf { minutes == 0L }
            }
            AlarmAnchor.TaskStartOnly -> if (relatedToEnd) null else minutes.toInt()
        }
    }

    private fun unfold(rawIcs: String): List<String> {
        val normalized = rawIcs.replace("\r\n", "\n").replace('\r', '\n')
        val result = mutableListOf<String>()
        val current = StringBuilder()
        for (line in normalized.lines()) {
            if (line.startsWith(" ") || line.startsWith("\t")) {
                current.append(line.drop(1))
            } else {
                if (current.isNotEmpty()) result += current.toString()
                current.clear()
                current.append(line)
            }
        }
        if (current.isNotEmpty()) result += current.toString()
        return result
    }

    private fun parseLine(line: String): IcalLine? {
        val colon = line.indexOfUnquoted(':')
        if (colon <= 0) return null
        val left = line.substring(0, colon)
        val value = line.substring(colon + 1)
        val parts = left.splitUnquoted(';')
        val params = parts.drop(1).mapNotNull {
            val split = it.split('=', limit = 2)
            if (split.size == 2) split[0].uppercase() to split[1] else null
        }.toMap()
        return IcalLine(parts.first().uppercase(), params, value)
    }

    private fun String.indexOfUnquoted(target: Char): Int {
        var quoted = false
        var escaped = false
        forEachIndexed { index, char ->
            when {
                escaped -> escaped = false
                char == '\\' -> escaped = true
                char == '"' -> quoted = !quoted
                char == target && !quoted -> return index
            }
        }
        return -1
    }

    private fun String.splitUnquoted(delimiter: Char): List<String> {
        val result = mutableListOf<String>()
        var start = 0
        var quoted = false
        var escaped = false
        forEachIndexed { index, char ->
            when {
                escaped -> escaped = false
                char == '\\' -> escaped = true
                char == '"' -> quoted = !quoted
                char == delimiter && !quoted -> {
                    result += substring(start, index)
                    start = index + 1
                }
            }
        }
        result += substring(start)
        return result
    }

    private fun parseIcalDate(line: IcalLine, zones: TimeZoneResolver): ParsedDate? {
        val value = line.value
        val isDateOnly = line.isDateOnly || value.length == 8
        // An unknown zone is read in the device's zone, the same zone it is then stored under.
        val sourceZone = line.params["TZID"]
            ?.unquoteParam()
            ?.let(zones::resolve)
            ?: zoneId
        val millis = runCatching {
            when {
                isDateOnly -> LocalDate.parse(value, DATE).atStartOfDay(zoneId).toInstant().toEpochMilli()
                value.endsWith("Z") -> LocalDateTime.parse(value.dropLast(1), DATE_TIME).toInstant(ZoneOffset.UTC).toEpochMilli()
                else -> LocalDateTime.parse(value, DATE_TIME).atZone(sourceZone).toInstant().toEpochMilli()
            }
        }.getOrNull()
        return millis?.let { ParsedDate(it, isDateOnly) }
    }

    /**
     * The zone a timed value is stored under: UTC for UTC values, null for floating values (they
     * follow the device), and otherwise the resolved TZID, or the device's zone it was read in.
     */
    private fun sourceZoneId(line: IcalLine, zones: TimeZoneResolver): String? {
        if (line.isDateOnly || line.value.length == 8) return null
        if (line.value.endsWith("Z")) return "UTC"
        val tzid = line.params["TZID"]?.unquoteParam()?.takeIf { it.isNotBlank() } ?: return null
        return (zones.resolve(tzid) ?: zoneId).id
    }

    private fun parseDurationMillis(value: String): Long? {
        val normalized = value.trim().uppercase()
        val sign = if (normalized.startsWith("-")) -1 else 1
        val body = normalized.trimStart('+', '-')
        val weeks = Regex("""P(\d+)W""").matchEntire(body)?.groupValues?.getOrNull(1)?.toLongOrNull()
        if (weeks != null) return sign * weeks * 7L * DAY_MILLIS
        return runCatching { sign * Duration.parse(body).toMillis() }.getOrNull()
    }

    private fun ParsedDate.plusDefaultDuration(allDay: Boolean): ParsedDate =
        // RFC 5545 3.6.1: a timed event without DTEND or DURATION takes up no time.
        if (allDay) plusDuration(DAY_MILLIS) else this

    /** Date-only values move by whole calendar days, so a 23h or 25h DST day still ends at midnight. */
    private fun ParsedDate.plusDuration(durationMillis: Long): ParsedDate {
        if (!isDateOnly) return ParsedDate(epochMillis + durationMillis, isDateOnly)
        val days = (durationMillis.toDouble() / DAY_MILLIS).roundToLong()
        val endMillis = Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalDate()
            .plusDays(days)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
        return ParsedDate(endMillis, isDateOnly)
    }

    /**
     * Writes a date or time. Timed values keep their kind: null zones are floating local times,
     * UTC stays UTC, and other zones are written as local time with their TZID.
     */
    private fun StringBuilder.appendDateProperty(
        name: String,
        epochMillis: Long,
        allDay: Boolean,
        timezoneId: String?,
    ) {
        if (allDay) {
            appendLine("$name;VALUE=DATE:${dateValue(epochMillis)}")
            return
        }
        if (timezoneId == null) {
            appendLine("$name:${localStamp(epochMillis, zoneId)}")
            return
        }
        val sourceZone = timezoneId.takeUnless { it.isUtcZoneId() }?.let(TimeZoneResolver::resolveKnownId)
        if (sourceZone == null) {
            appendLine("$name:${utcStamp(epochMillis)}")
        } else {
            appendLine("$name;TZID=${escapeParamToken(sourceZone.id)}:${localStamp(epochMillis, sourceZone)}")
        }
    }

    private fun String.isUtcZoneId(): Boolean =
        uppercase() in setOf("UTC", "Z", "GMT", "UCT", "ZULU", "ETC/UTC", "ETC/GMT", "ETC/UCT", "ETC/ZULU")

    /** One VTIMEZONE per zone used, reduced to the rules from about a year before [earliestMillis]. */
    private fun StringBuilder.appendTimezoneComponents(zoneIds: List<String?>, earliestMillis: Long) {
        zoneIds.asSequence()
            .filterNotNull()
            .filterNot { it.isUtcZoneId() }
            .mapNotNull(TimeZoneResolver::resolveKnownId)
            .map { it.id }
            .distinct()
            .forEach { id ->
                val component = IcalPlatform.vtimezoneText(id)?.takeIf { it.isNotBlank() } ?: return@forEach
                minifyTimezone(unfold(component), earliestMillis).forEach(::appendLine)
            }
    }

    /**
     * Drops the historic observances of a VTIMEZONE. Kept are rules still in force after the cutoff,
     * a year before the item's first date, and the last rule that ended before it, which defines
     * the offset at the cutoff. Returns the component unchanged when it can't be read.
     */
    private fun minifyTimezone(lines: List<String>, earliestMillis: Long): List<String> {
        if (lines.size < 3) return lines
        val cutoff = LocalDateTime.ofInstant(Instant.ofEpochMilli(earliestMillis).minus(Duration.ofDays(366)), ZoneOffset.UTC)
        val header = mutableListOf<String>()
        val observances = mutableListOf<List<String>>()
        var index = 1
        while (index < lines.lastIndex) {
            val line = lines[index]
            if (line.uppercase().startsWith("BEGIN:")) {
                val block = mutableListOf(line)
                var depth = 1
                index++
                while (index < lines.lastIndex && depth > 0) {
                    val nested = lines[index]
                    block += nested
                    if (nested.uppercase().startsWith("BEGIN:")) depth++
                    if (nested.uppercase().startsWith("END:")) depth--
                    index++
                }
                observances += block
            } else {
                header += line
                index++
            }
        }
        data class Observance(val lines: List<String>, val keep: Boolean, val lastOnset: LocalDateTime?)
        val parsed = observances.map { block ->
            val start = block.firstOrNull { propertyName(it) == "DTSTART" }?.substringAfter(':')?.trim()?.let(::parseLocalStamp)
                ?: return lines
            val rrule = block.firstOrNull { propertyName(it) == "RRULE" }?.substringAfter(':')
            val onsets = listOf(start) + block.filter { propertyName(it) == "RDATE" }
                .flatMap { it.substringAfter(':').split(',') }
                .mapNotNull { parseLocalStamp(it.trim()) }
            if (rrule != null) {
                val until = rrule.split(';').firstOrNull { it.startsWith("UNTIL=", true) }
                    ?.substringAfter('=')?.let(::parseLocalStamp)
                Observance(block, until == null || !until.isBefore(cutoff), until)
            } else {
                val last = onsets.max()
                Observance(block, !last.isBefore(cutoff), last)
            }
        }
        if (parsed.none { it.keep }) return lines
        val inEffectAtCutoff = parsed
            .filter { !it.keep && it.lastOnset != null }
            .maxByOrNull { it.lastOnset!! }
        return listOf(lines.first()) + header +
            parsed.filter { it.keep || it === inEffectAtCutoff }.flatMap { it.lines } +
            lines.last()
    }

    private fun parseLocalStamp(value: String): LocalDateTime? = runCatching {
        val trimmed = value.trim().removeSuffix("Z")
        if (trimmed.length == 8) LocalDate.parse(trimmed, DATE).atStartOfDay() else LocalDateTime.parse(trimmed, DATE_TIME)
    }.getOrNull()

    private fun EventRecurrenceOverride.recurrenceIdProperty(): String =
        if (thisAndFuture) RANGE_RECURRENCE_ID else "RECURRENCE-ID"

    private fun StringBuilder.appendEventOverride(
        master: EventEntity,
        override: EventRecurrenceOverride,
        now: String,
    ) {
        appendLine("BEGIN:VEVENT")
        appendLine("UID:${escape(master.uid)}")
        appendLine("DTSTAMP:$now")
        appendLine("LAST-MODIFIED:$now")
        appendLine("SEQUENCE:${master.sequence.coerceAtLeast(0)}")
        appendDateProperty(
            override.recurrenceIdProperty(),
            override.recurrenceIdMillis,
            master.allDay,
            master.timezoneId,
        )
        appendDateProperty("DTSTART", override.startsAtMillis, override.allDay, override.timezoneId)
        if (override.endsAtMillis > override.startsAtMillis) {
            appendDateProperty("DTEND", override.endsAtMillis, override.allDay, override.timezoneId)
        }
        appendLine("SUMMARY:${escape(override.title)}")
        override.status?.takeIf { it.isNotBlank() }?.let { appendLine("STATUS:${it.uppercase()}") }
        override.classification?.takeIf { it.isNotBlank() }?.let { appendLine("CLASS:${it.uppercase()}") }
        override.transparency?.takeIf { it.isNotBlank() }?.let { appendLine("TRANSP:${it.uppercase()}") }
        override.description?.takeIf { it.isNotBlank() }?.let { appendLine("DESCRIPTION:${escape(it)}") }
        override.location?.takeIf { it.isNotBlank() }?.let { appendLine("LOCATION:${escape(it)}") }
        override.categories?.takeIf { it.isNotBlank() }?.let { appendLine("CATEGORIES:${categoryValue(it)}") }
        appendOrganizer(override.organizerJson)
        appendAttendees(override.attendeesJson)
        appendReminders(override.remindersCsv, override.title)
        appendLine("END:VEVENT")
    }

    private fun StringBuilder.appendTaskOverride(
        master: TaskEntity,
        override: TaskRecurrenceOverride,
        now: String,
    ) {
        appendLine("BEGIN:VTODO")
        appendLine("UID:${escape(master.uid)}")
        appendLine("DTSTAMP:$now")
        appendLine("LAST-MODIFIED:$now")
        appendLine("SEQUENCE:${master.sequence.coerceAtLeast(0)}")
        appendDateProperty(
            if (override.thisAndFuture) RANGE_RECURRENCE_ID else "RECURRENCE-ID",
            override.recurrenceIdMillis,
            !(master.startHasTime || master.dueHasTime),
            master.timezoneId,
        )
        override.startAtMillis?.let {
            appendDateProperty("DTSTART", it, !override.startHasTime, override.timezoneId)
        }
        override.dueAtMillis?.let {
            appendDateProperty("DUE", it, !override.dueHasTime, override.timezoneId)
        }
        override.completedAtMillis?.let { appendLine("COMPLETED:${utcStamp(it)}") }
        val status = override.status?.takeIf { it.isNotBlank() }
            ?: if (override.isCompleted) "COMPLETED" else "NEEDS-ACTION"
        appendLine("STATUS:$status")
        override.priority?.let { appendLine("PRIORITY:$it") }
        override.percentComplete?.let { appendLine("PERCENT-COMPLETE:${it.coerceIn(0, 100)}") }
        appendLine("SUMMARY:${escape(override.title)}")
        override.notes?.takeIf { it.isNotBlank() }?.let { appendLine("DESCRIPTION:${escape(it)}") }
        override.location?.takeIf { it.isNotBlank() }?.let { appendLine("LOCATION:${escape(it)}") }
        override.url?.takeIf { it.isNotBlank() }?.let { appendLine("URL:${escape(it)}") }
        override.categories?.takeIf { it.isNotBlank() }?.let { appendLine("CATEGORIES:${categoryValue(it)}") }
        appendReminders(
            override.remindersCsv,
            override.title,
            positiveRelatedToEnd = override.dueAtMillis != null,
        )
        appendLine("END:VTODO")
    }

    /**
     * KGS only exposes a subset of RFC 5545 in its editor. Keep every property and nested
     * component it doesn't own when editing an existing resource so attachments, conference
     * links, custom X-properties, unsupported alarms and server scheduling metadata survive.
     * Text values and alarms the edit left unchanged are written back exactly as they were, so
     * their parameters, HTML and alarm metadata survive too.
     */
    private fun preserveUnsupportedData(
        originalRawIcs: String?,
        generatedRawIcs: String,
        componentType: ComponentType,
    ): String {
        if (originalRawIcs.isNullOrBlank()) return generatedRawIcs
        val originalBlocks = extractComponentBlocks(originalRawIcs, componentType.value)
        if (originalBlocks.isEmpty()) return generatedRawIcs
        val originalZones = TimeZoneResolver.forCalendar(originalRawIcs)
        val originals = originalBlocks.associate { block ->
            componentIdentity(block, originalZones) to originalComponent(block, componentType)
        }
        var merged = injectOriginalData(generatedRawIcs, componentType, originals)

        // Original time zone definitions are kept for the values that still refer to them.
        val referencedZones = unfold(merged)
            .mapNotNull { line -> parseLine(line)?.params?.get("TZID")?.unquoteParam()?.lowercase() }
            .toSet()
        val definedZones = extractComponentBlocks(merged, "VTIMEZONE")
            .mapNotNull { block -> block.firstOrNull { propertyName(it) == "TZID" }?.substringAfter(':')?.trim()?.lowercase() }
            .toSet()
        extractComponentBlocks(originalRawIcs, "VTIMEZONE").forEach { block ->
            val tzid = block.firstOrNull { propertyName(it) == "TZID" }?.substringAfter(':')?.trim()
                ?.removeSurrounding("\"")?.lowercase()
            if (tzid != null && tzid in referencedZones && tzid !in definedZones) {
                merged = merged.replaceFirst(
                    "BEGIN:${componentType.value}",
                    block.joinToString("\r\n", postfix = "\r\n") + "BEGIN:${componentType.value}",
                )
            }
        }

        val originalCalendarProperties = unfold(originalRawIcs)
            .dropWhile { !it.equals("BEGIN:VCALENDAR", true) }
            .drop(1)
            .takeWhile { !it.uppercase().startsWith("BEGIN:") && !it.equals("END:VCALENDAR", true) }
            .filter { propertyName(it) !in KNOWN_CALENDAR_PROPERTIES }
        originalCalendarProperties.forEach { line ->
            if (!merged.lineSequence().any { it.equals(line, true) }) {
                merged = merged.replace("CALSCALE:GREGORIAN", "CALSCALE:GREGORIAN\r\n$line")
            }
        }
        return merged
    }

    private fun extractComponentBlocks(rawIcs: String, componentName: String): List<List<String>> {
        val begin = "BEGIN:${componentName.uppercase()}"
        val end = "END:${componentName.uppercase()}"
        val result = mutableListOf<List<String>>()
        var active: MutableList<String>? = null
        var depth = 0
        unfold(rawIcs).forEach { line ->
            val upper = line.uppercase()
            if (active == null && upper == begin) {
                active = mutableListOf(line)
                depth = 1
            } else if (active != null) {
                val current = active ?: return@forEach
                current.add(line)
                if (upper.startsWith("BEGIN:")) depth++
                if (upper.startsWith("END:")) depth--
                if (upper == end && depth == 0) {
                    result += current.toList()
                    active = null
                }
            }
        }
        return result
    }

    /** Master or override, by the instant of its RECURRENCE-ID whichever way it was written. */
    private fun componentIdentity(block: List<String>, zones: TimeZoneResolver): String {
        val recurrence = topLevelItems(block).firstOrNull { it.size == 1 && propertyName(it[0]) == "RECURRENCE-ID" }?.single()
            ?: return "master"
        val millis = parseLine(recurrence)?.let { parseIcalDate(it, zones) }?.epochMillis
        return "override:${millis ?: recurrence.substringAfter(':').trim()}"
    }

    private class OriginalAlarm(val offset: Int, val lines: List<String>)

    private class OriginalComponent(
        val anchor: AlarmAnchor,
        /** Properties and nested components the app doesn't model. */
        val unsupported: List<String>,
        /** Alarms the app models, by the reminder offset they stand for. */
        val alarms: List<OriginalAlarm>,
        /** The first SUMMARY, DESCRIPTION, LOCATION and URL line, by property name. */
        val textLines: Map<String, String>,
        val organizerLine: String?,
    )

    /** A component's direct children: single property lines, and nested components as whole blocks. */
    private fun topLevelItems(block: List<String>): List<List<String>> {
        val items = mutableListOf<List<String>>()
        var index = 1
        while (index < block.lastIndex) {
            val line = block[index]
            if (line.uppercase().startsWith("BEGIN:")) {
                val nested = mutableListOf(line)
                var depth = 1
                index++
                while (index < block.lastIndex && depth > 0) {
                    val nestedLine = block[index]
                    nested += nestedLine
                    if (nestedLine.uppercase().startsWith("BEGIN:")) depth++
                    if (nestedLine.uppercase().startsWith("END:")) depth--
                    index++
                }
                items += nested
            } else {
                items += listOf(line)
                index++
            }
        }
        return items
    }

    private fun alarmAnchor(block: List<String>, componentType: ComponentType): AlarmAnchor = when {
        componentType == ComponentType.Event -> AlarmAnchor.Event
        topLevelItems(block).any { it.size == 1 && propertyName(it[0]) == "DUE" } -> AlarmAnchor.TaskWithDue
        else -> AlarmAnchor.TaskStartOnly
    }

    private fun alarmOffsetOf(alarmBlock: List<String>, anchor: AlarmAnchor): Int? {
        if (!alarmBlock.first().equals("BEGIN:VALARM", ignoreCase = true)) return null
        val properties = alarmBlock.drop(1).dropLast(1)
        if (properties.any { it.uppercase().startsWith("BEGIN:") }) return null
        return alarmOffset(properties.mapNotNull(::parseLine), anchor)
    }

    private fun originalComponent(block: List<String>, componentType: ComponentType): OriginalComponent {
        val known = if (componentType == ComponentType.Event) KNOWN_EVENT_PROPERTIES else KNOWN_TASK_PROPERTIES
        val anchor = alarmAnchor(block, componentType)
        val unsupported = mutableListOf<String>()
        val alarms = mutableListOf<OriginalAlarm>()
        val textLines = mutableMapOf<String, String>()
        var organizerLine: String? = null
        var parentSeen = false
        topLevelItems(block).forEach { item ->
            if (item.size > 1 || item[0].uppercase().startsWith("BEGIN:")) {
                val offset = alarmOffsetOf(item, anchor)
                if (offset != null) alarms += OriginalAlarm(offset, item) else unsupported += item
                return@forEach
            }
            val line = item[0]
            val name = propertyName(line)
            if (name in TEXT_PROPERTIES && name !in textLines) textLines[name] = line
            if (name == "ORGANIZER" && organizerLine == null) organizerLine = line
            // Only the first parent is modeled; other relationships aren't this app's to rewrite.
            val isModeledParent = name == "RELATED-TO" && !parentSeen &&
                parseLine(line)?.params?.get("RELTYPE")?.unquoteParam()?.uppercase().let { it == null || it == "PARENT" }
            if (isModeledParent) parentSeen = true
            if (name !in known || (name == "RDATE" && line.substringAfter(':', "").contains('/')) ||
                (name == "RELATED-TO" && !isModeledParent)
            ) {
                unsupported += line
            }
        }
        return OriginalComponent(anchor, unsupported, alarms, textLines, organizerLine)
    }

    private fun injectOriginalData(
        generated: String,
        componentType: ComponentType,
        originals: Map<String, OriginalComponent>,
    ): String {
        val normalized = generated.replace("\r\n", "\n").replace('\r', '\n')
        val out = mutableListOf<String>()
        var block = mutableListOf<String>()
        var inComponent = false
        var depth = 0
        normalized.lines().forEach { line ->
            val upper = line.uppercase()
            if (!inComponent && upper == "BEGIN:${componentType.value}") {
                inComponent = true
                depth = 1
                block = mutableListOf(line)
            } else if (inComponent) {
                block += line
                if (upper.startsWith("BEGIN:")) depth++
                if (upper.startsWith("END:")) depth--
                if (upper == "END:${componentType.value}" && depth == 0) {
                    val original = originals[componentIdentity(block, TimeZoneResolver.Empty)]
                    out += if (original == null) block else mergeComponent(block, componentType, original)
                    inComponent = false
                }
            } else {
                out += line
            }
        }
        return out.joinToString("\r\n").trimEnd() + "\r\n"
    }

    private fun mergeComponent(block: List<String>, componentType: ComponentType, original: OriginalComponent): List<String> {
        val sameAnchor = alarmAnchor(block, componentType) == original.anchor
        val unusedAlarms = original.alarms.toMutableList()
        val unsupportedRDates = original.unsupported.any { propertyName(it) == "RDATE" }
        val items = topLevelItems(block).mapNotNull { item ->
            if (item.size > 1) {
                // A reminder that is still set keeps its original alarm (sound, UID, acknowledgement).
                val offset = if (sameAnchor) alarmOffsetOf(item, original.anchor) else null
                val kept = offset?.let { value -> unusedAlarms.firstOrNull { it.offset == value } }
                if (kept != null) {
                    unusedAlarms.remove(kept)
                    return@mapNotNull kept.lines
                }
                return@mapNotNull item
            }
            val line = item[0]
            val name = propertyName(line)
            if (name == "RDATE" && unsupportedRDates) return@mapNotNull null
            if (name == "ORGANIZER") return@mapNotNull listOf(withOriginalOrganizerParams(line, original.organizerLine))
            val originalLine = original.textLines[name]
            if (originalLine != null && textOf(originalLine) == textOf(line)) listOf(originalLine) else item
        }
        return listOf(block.first()) + items.flatten() + original.unsupported + block.last()
    }

    /**
     * Keeps what the app doesn't model of an unchanged organizer, above all SCHEDULE-AGENT, which
     * decides who handles scheduling for this copy. A different organizer starts with a clean slate.
     */
    private fun withOriginalOrganizerParams(generated: String, original: String?): String {
        val originalLine = original ?: return generated
        val sameAddress = parseLine(generated)?.value?.stripMailto().equals(parseLine(originalLine)?.value?.stripMailto(), ignoreCase = true)
        if (!sameAddress) return generated
        val head = generated.substring(0, generated.indexOfUnquoted(':').coerceAtLeast(0))
        val value = generated.substring(head.length)
        val present = head.splitUnquoted(';').drop(1).map { it.substringBefore('=').uppercase() }.toSet()
        val originalHead = originalLine.substring(0, originalLine.indexOfUnquoted(':').coerceAtLeast(0))
        val kept = originalHead.splitUnquoted(';').drop(1).filter {
            val name = it.substringBefore('=').uppercase()
            name !in present && name !in MODELED_ORGANIZER_PARAMS
        }
        return (listOf(head) + kept).joinToString(";") + value
    }

    private fun textOf(line: String): String? = parseLine(line)?.textValue()

    private fun propertyName(line: String): String =
        line.substringBefore(':').substringBefore(';').uppercase()

    private fun foldIcal(rawIcs: String): String {
        val physicalLines = mutableListOf<String>()
        rawIcs.replace("\r\n", "\n").replace('\r', '\n').trimEnd().lines().forEach { foldLine(it, physicalLines) }
        return physicalLines.joinToString("\r\n", postfix = "\r\n")
    }

    /** Walks [line] once, counting UTF-8 bytes; a surrogate pair stays together. */
    private fun foldLine(line: String, out: MutableList<String>) {
        var segmentStart = 0
        var segmentBytes = 0
        var limit = ICAL_LINE_LIMIT_BYTES
        var prefix = ""
        var index = 0
        while (index < line.length) {
            val char = line[index]
            val isPair = char.isHighSurrogate() && index + 1 < line.length && line[index + 1].isLowSurrogate()
            val bytes = when {
                isPair -> 4
                char.code < 0x80 -> 1
                char.code < 0x800 -> 2
                else -> 3
            }
            if (segmentBytes + bytes > limit && index > segmentStart) {
                out += prefix + line.substring(segmentStart, index)
                segmentStart = index
                segmentBytes = 0
                limit = ICAL_LINE_LIMIT_BYTES - 1
                prefix = " "
            }
            segmentBytes += bytes
            index += if (isPair) 2 else 1
        }
        out += prefix + line.substring(segmentStart)
    }

    private fun StringBuilder.appendCalendarHeader() {
        appendLine("BEGIN:VCALENDAR")
        appendLine("VERSION:2.0")
        appendLine("PRODID:-//KGS Calendar//$kgsPlatformName//EN")
        appendLine("CALSCALE:GREGORIAN")
    }

    private fun StringBuilder.appendCalendarFooter() {
        appendLine("END:VCALENDAR")
    }

    private fun utcStamp(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atZone(ZoneOffset.UTC).format(DATE_TIME) + "Z"

    private fun localStamp(epochMillis: Long, sourceZone: ZoneId): String =
        Instant.ofEpochMilli(epochMillis).atZone(sourceZone).format(DATE_TIME)

    private fun dateValue(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalDate().format(DATE)

    private fun escape(value: String): String =
        value.replace("\\", "\\\\")
            .replace("\n", "\\n")
            .replace(";", "\\;")
            .replace(",", "\\,")

    private fun escapeParam(value: String): String {
        val escaped = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", " ")
        return "\"$escaped\""
    }

    private fun escapeParamToken(value: String): String =
        value.replace("\\", "\\\\").replace(";", "\\;").replace(":", "\\:")

    private fun categoryValue(value: String): String =
        value.split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString(",") { category ->
                category
                    .replace("\\", "\\\\")
                    .replace("\n", "\\n")
                    .replace(";", "\\;")
                    .replace(",", "\\,")
            }

    private fun List<IcalLine>.categoryText(): String? =
        filter { it.name == "CATEGORIES" }
            .flatMap { line ->
                line.value.splitUnquoted(',')
                    .map { it.unescapeIcal().trim() }
                    .filter { it.isNotBlank() }
            }
            .distinctBy { it.lowercase() }
            .joinToString(",")
            .takeIf { it.isNotBlank() }

    private fun calendarUserAddress(value: String): String {
        val trimmed = value.trim()
        return when {
            trimmed.startsWith("mailto:", true) -> "mailto:${escape(trimmed.substringAfter(':'))}"
            '@' in trimmed && ':' !in trimmed -> "mailto:${escape(trimmed)}"
            else -> escape(trimmed)
        }
    }

    private fun String.unescapeIcal(): String =
        replace("\\n", "\n")
            .replace("\\N", "\n")
            .replace("\\,", ",")
            .replace("\\;", ";")
            .replace("\\\\", "\\")

    private fun String.safeResourceName(): String =
        replace(Regex("[^A-Za-z0-9._-]"), "_").take(96).ifBlank { "item" }

    private fun IcalLine.toOrganizerJson(): String? {
        val email = value.stripMailto().ifBlank { return null }
        val name = params["CN"]?.unquoteParam()?.ifBlank { null }
        return ParticipantJson.encodeOrganizer(
            Organizer(
                email = email,
                name = name ?: email,
                sentBy = params["SENT-BY"]?.unquoteParam(),
                directory = params["DIR"]?.unquoteParam(),
                language = params["LANGUAGE"]?.unquoteParam(),
            ),
        )
    }

    private fun List<IcalLine>.toAttendeesJson(): String? {
        if (isEmpty()) return null
        val attendees = mapNotNull { line ->
            val email = line.value.stripMailto()
            if (email.isBlank()) return@mapNotNull null
            val name = line.params["CN"]?.unquoteParam()?.ifBlank { null } ?: email
            Attendee(
                email = email,
                name = name,
                partstat = line.params["PARTSTAT"]?.uppercase() ?: "NEEDS-ACTION",
                role = line.params["ROLE"]?.uppercase() ?: "REQ-PARTICIPANT",
                rsvp = line.params["RSVP"]?.uppercase(),
                calendarUserType = line.params["CUTYPE"]?.uppercase(),
                member = line.params["MEMBER"]?.unquoteParam(),
                delegatedTo = line.params["DELEGATED-TO"]?.unquoteParam(),
                delegatedFrom = line.params["DELEGATED-FROM"]?.unquoteParam(),
                sentBy = line.params["SENT-BY"]?.unquoteParam(),
                directory = line.params["DIR"]?.unquoteParam(),
                language = line.params["LANGUAGE"]?.unquoteParam(),
                scheduleAgent = line.params["SCHEDULE-AGENT"]?.uppercase(),
                scheduleForceSend = line.params["SCHEDULE-FORCE-SEND"]?.uppercase(),
                scheduleStatus = line.params["SCHEDULE-STATUS"]?.unquoteParam(),
            )
        }
        return ParticipantJson.encodeAttendees(attendees)
    }

    private fun String.stripMailto(): String =
        trim().removePrefixIgnoreCase("mailto:").unescapeIcal().trim()

    private fun String.removePrefixIgnoreCase(prefix: String): String =
        if (startsWith(prefix, ignoreCase = true)) drop(prefix.length) else this

    private fun String.unquoteParam(): String {
        val trimmed = trim()
        val unquoted = if (trimmed.length >= 2 && trimmed.first() == '"' && trimmed.last() == '"') {
            trimmed.drop(1).dropLast(1)
        } else {
            trimmed
        }
        return unquoted.replace("\\\"", "\"").replace("\\\\", "\\").unescapeIcal()
    }

    private fun Int.toColorText(): String =
        "#" + (this and 0x00FFFFFF).toString(16).uppercase().padStart(6, '0')

    private fun String.parseColorInt(): Int? {
        val normalized = trim().substringBefore(';').removePrefix("#")
        if (normalized.length != 6 || normalized.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) return null
        return 0xFF000000.toInt() or normalized.toInt(16)
    }

    /**
     * A text property's value. Only values marked as quoted-printable are decoded that way, and only
     * real HTML (a `data:text/html` value or recognisable tags) is flattened, so links with `?pwd=`,
     * `%20` or addresses in angle brackets are kept exactly as written.
     */
    private fun IcalLine.textValue(): String {
        val decoded = if (params["ENCODING"].equals("QUOTED-PRINTABLE", ignoreCase = true)) {
            decodeQuotedPrintable(value, params["CHARSET"])
        } else {
            value
        }
        val unescaped = decoded.unescapeIcal()
        val trimmed = unescaped.trimStart()
        return when {
            trimmed.startsWith("data:text/html", ignoreCase = true) && "," in trimmed ||
                trimmed.startsWith("text/html,", ignoreCase = true) ->
                unescaped.decodePercentHtmlPayload(params["CHARSET"])
            HTML_MARKUP.containsMatchIn(unescaped) -> unescaped.htmlToPlainText()
            else -> unescaped
        }
    }

    private fun String.decodePercentHtmlPayload(charsetName: String?): String {
        val charset = TextCharset.forNameOrUtf8(charsetName)
        var normalized = trimHtmlDataPrefix()
        if (normalized.contains(Regex("%[0-9A-Fa-f]{2}"))) {
            normalized = runCatching { urlDecode(normalized, charset) }.getOrDefault(normalized)
                .trimHtmlDataPrefix()
        }
        if (HTML_MARKUP.containsMatchIn(normalized)) normalized = normalized.htmlToPlainText()
        return normalized.trim()
    }

    private fun String.htmlToPlainText(): String =
        replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</(p|div|li|tr|h[1-6])\\s*>"), "\n")
            .withoutHtmlTags()
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&amp;", "&")
            .trim()

    /** What `<[^>]+>` removes, but in one pass: a backtracking regex rescans the rest for every `<`. */
    private fun String.withoutHtmlTags(): String {
        val out = StringBuilder(length)
        var position = 0
        while (position < length) {
            val open = indexOf('<', position)
            val close = if (open < 0) -1 else indexOf('>', open + 1)
            if (close < 0) break
            if (close == open + 1) {
                out.append(this, position, close + 1)
            } else {
                out.append(this, position, open)
            }
            position = close + 1
        }
        out.append(this, position, length)
        return out.toString()
    }

    private fun String.trimHtmlDataPrefix(): String {
        val trimmed = trim()
        return when {
            trimmed.startsWith("data:text/html", ignoreCase = true) && "," in trimmed -> trimmed.substringAfter(',')
            trimmed.startsWith("text/html,", ignoreCase = true) -> trimmed.substringAfter(',')
            else -> trimmed
        }
    }

    private fun decodeQuotedPrintable(value: String, charsetName: String?): String {
        val charset = TextCharset.forNameOrUtf8(charsetName)
        val bytes = ArrayList<Byte>(value.length)
        var index = 0
        while (index < value.length) {
            val char = value[index]
            if (char == '=' && index + 2 < value.length) {
                val hex = value.substring(index + 1, index + 3)
                val decoded = hex.toIntOrNull(16)
                if (decoded != null) {
                    bytes += decoded.toByte()
                    index += 3
                    continue
                }
            }
            if (char == '=' && index + 1 < value.length && value[index + 1] == '\n') {
                index += 2
                continue
            }
            if (char.code <= 0x7F) {
                bytes += char.code.toByte()
            } else {
                charset.encode(char.toString()).forEach { bytes += it }
            }
            index++
        }
        return charset.decode(bytes.toByteArray())
    }

    private data class IcalLine(
        val name: String,
        val params: Map<String, String>,
        val value: String,
    ) {
        val isDateOnly: Boolean = params["VALUE"].equals("DATE", ignoreCase = true)
    }

    private data class ComponentBlock(
        val lines: List<IcalLine>,
        /** The properties of each VALARM directly inside the component. */
        val alarms: List<List<IcalLine>>,
    )

    private data class ParsedDate(
        val epochMillis: Long,
        val isDateOnly: Boolean,
    )

    companion object {
        private val DATE = DateTimeFormatter.BASIC_ISO_DATE
        private val DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
        private const val DAY_MILLIS = 24L * 60L * 60L * 1000L
        private const val HOUR_MILLIS = 60L * 60L * 1000L
        private const val ICAL_LINE_LIMIT_BYTES = 75
        private const val RANGE_RECURRENCE_ID = "RECURRENCE-ID;RANGE=THISANDFUTURE"

        /** Organizer parameters the model owns, so that an edit may remove them. */
        private val MODELED_ORGANIZER_PARAMS = setOf("CN", "SENT-BY", "DIR", "LANGUAGE")
        private val TRIGGER_DURATION = Regex("P(?:(\\d+)W)?(?:(\\d+)D)?(?:T(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?)?")
        /** Tags that mark a value as HTML. Plain text with `<` and `>` (e-mail addresses, comparisons) stays text. */
        private val HTML_MARKUP = Regex(
            "(?i)</?(a|b|i|u|s|p|br|hr|div|span|font|em|strong|ul|ol|li|h[1-6]|table|tbody|thead|tr|td|th|blockquote|pre|code|img|sub|sup|strike|center|html|head|body|meta|style)(\\s[^<>]*)?/?>",
        )
        private val KNOWN_CALENDAR_PROPERTIES = setOf("VERSION", "PRODID", "CALSCALE")
        private val KNOWN_EVENT_PROPERTIES = setOf(
            "UID", "DTSTAMP", "SEQUENCE", "RECURRENCE-ID", "DTSTART", "DTEND", "DURATION",
            "SUMMARY", "STATUS", "CLASS", "TRANSP", "DESCRIPTION", "LOCATION", "CATEGORIES",
            "COLOR", "X-APPLE-CALENDAR-COLOR", "X-COLOR", "RRULE", "EXDATE", "RDATE",
            "ORGANIZER", "ATTENDEE", "LAST-MODIFIED",
        )
        private val KNOWN_TASK_PROPERTIES = setOf(
            "UID", "DTSTAMP", "SEQUENCE", "RECURRENCE-ID", "DTSTART", "DUE", "DURATION",
            "COMPLETED", "STATUS", "PRIORITY", "PERCENT-COMPLETE", "SUMMARY", "DESCRIPTION",
            "LOCATION", "URL", "CATEGORIES", "RELATED-TO", "RRULE", "EXDATE", "RDATE", "LAST-MODIFIED",
        )
        private val TEXT_PROPERTIES = setOf("SUMMARY", "DESCRIPTION", "LOCATION", "URL")
    }
}
