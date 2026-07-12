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
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
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
import com.kgs.calendar.ui.timeline.AllDayReservation
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


internal fun allDayReservationFor(
    date: LocalDate,
    events: List<EventEntity>,
    tasks: List<TaskEntity>,
    taskColorMode: TaskColorMode,
): AllDayReservation {
    val page = date.toDayPage()
    val occupiedLanes = buildAllDayOverlayItems(
        events = events,
        tasks = tasks,
        taskColorMode = taskColorMode,
        visibleStartPage = page,
        visibleEndPage = page,
    ).filter { page in it.startPage..it.endPage }
        .mapTo(mutableSetOf()) { it.lane }
    return AllDayReservation(date = date, lane = firstFreeLaneIndex(occupiedLanes))
}

internal fun AllDayReservation.minimumViewportHeight(): Dp {
    val rowCount = lane + 1
    return (rowCount * 24 + (rowCount - 1) * 5 + 15).dp
}

@Composable
internal fun AllDayViewportOverlay(
    events: List<EventEntity>,
    tasks: List<TaskEntity>,
    taskColorMode: TaskColorMode,
    anchorPage: Int,
    anchorOffsetPx: Float,
    dayWidthPx: Float,
    dayStepPx: Float,
    viewportWidthPx: Float,
    topOffset: Dp,
    height: Dp,
    hourHeightDp: Float,
    timeScrollPx: Int,
    defaultEventDurationMinutes: Int,
    draftEvent: DraftEventSelection?,
    onDraftTap: () -> Unit,
    onAllDaySlotSelected: (LocalDate) -> Unit,
    onEventMoved: (String, Long, LocalDate, LocalTime, LocalTime) -> Unit,
    onTaskMoved: (String, Long, LocalDate, LocalTime, LocalTime) -> Unit,
    onEventMovedAllDay: (String, Long, LocalDate) -> Unit,
    onTaskMovedAllDay: (String, Long, LocalDate) -> Unit,
    maxVisibleItems: Int,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onTaskStatusChanged: (String, String) -> Unit,
    onDetail: (DetailSheet) -> Unit,
    priorityPageCount: Int,
    reservation: AllDayReservation? = null,
) {
    if (height <= 0.dp || viewportWidthPx <= 0f || dayStepPx <= 0f) return
    val bufferStartPage = (anchorPage - 10).coerceAtLeast(0)
    val bufferEndPage = (anchorPage + 14).coerceAtMost(DayPagerPageCount - 1)
    val daySpacingPx = (dayStepPx - dayWidthPx).coerceAtLeast(0f)
    val viewportPages = remember(anchorPage, anchorOffsetPx, dayWidthPx, dayStepPx, viewportWidthPx, bufferStartPage, bufferEndPage) {
        (bufferStartPage..bufferEndPage).filter { page ->
            val left = anchorOffsetPx + (page - anchorPage) * dayStepPx
            left < viewportWidthPx + daySpacingPx && left + dayWidthPx + daySpacingPx > 0f
        }
    }
    val visibleStartPage = viewportPages.minOrNull() ?: anchorPage.coerceIn(bufferStartPage, bufferEndPage)
    val visibleEndPage = viewportPages.maxOrNull() ?: visibleStartPage
    val corePageCount = priorityPageCount.coerceIn(1, MAX_MULTI_DAY_COUNT)
    val priorityPages = remember(anchorPage, anchorOffsetPx, dayWidthPx, dayStepPx, viewportWidthPx, visibleStartPage, visibleEndPage, corePageCount) {
        val visiblePages = (visibleStartPage..visibleEndPage).toList()
        if (visiblePages.size <= corePageCount) {
            visiblePages
        } else {
            val coverageByPage = visiblePages.associateWith { page ->
                val left = anchorOffsetPx + (page - anchorPage) * dayStepPx
                val right = left + dayWidthPx
                (min(right, viewportWidthPx) - max(left, 0f)).coerceIn(0f, dayWidthPx)
            }
            val minStart = visiblePages.first()
            val maxStart = visiblePages.last() - corePageCount + 1
            (minStart..maxStart)
                .maxWithOrNull(
                    compareBy<Int> { start ->
                        (start until start + corePageCount).sumOf { page -> (coverageByPage[page] ?: 0f).toDouble() }
                    }.thenBy { start ->
                        -abs(start - anchorPage)
                    },
                )
                ?.let { start -> (start until start + corePageCount).toList() }
                ?: visiblePages.take(corePageCount)
        }
    }
    val priorityStartPage = priorityPages.firstOrNull() ?: visibleStartPage
    val priorityEndPage = priorityPages.lastOrNull() ?: visibleEndPage
    val layoutStartPage = visibleStartPage
    val layoutEndPage = visibleEndPage
    val overlayItems = remember(events, tasks, layoutStartPage, layoutEndPage, priorityStartPage, priorityEndPage, taskColorMode) {
        buildAllDayOverlayItems(
            events = events,
            tasks = tasks,
            taskColorMode = taskColorMode,
            visibleStartPage = layoutStartPage,
            visibleEndPage = layoutEndPage,
            priorityStartPage = priorityStartPage,
            priorityEndPage = priorityEndPage,
        )
    }
    val rowCount = overlayItems.maxOfOrNull { it.lane + 1 } ?: 0
    val pageItemsByPage = remember(overlayItems, layoutStartPage, layoutEndPage, priorityStartPage, priorityEndPage) {
        (layoutStartPage..layoutEndPage).associateWith { page ->
            overlayItems
                .filter { page in it.startPage..it.endPage }
                .sortedWith(allDayCollapsedPageItemComparator(priorityStartPage, priorityEndPage))
        }
    }
    val hasCollapsedOverflow = maxVisibleItems < rowCount &&
        (visibleStartPage..visibleEndPage).any { pageItemsByPage[it].orEmpty().size > maxVisibleItems }
    val collapsedVisibleItemLimit = when {
        maxVisibleItems <= 0 && rowCount > 0 -> 0
        hasCollapsedOverflow -> (maxVisibleItems - 1).coerceAtLeast(0)
        else -> maxVisibleItems
    }
    val overflowLane = if (maxVisibleItems <= 0) 0 else collapsedVisibleItemLimit
    val density = LocalDensity.current
    val expansionProgress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(MotionMedium, easing = MotionEmphasized),
        label = "allDayOverflowExpansionProgress",
    )
    val renderExpandedItems = expanded || expansionProgress > 0.01f
    val collapsedLayout = remember(overlayItems, pageItemsByPage, layoutStartPage, layoutEndPage, maxVisibleItems, collapsedVisibleItemLimit) {
        buildCollapsedAllDayLayout(
            overlayItems = overlayItems,
            pageItemsByPage = pageItemsByPage,
            visibleStartPage = layoutStartPage,
            visibleEndPage = layoutEndPage,
            maxVisibleItems = maxVisibleItems,
            collapsedVisibleItemLimit = collapsedVisibleItemLimit,
        )
    }
    val collapsedSegments = collapsedLayout.segments
    val continuationSegments = collapsedLayout.continuations
    val showCollapsedOverflowChips = hasCollapsedOverflow && !expanded && expansionProgress <= 0.08f
    var draggingAllDayItemId by remember { mutableStateOf<String?>(null) }
    fun moveAllDayItemToTimed(item: AllDayOverlayItem, date: LocalDate, start: LocalTime, end: LocalTime) {
        item.event?.let { event ->
            onEventMoved(event.resourceHref, event.startsAtMillis, date, start, end)
        }
        item.task?.let { task ->
            onTaskMoved(task.resourceHref, task.startAtMillis ?: task.dueAtMillis ?: System.currentTimeMillis(), date, start, end)
        }
    }
    fun moveAllDayItemToAllDay(item: AllDayOverlayItem, date: LocalDate) {
        item.event?.let { event ->
            onEventMovedAllDay(event.resourceHref, event.startsAtMillis, date)
        }
        item.task?.let { task ->
            onTaskMovedAllDay(task.resourceHref, task.startAtMillis ?: task.dueAtMillis ?: System.currentTimeMillis(), date)
        }
    }
    val hiddenPages = if (!showCollapsedOverflowChips) {
        emptyMap()
    } else {
        (visibleStartPage..visibleEndPage).mapNotNull { page ->
            val pageItems = pageItemsByPage[page].orEmpty()
            if (pageItems.size <= maxVisibleItems) return@mapNotNull null
            val hiddenItems = pageItems.drop(collapsedVisibleItemLimit)
            if (hiddenItems.isEmpty()) null else page to hiddenItems
        }.toMap()
    }
    Box(
        modifier = Modifier
            .offset(y = topOffset)
            .fillMaxWidth()
            .height(height)
            .then(if (draggingAllDayItemId == null) Modifier.verticalClipAllowHorizontalOverflow() else Modifier)
            .zIndex(9f),
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(anchorPage, anchorOffsetPx, dayStepPx) {
                    detectTapGestures { offset ->
                        val pageDelta = floor((offset.x - anchorOffsetPx) / dayStepPx).toInt()
                        val tappedPage = (anchorPage + pageDelta).coerceIn(0, DayPagerPageCount - 1)
                        onAllDaySlotSelected(tappedPage.toDayDate())
                    }
                },
        )
        val expandedRenderItems = if (renderExpandedItems) overlayItems else emptyList()
        expandedRenderItems.forEach { item ->
            val startX = anchorOffsetPx + (item.startPage - anchorPage) * dayStepPx
            val endX = anchorOffsetPx + (item.endPage - anchorPage) * dayStepPx + dayWidthPx
            val visibleLeft = startX.coerceAtLeast(0f)
            val visibleRight = endX.coerceAtMost(viewportWidthPx)
            val visibleWidth = visibleRight - visibleLeft
            if (visibleWidth <= 1f) return@forEach
            key("expanded-all-day:${item.id}") {
                val animatedBaseLane by animateFloatAsState(
                    targetValue = item.lane.toFloat(),
                    animationSpec = tween(MotionMedium, easing = MotionEmphasized),
                    label = "expandedAllDayLane",
                )
                val isOverflowItem = item.lane >= collapsedVisibleItemLimit
                val animatedLane = if (isOverflowItem) {
                    overflowLane + (animatedBaseLane - overflowLane) * expansionProgress
                } else {
                    animatedBaseLane
                }
                val itemAlpha = if (isOverflowItem) expansionProgress else 1f
                val chipTopDp = 7.dp + (animatedLane * 29f).dp
                AllDayViewportChip(
                    item = item,
                    chipLeftPx = visibleLeft,
                    chipTopDp = chipTopDp,
                    allDayHeight = height,
                    hourHeightDp = hourHeightDp,
                    timeScrollPx = timeScrollPx,
                    anchorPage = anchorPage,
                    anchorOffsetPx = anchorOffsetPx,
                    dayStepPx = dayStepPx,
                    defaultDurationMinutes = if (item.task != null) (DEFAULT_TASK_DURATION_MILLIS / 60_000L).toInt() else defaultEventDurationMinutes,
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = visibleLeft.roundToInt(),
                                y = with(density) { chipTopDp.roundToPx() },
                            )
                        }
                        .graphicsLayer {
                            alpha = itemAlpha
                            scaleY = if (isOverflowItem) 0.94f + 0.06f * expansionProgress else 1f
                        }
                        .width(with(density) { visibleWidth.toDp() }),
                    onTaskStatusChanged = onTaskStatusChanged,
                    onDetail = onDetail,
                    onMoveToTimed = ::moveAllDayItemToTimed,
                    onMoveToAllDay = ::moveAllDayItemToAllDay,
                    onDragStateChanged = { active -> draggingAllDayItemId = if (active) item.id else null },
                )
            }
        }
        if (!renderExpandedItems) {
            fun pageVisibility(page: Int): Float {
                val pageLeft = anchorOffsetPx + (page - anchorPage) * dayStepPx
                val pageRight = pageLeft + dayWidthPx
                return ((min(pageRight, viewportWidthPx) - max(pageLeft, 0f)) / dayWidthPx)
                    .coerceIn(0f, 1f)
            }

            continuationSegments.forEach { segment ->
                val left = anchorOffsetPx + (segment.page - anchorPage) * dayStepPx
                val spacing = (dayStepPx - dayWidthPx).coerceAtLeast(0f)
                val overlap = with(density) { 9.dp.toPx() }
                val edgeReach = dayWidthPx * 0.18f
                val fromPreviousAlpha = if (segment.fromPrevious) pageVisibility(segment.page - 1) else 0f
                val toNextAlpha = if (segment.toNext) pageVisibility(segment.page + 1) else 0f
                val segmentLeft = when {
                    segment.fromPrevious -> left - spacing - overlap
                    segment.toNext -> left + dayWidthPx - edgeReach
                    else -> left
                }
                val segmentRight = when {
                    segment.toNext -> left + dayWidthPx + spacing + overlap
                    segment.fromPrevious -> left + edgeReach
                    else -> left + dayWidthPx
                }
                val visibleLeft = segmentLeft.coerceAtLeast(0f)
                val visibleRight = segmentRight.coerceAtMost(viewportWidthPx)
                val visibleWidth = visibleRight - visibleLeft
                if (visibleWidth <= 1f) return@forEach
                val physicalFadeWidth = (spacing + overlap + edgeReach).coerceAtLeast(1f)
                val sideReachFraction = (physicalFadeWidth / visibleWidth).coerceIn(
                    0.05f,
                    if (segment.fromPrevious && segment.toNext) 0.48f else 1f,
                )
                key("all-day-continuation:${segment.item.id}:${segment.page}") {
                    val animatedLane by animateFloatAsState(
                        targetValue = segment.lane.toFloat(),
                        animationSpec = tween(MotionMedium, easing = MotionEmphasized),
                        label = "allDayContinuationLane",
                    )
                    AllDayContinuationChip(
                        segment = segment,
                        fromPreviousAlpha = fromPreviousAlpha,
                        toNextAlpha = toNextAlpha,
                        sideReachFraction = sideReachFraction,
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    x = visibleLeft.roundToInt(),
                                    y = with(density) { (7.dp + (animatedLane * 29f).dp).roundToPx() },
                                )
                            }
                            .width(with(density) { visibleWidth.toDp() })
                            ,
                    )
                }
            }
            collapsedSegments.forEach { segment ->
                val item = segment.item
                val startX = anchorOffsetPx + (segment.startPage - anchorPage) * dayStepPx
                val endX = anchorOffsetPx + (segment.endPage - anchorPage) * dayStepPx + dayWidthPx
                val visibleLeft = startX.coerceAtLeast(0f)
                val visibleRight = endX.coerceAtMost(viewportWidthPx)
                val visibleWidth = visibleRight - visibleLeft
                if (visibleWidth <= 1f) return@forEach
                val segmentOrdinal = collapsedSegments
                    .asSequence()
                    .filter { it.item.id == segment.item.id && it.startPage < segment.startPage }
                    .count()
                key("collapsed-all-day:${segment.item.id}:$segmentOrdinal") {
                    val animatedLane by animateFloatAsState(
                        targetValue = segment.lane.toFloat(),
                        animationSpec = tween(MotionMedium, easing = MotionEmphasized),
                        label = "collapsedAllDayLane",
                    )
                    val chipTopDp = 7.dp + (animatedLane * 29f).dp
                    AllDayViewportChip(
                        item = item,
                        chipLeftPx = visibleLeft,
                        chipTopDp = chipTopDp,
                        allDayHeight = height,
                        hourHeightDp = hourHeightDp,
                        timeScrollPx = timeScrollPx,
                        anchorPage = anchorPage,
                        anchorOffsetPx = anchorOffsetPx,
                        dayStepPx = dayStepPx,
                        defaultDurationMinutes = if (item.task != null) (DEFAULT_TASK_DURATION_MILLIS / 60_000L).toInt() else defaultEventDurationMinutes,
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    x = visibleLeft.roundToInt(),
                                    y = with(density) { chipTopDp.roundToPx() },
                                )
                            }
                            .width(with(density) { visibleWidth.toDp() }),
                        onTaskStatusChanged = onTaskStatusChanged,
                        onDetail = onDetail,
                        onMoveToTimed = ::moveAllDayItemToTimed,
                        onMoveToAllDay = ::moveAllDayItemToAllDay,
                        onDragStateChanged = { active -> draggingAllDayItemId = if (active) item.id else null },
                    )
                }
            }
        }
        hiddenPages.forEach { (page, hiddenItems) ->
            val left = anchorOffsetPx + (page - anchorPage) * dayStepPx
            val visibleLeft = left.coerceAtLeast(0f)
            val visibleRight = (left + dayWidthPx).coerceAtMost(viewportWidthPx)
            if (visibleRight - visibleLeft <= 1f) return@forEach
            key("all-day-overflow:$page") {
                val animatedOverflowLane by animateFloatAsState(
                    targetValue = overflowLane.toFloat(),
                    animationSpec = tween(MotionMedium, easing = MotionEmphasized),
                    label = "allDayOverflowLane",
                )
                AllDayOverflowChip(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = visibleLeft.roundToInt(),
                                y = with(density) { (7.dp + (animatedOverflowLane * 29f).dp).roundToPx() },
                            )
                        }
                        .width(with(density) { (visibleRight - visibleLeft).toDp() })
                        .height(22.dp),
                    hiddenItems = hiddenItems,
                    onClick = { onExpandedChange(true) },
                )
            }
        }
        reservation?.let { reserved ->
            val reservedPage = reserved.date.toDayPage()
            val left = anchorOffsetPx + (reservedPage - anchorPage) * dayStepPx
            val visibleLeft = left.coerceAtLeast(0f)
            val visibleRight = (left + dayWidthPx).coerceAtMost(viewportWidthPx)
            if (reservedPage in visibleStartPage..visibleEndPage && visibleRight - visibleLeft > 1f) {
                val animatedLane by animateFloatAsState(
                    targetValue = reserved.lane.toFloat(),
                    animationSpec = tween(MotionMedium, easing = MotionEmphasized),
                    label = "allDayReservationLane",
                )
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = visibleLeft.roundToInt(),
                                y = with(density) { (7.dp + (animatedLane * 29f).dp).roundToPx() },
                            )
                        }
                        .width(with(density) { (visibleRight - visibleLeft).toDp() })
                        .height(24.dp)
                        .zIndex(18f)
                        .padding(horizontal = 2.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(WarmBrown.copy(alpha = 0.10f))
                        .border(1.dp, WarmBrown.copy(alpha = 0.38f), RoundedCornerShape(8.dp))
                        .testTag("timeline-all-day-reservation-${reserved.date}-${reserved.lane}"),
                )
            }
        }
        draftEvent?.takeIf { it.allDay }?.let { draft ->
            val draftPage = draft.date.toDayPage()
            val left = anchorOffsetPx + (draftPage - anchorPage) * dayStepPx
            val visibleLeft = left.coerceAtLeast(0f)
            val visibleRight = (left + dayWidthPx).coerceAtMost(viewportWidthPx)
            val draftLane = if (renderExpandedItems) {
                firstFreeLaneIndex(
                    overlayItems
                        .filter { draftPage in it.startPage..it.endPage }
                        .map { it.lane }
                        .toSet(),
                )
            } else {
                val pageItems = pageItemsByPage[draftPage].orEmpty()
                val visibleDraftSlots = when {
                    maxVisibleItems <= 0 -> 0
                    pageItems.size > maxVisibleItems -> collapsedVisibleItemLimit
                    else -> maxVisibleItems
                }
                if (pageItems.size < visibleDraftSlots) pageItems.size else null
            }
            if (visibleRight - visibleLeft > 1f && draftLane != null) {
                val animatedDraftLane by animateFloatAsState(
                    targetValue = draftLane.toFloat(),
                    animationSpec = tween(MotionMedium, easing = MotionEmphasized),
                    label = "allDayDraftLane",
                )
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = visibleLeft.roundToInt(),
                                y = with(density) { (7.dp + (animatedDraftLane * 29f).dp).roundToPx() },
                            )
                        }
                        .width(with(density) { (visibleRight - visibleLeft).toDp() })
                        .height(24.dp)
                        .zIndex(20f)
                        .padding(horizontal = 2.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(draft.color).copy(alpha = 0.14f))
                        .border(1.5.dp, Color(draft.color), RoundedCornerShape(8.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDraftTap,
                        ),
                )
            }
        }
    }
}

@Composable
private fun AllDayContinuationChip(
    segment: AllDayContinuationSegment,
    fromPreviousAlpha: Float,
    toNextAlpha: Float,
    sideReachFraction: Float,
    modifier: Modifier,
) {
    val darkPalette = CurrentDarkPalette
    val eventVisuals = remember(segment.item.event?.status, segment.item.event?.manualColor, segment.item.event?.color, darkPalette) {
        segment.item.event?.cardVisuals(darkPalette = darkPalette)
    }
    val base = eventVisuals?.background ?: Color(segment.item.color)
    val itemAlpha = if (segment.item.completed) 0.42f else 1f
    val leftAlpha = itemAlpha * fromPreviousAlpha
    val rightAlpha = itemAlpha * toNextAlpha
    val leftReach = (sideReachFraction * fromPreviousAlpha.coerceIn(0f, 1f)).coerceIn(0f, 1f)
    val rightReach = (sideReachFraction * toNextAlpha.coerceIn(0f, 1f)).coerceIn(0f, 1f)
    val stops = when {
        segment.fromPrevious && segment.toNext -> arrayOf(
            0f to base.copy(alpha = leftAlpha),
            (leftReach * 0.42f) to base.copy(alpha = leftAlpha),
            leftReach to base.copy(alpha = 0f),
            (1f - rightReach) to base.copy(alpha = 0f),
            (1f - rightReach * 0.42f) to base.copy(alpha = rightAlpha),
            1f to base.copy(alpha = rightAlpha),
        )
        segment.fromPrevious -> arrayOf(
            0f to base.copy(alpha = leftAlpha),
            (leftReach * 0.42f) to base.copy(alpha = leftAlpha),
            leftReach to base.copy(alpha = 0f),
            1f to base.copy(alpha = 0f),
        )
        segment.toNext -> arrayOf(
            0f to base.copy(alpha = 0f),
            (1f - rightReach) to base.copy(alpha = 0f),
            (1f - rightReach * 0.42f) to base.copy(alpha = rightAlpha),
            1f to base.copy(alpha = rightAlpha),
        )
        else -> arrayOf(
            0f to base.copy(alpha = 0f),
            1f to base.copy(alpha = 0f),
        )
    }
    Box(
        modifier = modifier
            .background(Brush.horizontalGradient(*stops)),
    )
}

@Composable
private fun AllDayOverflowChip(
    modifier: Modifier,
    hiddenItems: List<AllDayOverlayItem>,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    val frontColor = if (MaterialTheme.colorScheme.background.isDark()) Color(0xFF7E8A96) else Color(0xFFA4AFBA)
    val rearColor = if (MaterialTheme.colorScheme.background.isDark()) Color(0xFF687683) else Color(0xFFB4BEC8)
    val stackColors = listOf(
        rearColor,
        rearColor.copy(alpha = 0.92f),
    )
    val textColor = if (frontColor.isDark()) Color.White else Color(0xFF1C1A18)
    Box(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        stackColors.forEachIndexed { index, color ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(18.dp)
                    .align(Alignment.TopCenter)
                    .offset(y = (3 + index * 2).dp)
                    .padding(horizontal = (4 + index * 3).dp)
                    .clip(shape)
                    .background(color),
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(20.dp)
                .align(Alignment.TopCenter)
                .clip(shape)
                .background(frontColor),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "•••",
                color = textColor,
                fontSize = 13.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

@Composable
private fun AllDayViewportChip(
    item: AllDayOverlayItem,
    modifier: Modifier,
    chipLeftPx: Float,
    chipTopDp: Dp,
    allDayHeight: Dp,
    hourHeightDp: Float,
    timeScrollPx: Int,
    anchorPage: Int,
    anchorOffsetPx: Float,
    dayStepPx: Float,
    defaultDurationMinutes: Int,
    shape: RoundedCornerShape = RoundedCornerShape(8.dp),
    onTaskStatusChanged: (String, String) -> Unit,
    onDetail: (DetailSheet) -> Unit,
    onMoveToTimed: (AllDayOverlayItem, LocalDate, LocalTime, LocalTime) -> Unit,
    onMoveToAllDay: (AllDayOverlayItem, LocalDate) -> Unit,
    onDragStateChanged: (Boolean) -> Unit,
) {
    val darkPalette = CurrentDarkPalette
    val eventVisuals = remember(item.event?.status, item.event?.manualColor, item.event?.color, darkPalette) {
        item.event?.cardVisuals(darkPalette = darkPalette)
    }
    val color = eventVisuals?.background ?: Color(item.color)
    val textColor = eventVisuals?.contentColor ?: if (color.isDark()) Color.White else Color(0xFF1C1A18)
    val eventTextStyle = tentativeReadableTextStyle(item.event?.isTentative() == true)
    val resourceHref = item.event?.resourceHref ?: item.task?.resourceHref
    val pendingAlpha = resourceHref?.let { pendingDeleteAlpha(it) } ?: 1f
    val alpha by animateFloatAsState(
        targetValue = if (item.completed) 0.48f else 1f,
        animationSpec = tween(MotionMedium, easing = MotionStandard),
        label = "allDayViewportChipAlpha",
    )
    var lastCompleted by remember(item.task?.uid) { mutableStateOf(item.completed) }
    var burstKey by remember(item.task?.uid) { mutableStateOf(0) }
    LaunchedEffect(item.completed) {
        if (item.completed && !lastCompleted) burstKey++
        lastCompleted = item.completed
    }
    val density = LocalDensity.current
    var dragX by remember(item.id) { mutableFloatStateOf(0f) }
    var dragY by remember(item.id) { mutableFloatStateOf(0f) }
    var isDragging by remember(item.id) { mutableStateOf(false) }
    var dragPointerOffset by remember(item.id) { mutableStateOf(Offset.Zero) }
    val chipTopPxForDrag = with(density) { chipTopDp.toPx() }
    val allDayHeightPxForDrag = with(density) { allDayHeight.toPx() }
    val hourHeightPxForDrag = with(density) { hourHeightDp.dp.toPx() }
    val laneTopPx = with(density) { 7.dp.toPx() }
    val laneStridePx = with(density) { 29.dp.toPx() }
    val rawFingerX = chipLeftPx + dragPointerOffset.x + dragX
    val rawFingerY = chipTopPxForDrag + dragPointerOffset.y + dragY
    val snappedPageDelta = if (dayStepPx > 0f) floor((rawFingerX - anchorOffsetPx) / dayStepPx).toInt() else 0
    val snappedPage = (anchorPage + snappedPageDelta).coerceIn(0, DayPagerPageCount - 1)
    val snappedLeftPx = anchorOffsetPx + (snappedPage - anchorPage) * dayStepPx
    val draggedIntoTimedGrid = rawFingerY >= allDayHeightPxForDrag && hourHeightPxForDrag > 0f && dayStepPx > 0f
    val timedPreviewHeight = ((defaultDurationMinutes.coerceIn(DraftMinDurationMinutes, 24 * 60 - 1) / 60f) * hourHeightDp).dp
    val displayHeight by animateDpAsState(
        targetValue = if (isDragging && draggedIntoTimedGrid) timedPreviewHeight else 24.dp,
        animationSpec = tween(90, easing = MotionStandard),
        label = "allDayDraggedPreviewHeight",
    )
    val snappedDragX = snappedLeftPx - chipLeftPx
    val snappedDragY = if (draggedIntoTimedGrid) {
        val duration = defaultDurationMinutes.coerceIn(DraftMinDurationMinutes, 24 * 60 - 1)
        val gridY = rawFingerY - allDayHeightPxForDrag + timeScrollPx
        val minuteOffset = ((gridY / hourHeightPxForDrag) * 60f).roundToInt().snapDraftMinute()
        val startMinute = (DayStartHour * 60 + minuteOffset)
            .coerceIn(DayStartHour * 60, (DayEndHour + 1) * 60 - duration)
        allDayHeightPxForDrag +
            (((startMinute - DayStartHour * 60) / 60f) * hourHeightPxForDrag) -
            timeScrollPx -
            chipTopPxForDrag
    } else {
        val lane = if (laneStridePx > 0f) ((rawFingerY - laneTopPx) / laneStridePx).roundToInt().coerceAtLeast(0) else 0
        (laneTopPx + lane * laneStridePx).coerceIn(0f, (allDayHeightPxForDrag - with(density) { 24.dp.toPx() }).coerceAtLeast(0f)) -
            chipTopPxForDrag
    }
    val displayDragX by animateFloatAsState(
        targetValue = if (isDragging) snappedDragX else 0f,
        animationSpec = tween(90, easing = MotionStandard),
        label = "allDayDraggedSnapX",
    )
    val displayDragY by animateFloatAsState(
        targetValue = if (isDragging) snappedDragY else 0f,
        animationSpec = tween(90, easing = MotionStandard),
        label = "allDayDraggedSnapY",
    )
    val dragModifier = Modifier.pointerInput(
        item.id,
        chipLeftPx,
        chipTopDp,
        allDayHeight,
        hourHeightDp,
        timeScrollPx,
        anchorPage,
        anchorOffsetPx,
        dayStepPx,
        defaultDurationMinutes,
    ) {
        val chipTopPx = chipTopDp.toPx()
        val allDayHeightPx = allDayHeight.toPx()
        val hourHeightPx = hourHeightDp.dp.toPx()
        detectDragGesturesAfterLongPress(
            onDragStart = { offset ->
                dragPointerOffset = offset
                isDragging = true
                onDragStateChanged(true)
            },
            onDrag = { change, dragAmount ->
                change.consume()
                dragX += dragAmount.x
                dragY += dragAmount.y
            },
            onDragCancel = {
                isDragging = false
                dragX = 0f
                dragY = 0f
                onDragStateChanged(false)
            },
            onDragEnd = {
                val fingerX = chipLeftPx + dragPointerOffset.x + dragX
                val fingerY = chipTopPx + dragPointerOffset.y + dragY
                val pageDelta = if (dayStepPx > 0f) floor((fingerX - anchorOffsetPx) / dayStepPx).toInt() else 0
                val targetDate = (anchorPage + pageDelta).coerceIn(0, DayPagerPageCount - 1).toDayDate()
                if (fingerY >= allDayHeightPx && hourHeightPx > 0f && dayStepPx > 0f) {
                    val duration = defaultDurationMinutes.coerceIn(DraftMinDurationMinutes, 24 * 60 - 1)
                    val gridY = fingerY - allDayHeightPx + timeScrollPx
                    val minuteOffset = ((gridY / hourHeightPx) * 60f).roundToInt().snapDraftMinute()
                    val startMinute = (DayStartHour * 60 + minuteOffset)
                        .coerceIn(DayStartHour * 60, (DayEndHour + 1) * 60 - duration)
                    onMoveToTimed(item, targetDate, startMinute.toDraftLocalTime(), (startMinute + duration).toDraftLocalTime())
                } else {
                    onMoveToAllDay(item, targetDate)
                }
                isDragging = false
                dragX = 0f
                dragY = 0f
                onDragStateChanged(false)
            },
        )
    }
    Box(
        modifier = modifier
            .wrapContentSize(Alignment.TopStart, unbounded = true)
            .requiredHeight(displayHeight)
            .testTag("timeline-all-day-item-${item.id}")
            .zIndex(
                when {
                    isDragging -> 80f
                    item.task != null && !item.completed && taskPriorityIntensity(item.task.priority) > 0f -> 3f
                    else -> 0f
                },
            )
            .graphicsLayer {
                translationX = displayDragX
                translationY = displayDragY
            }
            .alpha(alpha * pendingAlpha)
            .then(
                if (item.task != null && !item.completed) {
                    Modifier.taskPriorityMotion(item.task.priority, color)
                } else {
                    Modifier
                },
            )
            .background(color, shape)
            .then(
                when {
                    eventVisuals?.dashedBorder == true && eventVisuals.borderColor != null -> Modifier.dashedBorder(eventVisuals.borderColor, 8.dp)
                    eventVisuals?.borderColor != null -> Modifier.border(1.dp, eventVisuals.borderColor, shape)
                    else -> Modifier
                },
            )
            .then(dragModifier),
    ) {
        Box(
            Modifier
                .matchParentSize()
                .clip(shape)
                .clickable {
                    item.event?.let { onDetail(DetailSheet.Event(it)) }
                    item.task?.let { onDetail(DetailSheet.Task(it)) }
                },
        )
        TaskCardCompletionBurst(burstKey, color = textColor, modifier = Modifier.matchParentSize())
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (item.task != null) {
                TaskStatusCheckbox(
                    status = item.task.effectiveStatus(),
                    tint = textColor,
                    onStatusChange = { onTaskStatusChanged(item.task.resourceHref, it) },
                    boxSize = 18.dp,
                    iconSize = 15.dp,
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                item.title,
                color = textColor,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                lineHeight = 13.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                textDecoration = eventVisuals?.textDecoration,
                style = eventTextStyle,
            )
        }
        resourceHref?.let { href ->
            PendingMutationBadge(href, Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-2).dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AllDayArea(
    day: LocalDate,
    visibleStartDate: LocalDate,
    visibleEndDate: LocalDate,
    events: List<EventEntity>,
    tasks: List<TaskEntity>,
    height: Dp,
    onTaskStatusChanged: (String, String) -> Unit,
    onDetail: (DetailSheet) -> Unit,
    modifier: Modifier = Modifier,
) {
    var taskPopupOpen by remember(day) { mutableStateOf(false) }
    val quietInteraction = remember { MutableInteractionSource() }
    val allDayEvents = events.filter { it.isAllDayTopItemOn(day) }.take(2)
    val allDayTasks = tasks.filter { it.isFullDayTaskOn(day) }
    // Only the single day being morphed into/out of the month grid registers per-item shared
    // elements, so all-day chips on neighbouring days don't claim stray morph keys.
    val morphThisDay = day == LocalMorphItemDay.current
    if (allDayEvents.isEmpty() && allDayTasks.isEmpty()) {
        Spacer(
            modifier
                .height(height)
                .fillMaxWidth()
                .clickable(interactionSource = quietInteraction, indication = null, onClick = {}),
        )
        return
    }
    if (taskPopupOpen) {
        KgsModalBottomSheet(
            onDismissRequest = { taskPopupOpen = false },
            initialContentHeight = (116 + allDayTasks.size * 58).dp,
        ) {
            DayTasksSheet(
                day = day,
                tasks = allDayTasks,
                onTaskStatusChanged = onTaskStatusChanged,
                onTaskClick = { task ->
                    taskPopupOpen = false
                    onDetail(DetailSheet.Task(task))
                },
            )
        }
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .verticalClipAllowHorizontalOverflow()
            .clickable(interactionSource = quietInteraction, indication = null, onClick = {})
            .padding(top = 7.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        allDayEvents.forEach {
            val starts = it.allDayTopStartDate() ?: day
            val showTitle = day == maxOf(starts, visibleStartDate)
            AllDayChip(
                title = if (showTitle) it.title else "",
                color = Color(it.displayColor()),
                event = it,
                continuesFromPrevious = it.isAllDayTopItemOn(day.minusDays(1)) && day > visibleStartDate,
                continuesToNext = it.isAllDayTopItemOn(day.plusDays(1)) && day < visibleEndDate,
                onClick = { onDetail(DetailSheet.Event(it)) },
                morphDay = morphThisDay,
            )
        }
        if (allDayTasks.isNotEmpty()) {
            val singleTask = allDayTasks.singleOrNull()
            val showSingleTaskTitle = singleTask?.let { task ->
                val starts = task.startAtMillis?.toDate() ?: task.dueAtMillis?.toDate() ?: day
                day == maxOf(starts, visibleStartDate)
            } ?: true
            AllDayTaskChip(
                title = if (allDayTasks.size == 1) {
                    if (showSingleTaskTitle) allDayTasks.first().title else ""
                } else {
                    stringResource(R.string.task_count, allDayTasks.size)
                },
                color = Color(allDayTasks.first().color),
                completed = allDayTasks.size == 1 && allDayTasks.first().isInactive(),
                priority = singleTask?.priority,
                continuesFromPrevious = singleTask?.isFullDayTaskOn(day.minusDays(1)) == true && day > visibleStartDate,
                continuesToNext = singleTask?.isFullDayTaskOn(day.plusDays(1)) == true && day < visibleEndDate,
                status = singleTask?.effectiveStatus(),
                resourceHref = singleTask?.resourceHref,
                onStatusChange = if (singleTask != null) {
                    { status -> onTaskStatusChanged(singleTask.resourceHref, status) }
                } else {
                    null
                },
                onClick = {
                    if (allDayTasks.size == 1) {
                        onDetail(DetailSheet.Task(allDayTasks.first()))
                    } else {
                        taskPopupOpen = true
                    }
                },
                morphUid = if (morphThisDay && allDayTasks.size == 1) allDayTasks.first().resourceHref else null,
            )
        }
    }
}


@Composable
private fun DayTasksSheet(
    day: LocalDate,
    tasks: List<TaskEntity>,
    onTaskStatusChanged: (String, String) -> Unit,
    onTaskClick: (TaskEntity) -> Unit,
) {
    val sheetScrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(sheetScrollState)
            .padding(horizontal = 22.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            day.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM", LocalAppLocale.current)),
            color = WarmInk,
            fontSize = 22.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(stringResource(R.string.task_count, tasks.size), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, lineHeight = 17.sp)
        tasks.forEach { task ->
            TaskRow(
                task = task,
                taskColorMode = TaskColorMode.Collection,
                onTaskStatusChanged = onTaskStatusChanged,
                onClick = { onTaskClick(task) },
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun AllDayChip(
    title: String,
    color: Color,
    event: EventEntity? = null,
    continuesFromPrevious: Boolean,
    continuesToNext: Boolean,
    onClick: () -> Unit,
    morphDay: Boolean = false,
) {
    val darkPalette = CurrentDarkPalette
    val visuals = remember(event?.status, event?.manualColor, event?.color, darkPalette) {
        event?.cardVisuals(darkPalette = darkPalette)
    }
    val chipColor = visuals?.background ?: color
    val textColor = visuals?.contentColor ?: if (chipColor.isDark()) Color.White else Color(0xFF1C1A18)
    val shape = continuationShape(continuesFromPrevious, continuesToNext)
    val eventTextStyle = tentativeReadableTextStyle(event?.isTentative() == true)
    val pendingAlpha = event?.resourceHref?.let { pendingDeleteAlpha(it) } ?: 1f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            // Option B: an all-day event morphs out of / into its month-cell pill (matched by uid),
            // just like the timed cards, so the events already visible in the month rectangle travel
            // into the all-day band instead of merely scaling with the grid.
            .morphBounds(
                key = "item-${event?.uid}",
                enabled = EnablePerItemMorph && morphDay && event?.uid != null,
                enter = MorphItemCardEnter,
                exit = MorphItemCardExit,
                boundsTransform = MorphItemBoundsTransform,
            )
            .alpha(pendingAlpha)
            .drawContinuationBridge(chipColor, continuesFromPrevious, continuesToNext)
            .background(chipColor, shape)
            .then(
                when {
                    visuals?.dashedBorder == true && visuals.borderColor != null -> Modifier.dashedBorder(visuals.borderColor, 7.dp)
                    visuals?.borderColor != null -> Modifier.border(1.dp, visuals.borderColor, shape)
                    else -> Modifier
                },
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .matchParentSize()
                .clip(shape)
                .clickable(onClick = onClick),
        )
        Text(
            text = title,
            color = textColor,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            lineHeight = 13.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            textDecoration = visuals?.textDecoration,
            style = eventTextStyle,
            modifier = Modifier
                .padding(horizontal = 6.dp, vertical = 3.dp)
                .wrapContentWidth(unbounded = true),
        )
        event?.resourceHref?.let { href ->
            PendingMutationBadge(href, Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-2).dp))
        }
    }
}

@Composable
private fun AllDayTaskChip(
    title: String,
    color: Color,
    completed: Boolean,
    priority: Int?,
    continuesFromPrevious: Boolean,
    continuesToNext: Boolean,
    status: String?,
    resourceHref: String?,
    onStatusChange: ((String) -> Unit)?,
    onClick: () -> Unit,
    morphUid: String? = null,
) {
    var lastCompleted by remember { mutableStateOf(completed) }
    var burstKey by remember { mutableStateOf(0) }
    LaunchedEffect(completed) {
        if (completed && !lastCompleted) burstKey++
        lastCompleted = completed
    }
    val alpha by animateFloatAsState(
        targetValue = if (completed) 0.48f else 1f,
        animationSpec = tween(MotionMedium, easing = MotionStandard),
        label = "allDayTaskAlpha",
    )
    val shape = continuationShape(continuesFromPrevious, continuesToNext)
    val pendingAlpha = resourceHref?.let { pendingDeleteAlpha(it) } ?: 1f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            // Option B: a single full-day task morphs out of / into its month-cell pill (matched by
            // uid). Only when it's the lone all-day task — a collapsed "N tasks" chip has no
            // single source pill to match.
            .morphBounds(
                key = "item-${morphUid}",
                enabled = EnablePerItemMorph && morphUid != null,
                enter = MorphItemCardEnter,
                exit = MorphItemCardExit,
                boundsTransform = MorphItemBoundsTransform,
            )
            .alpha(alpha * pendingAlpha)
            .taskPriorityMotion(if (completed) null else priority, color)
            .drawContinuationBridge(color, continuesFromPrevious, continuesToNext)
            .background(color, shape),
    ) {
        TaskCardCompletionBurst(burstKey, color = if (color.isDark()) Color.White else WarmBrown, modifier = Modifier.matchParentSize())
        Box(
            Modifier
                .matchParentSize()
                .clip(shape)
                .clickable(onClick = onClick),
        )
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 5.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val checkTint = if (color.isDark()) Color.White else Color(0xFF1C1A18)
            if (status != null && onStatusChange != null) {
                TaskStatusCheckbox(
                    status = status,
                    tint = checkTint,
                    onStatusChange = onStatusChange,
                    boxSize = 20.dp,
                    iconSize = 16.dp,
                )
            } else {
                Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (completed) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = checkTint,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = title,
                color = if (color.isDark()) Color.White else Color(0xFF1C1A18),
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                lineHeight = 13.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                modifier = Modifier.wrapContentWidth(unbounded = true),
            )
        }
        resourceHref?.let { href ->
            PendingMutationBadge(href, Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-2).dp))
        }
    }
}
