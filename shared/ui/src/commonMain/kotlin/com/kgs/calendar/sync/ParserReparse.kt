package com.kgs.calendar.sync

import kotlin.coroutines.cancellation.CancellationException

// 7: text values are no longer decoded by guesswork, alarms are read in context, and Windows,
// prefixed and VTIMEZONE-defined zones resolve to real zones; events and tasks both changed.
const val FULL_REPARSE_VERSION = 7
const val PARSER_REPARSE_VERSION = 7

/**
 * Re-parses the cached raw iCal once per parser version so rows synced under an older parser are
 * corrected without a full re-download. Versions from [FULL_REPARSE_VERSION] on only need the tasks
 * redone. The version is only recorded after a pass that completed, so a failed one runs again on
 * the next start.
 */
suspend fun reparseCachedIcalIfNeeded(
    storedVersion: Int,
    reparseTaskResources: suspend () -> Unit,
    reparseAllResources: suspend () -> Unit,
    recordVersion: suspend (Int) -> Unit,
) {
    if (storedVersion >= PARSER_REPARSE_VERSION) return
    try {
        if (storedVersion >= FULL_REPARSE_VERSION) reparseTaskResources() else reparseAllResources()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        return
    }
    recordVersion(PARSER_REPARSE_VERSION)
}

/**
 * All-day and floating times are stored as instants in the zone they were read in. When the
 * device's zone changed since (after a flight, say), re-reads the cached calendar data and reloads
 * the device calendars in the new zone, so all-day items stay on their dates. The first check only
 * records the zone. Returns true when the data was read again.
 */
suspend fun reparseForZoneChangeIfNeeded(
    storedZoneId: String?,
    currentZoneId: String,
    reparseAllResources: suspend () -> Unit,
    reloadDeviceCalendars: suspend () -> Unit,
    recordZone: suspend (String) -> Unit,
): Boolean {
    if (storedZoneId == currentZoneId) return false
    if (storedZoneId != null) {
        try {
            reparseAllResources()
            reloadDeviceCalendars()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            // Recorded only after a pass that completed, so a failed one runs again.
            return false
        }
    }
    recordZone(currentZoneId)
    return storedZoneId != null
}
