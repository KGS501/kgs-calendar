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

internal const val TaskDetailMorphDurationMs = 460
internal const val TaskDetailControlsRevealDelayMs = 220
private val TaskDetailMorphBoundsTransform = androidx.compose.animation.BoundsTransform { _, _ ->
    tween(TaskDetailMorphDurationMs, easing = MotionEmphasized)
}

internal fun taskDetailMorphKey(task: TaskEntity): String =
    "task-detail:${task.collectionHref}:${task.resourceHref}"

internal fun DetailSheet.transitionKey(): String = when (this) {
    is DetailSheet.Event -> "event:${event.collectionHref}:${event.resourceHref}:${event.startsAtMillis}"
    is DetailSheet.Task -> taskDetailMorphKey(task)
}

/**
 * Groups views that share a single persistent surface. The Day and 3-day views are the SAME
 * surface — the timeline — so switching between them is NOT a screen swap: it stays composed
 * and animates the day-column width (3 ? 1 or 1 ? 3), which makes the focused day widen/narrow
 * in place while the neighbouring days physically slide in/out. Every other view is a distinct
 * surface, reached by a cross-fade (or, for the month grid, a container-transform morph of the
 * tapped day). Driving [AnimatedContent] off the *group* instead of the raw view is what keeps
 * Day?3-day from cross-fading.
 */
/**
 * Marks this composable as a shared element keyed by [key]. When the active view changes
 * (e.g. 3-day ? 1-day, or month-cell ? 1-day), the element with the same key in the
 * outgoing and incoming content morphs from one bounds to the other.
 *
 * The resize mode is [ScaleToBounds][SharedTransitionScope.ResizeMode.ScaleToBounds]
 * (Material's "container transform"): the content is laid out ONCE at its own target size
 * and then *scaled* as a single rigid block to fit the animating bounds. This is the key
 * to the Google-Calendar-style zoom — the whole day (grid lines, header, every card) grows
 * or shrinks together as one unit, instead of each child re-laying-out frame by frame
 * (RemeasureToBounds), which is what made contents drift to the bottom and snap at the end.
 * `FillBounds` lets width and height scale independently so the month?day case reads as a
 * zoom while the 3-day?1-day case reads as a pure column-width stretch.
 *
 * Pass [enabled] = false to register the key without participating in the morph, so that
 * only the *single* matching day in each view is a shared element (the others just cross-
 * fade with their scene). No-op entirely when not inside the morph container.
 */
@Composable
internal fun Modifier.morphBounds(
    key: Any,
    enabled: Boolean = true,
    enter: EnterTransition = MorphDayEnter,
    exit: ExitTransition = MorphDayExit,
    overlayClip: SharedTransitionScope.OverlayClip = MorphRoundedClip,
    overlayShape: Shape? = null,
    boundsTransform: androidx.compose.animation.BoundsTransform = MorphDayBoundsTransform,
    remeasureToBounds: Boolean = false,
): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val anim = LocalMorphAnimatedVisibilityScope.current ?: return this
    return with(shared) {
        // Always remember the state (stable slot) so toggling [enabled] across
        // recompositions doesn't shift the composition's remember table.
        val contentState = rememberSharedContentState(key)
        if (!enabled) return@with this@morphBounds
        this@morphBounds.sharedBounds(
            sharedContentState = contentState,
            animatedVisibilityScope = anim,
            enter = enter,
            exit = exit,
            boundsTransform = boundsTransform,
            resizeMode = if (remeasureToBounds) {
                SharedTransitionScope.ResizeMode.RemeasureToBounds
            } else {
                SharedTransitionScope.ResizeMode.scaleToBounds(
                    contentScale = ContentScale.FillBounds,
                    alignment = Alignment.TopStart,
                )
            },
            clipInOverlayDuringTransition = overlayShape?.let { OverlayClip(it) } ?: overlayClip,
            placeholderSize = if (remeasureToBounds) {
                SharedTransitionScope.PlaceholderSize.AnimatedSize
            } else {
                SharedTransitionScope.PlaceholderSize.ContentSize
            },
            // Keep the morphing element above the fading content of both scenes.
            zIndexInOverlay = 1f,
        )
    }
}

@Composable
internal fun Modifier.taskDetailMorph(key: String, cornerRadius: Dp): Modifier =
    morphBounds(
        key = key,
        enter = EnterTransition.None,
        exit = fadeOut(animationSpec = snap()),
        overlayShape = RoundedCornerShape(cornerRadius),
        boundsTransform = TaskDetailMorphBoundsTransform,
        remeasureToBounds = true,
    )

@Composable
internal fun Modifier.taskDetailFadeOverlay(): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    return with(shared) {
        this@taskDetailFadeOverlay.renderInSharedTransitionScopeOverlay(10f) { true }
    }
}

@Composable
internal fun rememberTaskDetailMorphCornerRadius(
    enabled: Boolean,
    ownRadius: Dp,
    counterpartRadius: Dp,
): Dp {
    val morphScope = LocalMorphAnimatedVisibilityScope.current
    if (!enabled || morphScope == null) return ownRadius
    val radius by morphScope.transition.animateDp(
        transitionSpec = { tween(TaskDetailMorphDurationMs, easing = MotionEmphasized) },
        label = "taskDetailCornerRadius",
    ) { state ->
        if (state == EnterExitState.Visible) ownRadius else counterpartRadius
    }
    return radius
}

@Composable
internal fun rememberTaskDetailMorphProgress(): Float {
    val morphScope = LocalMorphAnimatedVisibilityScope.current ?: return 1f
    val progress by morphScope.transition.animateFloat(
        transitionSpec = { tween(TaskDetailMorphDurationMs, easing = MotionEmphasized) },
        label = "taskDetailMorphProgress",
    ) { state ->
        if (state == EnterExitState.Visible) 1f else 0f
    }
    return progress
}

internal fun lerpFloat(start: Float, end: Float, fraction: Float): Float =
    start + (end - start) * fraction.coerceIn(0f, 1f)

internal fun phasedProgress(progress: Float, start: Float, end: Float): Float {
    if (end <= start) return if (progress >= end) 1f else 0f
    return ((progress - start) / (end - start)).coerceIn(0f, 1f)
}

internal fun Modifier.taskDetailRevealLayout(
    progress: Float,
    offsetY: Dp,
    alphaProgress: Float = progress,
): Modifier = this
    .layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val visibleHeight = (placeable.height * progress.coerceIn(0f, 1f)).roundToInt()
        layout(placeable.width, visibleHeight) {
            placeable.placeRelative(0, 0)
        }
    }
    .graphicsLayer {
        alpha = alphaProgress.coerceIn(0f, 1f)
        translationY = -offsetY.toPx() * (1f - progress.coerceIn(0f, 1f))
        clip = false
    }

// Rounds the morphing box's corners while it's lifted into the transition overlay, so the
// growing/shrinking month-cell <-> day "box" has soft edges like the month cell it came from
// (otherwise the solid background backing is a hard rectangle). Applied to both sides of the
// morph so it rounds in both directions. Only active during the transition; the settled views
// keep their own shapes.

@Composable
internal fun String.localizedParticipantRoleLabel(): String =
    ParticipantRoleOption.entries.firstOrNull { it.value.equals(this, ignoreCase = true) }?.localizedLabel() ?: this

@Composable
internal fun EventParticipantStack(
    attendees: List<CalendarParticipant>,
    eventColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    circleSize: Dp = 26.dp,
    maxVisible: Int = 5,
    muted: Boolean = false,
) {
    if (attendees.isEmpty() || maxVisible <= 0) return
    BoxWithConstraints(
        modifier = modifier
            .height(circleSize + 12.dp)
            .graphicsLayer { clip = false },
        contentAlignment = Alignment.CenterEnd,
    ) {
        val step = circleSize * 0.64f
        val horizontalPadding = 4.dp
        val verticalPadding = 3.dp
        val byWidth = (((maxWidth - horizontalPadding * 2 - circleSize) / step).toInt() + 1).coerceAtLeast(1)
        val capacity = min(maxVisible, byWidth)
        val needsMore = attendees.size > capacity
        val visibleCount = if (needsMore) (capacity - 1).coerceAtLeast(1) else capacity
        val visible = attendees.take(visibleCount)
        val totalCount = visible.size + if (needsMore) 1 else 0
        val stackWidth = circleSize + step * (totalCount - 1).coerceAtLeast(0).toFloat()
        val containerColor = if (muted) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
        } else {
            Color.White.copy(alpha = 0.94f)
        }
        Box(
            modifier = Modifier
                .width(stackWidth + horizontalPadding * 2)
                .height(circleSize + verticalPadding * 2)
                .graphicsLayer { clip = false },
        ) {
            val containerShape = RoundedCornerShape(999.dp)
            Box(
                Modifier
                    .matchParentSize()
                    .clip(containerShape)
                    .background(containerColor),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { clip = false },
            ) {
                visible.forEachIndexed { index, attendee ->
                    ParticipantCircle(
                        label = attendee.initials(),
                        color = attendee.avatarColor().participantMuted(muted, 0.70f),
                        contentColor = if (muted) WarmInk.copy(alpha = 0.66f) else Color.White,
                        borderColor = attendee.displayParticipationColor().participantMuted(muted, 0.82f),
                        modifier = Modifier
                            .offset(x = horizontalPadding + step * index.toFloat(), y = verticalPadding)
                            .size(circleSize),
                    )
                }
                if (needsMore) {
                    ParticipantCircle(
                        label = "+${attendees.size - visible.size}",
                        color = contentColor.participantMuted(muted, 0.78f).copy(alpha = 0.92f),
                        contentColor = if (muted) WarmInk.copy(alpha = 0.66f) else if (contentColor.isDark()) Color.White else Color(0xFF1C1A18),
                        borderColor = eventColor.participantMuted(muted, 0.82f),
                        modifier = Modifier
                            .offset(x = horizontalPadding + step * visible.size.toFloat(), y = verticalPadding)
                            .size(circleSize),
                    )
                }
            }
        }
    }
}

@Composable
internal fun CompactParticipantStatusDots(
    attendees: List<CalendarParticipant>,
    modifier: Modifier = Modifier,
    maxVisible: Int = 3,
    scale: Float = 1f,
) {
    val visible = attendees.take(maxVisible)
    val hasMore = attendees.size > visible.size
    val safeScale = scale.coerceIn(0.34f, 1f)
    val dotSize = (5f * safeScale).dp
    val horizontalPadding = (5f * safeScale).dp
    val verticalPadding = (2.5f * safeScale).dp
    val spacing = (3f * safeScale).dp
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 2.dp, bottomEnd = 2.dp),
        color = Color.White.copy(alpha = 0.94f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = horizontalPadding, vertical = verticalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing),
        ) {
            visible.forEach { attendee ->
                Box(
                    modifier = Modifier
                        .size(dotSize)
                        .clip(CircleShape)
                        .background(attendee.displayParticipationColor()),
                )
            }
            if (hasMore) {
                Box(
                    modifier = Modifier
                        .size(dotSize)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f)),
                )
            }
        }
    }
}

@Composable
internal fun ParticipantCircle(
    label: String,
    color: Color,
    contentColor: Color,
    borderColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(color)
            .border(2.5.dp, borderColor.copy(alpha = 0.96f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = contentColor,
            fontSize = 9.sp,
            lineHeight = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

internal fun CalendarParticipant.initials(): String {
    val source = displayName.ifBlank { email }
    val parts = source.split(Regex("\\s+")).filter { it.isNotBlank() }
    val initials = when {
        parts.size >= 2 -> "${parts.first().first()}${parts.last().first()}"
        source.isNotBlank() -> source.take(2)
        else -> "?"
    }
    return initials.uppercase(Locale.ROOT)
}

internal fun CalendarParticipant.avatarColor(): Color {
    val colors = listOf(
        Color(0xFF2563A8),
        Color(0xFF0F766E),
        Color(0xFF7C3AED),
        Color(0xFFC2410C),
        Color(0xFFBE123C),
        Color(0xFF4D7C0F),
    )
    val index = abs((email.ifBlank { displayName }).hashCode()) % colors.size
    return colors[index]
}

private fun Color.participantMuted(muted: Boolean, alpha: Float): Color =
    if (muted) greyedOut(0.78f).copy(alpha = alpha) else this

internal fun String?.toCalendarParticipant(): CalendarParticipant? =
    runCatching {
        if (isNullOrBlank()) return@runCatching null
        val obj = JSONObject(this)
        val email = obj.optString("email")
        CalendarParticipant(
            name = obj.optString("name", email),
            email = email,
            partstat = obj.optString("partstat", "NEEDS-ACTION"),
            role = obj.optString("role", "REQ-PARTICIPANT"),
            rsvp = obj.optString("rsvp").equals("TRUE", ignoreCase = true),
            scheduleStatus = obj.optString("scheduleStatus").takeIf { it.isNotBlank() },
        )
    }.getOrNull()

internal fun String?.toCalendarParticipants(): List<CalendarParticipant> =
    runCatching {
        if (isNullOrBlank()) return@runCatching emptyList()
        val array = JSONArray(this)
        buildList {
            repeat(array.length()) { index ->
                val obj = array.optJSONObject(index) ?: return@repeat
                val email = obj.optString("email")
                if (email.isBlank()) return@repeat
                add(
                    CalendarParticipant(
                        name = obj.optString("name", email),
                        email = email,
                        partstat = obj.optString("partstat", "NEEDS-ACTION"),
                        role = obj.optString("role", "REQ-PARTICIPANT"),
                        rsvp = obj.optString("rsvp").equals("TRUE", ignoreCase = true),
                        scheduleStatus = obj.optString("scheduleStatus").takeIf { it.isNotBlank() },
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())

internal fun List<CalendarParticipant>.toAttendeesJson(): String? =
    takeIf { it.isNotEmpty() }?.let { participants ->
        JSONArray().apply {
            participants.forEach { attendee ->
                put(
                    JSONObject().apply {
                        put("name", attendee.name)
                        put("email", attendee.email)
                        put("partstat", attendee.partstat)
                        put("role", attendee.role)
                        put("rsvp", if (attendee.rsvp) "TRUE" else "FALSE")
                        attendee.scheduleStatus?.let { put("scheduleStatus", it) }
                    },
                )
            }
        }.toString()
    }

internal fun String.isLikelyEmailAddress(): Boolean =
    matches(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))

private val TaskStatusOptions = listOf(
    "NEEDS-ACTION",
    "IN-PROCESS",
    "COMPLETED",
    "CANCELLED",
)

@Composable
private fun taskStatusOptionLabel(value: String): String = when (value) {
    "IN-PROCESS" -> appString(R.string.in_progress)
    "COMPLETED" -> appString(R.string.status_completed)
    "CANCELLED" -> appString(R.string.aborted)
    else -> appString(R.string.status_open)
}

/** Normalised effective status, falling back to the legacy isCompleted boolean. */
internal fun TaskEntity.effectiveStatus(): String = status?.uppercase()
    ?: if (isCompleted) "COMPLETED" else "NEEDS-ACTION"

/**
 * A task is "inactive" when it's done OR cancelled — both should be greyed out and have
 * their priority animation suppressed.
 */
internal fun TaskEntity.isInactive(): Boolean = isCompleted || effectiveStatus() == "CANCELLED"

/** Sort weight for the "Status" sort: In Bearbeitung first, then Offen, then others. */

internal enum class PlannedTaskSort(val label: String) {
    Date("Date"),
    Priority("Priority"),
    Status("Status");

    fun next(): PlannedTaskSort = entries[(ordinal + 1) % entries.size]
}

/**
 * Shared task checkbox used in the day view, sidebar, detail sheet and editor.
 *
 * - Tap toggles between COMPLETED and NEEDS-ACTION, unless the caller requests the picker.
 * - Long-press opens a status menu (Offen / In Bearbeitung / Erledigt / Abgebrochen).
 * - The glyph reflects the status: empty circle (open), slow indeterminate spinner
 *   (in progress), filled check (completed) and an X (cancelled).
 */
@Composable
internal fun TaskStatusCheckbox(
    status: String,
    tint: Color,
    onStatusChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    tapOpensPicker: Boolean = false,
    boxSize: Dp = 40.dp,
    iconSize: Dp = 24.dp,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(boxSize)
            .clip(CircleShape)
            .combinedClickable(
                onClick = {
                    if (tapOpensPicker) {
                        menuOpen = true
                    } else {
                        onStatusChange(if (status == "COMPLETED") "NEEDS-ACTION" else "COMPLETED")
                    }
                },
                onLongClick = { menuOpen = true },
            ),
        contentAlignment = Alignment.Center,
    ) {
        // The outer ring is always drawn; the state glyph (check / X / spinner) lives
        // *inside* the ring, never replacing it.
        if (status == "COMPLETED") {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(iconSize),
            )
        } else {
            Icon(
                Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(iconSize),
            )
            // Inner glyph centered inside the ring.
            when (status) {
                "IN-PROCESS" -> Box(
                    modifier = Modifier
                        .size(iconSize * 0.34f)
                        .clip(CircleShape)
                        .background(tint),
                )
                "CANCELLED" -> Icon(
                    Icons.Default.Close,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(iconSize * 0.58f),
                )
            }
        }
    }
    if (menuOpen) {
        TaskStatusPickerDialog(
            current = status,
            onPick = {
                menuOpen = false
                onStatusChange(it)
            },
            onDismiss = { menuOpen = false },
        )
    }
}

/**
 * Centered, rounded status picker. Rendered as a Dialog (its own window) so it is
 * never affected by the card's priority wiggle animation, and its corners match the
 * rounded look of the card overflow menu.
 */
@Composable
private fun TaskStatusPickerDialog(
    current: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(vertical = 10.dp)) {
                Text(
                    appString(R.string.status),
                    color = WarmInk,
                    fontSize = 14.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
                )
                TaskStatusOptions.forEach { value ->
                    val label = taskStatusOptionLabel(value)
                    val selected = value == current
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(value) }
                            .padding(horizontal = 22.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.RadioButtonUnchecked, contentDescription = null, tint = WarmInk, modifier = Modifier.size(22.dp))
                            when (value) {
                                "IN-PROCESS" -> Box(Modifier.size(8.dp).clip(CircleShape).background(WarmInk))
                                "CANCELLED" -> Icon(Icons.Default.Close, contentDescription = null, tint = WarmInk, modifier = Modifier.size(13.dp))
                                "COMPLETED" -> Icon(Icons.Default.Check, contentDescription = null, tint = WarmInk, modifier = Modifier.size(13.dp))
                            }
                        }
                        Text(
                            label,
                            color = WarmInk,
                            fontSize = 16.sp,
                            lineHeight = 19.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f),
                        )
                        if (selected) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = WarmBrown, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Returns the list with the calendar matching [defaultHref] (if any) pulled to the
 * front, keeping the relative order of the remaining items. Used wherever the user
 * picks a calendar for a new entry so the chosen default is always the first chip.
 */

internal fun DetailSheet.preferredInitialSnap(): SheetSnap = when (this) {
    is DetailSheet.Event -> {
        val description = event.description.orEmpty().cleanCalendarDisplayText()
        val metadataScore =
            (if (description.length >= 72 || description.count { it == '\n' } >= 2) 2 else if (description.isNotBlank()) 1 else 0) +
                (if (event.location.orEmpty().isNotBlank()) 1 else 0) +
                (if (event.categories.orEmpty().isNotBlank()) 1 else 0) +
                (if (event.attendeesJson.orEmpty().isNotBlank()) 2 else 0) +
                (if (!event.status.isNullOrBlank() || !event.classification.isNullOrBlank() || !event.transparency.isNullOrBlank()) 1 else 0) +
                (if (!event.recurrenceRule.isNullOrBlank()) 1 else 0) +
                (if (event.title.length >= 34) 1 else 0)
        when {
            metadataScore >= 2 -> SheetSnap.Expanded
            metadataScore == 0 -> SheetSnap.Quarter
            else -> SheetSnap.Half
        }
    }
    is DetailSheet.Task -> {
        val notes = task.notes.orEmpty().cleanCalendarDisplayText()
        val metadataScore =
            (if (notes.length >= 72 || notes.count { it == '\n' } >= 2) 2 else if (notes.isNotBlank()) 1 else 0) +
                (if (task.location.orEmpty().isNotBlank()) 1 else 0) +
                (if (task.url.orEmpty().isNotBlank()) 1 else 0) +
                (if (task.categories.orEmpty().isNotBlank()) 1 else 0) +
                (if (!task.recurrenceRule.isNullOrBlank()) 1 else 0) +
                (if (task.priority != null && task.priority != 9) 1 else 0) +
                (if (task.displayProgress() > 0) 1 else 0) +
                (if (task.title.length >= 34) 1 else 0)
        when {
            metadataScore >= 2 -> SheetSnap.Expanded
            metadataScore == 0 -> SheetSnap.Quarter
            else -> SheetSnap.Half
        }
    }
}

internal fun DetailSheet.estimatedPopoverHeight(): Dp = when (this) {
    is DetailSheet.Event -> {
        val description = event.description.orEmpty().cleanCalendarDisplayText()
        val location = event.location.orEmpty()
        val height = 186 +
            82 +
            detailTextBlockHeight(description, baseWhenBlank = 0) +
            if (location.isNotBlank()) {
                86 + if (location.shouldAttemptMapPreview(event.locationMapVerified)) 150 else 0
            } else {
                0
            } +
            if (!event.categories.isNullOrBlank()) 72 else 0 +
            event.estimatedParticipantsDetailHeight() +
            120 +
            if (!event.recurrenceRule.isNullOrBlank()) 72 else 0 +
            if (!event.syncError.isNullOrBlank()) 72 else 0 +
            82
        height.dp
    }
    is DetailSheet.Task -> {
        val notes = task.notes.orEmpty().cleanCalendarDisplayText()
        val location = task.location.orEmpty()
        val height = 186 +
            72 +
            76 +
            76 +
            if (!task.recurrenceRule.isNullOrBlank()) 72 else 0 +
            detailTextBlockHeight(notes, baseWhenBlank = 0) +
            if (location.isNotBlank()) {
                86 + if (location.shouldAttemptMapPreview(task.locationMapVerified)) 150 else 0
            } else {
                0
            } +
            if (!task.url.isNullOrBlank()) 72 else 0 +
            if (!task.categories.isNullOrBlank()) 72 else 0 +
            if (task.completedAtMillis != null) 72 else 0 +
            if (!task.syncError.isNullOrBlank()) 72 else 0 +
            26
        height.dp
    }
}

internal fun CollectionEntity.estimatedSettingsHeight(): Dp =
    392.dp

private fun detailTextBlockHeight(value: String, baseWhenBlank: Int = 72): Int {
    val cleaned = value.cleanCalendarDisplayText()
    if (cleaned.isBlank()) return baseWhenBlank
    val explicitLines = cleaned.count { it == '\n' } + 1
    val estimatedWrappedLines = (cleaned.length / 34) + 1
    val lines = max(explicitLines, estimatedWrappedLines).coerceIn(1, 24)
    return 54 + lines * 21
}

private fun EventEntity.estimatedParticipantsDetailHeight(): Int {
    val organizer = organizerJson.toCalendarParticipant()
    val attendees = attendeesJson.toCalendarParticipants()
    if (organizer == null && attendees.isEmpty()) return 0

    val header = 14
    val surfacePadding = 22
    val organizerHeight = if (organizer != null) 27 else 0
    val attendeeRows = attendees.size * 44
    val ownParticipationControls = if (attendees.isNotEmpty()) 78 else 0
    val spacingCount = listOf(
        1, // title to first content
        if (organizer != null && attendees.isNotEmpty()) 1 else 0,
        attendees.size.coerceAtLeast(0),
        if (ownParticipationControls > 0) 2 else 0,
    ).sum()
    return surfacePadding + header + organizerHeight + attendeeRows + ownParticipationControls + spacingCount * 10
}
