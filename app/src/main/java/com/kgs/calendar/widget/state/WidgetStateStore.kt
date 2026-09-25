package com.kgs.calendar.widget.state

import android.content.Context
import com.kgs.calendar.widget.bitmap.WidgetBitmapUriStore

/**
 * Process-wide widget state: per-widget navigation/expansion state persisted in the
 * `kgs_widget_state` preferences plus the in-memory caches shared by all widget updates.
 */
internal class WidgetStateStore(context: Context) {
    private val appContext: Context = context.applicationContext

    val month = WidgetMonthState(appContext)
    val day = WidgetDayState(appContext)
    val taskExpansion = WidgetTaskExpansionState(appContext)
    val interactionTokens = WidgetInteractionTokens()
    val dataGeneration = WidgetDataGeneration()
    val monthPages = WidgetMonthPageCache(dataGeneration)
    val monthSignatures = WidgetMonthUpdateSignatures()
    val collectionSignatures = WidgetCollectionUpdateSignatures()
    val collectionRows = WidgetCollectionRowsCache()
    val bitmapUris = WidgetBitmapUriStore(appContext)
}
