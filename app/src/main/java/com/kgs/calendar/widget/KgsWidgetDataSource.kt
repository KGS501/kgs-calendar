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

internal class KgsWidgetDataSource(
    private val context: Context,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    private fun textContext(settings: WidgetRenderSettings): Context =
        context.withWidgetLocale(settings.locale)

    private fun textContext(locale: Locale): Context =
        context.withWidgetLocale(locale)

    suspend fun loadSettings(kind: KgsWidgetKind): WidgetRenderSettings = coroutineScope {
        val graph = KgsCalendarApplication.graph(context)
        val appThemeMode = async { graph.settingsStore.themeMode.first() }
        val appColorMode = async { graph.settingsStore.colorMode.first() }
        val widgetThemeMode = async {
            when (kind) {
                KgsWidgetKind.Agenda -> graph.settingsStore.agendaWidgetThemeMode.first()
                KgsWidgetKind.Month -> graph.settingsStore.monthWidgetThemeMode.first()
                KgsWidgetKind.Tasks -> graph.settingsStore.tasksWidgetThemeMode.first()
                KgsWidgetKind.Multi -> graph.settingsStore.multiWidgetThemeMode.first()
                KgsWidgetKind.Day -> graph.settingsStore.dayWidgetThemeMode.first()
            }
        }
        val widgetColorMode = async {
            when (kind) {
                KgsWidgetKind.Agenda -> graph.settingsStore.agendaWidgetColorMode.first()
                KgsWidgetKind.Month -> graph.settingsStore.monthWidgetColorMode.first()
                KgsWidgetKind.Tasks -> graph.settingsStore.tasksWidgetColorMode.first()
                KgsWidgetKind.Multi -> graph.settingsStore.multiWidgetColorMode.first()
                KgsWidgetKind.Day -> graph.settingsStore.dayWidgetColorMode.first()
            }
        }
        val languageMode = async { graph.settingsStore.languageMode.first() }
        val firstDayOfWeek = async { graph.settingsStore.firstDayOfWeek.first() }
        val hiddenCollectionHrefs = async { graph.settingsStore.hiddenCollectionHrefs.first() }
        val showCompletedTasks = async { graph.settingsStore.showCompletedTasksInCalendar.first() }
        val taskColorMode = async { graph.settingsStore.taskColorMode.first() }
        val priorityAnimationsEnabled = async { graph.settingsStore.priorityAnimationsEnabled.first() }
        val subtasksExpandedByDefault = async { graph.settingsStore.subtasksExpandedByDefault.first() }
        val tasksWidgetDisplayMode = async { graph.settingsStore.tasksWidgetDisplayMode.first() }
        val tasksWidgetIncludeOverdue = async { graph.settingsStore.tasksWidgetIncludeOverdue.first() }
        val tasksWidgetSortMode = async { graph.settingsStore.tasksWidgetSortMode.first() }
        val tasksWidgetCreateMode = async { graph.settingsStore.tasksWidgetCreateMode.first() }
        val tasksWidgetSubtaskDefaultMode = async { graph.settingsStore.tasksWidgetSubtaskDefaultMode.first() }
        val maxVisibleAllDayItems = async { graph.settingsStore.maxVisibleAllDayItems.first() }
        val dayWidgetScalePercent = async { graph.settingsStore.dayWidgetScalePercent.first() }
        val dayWidgetStartHour = async { graph.settingsStore.dayWidgetStartHour.first() }
        val dayWidgetStartAtCurrentHour = async { graph.settingsStore.dayWidgetStartAtCurrentHour.first() }
        val multiWidgetMonthPercent = async { graph.settingsStore.multiWidgetMonthPercent.first() }
        val systemNightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        WidgetRenderSettings(
            locale = languageMode.await().toLocale(context),
            firstDayOfWeek = firstDayOfWeek.await(),
            hiddenCollectionHrefs = hiddenCollectionHrefs.await(),
            showCompletedTasks = showCompletedTasks.await(),
            themeMode = widgetThemeMode.await().resolve(appThemeMode.await()),
            colorMode = widgetColorMode.await().resolve(appColorMode.await()),
            systemNightMode = systemNightMode,
            taskColorMode = taskColorMode.await(),
            priorityAnimationsEnabled = priorityAnimationsEnabled.await(),
            subtasksExpandedByDefault = subtasksExpandedByDefault.await(),
            tasksWidgetDisplayMode = tasksWidgetDisplayMode.await(),
            tasksWidgetIncludeOverdue = tasksWidgetIncludeOverdue.await(),
            tasksWidgetSortMode = tasksWidgetSortMode.await(),
            tasksWidgetCreateMode = tasksWidgetCreateMode.await(),
            tasksWidgetSubtaskDefaultMode = tasksWidgetSubtaskDefaultMode.await(),
            maxVisibleAllDayItems = maxVisibleAllDayItems.await(),
            dayWidgetScalePercent = dayWidgetScalePercent.await(),
            dayWidgetStartHour = dayWidgetStartHour.await(),
            dayWidgetStartAtCurrentHour = dayWidgetStartAtCurrentHour.await(),
            multiWidgetMonthPercent = multiWidgetMonthPercent.await(),
        )
    }

    suspend fun dayTimeline(day: LocalDate, settings: WidgetRenderSettings): WidgetDayTimeline {
        val graph = KgsCalendarApplication.graph(context)
        val labels = textContext(settings)
        val start = day.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val end = day.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val events = graph.repository.eventsSnapshot(start, end)
            .filterNot { it.collectionHref in settings.hiddenCollectionHrefs }
            .filterNot { it.status.equals("CANCELLED", ignoreCase = true) }
        val tasks = graph.repository.datedTasksSnapshot(start, end)
            .filterNot { it.collectionHref in settings.hiddenCollectionHrefs }
            .filterNot { it.status.equals("CANCELLED", ignoreCase = true) }
            .filter { settings.showCompletedTasks || !it.isCompleted }
        val allDayItems = buildList {
            events
                .filter { it.isWidgetAllDayTopItemOn(day, zoneId) }
                .sortedWith(compareBy<EventEntity> { it.startsAtMillis }.thenBy { it.title.lowercase(settings.locale) })
                .forEach { event ->
                    add(
                        WidgetDayItem(
                            title = event.title.ifBlank { labels.getString(R.string.no_title) },
                            meta = labels.getString(R.string.all_day),
                            color = event.displayColor(),
                            completed = false,
                            isTask = false,
                            stableKey = "event:${event.resourceHref}",
                            location = event.location,
                            eventStatus = event.status,
                            eventResourceHref = event.resourceHref,
                        ),
                    )
                }
            tasks
                .filter { it.isWidgetFullDayTaskOn(day, zoneId) }
                .sortedWith(compareBy<TaskEntity> { it.startAtMillis ?: it.dueAtMillis ?: Long.MAX_VALUE }.thenBy { it.title.lowercase(settings.locale) })
                .forEach { task ->
                    add(
                        WidgetDayItem(
                            title = task.title.ifBlank { labels.getString(R.string.no_title) },
                            meta = labels.getString(R.string.task),
                            color = task.displayColor(settings.taskColorMode),
                            completed = task.isCompleted,
                            isTask = true,
                            stableKey = "task:${task.resourceHref}",
                            location = task.location,
                            taskResourceHref = task.resourceHref,
                            statusGlyph = task.widgetStatusGlyph(),
                            priority = task.priority,
                        ),
                    )
                }
        }
        val timedItems = buildList {
            events.forEach { event ->
                event.widgetTimedPlacementOn(day, zoneId)?.let { placement ->
                    val item = WidgetDayItem(
                        title = event.title.ifBlank { labels.getString(R.string.no_title) },
                        meta = placement.timeRangeText(),
                        color = event.displayColor(),
                        completed = false,
                        isTask = false,
                        stableKey = "event:${event.resourceHref}",
                        location = event.location,
                        eventStatus = event.status,
                        eventResourceHref = event.resourceHref,
                    )
                    add(WidgetDayTimedItem(item, placement.first, placement.second))
                }
            }
            tasks.forEach { task ->
                task.widgetTimedPlacementOn(day, zoneId)?.let { placement ->
                    val item = WidgetDayItem(
                        title = task.title.ifBlank { labels.getString(R.string.no_title) },
                        meta = placement.timeRangeText(),
                        color = task.displayColor(settings.taskColorMode),
                        completed = task.isCompleted,
                        isTask = true,
                        stableKey = "task:${task.resourceHref}",
                        location = task.location,
                        taskResourceHref = task.resourceHref,
                        statusGlyph = task.widgetStatusGlyph(),
                        priority = task.priority,
                    )
                    add(WidgetDayTimedItem(item, placement.first, placement.second))
                }
            }
        }
        val timedLayouts = layoutWidgetDayTimedItems(timedItems)
        return WidgetDayTimeline(
            day = day,
            allDayItems = allDayItems,
            timedItems = timedLayouts,
            signature = buildString {
                append(WIDGET_DAY_RENDER_SIGNATURE_VERSION)
                append('|').append(day.toEpochDay())
                append('|').append(settings.locale.toLanguageTag())
                append('|').append(settings.hiddenCollectionHrefs.sorted().joinToString(","))
                append('|').append(settings.showCompletedTasks)
                append('|').append(settings.themeMode.name)
                append('|').append(settings.colorMode.name)
                append('|').append(settings.systemNightMode)
                append('|').append(settings.taskColorMode.name)
                allDayItems.forEach { item ->
                    append("\na|").append(item.stableKey).append('|').append(item.title).append('|').append(item.color).append('|').append(item.completed)
                        .append('|').append(item.location.orEmpty()).append('|').append(item.eventStatus.orEmpty())
                }
                timedLayouts.forEach { item ->
                    append("\nt|").append(item.item.stableKey).append('|').append(item.startMinute).append('|').append(item.endMinute)
                        .append('|').append(item.item.title).append('|').append(item.item.color).append('|').append(item.item.completed)
                        .append('|').append(item.item.location.orEmpty()).append('|').append(item.item.eventStatus.orEmpty())
                }
            },
        )
    }

    suspend fun listRows(kind: KgsWidgetKind, settings: WidgetRenderSettings, appWidgetId: Int): List<WidgetListRow> = when (kind) {
        KgsWidgetKind.Agenda -> loadAgendaItems(days = 45, settings = settings)
            .filter { it.sortMillis >= todayStartMillis() }
            .sortedWith(compareBy<WidgetListRow> { it.sortMillis }.thenBy { it.title.lowercase(settings.locale) })
            .take(WIDGET_AGENDA_MAX_ROWS)
            .withAgendaSections()

        KgsWidgetKind.Day -> loadDayItems(LocalDate.now(zoneId), settings).take(80)

        KgsWidgetKind.Multi -> loadAgendaItems(days = 45, settings = settings)
            .filter { it.sortMillis >= todayStartMillis() }
            .sortedWith(compareBy<WidgetListRow> { it.sortMillis }.thenBy { it.title.lowercase(settings.locale) })
            .take(WIDGET_AGENDA_MAX_ROWS)
            .withAgendaSections()

        KgsWidgetKind.Tasks -> loadTaskItems(settings, appWidgetId)
            .take(150)
            .ifEmpty { listOf(WidgetListRow.empty(kind.emptyText(textContext(settings)))) }
        KgsWidgetKind.Month -> emptyList()
    }

    suspend fun collectionSignature(
        kind: KgsWidgetKind,
        settings: WidgetRenderSettings,
        appWidgetId: Int,
        rows: List<WidgetListRow>? = null,
    ): String =
        buildString {
            append(kind.name)
            append('|').append(LocalDate.now(zoneId).toEpochDay())
            append('|').append(settings.locale.toLanguageTag())
            append('|').append(settings.hiddenCollectionHrefs.sorted().joinToString(","))
            append('|').append(settings.showCompletedTasks)
            append('|').append(settings.themeMode.name)
            append('|').append(settings.colorMode.name)
            append('|').append(settings.systemNightMode)
            append('|').append(settings.taskColorMode.name)
            append('|').append(settings.priorityAnimationsEnabled)
            append('|').append(settings.subtasksExpandedByDefault)
            append('|').append(settings.tasksWidgetDisplayMode.name)
            append('|').append(settings.tasksWidgetIncludeOverdue)
            append('|').append(settings.tasksWidgetSortMode.name)
            append('|').append(settings.tasksWidgetCreateMode.name)
            append('|').append(settings.tasksWidgetSubtaskDefaultMode.name)
            append('|').append(settings.dayWidgetScalePercent)
            append('|').append(settings.dayWidgetStartHour)
            append('|').append(settings.dayWidgetStartAtCurrentHour)
            append('|').append(settings.multiWidgetMonthPercent)
            if (kind == KgsWidgetKind.Day && settings.dayWidgetStartAtCurrentHour) {
                append('|').append(LocalTime.now(zoneId).hour)
            }
            (rows ?: listRows(kind, settings, appWidgetId)).forEach { row ->
                append('\n')
                row.appendSignatureTo(this)
            }
        }

    private suspend fun loadAgendaItems(days: Long, settings: WidgetRenderSettings): List<WidgetListRow> {
        val today = LocalDate.now(zoneId)
        val start = today.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val end = today.plusDays(days).atStartOfDay(zoneId).toInstant().toEpochMilli()
        return loadCalendarItems(start, end, settings, KgsWidgetKind.Agenda)
    }

    private suspend fun loadDayItems(day: LocalDate, settings: WidgetRenderSettings): List<WidgetListRow> {
        val start = day.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val end = day.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        return loadCalendarItems(start, end, settings, KgsWidgetKind.Day)
            .sortedWith(compareBy<WidgetListRow> { it.allDaySort }.thenBy { it.sortMillis }.thenBy { it.title.lowercase(settings.locale) })
    }

    private suspend fun loadCalendarItems(
        startMillis: Long,
        endMillis: Long,
        settings: WidgetRenderSettings,
        launchKind: KgsWidgetKind,
    ): List<WidgetListRow> {
        val graph = KgsCalendarApplication.graph(context)
        val labels = textContext(settings)
        val startDate = Instant.ofEpochMilli(startMillis).atZone(zoneId).toLocalDate()
        val endDateExclusive = Instant.ofEpochMilli(endMillis).atZone(zoneId).toLocalDate()
        val eventSnapshot = graph.repository.eventsSnapshot(startMillis, endMillis)
            .filterNot { it.collectionHref in settings.hiddenCollectionHrefs }
            .filterNot { it.status.equals("CANCELLED", ignoreCase = true) }
        val taskSnapshot = graph.repository.datedTasksSnapshot(startMillis, endMillis)
            .filterNot { it.collectionHref in settings.hiddenCollectionHrefs }
            .filterNot { it.status.equals("CANCELLED", ignoreCase = true) }
            .filter { settings.showCompletedTasks || !it.isCompleted }
        val events = if (launchKind == KgsWidgetKind.Agenda) {
            buildAgendaEventRows(
                events = eventSnapshot,
                tasks = taskSnapshot,
                labels = labels,
                rangeStart = startDate,
                rangeEndExclusive = endDateExclusive,
            )
        } else {
            eventSnapshot.map { event -> event.toListRow(settings.locale, launchKind, labels) }
        }
        val tasks = taskSnapshot
            .map { task ->
                if (launchKind == KgsWidgetKind.Agenda) {
                    task.toAgendaTaskRow(settings, labels)
                } else {
                    task.toListRow(settings.locale, settings.taskColorMode, launchKind, labels)
                }
            }
        return events + tasks
    }

    private fun List<WidgetListRow>.withAgendaSections(): List<WidgetListRow> =
        buildList {
            var currentYear: Int? = null
            var currentDate: LocalDate? = null
            this@withAgendaSections.forEach { row ->
                if (row.date.year != currentYear) {
                    currentYear = row.date.year
                    currentDate = null
                    add(WidgetListRow.section(row.date.year.toString()))
                }
                val showDate = row.date != currentDate
                add(row.copy(showAgendaDate = showDate))
                currentDate = row.date
            }
        }

    private suspend fun loadTaskItems(settings: WidgetRenderSettings, appWidgetId: Int): List<WidgetListRow> {
        val graph = KgsCalendarApplication.graph(context)
        val today = LocalDate.now(zoneId)
        val allTasks = graph.repository.allTasksSnapshot()
            .distinctBy { it.resourceHref }
            .filterNot { it.collectionHref in settings.hiddenCollectionHrefs }
        val activeTasks = allTasks.filter { it.isWidgetActiveTask() }
        val selectedTasks = when (settings.tasksWidgetDisplayMode) {
            WidgetTaskDisplayMode.Planned -> activeTasks.filter { it.widgetTaskDate(zoneId) != null }
            WidgetTaskDisplayMode.Unplanned -> activeTasks.filter { it.widgetTaskDate(zoneId) == null }
            WidgetTaskDisplayMode.Today -> activeTasks.filter { task ->
                val date = task.widgetTaskDate(zoneId) ?: return@filter false
                date == today || (settings.tasksWidgetIncludeOverdue && date.isBefore(today))
            }
        }
        val visibleActiveTasks = includeDescendantTasks(selectedTasks, allTasks)
        val visibleTasks = includeAncestorTasks(visibleActiveTasks, allTasks)
        return visibleTasks.toTaskHierarchy(settings, appWidgetId)
    }

    private fun includeDescendantTasks(selectedTasks: List<TaskEntity>, allTasks: List<TaskEntity>): List<TaskEntity> {
        val selectedByResource = selectedTasks.associateBy { it.resourceHref }
        if (selectedByResource.isEmpty()) return emptyList()
        val included = LinkedHashMap(selectedByResource)
        val childrenByParent = allTasks
            .filter { !it.parentUid.isNullOrBlank() }
            .groupBy { it.collectionHref to it.parentUid.orEmpty() }
        val queue = ArrayDeque(selectedTasks)
        val traversed = selectedTasks.mapTo(mutableSetOf()) { it.resourceHref }
        while (queue.isNotEmpty()) {
            val parent = queue.removeFirst()
            childrenByParent[parent.collectionHref to parent.uid].orEmpty().forEach { child ->
                if (traversed.add(child.resourceHref)) {
                    queue.add(child)
                }
                if (child.isWidgetActiveTask()) {
                    included.putIfAbsent(child.resourceHref, child)
                }
            }
        }
        return included.values.toList()
    }

    private fun includeAncestorTasks(selectedTasks: List<TaskEntity>, allTasks: List<TaskEntity>): List<TaskEntity> {
        if (selectedTasks.isEmpty()) return emptyList()
        val byCollectionUid = allTasks.associateBy { it.collectionHref to it.uid }
        val globallyUniqueByUid = allTasks.groupBy { it.uid }
            .filterValues { it.size == 1 }
            .mapValues { it.value.single() }
        val included = LinkedHashMap<String, TaskEntity>()

        fun candidateParent(task: TaskEntity): TaskEntity? {
            val parentUid = task.parentUid?.takeIf { it.isNotBlank() } ?: return null
            return byCollectionUid[task.collectionHref to parentUid] ?: globallyUniqueByUid[parentUid]
        }

        selectedTasks.forEach { task ->
            var cursor: TaskEntity? = task
            val seen = mutableSetOf<String>()
            while (cursor != null && seen.add(cursor.resourceHref)) {
                included.putIfAbsent(cursor.resourceHref, cursor)
                cursor = candidateParent(cursor)
            }
        }
        return included.values.toList()
    }

    private fun List<TaskEntity>.toTaskHierarchy(settings: WidgetRenderSettings, appWidgetId: Int): List<WidgetListRow> {
        if (isEmpty()) return emptyList()
        val distinctTasks = distinctBy { it.resourceHref }
        val byCollectionUid = distinctTasks.associateBy { it.collectionHref to it.uid }
        val globallyUniqueByUid = distinctTasks.groupBy { it.uid }
            .filterValues { it.size == 1 }
            .mapValues { it.value.single() }
        val comparator = settings.taskComparator()

        fun candidateParent(task: TaskEntity): TaskEntity? {
            val parentUid = task.parentUid?.takeIf { it.isNotBlank() } ?: return null
            return byCollectionUid[task.collectionHref to parentUid] ?: globallyUniqueByUid[parentUid]
        }

        val parentByResource = distinctTasks.associate { task ->
            var parent = candidateParent(task)
            val seen = mutableSetOf(task.resourceHref)
            while (parent != null && seen.add(parent.resourceHref)) {
                parent = candidateParent(parent)
            }
            task.resourceHref to if (parent == null) candidateParent(task) else null
        }
        val childrenByParent = distinctTasks
            .mapNotNull { child -> parentByResource[child.resourceHref]?.resourceHref?.let { it to child } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, children) -> children.sortedWith(comparator) }
        val roots = distinctTasks.filter { task ->
            parentByResource[task.resourceHref] == null
        }.sortedWith(comparator)
        val defaultExpanded = settings.tasksWidgetSubtaskDefaultMode
            .resolveSubtasksExpandedByDefault(settings.subtasksExpandedByDefault)
        return buildList {
            val emitted = mutableSetOf<String>()
            fun append(task: TaskEntity, depth: Int, continuationLevels: Set<Int>, lastSibling: Boolean) {
                if (!emitted.add(task.resourceHref)) return
                val children = childrenByParent[task.resourceHref].orEmpty()
                val boundedDepth = depth.coerceAtMost(WIDGET_TASK_MAX_DEPTH)
                val expanded = KgsWidgetTaskExpansionState.isExpanded(
                    context = context,
                    appWidgetId = appWidgetId,
                    taskResourceHref = task.resourceHref,
                    defaultExpanded = defaultExpanded,
                )
                add(
                    task.toTaskRow(
                        settings = settings,
                        depth = boundedDepth,
                        childCount = children.size,
                        continuationLevels = continuationLevels.filter { it < WIDGET_TASK_MAX_DEPTH }.toSet(),
                        lastSibling = lastSibling,
                        subtasksExpanded = expanded,
                    ),
                )
                if (!expanded) return
                children.forEachIndexed { index, child ->
                    val childLast = index == children.lastIndex
                    val childContinuationLevels = if (childLast) continuationLevels else continuationLevels + boundedDepth
                    append(child, boundedDepth + 1, childContinuationLevels, childLast)
                }
            }
            roots.forEachIndexed { index, root ->
                append(root, 0, emptySet(), index == roots.lastIndex)
            }
            distinctTasks
                .filterNot { it.resourceHref in emitted }
                .filter { parentByResource[it.resourceHref] == null }
                .forEach {
                append(it, 0, emptySet(), true)
            }
        }
    }

    private fun todayStartMillis(): Long =
        LocalDate.now(zoneId).atStartOfDay(zoneId).toInstant().toEpochMilli()

    private fun EventEntity.toListRow(locale: Locale, launchKind: KgsWidgetKind, labels: Context): WidgetListRow {
        val start = Instant.ofEpochMilli(startsAtMillis).atZone(zoneId)
        val end = Instant.ofEpochMilli(endsAtMillis).atZone(zoneId)
        val titleText = title.ifBlank { labels.getString(R.string.no_title) }
        val metaText = if (allDay) {
            if (launchKind == KgsWidgetKind.Agenda) {
                labels.getString(R.string.all_day)
            } else {
                "${start.toLocalDate().relativeDateLabel(locale)} - ${labels.getString(R.string.all_day)}"
            }
        } else {
            val timeText = "${start.toLocalTime().timeText()} - ${end.toLocalTime().timeText()}"
            if (launchKind == KgsWidgetKind.Agenda) {
                timeText
            } else {
                "${start.toLocalDate().relativeDateLabel(locale)} - $timeText"
            }
        }
        return WidgetListRow.item(
            title = titleText,
            meta = metaText,
            color = displayColor(),
            sortMillis = startsAtMillis,
            date = start.toLocalDate(),
            completed = false,
            allDaySort = if (allDay) 0 else 1,
            launchKind = launchKind,
            stableKey = "event:$resourceHref",
            eventResourceHref = resourceHref,
            location = location?.takeIf { it.isNotBlank() },
            eventStatus = status,
            endMillis = endsAtMillis,
        )
    }

    private fun buildAgendaEventRows(
        events: List<EventEntity>,
        tasks: List<TaskEntity>,
        labels: Context,
        rangeStart: LocalDate,
        rangeEndExclusive: LocalDate,
    ): List<WidgetListRow> {
        val taskDates = tasks
            .mapNotNull { it.widgetTaskDate(zoneId) }
            .toSet()
        val eventDatesByResource = events.associate { event ->
            event.resourceHref to event.visibleWidgetAgendaDates(rangeStart, rangeEndExclusive)
        }
        return events.flatMap { event ->
            val dates = eventDatesByResource[event.resourceHref].orEmpty()
            if (dates.size <= 1) {
                dates.firstOrNull()?.let { date ->
                    listOf(event.toAgendaSpanRow(labels, date, date))
                }.orEmpty()
            } else {
                val interruptionDates = dates.filterTo(mutableSetOf()) { date ->
                    date in taskDates || events.any { other ->
                        other.resourceHref != event.resourceHref &&
                            eventDatesByResource[other.resourceHref].orEmpty().contains(date)
                    }
                }
                event.toAgendaSpanRows(labels, dates, interruptionDates)
            }
        }
    }

    private fun EventEntity.visibleWidgetAgendaDates(
        rangeStart: LocalDate,
        rangeEndExclusive: LocalDate,
    ): List<LocalDate> {
        val startDate = startsAtMillis.toDate(zoneId)
        val endDate = endDateInclusive(zoneId).coerceAtLeast(startDate)
        val first = maxOf(startDate, rangeStart)
        val last = minOf(endDate, rangeEndExclusive.minusDays(1))
        if (last.isBefore(first)) return emptyList()
        val dates = mutableListOf<LocalDate>()
        var date = first
        var guard = 0
        while (!date.isAfter(last) && guard < 370) {
            dates += date
            date = date.plusDays(1)
            guard++
        }
        return dates
    }

    private fun EventEntity.toAgendaSpanRows(
        labels: Context,
        dates: List<LocalDate>,
        interruptionDates: Set<LocalDate>,
    ): List<WidgetListRow> {
        val sortedDates = dates.sorted()
        if (sortedDates.isEmpty()) return emptyList()
        val rows = mutableListOf<WidgetListRow>()
        var segmentStart: LocalDate? = null
        var previous: LocalDate? = null

        fun flushSegment(end: LocalDate) {
            val start = segmentStart ?: return
            if (!end.isBefore(start)) {
                rows += toAgendaSpanRow(labels, start, end)
            }
            segmentStart = null
        }

        sortedDates.forEach { date ->
            val last = previous
            if (last != null && last.plusDays(1) != date) {
                flushSegment(last)
            }
            if (date in interruptionDates) {
                flushSegment(date.minusDays(1))
                rows += toAgendaSpanRow(labels, date, date)
            } else if (segmentStart == null) {
                segmentStart = date
            }
            previous = date
        }
        previous?.let(::flushSegment)
        return rows
    }

    private fun EventEntity.toAgendaSpanRow(
        labels: Context,
        startDate: LocalDate,
        endDate: LocalDate,
    ): WidgetListRow {
        val start = Instant.ofEpochMilli(startsAtMillis).atZone(zoneId)
        val end = Instant.ofEpochMilli(endsAtMillis).atZone(zoneId)
        val titleText = title.ifBlank { labels.getString(R.string.no_title) }
        val spansDays = endDate.isAfter(startDate)
        val metaText = when {
            spansDays && allDay -> "${startDate.widgetSpanDateText(labels)} - ${endDate.widgetSpanDateText(labels)}, ${labels.getString(R.string.all_day)}"
            spansDays -> "${startDate.widgetSpanDateText(labels)} - ${endDate.widgetSpanDateText(labels)}"
            allDay -> labels.getString(R.string.all_day)
            else -> "${start.toLocalTime().timeText()} - ${end.toLocalTime().timeText()}"
        }
        val rowStartMillis = if (startDate == startsAtMillis.toDate(zoneId)) {
            startsAtMillis
        } else {
            startDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
        }
        return WidgetListRow.item(
            title = titleText,
            meta = metaText,
            color = displayColor(),
            sortMillis = rowStartMillis,
            date = startDate,
            completed = false,
            allDaySort = if (allDay) 0 else 1,
            launchKind = KgsWidgetKind.Agenda,
            stableKey = "event:$resourceHref:${startDate.toEpochDay()}:${endDate.toEpochDay()}",
            eventResourceHref = resourceHref,
            location = location?.takeIf { it.isNotBlank() },
            eventStatus = status,
            endMillis = endsAtMillis,
            spanEndDate = endDate.takeIf { spansDays },
        )
    }

    private fun TaskEntity.toListRow(locale: Locale, taskColorMode: TaskColorMode, launchKind: KgsWidgetKind, labels: Context): WidgetListRow {
        val millis = startAtMillis ?: dueAtMillis
        val date = millis?.let { Instant.ofEpochMilli(it).atZone(zoneId).toLocalDate() }
        val time = millis?.let { Instant.ofEpochMilli(it).atZone(zoneId).toLocalTime() }
        val hasTime = if (startAtMillis != null) startHasTime else dueHasTime
        val dateText = date?.relativeDateLabel(locale) ?: labels.getString(R.string.no_date)
        val timeText = if (time != null && hasTime) " - ${time.timeText()}" else ""
        return WidgetListRow.item(
            title = title.ifBlank { labels.getString(R.string.no_title) },
            meta = "${labels.getString(R.string.task)} - $dateText$timeText",
            color = displayColor(taskColorMode),
            sortMillis = millis ?: Long.MAX_VALUE,
            date = date ?: LocalDate.now(zoneId),
            completed = isCompleted,
            allDaySort = if (hasTime) 1 else 0,
            launchKind = launchKind,
            stableKey = "task:$resourceHref",
            taskResourceHref = resourceHref,
        )
    }

    private fun TaskEntity.toAgendaTaskRow(settings: WidgetRenderSettings, labels: Context): WidgetListRow {
        val millis = startAtMillis ?: dueAtMillis
        val date = widgetTaskDate(zoneId) ?: LocalDate.now(zoneId)
        return WidgetListRow.task(
            title = title.ifBlank { labels.getString(R.string.no_title) },
            meta = localizedWidgetTaskTimeLabel(settings.locale, labels),
            color = displayColor(settings.taskColorMode),
            sortMillis = millis ?: Long.MAX_VALUE,
            date = date,
            completed = isCompleted,
            taskResourceHref = resourceHref,
            statusGlyph = when (effectiveStatus()) {
                "COMPLETED" -> "\u2713"
                "IN-PROCESS" -> "\u25D0"
                "CANCELLED" -> "\u00D7"
                else -> "\u25CB"
            },
            depth = 0,
            childCount = 0,
            continuationLevels = emptySet(),
            lastSibling = true,
            subtasksExpanded = false,
            priority = priority,
            priorityMotionEnabled = settings.priorityAnimationsEnabled,
            launchKind = KgsWidgetKind.Agenda,
        )
    }

    private fun TaskEntity.localizedWidgetTaskTimeLabel(locale: Locale, labels: Context): String {
        val startDate = startAtMillis?.toDate(zoneId)
        val dueDate = dueAtMillis?.toDate(zoneId)
        val date = dueDate ?: startDate ?: return labels.getString(R.string.inbox)
        val dateText = date.format(DateTimeFormatter.ofPattern("EEE, d. MMM", locale))
        val startTimed = startAtMillis?.takeIf { startHasTime }
        val dueTimed = dueAtMillis?.takeIf { dueHasTime }
        return when {
            startTimed != null && dueTimed != null && startDate == dueDate ->
                "$dateText, ${startTimed.toTimeText(zoneId)}-${dueTimed.toTimeText(zoneId)}"
            startTimed != null && dueTimed != null ->
                "${startTimed.toDate(zoneId).format(DateTimeFormatter.ofPattern("d. MMM", locale))} ${startTimed.toTimeText(zoneId)} - " +
                    "${dueTimed.toDate(zoneId).format(DateTimeFormatter.ofPattern("d. MMM", locale))} ${dueTimed.toTimeText(zoneId)}"
            startTimed != null ->
                "$dateText, ${labels.getString(R.string.from_time, startTimed.toTimeText(zoneId))}"
            dueTimed != null ->
                "$dateText, ${labels.getString(R.string.until_time, dueTimed.toTimeText(zoneId))}"
            else ->
                "$dateText, ${labels.getString(R.string.all_day)}"
        }
    }

    private fun TaskEntity.toTaskRow(
        settings: WidgetRenderSettings,
        depth: Int,
        childCount: Int,
        continuationLevels: Set<Int>,
        lastSibling: Boolean,
        subtasksExpanded: Boolean,
    ): WidgetListRow {
        val labels = textContext(settings)
        val millis = startAtMillis ?: dueAtMillis
        val date = widgetTaskDate(zoneId) ?: LocalDate.now(zoneId)
        val time = millis?.let { Instant.ofEpochMilli(it).atZone(zoneId).toLocalTime() }
        val hasTime = if (startAtMillis != null) startHasTime else dueHasTime
        val dateText = if (millis == null) labels.getString(R.string.no_date) else date.relativeDateLabel(settings.locale)
        val timeText = if (time != null && hasTime) " - ${time.timeText()}" else ""
        val statusText = statusText(labels)
        val metaText = listOfNotNull(
            "$dateText$timeText",
            statusText.takeUnless { it == labels.getString(R.string.status_open) },
        ).joinToString(" - ")
        return WidgetListRow.task(
            title = title.ifBlank { labels.getString(R.string.no_title) },
            meta = metaText,
            color = displayColor(settings.taskColorMode),
            sortMillis = millis ?: Long.MAX_VALUE,
            date = date,
            completed = isCompleted,
            taskResourceHref = resourceHref,
            statusGlyph = when (effectiveStatus()) {
                "COMPLETED" -> "\u2713"
                "IN-PROCESS" -> "\u25D0"
                "CANCELLED" -> "\u00D7"
                else -> "\u25CB"
            },
            depth = depth,
            childCount = childCount,
            continuationLevels = continuationLevels,
            lastSibling = lastSibling,
            subtasksExpanded = subtasksExpanded,
            priority = priority,
            priorityMotionEnabled = settings.priorityAnimationsEnabled,
        )
    }

    private fun TaskEntity.statusText(labels: Context): String = when (effectiveStatus()) {
        "IN-PROCESS" -> labels.getString(R.string.in_progress)
        "COMPLETED" -> labels.getString(R.string.status_completed)
        "CANCELLED" -> labels.getString(R.string.aborted)
        else -> labels.getString(R.string.status_open)
    }

    private fun LocalDate.relativeDateLabel(locale: Locale): String {
        val today = LocalDate.now(zoneId)
        val labels = textContext(locale)
        return when (this) {
            today -> labels.getString(R.string.today)
            else -> format(DateTimeFormatter.ofPattern("EEE, d. MMM", locale))
        }
    }

    private fun LocalDate.widgetSpanDateText(labels: Context): String {
        val locale = labels.resources.configuration.locales[0] ?: Locale.getDefault()
        return format(DateTimeFormatter.ofPattern("d. MMM", locale))
    }

    private fun LocalTime.timeText(): String =
        format(DateTimeFormatter.ofPattern("HH:mm"))
}

internal fun monthSpanChipLayout(span: Int): Int = when (span.coerceIn(1, 7)) {
    1 -> R.layout.widget_month_span_chip_1
    2 -> R.layout.widget_month_span_chip_2
    3 -> R.layout.widget_month_span_chip_3
    4 -> R.layout.widget_month_span_chip_4
    5 -> R.layout.widget_month_span_chip_5
    6 -> R.layout.widget_month_span_chip_6
    else -> R.layout.widget_month_span_chip_7
}

internal fun monthBottomFadeSpanLayout(span: Int): Int = when (span.coerceIn(1, 7)) {
    1 -> R.layout.widget_month_bottom_fade_span_1
    2 -> R.layout.widget_month_bottom_fade_span_2
    3 -> R.layout.widget_month_bottom_fade_span_3
    4 -> R.layout.widget_month_bottom_fade_span_4
    5 -> R.layout.widget_month_bottom_fade_span_5
    6 -> R.layout.widget_month_bottom_fade_span_6
    else -> R.layout.widget_month_bottom_fade_span_7
}

internal fun WidgetMonthItem.monthSpanTextStartPaddingDp(): Float =
    if (fadesFromPrevious) {
        WIDGET_MONTH_SPAN_FADE_WIDTH_DP + WIDGET_MONTH_SPAN_FADE_TEXT_INSET_DP
    } else {
        4f
    }

internal fun WidgetMonthItem.monthChipMaskRes(): Int {
    val edges = monthChipEdges(
        continuesFromPrevious = continuesFromPrevious,
        continuesToNext = continuesToNext,
        fadesFromPrevious = fadesFromPrevious,
        fadesToNext = fadesToNext,
    )
    return when (monthChipMask(edges)) {
        WidgetMonthChipMask.RoundRound -> R.drawable.widget_month_chip_mask_round_round
        WidgetMonthChipMask.RoundSquare -> R.drawable.widget_month_chip_mask_round_square
        WidgetMonthChipMask.RoundFade -> R.drawable.widget_month_chip_mask_round_fade
        WidgetMonthChipMask.SquareRound -> R.drawable.widget_month_chip_mask_square_round
        WidgetMonthChipMask.SquareSquare -> R.drawable.widget_month_chip_mask_square_square
        WidgetMonthChipMask.SquareFade -> R.drawable.widget_month_chip_mask_square_fade
        WidgetMonthChipMask.FadeRound -> R.drawable.widget_month_chip_mask_fade_round
        WidgetMonthChipMask.FadeSquare -> R.drawable.widget_month_chip_mask_fade_square
        WidgetMonthChipMask.FadeFade -> R.drawable.widget_month_chip_mask_fade_fade
    }
}

internal fun monthTodayLabel(text: String, isToday: Boolean): CharSequence =
    if (isToday && text.isNotEmpty()) {
        SpannableString(text).apply {
            setSpan(StyleSpan(Typeface.BOLD), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    } else {
        text
    }

internal fun monthDotBitmap(context: Context, color: Int): Bitmap {
    val density = context.resources.displayMetrics.density
    val sizePx = (WIDGET_MONTH_DOT_SIZE_DP * density).roundToInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val radius = sizePx / 2f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }
    canvas.drawCircle(radius, radius, radius, paint)
    return bitmap
}

internal data class WidgetRenderSettings(
    val locale: Locale = Locale.getDefault(),
    val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    val hiddenCollectionHrefs: Set<String> = emptySet(),
    val showCompletedTasks: Boolean = true,
    val themeMode: AppThemeMode = AppThemeMode.KgsBlue,
    val colorMode: AppColorMode = AppColorMode.Auto,
    val systemNightMode: Int = Configuration.UI_MODE_NIGHT_UNDEFINED,
    val taskColorMode: TaskColorMode = TaskColorMode.Collection,
    val priorityAnimationsEnabled: Boolean = true,
    val subtasksExpandedByDefault: Boolean = true,
    val tasksWidgetDisplayMode: WidgetTaskDisplayMode = WidgetTaskDisplayMode.Planned,
    val tasksWidgetIncludeOverdue: Boolean = true,
    val tasksWidgetSortMode: WidgetTaskSortMode = WidgetTaskSortMode.Date,
    val tasksWidgetCreateMode: WidgetTaskCreateMode = WidgetTaskCreateMode.Today,
    val tasksWidgetSubtaskDefaultMode: WidgetTaskSubtaskDefaultMode = WidgetTaskSubtaskDefaultMode.FollowApp,
    val maxVisibleAllDayItems: Int = 3,
    val dayWidgetScalePercent: Int = SettingsStore.DEFAULT_DAY_WIDGET_SCALE_PERCENT,
    val dayWidgetStartHour: Int = SettingsStore.DEFAULT_DAY_WIDGET_START_HOUR,
    val dayWidgetStartAtCurrentHour: Boolean = SettingsStore.DEFAULT_DAY_WIDGET_START_AT_CURRENT_HOUR,
    val multiWidgetMonthPercent: Int = SettingsStore.DEFAULT_MULTI_WIDGET_MONTH_PERCENT,
) {
    fun dayWidgetHourRowHeightDp(): Float =
        WIDGET_DAY_HOUR_ROW_HEIGHT_DP * SettingsStore.normalizeDayWidgetScalePercent(dayWidgetScalePercent) / 100f

    fun weekDays(): List<DayOfWeek> =
        (0..6).map { firstDayOfWeek.plus(it.toLong()) }

    fun taskComparator(): Comparator<TaskEntity> {
        val titleComparator = compareBy<TaskEntity> { it.title.lowercase(locale) }
        return when (tasksWidgetSortMode) {
            WidgetTaskSortMode.Priority -> compareBy<TaskEntity> { it.priority ?: 9 }
                .thenBy { it.startAtMillis ?: it.dueAtMillis ?: Long.MAX_VALUE }
                .then(titleComparator)
            WidgetTaskSortMode.Status -> compareBy<TaskEntity> { it.statusSortRank() }
                .thenBy { it.startAtMillis ?: it.dueAtMillis ?: Long.MAX_VALUE }
                .then(titleComparator)
            WidgetTaskSortMode.Date -> compareBy<TaskEntity> { it.startAtMillis ?: it.dueAtMillis ?: Long.MAX_VALUE }
                .thenBy { it.priority ?: 9 }
                .then(titleComparator)
        }
    }
}

internal data class WidgetListRow(
    val type: WidgetListRowType,
    val title: String,
    val meta: String,
    val color: Int,
    val sortMillis: Long,
    val date: LocalDate,
    val completed: Boolean,
    val allDaySort: Int,
    val launchKind: KgsWidgetKind,
    val stableId: Long,
    val location: String?,
    val eventStatus: String?,
    val endMillis: Long,
    val spanEndDate: LocalDate?,
    val showAgendaDate: Boolean,
    val eventResourceHref: String?,
    val taskResourceHref: String?,
    val statusGlyph: String,
    val depth: Int,
    val childCount: Int,
    val continuationLevels: Set<Int>,
    val lastSibling: Boolean,
    val subtasksExpanded: Boolean,
    val priority: Int?,
    val priorityMotionEnabled: Boolean,
) {
    fun appendSignatureTo(builder: StringBuilder) {
        builder
            .append(type.name)
            .append('|').append(stableId)
            .append('|').append(title)
            .append('|').append(meta)
            .append('|').append(color)
            .append('|').append(sortMillis)
            .append('|').append(date.toEpochDay())
            .append('|').append(completed)
            .append('|').append(allDaySort)
            .append('|').append(launchKind.name)
            .append('|').append(location.orEmpty())
            .append('|').append(eventStatus.orEmpty())
            .append('|').append(endMillis)
            .append('|').append(spanEndDate?.toEpochDay() ?: Long.MIN_VALUE)
            .append('|').append(showAgendaDate)
            .append('|').append(eventResourceHref.orEmpty())
            .append('|').append(taskResourceHref.orEmpty())
            .append('|').append(statusGlyph)
            .append('|').append(depth)
            .append('|').append(childCount)
            .append('|').append(continuationLevels.sorted().joinToString(","))
            .append('|').append(lastSibling)
            .append('|').append(subtasksExpanded)
            .append('|').append(priority ?: 0)
            .append('|').append(priorityMotionEnabled)
    }

    fun toRemoteViews(
        context: Context,
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
                return toAgendaEventRemoteViews(context, packageName, palette, appWidgetId, renderOptions)
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
                views.bindAgendaDate(palette, muted = completed, visible = showAgendaDate, locale = rowLocale)
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
            KgsWidgetBitmapUriStore.getIfPresent(context, appWidgetId, cacheKey)
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
            KgsWidgetBitmapUriStore.put(context, appWidgetId, cacheKey, bitmap)
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

    private fun taskPriorityMotionCacheKey(
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

    private fun toAgendaEventRemoteViews(
        context: Context,
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
            views.bindAgendaDate(palette, muted, visible = showAgendaDate, locale = rowLocale, endDate = spanEndDate)
        }
        val cardWidthDp = renderOptions.taskArtWidthDp
        views.setWidgetRowImage(
            context = context,
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

    private fun sourceKindIsDay(): Boolean =
        launchKind == KgsWidgetKind.Day

    private fun agendaEventCardCacheKey(
        palette: WidgetPalette,
        cardWidthDp: Float,
        rowHeightDp: Int,
        dayCard: Boolean,
        muted: Boolean,
    ): String =
        "agenda-event|$launchKind|$stableId|$title|$meta|${location.orEmpty()}|$eventStatus|$color|$completed|$endMillis|${spanEndDate?.toEpochDay() ?: Long.MIN_VALUE}|$palette|$cardWidthDp|$rowHeightDp|$dayCard|$muted|$WIDGET_AGENDA_ART_BITMAP_SCALE|$WIDGET_COLLECTION_RENDER_SIGNATURE_VERSION"

    private fun RemoteViews.bindAgendaDate(
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

    private fun agendaEventCardBitmap(
        context: Context,
        palette: WidgetPalette,
        cardWidthDp: Float,
        rowHeightDp: Int,
        dayCard: Boolean,
        muted: Boolean,
    ): Bitmap {
        val widthDp = cardWidthDp.coerceAtLeast(WIDGET_TASK_MIN_CARD_WIDTH_DP)
        val bitmapScale = context.resources.displayMetrics.density * WIDGET_AGENDA_ART_BITMAP_SCALE
        val bitmap = Bitmap.createBitmap(
            (widthDp * bitmapScale).roundToInt().coerceAtLeast(1),
            (rowHeightDp * bitmapScale).roundToInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(bitmap)
        canvas.drawColor(palette.rootBackgroundColor)
        canvas.scale(bitmapScale, bitmapScale)

        val baseColor = color
        val tentative = eventStatus.equals("TENTATIVE", ignoreCase = true)
        val backgroundColor = when {
            tentative -> palette.rootBackgroundColor
            muted -> baseColor.greyedOut(0.62f).blendWith(palette.rootBackgroundColor, 0.28f)
            else -> baseColor
        }
        val contentBase = when {
            tentative -> palette.text
            baseColor.isDarkColor() && !muted -> 0xFFFFFFFF.toInt()
            else -> 0xFF1C1A18.toInt()
        }
        val contentColor = contentBase.withAlpha(if (muted) 0.64f else 1f)
        val secondaryColor = contentBase.withAlpha(if (muted) 0.72f else 0.92f)
        val tertiaryColor = contentBase.withAlpha(if (muted) 0.66f else 0.86f)
        val radius = if (dayCard) WIDGET_DAY_EVENT_CARD_RADIUS_DP else WIDGET_AGENDA_EVENT_CARD_RADIUS_DP
        val cardRect = RectF(0.5f, 0.5f, widthDp - 0.5f, rowHeightDp - 0.5f)
        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = backgroundColor
        }
        canvas.drawRoundRect(cardRect, radius, radius, backgroundPaint)
        if (tentative || muted) {
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1f
                color = if (tentative) baseColor.withAlpha(0.95f) else baseColor.greyedOut(0.7f).withAlpha(0.9f)
                if (tentative) {
                    pathEffect = DashPathEffect(floatArrayOf(5f, 4f), 0f)
                }
            }
            canvas.drawRoundRect(cardRect, radius, radius, borderPaint)
        }

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = contentColor
            textSize = if (dayCard) 12f else 13f
            typeface = Typeface.create(Typeface.SANS_SERIF, if (dayCard) Typeface.BOLD else Typeface.BOLD)
            isStrikeThruText = eventStatus.equals("CANCELLED", ignoreCase = true)
        }
        val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = secondaryColor
            textSize = if (dayCard) 10.5f else 10.8f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            isStrikeThruText = titlePaint.isStrikeThruText
        }
        val locationPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tertiaryColor
            textSize = 10f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            isStrikeThruText = titlePaint.isStrikeThruText
        }
        val textStart = 12f
        val textWidth = (widthDp - 24f).coerceAtLeast(0f)
        val locationText = location?.takeIf { it.isNotBlank() }
        if (dayCard) {
            val titleBaseline = if (locationText == null) rowHeightDp / 2f + 4f else 20f
            canvas.drawText(ellipsizeForPaint(title, titlePaint, textWidth), textStart, titleBaseline, titlePaint)
            locationText?.let {
                canvas.drawText(ellipsizeForPaint(it, locationPaint, textWidth), textStart, 34.5f, locationPaint)
            }
        } else {
            canvas.drawText(ellipsizeForPaint(title, titlePaint, textWidth), textStart, 19f, titlePaint)
            canvas.drawText(ellipsizeForPaint(meta, timePaint, textWidth), textStart, 34.5f, timePaint)
            locationText?.let {
                canvas.drawText(ellipsizeForPaint(it, locationPaint, textWidth), textStart, 48.5f, locationPaint)
            }
        }
        return bitmap
    }

    private fun taskRowBackgroundBitmap(
        context: Context,
        palette: WidgetPalette,
        taskArtWidthDp: Float,
        cardColor: Int,
        contentColor: Int,
        secondaryColor: Int,
        subtaskExpansionProgress: Float,
        lightweight: Boolean,
        baseSpec: TaskCardBaseSpec,
        cardMeta: String,
    ): Bitmap {
        val bitmapScale = context.resources.displayMetrics.density *
            if (lightweight) WIDGET_TASK_TRANSITION_BITMAP_SCALE else WIDGET_TASK_PRIORITY_BITMAP_SCALE
        val bitmap = Bitmap.createBitmap(
            (taskArtWidthDp * bitmapScale).roundToInt().coerceAtLeast(1),
            (baseSpec.rowHeightDp * bitmapScale).roundToInt().coerceAtLeast(1),
            Bitmap.Config.RGB_565,
        )
        val canvas = Canvas(bitmap)
        canvas.drawColor(palette.rootBackgroundColor)
        canvas.scale(bitmapScale, bitmapScale)
        drawTaskHierarchy(canvas, palette.hierarchyLine, subtaskExpansionProgress, baseSpec)
        drawTaskCard(canvas, baseSpec, cardColor, TaskPriorityEffect.None)
        drawTaskContent(
            context = context,
            canvas = canvas,
            baseSpec = baseSpec,
            contentColor = contentColor,
            secondaryColor = secondaryColor,
            effect = TaskPriorityEffect.None,
            cardMeta = cardMeta,
            subtaskExpansionProgress = subtaskExpansionProgress,
        )
        return bitmap
    }

    private fun taskPriorityMotionBitmap(
        context: Context,
        palette: WidgetPalette,
        taskArtWidthDp: Float,
        cardColor: Int,
        contentColor: Int,
        secondaryColor: Int,
        priority: Int?,
        frame: Int,
        frameCount: Int,
        subtaskExpansionProgress: Float,
        baseSpec: TaskCardBaseSpec,
        cardMeta: String,
    ): Bitmap {
        val overdrawDp = if (launchKind == KgsWidgetKind.Agenda || launchKind == KgsWidgetKind.Day) WIDGET_TASK_PRIORITY_OVERDRAW_DP else 0f
        val bitmapScale = context.resources.displayMetrics.density * WIDGET_TASK_PRIORITY_BITMAP_SCALE
        val bitmap = Bitmap.createBitmap(
            ((taskArtWidthDp + overdrawDp * 2f) * bitmapScale).roundToInt().coerceAtLeast(1),
            ((baseSpec.rowHeightDp + overdrawDp * 2f) * bitmapScale).roundToInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(bitmap)
        canvas.drawColor(palette.rootBackgroundColor)
        canvas.scale(bitmapScale, bitmapScale)
        canvas.translate(overdrawDp, overdrawDp)
        val effect = WIDGET_TASK_CARD_RENDERER.effect(priority, frame, frameCount, bitmapScale)
        val glowOutset = effect.glowSpread / 2f
        drawTaskHierarchy(canvas, palette.hierarchyLine, subtaskExpansionProgress, baseSpec)
        drawTaskCard(
            canvas = canvas,
            baseSpec = baseSpec,
            color = cardColor,
            effect = effect.copy(scale = 1f),
            horizontalSpread = glowOutset,
            verticalSpread = glowOutset,
            alpha = effect.glowAlpha,
            radius = baseSpec.cornerRadiusDp + effect.glowSpread / 3f,
        )
        drawTaskCard(
            canvas = canvas,
            baseSpec = baseSpec,
            color = cardColor,
            effect = effect,
        )
        drawTaskContent(
            context = context,
            canvas = canvas,
            baseSpec = baseSpec,
            contentColor = contentColor.withAlpha(if (completed) 0.62f else 1f),
            secondaryColor = secondaryColor,
            effect = effect,
            cardMeta = cardMeta,
            subtaskExpansionProgress = subtaskExpansionProgress,
        )
        return bitmap
    }

    private fun drawTaskHierarchy(
        canvas: Canvas,
        lineColor: Int,
        subtaskExpansionProgress: Float,
        baseSpec: TaskCardBaseSpec,
    ) {
        val boundedDepth = baseSpec.hierarchyDepth
        if (boundedDepth <= 0 && childCount <= 0 && continuationLevels.isEmpty()) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = lineColor
            strokeWidth = baseSpec.hierarchyLineStrokeDp
            strokeCap = Paint.Cap.ROUND
            style = Paint.Style.STROKE
        }
        val centerY = baseSpec.rowHeightDp / 2f
        val height = baseSpec.rowHeightDp
        continuationLevels.forEach { level ->
            if (level in 0 until WIDGET_TASK_MAX_DEPTH) {
                val x = baseSpec.hierarchySideInsetDp + level * baseSpec.hierarchyIndentDp + baseSpec.hierarchyStemDp
                canvas.drawLine(x, -baseSpec.hierarchyOverlapDp, x, height + baseSpec.hierarchyOverlapDp, paint)
            }
        }
        if (boundedDepth > 0) {
            val branchLevel = boundedDepth - 1
            val branchX = baseSpec.hierarchySideInsetDp + branchLevel * baseSpec.hierarchyIndentDp + baseSpec.hierarchyStemDp
            val branchBottom = if (lastSibling) centerY else height + baseSpec.hierarchyOverlapDp
            canvas.drawLine(branchX, -baseSpec.hierarchyOverlapDp, branchX, branchBottom, paint)
            val branchEndX = baseSpec.hierarchySideInsetDp + boundedDepth * baseSpec.hierarchyIndentDp + baseSpec.hierarchyOverlapDp + 2f
            canvas.drawLine(branchX, centerY, branchEndX, centerY, paint)
        }
        if (childCount > 0 && subtaskExpansionProgress > 0.01f) {
            val x = baseSpec.hierarchySideInsetDp + boundedDepth * baseSpec.hierarchyIndentDp + baseSpec.hierarchyStemDp
            val cardBottom = (baseSpec.rowHeightDp + baseSpec.cardHeightDp) / 2f
            val tailEnd = lerpFloat(cardBottom - 1f, height + baseSpec.hierarchyOverlapDp, subtaskExpansionProgress.coerceIn(0f, 1f))
            canvas.drawLine(x, cardBottom - 1f, x, tailEnd, paint)
        }
    }

    private fun drawTaskCard(
        canvas: Canvas,
        baseSpec: TaskCardBaseSpec,
        color: Int,
        effect: TaskPriorityEffect,
        horizontalSpread: Float = 0f,
        verticalSpread: Float = horizontalSpread,
        alpha: Float = 1f,
        radius: Float = baseSpec.cornerRadiusDp + horizontalSpread * 0.45f,
    ) {
        val cardTop = (baseSpec.rowHeightDp - baseSpec.cardHeightDp) / 2f
        val cardBottom = cardTop + baseSpec.cardHeightDp
        val centerX = (baseSpec.cardLeftDp + baseSpec.cardRightDp) / 2f + effect.translationX
        val centerY = (cardTop + cardBottom) / 2f + effect.translationY
        val halfWidth = ((baseSpec.cardRightDp - baseSpec.cardLeftDp) * effect.scale) / 2f
        val halfHeight = (baseSpec.cardHeightDp * effect.scale) / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.color = if (alpha >= 0.999f) color else color.withAlpha(alpha)
        }
        val rect = RectF(
            centerX - halfWidth - horizontalSpread,
            centerY - halfHeight - verticalSpread,
            centerX + halfWidth + horizontalSpread,
            centerY + halfHeight + verticalSpread,
        )
        canvas.drawRoundRect(rect, radius, radius, paint)
    }

    private fun drawTaskContent(
        context: Context,
        canvas: Canvas,
        baseSpec: TaskCardBaseSpec,
        contentColor: Int,
        secondaryColor: Int,
        effect: TaskPriorityEffect,
        cardMeta: String,
        subtaskExpansionProgress: Float,
    ) {
        val cardCenterX = (baseSpec.cardLeftDp + baseSpec.cardRightDp) / 2f
        fun transformedX(value: Float): Float =
            cardCenterX + (value - cardCenterX) * effect.scale + effect.translationX

        val centerY = baseSpec.rowHeightDp / 2f + effect.translationY
        drawTaskStatusGlyph(
            canvas = canvas,
            statusGlyph = statusGlyph,
            tint = contentColor,
            centerX = transformedX(baseSpec.statusCenterXDp),
            centerY = centerY,
            radius = baseSpec.statusRadiusDp * effect.scale,
            strokeWidth = baseSpec.statusStrokeDp * effect.scale,
        )

        val hasMeta = cardMeta.isNotBlank()
        val textStart = transformedX(baseSpec.textStartDp)
        val textEnd = transformedX(baseSpec.textEndDp)
        val fontScale = context.resources.displayMetrics.scaledDensity / context.resources.displayMetrics.density
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = contentColor
            textSize = baseSpec.titleTextSizeSp * fontScale * effect.scale
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = secondaryColor
            textSize = baseSpec.metaTextSizeSp * fontScale * effect.scale
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val maxTextWidth = (textEnd - textStart).coerceAtLeast(0f)
        canvas.drawText(
            ellipsizeForPaint(title, titlePaint, maxTextWidth),
            textStart,
            centerY + baseSpec.titleBaselineOffsetDp * effect.scale,
            titlePaint,
        )
        if (hasMeta) {
            canvas.drawText(
                ellipsizeForPaint(cardMeta, metaPaint, maxTextWidth),
                textStart,
                centerY + baseSpec.metaBaselineOffsetDp * effect.scale,
                metaPaint,
            )
        }

        if (childCount > 0) {
            drawTaskSubtasksChevron(
                canvas = canvas,
                tint = contentColor,
                centerX = transformedX(baseSpec.chevronCenterXDp),
                centerY = centerY,
                expandedProgress = subtaskExpansionProgress,
                scale = effect.scale,
            )
        }
    }

    private fun taskStatusIconBitmap(context: Context, statusGlyph: String, tint: Int): Bitmap {
        val width = context.dpToPx(30)
        val height = context.dpToPx(WIDGET_TASK_CARD_HEIGHT_DP)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val centerX = width / 2f
        val centerY = height / 2f
        drawTaskStatusGlyph(
            canvas = canvas,
            statusGlyph = statusGlyph,
            tint = tint,
            centerX = centerX,
            centerY = centerY,
            radius = context.dpToPx(WIDGET_TASK_STATUS_RADIUS_DP),
            strokeWidth = context.dpToPx(WIDGET_TASK_STATUS_STROKE_DP),
        )
        return bitmap
    }

    private fun drawTaskStatusGlyph(
        canvas: Canvas,
        statusGlyph: String,
        tint: Int,
        centerX: Float,
        centerY: Float,
        radius: Float,
        strokeWidth: Float,
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tint
            this.strokeWidth = strokeWidth
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        when (statusGlyph) {
            "\u2713" -> {
                paint.style = Paint.Style.STROKE
                canvas.drawCircle(centerX, centerY, radius, paint)
                val path = Path().apply {
                    moveTo(centerX - radius * 0.48f, centerY + radius * 0.01f)
                    lineTo(centerX - radius * 0.11f, centerY + radius * 0.38f)
                    lineTo(centerX + radius * 0.56f, centerY - radius * 0.48f)
                }
                canvas.drawPath(path, paint)
            }
            "\u25D0" -> {
                paint.style = Paint.Style.STROKE
                canvas.drawCircle(centerX, centerY, radius, paint)
                paint.style = Paint.Style.FILL
                canvas.drawCircle(centerX, centerY, radius * 0.34f, paint)
            }
            "\u00D7" -> {
                paint.style = Paint.Style.STROKE
                canvas.drawCircle(centerX, centerY, radius, paint)
                canvas.drawLine(
                    centerX - radius * 0.44f,
                    centerY - radius * 0.44f,
                    centerX + radius * 0.44f,
                    centerY + radius * 0.44f,
                    paint,
                )
                canvas.drawLine(
                    centerX + radius * 0.44f,
                    centerY - radius * 0.44f,
                    centerX - radius * 0.44f,
                    centerY + radius * 0.44f,
                    paint,
                )
            }
            else -> {
                paint.style = Paint.Style.STROKE
                canvas.drawCircle(centerX, centerY, radius, paint)
            }
        }
    }

    private fun taskSubtasksArrowBitmap(context: Context, tint: Int, expandedProgress: Float): Bitmap {
        val size = context.dpToPx(30)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawTaskSubtasksChevron(
            canvas = canvas,
            tint = tint,
            centerX = size / 2f,
            centerY = size / 2f,
            expandedProgress = expandedProgress,
            scale = context.resources.displayMetrics.density,
        )
        return bitmap
    }

    private fun drawTaskSubtasksChevron(
        canvas: Canvas,
        tint: Int,
        centerX: Float,
        centerY: Float,
        expandedProgress: Float,
        scale: Float,
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tint
            strokeWidth = WIDGET_TASK_SUBTASK_ARROW_STROKE_DP * scale
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            style = Paint.Style.STROKE
        }
        val progress = expandedProgress.coerceIn(0f, 1f)
        canvas.save()
        canvas.rotate(-90f * (1f - progress), centerX, centerY)
        val path = Path().apply {
            moveTo(
                centerX - WIDGET_TASK_SUBTASK_ARROW_HALF_WIDTH_DP * scale,
                centerY - WIDGET_TASK_SUBTASK_ARROW_HALF_HEIGHT_DP * scale,
            )
            lineTo(centerX, centerY + WIDGET_TASK_SUBTASK_ARROW_HALF_HEIGHT_DP * scale)
            lineTo(
                centerX + WIDGET_TASK_SUBTASK_ARROW_HALF_WIDTH_DP * scale,
                centerY - WIDGET_TASK_SUBTASK_ARROW_HALF_HEIGHT_DP * scale,
            )
        }
        canvas.drawPath(path, paint)
        canvas.restore()
    }

    private fun openFillInIntent(openTask: Boolean = taskResourceHref != null): Intent {
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

    private fun toggleFillInIntent(): Intent {
        val intent = Intent()
        intent.putExtra(EXTRA_COLLECTION_ACTION, COLLECTION_ACTION_TOGGLE_TASK)
        intent.putExtra(KgsWidgetProvider.EXTRA_TASK_RESOURCE_HREF, taskResourceHref)
        intent.data = Uri.parse("kgs-calendar://widget-row-toggle/$stableId")
        return intent
    }

    private fun toggleSubtasksFillInIntent(appWidgetId: Int): Intent {
        val intent = Intent()
        intent.putExtra(EXTRA_COLLECTION_ACTION, COLLECTION_ACTION_TOGGLE_SUBTASKS)
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        intent.putExtra(KgsWidgetProvider.EXTRA_TASK_RESOURCE_HREF, taskResourceHref)
        intent.data = Uri.parse("kgs-calendar://widget-row-toggle-subtasks/$stableId")
        return intent
    }

    companion object {
        fun empty(title: String): WidgetListRow = WidgetListRow(
            type = WidgetListRowType.Empty,
            title = title,
            meta = "",
            color = 0,
            sortMillis = Long.MIN_VALUE,
            date = LocalDate.now(),
            completed = false,
            allDaySort = 0,
            launchKind = KgsWidgetKind.Tasks,
            stableId = stableId("tasks-empty:$title"),
            location = null,
            eventStatus = null,
            endMillis = Long.MIN_VALUE,
            spanEndDate = null,
            showAgendaDate = true,
            eventResourceHref = null,
            taskResourceHref = null,
            statusGlyph = "",
            depth = 0,
            childCount = 0,
            continuationLevels = emptySet(),
            lastSibling = true,
            subtasksExpanded = true,
            priority = null,
            priorityMotionEnabled = false,
        )

        fun section(title: String, sortValue: Long = Long.MIN_VALUE): WidgetListRow = WidgetListRow(
            type = WidgetListRowType.Section,
            title = title,
            meta = "",
            color = 0,
            sortMillis = sortValue,
            date = LocalDate.now(),
            completed = false,
            allDaySort = 0,
            launchKind = KgsWidgetKind.Agenda,
            stableId = stableId("section:$title"),
            location = null,
            eventStatus = null,
            endMillis = Long.MIN_VALUE,
            spanEndDate = null,
            showAgendaDate = true,
            eventResourceHref = null,
            taskResourceHref = null,
            statusGlyph = "",
            depth = 0,
            childCount = 0,
            continuationLevels = emptySet(),
            lastSibling = true,
            subtasksExpanded = true,
            priority = null,
            priorityMotionEnabled = false,
        )

        fun now(date: LocalDate, timeLabel: String, sortValue: Long): WidgetListRow = WidgetListRow(
            type = WidgetListRowType.Now,
            title = timeLabel,
            meta = "",
            color = 0,
            sortMillis = sortValue,
            date = date,
            completed = false,
            allDaySort = 0,
            launchKind = KgsWidgetKind.Day,
            stableId = stableId("day-now:${date.toEpochDay()}:$sortValue"),
            location = null,
            eventStatus = null,
            endMillis = Long.MIN_VALUE,
            spanEndDate = null,
            showAgendaDate = true,
            eventResourceHref = null,
            taskResourceHref = null,
            statusGlyph = "",
            depth = 0,
            childCount = 0,
            continuationLevels = emptySet(),
            lastSibling = true,
            subtasksExpanded = true,
            priority = null,
            priorityMotionEnabled = false,
        )

        fun item(
            title: String,
            meta: String,
            color: Int,
            sortMillis: Long,
            date: LocalDate,
            completed: Boolean,
            allDaySort: Int,
            launchKind: KgsWidgetKind,
            stableKey: String,
            eventResourceHref: String? = null,
            taskResourceHref: String? = null,
            location: String? = null,
            eventStatus: String? = null,
            endMillis: Long = sortMillis,
            spanEndDate: LocalDate? = null,
        ): WidgetListRow = WidgetListRow(
            type = WidgetListRowType.Item,
            title = title,
            meta = meta,
            color = color,
            sortMillis = sortMillis,
            date = date,
            completed = completed,
            allDaySort = allDaySort,
            launchKind = launchKind,
            stableId = stableId(stableKey),
            location = location,
            eventStatus = eventStatus,
            endMillis = endMillis,
            spanEndDate = spanEndDate,
            showAgendaDate = true,
            eventResourceHref = eventResourceHref,
            taskResourceHref = taskResourceHref,
            statusGlyph = "",
            depth = 0,
            childCount = 0,
            continuationLevels = emptySet(),
            lastSibling = true,
            subtasksExpanded = true,
            priority = null,
            priorityMotionEnabled = false,
        )

        fun task(
            title: String,
            meta: String,
            color: Int,
            sortMillis: Long,
            date: LocalDate,
            completed: Boolean,
            taskResourceHref: String,
            statusGlyph: String,
            location: String? = null,
            depth: Int,
            childCount: Int,
            continuationLevels: Set<Int>,
            lastSibling: Boolean,
            subtasksExpanded: Boolean,
            priority: Int?,
            priorityMotionEnabled: Boolean,
            launchKind: KgsWidgetKind = KgsWidgetKind.Tasks,
        ): WidgetListRow = WidgetListRow(
            type = WidgetListRowType.Task,
            title = title,
            meta = meta,
            color = color,
            sortMillis = sortMillis,
            date = date,
            completed = completed,
            allDaySort = 1,
            launchKind = launchKind,
            stableId = stableId("task-row:$taskResourceHref"),
            location = location,
            eventStatus = null,
            endMillis = sortMillis,
            spanEndDate = null,
            showAgendaDate = true,
            eventResourceHref = null,
            taskResourceHref = taskResourceHref,
            statusGlyph = statusGlyph,
            depth = depth,
            childCount = childCount,
            continuationLevels = continuationLevels,
            lastSibling = lastSibling,
            subtasksExpanded = subtasksExpanded,
            priority = priority,
            priorityMotionEnabled = priorityMotionEnabled,
        )

        private fun stableId(value: String): Long =
            value.fold(1125899906842597L) { acc, char -> acc * 31 + char.code }
    }
}

internal enum class WidgetListRowType {
    Empty,
    Section,
    Now,
    Item,
    Task,
}

internal fun WidgetSize.collectionArtWidthDp(kind: KgsWidgetKind): Float =
    when (kind) {
        KgsWidgetKind.Agenda,
        KgsWidgetKind.Multi,
        KgsWidgetKind.Day -> (
            widthDp -
                WIDGET_TASK_CARD_SIDE_INSET_DP * 2 -
                WIDGET_AGENDA_DATE_COLUMN_WIDTH_DP -
                WIDGET_AGENDA_COLUMN_GAP_DP
            ).toFloat().coerceAtLeast(WIDGET_TASK_MIN_CARD_WIDTH_DP)
        else -> widthDp.toFloat().coerceAtLeast(1f)
    }

internal fun WidgetSize.dayGridContentWidthDp(): Float =
    widthDp.toFloat().coerceAtLeast(120f)

internal data class WidgetPalette(
    val accent: Int,
    val text: Int,
    val muted: Int,
    val faint: Int,
    val onAccent: Int,
    val rootBackgroundColor: Int,
    val rootBackgroundRes: Int,
    val bottomFadeRes: Int,
    val topFadeRes: Int,
    val sortBackgroundRes: Int,
    val hierarchyLine: Int,
    val itemBackgroundRes: Int,
    val compactItemBackgroundRes: Int,
    val badgeBackgroundRes: Int,
    val daySelectedBackgroundRes: Int,
    val monthTodayTextColor: Int,
) {
    companion object {
        fun from(context: Context, themeMode: AppThemeMode, colorMode: AppColorMode): WidgetPalette {
            val dark = when (colorMode) {
                AppColorMode.Light -> false
                AppColorMode.Dark -> true
                AppColorMode.Auto -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            }
            val accent = if (themeMode == AppThemeMode.SystemDynamic) {
                context.systemAccentColor(dark)
            } else {
                when (themeMode) {
                    AppThemeMode.KgsWarm -> if (dark) 0xFFFFB68B.toInt() else 0xFF9E572B.toInt()
                    AppThemeMode.KgsFresh -> if (dark) 0xFF76DCC4.toInt() else 0xFF0E7C66.toInt()
                    else -> if (dark) 0xFF9BCAFF.toInt() else 0xFF2563A8.toInt()
                }
            }
            return if (dark) {
                WidgetPalette(
                    accent = accent,
                    text = 0xFFE6EEF7.toInt(),
                    muted = 0xFFD6E0EC.toInt(),
                    faint = 0xFF7B8B9B.toInt(),
                    onAccent = if (accent.isDarkColor()) 0xFFFFFFFF.toInt() else 0xFF111827.toInt(),
                    rootBackgroundColor = 0xFF101923.toInt(),
                    rootBackgroundRes = R.drawable.widget_background_dark,
                    bottomFadeRes = R.drawable.widget_bottom_fade_dark,
                    topFadeRes = R.drawable.widget_top_fade_dark,
                    sortBackgroundRes = R.drawable.widget_sort_background_dark,
                    hierarchyLine = 0xC8AEBBCC.toInt(),
                    itemBackgroundRes = R.drawable.widget_item_background_dark,
                    compactItemBackgroundRes = R.drawable.widget_month_cell_compact_dark,
                    badgeBackgroundRes = R.drawable.widget_badge_background_dark,
                    daySelectedBackgroundRes = R.drawable.widget_month_day_selected_dark,
                    monthTodayTextColor = 0xFF9BCAFF.toInt(),
                )
            } else {
                val root = when (themeMode) {
                    AppThemeMode.KgsWarm -> R.drawable.widget_background_warm
                    AppThemeMode.KgsFresh -> R.drawable.widget_background_fresh
                    else -> R.drawable.widget_background
                }
                val rootColor = when (themeMode) {
                    AppThemeMode.KgsWarm -> 0xFFFFEFE8.toInt()
                    AppThemeMode.KgsFresh -> 0xFFECF8F4.toInt()
                    else -> 0xFFEEF6FF.toInt()
                }
                val bottomFade = when (themeMode) {
                    AppThemeMode.KgsWarm -> R.drawable.widget_bottom_fade_warm
                    AppThemeMode.KgsFresh -> R.drawable.widget_bottom_fade_fresh
                    else -> R.drawable.widget_bottom_fade
                }
                val topFade = when (themeMode) {
                    AppThemeMode.KgsWarm -> R.drawable.widget_top_fade_warm
                    AppThemeMode.KgsFresh -> R.drawable.widget_top_fade_fresh
                    else -> R.drawable.widget_top_fade
                }
                val badge = when (themeMode) {
                    AppThemeMode.KgsWarm -> R.drawable.widget_badge_background_warm
                    AppThemeMode.KgsFresh -> R.drawable.widget_badge_background_fresh
                    else -> R.drawable.widget_badge_background
                }
                val sortBackground = when (themeMode) {
                    AppThemeMode.KgsWarm -> R.drawable.widget_sort_background_warm
                    AppThemeMode.KgsFresh -> R.drawable.widget_sort_background_fresh
                    else -> R.drawable.widget_sort_background
                }
                val selected = when (themeMode) {
                    AppThemeMode.KgsWarm -> R.drawable.widget_month_day_selected_warm
                    AppThemeMode.KgsFresh -> R.drawable.widget_month_day_selected_fresh
                    else -> R.drawable.widget_month_day_selected
                }
                WidgetPalette(
                    accent = accent,
                    text = 0xFF17202A.toInt(),
                    muted = 0xFF526173.toInt(),
                    faint = 0xFFA4AFBA.toInt(),
                    onAccent = 0xFFFFFFFF.toInt(),
                    rootBackgroundColor = rootColor,
                    rootBackgroundRes = root,
                    bottomFadeRes = bottomFade,
                    topFadeRes = topFade,
                    sortBackgroundRes = sortBackground,
                    hierarchyLine = 0xFF707780.toInt(),
                    itemBackgroundRes = R.drawable.widget_item_background,
                    compactItemBackgroundRes = R.drawable.widget_month_cell_compact,
                    badgeBackgroundRes = badge,
                    daySelectedBackgroundRes = selected,
                    monthTodayTextColor = 0xFF1E5F9F.toInt(),
                )
            }
        }
    }
}

internal data class WidgetMonthChipStyle(
    val backgroundRes: Int,
    val fillColor: Int,
    val textColor: Int,
) {
    fun cellBackgroundRes(item: WidgetMonthItem): Int =
        if (item.fadesFromPrevious || item.fadesToNext) {
            backgroundRes(item)
        } else {
            backgroundRes
        }

    fun backgroundRes(item: WidgetMonthItem): Int {
        val suffix = when {
            item.fadesFromPrevious && item.fadesToNext -> "middle"
            item.fadesFromPrevious -> "fade_start"
            item.fadesToNext -> "fade_end"
            item.continuesFromPrevious && item.continuesToNext -> "middle"
            item.continuesFromPrevious -> "end"
            item.continuesToNext -> "start"
            else -> "rounded"
        }
        return when (backgroundRes) {
            R.drawable.widget_month_chip_red -> when (suffix) {
                "start" -> R.drawable.widget_month_chip_red_start
                "middle" -> R.drawable.widget_month_chip_red_middle
                "end" -> R.drawable.widget_month_chip_red_end
                "fade_start" -> R.drawable.widget_month_chip_red_fade_start
                "fade_end" -> R.drawable.widget_month_chip_red_fade_end
                else -> backgroundRes
            }
            R.drawable.widget_month_chip_orange -> when (suffix) {
                "start" -> R.drawable.widget_month_chip_orange_start
                "middle" -> R.drawable.widget_month_chip_orange_middle
                "end" -> R.drawable.widget_month_chip_orange_end
                "fade_start" -> R.drawable.widget_month_chip_orange_fade_start
                "fade_end" -> R.drawable.widget_month_chip_orange_fade_end
                else -> backgroundRes
            }
            R.drawable.widget_month_chip_yellow -> when (suffix) {
                "start" -> R.drawable.widget_month_chip_yellow_start
                "middle" -> R.drawable.widget_month_chip_yellow_middle
                "end" -> R.drawable.widget_month_chip_yellow_end
                "fade_start" -> R.drawable.widget_month_chip_yellow_fade_start
                "fade_end" -> R.drawable.widget_month_chip_yellow_fade_end
                else -> backgroundRes
            }
            R.drawable.widget_month_chip_green -> when (suffix) {
                "start" -> R.drawable.widget_month_chip_green_start
                "middle" -> R.drawable.widget_month_chip_green_middle
                "end" -> R.drawable.widget_month_chip_green_end
                "fade_start" -> R.drawable.widget_month_chip_green_fade_start
                "fade_end" -> R.drawable.widget_month_chip_green_fade_end
                else -> backgroundRes
            }
            R.drawable.widget_month_chip_teal -> when (suffix) {
                "start" -> R.drawable.widget_month_chip_teal_start
                "middle" -> R.drawable.widget_month_chip_teal_middle
                "end" -> R.drawable.widget_month_chip_teal_end
                "fade_start" -> R.drawable.widget_month_chip_teal_fade_start
                "fade_end" -> R.drawable.widget_month_chip_teal_fade_end
                else -> backgroundRes
            }
            R.drawable.widget_month_chip_blue -> when (suffix) {
                "start" -> R.drawable.widget_month_chip_blue_start
                "middle" -> R.drawable.widget_month_chip_blue_middle
                "end" -> R.drawable.widget_month_chip_blue_end
                "fade_start" -> R.drawable.widget_month_chip_blue_fade_start
                "fade_end" -> R.drawable.widget_month_chip_blue_fade_end
                else -> backgroundRes
            }
            R.drawable.widget_month_chip_purple -> when (suffix) {
                "start" -> R.drawable.widget_month_chip_purple_start
                "middle" -> R.drawable.widget_month_chip_purple_middle
                "end" -> R.drawable.widget_month_chip_purple_end
                "fade_start" -> R.drawable.widget_month_chip_purple_fade_start
                "fade_end" -> R.drawable.widget_month_chip_purple_fade_end
                else -> backgroundRes
            }
            R.drawable.widget_month_chip_pink -> when (suffix) {
                "start" -> R.drawable.widget_month_chip_pink_start
                "middle" -> R.drawable.widget_month_chip_pink_middle
                "end" -> R.drawable.widget_month_chip_pink_end
                "fade_start" -> R.drawable.widget_month_chip_pink_fade_start
                "fade_end" -> R.drawable.widget_month_chip_pink_fade_end
                else -> backgroundRes
            }
            R.drawable.widget_month_chip_neutral_light -> when (suffix) {
                "start" -> R.drawable.widget_month_chip_neutral_light_start
                "middle" -> R.drawable.widget_month_chip_neutral_light_middle
                "end" -> R.drawable.widget_month_chip_neutral_light_end
                "fade_start" -> R.drawable.widget_month_chip_neutral_light_fade_start
                "fade_end" -> R.drawable.widget_month_chip_neutral_light_fade_end
                else -> backgroundRes
            }
            R.drawable.widget_month_chip_neutral_dark -> when (suffix) {
                "start" -> R.drawable.widget_month_chip_neutral_dark_start
                "middle" -> R.drawable.widget_month_chip_neutral_dark_middle
                "end" -> R.drawable.widget_month_chip_neutral_dark_end
                "fade_start" -> R.drawable.widget_month_chip_neutral_dark_fade_start
                "fade_end" -> R.drawable.widget_month_chip_neutral_dark_fade_end
                else -> backgroundRes
            }
            else -> backgroundRes
        }
    }
}

internal val KgsWidgetKind.usesCollectionList: Boolean
    get() = this != KgsWidgetKind.Month && this != KgsWidgetKind.Day

internal fun KgsWidgetKind.emptyText(context: Context): String = when (this) {
    KgsWidgetKind.Tasks -> context.getString(R.string.no_scheduled_open_tasks)
    else -> context.getString(R.string.no_events_or_tasks)
}

internal fun WidgetTaskDisplayMode.widgetLabel(context: Context): String = when (this) {
    WidgetTaskDisplayMode.Planned -> context.getString(R.string.planned_tasks)
    WidgetTaskDisplayMode.Unplanned -> context.getString(R.string.unplanned_tasks)
    WidgetTaskDisplayMode.Today -> context.getString(R.string.tasks_for_today)
}

internal fun WidgetTaskSortMode.widgetLabel(context: Context): String = when (this) {
    WidgetTaskSortMode.Date -> context.getString(R.string.date)
    WidgetTaskSortMode.Priority -> context.getString(R.string.priority)
    WidgetTaskSortMode.Status -> context.getString(R.string.status)
}

internal fun WidgetTaskSortMode.next(): WidgetTaskSortMode =
    WidgetTaskSortMode.entries[(ordinal + 1) % WidgetTaskSortMode.entries.size]

internal fun taskPriorityIntensity(priority: Int?): Float {
    val value = priority?.coerceIn(1, 9) ?: 9
    return ((9 - value) / 8f).coerceIn(0f, 1f)
}

internal fun priorityMotionFrameIntervalMillis(priority: Int?, intensity: Float, frameCount: Int): Int {
    val cycleMillis = (1050 - (intensity * 420f)).roundToInt().coerceAtLeast(520)
    return if (priority == 1) {
        42
    } else {
        ((cycleMillis * 2f) / frameCount.toFloat())
            .roundToInt()
            .coerceAtLeast(48)
    }
}

internal fun ellipsizeForPaint(text: String, paint: Paint, maxWidth: Float): String {
    if (maxWidth <= 0f || text.isEmpty()) return ""
    if (paint.measureText(text) <= maxWidth) return text
    val ellipsis = "\u2026"
    val ellipsisWidth = paint.measureText(ellipsis)
    if (ellipsisWidth >= maxWidth) return ""
    var low = 0
    var high = text.length
    while (low < high) {
        val mid = (low + high + 1) / 2
        if (paint.measureText(text, 0, mid) + ellipsisWidth <= maxWidth) {
            low = mid
        } else {
            high = mid - 1
        }
    }
    return text.take(low).trimEnd() + ellipsis
}

internal fun shouldHideWidgetTitle(
    widthDp: Number,
    title: String,
    reservedDp: Float,
    textSp: Float,
): Boolean {
    val available = (widthDp.toFloat() - reservedDp).coerceAtLeast(0f)
    if (available < 28f) return true
    val estimatedTitleWidth = title.length * textSp * 0.54f
    if (estimatedTitleWidth <= 0f) return false
    return available / estimatedTitleWidth < 0.38f
}

internal fun motionStandardEasing(fraction: Float): Float =
    cubicBezierEasing(fraction, x1 = 0.2f, y1 = 0f, x2 = 0f, y2 = 1f)

internal fun lerpFloat(start: Float, end: Float, fraction: Float): Float =
    start + (end - start) * fraction.coerceIn(0f, 1f)

private fun cubicBezierEasing(fraction: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
    val target = fraction.coerceIn(0f, 1f)
    var low = 0f
    var high = 1f
    repeat(14) {
        val mid = (low + high) / 2f
        if (cubicBezierCoordinate(mid, x1, x2) < target) {
            low = mid
        } else {
            high = mid
        }
    }
    return cubicBezierCoordinate((low + high) / 2f, y1, y2)
}

private fun cubicBezierCoordinate(t: Float, p1: Float, p2: Float): Float {
    val inverse = 1f - t
    return 3f * inverse * inverse * t * p1 + 3f * inverse * t * t * p2 + t * t * t
}

private fun TaskEntity.widgetTaskDate(zoneId: ZoneId = ZoneId.systemDefault()): LocalDate? =
    (startAtMillis ?: dueAtMillis)?.let { Instant.ofEpochMilli(it).atZone(zoneId).toLocalDate() }

private fun TaskEntity.effectiveStatus(): String =
    status?.uppercase(Locale.ROOT) ?: if (isCompleted) "COMPLETED" else "NEEDS-ACTION"

private fun TaskEntity.widgetStatusGlyph(): String = when (effectiveStatus()) {
    "COMPLETED" -> "\u2713"
    "IN-PROCESS" -> "\u25D0"
    "CANCELLED" -> "\u00D7"
    else -> "\u25CB"
}

private fun TaskEntity.isWidgetActiveTask(): Boolean =
    when (effectiveStatus()) {
        "COMPLETED", "CANCELLED" -> false
        else -> !isCompleted
    }

private fun TaskEntity.statusSortRank(): Int = when (effectiveStatus()) {
    "IN-PROCESS" -> 0
    "NEEDS-ACTION" -> 1
    "COMPLETED" -> 2
    "CANCELLED" -> 3
    else -> 4
}

internal fun AppLanguageMode.toLocale(context: Context): Locale =
    localeTag?.let(Locale::forLanguageTag) ?: context.resources.configuration.locales[0] ?: Locale.getDefault()

internal fun Context.withWidgetLocale(locale: Locale): Context {
    val configuration = Configuration(resources.configuration)
    configuration.setLocale(locale)
    return createConfigurationContext(configuration)
}

private fun WidgetThemeMode.resolve(appThemeMode: AppThemeMode): AppThemeMode = when (this) {
    WidgetThemeMode.FollowApp -> appThemeMode
    WidgetThemeMode.KgsBlue -> AppThemeMode.KgsBlue
    WidgetThemeMode.KgsWarm -> AppThemeMode.KgsWarm
    WidgetThemeMode.KgsFresh -> AppThemeMode.KgsFresh
    WidgetThemeMode.SystemDynamic -> AppThemeMode.SystemDynamic
}

private fun WidgetColorMode.resolve(appColorMode: AppColorMode): AppColorMode = when (this) {
    WidgetColorMode.FollowApp -> appColorMode
    WidgetColorMode.FollowOs -> AppColorMode.Auto
    WidgetColorMode.Light -> AppColorMode.Light
    WidgetColorMode.Dark -> AppColorMode.Dark
}

internal fun WidgetTaskSubtaskDefaultMode.resolveSubtasksExpandedByDefault(appDefault: Boolean): Boolean = when (this) {
    WidgetTaskSubtaskDefaultMode.FollowApp -> appDefault
    WidgetTaskSubtaskDefaultMode.Open -> true
    WidgetTaskSubtaskDefaultMode.Closed -> false
}

internal fun EventEntity.displayColor(): Int = manualColor ?: color

internal fun TaskEntity.displayColor(mode: TaskColorMode): Int =
    manualColor ?: when (mode) {
        TaskColorMode.Priority -> priority?.let(::priorityColor) ?: color
        TaskColorMode.Collection -> color
    }

internal fun EventEntity.monthOccurrenceKey(): String =
    "${resourceHref.ifBlank { uid }}:$startsAtMillis"

internal fun EventEntity.endDateInclusive(zoneId: ZoneId = ZoneId.systemDefault()): LocalDate =
    Instant.ofEpochMilli((endsAtMillis - 1).coerceAtLeast(startsAtMillis)).atZone(zoneId).toLocalDate()

private fun priorityColor(priority: Int): Int = when (priority.coerceIn(1, 9)) {
    1 -> 0xFFD93025.toInt()
    2 -> 0xFFE7602A.toInt()
    3 -> 0xFFF29900.toInt()
    4 -> 0xFFF8C542.toInt()
    5 -> 0xFFFFD84D.toInt()
    6 -> 0xFF55A8F5.toInt()
    7 -> 0xFF2E8FD8.toInt()
    8 -> 0xFF20A386.toInt()
    else -> 0xFF2E7D32.toInt()
}

internal fun Int.monthChipStyle(): WidgetMonthChipStyle {
    val r = (this shr 16) and 0xFF
    val g = (this shr 8) and 0xFF
    val b = this and 0xFF
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val saturation = if (max == 0) 0f else (max - min).toFloat() / max.toFloat()
    val value = max / 255f
    val fillColor = this or 0xFF000000.toInt()
    val textColor = if (fillColor.isDarkColor()) 0xFFFFFFFF.toInt() else 0xFF1C1A18.toInt()
    if (saturation < 0.14f) {
        return if (value >= 0.58f) {
            WidgetMonthChipStyle(R.drawable.widget_month_chip_neutral_light, fillColor, textColor)
        } else {
            WidgetMonthChipStyle(R.drawable.widget_month_chip_neutral_dark, fillColor, textColor)
        }
    }

    val hue = rgbHue(r, g, b, max, min)
    return when {
        hue < 15f -> WidgetMonthChipStyle(R.drawable.widget_month_chip_red, fillColor, textColor)
        hue < 45f -> WidgetMonthChipStyle(R.drawable.widget_month_chip_orange, fillColor, textColor)
        hue < 75f -> WidgetMonthChipStyle(R.drawable.widget_month_chip_yellow, fillColor, textColor)
        hue < 155f -> WidgetMonthChipStyle(R.drawable.widget_month_chip_green, fillColor, textColor)
        hue < 185f -> WidgetMonthChipStyle(R.drawable.widget_month_chip_teal, fillColor, textColor)
        hue < 235f -> WidgetMonthChipStyle(R.drawable.widget_month_chip_blue, fillColor, textColor)
        hue < 285f -> WidgetMonthChipStyle(R.drawable.widget_month_chip_purple, fillColor, textColor)
        hue < 345f -> WidgetMonthChipStyle(R.drawable.widget_month_chip_pink, fillColor, textColor)
        else -> WidgetMonthChipStyle(R.drawable.widget_month_chip_red, fillColor, textColor)
    }
}

private fun rgbHue(r: Int, g: Int, b: Int, max: Int, min: Int): Float {
    if (max == min) return 0f
    val delta = (max - min).toFloat()
    val hue = when (max) {
        r -> 60f * (((g - b) / delta) % 6f)
        g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }
    return if (hue < 0f) hue + 360f else hue
}

private fun EventEntity.visibleDates(start: LocalDate, endExclusive: LocalDate, zoneId: ZoneId = ZoneId.systemDefault()): List<LocalDate> {
    val first = startsAtMillis.toDate(zoneId)
    val last = endDateInclusive(zoneId)
    val from = if (first.isBefore(start)) start else first
    val to = if (!last.isBefore(endExclusive)) endExclusive.minusDays(1) else last
    if (to.isBefore(from)) return emptyList()
    val days = ChronoUnit.DAYS.between(from, to).toInt()
    return (0..days).map { from.plusDays(it.toLong()) }
}

private fun TaskEntity.visibleDates(start: LocalDate, endExclusive: LocalDate, zoneId: ZoneId = ZoneId.systemDefault()): List<LocalDate> {
    val first = startAtMillis?.toDate(zoneId) ?: dueAtMillis?.toDate(zoneId) ?: return emptyList()
    val last = (dueAtMillis?.toDate(zoneId) ?: startAtMillis?.toDate(zoneId) ?: first).coerceAtLeast(first)
    val from = if (first.isBefore(start)) start else first
    val to = if (!last.isBefore(endExclusive)) endExclusive.minusDays(1) else last
    if (to.isBefore(from)) return emptyList()
    val days = ChronoUnit.DAYS.between(from, to).toInt()
    return (0..days).map { from.plusDays(it.toLong()) }
}

private fun EventEntity.widgetTimedPlacementOn(day: LocalDate, zoneId: ZoneId = ZoneId.systemDefault()): Pair<Int, Int>? {
    if (allDay || isWidgetTimedMultiDayMiddleOn(day, zoneId)) return null
    val visibleStart = day.atTime(WIDGET_DAY_START_HOUR, 0).atZone(zoneId).toInstant().toEpochMilli()
    val visibleEnd = day.atTime(WIDGET_DAY_END_HOUR, 0).plusHours(1).atZone(zoneId).toInstant().toEpochMilli()
    val overlapStart = max(startsAtMillis, visibleStart)
    val overlapEnd = min(endsAtMillis, visibleEnd)
    if (overlapEnd <= overlapStart) return null
    val startMinute = ((overlapStart - visibleStart) / 60_000.0).roundToInt().coerceIn(0, 24 * 60 - 1)
    val duration = max(1, ((overlapEnd - overlapStart) / 60_000.0).roundToInt())
    return startMinute to (startMinute + duration).coerceIn(startMinute + 1, 24 * 60)
}

private fun TaskEntity.widgetTimedPlacementOn(day: LocalDate, zoneId: ZoneId = ZoneId.systemDefault()): Pair<Int, Int>? {
    val startTimed = startAtMillis?.takeIf { startHasTime }
    val dueTimed = dueAtMillis?.takeIf { dueHasTime }
    if (startTimed == null && dueTimed == null) return null
    val start = startTimed ?: (dueTimed!! - WIDGET_DAY_DEFAULT_TASK_DURATION_MILLIS)
    val end = when {
        startTimed != null && dueTimed != null && dueTimed > startTimed -> dueTimed
        startTimed != null -> startTimed + WIDGET_DAY_DEFAULT_TASK_DURATION_MILLIS
        else -> dueTimed!!
    }
    val visibleStart = day.atTime(WIDGET_DAY_START_HOUR, 0).atZone(zoneId).toInstant().toEpochMilli()
    val visibleEnd = day.atTime(WIDGET_DAY_END_HOUR, 0).plusHours(1).atZone(zoneId).toInstant().toEpochMilli()
    val overlapStart = max(start, visibleStart)
    val overlapEnd = min(max(end, start + WIDGET_DAY_DEFAULT_TASK_DURATION_MILLIS), visibleEnd)
    if (overlapEnd <= overlapStart) return null
    val startMinute = ((overlapStart - visibleStart) / 60_000.0).roundToInt().coerceIn(0, 24 * 60 - 1)
    val duration = max(1, ((overlapEnd - overlapStart) / 60_000.0).roundToInt())
    return startMinute to (startMinute + duration).coerceIn(startMinute + 1, 24 * 60)
}

private fun EventEntity.isWidgetAllDayTopItemOn(day: LocalDate, zoneId: ZoneId = ZoneId.systemDefault()): Boolean =
    if (allDay) day in visibleDates(day, day.plusDays(1), zoneId) else isWidgetTimedMultiDayMiddleOn(day, zoneId)

private fun EventEntity.isWidgetTimedMultiDayMiddleOn(day: LocalDate, zoneId: ZoneId = ZoneId.systemDefault()): Boolean {
    if (allDay) return false
    val start = startsAtMillis.toDate(zoneId)
    val end = endDateInclusive(zoneId)
    return start.isBefore(end) && day.isAfter(start) && day.isBefore(end)
}

private fun TaskEntity.isWidgetFullDayTaskOn(day: LocalDate, zoneId: ZoneId = ZoneId.systemDefault()): Boolean {
    if (startAtMillis == null && dueAtMillis == null) return false
    if ((startAtMillis != null && startHasTime) || (dueAtMillis != null && dueHasTime)) return false
    return day in visibleDates(day, day.plusDays(1), zoneId)
}

internal fun Pair<Int, Int>.timeRangeText(): String =
    "${first.minuteOfDayText()}-${second.minuteOfDayText()}"

private fun Int.minuteOfDayText(): String {
    val bounded = coerceIn(0, 24 * 60)
    val hour = (bounded / 60).coerceAtMost(24)
    val minute = bounded % 60
    return "%02d:%02d".format(hour, minute)
}

internal fun Long.toDate(zoneId: ZoneId = ZoneId.systemDefault()): LocalDate =
    Instant.ofEpochMilli(this).atZone(zoneId).toLocalDate()

private fun Long.toTimeText(zoneId: ZoneId = ZoneId.systemDefault()): String =
    Instant.ofEpochMilli(this).atZone(zoneId).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))

internal fun dayPendingIntentRequestCode(appWidgetId: Int, day: LocalDate): Int =
    appWidgetId * 10_000 + day.year % 100 * 400 + day.dayOfYear

private fun DayOfWeek.plus(days: Long): DayOfWeek =
    DayOfWeek.of(((value - 1 + days.toInt()) % 7) + 1)

internal fun Int.isDarkColor(): Boolean {
    val r = (this shr 16) and 0xFF
    val g = (this shr 8) and 0xFF
    val b = this and 0xFF
    return (0.299 * r + 0.587 * g + 0.114 * b) < 140
}

internal fun Int.withAlpha(alpha: Float): Int =
    (((alpha.coerceIn(0f, 1f) * 255).roundToInt() and 0xFF) shl 24) or (this and 0x00FFFFFF)

internal fun Int.blendWith(other: Int, fraction: Float): Int {
    val clamped = fraction.coerceIn(0f, 1f)
    val inverse = 1f - clamped
    val a = (((this ushr 24) and 0xFF) * inverse + ((other ushr 24) and 0xFF) * clamped).roundToInt()
    val r = (((this ushr 16) and 0xFF) * inverse + ((other ushr 16) and 0xFF) * clamped).roundToInt()
    val g = (((this ushr 8) and 0xFF) * inverse + ((other ushr 8) and 0xFF) * clamped).roundToInt()
    val b = ((this and 0xFF) * inverse + (other and 0xFF) * clamped).roundToInt()
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}

private fun Int.greyedOut(amount: Float): Int {
    val mix = amount.coerceIn(0f, 1f)
    val a = (this ushr 24) and 0xFF
    val r = (this ushr 16) and 0xFF
    val g = (this ushr 8) and 0xFF
    val b = this and 0xFF
    val gray = (r * 0.299f + g * 0.587f + b * 0.114f).roundToInt().coerceIn(0, 255)
    val nextR = (r + (gray - r) * mix).roundToInt().coerceIn(0, 255)
    val nextG = (g + (gray - g) * mix).roundToInt().coerceIn(0, 255)
    val nextB = (b + (gray - b) * mix).roundToInt().coerceIn(0, 255)
    return (a shl 24) or (nextR shl 16) or (nextG shl 8) or nextB
}

internal fun Context.dpToPx(value: Int): Int =
    (value * resources.displayMetrics.density).roundToInt()

internal fun Context.dpToPx(value: Float): Float =
    value * resources.displayMetrics.density

private fun Context.systemAccentColor(dark: Boolean): Int {
    val names = if (dark) {
        listOf("system_accent1_200", "system_accent1_300")
    } else {
        listOf("system_accent1_600", "system_accent1_500")
    }
    names.forEach { name ->
        val id = resources.getIdentifier(name, "color", "android")
        if (id != 0) {
            val color = runCatching { getColor(id) }.getOrNull()
            if (color != null) return color
        }
    }
    return if (dark) 0xFF9BCAFF.toInt() else 0xFF2563A8.toInt()
}
