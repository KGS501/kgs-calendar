package com.kgs.calendar.domain.model

sealed interface CalendarOccurrenceId {
    val resourceHref: String
    val recurrenceIdMillis: Long
    val kind: String

    val stableKey: String
        get() = "$kind:${resourceHref.length}:$resourceHref:$recurrenceIdMillis"

    data class Event(
        override val resourceHref: String,
        override val recurrenceIdMillis: Long,
    ) : CalendarOccurrenceId {
        override val kind: String = "event"
    }

    data class Task(
        override val resourceHref: String,
        override val recurrenceIdMillis: Long,
    ) : CalendarOccurrenceId {
        override val kind: String = "task"
    }
}
