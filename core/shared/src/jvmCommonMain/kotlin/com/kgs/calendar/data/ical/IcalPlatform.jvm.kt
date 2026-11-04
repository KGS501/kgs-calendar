package com.kgs.calendar.data.ical

import com.kgs.calendar.time.ZoneId
import kotlinx.datetime.toJavaZoneId
import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.Recur
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.component.VTimeZone
import net.fortuna.ical4j.model.parameter.Value
import java.io.StringReader
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** ical4j, as the Android app has always used it. */
internal actual object IcalPlatform {
    private val timeZoneRegistry by lazy { TimeZoneRegistryFactory.getInstance().createRegistry() }
    private val DATE: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE

    actual fun validateCalendar(text: String) {
        CalendarBuilder().build(StringReader(text))
    }

    actual fun vtimezoneOffsetsSeconds(block: String, samplesMillis: List<Long>): List<Int>? {
        val definition = runCatching {
            val calendar = CalendarBuilder().build(
                StringReader("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//KGS//TZ//EN\r\n$block\r\nEND:VCALENDAR\r\n"),
            )
            val component = calendar.getComponent<VTimeZone>("VTIMEZONE") ?: return null
            net.fortuna.ical4j.model.TimeZone(component)
        }.getOrNull() ?: return null
        return samplesMillis.map { definition.getOffset(it) / 1000 }
    }

    actual fun vtimezoneText(zoneId: String): String? = runCatching {
        TimeZoneRegistryFactory.getInstance()
            .createRegistry()
            .getTimeZone(zoneId)
            ?.vTimeZone
            ?.toString()
    }.getOrNull()

    /**
     * Not ical4j's Recur.getDates: that materializes the whole set up front, and its walk from DTSTART
     * has no limit. [CommonIcal]'s expander has one, and CommonIcalVsIcal4jTest keeps the two in step.
     */
    actual fun recurrenceStarts(
        rule: String,
        allDay: Boolean,
        seedMillis: Long,
        zone: ZoneId,
        searchStartMillis: Long,
        rangeEndMillis: Long,
    ): List<Long>? = CommonIcal.recurrenceStarts(rule, allDay, seedMillis, zone, searchStartMillis, rangeEndMillis)

    /** ical4j's answer for the same question; only safe with trusted rules, as in tests. */
    internal fun ical4jRecurrenceStarts(
        rule: String,
        allDay: Boolean,
        seedMillis: Long,
        zone: ZoneId,
        searchStartMillis: Long,
        rangeEndMillis: Long,
    ): List<Long>? = runCatching {
        val sourceZone = zone.toJavaZoneId()
        val recur = Recur(rule)
        val valueType = if (allDay) Value.DATE else Value.DATE_TIME
        val seed: net.fortuna.ical4j.model.Date
        val rangeStart: net.fortuna.ical4j.model.Date
        val rangeEnd: net.fortuna.ical4j.model.Date
        if (allDay) {
            seed = net.fortuna.ical4j.model.Date(Instant.ofEpochMilli(seedMillis).atZone(sourceZone).toLocalDate().format(DATE))
            rangeStart = net.fortuna.ical4j.model.Date(Instant.ofEpochMilli(searchStartMillis).atZone(sourceZone).toLocalDate().format(DATE))
            rangeEnd = net.fortuna.ical4j.model.Date(Instant.ofEpochMilli(rangeEndMillis).atZone(sourceZone).toLocalDate().plusDays(1).format(DATE))
        } else {
            val timeZone = timeZoneRegistry.getTimeZone(sourceZone.id)
            seed = if (timeZone != null) DateTime(java.util.Date(seedMillis), timeZone) else DateTime(seedMillis)
            rangeStart = if (timeZone != null) DateTime(java.util.Date(searchStartMillis), timeZone) else DateTime(searchStartMillis)
            rangeEnd = if (timeZone != null) DateTime(java.util.Date(rangeEndMillis), timeZone) else DateTime(rangeEndMillis)
        }
        recur.getDates(seed, rangeStart, rangeEnd, valueType).map { date ->
            if (allDay) {
                LocalDate.parse(date.toString(), DATE).atStartOfDay(sourceZone).toInstant().toEpochMilli()
            } else {
                date.time
            }
        }
    }.getOrNull()
}
