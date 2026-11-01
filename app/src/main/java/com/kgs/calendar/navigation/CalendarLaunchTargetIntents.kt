package com.kgs.calendar.navigation

import android.content.Intent
import android.net.Uri

/** Puts this target into [intent], as an extra and as a `kgs-calendar://launch` URI. */
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

/** The target written by [writeTo], or null when [intent] carries none. */
fun CalendarLaunchTarget.Companion.readFrom(intent: Intent): CalendarLaunchTarget? {
    val payload = intent.getStringExtra(CalendarLaunchTarget.EXTRA_TARGET)
        ?: intent.data?.getQueryParameter(CalendarLaunchTarget.URI_PAYLOAD)
        ?: return null
    return decode(payload)
}
