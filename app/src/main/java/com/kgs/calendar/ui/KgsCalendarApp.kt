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
import com.kgs.calendar.domain.model.startOfWeek
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
import com.kgs.calendar.widget.KgsWidgetKind
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

internal val LocalExitingResourceHrefs = compositionLocalOf<Set<String>> { emptySet() }
internal val LocalTaskHierarchyExitProgress = compositionLocalOf { 0f }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KgsCalendarApp(viewModel: CalendarViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val calendarTime = rememberCalendarTimeState()
    val systemDark = isSystemInDarkTheme()
    val useDarkTheme = when (state.colorMode) {
        AppColorMode.Auto -> systemDark
        AppColorMode.Light -> false
        AppColorMode.Dark -> true
    }
    val baseContext = LocalContext.current
    val configuration = LocalConfiguration.current
    val appLocale = remember(state.languageMode, configuration) {
        state.languageMode.resolveLocale(baseContext)
    }
    val localizedContext = remember(baseContext, appLocale) {
        baseContext.withAppLocale(appLocale)
    }
    val localizedConfiguration = remember(configuration, appLocale) {
        Configuration(configuration).apply { setLocale(appLocale) }
    }
    DisposableEffect(appLocale) {
        val previousLocale = Locale.getDefault()
        Locale.setDefault(appLocale)
        onDispose { Locale.setDefault(previousLocale) }
    }

    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedConfiguration,
        LocalAppLocale provides appLocale,
        LocalCalendarTimeSnapshot provides calendarTime,
    ) {
    KgsCalendarTheme(
        themeMode = state.themeMode,
        darkTheme = useDarkTheme,
        priorityAnimationsEnabled = state.priorityAnimationsEnabled,
    ) {
        val darkPalette = LocalCalendarUiTokens.current.darkPalette
        val defaultWireframeColor = WarmBrown.toArgb()
        val context = LocalContext.current
        val view = LocalView.current
        SideEffect {
            view.context.findActivity()?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkPalette
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkPalette
                window.navigationBarColor = Color.Transparent.toArgb()
            }
        }
        var createMenuOpen by remember { mutableStateOf(false) }
        var creationSheet by remember { mutableStateOf<CreationSheet?>(null) }
        var detailSheet by remember { mutableStateOf<DetailSheet?>(null) }
        val detailTaskBackStack = remember { mutableStateListOf<TaskEntity>() }
        var detailTaskMorphGeneration by remember { mutableStateOf(0) }
        var detailTaskMorphSourceHref by remember { mutableStateOf<String?>(null) }
        var searchOpen by remember { mutableStateOf(false) }
        var drawerOpen by remember { mutableStateOf(false) }
        var taskDrawerOpen by remember { mutableStateOf(false) }
        var completedTasksOpen by remember { mutableStateOf(false) }
        var settingsOpen by remember { mutableStateOf(false) }
        var settingsStartDestination by remember { mutableStateOf(SettingsDestination.Main) }
        var handledWidgetCreateEventSerial by rememberSaveable { mutableStateOf(0) }
        var handledWidgetCreateTaskSerial by rememberSaveable { mutableStateOf(0) }
        var handledWidgetOpenEventSerial by rememberSaveable { mutableStateOf(0) }
        var handledWidgetOpenTaskSerial by rememberSaveable { mutableStateOf(0) }
        var handledCalendarLaunchSerial by rememberSaveable { mutableStateOf(0) }
        var problemsOpen by remember { mutableStateOf(false) }
        var editingCollection by remember { mutableStateOf<CollectionEntity?>(null) }
        var editorSchedule by remember {
            mutableStateOf(
                EditorScheduleState.fromPreview(
                    EditorSchedulePreview(
                        date = calendarTime.today,
                        start = LocalTime.of(15, 0),
                        end = LocalTime.of(16, 0),
                    ),
                ),
            )
        }
        var draftWireframeColor by remember { mutableStateOf(defaultWireframeColor) }
        var editorWireframeMode by remember { mutableStateOf(false) }
        var editorTransferDraft by remember { mutableStateOf<EditorTransferDraft?>(null) }
        var creationCollapseRequest by remember { mutableStateOf(0) }
        var creationExpandRequest by remember { mutableStateOf(0) }
        var conversionSource by remember { mutableStateOf<ConversionSource?>(null) }
        var recurringSaveRequest by remember { mutableStateOf<RecurringSaveRequest?>(null) }
        var hiddenSaveNotice by remember { mutableStateOf<HiddenSaveNotice?>(null) }
        val deleteFadeResourceHrefs = remember { mutableStateMapOf<String, Unit>() }
        val pendingDeleteHrefs = remember(state.pendingMutationItems) {
            state.pendingMutationItems
                .filter { it.action == MutationAction.Delete }
                .map { it.resourceHref }
                .toSet()
        }
        LaunchedEffect(pendingDeleteHrefs) {
            pendingDeleteHrefs.forEach { href ->
                deleteFadeResourceHrefs[href] = Unit
                launch {
                    delay(30 * 60 * 1000L)
                    deleteFadeResourceHrefs.remove(href)
                }
            }
        }
        val retainedDeleteHrefs = deleteFadeResourceHrefs.keys.toSet() + pendingDeleteHrefs
        val smoothEvents = rememberSmoothRemoval(state.events, EventEntity::smoothRemovalKey, EventEntity::resourceHref) { it.resourceHref in retainedDeleteHrefs }
        val smoothSearchEvents = rememberSmoothRemoval(state.searchResults, EventEntity::smoothRemovalKey, EventEntity::resourceHref) { it.resourceHref in retainedDeleteHrefs }
        val smoothDatedTasks = rememberSmoothRemoval(state.datedTasks, TaskEntity::smoothRemovalKey, TaskEntity::resourceHref) { it.resourceHref in retainedDeleteHrefs }
        val smoothInboxTasks = rememberSmoothRemoval(state.inboxTasks, TaskEntity::smoothRemovalKey, TaskEntity::resourceHref) { it.resourceHref in retainedDeleteHrefs }
        val smoothScheduledOpenTasks = rememberSmoothRemoval(state.scheduledOpenTasks, TaskEntity::smoothRemovalKey, TaskEntity::resourceHref) { it.resourceHref in retainedDeleteHrefs }
        val smoothCompletedTasks = rememberSmoothRemoval(state.completedTasks, TaskEntity::smoothRemovalKey, TaskEntity::resourceHref) { it.resourceHref in retainedDeleteHrefs }
        val smoothSearchTasks = rememberSmoothRemoval(state.searchTaskResults, TaskEntity::smoothRemovalKey, TaskEntity::resourceHref) { it.resourceHref in retainedDeleteHrefs }
        val renderState = state.copy(
            events = smoothEvents.items,
            searchResults = smoothSearchEvents.items,
            searchTaskResults = smoothSearchTasks.items,
            datedTasks = smoothDatedTasks.items,
            inboxTasks = smoothInboxTasks.items,
            scheduledOpenTasks = smoothScheduledOpenTasks.items,
            completedTasks = smoothCompletedTasks.items,
        )
        val exitingResourceHrefs = smoothEvents.exitingResourceHrefs +
            smoothSearchEvents.exitingResourceHrefs +
            smoothDatedTasks.exitingResourceHrefs +
            smoothInboxTasks.exitingResourceHrefs +
            smoothScheduledOpenTasks.exitingResourceHrefs +
            smoothCompletedTasks.exitingResourceHrefs +
            smoothSearchTasks.exitingResourceHrefs
        val problemItems = state.problemItems()
        fun showHiddenSaveNotice(collectionHref: String?, kind: HiddenSaveKind) {
            val resolvedHref = collectionHref ?: when (kind) {
                HiddenSaveKind.Event -> state.defaultEventCollectionHref
                HiddenSaveKind.Task -> state.defaultTaskCollectionHref
            }
            if (resolvedHref != null && resolvedHref in state.hiddenCollectionHrefs) {
                hiddenSaveNotice = HiddenSaveNotice(resolvedHref, kind)
            }
        }
        fun selectViewFromNavigation(viewMode: CalendarViewMode) {
            if (viewMode == CalendarViewMode.ThreeDay && state.weekViewEnabled) {
                viewModel.selectDate(state.selectedDate.startOfWeek(state.firstDayOfWeek))
            }
            viewModel.selectView(viewMode)
        }
        val backgroundBlur by animateDpAsState(
            targetValue = if (createMenuOpen) 8.dp else 0.dp,
            animationSpec = tween(180, easing = MotionStandard),
            label = "createMenuBackgroundBlur",
        )

        // Android back navigation: close the topmost open overlay, otherwise step back
        // through the view history (e.g. Day -> Month -> Multiple days), and only let the system
        // close the app when there is nothing left to go back to.
        val viewHistory = remember { mutableStateListOf<CalendarViewMode>() }
        var lastSelectedView by remember { mutableStateOf(state.selectedView) }
        var poppingViewBack by remember { mutableStateOf(false) }
        LaunchedEffect(state.selectedView) {
            if (state.selectedView != lastSelectedView) {
                if (!poppingViewBack) viewHistory.add(lastSelectedView)
                poppingViewBack = false
                lastSelectedView = state.selectedView
            }
        }
        val anyOverlayOpen = createMenuOpen || searchOpen || drawerOpen || taskDrawerOpen ||
            completedTasksOpen || settingsOpen || problemsOpen || editingCollection != null ||
            detailSheet != null || creationSheet != null
        BackHandler(enabled = anyOverlayOpen || viewHistory.isNotEmpty()) {
            when {
                createMenuOpen -> createMenuOpen = false
                detailSheet is DetailSheet.Task && detailTaskBackStack.isNotEmpty() -> {
                    detailTaskMorphSourceHref = (detailSheet as DetailSheet.Task).task.resourceHref
                    detailTaskMorphGeneration++
                    detailSheet = DetailSheet.Task(detailTaskBackStack.removeAt(detailTaskBackStack.lastIndex))
                }
                detailSheet != null -> {
                    detailSheet = null
                    detailTaskBackStack.clear()
                }
                creationSheet != null -> creationSheet = null
                editingCollection != null -> editingCollection = null
                searchOpen -> {
                    searchOpen = false
                    viewModel.setSearchQuery("")
                }
                taskDrawerOpen -> taskDrawerOpen = false
                drawerOpen -> drawerOpen = false
                completedTasksOpen -> completedTasksOpen = false
                problemsOpen -> problemsOpen = false
                settingsOpen -> settingsOpen = false
                viewHistory.isNotEmpty() -> {
                    poppingViewBack = true
                    viewModel.selectView(viewHistory.removeAt(viewHistory.lastIndex))
                }
            }
        }

        LaunchedEffect(state.externalLoginUrl) {
            val url = state.externalLoginUrl ?: return@LaunchedEffect
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            viewModel.externalLoginUrlConsumed()
        }
        LaunchedEffect(creationSheet) {
            if (creationSheet == null) {
                editorWireframeMode = false
                conversionSource = null
            }
        }
        LaunchedEffect(detailSheet) {
            if (detailSheet == null) detailTaskMorphGeneration = 0
        }

        fun scheduleState(
            date: LocalDate,
            start: LocalTime,
            end: LocalTime,
            hasStartDate: Boolean = true,
            hasEndDate: Boolean = true,
            hasStartTime: Boolean = true,
            hasEndTime: Boolean = true,
            allDay: Boolean = false,
            endDate: LocalDate = date,
        ): EditorScheduleState = EditorScheduleState(
            startDateText = date.toString(),
            endDateText = endDate.toString(),
            startTimeText = start.toString().take(5),
            endTimeText = end.toString().take(5),
            hasStartDate = hasStartDate,
            hasEndDate = hasEndDate,
            hasStartTime = hasStartTime && !allDay,
            hasEndTime = hasEndTime && !allDay,
            allDay = allDay,
            lastValidPreview = null,
        ).recalculatePreview()

        fun scheduleForEvent(event: EventEntity): EditorScheduleState = scheduleState(
            date = event.startsAtMillis.toDate(),
            endDate = if (event.allDay) (event.endsAtMillis - 1).toDate() else event.endsAtMillis.toDate(),
            start = if (event.allDay) LocalTime.MIDNIGHT else event.startsAtMillis.toTime(),
            end = if (event.allDay) LocalTime.of(23, 59) else event.endsAtMillis.toTime(),
            hasStartTime = !event.allDay,
            hasEndTime = !event.allDay,
            allDay = event.allDay,
        )

        fun scheduleForTask(task: TaskEntity): EditorScheduleState {
            val startDate = task.startAtMillis?.toDate()
            val endDate = task.dueAtMillis?.toDate()
            val fallbackDate = startDate ?: endDate ?: calendarTime.today
            val start = task.startAtMillis?.toTime() ?: task.dueAtMillis?.toTime()?.minusMinutes(30) ?: LocalTime.of(15, 0)
            val end = task.dueAtMillis?.toTime() ?: start.defaultDraftEnd()
            val hasStartTime = task.startAtMillis != null && task.startHasTime
            val hasEndTime = task.dueAtMillis != null && task.dueHasTime
            return scheduleState(
                date = startDate ?: fallbackDate,
                endDate = endDate ?: fallbackDate,
                start = start,
                end = end,
                hasStartDate = startDate != null,
                hasEndDate = endDate != null,
                hasStartTime = hasStartTime,
                hasEndTime = hasEndTime,
                allDay = (startDate != null || endDate != null) && !hasStartTime && !hasEndTime,
            )
        }

        fun applyTransferScheduleToDraft(transfer: EditorTransferDraft) {
            editorSchedule = transfer.schedule ?: scheduleState(
                date = transfer.date ?: editorSchedule.lastValidPreview?.date ?: calendarTime.today,
                endDate = transfer.endDate ?: transfer.date ?: editorSchedule.lastValidPreview?.date ?: calendarTime.today,
                start = transfer.startTime ?: editorSchedule.lastValidPreview?.start ?: LocalTime.of(15, 0),
                end = transfer.endTime ?: editorSchedule.lastValidPreview?.end ?: LocalTime.of(16, 0),
                hasStartDate = transfer.date != null,
                hasEndDate = transfer.endDate != null,
                hasStartTime = transfer.startTime != null,
                hasEndTime = transfer.endTime != null,
                allDay = transfer.allDay == true,
            )
        }

        fun openEventCreation(date: LocalDate) {
            val start = LocalTime.now().nextDraftStart()
            editorSchedule = scheduleState(
                date = date,
                start = start,
                end = start.defaultDraftEnd(state.defaultEventDurationMinutes),
            )
            draftWireframeColor = state.collections
                .filter { it.supportsEvents && it.isEnabled && !it.isReadOnlyForUi() }
                .sortedWithDefaultFirst(state.defaultEventCollectionHref)
                .firstOrNull()
                ?.color
                ?: defaultWireframeColor
            editorTransferDraft = null
            conversionSource = null
            createMenuOpen = false
            searchOpen = false
            drawerOpen = false
            taskDrawerOpen = false
            settingsOpen = false
            problemsOpen = false
            editingCollection = null
            detailSheet = null
            detailTaskBackStack.clear()
            creationSheet = CreationSheet.EventFull
        }

        fun openTaskCreation(date: LocalDate, scheduledForDay: Boolean, useTaskDefaults: Boolean = false) {
            val start = LocalTime.now().nextDraftStart()
            val hasDate = scheduledForDay || (useTaskDefaults && state.defaultTaskHasDate)
            val allDay = scheduledForDay || (useTaskDefaults && state.defaultTaskHasDate && !state.defaultTaskHasTime)
            val usesTime = !scheduledForDay && useTaskDefaults && state.defaultTaskHasTime
            editorSchedule = scheduleState(
                date = date,
                start = start,
                end = start.defaultDraftEnd(state.defaultEventDurationMinutes),
                hasStartDate = hasDate,
                hasEndDate = usesTime,
                hasStartTime = usesTime,
                hasEndTime = usesTime,
                allDay = allDay,
            )
            draftWireframeColor = state.collections
                .filter { it.supportsTasks && it.isEnabled && !it.isReadOnlyForUi() }
                .sortedWithDefaultFirst(state.defaultTaskCollectionHref)
                .firstOrNull()
                ?.color
                ?: defaultWireframeColor
            editorTransferDraft = null
            conversionSource = null
            createMenuOpen = false
            searchOpen = false
            drawerOpen = false
            taskDrawerOpen = false
            settingsOpen = false
            problemsOpen = false
            editingCollection = null
            detailSheet = null
            detailTaskBackStack.clear()
            creationSheet = CreationSheet.Task
        }

        fun openAddCalendarSources() {
            creationSheet = null
            detailSheet = null
            detailTaskBackStack.clear()
            editingCollection = null
            drawerOpen = false
            taskDrawerOpen = false
            searchOpen = false
            settingsStartDestination = SettingsDestination.AddSource
            settingsOpen = true
        }

        LaunchedEffect(settingsOpen) {
            if (settingsOpen) viewModel.refreshAndroidProviderDiagnostics()
        }
        LaunchedEffect(state.widgetCreateEventSerial) {
            if (state.widgetCreateEventSerial > handledWidgetCreateEventSerial) {
                handledWidgetCreateEventSerial = state.widgetCreateEventSerial
                openEventCreation(state.widgetCreateEventDate ?: state.selectedDate)
            }
        }
        LaunchedEffect(state.widgetCreateTaskSerial) {
            if (state.widgetCreateTaskSerial > handledWidgetCreateTaskSerial) {
                handledWidgetCreateTaskSerial = state.widgetCreateTaskSerial
                openTaskCreation(
                    date = state.widgetCreateTaskDate ?: state.selectedDate,
                    scheduledForDay = state.widgetCreateTaskScheduled,
                )
            }
        }
        LaunchedEffect(state.widgetOpenEventSerial, state.events) {
            if (state.widgetOpenEventSerial > handledWidgetOpenEventSerial) {
                val event = state.widgetOpenEventUid?.let { uid ->
                    state.events.firstOrNull { it.resourceHref == uid || it.uid == uid }
                }
                if (event != null) {
                    handledWidgetOpenEventSerial = state.widgetOpenEventSerial
                    createMenuOpen = false
                    searchOpen = false
                    drawerOpen = false
                    taskDrawerOpen = false
                    settingsOpen = false
                    problemsOpen = false
                    editingCollection = null
                    creationSheet = null
                    detailTaskBackStack.clear()
                    detailTaskMorphGeneration = 0
                    detailTaskMorphSourceHref = null
                    detailSheet = DetailSheet.Event(event)
                }
            }
        }
        LaunchedEffect(state.widgetOpenTaskSerial, state.allTasks) {
            if (state.widgetOpenTaskSerial > handledWidgetOpenTaskSerial) {
                val task = state.widgetOpenTaskUid?.let { uid ->
                    state.allTasks.firstOrNull { it.resourceHref == uid || it.uid == uid }
                }
                if (task != null) {
                    handledWidgetOpenTaskSerial = state.widgetOpenTaskSerial
                    createMenuOpen = false
                    searchOpen = false
                    drawerOpen = false
                    taskDrawerOpen = false
                    settingsOpen = false
                    problemsOpen = false
                    editingCollection = null
                    creationSheet = null
                    detailTaskBackStack.clear()
                    detailTaskMorphGeneration = 0
                    detailTaskMorphSourceHref = null
                    detailSheet = DetailSheet.Task(task)
                }
            }
        }
        LaunchedEffect(state.calendarLaunchSerial) {
            if (state.calendarLaunchSerial > handledCalendarLaunchSerial) {
                val launchedEvent = state.calendarLaunchEvent
                val launchedTask = state.calendarLaunchTask
                val detail = when {
                    launchedEvent != null -> DetailSheet.Event(launchedEvent)
                    launchedTask != null -> DetailSheet.Task(launchedTask)
                    else -> null
                }
                if (detail != null) {
                    handledCalendarLaunchSerial = state.calendarLaunchSerial
                    createMenuOpen = false
                    searchOpen = false
                    drawerOpen = false
                    taskDrawerOpen = false
                    settingsOpen = false
                    problemsOpen = false
                    editingCollection = null
                    creationSheet = null
                    detailTaskBackStack.clear()
                    detailTaskMorphGeneration = 0
                    detailTaskMorphSourceHref = null
                    detailSheet = detail
                }
            }
        }

        CompositionLocalProvider(
            LocalPendingMutations provides state.pendingMutationItems,
            LocalExitingResourceHrefs provides exitingResourceHrefs,
        ) {
            Scaffold(
                contentWindowInsets = WindowInsets(0.dp),
            ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.background),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(backgroundBlur),
                ) {
                    CalendarShell(
                        state = renderState,
                        onMenu = {
                            createMenuOpen = false
                            searchOpen = false
                            creationSheet = null
                            detailSheet = null
                            editingCollection = null
                            completedTasksOpen = false
                            recurringSaveRequest = null
                            editorWireframeMode = false
                            drawerOpen = true
                        },
                        onDateSelected = viewModel::selectDate,
                        onViewSelected = viewModel::selectView,
                        onMultiDayCountChanged = viewModel::setMultiDayCount,
                        onToday = viewModel::today,
                        onSearch = {
                            drawerOpen = false
                            taskDrawerOpen = false
                            createMenuOpen = false
                            searchOpen = true
                        },
                        onTasks = {
                            drawerOpen = false
                            searchOpen = false
                            createMenuOpen = false
                            taskDrawerOpen = true
                        },
                        onTaskStatusChanged = viewModel::setTaskStatus,
                        onEventMoved = viewModel::moveTimedEvent,
                        onTaskMoved = viewModel::moveTimedTask,
                        onEventMovedAllDay = viewModel::moveAllDayEvent,
                        onTaskMovedAllDay = viewModel::moveAllDayTask,
                        onSlotSelected = { date, start ->
                            editorWireframeMode = true
                            if (creationSheet != null) creationCollapseRequest++
                            val duration = state.defaultEventDurationMinutes.coerceIn(DraftMinDurationMinutes, 24 * 60 - 1)
                            val minStartMinute = DayStartHour * 60
                            val maxStartMinute = ((DayEndHour + 1) * 60 - duration).coerceAtLeast(minStartMinute)
                            val centeredStartMinute = (start.minuteOfDay() - duration / 2)
                                .snapDraftMinute()
                                .coerceIn(minStartMinute, maxStartMinute)
                            editorSchedule = editorSchedule.applyTimelineChange(
                                EditorSchedulePreview(
                                    date = date,
                                    start = centeredStartMinute.toDraftLocalTime(),
                                    end = (centeredStartMinute + duration)
                                        .coerceAtMost((DayEndHour + 1) * 60 - 1)
                                        .toDraftLocalTime(),
                                ),
                            )
                            draftWireframeColor = state.collections
                                .filter { it.supportsEvents && it.isEnabled && !it.isReadOnlyForUi() }
                                .sortedWithDefaultFirst(state.defaultEventCollectionHref)
                                .firstOrNull()
                                ?.color
                                ?: defaultWireframeColor
                            editorTransferDraft = null
                            creationSheet = CreationSheet.EventLow
                        },
                        onAllDaySlotSelected = { date ->
                            editorWireframeMode = true
                            if (creationSheet != null) creationCollapseRequest++
                            editorSchedule = editorSchedule.applyTimelineChange(
                                EditorSchedulePreview(
                                    date = date,
                                    start = LocalTime.MIDNIGHT,
                                    end = LocalTime.of(23, 59),
                                    allDay = true,
                                ),
                            )
                            draftWireframeColor = state.collections
                                .filter { it.supportsEvents && it.isEnabled && !it.isReadOnlyForUi() }
                                .sortedWithDefaultFirst(state.defaultEventCollectionHref)
                                .firstOrNull()
                                ?.color
                                ?: defaultWireframeColor
                            editorTransferDraft = null
                            creationSheet = CreationSheet.EventLow
                        },
                        draftEvent = editorSchedule.lastValidPreview?.let { preview -> when (creationSheet) {
                            CreationSheet.EventLow,
                            CreationSheet.EventFull,
                            CreationSheet.TaskLow,
                            CreationSheet.Task,
                            -> DraftEventSelection(
                                preview.date,
                                preview.start,
                                preview.end,
                                draftWireframeColor,
                                preview.allDay,
                            )
                            else -> null
                        } },
                        onDraftEventChanged = { draft ->
                            editorSchedule = editorSchedule.applyTimelineChange(
                                EditorSchedulePreview(draft.date, draft.start, draft.end, draft.allDay),
                            )
                        },
                        onDraftInteraction = {
                            editorWireframeMode = true
                            creationCollapseRequest++
                        },
                        onDraftTap = {
                            creationExpandRequest++
                        },
                        timelineBottomInset = if (editorWireframeMode) EditorTinyVisibleHeight else 0.dp,
                        onDetail = { detailSheet = it },
                    )
                }
                AnimatedVisibility(
                    visible = creationSheet == null,
                    enter = fadeIn(animationSpec = tween(MotionShort, easing = MotionStandard)) +
                        scaleIn(initialScale = 0.92f, animationSpec = tween(MotionMedium, easing = MotionEmphasized)),
                    exit = fadeOut(animationSpec = tween(MotionShort, easing = MotionStandardAccelerate)) +
                        scaleOut(targetScale = 0.9f, animationSpec = tween(MotionShort, easing = MotionStandardAccelerate)),
                ) {
                    CreateFabMenu(
                        expanded = createMenuOpen,
                        onExpandedChange = { createMenuOpen = it },
                        onCreateEvent = {
                            openEventCreation(state.defaultFabCreationDate())
                        },
                        onCreateTask = {
                            openTaskCreation(state.defaultFabCreationDate(), scheduledForDay = false, useTaskDefaults = true)
                        },
                    )
                }
                if (state.isManualSyncing) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                CalendarDrawer(
                    visible = drawerOpen,
                    state = renderState,
                    onDismiss = { drawerOpen = false },
                    onViewSelected = {
                        selectViewFromNavigation(it)
                        drawerOpen = false
                    },
                    onSync = {
                        viewModel.syncNow()
                        drawerOpen = false
                    },
                    onCollectionVisibleInViews = viewModel::setCollectionVisibleInViews,
                    onCollectionSettings = { editingCollection = it },
                    onAppSettings = {
                        drawerOpen = false
                        settingsStartDestination = SettingsDestination.Main
                        settingsOpen = true
                    },
                    problems = problemItems,
                    onProblems = {
                        drawerOpen = false
                        problemsOpen = true
                    },
                )
                TaskDrawer(
                    visible = taskDrawerOpen,
                    state = renderState,
                    onDismiss = { taskDrawerOpen = false },
                    onTaskStatusChanged = viewModel::setTaskStatus,
                    onTaskClick = {
                        detailTaskMorphGeneration = 0
                        detailTaskMorphSourceHref = null
                        detailSheet = DetailSheet.Task(it)
                    },
                    onShowCompleted = { completedTasksOpen = true },
                    onCreateTask = {
                        openTaskCreation(calendarTime.today, scheduledForDay = false, useTaskDefaults = false)
                    },
                )
                CalendarSearchOverlay(
                    visible = searchOpen,
                    query = renderState.searchQuery,
                    results = renderState.searchResults,
                    taskResults = renderState.searchTaskResults,
                    allTasksForHierarchy = renderState.allTasks,
                    taskColorMode = renderState.taskColorMode,
                    subtasksExpandedByDefault = renderState.subtasksExpandedByDefault,
                    onQueryChange = viewModel::setSearchQuery,
                    onTaskStatusChanged = viewModel::setTaskStatus,
                    onEventClick = {
                        detailSheet = DetailSheet.Event(it)
                    },
                    onTaskClick = {
                        detailTaskMorphGeneration = 0
                        detailTaskMorphSourceHref = null
                        detailSheet = DetailSheet.Task(it)
                    },
                    onClose = {
                        searchOpen = false
                        viewModel.setSearchQuery("")
                    },
                )
            }
            }
        }

        if (problemsOpen) {
            ProblemsPage(
                problems = problemItems,
                onEventClick = {
                    problemsOpen = false
                    detailTaskBackStack.clear()
                    detailSheet = DetailSheet.Event(it)
                },
                onTaskClick = {
                    problemsOpen = false
                    detailTaskBackStack.clear()
                    detailTaskMorphGeneration = 0
                    detailTaskMorphSourceHref = null
                    detailSheet = DetailSheet.Task(it)
                },
                onClose = { problemsOpen = false },
            )
        }

        if (creationSheet == CreationSheet.EventLow || creationSheet == CreationSheet.TaskLow) {
            KgsModalBottomSheet(
                onDismissRequest = { creationSheet = null },
                containerColor = MaterialTheme.colorScheme.surface,
                initialSnap = SheetSnap.EditorTiny,
                dimBackground = false,
                dismissOnOutsideTap = false,
                collapseRequest = creationCollapseRequest,
                expandRequest = creationExpandRequest,
                collapseSnap = SheetSnap.EditorTiny,
                anchorMode = SheetAnchorMode.Editor,
                onSnapChanged = { editorWireframeMode = it == SheetSnap.EditorTiny },
                separationShadow = true,
            ) {
                when (creationSheet) {
                    CreationSheet.EventLow -> EventEditorSheet(
                        state = state,
                        schedule = editorSchedule,
                        onScheduleChange = { editorSchedule = it },
                        expanded = false,
                        initialEvent = null,
                        transferDraft = editorTransferDraft,
                        onDraftCollectionColorChanged = { draftWireframeColor = it },
                        requestTitleFocus = state.focusTitleOnCreate,
                        onSave = { payload ->
                            viewModel.createEvent(payload)
                            showHiddenSaveNotice(payload.collectionHref, HiddenSaveKind.Event)
                            conversionSource = null
                            creationSheet = null
                        },
                        onSwitchToTask = { transfer ->
                            val taskTransfer = transfer.withDestinationReminderDefaults(state.defaultTaskReminderMinutes)
                            editorTransferDraft = taskTransfer
                            applyTransferScheduleToDraft(taskTransfer)
                            conversionSource = null
                            creationSheet = CreationSheet.TaskLow
                        },
                        onOpenCalendarSources = { openAddCalendarSources() },
                        onClose = { creationSheet = null },
                    )
                    CreationSheet.TaskLow -> TaskEditorSheet(
                        state = state,
                        schedule = editorSchedule,
                        onScheduleChange = { editorSchedule = it },
                        requestTitleFocus = state.focusTitleOnCreate,
                        initialTask = null,
                        transferDraft = editorTransferDraft,
                        onDraftCollectionColorChanged = { draftWireframeColor = it },
                        onSave = { payload ->
                            viewModel.createTask(payload)
                            showHiddenSaveNotice(payload.collectionHref, HiddenSaveKind.Task)
                            conversionSource = null
                            creationSheet = null
                        },
                        onSwitchToEvent = { transfer ->
                            val eventTransfer = transfer.withDestinationReminderDefaults(state.defaultEventReminderMinutes)
                            editorTransferDraft = eventTransfer
                            applyTransferScheduleToDraft(eventTransfer)
                            conversionSource = null
                            creationSheet = CreationSheet.EventLow
                        },
                        onOpenCalendarSources = { openAddCalendarSources() },
                        onClose = { creationSheet = null },
                    )
                    else -> Unit
                }
            }
        } else creationSheet?.let { sheet ->
            KgsModalBottomSheet(
                onDismissRequest = { creationSheet = null },
                containerColor = MaterialTheme.colorScheme.surface,
                dimBackground = false,
                dismissOnOutsideTap = false,
                collapseRequest = 0,
                anchorMode = SheetAnchorMode.Editor,
                separationShadow = sheet == CreationSheet.EventFull || sheet == CreationSheet.Task,
                initialSnap = when (sheet) {
                    CreationSheet.EventLow,
                    CreationSheet.TaskLow,
                    -> SheetSnap.Half
                    CreationSheet.EventFull,
                    is CreationSheet.EditEvent,
                    is CreationSheet.DuplicateEvent,
                    CreationSheet.Task,
                    is CreationSheet.TaskForParent,
                    is CreationSheet.EditTask,
                    is CreationSheet.DuplicateTask,
                    -> SheetSnap.Expanded
                },
            ) {
                when (sheet) {
                    CreationSheet.EventLow,
                    CreationSheet.TaskLow,
                    -> Unit
                    CreationSheet.EventFull,
                    -> EventEditorSheet(
                        state = state,
                        schedule = editorSchedule,
                        onScheduleChange = { editorSchedule = it },
                        expanded = sheet == CreationSheet.EventFull,
                        initialEvent = null,
                        transferDraft = editorTransferDraft,
                        onDraftCollectionColorChanged = { draftWireframeColor = it },
                        requestTitleFocus = state.focusTitleOnCreate,
                        onSave = { payload ->
                            when (val source = conversionSource) {
                                is ConversionSource.Task -> viewModel.convertTaskToEvent(source.task.resourceHref, payload)
                                else -> viewModel.createEvent(payload)
                            }
                            showHiddenSaveNotice(payload.collectionHref, HiddenSaveKind.Event)
                            conversionSource = null
                            creationSheet = null
                        },
                        onSwitchToTask = { transfer ->
                            val taskTransfer = transfer.withDestinationReminderDefaults(state.defaultTaskReminderMinutes)
                            editorTransferDraft = taskTransfer
                            applyTransferScheduleToDraft(taskTransfer)
                            conversionSource = null
                            creationSheet = CreationSheet.Task
                        },
                        onOpenCalendarSources = { openAddCalendarSources() },
                        onClose = { creationSheet = null },
                    )
                    is CreationSheet.EditEvent -> EventEditorSheet(
                        state = state,
                        schedule = editorSchedule,
                        onScheduleChange = { editorSchedule = it },
                        expanded = true,
                        initialEvent = sheet.event,
                        readOnlyRemote = state.collections.firstOrNull { it.href == sheet.event.collectionHref }?.isReadOnlyForUi() == true,
                        transferDraft = null,
                        requestTitleFocus = false,
                        onSave = { payload ->
                            if (state.collections.firstOrNull { it.href == sheet.event.collectionHref }?.isReadOnlyForUi() == true) {
                                viewModel.updateEventManualColor(sheet.event.resourceHref, payload.manualColor)
                                showHiddenSaveNotice(sheet.event.collectionHref, HiddenSaveKind.Event)
                                creationSheet = null
                            } else if (!sheet.event.recurrenceRule.isNullOrBlank() || sheet.event.isRecurring) {
                                recurringSaveRequest = RecurringSaveRequest.Event(sheet.event, payload)
                            } else {
                                viewModel.updateEvent(sheet.event.resourceHref, payload)
                                showHiddenSaveNotice(payload.collectionHref, HiddenSaveKind.Event)
                                creationSheet = null
                            }
                        },
                        onSwitchToTask = { transfer ->
                            val taskTransfer = transfer.withDestinationReminderDefaults(state.defaultTaskReminderMinutes)
                            editorTransferDraft = taskTransfer
                            applyTransferScheduleToDraft(taskTransfer)
                            conversionSource = ConversionSource.Event(sheet.event)
                            creationSheet = CreationSheet.Task
                        },
                        onOpenCalendarSources = { openAddCalendarSources() },
                        onClose = { creationSheet = null },
                    )
                    is CreationSheet.DuplicateEvent -> EventEditorSheet(
                        state = state,
                        schedule = editorSchedule,
                        onScheduleChange = { editorSchedule = it },
                        expanded = true,
                        initialEvent = sheet.event,
                        transferDraft = null,
                        requestTitleFocus = state.focusTitleOnCreate,
                        headerTitle = stringResource(R.string.duplicate_event),
                        onSave = { payload ->
                            viewModel.createEvent(payload)
                            showHiddenSaveNotice(payload.collectionHref, HiddenSaveKind.Event)
                            conversionSource = null
                            creationSheet = null
                        },
                        onSwitchToTask = { transfer ->
                            val taskTransfer = transfer.withDestinationReminderDefaults(state.defaultTaskReminderMinutes)
                            editorTransferDraft = taskTransfer
                            applyTransferScheduleToDraft(taskTransfer)
                            conversionSource = null
                            creationSheet = CreationSheet.Task
                        },
                        onOpenCalendarSources = { openAddCalendarSources() },
                        onClose = { creationSheet = null },
                    )
                    CreationSheet.Task -> TaskEditorSheet(
                        state = state,
                        schedule = editorSchedule,
                        onScheduleChange = { editorSchedule = it },
                        requestTitleFocus = state.focusTitleOnCreate,
                        initialTask = null,
                        transferDraft = editorTransferDraft,
                        onDraftCollectionColorChanged = { draftWireframeColor = it },
                        onSave = { payload ->
                            when (val source = conversionSource) {
                                is ConversionSource.Event -> viewModel.convertEventToTask(source.event.resourceHref, payload)
                                else -> viewModel.createTask(payload)
                            }
                            showHiddenSaveNotice(payload.collectionHref, HiddenSaveKind.Task)
                            conversionSource = null
                            creationSheet = null
                        },
                        onSwitchToEvent = { transfer ->
                            val eventTransfer = transfer.withDestinationReminderDefaults(state.defaultEventReminderMinutes)
                            editorTransferDraft = eventTransfer
                            applyTransferScheduleToDraft(eventTransfer)
                            conversionSource = null
                            creationSheet = CreationSheet.EventFull
                        },
                        onOpenCalendarSources = { openAddCalendarSources() },
                        onClose = { creationSheet = null },
                    )
                    is CreationSheet.TaskForParent -> TaskEditorSheet(
                        state = state,
                        schedule = editorSchedule,
                        onScheduleChange = { editorSchedule = it },
                        requestTitleFocus = state.focusTitleOnCreate,
                        initialTask = null,
                        forcedParentTask = sheet.parent,
                        headerTitle = stringResource(R.string.add_subtask),
                        onSave = { payload ->
                            viewModel.createTask(payload)
                            showHiddenSaveNotice(payload.collectionHref, HiddenSaveKind.Task)
                            creationSheet = null
                        },
                        onSwitchToEvent = {},
                        onOpenCalendarSources = { openAddCalendarSources() },
                        onClose = { creationSheet = null },
                    )
                    is CreationSheet.EditTask -> TaskEditorSheet(
                        state = state,
                        schedule = editorSchedule,
                        onScheduleChange = { editorSchedule = it },
                        requestTitleFocus = false,
                        initialTask = sheet.task,
                        readOnlyRemote = state.collections.firstOrNull { it.href == sheet.task.collectionHref }?.isReadOnlyForUi() == true,
                        transferDraft = null,
                        onSave = { payload ->
                            if (state.collections.firstOrNull { it.href == sheet.task.collectionHref }?.isReadOnlyForUi() == true) {
                                viewModel.updateTaskManualColor(sheet.task.resourceHref, payload.manualColor)
                                showHiddenSaveNotice(sheet.task.collectionHref, HiddenSaveKind.Task)
                                creationSheet = null
                            } else if (!sheet.task.recurrenceRule.isNullOrBlank()) {
                                recurringSaveRequest = RecurringSaveRequest.Task(sheet.task, payload)
                            } else {
                                viewModel.updateTask(sheet.task.resourceHref, payload)
                                showHiddenSaveNotice(payload.collectionHref, HiddenSaveKind.Task)
                                creationSheet = null
                            }
                        },
                        onSwitchToEvent = { transfer ->
                            val eventTransfer = transfer.withDestinationReminderDefaults(state.defaultEventReminderMinutes)
                            editorTransferDraft = eventTransfer
                            applyTransferScheduleToDraft(eventTransfer)
                            conversionSource = ConversionSource.Task(sheet.task)
                            creationSheet = CreationSheet.EventFull
                        },
                        onOpenCalendarSources = { openAddCalendarSources() },
                        onClose = { creationSheet = null },
                    )
                    is CreationSheet.DuplicateTask -> TaskEditorSheet(
                        state = state,
                        schedule = editorSchedule,
                        onScheduleChange = { editorSchedule = it },
                        requestTitleFocus = state.focusTitleOnCreate,
                        initialTask = sheet.task,
                        transferDraft = null,
                        headerTitle = stringResource(R.string.duplicate_task),
                        onSave = { payload ->
                            viewModel.createTask(payload)
                            showHiddenSaveNotice(payload.collectionHref, HiddenSaveKind.Task)
                            conversionSource = null
                            creationSheet = null
                        },
                        onSwitchToEvent = { transfer ->
                            val eventTransfer = transfer.withDestinationReminderDefaults(state.defaultEventReminderMinutes)
                            editorTransferDraft = eventTransfer
                            applyTransferScheduleToDraft(eventTransfer)
                            conversionSource = null
                            creationSheet = CreationSheet.EventFull
                        },
                        onOpenCalendarSources = { openAddCalendarSources() },
                        onClose = { creationSheet = null },
                    )
                }
            }
        }

        val currentHiddenSaveNotice = hiddenSaveNotice
        val currentHiddenSaveCollection = currentHiddenSaveNotice?.let { notice ->
            state.collections.firstOrNull { it.href == notice.collectionHref }
        }
        if (currentHiddenSaveNotice != null && currentHiddenSaveCollection == null) {
            LaunchedEffect(currentHiddenSaveNotice) {
                hiddenSaveNotice = null
            }
        }
        if (currentHiddenSaveNotice != null && currentHiddenSaveCollection != null) {
            HiddenCalendarCreationDialog(
                collection = currentHiddenSaveCollection,
                itemLabel = stringResource(
                    when (currentHiddenSaveNotice.kind) {
                        HiddenSaveKind.Event -> R.string.event
                        HiddenSaveKind.Task -> R.string.task
                    },
                ),
                onDismiss = { hiddenSaveNotice = null },
                onUnhide = {
                    viewModel.setCollectionVisibleInViews(currentHiddenSaveCollection.href, true)
                    hiddenSaveNotice = null
                },
            )
        }

        // Rendered before the detail sheet so that tapping a task here lets the detail
        // sheet open *over* this full-screen list rather than behind it.
        if (completedTasksOpen) {
            CompletedTasksView(
                tasks = remember(renderState.taskHierarchyTasks) {
                    renderState.taskHierarchyTasks.partitionByRootActivity().inactiveRootTasks
                },
                taskColorMode = renderState.taskColorMode,
                subtasksExpandedByDefault = renderState.subtasksExpandedByDefault,
                onTaskStatusChanged = viewModel::setTaskStatus,
                onTaskClick = {
                    detailTaskMorphGeneration = 0
                    detailTaskMorphSourceHref = null
                    detailSheet = DetailSheet.Task(it)
                },
                onClose = { completedTasksOpen = false },
            )
        }

        detailSheet?.let { detail ->
            val currentDetail = when (detail) {
                is DetailSheet.Event -> {
                    val sameResource = renderState.events.filter { it.resourceHref == detail.event.resourceHref }
                    val refreshed = sameResource.firstOrNull { it.startsAtMillis == detail.event.startsAtMillis }
                        ?: sameResource.firstOrNull()
                    refreshed?.let { DetailSheet.Event(it) } ?: detail
                }
                is DetailSheet.Task -> renderState.allTasks.firstOrNull { it.resourceHref == detail.task.resourceHref }?.let { DetailSheet.Task(it) } ?: detail
            }
            KgsModalBottomSheet(
                onDismissRequest = {
                    detailSheet = null
                    detailTaskBackStack.clear()
                },
                initialSnap = currentDetail.preferredInitialSnap(),
                initialContentHeight = currentDetail.estimatedPopoverHeight(),
                onBackRequest = {
                    if (currentDetail is DetailSheet.Task && detailTaskBackStack.isNotEmpty()) {
                        detailTaskMorphSourceHref = currentDetail.task.resourceHref
                        detailTaskMorphGeneration++
                        detailSheet = DetailSheet.Task(detailTaskBackStack.removeAt(detailTaskBackStack.lastIndex))
                    } else {
                        detailSheet = null
                        detailTaskBackStack.clear()
                    }
                },
            ) {
                SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
                    CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                        AnimatedContent(
                            targetState = currentDetail,
                            contentKey = { it.transitionKey() },
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surface)
                                .graphicsLayer { clip = false },
                            transitionSpec = {
                                (EnterTransition.None togetherWith ExitTransition.None)
                                    .using(
                                        SizeTransform(clip = false) { _, _ ->
                                            tween(TaskDetailMorphDurationMs, easing = MotionEmphasized)
                                        },
                                    )
                            },
                            label = "taskDetailMorph",
                        ) { animatedDetail ->
                            val detailMorphScope = this
                            CompositionLocalProvider(LocalMorphAnimatedVisibilityScope provides detailMorphScope) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.surface),
                                ) {
                    DetailSheetContent(
                        detail = animatedDetail,
                        collections = state.collections,
                        hiddenCollectionHrefs = state.hiddenCollectionHrefs,
                        accounts = state.accounts,
                        problemResources = state.problemResources,
                        taskColorMode = state.taskColorMode,
                        eventFieldOrder = state.eventFieldOrder,
                        taskFieldOrder = state.taskFieldOrder,
                        autoLoadMapPreviews = state.autoLoadMapPreviews,
                        accountEmails = (state.accounts.map { it.username } + listOfNotNull(state.account?.username)).distinct(),
                        allTasks = renderState.allTasks,
                        taskMorphGeneration = detailTaskMorphGeneration,
                        taskMorphSourceHref = detailTaskMorphSourceHref,
                        onTaskStatusChanged = viewModel::setTaskStatus,
                        onTaskPriorityChanged = viewModel::setTaskPriority,
                        onTaskProgressChanged = viewModel::setTaskProgress,
                        onEventParticipationChanged = viewModel::setEventParticipation,
                    onEditEvent = {
                        editorSchedule = scheduleForEvent(it)
                        creationSheet = CreationSheet.EditEvent(it)
                        detailSheet = null
                    },
                    onDuplicateEvent = {
                        editorSchedule = scheduleForEvent(it)
                        creationSheet = CreationSheet.DuplicateEvent(it)
                        detailSheet = null
                    },
                    onCopyEventTo = { event, collectionHref ->
                        viewModel.copyEventTo(event.resourceHref, collectionHref)
                        detailSheet = null
                    },
                    onDeleteEvent = { uid, scope, occurrenceStartMillis ->
                        when (scope) {
                            EventDeleteScope.This -> viewModel.deleteEventOccurrence(uid, occurrenceStartMillis)
                            EventDeleteScope.ThisAndFollowing -> viewModel.deleteEventFollowing(uid, occurrenceStartMillis)
                            EventDeleteScope.All -> viewModel.deleteEvent(uid)
                        }
                        detailSheet = null
                    },
                    onEditTask = {
                        editorSchedule = scheduleForTask(it)
                        creationSheet = CreationSheet.EditTask(it)
                        detailSheet = null
                        detailTaskBackStack.clear()
                    },
                    onDuplicateTask = {
                        editorSchedule = scheduleForTask(it)
                        creationSheet = CreationSheet.DuplicateTask(it)
                        detailSheet = null
                        detailTaskBackStack.clear()
                    },
                    onCopyTaskTo = { task, collectionHref ->
                        viewModel.copyTaskTo(task.resourceHref, collectionHref)
                        detailSheet = null
                        detailTaskBackStack.clear()
                    },
                    onDeleteTask = {
                        viewModel.deleteTask(it)
                        detailSheet = null
                        detailTaskBackStack.clear()
                    },
                    onOpenSubtask = { parent, child ->
                        detailTaskBackStack.add(parent)
                        detailTaskMorphSourceHref = parent.resourceHref
                        detailTaskMorphGeneration++
                        detailSheet = DetailSheet.Task(child)
                    },
                    onOpenParentTask = { parent ->
                        val stackIndex = detailTaskBackStack.indexOfLast { it.resourceHref == parent.resourceHref }
                        if (stackIndex >= 0) {
                            while (detailTaskBackStack.size > stackIndex) {
                                detailTaskBackStack.removeAt(detailTaskBackStack.lastIndex)
                            }
                        } else {
                            detailTaskBackStack.clear()
                        }
                        detailTaskMorphSourceHref = (currentDetail as? DetailSheet.Task)?.task?.resourceHref
                        detailTaskMorphGeneration++
                        detailSheet = DetailSheet.Task(parent)
                    },
                    onAddSubtask = { parent ->
                        val start = LocalTime.now().nextDraftStart()
                        editorSchedule = scheduleState(
                            date = parent.taskDate() ?: state.selectedDate,
                            start = start,
                            end = start.defaultDraftEnd(),
                            hasStartDate = false,
                            hasEndDate = false,
                            hasStartTime = false,
                            hasEndTime = false,
                        )
                        detailTaskBackStack.clear()
                        detailSheet = null
                        creationSheet = CreationSheet.TaskForParent(parent)
                    },
                    onClose = {
                        detailSheet = null
                        detailTaskBackStack.clear()
                    },
                )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (settingsOpen) {
            SettingsPage(
                state = state,
                initialDestination = settingsStartDestination,
                onViewSelected = ::selectViewFromNavigation,
                onThemeSelected = viewModel::setThemeMode,
                onColorModeSelected = viewModel::setColorMode,
                onMonthWidgetThemeSelected = { viewModel.setWidgetThemeMode(KgsWidgetKind.Month, it) },
                onMonthWidgetColorModeSelected = { viewModel.setWidgetColorMode(KgsWidgetKind.Month, it) },
                onAgendaWidgetThemeSelected = { viewModel.setWidgetThemeMode(KgsWidgetKind.Agenda, it) },
                onAgendaWidgetColorModeSelected = { viewModel.setWidgetColorMode(KgsWidgetKind.Agenda, it) },
                onTasksWidgetThemeSelected = { viewModel.setWidgetThemeMode(KgsWidgetKind.Tasks, it) },
                onTasksWidgetColorModeSelected = { viewModel.setWidgetColorMode(KgsWidgetKind.Tasks, it) },
                onDayWidgetThemeSelected = { viewModel.setWidgetThemeMode(KgsWidgetKind.Day, it) },
                onDayWidgetColorModeSelected = { viewModel.setWidgetColorMode(KgsWidgetKind.Day, it) },
                onMultiWidgetThemeSelected = { viewModel.setWidgetThemeMode(KgsWidgetKind.Multi, it) },
                onMultiWidgetColorModeSelected = { viewModel.setWidgetColorMode(KgsWidgetKind.Multi, it) },
                onMultiWidgetMonthPercentChanged = viewModel::setMultiWidgetMonthPercent,
                onTasksWidgetDisplayModeSelected = viewModel::setTasksWidgetDisplayMode,
                onTasksWidgetIncludeOverdueChanged = viewModel::setTasksWidgetIncludeOverdue,
                onTasksWidgetCreateModeSelected = viewModel::setTasksWidgetCreateMode,
                onTasksWidgetSubtaskDefaultModeSelected = viewModel::setTasksWidgetSubtaskDefaultMode,
                onDayWidgetScaleChanged = viewModel::setDayWidgetScalePercent,
                onDayWidgetStartHourChanged = viewModel::setDayWidgetStartHour,
                onDayWidgetStartAtCurrentHourChanged = viewModel::setDayWidgetStartAtCurrentHour,
                onLanguageSelected = viewModel::setLanguageMode,
                onTaskColorModeSelected = viewModel::setTaskColorMode,
                onPriorityAnimationsChanged = viewModel::setPriorityAnimationsEnabled,
                onSubtasksExpandedByDefaultChanged = viewModel::setSubtasksExpandedByDefault,
                onAutoLoadMapPreviewsChanged = viewModel::setAutoLoadMapPreviews,
                onMaxVisibleAllDayItemsChanged = viewModel::setMaxVisibleAllDayItems,
                onMultiDaySidebarControlsChanged = viewModel::setMultiDaySidebarControlsEnabled,
                onMultiDayCountChanged = viewModel::setMultiDayCount,
                onWeekViewEnabledChanged = viewModel::setWeekViewEnabled,
                onFullWeekSwipeEnabledChanged = viewModel::setFullWeekSwipeEnabled,
                onFocusTitleOnCreateChanged = viewModel::setFocusTitleOnCreate,
                onFirstDayOfWeekSelected = viewModel::setFirstDayOfWeek,
                onShowCompletedTasksChanged = viewModel::setShowCompletedTasksInCalendar,
                onDefaultEventDurationChanged = viewModel::setDefaultEventDurationMinutes,
                onDefaultTaskHasDateChanged = viewModel::setDefaultTaskHasDate,
                onDefaultTaskHasTimeChanged = viewModel::setDefaultTaskHasTime,
                onDefaultEventRemindersChanged = viewModel::setDefaultEventReminderMinutes,
                onDefaultTaskRemindersChanged = viewModel::setDefaultTaskReminderMinutes,
                onTaskStartNotificationsChanged = viewModel::setTaskStartNotificationsEnabled,
                onTaskEndNotificationsChanged = viewModel::setTaskEndNotificationsEnabled,
                onEventStartNotificationsChanged = viewModel::setEventStartNotificationsEnabled,
                onEventEndNotificationsChanged = viewModel::setEventEndNotificationsEnabled,
                onDefaultEventCollectionSelected = viewModel::setDefaultEventCollectionHref,
                onDefaultTaskCollectionSelected = viewModel::setDefaultTaskCollectionHref,
                onEventFieldOrderChanged = viewModel::setEventFieldOrder,
                onTaskFieldOrderChanged = viewModel::setTaskFieldOrder,
                onCollectionsReordered = viewModel::applyCollectionOrder,
                onManualLogin = { serverUrl, username, password, onResult ->
                    viewModel.manualLogin(serverUrl, username, password, onResult)
                },
                onBrowserLogin = viewModel::startBrowserLogin,
                onAddReadOnlyCalendar = viewModel::addReadOnlyCalendar,
                onAddAndroidCalendars = viewModel::addAndroidDeviceCalendars,
                onDisabledAndroidProviderCalendarsVisibleChanged = viewModel::setDisabledAndroidProviderCalendarsVisible,
                onUpdateAccount = viewModel::updateAccount,
                onDeleteAccount = viewModel::deleteAccount,
                onCreateCalDavCalendar = viewModel::createCalDavCalendar,
                onSync = viewModel::syncNow,
                onCollectionSettings = { editingCollection = it },
                onLocalCalendarEnabledChanged = { enabled ->
                    state.collections.firstOrNull { it.href.isLocalCollectionHrefUi() }?.let { local ->
                        viewModel.setCollectionEnabled(local.href, enabled)
                    }
                },
                onClose = { settingsOpen = false },
            )
        }

        if (!state.welcomeCompleted) {
            WelcomeScreen(
                onStartFresh = viewModel::completeWelcome,
                onConnectCalendars = {
                    viewModel.completeWelcome()
                    openAddCalendarSources()
                },
            )
        }

        editingCollection?.let { collection ->
            KgsModalBottomSheet(
                onDismissRequest = { editingCollection = null },
                modifier = Modifier.zIndex(80f),
                initialSnap = SheetSnap.Quarter,
                initialContentHeight = collection.estimatedSettingsHeight(),
            ) {
                CollectionSettingsSheet(
                    collection = collection,
                    visibleInViews = collection.href !in state.hiddenCollectionHrefs,
                    onSave = { name, color ->
                        viewModel.updateCollectionAppearance(collection.href, name, color)
                        editingCollection = null
                    },
                    onEnabledChanged = { enabled ->
                        viewModel.setCollectionEnabled(collection.href, enabled)
                        editingCollection = editingCollection?.copy(isEnabled = enabled)
                    },
                    onVisibleInViewsChanged = { visible ->
                        viewModel.setCollectionVisibleInViews(collection.href, visible)
                    },
                    onDelete = if (collection.canDeleteFromServerForUi()) {
                        {
                            viewModel.deleteCalDavCalendar(collection.href)
                            editingCollection = null
                        }
                    } else {
                        null
                    },
                    onClose = { editingCollection = null },
                )
            }
        }

        recurringSaveRequest?.let { request ->
            RecurringSaveScopeDialog(
                itemLabel = when (request) {
                    is RecurringSaveRequest.Event -> stringResource(R.string.event)
                    is RecurringSaveRequest.Task -> stringResource(R.string.task)
                },
                onDismiss = { recurringSaveRequest = null },
                onSaveThis = {
                    when (request) {
                        is RecurringSaveRequest.Event -> viewModel.updateEventOccurrence(request.event.resourceHref, request.event.startsAtMillis, request.payload)
                        is RecurringSaveRequest.Task -> viewModel.updateTaskOccurrence(request.task.resourceHref, request.task.occurrenceStartForEdit(), request.payload)
                    }
                    when (request) {
                        is RecurringSaveRequest.Event -> showHiddenSaveNotice(request.payload.collectionHref, HiddenSaveKind.Event)
                        is RecurringSaveRequest.Task -> showHiddenSaveNotice(request.payload.collectionHref, HiddenSaveKind.Task)
                    }
                    recurringSaveRequest = null
                    creationSheet = null
                },
                onSaveFollowing = {
                    when (request) {
                        is RecurringSaveRequest.Event -> viewModel.updateEventFollowing(request.event.resourceHref, request.event.startsAtMillis, request.payload)
                        is RecurringSaveRequest.Task -> viewModel.updateTaskFollowing(request.task.resourceHref, request.task.occurrenceStartForEdit(), request.payload)
                    }
                    when (request) {
                        is RecurringSaveRequest.Event -> showHiddenSaveNotice(request.payload.collectionHref, HiddenSaveKind.Event)
                        is RecurringSaveRequest.Task -> showHiddenSaveNotice(request.payload.collectionHref, HiddenSaveKind.Task)
                    }
                    recurringSaveRequest = null
                    creationSheet = null
                },
                onSaveAll = {
                    when (request) {
                        is RecurringSaveRequest.Event -> viewModel.updateEvent(request.event.resourceHref, request.payload)
                        is RecurringSaveRequest.Task -> viewModel.updateTask(request.task.resourceHref, request.payload)
                    }
                    when (request) {
                        is RecurringSaveRequest.Event -> showHiddenSaveNotice(request.payload.collectionHref, HiddenSaveKind.Event)
                        is RecurringSaveRequest.Task -> showHiddenSaveNotice(request.payload.collectionHref, HiddenSaveKind.Task)
                    }
                    recurringSaveRequest = null
                    creationSheet = null
                },
            )
        }
        AnimatedVisibility(
            visible = !state.initialDataLoaded,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1000f),
            enter = fadeIn(animationSpec = tween(90, easing = MotionStandard)),
            exit = fadeOut(animationSpec = tween(180, easing = MotionStandardAccelerate)),
        ) {
            StartupDataOverlay()
        }
    }
}

}

@Composable
private fun StartupDataOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.size(124.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 4.dp,
            )
            Image(
                painter = painterResource(R.drawable.kgs_logo_vector),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier.size(82.dp),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@Composable
internal fun KgsSheetHandle(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(64.dp)
                .height(5.dp)
                .clip(RoundedCornerShape(2.5.dp))
                .background(WarmLine),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LowEventEditorOverlay(content: @Composable () -> Unit) {
    val sheetVisible = remember { MutableTransitionState(false).apply { targetState = true } }
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visibleState = sheetVisible,
            enter = slideInVertically(animationSpec = tween(MotionLong, easing = MotionEmphasized)) { it / 2 } +
                fadeIn(animationSpec = tween(MotionMedium, easing = MotionStandard)),
            exit = slideOutVertically(animationSpec = tween(MotionMedium, easing = MotionStandardAccelerate)) { it / 3 } +
                fadeOut(animationSpec = tween(MotionShort, easing = MotionStandardAccelerate)),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 10.dp,
            ) {
                Column {
                    KgsSheetHandle()
                    content()
                }
            }
        }
    }
}

private data class LogoParticleSpec(
    val angle: Float,
    val distance: Float,
    val radius: Float,
    val delay: Float,
    val colorSlot: Int,
)

@Composable
internal fun KgsLogoBurstButton() {
    val scope = rememberCoroutineScope()
    val burstProgress = remember { Animatable(1f) }
    var burstJob by remember { mutableStateOf<Job?>(null) }
    val particles = remember {
        val random = Random(501)
        List(34) { index ->
            LogoParticleSpec(
                angle = ((index / 34f) * 2f * PI + random.nextFloat() * 0.34f).toFloat(),
                distance = 18f + random.nextFloat() * 23f,
                radius = 1.8f + random.nextFloat() * 2.8f,
                delay = random.nextFloat() * 0.26f,
                colorSlot = index % 4,
            )
        }
    }
    val progress = burstProgress.value
    val pop = sin(progress.toDouble() * PI).toFloat().coerceAtLeast(0f)
    val logoScale = 1f + pop * 0.18f
    val logoRotation = sin(progress.toDouble() * PI * 2.2).toFloat() * 9f * (1f - progress)
    val particleColors = listOf(
        WarmBrown,
        WarmPeach,
        Color(0xFFFFD166),
        Color(0xFF7BDFF2),
    )
    val ringColor = WarmBrown

    Box(
        modifier = Modifier
            .size(58.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                burstJob?.cancel()
                burstJob = scope.launch {
                    burstProgress.stop()
                    burstProgress.snapTo(0f)
                    burstProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(920, easing = MotionEmphasized),
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.matchParentSize()) {
            if (progress < 1f) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val ringAlpha = (1f - progress) * 0.34f
                drawCircle(
                    color = ringColor.copy(alpha = ringAlpha),
                    radius = 15.dp.toPx() + 17.dp.toPx() * progress,
                    center = center,
                    style = Stroke(width = (2.4f * (1f - progress)).coerceAtLeast(0.5f).dp.toPx()),
                )
                particles.forEach { spec ->
                    val localProgress = ((progress - spec.delay) / (1f - spec.delay)).coerceIn(0f, 1f)
                    if (localProgress > 0f) {
                        val eased = 1f - (1f - localProgress) * (1f - localProgress)
                        val alpha = (1f - localProgress) * (1f - localProgress)
                        val driftX = cos(spec.angle.toDouble()).toFloat() * spec.distance * eased
                        val driftY = sin(spec.angle.toDouble()).toFloat() * spec.distance * eased - 6f * progress
                        drawCircle(
                            color = particleColors[spec.colorSlot].copy(alpha = alpha),
                            radius = spec.radius * (1f + 0.45f * (1f - localProgress)),
                            center = Offset(center.x + driftX, center.y + driftY),
                        )
                    }
                }
            }
        }
        Image(
            painter = painterResource(R.drawable.kgs_logo_vector),
            contentDescription = stringResource(R.string.app_name),
            modifier = Modifier
                .size(42.dp)
                .graphicsLayer {
                    scaleX = logoScale
                    scaleY = logoScale
                    rotationZ = logoRotation
                    shadowElevation = 0f
                },
            contentScale = ContentScale.Fit,
        )
    }
}

internal val LocalSheetHeaderDragModifier = compositionLocalOf<Modifier> { Modifier }
internal val LocalPendingMutations = compositionLocalOf<List<PendingMutationEntity>> { emptyList() }

/**
 * Plumbing for the shared-element morph between the 3-day/month views and the 1-day
 * view. The [SharedTransitionScope] comes from the top-level SharedTransitionLayout and
 * the [AnimatedVisibilityScope] from each AnimatedContent slot. Exposed via composition
 * locals so deeply-nested cards/headers can opt into the morph without threading the
 * scopes through every signature. Both are null outside the view-switch container, in
 * which case [morphBounds] is a no-op.
 */
internal val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
internal val LocalMorphAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

// Multiplier on every view-morph / timeline animation duration. 1 = production speed; raise it
// (e.g. to 3) to slow the motion down for on-device evaluation. (Search for MorphSlowFactor to
// find everything it gates.)
private const val MorphSlowFactor = 1

internal const val MorphDurationMs = 440 * MorphSlowFactor

// Fades for the month-cell <-> day container transform (the only thing morphBounds now drives;
// 3-day<->1-day is the affine overlay). The day block scales from/into the cell via ScaleToBounds;
// on top of that scale we fade it so the background, grid and cards don't pop in/out:
//
//  • month -> day: the day block fades IN over the early part of the morph as it grows out of the
//    cell (a smooth reveal rather than a hard appearance).
//  • day -> month: it fades OUT over the late part as it shrinks back into the cell.
//  • the cell's own pills are dropped/added instantly (snap) so they never scale up into a giant
//    "ballooning" copy behind the real morph.
// The day whose event/task cards should register per-item shared elements (so they morph out of
// the tapped month cell's pills). Set only while a month<->1-day morph is applicable; null in the
// 3-day affine overlay and otherwise, so cards don't register duplicate/ stray shared elements.
internal val LocalMorphItemDay = compositionLocalOf<LocalDate?> { null }

// Option B (per-event pill<->card morph). OFF by deliberate choice. A per-item element travels its
// OWN bounds path (pill -> card) whose size delta is far smaller than the grid's (a pill fills a big
// fraction of its little month cell, but a card is a small fraction of the full day), so it MUST
// reach full size before the cell->full-day grid finishes zooming — it "races ahead" on the way up.
// No easing can remove that; it's geometric. Shrinking-toward-a-point hides it on the way down,
// which is why only the way up ever looked broken. With this OFF, events are plain content inside
// the day block and are scaled by ITS container transform, so they grow/shrink in perfect lockstep
// with the grid in BOTH directions (the up becomes an exact mirror of the good-looking down), and
// the month pills cross-fade into the full cards as the whole day zooms — the real Material/Google
// Calendar container transform. The per-item plumbing below stays behind this flag in case we ever
// want to revisit the independent morph; flip to true to bring it back (with the geometric caveat).
internal const val EnablePerItemMorph = false

private val MorphContentFadeMs = MorphDurationMs * 3 / 10
internal val MorphDayEnter: EnterTransition = fadeIn(tween(MorphContentFadeMs, easing = MorphEasing))
internal val MorphDayExit: ExitTransition =
    fadeOut(tween(MorphContentFadeMs, delayMillis = MorphDurationMs - MorphContentFadeMs, easing = MorphEasing))
// The month cell (the morph's container source/target) now cross-fades gradually instead of
// snapping, so the tapped day's lighter background doesn't vanish/appear abruptly.
internal val MorphCellEnter: EnterTransition =
    fadeIn(tween(MorphContentFadeMs, delayMillis = MorphDurationMs - MorphContentFadeMs, easing = MorphEasing))
internal val MorphCellExit: ExitTransition = fadeOut(tween(MorphContentFadeMs, easing = MorphEasing))

// Option B: a single event/task morphing between its month-cell pill and its full 1-day card.
// The CARD side stays visible across (almost) the whole morph — it fades in near the start when
// growing and fades out only at the very end when shrinking — so its scale (and its text scaling
// with it) is visible the entire time, matching the smooth scale-DOWN. The PILL side is the
// opposite (only present at the cell-sized end), so it never lingers as a ballooning copy.
private val MorphItemFadeMs = MorphDurationMs / 6
internal val MorphItemCardEnter: EnterTransition = fadeIn(tween(MorphItemFadeMs, easing = MorphEasing))
internal val MorphItemCardExit: ExitTransition =
    fadeOut(tween(MorphItemFadeMs, delayMillis = MorphDurationMs - MorphItemFadeMs, easing = MorphEasing))
internal val MorphItemPillEnter: EnterTransition =
    fadeIn(tween(MorphItemFadeMs, delayMillis = MorphDurationMs - MorphItemFadeMs, easing = MorphEasing))
internal val MorphItemPillExit: ExitTransition = fadeOut(tween(MorphItemFadeMs, easing = MorphEasing))

// Direction-aware bounds spec for the per-event morph.
//
// The core problem on the way UP (month -> day): a pill fills a big fraction of its little month
// cell, but the full event card is a small fraction of the full day grid. So on a shared easing the
// event's size-fraction shoots up to near-final almost instantly while the grid is still zooming —
// the event "races ahead and finishes early", which is exactly the disconnect. (The same geometric
// mismatch exists on the way down, but shrinking-toward-a-point hides it, which is why the reverse
// already looks right.)
//
// Fix for the growing direction: re-map time so the event's size-fraction TRACKS the grid's instead
// of running ahead. The grid's size-fraction at eased time e is ~e (it zooms from ~0 to full). The
// event's size-fraction is ratio + (1-ratio)*e_item, where ratio = pillSize/cardSize (how big the
// pill already is relative to the final card). Solving for the event to match the grid gives
// e_item = (e - ratio) / (1 - ratio), clamped at 0 — i.e. the event holds at pill size until the
// grid has zoomed up to the pill's relative size, then climbs in lockstep with it and they land
// together. ratio is taken per-item from the actual bounds, so tall cards (which diverge a lot) get
// strongly held back while short all-day chips (which barely diverge) are left almost untouched.
//
// The shrinking direction keeps the plain MorphEasing so the already-perfect reverse is unchanged.
internal val MorphItemBoundsTransform = androidx.compose.animation.BoundsTransform { initial, target ->
    if (target.height >= initial.height) {
        val ratio = (initial.height / target.height).coerceIn(0f, 0.92f)
        val gridTracking = androidx.compose.animation.core.Easing { f ->
            ((MorphEasing.transform(f) - ratio) / (1f - ratio)).coerceIn(0f, 1f)
        }
        tween(MorphDurationMs, easing = gridTracking)
    } else {
        tween(MorphDurationMs, easing = MorphEasing)
    }
}
internal val MorphDayBoundsTransform = androidx.compose.animation.BoundsTransform { _, _ ->
    tween(MorphDurationMs, easing = MorphEasing)
}

private val MorphCornerRadiusDp = 12.dp
internal val MorphRoundedClip: SharedTransitionScope.OverlayClip =
    object : SharedTransitionScope.OverlayClip {
        override fun getClipPath(
            sharedContentState: SharedTransitionScope.SharedContentState,
            bounds: androidx.compose.ui.geometry.Rect,
            layoutDirection: androidx.compose.ui.unit.LayoutDirection,
            density: androidx.compose.ui.unit.Density,
        ): androidx.compose.ui.graphics.Path {
            val r = with(density) { MorphCornerRadiusDp.toPx() }
            return androidx.compose.ui.graphics.Path().apply {
                addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        rect = bounds,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
                    ),
                )
            }
        }
    }






@Composable
private fun CreateFabMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onCreateTask: () -> Unit,
    onCreateEvent: () -> Unit,
) {
    val quietInteraction = remember { MutableInteractionSource() }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val fabSize by animateDpAsState(
        targetValue = if (expanded) 62.dp else 56.dp,
        animationSpec = tween(MotionMedium, easing = MotionEmphasized),
        label = "fabSize",
    )
    val fabCorner by animateDpAsState(
        targetValue = if (expanded) 31.dp else 18.dp,
        animationSpec = tween(MotionMedium, easing = MotionEmphasized),
        label = "fabCorner",
    )
    val fabColor by animateColorAsState(
        targetValue = if (expanded) WarmBrown else accentContainerColor(),
        animationSpec = tween(MotionMedium, easing = MotionStandard),
        label = "fabColor",
    )
    val fabElevation by animateDpAsState(
        targetValue = if (expanded) 16.dp else 12.dp,
        animationSpec = tween(MotionMedium, easing = MotionStandard),
        label = "fabElevation",
    )
    val overlayProgress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(210, easing = MotionEmphasized),
        label = "createMenuOverlayWave",
    )
    val overlayColor = MaterialTheme.colorScheme.surface
    Box(Modifier.fillMaxSize()) {
        if (overlayProgress > 0.01f || expanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = quietInteraction,
                        indication = null,
                        onClick = { onExpandedChange(false) },
                    ),
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val center = Offset(size.width - 56.dp.toPx(), size.height - 52.dp.toPx())
                    val maxRadius = sqrt(size.width * size.width + size.height * size.height) * 1.08f
                    drawCircle(
                        color = overlayColor.copy(alpha = 0.54f * overlayProgress),
                        radius = maxRadius * overlayProgress,
                        center = center,
                    )
                    drawCircle(
                        color = overlayColor.copy(alpha = 0.04f * overlayProgress),
                        radius = maxRadius * overlayProgress * 0.74f,
                        center = center,
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(animationSpec = tween(MotionMedium, delayMillis = 40, easing = MotionStandard)) +
                scaleIn(initialScale = 0.9f, animationSpec = tween(MotionMedium, easing = MotionEmphasized)) +
                slideInVertically(animationSpec = tween(MotionMedium, easing = MotionEmphasized)) { it / 3 },
            exit = fadeOut(animationSpec = tween(MotionShort, easing = MotionStandardAccelerate)) +
                scaleOut(targetScale = 0.92f, animationSpec = tween(MotionShort, easing = MotionStandardAccelerate)) +
                slideOutVertically(animationSpec = tween(MotionShort, easing = MotionStandardAccelerate)) { it / 4 },
            modifier = Modifier.align(Alignment.BottomEnd),
        ) {
            Column(
                modifier = Modifier
                    .padding(end = 24.dp, bottom = navBottom + 108.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.End,
            ) {
                CreateMenuButton(Icons.Default.TaskAlt, stringResource(R.string.task)) {
                    onExpandedChange(false)
                    onCreateTask()
                }
                CreateMenuButton(Icons.Default.Event, stringResource(R.string.event)) {
                    onExpandedChange(false)
                    onCreateEvent()
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 28.dp, bottom = navBottom + 28.dp)
                .size(fabSize)
                .shadow(fabElevation, RoundedCornerShape(fabCorner))
                .clip(RoundedCornerShape(fabCorner))
                .background(fabColor)
                .clickable { onExpandedChange(!expanded) },
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = expanded,
                transitionSpec = {
                    (scaleIn(initialScale = 0.75f, animationSpec = tween(MotionShort, easing = MotionStandard)) +
                        fadeIn(animationSpec = tween(MotionShort, easing = MotionStandard))) togetherWith
                        (scaleOut(targetScale = 0.75f, animationSpec = tween(MotionShort, easing = MotionStandardAccelerate)) +
                            fadeOut(animationSpec = tween(MotionShort, easing = MotionStandardAccelerate)))
                },
                label = "fabIcon",
            ) { isExpanded ->
                // Tint must contrast the FAB's own background, which is a light accent container
                // in BOTH light and dark mode — so a light WarmInk icon vanished in dark mode.
                val iconBackground = if (isExpanded) WarmBrown else accentContainerColor()
                Icon(
                    imageVector = if (isExpanded) Icons.Default.Close else Icons.Default.Add,
                    contentDescription = if (isExpanded) stringResource(R.string.close) else stringResource(R.string.create),
                    tint = if (iconBackground.isDark()) Color.White else Color(0xFF1C1A18),
                    modifier = Modifier.size(if (isExpanded) 32.dp else 34.dp),
                )
            }
        }
    }
}

@Composable
private fun CreateMenuButton(icon: ImageVector, text: String, onClick: () -> Unit) {
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(MotionMedium, easing = MotionEmphasized),
        label = "createMenuButtonScale",
    )
    val container = accentContainerColor()
    // The accent container is light in both themes, so text/icon must be a dark glyph to stay
    // legible (WarmInk/WarmBrown turn light in dark mode and disappeared into the fill).
    val onContainer = if (container.isDark()) Color.White else Color(0xFF1C1A18)
    Surface(
        onClick = onClick,
        modifier = Modifier
            .width(178.dp)
            .height(54.dp)
            .scale(scale),
        shape = RoundedCornerShape(27.dp),
        color = container,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(icon, contentDescription = null, tint = onContainer, modifier = Modifier.size(24.dp))
            Text(text, color = onContainer, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1)
        }
    }
}
