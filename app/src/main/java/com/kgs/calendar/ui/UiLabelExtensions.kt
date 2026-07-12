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
internal fun CalendarViewMode.localizedLabel(): String = when (this) {
    CalendarViewMode.ThreeDay -> appString(R.string.three_days)
    CalendarViewMode.Day -> appString(R.string.day)
    CalendarViewMode.Month -> appString(R.string.month)
    CalendarViewMode.Agenda -> appString(R.string.agenda)
    CalendarViewMode.Tasks -> appString(R.string.tasks)
}

internal fun CalendarViewMode.settingsIcon(): ImageVector = when (this) {
    CalendarViewMode.Agenda -> Icons.Default.ViewAgenda
    CalendarViewMode.Day -> Icons.Default.ViewDay
    CalendarViewMode.ThreeDay -> Icons.Default.ViewWeek
    CalendarViewMode.Month -> Icons.Default.CalendarMonth
    CalendarViewMode.Tasks -> Icons.Default.TaskAlt
}


@Composable
internal fun DayOfWeek.localizedWeekdayLabel(): String =
    when (this) {
        DayOfWeek.MONDAY -> appString(R.string.week_monday)
        DayOfWeek.TUESDAY -> appString(R.string.week_tuesday)
        DayOfWeek.WEDNESDAY -> appString(R.string.week_wednesday)
        DayOfWeek.THURSDAY -> appString(R.string.week_thursday)
        DayOfWeek.FRIDAY -> appString(R.string.week_friday)
        DayOfWeek.SATURDAY -> appString(R.string.week_saturday)
        DayOfWeek.SUNDAY -> appString(R.string.week_sunday)
    }


private fun TaskEntity.statusLabel(): String = when (status?.uppercase()) {
    "IN-PROCESS" -> "In progress"
    "CANCELLED" -> "Cancelled"
    "COMPLETED" -> "Completed"
    "NEEDS-ACTION" -> "Open"
    null -> if (isCompleted) "Completed" else "Open"
    else -> status!!
}

@Composable
internal fun AppThemeMode.localizedLabel(): String = when (this) {
    AppThemeMode.KgsBlue -> appString(R.string.kgs_blue)
    AppThemeMode.KgsWarm -> appString(R.string.kgs_warm)
    AppThemeMode.KgsFresh -> appString(R.string.kgs_fresh)
    AppThemeMode.SystemDynamic -> appString(R.string.android_colors)
}

@Composable
internal fun AppColorMode.localizedLabel(): String = when (this) {
    AppColorMode.Auto -> appString(R.string.auto)
    AppColorMode.Light -> appString(R.string.light)
    AppColorMode.Dark -> appString(R.string.dark)
}

@Composable
internal fun WidgetThemeMode.localizedLabel(): String = when (this) {
    WidgetThemeMode.FollowApp -> appString(R.string.follow_app)
    WidgetThemeMode.KgsBlue -> appString(R.string.kgs_blue)
    WidgetThemeMode.KgsWarm -> appString(R.string.kgs_warm)
    WidgetThemeMode.KgsFresh -> appString(R.string.kgs_fresh)
    WidgetThemeMode.SystemDynamic -> appString(R.string.android_colors)
}

@Composable
internal fun WidgetColorMode.localizedLabel(): String = when (this) {
    WidgetColorMode.FollowApp -> appString(R.string.follow_app)
    WidgetColorMode.FollowOs -> appString(R.string.follow_os)
    WidgetColorMode.Light -> appString(R.string.light)
    WidgetColorMode.Dark -> appString(R.string.dark)
}

@Composable
internal fun WidgetTaskDisplayMode.localizedLabel(): String = when (this) {
    WidgetTaskDisplayMode.Planned -> appString(R.string.planned_tasks)
    WidgetTaskDisplayMode.Unplanned -> appString(R.string.unplanned_tasks)
    WidgetTaskDisplayMode.Today -> appString(R.string.tasks_for_today)
}

@Composable
internal fun WidgetTaskCreateMode.localizedLabel(): String = when (this) {
    WidgetTaskCreateMode.Today -> appString(R.string.create_task_for_today)
    WidgetTaskCreateMode.Unplanned -> appString(R.string.create_unplanned_task)
}

@Composable
internal fun WidgetTaskSubtaskDefaultMode.localizedLabel(): String = when (this) {
    WidgetTaskSubtaskDefaultMode.FollowApp -> appString(R.string.follow_app)
    WidgetTaskSubtaskDefaultMode.Open -> appString(R.string.subtasks_default_open)
    WidgetTaskSubtaskDefaultMode.Closed -> appString(R.string.subtasks_default_closed)
}

@Composable
internal fun AppLanguageMode.localizedLabel(): String = when (this) {
    AppLanguageMode.System -> appString(R.string.follow_system)
    AppLanguageMode.English -> appString(R.string.english)
    AppLanguageMode.German -> appString(R.string.german)
}

@Composable
internal fun TaskEntity.localizedStatusLabel(): String = when (status?.uppercase()) {
    "IN-PROCESS" -> appString(R.string.in_progress)
    "CANCELLED" -> appString(R.string.aborted)
    "COMPLETED" -> appString(R.string.status_completed)
    "NEEDS-ACTION" -> appString(R.string.status_open)
    null -> if (isCompleted) appString(R.string.status_completed) else appString(R.string.status_open)
    else -> status!!
}


private fun CollectionEntity.typeLabel(): String = when {
    supportsEvents && supportsTasks -> "Events and tasks"
    supportsTasks -> "Tasks"
    else -> "Events"
}


private fun String.participantRoleLabel(): String =
    ParticipantRoleOption.entries.firstOrNull { it.value.equals(this, ignoreCase = true) }?.label ?: this

private fun String.eventStatusLabel(): String = when (uppercase(Locale.US)) {
    "TENTATIVE" -> "Tentative"
    "CANCELLED" -> "Cancelled"
    else -> "Confirmed"
}

@Composable
internal fun EventEntity.statusLabelText(): String = when ((status ?: EventStatusOption.Confirmed.value).uppercase(Locale.US)) {
    "TENTATIVE" -> appString(R.string.tentative)
    "CANCELLED" -> appString(R.string.cancelled)
    else -> appString(R.string.confirmed)
}

private fun String.eventClassLabel(): String = when (uppercase(Locale.US)) {
    "CONFIDENTIAL" -> "Show busy status when shared"
    "PRIVATE" -> "Hide this event when shared"
    else -> "Show full event when shared"
}

@Composable
internal fun EventEntity.classLabelText(): String = when ((classification ?: EventClassOption.Public.value).uppercase(Locale.US)) {
    "CONFIDENTIAL" -> appString(R.string.busy_only)
    "PRIVATE" -> appString(R.string.hidden)
    else -> appString(R.string.full_details)
}

private fun String.eventTransparencyLabel(): String = when (uppercase(Locale.US)) {
    "TRANSPARENT" -> "Free"
    else -> "Busy"
}

@Composable
internal fun EventEntity.transparencyLabelText(): String = when ((transparency ?: EventTransparencyOption.Busy.value).uppercase(Locale.US)) {
    "TRANSPARENT" -> appString(R.string.free)
    else -> appString(R.string.busy)
}

private fun String.participationLabel(): String = when (uppercase(Locale.US)) {
    "ACCEPTED" -> "Accepted"
    "DECLINED" -> "Declined"
    "TENTATIVE" -> "Tentative"
    "DELEGATED" -> "Delegated"
    else -> "No response"
}

@Composable
private fun String.participationColor(): Color = when (uppercase(Locale.US)) {
    "ACCEPTED" -> Color(0xFF00A86B)
    "DECLINED" -> Color(0xFFE53935)
    "TENTATIVE" -> Color(0xFFFFA000)
    "DELEGATED" -> Color(0xFF5B6CFF)
    else -> Color(0xFF6B7280)
}

private fun CalendarParticipant.displayParticipationLabel(): String =
    deliveryStatusLabel() ?: partstat.participationLabel()

@Composable
internal fun CalendarParticipant.localizedDisplayParticipationLabel(): String =
    localizedDeliveryStatusLabel() ?: when (partstat.uppercase(Locale.US)) {
        "ACCEPTED" -> appString(R.string.accept)
        "DECLINED" -> appString(R.string.decline)
        "TENTATIVE" -> appString(R.string.tentative)
        "DELEGATED" -> appString(R.string.delegated)
        else -> appString(R.string.no_response)
    }

@Composable
internal fun CalendarParticipant.displayParticipationColor(): Color =
    if (deliveryStatusLabel() != null) deliveryStatusColor() else partstat.participationColor()

private fun CalendarParticipant.deliveryStatusLabel(): String? {
    val code = scheduleStatus
        ?.substringBefore(',')
        ?.substringBefore(';')
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: return null
    return when {
        code == "1.0" -> "Preparing invitation"
        code == "1.1" -> "Invitation sent"
        code == "1.2" || code == "2.0" -> null
        code == "3.7" -> "Invitation could not be delivered"
        code.startsWith("3.") -> "Invitation could not be delivered"
        code.startsWith("5.") -> "Invitation could not be delivered"
        else -> null
    }
}

@Composable
internal fun CalendarParticipant.deliveryStatusColor(): Color =
    when {
        scheduleStatus.orEmpty().startsWith("1.") -> Color(0xFF4DA3FF)
        else -> Color(0xFFE53935)
    }



internal fun TaskEntity.statusSortRank(): Int = when (effectiveStatus()) {
    "IN-PROCESS" -> 0
    "NEEDS-ACTION" -> 1
    "COMPLETED" -> 2
    "CANCELLED" -> 3
    else -> 4
}

@Composable
internal fun PlannedTaskSort.localizedLabel(): String = when (this) {
    PlannedTaskSort.Date -> appString(R.string.date)
    PlannedTaskSort.Priority -> appString(R.string.priority)
    PlannedTaskSort.Status -> appString(R.string.status)
}


@Composable
internal fun TaskDefaultSchedule.localizedLabel(): String = when (this) {
    TaskDefaultSchedule.None -> appString(R.string.no_date)
    TaskDefaultSchedule.DateOnly -> appString(R.string.date_only)
    TaskDefaultSchedule.DateTime -> appString(R.string.date_and_time)
}


@Composable
internal fun DurationUnit.localizedLabel(): String = when (this) {
    DurationUnit.Minutes -> appString(R.string.minutes)
    DurationUnit.Hours -> appString(R.string.hours)
}


@Composable
internal fun Int.localizedDurationLabel(): String =
    if (this < 60) {
        stringResource(R.string.duration_minutes, this)
    } else {
        val hoursText = stringResource(R.string.duration_hours, this / 60)
        if (this % 60 == 0) hoursText else "$hoursText ${stringResource(R.string.duration_minutes, this % 60)}"
    }


@Composable
internal fun CollectionEntity.localizedPermissionMetadataRows(): List<Pair<String, String>> {
    val capabilities = capabilitiesJson.toJsonObjectOrNull()
    val androidAccessLevel = capabilities?.optIntOrNull("androidAccessLevel")
    val androidVisible = capabilities?.optBooleanOrNull("androidVisible")
    val androidSyncEvents = capabilities?.optBooleanOrNull("androidSyncEvents")
    val reminders = capabilities?.optBooleanOrNull("reminders")
    val availability = capabilities?.optStringOrNull("androidAvailability")?.takeIf { it.isNotBlank() }
    val androidAccessLabel = androidAccessLevel?.localizedAndroidAccessLevel()
    val calDavCanWriteContent = capabilities?.optBooleanOrNull("canWriteContent")
    val calDavCanWriteProperties = capabilities?.optBooleanOrNull("canWriteProperties")
    val calDavCanCreate = capabilities?.optBooleanOrNull("canCreateResources")
    val calDavCanDelete = capabilities?.optBooleanOrNull("canDeleteResources")
    val calDavCanReadFreeBusy = capabilities?.optBooleanOrNull("canReadFreeBusy")
    val calDavIncrementalSync = capabilities?.optBooleanOrNull("supportsSyncCollection")
    val accessLabel = when {
        isAndroidProviderForUi() && readOnly -> listOfNotNull(
            stringResource(R.string.calendar_access_read_only),
            androidAccessLabel,
        ).joinToString(" · ")
        isAndroidProviderForUi() && androidAccessLabel != null -> stringResource(R.string.calendar_access_writable_level, androidAccessLabel)
        readOnly -> stringResource(R.string.calendar_access_read_only)
        else -> stringResource(R.string.calendar_access_full)
    }
    val identifier = externalId?.takeIf { it.isNotBlank() } ?: href
    return buildList {
        add(stringResource(R.string.source) to localizedSourceTypeLabel())
        add(stringResource(R.string.calendar_access) to accessLabel)
        add(stringResource(R.string.calendar_writes) to if (isReadOnlyForUi()) stringResource(R.string.calendar_writes_not_allowed) else stringResource(R.string.calendar_writes_allowed))
        add(stringResource(R.string.calendar_sync_state) to if (isEnabled) stringResource(R.string.calendar_active) else stringResource(R.string.calendar_inactive))
        if (isAndroidProviderForUi()) {
            add(stringResource(R.string.calendar_device_visibility) to androidVisible.localizedVisibleHidden())
            add(stringResource(R.string.calendar_provider_sync) to androidSyncEvents.localizedSupportedUnsupported())
        } else if (sourceType == SourceType.CalDav) {
            add(stringResource(R.string.caldav_edit_events) to calDavCanWriteContent.localizedSupportedUnsupported(defaultSupported = !readOnly))
            add(stringResource(R.string.caldav_edit_calendar) to calDavCanWriteProperties.localizedSupportedUnsupported(defaultSupported = !readOnly))
            add(stringResource(R.string.caldav_create_items) to calDavCanCreate.localizedSupportedUnsupported(defaultSupported = !readOnly))
            add(stringResource(R.string.caldav_delete_items) to calDavCanDelete.localizedSupportedUnsupported(defaultSupported = !readOnly))
            add(stringResource(R.string.caldav_free_busy) to calDavCanReadFreeBusy.localizedSupportedUnsupported(defaultSupported = true))
            add(stringResource(R.string.caldav_incremental_sync) to calDavIncrementalSync.localizedSupportedUnsupported())
        }
        add(stringResource(R.string.calendar_reminder_permission) to reminders.localizedSupportedUnsupported(defaultSupported = true))
        availability?.let { add(stringResource(R.string.calendar_availability_values) to it) }
        add(stringResource(R.string.calendar_identifier) to identifier)
        add(stringResource(R.string.calendar_sync_token) to (syncToken ?: stringResource(R.string.calendar_unknown)))
    }
}

@Composable
private fun CollectionEntity.localizedSourceTypeLabel(): String = when {
    href.isLocalCollectionHrefUi() || sourceType == SourceType.Local -> stringResource(R.string.local_calendar)
    isAndroidProviderForUi() -> stringResource(R.string.android_device_calendars)
    href.isReadOnlyCollectionHrefUi() || sourceType == SourceType.ReadOnlyUrl -> stringResource(R.string.read_only_url)
    else -> stringResource(R.string.caldav)
}

@Composable
private fun Int.localizedAndroidAccessLevel(): String = when (this) {
    0 -> stringResource(R.string.calendar_android_access_none)
    100 -> stringResource(R.string.calendar_android_access_freebusy)
    200 -> stringResource(R.string.calendar_android_access_read)
    300 -> stringResource(R.string.calendar_android_access_respond)
    400 -> stringResource(R.string.calendar_android_access_override)
    500 -> stringResource(R.string.calendar_android_access_contributor)
    600 -> stringResource(R.string.calendar_android_access_editor)
    700 -> stringResource(R.string.calendar_android_access_owner)
    800 -> stringResource(R.string.calendar_android_access_root)
    else -> this.toString()
}

@Composable
internal fun Boolean?.localizedSupportedUnsupported(defaultSupported: Boolean? = null): String =
    when (this ?: defaultSupported) {
        true -> stringResource(R.string.calendar_supported)
        false -> stringResource(R.string.calendar_unsupported)
        null -> stringResource(R.string.calendar_unknown)
    }

@Composable
private fun Boolean?.localizedVisibleHidden(): String =
    when (this) {
        true -> stringResource(R.string.calendar_visible)
        false -> stringResource(R.string.calendar_hidden)
        null -> stringResource(R.string.calendar_unknown)
    }


private fun CollectionEntity.kindLabel(): String = buildList {
    if (supportsEvents) add("Events")
    if (supportsTasks) add("Tasks")
}.joinToString(" & ").ifBlank { "Calendar" }

@Composable
internal fun CollectionEntity.localizedKindLabel(): String = buildList {
    if (supportsEvents) add(stringResource(R.string.events))
    if (supportsTasks) add(stringResource(R.string.tasks))
}.joinToString(" & ").ifBlank { stringResource(R.string.calendar) }
