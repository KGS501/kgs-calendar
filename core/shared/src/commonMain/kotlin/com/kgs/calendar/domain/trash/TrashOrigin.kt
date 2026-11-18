package com.kgs.calendar.domain.trash

/** Where a "Recently deleted" item lives, which decides how it is restored, purged and expires. */
enum class TrashOrigin(val value: String) {
    /** A snapshot the app wrote when the item was deleted; kept for [TrashRetention.DAYS] days. */
    LocalSnapshot("local"),

    /** An object in a Nextcloud account's CalDAV trash bin; the server keeps it for its own retention. */
    ServerTrashBin("server"),
    ;

    companion object {
        /** Unknown values fall back to [LocalSnapshot], the column default. */
        fun fromValue(value: String?): TrashOrigin = entries.firstOrNull { it.value == value } ?: LocalSnapshot
    }
}
