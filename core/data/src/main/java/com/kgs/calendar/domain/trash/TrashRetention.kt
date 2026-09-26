package com.kgs.calendar.domain.trash

import java.util.concurrent.TimeUnit

/** How long "Recently deleted" keeps a local snapshot before it is purged automatically. */
object TrashRetention {
    const val DAYS = 30

    val MILLIS: Long = TimeUnit.DAYS.toMillis(DAYS.toLong())

    /** Items deleted before this instant are expired. */
    fun cutoffMillis(nowMillis: Long): Long = nowMillis - MILLIS

    /** When an item deleted at [deletedAtMillis] will be purged. */
    fun expiresAtMillis(deletedAtMillis: Long): Long = deletedAtMillis + MILLIS

    /**
     * Whole days left until [expiresAtMillis], rounded up so an item expiring later today still shows
     * one day; zero once it has expired.
     */
    fun daysLeft(expiresAtMillis: Long, nowMillis: Long): Int {
        val remaining = expiresAtMillis - nowMillis
        if (remaining <= 0) return 0
        val day = TimeUnit.DAYS.toMillis(1)
        return ((remaining + day - 1) / day).toInt()
    }
}
