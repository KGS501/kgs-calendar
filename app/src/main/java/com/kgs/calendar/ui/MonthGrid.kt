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
internal fun MonthBlock(
    month: YearMonth,
    firstDayOfWeek: DayOfWeek,
    eventsByDay: Map<LocalDate, List<EventEntity>>,
    tasksByDay: Map<LocalDate, List<TaskEntity>>,
    taskColorMode: TaskColorMode,
    morphDay: LocalDate,
    onOpenDay: (LocalDate) -> Unit,
) {
    val firstDay = month.atDay(1)
    val leading = firstDay.leadingDaysFrom(firstDayOfWeek)
    val length = month.lengthOfMonth()
    val rowCount = ((leading + length + 6) / 7)
    Column(Modifier.fillMaxWidth()) {
        // Breathing room + big month name where one month ends and the next begins.
        Text(
            month.atDay(1).format(DateTimeFormatter.ofPattern("MMMM", LocalAppLocale.current)),
            color = WarmBrown,
            fontSize = 22.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 12.dp, top = 22.dp, bottom = 8.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            repeat(rowCount) { row ->
                val rowStartDate = firstDay.plusDays((row * 7 - leading).toLong())
                val rowEndDate = rowStartDate.plusDays(6)
                val rowDays = (0 until 7).mapNotNull { column ->
                    val dayIndex = row * 7 + column - leading + 1
                    if (dayIndex in 1..length) month.atDay(dayIndex) else null
                }
                Box(
                    modifier = Modifier.fillMaxWidth().height(MonthDayRowHeight),
                ) {
                    Row(
                        modifier = Modifier.matchParentSize(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        for (column in 0 until 7) {
                            val dayIndex = row * 7 + column - leading + 1
                            if (dayIndex in 1..length) {
                                val day = month.atDay(dayIndex)
                                MonthDayCard(
                                    day = day,
                                    firstDayOfWeek = firstDayOfWeek,
                                    events = eventsByDay[day].orEmpty(),
                                    tasks = tasksByDay[day].orEmpty(),
                                    taskColorMode = taskColorMode,
                                    morphEnabled = day == morphDay,
                                    showInlinePills = false,
                                    onClick = { onOpenDay(day) },
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                )
                            } else {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                    MonthRowPillOverlay(
                        rowDays = rowDays,
                        rowStartDate = rowStartDate,
                        rowEndDate = rowEndDate,
                        eventsByDay = eventsByDay,
                        tasksByDay = tasksByDay,
                        taskColorMode = taskColorMode,
                        modifier = Modifier.matchParentSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthDayCard(
    day: LocalDate,
    firstDayOfWeek: DayOfWeek,
    events: List<EventEntity>,
    tasks: List<TaskEntity>,
    taskColorMode: TaskColorMode,
    morphEnabled: Boolean,
    showInlinePills: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isToday = day == LocalCalendarTimeSnapshot.current.today
    val todayCellColor = WarmBrown.blendWith(MaterialTheme.colorScheme.surface, 0.84f).copy(alpha = 0.96f)
    // Keep the visible month cell in the month scene. Only an invisible anchor participates as
    // the source of the container transform. Otherwise Compose lifts the solid cell into the
    // transition overlay immediately, briefly covering row-spanning pills before the entering
    // day surface has faded in. The destination timeline still grows from these exact bounds.
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .morphBounds(
                    "dayblock-$day",
                    enabled = morphEnabled,
                    enter = MorphCellEnter,
                    exit = MorphCellExit,
                ),
        )
        Column(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(12.dp))
                .background(
                    color = if (isToday) todayCellColor
                    else WarmGrid.copy(alpha = if (MaterialTheme.colorScheme.background.isDark()) 0.92f else 0.96f),
                    shape = RoundedCornerShape(12.dp),
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 3.dp, vertical = 3.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(20.dp)
                .clip(CircleShape)
                .background(if (isToday) WarmBrown else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                day.dayOfMonth.toString(),
                color = if (isToday) accentContainerContentColor() else WarmInk,
                fontSize = 12.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        val pills = buildList {
            val startsMonthRow = day.dayOfMonth == 1 || day.dayOfWeek == firstDayOfWeek
            val nextDay = day.plusDays(1)
            val endsMonthRow = nextDay.month != day.month || nextDay.dayOfWeek == firstDayOfWeek
            events.forEach {
                val starts = it.startsAtMillis.toDate()
                val ends = (it.endsAtMillis - 1).toDate()
                val showTitle = day == starts || (starts.isBefore(day) && startsMonthRow)
                add(
                    MonthPill(
                        uid = it.uid,
                        resourceHref = it.resourceHref,
                        title = if (showTitle) it.title else "",
                        color = it.displayColor(),
                        completed = false,
                        eventStatus = it.status,
                        continuesFromPrevious = starts.isBefore(day) && !startsMonthRow,
                        continuesToNext = ends.isAfter(day) && !endsMonthRow,
                    ),
                )
            }
            tasks.forEach {
                val start = it.startAtMillis?.toDate() ?: it.dueAtMillis?.toDate() ?: day
                val end = it.dueAtMillis?.toDate() ?: it.startAtMillis?.toDate() ?: day
                val showTitle = day == start || (start.isBefore(day) && startsMonthRow)
                add(
                    MonthPill(
                        uid = it.uid,
                        resourceHref = it.resourceHref,
                        title = if (showTitle) it.title else "",
                        color = it.displayColor(taskColorMode),
                        completed = it.isCompleted,
                        continuesFromPrevious = start.isBefore(day) && !startsMonthRow,
                        continuesToNext = end.isAfter(day) && !endsMonthRow,
                    ),
                )
            }
        }
        val maxPills = MonthVisiblePillLanes
        if (showInlinePills) {
            pills.take(maxPills).forEach { pill -> MonthPillChip(pill, morphEnabled = morphEnabled) }
        }
            if (showInlinePills && pills.size > maxPills) {
                Text(
                    "+${pills.size - maxPills}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.sp,
                    lineHeight = 10.sp,
                    modifier = Modifier.padding(start = 2.dp),
                )
            }
        }
    }
}

private val MonthDayRowHeight = 108.dp
private const val MonthVisiblePillLanes = 4

private data class MonthRowPillCandidate(
    val id: String,
    val uid: String?,
    val resourceHref: String?,
    override val title: String,
    val color: Int,
    val completed: Boolean,
    val eventStatus: String?,
    override val start: LocalDate,
    val end: LocalDate,
    override val occurrenceSortMillis: Long,
) : MonthRowOrderItem {
    override val spanDays: Long = ChronoUnit.DAYS.between(start, end).coerceAtLeast(0) + 1
    val isMultiDay: Boolean = spanDays > 1
}

private data class MonthRowPillSegment(
    val candidate: MonthRowPillCandidate,
    val visualStart: LocalDate,
    val visualEnd: LocalDate,
    val lane: Int,
    val fadeMode: MonthRowFadeMode,
)

private enum class MonthRowFadeMode {
    None,
    IntoNextDay,
    AtRowEnd,
}

private fun buildMonthRowPillSegments(
    rowDays: List<LocalDate>,
    rowStartDate: LocalDate,
    rowEndDate: LocalDate,
    eventsByDay: Map<LocalDate, List<EventEntity>>,
    tasksByDay: Map<LocalDate, List<TaskEntity>>,
    taskColorMode: TaskColorMode,
): Pair<List<MonthRowPillSegment>, Map<LocalDate, Int>> {
    val rowStart = rowDays.firstOrNull() ?: return emptyList<MonthRowPillSegment>() to emptyMap()
    val rowEnd = rowDays.lastOrNull() ?: rowStart
    val candidates = buildList {
        rowDays
            .flatMap { eventsByDay[it].orEmpty() }
            .distinctBy { it.monthOccurrenceKey() }
            .forEach { event ->
                val start = event.startsAtMillis.toDate()
                val end = (event.endsAtMillis - 1).toDate().coerceAtLeast(start)
                if (end < rowStart || start > rowEnd) return@forEach
                add(
                    MonthRowPillCandidate(
                        id = "event:${event.monthOccurrenceKey()}",
                        uid = event.uid,
                        resourceHref = event.resourceHref,
                        title = event.title,
                        color = event.displayColor(),
                        completed = false,
                        eventStatus = event.status,
                        start = start,
                        end = end,
                        occurrenceSortMillis = event.startsAtMillis,
                    ),
                )
            }
        rowDays
            .flatMap { tasksByDay[it].orEmpty() }
            .distinctBy { it.resourceHref ?: it.uid }
            .forEach { task ->
                val start = task.startAtMillis?.toDate() ?: task.dueAtMillis?.toDate() ?: rowStart
                val end = (task.dueAtMillis?.toDate() ?: task.startAtMillis?.toDate() ?: start).coerceAtLeast(start)
                if (end < rowStart || start > rowEnd) return@forEach
                add(
                    MonthRowPillCandidate(
                        id = "task:${task.resourceHref ?: task.uid}",
                        uid = task.uid,
                        resourceHref = task.resourceHref,
                        title = task.title,
                        color = task.displayColor(taskColorMode),
                        completed = task.isCompleted,
                        eventStatus = null,
                        start = start,
                        end = end,
                        occurrenceSortMillis = task.startAtMillis ?: task.dueAtMillis ?: Long.MAX_VALUE,
                    ),
                )
            }
    }

    data class MutableSegment(
        val candidate: MonthRowPillCandidate,
        val visualStart: LocalDate,
        var visualEnd: LocalDate,
        val actualEnd: LocalDate,
        val lane: Int,
        var fadeMode: MonthRowFadeMode = MonthRowFadeMode.None,
    )

    val laneSegments = mutableListOf<MutableList<MutableSegment>>()
    val segments = mutableListOf<MutableSegment>()
    // Reserve the visible lanes for the longest spans first. A chronological greedy pass lets
    // small items early in the week occupy all three visible lanes and can hide a long event that
    // starts later in the same row (for example a holiday beginning on Monday). Duration-first
    // packing keeps the visually important continuous bars visible while still stacking unrelated
    // same-day items in additional lanes.
    val sortedCandidates = candidates.sortedWith(MonthRowOrderComparator)
    sortedCandidates.forEach { candidate ->
        val visualStart = maxOf(candidate.start, rowStart)
        val visualEnd = minOf(candidate.end, rowEnd)
        fun doesNotOverlap(existing: MutableSegment): Boolean =
            existing.actualEnd < candidate.start ||
                candidate.end < existing.candidate.start

        fun isMultiDayJunction(existing: MutableSegment): Boolean =
            existing.candidate.isMultiDay &&
                candidate.isMultiDay &&
                (
                    existing.candidate.start < candidate.start &&
                        existing.actualEnd == candidate.start &&
                        candidate.start in rowStart..rowEnd
                    ) ||
                (
                    candidate.start < existing.candidate.start &&
                        candidate.end == existing.candidate.start &&
                        existing.candidate.start in rowStart..rowEnd
                    )

        fun canPlaceFully(lane: List<MutableSegment>): Boolean =
            lane.all(::doesNotOverlap)

        fun canShareLaneWithFade(lane: List<MutableSegment>): Boolean =
            lane.all { existing -> doesNotOverlap(existing) || isMultiDayJunction(existing) }

        val fullLane = laneSegments.indexOfFirst(::canPlaceFully)
        val visibleFadeLane = if (candidate.isMultiDay && laneSegments.size >= MonthVisiblePillLanes) {
            laneSegments.indexOfFirst { lane -> canShareLaneWithFade(lane) }
                .takeIf { it in 0 until MonthVisiblePillLanes }
                ?: -1
        } else {
            -1
        }
        val targetLane = when {
            fullLane in 0 until MonthVisiblePillLanes -> fullLane
            laneSegments.size < MonthVisiblePillLanes -> laneSegments.size
            visibleFadeLane >= 0 -> visibleFadeLane
            fullLane >= 0 -> fullLane
            else -> laneSegments.size
        }
        val segment = MutableSegment(
            candidate = candidate,
            visualStart = visualStart,
            visualEnd = visualEnd,
            actualEnd = candidate.end,
            lane = targetLane,
        )
        if (targetLane < laneSegments.size) {
            laneSegments[targetLane].forEach { existing ->
                when {
                    isMultiDayJunction(existing) &&
                        existing.candidate.start < candidate.start &&
                        existing.actualEnd == candidate.start -> {
                        existing.visualEnd = minOf(existing.visualEnd, candidate.start.minusDays(1))
                        existing.fadeMode = MonthRowFadeMode.IntoNextDay
                    }
                    isMultiDayJunction(existing) &&
                        candidate.start < existing.candidate.start &&
                        candidate.end == existing.candidate.start -> {
                        segment.visualEnd = minOf(segment.visualEnd, existing.candidate.start.minusDays(1))
                        segment.fadeMode = MonthRowFadeMode.IntoNextDay
                    }
                }
            }
            laneSegments[targetLane] += segment
        } else {
            laneSegments += mutableListOf(segment)
        }
        segments += segment
    }
    val nextRowFirstDay = rowEnd.plusDays(1)
    val startsOnNextRowFirstDay =
        eventsByDay[nextRowFirstDay].orEmpty().any { it.startsAtMillis.toDate() == nextRowFirstDay } ||
            tasksByDay[nextRowFirstDay].orEmpty().any {
                val start = it.startAtMillis?.toDate() ?: it.dueAtMillis?.toDate()
                start == nextRowFirstDay
            }
    if (startsOnNextRowFirstDay) {
        segments
            .filter { it.actualEnd == nextRowFirstDay && it.visualEnd == rowEnd && !it.visualEnd.isBefore(it.visualStart) }
            .forEach { it.fadeMode = MonthRowFadeMode.AtRowEnd }
    }

    val visibleSegments = segments
        .filter { it.lane < MonthVisiblePillLanes && !it.visualEnd.isBefore(it.visualStart) }
        .map {
            MonthRowPillSegment(
                candidate = it.candidate,
                visualStart = it.visualStart,
                visualEnd = it.visualEnd,
                lane = it.lane,
                fadeMode = it.fadeMode,
            )
        }
    val hiddenByDay = rowDays.associateWith { day ->
        val fadeRepresentedIds = segments
            .filter { it.fadeMode != MonthRowFadeMode.None && it.actualEnd == day }
            .map { it.candidate.id }
            .toSet()
        val active = candidates.count { day in it.start..it.end && it.id !in fadeRepresentedIds }
        val visible = visibleSegments.count { day in it.visualStart..it.visualEnd }
        (active - visible).coerceAtLeast(0)
    }.filterValues { it > 0 }
    return visibleSegments to hiddenByDay
}

@Composable
private fun MonthRowPillOverlay(
    rowDays: List<LocalDate>,
    rowStartDate: LocalDate,
    rowEndDate: LocalDate,
    eventsByDay: Map<LocalDate, List<EventEntity>>,
    tasksByDay: Map<LocalDate, List<TaskEntity>>,
    taskColorMode: TaskColorMode,
    modifier: Modifier = Modifier,
) {
    if (rowDays.isEmpty()) return
    val (segments, hiddenByDay) = remember(rowDays, rowStartDate, rowEndDate, eventsByDay, tasksByDay, taskColorMode) {
        buildMonthRowPillSegments(rowDays, rowStartDate, rowEndDate, eventsByDay, tasksByDay, taskColorMode)
    }
    BoxWithConstraints(modifier = modifier) {
        val spacing = 4.dp
        val cellWidth = (maxWidth - spacing * 6) / 7f
        val step = cellWidth + spacing
        val pillTop = 25.dp
        val pillHeight = 13.dp
        val laneStep = 15.dp

        segments.forEach { segment ->
            val startColumn = ChronoUnit.DAYS.between(rowStartDate, segment.visualStart).toInt().coerceIn(0, 6)
            val endColumn = ChronoUnit.DAYS.between(rowStartDate, segment.visualEnd).toInt().coerceIn(startColumn, 6)
            val span = endColumn - startColumn + 1
            val x = step * startColumn + 3.dp
            val bodyWidth = cellWidth * span + spacing * (span - 1) - 6.dp
            val availableTailWidth = (maxWidth - x - bodyWidth).coerceAtLeast(0.dp)
            val tailWidth = when (segment.fadeMode) {
                MonthRowFadeMode.IntoNextDay -> minOf(cellWidth * 0.34f + spacing, availableTailWidth)
                else -> 0.dp
            }
            val width = bodyWidth + tailWidth
            val leftSolidStart = if (segment.candidate.start < segment.visualStart && width > 0.dp) {
                val fadeWidth = minOf(cellWidth * 0.30f, width)
                (fadeWidth.value / width.value).coerceIn(0.02f, 0.36f)
            } else {
                0f
            }
            val rightSolidEnd = when {
                segment.fadeMode == MonthRowFadeMode.IntoNextDay && width > 0.dp ->
                    (bodyWidth.value / width.value).coerceIn(0.05f, 0.98f)
                (segment.fadeMode == MonthRowFadeMode.AtRowEnd || segment.candidate.end > segment.visualEnd) && bodyWidth > 0.dp -> {
                    val fadeWidth = minOf(cellWidth * 0.34f, bodyWidth)
                    ((bodyWidth - fadeWidth).value / width.value).coerceIn(leftSolidStart + 0.04f, 0.98f)
                }
                else -> 1f
            }
            MonthRowPillChip(
                segment = segment,
                leftSolidStart = leftSolidStart,
                rightSolidEnd = rightSolidEnd,
                modifier = Modifier
                    .offset(x = x, y = pillTop + laneStep * segment.lane)
                    .width(width)
                    .height(pillHeight),
            )
        }
        hiddenByDay.forEach { (day, count) ->
            val column = ChronoUnit.DAYS.between(rowStartDate, day).toInt()
            if (column !in 0..6) return@forEach
            Text(
                "+$count",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 9.sp,
                lineHeight = 10.sp,
                modifier = Modifier.offset(x = step * column + 6.dp, y = pillTop + laneStep * MonthVisiblePillLanes),
            )
        }
    }
}

@Composable
private fun MonthRowPillChip(
    segment: MonthRowPillSegment,
    leftSolidStart: Float,
    rightSolidEnd: Float,
    modifier: Modifier = Modifier,
) {
    val candidate = segment.candidate
    val bg = Color(candidate.color)
    val tentative = candidate.eventStatus.equals("TENTATIVE", ignoreCase = true)
    val cancelled = candidate.eventStatus.equals("CANCELLED", ignoreCase = true)
    val fill = when {
        cancelled -> bg.greyedOut(0.58f).copy(alpha = 0.34f)
        candidate.completed -> bg.greyedOut(0.58f).copy(alpha = 0.46f)
        tentative -> Color.Transparent
        else -> bg
    }
    val textColor = when {
        tentative && CurrentDarkPalette -> Color.White
        tentative -> Color(0xFF17202A)
        bg.isDark() && !cancelled && !candidate.completed -> Color.White
        else -> Color(0xFF1C1A18)
    }
    val hasEdgeFade = leftSolidStart > 0.005f || rightSolidEnd < 0.995f
    val brush = if (hasEdgeFade) {
        val start = leftSolidStart.coerceIn(0f, 0.45f)
        val end = rightSolidEnd.coerceIn((start + 0.04f).coerceAtMost(0.96f), 1f)
        val leftTransparentEnd = (start * 0.18f).coerceIn(0f, start)
        val rightTransparentStart = (end + (1f - end) * 0.78f).coerceIn(end, 1f)
        Brush.horizontalGradient(
            0f to fill.copy(alpha = 0f),
            leftTransparentEnd to fill.copy(alpha = 0f),
            start to fill,
            end to fill,
            rightTransparentStart to fill.copy(alpha = 0f),
            1f to fill.copy(alpha = 0f),
        )
    } else {
        Brush.horizontalGradient(0f to fill, 1f to fill)
    }
    val fadeBase = WarmGrid.copy(alpha = if (MaterialTheme.colorScheme.background.isDark()) 0.92f else 0.96f)
    BoxWithConstraints(
        modifier = modifier
            .then(if (hasEdgeFade && !tentative) Modifier.background(fadeBase, RoundedCornerShape(6.dp)) else Modifier)
            .background(brush, RoundedCornerShape(6.dp))
            .then(if (tentative) Modifier.dashedBorder(bg.copy(alpha = 0.9f), 6.dp) else Modifier),
    ) {
        val titleStartPadding = 4.dp + maxWidth * leftSolidStart
        Text(
            candidate.title,
            color = textColor.copy(alpha = if (cancelled) 0.54f else if (candidate.completed) 0.64f else 1f),
            fontSize = 9.sp,
            lineHeight = 11.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            fontWeight = FontWeight.Medium,
            textDecoration = if (cancelled) TextDecoration.LineThrough else null,
            modifier = Modifier.padding(start = titleStartPadding, end = 4.dp, top = 1.dp, bottom = 0.dp),
        )
        candidate.resourceHref?.let { href ->
            PendingMutationBadge(href, Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-2).dp))
        }
    }
}

private data class MonthPill(
    val uid: String?,
    val resourceHref: String?,
    val title: String,
    val color: Int,
    val completed: Boolean,
    val eventStatus: String? = null,
    val continuesFromPrevious: Boolean = false,
    val continuesToNext: Boolean = false,
)

@Composable
private fun MonthPillChip(pill: MonthPill, morphEnabled: Boolean = false) {
    val bg = Color(pill.color)
    val tentative = pill.eventStatus.equals("TENTATIVE", ignoreCase = true)
    val cancelled = pill.eventStatus.equals("CANCELLED", ignoreCase = true)
    val fill = when {
        cancelled -> bg.greyedOut(0.58f).copy(alpha = 0.34f)
        pill.completed -> bg.greyedOut(0.58f).copy(alpha = 0.46f)
        tentative -> Color.Transparent
        else -> bg
    }
    val textColor = when {
        tentative && CurrentDarkPalette -> Color.White
        tentative -> Color(0xFF17202A)
        bg.isDark() && !cancelled && !pill.completed -> Color.White
        else -> Color(0xFF1C1A18)
    }
    val shape = continuationShape(pill.continuesFromPrevious, pill.continuesToNext)
    val pendingAlpha = pill.resourceHref?.let { pendingDeleteAlpha(it) } ?: 1f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // Option B: this pill is the source of a per-item morph into its full 1-day card.
            .morphBounds(
                key = "item-${pill.uid}",
                enabled = EnablePerItemMorph && morphEnabled && pill.uid != null,
                enter = MorphItemPillEnter,
                exit = MorphItemPillExit,
                boundsTransform = MorphItemBoundsTransform,
            )
            .alpha(pendingAlpha)
            .drawContinuationBridge(fill, pill.continuesFromPrevious, pill.continuesToNext, bridgeWidth = DayColumnSpacing + 8.dp)
            .background(fill, shape)
            .then(if (tentative) Modifier.dashedBorder(bg.copy(alpha = 0.9f), 6.dp) else Modifier)
            .padding(horizontal = 4.dp, vertical = 1.dp),
    ) {
        Text(
            pill.title,
            color = textColor.copy(alpha = if (cancelled) 0.54f else if (pill.completed) 0.64f else 1f),
            fontSize = 9.sp,
            lineHeight = 11.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            fontWeight = FontWeight.Medium,
            textDecoration = if (cancelled) TextDecoration.LineThrough else null,
        )
        pill.resourceHref?.let { href ->
            PendingMutationBadge(href, Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-2).dp))
        }
    }
}

