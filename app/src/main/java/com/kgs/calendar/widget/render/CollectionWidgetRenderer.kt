package com.kgs.calendar.widget.render

import android.content.Context
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.RemoteViews
import com.kgs.calendar.R
import com.kgs.calendar.data.settings.AppColorMode
import com.kgs.calendar.data.settings.AppThemeMode
import com.kgs.calendar.data.settings.WidgetTaskSortMode
import com.kgs.calendar.widget.KgsWidgetKind
import com.kgs.calendar.widget.bitmap.widgetSortIconBitmap
import com.kgs.calendar.widget.data.KgsWidgetDataSource
import com.kgs.calendar.widget.dpToPx
import com.kgs.calendar.widget.model.WidgetCollectionRenderOptions
import com.kgs.calendar.widget.model.WidgetCollectionSnapshot
import com.kgs.calendar.widget.model.WidgetRenderSettings
import com.kgs.calendar.widget.model.WidgetSize
import com.kgs.calendar.widget.model.collectionArtWidthDp
import com.kgs.calendar.widget.model.emptyText
import com.kgs.calendar.widget.model.preparedWidgetValue
import com.kgs.calendar.widget.model.shouldHideWidgetTitle
import com.kgs.calendar.widget.model.usesDirectCollectionItems
import com.kgs.calendar.widget.model.widgetLabel
import com.kgs.calendar.widget.theme.WidgetPalette
import com.kgs.calendar.widget.withWidgetLocale
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal class CollectionWidgetRenderer(
    private val context: Context,
    private val zoneId: ZoneId,
    private val dataSource: KgsWidgetDataSource,
) {
    private val packageName = context.packageName
    private val intents = WidgetPendingIntents(context)

    suspend fun renderCollectionWidget(
        kind: KgsWidgetKind,
        appWidgetId: Int,
        options: Bundle,
        forceServiceCollection: Boolean = false,
        collectionSnapshot: WidgetCollectionSnapshot? = null,
    ): RemoteViews {
        collectionSnapshot?.let { snapshot ->
            require(snapshot.kind == kind && snapshot.appWidgetId == appWidgetId)
        }
        val today = LocalDate.now(zoneId)
        val settings = preparedWidgetValue(collectionSnapshot?.settings) {
            dataSource.loadSettings(kind)
        }
        val textContext = context.withWidgetLocale(settings.locale)
        val palette = collectionSnapshot?.palette
            ?: WidgetPalette.from(context, settings.themeMode, settings.colorMode)
        val size = collectionSnapshot?.renderSize ?: WidgetSize.from(context, options, kind)
        val views = RemoteViews(packageName, if (kind == KgsWidgetKind.Tasks) R.layout.widget_calendar_tasks else R.layout.widget_calendar)
        views.bindBaseShell(kind, appWidgetId, settings, palette, today, size)
        views.setViewVisibility(R.id.widget_month_section, View.GONE)
        views.setTextViewText(R.id.widget_empty, kind.emptyText(textContext))
        views.setTextColor(R.id.widget_empty, palette.muted)
        if (kind.usesDirectCollectionItems() && !forceServiceCollection) {
            val rows = preparedWidgetValue(collectionSnapshot?.rows) {
                dataSource.listRows(kind, settings, appWidgetId)
            }
            views.bindDirectCollectionItems(
                context = textContext,
                packageName = packageName,
                palette = palette,
                rows = rows,
                sourceKind = kind,
                appWidgetId = appWidgetId,
                renderOptions = WidgetCollectionRenderOptions(taskArtWidthDp = size.collectionArtWidthDp(kind)),
            )
        } else {
            views.setRemoteAdapter(R.id.widget_list, intents.collectionAdapterIntent(kind, appWidgetId))
        }
        if (kind != KgsWidgetKind.Tasks) {
            views.setEmptyView(R.id.widget_list, R.id.widget_empty)
        }
        views.setPendingIntentTemplate(R.id.widget_list, intents.collectionClickPendingIntent(kind, appWidgetId))
        return views
    }

    fun error(kind: KgsWidgetKind, appWidgetId: Int, message: String): RemoteViews {
        val settings = WidgetRenderSettings(locale = Locale.getDefault())
        val palette = WidgetPalette.from(context, AppThemeMode.KgsBlue, AppColorMode.Auto)
        val views = RemoteViews(packageName, if (kind == KgsWidgetKind.Tasks) R.layout.widget_calendar_tasks else R.layout.widget_calendar)
        views.bindBaseShell(kind, appWidgetId, settings, palette, LocalDate.now(zoneId))
        views.setViewVisibility(R.id.widget_month_section, View.GONE)
        views.setViewVisibility(R.id.widget_list, View.GONE)
        views.showEmpty(message, palette)
        return views
    }

    private fun RemoteViews.bindBaseShell(
        kind: KgsWidgetKind,
        appWidgetId: Int,
        settings: WidgetRenderSettings,
        palette: WidgetPalette,
        today: LocalDate,
        size: WidgetSize? = null,
        ) {
        setInt(R.id.widget_root, "setBackgroundResource", palette.rootBackgroundRes)
        setInt(R.id.widget_header, "setBackgroundResource", palette.rootBackgroundRes)
        val contentPadding = context.dpToPx(12)
        val textContext = context.withWidgetLocale(settings.locale)
        val agendaEdgeFade = kind == KgsWidgetKind.Agenda
        setViewPadding(
            R.id.widget_content,
            if (kind == KgsWidgetKind.Tasks) 0 else contentPadding,
            contentPadding,
            if (kind == KgsWidgetKind.Tasks) 0 else contentPadding,
            if (kind == KgsWidgetKind.Tasks || agendaEdgeFade) 0 else contentPadding,
        )
        if (agendaEdgeFade) {
            setViewPadding(R.id.widget_header, 0, 0, 0, 0)
        }
        val titleText = kind.title(textContext)
        setTextViewText(R.id.widget_title, titleText)
        setTextColor(R.id.widget_title, palette.text)
        setTextViewText(R.id.widget_subtitle, headerSubtitle(kind, today, settings))
        setTextColor(R.id.widget_subtitle, palette.muted)
        val hideTitleText = size?.let { widgetSize ->
            val reservedDp = if (kind == KgsWidgetKind.Tasks) {
                56f + settings.tasksWidgetSortMode.widgetButtonWidthDp(textContext)
            } else {
                54f
            }
            shouldHideWidgetTitle(widgetSize.widthDp, titleText, reservedDp, textSp = 16f)
        } == true
        setViewVisibility(R.id.widget_title_group, if (hideTitleText) View.GONE else View.VISIBLE)
        setViewVisibility(R.id.widget_title, if (hideTitleText) View.GONE else View.VISIBLE)
        setViewVisibility(R.id.widget_subtitle, if (hideTitleText) View.GONE else View.VISIBLE)
        setInt(R.id.widget_header, "setGravity", if (hideTitleText) Gravity.CENTER else Gravity.CENTER_VERTICAL)
        if (kind == KgsWidgetKind.Tasks) {
            val openTasksIntent = intents.openAppPendingIntent(KgsWidgetKind.Tasks, 45_000 + appWidgetId, today)
            setOnClickPendingIntent(R.id.widget_title, openTasksIntent)
            setOnClickPendingIntent(R.id.widget_subtitle, openTasksIntent)
        }
        setTextColor(R.id.widget_badge, palette.onAccent)
        setInt(R.id.widget_badge, "setBackgroundResource", palette.badgeBackgroundRes)
        setInt(R.id.widget_sort, "setBackgroundResource", palette.sortBackgroundRes)
        setImageViewBitmap(R.id.widget_sort_icon, widgetSortIconBitmap(context, palette.onAccent))
        setTextColor(R.id.widget_sort_label_date, palette.onAccent)
        setTextColor(R.id.widget_sort_label_priority, palette.onAccent)
        setTextColor(R.id.widget_sort_label_status, palette.onAccent)
        setTextViewText(R.id.widget_sort_label_date, WidgetTaskSortMode.Date.widgetLabel(textContext))
        setTextViewText(R.id.widget_sort_label_priority, WidgetTaskSortMode.Priority.widgetLabel(textContext))
        setTextViewText(R.id.widget_sort_label_status, WidgetTaskSortMode.Status.widgetLabel(textContext))
        if (kind == KgsWidgetKind.Tasks) {
            bindTasksSortButtonState(
                context = textContext,
                appWidgetId = appWidgetId,
                mode = settings.tasksWidgetSortMode,
                widthDp = settings.tasksWidgetSortMode.widgetButtonWidthDp(textContext),
                createMode = settings.tasksWidgetCreateMode,
            )
        }
        setViewVisibility(R.id.widget_sort, if (kind == KgsWidgetKind.Tasks) View.VISIBLE else View.GONE)
        bindCollectionBottomFade(palette, visible = kind == KgsWidgetKind.Tasks || agendaEdgeFade)
        if (kind != KgsWidgetKind.Tasks) {
            setOnClickPendingIntent(R.id.widget_root, intents.openAppPendingIntent(kind, appWidgetId, today))
        }
        when (kind) {
            KgsWidgetKind.Tasks -> {
                setTextViewText(R.id.widget_badge, "+")
                setTextViewTextSize(R.id.widget_badge, TypedValue.COMPLEX_UNIT_SP, 17f)
                setOnClickPendingIntent(R.id.widget_badge, intents.createTaskPendingIntent(appWidgetId, today, settings.tasksWidgetCreateMode))
            }
            KgsWidgetKind.Agenda -> {
                setTextViewText(R.id.widget_badge, "+")
                setTextViewTextSize(R.id.widget_badge, TypedValue.COMPLEX_UNIT_SP, 17f)
                setOnClickPendingIntent(R.id.widget_badge, intents.createEventPendingIntent(appWidgetId, today))
            }
            else -> {
                setTextViewText(R.id.widget_badge, "\u21BB")
                setTextViewTextSize(R.id.widget_badge, TypedValue.COMPLEX_UNIT_SP, 13f)
                setOnClickPendingIntent(R.id.widget_badge, intents.refreshPendingIntent(kind, appWidgetId))
            }
        }
        setViewVisibility(R.id.widget_empty, View.GONE)
        setViewVisibility(R.id.widget_list, View.VISIBLE)
    }

    private fun RemoteViews.showEmpty(text: String, palette: WidgetPalette) {
        setViewVisibility(R.id.widget_empty, View.VISIBLE)
        setTextViewText(R.id.widget_empty, text)
        setTextColor(R.id.widget_empty, palette.muted)
    }

    private fun headerSubtitle(kind: KgsWidgetKind, today: LocalDate, settings: WidgetRenderSettings): String {
        val textContext = context.withWidgetLocale(settings.locale)
        val shortDate = today.format(DateTimeFormatter.ofPattern("EEE, d. MMM", settings.locale))
        return when (kind) {
            KgsWidgetKind.Month, KgsWidgetKind.Multi -> YearMonth.from(today).format(DateTimeFormatter.ofPattern("MMMM yyyy", settings.locale))
            KgsWidgetKind.Day -> shortDate
            KgsWidgetKind.Tasks -> settings.tasksWidgetDisplayMode.widgetLabel(textContext)
            KgsWidgetKind.Agenda -> shortDate
        }
    }
}
