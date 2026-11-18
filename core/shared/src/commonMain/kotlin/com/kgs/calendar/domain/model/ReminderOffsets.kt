package com.kgs.calendar.domain.model

const val REMINDER_AT_START = 0
const val REMINDER_AT_END = -1
const val MAX_REMINDER_MINUTES = 60 * 24 * 30

fun Int.isSupportedReminderOffset(): Boolean =
    this == REMINDER_AT_END || this in REMINDER_AT_START..MAX_REMINDER_MINUTES

fun Iterable<Int>.normalizedReminderOffsets(): List<Int> =
    filter { it.isSupportedReminderOffset() }
        .distinct()
        .sortedWith(compareBy { if (it == REMINDER_AT_END) Int.MAX_VALUE else it })
