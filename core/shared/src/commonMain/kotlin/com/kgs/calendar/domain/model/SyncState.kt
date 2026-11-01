package com.kgs.calendar.domain.model

/** Account sync state. [value] is the string stored in Room. */
enum class SyncState(val value: String) {
    Idle("idle"),
    Syncing("syncing"),
    Error("error"),
    ;

    companion object {
        /** Unknown values fall back to [Idle], the column default. */
        fun fromValue(value: String?): SyncState = entries.firstOrNull { it.value == value } ?: Idle
    }
}
