package com.kgs.calendar.data.trash

import com.kgs.calendar.data.ical.IcalCodec
import com.kgs.calendar.data.local.entity.AccountEntity
import com.kgs.calendar.data.local.entity.CollectionEntity
import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.data.local.entity.TrashedItemEntity
import com.kgs.calendar.data.remote.CalDavTrashBinSupport
import com.kgs.calendar.domain.model.ComponentType
import com.kgs.calendar.domain.model.SourceType
import com.kgs.calendar.domain.trash.TrashRetention
import java.util.concurrent.TimeUnit

/**
 * A "Recently deleted" [item] with the event or task read back from its stored iCalendar data, so
 * it can be shown with the app's usual event and task cards and detail sheet. Exactly one of
 * [event] and [task] is set. They are for display only: their [EventEntity.resourceHref] and
 * [TaskEntity.resourceHref] are [displayHref], which never matches a stored item.
 */
data class TrashedItemPreview(
    val item: TrashedItemEntity,
    val event: EventEntity? = null,
    val task: TaskEntity? = null,
) {
    companion object {
        /** A unique, synthetic href per trash item, so previews never collide with live items or each other. */
        fun displayHref(item: TrashedItemEntity): String = "trashed-item:${item.id}"
    }
}

/**
 * Reads [item]'s iCalendar data into an event or task in its original calendar, with the item's
 * own colour. When the data can't be read, a minimal entity is built from the stored title and
 * date so the item can still be listed, opened and deleted.
 */
internal fun IcalCodec.previewOf(item: TrashedItemEntity): TrashedItemPreview {
    val href = TrashedItemPreview.displayHref(item)
    val parsed = runCatching { parse(item.rawIcs, item.collectionHref, href, item.collectionColor) }.getOrNull()
    return when (item.componentType) {
        ComponentType.Task -> TrashedItemPreview(
            item = item,
            task = (parsed?.task ?: item.fallbackTask(href)).copy(manualColor = item.manualColor, syncError = null),
        )
        ComponentType.Event,
        ComponentType.Unknown,
        -> TrashedItemPreview(
            item = item,
            event = (parsed?.event ?: item.fallbackEvent(href)).copy(manualColor = item.manualColor, syncError = null),
        )
    }
}

private fun TrashedItemEntity.fallbackEvent(href: String): EventEntity {
    val start = startMillis ?: deletedAtMillis
    return EventEntity(
        uid = uid,
        collectionHref = collectionHref,
        resourceHref = href,
        title = title,
        description = null,
        location = null,
        startsAtMillis = start,
        endsAtMillis = start + if (hasTime) TimeUnit.HOURS.toMillis(1) else TimeUnit.DAYS.toMillis(1),
        allDay = !hasTime,
        recurrenceRule = null,
        isRecurring = false,
        color = collectionColor,
    )
}

private fun TrashedItemEntity.fallbackTask(href: String): TaskEntity = TaskEntity(
    uid = uid,
    collectionHref = collectionHref,
    resourceHref = href,
    title = title,
    notes = null,
    dueAtMillis = startMillis,
    dueHasTime = hasTime,
    startAtMillis = null,
    completedAtMillis = null,
    isCompleted = false,
    priority = null,
    color = collectionColor,
)

/**
 * How many days a whole event or task deleted from [collection] stays in "Recently deleted", or
 * null when that isn't known yet: a CalDAV account whose server hasn't been checked for a Nextcloud
 * trash bin, or whose trash bin doesn't report its retention.
 * - Nextcloud accounts with a trash bin: the server's retention, rounded up to whole days.
 * - Everything else (local, device calendars, CalDAV servers without a trash bin): the local
 *   [TrashRetention.DAYS].
 */
fun trashRetentionDays(collection: CollectionEntity?, account: AccountEntity?): Int? {
    collection ?: return null
    if (collection.sourceType != SourceType.CalDav) return TrashRetention.DAYS
    val support = CalDavTrashBinSupport.readFrom(account?.capabilitiesJson) ?: return null
    val trashBin = support.trashBin ?: return TrashRetention.DAYS
    val seconds = trashBin.retentionSeconds ?: return null
    val day = TimeUnit.DAYS.toSeconds(1)
    return ((seconds + day - 1) / day).toInt()
}
