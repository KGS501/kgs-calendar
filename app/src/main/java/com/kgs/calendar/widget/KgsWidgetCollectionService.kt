package com.kgs.calendar.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.annotation.RequiresApi
import com.kgs.calendar.KgsCalendarApplication
import com.kgs.calendar.MainActivity
import com.kgs.calendar.R
import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.data.settings.AppColorMode
import com.kgs.calendar.data.settings.AppLanguageMode
import com.kgs.calendar.data.settings.AppThemeMode
import com.kgs.calendar.data.settings.SettingsStore
import com.kgs.calendar.data.settings.TaskColorMode
import com.kgs.calendar.data.settings.WidgetColorMode
import com.kgs.calendar.data.settings.WidgetTaskCreateMode
import com.kgs.calendar.data.settings.WidgetTaskDisplayMode
import com.kgs.calendar.data.settings.WidgetTaskSortMode
import com.kgs.calendar.data.settings.WidgetTaskSubtaskDefaultMode
import com.kgs.calendar.data.settings.WidgetThemeMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt

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

private class KgsWidgetDayCollectionFactory(
    private val context: Context,
    private val appWidgetId: Int,
) : RemoteViewsService.RemoteViewsFactory {
    private val packageName = context.packageName
    private var rows: List<WidgetDayGridRow> = emptyList()
    private var rowViews: List<RemoteViews> = emptyList()

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        val startedAt = SystemClock.elapsedRealtime()
        runCatching {
            val snapshot = runBlocking {
                KgsWidgetRenderer(context).dayGridCollectionSnapshot(appWidgetId)
            }
            val textContext = context.withWidgetLocale(snapshot.settings.locale)
            rows = snapshot.rows
            rowViews = rows.map { row ->
                row.toRemoteViews(
                    context = textContext,
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

private class KgsWidgetCollectionFactory(
    private val context: Context,
    private val kind: KgsWidgetKind,
    private val appWidgetId: Int,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) : RemoteViewsService.RemoteViewsFactory {
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
            val cached = KgsWidgetCollectionRowsCache.get(kind, appWidgetId)
            if (cached != null) {
                settings = cached.settings
                palette = cached.palette
                rows = cached.rows
                renderOptions = renderOptions.withTaskArtWidth(cached.taskArtWidthDp)
            } else {
                runBlocking {
                    val dataSource = KgsWidgetDataSource(context, zoneId)
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
                row.toRemoteViews(textContext, packageName, palette, kind, appWidgetId, renderOptions)
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
