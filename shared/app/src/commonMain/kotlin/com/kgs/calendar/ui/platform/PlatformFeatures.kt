package com.kgs.calendar.ui.platform

/** What the platform offers, so screens leave out what it lacks (e.g. home-screen widgets on the desktop). */
internal expect object PlatformFeatures {
    val homeScreenWidgets: Boolean
    val haptics: Boolean

    /** The name of the system's own color scheme: Android's wallpaper colors or Windows' accent color. */
    val systemColorsLabel: Int
}
