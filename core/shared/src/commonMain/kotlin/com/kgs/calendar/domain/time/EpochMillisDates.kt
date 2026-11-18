package com.kgs.calendar.domain.time

import com.kgs.calendar.time.*
import kotlinx.datetime.*
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import com.kgs.calendar.time.ZoneId
import com.kgs.calendar.time.DateTimeFormatter

fun Long.toDate(zoneId: ZoneId = ZoneId.systemDefault()): LocalDate =
    Instant.fromEpochMilliseconds(this).toLocalDateTime(zoneId).date

fun Long.toTimeText(zoneId: ZoneId = ZoneId.systemDefault()): String =
    Instant.ofEpochMilli(this).atZone(zoneId).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
