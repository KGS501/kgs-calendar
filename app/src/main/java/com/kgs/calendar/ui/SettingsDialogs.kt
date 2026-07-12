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
internal fun CollectionSelectionDialog(
    title: String,
    selectedHref: String?,
    collections: List<CollectionEntity>,
    hiddenCollectionHrefs: Set<String> = emptySet(),
    onSelected: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SettingsRadioRow(selected = selectedHref == null, title = appString(R.string.automatic), onClick = { onSelected(null) })
                collections.forEach { collection ->
                    SettingsCollectionChoiceRow(
                        selected = selectedHref == collection.href,
                        collection = collection,
                        hidden = collection.href in hiddenCollectionHrefs,
                        onClick = { onSelected(collection.href) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(appString(R.string.close)) } },
    )
}

@Composable
private fun SettingsCollectionChoiceRow(
    selected: Boolean,
    collection: CollectionEntity,
    hidden: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(SettingsControlHeight)
            .clip(SettingsControlShape)
            .then(if (hidden) Modifier.dashedBorder(SyncPendingOrange, 25.dp) else Modifier)
            .clickable(onClick = onClick),
        shape = SettingsControlShape,
        color = if (selected) WarmPeach else settingsControlColor(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(collection.color)),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(collection.displayName, color = WarmInk, fontSize = 15.sp, lineHeight = 18.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(collection.localizedKindLabel(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(WarmBrown),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

internal enum class TaskDefaultSchedule(val label: String) {
    None("No date"),
    DateOnly("Date only"),
    DateTime("Date and time"),
}

internal enum class DurationUnit(val label: String, val minutes: Int) {
    Minutes("Minutes", 1),
    Hours("Hours", 60),
}

private enum class EventDurationChoice {
    ThirtyMinutes,
    OneHour,
    Custom,
}

@Composable
internal fun ThemeSelectionDialog(
    selected: AppThemeMode,
    onSelected: (AppThemeMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(appString(R.string.choose_design)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppThemeMode.entries.forEach { mode ->
                    ThemeChoiceRow(
                        mode = mode,
                        selected = selected == mode,
                        colors = mode.themePreviewColors(scheme),
                        onClick = { onSelected(mode) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(appString(R.string.close)) } },
    )
}

@Composable
private fun ThemeChoiceRow(
    mode: AppThemeMode,
    selected: Boolean,
    colors: List<Color>,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(SettingsControlHeight)
            .clip(SettingsControlShape)
            .clickable(onClick = onClick),
        shape = SettingsControlShape,
        color = if (selected) WarmPeach else settingsControlColor(),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                mode.localizedLabel(),
                color = WarmInk,
                fontSize = 15.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                colors.forEach { color ->
                    Box(Modifier.size(18.dp).clip(CircleShape).background(color))
                }
            }
            if (selected) {
                Icon(Icons.Default.Check, contentDescription = null, tint = WarmBrown, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
internal fun AppThemeMode.themePreviewColors(currentScheme: androidx.compose.material3.ColorScheme): List<Color> = when (this) {
    AppThemeMode.KgsBlue -> listOf(Color(0xFF2563A8), Color(0xFFDCEBFF), Color.White, Color(0xFF4FA7BD))
    AppThemeMode.KgsWarm -> listOf(Color(0xFF9E572B), Color(0xFFFBE9E2), Color.White, Color(0xFF56B0A2))
    AppThemeMode.KgsFresh -> listOf(Color(0xFF0E7C66), Color(0xFFDDF2EC), Color.White, Color(0xFFE29D3E))
    AppThemeMode.SystemDynamic -> listOf(currentScheme.primary, currentScheme.background, currentScheme.surface, currentScheme.tertiary)
}

@Composable
internal fun EventDurationDialog(
    currentMinutes: Int,
    onSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialChoice = remember(currentMinutes) {
        when (currentMinutes) {
            30 -> EventDurationChoice.ThirtyMinutes
            60 -> EventDurationChoice.OneHour
            else -> EventDurationChoice.Custom
        }
    }
    var choice by remember(currentMinutes) { mutableStateOf(initialChoice) }
    val initialUnit = remember(currentMinutes) {
        if (currentMinutes >= 60 && currentMinutes % 60 == 0) DurationUnit.Hours else DurationUnit.Minutes
    }
    var unit by remember(currentMinutes) { mutableStateOf(initialUnit) }
    var amountText by remember(currentMinutes) {
        mutableStateOf((if (initialUnit == DurationUnit.Hours) currentMinutes / 60 else currentMinutes).coerceAtLeast(1).toString())
    }
    val minuteDurationLabel = DurationUnit.Minutes.localizedLabel()
    val hourDurationLabel = DurationUnit.Hours.localizedLabel()
    val durationUnitLabels = listOf(
        DurationUnit.Minutes to minuteDurationLabel,
        DurationUnit.Hours to hourDurationLabel,
    )
    ModalEditorDialog(title = appString(R.string.default_event_duration), onDismiss = onDismiss) {
        PresetListRow(
            label = appString(R.string.thirty_min),
            selected = choice == EventDurationChoice.ThirtyMinutes,
            onClick = { choice = EventDurationChoice.ThirtyMinutes },
        )
        PresetListRow(
            label = appString(R.string.one_hour),
            selected = choice == EventDurationChoice.OneHour,
            onClick = { choice = EventDurationChoice.OneHour },
        )
        PresetListRow(
            label = appString(R.string.custom_duration),
            selected = choice == EventDurationChoice.Custom,
            onClick = { choice = EventDurationChoice.Custom },
        )
        AnimatedVisibility(visible = choice == EventDurationChoice.Custom) {
            NumberUnitRow(
                amount = amountText,
                onAmountChange = {
                    amountText = it
                    choice = EventDurationChoice.Custom
                },
                unitLabel = durationUnitLabels.firstOrNull { it.first == unit }?.second ?: minuteDurationLabel,
                onUnitSelected = { label ->
                    durationUnitLabels.firstOrNull { it.second == label }?.let { selectedUnit ->
                        unit = selectedUnit.first
                        choice = EventDurationChoice.Custom
                    }
                },
                unitOptions = durationUnitLabels.map { it.second },
                label = appString(R.string.duration),
            )
        }
        DialogActions(
            onDismiss = onDismiss,
            onSave = {
                val minutes = when (choice) {
                    EventDurationChoice.ThirtyMinutes -> 30
                    EventDurationChoice.OneHour -> 60
                    EventDurationChoice.Custom -> (amountText.toIntOrNull() ?: 1).coerceAtLeast(1) * unit.minutes
                }
                onSelected(minutes.coerceIn(5, 24 * 60))
            },
            saveEnabled = choice != EventDurationChoice.Custom || amountText.toIntOrNull() != null,
        )
    }
}

@Composable
internal fun <T> SettingsChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelected: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                options.forEach { option ->
                    SettingsRadioRow(
                        selected = option == selected,
                        title = label(option),
                        onClick = { onSelected(option) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(appString(R.string.close)) } },
    )
}

@Composable
internal fun FieldOrderList(
    fields: List<String>,
    onChanged: (List<String>) -> Unit,
) {
    var ordered by remember { mutableStateOf(fields) }
    LaunchedEffect(fields) {
        if (fields != ordered) ordered = fields
    }
    val density = LocalDensity.current
    val rowHeightDp = SettingsControlHeight
    val spacingDp = 8.dp
    val stepPx = with(density) { (rowHeightDp + spacingDp).toPx() }
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    val targetIndex = draggingIndex?.let { start ->
        (start + (dragOffsetPx / stepPx).roundToInt()).coerceIn(0, ordered.lastIndex)
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(spacingDp),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(MotionMedium, easing = MotionStandard)),
    ) {
        ordered.forEachIndexed { index, field ->
            key(field) {
                val isDragging = index == draggingIndex
                val displacement = when {
                    isDragging || draggingIndex == null || targetIndex == null -> 0f
                    draggingIndex!! < targetIndex!! && index > draggingIndex!! && index <= targetIndex!! -> -stepPx
                    draggingIndex!! > targetIndex!! && index < draggingIndex!! && index >= targetIndex!! -> stepPx
                    else -> 0f
                }
                val animatedDisplacement by animateFloatAsState(
                    targetValue = displacement,
                    animationSpec = if (draggingIndex == null) snap() else tween(MotionMedium, easing = MotionStandard),
                    label = "fieldOrderShift",
                )
                Box(
                    modifier = Modifier
                        .zIndex(if (isDragging) 10f else 0f)
                        .graphicsLayer { translationY = if (isDragging) dragOffsetPx else animatedDisplacement }
                        .fillMaxWidth()
                        .height(rowHeightDp)
                        .clip(SettingsControlShape)
                        .background(if (isDragging) WarmPeach else settingsControlColor()),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(WarmBrown.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = field.settingsFieldIcon(),
                                contentDescription = null,
                                tint = WarmBrown,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            field.localizedSettingsFieldLabel(),
                            color = WarmInk,
                            fontSize = 15.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(56.dp)
                                .pointerInput(field, index) {
                                    detectDragGestures(
                                        onDragStart = {
                                            draggingIndex = index
                                            dragOffsetPx = 0f
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragOffsetPx += dragAmount.y
                                        },
                                        onDragEnd = {
                                            val start = draggingIndex
                                            if (start != null) {
                                                val end = (start + (dragOffsetPx / stepPx).roundToInt()).coerceIn(0, ordered.lastIndex)
                                                if (start != end) {
                                                    val mutable = ordered.toMutableList()
                                                    val moved = mutable.removeAt(start)
                                                    mutable.add(end, moved)
                                                    ordered = mutable
                                                    onChanged(mutable)
                                                }
                                            }
                                            draggingIndex = null
                                            dragOffsetPx = 0f
                                        },
                                        onDragCancel = {
                                            draggingIndex = null
                                            dragOffsetPx = 0f
                                        },
                                    )
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.DragHandle, contentDescription = stringResource(R.string.move), tint = WarmInk.copy(alpha = 0.72f), modifier = Modifier.size(26.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun String.localizedSettingsFieldLabel(): String = when (this) {
    "time" -> stringResource(R.string.field_time)
    "status" -> stringResource(R.string.field_status)
    "location" -> stringResource(R.string.field_location)
    "reminders" -> stringResource(R.string.field_reminders)
    "notes" -> stringResource(R.string.field_notes)
    "categories" -> stringResource(R.string.field_categories)
    "tags" -> stringResource(R.string.field_categories)
    "color" -> stringResource(R.string.field_color)
    "participants" -> stringResource(R.string.field_participants)
    "recurrence" -> stringResource(R.string.field_recurrence)
    "url" -> stringResource(R.string.field_url)
    "priority" -> stringResource(R.string.field_priority)
    "progress" -> stringResource(R.string.field_progress)
    else -> replaceFirstChar { it.titlecase(LocalAppLocale.current) }
}

private fun String.settingsFieldIcon(): ImageVector = when (this) {
    "time" -> Icons.Default.AccessTime
    "status" -> Icons.Default.CheckCircle
    "location" -> Icons.Default.LocationOn
    "reminders" -> Icons.Default.Notifications
    "notes" -> Icons.Default.Notes
    "categories" -> Icons.Default.Label
    "tags" -> Icons.Default.Label
    "color" -> Icons.Default.Palette
    "participants" -> Icons.Default.PersonAdd
    "recurrence" -> Icons.Default.Repeat
    "url" -> Icons.Default.Link
    "priority" -> Icons.Default.Flag
    "progress" -> Icons.Default.Percent
    else -> Icons.Default.Edit
}

@Composable
internal fun CalendarUiState.localizedDefaultTaskScheduleLabel(): String = when {
    !defaultTaskHasDate -> stringResource(R.string.no_date)
    defaultTaskHasTime -> stringResource(R.string.date_and_time)
    else -> stringResource(R.string.date_only)
}

@Composable
internal fun CollectionReorderList(
    collections: List<CollectionEntity>,
    accounts: List<AccountEntity>,
    hiddenCollectionHrefs: Set<String> = emptySet(),
    onReordered: (List<String>) -> Unit,
    onCollectionClick: (CollectionEntity) -> Unit = {},
) {
    // Local working copy. The list order itself stays STABLE during a drag — we never
    // mutate it mid-gesture (that's what made the old version "snap" and drop the
    // gesture). Instead the dragged row floats by the raw finger delta, and the other
    // rows animate into the gap. We only commit the new order on release.
    var ordered by remember { mutableStateOf(collections) }
    val density = LocalDensity.current
    val rowHeightDp = SettingsControlHeight
    val spacingDp = 8.dp
    val stepPx = with(density) { (rowHeightDp + spacingDp).toPx() }

    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    var pendingOrderHrefs by remember { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(collections, draggingIndex) {
        val incomingHrefs = collections.map { it.href }
        val pending = pendingOrderHrefs
        when {
            pending != null && incomingHrefs == pending -> {
                pendingOrderHrefs = null
                ordered = collections
            }
            pending == null && draggingIndex == null -> {
                ordered = collections
            }
        }
    }

    val reorderable = remember(ordered) { ordered.filter { it.isVisibleInSettingsCalendarList() && it.isEnabled } }
    val inactiveCollections = remember(ordered) {
        ordered.filter { it.isVisibleInSettingsCalendarList() && !it.isEnabled }
    }

    // Live target slot derived from how far the finger has travelled in whole visible rows.
    val targetIndex = draggingIndex?.let { start ->
        (start + (dragOffsetPx / stepPx).roundToInt()).coerceIn(0, reorderable.lastIndex)
    }

    Column(verticalArrangement = Arrangement.spacedBy(spacingDp), modifier = Modifier.fillMaxWidth()) {
        val accountNames = remember(accounts) { accounts.associate { it.id to (it.displayName ?: it.username) } }
        ordered.forEach { collection ->
          key(collection.href) {
            val visibleIndex = reorderable.indexOfFirst { it.href == collection.href }
            val visible = visibleIndex >= 0
            val isDragging = visible && visibleIndex == draggingIndex
            // Non-dragged rows shift by one step to open a gap at the target slot.
            val displacement: Float = when {
                !visible || isDragging || draggingIndex == null || targetIndex == null -> 0f
                draggingIndex!! < targetIndex!! && visibleIndex > draggingIndex!! && visibleIndex <= targetIndex!! -> -stepPx
                draggingIndex!! > targetIndex!! && visibleIndex < draggingIndex!! && visibleIndex >= targetIndex!! -> stepPx
                else -> 0f
            }
            // While dragging, neighbouring rows glide to open the gap. The instant the
            // drag ends (draggingIndex == null) the underlying list has already been
            // reordered, so displacement must snap to 0 — otherwise rows animate from
            // their old gap offset and you get the "flick back then shuffle" glitch.
            val animatedDisplacement by animateFloatAsState(
                targetValue = displacement,
                animationSpec = if (draggingIndex == null) snap() else tween(MotionMedium, easing = MotionStandard),
                label = "reorderRowShift",
            )
            AnimatedVisibility(
                visible = visible,
                enter = expandVertically(animationSpec = tween(MotionMedium, easing = MotionStandard)) + fadeIn(animationSpec = tween(MotionShort, easing = MotionStandard)),
                exit = shrinkVertically(animationSpec = tween(MotionMedium, easing = MotionStandardAccelerate)) + fadeOut(animationSpec = tween(MotionShort, easing = MotionStandardAccelerate)),
            ) {
                // Indicate the dragged row with a border + brighter fill instead of a
                // shadow — the elevation shadow used to flash a grey rectangle on drop.
                Box(
                    modifier = Modifier
                        .zIndex(if (isDragging) 10f else 0f)
                        .graphicsLayer { translationY = if (isDragging) dragOffsetPx else animatedDisplacement }
                        .fillMaxWidth()
                        .height(rowHeightDp)
                        .clip(SettingsControlShape)
                        .background(if (isDragging) WarmPeach else settingsControlColor())
                        .then(
                            if (collection.href in hiddenCollectionHrefs) {
                                Modifier.dashedBorder(SyncPendingOrange, 25.dp)
                            } else {
                                Modifier
                            },
                        )
                        .clickable(onClick = { onCollectionClick(collection) }),
                ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(collection.color)),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(end = 4.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            collection.displayName,
                            color = WarmInk,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            lineHeight = 17.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val tags = buildString {
                            append(accountNames[collection.accountId] ?: stringResource(R.string.source))
                            append(" • ")
                            append(
                                listOfNotNull(
                                    stringResource(R.string.events).takeIf { collection.supportsEvents },
                                    stringResource(R.string.tasks).takeIf { collection.supportsTasks },
                                ).joinToString(" + "),
                            )
                        }
                        Text(tags, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 14.sp, maxLines = 1)
                    }
                    // Generous handle hit area: full row height, ~56dp wide. Keying the
                    // pointerInput on `index` ensures the gesture closure always sees this
                    // row's CURRENT index (after a previous reorder it would otherwise be
                    // stale and grab the wrong calendar).
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(56.dp)
                            .pointerInput(collection.href, visibleIndex) {
                                detectDragGestures(
                                    onDragStart = {
                                        draggingIndex = visibleIndex.takeIf { it >= 0 }
                                        dragOffsetPx = 0f
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffsetPx += dragAmount.y
                                    },
                                    onDragEnd = {
                                        val start = draggingIndex
                                        if (start != null) {
                                            val shift = (dragOffsetPx / stepPx).roundToInt()
                                            val end = (start + shift).coerceIn(0, reorderable.lastIndex)
                                            if (start != end) {
                                                val visibleMutable = reorderable.toMutableList()
                                                val moved = visibleMutable.removeAt(start)
                                                visibleMutable.add(end, moved)
                                                val nextVisible = visibleMutable.toMutableList()
                                                val reorderedAll = ordered.map { item ->
                                                    if (item.isVisibleInSettingsCalendarList() && item.isEnabled) nextVisible.removeAt(0) else item
                                                }
                                                val nextOrder = reorderedAll.map { it.href }
                                                ordered = reorderedAll
                                                pendingOrderHrefs = nextOrder
                                                onReordered(nextOrder)
                                            }
                                        }
                                        draggingIndex = null
                                        dragOffsetPx = 0f
                                    },
                                    onDragCancel = {
                                        draggingIndex = null
                                        dragOffsetPx = 0f
                                    },
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.DragHandle,
                            contentDescription = stringResource(R.string.move),
                            tint = WarmInk.copy(alpha = 0.7f),
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
                }
            }
          }
        }
        if (inactiveCollections.isNotEmpty()) {
            Text(
                stringResource(R.string.inactive_calendars),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp, start = 2.dp),
            )
            inactiveCollections.forEach { collection ->
                key("inactive-${collection.href}") {
                    InactiveCollectionOrderRow(
                        collection = collection,
                        accountName = accountNames[collection.accountId] ?: stringResource(R.string.source),
                        hidden = collection.href in hiddenCollectionHrefs,
                        onClick = { onCollectionClick(collection) },
                    )
                }
            }
        }
        if (ordered.none { it.isVisibleInSettingsCalendarList() }) {
            Text(
                stringResource(R.string.no_calendars_synced),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                lineHeight = 16.sp,
            )
        }
    }
}

@Composable
private fun InactiveCollectionOrderRow(
    collection: CollectionEntity,
    accountName: String,
    hidden: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(SettingsControlHeight)
            .clip(SettingsControlShape)
            .background(settingsControlColor().copy(alpha = 0.72f))
            .then(if (hidden) Modifier.dashedBorder(SyncPendingOrange, 25.dp) else Modifier)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(collection.color).copy(alpha = 0.62f)),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    collection.displayName,
                    color = WarmInk.copy(alpha = 0.72f),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    lineHeight = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "$accountName - ${stringResource(R.string.calendar_inactive)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
