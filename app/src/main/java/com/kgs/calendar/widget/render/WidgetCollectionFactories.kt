package com.kgs.calendar.widget.render

import android.appwidget.AppWidgetManager
import android.os.SystemClock
import android.util.Log
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.kgs.calendar.R
import com.kgs.calendar.data.settings.AppColorMode
import com.kgs.calendar.data.settings.AppThemeMode
import com.kgs.calendar.widget.KgsWidgetKind
import com.kgs.calendar.widget.TAG
import com.kgs.calendar.widget.WIDGET_COLLECTION_VIEW_TYPE_COUNT
import com.kgs.calendar.widget.WidgetDependencies
import com.kgs.calendar.widget.WidgetLog
import com.kgs.calendar.widget.model.WidgetCollectionRenderOptions
import com.kgs.calendar.widget.model.WidgetDayGridRow
import com.kgs.calendar.widget.model.WidgetListRow
import com.kgs.calendar.widget.model.WidgetRenderSettings
import com.kgs.calendar.widget.model.WidgetSize
import com.kgs.calendar.widget.model.collectionArtWidthDp
import com.kgs.calendar.widget.theme.WidgetPalette
import com.kgs.calendar.widget.withWidgetLocale
import java.time.ZoneId
import kotlinx.coroutines.runBlocking

internal class KgsWidgetDayCollectionFactory(
    private val widgets: WidgetDependencies,
    private val appWidgetId: Int,
) : RemoteViewsService.RemoteViewsFactory {
    private val context = widgets.appContext
    private val packageName = context.packageName
    private var rows: List<WidgetDayGridRow> = emptyList()
    private var rowViews: List<RemoteViews> = emptyList()

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        val startedAt = SystemClock.elapsedRealtime()
        runCatching {
            val snapshot = runBlocking {
                widgets.renderer().dayGridCollectionSnapshot(appWidgetId)
            }
            val textContext = context.withWidgetLocale(snapshot.settings.locale)
            rows = snapshot.rows
            rowViews = rows.map { row ->
                row.toRemoteViews(
                    context = textContext,
                    images = widgets.state.bitmapUris,
                    packageName = packageName,
                    palette = snapshot.palette,
                    widthDp = snapshot.widthDp,
                    appWidgetId = appWidgetId,
                )
            }
        }.onFailure { error ->
            Log.e(TAG, "Failed to load Day widget grid rows", error)
            rows = emptyList()
            rowViews = emptyList()
        }
        WidgetLog.d(context, "Loaded ${rows.size} Day widget grid rows in ${SystemClock.elapsedRealtime() - startedAt}ms")
    }

    override fun onDestroy() {
        rows = emptyList()
        rowViews = emptyList()
    }

    override fun getCount(): Int = rowViews.size

    override fun getViewAt(position: Int): RemoteViews {
        return rowViews.getOrNull(position)
            ?: RemoteViews(packageName, R.layout.widget_collection_spacer)
    }

    override fun getLoadingView(): RemoteViews = RemoteViews(packageName, R.layout.widget_collection_spacer)

    override fun getViewTypeCount(): Int = 2

    override fun getItemId(position: Int): Long = rows.getOrNull(position)?.stableId ?: position.toLong()

    override fun hasStableIds(): Boolean = true
}

internal class KgsWidgetCollectionFactory(
    private val widgets: WidgetDependencies,
    private val kind: KgsWidgetKind,
    private val appWidgetId: Int,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) : RemoteViewsService.RemoteViewsFactory {
    private val context = widgets.appContext
    private val packageName = context.packageName
    private var rows: List<WidgetListRow> = emptyList()
    private var rowViews: List<RemoteViews> = emptyList()
    private var settings = WidgetRenderSettings()
    private var palette = WidgetPalette.from(context, AppThemeMode.KgsBlue, AppColorMode.Auto)
    private var renderOptions = WidgetCollectionRenderOptions(
        taskArtWidthDp = WidgetSize.from(
            context,
            AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId),
            kind,
        ).collectionArtWidthDp(kind),
    )

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        val startedAt = SystemClock.elapsedRealtime()
        runCatching {
            val cached = widgets.state.collectionRows.get(kind, appWidgetId)
            if (cached != null) {
                settings = cached.settings
                palette = cached.palette
                rows = cached.rows
                renderOptions = renderOptions.withTaskArtWidth(cached.taskArtWidthDp)
            } else {
                runBlocking {
                    val dataSource = widgets.dataSource(zoneId)
                    settings = dataSource.loadSettings(kind)
                    palette = WidgetPalette.from(context, settings.themeMode, settings.colorMode)
                    rows = dataSource.listRows(kind, settings, appWidgetId)
                    renderOptions = renderOptions.withTaskArtWidth(
                        WidgetSize.from(
                            context,
                            AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId),
                            kind,
                        ).collectionArtWidthDp(kind),
                    )
                }
            }
            val textContext = context.withWidgetLocale(settings.locale)
            rowViews = rows.map { row ->
                row.toRemoteViews(textContext, widgets.state.bitmapUris, packageName, palette, kind, appWidgetId, renderOptions)
            }
        }.onFailure { error ->
            Log.e(TAG, "Failed to load ${kind.name} widget rows", error)
            rows = emptyList()
            rowViews = emptyList()
        }
        WidgetLog.d(context, "Loaded ${rows.size} ${kind.name} widget rows in ${SystemClock.elapsedRealtime() - startedAt}ms")
    }

    override fun onDestroy() {
        rows = emptyList()
        rowViews = emptyList()
    }

    override fun getCount(): Int = rows.size

    override fun getViewAt(position: Int): RemoteViews =
        rowViews.getOrNull(position)
            ?: RemoteViews(packageName, R.layout.widget_collection_spacer)

    override fun getLoadingView(): RemoteViews = RemoteViews(packageName, R.layout.widget_collection_spacer)

    override fun getViewTypeCount(): Int = WIDGET_COLLECTION_VIEW_TYPE_COUNT

    override fun getItemId(position: Int): Long = rows.getOrNull(position)?.stableId ?: position.toLong()

    override fun hasStableIds(): Boolean = true
}
