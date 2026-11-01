package com.kgs.calendar.data.provider

import com.kgs.calendar.data.local.entity.EventEntity
import java.util.concurrent.ConcurrentHashMap

/**
 * Remembers Android provider events this app has just written or deleted, so a provider refresh
 * that races the write neither reverts nor resurrects them. One instance is shared by the event
 * mutations and the provider sync engine.
 */
internal class AndroidProviderWriteShield {
    private val recentLocalWrites = ConcurrentHashMap<String, Long>()
    private val recentLocalDeletes = ConcurrentHashMap<String, Long>()

    fun markLocalWrite(resourceHref: String) {
        recentLocalWrites[resourceHref] = System.currentTimeMillis()
        recentLocalDeletes.remove(resourceHref)
    }

    fun markLocalDelete(resourceHref: String) {
        recentLocalDeletes[resourceHref] = System.currentTimeMillis()
        recentLocalWrites.remove(resourceHref)
    }

    fun shouldKeepLocalWrite(resourceHref: String, existing: EventEntity?, providerEvent: EventEntity): Boolean {
        val writtenAt = recentLocalWrites[resourceHref] ?: return false
        if (System.currentTimeMillis() - writtenAt > RECENT_ANDROID_WRITE_SHIELD_MILLIS) {
            recentLocalWrites.remove(resourceHref)
            return false
        }
        if (existing == null || existing.hasSameAndroidProviderFields(providerEvent)) {
            recentLocalWrites.remove(resourceHref)
            return false
        }
        return true
    }

    fun shouldIgnoreLocalDelete(resourceHref: String): Boolean {
        val deletedAt = recentLocalDeletes[resourceHref] ?: return false
        if (System.currentTimeMillis() - deletedAt <= RECENT_ANDROID_WRITE_SHIELD_MILLIS) return true
        recentLocalDeletes.remove(resourceHref)
        return false
    }

    private fun EventEntity.hasSameAndroidProviderFields(other: EventEntity): Boolean =
        title == other.title &&
            description == other.description &&
            location == other.location &&
            startsAtMillis == other.startsAtMillis &&
            endsAtMillis == other.endsAtMillis &&
            allDay == other.allDay &&
            recurrenceRule == other.recurrenceRule &&
            exDatesCsv == other.exDatesCsv &&
            remindersCsv == other.remindersCsv &&
            collectionHref == other.collectionHref

    private companion object {
        const val RECENT_ANDROID_WRITE_SHIELD_MILLIS = 90L * 1000L
    }
}
