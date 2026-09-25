package com.kgs.calendar.sync

import kotlin.coroutines.cancellation.CancellationException

internal const val FULL_REPARSE_VERSION = 5
internal const val PARSER_REPARSE_VERSION = 6

/**
 * Re-parses the cached raw iCal once per parser version so rows synced under an older parser are
 * corrected without a full re-download. Versions from [FULL_REPARSE_VERSION] on only need the tasks
 * redone. The version is only recorded after a pass that completed, so a failed one runs again on
 * the next start.
 */
internal suspend fun reparseCachedIcalIfNeeded(
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
