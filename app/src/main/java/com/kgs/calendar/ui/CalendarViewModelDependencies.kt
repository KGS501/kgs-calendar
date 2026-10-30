package com.kgs.calendar.ui

import android.net.Uri
import androidx.annotation.StringRes
import com.kgs.calendar.widget.KgsWidgetKind
import kotlinx.coroutines.flow.Flow

/** Schedules home-screen widget refreshes after the UI changed data or widget settings. */
interface WidgetRefresher {
    fun updateAll()

    fun update(kind: KgsWidgetKind, forceFullDayUpdate: Boolean = false)
}

/** Recomputes the scheduled reminder alarms. */
fun interface ReminderRescheduler {
    suspend fun reschedule()
}

/** Process-level signals and hooks owned by the Application. */
interface AppLifecycleSignals {
    /** Wall-clock time of every process foreground transition. */
    val processForegroundedAt: Flow<Long>

    fun registerAndroidCalendarObserverIfPermitted()
}

/** Writes calendars as an iCalendar file to a document the user picked; returns the items written. */
fun interface CalendarFileExporter {
    suspend fun export(collectionHrefs: List<String>, target: Uri): Int
}

/** Reads a document the user picked (e.g. a photo for a group), or null when it can't be read. */
fun interface PickedContentReader {
    suspend fun read(uri: Uri): ByteArray?
}

/** Resolves string resources for messages produced outside of composition. */
fun interface UiStrings {
    fun get(@StringRes id: Int): String
}
