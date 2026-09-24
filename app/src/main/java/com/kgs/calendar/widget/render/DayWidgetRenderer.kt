package com.kgs.calendar.widget.render

import android.content.Context
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.RemoteViews
import com.kgs.calendar.R
import com.kgs.calendar.data.settings.WidgetTaskCreateMode
import com.kgs.calendar.widget.KgsWidgetKind
import com.kgs.calendar.widget.WIDGET_DAY_LIST_SIDE_BLEED_DP
import com.kgs.calendar.widget.WIDGET_DAY_ROOT_PADDING_DP
import com.kgs.calendar.widget.WidgetLog
import com.kgs.calendar.widget.bitmap.dayTimelineBitmap
import com.kgs.calendar.widget.bitmap.widgetTodayDateIconBitmap
import com.kgs.calendar.widget.dpToPx
import com.kgs.calendar.widget.model.PreparedDayWidgetRender
import com.kgs.calendar.widget.model.WidgetDayAllDaySectionFrameData
import com.kgs.calendar.widget.model.WidgetDayGridRow
import com.kgs.calendar.widget.model.dayGridContentWidthDp
import com.kgs.calendar.widget.model.shouldHideWidgetTitle
import com.kgs.calendar.widget.model.usesDirectDayGridItems
import com.kgs.calendar.widget.withWidgetLocale
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal class DayWidgetRenderer(
    private val context: Context,
    private val zoneId: ZoneId,
) {
    private val packageName = context.packageName
    private val intents = WidgetPendingIntents(context)

    fun renderDayAllDaySectionFrame(
        frameData: WidgetDayAllDaySectionFrameData,
        expansionProgress: Float,
    ): RemoteViews {
        val textContext = context.withWidgetLocale(frameData.settings.locale)
        val views = RemoteViews(packageName, R.layout.widget_day_calendar)
        views.bindCollectionBottomFade(frameData.palette, visible = true)
        if (frameData.timeline.allDayItems.isNotEmpty()) {
            views.setViewVisibility(R.id.widget_day_all_day_section, View.VISIBLE)
            val settled = expansionProgress <= 0.001f || expansionProgress >= 0.999f
            WidgetDayGridRow(
                day = frameData.timeline.day,
                hour = null,
                allDayItems = frameData.timeline.allDayItems,
                priorityAnimationsEnabled = frameData.settings.priorityAnimationsEnabled && settled,
                maxVisibleAllDayItems = frameData.settings.maxVisibleAllDayItems,
                allDayExpanded = frameData.allDayExpanded,
                allDayExpansionProgress = expansionProgress,
            ).bindInto(
                target = views,
                context = textContext,
                packageName = packageName,
                palette = frameData.palette,
                widthDp = frameData.contentWidthDp,
                rootId = R.id.widget_day_all_day_section,
                artId = R.id.widget_day_all_day_art,
                motionId = R.id.widget_day_priority_motion,
                overlayId = R.id.widget_day_all_day_overlay,
                appWidgetId = frameData.appWidgetId,
                useImageUris = true,
            )
        } else {
            views.setViewVisibility(R.id.widget_day_all_day_section, View.GONE)
            views.removeAllViews(R.id.widget_day_all_day_overlay)
        }
        return views
    }

    fun renderDayWidget(
        prepared: PreparedDayWidgetRender,
        applyInitialScroll: Boolean = true,
        forceServiceCollection: Boolean = false,
    ): RemoteViews {
        val appWidgetId = prepared.appWidgetId
        val settings = prepared.settings
        val textContext = context.withWidgetLocale(settings.locale)
        val palette = prepared.palette
        val today = prepared.today
        val day = prepared.day
        val size = prepared.size
        val timeline = prepared.timeline
        val contentWidthDp = size.dayGridContentWidthDp()
        val gridRows = prepared.gridRows
        val views = RemoteViews(packageName, R.layout.widget_day_calendar)
        val contentPadding = context.dpToPx(WIDGET_DAY_ROOT_PADDING_DP)
        views.setInt(R.id.widget_root, "setBackgroundResource", palette.rootBackgroundRes)
        views.setInt(R.id.widget_header, "setBackgroundResource", palette.rootBackgroundRes)
        views.setInt(R.id.widget_day_header_compact, "setBackgroundResource", palette.rootBackgroundRes)
        views.setViewPadding(R.id.widget_content, contentPadding, contentPadding, contentPadding, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setViewLayoutMargin(R.id.widget_day_list_viewport, RemoteViews.MARGIN_BOTTOM, 0f, TypedValue.COMPLEX_UNIT_DIP)
        }
        val dayTitle = day.format(DateTimeFormatter.ofPattern("EEE, d. MMM", settings.locale))
        val hideDayTitle = shouldHideWidgetTitle(size.widthDp, dayTitle, reservedDp = 174f, textSp = 16f)
        views.setViewVisibility(R.id.widget_header, if (hideDayTitle) View.GONE else View.VISIBLE)
        views.setViewVisibility(R.id.widget_day_header_compact, if (hideDayTitle) View.VISIBLE else View.GONE)
        views.setTextViewText(R.id.widget_title, dayTitle)
        views.setTextColor(R.id.widget_title, palette.text)
        views.setViewVisibility(R.id.widget_title, View.VISIBLE)
        views.setInt(R.id.widget_header, "setGravity", Gravity.CENTER_VERTICAL)
        views.setImageViewBitmap(R.id.widget_day_today, widgetTodayDateIconBitmap(context, palette.onAccent, today.dayOfMonth))
        views.setImageViewBitmap(R.id.widget_day_today_compact, widgetTodayDateIconBitmap(context, palette.onAccent, today.dayOfMonth))
        views.setInt(R.id.widget_day_today, "setBackgroundResource", palette.badgeBackgroundRes)
        views.setInt(R.id.widget_day_today_compact, "setBackgroundResource", palette.badgeBackgroundRes)
        val todayVisibility = if (day == today) View.GONE else View.VISIBLE
        views.setViewVisibility(R.id.widget_day_today, todayVisibility)
        views.setViewVisibility(R.id.widget_day_today_compact, todayVisibility)
        views.setOnClickPendingIntent(R.id.widget_day_today, intents.dayTodayPendingIntent(appWidgetId))
        views.setOnClickPendingIntent(R.id.widget_day_today_compact, intents.dayTodayPendingIntent(appWidgetId))
        views.setTextViewText(R.id.widget_badge, "+")
        views.setTextViewText(R.id.widget_badge_compact, "+")
        views.setTextViewTextSize(R.id.widget_badge, TypedValue.COMPLEX_UNIT_SP, 17f)
        views.setTextViewTextSize(R.id.widget_badge_compact, TypedValue.COMPLEX_UNIT_SP, 17f)
        views.setTextColor(R.id.widget_badge, palette.onAccent)
        views.setTextColor(R.id.widget_badge_compact, palette.onAccent)
        views.setInt(R.id.widget_badge, "setBackgroundResource", palette.badgeBackgroundRes)
        views.setInt(R.id.widget_badge_compact, "setBackgroundResource", palette.badgeBackgroundRes)
        val createTaskIntent = intents.createTaskPendingIntent(appWidgetId, day, WidgetTaskCreateMode.Today)
        views.setOnClickPendingIntent(R.id.widget_badge, createTaskIntent)
        views.setOnClickPendingIntent(R.id.widget_badge_compact, createTaskIntent)
        views.setTextViewText(R.id.widget_month_prev, "\u2039")
        views.setTextViewText(R.id.widget_month_next, "\u203A")
        views.setTextViewText(R.id.widget_month_prev_compact, "\u2039")
        views.setTextViewText(R.id.widget_month_next_compact, "\u203A")
        views.setTextColor(R.id.widget_month_prev, palette.accent)
        views.setTextColor(R.id.widget_month_next, palette.accent)
        views.setTextColor(R.id.widget_month_prev_compact, palette.accent)
        views.setTextColor(R.id.widget_month_next_compact, palette.accent)
        val previousDayIntent = intents.dayNavigationPendingIntent(appWidgetId, previous = true)
        val nextDayIntent = intents.dayNavigationPendingIntent(appWidgetId, previous = false)
        views.setOnClickPendingIntent(R.id.widget_month_prev, previousDayIntent)
        views.setOnClickPendingIntent(R.id.widget_month_prev_compact, previousDayIntent)
        views.setOnClickPendingIntent(R.id.widget_month_next, nextDayIntent)
        views.setOnClickPendingIntent(R.id.widget_month_next_compact, nextDayIntent)
        val openApp = intents.openMainAppPendingIntent(appWidgetId)
        views.setOnClickPendingIntent(R.id.widget_root, openApp)
        views.setOnClickPendingIntent(R.id.widget_content, openApp)
        views.setOnClickPendingIntent(R.id.widget_title, openApp)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setViewVisibility(R.id.widget_day_timeline_art, View.GONE)
            views.setViewVisibility(R.id.widget_list, View.VISIBLE)
            if (timeline.allDayItems.isNotEmpty()) {
                views.setViewVisibility(R.id.widget_day_all_day_section, View.VISIBLE)
                WidgetDayGridRow(
                    day = timeline.day,
                    hour = null,
                    allDayItems = timeline.allDayItems,
                    priorityAnimationsEnabled = settings.priorityAnimationsEnabled,
                    maxVisibleAllDayItems = settings.maxVisibleAllDayItems,
                    allDayExpanded = prepared.allDayExpanded,
                ).bindInto(
                    target = views,
                    context = textContext,
                    packageName = packageName,
                    palette = palette,
                    widthDp = contentWidthDp,
                    rootId = R.id.widget_day_all_day_section,
                    artId = R.id.widget_day_all_day_art,
                    motionId = R.id.widget_day_priority_motion,
                    overlayId = R.id.widget_day_all_day_overlay,
                    appWidgetId = appWidgetId,
                )
            } else {
                views.setViewVisibility(R.id.widget_day_all_day_section, View.GONE)
                views.removeAllViews(R.id.widget_day_all_day_overlay)
            }
            if (usesDirectDayGridItems() && !forceServiceCollection) {
                views.setPendingIntentTemplate(R.id.widget_list, intents.collectionClickPendingIntent(KgsWidgetKind.Day, appWidgetId))
                views.bindDirectDayGridItems(
                    context = textContext,
                    packageName = packageName,
                    palette = palette,
                    rows = gridRows,
                    widthDp = contentWidthDp + WIDGET_DAY_LIST_SIDE_BLEED_DP * 2f,
                    appWidgetId = appWidgetId,
                )
            } else if (applyInitialScroll) {
                views.setRemoteAdapter(R.id.widget_list, intents.collectionAdapterIntent(KgsWidgetKind.Day, appWidgetId))
                views.setPendingIntentTemplate(R.id.widget_list, intents.collectionClickPendingIntent(KgsWidgetKind.Day, appWidgetId))
            }
        } else {
            views.setOnClickPendingIntent(R.id.widget_day_timeline_art, openApp)
            views.setViewVisibility(R.id.widget_day_all_day_section, View.GONE)
            views.setViewVisibility(R.id.widget_day_timeline_art, View.VISIBLE)
            views.setViewVisibility(R.id.widget_list, View.GONE)
            views.setImageViewBitmap(R.id.widget_day_timeline_art, dayTimelineBitmap(context, zoneId, size, palette, timeline))
        }
        views.bindCollectionBottomFade(palette, visible = true)
        views.setViewVisibility(R.id.widget_empty, View.GONE)
        views.setViewVisibility(R.id.widget_month_section, View.GONE)
        WidgetLog.d(context, "Day widget $appWidgetId date=$day timed=${timeline.timedItems.size} allDay=${timeline.allDayItems.size}")
        return views
    }
}
