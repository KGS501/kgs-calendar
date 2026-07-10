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
internal fun DayTimedColumn(
    day: LocalDate,
    hourHeightDp: Float,
    dayWidthPx: Float,
    taskColorMode: TaskColorMode,
    events: List<EventEntity>,
    tasks: List<TaskEntity>,
    draftEvent: DraftEventSelection?,
    onDraftEventChanged: (DraftEventSelection) -> Unit,
    onDraftInteraction: () -> Unit,
    onDraftTap: () -> Unit,
    timeScroll: androidx.compose.foundation.ScrollState,
    gridViewportHeightPx: Int,
    onSlotSelected: (LocalDate, LocalTime) -> Unit,
    onTaskStatusChanged: (String, String) -> Unit,
    onEventMoved: (String, Long, LocalDate, LocalTime, LocalTime) -> Unit,
    onTaskMoved: (String, Long, LocalDate, LocalTime, LocalTime) -> Unit,
    onEventMovedAllDay: (String, Long, LocalDate) -> Unit,
    onTaskMovedAllDay: (String, Long, LocalDate) -> Unit,
    onDetail: (DetailSheet) -> Unit,
    modifier: Modifier = Modifier,
    drawGrid: Boolean = true,
) {
    val hourCount = DayEndHour - DayStartHour + 1
    val gridColor = WarmGrid
    val timedItems = remember(day, events, tasks, hourHeightDp) {
        layoutTimedItemsForDay(
            day = day,
            hourHeightDp = hourHeightDp,
            events = events.filter { !it.allDay },
            tasks = tasks,
        )
    }
    Box(modifier = modifier) {
        if (drawGrid) {
            Box(
                Modifier
                    .matchParentSize()
                    .drawBehind {
                        val rowHeight = hourHeightDp.dp.toPx()
                        val cellHeight = (rowHeight - HourCellGap.toPx()).coerceAtLeast(0f)
                        val cellInset = HourCellGap.toPx() / 2f
                        val radius = 10.dp.toPx()
                        repeat(hourCount) { index ->
                            val top = index * rowHeight + cellInset
                            val cellSize = Size(width = size.width, height = cellHeight)
                            val cornerRadius = CornerRadius(radius, radius)
                            drawRoundRect(
                                color = gridColor,
                                topLeft = Offset(0f, top),
                                size = cellSize,
                                cornerRadius = cornerRadius,
                            )
                        }
                    },
            )
        }
        timedItems.forEach { layout ->
            when (val item = layout.item) {
                is TimedCalendarItem.EventItem -> TimedEventBlock(
                    event = item.event,
                    placement = layout.placement,
                    lane = layout.lane,
                    laneCount = layout.laneCount,
                    day = day,
                    hourHeightDp = hourHeightDp,
                    color = item.event.displayColor(),
                    dayWidthPx = dayWidthPx,
                    onMove = { targetDate, start, end -> onEventMoved(item.event.resourceHref, item.event.startsAtMillis, targetDate, start, end) },
                    onMoveAllDay = { targetDate -> onEventMovedAllDay(item.event.resourceHref, item.event.startsAtMillis, targetDate) },
                    onClick = { onDetail(DetailSheet.Event(item.event)) },
                )
                is TimedCalendarItem.TaskItem -> TimedTaskBlock(
                    task = item.task,
                    placement = layout.placement,
                    lane = layout.lane,
                    laneCount = layout.laneCount,
                    day = day,
                    hourHeightDp = hourHeightDp,
                    color = item.task.displayColor(taskColorMode),
                    dayWidthPx = dayWidthPx,
                    onTaskStatusChanged = onTaskStatusChanged,
                    onMove = { targetDate, start, end ->
                        onTaskMoved(
                            item.task.resourceHref,
                            item.task.startAtMillis ?: item.task.dueAtMillis ?: System.currentTimeMillis(),
                            targetDate,
                            start,
                            end,
                        )
                    },
                    onMoveAllDay = { targetDate ->
                        onTaskMovedAllDay(
                            item.task.resourceHref,
                            item.task.startAtMillis ?: item.task.dueAtMillis ?: System.currentTimeMillis(),
                            targetDate,
                        )
                    },
                    onClick = { onDetail(DetailSheet.Task(item.task)) },
                )
            }
        }
        draftEvent?.takeIf { !it.allDay }?.let { draft ->
            DraftEventWireframe(
                draft = EditorSchedulePreview(draft.date, draft.start, draft.end, draft.allDay),
                color = draft.color,
                hourHeightDp = hourHeightDp,
                dayWidthPx = dayWidthPx,
                onDraftChanged = { changed ->
                    onDraftEventChanged(
                        DraftEventSelection(changed.date, changed.start, changed.end, draft.color, changed.allDay),
                    )
                },
                onInteraction = onDraftInteraction,
                onTap = onDraftTap,
                timeScroll = timeScroll,
                gridViewportHeightPx = gridViewportHeightPx,
            )
        }
    }
}

@Composable
internal fun FadingTimedText(
    text: String,
    color: Color,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight = FontWeight.Normal,
    maxLines: Int = 1,
    textScale: Float = 1f,
    textDecoration: TextDecoration? = null,
    shadow: Shadow? = null,
) {
    // Single-line text must not wrap; otherwise a title like "Klavier Maximilian"
    // wraps after the first word and the wrapped line gets clipped by maxLines=1,
    // leaving only "Klavier" visible. With softWrap disabled the overflow runs
    // off the right edge where the gradient below fades it out.
    val softWrap = maxLines > 1
    // Scale by adjusting font/line-height directly rather than via graphicsLayer.
    // graphicsLayer scaling with transformOrigin = (0,0) shrinks the rendered text
    // toward the top-left, leaving the right side of the layout visually empty —
    // which made the gradient look like it started far from the card's edge.
    val scaledFontSize = (fontSize.value * textScale).sp
    val scaledLineHeight = (lineHeight.value * textScale).sp
    Box(
        modifier = modifier
            .fillMaxWidth()
            .rightEdgeFadeMask(),
    ) {
        Text(
            text = text,
            color = color,
            fontSize = scaledFontSize,
            lineHeight = scaledLineHeight,
            fontWeight = fontWeight,
            textDecoration = textDecoration,
            style = TextStyle(shadow = shadow),
            maxLines = maxLines,
            softWrap = softWrap,
            overflow = TextOverflow.Clip,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun Modifier.rightEdgeFadeMask(edgeWidth: Dp = 22.dp): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val edge = edgeWidth.toPx().coerceAtMost(size.width)
            drawRect(
                brush = Brush.horizontalGradient(
                    0.00f to Color.Black,
                    0.55f to Color.Black.copy(alpha = 0.95f),
                    0.78f to Color.Black.copy(alpha = 0.65f),
                    0.92f to Color.Black.copy(alpha = 0.22f),
                    1.00f to Color.Transparent,
                    startX = size.width - edge,
                    endX = size.width,
                ),
                topLeft = Offset(size.width - edge, 0f),
                size = Size(edge, size.height),
                blendMode = BlendMode.DstIn,
            )
        }

internal fun Modifier.bottomEdgeFadeMask(edgeHeight: Dp = 8.dp): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val edge = edgeHeight.toPx().coerceAtMost(size.height)
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.Black,
                    1f to Color.Transparent,
                    startY = size.height - edge,
                    endY = size.height,
                ),
                topLeft = Offset(0f, size.height - edge),
                size = Size(size.width, edge),
                blendMode = BlendMode.DstIn,
            )
        }

@Composable
internal fun tentativeReadableTextStyle(enabled: Boolean): TextStyle =
    if (enabled) {
        val backdrop = if (MaterialTheme.colorScheme.background.isDark()) Color.Black else Color.White
        TextStyle(
            shadow = Shadow(
                color = backdrop.copy(alpha = 0.88f),
                offset = Offset(0f, 1f),
                blurRadius = 3.2f,
            ),
        )
    } else {
        TextStyle.Default
    }

private enum class ParticipantDisplayMode { Hidden, Compact, Full }

private data class ParticipantDisplayTarget(
    val mode: ParticipantDisplayMode,
    val fullLimit: Int,
)

@Composable
private fun TimedEventBlock(
    event: EventEntity,
    placement: TimedPlacement,
    lane: Int,
    laneCount: Int,
    day: LocalDate,
    hourHeightDp: Float,
    color: Int,
    dayWidthPx: Float,
    onMove: (LocalDate, LocalTime, LocalTime) -> Unit,
    onMoveAllDay: (LocalDate) -> Unit,
    onClick: () -> Unit,
) {
    val darkPalette = CurrentDarkPalette
    val visuals = remember(event.status, event.manualColor, event.color, color, darkPalette) {
        event.cardVisuals(darkPalette = darkPalette)
    }
    val background = visuals.background
    val textColor = visuals.contentColor
    val pendingAlpha = pendingDeleteAlpha(event.resourceHref)
    val attendees = remember(event.attendeesJson) { event.attendeesJson.toCalendarParticipants() }
    var dragX by remember(event.resourceHref, event.startsAtMillis) { mutableStateOf(0f) }
    var dragY by remember(event.resourceHref, event.startsAtMillis) { mutableStateOf(0f) }
    var isDragging by remember(event.resourceHref, event.startsAtMillis) { mutableStateOf(false) }
    val dragReporter = LocalTimedDragReporter.current
    LaunchedEffect(isDragging) { dragReporter(day, isDragging) }
    DisposableEffect(event.resourceHref, event.startsAtMillis) {
        onDispose { dragReporter(day, false) }
    }
    val density = LocalDensity.current
    val hourHeightPxForDrag = with(density) { hourHeightDp.dp.toPx() }
    val dayStepPx = dayWidthPx + with(density) { DayColumnSpacing.toPx() }
    val duration = (placement.endMinute - placement.startMinute).coerceAtLeast(DraftMinDurationMinutes)
    val snappedDayDelta = if (dayStepPx > 0f) (dragX / dayStepPx).roundToInt() else 0
    val snappedMinuteDelta = if (hourHeightPxForDrag > 0f) {
        ((dragY / hourHeightPxForDrag) * 60f).roundToInt().snapDraftMinute()
    } else {
        0
    }
    val snappedStart = (placement.startMinute + snappedMinuteDelta)
        .snapDraftMinute()
        .coerceIn(DayStartHour * 60, (DayEndHour + 1) * 60 - duration)
    val snappedDragYPx = with(density) { ((snappedStart - placement.startMinute) / 60f * hourHeightDp).dp.toPx() }
    val animatedDragX by animateFloatAsState(
        targetValue = if (isDragging) snappedDayDelta * dayStepPx else 0f,
        animationSpec = tween(110, easing = MotionStandard),
        label = "eventDragX",
    )
    val animatedDragY by animateFloatAsState(
        targetValue = if (isDragging) snappedDragYPx else 0f,
        animationSpec = tween(110, easing = MotionStandard),
        label = "eventDragY",
    )
    val titleScale = ((placement.heightDp - 13f) / 20f).coerceIn(0f, 1f)
    val compactVerticalPadding = (1.5f + titleScale * 1.5f).dp
    val compactTitleScale = 0.9f + titleScale * 0.1f
    val showTitle = placement.heightDp >= 13f
    val titleMaxLines = if (placement.heightDp >= 43f) 2 else 1
    val displayLocation = remember(event.location, event.locationMapVerified) {
        event.location
            ?.cardLocationText(event.locationMapVerified)
            .orEmpty()
    }
    val showLocation = placement.heightDp >= 40f && displayLocation.isNotBlank()
    val locationMaxLines = when {
        placement.heightDp >= 86f -> 4
        placement.heightDp >= 68f -> 3
        placement.heightDp >= 52f -> 2
        else -> 1
    }
    val participantMode = when {
        attendees.isEmpty() || placement.heightDp < 5f -> ParticipantDisplayMode.Hidden
        placement.heightDp >= 72f -> ParticipantDisplayMode.Full
        else -> ParticipantDisplayMode.Compact
    }
    val compactParticipantScale = ((placement.heightDp - 5f) / 18f).coerceIn(0.34f, 1f)
    val participantLimit = if (displayLocation.isNotBlank() && placement.heightDp < 88f) 2 else 6
    val participantTarget = ParticipantDisplayTarget(
        mode = participantMode,
        fullLimit = if (participantMode == ParticipantDisplayMode.Full) participantLimit else 0,
    )
    val fadeTextBottom = titleMaxLines > 1 || showLocation
    val textShadow = if (event.isTentative()) {
        Shadow(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            offset = Offset(0f, 1f),
            blurRadius = 3.2f,
        )
    } else {
        null
    }
    val dragModifier = Modifier.pointerInput(event.resourceHref, event.startsAtMillis, placement, hourHeightDp, dayWidthPx) {
        val hourHeightPx = with(density) { hourHeightDp.dp.toPx() }
        val dayStep = dayWidthPx + with(density) { DayColumnSpacing.toPx() }
        detectDragGesturesAfterLongPress(
            onDragStart = {
                isDragging = true
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
            },
            onDragEnd = {
                val dayDelta = if (dayStep > 0f) (dragX / dayStep).roundToInt() else 0
                val minuteDelta = ((dragY / hourHeightPx) * 60f).roundToInt().snapDraftMinute()
                val nextStart = (placement.startMinute + minuteDelta)
                    .snapDraftMinute()
                    .coerceIn(DayStartHour * 60, (DayEndHour + 1) * 60 - duration)
                val targetDate = day.plusDays(dayDelta.toLong())
                val movedTopPx = with(density) { placement.topDp.dp.toPx() } + dragY
                if (movedTopPx < with(density) { (-16).dp.toPx() }) {
                    onMoveAllDay(targetDate)
                } else {
                    onMove(targetDate, nextStart.toDraftLocalTime(), (nextStart + duration).toDraftLocalTime())
                }
                isDragging = false
                dragX = 0f
                dragY = 0f
            },
        )
    }
    Row(
        modifier = Modifier
            .offset {
                IntOffset(
                    animatedDragX.roundToInt(),
                    with(density) { (placement.topDp.dp.toPx() + animatedDragY).roundToInt() },
                )
            }
            .zIndex(if (isDragging) 20f else 1f)
            .fillMaxWidth()
            .height(placement.heightDp.dp),
    ) {
        if (lane > 0) Spacer(Modifier.weight(lane.toFloat()))
        val shape = RoundedCornerShape(10.dp)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                // Option B: morph this card out of / into its month-cell pill (matched by uid).
                .morphBounds(
                    key = "item-${event.resourceHref}",
                    enabled = EnablePerItemMorph && day == LocalMorphItemDay.current,
                    enter = MorphItemCardEnter,
                    exit = MorphItemCardExit,
                    boundsTransform = MorphItemBoundsTransform,
                )
                .padding(vertical = 1.dp)
                .alpha(pendingAlpha)
                .background(background, shape)
                .then(dragModifier),
        ) {
            Box(
                Modifier
                    .matchParentSize()
                    .clip(shape)
                    .clickable(onClick = onClick),
            )
            Box(Modifier.matchParentSize()) {
                if (showTitle) {
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .then(if (fadeTextBottom) Modifier.bottomEdgeFadeMask() else Modifier)
                            .padding(start = 6.dp, end = 0.dp, top = compactVerticalPadding, bottom = 0.dp),
                        verticalArrangement = Arrangement.Top,
                    ) {
                        FadingTimedText(
                            text = event.title,
                            color = textColor,
                            fontSize = 12.sp,
                            lineHeight = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = titleMaxLines,
                            textScale = compactTitleScale,
                            textDecoration = visuals.textDecoration,
                            shadow = textShadow,
                        )
                        if (showLocation) {
                            FadingTimedText(
                                text = displayLocation,
                                color = textColor.copy(alpha = 0.86f),
                                fontSize = 11.sp,
                                lineHeight = 12.sp,
                                maxLines = locationMaxLines,
                                textDecoration = visuals.textDecoration,
                                shadow = textShadow,
                            )
                        }
                    }
                }
            }
            AnimatedContent(
                targetState = participantTarget,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .fillMaxWidth(),
                transitionSpec = {
                    val shrinking = initialState.mode == ParticipantDisplayMode.Full &&
                        (targetState.mode != ParticipantDisplayMode.Full || targetState.fullLimit < initialState.fullLimit)
                    val entering = if (shrinking) {
                        fadeIn(tween(120, delayMillis = 90, easing = MotionStandard)) +
                            scaleIn(tween(120, delayMillis = 90, easing = MotionStandard), initialScale = 0.9f)
                    } else {
                        fadeIn(tween(150, easing = MotionStandard)) +
                            scaleIn(tween(150, easing = MotionStandard), initialScale = 0.86f)
                    }
                    val exiting = if (shrinking) {
                        fadeOut(tween(90, easing = MotionStandard)) +
                            scaleOut(tween(90, easing = MotionStandard), targetScale = 0.92f)
                    } else {
                        fadeOut(tween(110, easing = MotionStandard)) +
                            scaleOut(tween(110, easing = MotionStandard), targetScale = 0.86f)
                    }
                    entering.togetherWith(exiting).using(SizeTransform(clip = false))
                },
                label = "participantStackMorph",
            ) { target ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { clip = false },
                    contentAlignment = Alignment.BottomEnd,
                ) {
                    when (target.mode) {
                        ParticipantDisplayMode.Full -> EventParticipantStack(
                            attendees = attendees,
                            eventColor = visuals.baseColor,
                            contentColor = textColor,
                            modifier = Modifier
                                .padding(end = 4.dp, bottom = 2.dp)
                                .fillMaxWidth(0.92f),
                            circleSize = 22.dp,
                            maxVisible = target.fullLimit,
                        )

                        ParticipantDisplayMode.Compact -> CompactParticipantStatusDots(
                            attendees = attendees,
                            scale = compactParticipantScale,
                            modifier = Modifier.padding(end = 3.dp),
                        )

                        ParticipantDisplayMode.Hidden -> Spacer(Modifier.size(0.dp))
                    }
                }
            }
            Box(
                Modifier
                    .matchParentSize()
                    .then(
                        when {
                            visuals.dashedBorder && visuals.borderColor != null -> Modifier.dashedBorder(visuals.borderColor, 10.dp)
                            visuals.borderColor != null -> Modifier.border(1.dp, visuals.borderColor, shape)
                            else -> Modifier
                        },
                    ),
            )
            PendingMutationBadge(
                resourceHref = event.resourceHref,
                modifier = Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-2).dp),
            )
        }
        if (laneCount - lane - 1 > 0) Spacer(Modifier.weight((laneCount - lane - 1).toFloat()))
    }
}

@Composable
private fun TimedTaskBlock(
    task: TaskEntity,
    placement: TimedPlacement,
    lane: Int,
    laneCount: Int,
    day: LocalDate,
    hourHeightDp: Float,
    color: Int,
    dayWidthPx: Float,
    onTaskStatusChanged: (String, String) -> Unit,
    onMove: (LocalDate, LocalTime, LocalTime) -> Unit,
    onMoveAllDay: (LocalDate) -> Unit,
    onClick: () -> Unit,
) {
    val background = remember(color) { Color(color) }
    val textColor = remember(color) { if (background.isDark()) Color.White else Color(0xFF1C1A18) }
    val pendingAlpha = pendingDeleteAlpha(task.resourceHref)
    var lastCompleted by remember(task.resourceHref) { mutableStateOf(task.isCompleted) }
    var burstKey by remember(task.resourceHref) { mutableStateOf(0) }
    LaunchedEffect(task.isCompleted) {
        if (task.isCompleted && !lastCompleted) burstKey++
        lastCompleted = task.isCompleted
    }
    val inactive = task.isInactive()
    val taskAlpha by animateFloatAsState(
        targetValue = if (inactive) 0.48f else 1f,
        animationSpec = tween(MotionMedium, easing = MotionStandard),
        label = "timedTaskAlpha",
    )
    var dragX by remember(task.resourceHref) { mutableStateOf(0f) }
    var dragY by remember(task.resourceHref) { mutableStateOf(0f) }
    var isDragging by remember(task.resourceHref) { mutableStateOf(false) }
    val dragReporter = LocalTimedDragReporter.current
    LaunchedEffect(isDragging) { dragReporter(day, isDragging) }
    DisposableEffect(task.resourceHref) {
        onDispose { dragReporter(day, false) }
    }
    val density = LocalDensity.current
    val hourHeightPxForDrag = with(density) { hourHeightDp.dp.toPx() }
    val dayStepPx = dayWidthPx + with(density) { DayColumnSpacing.toPx() }
    val duration = (placement.endMinute - placement.startMinute).coerceAtLeast(DraftMinDurationMinutes)
    val snappedDayDelta = if (dayStepPx > 0f) (dragX / dayStepPx).roundToInt() else 0
    val snappedMinuteDelta = if (hourHeightPxForDrag > 0f) {
        ((dragY / hourHeightPxForDrag) * 60f).roundToInt().snapDraftMinute()
    } else {
        0
    }
    val snappedStart = (placement.startMinute + snappedMinuteDelta)
        .snapDraftMinute()
        .coerceIn(DayStartHour * 60, (DayEndHour + 1) * 60 - duration)
    val snappedDragYPx = with(density) { ((snappedStart - placement.startMinute) / 60f * hourHeightDp).dp.toPx() }
    val animatedDragX by animateFloatAsState(
        targetValue = if (isDragging) snappedDayDelta * dayStepPx else 0f,
        animationSpec = tween(110, easing = MotionStandard),
        label = "taskDragX",
    )
    val animatedDragY by animateFloatAsState(
        targetValue = if (isDragging) snappedDragYPx else 0f,
        animationSpec = tween(110, easing = MotionStandard),
        label = "taskDragY",
    )
    val titleScale = ((placement.heightDp - 18f) / 18f).coerceIn(0f, 1f)
    val compactVerticalPadding = (1.5f + titleScale * 1.5f).dp
    val compactTitleScale = 0.9f + titleScale * 0.1f
    val showTitle = placement.heightDp >= 18f
    val titleMaxLines = if (placement.heightDp >= 43f) 2 else 1
    val displayLocation = remember(task.location, task.locationMapVerified) {
        task.location
            ?.cardLocationText(task.locationMapVerified)
            .orEmpty()
    }
    val showLocation = placement.heightDp >= 42f && displayLocation.isNotBlank()
    val locationMaxLines = when {
        placement.heightDp >= 86f -> 4
        placement.heightDp >= 68f -> 3
        placement.heightDp >= 52f -> 2
        else -> 1
    }
    val checkboxBoxSize = min(18f, (placement.heightDp - 2f).coerceAtLeast(15f))
    val checkboxIconSize = min(15f, checkboxBoxSize)
    val checkboxTopOffset = min(
        compactVerticalPadding.value,
        max(0f, ((placement.heightDp - 2f) - checkboxBoxSize) / 2f),
    ).dp
    val dragModifier = Modifier.pointerInput(task.resourceHref, placement, hourHeightDp, dayWidthPx) {
        val hourHeightPx = with(density) { hourHeightDp.dp.toPx() }
        val dayStep = dayWidthPx + with(density) { DayColumnSpacing.toPx() }
        detectDragGesturesAfterLongPress(
            onDragStart = {
                isDragging = true
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
            },
            onDragEnd = {
                val dayDelta = if (dayStep > 0f) (dragX / dayStep).roundToInt() else 0
                val minuteDelta = ((dragY / hourHeightPx) * 60f).roundToInt().snapDraftMinute()
                val nextStart = (placement.startMinute + minuteDelta)
                    .snapDraftMinute()
                    .coerceIn(DayStartHour * 60, (DayEndHour + 1) * 60 - duration)
                val targetDate = day.plusDays(dayDelta.toLong())
                val movedTopPx = with(density) { placement.topDp.dp.toPx() } + dragY
                if (movedTopPx < with(density) { (-16).dp.toPx() }) {
                    onMoveAllDay(targetDate)
                } else {
                    onMove(targetDate, nextStart.toDraftLocalTime(), (nextStart + duration).toDraftLocalTime())
                }
                isDragging = false
                dragX = 0f
                dragY = 0f
            },
        )
    }
    Row(
        modifier = Modifier
            .offset {
                IntOffset(
                    animatedDragX.roundToInt(),
                    with(density) { (placement.topDp.dp.toPx() + animatedDragY).roundToInt() },
                )
            }
            .zIndex(if (isDragging) 20f else if (!inactive && taskPriorityIntensity(task.priority) > 0f) 3f else 1f)
            .fillMaxWidth()
            .height(placement.heightDp.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (lane > 0) Spacer(Modifier.weight(lane.toFloat()))
        val shape = RoundedCornerShape(10.dp)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                // Option B: morph this task card out of / into its month-cell pill (matched by uid).
                .morphBounds(
                    key = "item-${task.resourceHref}",
                    enabled = EnablePerItemMorph && day == LocalMorphItemDay.current,
                    enter = MorphItemCardEnter,
                    exit = MorphItemCardExit,
                    boundsTransform = MorphItemBoundsTransform,
                )
                .taskPriorityMotion(if (inactive) null else task.priority, background)
                .padding(vertical = 1.dp)
                .alpha(taskAlpha * pendingAlpha)
                .background(background, shape)
                .then(dragModifier),
        ) {
            TaskCardCompletionBurst(burstKey, color = textColor, modifier = Modifier.matchParentSize())
            Box(
                Modifier
                    .matchParentSize()
                    .clip(shape)
                    .clickable(onClick = onClick),
            )
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 5.dp),
                verticalAlignment = Alignment.Top,
            ) {
                TaskStatusCheckbox(
                    status = task.effectiveStatus(),
                    tint = textColor,
                    onStatusChange = { onTaskStatusChanged(task.resourceHref, it) },
                    boxSize = checkboxBoxSize.dp,
                    iconSize = checkboxIconSize.dp,
                    modifier = Modifier.offset(y = checkboxTopOffset),
                )
                Spacer(Modifier.width(3.dp))
                if (showTitle) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .then(if (titleMaxLines > 1 || showLocation) Modifier.bottomEdgeFadeMask() else Modifier)
                            .padding(top = compactVerticalPadding),
                    ) {
                        FadingTimedText(
                            text = task.title,
                            color = textColor,
                            maxLines = titleMaxLines,
                            fontSize = 12.sp,
                            lineHeight = 13.sp,
                            fontWeight = FontWeight.Medium,
                            textScale = compactTitleScale,
                        )
                        if (showLocation) {
                            FadingTimedText(
                                text = displayLocation,
                                color = textColor.copy(alpha = 0.86f),
                                fontSize = 11.sp,
                                lineHeight = 12.sp,
                                maxLines = locationMaxLines,
                            )
                        }
                    }
                }
            }
            PendingMutationBadge(
                resourceHref = task.resourceHref,
                modifier = Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-2).dp),
            )
        }
        if (laneCount - lane - 1 > 0) Spacer(Modifier.weight((laneCount - lane - 1).toFloat()))
    }
}

@Composable
internal fun Modifier.taskPriorityMotion(
    priority: Int?,
    color: Color,
    expandHorizontally: Boolean = true,
): Modifier {
    if (!PriorityAnimationsEnabled) return this
    val intensity = taskPriorityIntensity(priority)
    if (intensity <= 0f) return this
    val transition = rememberInfiniteTransition(label = "taskPriorityMotion")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = (1050 - (intensity * 420f)).roundToInt().coerceAtLeast(520),
                easing = MotionStandard,
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "taskPriorityPulseValue",
    )
    val shakePhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(260, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "taskPriorityShakePhase",
    )
    val highestPriorityShake = if (priority == 1) cos(shakePhase * PI * 6.0).toFloat() * 1.6f else 0f
    val bounce = ((pulse - 0.5f) * -2f * intensity)
    val scale = 1f + intensity * 0.018f * pulse
    return this
        .graphicsLayer {
            translationX = highestPriorityShake
            translationY = bounce
            scaleX = if (expandHorizontally) scale else 1f
            scaleY = scale
        }
        .drawBehind {
            val spread = 8.dp.toPx() * intensity * (0.45f + pulse)
            val horizontalSpread = if (expandHorizontally) spread else 0f
            drawRoundRect(
                color = color.copy(alpha = 0.18f * intensity * (0.45f + 0.55f * pulse)),
                topLeft = Offset(-horizontalSpread / 2f, -spread / 2f),
                size = Size(size.width + horizontalSpread, size.height + spread),
                cornerRadius = CornerRadius(8.dp.toPx() + spread / 3f, 8.dp.toPx() + spread / 3f),
            )
        }
}
