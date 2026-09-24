package com.kgs.calendar.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.widget.RemoteViewsService
import com.kgs.calendar.widget.render.KgsWidgetCollectionFactory
import com.kgs.calendar.widget.render.KgsWidgetDayCollectionFactory

class KgsWidgetCollectionService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val kind = intent.getStringExtra(EXTRA_WIDGET_KIND)
            ?.let { runCatching { KgsWidgetKind.valueOf(it) }.getOrNull() }
            ?: KgsWidgetKind.Agenda
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        return if (kind == KgsWidgetKind.Day) {
            KgsWidgetDayCollectionFactory(applicationContext, appWidgetId)
        } else {
            KgsWidgetCollectionFactory(applicationContext, kind, appWidgetId)
        }
    }
}
