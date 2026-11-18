package com.kgs.calendar.sync

import com.kgs.calendar.AppGraph
import com.kgs.calendar.time.ZoneId
import com.kgs.calendar.time.systemDefault
import kotlinx.coroutines.flow.first

/** [reparseForZoneChangeIfNeeded] for the device's current zone. */
internal suspend fun AppGraph.reparseForCurrentZoneIfNeeded(): Boolean = reparseForZoneChangeIfNeeded(
    storedZoneId = settingsStore.parsedZoneId.first(),
    currentZoneId = ZoneId.systemDefault().id,
    reparseAllResources = repository::reparseLocalResources,
    reloadDeviceCalendars = { repository.refreshAndroidCalendarsIfEnabled() },
    recordZone = settingsStore::setParsedZoneId,
)
