package com.kgs.calendar.widget.render

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.RemoteViews
import com.kgs.calendar.R
import com.kgs.calendar.widget.KgsWidgetKind
import com.kgs.calendar.widget.WidgetLog
import com.kgs.calendar.widget.bitmap.widgetTodayDateIconBitmap
import com.kgs.calendar.widget.dpToPx
import com.kgs.calendar.widget.model.MonthWidgetRenderResult
import com.kgs.calendar.widget.model.PreparedMonthWidgetRender
import com.kgs.calendar.widget.model.WidgetMonthPage
import com.kgs.calendar.widget.model.WidgetMonthRenderSpec
import com.kgs.calendar.widget.model.WidgetRenderSettings
import com.kgs.calendar.widget.model.shouldHideWidgetTitle
import com.kgs.calendar.widget.model.widgetRequestCode
import com.kgs.calendar.widget.theme.WidgetPalette
import java.time.LocalDate
import java.time.YearMonth

internal class MonthWidgetRenderer(
    private val context: Context,
    private val monthPages: WidgetMonthPageBinder,
) {
    private val packageName = context.packageName
    private val intents = WidgetPendingIntents(context)

    fun renderPreparedMonthPage(prepared: PreparedMonthWidgetRender): MonthWidgetRenderResult {
        WidgetLog.d(
            context,
            "Month widget ${prepared.appWidgetId} bucket=${prepared.renderSpec.bucket.name} rows=${prepared.page.rowCount} " +
                "weekHeight=${prepared.renderSpec.weekCellHeightDp} items=${prepared.itemCount} complete=${prepared.hasCompleteData}",
        )

        return MonthWidgetRenderResult(
            views = renderMonthRoot(
                appWidgetId = prepared.appWidgetId,
                settings = prepared.settings,
                palette = prepared.palette,
                today = prepared.today,
                page = prepared.page,
                renderSpec = prepared.renderSpec,
            ),
            hasCompleteData = prepared.hasCompleteData,
            signature = prepared.signature,
        )
    }

    private fun renderMonthRoot(
        appWidgetId: Int,
        settings: WidgetRenderSettings,
        palette: WidgetPalette,
        today: LocalDate,
        page: WidgetMonthPage,
        renderSpec: WidgetMonthRenderSpec,
    ): RemoteViews {
        val views = RemoteViews(packageName, monthRenderedLayout())
        views.setInt(R.id.widget_root, "setBackgroundResource", palette.rootBackgroundRes)
        val padding = context.dpToPx(renderSpec.rootPaddingDp)
        views.setViewPadding(R.id.widget_root, padding, padding, padding, padding)
        val monthTitle = page.title(settings.locale)
        val hideMonthTitle = shouldHideWidgetTitle(renderSpec.totalWidthDp, monthTitle, reservedDp = 178f, textSp = renderSpec.titleTextSp)
        val currentMonth = YearMonth.from(today)
        val showingCurrentMonth = page.month == currentMonth
        views.setTextViewText(R.id.widget_title, monthTitle)
        views.setTextColor(R.id.widget_title, palette.text)
        views.setTextViewTextSize(R.id.widget_title, TypedValue.COMPLEX_UNIT_SP, renderSpec.titleTextSp)
        views.setViewVisibility(R.id.widget_title, if (hideMonthTitle) View.GONE else View.VISIBLE)
        views.setInt(R.id.widget_header, "setGravity", if (hideMonthTitle) Gravity.CENTER else Gravity.CENTER_VERTICAL)
        val monthOpenPendingIntent = intents.openAppPendingIntent(
            kind = KgsWidgetKind.Month,
            requestCode = widgetRequestCode("month-open:$appWidgetId:${page.month}"),
            date = page.month.atDay(1),
            clearTask = true,
        )
        views.setOnClickPendingIntent(R.id.widget_header, monthOpenPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_title, monthOpenPendingIntent)
        views.setTextViewText(R.id.widget_badge, "+")
        views.setTextColor(R.id.widget_badge, palette.onAccent)
        views.setInt(R.id.widget_badge, "setBackgroundResource", palette.badgeBackgroundRes)
        views.setOnClickPendingIntent(R.id.widget_badge, intents.createEventPendingIntent(appWidgetId, today))
        views.setImageViewBitmap(R.id.widget_day_today, widgetTodayDateIconBitmap(context, palette.onAccent, today.dayOfMonth))
        views.setInt(R.id.widget_day_today, "setBackgroundResource", palette.badgeBackgroundRes)
        views.setViewVisibility(R.id.widget_day_today, if (showingCurrentMonth) View.GONE else View.VISIBLE)
        views.setOnClickPendingIntent(R.id.widget_day_today, intents.monthTodayPendingIntent(appWidgetId))
        views.setTextViewText(R.id.widget_month_prev, "\u2039")
        views.setTextViewText(R.id.widget_month_next, "\u203A")
        views.setTextColor(R.id.widget_month_prev, palette.accent)
        views.setTextColor(R.id.widget_month_next, palette.accent)
        views.setOnClickPendingIntent(R.id.widget_month_prev, intents.monthNavigationPendingIntent(appWidgetId, previous = true))
        views.setOnClickPendingIntent(R.id.widget_month_next, intents.monthNavigationPendingIntent(appWidgetId, previous = false))

        monthPages.bindMonthPage(views, R.id.widget_month_page_a, page, settings, palette, renderSpec, appWidgetId)
        views.removeAllViews(R.id.widget_month_page_b)
        views.setDisplayedChild(R.id.widget_month_flipper, 0)
        return views
    }

    private fun monthRenderedLayout(): Int =
        R.layout.widget_month_calendar
}
