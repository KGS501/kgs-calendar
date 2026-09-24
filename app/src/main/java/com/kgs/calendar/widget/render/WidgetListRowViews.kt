package com.kgs.calendar.widget.render

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
import com.kgs.calendar.R
import com.kgs.calendar.widget.COLLECTION_ACTION_OPEN
import com.kgs.calendar.widget.COLLECTION_ACTION_TOGGLE_SUBTASKS
import com.kgs.calendar.widget.COLLECTION_ACTION_TOGGLE_TASK
import com.kgs.calendar.widget.EXTRA_COLLECTION_ACTION
import com.kgs.calendar.widget.EXTRA_WIDGET_ACTION
import com.kgs.calendar.widget.EXTRA_WIDGET_DATE
import com.kgs.calendar.widget.EXTRA_WIDGET_EVENT_UID
import com.kgs.calendar.widget.EXTRA_WIDGET_KIND
import com.kgs.calendar.widget.EXTRA_WIDGET_TASK_UID
import com.kgs.calendar.widget.KgsWidgetKind
import com.kgs.calendar.widget.KgsWidgetProvider
import com.kgs.calendar.widget.TAG
import com.kgs.calendar.widget.WIDGET_ACTION_OPEN_EVENT
import com.kgs.calendar.widget.WIDGET_ACTION_OPEN_TASK
import com.kgs.calendar.widget.WIDGET_AGENDA_ART_BITMAP_SCALE
import com.kgs.calendar.widget.WIDGET_AGENDA_EVENT_ROW_HEIGHT_DP
import com.kgs.calendar.widget.WIDGET_AGENDA_SPAN_EVENT_ROW_HEIGHT_DP
import com.kgs.calendar.widget.WIDGET_COLLECTION_RENDER_SIGNATURE_VERSION
import com.kgs.calendar.widget.WIDGET_DAY_EVENT_ROW_HEIGHT_DP
import com.kgs.calendar.widget.WIDGET_TASK_CARD_RENDERER
import com.kgs.calendar.widget.WIDGET_TASK_PRIORITY_BITMAP_SCALE
import com.kgs.calendar.widget.WIDGET_TASK_PRIORITY_OVERDRAW_DP
import com.kgs.calendar.widget.WIDGET_TASK_ROW_HEIGHT_DP
import com.kgs.calendar.widget.WIDGET_TASK_TRANSITION_MIN_ROW_HEIGHT_DP
import com.kgs.calendar.widget.bitmap.WidgetBitmapUriStore
import com.kgs.calendar.widget.bitmap.agendaEventCardBitmap
import com.kgs.calendar.widget.bitmap.taskPriorityMotionBitmap
import com.kgs.calendar.widget.bitmap.taskRowBackgroundBitmap
import com.kgs.calendar.widget.bitmap.taskStatusIconBitmap
import com.kgs.calendar.widget.bitmap.taskSubtasksArrowBitmap
import com.kgs.calendar.widget.dpToPx
import com.kgs.calendar.widget.model.WidgetCollectionRenderOptions
import com.kgs.calendar.widget.model.WidgetListRow
import com.kgs.calendar.widget.model.WidgetListRowType
import com.kgs.calendar.widget.model.priorityMotionFrameIds
import com.kgs.calendar.widget.model.priorityMotionFrameIntervalMillis
import com.kgs.calendar.widget.model.usesAgendaCollectionStyle
import com.kgs.calendar.widget.theme.WidgetPalette
import com.kgs.calendar.widget.theme.blendWith
import com.kgs.calendar.widget.theme.isDarkColor
import com.kgs.calendar.widget.theme.withAlpha
import com.kgs.calendar.widget.update.WidgetPerformanceMonitor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

internal fun WidgetListRow.toRemoteViews(
    context: Context,
    images: WidgetBitmapUriStore,
    packageName: String,
    palette: WidgetPalette,
    sourceKind: KgsWidgetKind,
    appWidgetId: Int,
    renderOptions: WidgetCollectionRenderOptions = WidgetCollectionRenderOptions(),
): RemoteViews {
    if (type == WidgetListRowType.Empty) {
        val views = RemoteViews(packageName, R.layout.widget_tasks_empty_row)
        views.setTextViewText(R.id.widget_tasks_empty_text, title)
        views.setTextColor(R.id.widget_tasks_empty_text, palette.muted)
        return views
    }
    if (type == WidgetListRowType.Section) {
        val views = RemoteViews(packageName, R.layout.widget_section_title)
        views.setTextViewText(R.id.widget_section_title_text, title)
        views.setTextColor(R.id.widget_section_title_text, if (title.startsWith("\u25CF")) palette.accent else palette.muted)
        if (sourceKind.usesAgendaCollectionStyle()) {
            views.setViewPadding(R.id.widget_section_title_text, context.dpToPx(62), 0, 0, 0)
            views.setTextViewTextSize(R.id.widget_section_title_text, TypedValue.COMPLEX_UNIT_SP, if (sourceKind == KgsWidgetKind.Day) 12f else 15f)
        }
        return views
    }
    if (type == WidgetListRowType.Now) {
        val views = RemoteViews(packageName, R.layout.widget_day_now_row)
        views.setTextViewText(R.id.widget_day_now_time, title)
        views.setTextColor(R.id.widget_day_now_time, palette.onAccent)
        views.setInt(R.id.widget_day_now_time, "setBackgroundResource", palette.sortBackgroundRes)
        views.setInt(R.id.widget_day_now_line, "setBackgroundColor", palette.text.withAlpha(if (palette.rootBackgroundColor.isDarkColor()) 0.62f else 0.52f))
        return views
    }
    if (type == WidgetListRowType.Item) {
        if (sourceKind.usesAgendaCollectionStyle()) {
            return toAgendaEventRemoteViews(context, images, packageName, palette, appWidgetId, renderOptions)
        }
        val views = RemoteViews(packageName, R.layout.widget_list_item)
        views.setInt(R.id.widget_item_root, "setBackgroundResource", palette.itemBackgroundRes)
        views.setInt(R.id.widget_item_accent, "setBackgroundColor", color)
        views.setTextViewText(R.id.widget_item_title, title)
        views.setTextColor(R.id.widget_item_title, if (completed) palette.muted else palette.text)
        views.setTextViewText(R.id.widget_item_meta, meta)
        views.setTextColor(R.id.widget_item_meta, palette.muted)
        views.setOnClickFillInIntent(R.id.widget_item_root, openFillInIntent())
        return views
    }

    val agendaRow = sourceKind.usesAgendaCollectionStyle()
    val views = RemoteViews(packageName, if (agendaRow) R.layout.widget_agenda_task_item else R.layout.widget_task_item)
    if (agendaRow) {
        if (sourceKind == KgsWidgetKind.Day) {
            views.bindDayTimeColumn(palette, meta)
        } else {
            val rowLocale = context.resources.configuration.locales[0] ?: Locale.getDefault()
            views.bindAgendaDate(date, palette, muted = completed, visible = showAgendaDate, locale = rowLocale)
        }
        views.setOnClickFillInIntent(R.id.widget_agenda_task_row, openFillInIntent(openTask = true))
    }
    val taskArtWidthDp = renderOptions.taskArtWidthDp.coerceAtLeast(1f)
    val cardMeta = if (launchKind == KgsWidgetKind.Day) location.orEmpty() else meta
    val baseSpec = WIDGET_TASK_CARD_RENDERER.baseSpec(
        kind = launchKind,
        priority = priority,
        widthDp = taskArtWidthDp,
        depth = depth,
        childCount = childCount,
        hasMeta = cardMeta.isNotBlank(),
        completed = completed,
    )
    val rowRenderOptions = renderOptions.taskRows[stableId]
    val rowProgress = rowRenderOptions?.rowProgress?.coerceIn(0f, 1f) ?: 1f
    val rowEasedProgress = rowProgress
    val subtaskExpansionProgress = rowRenderOptions?.subtaskExpansionProgress?.coerceIn(0f, 1f)
        ?: if (subtasksExpanded) 1f else 0f
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val rowHeight = max(WIDGET_TASK_TRANSITION_MIN_ROW_HEIGHT_DP, baseSpec.rowHeightDp * rowEasedProgress)
        val cardHeight = max(WIDGET_TASK_TRANSITION_MIN_ROW_HEIGHT_DP, baseSpec.cardHeightDp * rowEasedProgress)
        views.setViewLayoutHeight(R.id.widget_task_root, rowHeight, TypedValue.COMPLEX_UNIT_DIP)
        views.setViewLayoutHeight(R.id.widget_task_background_art, rowHeight, TypedValue.COMPLEX_UNIT_DIP)
        views.setViewLayoutHeight(R.id.widget_task_priority_motion, rowHeight, TypedValue.COMPLEX_UNIT_DIP)
        views.setViewLayoutHeight(R.id.widget_task_content, cardHeight, TypedValue.COMPLEX_UNIT_DIP)
    }
    views.setFloat(R.id.widget_task_root, "setAlpha", rowEasedProgress)
    val contentStart = context.dpToPx(baseSpec.contentStartDp).roundToInt()
    val cardColor = if (completed) color.blendWith(palette.rootBackgroundColor, 0.48f) else color
    val contentColor = if (cardColor.isDarkColor()) 0xFFFFFFFF.toInt() else 0xFF1C1A18.toInt()
    val secondaryColor = contentColor.withAlpha(if (completed) 0.52f else 0.74f)
    val priorityIntensity = if (!completed && priorityMotionEnabled && !renderOptions.suppressPriorityMotion) {
        WIDGET_TASK_CARD_RENDERER.priorityIntensity(priority)
    } else {
        0f
    }
    if (priorityIntensity > 0f) {
        val priorityFrameIds = sourceKind.priorityMotionFrameIds()
        val priorityFrameCount = priorityFrameIds.size
        val priorityFrameIntervalMillis = priorityMotionFrameIntervalMillis(priority, priorityIntensity, priorityFrameCount)
        priorityFrameIds.forEachIndexed { frame, viewId ->
            views.setWidgetRowImage(
                context = context,
                images = images,
                appWidgetId = appWidgetId,
                viewId = viewId,
                cacheKey = taskPriorityMotionCacheKey(
                    palette = palette,
                    taskArtWidthDp = taskArtWidthDp,
                    cardColor = cardColor,
                    contentColor = contentColor,
                    secondaryColor = secondaryColor,
                    priority = priority,
                    intensity = priorityIntensity,
                    frame = frame,
                    frameCount = priorityFrameCount,
                    subtaskExpansionProgress = subtaskExpansionProgress,
                ),
                bitmapProvider = { taskPriorityMotionBitmap(
                    context = context,
                    palette = palette,
                    taskArtWidthDp = taskArtWidthDp,
                    cardColor = cardColor,
                    contentColor = contentColor,
                    secondaryColor = secondaryColor,
                    priority = priority,
                    frame = frame,
                    frameCount = priorityFrameCount,
                    subtaskExpansionProgress = subtaskExpansionProgress,
                    baseSpec = baseSpec,
                    cardMeta = cardMeta,
                ) },
            )
        }
        views.setInt(
            R.id.widget_task_priority_motion,
            "setFlipInterval",
            priorityFrameIntervalMillis,
        )
        if (agendaRow && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val motionHeight = max(
                WIDGET_TASK_TRANSITION_MIN_ROW_HEIGHT_DP,
                WIDGET_TASK_ROW_HEIGHT_DP * rowEasedProgress + WIDGET_TASK_PRIORITY_OVERDRAW_DP * 2f,
            )
            views.setViewLayoutHeight(R.id.widget_task_priority_motion, motionHeight, TypedValue.COMPLEX_UNIT_DIP)
        }
        views.setViewVisibility(R.id.widget_task_background_art, View.GONE)
    } else {
        views.setImageViewBitmap(
            R.id.widget_task_background_art,
            taskRowBackgroundBitmap(
                context = context,
                palette = palette,
                taskArtWidthDp = taskArtWidthDp,
                cardColor = cardColor,
                contentColor = contentColor.withAlpha(if (completed) 0.62f else 1f),
                secondaryColor = secondaryColor,
                subtaskExpansionProgress = subtaskExpansionProgress,
                lightweight = renderOptions.lightweightTaskTransition,
                baseSpec = baseSpec,
                cardMeta = cardMeta,
            ),
        )
        views.setViewVisibility(R.id.widget_task_background_art, View.VISIBLE)
    }
    views.setViewVisibility(
        R.id.widget_task_priority_motion,
        if (priorityIntensity > 0f) View.VISIBLE else View.GONE,
    )
    val contentEndPadding = (taskArtWidthDp - baseSpec.textEndDp).coerceAtLeast(0f)
    views.setViewPadding(R.id.widget_task_content, contentStart, 0, context.dpToPx(contentEndPadding).roundToInt(), 0)
    val overlayContentColor = 0x00000000
    views.setTextViewTextSize(R.id.widget_task_title, TypedValue.COMPLEX_UNIT_SP, baseSpec.titleTextSizeSp)
    views.setTextViewTextSize(R.id.widget_task_meta, TypedValue.COMPLEX_UNIT_SP, baseSpec.metaTextSizeSp)
    views.setImageViewBitmap(
        R.id.widget_task_status,
        taskStatusIconBitmap(context, statusGlyph, overlayContentColor),
    )
    views.setTextViewText(R.id.widget_task_title, title)
    views.setTextColor(R.id.widget_task_title, overlayContentColor)
    views.setTextViewText(R.id.widget_task_meta, cardMeta)
    views.setTextColor(R.id.widget_task_meta, overlayContentColor)
    if (childCount > 0) {
        views.setImageViewBitmap(R.id.widget_task_subtasks, taskSubtasksArrowBitmap(context, overlayContentColor, subtaskExpansionProgress))
        views.setOnClickFillInIntent(R.id.widget_task_subtasks, toggleSubtasksFillInIntent(appWidgetId))
    }
    views.setViewVisibility(R.id.widget_task_subtasks, if (childCount > 0) View.VISIBLE else View.GONE)
    views.setOnClickFillInIntent(R.id.widget_task_root, openFillInIntent(openTask = true))
    views.setOnClickFillInIntent(R.id.widget_task_background_art, openFillInIntent(openTask = true))
    views.setOnClickFillInIntent(R.id.widget_task_content, openFillInIntent(openTask = true))
    views.setOnClickFillInIntent(R.id.widget_task_title, openFillInIntent(openTask = true))
    views.setOnClickFillInIntent(R.id.widget_task_status, toggleFillInIntent())
    return views
}

private fun RemoteViews.setWidgetRowImage(
    context: Context,
    images: WidgetBitmapUriStore,
    appWidgetId: Int,
    viewId: Int,
    cacheKey: String,
    bitmapProvider: () -> Bitmap,
) {
    if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        val bitmap = bitmapProvider()
        WidgetPerformanceMonitor.current()?.recordBitmapRendered()
        setImageViewBitmap(viewId, bitmap)
        return
    }
    val cachedUri = runCatching {
        images.getIfPresent(appWidgetId, cacheKey)
    }.onFailure { error ->
        Log.w(TAG, "Failed to read cached widget row image", error)
    }.getOrNull()
    if (cachedUri != null) {
        setImageViewUri(viewId, cachedUri)
        return
    }
    val bitmap = bitmapProvider()
    WidgetPerformanceMonitor.current()?.recordBitmapRendered()
    val uri = runCatching {
        images.put(appWidgetId, cacheKey, bitmap)
    }.onFailure { error ->
        Log.w(TAG, "Failed to cache widget row image; falling back to an inline bitmap", error)
    }.getOrNull()
    if (uri != null) {
        setImageViewUri(viewId, uri)
        bitmap.recycle()
    } else {
        setImageViewBitmap(viewId, bitmap)
    }
}

private fun WidgetListRow.taskPriorityMotionCacheKey(
    palette: WidgetPalette,
    taskArtWidthDp: Float,
    cardColor: Int,
    contentColor: Int,
    secondaryColor: Int,
    priority: Int?,
    intensity: Float,
    frame: Int,
    frameCount: Int,
    subtaskExpansionProgress: Float,
): String =
    "task-priority|$launchKind|$stableId|$title|$meta|${location.orEmpty()}|$statusGlyph|$completed|$depth|$childCount|${continuationLevels.sorted().joinToString(",")}|$lastSibling|$subtasksExpanded|$palette|$taskArtWidthDp|$cardColor|$contentColor|$secondaryColor|$priority|$intensity|$frame|$frameCount|$subtaskExpansionProgress|$WIDGET_TASK_PRIORITY_BITMAP_SCALE|$WIDGET_TASK_PRIORITY_OVERDRAW_DP|$WIDGET_COLLECTION_RENDER_SIGNATURE_VERSION"

private fun WidgetListRow.toAgendaEventRemoteViews(
    context: Context,
    images: WidgetBitmapUriStore,
    packageName: String,
    palette: WidgetPalette,
    appWidgetId: Int,
    renderOptions: WidgetCollectionRenderOptions,
): RemoteViews {
    val views = RemoteViews(packageName, R.layout.widget_agenda_event_item)
    val dayRow = sourceKindIsDay()
    val rowHeightDp = when {
        dayRow -> WIDGET_DAY_EVENT_ROW_HEIGHT_DP
        spanEndDate != null -> WIDGET_AGENDA_SPAN_EVENT_ROW_HEIGHT_DP
        else -> WIDGET_AGENDA_EVENT_ROW_HEIGHT_DP
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        views.setViewLayoutHeight(R.id.widget_agenda_event_root, rowHeightDp.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
        views.setViewLayoutHeight(R.id.widget_agenda_date_column, rowHeightDp.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
        views.setViewLayoutHeight(R.id.widget_agenda_event_art, rowHeightDp.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
    }
    val muted = !sourceKindIsDay() && endMillis > 0L && endMillis < System.currentTimeMillis()
    val rowLocale = context.resources.configuration.locales[0] ?: Locale.getDefault()
    if (dayRow) {
        views.bindDayTimeColumn(palette, meta)
    } else {
        views.bindAgendaDate(date, palette, muted, visible = showAgendaDate, locale = rowLocale, endDate = spanEndDate)
    }
    val cardWidthDp = renderOptions.taskArtWidthDp
    views.setWidgetRowImage(
        context = context,
        images = images,
        appWidgetId = appWidgetId,
        viewId = R.id.widget_agenda_event_art,
        cacheKey = agendaEventCardCacheKey(
            palette = palette,
            cardWidthDp = cardWidthDp,
            rowHeightDp = rowHeightDp,
            dayCard = dayRow,
            muted = muted,
        ),
        bitmapProvider = { agendaEventCardBitmap(
            context = context,
            palette = palette,
            cardWidthDp = cardWidthDp,
            rowHeightDp = rowHeightDp,
            dayCard = dayRow,
            muted = muted,
        ) },
    )
    views.setOnClickFillInIntent(R.id.widget_agenda_event_root, openFillInIntent(openTask = false))
    views.setOnClickFillInIntent(R.id.widget_agenda_event_art, openFillInIntent(openTask = false))
    return views
}

private fun WidgetListRow.sourceKindIsDay(): Boolean =
    launchKind == KgsWidgetKind.Day

private fun WidgetListRow.agendaEventCardCacheKey(
    palette: WidgetPalette,
    cardWidthDp: Float,
    rowHeightDp: Int,
    dayCard: Boolean,
    muted: Boolean,
): String =
    "agenda-event|$launchKind|$stableId|$title|$meta|${location.orEmpty()}|$eventStatus|$color|$completed|$endMillis|${spanEndDate?.toEpochDay() ?: Long.MIN_VALUE}|$palette|$cardWidthDp|$rowHeightDp|$dayCard|$muted|$WIDGET_AGENDA_ART_BITMAP_SCALE|$WIDGET_COLLECTION_RENDER_SIGNATURE_VERSION"

private fun RemoteViews.bindAgendaDate(
    date: LocalDate,
    palette: WidgetPalette,
    muted: Boolean,
    visible: Boolean,
    locale: Locale,
    endDate: LocalDate? = null,
) {
    if (!visible) {
        setTextViewText(R.id.widget_agenda_date_month, "")
        setTextViewText(R.id.widget_agenda_date_day, "")
        setViewVisibility(R.id.widget_agenda_date_month, View.INVISIBLE)
        setViewVisibility(R.id.widget_agenda_date_day, View.INVISIBLE)
        setViewVisibility(R.id.widget_agenda_date_line, View.GONE)
        setViewVisibility(R.id.widget_agenda_end_month, View.GONE)
        setViewVisibility(R.id.widget_agenda_end_day, View.GONE)
        return
    }
    val month = date.format(DateTimeFormatter.ofPattern("MMM", locale)).replace(".", "")
    val color = palette.text.withAlpha(if (muted) 0.58f else 1f)
    setViewVisibility(R.id.widget_agenda_date_month, View.VISIBLE)
    setViewVisibility(R.id.widget_agenda_date_day, View.VISIBLE)
    setViewVisibility(R.id.widget_agenda_date_line, if (endDate != null) View.VISIBLE else View.GONE)
    setViewVisibility(R.id.widget_agenda_end_month, if (endDate != null) View.VISIBLE else View.GONE)
    setViewVisibility(R.id.widget_agenda_end_day, if (endDate != null) View.VISIBLE else View.GONE)
    setTextViewText(R.id.widget_agenda_date_month, month)
    setTextViewText(R.id.widget_agenda_date_day, date.dayOfMonth.toString())
    setTextColor(R.id.widget_agenda_date_month, color)
    setTextColor(R.id.widget_agenda_date_day, color)
    setTextViewTextSize(R.id.widget_agenda_date_month, TypedValue.COMPLEX_UNIT_SP, 11f)
    setTextViewTextSize(R.id.widget_agenda_date_day, TypedValue.COMPLEX_UNIT_SP, 20f)
    if (endDate != null) {
        val endMonth = endDate.format(DateTimeFormatter.ofPattern("MMM", locale)).replace(".", "")
        val lineColor = palette.text.withAlpha(if (muted) 0.22f else 0.42f)
        setInt(R.id.widget_agenda_date_line, "setBackgroundColor", lineColor)
        setTextViewText(R.id.widget_agenda_end_month, endMonth)
        setTextViewText(R.id.widget_agenda_end_day, endDate.dayOfMonth.toString())
        setTextColor(R.id.widget_agenda_end_month, color)
        setTextColor(R.id.widget_agenda_end_day, color)
        setTextViewTextSize(R.id.widget_agenda_end_month, TypedValue.COMPLEX_UNIT_SP, 10f)
        setTextViewTextSize(R.id.widget_agenda_end_day, TypedValue.COMPLEX_UNIT_SP, 17f)
    }
}

private fun RemoteViews.bindDayTimeColumn(palette: WidgetPalette, timeText: String) {
    val rawPrimary = timeText.substringBefore('-').trim().ifBlank { timeText }
    val secondary = timeText.substringAfter('-', "").trim()
    val primary = if (secondary.isBlank() && rawPrimary.length > 7) {
        "${rawPrimary.take(5)}."
    } else {
        rawPrimary
    }
    val compactPrimary = secondary.isBlank() && rawPrimary.length > 6
    setViewVisibility(R.id.widget_agenda_date_month, View.VISIBLE)
    setViewVisibility(R.id.widget_agenda_date_day, View.VISIBLE)
    setViewVisibility(R.id.widget_agenda_date_line, View.GONE)
    setViewVisibility(R.id.widget_agenda_end_month, View.GONE)
    setViewVisibility(R.id.widget_agenda_end_day, View.GONE)
    setTextViewText(R.id.widget_agenda_date_month, primary)
    setTextViewText(R.id.widget_agenda_date_day, secondary)
    setTextColor(R.id.widget_agenda_date_month, palette.muted)
    setTextColor(R.id.widget_agenda_date_day, palette.muted.withAlpha(0.72f))
    setTextViewTextSize(R.id.widget_agenda_date_month, TypedValue.COMPLEX_UNIT_SP, if (compactPrimary) 8.8f else 10f)
    setTextViewTextSize(R.id.widget_agenda_date_day, TypedValue.COMPLEX_UNIT_SP, 10f)
}

private fun WidgetListRow.openFillInIntent(openTask: Boolean = taskResourceHref != null): Intent {
    val intent = Intent()
    intent.putExtra(EXTRA_COLLECTION_ACTION, COLLECTION_ACTION_OPEN)
    intent.putExtra(EXTRA_WIDGET_KIND, if (openTask) KgsWidgetKind.Tasks.name else launchKind.name)
    intent.putExtra(EXTRA_WIDGET_DATE, date.toString())
    if (openTask && !taskResourceHref.isNullOrBlank()) {
        intent.putExtra(EXTRA_WIDGET_ACTION, WIDGET_ACTION_OPEN_TASK)
        intent.putExtra(EXTRA_WIDGET_TASK_UID, taskResourceHref)
    } else if (!eventResourceHref.isNullOrBlank()) {
        intent.putExtra(EXTRA_WIDGET_ACTION, WIDGET_ACTION_OPEN_EVENT)
        intent.putExtra(EXTRA_WIDGET_EVENT_UID, eventResourceHref)
    }
    intent.data = Uri.parse("kgs-calendar://widget-row-open/$stableId")
    return intent
}

private fun WidgetListRow.toggleFillInIntent(): Intent {
    val intent = Intent()
    intent.putExtra(EXTRA_COLLECTION_ACTION, COLLECTION_ACTION_TOGGLE_TASK)
    intent.putExtra(KgsWidgetProvider.EXTRA_TASK_RESOURCE_HREF, taskResourceHref)
    intent.data = Uri.parse("kgs-calendar://widget-row-toggle/$stableId")
    return intent
}

private fun WidgetListRow.toggleSubtasksFillInIntent(appWidgetId: Int): Intent {
    val intent = Intent()
    intent.putExtra(EXTRA_COLLECTION_ACTION, COLLECTION_ACTION_TOGGLE_SUBTASKS)
    intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
    intent.putExtra(KgsWidgetProvider.EXTRA_TASK_RESOURCE_HREF, taskResourceHref)
    intent.data = Uri.parse("kgs-calendar://widget-row-toggle-subtasks/$stableId")
    return intent
}
