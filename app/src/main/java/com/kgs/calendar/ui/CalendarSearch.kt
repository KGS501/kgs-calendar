@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.kgs.calendar.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Rect
import android.location.LocationManager
import android.net.Uri
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.util.TypedValue
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.core.view.WindowCompat
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kgs.calendar.R
import com.kgs.calendar.data.SourceType
import com.kgs.calendar.data.settings.AppColorMode
import com.kgs.calendar.data.settings.AppLanguageMode
import com.kgs.calendar.data.local.entity.AccountEntity
import com.kgs.calendar.data.local.entity.CalendarResourceEntity
import com.kgs.calendar.data.local.entity.CollectionEntity
import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.local.entity.PendingMutationEntity
import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.data.settings.AppThemeMode
import com.kgs.calendar.data.settings.SettingsStore
import com.kgs.calendar.data.settings.TaskColorMode
import com.kgs.calendar.data.settings.WidgetColorMode
import com.kgs.calendar.data.settings.WidgetTaskCreateMode
import com.kgs.calendar.data.settings.WidgetTaskDisplayMode
import com.kgs.calendar.data.settings.WidgetTaskSubtaskDefaultMode
import com.kgs.calendar.data.settings.WidgetThemeMode
import com.kgs.calendar.domain.model.CalendarViewMode
import com.kgs.calendar.domain.model.MAX_MULTI_DAY_COUNT
import com.kgs.calendar.domain.model.EventEditPayload
import com.kgs.calendar.domain.model.MAX_REMINDER_MINUTES
import com.kgs.calendar.domain.model.MIN_MULTI_DAY_COUNT
import com.kgs.calendar.domain.model.MutationAction
import com.kgs.calendar.domain.model.REMINDER_AT_END
import com.kgs.calendar.domain.model.REMINDER_AT_START
import com.kgs.calendar.domain.model.TaskEditPayload
import com.kgs.calendar.domain.model.coerceMultiDayCount
import com.kgs.calendar.domain.model.isMonthSurfaceTaskVisible
import com.kgs.calendar.domain.model.normalizedReminderOffsets
import com.kgs.calendar.ui.calendar.DayEndHour
import com.kgs.calendar.ui.calendar.DayPagerPageCount
import com.kgs.calendar.ui.calendar.DayStartHour
import com.kgs.calendar.ui.calendar.DefaultTaskDurationMillis as DEFAULT_TASK_DURATION_MILLIS
import com.kgs.calendar.ui.calendar.MonthStripPageCount
import com.kgs.calendar.ui.calendar.MonthViewBase
import com.kgs.calendar.ui.calendar.MonthViewPageCount
import com.kgs.calendar.ui.calendar.YearStripBase
import com.kgs.calendar.ui.calendar.YearStripPageCount
import com.kgs.calendar.ui.calendar.leadingDaysFrom
import com.kgs.calendar.ui.calendar.monthGridHeight
import com.kgs.calendar.ui.calendar.monthGridRowCount
import com.kgs.calendar.ui.calendar.overviewPanelHeight
import com.kgs.calendar.ui.calendar.shortMonthLabel
import com.kgs.calendar.ui.calendar.toDayDate
import com.kgs.calendar.ui.calendar.toDayPage
import com.kgs.calendar.ui.calendar.toMonth
import com.kgs.calendar.ui.calendar.toMonthPage
import com.kgs.calendar.ui.calendar.toMonthViewPage
import com.kgs.calendar.ui.calendar.weekHeaderLabels
import com.kgs.calendar.ui.editor.EditorSchedulePreview
import com.kgs.calendar.ui.editor.EditorScheduleState
import com.kgs.calendar.ui.labels.RecurrenceOption
import com.kgs.calendar.ui.labels.ReminderChoice
import com.kgs.calendar.ui.labels.ReminderUnit
import com.kgs.calendar.ui.labels.parseReminderMinutes
import com.kgs.calendar.ui.labels.recurrenceFrequency
import com.kgs.calendar.ui.labels.recurrencePart
import com.kgs.calendar.ui.labels.toIsoUntilDate
import com.kgs.calendar.ui.labels.toRecurrenceUntilValue
import com.kgs.calendar.ui.labels.toReminderAmountUnit
import com.kgs.calendar.ui.layout.AllDayContinuationSegment
import com.kgs.calendar.ui.layout.AllDayOverlayItem
import com.kgs.calendar.ui.layout.TimedCalendarItem
import com.kgs.calendar.ui.layout.TimedPlacement
import com.kgs.calendar.ui.layout.allDayCollapsedPageItemComparator
import com.kgs.calendar.ui.layout.allDayViewportPriorityTier
import com.kgs.calendar.ui.layout.buildCollapsedAllDayLayout
import com.kgs.calendar.ui.layout.layoutTimedItemsForDay
import com.kgs.calendar.ui.model.agendaSortMillis
import com.kgs.calendar.ui.model.allDayTopEndDate
import com.kgs.calendar.ui.model.allDayTopStartDate
import com.kgs.calendar.ui.model.isAllDayTopItemOn
import com.kgs.calendar.ui.model.isFullDayTaskOn
import com.kgs.calendar.ui.model.occurrenceStartForEdit
import com.kgs.calendar.ui.model.occursOn
import com.kgs.calendar.ui.model.taskDate
import com.kgs.calendar.ui.model.toDate
import com.kgs.calendar.ui.model.toTime
import com.kgs.calendar.ui.model.toTimeText
import com.kgs.calendar.ui.model.visibleAgendaDates
import com.kgs.calendar.ui.model.visibleDates
import com.kgs.calendar.ui.month.MonthRowOrderComparator
import com.kgs.calendar.ui.month.MonthRowOrderItem
import com.kgs.calendar.ui.theme.KgsCalendarTheme
import com.kgs.calendar.ui.theme.CalendarUiTokens
import com.kgs.calendar.ui.theme.LocalCalendarUiTokens
import com.kgs.calendar.ui.time.LocalCalendarTimeSnapshot
import com.kgs.calendar.ui.time.rememberCalendarTimeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.time.DayOfWeek
import java.time.Instant
import java.time.YearMonth
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.floor
import kotlin.random.Random
import kotlin.math.ln
import kotlin.math.tan

@Composable
internal fun CalendarSearchOverlay(
    visible: Boolean,
    query: String,
    results: List<EventEntity>,
    taskResults: List<TaskEntity>,
    allTasksForHierarchy: List<TaskEntity>,
    taskColorMode: TaskColorMode,
    subtasksExpandedByDefault: Boolean,
    onQueryChange: (String) -> Unit,
    onTaskStatusChanged: (String, String) -> Unit,
    onEventClick: (EventEntity) -> Unit,
    onTaskClick: (TaskEntity) -> Unit,
    onClose: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(animationSpec = tween(MotionLong, easing = MotionEmphasized)) { -it / 5 } +
            scaleIn(initialScale = 0.98f, animationSpec = tween(MotionLong, easing = MotionEmphasized)) +
            fadeIn(animationSpec = tween(MotionMedium, delayMillis = 50, easing = MotionStandard)),
        exit = slideOutVertically(animationSpec = tween(MotionMedium, easing = MotionStandardAccelerate)) { -it / 6 } +
            scaleOut(targetScale = 0.98f, animationSpec = tween(MotionMedium, easing = MotionStandardAccelerate)) +
            fadeOut(animationSpec = tween(MotionShort, easing = MotionStandardAccelerate)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
        ) {
            SearchTopBar(query = query, onQueryChange = onQueryChange, onClose = onClose)
            SearchResultsList(
                query = query,
                eventResults = results,
                taskResults = taskResults,
                allTasksForHierarchy = allTasksForHierarchy,
                taskColorMode = taskColorMode,
                subtasksExpandedByDefault = subtasksExpandedByDefault,
                onEventClick = onEventClick,
                onTaskClick = onTaskClick,
                onTaskStatusChanged = onTaskStatusChanged,
            )
        }
    }
}

@Composable
private fun SearchTopBar(query: String, onQueryChange: (String) -> Unit, onClose: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(statusTop + 62.dp)
            .background(MaterialTheme.colorScheme.background)
            .padding(start = 16.dp, end = 14.dp, top = statusTop),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconButton(onClick = onClose, modifier = Modifier.size(38.dp)) {
            Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back), tint = WarmInk, modifier = Modifier.size(25.dp))
        }
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.headlineSmall.copy(
                color = WarmInk,
                fontSize = 22.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.Normal,
            ),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTapGestures {
                        focused = true
                        focusRequester.requestFocus()
                    }
                }
                .focusRequester(focusRequester)
                .onFocusChanged { focused = it.isFocused },
            cursorBrush = SolidColor(WarmBrown),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isBlank() && !focused) {
                        Text(stringResource(R.string.search), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 22.sp, lineHeight = 26.sp)
                    }
                    innerTextField()
                }
            },
        )
        IconButton(
            modifier = Modifier.size(38.dp),
            onClick = {
            if (query.isBlank()) onClose() else onQueryChange("")
        }) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close), tint = WarmInk, modifier = Modifier.size(25.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SearchResultsList(
    query: String,
    eventResults: List<EventEntity>,
    taskResults: List<TaskEntity>,
    allTasksForHierarchy: List<TaskEntity> = taskResults,
    taskColorMode: TaskColorMode,
    subtasksExpandedByDefault: Boolean,
    onEventClick: (EventEntity) -> Unit,
    onTaskClick: (TaskEntity) -> Unit,
    onTaskStatusChanged: (String, String) -> Unit,
    showSearchIntro: Boolean = true,
    autoScrollToNow: Boolean = query.isNotBlank() || !showSearchIntro,
    emptyMessage: String? = null,
    scrollRequestKey: Int = 0,
    scrollTargetDate: LocalDate? = null,
    stickyHeaderBackground: Color = MaterialTheme.colorScheme.surface,
    showTaskChains: Boolean = true,
    expandMultiDayEventSpans: Boolean = false,
) {
    val resolvedEmptyMessage = emptyMessage ?: stringResource(R.string.no_results)
    val noDateLabel = stringResource(R.string.no_date)
    val hierarchySubset = remember(query, showSearchIntro, showTaskChains, taskResults, allTasksForHierarchy) {
        if (!showTaskChains) {
            TaskHierarchySubset(emptyList())
        } else {
            when {
                query.isNotBlank() -> allTasksForHierarchy.searchHierarchySubsetForMatches(taskResults)
                !showSearchIntro -> TaskHierarchySubset(
                    tasks = allTasksForHierarchy.chainSubsetForTargets(
                        targets = taskResults,
                        includeTargetDescendants = true,
                    ),
                )
                else -> TaskHierarchySubset(tasks = taskResults)
            }
        }
    }
    val hierarchyExpandedByDefault = when {
        query.isNotBlank() -> false
        !showSearchIntro -> true
        else -> subtasksExpandedByDefault
    }
    val taskHierarchy = rememberTaskHierarchyPresentation(
        tasks = hierarchySubset.tasks,
        expandedByDefault = hierarchyExpandedByDefault,
        defaultExpandedResourceHrefs = hierarchySubset.defaultExpandedResourceHrefs,
    )
    val taskGroups = remember(taskHierarchy.entries) {
        buildList {
            var current = mutableListOf<TaskHierarchyEntry>()
            taskHierarchy.entries.forEach { entry ->
                if (entry.depth == 0 && current.isNotEmpty()) {
                    add(CalendarSearchResult.TaskGroup(current.toList()))
                    current = mutableListOf()
                }
                current += entry
            }
            if (current.isNotEmpty()) add(CalendarSearchResult.TaskGroup(current.toList()))
        }
    }
    val flatTaskItems = remember(showTaskChains, taskResults) {
        if (showTaskChains) {
            emptyList()
        } else {
            taskResults
                .distinctBy { it.resourceHref }
                .filter { it.agendaSortMillis() != null }
                .map { CalendarSearchResult.TaskItem(it) }
        }
    }
    val eventItems = remember(eventResults, taskResults, expandMultiDayEventSpans) {
        if (expandMultiDayEventSpans) {
            buildAgendaEventResults(eventResults, taskResults)
        } else {
            eventResults.map { CalendarSearchResult.Event(it) }
        }
    }
    val results = remember(eventItems, taskGroups, flatTaskItems) {
        (eventItems + taskGroups + flatTaskItems)
            .sortedWith(
                compareBy<CalendarSearchResult> { it.sortMillis == null }
                    .thenBy { it.sortMillis ?: Long.MAX_VALUE },
            )
    }
    val groupedResults = remember(results) {
        results.groupBy { item -> item.date?.year?.toString() ?: noDateLabel }
    }
    val today = LocalCalendarTimeSnapshot.current.today
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val firstFutureKey = remember(results, today) {
        results.firstOrNull { it.isFutureSearchSection(today) }?.stableKey()
    }
    var initialAutoScrollHandled by remember { mutableStateOf(false) }
    var lastHandledScrollRequestKey by remember { mutableStateOf(scrollRequestKey) }
    val firstFutureLazyIndex = remember(groupedResults, firstFutureKey, today) {
        if (firstFutureKey == null) null else {
            var lazyIndex = if (showSearchIntro && query.isNotBlank()) 1 else 0
            var found: Int? = null
            var dividerInserted = false
            for (groupItems in groupedResults.values) {
                if (found != null) break
                lazyIndex++ // sticky year header
                for (item in groupItems) {
                    if (!dividerInserted && item.isFutureSearchSection(today)) {
                        lazyIndex++ // past/future divider
                        dividerInserted = true
                    }
                    if (item.stableKey() == firstFutureKey) {
                        found = lazyIndex
                        break
                    }
                    lazyIndex++
                }
            }
            found
        }
    }
    val requestedDateLazyIndex = remember(groupedResults, scrollTargetDate, showSearchIntro, query, today) {
        val targetDate = scrollTargetDate ?: return@remember null
        var lazyIndex = if (showSearchIntro && query.isNotBlank()) 1 else 0
        var found: Int? = null
        var dividerInserted = false
        for (groupItems in groupedResults.values) {
            if (found != null) break
            lazyIndex++ // sticky year header
            for (item in groupItems) {
                if (!dividerInserted && item.isFutureSearchSection(today)) {
                    lazyIndex++ // past/future divider
                    dividerInserted = true
                }
                val itemDate = item.date
                if (itemDate != null && !itemDate.isBefore(targetDate)) {
                    found = lazyIndex
                    break
                }
                lazyIndex++
            }
        }
        found
    }
    LaunchedEffect(query, firstFutureLazyIndex, requestedDateLazyIndex, scrollRequestKey) {
        if (autoScrollToNow) {
            val explicitAgendaRequest = !showSearchIntro && scrollRequestKey != lastHandledScrollRequestKey
            val requestedIndex = if (explicitAgendaRequest && requestedDateLazyIndex != null) requestedDateLazyIndex else null
            val baseTarget = requestedIndex ?: firstFutureLazyIndex
            if (baseTarget == null) return@LaunchedEffect
            val targetIndex = if (showSearchIntro) {
                (baseTarget - 2).coerceAtLeast(0)
            } else {
                (baseTarget - 2).coerceAtLeast(0)
            }
            val targetOffset = if (showSearchIntro) 0 else -with(density) { 38.dp.roundToPx() }
            if (initialAutoScrollHandled && explicitAgendaRequest) {
                listState.animateScrollToItem(targetIndex, targetOffset)
            } else {
                listState.scrollToItem(targetIndex, targetOffset)
            }
            initialAutoScrollHandled = true
            lastHandledScrollRequestKey = scrollRequestKey
        }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (query.isBlank() && showSearchIntro) {
            item {
                Text(stringResource(R.string.search_events_tasks), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, lineHeight = 18.sp, modifier = Modifier.padding(start = 62.dp, top = 18.dp))
            }
        } else {
            if (showSearchIntro && query.isNotBlank()) {
                item {
                    Text(
                        stringResource(R.string.search_may_miss_old_results),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(start = 62.dp, bottom = 8.dp),
                    )
                }
            }
            if (results.isEmpty()) {
                item {
                    Text(resolvedEmptyMessage, color = WarmInk, fontSize = 16.sp, lineHeight = 20.sp, modifier = Modifier.padding(start = 62.dp, top = 18.dp))
                }
            } else {
                var futureDividerInserted = false
                groupedResults.forEach { (group, groupItems) ->
                    stickyHeader(key = "search-year-$group") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(stickyHeaderBackground.copy(alpha = 0.97f))
                                .padding(start = 62.dp, top = 7.dp, bottom = 7.dp),
                        ) {
                            Text(
                                group,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 15.sp,
                                lineHeight = 19.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    var lastDateKey: String? = null
                    groupItems.forEach { item ->
                        val dateKey = item.date?.toString() ?: "__no_date__"
                        val showDate = dateKey != lastDateKey
                        if (!futureDividerInserted && item.isFutureSearchSection(today)) {
                            item(key = "search-past-future-divider") {
                                SearchPastFutureDivider()
                            }
                            futureDividerInserted = true
                        }
                        item(key = item.stableKey()) {
                        when (item) {
                            is CalendarSearchResult.Event -> SearchResultCard(
                                event = item.event,
                                displayDate = item.date,
                                spanEndDate = item.spanEndDate,
                                showDate = showDate,
                                onClick = { onEventClick(item.event) },
                            )
                            is CalendarSearchResult.TaskItem -> SearchTaskResultCard(
                                task = item.task,
                                showDate = showDate,
                                taskColorMode = taskColorMode,
                                onTaskStatusChanged = onTaskStatusChanged,
                                hierarchyDepth = 0,
                                hierarchyContinuationLevels = emptySet(),
                                hierarchyLastSibling = true,
                                hasSubtasks = false,
                                subtasksExpanded = false,
                                onToggleSubtasks = {},
                                onClick = { onTaskClick(item.task) },
                            )
                            is CalendarSearchResult.TaskGroup -> Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer { clip = false },
                            ) {
                                item.entries.forEach { entry ->
                                    AnimatedTaskHierarchyEntry(entry) {
                                        SearchTaskResultCard(
                                            task = entry.task,
                                            showDate = showDate,
                                            taskColorMode = taskColorMode,
                                            onTaskStatusChanged = onTaskStatusChanged,
                                            hierarchyDepth = entry.depth,
                                            hierarchyContinuationLevels = entry.continuationLevels,
                                            hierarchyLastSibling = entry.lastSibling,
                                            hasSubtasks = entry.hasChildren,
                                            subtasksExpanded = entry.expanded,
                                            onToggleSubtasks = { taskHierarchy.toggle(entry.task) },
                                            onClick = { onTaskClick(entry.task) },
                                        )
                                    }
                                }
                            }
                        }
                        }
                        lastDateKey = dateKey
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

private sealed interface CalendarSearchResult {
    val sortMillis: Long?
    val date: LocalDate?

    data class Event(
        val event: EventEntity,
        override val date: LocalDate = event.startsAtMillis.toDate(),
        val spanEndDate: LocalDate = date,
    ) : CalendarSearchResult {
        override val sortMillis: Long = if (date == event.startsAtMillis.toDate()) {
            event.startsAtMillis
        } else {
            date.startOfDayMillis()
        }
    }

    data class TaskGroup(val entries: List<TaskHierarchyEntry>) : CalendarSearchResult {
        private val root: TaskEntity = entries.first().task
        override val sortMillis: Long? = entries
            .mapNotNull { it.task.startAtMillis ?: it.task.dueAtMillis }
            .minOrNull()
            ?: root.startAtMillis
            ?: root.dueAtMillis
        override val date: LocalDate? = sortMillis?.toDate()
    }

    data class TaskItem(val task: TaskEntity) : CalendarSearchResult {
        override val sortMillis: Long? = task.agendaSortMillis()
        override val date: LocalDate? = sortMillis?.toDate()
    }
}

private fun CalendarSearchResult.stableKey(): String = when (this) {
    is CalendarSearchResult.Event -> "event-${event.resourceHref}-${event.startsAtMillis}-$date-$spanEndDate"
    is CalendarSearchResult.TaskGroup -> "task-group-${entries.first().task.resourceHref}"
    is CalendarSearchResult.TaskItem -> "task-${task.resourceHref}-${task.agendaSortMillis() ?: 0L}"
}

private fun CalendarSearchResult.isFutureSearchSection(today: LocalDate): Boolean =
    date?.isBefore(today) != true

private fun buildAgendaEventResults(
    events: List<EventEntity>,
    tasks: List<TaskEntity>,
): List<CalendarSearchResult.Event> {
    val taskDates = tasks
        .flatMap { it.visibleDates() }
        .toSet()
    val eventDatesByResource = events.associate { event ->
        event.resourceHref to event.visibleAgendaDates()
    }
    return events.flatMap { event ->
        val dates = eventDatesByResource[event.resourceHref].orEmpty()
        if (dates.size <= 1) {
            listOf(CalendarSearchResult.Event(event, dates.firstOrNull() ?: event.startsAtMillis.toDate()))
        } else {
            val interruptionDates = dates.filterTo(mutableSetOf()) { date ->
                date in taskDates || events.any { other ->
                    other.resourceHref != event.resourceHref && eventDatesByResource[other.resourceHref].orEmpty().contains(date)
                }
            }
            event.toAgendaSpanResults(dates, interruptionDates)
        }
    }
}

private fun EventEntity.toAgendaSpanResults(
    dates: List<LocalDate>,
    interruptionDates: Set<LocalDate>,
): List<CalendarSearchResult.Event> {
    val sortedDates = dates.sorted()
    if (sortedDates.isEmpty()) return emptyList()
    val results = mutableListOf<CalendarSearchResult.Event>()
    var segmentStart: LocalDate? = null
    var previous: LocalDate? = null

    fun flushSegment(end: LocalDate) {
        val start = segmentStart ?: return
        if (!end.isBefore(start)) {
            results += CalendarSearchResult.Event(this, start, end)
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
            results += CalendarSearchResult.Event(this, date, date)
        } else if (segmentStart == null) {
            segmentStart = date
        }
        previous = date
    }
    previous?.let(::flushSegment)
    return results
}

@Composable
private fun SearchPastFutureDivider() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalBleed(18.dp)
            .padding(top = 12.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            stringResource(R.string.past),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        HorizontalDivider(color = WarmLine)
        Text(
            stringResource(R.string.future),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SearchResultCard(
    event: EventEntity,
    showDate: Boolean,
    onClick: () -> Unit,
    displayDate: LocalDate = event.startsAtMillis.toDate(),
    spanEndDate: LocalDate = displayDate,
) {
    val isPast = event.endsAtMillis < System.currentTimeMillis()
    val isSpan = spanEndDate.isAfter(displayDate)
    val attendees = remember(event.attendeesJson) { event.attendeesJson.toCalendarParticipants() }
    val rowHeight = when {
        isSpan -> AgendaMultiDaySpanCardHeight
        attendees.isNotEmpty() -> 76.dp
        else -> 64.dp
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        val visuals = event.cardVisuals(muted = isPast, darkPalette = CurrentDarkPalette)
        SearchDateColumn(
            date = displayDate,
            muted = isPast,
            visible = showDate,
            endDate = spanEndDate.takeIf { isSpan },
            height = if (isSpan) rowHeight else null,
        )
        val eventTextStyle = tentativeReadableTextStyle(event.isTentative() && !isPast)
        val shape = RoundedCornerShape(13.dp)
        val pendingAlpha = pendingDeleteAlpha(event.resourceHref)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(rowHeight)
                .alpha(pendingAlpha)
                .clip(shape)
                .background(visuals.background)
                .then(
                    when {
                        visuals.dashedBorder && visuals.borderColor != null -> Modifier.dashedBorder(visuals.borderColor, 13.dp)
                        visuals.borderColor != null -> Modifier.border(1.dp, visuals.borderColor, shape)
                        else -> Modifier
                    },
                )
                .clickable(onClick = onClick)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .bottomEdgeFadeMask(10.dp)
                    .padding(start = 12.dp, top = 7.dp, end = 12.dp, bottom = 0.dp)
                    .padding(end = if (attendees.isNotEmpty()) 54.dp else 0.dp),
                verticalArrangement = Arrangement.Top,
            ) {
                FadingTimedText(event.title, color = visuals.contentColor, fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, textDecoration = visuals.textDecoration, shadow = eventTextStyle.shadow)
                FadingTimedText(
                    if (isSpan) event.localizedAgendaSpanLabel(displayDate, spanEndDate) else event.localizedTimeLabel(),
                    color = visuals.contentColor.copy(alpha = if (isPast) 0.72f else 0.92f),
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    maxLines = 1,
                    textDecoration = visuals.textDecoration,
                    shadow = eventTextStyle.shadow,
                )
                event.location?.takeIf { it.isNotBlank() }?.let {
                    FadingTimedText(it.cardLocationText(event.locationMapVerified), color = visuals.contentColor.copy(alpha = if (isPast) 0.66f else 0.86f), fontSize = 11.sp, lineHeight = 13.sp, maxLines = 1, textDecoration = visuals.textDecoration, shadow = eventTextStyle.shadow)
                }
            }
            EventParticipantStack(
                attendees = attendees,
                eventColor = visuals.baseColor,
                contentColor = visuals.contentColor,
                modifier = Modifier.align(Alignment.BottomEnd),
                circleSize = 24.dp,
                maxVisible = 5,
                muted = isPast,
            )
            PendingMutationBadge(
                resourceHref = event.resourceHref,
                modifier = Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-2).dp),
            )
        }
    }
}

@Composable
private fun SearchTaskResultCard(
    task: TaskEntity,
    showDate: Boolean,
    taskColorMode: TaskColorMode,
    onTaskStatusChanged: (String, String) -> Unit,
    hierarchyDepth: Int = 0,
    hierarchyContinuationLevels: Set<Int> = emptySet(),
    hierarchyLastSibling: Boolean = true,
    hasSubtasks: Boolean = false,
    subtasksExpanded: Boolean = true,
    onToggleSubtasks: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val taskDate = (task.startAtMillis ?: task.dueAtMillis)?.toDate()
    val isMuted = task.isInactive()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(taskHierarchyLayerZ(hierarchyDepth) + taskPriorityLayerZ(task, PriorityAnimationsEnabled))
            .graphicsLayer { clip = false },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (hierarchyDepth == 0) {
            SearchDateColumn(date = taskDate, muted = isMuted, visible = showDate)
        } else {
            Spacer(Modifier.width(50.dp))
        }
        TaskRow(
            task = task,
            taskColorMode = taskColorMode,
            onTaskStatusChanged = onTaskStatusChanged,
            prominent = true,
            hierarchyDepth = hierarchyDepth,
            hierarchyContinuationLevels = hierarchyContinuationLevels,
            hierarchyLastSibling = hierarchyLastSibling,
            hasSubtasks = hasSubtasks,
            subtasksExpanded = subtasksExpanded,
            onToggleSubtasks = onToggleSubtasks,
            modifier = Modifier.weight(1f),
            outerHorizontalPadding = 0.dp,
            outerVerticalPadding = 3.dp,
            connectorStemInset = TaskHierarchyStemInset,
            priorityMotionEnabled = !isMuted,
            onClick = onClick,
        )
    }
}

private val AgendaMultiDaySpanCardHeight = 96.dp

@Composable
private fun SearchDateColumn(
    date: LocalDate?,
    muted: Boolean,
    visible: Boolean = true,
    endDate: LocalDate? = null,
    height: Dp? = null,
) {
    Column(
        modifier = Modifier
            .width(50.dp)
            .then(if (height != null) Modifier.height(height) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (visible) {
            if (date == null) {
                Text(
                    appString(R.string.none),
                    color = WarmInk.copy(alpha = if (muted) 0.58f else 1f),
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    appString(R.string.date),
                    color = WarmInk.copy(alpha = if (muted) 0.58f else 1f),
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            } else {
                Text(
                    date.format(DateTimeFormatter.ofPattern("MMM", LocalAppLocale.current)).replace(".", ""),
                    color = WarmInk.copy(alpha = if (muted) 0.58f else 1f),
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    date.dayOfMonth.toString(),
                    color = WarmInk.copy(alpha = if (muted) 0.58f else 1f),
                    fontSize = 20.sp,
                    lineHeight = 23.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (endDate != null) {
                    Spacer(Modifier.height(3.dp))
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .weight(1f)
                            .clip(RoundedCornerShape(999.dp))
                            .background(WarmInk.copy(alpha = if (muted) 0.22f else 0.42f)),
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        endDate.format(DateTimeFormatter.ofPattern("MMM", LocalAppLocale.current)).replace(".", ""),
                        color = WarmInk.copy(alpha = if (muted) 0.58f else 1f),
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        endDate.dayOfMonth.toString(),
                        color = WarmInk.copy(alpha = if (muted) 0.58f else 1f),
                        fontSize = 17.sp,
                        lineHeight = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
