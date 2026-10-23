package com.kgs.calendar.domain.time

import java.time.ZoneId

/**
 * The time zone to work in: [this] when one is fixed, as tests do, or else the device's current
 * zone. Read on every use, so a zone change while the app runs (after a flight, say) applies at
 * once instead of with the next start.
 */
fun ZoneId?.orDeviceZone(): ZoneId = this ?: ZoneId.systemDefault()
