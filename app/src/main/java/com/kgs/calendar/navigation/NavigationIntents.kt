package com.kgs.calendar.navigation

import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract

/* Android intents for the launch targets and shared drafts defined in :shared:ui. */

fun CalendarLaunchTarget.writeTo(intent: Intent) {
    val payload = encode()
    intent.putExtra(CalendarLaunchTarget.EXTRA_TARGET, payload)
    intent.data = Uri.Builder()
        .scheme(CalendarLaunchTarget.URI_SCHEME)
        .authority(CalendarLaunchTarget.URI_AUTHORITY)
        .appendPath(action.wireName)
        .appendQueryParameter(CalendarLaunchTarget.URI_PAYLOAD, payload)
        .build()
}

fun CalendarLaunchTarget.Companion.readFrom(intent: Intent): CalendarLaunchTarget? {
    val payload = intent.getStringExtra(CalendarLaunchTarget.EXTRA_TARGET)
        ?: intent.data?.getQueryParameter(CalendarLaunchTarget.URI_PAYLOAD)
        ?: return null
    return decode(payload)
}

/** Hands the draft from [ShareToCalendarActivity][com.kgs.calendar.ShareToCalendarActivity] to the main activity. */
fun SharedEventDraft.writeTo(intent: Intent): Intent = intent
    .setAction(SharedEventDraft.ACTION_CREATE_SHARED_EVENT)
    .putExtra(SharedEventDraft.EXTRA_TITLE, title)
    .putExtra(SharedEventDraft.EXTRA_NOTES, notes)

/** The draft of an `ACTION_SEND` share, or null for any other intent. */
fun SharedEventDraft.Companion.fromShareIntent(intent: Intent): SharedEventDraft? {
    if (intent.action != Intent.ACTION_SEND) return null
    return fromShare(
        subject = runCatching { intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT) }.getOrNull(),
        text = runCatching { intent.getCharSequenceExtra(Intent.EXTRA_TEXT) }.getOrNull(),
        htmlText = runCatching { intent.getStringExtra(Intent.EXTRA_HTML_TEXT) }.getOrNull(),
    )
}

/** Android's calendar insert contract opens a draft for review, never saves directly. */
fun SharedEventDraft.Companion.fromCalendarInsertIntent(intent: Intent): SharedEventDraft? {
    val eventType = intent.type in setOf("vnd.android.cursor.dir/event", "vnd.android.cursor.item/event")
    val eventsUri = intent.data?.let {
        it.scheme == "content" && it.authority in setOf("com.android.calendar", "calendar") &&
            it.path?.trimEnd('/') == "/events"
    } == true
    val insert = intent.action == Intent.ACTION_INSERT || intent.action == Intent.ACTION_INSERT_OR_EDIT
    if (!insert || !(eventsUri || eventType)) return null
    return SharedEventDraft(
        title = sharedTitle(intent.getStringExtra(CalendarContract.Events.TITLE).orEmpty()),
        notes = limitNotes(intent.getStringExtra(CalendarContract.Events.DESCRIPTION).orEmpty()),
        location = intent.getStringExtra(CalendarContract.Events.EVENT_LOCATION).orEmpty().take(2_000),
        beginTimeMillis = intent.optionalTime(CalendarContract.EXTRA_EVENT_BEGIN_TIME),
        endTimeMillis = intent.optionalTime(CalendarContract.EXTRA_EVENT_END_TIME),
        allDay = intent.getBooleanExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, false),
    )
}

private fun Intent.optionalTime(key: String): Long? =
    getLongExtra(key, Long.MIN_VALUE).takeUnless { it == Long.MIN_VALUE }

/** The draft handed over by [writeTo]; the limits apply again because the main activity is exported. */
fun SharedEventDraft.Companion.readFrom(intent: Intent): SharedEventDraft? {
    if (intent.action != SharedEventDraft.ACTION_CREATE_SHARED_EVENT) return null
    return SharedEventDraft(
        title = sharedTitle(runCatching { intent.getStringExtra(SharedEventDraft.EXTRA_TITLE) }.getOrNull().orEmpty()),
        notes = limitNotes(runCatching { intent.getStringExtra(SharedEventDraft.EXTRA_NOTES) }.getOrNull().orEmpty()),
    )
}
