package com.kgs.calendar.domain.trash

import java.util.concurrent.TimeUnit

/** How long "Recently deleted" keeps an item before it is purged automatically. */
object TrashRetention {
    const val DAYS = 30

    val MILLIS: Long = TimeUnit.DAYS.toMillis(DAYS.toLong())

    /** Items deleted before this instant are expired. */
    fun cutoffMillis(nowMillis: Long): Long = nowMillis - MILLIS

    /** When an item deleted at [deletedAtMillis] will be purged. */
    fun expiresAtMillis(deletedAtMillis: Long): Long = deletedAtMillis + MILLIS
}
