package com.kgs.calendar.domain.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun Long.toDate(zoneId: ZoneId = ZoneId.systemDefault()): LocalDate =
    Instant.ofEpochMilli(this).atZone(zoneId).toLocalDate()

fun Long.toTimeText(zoneId: ZoneId = ZoneId.systemDefault()): String =
    Instant.ofEpochMilli(this).atZone(zoneId).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
