package com.kgs.calendar.domain.model

/** Queued CalDAV upload action. [value] is the string stored in Room. */
enum class MutationAction(val value: String) {
    Put("PUT"),
    Delete("DELETE"),

    /** Any other stored value; such mutations are left untouched by the uploader. */
    Unknown("UNKNOWN"),
    ;

    companion object {
        fun fromValue(value: String?): MutationAction =
            entries.firstOrNull { it != Unknown && it.value == value } ?: Unknown
    }
}
