package com.kgs.calendar.time

import kotlinx.datetime.TimeZone
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSSystemTimeZoneDidChangeNotification
import platform.Foundation.NSTimeZone
import platform.Foundation.resetSystemTimeZone

/** Read for every event on screen, so even asking the system for its name costs too much. */
private var systemZone: TimeZone? = null

private val zoneChanges by lazy {
    NSNotificationCenter.defaultCenter.addObserverForName(NSSystemTimeZoneDidChangeNotification, `object` = null, queue = null) { _ ->
        refreshSystemZone()
    }
}

internal actual fun platformSystemZone(): TimeZone {
    systemZone?.let { return it }
    zoneChanges
    return TimeZone.currentSystemDefault().also { systemZone = it }
}

/** Forgets the zone; also for coming back to the foreground, when a change may have been missed. */
fun refreshSystemZone() {
    NSTimeZone.resetSystemTimeZone()
    systemZone = null
}
