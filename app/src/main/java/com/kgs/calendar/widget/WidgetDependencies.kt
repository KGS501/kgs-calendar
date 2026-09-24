package com.kgs.calendar.widget

import android.content.Context
import com.kgs.calendar.data.query.CalendarQueries
import com.kgs.calendar.data.settings.SettingsStore
import com.kgs.calendar.widget.data.KgsWidgetDataSource
import com.kgs.calendar.widget.data.WidgetMonthPageSource
import com.kgs.calendar.widget.render.KgsWidgetRenderer
import com.kgs.calendar.widget.state.WidgetStateStore
import com.kgs.calendar.widget.update.KgsWidgetUpdateScheduler
import com.kgs.calendar.widget.update.KgsWidgetUpdater
import java.time.ZoneId

/**
 * Everything the widgets need, owned by [com.kgs.calendar.AppGraph]. Widget entry points
 * (providers, receivers, the collection service) fetch it once and pass it down.
 */
internal class WidgetDependencies(
    context: Context,
    val settingsStore: SettingsStore,
    val queries: CalendarQueries,
    val state: WidgetStateStore = WidgetStateStore(context),
) {
    val appContext: Context = context.applicationContext
    val updater = KgsWidgetUpdater(this)
    val scheduler = KgsWidgetUpdateScheduler(this)

    fun renderer(zoneId: ZoneId = ZoneId.systemDefault()): KgsWidgetRenderer =
        KgsWidgetRenderer(this, zoneId)

    fun dataSource(zoneId: ZoneId = ZoneId.systemDefault()): KgsWidgetDataSource =
        KgsWidgetDataSource(appContext, settingsStore, queries, state.taskExpansion, zoneId)

    fun monthPageSource(zoneId: ZoneId = ZoneId.systemDefault()): WidgetMonthPageSource =
        WidgetMonthPageSource(appContext, queries, zoneId)
}
