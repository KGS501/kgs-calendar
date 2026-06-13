package com.kgs.calendar.data.ical

import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.data.local.entity.withValidIcalSchedule
import com.kgs.calendar.domain.model.ComponentType
import com.kgs.calendar.domain.model.REMINDER_AT_END
import com.kgs.calendar.domain.model.isSupportedReminderOffset
import com.kgs.calendar.domain.model.normalizedReminderOffsets
import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import java.io.ByteArrayOutputStream
import java.io.StringReader
import java.nio.charset.Charset
import java.net.URLDecoder
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.util.Locale

class IcalCodec(
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    fun parse(
        rawIcs: String,
        collectionHref: String,
        resourceHref: String,
        collectionColor: Int,
    ): ParsedCalendarComponent? {
        runCatching { CalendarBuilder().build(StringReader(rawIcs)) }

        val components = components(rawIcs)
        val eventBlocks = components[ComponentType.Event].orEmpty()
        if (eventBlocks.isNotEmpty()) {
            val master = eventBlocks.firstOrNull { block -> block.lines.none { it.name == "RECURRENCE-ID" } }
                ?: eventBlocks.first()
            val parsedMaster = parseEvent(master, collectionHref, resourceHref, collectionColor) ?: return null
            val masterEvent = parsedMaster.event ?: return parsedMaster
            val overrides = eventBlocks
                .filter { it !== master }
                .mapNotNull { block ->
                    val recurrenceLine = block.lines.firstOrNull { it.name == "RECURRENCE-ID" } ?: return@mapNotNull null
                    val recurrenceId = parseIcalDate(recurrenceLine)?.epochMillis ?: return@mapNotNull null
                    val parsedOverride = parseEvent(block, collectionHref, resourceHref, collectionColor)?.event
                        ?: return@mapNotNull null
                    EventRecurrenceOverride.fromEvent(recurrenceId, parsedOverride)
                }
            return parsedMaster.copy(
                event = masterEvent.copy(
                    recurrenceOverridesJson = RecurrenceOverrideCodec.encodeEvents(overrides),
                ),
            )
        }
        val taskBlocks = components[ComponentType.Task].orEmpty()
        if (taskBlocks.isNotEmpty()) {
            val master = taskBlocks.firstOrNull { block -> block.lines.none { it.name == "RECURRENCE-ID" } }
                ?: taskBlocks.first()
            val parsedMaster = parseTask(master, collectionHref, resourceHref, collectionColor) ?: return null
            val masterTask = parsedMaster.task ?: return parsedMaster
            val overrides = taskBlocks
                .filter { it !== master }
                .mapNotNull { block ->
                    val recurrenceLine = block.lines.firstOrNull { it.name == "RECURRENCE-ID" } ?: return@mapNotNull null
                    val recurrenceId = parseIcalDate(recurrenceLine)?.epochMillis ?: return@mapNotNull null
                    val parsed = parseTask(block, collectionHref, resourceHref, collectionColor)?.task
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
                        remindersCsv = if (block.reminderMinutes.isNotEmpty()) parsed.remindersCsv else masterTask.remindersCsv,
                    )
                    TaskRecurrenceOverride.fromTask(recurrenceId, parsedOverride)
                }
            return parsedMaster.copy(
                task = masterTask.copy(
                    recurrenceOverridesJson = RecurrenceOverrideCodec.encodeTasks(overrides),
                ),
            )
        }
        return null
    }

    fun parseAll(
        rawIcs: String,
        collectionHref: String,
        resourceHrefPrefix: String,
        collectionColor: Int,
    ): List<ParsedCalendarComponent> {
        val components = components(rawIcs)
        val events = components[ComponentType.Event].orEmpty().mapNotNull { block ->
            val uid = block.lines.firstOrNull { it.name == "UID" }?.value ?: return@mapNotNull null
            parseEvent(block, collectionHref, "$resourceHrefPrefix/${uid.safeResourceName()}.ics", collectionColor)
        }
        val tasks = components[ComponentType.Task].orEmpty().mapNotNull { block ->
            val uid = block.lines.firstOrNull { it.name == "UID" }?.value ?: return@mapNotNull null
            parseTask(block, collectionHref, "$resourceHrefPrefix/${uid.safeResourceName()}.ics", collectionColor)
        }
        return events + tasks
    }

    fun serializeEvent(event: EventEntity, originalRawIcs: String? = null): String {
        val now = utcStamp(Instant.now().toEpochMilli())
        val generated = buildString {
            appendCalendarHeader()
            appendTimezoneComponent(event.timezoneId)
            appendLine("BEGIN:VEVENT")
            appendLine("UID:${escape(event.uid)}")
            appendLine("DTSTAMP:$now")
            appendLine("SEQUENCE:${event.sequence.coerceAtLeast(0)}")
            appendDateProperty("DTSTART", event.startsAtMillis, event.allDay, event.timezoneId)
            appendDateProperty("DTEND", event.endsAtMillis, event.allDay, event.timezoneId)
            appendLine("SUMMARY:${escape(event.title)}")
            event.status?.takeIf { it.isNotBlank() }?.let { appendLine("STATUS:${it.uppercase(Locale.US)}") }
            event.classification?.takeIf { it.isNotBlank() }?.let { appendLine("CLASS:${it.uppercase(Locale.US)}") }
            event.transparency?.takeIf { it.isNotBlank() }?.let { appendLine("TRANSP:${it.uppercase(Locale.US)}") }
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
            RecurrenceOverrideCodec.decodeEvents(event.recurrenceOverridesJson).forEach { override ->
                appendEventOverride(event, override, now)
            }
            appendCalendarFooter()
        }
        return foldIcal(preserveUnsupportedData(originalRawIcs, generated, ComponentType.Event))
    }

    private fun StringBuilder.appendOrganizer(organizerJson: String?) {
        val email = organizerJson.jsonField("email")?.trim().orEmpty()
        if (email.isBlank()) return
        val name = organizerJson.jsonField("name")?.trim().orEmpty()
        val params = buildString {
            if (name.isNotBlank()) append(";CN=${escapeParam(name)}")
            organizerJson.jsonField("sentBy")?.takeIf { it.isNotBlank() }?.let { append(";SENT-BY=${escapeParam(it)}") }
            organizerJson.jsonField("directory")?.takeIf { it.isNotBlank() }?.let { append(";DIR=${escapeParam(it)}") }
            organizerJson.jsonField("language")?.takeIf { it.isNotBlank() }?.let { append(";LANGUAGE=${escapeParam(it)}") }
        }
        appendLine("ORGANIZER$params:${calendarUserAddress(email)}")
    }

    private fun StringBuilder.appendAttendees(attendeesJson: String?) {
        attendeesJson.jsonObjectBodies().forEach { attendeeJson ->
            val email = attendeeJson.jsonField("email")?.trim().orEmpty()
            if (email.isBlank()) return@forEach
            val name = attendeeJson.jsonField("name")?.trim().orEmpty()
            val partstat = attendeeJson.jsonField("partstat")?.takeIf { it.isNotBlank() }
                ?.uppercase(Locale.US) ?: "NEEDS-ACTION"
            val role = attendeeJson.jsonField("role")?.takeIf { it.isNotBlank() }
                ?.uppercase(Locale.US) ?: "REQ-PARTICIPANT"
            val rsvp = attendeeJson.jsonField("rsvp")?.trim().orEmpty()
            val scheduleStatus = attendeeJson.jsonField("scheduleStatus")?.trim().orEmpty()
            val params = buildString {
                if (name.isNotBlank()) append(";CN=${escapeParam(name)}")
                attendeeJson.jsonField("calendarUserType")?.takeIf { it.isNotBlank() }?.let { append(";CUTYPE=${it.uppercase(Locale.US)}") }
                attendeeJson.jsonField("member")?.takeIf { it.isNotBlank() }?.let { append(";MEMBER=${escapeParam(it)}") }
                if (role.isNotBlank()) append(";ROLE=$role")
                if (partstat.isNotBlank()) append(";PARTSTAT=$partstat")
                if (rsvp.isNotBlank()) append(";RSVP=${rsvp.uppercase(Locale.US)}")
                attendeeJson.jsonField("delegatedTo")?.takeIf { it.isNotBlank() }?.let { append(";DELEGATED-TO=${escapeParam(it)}") }
                attendeeJson.jsonField("delegatedFrom")?.takeIf { it.isNotBlank() }?.let { append(";DELEGATED-FROM=${escapeParam(it)}") }
                attendeeJson.jsonField("sentBy")?.takeIf { it.isNotBlank() }?.let { append(";SENT-BY=${escapeParam(it)}") }
                attendeeJson.jsonField("directory")?.takeIf { it.isNotBlank() }?.let { append(";DIR=${escapeParam(it)}") }
                attendeeJson.jsonField("language")?.takeIf { it.isNotBlank() }?.let { append(";LANGUAGE=${escapeParam(it)}") }
                attendeeJson.jsonField("scheduleAgent")?.takeIf { it.isNotBlank() }?.let { append(";SCHEDULE-AGENT=${it.uppercase(Locale.US)}") }
                attendeeJson.jsonField("scheduleForceSend")?.takeIf { it.isNotBlank() }?.let { append(";SCHEDULE-FORCE-SEND=${it.uppercase(Locale.US)}") }
                if (scheduleStatus.isNotBlank()) append(";SCHEDULE-STATUS=${escapeParam(scheduleStatus)}")
            }
            appendLine("ATTENDEE$params:${calendarUserAddress(email)}")
        }
    }

    fun serializeTask(task: TaskEntity, originalRawIcs: String? = null): String {
        val now = utcStamp(Instant.now().toEpochMilli())
        val normalizedTask = task.withValidIcalSchedule()
        val generated = buildString {
            appendCalendarHeader()
            appendTimezoneComponent(normalizedTask.timezoneId)
            appendLine("BEGIN:VTODO")
            appendLine("UID:${escape(normalizedTask.uid)}")
            appendLine("DTSTAMP:$now")
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
            RecurrenceOverrideCodec.decodeTasks(normalizedTask.recurrenceOverridesJson).forEach { override ->
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
    ): ParsedCalendarComponent? {
        val lines = block.lines
        val values = lines.associateBy { it.name }
        val uid = values["UID"]?.value ?: return null
        val startLine = values["DTSTART"] ?: values["RECURRENCE-ID"] ?: return null
        val start = parseIcalDate(startLine) ?: return null
        val end = values["DTEND"]?.let { parseIcalDate(it) }
            ?: values["DURATION"]?.value?.let { duration ->
                parseDurationMillis(duration)?.let { ParsedDate(start.epochMillis + it, start.isDateOnly) }
            }
            ?: start.plusDefaultDuration(startLine.isDateOnly)
        val title = values["SUMMARY"]?.textValue()?.ifBlank { "Untitled event" } ?: "Untitled event"
        val rrule = values["RRULE"]?.value?.takeIf { it.isNotBlank() }
        // EXDATE can appear multiple times and each value may be comma-separated.
        val exDateMillis = lines.asSequence()
            .filter { it.name == "EXDATE" }
            .flatMap { line ->
                line.value.split(',').asSequence().map { v ->
                    parseIcalDate(IcalLine("EXDATE", line.params, v.trim()))?.epochMillis
                }
            }
            .filterNotNull()
            .toList()
        val rDateMillis = lines.asSequence()
            .filter { it.name == "RDATE" }
            .flatMap { line ->
                line.value.split(',').asSequence().map { value ->
                    parseIcalDate(IcalLine("RDATE", line.params, value.trim().substringBefore('/')))?.epochMillis
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
            timezoneId = startLine.params["TZID"]?.unquoteParam()?.takeIf { it.isNotBlank() },
            sequence = values["SEQUENCE"]?.value?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
            remindersCsv = block.reminderMinutes.normalizedReminderOffsets().takeIf { it.isNotEmpty() }?.joinToString(","),
            status = values["STATUS"]?.value?.trim()?.uppercase(Locale.US)?.takeIf { it.isNotBlank() },
            classification = values["CLASS"]?.value?.trim()?.uppercase(Locale.US)?.takeIf { it.isNotBlank() },
            transparency = values["TRANSP"]?.value?.trim()?.uppercase(Locale.US)?.takeIf { it.isNotBlank() },
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
    ): ParsedCalendarComponent? {
        val lines = block.lines
        val values = lines.associateBy { it.name }
        val uid = values["UID"]?.value ?: return null
        val completedAt = values["COMPLETED"]?.let { parseIcalDate(it)?.epochMillis }
        val rawStatus = values["STATUS"]?.value?.trim()?.uppercase(Locale.US)?.takeIf { it.isNotBlank() }
        val normalizedStatus = when (rawStatus) {
            "NEEDS-ACTION", "IN-PROCESS", "COMPLETED", "CANCELLED" -> rawStatus
            else -> rawStatus // keep unknown values as-is rather than dropping them
        }
        val statusCompleted = normalizedStatus == "COMPLETED"
        val title = values["SUMMARY"]?.textValue()?.ifBlank { "Untitled task" } ?: "Untitled task"
        val due = values["DUE"]?.let { parseIcalDate(it) }
        val start = values["DTSTART"]?.let { parseIcalDate(it) }
        val exDateMillis = lines.asSequence()
            .filter { it.name == "EXDATE" }
            .flatMap { line ->
                line.value.split(',').asSequence().map { v ->
                    parseIcalDate(IcalLine("EXDATE", line.params, v.trim()))?.epochMillis
                }
            }
            .filterNotNull()
            .toList()
        val rDateMillis = lines.asSequence()
            .filter { it.name == "RDATE" }
            .flatMap { line ->
                line.value.split(',').asSequence().map { value ->
                    parseIcalDate(IcalLine("RDATE", line.params, value.trim().substringBefore('/')))?.epochMillis
                }
            }
            .filterNotNull()
            .toList()
        val sourceTimeZone = (values["DTSTART"] ?: values["DUE"])
            ?.params?.get("TZID")?.unquoteParam()?.takeIf { it.isNotBlank() }
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
                    line.params["RELTYPE"]?.unquoteParam()?.uppercase(Locale.US).let { it == null || it == "PARENT" }
            }?.textValue()?.takeIf { it.isNotBlank() },
            recurrenceRule = values["RRULE"]?.value,
            exDatesCsv = exDateMillis.takeIf { it.isNotEmpty() }?.joinToString(","),
            rDatesCsv = rDateMillis.takeIf { it.isNotEmpty() }?.joinToString(","),
            timezoneId = sourceTimeZone,
            sequence = values["SEQUENCE"]?.value?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
            remindersCsv = block.reminderMinutes.normalizedReminderOffsets().takeIf { it.isNotEmpty() }?.joinToString(","),
            color = color,
        ).withValidIcalSchedule()
        return ParsedCalendarComponent(uid, ComponentType.Task, null, task)
    }

    private fun components(rawIcs: String): Map<String, List<ComponentBlock>> {
        val result = mutableMapOf<String, MutableList<ComponentBlock>>()
        var activeName: String? = null
        var activeLines = mutableListOf<IcalLine>()
        var activeReminders = mutableListOf<Int>()
        // Depth of nested BEGIN blocks (VALARM, VTIMEZONE, STANDARD, DAYLIGHT, ...) within
        // the currently active VEVENT/VTODO. Lines inside nested blocks must not be collected
        // and their END must not close the outer component. We do, however, peek at TRIGGER
        // lines inside VALARM blocks to recover reminder offsets.
        var nestedDepth = 0
        for (line in unfold(rawIcs)) {
            val upper = line.uppercase(Locale.US)
            when {
                activeName == null && upper == "BEGIN:VEVENT" -> {
                    activeName = ComponentType.Event
                    activeLines = mutableListOf()
                    activeReminders = mutableListOf()
                    nestedDepth = 0
                }
                activeName == null && upper == "BEGIN:VTODO" -> {
                    activeName = ComponentType.Task
                    activeLines = mutableListOf()
                    activeReminders = mutableListOf()
                    nestedDepth = 0
                }
                activeName != null && upper.startsWith("BEGIN:") -> {
                    nestedDepth++
                }
                activeName != null && nestedDepth > 0 && upper.startsWith("END:") -> {
                    nestedDepth--
                }
                activeName != null && nestedDepth == 0 &&
                    (upper == "END:VEVENT" || upper == "END:VTODO") -> {
                    result.getOrPut(activeName) { mutableListOf() }
                        .add(ComponentBlock(activeLines, activeReminders.distinct()))
                    activeName = null
                }
                activeName != null && nestedDepth > 0 && upper.startsWith("TRIGGER") -> {
                    parseLine(line)?.let { triggerLine ->
                        reminderMinutesFromTrigger(triggerLine)?.let { activeReminders += it }
                    }
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

    /**
     * Converts a VALARM TRIGGER into minutes-before-start. Handles the common relative
     * form (e.g. -PT15M, -PT1H, -P1D, -P1DT2H). Positive (after-start) and absolute
     * DATE-TIME triggers are ignored (returned as null) since the UI models only
     * before-offsets.
     */
    private fun reminderMinutesFromTrigger(line: IcalLine): Int? {
        if (line.params["VALUE"].equals("DATE-TIME", ignoreCase = true)) return null
        val raw = line.value.trim().uppercase(Locale.US)
        if (raw.isEmpty()) return null
        val relatedToEnd = line.params["RELATED"].equals("END", ignoreCase = true)
        val negative = raw.startsWith("-")
        val body = raw.trimStart('-', '+')
        if (!body.startsWith("P")) return null
        val regex = Regex("P(?:(\\d+)W)?(?:(\\d+)D)?(?:T(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?)?")
        val match = regex.matchEntire(body) ?: return null
        val (w, d, h, m, s) = match.destructured
        val minutes = (w.toLongOrNull() ?: 0) * 7 * 24 * 60 +
            (d.toLongOrNull() ?: 0) * 24 * 60 +
            (h.toLongOrNull() ?: 0) * 60 +
            (m.toLongOrNull() ?: 0) +
            (s.toLongOrNull() ?: 0) / 60
        // The UI represents before-start offsets, exact start, and exact end.
        return when {
            relatedToEnd && minutes == 0L -> REMINDER_AT_END
            negative || minutes == 0L -> minutes.toInt()
            else -> null
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
            if (split.size == 2) split[0].uppercase(Locale.US) to split[1] else null
        }.toMap()
        return IcalLine(parts.first().uppercase(Locale.US), params, value)
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

    private fun parseIcalDate(line: IcalLine): ParsedDate? {
        val value = line.value
        val isDateOnly = line.isDateOnly || value.length == 8
        val sourceZone = line.params["TZID"]
            ?.unquoteParam()
            ?.let { id -> runCatching { ZoneId.of(id) }.getOrNull() }
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

    private fun parseDurationMillis(value: String): Long? {
        val normalized = value.trim().uppercase(Locale.US)
        val sign = if (normalized.startsWith("-")) -1 else 1
        val body = normalized.trimStart('+', '-')
        val weeks = Regex("""P(\d+)W""").matchEntire(body)?.groupValues?.getOrNull(1)?.toLongOrNull()
        if (weeks != null) return sign * weeks * 7L * DAY_MILLIS
        return runCatching { sign * Duration.parse(body).toMillis() }.getOrNull()
    }

    private fun ParsedDate.plusDefaultDuration(allDay: Boolean): ParsedDate =
        ParsedDate(epochMillis + if (allDay) DAY_MILLIS else HOUR_MILLIS, allDay)

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
        val sourceZone = timezoneId?.let { runCatching { ZoneId.of(it) }.getOrNull() }
        if (sourceZone == null) {
            appendLine("$name:${utcStamp(epochMillis)}")
        } else {
            appendLine("$name;TZID=${escapeParamToken(timezoneId)}:${localStamp(epochMillis, sourceZone)}")
        }
    }

    private fun StringBuilder.appendTimezoneComponent(timezoneId: String?) {
        val id = timezoneId?.takeIf { it.isNotBlank() } ?: return
        val component = runCatching {
            TimeZoneRegistryFactory.getInstance()
                .createRegistry()
                .getTimeZone(id)
                ?.vTimeZone
                ?.toString()
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: return
        append(component.replace("\r\n", "\n").trimEnd())
        appendLine()
    }

    private fun StringBuilder.appendEventOverride(
        master: EventEntity,
        override: EventRecurrenceOverride,
        now: String,
    ) {
        appendLine("BEGIN:VEVENT")
        appendLine("UID:${escape(master.uid)}")
        appendLine("DTSTAMP:$now")
        appendLine("SEQUENCE:${master.sequence.coerceAtLeast(0)}")
        appendDateProperty(
            "RECURRENCE-ID",
            override.recurrenceIdMillis,
            master.allDay,
            master.timezoneId,
        )
        appendDateProperty("DTSTART", override.startsAtMillis, override.allDay, override.timezoneId)
        appendDateProperty("DTEND", override.endsAtMillis, override.allDay, override.timezoneId)
        appendLine("SUMMARY:${escape(override.title)}")
        override.status?.takeIf { it.isNotBlank() }?.let { appendLine("STATUS:${it.uppercase(Locale.US)}") }
        override.classification?.takeIf { it.isNotBlank() }?.let { appendLine("CLASS:${it.uppercase(Locale.US)}") }
        override.transparency?.takeIf { it.isNotBlank() }?.let { appendLine("TRANSP:${it.uppercase(Locale.US)}") }
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
        appendLine("SEQUENCE:${master.sequence.coerceAtLeast(0)}")
        appendDateProperty(
            "RECURRENCE-ID",
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
     */
    private fun preserveUnsupportedData(
        originalRawIcs: String?,
        generatedRawIcs: String,
        componentType: String,
    ): String {
        if (originalRawIcs.isNullOrBlank()) return generatedRawIcs
        val originalBlocks = extractComponentBlocks(originalRawIcs, componentType)
        if (originalBlocks.isEmpty()) return generatedRawIcs
        val unknownByKey = originalBlocks.associate { block ->
            componentIdentity(block) to extractUnsupportedComponentLines(block, componentType)
        }
        var merged = injectUnsupportedComponentLines(generatedRawIcs, componentType, unknownByKey)

        val generatedUpper = merged.uppercase(Locale.US)
        val originalTimeZones = extractComponentBlocks(originalRawIcs, "VTIMEZONE")
        originalTimeZones.forEach { block ->
            val tzid = block.firstOrNull { propertyName(it) == "TZID" }?.substringAfter(':')?.trim()
            if (tzid != null && !generatedUpper.contains("TZID:${tzid.uppercase(Locale.US)}")) {
                merged = merged.replaceFirst(
                    "BEGIN:$componentType",
                    block.joinToString("\r\n", postfix = "\r\n") + "BEGIN:$componentType",
                )
            }
        }

        val originalCalendarProperties = unfold(originalRawIcs)
            .dropWhile { !it.equals("BEGIN:VCALENDAR", true) }
            .drop(1)
            .takeWhile { !it.uppercase(Locale.US).startsWith("BEGIN:") && !it.equals("END:VCALENDAR", true) }
            .filter { propertyName(it) !in KNOWN_CALENDAR_PROPERTIES }
        originalCalendarProperties.forEach { line ->
            if (!merged.lineSequence().any { it.equals(line, true) }) {
                merged = merged.replace("CALSCALE:GREGORIAN", "CALSCALE:GREGORIAN\r\n$line")
            }
        }
        return merged
    }

    private fun extractComponentBlocks(rawIcs: String, componentType: String): List<List<String>> {
        val begin = "BEGIN:${componentType.uppercase(Locale.US)}"
        val end = "END:${componentType.uppercase(Locale.US)}"
        val result = mutableListOf<List<String>>()
        var active: MutableList<String>? = null
        var depth = 0
        unfold(rawIcs).forEach { line ->
            val upper = line.uppercase(Locale.US)
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

    private fun componentIdentity(block: List<String>): String {
        val recurrence = block.firstOrNull { propertyName(it) == "RECURRENCE-ID" }
        return recurrence?.substringAfter(':')?.trim()?.let { "override:$it" } ?: "master"
    }

    private fun extractUnsupportedComponentLines(block: List<String>, componentType: String): List<String> {
        val known = if (componentType == ComponentType.Event) KNOWN_EVENT_PROPERTIES else KNOWN_TASK_PROPERTIES
        val result = mutableListOf<String>()
        var index = 1
        while (index < block.lastIndex) {
            val line = block[index]
            if (line.uppercase(Locale.US).startsWith("BEGIN:")) {
                val nestedName = line.substringAfter(':').uppercase(Locale.US)
                val nested = mutableListOf(line)
                var depth = 1
                index++
                while (index < block.lastIndex && depth > 0) {
                    val nestedLine = block[index]
                    nested += nestedLine
                    if (nestedLine.uppercase(Locale.US).startsWith("BEGIN:")) depth++
                    if (nestedLine.uppercase(Locale.US).startsWith("END:")) depth--
                    index++
                }
                if (nestedName != "VALARM" || !isRepresentedAlarm(nested)) result += nested
                continue
            }
            val name = propertyName(line)
            if (name !in known || (name == "RDATE" && line.substringAfter(':', "").contains('/'))) {
                result += line
            }
            index++
        }
        return result
    }

    private fun isRepresentedAlarm(block: List<String>): Boolean {
        val action = block.firstOrNull { propertyName(it) == "ACTION" }?.substringAfter(':')?.trim()
        val trigger = block.firstOrNull { propertyName(it) == "TRIGGER" }?.let(::parseLine)
        return action.equals("DISPLAY", true) && trigger?.let(::reminderMinutesFromTrigger) != null
    }

    private fun injectUnsupportedComponentLines(
        generated: String,
        componentType: String,
        unknownByKey: Map<String, List<String>>,
    ): String {
        val normalized = generated.replace("\r\n", "\n").replace('\r', '\n')
        val out = mutableListOf<String>()
        var block = mutableListOf<String>()
        var inComponent = false
        var depth = 0
        normalized.lines().forEach { line ->
            val upper = line.uppercase(Locale.US)
            if (!inComponent && upper == "BEGIN:$componentType") {
                inComponent = true
                depth = 1
                block = mutableListOf(line)
            } else if (inComponent) {
                block += line
                if (upper.startsWith("BEGIN:")) depth++
                if (upper.startsWith("END:")) depth--
                if (upper == "END:$componentType" && depth == 0) {
                    val identity = componentIdentity(block)
                    val unsupported = unknownByKey[identity].orEmpty()
                    if (unsupported.any { propertyName(it) == "RDATE" }) {
                        block.removeAll { propertyName(it) == "RDATE" }
                    }
                    block.addAll(block.lastIndex, unsupported)
                    out += block
                    inComponent = false
                }
            } else {
                out += line
            }
        }
        return out.joinToString("\r\n").trimEnd() + "\r\n"
    }

    private fun propertyName(line: String): String =
        line.substringBefore(':').substringBefore(';').uppercase(Locale.US)

    private fun foldIcal(rawIcs: String): String {
        val physicalLines = mutableListOf<String>()
        rawIcs.replace("\r\n", "\n").replace('\r', '\n').trimEnd().lines().forEach { line ->
            var remaining = line
            var first = true
            var limit = ICAL_LINE_LIMIT_BYTES
            while (remaining.toByteArray(Charsets.UTF_8).size > limit) {
                var splitAt = remaining.length
                while (splitAt > 1 && remaining.substring(0, splitAt).toByteArray(Charsets.UTF_8).size > limit) {
                    splitAt--
                }
                if (
                    splitAt in 1 until remaining.length &&
                    Character.isHighSurrogate(remaining[splitAt - 1]) &&
                    Character.isLowSurrogate(remaining[splitAt])
                ) {
                    splitAt--
                }
                physicalLines += (if (first) "" else " ") + remaining.substring(0, splitAt)
                remaining = remaining.substring(splitAt)
                first = false
                limit = ICAL_LINE_LIMIT_BYTES - 1
            }
            physicalLines += (if (first) "" else " ") + remaining
        }
        return physicalLines.joinToString("\r\n", postfix = "\r\n")
    }

    private fun StringBuilder.appendCalendarHeader() {
        appendLine("BEGIN:VCALENDAR")
        appendLine("VERSION:2.0")
        appendLine("PRODID:-//KGS Calendar//Android//EN")
        appendLine("CALSCALE:GREGORIAN")
    }

    private fun StringBuilder.appendCalendarFooter() {
        appendLine("END:VCALENDAR")
    }

    private fun utcStamp(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atOffset(ZoneOffset.UTC).format(DATE_TIME) + "Z"

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
            .distinctBy { it.lowercase(Locale.US) }
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
        return jsonObject(
            "name" to (name ?: email),
            "email" to email,
            "sentBy" to params["SENT-BY"]?.unquoteParam(),
            "directory" to params["DIR"]?.unquoteParam(),
            "language" to params["LANGUAGE"]?.unquoteParam(),
        )
    }

    private fun List<IcalLine>.toAttendeesJson(): String? {
        if (isEmpty()) return null
        val attendees = mapNotNull { line ->
            val email = line.value.stripMailto()
            if (email.isBlank()) return@mapNotNull null
            val name = line.params["CN"]?.unquoteParam()?.ifBlank { null } ?: email
            jsonObject(
                "name" to name,
                "email" to email,
                "partstat" to (line.params["PARTSTAT"]?.uppercase(Locale.US) ?: "NEEDS-ACTION"),
                "role" to (line.params["ROLE"]?.uppercase(Locale.US) ?: "REQ-PARTICIPANT"),
                "rsvp" to line.params["RSVP"]?.uppercase(Locale.US),
                "calendarUserType" to line.params["CUTYPE"]?.uppercase(Locale.US),
                "member" to line.params["MEMBER"]?.unquoteParam(),
                "delegatedTo" to line.params["DELEGATED-TO"]?.unquoteParam(),
                "delegatedFrom" to line.params["DELEGATED-FROM"]?.unquoteParam(),
                "sentBy" to line.params["SENT-BY"]?.unquoteParam(),
                "directory" to line.params["DIR"]?.unquoteParam(),
                "language" to line.params["LANGUAGE"]?.unquoteParam(),
                "scheduleAgent" to line.params["SCHEDULE-AGENT"]?.uppercase(Locale.US),
                "scheduleForceSend" to line.params["SCHEDULE-FORCE-SEND"]?.uppercase(Locale.US),
                "scheduleStatus" to line.params["SCHEDULE-STATUS"]?.unquoteParam(),
            )
        }
        return attendees.takeIf { it.isNotEmpty() }?.joinToString(prefix = "[", postfix = "]")
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

    private fun jsonObject(vararg fields: Pair<String, String?>): String =
        fields.joinToString(prefix = "{", postfix = "}") { (key, value) ->
            val encodedValue = value?.let { "\"${it.escapeJson()}\"" } ?: "null"
            "\"${key.escapeJson()}\":$encodedValue"
        }

    private fun String.escapeJson(): String =
        buildString(length) {
            this@escapeJson.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(char)
                }
            }
        }

    private fun String?.jsonField(field: String): String? {
        if (isNullOrBlank()) return null
        val pattern = Regex("\"${Regex.escape(field)}\"\\s*:\\s*(null|\"((?:\\\\.|[^\"\\\\])*)\")")
        val match = pattern.find(this) ?: return null
        if (match.groupValues[1] == "null") return null
        return match.groupValues[2].unescapeJson()
    }

    private fun String?.jsonObjectBodies(): List<String> {
        val text = this?.trim().orEmpty()
        if (text.isBlank()) return emptyList()
        val result = mutableListOf<String>()
        var depth = 0
        var start = -1
        var inString = false
        var escaped = false
        text.forEachIndexed { index, char ->
            when {
                escaped -> escaped = false
                inString && char == '\\' -> escaped = true
                inString && char == '"' -> inString = false
                inString -> Unit
                char == '"' -> inString = true
                char == '{' -> {
                    if (depth == 0) start = index
                    depth++
                }
                char == '}' && depth > 0 -> {
                    depth--
                    if (depth == 0 && start >= 0) {
                        result += text.substring(start, index + 1)
                        start = -1
                    }
                }
            }
        }
        return result
    }

    private fun String.unescapeJson(): String =
        buildString(length) {
            var index = 0
            while (index < this@unescapeJson.length) {
                val char = this@unescapeJson[index]
                if (char == '\\' && index + 1 < this@unescapeJson.length) {
                    when (val escaped = this@unescapeJson[index + 1]) {
                        '\\' -> append('\\')
                        '"' -> append('"')
                        'n' -> append('\n')
                        'r' -> append('\r')
                        't' -> append('\t')
                        'u' -> {
                            val hex = this@unescapeJson.drop(index + 2).take(4)
                            val decoded = hex.takeIf { it.length == 4 }?.toIntOrNull(16)
                            if (decoded != null) {
                                append(decoded.toChar())
                                index += 4
                            } else {
                                append("\\u")
                            }
                        }
                        else -> append(escaped)
                    }
                    index += 2
                } else {
                    append(char)
                    index++
                }
            }
        }

    private fun Int.toColorText(): String =
        "#%06X".format(this and 0x00FFFFFF)

    private fun String.parseColorInt(): Int? {
        val normalized = trim().substringBefore(';').removePrefix("#")
        if (normalized.length != 6 || normalized.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) return null
        return 0xFF000000.toInt() or normalized.toInt(16)
    }

    private fun IcalLine.textValue(): String {
        val decoded = if (
            params["ENCODING"].equals("QUOTED-PRINTABLE", ignoreCase = true) ||
            value.contains(Regex("=[0-9A-Fa-f]{2}"))
        ) {
            decodeQuotedPrintable(value, params["CHARSET"])
        } else {
            value
        }
        val unescaped = decoded.unescapeIcal()
        return if (
            unescaped.startsWith("data:text/html", ignoreCase = true) ||
            unescaped.startsWith("text/html,", ignoreCase = true) ||
            unescaped.contains(Regex("%[0-9A-Fa-f]{2}")) ||
            ('<' in unescaped && '>' in unescaped)
        ) {
            unescaped.decodePercentHtmlPayload(params["CHARSET"])
        } else {
            unescaped
        }
    }

    private fun String.decodePercentHtmlPayload(charsetName: String?): String {
        val charset = runCatching { Charset.forName(charsetName ?: "UTF-8") }.getOrDefault(Charsets.UTF_8)
        var normalized = trimHtmlDataPrefix()
        if (normalized.contains(Regex("%[0-9A-Fa-f]{2}"))) {
            normalized = runCatching { URLDecoder.decode(normalized, charset.name()) }.getOrDefault(normalized)
                .trimHtmlDataPrefix()
        }
        if ('<' in normalized && '>' in normalized) {
            normalized = normalized
                .replace(Regex("(?i)<br\\s*/?>"), "\n")
                .replace(Regex("(?i)</p\\s*>"), "\n")
                .replace(Regex("(?s)<[^>]+>"), "")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
        }
        return normalized.trim()
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
        val charset = runCatching { Charset.forName(charsetName ?: "UTF-8") }.getOrDefault(Charsets.UTF_8)
        val bytes = ByteArrayOutputStream(value.length)
        var index = 0
        while (index < value.length) {
            val char = value[index]
            if (char == '=' && index + 2 < value.length) {
                val hex = value.substring(index + 1, index + 3)
                val decoded = hex.toIntOrNull(16)
                if (decoded != null) {
                    bytes.write(decoded)
                    index += 3
                    continue
                }
            }
            if (char == '=' && index + 1 < value.length && value[index + 1] == '\n') {
                index += 2
                continue
            }
            if (char.code <= 0x7F) {
                bytes.write(char.code)
            } else {
                bytes.write(char.toString().toByteArray(charset))
            }
            index++
        }
        return String(bytes.toByteArray(), charset)
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
        val reminderMinutes: List<Int>,
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
        private val KNOWN_CALENDAR_PROPERTIES = setOf("VERSION", "PRODID", "CALSCALE")
        private val KNOWN_EVENT_PROPERTIES = setOf(
            "UID", "DTSTAMP", "SEQUENCE", "RECURRENCE-ID", "DTSTART", "DTEND", "DURATION",
            "SUMMARY", "STATUS", "CLASS", "TRANSP", "DESCRIPTION", "LOCATION", "CATEGORIES",
            "COLOR", "X-APPLE-CALENDAR-COLOR", "X-COLOR", "RRULE", "EXDATE", "RDATE",
            "ORGANIZER", "ATTENDEE",
        )
        private val KNOWN_TASK_PROPERTIES = setOf(
            "UID", "DTSTAMP", "SEQUENCE", "RECURRENCE-ID", "DTSTART", "DUE", "DURATION",
            "COMPLETED", "STATUS", "PRIORITY", "PERCENT-COMPLETE", "SUMMARY", "DESCRIPTION",
            "LOCATION", "URL", "CATEGORIES", "RELATED-TO", "RRULE", "EXDATE", "RDATE",
        )
    }
}
