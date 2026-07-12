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
internal fun CompletionBurst(playKey: Int, color: Color, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(1f) }
    LaunchedEffect(playKey) {
        if (playKey > 0) {
            progress.snapTo(0f)
            progress.animateTo(1f, animationSpec = tween(MotionLong, easing = MotionStandard))
        }
    }
    if (playKey <= 0 || progress.value >= 1f) return
    Canvas(modifier.fillMaxSize()) {
        val p = progress.value
        val alpha = (1f - p).coerceIn(0f, 1f)
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(color.copy(alpha = 0.22f * alpha), radius = size.minDimension * (0.34f + p * 0.75f), center = center)
        repeat(10) { index ->
            val angle = (Math.PI * 2.0 * index / 10.0).toFloat()
            val distance = size.minDimension * (0.18f + p * 0.62f)
            val particleCenter = Offset(
                x = center.x + kotlin.math.cos(angle) * distance,
                y = center.y + kotlin.math.sin(angle) * distance,
            )
            drawCircle(
                color = if (index % 2 == 0) DraftAccent.copy(alpha = alpha) else color.copy(alpha = alpha),
                radius = size.minDimension * (0.08f * (1f - p * 0.45f)),
                center = particleCenter,
            )
        }
    }
}
@Composable
internal fun TaskCardCompletionBurst(playKey: Int, color: Color, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(1f) }
    LaunchedEffect(playKey) {
        if (playKey > 0) {
            progress.snapTo(0f)
            progress.animateTo(1f, animationSpec = tween(620, easing = MotionEmphasized))
        }
    }
    if (playKey <= 0 || progress.value >= 1f) return
    Canvas(modifier.fillMaxSize()) {
        val p = progress.value
        val alpha = (1f - p).coerceIn(0f, 1f)
        val center = Offset(size.width / 2f, size.height / 2f)
        val glowRadius = max(size.width, size.height) * (0.18f + p * 0.82f)
        drawRoundRect(
            color = color.copy(alpha = 0.22f * alpha),
            topLeft = Offset(-size.width * 0.08f * p, -size.height * 0.42f * p),
            size = Size(size.width * (1f + 0.16f * p), size.height * (1f + 0.84f * p)),
            cornerRadius = CornerRadius(16.dp.toPx() + p * 18.dp.toPx(), 16.dp.toPx() + p * 18.dp.toPx()),
        )
        drawCircle(color.copy(alpha = 0.18f * alpha), radius = glowRadius, center = center)
        repeat(18) { index ->
            val lane = index / 6f
            val baseX = size.width * ((index % 6) + 0.5f) / 6f
            val angle = (Math.PI * 2.0 * (index / 18.0) + lane * 0.55).toFloat()
            val distance = size.minDimension * (0.12f + p * (0.95f + lane * 0.12f))
            val particleCenter = Offset(
                x = baseX + kotlin.math.cos(angle) * distance,
                y = center.y + kotlin.math.sin(angle) * distance * 0.7f,
            )
            drawCircle(
                color = if (index % 3 == 0) DraftAccent.copy(alpha = alpha) else color.copy(alpha = alpha * 0.92f),
                radius = size.minDimension.coerceAtLeast(18f) * (0.045f + (1f - p) * 0.045f),
                center = particleCenter,
            )
        }
    }
}

@Composable
private fun OneDayList(state: CalendarUiState, onTaskStatusChanged: (String, String) -> Unit, onDetail: (DetailSheet) -> Unit) {
    AgendaDaySection(
        state.selectedDate,
        state.events.filter { it.occursOn(state.selectedDate) },
        state.datedTasks.filter { it.taskDate() == state.selectedDate },
        state.taskColorMode,
        state.subtasksExpandedByDefault,
        onTaskStatusChanged,
        onDetail,
    )
}

@Composable
internal fun AgendaList(
    state: CalendarUiState,
    onTaskStatusChanged: (String, String) -> Unit,
    onDetail: (DetailSheet) -> Unit,
    todayScrollRequest: Int,
    scrollTargetDate: LocalDate?,
) {
    val calendarTasks = remember(state.datedTasks, state.showCompletedTasksInCalendar) {
        if (state.showCompletedTasksInCalendar) state.datedTasks else state.datedTasks.filterNot { it.isCompleted }
    }
    SearchResultsList(
        query = "",
        eventResults = state.events,
        taskResults = calendarTasks,
        allTasksForHierarchy = emptyList(),
        taskColorMode = state.taskColorMode,
        subtasksExpandedByDefault = state.subtasksExpandedByDefault,
        onEventClick = { onDetail(DetailSheet.Event(it)) },
        onTaskClick = { onDetail(DetailSheet.Task(it)) },
        onTaskStatusChanged = onTaskStatusChanged,
        showSearchIntro = false,
        autoScrollToNow = true,
        emptyMessage = stringResource(R.string.no_events_or_tasks),
        scrollRequestKey = todayScrollRequest,
        scrollTargetDate = scrollTargetDate,
        stickyHeaderBackground = MaterialTheme.colorScheme.background,
        showTaskChains = false,
        expandMultiDayEventSpans = true,
    )
}

@Composable
private fun AgendaDaySection(
    date: LocalDate,
    events: List<EventEntity>,
    tasks: List<TaskEntity>,
    taskColorMode: TaskColorMode,
    subtasksExpandedByDefault: Boolean,
    onTaskStatusChanged: (String, String) -> Unit,
    onDetail: (DetailSheet) -> Unit,
) {
    val hierarchy = rememberTaskHierarchyPresentation(
        tasks.sortedBy { it.startAtMillis ?: it.dueAtMillis ?: Long.MAX_VALUE },
        expandedByDefault = subtasksExpandedByDefault,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        AgendaDateColumn(date)
        Column(
            modifier = Modifier
                .weight(1f)
                .graphicsLayer { clip = false },
        ) {
            events.sortedBy { it.startsAtMillis }.forEach { event ->
                AgendaEventCard(event) { onDetail(DetailSheet.Event(event)) }
            }
            hierarchy.entries.forEach { entry ->
                val task = entry.task
                AnimatedTaskHierarchyEntry(entry) {
                    TaskRow(
                        task = task,
                        taskColorMode = taskColorMode,
                        onTaskStatusChanged = onTaskStatusChanged,
                        prominent = true,
                        hierarchyDepth = entry.depth,
                        hierarchyContinuationLevels = entry.continuationLevels,
                        hierarchyLastSibling = entry.lastSibling,
                        hasSubtasks = entry.hasChildren,
                        subtasksExpanded = entry.expanded,
                        onToggleSubtasks = { hierarchy.toggle(task) },
                        outerHorizontalPadding = 0.dp,
                        outerVerticalPadding = 0.dp,
                    ) { onDetail(DetailSheet.Task(task)) }
                }
            }
        }
    }
}

@Composable
private fun AgendaDateColumn(date: LocalDate) {
    Column(
        modifier = Modifier.width(38.dp).padding(top = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            date.format(DateTimeFormatter.ofPattern("MMM", LocalAppLocale.current)).replace(".", ""),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            date.dayOfMonth.toString(),
            color = WarmInk,
            fontSize = 22.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun AgendaEventCard(event: EventEntity, onClick: () -> Unit) {
    // Render exactly like the 3-day / 1-day cards (cardVisuals with no muting): the old
    // muted=isPast greyed past events into washed-out cards with dark text in dark mode.
    val darkPalette = CurrentDarkPalette
    val visuals = remember(event.status, event.manualColor, event.color, darkPalette) {
        event.cardVisuals(darkPalette = darkPalette)
    }
    val attendees = remember(event.attendeesJson) { event.attendeesJson.toCalendarParticipants() }
    val eventTextStyle = tentativeReadableTextStyle(event.isTentative())
    val shape = RoundedCornerShape(12.dp)
    val pendingAlpha = pendingDeleteAlpha(event.resourceHref)
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = visuals.background),
        shape = shape,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(pendingAlpha)
            .then(
                when {
                    visuals.dashedBorder && visuals.borderColor != null -> Modifier.dashedBorder(visuals.borderColor, 12.dp)
                    visuals.borderColor != null -> Modifier.border(1.dp, visuals.borderColor, shape)
                    else -> Modifier
                },
            ),
    ) {
        Box(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp).padding(end = if (attendees.isNotEmpty()) 44.dp else 0.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    event.title,
                    color = visuals.contentColor,
                    fontSize = 14.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = visuals.textDecoration,
                    style = eventTextStyle,
                )
                Text(
                    event.localizedTimeLabel(),
                    color = visuals.contentColor.copy(alpha = 0.78f),
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = visuals.textDecoration,
                    style = eventTextStyle,
                )
                event.location?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it.cardLocationText(event.locationMapVerified),
                        color = visuals.contentColor.copy(alpha = 0.74f),
                        fontSize = 11.sp,
                        lineHeight = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textDecoration = visuals.textDecoration,
                        style = eventTextStyle,
                    )
                }
            }
            EventParticipantStack(
                attendees = attendees,
                eventColor = visuals.baseColor,
                contentColor = visuals.contentColor,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 7.dp).fillMaxWidth(0.58f),
                circleSize = 23.dp,
                maxVisible = 4,
            )
            PendingMutationBadge(
                resourceHref = event.resourceHref,
                modifier = Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-2).dp),
            )
        }
    }
}

@Composable
internal fun TaskInbox(
    state: CalendarUiState,
    onTaskStatusChanged: (String, String) -> Unit,
    onDetail: (DetailSheet) -> Unit,
    onShowCompleted: (() -> Unit)? = null,
) {
    var plannedSort by rememberSaveable { mutableStateOf(PlannedTaskSort.Date) }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val activeRootTasks = remember(state.taskHierarchyTasks) {
        state.taskHierarchyTasks.partitionByRootActivity().activeRootTasks
    }
    val activeSections = remember(activeRootTasks) { activeRootTasks.partitionByRootSchedule() }
    val openInboxTasks = activeSections.first
    val scheduledTasks = remember(activeSections.second, plannedSort) {
        when (plannedSort) {
            PlannedTaskSort.Priority -> activeSections.second.sortedWith(
                compareBy<TaskEntity> { it.priority ?: 9 }
                    .thenBy { it.startAtMillis ?: it.dueAtMillis ?: Long.MAX_VALUE }
                    .thenBy { it.title.lowercase(Locale.ROOT) },
            )
            PlannedTaskSort.Status -> activeSections.second.sortedWith(
                compareBy<TaskEntity> { it.statusSortRank() }
                    .thenBy { it.startAtMillis ?: it.dueAtMillis ?: Long.MAX_VALUE }
                    .thenBy { it.title.lowercase(Locale.ROOT) },
            )
            PlannedTaskSort.Date -> activeSections.second.sortedWith(
                compareBy<TaskEntity> { it.startAtMillis ?: it.dueAtMillis ?: Long.MAX_VALUE }
                    .thenBy { it.priority ?: 9 }
                    .thenBy { it.title.lowercase(Locale.ROOT) },
            )
        }
    }
    val showDateColumn = plannedSort == PlannedTaskSort.Date
    val inboxSectionTasks = openInboxTasks
    val scheduledSectionTasks = scheduledTasks
    val inboxHierarchy = rememberTaskHierarchyPresentation(inboxSectionTasks, state.subtasksExpandedByDefault)
    val scheduledHierarchy = rememberTaskHierarchyPresentation(scheduledSectionTasks, state.subtasksExpandedByDefault)
    Box(
        modifier = Modifier
            .fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 14.dp, bottom = navBottom + 18.dp)
                .graphicsLayer { clip = false },
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TaskInboxSectionHeader(
                title = stringResource(R.string.no_date),
                count = inboxSectionTasks.size,
            )
            if (inboxSectionTasks.isEmpty()) {
                Text(
                    stringResource(R.string.no_unscheduled_tasks),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(start = 22.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                )
            }
            Column(Modifier.animateContentSize(animationSpec = tween(MotionMedium, easing = MotionStandard))) {
            inboxHierarchy.entries.forEach { entry ->
                val task = entry.task
                AnimatedTaskHierarchyEntry(entry) {
                    TaskRow(
                        task = task,
                        taskColorMode = state.taskColorMode,
                        onTaskStatusChanged = onTaskStatusChanged,
                        prominent = true,
                        hierarchyDepth = entry.depth,
                        hierarchyContinuationLevels = entry.continuationLevels,
                        hierarchyLastSibling = entry.lastSibling,
                        hasSubtasks = entry.hasChildren,
                        subtasksExpanded = entry.expanded,
                        onToggleSubtasks = { inboxHierarchy.toggle(task) },
                        outerHorizontalPadding = 18.dp,
                        outerVerticalPadding = 3.dp,
                    ) { onDetail(DetailSheet.Task(task)) }
                }
            }
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = WarmLine.copy(alpha = 0.72f), modifier = Modifier.padding(horizontal = 4.dp))
            TaskInboxSectionHeader(
                title = stringResource(R.string.with_date),
                count = scheduledSectionTasks.size,
                actionLabel = plannedSort.localizedLabel(),
                actionIcon = Icons.Default.Sort,
                onActionClick = { plannedSort = plannedSort.next() },
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { clip = false },
            ) {
                if (scheduledSectionTasks.isEmpty()) {
                    Text(
                        stringResource(R.string.no_scheduled_open_tasks),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(start = 22.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                    )
                } else {
                    var lastDate: LocalDate? = null
                    scheduledHierarchy.entries.forEach { entry ->
                        val task = entry.task
                        val date = (task.startAtMillis ?: task.dueAtMillis)?.toDate()
                        val showDate = showDateColumn && entry.depth == 0 && date != lastDate
                        AnimatedTaskHierarchyEntry(entry) {
                            ScheduledTaskRow(
                                task = task,
                                showDateColumn = showDateColumn,
                                showDate = showDate,
                                date = date,
                                taskColorMode = state.taskColorMode,
                                onTaskStatusChanged = onTaskStatusChanged,
                                hierarchyDepth = entry.depth,
                                hierarchyContinuationLevels = entry.continuationLevels,
                                hierarchyLastSibling = entry.lastSibling,
                                hasSubtasks = entry.hasChildren,
                                subtasksExpanded = entry.expanded,
                                onToggleSubtasks = { scheduledHierarchy.toggle(task) },
                                onClick = { onDetail(DetailSheet.Task(task)) },
                            )
                        }
                        if (showDateColumn && entry.depth == 0) {
                            lastDate = date
                        }
                    }
                }
            }
            if (onShowCompleted != null) {
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = WarmLine.copy(alpha = 0.72f), modifier = Modifier.padding(horizontal = 4.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onShowCompleted),
                    shape = RoundedCornerShape(14.dp),
                    // Light accent fill in light mode; a proper dark elevated surface in dark mode
                    // (the old white-blended fill stayed light and clashed with the dark UI).
                    color = if (MaterialTheme.colorScheme.background.isDark()) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        WarmBrown.blendWith(Color.White, 0.82f)
                    },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = WarmBrown, modifier = Modifier.size(20.dp))
                        Text(
                            stringResource(R.string.completed_tasks),
                            color = WarmInk,
                            fontSize = 14.sp,
                            lineHeight = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = WarmBrown.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(18.dp)
                .background(
                    Brush.verticalGradient(
                        0f to MaterialTheme.colorScheme.background.copy(alpha = 0f),
                        1f to MaterialTheme.colorScheme.background,
                    ),
                ),
        )
    }
}

@Composable
internal fun CompletedTasksView(
    tasks: List<TaskEntity>,
    taskColorMode: TaskColorMode,
    subtasksExpandedByDefault: Boolean,
    onTaskStatusChanged: (String, String) -> Unit,
    onTaskClick: (TaskEntity) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(enabled = true) { onClose() }
    var query by rememberSaveable { mutableStateOf("") }
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val filtered = remember(tasks, query) {
        val q = query.trim().lowercase(Locale.ROOT)
        if (q.isEmpty()) tasks else tasks.filter {
            it.title.lowercase(Locale.ROOT).contains(q) ||
                (it.notes?.lowercase(Locale.ROOT)?.contains(q) == true) ||
                (it.categories?.lowercase(Locale.ROOT)?.contains(q) == true)
        }
    }
    val hierarchy = rememberTaskHierarchyPresentation(filtered, subtasksExpandedByDefault)
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = statusTop),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back), tint = WarmInk)
                }
                Text(
                    stringResource(R.string.completed_tasks),
                    color = WarmInk,
                    fontSize = 20.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.search_ellipsis)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )
            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (tasks.isEmpty()) stringResource(R.string.no_completed_tasks) else stringResource(R.string.no_matches),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .animateContentSize(animationSpec = tween(MotionMedium, easing = MotionStandard))
                        .padding(top = 6.dp, bottom = navBottom + 18.dp),
                ) {
                    hierarchy.entries.forEach { entry ->
                        val task = entry.task
                        AnimatedTaskHierarchyEntry(entry) {
                            TaskRow(
                                task = task,
                                taskColorMode = taskColorMode,
                                onTaskStatusChanged = onTaskStatusChanged,
                                prominent = true,
                                hierarchyDepth = entry.depth,
                                hierarchyContinuationLevels = entry.continuationLevels,
                                hierarchyLastSibling = entry.lastSibling,
                                hasSubtasks = entry.hasChildren,
                                subtasksExpanded = entry.expanded,
                                onToggleSubtasks = { hierarchy.toggle(task) },
                                outerHorizontalPadding = 16.dp,
                                outerVerticalPadding = 3.dp,
                                onClick = { onTaskClick(task) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskInboxSectionHeader(
    title: String,
    count: Int,
    actionLabel: String? = null,
    actionIcon: ImageVector? = null,
    onActionClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = WarmBrown,
            ) {
                Text(
                    count.toString(),
                    color = accentContainerContentColor(),
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
            Text(
                title,
                color = WarmInk,
                fontSize = 14.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (actionLabel != null && onActionClick != null) {
                val sortShape = RoundedCornerShape(999.dp)
                Surface(
                    modifier = Modifier
                        .width(118.dp)
                        .animateContentSize(animationSpec = tween(MotionMedium, easing = MotionStandard))
                        .clip(sortShape)
                        .clickable(onClick = onActionClick),
                    shape = sortShape,
                    color = WarmBrown,
                    border = null,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        if (actionIcon != null) {
                            Icon(actionIcon, contentDescription = null, tint = accentContainerContentColor(), modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(7.dp))
                        }
                        AnimatedContent(
                            targetState = actionLabel,
                            transitionSpec = { fadeIn(tween(MotionShort)) togetherWith fadeOut(tween(MotionShort)) },
                            label = "taskSortLabel",
                        ) { label ->
                            Text(
                                label,
                                color = accentContainerContentColor(),
                                fontSize = 12.sp,
                                lineHeight = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduledTaskRow(
    task: TaskEntity,
    showDateColumn: Boolean,
    showDate: Boolean,
    date: LocalDate?,
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { clip = false },
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Spacer(Modifier.width(16.dp))
        AnimatedVisibility(
            visible = showDateColumn,
            enter = slideInHorizontally(animationSpec = tween(MotionMedium, easing = MotionStandard)) { -it } +
                expandHorizontally(animationSpec = tween(MotionMedium, easing = MotionStandard), expandFrom = Alignment.Start) +
                fadeIn(animationSpec = tween(MotionShort, easing = MotionStandard)),
            exit = slideOutHorizontally(animationSpec = tween(MotionShort, easing = MotionStandardAccelerate)) { -it } +
                shrinkHorizontally(animationSpec = tween(MotionShort, easing = MotionStandardAccelerate), shrinkTowards = Alignment.Start) +
                fadeOut(animationSpec = tween(MotionShort, easing = MotionStandardAccelerate)),
        ) {
            MiniTaskDateColumn(
                date = date,
                visible = showDate,
                modifier = Modifier.padding(top = 7.dp),
            )
        }
        val rowHorizontalPadding by animateDpAsState(
            targetValue = 2.dp,
            animationSpec = tween(MotionMedium, easing = MotionStandard),
            label = "scheduledTaskRowHorizontalPadding",
        )
        val rowVerticalPadding by animateDpAsState(
            targetValue = 3.dp,
            animationSpec = tween(MotionMedium, easing = MotionStandard),
            label = "scheduledTaskRowVerticalPadding",
        )
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
            outerHorizontalPadding = rowHorizontalPadding,
            outerVerticalPadding = rowVerticalPadding,
            onClick = onClick,
        )
        Spacer(Modifier.width(16.dp))
    }
}

@Composable
private fun MiniTaskDateColumn(date: LocalDate?, visible: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.width(30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (visible) {
            if (date == null) {
                Text(stringResource(R.string.no_date_short_top), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp, lineHeight = 11.sp, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.no_date_short_bottom), color = WarmInk, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.SemiBold)
            } else {
                Text(
                    date.format(DateTimeFormatter.ofPattern("MMM", LocalAppLocale.current)).replace(".", ""),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    date.dayOfMonth.toString(),
                    color = WarmInk,
                    fontSize = 17.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun EventRow(event: EventEntity, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(Color(event.displayColor())))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(event.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                Text(event.localizedTimeLabel(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun TaskRow(
    task: TaskEntity,
    taskColorMode: TaskColorMode,
    onTaskStatusChanged: (String, String) -> Unit,
    prominent: Boolean = false,
    hierarchyDepth: Int = 0,
    hierarchyContinuationLevels: Set<Int> = emptySet(),
    hierarchyLastSibling: Boolean = true,
    hasSubtasks: Boolean = false,
    subtasksExpanded: Boolean = true,
    onToggleSubtasks: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    outerHorizontalPadding: Dp? = null,
    outerVerticalPadding: Dp? = null,
    connectorStemInset: Dp? = null,
    fixedCardHeight: Dp? = null,
    fadeTextAtBottom: Boolean = false,
    priorityMotionEnabled: Boolean = true,
    detailMorphKey: String? = null,
    detailMorphFromHeader: Boolean = false,
    onClick: () -> Unit,
) {
    val hierarchyLineColor = TaskHierarchyLine
    val detailMorphProgress = if (detailMorphFromHeader) rememberTaskDetailMorphProgress() else 1f
    val subtaskArrowRotation by animateFloatAsState(
        targetValue = if (subtasksExpanded) 0f else -90f,
        animationSpec = tween(MotionMedium, easing = MotionStandard),
        label = "subtaskArrowRotation",
    )
    var lastCompleted by remember(task.resourceHref) { mutableStateOf(task.isCompleted) }
    var burstKey by remember(task.resourceHref) { mutableStateOf(0) }
    LaunchedEffect(task.isCompleted) {
        if (task.isCompleted && !lastCompleted) burstKey++
        lastCompleted = task.isCompleted
    }
    val inactive = task.isInactive()
    val taskAlpha by animateFloatAsState(
        targetValue = if (inactive) 0.62f else 1f,
        animationSpec = tween(MotionMedium, easing = MotionStandard),
        label = "taskRowAlpha",
    )
    val cardColor = Color(task.displayColor(taskColorMode))
    val renderedCardColor by animateColorAsState(
        targetValue = if (inactive) {
            cardColor.blendWith(MaterialTheme.colorScheme.background, 0.48f).copy(alpha = 1f)
        } else {
            cardColor.copy(alpha = 1f)
        },
        animationSpec = tween(MotionMedium, easing = MotionStandard),
        label = "taskRowCardColor",
    )
    val baseContentColor = if (renderedCardColor.isDark()) Color.White else Color(0xFF1C1A18)
    val contentColor = baseContentColor.copy(alpha = taskAlpha)
    val pendingAlpha = pendingDeleteAlpha(task.resourceHref)
    val motionPadding = outerHorizontalPadding ?: if (prominent) 16.dp else 0.dp
    val verticalMotionPadding = outerVerticalPadding ?: if (prominent) 5.dp else 0.dp
    val hierarchyExitProgress = LocalTaskHierarchyExitProgress.current.coerceIn(0f, 1f)
    val renderedVerticalMotionPadding = verticalMotionPadding * (1f - hierarchyExitProgress)
    val hierarchyConnectorProgress = if (hierarchyExitProgress <= 0f) {
        1f
    } else {
        1f - phasedProgress(hierarchyExitProgress, 0f, 0.72f)
    }
    val hierarchyExitCardAlpha = if (hierarchyExitProgress < 0.82f) {
        1f
    } else {
        1f - phasedProgress(hierarchyExitProgress, 0.82f, 1f)
    }
    val cardRadius = if (prominent) 14.dp else 8.dp
    val renderedCardRadius = rememberTaskDetailMorphCornerRadius(
        enabled = detailMorphKey != null,
        ownRadius = cardRadius,
        counterpartRadius = 18.dp,
    )
    val resolvedConnectorStemInset = connectorStemInset ?: (motionPadding + TaskHierarchyStemInset)
    val parentTailTarget = if (hasSubtasks && subtasksExpanded) 1f else 0f
    val parentTailProgress by animateFloatAsState(
        targetValue = parentTailTarget,
        animationSpec = tween(
            durationMillis = if (parentTailTarget == 0f) MotionShort else MotionMedium,
            easing = if (parentTailTarget == 0f) MotionStandardAccelerate else MotionEmphasized,
        ),
        label = "taskHierarchyParentTailProgress",
    )
    val priorityMotionTask = task.takeIf { priorityMotionEnabled }
    val titleFontSize = if (detailMorphFromHeader) lerpFloat(22f, 13f, detailMorphProgress).sp else if (prominent) 13.sp else 14.sp
    val titleLineHeight = if (detailMorphFromHeader) lerpFloat(26f, 16f, detailMorphProgress).sp else if (prominent) 16.sp else 17.sp
    val subtitleFontSize = if (detailMorphFromHeader) lerpFloat(12f, 11f, detailMorphProgress).sp else if (prominent) 11.sp else 12.sp
    val subtitleLineHeight = if (detailMorphFromHeader) lerpFloat(14f, 14f, detailMorphProgress).sp else if (prominent) 14.sp else 15.sp
    val checkboxBoxSize = if (detailMorphFromHeader) lerpFloat(42f, 30f, detailMorphProgress).dp else if (prominent) 30.dp else 40.dp
    val checkboxIconSize = if (detailMorphFromHeader) lerpFloat(28f, 20f, detailMorphProgress).dp else if (prominent) 20.dp else 24.dp
    val cardContentHorizontalPadding = if (detailMorphFromHeader) lerpFloat(14f, 9f, detailMorphProgress).dp else if (prominent) 9.dp else 12.dp
    val cardContentTopPadding = if (detailMorphFromHeader) lerpFloat(12f, 7f, detailMorphProgress).dp else if (prominent) 7.dp else 10.dp
    val cardContentBottomPadding = when {
        detailMorphFromHeader -> lerpFloat(12f, if (fadeTextAtBottom) 0f else 7f, detailMorphProgress).dp
        fadeTextAtBottom -> 0.dp
        prominent -> 7.dp
        else -> 10.dp
    }
    val checkboxTextSpacing = if (detailMorphFromHeader) lerpFloat(10f, 6f, detailMorphProgress).dp else if (prominent) 6.dp else 8.dp
    val titleFontWeight = if (detailMorphFromHeader) {
        FontWeight(lerpFloat(600f, 500f, detailMorphProgress).roundToInt())
    } else if (prominent) {
        FontWeight.Medium
    } else {
        FontWeight.Normal
    }
    val subtitleText = if (detailMorphFromHeader && detailMorphProgress < 0.48f) {
        stringResource(if (task.isCompleted) R.string.completed_task else R.string.task)
    } else {
        task.localizedTaskTimeLabel()
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .zIndex(
                taskHierarchyLayerZ(hierarchyDepth) +
                    (priorityMotionTask?.let { taskPriorityLayerZ(it, PriorityAnimationsEnabled) } ?: 0f),
            )
            .graphicsLayer { clip = false },
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .zIndex(0f)
                .taskHierarchyConnector(
                    depth = hierarchyDepth,
                    continuationLevels = hierarchyContinuationLevels,
                    lastSibling = hierarchyLastSibling,
                    branchEndInset = motionPadding,
                    stemInset = resolvedConnectorStemInset,
                    progress = hierarchyConnectorProgress,
                    lineColor = hierarchyLineColor,
                )
                .taskHierarchyParentTail(
                    depth = hierarchyDepth,
                    stemInset = resolvedConnectorStemInset,
                    verticalPadding = renderedVerticalMotionPadding,
                    progress = parentTailProgress,
                    lineColor = hierarchyLineColor,
                ),
        )
        Card(
            onClick = onClick,
            shape = RoundedCornerShape(renderedCardRadius),
            colors = CardDefaults.cardColors(containerColor = renderedCardColor),
            modifier = Modifier
                .padding(start = (hierarchyDepth * 18).dp)
                .padding(horizontal = motionPadding, vertical = renderedVerticalMotionPadding)
                .fillMaxWidth()
                .then(fixedCardHeight?.let { Modifier.height(it) } ?: Modifier)
                .then(detailMorphKey?.let { Modifier.taskDetailMorph(it, renderedCardRadius) } ?: Modifier)
                .zIndex(1f)
                .alpha(pendingAlpha * hierarchyExitCardAlpha)
                .graphicsLayer { clip = false }
                .taskPriorityMotion(if (inactive || !priorityMotionEnabled) null else task.priority, cardColor),
        ) {
            Box(Modifier.fillMaxWidth()) {
                TaskCardCompletionBurst(burstKey, color = contentColor, modifier = Modifier.matchParentSize())
                Row(
                    Modifier
                        .then(if (fixedCardHeight != null) Modifier.fillMaxSize() else Modifier)
                        .padding(horizontal = cardContentHorizontalPadding)
                        .padding(
                            top = cardContentTopPadding,
                            bottom = cardContentBottomPadding,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TaskStatusCheckbox(
                        status = task.effectiveStatus(),
                        tint = contentColor,
                        onStatusChange = { onTaskStatusChanged(task.resourceHref, it) },
                        boxSize = checkboxBoxSize,
                        iconSize = checkboxIconSize,
                    )
                    Spacer(Modifier.width(checkboxTextSpacing))
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .then(
                                if (fadeTextAtBottom) {
                                    Modifier
                                        .fillMaxHeight()
                                        .bottomEdgeFadeMask(10.dp)
                                } else {
                                    Modifier
                                },
                            ),
                        verticalArrangement = Arrangement.spacedBy(1.dp),
                    ) {
                        if (fadeTextAtBottom) {
                            FadingTimedText(
                                text = task.title,
                                color = contentColor,
                                fontSize = titleFontSize,
                                lineHeight = titleLineHeight,
                                fontWeight = titleFontWeight,
                                maxLines = if (prominent) 2 else 1,
                            )
                            AnimatedContent(
                                targetState = subtitleText,
                                transitionSpec = {
                                    fadeIn(tween(110, easing = MotionStandard)) togetherWith
                                        fadeOut(tween(110, easing = MotionStandardAccelerate))
                                },
                                label = "taskRowMorphSubtitle",
                            ) { text ->
                                FadingTimedText(
                                    text = text,
                                    color = contentColor.copy(alpha = 0.74f),
                                    fontSize = subtitleFontSize,
                                    lineHeight = subtitleLineHeight,
                                )
                            }
                        } else {
                            Text(
                                task.title,
                                maxLines = if (prominent) 2 else 1,
                                overflow = TextOverflow.Ellipsis,
                                color = contentColor,
                                fontSize = titleFontSize,
                                lineHeight = titleLineHeight,
                                fontWeight = titleFontWeight,
                            )
                            AnimatedContent(
                                targetState = subtitleText,
                                transitionSpec = {
                                    fadeIn(tween(110, easing = MotionStandard)) togetherWith
                                        fadeOut(tween(110, easing = MotionStandardAccelerate))
                                },
                                label = "taskRowMorphSubtitle",
                            ) { text ->
                                Text(
                                    text,
                                    color = contentColor.copy(alpha = 0.74f),
                                    fontSize = subtitleFontSize,
                                    lineHeight = subtitleLineHeight,
                                )
                            }
                        }
                    }
                    if (detailMorphFromHeader) {
                        val targetActionWidth = if (hasSubtasks && onToggleSubtasks != null) 30f else 0f
                        val actionWidth = lerpFloat(96f, targetActionWidth, detailMorphProgress).dp
                        Box(
                            modifier = Modifier
                                .width(actionWidth)
                                .height(42.dp),
                            contentAlignment = Alignment.CenterEnd,
                        ) {
                            Row(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .alpha(1f - phasedProgress(detailMorphProgress, 0.08f, 0.62f)),
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = contentColor,
                                    modifier = Modifier.size(24.dp),
                                )
                                Spacer(Modifier.width(24.dp))
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = null,
                                    tint = contentColor,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                            if (hasSubtasks && onToggleSubtasks != null) {
                                IconButton(
                                    onClick = onToggleSubtasks,
                                    modifier = Modifier
                                        .size(30.dp)
                                        .alpha(phasedProgress(detailMorphProgress, 0.42f, 0.88f)),
                                ) {
                                    Icon(
                                        Icons.Default.KeyboardArrowDown,
                                        contentDescription = stringResource(
                                            if (subtasksExpanded) R.string.collapse_subtasks else R.string.expand_subtasks,
                                        ),
                                        tint = contentColor,
                                        modifier = Modifier
                                            .size(20.dp)
                                            .graphicsLayer { rotationZ = subtaskArrowRotation },
                                    )
                                }
                            }
                        }
                    } else if (hasSubtasks && onToggleSubtasks != null) {
                        IconButton(
                            onClick = onToggleSubtasks,
                            modifier = Modifier.size(if (prominent) 30.dp else 36.dp),
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = stringResource(
                                    if (subtasksExpanded) R.string.collapse_subtasks else R.string.expand_subtasks,
                                ),
                                tint = contentColor,
                                modifier = Modifier
                                    .size(20.dp)
                                    .graphicsLayer { rotationZ = subtaskArrowRotation },
                            )
                        }
                    }
                }
                PendingMutationBadge(
                    resourceHref = task.resourceHref,
                    modifier = Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-2).dp),
                )
            }
        }
    }
}
