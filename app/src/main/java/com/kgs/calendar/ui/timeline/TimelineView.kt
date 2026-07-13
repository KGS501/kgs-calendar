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
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.gestures.stopScroll
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
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
import com.kgs.calendar.domain.model.startOfWeek
import com.kgs.calendar.domain.model.timelineDayCount
import com.kgs.calendar.domain.model.timelineVisibleAnchor
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
import com.kgs.calendar.ui.timeline.fallbackPagerSnapPageLimit
import com.kgs.calendar.ui.timeline.FullWeekPagerFlingBehavior
import com.kgs.calendar.ui.timeline.FullWeekPagerGestureState
import com.kgs.calendar.ui.timeline.pagePosition
import com.kgs.calendar.ui.timeline.weekStartPageOffset
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
import com.kgs.calendar.ui.timeline.NoOpTimelineTimedDragReporter
import com.kgs.calendar.ui.timeline.PinchSnapshot
import com.kgs.calendar.ui.timeline.TimelineDragPoint
import com.kgs.calendar.ui.timeline.TimelineDragReducer
import com.kgs.calendar.ui.timeline.TimelineDragSession
import com.kgs.calendar.ui.timeline.TimelineDraggedItem
import com.kgs.calendar.ui.timeline.TimelineDraggedItemKind
import com.kgs.calendar.ui.timeline.TimelineDropTarget
import com.kgs.calendar.ui.timeline.TimelineTimedDragReporter
import com.kgs.calendar.ui.timeline.TimelineTimedDragStart
import com.kgs.calendar.ui.timeline.TimelineViewportState
import com.kgs.calendar.ui.timeline.updateVerticalPinch
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
private data class TimelineRootDragLayout(
    val rootOrigin: TimelineDragPoint,
    val anchorPage: Int,
    val anchorOffsetPx: Float,
    val dayWidthPx: Float,
    val dayStepPx: Float,
    val sidebarWidthPx: Float,
    val fixedAllDayBoundaryY: Float,
    val sourceInsetXPx: Float,
    val initialPointerYPx: Float,
    val initialTimeScrollPx: Int,
    val sourceTopPx: Float,
    val sourceWidthPx: Float,
    val sourceHeightPx: Float,
)

private data class ActiveTimelineTimedDrag(
    val item: TimelineDraggedItem,
    val session: TimelineDragSession,
    val layout: TimelineRootDragLayout,
    val awaitingCommit: Boolean = false,
)

private interface TimelineVerticalPinchOwner {
    fun begin(upperY: Float, lowerY: Float): PinchSnapshot?
    fun update(snapshot: PinchSnapshot, upperY: Float, lowerY: Float)
    fun end()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TimelineView(
    state: CalendarUiState,
    // The active timeline view — Day (1 column) or ThreeDay (3 columns). The same composable
    // serves both; switching between them animates the column width rather than swapping.
    selectedView: CalendarViewMode,
    onDateSelected: (LocalDate) -> Unit,
    onViewSelected: (CalendarViewMode) -> Unit,
    onMultiDayCountChanged: (Int) -> Unit,
    onTaskStatusChanged: (String, String) -> Unit,
    onEventMoved: (String, Long, LocalDate, LocalTime, LocalTime) -> Unit,
    onTaskMoved: (String, Long, LocalDate, LocalTime, LocalTime) -> Unit,
    onEventMovedAllDay: (String, Long, LocalDate) -> Unit,
    onTaskMovedAllDay: (String, Long, LocalDate) -> Unit,
    onSlotSelected: (LocalDate, LocalTime) -> Unit,
    onAllDaySlotSelected: (LocalDate) -> Unit,
    draftEvent: DraftEventSelection?,
    onDraftEventChanged: (DraftEventSelection) -> Unit,
    onDraftInteraction: () -> Unit,
    onDraftTap: () -> Unit,
    timelineBottomInset: Dp,
    onDetail: (DetailSheet) -> Unit,
    // Hoisted so the vertical zoom (pinch) and scroll position are shared between the
    // 3-day and 1-day instances and survive the morph between them.
    timeScroll: androidx.compose.foundation.ScrollState,
    hourHeightDp: Float,
    onHourHeightChange: (Float) -> Unit,
    initialTimeScrollApplied: Boolean,
    onInitialTimeScrollApplied: () -> Unit,
    monthMorphDay: LocalDate,
) {
    val scope = rememberCoroutineScope()
    val calendarTime = LocalCalendarTimeSnapshot.current
    val now = calendarTime.nowMinute
    val density = LocalDensity.current
    var timelineRootOffset by remember { mutableStateOf(Offset.Zero) }
    var activeTimedDrag by remember { mutableStateOf<ActiveTimelineTimedDrag?>(null) }
    var dayViewportWidthPx by remember { mutableStateOf(0) }
    var gridViewportHeightPx by remember { mutableStateOf(0) }
    val bodyScrollableState = rememberScrollableState { delta ->
        val consumed = timeScroll.dispatchRawDelta(-delta)
        -consumed
    }
    val daySpacingPx = with(density) { DayColumnSpacing.toPx() }
    // The tapped day that is currently expanding into / collapsing out of the 1-day view, plus
    // the 3-day window it morphs within. Non-null only while a header-tap morph is playing; it
    // drives the in-place "zoom around the tapped column" overlay below. Cleared when the
    // column-count animation settles.
    var morphContext by remember { mutableStateOf<DayMorphContext?>(null) }
    // The timeline's OWN mode (Day vs ThreeDay), held independently of selectedView. When the app
    // switches away to month/agenda/tasks, the timeline keeps its last mode while it exits — so a
    // 1-day view collapses back into a month cell (and a 3-day view does NOT pretend to be a day).
    // selectedView is momentarily "Month" for the outgoing timeline, which would otherwise flip
    // this to 3-day and break the reverse morph.
    var timelineMode by remember {
        mutableStateOf(if (selectedView == CalendarViewMode.Day) CalendarViewMode.Day else CalendarViewMode.ThreeDay)
    }
    LaunchedEffect(selectedView) {
        if (selectedView == CalendarViewMode.Day) timelineMode = CalendarViewMode.Day
        else if (selectedView == CalendarViewMode.ThreeDay) timelineMode = CalendarViewMode.ThreeDay
    }
    val isDayMode = timelineMode == CalendarViewMode.Day
    val isWeekMode = !isDayMode && state.weekViewEnabled
    val fullWeekPagingEnabled = isWeekMode && state.fullWeekSwipeEnabled
    val configuredMultiDayCount = state.multiDayCount.coerceMultiDayCount()
    val multiDayCount = timelineDayCount(
        viewMode = CalendarViewMode.ThreeDay,
        weekViewEnabled = state.weekViewEnabled,
        multiDayCount = configuredMultiDayCount,
    )
    val fallbackSnapPageLimit = fallbackPagerSnapPageLimit(
        isDayMode = isDayMode,
        weekViewEnabled = state.weekViewEnabled,
        configuredMultiDayCount = configuredMultiDayCount,
    )
    val targetDayCount = if (isDayMode) 1 else multiDayCount
    val dayEndGutterPx = daySpacingPx
    val dayGeometryViewportWidthPx = (dayViewportWidthPx - dayEndGutterPx).coerceAtLeast(0f)
    // Animate the column COUNT as a float between 1 and the configured multi-day count. The pager's fixed page size is
    // derived from it for the steady states and the back-button morph; the header-tap morph
    // instead uses [morphContext] + the overlay so the tapped day can expand from ANY column
    // (the pager can only ever anchor the leftmost page).
    val animatedDayCount by animateFloatAsState(
        targetValue = targetDayCount.toFloat(),
        animationSpec = tween(MorphDurationMs, easing = MorphEasing),
        label = "dayColumnCount",
    )
    // 0 = full multi-day, 1 = full 1-day. Derived from the animating count so the overlay and the
    // underlying pager are always perfectly in step.
    val morphSpan = (multiDayCount - 1).coerceAtLeast(1).toFloat()
    val morphProgress = ((multiDayCount - animatedDayCount) / morphSpan).coerceIn(0f, 1f)
    // Day indexing (which dates are visible, all-day stacking) uses the integer target so the
    // content is correct for where we're heading; only the WIDTH interpolates.
    // Track the in-between count as the width animates (round up) so the visible-day window and
    // the all-day band reflect the slide — e.g. a neighbour day's all-day chips stay rendered
    // while it slides out, instead of snapping to the destination count the instant the morph
    // starts.
    val clampedDayCount = ceil(animatedDayCount).roundToInt().coerceIn(1, MAX_MULTI_DAY_COUNT)
    val dayWidthPx = ((dayGeometryViewportWidthPx - ((animatedDayCount - 1f) * daySpacingPx)) / animatedDayCount)
        .coerceAtLeast(with(density) { 32.dp.toPx() })
    val dayWidthDp = with(density) { dayWidthPx.toDp() }
    // Derive the pager anchor from the active timeline policy, not visibleRange.startDate. The
    // loaded range is intentionally wider than the displayed window for morphs, so its start may
    // be several days earlier than either the focused date or the aligned week anchor.
    val selectedAnchorDate = timelineVisibleAnchor(
        date = state.selectedDate,
        viewMode = timelineMode,
        weekViewEnabled = state.weekViewEnabled,
        fullWeekSwipeEnabled = state.fullWeekSwipeEnabled,
        firstDayOfWeek = state.firstDayOfWeek,
    )
    val selectedPage = selectedAnchorDate.toDayPage()
    val pagerState = rememberPagerState(initialPage = selectedPage, pageCount = { DayPagerPageCount })
    val alignedWeekPageOffset = remember(state.firstDayOfWeek) {
        weekStartPageOffset(state.firstDayOfWeek)
    }
    val fullWeekGestureState = remember(alignedWeekPageOffset, fullWeekPagingEnabled) {
        FullWeekPagerGestureState(
            initialAnchorPage = selectedPage,
            pageCount = DayPagerPageCount,
            weekStartPageOffset = alignedWeekPageOffset,
        )
    }
    val fullWeekFlingBehavior = remember(pagerState, fullWeekGestureState) {
        FullWeekPagerFlingBehavior(
            pagerState = pagerState,
            gestureState = fullWeekGestureState,
            animationSpec = tween(durationMillis = 320, easing = MotionEmphasized),
        )
    }
    val fallbackPagerFlingBehavior = PagerDefaults.flingBehavior(
        state = pagerState,
        pagerSnapDistance = PagerSnapDistance.atMost(fallbackSnapPageLimit),
    )
    var programmaticTargetPage by remember { mutableStateOf<Int?>(null) }
    var handledWidgetDateNavigationSerial by remember { mutableStateOf(0) }
    val latestMorphContext by rememberUpdatedState(morphContext)
    val latestSelectedDate by rememberUpdatedState(state.selectedDate)

    LaunchedEffect(selectedPage, fullWeekPagingEnabled, fullWeekGestureState) {
        if (fullWeekPagingEnabled) fullWeekGestureState.resetAnchor(selectedPage)
    }

    LaunchedEffect(pagerState, fullWeekGestureState, fullWeekPagingEnabled) {
        if (!fullWeekPagingEnabled) return@LaunchedEffect
        pagerState.interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> fullWeekGestureState.beginGesture(
                    pagePosition = pagerState.pagePosition(),
                    settledPage = pagerState.settledPage,
                )
                is DragInteraction.Stop ->
                    fullWeekGestureState.recordGesturePosition(pagerState.pagePosition())
                is DragInteraction.Cancel -> fullWeekGestureState.cancelGesture()
            }
        }
    }

    LaunchedEffect(pagerState, fullWeekGestureState, fullWeekPagingEnabled) {
        if (!fullWeekPagingEnabled) return@LaunchedEffect
        snapshotFlow { pagerState.pagePosition() }
            .collect(fullWeekGestureState::recordGesturePosition)
    }
    val actualVisiblePages = remember(
        pagerState.layoutInfo.visiblePagesInfo,
        dayViewportWidthPx,
        dayGeometryViewportWidthPx,
        dayWidthPx,
        pagerState.currentPage,
        clampedDayCount,
    ) {
        val viewportWidth = dayViewportWidthPx
        val geometryViewportWidth = dayGeometryViewportWidthPx
        val pageExtent = dayWidthPx + daySpacingPx
        pagerState.layoutInfo.visiblePagesInfo
            .filter { page ->
                viewportWidth <= 0 || (page.offset + pageExtent > 0 && page.offset < geometryViewportWidth + daySpacingPx)
            }
            .map { it.index.coerceIn(0, DayPagerPageCount - 1) }
            .distinct()
            .sorted()
            .ifEmpty {
                val start = pagerState.currentPage.coerceIn(0, DayPagerPageCount - 1)
                (0 until clampedDayCount).map { (start + it).coerceIn(0, DayPagerPageCount - 1) }
            }
    }
    val visibleStartDate = actualVisiblePages.minOrNull()?.toDayDate() ?: state.selectedDate
    val visibleEndDate = actualVisiblePages.maxOrNull()?.toDayDate() ?: visibleStartDate.plusDays((clampedDayCount - 1).toLong())
    val pagerVisibleDays = actualVisiblePages.map { it.toDayDate() }
    val eventsByDay = remember(state.events) { state.events.indexEventsByDay() }
    val calendarTasks = remember(state.datedTasks, state.showCompletedTasksInCalendar) {
        if (state.showCompletedTasksInCalendar) state.datedTasks else state.datedTasks.filterNot { it.isCompleted }
    }
    val tasksByDay = remember(calendarTasks) { calendarTasks.indexTasksByDay() }
    var allDayExpanded by remember { mutableStateOf(false) }
    val baseAllDayHeight = remember(pagerVisibleDays, state.events, calendarTasks, state.maxVisibleAllDayItems, allDayExpanded, draftEvent) {
        pagerVisibleDays.allDayAreaHeight(
            events = state.events,
            tasks = calendarTasks,
            maxVisibleItems = state.maxVisibleAllDayItems,
            expanded = allDayExpanded,
            draftDate = draftEvent?.takeIf { it.allDay }?.date,
        )
    }
    val visibleReservation = activeTimedDrag?.session?.reservation
        ?.takeIf { reservation -> reservation.date in pagerVisibleDays }
    val allDayHeight = visibleReservation
        ?.minimumViewportHeight()
        ?.let { reservationHeight -> maxOf(baseAllDayHeight, reservationHeight) }
        ?: baseAllDayHeight
    val allDayHasOverflow = remember(pagerVisibleDays, state.events, calendarTasks, state.maxVisibleAllDayItems) {
        pagerVisibleDays.hasAllDayOverflow(state.events, calendarTasks, state.maxVisibleAllDayItems)
    }
    val allDayArrowRotation by animateFloatAsState(
        targetValue = if (allDayExpanded) 180f else 0f,
        animationSpec = tween(MotionMedium, easing = MotionEmphasized),
        label = "allDayArrowRotation",
    )
    // Use the morph duration so the all-day band grows/shrinks in step with the column-width
    // slide when toggling 1-day <-> 3-day.
    val animatedAllDayHeight by animateDpAsState(
        targetValue = allDayHeight,
        animationSpec = tween(MorphDurationMs, easing = MorphEasing),
        label = "allDayHeight",
    )
    val minHourHeightDp = if (gridViewportHeightPx > 0) {
        max(AbsoluteMinHourRowHeightDp, with(density) { gridViewportHeightPx.toDp().value } / (DayEndHour - DayStartHour + 1))
    } else {
        AbsoluteMinHourRowHeightDp
    }

    LaunchedEffect(minHourHeightDp) {
        if (hourHeightDp < minHourHeightDp) onHourHeightChange(minHourHeightDp)
    }

    LaunchedEffect(selectedPage, morphContext, state.dateNavigationSerial) {
        // A header-tap morph owns the visible timeline until its final handoff. Moving the pager
        // while that animation is starting makes a middle/right tapped column jump left before it
        // expands. The handoff effect below settles the still-hidden pager immediately before the
        // overlay is removed.
        if (morphContext != null) return@LaunchedEffect
        if (state.dateNavigationSerial > handledWidgetDateNavigationSerial) {
            handledWidgetDateNavigationSerial = state.dateNavigationSerial
            programmaticTargetPage = selectedPage
            pagerState.scrollToPage(selectedPage)
            programmaticTargetPage = null
            return@LaunchedEffect
        }
        if (selectedView == CalendarViewMode.Day &&
            pagerState.currentPage == selectedPage &&
            abs(pagerState.currentPageOffsetFraction) > 0.001f
        ) {
            programmaticTargetPage = selectedPage
            pagerState.scrollToPage(selectedPage)
            programmaticTargetPage = null
            return@LaunchedEffect
        }
        if (pagerState.currentPage != selectedPage && pagerState.settledPage != selectedPage) {
            programmaticTargetPage = selectedPage
            val distance = selectedPage - pagerState.currentPage
            if (abs(distance) > 8) {
                pagerState.scrollToPage(selectedPage - distance.coerceIn(-3, 3))
            }
            pagerState.animateScrollToPage(
                page = selectedPage,
                animationSpec = tween(durationMillis = 320, easing = MotionEmphasized),
            )
            programmaticTargetPage = null
        }
    }

    LaunchedEffect(morphContext, animatedDayCount, targetDayCount, selectedPage, state.selectedDate) {
        val ctx = morphContext
        if (ctx != null && timelineMode == ctx.targetMode && abs(animatedDayCount - targetDayCount) < 0.001f) {
            // Keep the overlay visible through one settled pager frame. Clearing it immediately
            // from animateFloatAsState's completion callback reveals stale pager geometry for a
            // single frame when the tapped day was not the leftmost 3-day column.
            val targetDate = ctx.targetAnchorDate
            programmaticTargetPage = targetDate.toDayPage()
            pagerState.scrollToPage(targetDate.toDayPage())
            if (state.selectedDate != targetDate) {
                // Commit the selected date only after the visual morph has reached its target.
                // Updating it at tap-time makes middle/right columns relocate to the pager's
                // left edge before their expansion animation begins.
                onDateSelected(targetDate)
                return@LaunchedEffect
            }
            withFrameNanos { }
            morphContext = null
            programmaticTargetPage = null
        }
    }

    LaunchedEffect(gridViewportHeightPx, hourHeightDp, timeScroll.maxValue) {
        if (!initialTimeScrollApplied && gridViewportHeightPx > 0 && timeScroll.maxValue > 0) {
            val target = with(density) { ((9 - DayStartHour) * hourHeightDp).dp.roundToPx() }
            timeScroll.scrollTo(target.coerceIn(0, timeScroll.maxValue))
            onInitialTimeScrollApplied()
        }
    }
    var pinchTargetScrollPx by remember(timeScroll) { mutableFloatStateOf(Float.NaN) }
    val currentPinchBegin = rememberUpdatedState<(Float, Float) -> PinchSnapshot?>(
        newValue = pinchBegin@ { upperY, lowerY ->
            if (gridViewportHeightPx <= 0) return@pinchBegin null
            scope.launch { timeScroll.stopScroll() }
            pinchTargetScrollPx = timeScroll.value.toFloat()
            PinchSnapshot.begin(
                upperY = upperY,
                lowerY = lowerY,
                viewport = TimelineViewportState(
                    hourHeightPx = with(density) { hourHeightDp.dp.toPx() },
                    scrollPx = timeScroll.value.toFloat(),
                ),
                contentStartMinute = DayStartHour * 60,
                contentEndMinute = (DayEndHour + 1) * 60,
                viewportHeightPx = gridViewportHeightPx.toFloat(),
                minHourHeightPx = with(density) { minHourHeightDp.dp.toPx() },
                maxHourHeightPx = with(density) { MaxHourRowHeightDp.dp.toPx() },
                contentTopY = with(density) { (DayHeaderHeight + animatedAllDayHeight).toPx() },
            )
        },
    )
    val currentPinchUpdate = rememberUpdatedState<(PinchSnapshot, Float, Float) -> Unit>(
        newValue = { snapshot, upperY, lowerY ->
            val update = updateVerticalPinch(snapshot, upperY, lowerY)
            pinchTargetScrollPx = update.viewport.scrollPx
            onHourHeightChange(with(density) { update.viewport.hourHeightPx.toDp().value })
            timeScroll.dispatchRawDelta(update.viewport.scrollPx - timeScroll.value)
        },
    )
    val verticalPinchOwner = remember {
        object : TimelineVerticalPinchOwner {
            override fun begin(upperY: Float, lowerY: Float): PinchSnapshot? =
                currentPinchBegin.value(upperY, lowerY)

            override fun update(snapshot: PinchSnapshot, upperY: Float, lowerY: Float) {
                currentPinchUpdate.value(snapshot, upperY, lowerY)
            }

            override fun end() {
                val targetScrollPx = pinchTargetScrollPx
                if (!targetScrollPx.isFinite()) return
                scope.launch {
                    withFrameNanos { }
                    timeScroll.scrollTo(targetScrollPx.roundToInt().coerceIn(0, timeScroll.maxValue))
                    pinchTargetScrollPx = Float.NaN
                }
            }
        }
    }
    LaunchedEffect(pinchTargetScrollPx, timeScroll.maxValue) {
        if (!pinchTargetScrollPx.isFinite()) return@LaunchedEffect
        val target = pinchTargetScrollPx.roundToInt().coerceIn(0, timeScroll.maxValue)
        timeScroll.dispatchRawDelta((target - timeScroll.value).toFloat())
    }
    var previousTimelineInsetPx by remember { mutableStateOf(0) }
    LaunchedEffect(timelineBottomInset, gridViewportHeightPx) {
        val insetPx = with(density) { timelineBottomInset.roundToPx() }
        val delta = insetPx - previousTimelineInsetPx
        if (delta != 0 && gridViewportHeightPx > 0 && timeScroll.maxValue > 0) {
            withFrameNanos { }
            timeScroll.animateScrollTo((timeScroll.value + delta).coerceIn(0, timeScroll.maxValue))
        }
        previousTimelineInsetPx = insetPx
    }

    LaunchedEffect(pagerState, fullWeekGestureState, fullWeekPagingEnabled) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                if (fullWeekPagingEnabled) fullWeekGestureState.onSettled(page)
                // Header morphs deliberately resize the pager columns. That internal relayout can
                // change settledPage even though the user did not horizontally scroll. Treating it
                // like a swipe is the legacy focus behavior that moves a tapped middle/right date
                // to the left before its 1-day morph. During a morph, the explicit handoff effect
                // above is the sole owner of selected-date updates.
                if (latestMorphContext != null) return@collect
                val target = programmaticTargetPage
                if (target != null) {
                    if (page == target) programmaticTargetPage = null
                    return@collect
                }
                val date = page.toDayDate()
                if (date != latestSelectedDate) {
                    onDateSelected(date)
                }
            }
    }

    // Tapping a day header morphs between 3-day and 1-day. We capture which day was tapped and
    // its column slot so the overlay can expand it FROM that position (not force it leftmost).
    val onHeaderTap: (LocalDate) -> Unit = { day ->
        if (isDayMode) {
            // Collapse to multi-day or Week while preserving this day's target column.
            val targetAnchor = if (state.weekViewEnabled) day.startOfWeek(state.firstDayOfWeek) else day
            val targetDays = (0 until multiDayCount).map { targetAnchor.plusDays(it.toLong()) }
            morphContext = DayMorphContext(
                days = targetDays,
                expandSlot = targetDays.indexOf(day).coerceAtLeast(0),
                targetMode = CalendarViewMode.ThreeDay,
                targetAnchorDate = targetAnchor,
            )
            scope.launch {
                // First paint the affine overlay at the untouched 1-day geometry. Starting the
                // width animation in the same frame as the tap lets pager/view state changes win
                // the first draw and creates a visible snap before the actual morph.
                withFrameNanos { }
                timelineMode = CalendarViewMode.ThreeDay
                onViewSelected(CalendarViewMode.ThreeDay)
            }
        } else {
            // Expand this day to the 1-day view, zooming out of its current column in place.
            val leftmost = visibleStartDate
            val slot = ChronoUnit.DAYS.between(leftmost, day).toInt().coerceIn(0, multiDayCount - 1)
            morphContext = DayMorphContext(
                days = (0 until multiDayCount).map { leftmost.plusDays(it.toLong()) },
                expandSlot = slot,
                targetMode = CalendarViewMode.Day,
                targetAnchorDate = day,
            )
            scope.launch {
                // Keep one frame of the original 3-day positions before beginning the local
                // animation. The persisted view setting follows the local mode instead of driving
                // the animation, so a tapped middle/right date cannot be focused left first.
                withFrameNanos { }
                timelineMode = CalendarViewMode.Day
                onViewSelected(CalendarViewMode.Day)
            }
        }
    }

    val todayDate = calendarTime.today
    val todayPage = todayDate.toDayPage()
    val currentLineGeometry = morphContext?.let { ctx ->
        val todaySlot = ctx.days.indexOf(todayDate)
        if (todaySlot >= 0 && dayGeometryViewportWidthPx > 0f) {
            val columnWidthMulti = ((dayGeometryViewportWidthPx - (multiDayCount - 1f) * daySpacingPx) / multiDayCount).coerceAtLeast(1f)
            val step = columnWidthMulti + daySpacingPx
            val zoom = 1f + (dayGeometryViewportWidthPx / columnWidthMulti - 1f) * morphProgress
            val e = ctx.expandSlot
            CurrentLineGeometry(
                leftPx = (todaySlot - e) * step * zoom + e * step * (1f - morphProgress),
                widthPx = columnWidthMulti * zoom,
            )
        } else {
            null
        }
    } ?: if (morphContext == null && dayViewportWidthPx > 0) {
        CurrentLineGeometry(
            leftPx = ((todayPage - pagerState.currentPage) - pagerState.currentPageOffsetFraction) * (dayWidthPx + daySpacingPx),
            widthPx = dayWidthPx,
        )
    } else {
        null
    }
    val currentLineVisible = currentLineGeometry?.let { geometry ->
        geometry.leftPx < dayViewportWidthPx && geometry.leftPx + geometry.widthPx > 0f
    } == true

    val timedDragReducer = remember(density) {
        TimelineDragReducer(
            entryMarginPx = with(density) { 12.dp.toPx() },
            exitMarginPx = with(density) { 16.dp.toPx() },
        )
    }
    val currentDragStart = rememberUpdatedState<(TimelineTimedDragStart) -> Unit>(
        newValue = dragStart@ { start ->
            if (morphContext != null || dayWidthPx <= 0f) return@dragStart
            val firstPage = pagerState.layoutInfo.visiblePagesInfo.minByOrNull { it.offset }
            val anchorPage = firstPage?.index ?: pagerState.currentPage
            val anchorOffsetPx = firstPage?.offset?.toFloat() ?: 0f
            val sidebarWidthPx = with(density) { TimeSidebarWidth.toPx() }
            val dayStepPx = dayWidthPx + daySpacingPx
            val rootOrigin = TimelineDragPoint(timelineRootOffset.x, timelineRootOffset.y)
            val sourcePageLeft = sidebarWidthPx + anchorOffsetPx +
                (start.item.sourceDate.toDayPage() - anchorPage) * dayStepPx
            val sourceLeft = start.cardBoundsInRoot.left - rootOrigin.x
            val layout = TimelineRootDragLayout(
                rootOrigin = rootOrigin,
                anchorPage = anchorPage,
                anchorOffsetPx = anchorOffsetPx,
                dayWidthPx = dayWidthPx,
                dayStepPx = dayStepPx,
                sidebarWidthPx = sidebarWidthPx,
                fixedAllDayBoundaryY = with(density) { (DayHeaderHeight + animatedAllDayHeight).toPx() },
                sourceInsetXPx = sourceLeft - sourcePageLeft,
                initialPointerYPx = start.pointerInRoot.y - rootOrigin.y,
                initialTimeScrollPx = timeScroll.value,
                sourceTopPx = start.cardBoundsInRoot.top - rootOrigin.y,
                sourceWidthPx = start.cardBoundsInRoot.width,
                sourceHeightPx = start.cardBoundsInRoot.height,
            )
            activeTimedDrag = ActiveTimelineTimedDrag(
                item = start.item,
                session = TimelineDragSession(
                    target = TimelineDropTarget.Timed(
                        date = start.item.sourceDate,
                        startMinute = start.item.startMinute,
                        endMinute = start.item.endMinute,
                    ),
                ),
                layout = layout,
            )
        },
    )
    val currentDragUpdate = rememberUpdatedState<(TimelineDragPoint) -> Unit>(
        newValue = dragUpdate@ { pointerInRoot ->
            val active = activeTimedDrag ?: return@dragUpdate
            if (active.awaitingCommit) return@dragUpdate
            val layout = active.layout
            val pointerX = pointerInRoot.x - layout.rootOrigin.x
            val pointerY = pointerInRoot.y - layout.rootOrigin.y
            val dayAreaX = pointerX - layout.sidebarWidthPx
            val pageDelta = floor((dayAreaX - layout.anchorOffsetPx) / layout.dayStepPx).toInt()
            val targetPage = (layout.anchorPage + pageDelta).coerceIn(0, DayPagerPageCount - 1)
            val targetDate = targetPage.toDayDate()
            val duration = (active.item.endMinute - active.item.startMinute)
                .coerceAtLeast(DraftMinDurationMinutes)
            val hourHeightPx = with(density) { hourHeightDp.dp.toPx() }.coerceAtLeast(1f)
            val pointerDeltaY = pointerY - layout.initialPointerYPx + (timeScroll.value - layout.initialTimeScrollPx)
            val minuteDelta = ((pointerDeltaY / hourHeightPx) * 60f).roundToInt().snapDraftMinute()
            val startMinute = (active.item.startMinute + minuteDelta)
                .coerceIn(DayStartHour * 60, (DayEndHour + 1) * 60 - duration)
            val timedTarget = TimelineDropTarget.Timed(
                date = targetDate,
                startMinute = startMinute,
                endMinute = startMinute + duration,
            )
            val reservation = allDayReservationFor(
                date = targetDate,
                events = state.events,
                tasks = calendarTasks,
                taskColorMode = state.taskColorMode,
            )
            val allDayTarget = TimelineDropTarget.AllDay(
                date = reservation.date,
                lane = reservation.lane,
            )
            activeTimedDrag = active.copy(
                session = timedDragReducer.update(
                    pointerY = pointerY,
                    boundaryY = layout.fixedAllDayBoundaryY,
                    timedTarget = timedTarget,
                    allDayTarget = allDayTarget,
                    previous = active.session,
                ),
            )
        },
    )
    val currentDragEnd = rememberUpdatedState<() -> Unit>(
        newValue = dragEnd@ {
            val active = activeTimedDrag ?: return@dragEnd
            activeTimedDrag = active.copy(awaitingCommit = true)
            when (val target = active.session.target) {
                is TimelineDropTarget.AllDay -> when (active.item.kind) {
                    TimelineDraggedItemKind.Event -> onEventMovedAllDay(
                        active.item.resourceHref,
                        active.item.occurrenceMillis,
                        target.date,
                    )

                    TimelineDraggedItemKind.Task -> onTaskMovedAllDay(
                        active.item.resourceHref,
                        active.item.occurrenceMillis,
                        target.date,
                    )
                }

                is TimelineDropTarget.Timed -> when (active.item.kind) {
                    TimelineDraggedItemKind.Event -> onEventMoved(
                        active.item.resourceHref,
                        active.item.occurrenceMillis,
                        target.date,
                        target.startMinute.toDraftLocalTime(),
                        target.endMinute.toDraftLocalTime(),
                    )

                    TimelineDraggedItemKind.Task -> onTaskMoved(
                        active.item.resourceHref,
                        active.item.occurrenceMillis,
                        target.date,
                        target.startMinute.toDraftLocalTime(),
                        target.endMinute.toDraftLocalTime(),
                    )
                }
            }
        },
    )
    val currentDragCancel = rememberUpdatedState<() -> Unit>(newValue = { activeTimedDrag = null })
    val timedDragReporter = remember {
        object : TimelineTimedDragReporter {
            override val usesRootOverlay: Boolean = true
            override fun start(start: TimelineTimedDragStart) = currentDragStart.value(start)
            override fun update(pointerInRoot: TimelineDragPoint) = currentDragUpdate.value(pointerInRoot)
            override fun end() = currentDragEnd.value()
            override fun cancel() = currentDragCancel.value()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { timelineRootOffset = it.positionInRoot() }
            .timelineVerticalPinch(verticalPinchOwner)
            .testTag("timeline-gesture-surface")
            // The WHOLE 1-day timeline — time bar, all-day band and grid together — is the shared
            // element for the month-cell <-> day morph, so the entire view scales up out of (and
            // back into) the tapped month cell as one unit. Only enabled in 1-day mode: 3-day uses
            // the affine overlay instead, and when this is disabled the modifier is a plain no-op.
            // No solid backing: the real elements (grid, time bar, cards) fade in from transparency
            // and the month cross-fades out underneath, so nothing flashes to a flat colour.
            .morphBounds("dayblock-$monthMorphDay", enabled = isDayMode)
            .clipToBounds(),
    ) {
        Row(Modifier.matchParentSize()) {
            Column(Modifier.width(TimeSidebarWidth)) {
                Box(
                    modifier = Modifier
                        .height(DayHeaderHeight)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = !isDayMode && !state.weekViewEnabled && state.multiDaySidebarControlsEnabled,
                        enter = slideInHorizontally(
                            initialOffsetX = { -it },
                            animationSpec = tween(280, easing = MotionEmphasized),
                        ) + fadeIn(animationSpec = tween(180, easing = MotionStandard)),
                        exit = slideOutHorizontally(
                            targetOffsetX = { -it },
                            animationSpec = tween(280, easing = MotionEmphasized),
                        ) + fadeOut(animationSpec = tween(180, easing = MotionStandard)),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        MultiDayCountControls(
                            dayCount = multiDayCount,
                            onDayCountChanged = onMultiDayCountChanged,
                        )
                    }
                }
                Spacer(Modifier.height(animatedAllDayHeight))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(timeScroll),
                ) {
                    Column {
                        TimeSidebar(
                            hours = DayStartHour..DayEndHour,
                            hourHeightDp = hourHeightDp,
                        )
                        if (timelineBottomInset > 0.dp) {
                            Spacer(Modifier.height(timelineBottomInset + 20.dp))
                        }
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { dayViewportWidthPx = it.width }
                    // While the affine morph plays, clip to this area so the columns sliding off the
                    // left edge disappear BEHIND the time bar instead of drawing over it. (Left
                    // un-clipped in steady state so a card can still be dragged across day edges.)
                    .then(if (morphContext != null) Modifier.clipToBounds() else Modifier),
            ) {
            // Enable per-item (event/task) morphs out of the month cell only for the real pager in
            // 1-day mode and only when NOT doing the 3-day affine morph — so the selected day's
            // cards pair up with the month pills, without the affine overlay's copies registering
            // duplicate keys.
            val itemMorphDay = if (morphContext == null && isDayMode) state.selectedDate else null
            CompositionLocalProvider(
                LocalMorphItemDay provides itemMorphDay,
                LocalTimedDragReporter provides timedDragReporter,
            ) {
            if (morphContext == null) {
                TimedGridViewportLayer(
                    visiblePages = pagerState.layoutInfo.visiblePagesInfo,
                    pageWidthPx = dayWidthPx,
                    hourHeightDp = hourHeightDp,
                    topOffset = DayHeaderHeight + animatedAllDayHeight,
                    scrollOffsetPx = timeScroll.value,
                    modifier = Modifier
                        .matchParentSize()
                        .zIndex(0f),
                )
            }
            HorizontalPager(
                state = pagerState,
                pageSize = PageSize.Fixed(dayWidthDp),
                pageSpacing = DayColumnSpacing,
                flingBehavior = if (fullWeekPagingEnabled) {
                    fullWeekFlingBehavior
                } else {
                    fallbackPagerFlingBehavior
                },
                // Keep both neighbours composed so they're ready to slide in when the 1-day
                // view widens out to 3-day, instead of popping in once they reach the viewport.
                beyondViewportPageCount = multiDayCount.coerceAtLeast(2),
                // Hidden (but kept composed, so scroll/zoom state survive) while a header-tap
                // morph plays — the affine overlay below renders that transition instead.
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(1f)
                    .alpha(if (morphContext != null) 0f else 1f),
            ) { page ->
                val day = page.toDayDate()
                Box(modifier = Modifier.zIndex(0f)) {
                DayPagerColumn(
                    day = day,
                    visibleStartDate = visibleStartDate,
                    visibleEndDate = visibleEndDate,
                    allDayHeight = animatedAllDayHeight,
                    hourHeightDp = hourHeightDp,
                    dayWidthPx = dayWidthPx,
                    taskColorMode = state.taskColorMode,
                    events = eventsByDay[day].orEmpty(),
                    tasks = tasksByDay[day].orEmpty(),
                    draftEvent = draftEvent?.takeIf { it.date == day },
                    onDraftEventChanged = onDraftEventChanged,
                    timeScroll = timeScroll,
                    bottomObscuredHeight = timelineBottomInset,
                    bodyScrollableState = bodyScrollableState,
                    onDateSelected = onDateSelected,
                    onDayHeaderClick = onHeaderTap,
                    onSlotSelected = onSlotSelected,
                    onDraftInteraction = onDraftInteraction,
                    onDraftTap = onDraftTap,
                    onTaskStatusChanged = onTaskStatusChanged,
                    onEventMoved = onEventMoved,
                    onTaskMoved = onTaskMoved,
                    onEventMovedAllDay = onEventMovedAllDay,
                    onTaskMovedAllDay = onTaskMovedAllDay,
                    onDetail = onDetail,
                    onGridViewportHeight = { gridViewportHeightPx = it },
                    drawTimedGrid = false,
                )
                }
            }
            }
            // In-place "zoom around the tapped column" overlay. While a header-tap morph plays it
            // replaces the hidden pager so the tapped day expands FROM its own position (the pager
            // can only ever anchor the leftmost page) and the other two columns slide off WITH
            // their events. At progress 0 it matches the 3-day pager exactly and at 1 the 1-day
            // pager, so swapping back to the real pager at either end is seamless. The expanding
            // column's rect is mapped to the full viewport; every column scales around it.
            morphContext?.let { ctx ->
                val columnWidthMulti = ((dayGeometryViewportWidthPx - (multiDayCount - 1f) * daySpacingPx) / multiDayCount).coerceAtLeast(1f)
                val step = columnWidthMulti + daySpacingPx
                val zoom = 1f + (dayGeometryViewportWidthPx / columnWidthMulti - 1f) * morphProgress
                val e = ctx.expandSlot
                ctx.days.forEachIndexed { slot, day ->
                    val leftPx = (slot - e) * step * zoom + e * step * (1f - morphProgress)
                    val columnWidthPx = columnWidthMulti * zoom
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(leftPx.roundToInt(), 0) }
                            .width(with(density) { columnWidthPx.toDp() })
                            .fillMaxHeight()
                            .zIndex(if (slot == e) 1f else 0f),
                    ) {
                        DayPagerColumn(
                            day = day,
                            visibleStartDate = day,
                            visibleEndDate = day,
                            allDayHeight = animatedAllDayHeight,
                            hourHeightDp = hourHeightDp,
                            dayWidthPx = columnWidthPx,
                            taskColorMode = state.taskColorMode,
                            events = eventsByDay[day].orEmpty(),
                            tasks = tasksByDay[day].orEmpty(),
                            draftEvent = draftEvent?.takeIf { it.date == day },
                            onDraftEventChanged = onDraftEventChanged,
                            timeScroll = timeScroll,
                            bottomObscuredHeight = timelineBottomInset,
                            bodyScrollableState = bodyScrollableState,
                            onDateSelected = onDateSelected,
                            onDayHeaderClick = onHeaderTap,
                            onSlotSelected = onSlotSelected,
                            onDraftInteraction = onDraftInteraction,
                            onDraftTap = onDraftTap,
                            onTaskStatusChanged = onTaskStatusChanged,
                            onEventMoved = onEventMoved,
                            onTaskMoved = onTaskMoved,
                            onEventMovedAllDay = onEventMovedAllDay,
                            onTaskMovedAllDay = onTaskMovedAllDay,
                            onDetail = onDetail,
                            onGridViewportHeight = {},
                        )
                    }
                }
                // The all-day band morphs WITH the columns: drive the same overlay off the affine
                // geometry — anchor at the first column's animated left edge, with the animated
                // gap between adjacent days as the step. Clipped to the day area by the parent, so
                // chips on days sliding off the left vanish behind the time bar like the columns.
                AllDayViewportOverlay(
                    events = state.events,
                    tasks = calendarTasks,
                    taskColorMode = state.taskColorMode,
                    anchorPage = ctx.days.first().toDayPage(),
                    anchorOffsetPx = -e * step * zoom + e * step * (1f - morphProgress),
                    dayWidthPx = columnWidthMulti * zoom,
                    dayStepPx = step * zoom,
                    viewportWidthPx = dayViewportWidthPx.toFloat(),
                    topOffset = DayHeaderHeight,
                    height = animatedAllDayHeight,
                    hourHeightDp = hourHeightDp,
                    timeScrollPx = timeScroll.value,
                    defaultEventDurationMinutes = state.defaultEventDurationMinutes,
                    draftEvent = draftEvent,
                    onDraftTap = onDraftTap,
                    onAllDaySlotSelected = onAllDaySlotSelected,
                    onEventMoved = onEventMoved,
                    onTaskMoved = onTaskMoved,
                    onEventMovedAllDay = onEventMovedAllDay,
                    onTaskMovedAllDay = onTaskMovedAllDay,
                    maxVisibleItems = state.maxVisibleAllDayItems,
                    expanded = allDayExpanded,
                    onExpandedChange = { allDayExpanded = it },
                    onTaskStatusChanged = onTaskStatusChanged,
                    onDetail = onDetail,
                    priorityPageCount = ctx.days.size,
                    reservation = visibleReservation,
                )
            }
            val firstVisiblePageInfo = pagerState.layoutInfo.visiblePagesInfo.minByOrNull { it.offset }
            // Normal pager-driven current-time line + all-day band, only when NOT morphing (during
            // the morph the affine all-day overlay above takes over and the time line is hidden).
            if (morphContext == null) {
                AllDayViewportOverlay(
                    events = state.events,
                    tasks = calendarTasks,
                    taskColorMode = state.taskColorMode,
                    anchorPage = firstVisiblePageInfo?.index ?: pagerState.currentPage,
                    anchorOffsetPx = firstVisiblePageInfo?.offset?.toFloat() ?: 0f,
                    dayWidthPx = dayWidthPx,
                    dayStepPx = dayWidthPx + daySpacingPx,
                    viewportWidthPx = dayViewportWidthPx.toFloat(),
                    topOffset = DayHeaderHeight,
                    height = animatedAllDayHeight,
                    hourHeightDp = hourHeightDp,
                    timeScrollPx = timeScroll.value,
                    defaultEventDurationMinutes = state.defaultEventDurationMinutes,
                    draftEvent = draftEvent,
                    onDraftTap = onDraftTap,
                    onAllDaySlotSelected = onAllDaySlotSelected,
                    onEventMoved = onEventMoved,
                    onTaskMoved = onTaskMoved,
                    onEventMovedAllDay = onEventMovedAllDay,
                    onTaskMovedAllDay = onTaskMovedAllDay,
                    maxVisibleItems = state.maxVisibleAllDayItems,
                    expanded = allDayExpanded,
                    onExpandedChange = { allDayExpanded = it },
                    onTaskStatusChanged = onTaskStatusChanged,
                    onDetail = onDetail,
                    priorityPageCount = targetDayCount,
                    reservation = visibleReservation,
                )
            }
            }
        }
        if (allDayHasOverflow || allDayExpanded) {
            Box(
                modifier = Modifier
                    .offset(y = DayHeaderHeight)
                    .width(TimeSidebarWidth)
                    .height(animatedAllDayHeight)
                    .padding(bottom = 4.dp)
                    .zIndex(14f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { allDayExpanded = !allDayExpanded },
                    ),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (allDayExpanded) stringResource(R.string.collapse_all_day_items) else stringResource(R.string.expand_all_day_items),
                    tint = WarmInk.copy(alpha = 0.82f),
                    modifier = Modifier
                        .size(28.dp)
                        .graphicsLayer {
                            rotationZ = allDayArrowRotation
                            val arrowScale = 0.94f + 0.06f * abs(allDayArrowRotation - 90f) / 90f
                            scaleX = arrowScale
                            scaleY = arrowScale
                        },
                )
            }
        }
        if (currentLineVisible && currentLineGeometry != null) {
            CurrentTimeLine(
                now = now,
                todayPageOffset = with(density) { currentLineGeometry.leftPx.toDp() },
                dayWidth = with(density) { currentLineGeometry.widthPx.toDp() },
                hourHeightDp = hourHeightDp,
                topOffset = DayHeaderHeight + animatedAllDayHeight,
                scrollOffset = with(density) { timeScroll.value.toDp() },
                modifier = Modifier
                    .matchParentSize()
                    .zIndex(30f),
            )
        }
        activeTimedDrag?.let { active ->
            TimelineTimedDragOverlay(
                active = active,
                hourHeightDp = hourHeightDp,
                timeScrollPx = timeScroll.value,
            )
        }
    }
}

@Composable
private fun MultiDayCountControls(
    dayCount: Int,
    onDayCountChanged: (Int) -> Unit,
) {
    val safeDayCount = dayCount.coerceMultiDayCount()
    val canIncrease = safeDayCount < MAX_MULTI_DAY_COUNT
    val canDecrease = safeDayCount > MIN_MULTI_DAY_COUNT
    val railShape = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 16.dp, bottomEnd = 16.dp)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(railShape)
            .background(multiDayCountRailColor())
            .fillMaxWidth()
            .padding(end = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        MultiDayCountIconButton(
            icon = Icons.Default.Add,
            contentDescription = stringResource(R.string.increase_multi_day_count),
            enabled = canIncrease,
            onClick = { onDayCountChanged(safeDayCount + 1) },
        )
        MultiDayCountIconButton(
            icon = Icons.Default.Remove,
            contentDescription = stringResource(R.string.decrease_multi_day_count),
            enabled = canDecrease,
            onClick = { onDayCountChanged(safeDayCount - 1) },
        )
    }
}

@Composable
private fun MultiDayCountIconButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val tint = if (enabled) WarmInk else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f)
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun multiDayCountRailColor(): Color =
    if (MaterialTheme.colorScheme.background.isDark()) {
        MaterialTheme.colorScheme.surface.blendWith(Color.White, 0.08f)
    } else {
        Color.White
    }

/** A header-tap morph in progress: which multi-day columns are involved and which one
 * expands to fill / collapses from the 1-day view. */
private data class DayMorphContext(
    val days: List<LocalDate>,
    val expandSlot: Int,
    val targetMode: CalendarViewMode,
    val targetAnchorDate: LocalDate,
)

private data class CurrentLineGeometry(
    val leftPx: Float,
    val widthPx: Float,
)

@Composable
private fun TimedGridViewportLayer(
    visiblePages: List<androidx.compose.foundation.pager.PageInfo>,
    pageWidthPx: Float,
    hourHeightDp: Float,
    topOffset: Dp,
    scrollOffsetPx: Int,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val gridColor = WarmGrid
    Canvas(modifier) {
        val topOffsetPx = with(density) { topOffset.toPx() }
        val rowHeight = with(density) { hourHeightDp.dp.toPx() }
        val cellGap = HourCellGap.toPx()
        val cellHeight = (rowHeight - cellGap).coerceAtLeast(0f)
        val cellInset = cellGap / 2f
        val radius = 10.dp.toPx()
        clipRect(left = 0f, top = topOffsetPx, right = size.width, bottom = size.height) {
            visiblePages.forEach { page ->
                val left = page.offset.toFloat()
                repeat(DayEndHour - DayStartHour + 1) { index ->
                    val top = topOffsetPx - scrollOffsetPx + index * rowHeight + cellInset
                    if (top > size.height || top + cellHeight < topOffsetPx) return@repeat
                    drawRoundRect(
                        color = gridColor,
                        topLeft = Offset(left, top),
                        size = Size(width = pageWidthPx, height = cellHeight),
                        cornerRadius = CornerRadius(radius, radius),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayHeader(day: LocalDate, selected: Boolean, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val compact = maxWidth < 44.dp
        val circleSize = if (compact) 32.dp else 40.dp
        val weekdayFont = if (compact) 11.sp else 14.sp
        val weekdayLine = if (compact) 13.sp else 16.sp
        val dayFont = if (compact) 17.sp else 22.sp
        val dayLine = if (compact) 19.sp else 24.sp
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                day.format(DateTimeFormatter.ofPattern("EE", LocalAppLocale.current)).replace(".", ""),
                color = if (selected) WarmBrown else WarmInk,
                fontWeight = FontWeight.Bold,
                fontSize = weekdayFont,
                lineHeight = weekdayLine,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
            Box(
                modifier = Modifier
                    .size(circleSize)
                    .clip(CircleShape)
                    .background(if (selected) WarmBrown else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    day.dayOfMonth.toString(),
                    color = if (selected) Color.White else WarmInk,
                    fontSize = dayFont,
                    lineHeight = dayLine,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Reports when a timed card inside a 3-day pager page starts/stops being dragged.
 * The pager parent uses this to boost the dragging page's zIndex so the card can
 * visually escape the page bounds — without this, dragging an event to a future
 * day renders behind the next page's background, since pager pages are drawn in
 * index order by default.
 */
internal val LocalTimedDragReporter = compositionLocalOf<TimelineTimedDragReporter> {
    NoOpTimelineTimedDragReporter
}

@Composable
private fun DayPagerColumn(
    day: LocalDate,
    visibleStartDate: LocalDate,
    visibleEndDate: LocalDate,
    allDayHeight: Dp,
    events: List<EventEntity>,
    tasks: List<TaskEntity>,
    draftEvent: DraftEventSelection?,
    onDraftEventChanged: (DraftEventSelection) -> Unit,
    onDraftInteraction: () -> Unit,
    onDraftTap: () -> Unit,
    hourHeightDp: Float,
    dayWidthPx: Float,
    timeScroll: androidx.compose.foundation.ScrollState,
    bottomObscuredHeight: Dp,
    bodyScrollableState: androidx.compose.foundation.gestures.ScrollableState,
    onDateSelected: (LocalDate) -> Unit,
    onDayHeaderClick: (LocalDate) -> Unit,
    onSlotSelected: (LocalDate, LocalTime) -> Unit,
    onTaskStatusChanged: (String, String) -> Unit,
    onEventMoved: (String, Long, LocalDate, LocalTime, LocalTime) -> Unit,
    onTaskMoved: (String, Long, LocalDate, LocalTime, LocalTime) -> Unit,
    onEventMovedAllDay: (String, Long, LocalDate) -> Unit,
    onTaskMovedAllDay: (String, Long, LocalDate) -> Unit,
    onDetail: (DetailSheet) -> Unit,
    onGridViewportHeight: (Int) -> Unit,
    taskColorMode: TaskColorMode,
    drawTimedGrid: Boolean = true,
) {
    // Just the day's own content (header + timed grid + cards). The month-cell <-> day morph is
    // now applied one level up, on the whole timeline Row (so the time bar and all-day band scale
    // out of the tapped cell too); the 3-day <-> 1-day morph is the affine overlay. So this column
    // no longer carries a shared element itself.
    val headerInteraction = remember(day) { MutableInteractionSource() }
    val density = LocalDensity.current
    var localGridViewportHeightPx by remember { mutableStateOf(0) }
    val bottomObscuredPx = with(density) { bottomObscuredHeight.roundToPx() }
    val effectiveGridViewportHeightPx = (localGridViewportHeightPx - bottomObscuredPx).coerceAtLeast(0)
    LaunchedEffect(effectiveGridViewportHeightPx) {
        if (effectiveGridViewportHeightPx > 0) onGridViewportHeight(effectiveGridViewportHeightPx)
    }
    Column(Modifier.fillMaxSize()) {
        DayHeader(
            day = day,
            selected = day == LocalCalendarTimeSnapshot.current.today,
            modifier = Modifier
                .zIndex(4f)
                .height(DayHeaderHeight)
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .clickable(
                    interactionSource = headerInteraction,
                    indication = null,
                    onClick = { onDayHeaderClick(day) },
                ),
        )
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(allDayHeight)
                .zIndex(4f),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .onSizeChanged {
                    localGridViewportHeightPx = it.height
                    onGridViewportHeight((it.height - bottomObscuredPx).coerceAtLeast(0))
                }
                .verticalClipAllowHorizontalOverflow()
                .scrollable(state = bodyScrollableState, orientation = Orientation.Vertical),
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(day, hourHeightDp, timeScroll.value) {
                        val rowHeightPx = with(density) { hourHeightDp.dp.toPx() }
                        detectTapGestures { offset ->
                            val scrolledY = offset.y + timeScroll.value
                            val totalVisibleMinutes = ((scrolledY / rowHeightPx) * 60f)
                                .roundToInt()
                                .snapDraftMinute()
                            val minuteOfDay = (DayStartHour * 60 + totalVisibleMinutes)
                                .coerceIn(DayStartHour * 60, (DayEndHour + 1) * 60 - DraftMinDurationMinutes)
                            onSlotSelected(day, minuteOfDay.toDraftLocalTime())
                        }
                    },
            )
            DayTimedColumn(
                day = day,
                hourHeightDp = hourHeightDp,
                dayWidthPx = dayWidthPx,
                taskColorMode = taskColorMode,
                events = events,
                tasks = tasks,
                draftEvent = draftEvent,
                onDraftEventChanged = onDraftEventChanged,
                onDraftInteraction = onDraftInteraction,
                onDraftTap = onDraftTap,
                timeScroll = timeScroll,
                gridViewportHeightPx = effectiveGridViewportHeightPx,
                onSlotSelected = onSlotSelected,
                onTaskStatusChanged = onTaskStatusChanged,
                onEventMoved = onEventMoved,
                onTaskMoved = onTaskMoved,
                onEventMovedAllDay = onEventMovedAllDay,
                onTaskMovedAllDay = onTaskMovedAllDay,
                onDetail = onDetail,
                drawGrid = drawTimedGrid,
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(0, -timeScroll.value) }
                    .height(((DayEndHour - DayStartHour + 1) * hourHeightDp).dp),
            )
        }
    }
}

private fun Modifier.timelineVerticalPinch(owner: TimelineVerticalPinchOwner): Modifier =
    pointerInput(owner) {
        awaitEachGesture {
            var snapshot: PinchSnapshot? = null
            var ownsGesture = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressed = event.changes.filter { it.pressed }
                if (!ownsGesture && pressed.size >= 2) {
                    val upperY = pressed.minOf { it.position.y }
                    val lowerY = pressed.maxOf { it.position.y }
                    snapshot = owner.begin(upperY, lowerY)
                    ownsGesture = snapshot != null
                }
                val currentSnapshot = snapshot
                if (ownsGesture && currentSnapshot != null && pressed.size >= 2) {
                    val upperY = pressed.minOf { it.position.y }
                    val lowerY = pressed.maxOf { it.position.y }
                    owner.update(currentSnapshot, upperY, lowerY)
                    event.changes.forEach { it.consume() }
                } else if (ownsGesture && pressed.size < 2) {
                    owner.end()
                    ownsGesture = false
                    snapshot = null
                }
                if (event.changes.none { it.pressed }) break
            }
        }
    }

@Composable
private fun TimeSidebar(hours: IntRange, hourHeightDp: Float) {
    val hourCount = hours.count()
    val textColor = WarmInk
    Box(
        modifier = Modifier
            .width(TimeSidebarWidth)
            .height((hourHeightDp * hourCount).dp),
    ) {
        Canvas(Modifier.matchParentSize()) {
            val rowHeight = hourHeightDp.dp.toPx()
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textColor.toArgb()
                textSize = 10.sp.toPx()
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
            }
            val baselineOffset = 11.sp.toPx()
            hours.forEachIndexed { index, hour ->
                drawContext.canvas.nativeCanvas.drawText(
                    "%02d:00".format(hour),
                    size.width / 2f,
                    index * rowHeight + baselineOffset,
                    paint,
                )
            }
        }
    }
}


@Composable
private fun TimelineTimedDragOverlay(
    active: ActiveTimelineTimedDrag,
    hourHeightDp: Float,
    timeScrollPx: Int,
) {
    val density = LocalDensity.current
    val layout = active.layout
    val target = active.session.target
    val targetPageLeft = layout.anchorOffsetPx +
        (target.date.toDayPage() - layout.anchorPage) * layout.dayStepPx
    val horizontalInsetPx = with(density) { 2.dp.toPx() }
    val targetX = when (target) {
        is TimelineDropTarget.AllDay -> layout.sidebarWidthPx + targetPageLeft + horizontalInsetPx
        is TimelineDropTarget.Timed -> layout.sidebarWidthPx + targetPageLeft + layout.sourceInsetXPx
    }
    val targetY = when (target) {
        is TimelineDropTarget.AllDay -> with(density) {
            (DayHeaderHeight + 7.dp + (target.lane * 29).dp).toPx()
        }

        is TimelineDropTarget.Timed -> layout.sourceTopPx +
            ((target.startMinute - active.item.startMinute) / 60f) * with(density) { hourHeightDp.dp.toPx() } -
            (timeScrollPx - layout.initialTimeScrollPx)
    }
    val targetWidth = when (target) {
        is TimelineDropTarget.AllDay -> (layout.dayWidthPx - horizontalInsetPx * 2f).coerceAtLeast(1f)
        is TimelineDropTarget.Timed -> layout.sourceWidthPx.coerceAtMost(layout.dayWidthPx).coerceAtLeast(1f)
    }
    val targetHeight = when (target) {
        is TimelineDropTarget.AllDay -> with(density) { 24.dp.toPx() }
        is TimelineDropTarget.Timed -> layout.sourceHeightPx
    }
    val animatedX by animateFloatAsState(
        targetValue = targetX,
        animationSpec = tween(MotionShort, easing = MotionEmphasized),
        label = "timedDragOverlayX",
    )
    val animatedY by animateFloatAsState(
        targetValue = targetY,
        animationSpec = tween(MotionShort, easing = MotionEmphasized),
        label = "timedDragOverlayY",
    )
    val animatedWidth by animateFloatAsState(
        targetValue = targetWidth,
        animationSpec = tween(MotionShort, easing = MotionEmphasized),
        label = "timedDragOverlayWidth",
    )
    val animatedHeight by animateFloatAsState(
        targetValue = targetHeight,
        animationSpec = tween(MotionShort, easing = MotionEmphasized),
        label = "timedDragOverlayHeight",
    )
    val color = Color(active.item.colorArgb)
    val contentColor = if (color.isDark()) Color.White else Color(0xFF1C1A18)
    val shape = RoundedCornerShape(if (target is TimelineDropTarget.AllDay) 8.dp else 10.dp)
    val cardHeightDp = with(density) { targetHeight.toDp().value }
    val timedTitleScale = ((cardHeightDp - if (active.item.kind == TimelineDraggedItemKind.Task) 18f else 13f) / 20f)
        .coerceIn(0f, 1f)
    val timedVerticalPadding = (1.5f + timedTitleScale * 1.5f).dp
    val timedTitleMaxLines = if (cardHeightDp >= 43f) 2 else 1
    val showLocation = target is TimelineDropTarget.Timed &&
        cardHeightDp >= (if (active.item.kind == TimelineDraggedItemKind.Task) 42f else 40f) &&
        active.item.location.isNotBlank()
    val locationMaxLines = when {
        cardHeightDp >= 86f -> 4
        cardHeightDp >= 68f -> 3
        cardHeightDp >= 52f -> 2
        else -> 1
    }
    Box(
        modifier = Modifier
            .offset { IntOffset(animatedX.roundToInt(), animatedY.roundToInt()) }
            .width(with(density) { animatedWidth.toDp() })
            .height(with(density) { animatedHeight.toDp() })
            .zIndex(100f)
            .shadow(7.dp, shape)
            .then(
                if (active.item.kind == TimelineDraggedItemKind.Task && !active.item.completed) {
                    Modifier.taskPriorityMotion(active.item.priority, color)
                } else {
                    Modifier
                },
            )
            .background(color, shape)
            .testTag("timeline-drag-overlay"),
    ) {
        if (target is TimelineDropTarget.AllDay) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (active.item.kind == TimelineDraggedItemKind.Task) {
                    Icon(
                        imageVector = if (active.item.completed) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = active.item.title,
                    color = contentColor,
                    fontSize = 12.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxSize().padding(start = if (active.item.kind == TimelineDraggedItemKind.Task) 5.dp else 6.dp),
                verticalAlignment = Alignment.Top,
            ) {
                if (active.item.kind == TimelineDraggedItemKind.Task) {
                    Icon(
                        imageVector = if (active.item.completed) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.padding(top = timedVerticalPadding).size(15.dp),
                    )
                    Spacer(Modifier.width(3.dp))
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .then(if (timedTitleMaxLines > 1 || showLocation) Modifier.bottomEdgeFadeMask() else Modifier)
                        .padding(top = timedVerticalPadding),
                ) {
                    FadingTimedText(
                        text = active.item.title,
                        color = contentColor,
                        fontSize = 12.sp,
                        lineHeight = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = timedTitleMaxLines,
                        textScale = 0.9f + timedTitleScale * 0.1f,
                        modifier = Modifier.testTag("timeline-drag-overlay-title"),
                    )
                    if (showLocation) {
                        FadingTimedText(
                            text = active.item.location,
                            color = contentColor.copy(alpha = 0.86f),
                            fontSize = 11.sp,
                            lineHeight = 12.sp,
                            maxLines = locationMaxLines,
                            modifier = Modifier.testTag("timeline-drag-overlay-location"),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CurrentTimeLine(
    now: LocalTime,
    todayPageOffset: Dp,
    dayWidth: Dp,
    hourHeightDp: Float,
    topOffset: Dp,
    scrollOffset: Dp,
    modifier: Modifier = Modifier,
) {
    if (now.hour !in DayStartHour..DayEndHour) return
    val minuteOffset = (now.hour * 60 + now.minute) - DayStartHour * 60
    val pillHeight = 18.dp
    val lineCenterY = topOffset + (minuteOffset / 60f * hourHeightDp).dp - scrollOffset
    val lineTopY = lineCenterY - (pillHeight / 2f)
    if (lineTopY < topOffset) return
    val darkPalette = MaterialTheme.colorScheme.background.isDark()
    val indicatorColor = if (darkPalette) Color.White else Color(0xFF1D1511)
    val pillBackground = if (darkPalette) Color.White.copy(alpha = 0.86f) else Color(0xFF1D1511).copy(alpha = 0.72f)
    val pillTextColor = if (darkPalette) Color(0xFF151515) else Color.White
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .offset(x = todayPageOffset)
                .offset(y = lineTopY)
                .width(TimeSidebarWidth)
                .height(pillHeight)
                .padding(horizontal = 2.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(pillBackground),
            contentAlignment = Alignment.Center,
        ) {
            Text(now.format(DateTimeFormatter.ofPattern("HH:mm")), color = pillTextColor, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Normal)
        }
        Box(
            modifier = Modifier
                .offset(x = TimeSidebarWidth + todayPageOffset, y = lineCenterY)
                .height(1.dp)
                .width(dayWidth)
                .background(indicatorColor.copy(alpha = if (darkPalette) 0.62f else 0.52f)),
        )
    }
}
