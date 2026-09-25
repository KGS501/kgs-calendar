package com.kgs.calendar.widget.render

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import com.kgs.calendar.MainActivity
import com.kgs.calendar.data.settings.WidgetTaskCreateMode
import com.kgs.calendar.widget.EXTRA_WIDGET_ACTION
import com.kgs.calendar.widget.EXTRA_WIDGET_DATE
import com.kgs.calendar.widget.EXTRA_WIDGET_KIND
import com.kgs.calendar.widget.EXTRA_WIDGET_TASK_CREATE_MODE
import com.kgs.calendar.widget.KgsTasksWidgetProvider
import com.kgs.calendar.widget.KgsWidgetActionReceiver
import com.kgs.calendar.widget.KgsWidgetCollectionService
import com.kgs.calendar.widget.KgsWidgetKind
import com.kgs.calendar.widget.KgsWidgetProvider
import com.kgs.calendar.widget.WIDGET_ACTION_CREATE_EVENT
import com.kgs.calendar.widget.WIDGET_ACTION_CREATE_TASK
import java.time.LocalDate

internal class WidgetPendingIntents(private val context: Context) {
    fun collectionAdapterIntent(kind: KgsWidgetKind, appWidgetId: Int): Intent =
        Intent(context, KgsWidgetCollectionService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            putExtra(EXTRA_WIDGET_KIND, kind.name)
            data = Uri.parse("kgs-calendar://widget-collection/${kind.name}/$appWidgetId")
        }

    fun collectionClickPendingIntent(kind: KgsWidgetKind, appWidgetId: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            50_000 + appWidgetId * 10 + kind.ordinal,
            Intent(context, KgsWidgetActionReceiver::class.java).apply {
                action = KgsWidgetProvider.ACTION_COLLECTION_CLICK
                data = Uri.parse("kgs-calendar://widget-collection-click/${kind.name}/$appWidgetId")
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )

    fun openMainAppPendingIntent(appWidgetId: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = Uri.parse("kgs-calendar://widget-open/$appWidgetId/${SystemClock.elapsedRealtime()}")
        }
        return PendingIntent.getActivity(
            context,
            60_000 + appWidgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun openAppPendingIntent(kind: KgsWidgetKind, requestCode: Int, date: LocalDate, clearTask: Boolean = false): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                (if (clearTask) Intent.FLAG_ACTIVITY_CLEAR_TASK else Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(EXTRA_WIDGET_KIND, kind.name)
            putExtra(EXTRA_WIDGET_DATE, date.toString())
            data = Uri.parse("kgs-calendar://widget/${kind.name}/$requestCode/$date")
        }
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun createEventPendingIntent(appWidgetId: Int, date: LocalDate): PendingIntent {
        val requestCode = 30_000 + appWidgetId
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_WIDGET_KIND, KgsWidgetKind.Day.name)
            putExtra(EXTRA_WIDGET_DATE, date.toString())
            putExtra(EXTRA_WIDGET_ACTION, WIDGET_ACTION_CREATE_EVENT)
            data = Uri.parse("kgs-calendar://widget-create-event/$appWidgetId/$date")
        }
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun createTaskPendingIntent(appWidgetId: Int, date: LocalDate, mode: WidgetTaskCreateMode): PendingIntent {
        val requestCode = 35_000 + appWidgetId
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_WIDGET_KIND, KgsWidgetKind.Tasks.name)
            putExtra(EXTRA_WIDGET_DATE, date.toString())
            putExtra(EXTRA_WIDGET_ACTION, WIDGET_ACTION_CREATE_TASK)
            putExtra(EXTRA_WIDGET_TASK_CREATE_MODE, mode.name)
            data = Uri.parse("kgs-calendar://widget-create-task/$appWidgetId/$date/${mode.name}")
        }
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun monthNavigationPendingIntent(
        appWidgetId: Int,
        previous: Boolean,
        targetKind: KgsWidgetKind = KgsWidgetKind.Month,
    ): PendingIntent {
        val intent = Intent(context, targetKind.providerClass).apply {
            action = if (previous) KgsWidgetProvider.ACTION_MONTH_PREVIOUS else KgsWidgetProvider.ACTION_MONTH_NEXT
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            data = Uri.parse("kgs-calendar://widget-month/${targetKind.name}/${if (previous) "previous" else "next"}/$appWidgetId")
        }
        return PendingIntent.getBroadcast(
            context,
            20_000 + appWidgetId * 10 + targetKind.ordinal * 2 + if (previous) 0 else 1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun monthTodayPendingIntent(
        appWidgetId: Int,
        targetKind: KgsWidgetKind = KgsWidgetKind.Month,
    ): PendingIntent {
        val intent = Intent(context, targetKind.providerClass).apply {
            action = KgsWidgetProvider.ACTION_MONTH_TODAY
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            data = Uri.parse("kgs-calendar://widget-month/${targetKind.name}/today/$appWidgetId")
        }
        return PendingIntent.getBroadcast(
            context,
            23_000 + appWidgetId * 10 + targetKind.ordinal,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun dayNavigationPendingIntent(appWidgetId: Int, previous: Boolean): PendingIntent {
        val intent = Intent(context, KgsWidgetKind.Day.providerClass).apply {
            action = if (previous) KgsWidgetProvider.ACTION_DAY_PREVIOUS else KgsWidgetProvider.ACTION_DAY_NEXT
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            data = Uri.parse("kgs-calendar://widget-day/${if (previous) "previous" else "next"}/$appWidgetId")
        }
        return PendingIntent.getBroadcast(
            context,
            24_000 + appWidgetId * 2 + if (previous) 0 else 1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun dayTodayPendingIntent(appWidgetId: Int): PendingIntent {
        val intent = Intent(context, KgsWidgetKind.Day.providerClass).apply {
            action = KgsWidgetProvider.ACTION_DAY_TODAY
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            data = Uri.parse("kgs-calendar://widget-day/today/$appWidgetId")
        }
        return PendingIntent.getBroadcast(
            context,
            27_000 + appWidgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun refreshPendingIntent(kind: KgsWidgetKind, appWidgetId: Int): PendingIntent {
        val intent = Intent(context, kind.providerClass).apply {
            action = KgsWidgetProvider.ACTION_REFRESH
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            data = Uri.parse("kgs-calendar://widget-refresh/${kind.name}/$appWidgetId")
        }
        return PendingIntent.getBroadcast(
            context,
            10_000 + appWidgetId + kind.ordinal,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun tasksSortPendingIntent(appWidgetId: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            60_000 + appWidgetId,
            Intent(context, KgsTasksWidgetProvider::class.java).apply {
                action = KgsWidgetProvider.ACTION_TASKS_SORT_NEXT
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse("kgs-calendar://widget-tasks/sort/$appWidgetId")
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}

internal fun dayPendingIntentRequestCode(appWidgetId: Int, day: LocalDate): Int =
    appWidgetId * 10_000 + day.year % 100 * 400 + day.dayOfYear
