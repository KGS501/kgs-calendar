package com.kgs.calendar.ui.platform

import android.text.format.DateFormat
import java.util.Locale

internal actual fun bestDateTimePattern(locale: Locale, skeleton: String): String =
    DateFormat.getBestDateTimePattern(locale, skeleton)
