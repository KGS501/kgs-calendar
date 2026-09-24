package com.kgs.calendar.widget.render

import android.content.Context
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import com.kgs.calendar.R
import com.kgs.calendar.widget.KgsWidgetKind
import com.kgs.calendar.widget.WIDGET_COLLECTION_VIEW_TYPE_COUNT
import com.kgs.calendar.widget.bitmap.WidgetBitmapUriStore
import com.kgs.calendar.widget.model.WidgetCollectionRenderOptions
import com.kgs.calendar.widget.model.WidgetDayGridRow
import com.kgs.calendar.widget.model.WidgetListRow
import com.kgs.calendar.widget.theme.WidgetPalette

@RequiresApi(Build.VERSION_CODES.S)
internal fun RemoteViews.bindDirectCollectionItems(
    context: Context,
    images: WidgetBitmapUriStore,
    packageName: String,
    palette: WidgetPalette,
    rows: List<WidgetListRow>,
    sourceKind: KgsWidgetKind,
    appWidgetId: Int,
    renderOptions: WidgetCollectionRenderOptions = WidgetCollectionRenderOptions(),
) {
    val builder = RemoteViews.RemoteCollectionItems.Builder()
        .setHasStableIds(true)
        .setViewTypeCount(WIDGET_COLLECTION_VIEW_TYPE_COUNT)
    rows.forEach { row ->
        builder.addItem(row.stableId, row.toRemoteViews(context, images, packageName, palette, sourceKind, appWidgetId, renderOptions))
    }
    setRemoteAdapter(R.id.widget_list, builder.build())
}

@RequiresApi(Build.VERSION_CODES.S)
internal fun RemoteViews.bindDirectDayGridItems(
    context: Context,
    images: WidgetBitmapUriStore,
    packageName: String,
    palette: WidgetPalette,
    rows: List<WidgetDayGridRow>,
    widthDp: Float,
    appWidgetId: Int,
) {
    val builder = RemoteViews.RemoteCollectionItems.Builder()
        .setHasStableIds(true)
        .setViewTypeCount(2)
    rows.forEach { row ->
        builder.addItem(
            row.stableId,
            row.toRemoteViews(
                context = context,
                images = images,
                packageName = packageName,
                palette = palette,
                widthDp = widthDp,
                appWidgetId = appWidgetId,
            ),
        )
    }
    setRemoteAdapter(R.id.widget_list, builder.build())
}

internal fun RemoteViews.bindCollectionBottomFade(palette: WidgetPalette, visible: Boolean) {
    setInt(R.id.widget_bottom_fade, "setBackgroundResource", palette.bottomFadeRes)
    setViewVisibility(R.id.widget_bottom_fade, if (visible) View.VISIBLE else View.GONE)
}
