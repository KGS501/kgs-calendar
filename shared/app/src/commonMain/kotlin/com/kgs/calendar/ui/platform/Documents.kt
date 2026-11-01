package com.kgs.calendar.ui.platform

import androidx.compose.runtime.Composable

/*
 * Files the user picks or creates are passed around as document references: the string form of
 * a content URI on Android, an absolute file path on the desktop. Only the platform reads them.
 */

/**
 * Lets the user choose where to save a new file of [mimeType]. Call the returned function with a
 * suggested file name; [onResult] receives the chosen document, or null when cancelled.
 */
@Composable
internal expect fun rememberCreateDocument(mimeType: String, onResult: (String?) -> Unit): (suggestedName: String) -> Unit

/** Lets the user pick an image; [onResult] receives the picked document, or null when cancelled. */
@Composable
internal expect fun rememberPickImage(onResult: (String?) -> Unit): () -> Unit

/**
 * Asks for access to the device's calendars and reports whether it was granted. Null where the
 * platform has no device calendars, such as the desktop.
 */
@Composable
internal expect fun rememberCalendarPermissionRequest(onResult: (Boolean) -> Unit): (() -> Unit)?

/** Whether the app may already read and write the device's calendars. */
@Composable
internal expect fun hasCalendarPermission(): Boolean

/** The installed version name, e.g. "V.1.4.6-3". */
@Composable
internal expect fun appVersionName(): String
