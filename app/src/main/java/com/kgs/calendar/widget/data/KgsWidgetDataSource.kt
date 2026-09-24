package com.kgs.calendar.widget.data

import android.content.Context
import android.content.res.Configuration
import com.kgs.calendar.KgsCalendarApplication
import com.kgs.calendar.R
import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.data.settings.AppColorMode
import com.kgs.calendar.data.settings.AppThemeMode
import com.kgs.calendar.data.settings.TaskColorMode
import com.kgs.calendar.data.settings.WidgetColorMode
import com.kgs.calendar.data.settings.WidgetTaskDisplayMode
import com.kgs.calendar.data.settings.WidgetThemeMode
import com.kgs.calendar.domain.event.displayColor
import com.kgs.calendar.domain.event.endDateInclusive
import com.kgs.calendar.domain.event.isAllDayTopItemOn
import com.kgs.calendar.domain.event.isCancelled
import com.kgs.calendar.domain.model.TaskStatus
import com.kgs.calendar.domain.task.TaskParentLookup
import com.kgs.calendar.domain.task.displayColor
import com.kgs.calendar.domain.task.effectiveStatus
import com.kgs.calendar.domain.task.isOpen
import com.kgs.calendar.domain.task.taskStatus
import com.kgs.calendar.domain.task.treeParents
import com.kgs.calendar.domain.time.toDate
import com.kgs.calendar.domain.time.toTimeText
import com.kgs.calendar.widget.KgsWidgetKind
import com.kgs.calendar.widget.WIDGET_AGENDA_MAX_ROWS
import com.kgs.calendar.widget.WIDGET_DAY_RENDER_SIGNATURE_VERSION
import com.kgs.calendar.widget.WIDGET_TASK_MAX_DEPTH
import com.kgs.calendar.widget.model.WidgetDayItem
import com.kgs.calendar.widget.model.WidgetDayTimedItem
import com.kgs.calendar.widget.model.WidgetDayTimeline
import com.kgs.calendar.widget.model.WidgetListRow
import com.kgs.calendar.widget.model.WidgetRenderSettings
import com.kgs.calendar.widget.model.emptyText
import com.kgs.calendar.widget.model.layoutWidgetDayTimedItems
import com.kgs.calendar.widget.model.resolveSubtasksExpandedByDefault
import com.kgs.calendar.widget.state.KgsWidgetTaskExpansionState
import com.kgs.calendar.widget.toLocale
import com.kgs.calendar.widget.withWidgetLocale
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first

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
            .filterNot { it.isCancelled() }
        val tasks = graph.repository.datedTasksSnapshot(start, end)
            .filterNot { it.collectionHref in settings.hiddenCollectionHrefs }
            .filterNot { it.taskStatus == TaskStatus.Cancelled }
            .filter { settings.showCompletedTasks || !it.isCompleted }
        val allDayItems = buildList {
            events
                .filter { it.isAllDayTopItemOn(day, zoneId) }
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
            .filterNot { it.isCancelled() }
        val taskSnapshot = graph.repository.datedTasksSnapshot(startMillis, endMillis)
            .filterNot { it.collectionHref in settings.hiddenCollectionHrefs }
            .filterNot { it.taskStatus == TaskStatus.Cancelled }
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
        val activeTasks = allTasks.filter { it.isOpen() }
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
                if (child.isOpen()) {
                    included.putIfAbsent(child.resourceHref, child)
                }
            }
        }
        return included.values.toList()
    }

    private fun includeAncestorTasks(selectedTasks: List<TaskEntity>, allTasks: List<TaskEntity>): List<TaskEntity> {
        if (selectedTasks.isEmpty()) return emptyList()
        val parents = TaskParentLookup(allTasks)
        val included = LinkedHashMap<String, TaskEntity>()
        selectedTasks.forEach { task ->
            parents.selfAndAncestors(task).forEach { included.putIfAbsent(it.resourceHref, it) }
        }
        return included.values.toList()
    }

    private fun List<TaskEntity>.toTaskHierarchy(settings: WidgetRenderSettings, appWidgetId: Int): List<WidgetListRow> {
        if (isEmpty()) return emptyList()
        val distinctTasks = distinctBy { it.resourceHref }
        val comparator = settings.taskComparator()
        val parentByResource = distinctTasks.treeParents { it.resourceHref }
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
