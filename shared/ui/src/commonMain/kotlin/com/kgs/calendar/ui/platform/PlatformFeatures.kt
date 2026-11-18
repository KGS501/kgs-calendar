package com.kgs.calendar.ui.platform

import com.kgs.calendar.ui.res.Res
import com.kgs.calendar.ui.res.StringResource

/** Features that exist on one platform only; the shared UI hides what the platform lacks. */
expect object PlatformFeatures {
    /** The iOS app; texts that name the platform (Android, iPhone) follow it. */
    val isIos: Boolean

    /** The Windows app; its texts follow it the same way, and it doesn't use the device's location. */
    val isWindows: Boolean

    /** Calendars of other apps on the device (Android's Calendar Provider, iOS's EventKit). */
    val deviceCalendars: Boolean

    /** Home-screen widgets. */
    val homeScreenWidgets: Boolean

    /** A palette taken from the system (Android 12+ wallpaper colours, Windows' accent colour). */
    val systemColors: Boolean

    /** The system palette's name in the colour choice. */
    val systemColorsLabel: StringResource

    /** Vibration on touch (not on the desktop). */
    val haptics: Boolean

    /** What removing a source and uninstalling the app delete here. */
    val privacyDeleteBody: StringResource
}

/** The device's own calendars by the platform's name: "Android device calendars", "iPhone calendars". */
val deviceCalendarsName: StringResource
    get() = if (PlatformFeatures.isIos) Res.string.device_calendars_ios else Res.string.android_device_calendars
