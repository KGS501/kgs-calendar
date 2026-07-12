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

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun KgsModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    initialSnap: SheetSnap = SheetSnap.Half,
    initialContentHeight: Dp? = null,
    dimBackground: Boolean = true,
    dismissOnOutsideTap: Boolean = true,
    collapseRequest: Int = 0,
    expandRequest: Int = 0,
    collapseSnap: SheetSnap = SheetSnap.Quarter,
    anchorMode: SheetAnchorMode = SheetAnchorMode.ContentFit,
    onSnapChanged: (SheetSnap) -> Unit = {},
    onBackRequest: (() -> Unit)? = null,
    separationShadow: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val quietInteraction = remember { MutableInteractionSource() }
    val view = LocalView.current
    val navigationBarColor = Color.Transparent.toArgb()

    DisposableEffect(view, navigationBarColor) {
        val window = view.context.findActivity()?.window
        val previousColor = window?.navigationBarColor
        window?.navigationBarColor = navigationBarColor
        onDispose {
            if (previousColor != null) {
                window.navigationBarColor = previousColor
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .then(modifier),
    ) {
            val screenHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
            val density = LocalDensity.current
            val sheetBottomPx = screenHeightPx
            val expandedAnchor = screenHeightPx * 0.065f
            val halfAnchor = screenHeightPx * 0.50f
            val hiddenAnchor = sheetBottomPx
            val navigationBarInsetPx = with(density) {
                WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().toPx()
            }
            val sheetVisualBottomPx = (hiddenAnchor - navigationBarInsetPx).coerceAtLeast(expandedAnchor + 1f)
            val quarterAnchor = (sheetVisualBottomPx - screenHeightPx * 0.18f).coerceIn(expandedAnchor, hiddenAnchor)
            val editorSmallAnchor = sheetVisualBottomPx - with(density) { EditorSmallVisibleHeight.toPx() }
            val editorTinyAnchor = sheetVisualBottomPx - with(density) { EditorTinyVisibleHeight.toPx() }
            val contentAnchor = initialContentHeight?.let { requestedHeight ->
                val minVisibleHeight = with(density) { 148.dp.toPx() }
                val sheetChromeAndBreathingRoom = with(density) { 104.dp.toPx() }
                val requestedHeightPx = (with(density) { requestedHeight.toPx() } + sheetChromeAndBreathingRoom)
                    .coerceIn(minVisibleHeight, sheetVisualBottomPx - expandedAnchor)
                (sheetVisualBottomPx - requestedHeightPx).coerceIn(expandedAnchor, sheetVisualBottomPx - minVisibleHeight)
            }
            var sheetOffsetPx by remember { mutableFloatStateOf(hiddenAnchor) }
            var edgeBouncePx by remember { mutableFloatStateOf(0f) }
            var edgeBounceJob by remember { mutableStateOf<Job?>(null) }
            var shown by remember { mutableStateOf(false) }
            var lastCollapseRequest by remember { mutableStateOf(collapseRequest) }
            var lastExpandRequest by remember { mutableStateOf(expandRequest) }
            val maxEdgeBouncePx = min(with(density) { 24.dp.toPx() }, expandedAnchor * 0.45f)

            fun clampedOffset(value: Float): Float = value.coerceIn(expandedAnchor, hiddenAnchor)

            fun dragSheetBy(deltaY: Float): Float {
                val previous = sheetOffsetPx
                edgeBouncePx = 0f
                edgeBounceJob?.cancel()
                sheetOffsetPx = clampedOffset(previous + deltaY)
                return sheetOffsetPx - previous
            }

            fun settleEdgeBounce(delayMillis: Long = 0L) {
                edgeBounceJob?.cancel()
                edgeBounceJob = scope.launch {
                    if (delayMillis > 0L) delay(delayMillis)
                    animate(
                        initialValue = edgeBouncePx,
                        targetValue = 0f,
                        animationSpec = tween(MotionShort, easing = MotionEmphasized),
                    ) { value, _ ->
                        edgeBouncePx = value
                    }
                }
            }

            fun bounceAtExpandedEdge(deltaY: Float): Float {
                if (sheetOffsetPx > expandedAnchor + 0.5f || deltaY >= 0f || maxEdgeBouncePx <= 0f) return 0f
                edgeBounceJob?.cancel()
                edgeBouncePx = (edgeBouncePx + deltaY * 0.22f).coerceIn(-maxEdgeBouncePx, 0f)
                settleEdgeBounce(delayMillis = 80L)
                return deltaY
            }

            fun anchorFor(snap: SheetSnap): Float = when (snap) {
                SheetSnap.Expanded -> expandedAnchor
                SheetSnap.Half -> halfAnchor
                SheetSnap.Quarter -> quarterAnchor
                SheetSnap.EditorSmall -> editorSmallAnchor
                SheetSnap.EditorTiny -> editorTinyAnchor
            }

            fun sheetAnchors(): List<Float> =
                when (anchorMode) {
                    SheetAnchorMode.ContentFit -> listOfNotNull(expandedAnchor, contentAnchor, hiddenAnchor)
                    SheetAnchorMode.Editor -> listOf(expandedAnchor, editorSmallAnchor, editorTinyAnchor, hiddenAnchor)
                }
                    .distinctBy { (it / 4f).roundToInt() }
                    .sorted()

            fun snapForAnchor(anchor: Float): SheetSnap = when {
                abs(anchor - expandedAnchor) < 2f -> SheetSnap.Expanded
                abs(anchor - editorSmallAnchor) < 2f -> SheetSnap.EditorSmall
                abs(anchor - editorTinyAnchor) < 2f -> SheetSnap.EditorTiny
                abs(anchor - halfAnchor) < 2f -> SheetSnap.Half
                else -> SheetSnap.Quarter
            }

            fun targetAnchor(velocityY: Float = 0f): Float {
                val anchors = sheetAnchors()
                if (velocityY < -850f) {
                    return anchors.lastOrNull { it < sheetOffsetPx - 8f } ?: expandedAnchor
                }
                if (velocityY > 850f) {
                    return anchors.firstOrNull { it > sheetOffsetPx + 8f } ?: hiddenAnchor
                }
                return anchors.minBy { abs(it - sheetOffsetPx) }
            }

            suspend fun animateSheetTo(target: Float, dismissAfter: Boolean = false) {
                val clampedTarget = clampedOffset(target)
                animate(
                    initialValue = sheetOffsetPx,
                    targetValue = clampedTarget,
                    animationSpec = tween(MotionMedium, easing = MotionEmphasized),
                ) { value, _ ->
                    sheetOffsetPx = clampedOffset(value)
                }
                if (dismissAfter || clampedTarget == hiddenAnchor) {
                    onDismissRequest()
                } else {
                    onSnapChanged(snapForAnchor(clampedTarget))
                }
            }

            fun settleSheet(velocityY: Float = 0f) {
                scope.launch { animateSheetTo(targetAnchor(velocityY)) }
            }

            fun closeSheet() {
                scope.launch { animateSheetTo(hiddenAnchor, dismissAfter = true) }
            }

            BackHandler(enabled = true) {
                onBackRequest?.invoke() ?: closeSheet()
            }

            LaunchedEffect(screenHeightPx, initialSnap, initialContentHeight) {
                if (!shown) {
                    sheetOffsetPx = hiddenAnchor
                    shown = true
                    animateSheetTo(contentAnchor ?: anchorFor(initialSnap))
                } else {
                    sheetOffsetPx = clampedOffset(sheetOffsetPx)
                }
            }

            LaunchedEffect(collapseRequest) {
                val shouldCollapse = collapseRequest != lastCollapseRequest
                lastCollapseRequest = collapseRequest
                if (shown && shouldCollapse) {
                    animateSheetTo(anchorFor(collapseSnap))
                }
            }

            LaunchedEffect(expandRequest) {
                val shouldExpand = expandRequest != lastExpandRequest
                lastExpandRequest = expandRequest
                if (shown && shouldExpand) {
                    animateSheetTo(expandedAnchor)
                }
            }

            val sheetNestedScrollConnection = remember(screenHeightPx) {
                object : NestedScrollConnection {
                    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                        if (
                            anchorMode == SheetAnchorMode.Editor &&
                            source == NestedScrollSource.UserInput &&
                            available.y < 0f &&
                            sheetOffsetPx > expandedAnchor + 0.5f
                        ) {
                            return Offset(x = 0f, y = dragSheetBy(available.y))
                        }
                        return Offset.Zero
                    }

                    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                        if (source != NestedScrollSource.UserInput) return Offset.Zero
                        val deltaY = available.y
                        return when {
                            deltaY > 0f && sheetOffsetPx < hiddenAnchor -> Offset(x = 0f, y = dragSheetBy(deltaY))
                            deltaY < 0f && sheetOffsetPx > expandedAnchor -> Offset(x = 0f, y = dragSheetBy(deltaY))
                            deltaY < 0f && sheetOffsetPx <= expandedAnchor + 0.5f -> Offset(x = 0f, y = bounceAtExpandedEdge(deltaY))
                            else -> Offset.Zero
                        }
                    }

                    override suspend fun onPreFling(available: Velocity): Velocity {
                        return Velocity.Zero
                    }

                    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                        if (abs(consumed.y) > 50f) {
                            return Velocity.Zero
                        }
                        if (available.y < 0f && sheetOffsetPx <= expandedAnchor + 0.5f && maxEdgeBouncePx > 0f) {
                            edgeBounceJob?.cancel()
                            edgeBouncePx = (-min(maxEdgeBouncePx, max(8f, abs(available.y) * 0.012f))).coerceIn(-maxEdgeBouncePx, 0f)
                            settleEdgeBounce()
                            return available
                        }
                        return if (available.y != 0f || sheetAnchors().none { abs(it - sheetOffsetPx) < 1f }) {
                            val target = if (sheetOffsetPx <= expandedAnchor + 0.5f && available.y < 0f) {
                                expandedAnchor
                            } else {
                                targetAnchor(available.y)
                            }
                            animateSheetTo(target)
                            available
                        } else {
                            Velocity.Zero
                        }
                    }
                }
            }

            val openProgress = ((hiddenAnchor - sheetOffsetPx) / (hiddenAnchor - quarterAnchor)).coerceIn(0f, 1f)
            val effectiveSheetOffsetPx = (sheetOffsetPx + edgeBouncePx).coerceIn(0f, hiddenAnchor)
            val sheetHeight = with(density) { (sheetBottomPx - effectiveSheetOffsetPx).coerceAtLeast(1f).toDp() }
            val cornerRadius = 28.dp

            if (dimBackground || dismissOnOutsideTap) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(if (dimBackground) openProgress else 1f)
                        .then(if (dimBackground) Modifier.background(Color.Black.copy(alpha = 0.38f)) else Modifier)
                        .then(
                            if (dismissOnOutsideTap) {
                                Modifier.clickable(
                                    interactionSource = quietInteraction,
                                    indication = null,
                                    onClick = ::closeSheet,
                                )
                            } else {
                                Modifier
                            },
                        ),
                )
            }

            val sheetDragModifier = Modifier.draggable(
                state = rememberDraggableState { delta -> dragSheetBy(delta) },
                orientation = Orientation.Vertical,
                onDragStopped = { velocity -> settleSheet(velocity) },
            )
            val sheetHeaderModifier = Modifier
                .pointerInput(screenHeightPx) {
                    detectTapGestures(
                        onTap = {
                            scope.launch { animateSheetTo(expandedAnchor) }
                        },
                    )
                }
                .then(sheetDragModifier)

            val separationShadowDark = MaterialTheme.colorScheme.background.isDark()
            if (separationShadow) {
                val shadowHeight = if (separationShadowDark) 42.dp else 34.dp
                val shadowHeightPx = with(density) { shadowHeight.toPx() }
                val cornerRadiusPx = with(density) { cornerRadius.toPx() }
                val topAlpha = if (separationShadowDark) 0.2f else 0.075f
                val bottomAlpha = if (separationShadowDark) 0.36f else 0.15f
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(shadowHeight + cornerRadius)
                        .offset {
                            IntOffset(
                                x = 0,
                                y = (effectiveSheetOffsetPx - shadowHeightPx).roundToInt(),
                            )
                        },
                ) {
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.42f to Color.Black.copy(alpha = topAlpha),
                            1f to Color.Black.copy(alpha = bottomAlpha),
                        ),
                        topLeft = Offset.Zero,
                        size = Size(width = size.width, height = size.height + cornerRadiusPx),
                        cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sheetHeight)
                    .offset { IntOffset(x = 0, y = effectiveSheetOffsetPx.roundToInt()) }
                    .then(
                        if (separationShadow) {
                            Modifier.shadow(
                                elevation = if (separationShadowDark) 30.dp else 18.dp,
                                shape = RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius),
                                clip = false,
                                ambientColor = Color.Black.copy(alpha = if (separationShadowDark) 0.24f else 0.13f),
                                spotColor = Color.Black.copy(alpha = if (separationShadowDark) 0.28f else 0.17f),
                            )
                        } else {
                            Modifier
                        },
                    )
                    .nestedScroll(sheetNestedScrollConnection),
                shape = RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius),
                color = containerColor,
                shadowElevation = 0.dp,
            ) {
                CompositionLocalProvider(LocalOverscrollFactory provides null) {
                    CompositionLocalProvider(LocalSheetHeaderDragModifier provides sheetHeaderModifier) {
                    Column(Modifier.fillMaxSize()) {
                        KgsSheetHandle(
                            modifier = sheetHeaderModifier,
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        ) {
                            content()
                        }
                    }
                    }
                }
            }
        }
}

