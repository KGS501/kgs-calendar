package com.kgs.calendar.time

import kotlinx.datetime.TimeZone

internal actual fun platformSystemZone(): TimeZone = TimeZone.currentSystemDefault()
