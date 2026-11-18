package com.kgs.calendar.domain.model

/** Origin of an account or collection. [value] is the string stored in Room. */
enum class SourceType(val value: String) {
    Local("local"),
    CalDav("caldav"),
    ReadOnlyUrl("readonly_url"),
    AndroidProvider("android_provider"),
    ;

    companion object {
        /** Unknown values fall back to [CalDav], the column default and the sync engine fallback. */
        fun fromValue(value: String?): SourceType = entries.firstOrNull { it.value == value } ?: CalDav
    }
}
