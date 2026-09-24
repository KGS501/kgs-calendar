package com.kgs.calendar.widget.render

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.RemoteViews
import com.kgs.calendar.R
import com.kgs.calendar.data.settings.SettingsStore
import com.kgs.calendar.widget.KgsWidgetKind
import com.kgs.calendar.widget.WIDGET_MULTI_CONTENT_PADDING_DP
import com.kgs.calendar.widget.bitmap.widgetTodayDateIconBitmap
import com.kgs.calendar.widget.dpToPx
import com.kgs.calendar.widget.model.PreparedMultiWidgetRender
import com.kgs.calendar.widget.model.WidgetCollectionRenderOptions
import com.kgs.calendar.widget.model.WidgetMonthPage
import com.kgs.calendar.widget.model.WidgetMonthRenderSpec
import com.kgs.calendar.widget.model.WidgetRenderSettings
import com.kgs.calendar.widget.model.WidgetSize
import com.kgs.calendar.widget.model.emptyText
import com.kgs.calendar.widget.model.shouldHideWidgetTitle
import com.kgs.calendar.widget.model.usesDirectCollectionItems
import com.kgs.calendar.widget.theme.WidgetPalette
import com.kgs.calendar.widget.withWidgetLocale
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.roundToInt

internal class MultiWidgetRenderer(
    private val context: Context,
    private val zoneId: ZoneId,
    private val monthPages: WidgetMonthPageBinder,
) {
    private val packageName = context.packageName
    private val intents = WidgetPendingIntents(context)

    fun renderMultiMonthNavigationPage(
        appWidgetId: Int,
        options: Bundle,
        settings: WidgetRenderSettings,
        page: WidgetMonthPage,
    ): RemoteViews {
        val today = LocalDate.now(zoneId)
        val palette = WidgetPalette.from(context, settings.themeMode, settings.colorMode)
        val size = WidgetSize.from(context, options, KgsWidgetKind.Multi)
        val contentPaddingDp = 12
        val contentHeightDp = (size.heightDp - contentPaddingDp).coerceAtLeast(2)
        val monthPercent = SettingsStore.normalizeMultiWidgetMonthPercent(settings.multiWidgetMonthPercent)
        val monthPanelHeightDp = ((contentHeightDp * monthPercent) / 100f)
            .roundToInt()
            .coerceIn(1, contentHeightDp - 1)
        val agendaPanelHeightDp = (contentHeightDp - monthPanelHeightDp).coerceAtLeast(1)
        val monthSpec = WidgetMonthRenderSpec.from(
            WidgetSize(widthDp = size.widthDp, heightDp = monthPanelHeightDp),
            page.rowCount,
        )
        val views = RemoteViews(packageName, R.layout.widget_calendar_multi)
        val contentPadding = context.dpToPx(contentPaddingDp)
        views.setInt(R.id.widget_root, "setBackgroundResource", palette.rootBackgroundRes)
        views.setViewPadding(R.id.widget_content, contentPadding, contentPadding, contentPadding, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setViewLayoutHeight(
                R.id.widget_multi_month_panel,
                monthPanelHeightDp.toFloat(),
                TypedValue.COMPLEX_UNIT_DIP,
            )
            views.setViewLayoutHeight(
                R.id.widget_multi_agenda_panel,
                agendaPanelHeightDp.toFloat(),
                TypedValue.COMPLEX_UNIT_DIP,
            )
        }
        views.setOnClickPendingIntent(R.id.widget_root, intents.openMainAppPendingIntent(appWidgetId))

        val monthTitle = page.title(settings.locale)
        val hideTitle = shouldHideWidgetTitle(
            size.widthDp - contentPaddingDp * 2,
            monthTitle,
            reservedDp = 150f,
            textSp = monthSpec.titleTextSp,
        )
        val showingCurrentMonth = page.month == YearMonth.from(today)
        views.setTextViewText(R.id.widget_multi_month_title, monthTitle)
        views.setTextColor(R.id.widget_multi_month_title, palette.text)
        views.setTextViewTextSize(
            R.id.widget_multi_month_title,
            TypedValue.COMPLEX_UNIT_SP,
            monthSpec.titleTextSp,
        )
        views.setViewVisibility(R.id.widget_multi_month_title, if (hideTitle) View.GONE else View.VISIBLE)
        views.setInt(
            R.id.widget_multi_month_header,
            "setGravity",
            if (hideTitle) Gravity.CENTER else Gravity.CENTER_VERTICAL,
        )
        views.setTextViewText(R.id.widget_multi_month_badge, "+")
        views.setTextViewTextSize(R.id.widget_multi_month_badge, TypedValue.COMPLEX_UNIT_SP, 17f)
        views.setTextColor(R.id.widget_multi_month_badge, palette.onAccent)
        views.setInt(R.id.widget_multi_month_badge, "setBackgroundResource", palette.badgeBackgroundRes)
        views.setOnClickPendingIntent(R.id.widget_multi_month_badge, intents.createEventPendingIntent(appWidgetId, today))
        views.setImageViewBitmap(
            R.id.widget_day_today,
            widgetTodayDateIconBitmap(context, palette.onAccent, today.dayOfMonth),
        )
        views.setInt(R.id.widget_day_today, "setBackgroundResource", palette.badgeBackgroundRes)
        views.setViewVisibility(R.id.widget_day_today, if (showingCurrentMonth) View.GONE else View.VISIBLE)
        views.setOnClickPendingIntent(
            R.id.widget_day_today,
            intents.monthTodayPendingIntent(appWidgetId, targetKind = KgsWidgetKind.Multi),
        )
        views.setTextViewText(R.id.widget_month_prev, "\u2039")
        views.setTextViewText(R.id.widget_month_next, "\u203A")
        views.setTextColor(R.id.widget_month_prev, palette.accent)
        views.setTextColor(R.id.widget_month_next, palette.accent)
        views.setOnClickPendingIntent(
            R.id.widget_month_prev,
            intents.monthNavigationPendingIntent(appWidgetId, previous = true, targetKind = KgsWidgetKind.Multi),
        )
        views.setOnClickPendingIntent(
            R.id.widget_month_next,
            intents.monthNavigationPendingIntent(appWidgetId, previous = false, targetKind = KgsWidgetKind.Multi),
        )
        views.setOnClickPendingIntent(
            R.id.widget_multi_month_title,
            intents.openAppPendingIntent(KgsWidgetKind.Month, 66_000 + appWidgetId, page.month.atDay(1)),
        )
        monthPages.bindMonthPage(
            views = views,
            pageContainerId = R.id.widget_month_section,
            page = page,
            settings = settings,
            palette = palette,
            renderSpec = monthSpec,
            appWidgetId = appWidgetId,
        )
        views.setViewVisibility(R.id.widget_month_section, View.VISIBLE)
        return views
    }

    fun renderMultiWidget(
        appWidgetId: Int,
        renderData: PreparedMultiWidgetRender,
        forceServiceCollection: Boolean = false,
    ): RemoteViews {
        require(renderData.appWidgetId == appWidgetId)
        val today = renderData.today
        val settings = renderData.settings
        val textContext = context.withWidgetLocale(settings.locale)
        val palette = renderData.palette
        val size = renderData.size
        val contentPaddingDp = WIDGET_MULTI_CONTENT_PADDING_DP
        val monthPanelHeightDp = renderData.monthPanelHeightDp
        val agendaPanelHeightDp = renderData.agendaPanelHeightDp
        val page = renderData.page
        val monthSpec = renderData.monthSpec
        val views = RemoteViews(packageName, R.layout.widget_calendar_multi)
        val contentPadding = context.dpToPx(contentPaddingDp)
        val openApp = intents.openMainAppPendingIntent(appWidgetId)

        views.setInt(R.id.widget_root, "setBackgroundResource", palette.rootBackgroundRes)
        views.setViewPadding(R.id.widget_content, contentPadding, contentPadding, contentPadding, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setViewLayoutHeight(R.id.widget_multi_month_panel, monthPanelHeightDp.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
            views.setViewLayoutHeight(R.id.widget_multi_agenda_panel, agendaPanelHeightDp.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
        }
        views.setOnClickPendingIntent(R.id.widget_root, openApp)

        val multiMonthTitle = page.title(settings.locale)
        val hideMultiMonthTitle = shouldHideWidgetTitle(size.widthDp - contentPaddingDp * 2, multiMonthTitle, reservedDp = 150f, textSp = monthSpec.titleTextSp)
        val showingCurrentMonth = page.month == YearMonth.from(today)
        views.setTextViewText(R.id.widget_multi_month_title, multiMonthTitle)
        views.setTextColor(R.id.widget_multi_month_title, palette.text)
        views.setTextViewTextSize(R.id.widget_multi_month_title, TypedValue.COMPLEX_UNIT_SP, monthSpec.titleTextSp)
        views.setViewVisibility(R.id.widget_multi_month_title, if (hideMultiMonthTitle) View.GONE else View.VISIBLE)
        views.setInt(R.id.widget_multi_month_header, "setGravity", if (hideMultiMonthTitle) Gravity.CENTER else Gravity.CENTER_VERTICAL)
        views.setTextViewText(R.id.widget_multi_month_badge, "+")
        views.setTextViewTextSize(R.id.widget_multi_month_badge, TypedValue.COMPLEX_UNIT_SP, 17f)
        views.setTextColor(R.id.widget_multi_month_badge, palette.onAccent)
        views.setInt(R.id.widget_multi_month_badge, "setBackgroundResource", palette.badgeBackgroundRes)
        views.setOnClickPendingIntent(R.id.widget_multi_month_badge, intents.createEventPendingIntent(appWidgetId, today))
        views.setImageViewBitmap(R.id.widget_day_today, widgetTodayDateIconBitmap(context, palette.onAccent, today.dayOfMonth))
        views.setInt(R.id.widget_day_today, "setBackgroundResource", palette.badgeBackgroundRes)
        views.setViewVisibility(R.id.widget_day_today, if (showingCurrentMonth) View.GONE else View.VISIBLE)
        views.setOnClickPendingIntent(R.id.widget_day_today, intents.monthTodayPendingIntent(appWidgetId, targetKind = KgsWidgetKind.Multi))
        views.setTextViewText(R.id.widget_month_prev, "\u2039")
        views.setTextViewText(R.id.widget_month_next, "\u203A")
        views.setTextColor(R.id.widget_month_prev, palette.accent)
        views.setTextColor(R.id.widget_month_next, palette.accent)
        views.setOnClickPendingIntent(R.id.widget_month_prev, intents.monthNavigationPendingIntent(appWidgetId, previous = true, targetKind = KgsWidgetKind.Multi))
        views.setOnClickPendingIntent(R.id.widget_month_next, intents.monthNavigationPendingIntent(appWidgetId, previous = false, targetKind = KgsWidgetKind.Multi))
        views.setOnClickPendingIntent(
            R.id.widget_multi_month_title,
            intents.openAppPendingIntent(KgsWidgetKind.Month, 66_000 + appWidgetId, page.month.atDay(1)),
        )
        monthPages.bindMonthPage(
            views = views,
            pageContainerId = R.id.widget_month_section,
            page = page,
            settings = settings,
            palette = palette,
            renderSpec = monthSpec,
            appWidgetId = appWidgetId,
        )
        views.setViewVisibility(R.id.widget_month_section, View.VISIBLE)

        views.bindCollectionBottomFade(palette, visible = true)
        views.setInt(R.id.widget_multi_agenda_top_fade, "setBackgroundResource", palette.topFadeRes)
        views.setTextViewText(R.id.widget_empty, KgsWidgetKind.Multi.emptyText(textContext))
        views.setTextColor(R.id.widget_empty, palette.muted)
        views.setViewVisibility(R.id.widget_empty, View.GONE)
        views.setViewVisibility(R.id.widget_list, View.VISIBLE)
        if (KgsWidgetKind.Multi.usesDirectCollectionItems() && !forceServiceCollection) {
            views.bindDirectCollectionItems(
                context = textContext,
                packageName = packageName,
                palette = palette,
                rows = renderData.collectionSnapshot.rows,
                sourceKind = KgsWidgetKind.Multi,
                appWidgetId = appWidgetId,
                renderOptions = WidgetCollectionRenderOptions(taskArtWidthDp = renderData.collectionSnapshot.taskArtWidthDp),
            )
        } else {
            views.setRemoteAdapter(R.id.widget_list, intents.collectionAdapterIntent(KgsWidgetKind.Multi, appWidgetId))
        }
        views.setEmptyView(R.id.widget_list, R.id.widget_empty)
        views.setPendingIntentTemplate(R.id.widget_list, intents.collectionClickPendingIntent(KgsWidgetKind.Multi, appWidgetId))
        return views
    }
}
