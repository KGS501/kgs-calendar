package com.kgs.calendar.widget.render

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import com.kgs.calendar.R
import com.kgs.calendar.domain.task.taskPriorityIntensity
import com.kgs.calendar.widget.COLLECTION_ACTION_OPEN
import com.kgs.calendar.widget.COLLECTION_ACTION_TOGGLE_TASK
import com.kgs.calendar.widget.EXTRA_COLLECTION_ACTION
import com.kgs.calendar.widget.EXTRA_WIDGET_ACTION
import com.kgs.calendar.widget.EXTRA_WIDGET_DATE
import com.kgs.calendar.widget.EXTRA_WIDGET_EVENT_UID
import com.kgs.calendar.widget.EXTRA_WIDGET_KIND
import com.kgs.calendar.widget.EXTRA_WIDGET_TASK_UID
import com.kgs.calendar.widget.KgsWidgetActionReceiver
import com.kgs.calendar.widget.KgsWidgetKind
import com.kgs.calendar.widget.KgsWidgetProvider
import com.kgs.calendar.widget.TAG
import com.kgs.calendar.widget.WIDGET_ACTION_OPEN_EVENT
import com.kgs.calendar.widget.WIDGET_ACTION_OPEN_TASK
import com.kgs.calendar.widget.WIDGET_AGENDA_PRIORITY_MOTION_FRAME_IDS
import com.kgs.calendar.widget.WIDGET_DAY_ALL_DAY_CARD_HEIGHT_DP
import com.kgs.calendar.widget.WIDGET_DAY_ALL_DAY_LANE_STRIDE_DP
import com.kgs.calendar.widget.WIDGET_DAY_ALL_DAY_TOP_PADDING_DP
import com.kgs.calendar.widget.WIDGET_DAY_GRID_GAP_DP
import com.kgs.calendar.widget.WIDGET_DAY_PRIORITY_OVERDRAW_DP
import com.kgs.calendar.widget.WIDGET_DAY_TIME_COLUMN_WIDTH_DP
import com.kgs.calendar.widget.WIDGET_TASK_MIN_CARD_WIDTH_DP
import com.kgs.calendar.widget.bitmap.KgsWidgetBitmapUriStore
import com.kgs.calendar.widget.bitmap.dayGridRowBitmap
import com.kgs.calendar.widget.bitmap.dayNowLineOverlayBitmap
import com.kgs.calendar.widget.bitmap.dayPriorityMotionBitmap
import com.kgs.calendar.widget.model.WidgetDayGridRow
import com.kgs.calendar.widget.model.WidgetDayItem
import com.kgs.calendar.widget.model.hasDayPriorityMotion
import com.kgs.calendar.widget.model.priorityMotionFrameIntervalMillis
import com.kgs.calendar.widget.model.widgetRequestCode
import com.kgs.calendar.widget.theme.WidgetPalette
import com.kgs.calendar.widget.update.WidgetPerformanceMonitor
import java.time.LocalDate

internal fun WidgetDayGridRow.toRemoteViews(
    context: Context,
    packageName: String,
    palette: WidgetPalette,
    widthDp: Float,
    appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID,
): RemoteViews {
    val views = RemoteViews(packageName, R.layout.widget_day_hour_row)
    bindInto(
        target = views,
        context = context,
        packageName = packageName,
        palette = palette,
        widthDp = widthDp,
        rootId = R.id.widget_day_hour_root,
        artId = R.id.widget_day_hour_art,
        motionId = R.id.widget_day_priority_motion,
        nowOverlayId = R.id.widget_day_now_overlay,
        overlayId = R.id.widget_day_hour_overlay,
        appWidgetId = appWidgetId,
        useImageUris = appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID,
        useFillInIntents = true,
    )
    return views
}

internal fun WidgetDayGridRow.bindInto(
    target: RemoteViews,
    context: Context,
    packageName: String,
    palette: WidgetPalette,
    widthDp: Float,
    rootId: Int,
    artId: Int,
    motionId: Int,
    nowOverlayId: Int? = null,
    overlayId: Int,
    appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID,
    useImageUris: Boolean = false,
    useFillInIntents: Boolean = false,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        target.setViewLayoutHeight(rootId, rowHeightDp, TypedValue.COMPLEX_UNIT_DIP)
        target.setViewLayoutHeight(artId, rowHeightDp, TypedValue.COMPLEX_UNIT_DIP)
        target.setViewLayoutHeight(overlayId, rowHeightDp, TypedValue.COMPLEX_UNIT_DIP)
    }
    target.setDayGridImage(
        context = context,
        appWidgetId = appWidgetId,
        viewId = artId,
        cacheKey = imageCacheKey(palette, widthDp, "art"),
        useImageUri = useImageUris,
        bitmapProvider = { dayGridRowBitmap(
            context = context,
            palette = palette,
            widthDp = widthDp,
            heightDp = rowHeightDp,
            omitPriorityCards = hasPriorityMotion,
        ) },
    )
    bindPriorityMotion(
        target = target,
        context = context,
        palette = palette,
        widthDp = widthDp,
        motionId = motionId,
        appWidgetId = appWidgetId,
        useImageUris = useImageUris,
    )
    bindNowLineOverlay(
        target = target,
        context = context,
        palette = palette,
        widthDp = widthDp,
        nowOverlayId = nowOverlayId,
        appWidgetId = appWidgetId,
        useImageUris = useImageUris,
    )
    if (useFillInIntents) {
        target.setOnClickFillInIntent(rootId, openDayFillInIntent())
    } else if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
        target.setOnClickPendingIntent(rootId, openDayBroadcastPendingIntent(context, appWidgetId))
    } else {
        target.setOnClickFillInIntent(rootId, openDayFillInIntent())
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        bindTouchTargets(target, context, packageName, widthDp, overlayId, appWidgetId, useFillInIntents)
    }
}

private fun WidgetDayGridRow.bindPriorityMotion(
    target: RemoteViews,
    context: Context,
    palette: WidgetPalette,
    widthDp: Float,
    motionId: Int,
    appWidgetId: Int,
    useImageUris: Boolean,
) {
    if (!hasPriorityMotion) {
        target.setViewVisibility(motionId, View.GONE)
        return
    }
    val motionBounds = priorityMotionBounds(widthDp) ?: run {
        target.setViewVisibility(motionId, View.GONE)
        return
    }
    val frameIds = WIDGET_AGENDA_PRIORITY_MOTION_FRAME_IDS
    val fastestPriority = (allDayItems.asSequence() + timedItems.asSequence().map { it.item })
        .filter { it.hasDayPriorityMotion() }
        .mapNotNull { it.priority }
        .minOrNull()
    val intensity = taskPriorityIntensity(fastestPriority)
    val frameIntervalMillis = priorityMotionFrameIntervalMillis(fastestPriority, intensity, frameIds.size)
    frameIds.forEachIndexed { frame, viewId ->
        target.setDayGridImage(
            context = context,
            appWidgetId = appWidgetId,
            viewId = viewId,
            cacheKey = imageCacheKey(palette, widthDp, "priority-$frame-$frameIntervalMillis"),
            useImageUri = useImageUris,
            bitmapProvider = { dayPriorityMotionBitmap(
                context = context,
                palette = palette,
                widthDp = widthDp,
                heightDp = rowHeightDp,
                frame = frame,
                frameCount = frameIds.size,
                frameIntervalMillis = frameIntervalMillis,
                motionBounds = motionBounds,
            ) },
        )
    }
    target.setInt(
        motionId,
        "setFlipInterval",
        frameIntervalMillis,
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val overdraw = WIDGET_DAY_PRIORITY_OVERDRAW_DP
        target.setViewLayoutWidth(motionId, motionBounds.width() + overdraw * 2f, TypedValue.COMPLEX_UNIT_DIP)
        target.setViewLayoutHeight(motionId, motionBounds.height() + overdraw * 2f, TypedValue.COMPLEX_UNIT_DIP)
        target.setViewLayoutMargin(motionId, RemoteViews.MARGIN_LEFT, motionBounds.left - overdraw, TypedValue.COMPLEX_UNIT_DIP)
        target.setViewLayoutMargin(motionId, RemoteViews.MARGIN_TOP, motionBounds.top - overdraw, TypedValue.COMPLEX_UNIT_DIP)
    }
    target.setViewVisibility(motionId, View.VISIBLE)
}

private fun WidgetDayGridRow.bindNowLineOverlay(
    target: RemoteViews,
    context: Context,
    palette: WidgetPalette,
    widthDp: Float,
    nowOverlayId: Int?,
    appWidgetId: Int,
    useImageUris: Boolean,
) {
    if (nowOverlayId == null) return
    if (hour == null || nowMinute == null || rowHeightDp <= 0f) {
        target.setViewVisibility(nowOverlayId, View.GONE)
        return
    }
    target.setDayGridImage(
        context = context,
        appWidgetId = appWidgetId,
        viewId = nowOverlayId,
        cacheKey = imageCacheKey(palette, widthDp, "now-line-$nowMinute"),
        useImageUri = useImageUris,
        bitmapProvider = { dayNowLineOverlayBitmap(
            context = context,
            palette = palette,
            widthDp = widthDp,
            heightDp = rowHeightDp,
        ) },
    )
    target.setViewVisibility(nowOverlayId, View.VISIBLE)
}

private fun RemoteViews.setDayGridImage(
    context: Context,
    appWidgetId: Int,
    viewId: Int,
    cacheKey: String,
    useImageUri: Boolean,
    bitmapProvider: () -> Bitmap,
) {
    if (!useImageUri) {
        val bitmap = bitmapProvider()
        WidgetPerformanceMonitor.current()?.recordBitmapRendered()
        setImageViewBitmap(viewId, bitmap)
        return
    }
    val cachedUri = runCatching {
        KgsWidgetBitmapUriStore.getIfPresent(context, appWidgetId, cacheKey)
    }.onFailure { error ->
        Log.w(TAG, "Failed to read cached Day widget image", error)
    }.getOrNull()
    if (cachedUri != null) {
        setImageViewUri(viewId, cachedUri)
        return
    }
    val bitmap = bitmapProvider()
    WidgetPerformanceMonitor.current()?.recordBitmapRendered()
    val uri = runCatching {
        KgsWidgetBitmapUriStore.put(context, appWidgetId, cacheKey, bitmap)
    }.onFailure { error ->
        Log.w(TAG, "Failed to cache Day widget image; falling back to an inline bitmap", error)
    }.getOrNull()
    if (uri != null) {
        setImageViewUri(viewId, uri)
        bitmap.recycle()
    } else {
        setImageViewBitmap(viewId, bitmap)
    }
}

@RequiresApi(Build.VERSION_CODES.S)
private fun WidgetDayGridRow.bindTouchTargets(
    parent: RemoteViews,
    context: Context,
    packageName: String,
    widthDp: Float,
    overlayId: Int,
    appWidgetId: Int,
    useFillInIntents: Boolean,
) {
    parent.removeAllViews(overlayId)
    val targets = when (hour) {
        null -> allDayTouchTargets(widthDp)
        else -> timedTouchTargets(widthDp, hour)
    }
    targets.forEach { target ->
        val touch = RemoteViews(packageName, R.layout.widget_day_card_touch)
        touch.setViewLayoutWidth(R.id.widget_day_card_touch_root, target.widthDp, TypedValue.COMPLEX_UNIT_DIP)
        touch.setViewLayoutHeight(R.id.widget_day_card_touch_root, target.heightDp, TypedValue.COMPLEX_UNIT_DIP)
        touch.setViewLayoutMargin(R.id.widget_day_card_touch_root, RemoteViews.MARGIN_LEFT, target.leftDp, TypedValue.COMPLEX_UNIT_DIP)
        touch.setViewLayoutMargin(R.id.widget_day_card_touch_root, RemoteViews.MARGIN_TOP, target.topDp, TypedValue.COMPLEX_UNIT_DIP)
        touch.setViewVisibility(
            R.id.widget_day_card_checkbox_touch,
            if (target.item.isTask && !target.item.taskResourceHref.isNullOrBlank()) View.VISIBLE else View.GONE,
        )
        if (useFillInIntents) {
            touch.setOnClickFillInIntent(R.id.widget_day_card_click, target.item.openFillInIntent(day))
            touch.setOnClickFillInIntent(R.id.widget_day_card_touch_root, target.item.openFillInIntent(day))
            if (target.item.isTask && !target.item.taskResourceHref.isNullOrBlank()) {
                touch.setOnClickFillInIntent(R.id.widget_day_card_checkbox_touch, target.item.toggleFillInIntent())
            }
        } else if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            val openPendingIntent = target.item.openBroadcastPendingIntent(context, appWidgetId, day)
            touch.setOnClickPendingIntent(R.id.widget_day_card_click, openPendingIntent)
            touch.setOnClickPendingIntent(R.id.widget_day_card_touch_root, openPendingIntent)
            if (target.item.isTask && !target.item.taskResourceHref.isNullOrBlank()) {
                touch.setOnClickPendingIntent(R.id.widget_day_card_checkbox_touch, target.item.toggleBroadcastPendingIntent(context, appWidgetId))
            }
        } else {
            touch.setOnClickFillInIntent(R.id.widget_day_card_click, target.item.openFillInIntent(day))
            touch.setOnClickFillInIntent(R.id.widget_day_card_touch_root, target.item.openFillInIntent(day))
            if (target.item.isTask && !target.item.taskResourceHref.isNullOrBlank()) {
                touch.setOnClickFillInIntent(R.id.widget_day_card_checkbox_touch, target.item.toggleFillInIntent())
            }
        }
        parent.addView(overlayId, touch)
    }
    if (hour == null && appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID && !useFillInIntents) {
        val showToggle = allDayHasOverflow || allDayExpanded
        if (showToggle) {
            parent.addAllDayToggleTarget(
                packageName = packageName,
                context = context,
                appWidgetId = appWidgetId,
                overlayId = overlayId,
                leftDp = 0f,
                topDp = 0f,
                widthDp = WIDGET_DAY_TIME_COLUMN_WIDTH_DP,
                heightDp = rowHeightDp,
            )
        }
        if (allDayHasOverflow && !renderExpandedAllDayItems) {
            val gridLeft = WIDGET_DAY_TIME_COLUMN_WIDTH_DP + WIDGET_DAY_GRID_GAP_DP
            val gridWidth = (widthDp - gridLeft - 2f).coerceAtLeast(WIDGET_TASK_MIN_CARD_WIDTH_DP)
            parent.addAllDayToggleTarget(
                packageName = packageName,
                context = context,
                appWidgetId = appWidgetId,
                overlayId = overlayId,
                leftDp = gridLeft,
                topDp = WIDGET_DAY_ALL_DAY_TOP_PADDING_DP + allDayOverflowLane * WIDGET_DAY_ALL_DAY_LANE_STRIDE_DP,
                widthDp = gridWidth,
                heightDp = WIDGET_DAY_ALL_DAY_CARD_HEIGHT_DP.toFloat(),
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.S)
private fun RemoteViews.addAllDayToggleTarget(
    packageName: String,
    context: Context,
    appWidgetId: Int,
    overlayId: Int,
    leftDp: Float,
    topDp: Float,
    widthDp: Float,
    heightDp: Float,
) {
    val toggle = RemoteViews(packageName, R.layout.widget_day_all_day_toggle_touch)
    toggle.setViewLayoutWidth(R.id.widget_day_all_day_toggle_root, widthDp, TypedValue.COMPLEX_UNIT_DIP)
    toggle.setViewLayoutHeight(R.id.widget_day_all_day_toggle_root, heightDp, TypedValue.COMPLEX_UNIT_DIP)
    toggle.setViewLayoutMargin(R.id.widget_day_all_day_toggle_root, RemoteViews.MARGIN_LEFT, leftDp, TypedValue.COMPLEX_UNIT_DIP)
    toggle.setViewLayoutMargin(R.id.widget_day_all_day_toggle_root, RemoteViews.MARGIN_TOP, topDp, TypedValue.COMPLEX_UNIT_DIP)
    toggle.setOnClickPendingIntent(R.id.widget_day_all_day_toggle_root, allDayTogglePendingIntent(context, appWidgetId))
    addView(overlayId, toggle)
}

private fun WidgetDayItem.openFillInIntent(date: LocalDate): Intent {
    val intent = Intent()
    intent.putExtra(EXTRA_COLLECTION_ACTION, COLLECTION_ACTION_OPEN)
    intent.putExtra(EXTRA_WIDGET_KIND, if (isTask) KgsWidgetKind.Tasks.name else KgsWidgetKind.Day.name)
    intent.putExtra(EXTRA_WIDGET_DATE, date.toString())
    if (isTask && !taskResourceHref.isNullOrBlank()) {
        intent.putExtra(EXTRA_WIDGET_ACTION, WIDGET_ACTION_OPEN_TASK)
        intent.putExtra(EXTRA_WIDGET_TASK_UID, taskResourceHref)
    } else if (!eventResourceHref.isNullOrBlank()) {
        intent.putExtra(EXTRA_WIDGET_ACTION, WIDGET_ACTION_OPEN_EVENT)
        intent.putExtra(EXTRA_WIDGET_EVENT_UID, eventResourceHref)
    }
    intent.data = Uri.parse("kgs-calendar://widget-day-grid-open/$stableKey")
    return intent
}

private fun WidgetDayItem.toggleFillInIntent(): Intent {
    val intent = Intent()
    intent.putExtra(EXTRA_COLLECTION_ACTION, COLLECTION_ACTION_TOGGLE_TASK)
    intent.putExtra(KgsWidgetProvider.EXTRA_TASK_RESOURCE_HREF, taskResourceHref)
    intent.data = Uri.parse("kgs-calendar://widget-day-grid-toggle/$stableKey")
    return intent
}

private fun WidgetDayItem.openBroadcastPendingIntent(context: Context, appWidgetId: Int, date: LocalDate): PendingIntent =
    PendingIntent.getBroadcast(
        context,
        widgetRequestCode("day-grid-open:$appWidgetId:$stableKey"),
        openFillInIntent(date).apply {
            action = KgsWidgetProvider.ACTION_COLLECTION_CLICK
            setClass(context, KgsWidgetActionReceiver::class.java)
            data = Uri.parse("kgs-calendar://widget-day-grid-open/$appWidgetId/$stableKey")
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

private fun WidgetDayItem.toggleBroadcastPendingIntent(context: Context, appWidgetId: Int): PendingIntent =
    PendingIntent.getBroadcast(
        context,
        widgetRequestCode("day-grid-toggle:$appWidgetId:$stableKey"),
        toggleFillInIntent().apply {
            action = KgsWidgetProvider.ACTION_COLLECTION_CLICK
            setClass(context, KgsWidgetActionReceiver::class.java)
            data = Uri.parse("kgs-calendar://widget-day-grid-toggle/$appWidgetId/$stableKey")
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

private fun WidgetDayGridRow.openDayFillInIntent(): Intent =
    Intent().apply {
        putExtra(EXTRA_COLLECTION_ACTION, COLLECTION_ACTION_OPEN)
        data = Uri.parse("kgs-calendar://widget-day-grid-day/${day.toEpochDay()}/${hour ?: "all-day"}")
    }

private fun WidgetDayGridRow.openDayBroadcastPendingIntent(context: Context, appWidgetId: Int): PendingIntent =
    PendingIntent.getBroadcast(
        context,
        widgetRequestCode("day-grid-day:$appWidgetId:${day.toEpochDay()}:${hour ?: "all-day"}"),
        openDayFillInIntent().apply {
            action = KgsWidgetProvider.ACTION_COLLECTION_CLICK
            setClass(context, KgsWidgetActionReceiver::class.java)
            data = Uri.parse("kgs-calendar://widget-day-grid-day/$appWidgetId/${day.toEpochDay()}/${hour ?: "all-day"}")
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

private fun allDayTogglePendingIntent(context: Context, appWidgetId: Int): PendingIntent =
    PendingIntent.getBroadcast(
        context,
        widgetRequestCode("day-all-day-toggle:$appWidgetId"),
        Intent(context, KgsWidgetKind.Day.providerClass).apply {
            action = KgsWidgetProvider.ACTION_DAY_TOGGLE_ALL_DAY
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            data = Uri.parse("kgs-calendar://widget-day/all-day-toggle/$appWidgetId")
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
