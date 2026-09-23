package com.kgs.calendar.domain.model

/** iCalendar component kind of a stored resource. [value] is the component name stored in Room. */
enum class ComponentType(val value: String) {
    Event("VEVENT"),
    Task("VTODO"),

    /** Stored for failed resources whose raw data has no recognisable component. */
    Unknown("UNKNOWN"),
    ;

    companion object {
        fun fromValue(value: String?): ComponentType =
            entries.firstOrNull { it.value.equals(value, ignoreCase = true) } ?: Unknown
    }
}
