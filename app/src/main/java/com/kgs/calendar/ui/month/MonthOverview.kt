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
import com.kgs.calendar.ui.month.MonthGestureAxis
import com.kgs.calendar.ui.month.MonthOverviewGestureReducer
import com.kgs.calendar.ui.month.MonthOverviewGestureState
import com.kgs.calendar.ui.month.MonthSettleTarget
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
internal fun MonthOverview(
    month: YearMonth,
    state: CalendarUiState,
    firstDayOfWeek: DayOfWeek,
    onDaySelected: (LocalDate) -> Unit,
    onMonthSelected: (YearMonth) -> Unit,
    onMonthOffset: (Long) -> Unit,
    onDismissDrag: (Float) -> Unit,
    onDismissDragEnd: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val dragX = remember { Animatable(0f) }
    var displayedMonthText by rememberSaveable { mutableStateOf(month.toString()) }
    val displayedMonth = remember(displayedMonthText) { YearMonth.parse(displayedMonthText) }
    var transitionTargetMonthText by remember { mutableStateOf<String?>(null) }
    val transitionTargetMonth = remember(transitionTargetMonthText) {
        transitionTargetMonthText?.let(YearMonth::parse)
    }
    var transitionDirection by remember { mutableFloatStateOf(1f) }
    var suppressNextExternalMonthAnimation by remember { mutableStateOf(false) }
    var monthViewportWidthPx by remember { mutableFloatStateOf(0f) }
    var monthDragVelocityX by remember { mutableFloatStateOf(0f) }
    var monthDragY by remember { mutableFloatStateOf(0f) }
    var lastMonthDragNanos by remember { mutableStateOf(0L) }
    var monthGestureMoved by remember { mutableStateOf(false) }
    var gestureState by remember { mutableStateOf(MonthOverviewGestureState()) }
    var interruptedSettle by remember { mutableStateOf<MonthSettleTarget?>(null) }
    val gestureReducer = remember { MonthOverviewGestureReducer() }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val locale = LocalAppLocale.current
    val monthFormatter = remember(locale) { DateTimeFormatter.ofPattern("MMM", locale) }

    LaunchedEffect(month) {
        if (month.toString() == displayedMonthText) return@LaunchedEffect
        if (suppressNextExternalMonthAnimation || monthViewportWidthPx <= 0f) {
            suppressNextExternalMonthAnimation = false
            displayedMonthText = month.toString()
            dragX.snapTo(0f)
            return@LaunchedEffect
        }
        val direction = if (ChronoUnit.MONTHS.between(displayedMonth, month) >= 0) 1f else -1f
        transitionDirection = direction
        transitionTargetMonthText = month.toString()
        dragX.stop()
        dragX.snapTo(0f)
        dragX.animateTo(
            targetValue = -direction * monthViewportWidthPx,
            animationSpec = tween(230, easing = MotionEmphasized),
        )
        displayedMonthText = month.toString()
        transitionTargetMonthText = null
        dragX.snapTo(0f)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(25f)
            .background(MaterialTheme.colorScheme.background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            )
            .padding(top = 8.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Column(
            modifier = Modifier
                .testTag("month-overview-grid")
                .pointerInput(monthViewportWidthPx) {
                    detectDragGestures(
                        onDragStart = {
                            settleJob?.cancel()
                            val displayedAtStart = YearMonth.parse(displayedMonthText)
                            val pendingTarget = transitionTargetMonthText?.let(YearMonth::parse)
                            interruptedSettle = pendingTarget?.let { target ->
                                MonthSettleTarget(displayedAtStart, target)
                            }
                            pendingTarget?.let { target ->
                                displayedMonthText = target.toString()
                                transitionTargetMonthText = null
                                suppressNextExternalMonthAnimation = true
                                onMonthSelected(target)
                            }
                            gestureState = MonthOverviewGestureState()
                            monthDragVelocityX = 0f
                            monthDragY = 0f
                            monthGestureMoved = false
                            lastMonthDragNanos = System.nanoTime()
                            scope.launch { dragX.stop(); dragX.snapTo(0f) }
                        },
                        onDrag = { change, dragAmount ->
                            gestureState = gestureReducer.update(
                                delta = dragAmount,
                                touchSlop = viewConfiguration.touchSlop,
                                state = gestureState,
                            )
                            if (gestureState.axis == MonthGestureAxis.Undecided) return@detectDragGestures
                            change.consume()
                            monthGestureMoved = true
                            if (gestureState.axis == MonthGestureAxis.Vertical) {
                                monthDragY += dragAmount.y
                                if (monthDragY < 0f) onDismissDrag(dragAmount.y)
                            } else {
                                val now = System.nanoTime()
                                val seconds = ((now - lastMonthDragNanos).coerceAtLeast(1L) / 1_000_000_000f)
                                    .coerceAtLeast(0.001f)
                                monthDragVelocityX = dragAmount.x / seconds
                                lastMonthDragNanos = now
                                scope.launch {
                                    dragX.snapTo(
                                        (dragX.value + dragAmount.x)
                                            .coerceIn(-monthViewportWidthPx, monthViewportWidthPx),
                                    )
                                }
                            }
                        },
                        onDragEnd = {
                            if (gestureState.axis == MonthGestureAxis.Vertical) {
                                onDismissDragEnd()
                                monthGestureMoved = false
                            } else {
                                val threshold = (monthViewportWidthPx * 0.18f).coerceAtLeast(32f)
                                val targetOffset = when {
                                    monthDragVelocityX < -620f || dragX.value < -threshold -> -monthViewportWidthPx
                                    monthDragVelocityX > 620f || dragX.value > threshold -> monthViewportWidthPx
                                    else -> 0f
                                }
                                val monthDelta = when {
                                    targetOffset < 0f -> 1L
                                    targetOffset > 0f -> -1L
                                    else -> 0L
                                }
                                val settleAtStart = interruptedSettle
                                val targetMonth = when {
                                    monthDelta == 0L -> null
                                    settleAtStart != null -> gestureReducer.interrupt(
                                        settling = settleAtStart,
                                        deltaMonths = monthDelta,
                                    ).targetMonth
                                    else -> YearMonth.parse(displayedMonthText).plusMonths(monthDelta)
                                }
                                interruptedSettle = null
                                transitionTargetMonthText = targetMonth?.toString()
                                transitionDirection = if (targetOffset < 0f) 1f else -1f
                                settleJob = scope.launch {
                                    dragX.animateTo(targetOffset, tween(MotionMedium, easing = MotionEmphasized))
                                    if (targetMonth != null) {
                                        displayedMonthText = targetMonth.toString()
                                        suppressNextExternalMonthAnimation = true
                                        onMonthSelected(targetMonth)
                                    }
                                    transitionTargetMonthText = null
                                    dragX.snapTo(0f)
                                    monthGestureMoved = false
                                }
                            }
                        },
                        onDragCancel = {
                            interruptedSettle = null
                            onDismissDragEnd()
                            monthGestureMoved = false
                            settleJob = scope.launch {
                                dragX.animateTo(0f, tween(MotionShort, easing = MotionStandard))
                            }
                        },
                    )
                },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                weekHeaderLabels(firstDayOfWeek).forEach { label ->
                    Text(
                        text = label,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(month.monthGridHeight(firstDayOfWeek))
                    .clipToBounds(),
            ) {
                val density = LocalDensity.current
                val widthPx = with(density) { maxWidth.toPx() }
                LaunchedEffect(widthPx) { monthViewportWidthPx = widthPx }
                val visibleMonths = transitionTargetMonth?.let { target ->
                    listOf(displayedMonth to 0f, target to transitionDirection)
                } ?: listOf(
                    displayedMonth.minusMonths(1) to -1f,
                    displayedMonth to 0f,
                    displayedMonth.plusMonths(1) to 1f,
                )
                visibleMonths.forEach { (visibleMonth, slotOffset) ->
                    Box(
                        modifier = Modifier
                            .width(maxWidth)
                            .offset { IntOffset((slotOffset * widthPx + dragX.value).roundToInt(), 0) },
                    ) {
                        MonthGrid(
                            month = visibleMonth,
                            markersByDay = monthMarkersFor(
                                visibleMonth,
                                state.events,
                                if (state.showCompletedTasksInCalendar) {
                                    state.datedTasks
                                } else {
                                    state.datedTasks.filterNot { it.isCompleted }
                                },
                                state.taskColorMode,
                            ),
                            firstDayOfWeek = firstDayOfWeek,
                            selectedStart = state.selectedDate,
                            daysEnabled = !monthGestureMoved &&
                                abs(dragX.value) < 1f &&
                                transitionTargetMonth == null,
                            onDaySelected = onDaySelected,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        MonthStrip(
            month = month,
            formatter = monthFormatter,
            onMonthSelected = onMonthSelected,
        )
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    markersByDay: Map<LocalDate, MonthDayMarkers>,
    firstDayOfWeek: DayOfWeek,
    selectedStart: LocalDate,
    daysEnabled: Boolean = true,
    onDaySelected: (LocalDate) -> Unit,
) {
    val firstDay = month.atDay(1)
    val leadingEmptyDays = firstDay.leadingDaysFrom(firstDayOfWeek)
    val rowCount = month.monthGridRowCount(firstDayOfWeek)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        repeat(rowCount) { row ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { column ->
                    val dayIndex = row * 7 + column - leadingEmptyDays + 1
                    if (dayIndex in 1..month.lengthOfMonth()) {
                        val day = month.atDay(dayIndex)
                        val markers = markersByDay[day]
                        MonthDayCell(
                            day = day,
                            colors = markers?.colors.orEmpty().map(::Color),
                            hasMore = markers?.hasMore == true,
                            isToday = day == LocalCalendarTimeSnapshot.current.today,
                            isSelectedStart = day == selectedStart,
                            enabled = daysEnabled,
                            onClick = { onDaySelected(day) },
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(Modifier.weight(1f).height(44.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthDayCell(
    day: LocalDate,
    colors: List<Color>,
    hasMore: Boolean,
    isToday: Boolean,
    isSelectedStart: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = when {
            isToday -> WarmBrown
            isSelectedStart -> WarmPeach
            else -> Color.Transparent
    }
    val textColor = if (isToday) accentContainerContentColor() else WarmInk
    Column(
        modifier = modifier
            .testTag("month-overview-day-$day")
            .height(44.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(background)
                .then(
                    if (isSelectedStart && !isToday) {
                        Modifier.border(1.2.dp, WarmBrown.copy(alpha = 0.58f), CircleShape)
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = day.dayOfMonth.toString(),
                color = textColor,
                fontSize = 14.sp,
                lineHeight = 16.sp,
                fontWeight = if (isToday || isSelectedStart) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
        Row(
            modifier = Modifier.height(8.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            colors.forEach { color ->
                Box(
                    Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(color),
                )
            }
            if (hasMore) {
                Text(
                    "+",
                    color = WarmInk,
                    fontSize = 8.sp,
                    lineHeight = 8.sp,
                    modifier = Modifier.offset(y = (-2).dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MonthStrip(
    month: YearMonth,
    formatter: DateTimeFormatter,
    onMonthSelected: (YearMonth) -> Unit,
) {
    val selectedPage = month.toMonthPage()
    val locale = LocalAppLocale.current
    val density = LocalDensity.current
    val edgeScrollOffsetPx = with(density) { 20.dp.roundToPx() }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (selectedPage - 2).coerceIn(0, MonthStripPageCount - 1),
        initialFirstVisibleItemScrollOffset = edgeScrollOffsetPx,
    )

    LaunchedEffect(selectedPage, edgeScrollOffsetPx) {
        val target = (selectedPage - 2).coerceIn(0, MonthStripPageCount - 1)
        if (listState.firstVisibleItemIndex != target || abs(listState.firstVisibleItemScrollOffset - edgeScrollOffsetPx) > 2) {
            listState.animateScrollToItem(
                index = target,
                scrollOffset = edgeScrollOffsetPx,
            )
        }
    }

    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        contentPadding = PaddingValues(horizontal = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(top = 5.dp, bottom = 5.dp)
            .horizontalEdgeFade(edgeWidth = 8.dp),
    ) {
        items(MonthStripPageCount, key = { it }) { page ->
        val itemMonth = page.toMonth()
        val selected = itemMonth == month
        Row(
            modifier = Modifier
                .height(34.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        ) {
            if (itemMonth.monthValue == 1) {
                Text(
                    itemMonth.year.toString(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .align(Alignment.CenterVertically),
                )
            }
            Surface(
                onClick = { onMonthSelected(itemMonth) },
                modifier = Modifier.height(33.dp),
                shape = RoundedCornerShape(16.dp),
                color = if (selected) WarmPeach else MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = itemMonth.shortMonthLabel(formatter, locale),
                        color = WarmInk,
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
        }
    }
}

@Composable
internal fun YearStrip(
    selectedYear: Int,
    onYearSelected: (Int) -> Unit,
) {
    val selectedIndex = (selectedYear - YearStripBase).coerceIn(0, YearStripPageCount - 1)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (selectedIndex - 2).coerceAtLeast(0))
    LaunchedEffect(selectedIndex) {
        listState.animateScrollToItem((selectedIndex - 2).coerceAtLeast(0))
    }
    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(MaterialTheme.colorScheme.background)
            .horizontalEdgeFade(edgeWidth = 12.dp),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(YearStripPageCount, key = { it }) { index ->
            val year = YearStripBase + index
            val selected = year == selectedYear
            Surface(
                onClick = { onYearSelected(year) },
                modifier = Modifier.height(36.dp),
                shape = RoundedCornerShape(18.dp),
                color = if (selected) WarmPeach else MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                border = BorderStroke(1.dp, if (selected) WarmBrown.copy(alpha = 0.42f) else WarmLine.copy(alpha = 0.62f)),
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 13.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        year.toString(),
                        color = WarmInk,
                        fontSize = 14.sp,
                        lineHeight = 17.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}
