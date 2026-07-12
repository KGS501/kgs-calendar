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

private val LocalExitingResourceHrefs = compositionLocalOf<Set<String>> { emptySet() }
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
                        viewModel.selectView(it)
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
                onViewSelected = viewModel::selectView,
                onThemeSelected = viewModel::setThemeMode,
                onColorModeSelected = viewModel::setColorMode,
                onMonthWidgetThemeSelected = viewModel::setMonthWidgetThemeMode,
                onMonthWidgetColorModeSelected = viewModel::setMonthWidgetColorMode,
                onAgendaWidgetThemeSelected = viewModel::setAgendaWidgetThemeMode,
                onAgendaWidgetColorModeSelected = viewModel::setAgendaWidgetColorMode,
                onTasksWidgetThemeSelected = viewModel::setTasksWidgetThemeMode,
                onTasksWidgetColorModeSelected = viewModel::setTasksWidgetColorMode,
                onDayWidgetThemeSelected = viewModel::setDayWidgetThemeMode,
                onDayWidgetColorModeSelected = viewModel::setDayWidgetColorMode,
                onMultiWidgetThemeSelected = viewModel::setMultiWidgetThemeMode,
                onMultiWidgetColorModeSelected = viewModel::setMultiWidgetColorMode,
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

@Composable
private fun KgsSheetHandle(modifier: Modifier = Modifier) {
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

@Composable
internal fun MonthView(
    state: CalendarUiState,
    onMonthChanged: (YearMonth) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    morphDay: LocalDate,
    jumpRequest: YearMonth?,
    onJumpConsumed: () -> Unit,
    onDetail: (DetailSheet) -> Unit,
) {
    val firstDayOfWeek = state.firstDayOfWeek
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // One LazyColumn item per month. Each month is a self-contained block with a big
    // header and breathing room above it, so month boundaries are obvious.
    val months = remember { (0 until MonthViewPageCount).map { MonthViewBase.plusMonths(it.toLong()) } }
    val initialIndex = remember { YearMonth.from(state.selectedDate).toMonthViewPage() }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)

    val calendarTasks = remember(state.datedTasks) {
        state.datedTasks.filter { task ->
            isMonthSurfaceTaskVisible(task.isCompleted, task.status)
        }
    }
    val eventsByDay = remember(state.events) { state.events.indexEventsByDay() }
    val tasksByDay = remember(calendarTasks) { calendarTasks.indexTasksByDay() }

    // Recenter the loaded data window on the month at the top of the viewport.
    LaunchedEffect(listState, months) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .map { months.getOrNull(it) ?: YearMonth.from(state.selectedDate) }
            .distinctUntilChanged()
            .collect { onMonthChanged(it) }
    }
    // Consume explicit jump requests (year strip / Today). For long jumps, stage close to the
    // target first, then animate the final few months so Today feels fluid without sweeping the
    // viewport through years of intermediate state.
    LaunchedEffect(jumpRequest) {
        val target = jumpRequest?.toMonthViewPage() ?: return@LaunchedEffect
        val distance = target - listState.firstVisibleItemIndex
        if (abs(distance) > 5) {
            listState.scrollToItem(target - distance.coerceIn(-3, 3))
        }
        listState.animateScrollToItem(target)
        onJumpConsumed()
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
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
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(bottom = navBottom + 24.dp),
        ) {
            items(months, key = { it.toString() }) { month ->
                MonthBlock(
                    month = month,
                    firstDayOfWeek = firstDayOfWeek,
                    eventsByDay = eventsByDay,
                    tasksByDay = tasksByDay,
                    taskColorMode = state.taskColorMode,
                    morphDay = morphDay,
                    onOpenDay = onOpenDay,
                )
            }
        }
    }
}

internal val LocalSheetHeaderDragModifier = compositionLocalOf<Modifier> { Modifier }
private val LocalPendingMutations = compositionLocalOf<List<PendingMutationEntity>> { emptyList() }

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

@Composable
private fun SettingsPage(
    state: CalendarUiState,
    initialDestination: SettingsDestination = SettingsDestination.Main,
    onViewSelected: (CalendarViewMode) -> Unit,
    onThemeSelected: (AppThemeMode) -> Unit,
    onColorModeSelected: (AppColorMode) -> Unit,
    onMonthWidgetThemeSelected: (WidgetThemeMode) -> Unit,
    onMonthWidgetColorModeSelected: (WidgetColorMode) -> Unit,
    onAgendaWidgetThemeSelected: (WidgetThemeMode) -> Unit,
    onAgendaWidgetColorModeSelected: (WidgetColorMode) -> Unit,
    onTasksWidgetThemeSelected: (WidgetThemeMode) -> Unit,
    onTasksWidgetColorModeSelected: (WidgetColorMode) -> Unit,
    onDayWidgetThemeSelected: (WidgetThemeMode) -> Unit,
    onDayWidgetColorModeSelected: (WidgetColorMode) -> Unit,
    onMultiWidgetThemeSelected: (WidgetThemeMode) -> Unit,
    onMultiWidgetColorModeSelected: (WidgetColorMode) -> Unit,
    onMultiWidgetMonthPercentChanged: (Int) -> Unit,
    onTasksWidgetDisplayModeSelected: (WidgetTaskDisplayMode) -> Unit,
    onTasksWidgetIncludeOverdueChanged: (Boolean) -> Unit,
    onTasksWidgetCreateModeSelected: (WidgetTaskCreateMode) -> Unit,
    onTasksWidgetSubtaskDefaultModeSelected: (WidgetTaskSubtaskDefaultMode) -> Unit,
    onDayWidgetScaleChanged: (Int) -> Unit,
    onDayWidgetStartHourChanged: (Int) -> Unit,
    onDayWidgetStartAtCurrentHourChanged: (Boolean) -> Unit,
    onLanguageSelected: (AppLanguageMode) -> Unit,
    onTaskColorModeSelected: (TaskColorMode) -> Unit,
    onPriorityAnimationsChanged: (Boolean) -> Unit,
    onSubtasksExpandedByDefaultChanged: (Boolean) -> Unit,
    onAutoLoadMapPreviewsChanged: (Boolean) -> Unit,
    onMaxVisibleAllDayItemsChanged: (Int) -> Unit,
    onMultiDaySidebarControlsChanged: (Boolean) -> Unit,
    onMultiDayCountChanged: (Int) -> Unit,
    onFocusTitleOnCreateChanged: (Boolean) -> Unit,
    onFirstDayOfWeekSelected: (DayOfWeek) -> Unit,
    onShowCompletedTasksChanged: (Boolean) -> Unit,
    onDefaultEventDurationChanged: (Int) -> Unit,
    onDefaultTaskHasDateChanged: (Boolean) -> Unit,
    onDefaultTaskHasTimeChanged: (Boolean) -> Unit,
    onDefaultEventRemindersChanged: (Set<Int>) -> Unit,
    onDefaultTaskRemindersChanged: (Set<Int>) -> Unit,
    onTaskStartNotificationsChanged: (Boolean) -> Unit,
    onTaskEndNotificationsChanged: (Boolean) -> Unit,
    onEventStartNotificationsChanged: (Boolean) -> Unit,
    onEventEndNotificationsChanged: (Boolean) -> Unit,
    onDefaultEventCollectionSelected: (String?) -> Unit,
    onDefaultTaskCollectionSelected: (String?) -> Unit,
    onEventFieldOrderChanged: (List<String>) -> Unit,
    onTaskFieldOrderChanged: (List<String>) -> Unit,
    onCollectionsReordered: (List<String>) -> Unit,
    onManualLogin: (String, String, String, (Boolean, String?) -> Unit) -> Unit,
    onBrowserLogin: (String) -> Unit,
    onAddReadOnlyCalendar: (String) -> Unit,
    onAddAndroidCalendars: () -> Unit,
    onDisabledAndroidProviderCalendarsVisibleChanged: (Boolean) -> Unit,
    onUpdateAccount: (String, String, String, String, String?) -> Unit,
    onDeleteAccount: (String) -> Unit,
    onCreateCalDavCalendar: (String, String, Boolean, Boolean) -> Unit,
    onSync: () -> Unit,
    onCollectionSettings: (CollectionEntity) -> Unit,
    onLocalCalendarEnabledChanged: (Boolean) -> Unit,
    onClose: () -> Unit,
) {
    val initialNavigationStack = remember(initialDestination) {
        if (initialDestination == SettingsDestination.Main) {
            listOf(SettingsDestination.Main.name)
        } else {
            listOf(SettingsDestination.Main.name, initialDestination.name)
        }
    }
    var navigationStack by rememberSaveable(initialDestination.name) { mutableStateOf(initialNavigationStack) }
    var selectedAccountId by rememberSaveable { mutableStateOf<String?>(null) }
    var defaultViewDialogOpen by remember { mutableStateOf(false) }
    var firstDayDialogOpen by remember { mutableStateOf(false) }
    var eventDurationDialogOpen by remember { mutableStateOf(false) }
    var taskDefaultDialogOpen by remember { mutableStateOf(false) }
    var themeDialogOpen by remember { mutableStateOf(false) }
    var colorModeDialogOpen by remember { mutableStateOf(false) }
    var widgetThemeDialogTarget by remember { mutableStateOf<SettingsDestination?>(null) }
    var widgetColorModeDialogTarget by remember { mutableStateOf<SettingsDestination?>(null) }
    var tasksWidgetDisplayDialogOpen by remember { mutableStateOf(false) }
    var tasksWidgetCreateDialogOpen by remember { mutableStateOf(false) }
    var tasksWidgetSubtaskDefaultDialogOpen by remember { mutableStateOf(false) }
    var languageDialogOpen by remember { mutableStateOf(false) }
    var defaultEventCollectionDialogOpen by remember { mutableStateOf(false) }
    var defaultTaskCollectionDialogOpen by remember { mutableStateOf(false) }
    var deleteAccountCandidateId by remember { mutableStateOf<String?>(null) }
    var readOnlyUrl by rememberSaveable { mutableStateOf("") }
    var addServerUrl by rememberSaveable { mutableStateOf("") }
    var addUsername by rememberSaveable { mutableStateOf("") }
    var addPassword by rememberSaveable { mutableStateOf("") }
    var addAccountError by rememberSaveable { mutableStateOf<String?>(null) }
    var addAccountSubmitting by remember { mutableStateOf(false) }
    var editAccountName by rememberSaveable { mutableStateOf("") }
    var editAccountServerUrl by rememberSaveable { mutableStateOf("") }
    var editAccountUsername by rememberSaveable { mutableStateOf("") }
    var editAccountPassword by rememberSaveable { mutableStateOf("") }
    var newCalDavCalendarName by rememberSaveable { mutableStateOf("") }
    var newCalDavCalendarEvents by rememberSaveable { mutableStateOf(true) }
    var newCalDavCalendarTasks by rememberSaveable { mutableStateOf(false) }
    var navigatingForward by remember { mutableStateOf(true) }
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val currentDestination = remember(navigationStack) {
        runCatching { SettingsDestination.valueOf(navigationStack.last()) }.getOrDefault(SettingsDestination.Main)
    }
    fun navigateTo(destination: SettingsDestination) {
        navigatingForward = true
        navigationStack = navigationStack + destination.name
    }
    fun navigateToRoot(destination: SettingsDestination) {
        navigatingForward = true
        navigationStack = listOf(SettingsDestination.Main.name, destination.name)
    }
    fun resetAddAccountForm() {
        addServerUrl = ""
        addUsername = ""
        addPassword = ""
        addAccountError = null
        addAccountSubmitting = false
    }
    val context = LocalContext.current
    val calDavValidationFailedText = stringResource(R.string.caldav_validation_failed)
    val calendarPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val readGranted = grants[Manifest.permission.READ_CALENDAR] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        val writeGranted = grants[Manifest.permission.WRITE_CALENDAR] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
        if (readGranted && writeGranted) {
            onAddAndroidCalendars()
            navigateToRoot(SettingsDestination.Accounts)
        }
    }
    fun addAndroidCalendarsWithPermission() {
        val readGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        val writeGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
        if (readGranted && writeGranted) {
            onAddAndroidCalendars()
            navigateToRoot(SettingsDestination.Accounts)
        } else {
            calendarPermissionLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
        }
    }
    fun navigateBack() {
        navigatingForward = false
        if (navigationStack.size > 1) navigationStack = navigationStack.dropLast(1) else onClose()
    }
    BackHandler(enabled = true) {
        navigateBack()
    }
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(40f),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = statusTop + 8.dp, bottom = navBottom),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconButton(onClick = ::navigateBack, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = WarmInk,
                        modifier = Modifier.size(25.dp),
                    )
                }
                Text(
                    currentDestination.localizedTitle(),
                    color = WarmInk,
                    fontSize = 24.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            AnimatedContent(
                targetState = currentDestination,
                transitionSpec = {
                    if (navigatingForward) {
                        (slideInHorizontally(tween(MotionMedium, easing = MotionStandard)) { it / 5 } + fadeIn(tween(MotionMedium))) togetherWith
                            (slideOutHorizontally(tween(MotionShort, easing = MotionStandardAccelerate)) { -it / 5 } + fadeOut(tween(MotionShort)))
                    } else {
                        (slideInHorizontally(tween(MotionMedium, easing = MotionStandard)) { -it / 5 } + fadeIn(tween(MotionMedium))) togetherWith
                            (slideOutHorizontally(tween(MotionShort, easing = MotionStandardAccelerate)) { it / 5 } + fadeOut(tween(MotionShort)))
                    }
                },
                label = "settingsSubpage",
            ) { page ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    when (page) {
                        SettingsDestination.Main -> {
                            Column(
                                modifier = Modifier.padding(horizontal = 18.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                SettingsMenuRow(
                                    title = stringResource(R.string.calendar),
                                    value = stringResource(R.string.calendar_sources_summary, state.accounts.size.coerceAtLeast(if (state.account != null) 1 else 0), state.collections.count { it.isEnabled }),
                                    leadingIcon = Icons.Default.CalendarMonth,
                                ) {
                                    navigateTo(SettingsDestination.Accounts)
                                }
                                SettingsMenuRow(
                                    title = stringResource(R.string.behavior),
                                    value = stringResource(R.string.behavior_summary),
                                    leadingIcon = Icons.Default.Tune,
                                ) {
                                    navigateTo(SettingsDestination.Behavior)
                                }
                                SettingsMenuRow(
                                    title = stringResource(R.string.design),
                                    value = state.themeMode.localizedLabel(),
                                    leadingIcon = Icons.Default.Palette,
                                ) {
                                    navigateTo(SettingsDestination.Design)
                                }
                                SettingsMenuRow(
                                    title = stringResource(R.string.widgets),
                                    value = stringResource(R.string.widget_settings_summary),
                                    leadingIcon = Icons.Default.Widgets,
                                ) {
                                    navigateTo(SettingsDestination.Widgets)
                                }
                                SettingsMenuRow(
                                    title = stringResource(R.string.privacy),
                                    value = stringResource(R.string.privacy_summary),
                                    leadingIcon = Icons.Default.Lock,
                                ) {
                                    navigateTo(SettingsDestination.Privacy)
                                }
                                SettingsMenuRow(
                                    title = stringResource(R.string.sponsor_project),
                                    value = stringResource(R.string.sponsor_project_summary),
                                    leadingIcon = Icons.Default.Favorite,
                                ) {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SponsorProjectUrl)))
                                }
                                SettingsMenuRow(
                                    title = stringResource(R.string.report_bug),
                                    value = stringResource(R.string.report_bug_summary),
                                    leadingIcon = Icons.Default.BugReport,
                                ) {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BugReportIssuesUrl)))
                                }
                            }
                        }
                        SettingsDestination.Accounts -> {
                            val accounts = state.accounts.ifEmpty { state.account?.let(::listOf).orEmpty() }
                            val externalAccounts = accounts.filterNot { it.id == UiLocalAccountId }
                            val localCollection = state.collections.firstOrNull { it.href.isLocalCollectionHrefUi() }
                            val visibleAccounts = accounts
                            val visibleCollections = state.collections
                            SettingsSection(title = stringResource(R.string.calendar), icon = Icons.Default.CalendarMonth) {
                                visibleAccounts.forEach { account ->
                                    key(account.id) {
                                        AnimatedVisibility(
                                            visible = !(account.id == UiLocalAccountId && localCollection?.isEnabled == false),
                                            enter = expandVertically(animationSpec = tween(MotionMedium, easing = MotionStandard)) + fadeIn(animationSpec = tween(MotionShort, easing = MotionStandard)),
                                            exit = shrinkVertically(animationSpec = tween(MotionMedium, easing = MotionStandardAccelerate)) + fadeOut(animationSpec = tween(MotionShort, easing = MotionStandardAccelerate)),
                                        ) {
                                            SettingsMenuRow(
                                                title = account.displayName ?: account.username,
                                                value = when {
                                                    account.id == UiLocalAccountId -> stringResource(R.string.local_source)
                                                    account.isAndroidProviderForUi() -> stringResource(R.string.android_device_calendars)
                                                    account.sourceType == SourceType.ReadOnlyUrl || account.username == "Read-only URL" -> stringResource(R.string.read_only_url)
                                                    else -> account.serverUrl
                                                },
                                                warningBadge = account.isAndroidProviderForUi() && state.hiddenAndroidProviderCalendarNames.isNotEmpty(),
                                            ) {
                                                selectedAccountId = account.id
                                                editAccountName = account.displayName ?: account.username
                                                editAccountServerUrl = account.serverUrl
                                                editAccountUsername = account.username
                                                editAccountPassword = ""
                                                navigateTo(SettingsDestination.AccountDetail)
                                            }
                                        }
                                    }
                                }
                                SettingsMenuRow(
                                    title = stringResource(R.string.add_calendar),
                                    value = stringResource(R.string.add_calendar_summary),
                                    leadingIcon = Icons.Default.Add,
                                ) {
                                    navigateTo(SettingsDestination.AddSource)
                                }
                                if (externalAccounts.isNotEmpty() && localCollection != null) {
                                    SettingsSwitchRow(
                                        title = stringResource(R.string.show_local_calendar),
                                        checked = localCollection.isEnabled,
                                        onCheckedChange = onLocalCalendarEnabledChanged,
                                        subtitle = stringResource(R.string.show_local_calendar_help),
                                    )
                                }
                                Button(
                                    onClick = onSync,
                                    enabled = externalAccounts.isNotEmpty() && !state.isBusy,
                                    shape = SettingsControlShape,
                                    colors = ButtonDefaults.buttonColors(containerColor = WarmBrown),
                                    modifier = Modifier.fillMaxWidth().height(SettingsControlHeight),
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.sync_now))
                                }
                            }
                            SettingsSection(title = stringResource(R.string.calendar_order), icon = Icons.Default.DragHandle) {
                                CollectionReorderList(
                                    collections = visibleCollections,
                                    accounts = accounts,
                                    hiddenCollectionHrefs = state.hiddenCollectionHrefs,
                                    onReordered = onCollectionsReordered,
                                    onCollectionClick = onCollectionSettings,
                                )
                            }
                        }
                        SettingsDestination.AddSource -> {
                            SettingsSection(title = stringResource(R.string.add_calendar), icon = Icons.Default.Add) {
                                SourceOptionCard(
                                    title = stringResource(R.string.android_device_calendars),
                                    value = stringResource(R.string.android_device_calendars_summary),
                                    leadingIcon = Icons.Default.CalendarMonth,
                                    helpText = stringResource(R.string.android_device_calendars_help),
                                    examples = listOf(
                                        SourceExampleBrand("Google", Color(0xFF4285F4), iconRes = R.drawable.brand_google_calendar),
                                        SourceExampleBrand("Microsoft", Color(0xFF00A4EF), iconRes = R.drawable.brand_microsoft),
                                        SourceExampleBrand("Samsung", Color(0xFF1428A0), iconRes = R.drawable.brand_samsung),
                                    ),
                                ) {
                                    addAndroidCalendarsWithPermission()
                                }
                                SourceOptionCard(
                                    title = stringResource(R.string.caldav_and_nextcloud),
                                    value = stringResource(R.string.caldav_account_summary),
                                    leadingIcon = Icons.Default.PersonAdd,
                                    examples = listOf(
                                        SourceExampleBrand("Nextcloud", Color(0xFF0082C9), iconRes = R.drawable.brand_nextcloud),
                                        SourceExampleBrand("mailbox.org", Color(0xFF76B900), iconRes = R.drawable.brand_mailbox),
                                        SourceExampleBrand("DAVx5", Color(0xFF7CB342), iconRes = R.drawable.brand_davx5),
                                        SourceExampleBrand("iCloud", Color(0xFF6E6E73), iconRes = R.drawable.brand_icloud),
                                    ),
                                ) {
                                    resetAddAccountForm()
                                    navigateTo(SettingsDestination.AddAccount)
                                }
                                SourceOptionCard(
                                    title = stringResource(R.string.subscribe_url),
                                    value = stringResource(R.string.subscribe_url_summary),
                                    leadingIcon = Icons.Default.Public,
                                    examples = listOf(
                                        SourceExampleBrand("ICS", Color(0xFF7C3AED), imageVector = Icons.Default.Event),
                                        SourceExampleBrand("webcal", Color(0xFF0891B2), imageVector = Icons.Default.Link),
                                        SourceExampleBrand("public URL", Color(0xFF64748B), imageVector = Icons.Default.Public),
                                    ),
                                ) {
                                    readOnlyUrl = ""
                                    navigateTo(SettingsDestination.AddReadOnly)
                                }
                            }
                        }
                        SettingsDestination.AddAccount -> {
                            SettingsSection(title = stringResource(R.string.caldav_account), icon = Icons.Default.PersonAdd) {
                                OutlinedTextField(
                                    value = addServerUrl,
                                    onValueChange = {
                                        addServerUrl = it
                                        addAccountError = null
                                    },
                                    label = { Text(stringResource(R.string.caldav_server_url)) },
                                    singleLine = true,
                                    keyboardOptions = UrlKeyboardOptions,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                OutlinedTextField(
                                    value = addUsername,
                                    onValueChange = {
                                        addUsername = it
                                        addAccountError = null
                                    },
                                    label = { Text(stringResource(R.string.username)) },
                                    singleLine = true,
                                    keyboardOptions = UsernameKeyboardOptions,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                SecurePasswordField(
                                    value = addPassword,
                                    onValueChange = {
                                        addPassword = it
                                        addAccountError = null
                                    },
                                    label = stringResource(R.string.password_or_app_password),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                AnimatedVisibility(visible = addAccountError != null) {
                                    Text(
                                        addAccountError.orEmpty(),
                                        color = MaterialTheme.colorScheme.error,
                                        fontSize = 13.sp,
                                        lineHeight = 17.sp,
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    Button(
                                        onClick = {
                                            onBrowserLogin(addServerUrl)
                                            navigateToRoot(SettingsDestination.Accounts)
                                        },
                                        enabled = addServerUrl.isNotBlank() && !state.isBusy,
                                        shape = SettingsControlShape,
                                        colors = ButtonDefaults.buttonColors(containerColor = settingsControlColor(), contentColor = WarmInk),
                                        modifier = Modifier.weight(1f).height(SettingsControlHeight),
                                    ) {
                                        Text(stringResource(R.string.browser))
                                    }
                                    Button(
                                        onClick = {
                                            addAccountSubmitting = true
                                            addAccountError = null
                                            onManualLogin(addServerUrl, addUsername, addPassword) { success, _ ->
                                                addAccountSubmitting = false
                                                if (success) {
                                                    resetAddAccountForm()
                                                    navigateToRoot(SettingsDestination.Accounts)
                                                } else {
                                                    addAccountError = calDavValidationFailedText
                                                }
                                            }
                                        },
                                        enabled = addServerUrl.isNotBlank() &&
                                            addUsername.isNotBlank() &&
                                            addPassword.isNotBlank() &&
                                            !addAccountSubmitting &&
                                            !state.isBusy,
                                        shape = SettingsControlShape,
                                        colors = ButtonDefaults.buttonColors(containerColor = WarmBrown),
                                        modifier = Modifier.weight(1f).height(SettingsControlHeight),
                                    ) {
                                        if (addAccountSubmitting) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.onPrimary,
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(stringResource(R.string.checking_connection))
                                        } else {
                                            Text(stringResource(R.string.add))
                                        }
                                    }
                                }
                                SettingsHelpText(stringResource(R.string.new_accounts_help))
                                SettingsHelpText(stringResource(R.string.nextcloud_browser_login_help))
                            }
                        }
                        SettingsDestination.AddReadOnly -> {
                            SettingsSection(title = "Read-only URL", icon = Icons.Default.Public) {
                                OutlinedTextField(
                                    value = readOnlyUrl,
                                    onValueChange = { readOnlyUrl = it },
                                    label = { Text(stringResource(R.string.ics_calendar_url)) },
                                    singleLine = true,
                                    keyboardOptions = UrlKeyboardOptions,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Button(
                                    onClick = {
                                        onAddReadOnlyCalendar(readOnlyUrl)
                                        readOnlyUrl = ""
                                        navigateToRoot(SettingsDestination.Accounts)
                                    },
                                    enabled = readOnlyUrl.startsWith("http://", ignoreCase = true) || readOnlyUrl.startsWith("https://", ignoreCase = true),
                                    shape = SettingsControlShape,
                                    colors = ButtonDefaults.buttonColors(containerColor = WarmBrown),
                                    modifier = Modifier.fillMaxWidth().height(SettingsControlHeight),
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.add_url))
                                }
                                SettingsHelpText(stringResource(R.string.subscribed_readonly_help))
                            }
                        }
                        SettingsDestination.AccountDetail -> {
                            val account = state.accounts.firstOrNull { it.id == selectedAccountId }
                            if (account != null) {
                                val isLocalSource = account.id == UiLocalAccountId || account.serverUrl.startsWith(UiLocalCollectionPrefix)
                                val isAndroidSource = account.isAndroidProviderForUi()
                                SettingsSection(title = stringResource(R.string.edit_source), icon = Icons.Default.Settings) {
                                    OutlinedTextField(editAccountName, { editAccountName = it }, label = { Text(stringResource(R.string.name)) }, singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
                                    if (!isLocalSource && !isAndroidSource) {
                                        OutlinedTextField(editAccountServerUrl, { editAccountServerUrl = it }, label = { Text(stringResource(R.string.server_url)) }, singleLine = true, keyboardOptions = UrlKeyboardOptions, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
                                    }
                                    if (!isLocalSource && !isAndroidSource && account.sourceType != SourceType.ReadOnlyUrl && account.username != "Read-only URL") {
                                        OutlinedTextField(editAccountUsername, { editAccountUsername = it }, label = { Text(stringResource(R.string.username)) }, singleLine = true, keyboardOptions = UsernameKeyboardOptions, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
                                        SecurePasswordField(
                                            value = editAccountPassword,
                                            onValueChange = { editAccountPassword = it },
                                            label = stringResource(R.string.new_password_optional),
                                            shape = RoundedCornerShape(16.dp),
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                        HorizontalDivider(color = WarmLine)
                                        Text(stringResource(R.string.caldav_discovery), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                        CalendarMetadataRow(stringResource(R.string.caldav_principal), account.principalUrl ?: stringResource(R.string.calendar_unknown))
                                        CalendarMetadataRow(stringResource(R.string.caldav_calendar_home), account.calendarHomeUrl ?: stringResource(R.string.calendar_unknown))
                                        val accountCapabilities = account.capabilitiesJson.toJsonObjectOrNull()
                                        CalendarMetadataRow(
                                            stringResource(R.string.caldav_scheduling),
                                            accountCapabilities?.optBooleanOrNull("supportsScheduling").localizedSupportedUnsupported(),
                                        )
                                    }
                                    if (isAndroidSource) {
                                        AndroidProviderDiagnosticsCard(
                                            hiddenCalendarNames = state.hiddenAndroidProviderCalendarNames,
                                            showDisabledProviderCalendars = state.showDisabledAndroidProviderCalendars,
                                            onDisabledProviderCalendarsVisibleChanged = onDisabledAndroidProviderCalendarsVisibleChanged,
                                        )
                                    }
                                    if (!isLocalSource && !isAndroidSource && account.sourceType == SourceType.CalDav) {
                                        HorizontalDivider(color = WarmLine)
                                        Text(stringResource(R.string.create_server_calendar), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                        OutlinedTextField(
                                            value = newCalDavCalendarName,
                                            onValueChange = { newCalDavCalendarName = it },
                                            label = { Text(stringResource(R.string.name)) },
                                            singleLine = true,
                                            shape = RoundedCornerShape(16.dp),
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                        SettingsSwitchRow(
                                            title = stringResource(R.string.events),
                                            checked = newCalDavCalendarEvents,
                                            onCheckedChange = { newCalDavCalendarEvents = it },
                                        )
                                        SettingsSwitchRow(
                                            title = stringResource(R.string.tasks),
                                            checked = newCalDavCalendarTasks,
                                            onCheckedChange = { newCalDavCalendarTasks = it },
                                        )
                                        Button(
                                            onClick = {
                                                onCreateCalDavCalendar(
                                                    account.id,
                                                    newCalDavCalendarName,
                                                    newCalDavCalendarEvents,
                                                    newCalDavCalendarTasks,
                                                )
                                                newCalDavCalendarName = ""
                                            },
                                            enabled = newCalDavCalendarName.isNotBlank() &&
                                                (newCalDavCalendarEvents || newCalDavCalendarTasks) &&
                                                !state.isBusy,
                                            shape = SettingsControlShape,
                                            colors = ButtonDefaults.buttonColors(containerColor = settingsControlColor(), contentColor = WarmInk),
                                            modifier = Modifier.fillMaxWidth().height(SettingsControlHeight),
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text(stringResource(R.string.create_server_calendar))
                                        }
                                    }
                                    Button(
                                        onClick = {
                                            onUpdateAccount(
                                                account.id,
                                                editAccountName,
                                                if (isLocalSource || isAndroidSource) account.serverUrl else editAccountServerUrl,
                                                if (isLocalSource || isAndroidSource) account.username else editAccountUsername,
                                                if (isLocalSource || isAndroidSource) null else editAccountPassword.ifBlank { null },
                                            )
                                            navigateBack()
                                        },
                                        shape = SettingsControlShape,
                                        colors = ButtonDefaults.buttonColors(containerColor = WarmBrown),
                                        modifier = Modifier.fillMaxWidth().height(SettingsControlHeight),
                                    ) { Text(stringResource(R.string.save_changes)) }
                                    if (!isLocalSource) {
                                        Button(
                                            onClick = {
                                                deleteAccountCandidateId = account.id
                                            },
                                            shape = SettingsControlShape,
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.error),
                                            modifier = Modifier.fillMaxWidth().height(SettingsControlHeight),
                                        ) { Text(stringResource(R.string.remove_source)) }
                                    }
                                }
                            }
                        }
                        SettingsDestination.Behavior -> {
                            SettingsSection(title = stringResource(R.string.view), icon = Icons.Default.ViewAgenda) {
                                SettingsButtonRow(
                                    label = stringResource(R.string.default_view),
                                    value = state.selectedView.localizedLabel(),
                                    onClick = { defaultViewDialogOpen = true },
                                )
                                SettingsButtonRow(
                                    label = stringResource(R.string.first_day_of_week),
                                    value = state.firstDayOfWeek.localizedWeekdayLabel(),
                                    onClick = { firstDayDialogOpen = true },
                                )
                                SettingsSwitchRow(
                                    title = stringResource(R.string.show_completed_tasks),
                                    checked = state.showCompletedTasksInCalendar,
                                    onCheckedChange = onShowCompletedTasksChanged,
                                )
                                SettingsSwitchRow(
                                    title = stringResource(R.string.subtasks_expanded),
                                    checked = state.subtasksExpandedByDefault,
                                    onCheckedChange = onSubtasksExpandedByDefaultChanged,
                                    subtitle = stringResource(R.string.subtasks_expanded_help),
                                )
                                SettingsSliderRow(
                                    title = stringResource(R.string.all_day_items),
                                    subtitle = stringResource(R.string.all_day_items_help),
                                    value = state.maxVisibleAllDayItems,
                                    range = 0..10,
                                    onValueChanged = onMaxVisibleAllDayItemsChanged,
                                )
                                SettingsSwitchRow(
                                    title = stringResource(R.string.multi_day_sidebar_controls),
                                    checked = state.multiDaySidebarControlsEnabled,
                                    onCheckedChange = onMultiDaySidebarControlsChanged,
                                    subtitle = stringResource(R.string.multi_day_sidebar_controls_help),
                                )
                                AnimatedVisibility(
                                    visible = !state.multiDaySidebarControlsEnabled,
                                    enter = fadeIn(animationSpec = tween(MotionMedium, easing = MotionStandard)) +
                                        expandVertically(animationSpec = tween(MotionMedium, easing = MotionStandard)),
                                    exit = fadeOut(animationSpec = tween(120, easing = MotionStandardAccelerate)) +
                                        shrinkVertically(animationSpec = tween(160, easing = MotionStandardAccelerate)),
                                ) {
                                    SettingsSliderRow(
                                        title = stringResource(R.string.multi_day_count_setting),
                                        subtitle = stringResource(R.string.multi_day_count_setting_help),
                                        value = state.multiDayCount.coerceMultiDayCount(),
                                        range = MIN_MULTI_DAY_COUNT..MAX_MULTI_DAY_COUNT,
                                        onValueChanged = onMultiDayCountChanged,
                                    )
                                }
                                SettingsSwitchRow(
                                    title = stringResource(R.string.auto_map_previews),
                                    checked = state.autoLoadMapPreviews,
                                    onCheckedChange = onAutoLoadMapPreviewsChanged,
                                    subtitle = stringResource(R.string.auto_map_previews_help),
                                )
                            }
                            SettingsSection(title = stringResource(R.string.new_items), icon = Icons.Default.Add) {
                                SettingsSwitchRow(
                                    title = stringResource(R.string.focus_title_directly),
                                    checked = state.focusTitleOnCreate,
                                    onCheckedChange = onFocusTitleOnCreateChanged,
                                    subtitle = stringResource(R.string.focus_title_directly_help),
                                )
                                SettingsButtonRow(
                                    label = stringResource(R.string.default_event_duration),
                                    value = state.defaultEventDurationMinutes.localizedDurationLabel(),
                                    onClick = { eventDurationDialogOpen = true },
                                )
                                SettingsButtonRow(
                                    label = stringResource(R.string.new_tasks),
                                    value = state.localizedDefaultTaskScheduleLabel(),
                                    onClick = { taskDefaultDialogOpen = true },
                                )
                                val eventTargets = remember(state.collections) {
                                    state.collections.filter { it.supportsEvents && it.isEnabled && !it.isReadOnlyForUi() }
                                }
                                if (eventTargets.isNotEmpty()) {
                                    SettingsButtonRow(
                                        label = stringResource(R.string.default_calendar_events),
                                        value = eventTargets.firstOrNull { it.href == state.defaultEventCollectionHref }?.displayName ?: stringResource(R.string.automatic),
                                        onClick = { defaultEventCollectionDialogOpen = true },
                                    )
                                }
                                val taskTargets = remember(state.collections) {
                                    state.collections.filter { it.supportsTasks && it.isEnabled && !it.isReadOnlyForUi() }
                                }
                                if (taskTargets.isNotEmpty()) {
                                    SettingsButtonRow(
                                        label = stringResource(R.string.default_list_tasks),
                                        value = taskTargets.firstOrNull { it.href == state.defaultTaskCollectionHref }?.displayName ?: stringResource(R.string.automatic),
                                        onClick = { defaultTaskCollectionDialogOpen = true },
                                    )
                                }
                            }
                            SettingsSection(title = stringResource(R.string.notifications), icon = Icons.Default.Notifications) {
                                SettingsReminderEditor(
                                    title = stringResource(R.string.default_event_reminders),
                                    selected = state.defaultEventReminderMinutes,
                                    onSelectedChange = onDefaultEventRemindersChanged,
                                )
                                SettingsReminderEditor(
                                    title = stringResource(R.string.default_task_reminders),
                                    selected = state.defaultTaskReminderMinutes,
                                    onSelectedChange = onDefaultTaskRemindersChanged,
                                )
                                SettingsHelpText(stringResource(R.string.default_reminders_help))
                            }
                        }
                        SettingsDestination.Design -> {
                            SettingsSection(title = stringResource(R.string.color_scheme), icon = Icons.Default.Palette) {
                                SettingsButtonRow(
                                    label = stringResource(R.string.design),
                                    value = state.themeMode.localizedLabel(),
                                    onClick = { themeDialogOpen = true },
                                )
                                SettingsButtonRow(
                                    label = stringResource(R.string.appearance),
                                    value = state.colorMode.localizedLabel(),
                                    onClick = { colorModeDialogOpen = true },
                                )
                                Row(
                                    modifier = Modifier.padding(top = 2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    state.themeMode.themePreviewColors(MaterialTheme.colorScheme).forEach { color ->
                                        Box(Modifier.size(18.dp).clip(CircleShape).background(color))
                                    }
                                }
                            }
                            SettingsSection(title = stringResource(R.string.language), icon = Icons.Default.Public) {
                                SettingsButtonRow(
                                    label = stringResource(R.string.app_language),
                                    value = state.languageMode.localizedLabel(),
                                    onClick = { languageDialogOpen = true },
                                )
                            }
                            SettingsSection(title = stringResource(R.string.tasks), icon = Icons.Default.TaskAlt) {
                                SettingsTwoOptionRow(
                                    title = stringResource(R.string.color_source),
                                    subtitle = stringResource(R.string.color_source_help),
                                    leftLabel = stringResource(R.string.calendar),
                                    rightLabel = stringResource(R.string.priority),
                                    leftSelected = state.taskColorMode == TaskColorMode.Collection,
                                    onLeftSelected = { onTaskColorModeSelected(TaskColorMode.Collection) },
                                    onRightSelected = { onTaskColorModeSelected(TaskColorMode.Priority) },
                                )
                                SettingsSwitchRow(
                                    title = stringResource(R.string.priority_animations),
                                    checked = state.priorityAnimationsEnabled,
                                    onCheckedChange = onPriorityAnimationsChanged,
                                    subtitle = stringResource(R.string.priority_animations_help),
                                )
                                SettingsMenuRow(stringResource(R.string.sort_task_fields), stringResource(R.string.field_order_summary)) {
                                    navigateTo(SettingsDestination.TaskFieldOrder)
                                }
                            }
                            SettingsSection(title = stringResource(R.string.events), icon = Icons.Default.Event) {
                                SettingsMenuRow(stringResource(R.string.sort_event_fields), stringResource(R.string.field_order_summary)) {
                                    navigateTo(SettingsDestination.EventFieldOrder)
                                }
                            }
                        }
                        SettingsDestination.Widgets -> {
                            SettingsSection(title = stringResource(R.string.widgets), icon = Icons.Default.Widgets) {
                                SettingsMenuRow(
                                    title = stringResource(R.string.widget_agenda_name),
                                    value = state.agendaWidgetThemeMode.localizedLabel(),
                                    leadingIcon = Icons.Default.ViewAgenda,
                                ) { navigateTo(SettingsDestination.WidgetAgenda) }
                                SettingsMenuRow(
                                    title = stringResource(R.string.widget_month_name),
                                    value = state.monthWidgetThemeMode.localizedLabel(),
                                    leadingIcon = Icons.Default.CalendarMonth,
                                ) { navigateTo(SettingsDestination.WidgetMonth) }
                                SettingsMenuRow(
                                    title = stringResource(R.string.widget_tasks_name),
                                    value = state.tasksWidgetThemeMode.localizedLabel(),
                                    leadingIcon = Icons.Default.TaskAlt,
                                ) { navigateTo(SettingsDestination.WidgetTasks) }
                                SettingsMenuRow(
                                    title = stringResource(R.string.widget_day_name),
                                    value = state.dayWidgetThemeMode.localizedLabel(),
                                    leadingIcon = Icons.Default.ViewDay,
                                ) { navigateTo(SettingsDestination.WidgetDay) }
                                SettingsMenuRow(
                                    title = stringResource(R.string.widget_multi_name),
                                    value = state.multiWidgetThemeMode.localizedLabel(),
                                    leadingIcon = Icons.Default.Widgets,
                                ) { navigateTo(SettingsDestination.WidgetMulti) }
                            }
                        }
                        SettingsDestination.WidgetAgenda -> {
                            SettingsSection(title = stringResource(R.string.widget_agenda_name), icon = Icons.Default.ViewAgenda) {
                                SettingsButtonRow(
                                    label = stringResource(R.string.color_scheme),
                                    value = state.agendaWidgetThemeMode.localizedLabel(),
                                    onClick = { widgetThemeDialogTarget = SettingsDestination.WidgetAgenda },
                                )
                                SettingsButtonRow(
                                    label = stringResource(R.string.appearance),
                                    value = state.agendaWidgetColorMode.localizedLabel(),
                                    onClick = { widgetColorModeDialogTarget = SettingsDestination.WidgetAgenda },
                                )
                            }
                        }
                        SettingsDestination.WidgetMonth -> {
                            SettingsSection(title = stringResource(R.string.widget_month_name), icon = Icons.Default.CalendarMonth) {
                                SettingsButtonRow(
                                    label = stringResource(R.string.color_scheme),
                                    value = state.monthWidgetThemeMode.localizedLabel(),
                                    onClick = { widgetThemeDialogTarget = SettingsDestination.WidgetMonth },
                                )
                                SettingsButtonRow(
                                    label = stringResource(R.string.appearance),
                                    value = state.monthWidgetColorMode.localizedLabel(),
                                    onClick = { widgetColorModeDialogTarget = SettingsDestination.WidgetMonth },
                                )
                                SettingsHelpText(stringResource(R.string.widget_month_settings_help))
                            }
                        }
                        SettingsDestination.WidgetTasks -> {
                            SettingsSection(title = stringResource(R.string.widget_tasks_name), icon = Icons.Default.TaskAlt) {
                                SettingsButtonRow(
                                    label = stringResource(R.string.color_scheme),
                                    value = state.tasksWidgetThemeMode.localizedLabel(),
                                    onClick = { widgetThemeDialogTarget = SettingsDestination.WidgetTasks },
                                )
                                SettingsButtonRow(
                                    label = stringResource(R.string.appearance),
                                    value = state.tasksWidgetColorMode.localizedLabel(),
                                    onClick = { widgetColorModeDialogTarget = SettingsDestination.WidgetTasks },
                                )
                                SettingsButtonRow(
                                    label = stringResource(R.string.tasks_widget_display),
                                    value = state.tasksWidgetDisplayMode.localizedLabel(),
                                    onClick = { tasksWidgetDisplayDialogOpen = true },
                                )
                                AnimatedVisibility(visible = state.tasksWidgetDisplayMode == WidgetTaskDisplayMode.Today) {
                                    SettingsSwitchRow(
                                        title = stringResource(R.string.include_overdue_tasks),
                                        checked = state.tasksWidgetIncludeOverdue,
                                        onCheckedChange = onTasksWidgetIncludeOverdueChanged,
                                    )
                                }
                                SettingsButtonRow(
                                    label = stringResource(R.string.tasks_widget_plus_action),
                                    value = state.tasksWidgetCreateMode.localizedLabel(),
                                    onClick = { tasksWidgetCreateDialogOpen = true },
                                )
                                SettingsButtonRow(
                                    label = stringResource(R.string.tasks_widget_subtasks_default),
                                    value = state.tasksWidgetSubtaskDefaultMode.localizedLabel(),
                                    onClick = { tasksWidgetSubtaskDefaultDialogOpen = true },
                                )
                                SettingsHelpText(stringResource(R.string.tasks_widget_settings_help))
                            }
                        }
                        SettingsDestination.WidgetDay -> {
                            SettingsSection(title = stringResource(R.string.widget_day_name), icon = Icons.Default.ViewDay) {
                                SettingsButtonRow(
                                    label = stringResource(R.string.color_scheme),
                                    value = state.dayWidgetThemeMode.localizedLabel(),
                                    onClick = { widgetThemeDialogTarget = SettingsDestination.WidgetDay },
                                )
                                SettingsButtonRow(
                                    label = stringResource(R.string.appearance),
                                    value = state.dayWidgetColorMode.localizedLabel(),
                                    onClick = { widgetColorModeDialogTarget = SettingsDestination.WidgetDay },
                                )
                                SettingsSliderRow(
                                    title = stringResource(R.string.day_widget_scale),
                                    subtitle = stringResource(R.string.day_widget_scale_help),
                                    value = state.dayWidgetScalePercent,
                                    valueLabel = "${state.dayWidgetScalePercent}%",
                                    range = SettingsStore.MIN_DAY_WIDGET_SCALE_PERCENT..SettingsStore.MAX_DAY_WIDGET_SCALE_PERCENT,
                                    step = 5,
                                    onValueChanged = onDayWidgetScaleChanged,
                                )
                                SettingsSwitchRow(
                                    title = stringResource(R.string.day_widget_start_current_hour),
                                    subtitle = stringResource(R.string.day_widget_start_current_hour_help),
                                    checked = state.dayWidgetStartAtCurrentHour,
                                    onCheckedChange = onDayWidgetStartAtCurrentHourChanged,
                                )
                                AnimatedVisibility(visible = !state.dayWidgetStartAtCurrentHour) {
                                    SettingsSliderRow(
                                        title = stringResource(R.string.day_widget_start_hour),
                                        subtitle = stringResource(R.string.day_widget_start_hour_help),
                                        value = state.dayWidgetStartHour,
                                        valueLabel = "%02d:00".format(state.dayWidgetStartHour),
                                        range = 0..23,
                                        onValueChanged = onDayWidgetStartHourChanged,
                                    )
                                }
                            }
                        }
                        SettingsDestination.WidgetMulti -> {
                            SettingsSection(title = stringResource(R.string.widget_multi_name), icon = Icons.Default.Widgets) {
                                SettingsButtonRow(
                                    label = stringResource(R.string.color_scheme),
                                    value = state.multiWidgetThemeMode.localizedLabel(),
                                    onClick = { widgetThemeDialogTarget = SettingsDestination.WidgetMulti },
                                )
                                SettingsButtonRow(
                                    label = stringResource(R.string.appearance),
                                    value = state.multiWidgetColorMode.localizedLabel(),
                                    onClick = { widgetColorModeDialogTarget = SettingsDestination.WidgetMulti },
                                )
                                SettingsSliderRow(
                                    title = stringResource(R.string.multi_widget_split),
                                    subtitle = stringResource(R.string.multi_widget_split_help),
                                    value = state.multiWidgetMonthPercent,
                                    valueLabel = "${state.multiWidgetMonthPercent}% / ${100 - state.multiWidgetMonthPercent}%",
                                    range = SettingsStore.MIN_MULTI_WIDGET_MONTH_PERCENT..SettingsStore.MAX_MULTI_WIDGET_MONTH_PERCENT,
                                    step = 5,
                                    onValueChanged = onMultiWidgetMonthPercentChanged,
                                )
                            }
                        }
                        SettingsDestination.Privacy -> PrivacyPolicyPage()
                        SettingsDestination.EventFieldOrder -> {
                            SettingsSection(title = stringResource(R.string.event_fields), icon = Icons.Default.Event) {
                                FieldOrderList(
                                    fields = state.eventFieldOrder,
                                    onChanged = onEventFieldOrderChanged,
                                )
                            }
                        }
                        SettingsDestination.TaskFieldOrder -> {
                            SettingsSection(title = stringResource(R.string.task_fields), icon = Icons.Default.TaskAlt) {
                                FieldOrderList(
                                    fields = state.taskFieldOrder,
                                    onChanged = onTaskFieldOrderChanged,
                                )
                            }
                        }
                        SettingsDestination.Sources -> {
                            SettingsSection(title = stringResource(R.string.sources), icon = Icons.Default.CalendarMonth) {
                                SettingsInfoRow(stringResource(R.string.calendar), state.collections.count { it.supportsEvents && !it.supportsTasks }.toString())
                                SettingsInfoRow(stringResource(R.string.task_lists), state.collections.count { it.supportsTasks && !it.supportsEvents }.toString())
                                SettingsInfoRow(stringResource(R.string.events_and_tasks), state.collections.count { it.supportsEvents && it.supportsTasks }.toString())
                                SettingsInfoRow(stringResource(R.string.active), state.collections.count { it.isEnabled }.toString())
                                SettingsHelpText(stringResource(R.string.calendar_sidebar_help))
                            }
                            SettingsSection(title = stringResource(R.string.readonly_urls), icon = Icons.Default.Public) {
                                OutlinedTextField(
                                    value = readOnlyUrl,
                                    onValueChange = { readOnlyUrl = it },
                                    label = { Text(stringResource(R.string.ics_calendar_url)) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Button(
                                    onClick = {
                                        onAddReadOnlyCalendar(readOnlyUrl)
                                        readOnlyUrl = ""
                                        navigateToRoot(SettingsDestination.Accounts)
                                    },
                                    enabled = readOnlyUrl.startsWith("http://", ignoreCase = true) || readOnlyUrl.startsWith("https://", ignoreCase = true),
                                    shape = RoundedCornerShape(18.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = WarmBrown),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.add_url))
                                }
                                SettingsHelpText(stringResource(R.string.readonly_urls_help))
                            }
                        }
                        SettingsDestination.Reorder -> {
                            SettingsSection(title = stringResource(R.string.calendar_order), icon = Icons.Default.DragHandle) {
                                SettingsHelpText(stringResource(R.string.calendar_order_help))
                                CollectionReorderList(
                                    collections = state.collections,
                                    accounts = state.accounts,
                                    hiddenCollectionHrefs = state.hiddenCollectionHrefs,
                                    onReordered = onCollectionsReordered,
                                    onCollectionClick = onCollectionSettings,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(navBottom + 12.dp))
                }
            }
        }
    }

    if (defaultViewDialogOpen) {
        AlertDialog(
            onDismissRequest = { defaultViewDialogOpen = false },
            title = { Text(appString(R.string.default_view)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(CalendarViewMode.ThreeDay, CalendarViewMode.Day, CalendarViewMode.Month, CalendarViewMode.Agenda).forEach { view ->
                        SettingsRadioRow(
                            selected = state.selectedView == view,
                            title = view.localizedLabel(),
                            leadingIcon = view.settingsIcon(),
                            onClick = {
                                onViewSelected(view)
                                defaultViewDialogOpen = false
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { defaultViewDialogOpen = false }) {
                    Text(appString(R.string.done))
                }
            }
        )
    }
    if (firstDayDialogOpen) {
        SettingsChoiceDialog(
            title = appString(R.string.first_day_of_week),
            options = listOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY, DayOfWeek.SATURDAY),
            selected = state.firstDayOfWeek,
            label = { it.localizedWeekdayLabel() },
            onSelected = {
                onFirstDayOfWeekSelected(it)
                firstDayDialogOpen = false
            },
            onDismiss = { firstDayDialogOpen = false },
        )
    }
    if (themeDialogOpen) {
        ThemeSelectionDialog(
            selected = state.themeMode,
            onSelected = {
                onThemeSelected(it)
                themeDialogOpen = false
            },
            onDismiss = { themeDialogOpen = false },
        )
    }
    if (colorModeDialogOpen) {
        SettingsChoiceDialog(
            title = appString(R.string.appearance),
            options = listOf(AppColorMode.Auto, AppColorMode.Light, AppColorMode.Dark),
            selected = state.colorMode,
            label = { it.localizedLabel() },
            onSelected = {
                onColorModeSelected(it)
                colorModeDialogOpen = false
            },
            onDismiss = { colorModeDialogOpen = false },
        )
    }
    widgetColorModeDialogTarget?.let { target ->
        SettingsChoiceDialog(
            title = appString(R.string.appearance),
            options = listOf(WidgetColorMode.FollowApp, WidgetColorMode.FollowOs, WidgetColorMode.Light, WidgetColorMode.Dark),
            selected = when (target) {
                SettingsDestination.WidgetAgenda -> state.agendaWidgetColorMode
                SettingsDestination.WidgetMonth -> state.monthWidgetColorMode
                SettingsDestination.WidgetTasks -> state.tasksWidgetColorMode
                SettingsDestination.WidgetDay -> state.dayWidgetColorMode
                SettingsDestination.WidgetMulti -> state.multiWidgetColorMode
                else -> WidgetColorMode.FollowApp
            },
            label = { it.localizedLabel() },
            onSelected = {
                when (target) {
                    SettingsDestination.WidgetAgenda -> onAgendaWidgetColorModeSelected(it)
                    SettingsDestination.WidgetMonth -> onMonthWidgetColorModeSelected(it)
                    SettingsDestination.WidgetTasks -> onTasksWidgetColorModeSelected(it)
                    SettingsDestination.WidgetDay -> onDayWidgetColorModeSelected(it)
                    SettingsDestination.WidgetMulti -> onMultiWidgetColorModeSelected(it)
                    else -> Unit
                }
                widgetColorModeDialogTarget = null
            },
            onDismiss = { widgetColorModeDialogTarget = null },
        )
    }
    widgetThemeDialogTarget?.let { target ->
        SettingsChoiceDialog(
            title = appString(R.string.color_scheme),
            options = listOf(
                WidgetThemeMode.FollowApp,
                WidgetThemeMode.KgsBlue,
                WidgetThemeMode.KgsWarm,
                WidgetThemeMode.KgsFresh,
                WidgetThemeMode.SystemDynamic,
            ),
            selected = when (target) {
                SettingsDestination.WidgetAgenda -> state.agendaWidgetThemeMode
                SettingsDestination.WidgetMonth -> state.monthWidgetThemeMode
                SettingsDestination.WidgetTasks -> state.tasksWidgetThemeMode
                SettingsDestination.WidgetDay -> state.dayWidgetThemeMode
                SettingsDestination.WidgetMulti -> state.multiWidgetThemeMode
                else -> WidgetThemeMode.FollowApp
            },
            label = { it.localizedLabel() },
            onSelected = {
                when (target) {
                    SettingsDestination.WidgetAgenda -> onAgendaWidgetThemeSelected(it)
                    SettingsDestination.WidgetMonth -> onMonthWidgetThemeSelected(it)
                    SettingsDestination.WidgetTasks -> onTasksWidgetThemeSelected(it)
                    SettingsDestination.WidgetDay -> onDayWidgetThemeSelected(it)
                    SettingsDestination.WidgetMulti -> onMultiWidgetThemeSelected(it)
                    else -> Unit
                }
                widgetThemeDialogTarget = null
            },
            onDismiss = { widgetThemeDialogTarget = null },
        )
    }
    if (tasksWidgetDisplayDialogOpen) {
        SettingsChoiceDialog(
            title = appString(R.string.tasks_widget_display),
            options = listOf(WidgetTaskDisplayMode.Planned, WidgetTaskDisplayMode.Unplanned, WidgetTaskDisplayMode.Today),
            selected = state.tasksWidgetDisplayMode,
            label = { it.localizedLabel() },
            onSelected = {
                onTasksWidgetDisplayModeSelected(it)
                tasksWidgetDisplayDialogOpen = false
            },
            onDismiss = { tasksWidgetDisplayDialogOpen = false },
        )
    }
    if (tasksWidgetCreateDialogOpen) {
        SettingsChoiceDialog(
            title = appString(R.string.tasks_widget_plus_action),
            options = listOf(WidgetTaskCreateMode.Today, WidgetTaskCreateMode.Unplanned),
            selected = state.tasksWidgetCreateMode,
            label = { it.localizedLabel() },
            onSelected = {
                onTasksWidgetCreateModeSelected(it)
                tasksWidgetCreateDialogOpen = false
            },
            onDismiss = { tasksWidgetCreateDialogOpen = false },
        )
    }
    if (tasksWidgetSubtaskDefaultDialogOpen) {
        SettingsChoiceDialog(
            title = appString(R.string.tasks_widget_subtasks_default),
            options = listOf(
                WidgetTaskSubtaskDefaultMode.FollowApp,
                WidgetTaskSubtaskDefaultMode.Open,
                WidgetTaskSubtaskDefaultMode.Closed,
            ),
            selected = state.tasksWidgetSubtaskDefaultMode,
            label = { it.localizedLabel() },
            onSelected = {
                onTasksWidgetSubtaskDefaultModeSelected(it)
                tasksWidgetSubtaskDefaultDialogOpen = false
            },
            onDismiss = { tasksWidgetSubtaskDefaultDialogOpen = false },
        )
    }
    if (languageDialogOpen) {
        SettingsChoiceDialog(
            title = appString(R.string.app_language),
            options = listOf(AppLanguageMode.System, AppLanguageMode.English, AppLanguageMode.German),
            selected = state.languageMode,
            label = { it.localizedLabel() },
            onSelected = {
                onLanguageSelected(it)
                languageDialogOpen = false
            },
            onDismiss = { languageDialogOpen = false },
        )
    }
    if (eventDurationDialogOpen) {
        EventDurationDialog(
            currentMinutes = state.defaultEventDurationMinutes,
            onSelected = {
                onDefaultEventDurationChanged(it)
                eventDurationDialogOpen = false
            },
            onDismiss = { eventDurationDialogOpen = false },
        )
    }
    if (taskDefaultDialogOpen) {
        SettingsChoiceDialog(
            title = appString(R.string.new_tasks),
            options = listOf(TaskDefaultSchedule.None, TaskDefaultSchedule.DateOnly, TaskDefaultSchedule.DateTime),
            selected = when {
                !state.defaultTaskHasDate -> TaskDefaultSchedule.None
                state.defaultTaskHasTime -> TaskDefaultSchedule.DateTime
                else -> TaskDefaultSchedule.DateOnly
            },
            label = { it.localizedLabel() },
            onSelected = {
                onDefaultTaskHasDateChanged(it != TaskDefaultSchedule.None)
                onDefaultTaskHasTimeChanged(it == TaskDefaultSchedule.DateTime)
                taskDefaultDialogOpen = false
            },
            onDismiss = { taskDefaultDialogOpen = false },
        )
    }
    if (defaultEventCollectionDialogOpen) {
        CollectionSelectionDialog(
            title = appString(R.string.default_calendar),
            selectedHref = state.defaultEventCollectionHref,
            collections = state.collections.filter { it.supportsEvents && it.isEnabled && !it.isReadOnlyForUi() },
            hiddenCollectionHrefs = state.hiddenCollectionHrefs,
            onSelected = {
                onDefaultEventCollectionSelected(it)
                defaultEventCollectionDialogOpen = false
            },
            onDismiss = { defaultEventCollectionDialogOpen = false },
        )
    }
    if (defaultTaskCollectionDialogOpen) {
        CollectionSelectionDialog(
            title = appString(R.string.default_list),
            selectedHref = state.defaultTaskCollectionHref,
            collections = state.collections.filter { it.supportsTasks && it.isEnabled && !it.isReadOnlyForUi() },
            hiddenCollectionHrefs = state.hiddenCollectionHrefs,
            onSelected = {
                onDefaultTaskCollectionSelected(it)
                defaultTaskCollectionDialogOpen = false
            },
            onDismiss = { defaultTaskCollectionDialogOpen = false },
        )
    }
    deleteAccountCandidateId?.let { accountId ->
        AlertDialog(
            onDismissRequest = { deleteAccountCandidateId = null },
            title = { Text(stringResource(R.string.remove_source_question)) },
            text = { Text(stringResource(R.string.remove_source_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteAccount(accountId)
                        deleteAccountCandidateId = null
                        navigateBack()
                    },
                ) {
                    Text(stringResource(R.string.remove), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteAccountCandidateId = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun ProblemsPage(
    problems: List<ProblemItem>,
    onEventClick: (EventEntity) -> Unit,
    onTaskClick: (TaskEntity) -> Unit,
    onClose: () -> Unit,
) {
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(90f),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = statusTop + 8.dp, bottom = navBottom),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back), tint = WarmInk, modifier = Modifier.size(25.dp))
                }
                Text(
                    stringResource(R.string.problems),
                    color = WarmInk,
                    fontSize = 24.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (problems.isEmpty()) {
                    SettingsInfoRow(stringResource(R.string.no_problems), stringResource(R.string.all_synced))
                } else {
                    problems.forEach { problem ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(SettingsControlShape)
                                .clickable(
                                    enabled = problem.target != null,
                                    onClick = {
                                        when (val target = problem.target) {
                                            is ProblemTarget.Event -> onEventClick(target.event)
                                            is ProblemTarget.Task -> onTaskClick(target.task)
                                            null -> Unit
                                        }
                                    },
                                ),
                            shape = SettingsControlShape,
                            color = settingsControlColor(),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    Icons.Default.WarningAmber,
                                    contentDescription = null,
                                    tint = SyncPendingOrange,
                                    modifier = Modifier.size(22.dp),
                                )
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(
                                        problem.title,
                                        color = WarmInk,
                                        fontSize = 14.sp,
                                        lineHeight = 18.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        problem.body,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp,
                                    )
                                }
                                if (problem.target != null) {
                                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun CollectionSelectionDialog(
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

private enum class TaskDefaultSchedule(val label: String) {
    None("No date"),
    DateOnly("Date only"),
    DateTime("Date and time"),
}

@Composable
private fun TaskDefaultSchedule.localizedLabel(): String = when (this) {
    TaskDefaultSchedule.None -> appString(R.string.no_date)
    TaskDefaultSchedule.DateOnly -> appString(R.string.date_only)
    TaskDefaultSchedule.DateTime -> appString(R.string.date_and_time)
}

private enum class DurationUnit(val label: String, val minutes: Int) {
    Minutes("Minutes", 1),
    Hours("Hours", 60),
}

@Composable
private fun DurationUnit.localizedLabel(): String = when (this) {
    DurationUnit.Minutes -> appString(R.string.minutes)
    DurationUnit.Hours -> appString(R.string.hours)
}

private enum class EventDurationChoice {
    ThirtyMinutes,
    OneHour,
    Custom,
}

@Composable
private fun ThemeSelectionDialog(
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
private fun AppThemeMode.themePreviewColors(currentScheme: androidx.compose.material3.ColorScheme): List<Color> = when (this) {
    AppThemeMode.KgsBlue -> listOf(Color(0xFF2563A8), Color(0xFFDCEBFF), Color.White, Color(0xFF4FA7BD))
    AppThemeMode.KgsWarm -> listOf(Color(0xFF9E572B), Color(0xFFFBE9E2), Color.White, Color(0xFF56B0A2))
    AppThemeMode.KgsFresh -> listOf(Color(0xFF0E7C66), Color(0xFFDDF2EC), Color.White, Color(0xFFE29D3E))
    AppThemeMode.SystemDynamic -> listOf(currentScheme.primary, currentScheme.background, currentScheme.surface, currentScheme.tertiary)
}

@Composable
private fun EventDurationDialog(
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
private fun <T> SettingsChoiceDialog(
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
private fun FieldOrderList(
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
private fun Int.localizedDurationLabel(): String =
    if (this < 60) {
        stringResource(R.string.duration_minutes, this)
    } else {
        val hoursText = stringResource(R.string.duration_hours, this / 60)
        if (this % 60 == 0) hoursText else "$hoursText ${stringResource(R.string.duration_minutes, this % 60)}"
    }

@Composable
private fun CalendarUiState.localizedDefaultTaskScheduleLabel(): String = when {
    !defaultTaskHasDate -> stringResource(R.string.no_date)
    defaultTaskHasTime -> stringResource(R.string.date_and_time)
    else -> stringResource(R.string.date_only)
}

@Composable
private fun CollectionReorderList(
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

@Composable
private fun SettingsDestination.localizedTitle(): String = when (this) {
    SettingsDestination.Main -> stringResource(R.string.settings)
    SettingsDestination.Accounts -> stringResource(R.string.calendar)
    SettingsDestination.AddSource -> stringResource(R.string.add_calendar)
    SettingsDestination.AddAccount -> stringResource(R.string.caldav_account)
    SettingsDestination.AddReadOnly -> stringResource(R.string.read_only_calendar)
    SettingsDestination.AccountDetail -> stringResource(R.string.edit_source)
    SettingsDestination.Behavior -> stringResource(R.string.behavior)
    SettingsDestination.Design -> stringResource(R.string.design)
    SettingsDestination.Widgets -> stringResource(R.string.widgets)
    SettingsDestination.WidgetAgenda -> stringResource(R.string.widget_agenda_name)
    SettingsDestination.WidgetMonth -> stringResource(R.string.widget_month_name)
    SettingsDestination.WidgetTasks -> stringResource(R.string.widget_tasks_name)
    SettingsDestination.WidgetMulti -> stringResource(R.string.widget_multi_name)
    SettingsDestination.WidgetDay -> stringResource(R.string.widget_day_name)
    SettingsDestination.Privacy -> stringResource(R.string.privacy)
    SettingsDestination.EventFieldOrder -> stringResource(R.string.event_fields)
    SettingsDestination.TaskFieldOrder -> stringResource(R.string.task_fields)
    SettingsDestination.Sources -> stringResource(R.string.calendars_and_sources)
    SettingsDestination.Reorder -> stringResource(R.string.order)
}

@Composable
private fun PrivacyPolicyPage() {
    val context = LocalContext.current
    SettingsSection(title = stringResource(R.string.privacy), icon = Icons.Default.Lock) {
        SettingsMenuRow(
            title = stringResource(R.string.open_privacy_policy),
            value = PrivacyPolicyUrl,
            leadingIcon = Icons.Default.Public,
        ) {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PrivacyPolicyUrl)))
        }
        PrivacyTextCard(
            title = stringResource(R.string.privacy_storage_title),
            body = stringResource(R.string.privacy_storage_body),
        )
        PrivacyTextCard(
            title = stringResource(R.string.privacy_sync_title),
            body = stringResource(R.string.privacy_sync_body),
        )
        PrivacyTextCard(
            title = stringResource(R.string.privacy_location_title),
            body = stringResource(R.string.privacy_location_body),
        )
        PrivacyTextCard(
            title = stringResource(R.string.privacy_delete_title),
            body = stringResource(R.string.privacy_delete_body),
        )
    }
}

private const val PrivacyPolicyUrl = "https://kgs501.github.io/kgs-calendar/"
private const val BugReportIssuesUrl = "https://github.com/KGS501/kgs-calendar/issues"
internal const val SponsorProjectUrl = "https://github.com/sponsors/KGS501"
private const val GoogleCalendarSyncSelectUrl = "https://calendar.google.com/calendar/syncselect"

@Composable
private fun AndroidProviderDiagnosticsCard(
    hiddenCalendarNames: List<String>,
    showDisabledProviderCalendars: Boolean,
    onDisabledProviderCalendarsVisibleChanged: (Boolean) -> Unit,
) {
    if (hiddenCalendarNames.isEmpty()) return
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val collapsedCount = 3
    val shownNames = if (expanded) hiddenCalendarNames else hiddenCalendarNames.take(collapsedCount)
    val remaining = (hiddenCalendarNames.size - collapsedCount).coerceAtLeast(0)
    val cardColor = SyncPendingOrange.copy(alpha = if (MaterialTheme.colorScheme.background.isDark()) 0.26f else 0.14f)
    val textColor = WarmInk
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = SettingsControlShape,
        color = cardColor,
        border = BorderStroke(1.dp, SyncPendingOrange.copy(alpha = 0.42f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = SyncPendingOrange,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    stringResource(R.string.android_hidden_calendars_title),
                    color = textColor,
                    fontSize = 15.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    shownNames.forEach { name ->
                        Text(
                            "- $name",
                            color = textColor,
                            fontSize = 13.sp,
                            lineHeight = 17.sp,
                        )
                    }
                    if (!expanded && remaining > 0) {
                        Text(
                            stringResource(R.string.more_hidden_calendars, remaining),
                            color = WarmBrown,
                            fontSize = 13.sp,
                            lineHeight = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { expanded = true }
                                .padding(vertical = 2.dp),
                        )
                    } else if (expanded && remaining > 0) {
                        TextButton(onClick = { expanded = false }) {
                            Text(stringResource(R.string.show_less))
                        }
                    }
                }
            }
            Text(
                stringResource(R.string.android_hidden_calendars_help),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GoogleCalendarSyncSelectUrl))) },
                    shape = SettingsControlShape,
                    modifier = Modifier.fillMaxWidth().height(SettingsControlHeight),
                ) {
                    Text(stringResource(R.string.open_google_syncselect))
                }
                Button(
                    onClick = { onDisabledProviderCalendarsVisibleChanged(!showDisabledProviderCalendars) },
                    shape = SettingsControlShape,
                    colors = ButtonDefaults.buttonColors(containerColor = WarmBrown),
                    modifier = Modifier.fillMaxWidth().height(SettingsControlHeight),
                ) {
                    Text(
                        if (showDisabledProviderCalendars) {
                            stringResource(R.string.hide_disabled_provider_calendars)
                        } else {
                            stringResource(R.string.show_disabled_provider_calendars)
                        },
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun PrivacyTextCard(title: String, body: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = SettingsControlShape,
        color = settingsControlColor(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, color = WarmInk, fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
            SelectionContainer {
                Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 17.sp)
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(icon, contentDescription = null, tint = WarmBrown, modifier = Modifier.size(20.dp))
            Text(title, color = WarmInk, fontSize = 16.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold)
        }
        content()
    }
}

@Composable
internal fun settingsControlColor(): Color =
    if (MaterialTheme.colorScheme.background.isDark()) MaterialTheme.colorScheme.surface else Color.White

@Composable
internal fun accentContainerColor(): Color =
    if (MaterialTheme.colorScheme.background.isDark()) {
        WarmBrown.blendWith(Color.White, 0.18f)
    } else {
        WarmBrown.blendWith(Color.White, 0.78f)
    }

@Composable
internal fun accentContainerContentColor(): Color =
    if (MaterialTheme.colorScheme.background.isDark()) {
        MaterialTheme.colorScheme.background
    } else {
        if (WarmBrown.isDark()) Color.White else Color(0xFF1C1A18)
    }

@Composable
private fun SettingsWarningCard(problems: List<String>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = SettingsControlShape,
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = if (MaterialTheme.colorScheme.background.isDark()) 0.74f else 0.92f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Default.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(22.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(R.string.problems),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontSize = 15.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                problems.forEach { problem ->
                    Text(
                        "- $problem",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsHelpText(text: String) {
    var open by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        IconButton(onClick = { open = true }, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.HelpOutline, contentDescription = stringResource(R.string.explanation), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
        Text(
            text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(top = 5.dp),
        )
    }
    if (open) {
        AlertDialog(
            modifier = Modifier.padding(horizontal = 20.dp),
            onDismissRequest = { open = false },
            shape = RoundedCornerShape(24.dp),
            title = { Text(stringResource(R.string.explanation), color = WarmInk, fontWeight = FontWeight.SemiBold) },
            text = { SelectionContainer { Text(text, color = WarmInk, lineHeight = 20.sp) } },
            confirmButton = {
                TextButton(onClick = { open = false }) { Text(stringResource(R.string.close)) }
            },
        )
    }
}

@Composable
private fun InlineHelpButton(text: String) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }, modifier = Modifier.size(24.dp)) {
        Icon(Icons.Default.HelpOutline, contentDescription = stringResource(R.string.explanation), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
    }
    if (open) {
        AlertDialog(
            modifier = Modifier.padding(horizontal = 20.dp),
            onDismissRequest = { open = false },
            shape = RoundedCornerShape(24.dp),
            title = { Text(stringResource(R.string.explanation), color = WarmInk, fontWeight = FontWeight.SemiBold) },
            text = { SelectionContainer { Text(text, color = WarmInk, lineHeight = 20.sp) } },
            confirmButton = {
                TextButton(onClick = { open = false }) { Text(stringResource(R.string.close)) }
            },
        )
    }
}

@Composable
private fun SettingsMenuRow(
    title: String,
    value: String,
    leadingIcon: ImageVector? = null,
    helpText: String? = null,
    warningBadge: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(SettingsControlHeight)
            .clip(SettingsControlShape)
            .clickable(onClick = onClick),
        shape = SettingsControlShape,
        color = settingsControlColor(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            leadingIcon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = WarmBrown,
                    modifier = Modifier.size(21.dp),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = WarmInk, fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            helpText?.let { InlineHelpButton(it) }
            if (warningBadge) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(SyncPendingOrange.copy(alpha = if (MaterialTheme.colorScheme.background.isDark()) 0.34f else 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.WarningAmber,
                        contentDescription = stringResource(R.string.android_hidden_calendars_title),
                        tint = SyncPendingOrange,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

private data class SourceExampleBrand(
    val name: String,
    val color: Color,
    val iconRes: Int? = null,
    val imageVector: ImageVector? = null,
)

@Composable
private fun SourceOptionCard(
    title: String,
    value: String,
    leadingIcon: ImageVector,
    examples: List<SourceExampleBrand>,
    helpText: String? = null,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SettingsControlShape)
            .clickable(onClick = onClick),
        shape = SettingsControlShape,
        color = settingsControlColor(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = WarmBrown,
                    modifier = Modifier.size(21.dp),
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(title, color = WarmInk, fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 15.sp)
                }
                helpText?.let { InlineHelpButton(it) }
            }
            FadedHorizontalScrollRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                examples.forEach { brand ->
                    SourceExamplePill(brand)
                }
            }
        }
    }
}

@Composable
private fun SourceExamplePill(brand: SourceExampleBrand) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = brand.color.copy(alpha = if (MaterialTheme.colorScheme.background.isDark()) 0.24f else 0.13f),
        border = BorderStroke(1.dp, brand.color.copy(alpha = if (MaterialTheme.colorScheme.background.isDark()) 0.48f else 0.34f)),
    ) {
        Row(
            modifier = Modifier.padding(start = 6.dp, top = 5.dp, end = 10.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(brand.color),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    brand.iconRes != null -> Icon(
                        painter = painterResource(brand.iconRes),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(15.dp),
                    )
                    brand.imageVector != null -> Icon(
                        imageVector = brand.imageVector,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
            Text(
                brand.name,
                color = WarmInk,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SettingsButtonRow(label: String, value: String, onClick: () -> Unit) {
    SettingsMenuRow(title = label, value = value, onClick = onClick)
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
    warningUnchecked: Boolean = false,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(SettingsControlHeight)
            .clip(SettingsControlShape)
            .clickable { onCheckedChange(!checked) },
        shape = SettingsControlShape,
        color = settingsControlColor(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = WarmInk, fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
                subtitle?.let {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        InlineHelpButton(it)
                        Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = if (warningUnchecked) {
                    SwitchDefaults.colors(
                        uncheckedThumbColor = SyncPendingOrange,
                        uncheckedTrackColor = SyncPendingOrange.copy(alpha = 0.28f),
                        uncheckedBorderColor = SyncPendingOrange,
                    )
                } else {
                    SwitchDefaults.colors()
                },
            )
        }
    }
}

@Composable
private fun SettingsSliderRow(
    title: String,
    subtitle: String? = null,
    value: Int,
    valueLabel: String = value.toString(),
    range: IntRange,
    step: Int = 1,
    onValueChanged: (Int) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = SettingsControlShape,
        color = settingsControlColor(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    subtitle?.let { InlineHelpButton(it) }
                    Text(title, color = WarmInk, fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
                }
                Text(valueLabel, color = WarmBrown, fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
            }
            val safeStep = step.coerceAtLeast(1)
            val span = (range.last - range.first).coerceAtLeast(safeStep)
            Slider(
                value = value.toFloat(),
                onValueChange = {
                    val snapped = range.first + (((it - range.first) / safeStep).roundToInt() * safeStep)
                    onValueChanged(snapped.coerceIn(range.first, range.last))
                },
                valueRange = range.first.toFloat()..range.last.toFloat(),
                steps = (span / safeStep - 1).coerceAtLeast(0),
            )
        }
    }
}

@Composable
private fun SettingsTwoOptionRow(
    title: String,
    subtitle: String,
    leftLabel: String,
    rightLabel: String,
    leftSelected: Boolean,
    onLeftSelected: () -> Unit,
    onRightSelected: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(SettingsControlHeight),
        shape = SettingsControlShape,
        color = settingsControlColor(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = WarmInk, fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    InlineHelpButton(subtitle)
                    Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(
                modifier = Modifier
                    .height(38.dp)
                    .clip(RoundedCornerShape(19.dp))
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.42f))
                    .padding(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SettingsSegmentPill(leftLabel, selected = leftSelected, onClick = onLeftSelected)
                SettingsSegmentPill(rightLabel, selected = !leftSelected, onClick = onRightSelected)
            }
        }
    }
}

@Composable
private fun SettingsSegmentPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) WarmBrown else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else WarmInk,
            fontSize = 12.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SettingsInfoRow(label: String, value: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(SettingsControlHeight),
        shape = SettingsControlShape,
        color = settingsControlColor(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, color = WarmInk, fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 15.sp)
            }
        }
    }
}

@Composable
private fun SettingsRadioRow(selected: Boolean, title: String, leadingIcon: ImageVector? = null, onClick: () -> Unit) {
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
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            leadingIcon?.let {
                Icon(it, contentDescription = null, tint = if (selected) WarmBrown else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
            Text(title, color = WarmInk, fontSize = 15.sp, lineHeight = 18.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, modifier = Modifier.weight(1f))
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

@Composable
private fun CollectionSettingsSheet(
    collection: CollectionEntity,
    visibleInViews: Boolean,
    onSave: (String, Int?) -> Unit,
    onEnabledChanged: (Boolean) -> Unit,
    onVisibleInViewsChanged: (Boolean) -> Unit,
    onDelete: (() -> Unit)? = null,
    onClose: () -> Unit,
) {
    var name by remember(collection.href) { mutableStateOf(collection.displayName) }
    var customColor by remember(collection.href) { mutableStateOf(collection.customColor) }
    var colorPickerOpen by remember(collection.href) { mutableStateOf(false) }
    var enabled by remember(collection.href) { mutableStateOf(collection.isEnabled) }
    var confirmDelete by remember(collection.href) { mutableStateOf(false) }
    var metadataExpanded by remember(collection.href) { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val metadataRows = collection.localizedPermissionMetadataRows()
    val automaticColor = collection.automaticColor ?: collection.sourceColor ?: collection.color
    val effectiveColor = customColor ?: automaticColor
    val automaticDescription = if (collection.sourceColor != null) {
        stringResource(R.string.source_color_help)
    } else {
        stringResource(R.string.auto_color_help)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        EditorTopBar(
            title = stringResource(R.string.calendar),
            onClose = onClose,
            onSave = { onSave(name, customColor) },
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(24.dp).clip(CircleShape).background(Color(effectiveColor)))
            if (collection.supportsEvents) CalendarCapabilityChip(stringResource(R.string.events), Color(0xFF2F5AEA))
            if (collection.supportsTasks) CalendarCapabilityChip(stringResource(R.string.tasks), Color(0xFF00A86B))
        }
        OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.name)) }, singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
        SettingsMenuRow(
            title = stringResource(R.string.color),
            value = if (customColor == null) stringResource(R.string.auto_source_color, automaticColor.toHexText()) else stringResource(R.string.custom_source_color, effectiveColor.toHexText()),
            leadingIcon = Icons.Default.Palette,
            onClick = { colorPickerOpen = true },
        )
        Text(
            if (customColor == null) automaticDescription else stringResource(R.string.custom_color_active),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            lineHeight = 17.sp,
        )
        if (customColor != null) {
            TextButton(onClick = { customColor = null }) {
                Text(stringResource(R.string.remove_custom_color))
            }
        }
        SettingsSwitchRow(
            title = if (visibleInViews) appString(R.string.calendar_visible) else appString(R.string.calendar_hidden),
            checked = visibleInViews,
            onCheckedChange = onVisibleInViewsChanged,
            subtitle = appString(R.string.calendar_sidebar_help),
            warningUnchecked = !visibleInViews,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.active), color = WarmInk, fontSize = 16.sp, lineHeight = 19.sp)
                Text(
                    stringResource(R.string.calendar_active_help),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = {
                    enabled = it
                    onEnabledChanged(it)
                },
            )
        }
        HorizontalDivider(color = WarmLine)
        SettingsMenuRow(
            title = stringResource(R.string.metadata),
            value = if (metadataExpanded) {
                stringResource(R.string.show_less)
            } else {
                metadataRows.firstOrNull()?.let { "${it.first}: ${it.second}" } ?: stringResource(R.string.none)
            },
            leadingIcon = Icons.Default.Tune,
            onClick = { metadataExpanded = !metadataExpanded },
        )
        AnimatedVisibility(
            visible = metadataExpanded,
            enter = expandVertically(animationSpec = tween(MotionMedium, easing = MotionStandard)) + fadeIn(tween(MotionShort)),
            exit = shrinkVertically(animationSpec = tween(MotionMedium, easing = MotionStandardAccelerate)) + fadeOut(tween(MotionShort)),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                metadataRows.forEach { row ->
                    CalendarMetadataRow(label = row.first, value = row.second)
                }
            }
        }
        if (onDelete != null) {
            Button(
                onClick = { confirmDelete = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.error,
                ),
                modifier = Modifier.fillMaxWidth().height(SettingsControlHeight),
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.delete_server_calendar))
            }
        }
        Spacer(Modifier.height(18.dp))
    }
    if (colorPickerOpen) {
        ColorPickerDialog(
            initialColor = effectiveColor,
            onDismiss = { colorPickerOpen = false },
            onSave = {
                customColor = it
                colorPickerOpen = false
            },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_server_calendar)) },
            text = { Text(stringResource(R.string.delete_server_calendar_warning, collection.displayName)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete?.invoke()
                    },
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun CalendarViewMode.localizedLabel(): String = when (this) {
    CalendarViewMode.ThreeDay -> appString(R.string.three_days)
    CalendarViewMode.Day -> appString(R.string.day)
    CalendarViewMode.Month -> appString(R.string.month)
    CalendarViewMode.Agenda -> appString(R.string.agenda)
    CalendarViewMode.Tasks -> appString(R.string.tasks)
}

private fun CalendarViewMode.settingsIcon(): ImageVector = when (this) {
    CalendarViewMode.Agenda -> Icons.Default.ViewAgenda
    CalendarViewMode.Day -> Icons.Default.ViewDay
    CalendarViewMode.ThreeDay -> Icons.Default.ViewWeek
    CalendarViewMode.Month -> Icons.Default.CalendarMonth
    CalendarViewMode.Tasks -> Icons.Default.TaskAlt
}

internal fun Modifier.dashedBorder(color: Color): Modifier = drawWithContent {
    drawContent()
    val stroke = 1.8.dp.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(stroke / 2, stroke / 2),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()),
        style = Stroke(
            width = stroke,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 7.dp.toPx())),
        ),
    )
}

internal fun Modifier.dashedBorder(color: Color, radius: Dp): Modifier = drawWithContent {
    drawContent()
    val stroke = 1.8.dp.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(stroke / 2, stroke / 2),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(radius.toPx(), radius.toPx()),
        style = Stroke(
            width = stroke,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 7.dp.toPx())),
        ),
    )
}

@Composable
private fun DayOfWeek.localizedWeekdayLabel(): String =
    when (this) {
        DayOfWeek.MONDAY -> appString(R.string.week_monday)
        DayOfWeek.TUESDAY -> appString(R.string.week_tuesday)
        DayOfWeek.WEDNESDAY -> appString(R.string.week_wednesday)
        DayOfWeek.THURSDAY -> appString(R.string.week_thursday)
        DayOfWeek.FRIDAY -> appString(R.string.week_friday)
        DayOfWeek.SATURDAY -> appString(R.string.week_saturday)
        DayOfWeek.SUNDAY -> appString(R.string.week_sunday)
    }

internal fun Modifier.horizontalEdgeFade(
    edgeWidth: Dp = 14.dp,
    fadeStart: Boolean = true,
    fadeEnd: Boolean = true,
): Modifier = graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
    drawContent()
    val edge = edgeWidth.toPx().coerceAtMost(size.width / 3f)
    if (fadeStart) {
        drawRect(
            brush = Brush.horizontalGradient(
                0.00f to Color.Transparent,
                1.00f to Color.Black,
                startX = 0f,
                endX = edge,
            ),
            topLeft = Offset.Zero,
            size = Size(edge, size.height),
            blendMode = BlendMode.DstIn,
        )
    }
    if (fadeEnd) {
        drawRect(
            brush = Brush.horizontalGradient(
                0.00f to Color.Black,
                1.00f to Color.Transparent,
                startX = size.width - edge,
                endX = size.width,
            ),
            topLeft = Offset(size.width - edge, 0f),
            size = Size(edge, size.height),
            blendMode = BlendMode.DstIn,
        )
    }
}

internal fun Modifier.horizontalBleed(bleed: Dp): Modifier =
    if (bleed == 0.dp) {
        this
    } else {
        layout { measurable, constraints ->
            val bleedPx = bleed.roundToPx()
            val extraWidth = bleedPx * 2
            val expandedConstraints = constraints.copy(
                minWidth = (constraints.minWidth + extraWidth).coerceAtLeast(0),
                maxWidth = if (constraints.maxWidth == Int.MAX_VALUE) Int.MAX_VALUE else (constraints.maxWidth + extraWidth).coerceAtLeast(0),
            )
            val placeable = measurable.measure(expandedConstraints)
            layout(constraints.maxWidth, placeable.height) {
                placeable.placeRelative(-bleedPx, 0)
            }
        }
    }

private fun Modifier.bottomEdgeFade(color: Color, edgeHeight: Dp = 32.dp): Modifier = graphicsLayer {
    compositingStrategy = CompositingStrategy.Offscreen
}.drawWithContent {
    drawContent()
    val edge = edgeHeight.toPx().coerceAtMost(size.height / 3f)
    drawRect(
        brush = Brush.verticalGradient(
            0f to Color.Black,
            1f to Color.Transparent,
        ),
        topLeft = Offset(0f, size.height - edge),
        size = Size(size.width, edge),
        blendMode = BlendMode.DstIn,
    )
}

internal fun Modifier.editorInset(): Modifier = padding(horizontal = EditorHorizontalPadding)

internal fun Modifier.verticalClipAllowHorizontalOverflow(): Modifier = drawWithContent {
    clipRect(
        left = -size.width * 4f,
        top = 0f,
        right = size.width * 5f,
        bottom = size.height,
    ) {
        this@drawWithContent.drawContent()
    }
}

internal data class LocationAnchor(val latitude: Double, val longitude: Double) {
    val cacheKey: String = "${(latitude * 100).roundToInt()}_${(longitude * 100).roundToInt()}"

    fun nominatimViewboxParam(): String {
        val latDelta = 0.35
        val lonDelta = 0.55
        val left = longitude - lonDelta
        val right = longitude + lonDelta
        val top = latitude + latDelta
        val bottom = latitude - latDelta
        return "&viewbox=$left,$top,$right,$bottom"
    }
}

@SuppressLint("MissingPermission")
internal fun Context.lastKnownLocationAnchor(): LocationAnchor? {
    val manager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    return listOf(
        LocationManager.NETWORK_PROVIDER,
        LocationManager.GPS_PROVIDER,
        LocationManager.PASSIVE_PROVIDER,
    ).mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
        .maxByOrNull { it.time }
        ?.let { LocationAnchor(it.latitude, it.longitude) }
}

internal data class LocationSuggestion(
    val displayName: String,
    val primaryName: String,
    val latitude: Double,
    val longitude: Double,
)

internal data class LocationSelection(
    val value: String,
    val mapVerified: Boolean,
)

internal object LocationLookup {
    private const val USER_AGENT = "KGSCalendar/1.0 Android (com.kgs501.kgscalendar)"

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    private val searchCache = ConcurrentHashMap<String, List<LocationSuggestion>>()
    private val byteCache = ConcurrentHashMap<String, ByteArray>()
    private val mapCache = ConcurrentHashMap<String, Bitmap>()

    suspend fun search(query: String, limit: Int = 8, anchor: LocationAnchor? = null, allowAliases: Boolean = true): List<LocationSuggestion> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 3) return@withContext emptyList()
        val country = Locale.getDefault().country.lowercase(Locale.US).takeIf { it.length == 2 }
        val language = Locale.getDefault().toLanguageTag()
        val key = listOf(trimmed.lowercase(Locale.ROOT), country.orEmpty(), language, limit, anchor?.cacheKey.orEmpty(), allowAliases).joinToString("|")
        searchCache[key]?.let { return@withContext it }
        val countryParam = country?.let { "&countrycodes=$it" }.orEmpty()
        val viewboxParam = anchor?.nominatimViewboxParam().orEmpty()
        val encodedQuery = URLEncoder.encode(trimmed, "UTF-8")
        val url = "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=$limit&addressdetails=1&q=$encodedQuery$countryParam$viewboxParam"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept-Language", language)
            .build()
        val body = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext emptyList()
            response.body?.string().orEmpty()
        }
        val parsed = JSONArray(body).let { array ->
            (0 until array.length()).mapNotNull { index ->
                val obj = array.optJSONObject(index) ?: return@mapNotNull null
                val lat = obj.optString("lat").toDoubleOrNull() ?: return@mapNotNull null
                val lon = obj.optString("lon").toDoubleOrNull() ?: return@mapNotNull null
                val displayName = obj.optString("display_name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val primaryName = obj.optString("name").takeIf { it.isNotBlank() }
                    ?: displayName.substringBefore(',').trim()
                LocationSuggestion(
                    displayName = displayName,
                    primaryName = primaryName,
                    latitude = lat,
                    longitude = lon,
                )
            }
        }.distinctBy { it.displayName }.take(limit)
        searchCache[key] = parsed
        parsed
    }

    suspend fun bytes(url: String): ByteArray = withContext(Dispatchers.IO) {
        byteCache[url]?.let { return@withContext it }
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()
        val bytes = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Map request failed: ${response.code}")
            response.body?.bytes() ?: error("Empty map response")
        }
        byteCache[url] = bytes
        bytes
    }

    suspend fun mapPreviewBitmap(suggestion: LocationSuggestion, width: Int = 640, height: Int = 260, zoom: Int = 15): Bitmap = withContext(Dispatchers.IO) {
        val key = "${suggestion.latitude},${suggestion.longitude},$width,$height,$zoom"
        mapCache[key]?.let { return@withContext it }
        val tileSize = 256
        val tileCount = 1 shl zoom
        val latRad = Math.toRadians(suggestion.latitude)
        val centerTileX = (suggestion.longitude + 180.0) / 360.0 * tileCount
        val centerTileY = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * tileCount
        val centerPixelX = centerTileX * tileSize
        val centerPixelY = centerTileY * tileSize
        val leftPixel = centerPixelX - width / 2.0
        val topPixel = centerPixelY - height / 2.0
        val firstTileX = floor(leftPixel / tileSize).toInt()
        val lastTileX = floor((leftPixel + width) / tileSize).toInt()
        val firstTileY = floor(topPixel / tileSize).toInt()
        val lastTileY = floor((topPixel + height) / tileSize).toInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(bitmap)
        canvas.drawColor(0xFFE9DED8.toInt())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        for (tileX in firstTileX..lastTileX) {
            for (tileY in firstTileY..lastTileY) {
                if (tileY !in 0 until tileCount) continue
                val wrappedTileX = ((tileX % tileCount) + tileCount) % tileCount
                val tileUrl = "https://tile.openstreetmap.org/$zoom/$wrappedTileX/$tileY.png"
                val tileBytes = runCatching { bytes(tileUrl) }.getOrNull() ?: continue
                val tile = BitmapFactory.decodeByteArray(tileBytes, 0, tileBytes.size) ?: continue
                val destLeft = (tileX * tileSize - leftPixel).roundToInt()
                val destTop = (tileY * tileSize - topPixel).roundToInt()
                canvas.drawBitmap(tile, null, Rect(destLeft, destTop, destLeft + tileSize, destTop + tileSize), paint)
            }
        }
        val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFE53935.toInt()
            style = Paint.Style.FILL
        }
        val markerStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }
        val cx = width / 2f
        val cy = height / 2f
        canvas.drawCircle(cx, cy, 16f, markerStroke)
        canvas.drawCircle(cx, cy, 12f, markerPaint)
        canvas.drawCircle(cx, cy, 5f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() })
        mapCache[key] = bitmap
        bitmap
    }
}

internal fun String.shouldAttemptMapPreview(mapVerified: Boolean?): Boolean {
    if (mapVerified == false) return false
    if (mapVerified == true) return cleanCalendarDisplayText().length >= 3
    val cleaned = cleanCalendarDisplayText()
    if (cleaned.length < 8) return false
    val commaCount = cleaned.count { it == ',' }
    val hasAddressSignal = commaCount >= 2 || (commaCount >= 1 && cleaned.any { it.isDigit() })
    return hasAddressSignal && !cleaned.contains('\n')
}

internal fun Context.openMapLocation(suggestion: LocationSuggestion) {
    val latLng = "${suggestion.latitude},${suggestion.longitude}"
    val encodedLabel = Uri.encode(suggestion.primaryName.ifBlank { suggestion.displayName })
    val mapIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:${suggestion.latitude},${suggestion.longitude}?q=$latLng($encodedLabel)"))
    val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openstreetmap.org/?mlat=${suggestion.latitude}&mlon=${suggestion.longitude}#map=16/${suggestion.latitude}/${suggestion.longitude}"))
    runCatching { startActivity(mapIntent) }
        .recoverCatching { startActivity(fallbackIntent) }
}

internal fun Context.openMapLocation(location: String) {
    val cleaned = location.cleanCalendarDisplayText()
    if (cleaned.isBlank()) return
    val encodedQuery = Uri.encode(cleaned)
    val mapIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$encodedQuery"))
    val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openstreetmap.org/search?query=$encodedQuery"))
    runCatching { startActivity(mapIntent) }
        .recoverCatching { startActivity(fallbackIntent) }
}

internal fun TaskEntity.displayProgress(): Int =
    when {
        isCompleted -> 100
        percentComplete != null -> percentComplete.coerceIn(0, 100)
        else -> 0
    }

internal fun taskPriorityIntensity(priority: Int?): Float {
    val value = priority?.coerceIn(1, 9) ?: 9
    return ((9 - value) / 8f).coerceIn(0f, 1f)
}

internal fun EventEntity.displayColor(): Int = manualColor ?: color

internal data class EventCardVisuals(
    val baseColor: Color,
    val background: Color,
    val contentColor: Color,
    val borderColor: Color?,
    val dashedBorder: Boolean,
    val textDecoration: TextDecoration?,
)

internal fun EventEntity.cardVisuals(muted: Boolean = false, darkPalette: Boolean): EventCardVisuals {
    val base = Color(displayColor())
    val tentative = isTentative()
    val cancelled = isCancelled()
    val background = when {
        cancelled -> base.greyedOut(0.58f).copy(alpha = 0.34f)
        muted -> base.greyedOut(0.62f).copy(alpha = 0.72f)
        tentative -> Color.Transparent
        else -> base
    }
    val normalText = when {
        tentative -> if (darkPalette) Color.White else Color(0xFF17202A)
        base.isDark() && !muted && !cancelled -> Color.White
        else -> Color(0xFF1C1A18)
    }
    return EventCardVisuals(
        baseColor = base,
        background = background,
        contentColor = normalText.copy(alpha = if (cancelled) 0.54f else if (muted) 0.64f else 1f),
        borderColor = when {
            tentative -> base.copy(alpha = 0.95f)
            muted -> base.greyedOut(0.7f).copy(alpha = 0.9f)
            cancelled -> base.greyedOut(0.72f).copy(alpha = 0.74f)
            else -> null
        },
        dashedBorder = tentative && !muted && !cancelled,
        textDecoration = if (cancelled) TextDecoration.LineThrough else null,
    )
}

internal fun EventEntity.isTentative(): Boolean =
    status.equals("TENTATIVE", ignoreCase = true)

private fun EventEntity.isCancelled(): Boolean =
    status.equals("CANCELLED", ignoreCase = true)

internal fun Color.greyedOut(amount: Float): Color {
    val gray = (red * 0.299f + green * 0.587f + blue * 0.114f).coerceIn(0f, 1f)
    val mix = amount.coerceIn(0f, 1f)
    return Color(
        red = red + (gray - red) * mix,
        green = green + (gray - green) * mix,
        blue = blue + (gray - blue) * mix,
        alpha = alpha,
    )
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
private fun AppThemeMode.localizedLabel(): String = when (this) {
    AppThemeMode.KgsBlue -> appString(R.string.kgs_blue)
    AppThemeMode.KgsWarm -> appString(R.string.kgs_warm)
    AppThemeMode.KgsFresh -> appString(R.string.kgs_fresh)
    AppThemeMode.SystemDynamic -> appString(R.string.android_colors)
}

@Composable
private fun AppColorMode.localizedLabel(): String = when (this) {
    AppColorMode.Auto -> appString(R.string.auto)
    AppColorMode.Light -> appString(R.string.light)
    AppColorMode.Dark -> appString(R.string.dark)
}

@Composable
private fun WidgetThemeMode.localizedLabel(): String = when (this) {
    WidgetThemeMode.FollowApp -> appString(R.string.follow_app)
    WidgetThemeMode.KgsBlue -> appString(R.string.kgs_blue)
    WidgetThemeMode.KgsWarm -> appString(R.string.kgs_warm)
    WidgetThemeMode.KgsFresh -> appString(R.string.kgs_fresh)
    WidgetThemeMode.SystemDynamic -> appString(R.string.android_colors)
}

@Composable
private fun WidgetColorMode.localizedLabel(): String = when (this) {
    WidgetColorMode.FollowApp -> appString(R.string.follow_app)
    WidgetColorMode.FollowOs -> appString(R.string.follow_os)
    WidgetColorMode.Light -> appString(R.string.light)
    WidgetColorMode.Dark -> appString(R.string.dark)
}

@Composable
private fun WidgetTaskDisplayMode.localizedLabel(): String = when (this) {
    WidgetTaskDisplayMode.Planned -> appString(R.string.planned_tasks)
    WidgetTaskDisplayMode.Unplanned -> appString(R.string.unplanned_tasks)
    WidgetTaskDisplayMode.Today -> appString(R.string.tasks_for_today)
}

@Composable
private fun WidgetTaskCreateMode.localizedLabel(): String = when (this) {
    WidgetTaskCreateMode.Today -> appString(R.string.create_task_for_today)
    WidgetTaskCreateMode.Unplanned -> appString(R.string.create_unplanned_task)
}

@Composable
private fun WidgetTaskSubtaskDefaultMode.localizedLabel(): String = when (this) {
    WidgetTaskSubtaskDefaultMode.FollowApp -> appString(R.string.follow_app)
    WidgetTaskSubtaskDefaultMode.Open -> appString(R.string.subtasks_default_open)
    WidgetTaskSubtaskDefaultMode.Closed -> appString(R.string.subtasks_default_closed)
}

@Composable
private fun AppLanguageMode.localizedLabel(): String = when (this) {
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

internal fun String?.toCategoryTags(): List<String> =
    this
        ?.split(',')
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        ?.distinctBy { it.lowercase(Locale.ROOT) }
        .orEmpty()

internal fun List<String>.toCategoriesCsv(): String =
    map { it.trim() }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase(Locale.ROOT) }
        .joinToString(",")

internal fun CalendarUiState.allKnownCategoryTags(): List<String> =
    buildList {
        events.forEach { addAll(it.categories.toCategoryTags()) }
        datedTasks.forEach { addAll(it.categories.toCategoryTags()) }
        inboxTasks.forEach { addAll(it.categories.toCategoryTags()) }
        scheduledOpenTasks.forEach { addAll(it.categories.toCategoryTags()) }
        completedTasks.forEach { addAll(it.categories.toCategoryTags()) }
    }.distinctBy { it.lowercase(Locale.ROOT) }.sortedBy { it.lowercase(Locale.ROOT) }

internal data class ProblemItem(
    val id: String,
    val title: String,
    val body: String,
    val target: ProblemTarget? = null,
)

internal sealed interface ProblemTarget {
    data class Event(val event: EventEntity) : ProblemTarget
    data class Task(val task: TaskEntity) : ProblemTarget
}

@Composable
private fun CalendarUiState.problemItems(): List<ProblemItem> =
    buildList {
        message
            ?.takeIf { it.isNotBlank() && it.isProblemMessage() }
            ?.let {
                add(ProblemItem("message-$it", stringResource(R.string.app_notice), it))
            }
        (accounts + listOfNotNull(account))
            .distinctBy { it.id }
            .forEach { source ->
                source.syncError?.takeIf { it.isNotBlank() }?.let { error ->
                    add(
                        ProblemItem(
                            id = "source-${source.id}",
                            title = stringResource(R.string.source_named, source.displayName ?: source.username),
                            body = error,
                        ),
                    )
                }
            }
        val eventCandidates = (events + searchResults + problemEvents).distinctBy { it.resourceHref }
        val taskCandidates = (datedTasks + inboxTasks + scheduledOpenTasks + completedTasks + searchTaskResults + problemTasks)
            .distinctBy { it.resourceHref }
        problemResources.forEach { resource ->
            val event = eventCandidates.firstOrNull { it.resourceHref == resource.href || it.uid == resource.uid }
            val task = taskCandidates.firstOrNull { it.resourceHref == resource.href || it.uid == resource.uid }
            val target = when {
                event != null -> ProblemTarget.Event(event)
                task != null -> ProblemTarget.Task(task)
                else -> null
            }
            val typeLabel = when {
                event != null -> stringResource(R.string.event)
                task != null -> stringResource(R.string.task)
                resource.componentType.equals(com.kgs.calendar.domain.model.ComponentType.Event, ignoreCase = true) -> stringResource(R.string.event)
                resource.componentType.equals(com.kgs.calendar.domain.model.ComponentType.Task, ignoreCase = true) -> stringResource(R.string.task)
                else -> stringResource(R.string.item)
            }
            val itemTitle = (event?.title ?: task?.title)
                ?.ifBlank { stringResource(R.string.no_title) }
                ?: stringResource(R.string.unknown_item)
            add(
                ProblemItem(
                    id = "resource-${resource.href}",
                    title = "$typeLabel: $itemTitle",
                    body = resource.syncError.orEmpty(),
                    target = target,
                ),
            )
        }
        pendingMutationItems.forEach { mutation ->
            val event = eventCandidates.firstOrNull { it.resourceHref == mutation.resourceHref }
            val task = taskCandidates.firstOrNull { it.resourceHref == mutation.resourceHref }
            val target = when {
                event != null -> ProblemTarget.Event(event)
                task != null -> ProblemTarget.Task(task)
                else -> null
            }
            val itemTitle = (event?.title ?: task?.title)
                ?.ifBlank { stringResource(R.string.no_title) }
                ?: stringResource(R.string.unknown_item)
            val actionLabel = when (mutation.action) {
                MutationAction.Delete -> stringResource(R.string.delete_waits_sync)
                MutationAction.Put -> stringResource(R.string.change_waits_sync)
                else -> stringResource(R.string.change_waits_sync)
            }
            val typeLabel = when (mutation.componentType) {
                com.kgs.calendar.domain.model.ComponentType.Event -> stringResource(R.string.event)
                com.kgs.calendar.domain.model.ComponentType.Task -> stringResource(R.string.task)
                else -> stringResource(R.string.item)
            }
            add(
                ProblemItem(
                    id = "pending-${mutation.id}",
                    title = "$typeLabel: $itemTitle",
                    body = stringResource(R.string.pending_problem_body, actionLabel),
                    target = target,
                ),
            )
        }
    }.distinctBy { it.id }

private fun String.isProblemMessage(): Boolean {
    val lower = lowercase(Locale.ROOT)
    return listOf("error", "failed", "fehl", "konnte", "unable", "sync", "warn").any { it in lower }
}

private data class SmoothRemovalResult<T>(
    val items: List<T>,
    val exitingResourceHrefs: Set<String>,
)

@Composable
private fun <T> rememberSmoothRemoval(
    items: List<T>,
    itemKey: (T) -> String,
    resourceHref: (T) -> String,
    retainRemoved: (T) -> Boolean,
): SmoothRemovalResult<T> {
    val scope = rememberCoroutineScope()
    val exitingItems = remember { mutableStateMapOf<String, T>() }
    var previousItems by remember { mutableStateOf<Map<String, T>>(emptyMap()) }
    val currentByKey = remember(items) { items.associateBy(itemKey) }
    val removedNow = previousItems
        .filterKeys { it !in currentByKey.keys }
        .filterValues(retainRemoved)
    val exitingSnapshot = (exitingItems + removedNow).filterKeys { it !in currentByKey.keys }

    SideEffect {
        if (removedNow.isNotEmpty()) {
            removedNow.forEach { (key, item) ->
                exitingItems[key] = item
                scope.launch {
                    delay(MotionMedium.toLong())
                    exitingItems.remove(key)
                }
            }
        }
        currentByKey.keys.forEach { exitingItems.remove(it) }
        previousItems = currentByKey
    }

    return SmoothRemovalResult(
        items = (items + exitingSnapshot.values).distinctBy(itemKey),
        exitingResourceHrefs = exitingSnapshot.values.map(resourceHref).toSet(),
    )
}

@Composable
internal fun pendingMutationFor(resourceHref: String): PendingMutationEntity? {
    val mutations = LocalPendingMutations.current
    return remember(mutations, resourceHref) {
        mutations.lastOrNull { it.resourceHref == resourceHref }
    }
}

@Composable
internal fun pendingDeleteAlpha(resourceHref: String): Float {
    val pending = pendingMutationFor(resourceHref)
    val exiting = resourceHref in LocalExitingResourceHrefs.current
    val target = when {
        exiting -> 0f
        pending?.action == MutationAction.Delete -> 0.24f
        else -> 1f
    }
    val alpha by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(MotionMedium, easing = MotionStandard),
        label = "pendingDeleteAlpha",
    )
    return alpha
}

@Composable
internal fun PendingMutationBadge(
    resourceHref: String,
    modifier: Modifier = Modifier,
) {
    val pending = pendingMutationFor(resourceHref)
    var visible by remember(pending?.id, pending?.createdAtMillis) {
        mutableStateOf(
            pending != null &&
                System.currentTimeMillis() - pending.createdAtMillis >= PENDING_BADGE_DELAY_MILLIS,
        )
    }
    LaunchedEffect(pending?.id, pending?.createdAtMillis) {
        val mutation = pending
        if (mutation == null) {
            visible = false
            return@LaunchedEffect
        }
        val remaining = PENDING_BADGE_DELAY_MILLIS -
            (System.currentTimeMillis() - mutation.createdAtMillis)
        if (remaining > 0L) delay(remaining)
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = scaleIn(animationSpec = tween(MotionShort, easing = MotionEmphasized)) + fadeIn(tween(MotionShort)),
        exit = scaleOut(animationSpec = tween(MotionShort, easing = MotionStandardAccelerate)) + fadeOut(tween(MotionShort)),
    ) {
        Box(
            Modifier
                .size(8.dp)
                .shadow(2.dp, CircleShape, clip = false)
                .border(1.dp, Color.White.copy(alpha = 0.78f), CircleShape)
                .clip(CircleShape)
                .background(SyncPendingOrange),
        )
    }
}

internal data class CalendarParticipant(
    val name: String,
    val email: String,
    val partstat: String = "NEEDS-ACTION",
    val role: String = "REQ-PARTICIPANT",
    val rsvp: Boolean = false,
    val scheduleStatus: String? = null,
) {
    val displayName: String = name.ifBlank { email }
}

internal enum class ParticipantRoleOption(val value: String, val label: String) {
    Chair("CHAIR", "Chair"),
    Required("REQ-PARTICIPANT", "Required participant"),
    Optional("OPT-PARTICIPANT", "Optional participant"),
    NonParticipant("NON-PARTICIPANT", "Non-participant"),
}

@Composable
internal fun ParticipantRoleOption.localizedLabel(): String = when (this) {
    ParticipantRoleOption.Chair -> appString(R.string.chair)
    ParticipantRoleOption.Required -> appString(R.string.required_participant)
    ParticipantRoleOption.Optional -> appString(R.string.optional_participant)
    ParticipantRoleOption.NonParticipant -> appString(R.string.non_participant)
}

internal fun List<CollectionEntity>.sortedWithDefaultFirst(defaultHref: String?): List<CollectionEntity> {
    if (defaultHref.isNullOrBlank()) return this
    val default = firstOrNull { it.href == defaultHref } ?: return this
    return listOf(default) + filter { it.href != defaultHref }
}

private fun String.isReadOnlyCollectionHrefUi(): Boolean = startsWith(UiReadOnlyCollectionPrefix)

internal fun CollectionEntity.isReadOnlyForUi(): Boolean = readOnly || href.isReadOnlyCollectionHrefUi()

private fun CollectionEntity.canDeleteFromServerForUi(): Boolean =
    sourceType == SourceType.CalDav &&
        !readOnly &&
        capabilitiesJson.toJsonObjectOrNull()?.optBooleanOrNull("canDeleteResources") != false

internal fun String.isLocalCollectionHrefUi(): Boolean = startsWith(UiLocalCollectionPrefix)

internal fun CollectionEntity.isAndroidProviderForUi(): Boolean =
    sourceType == SourceType.AndroidProvider || href.startsWith(UiAndroidCollectionPrefix)

internal fun AccountEntity.isAndroidProviderForUi(): Boolean =
    sourceType == SourceType.AndroidProvider || id == UiAndroidAccountId

internal data class EventEditorCapabilities(
    val recurrence: Boolean,
    val reminders: Boolean,
    val location: Boolean,
    val notes: Boolean,
    val status: Boolean,
    val categories: Boolean,
    val color: Boolean,
    val participants: Boolean,
) {
    fun allows(field: String): Boolean =
        when (field) {
            "recurrence" -> recurrence
            "reminders" -> reminders
            "location" -> location
            "notes" -> notes
            "status" -> status
            "categories" -> categories
            "color" -> color
            "participants" -> participants
            else -> true
        }

    companion object {
        val Full = EventEditorCapabilities(
            recurrence = true,
            reminders = true,
            location = true,
            notes = true,
            status = true,
            categories = true,
            color = true,
            participants = true,
        )
    }
}

@Composable
private fun CalendarMetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            label,
            color = WarmInk,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(118.dp),
        )
        Text(
            value,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CollectionEntity.localizedPermissionMetadataRows(): List<Pair<String, String>> {
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
private fun Boolean?.localizedSupportedUnsupported(defaultSupported: Boolean? = null): String =
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

private fun String?.toJsonObjectOrNull(): JSONObject? =
    takeIf { !it.isNullOrBlank() }?.let { json ->
        runCatching { JSONObject(json) }.getOrNull()
    }

private fun JSONObject.optBooleanOrNull(name: String): Boolean? =
    if (has(name) && !isNull(name)) optBoolean(name) else null

private fun JSONObject.optIntOrNull(name: String): Int? =
    if (has(name) && !isNull(name)) optInt(name) else null

private fun JSONObject.optStringOrNull(name: String): String? =
    if (has(name) && !isNull(name)) optString(name) else null

internal fun CollectionEntity.eventEditorCapabilities(): EventEditorCapabilities =
    if (!isAndroidProviderForUi()) {
        EventEditorCapabilities.Full
    } else {
        EventEditorCapabilities(
            recurrence = true,
            reminders = capabilitiesJson?.contains("\"reminders\":false", ignoreCase = true) != true,
            location = true,
            notes = true,
            status = false,
            categories = false,
            color = true,
            participants = false,
        )
    }

private fun CollectionEntity.kindLabel(): String = buildList {
    if (supportsEvents) add("Events")
    if (supportsTasks) add("Tasks")
}.joinToString(" & ").ifBlank { "Calendar" }

@Composable
private fun CollectionEntity.localizedKindLabel(): String = buildList {
    if (supportsEvents) add(stringResource(R.string.events))
    if (supportsTasks) add(stringResource(R.string.tasks))
}.joinToString(" & ").ifBlank { stringResource(R.string.calendar) }

private fun CollectionEntity.isVisibleInSettingsCalendarList(): Boolean =
    !(href.isLocalCollectionHrefUi() && !isEnabled)

internal fun TaskEntity.displayColor(mode: TaskColorMode): Int =
    manualColor ?: when (mode) {
        TaskColorMode.Collection -> color
        TaskColorMode.Priority -> priority?.let { priorityColor(it).toArgb() } ?: color
    }

internal fun String.cleanCalendarDisplayText(): String {
    var normalized = trim().trimHtmlDataPrefixForDisplay()
    if (normalized.contains(Regex("%[0-9A-Fa-f]{2}"))) {
        normalized = runCatching { Uri.decode(normalized) }.getOrDefault(normalized).trimHtmlDataPrefixForDisplay()
    }
    if ('<' in normalized && '>' in normalized) {
        normalized = normalized
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</p\\s*>"), "\n")
            .replace(Regex("(?s)<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
    }
    return normalized.trim()
}

private fun String.locationDisplayName(): String =
    cleanCalendarDisplayText()
        .substringBefore(',')
        .trim()

internal fun String.cardLocationText(mapVerified: Boolean?): String {
    val cleaned = cleanCalendarDisplayText()
    if (cleaned.isBlank()) return ""
    return if (mapVerified == true) cleaned.substringBefore(',').trim() else cleaned
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun AppLanguageMode.resolveLocale(context: Context): Locale =
    localeTag?.let(Locale::forLanguageTag) ?: context.currentConfigurationLocale()

internal fun Context.withAppLocale(locale: Locale): Context {
    val config = Configuration(resources.configuration)
    config.setLocale(locale)
    val localizedContext = createConfigurationContext(config)
    return object : ContextWrapper(this) {
        override fun getAssets(): AssetManager = localizedContext.assets
        override fun getResources(): Resources = localizedContext.resources
    }
}

private fun Context.currentConfigurationLocale(): Locale =
    resources.configuration.locales[0] ?: Locale.getDefault()

private fun String.trimHtmlDataPrefixForDisplay(): String {
    val normalized = trim()
    return when {
        normalized.startsWith("data:text/html", ignoreCase = true) && "," in normalized -> normalized.substringAfter(',')
        normalized.startsWith("text/html,", ignoreCase = true) -> normalized.substringAfter(',')
        else -> normalized
    }
}

@Composable
internal fun CalendarParticipant.localizedDeliveryStatusLabel(): String? {
    val code = scheduleStatus
        ?.substringBefore(',')
        ?.substringBefore(';')
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: return null
    return when {
        code == "1.0" -> appString(R.string.invitation_preparing)
        code == "1.2" || code == "2.0" -> null
        code.startsWith("1.") -> appString(R.string.invitation_sent)
        code.startsWith("3.") || code.startsWith("5.") -> appString(R.string.delivery_failed)
        else -> null
    }
}

@Composable
internal fun String.toLocalizedRecurrenceLabel(): String {
    val rule = trim()
    if (rule.isBlank()) return appString(R.string.one_time)
    val freqLabel = when (rule.recurrenceFrequency()) {
        "DAILY" -> appString(R.string.daily)
        "WEEKLY" -> appString(R.string.weekly)
        "MONTHLY" -> appString(R.string.monthly)
        "YEARLY" -> appString(R.string.yearly)
        else -> appString(R.string.custom_recurrence)
    }
    val dayPart = rule.recurrencePart("BYDAY") ?: rule.recurrencePart("DAY")
    val dayLabel = dayPart?.split(',')?.map { it.toLocalizedWeekdayLabel() }?.joinToString(", ")
    val interval = rule.recurrencePart("INTERVAL")?.toIntOrNull()?.takeIf { it > 1 }?.let { "${appString(R.string.every)} $it" }
    val count = rule.recurrencePart("COUNT")?.let { appString(R.string.times_count, it) }
    val until = rule.recurrencePart("UNTIL")?.toIsoUntilDate()
    return listOfNotNull(
        freqLabel,
        interval,
        dayLabel?.let { appString(R.string.on_weekday, it) },
        count,
        until?.let { appString(R.string.until, it) },
    ).joinToString(" ")
}

@Composable
internal fun String?.localizedReminderSummary(): String? {
    val minutes = this?.split(',')?.mapNotNull { it.trim().toIntOrNull() }?.normalizedReminderOffsets().orEmpty()
    if (minutes.isEmpty()) return null
    return minutes.map { reminderMinuteLabel(it) }.joinToString(", ")
}

@Composable
private fun String.toLocalizedWeekdayLabel(): String = when (uppercase(Locale.US)) {
    "MO" -> appString(R.string.week_monday_short)
    "TU" -> appString(R.string.week_tuesday_short)
    "WE" -> appString(R.string.week_wednesday_short)
    "TH" -> appString(R.string.week_thursday_short)
    "FR" -> appString(R.string.week_friday_short)
    "SA" -> appString(R.string.week_saturday_short)
    "SU" -> appString(R.string.week_sunday_short)
    else -> this
}

private fun CollectionEntity.typeLabel(): String = when {
    supportsEvents && supportsTasks -> "Events and tasks"
    supportsTasks -> "Tasks"
    else -> "Events"
}

internal fun continuationShape(continuesFromPrevious: Boolean, continuesToNext: Boolean): RoundedCornerShape =
    RoundedCornerShape(
        topStart = if (continuesFromPrevious) 0.dp else 8.dp,
        bottomStart = if (continuesFromPrevious) 0.dp else 8.dp,
        topEnd = if (continuesToNext) 0.dp else 8.dp,
        bottomEnd = if (continuesToNext) 0.dp else 8.dp,
    )

internal fun Modifier.drawContinuationBridge(
    color: Color,
    continuesFromPrevious: Boolean,
    continuesToNext: Boolean,
    bridgeWidth: Dp = DayColumnSpacing,
): Modifier = drawBehind {
    val bridge = bridgeWidth.toPx()
    if (continuesFromPrevious) {
        drawRect(
            color = color,
            topLeft = Offset(-bridge, 0f),
            size = Size(bridge, size.height),
        )
    }
    if (continuesToNext) {
        drawRect(
            color = color,
            topLeft = Offset(size.width, 0f),
            size = Size(bridge, size.height),
        )
    }
}

private fun Int.toHexText(): String =
    "#%06X".format(this and 0x00FFFFFF)

private fun String.parseHexColorOrNull(): Int? {
    val normalized = trim().removePrefix("#")
    if (normalized.length != 6 || normalized.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) return null
    return (0xFF000000.toInt() or normalized.toInt(16))
}

private fun LocalTime.nextDraftStart(): LocalTime {
    val nextHourMinute = ((hour + 1).coerceAtMost(23)) * 60
    return nextHourMinute.toDraftLocalTime()
}

private fun LocalTime.defaultDraftEnd(durationMinutes: Int = 60): LocalTime =
    (minuteOfDay() + durationMinutes.coerceIn(15, 24 * 60)).coerceAtMost((DayEndHour + 1) * 60 - 1).toDraftLocalTime()

internal fun LocalTime.minuteOfDay(): Int = hour * 60 + minute

internal fun LocalDate.startOfDayMillis(): Long =
    atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

internal fun eventDateTimeRangeInvalid(
    startDateText: String,
    endDateText: String,
    startTimeText: String,
    endTimeText: String,
    allDay: Boolean,
): Boolean {
    val startDate = runCatching { LocalDate.parse(startDateText) }.getOrNull() ?: return false
    val endDate = runCatching { LocalDate.parse(endDateText) }.getOrNull() ?: return false
    if (allDay) return endDate.isBefore(startDate)
    val startTime = runCatching { LocalTime.parse(startTimeText) }.getOrNull() ?: return false
    val endTime = runCatching { LocalTime.parse(endTimeText) }.getOrNull() ?: return false
    return !endDate.atTime(endTime).isAfter(startDate.atTime(startTime))
}

internal fun taskDateTimeRangeInvalid(
    hasStartDate: Boolean,
    startDateText: String,
    hasStartTime: Boolean,
    startTimeText: String,
    hasEndDate: Boolean,
    endDateText: String,
    hasEndTime: Boolean,
    endTimeText: String,
    allDay: Boolean,
): Boolean {
    if (!hasStartDate || !hasEndDate) return false
    val startDate = runCatching { LocalDate.parse(startDateText) }.getOrNull() ?: return false
    val endDate = runCatching { LocalDate.parse(endDateText) }.getOrNull() ?: return false
    if (allDay || (!hasStartTime && !hasEndTime)) return endDate.isBefore(startDate)
    val startTime = if (hasStartTime) {
        runCatching { LocalTime.parse(startTimeText) }.getOrNull() ?: return false
    } else {
        LocalTime.MIDNIGHT
    }
    val endTime = if (hasEndTime) {
        runCatching { LocalTime.parse(endTimeText) }.getOrNull() ?: return false
    } else {
        LocalTime.MAX
    }
    return !endDate.atTime(endTime).isAfter(startDate.atTime(startTime))
}

internal fun Int.toDraftLocalTime(): LocalTime {
    val minute = coerceIn(DayStartHour * 60, (DayEndHour + 1) * 60 - 1)
    return LocalTime.of(minute / 60, minute % 60)
}

internal fun Int.snapDraftMinute(): Int =
    ((this + DraftSnapMinutes / 2) / DraftSnapMinutes) * DraftSnapMinutes

internal fun EditorSchedulePreview.withDraggedMinutes(
    mode: DraftDragMode,
    originalStartMinute: Int,
    originalEndMinute: Int,
    deltaMinutes: Int,
): EditorSchedulePreview {
    val minMinute = DayStartHour * 60
    val maxMinute = (DayEndHour + 1) * 60 - 1
    val originalStart = originalStartMinute.coerceIn(minMinute, maxMinute - DraftMinDurationMinutes)
    val originalEnd = originalEndMinute.coerceIn(originalStart + DraftMinDurationMinutes, maxMinute)
    val duration = originalEnd - originalStart

    val (startMinute, endMinute) = when (mode) {
        DraftDragMode.Move -> {
            val nextStart = (originalStart + deltaMinutes)
                .snapDraftMinute()
                .coerceIn(minMinute, maxMinute - duration)
            nextStart to nextStart + duration
        }
        DraftDragMode.Start -> {
            val nextStart = (originalStart + deltaMinutes)
                .snapDraftMinute()
                .coerceIn(minMinute, originalEnd - DraftMinDurationMinutes)
            nextStart to originalEnd
        }
        DraftDragMode.End -> {
            val nextEnd = (originalEnd + deltaMinutes)
                .snapDraftMinute()
                .coerceIn(originalStart + DraftMinDurationMinutes, maxMinute)
            originalStart to nextEnd
        }
    }

    return copy(start = startMinute.toDraftLocalTime(), end = endMinute.toDraftLocalTime())
}

internal fun List<LocalDate>.allDayAreaHeight(
    events: List<EventEntity>,
    tasks: List<TaskEntity>,
    maxVisibleItems: Int,
    expanded: Boolean,
    draftDate: LocalDate?,
): Dp {
    val visibleStartPage = minOfOrNull { it.toDayPage() } ?: return 22.dp
    val visibleEndPage = maxOfOrNull { it.toDayPage() } ?: visibleStartPage
    val overlayItems = buildAllDayOverlayItems(events, tasks, TaskColorMode.Collection, visibleStartPage, visibleEndPage)
    val rows = overlayItems.maxOfOrNull { it.lane + 1 } ?: 0
    val displayedRows = when {
        expanded -> rows
        else -> (visibleStartPage..visibleEndPage).maxOfOrNull { page ->
            val pageItems = overlayItems.filter { page in it.startPage..it.endPage }
            when {
                pageItems.isEmpty() -> 0
                maxVisibleItems <= 0 -> 1
                pageItems.size > maxVisibleItems -> maxVisibleItems
                else -> pageItems.size
            }
        } ?: 0
    }
    val draftPage = draftDate
        ?.takeIf { date -> any { it == date } }
        ?.toDayPage()
    val draftRows = when {
        draftPage == null -> 0
        expanded -> firstFreeLaneIndex(
            overlayItems
                .filter { draftPage in it.startPage..it.endPage }
                .map { it.lane }
                .toSet(),
        ) + 1
        maxVisibleItems <= 0 -> 0
        else -> {
            val pageItems = overlayItems.filter { draftPage in it.startPage..it.endPage }
            val visibleDraftSlots = if (pageItems.size > maxVisibleItems) {
                (maxVisibleItems - 1).coerceAtLeast(0)
            } else {
                maxVisibleItems
            }
            if (pageItems.size < visibleDraftSlots) pageItems.size + 1 else 0
        }
    }
    val displayedRowsWithDraft = max(displayedRows, draftRows)
    return if (displayedRowsWithDraft == 0) 22.dp else (displayedRowsWithDraft * 24 + (displayedRowsWithDraft - 1) * 5 + 15).dp
}

internal fun firstFreeLaneIndex(occupiedLanes: Set<Int>): Int {
    var lane = 0
    while (lane in occupiedLanes) lane++
    return lane
}

internal fun List<LocalDate>.hasAllDayOverflow(
    events: List<EventEntity>,
    tasks: List<TaskEntity>,
    maxVisibleItems: Int,
): Boolean {
    val visibleStartPage = minOfOrNull { it.toDayPage() } ?: return false
    val visibleEndPage = maxOfOrNull { it.toDayPage() } ?: visibleStartPage
    val overlayItems = buildAllDayOverlayItems(events, tasks, TaskColorMode.Collection, visibleStartPage, visibleEndPage)
    return (visibleStartPage..visibleEndPage).any { page ->
        val pageItems = overlayItems.count { page in it.startPage..it.endPage }
        if (maxVisibleItems <= 0) pageItems > 0 else pageItems > maxVisibleItems
    }
}

internal fun List<EventEntity>.indexEventsByDay(): Map<LocalDate, List<EventEntity>> {
    val result = linkedMapOf<LocalDate, MutableList<EventEntity>>()
    forEach { event ->
        val start = event.startsAtMillis.toDate()
        val end = (event.endsAtMillis - 1).toDate()
        var date = start
        var guard = 0
        while (!date.isAfter(end) && guard < 370) {
            result.getOrPut(date) { mutableListOf() } += event
            date = date.plusDays(1)
            guard++
        }
    }
    return result
}

internal fun EventEntity.monthOccurrenceKey(): String =
    "${resourceHref ?: uid}:${startsAtMillis}"

private fun EventEntity.smoothRemovalKey(): String =
    "${resourceHref}:${startsAtMillis}"

private fun TaskEntity.smoothRemovalKey(): String =
    "${resourceHref}:${startAtMillis ?: dueAtMillis ?: completedAtMillis ?: 0L}"

internal fun List<TaskEntity>.indexTasksByDay(): Map<LocalDate, List<TaskEntity>> =
    flatMap { task -> task.visibleDates().map { it to task } }
        .groupBy({ it.first }, { it.second })

private fun CalendarUiState.defaultFabCreationDate(today: LocalDate = LocalDate.now()): LocalDate =
    when (selectedView) {
        CalendarViewMode.Day -> selectedDate
        CalendarViewMode.ThreeDay -> {
            val endExclusive = selectedDate.plusDays(multiDayCount.coerceMultiDayCount().toLong())
            if (!today.isBefore(selectedDate) && today.isBefore(endExclusive)) today else selectedDate
        }
        CalendarViewMode.Month,
        CalendarViewMode.Agenda,
        CalendarViewMode.Tasks,
        -> today
    }

internal fun monthMarkersFor(
    month: YearMonth,
    events: List<EventEntity>,
    tasks: List<TaskEntity>,
    taskColorMode: TaskColorMode,
): Map<LocalDate, MonthDayMarkers> {
    val first = month.atDay(1)
    val last = month.atEndOfMonth()
    val colorsByDay = linkedMapOf<LocalDate, MutableList<Int>>()

    fun addMarker(day: LocalDate, color: Int) {
        if (day in first..last) {
            colorsByDay.getOrPut(day) { mutableListOf() } += color
        }
    }

    events.forEach { event ->
        val start = event.startsAtMillis.toDate().coerceAtLeast(first)
        val end = (event.endsAtMillis - 1).toDate().coerceAtMost(last)
        var day = start
        var guard = 0
        while (!day.isAfter(end) && guard < 32) {
            addMarker(day, event.displayColor())
            day = day.plusDays(1)
            guard++
        }
    }
    tasks.forEach { task ->
        task.visibleDates()
            .filter { it in first..last }
            .forEach { addMarker(it, task.displayColor(taskColorMode)) }
    }

    return colorsByDay.mapValues { (_, colors) ->
        MonthDayMarkers(colors = colors.take(3), hasMore = colors.size > 3)
    }
}

private fun String.toDisplayDate(): String = runCatching {
    LocalDate.parse(this).format(DateTimeFormatter.ofPattern("EEE, d. MMM yyyy", Locale.getDefault()))
}.getOrDefault(this)

internal enum class SheetSnap {
    Expanded,
    Half,
    Quarter,
    EditorSmall,
    EditorTiny,
}

internal enum class SheetAnchorMode {
    ContentFit,
    Editor,
}

internal data class DraftEventSelection(
    val date: LocalDate,
    val start: LocalTime,
    val end: LocalTime,
    val color: Int = DraftAccent.toArgb(),
    val allDay: Boolean = false,
)

internal data class MonthDayMarkers(
    val colors: List<Int>,
    val hasMore: Boolean,
)

internal enum class DraftDragMode {
    Move,
    Start,
    End,
}

@Composable
internal fun RecurrenceOption.localizedLabel(): String = when (this) {
    RecurrenceOption.Once -> appString(R.string.one_time)
    RecurrenceOption.Daily -> appString(R.string.daily)
    RecurrenceOption.Weekly -> appString(R.string.weekly)
    RecurrenceOption.Monthly -> appString(R.string.monthly)
    RecurrenceOption.Yearly -> appString(R.string.yearly)
    RecurrenceOption.Custom -> appString(R.string.custom_recurrence)
}

@Composable
internal fun RecurrenceOption.localizedIntervalUnitLabel(): String = when (this) {
    RecurrenceOption.Daily -> appString(R.string.days)
    RecurrenceOption.Weekly -> appString(R.string.weeks)
    RecurrenceOption.Monthly -> appString(R.string.months)
    RecurrenceOption.Yearly -> appString(R.string.years)
    else -> appString(R.string.intervals)
}

internal data class EditorTransferDraft(
    val title: String = "",
    val notes: String = "",
    val location: String = "",
    val locationMapVerified: Boolean? = null,
    val manualColor: Int? = null,
    val categories: String = "",
    val recurrenceRule: String = "",
    val reminderMinutes: Set<Int> = emptySet(),
    val sourceDefaultReminderMinutes: Set<Int> = emptySet(),
    val date: LocalDate? = null,
    val endDate: LocalDate? = null,
    val startTime: LocalTime? = null,
    val endTime: LocalTime? = null,
    val allDay: Boolean? = null,
    val schedule: EditorScheduleState? = null,
)

internal fun EditorTransferDraft.withDestinationReminderDefaults(destinationDefaultReminderMinutes: Set<Int>): EditorTransferDraft {
    val sourceDefaults = sourceDefaultReminderMinutes.normalizedReminderOffsets().toSet()
    val customReminders = reminderMinutes
        .normalizedReminderOffsets()
        .filterNot { it in sourceDefaults }
        .toSet()
    val destinationDefaults = destinationDefaultReminderMinutes.normalizedReminderOffsets().toSet()
    return copy(
        reminderMinutes = (customReminders + destinationDefaults)
            .normalizedReminderOffsets()
            .toSet(),
        sourceDefaultReminderMinutes = destinationDefaults,
    )
}

internal fun buildAllDayOverlayItems(
    events: List<EventEntity>,
    tasks: List<TaskEntity>,
    taskColorMode: TaskColorMode,
    visibleStartPage: Int,
    visibleEndPage: Int,
    priorityStartPage: Int = visibleStartPage,
    priorityEndPage: Int = visibleEndPage,
): List<AllDayOverlayItem> {
    data class Candidate(
        val id: String,
        val title: String,
        val color: Int,
        val startPage: Int,
        val endPage: Int,
        val event: EventEntity?,
        val task: TaskEntity?,
        val completed: Boolean,
    )
    data class AssignedCandidate(
        val candidate: Candidate,
        var lane: Int,
    )

    val candidates = buildList {
        events.forEach { event ->
            val topStart = event.allDayTopStartDate() ?: return@forEach
            val topEnd = event.allDayTopEndDate() ?: return@forEach
            val startPage = topStart.toDayPage()
            val endPage = topEnd.toDayPage()
            if (endPage >= visibleStartPage && startPage <= visibleEndPage) {
                add(
                    Candidate(
                        id = "event:${event.uid}:${event.startsAtMillis}",
                        title = event.title,
                        color = event.displayColor(),
                        startPage = startPage,
                        endPage = endPage,
                        event = event,
                        task = null,
                        completed = false,
                    ),
                )
            }
        }
        tasks.filter { it.startAtMillis != null || it.dueAtMillis != null }
            .filterNot { it.startHasTime || it.dueHasTime }
            .forEach { task ->
                val dates = task.visibleDates()
                val startPage = dates.firstOrNull()?.toDayPage() ?: return@forEach
                val endPage = dates.lastOrNull()?.toDayPage() ?: startPage
                if (endPage >= visibleStartPage && startPage <= visibleEndPage) {
                    add(
                        Candidate(
                            id = "task:${task.uid}",
                            title = task.title,
                            color = task.displayColor(taskColorMode),
                            startPage = startPage,
                            endPage = endPage,
                            event = null,
                            task = task,
                            completed = task.isCompleted,
                        ),
                    )
                }
            }
    }.sortedWith(
        compareBy<Candidate> { allDayViewportPriorityTier(it.startPage, it.endPage, priorityStartPage, priorityEndPage) }
            .thenByDescending { it.endPage - it.startPage }
            .thenBy { it.startPage }
            .thenBy { it.title },
    )

    val laneEnds = mutableListOf<Int>()
    val assigned = candidates.map { candidate ->
        val lane = laneEnds.indexOfFirst { it < candidate.startPage }.let { index ->
            if (index >= 0) index else laneEnds.size.also { laneEnds.add(Int.MIN_VALUE) }
        }
        laneEnds[lane] = candidate.endPage
        AssignedCandidate(candidate, lane)
    }.toMutableList()

    return assigned.map { assignment ->
        val candidate = assignment.candidate
        AllDayOverlayItem(
            id = candidate.id,
            title = candidate.title,
            color = candidate.color,
            startPage = candidate.startPage,
            endPage = candidate.endPage,
            lane = assignment.lane,
            event = candidate.event,
            task = candidate.task,
            completed = candidate.completed,
        )
    }
}

@Composable
internal fun EventEntity.localizedTimeLabel(): String {
    if (allDay) return stringResource(R.string.all_day)
    return "${startsAtMillis.toTimeText()} - ${endsAtMillis.toTimeText()}"
}

@Composable
internal fun EventEntity.localizedAgendaSpanLabel(startDate: LocalDate, endDate: LocalDate): String {
    val dateFormatter = DateTimeFormatter.ofPattern("d. MMM yyyy", LocalAppLocale.current)
    val allDayLabel = appString(R.string.all_day)
    return if (allDay) {
        "${startDate.format(dateFormatter)} - ${endDate.format(dateFormatter)}, $allDayLabel"
    } else {
        "${startDate.format(dateFormatter)} - ${endDate.format(dateFormatter)}"
    }
}

@Composable
internal fun EventEntity.localizedFullTimeLabel(): String {
    val dateFormatter = DateTimeFormatter.ofPattern("EEE, d. MMM yyyy", LocalAppLocale.current)
    val allDayLabel = stringResource(R.string.all_day)
    val startDate = startsAtMillis.toDate()
    val endDate = (endsAtMillis - 1).toDate()
    return when {
        allDay && startDate == endDate -> "${startDate.format(dateFormatter)}, $allDayLabel"
        allDay -> "${startDate.format(dateFormatter)} - ${endDate.format(dateFormatter)}, $allDayLabel"
        startDate == endsAtMillis.toDate() -> "${startDate.format(dateFormatter)}, ${startsAtMillis.toTimeText()}-${endsAtMillis.toTimeText()}"
        else -> "${startDate.format(dateFormatter)}, ${startsAtMillis.toTimeText()} - ${endsAtMillis.toDate().format(dateFormatter)}, ${endsAtMillis.toTimeText()}"
    }
}

@Composable
internal fun TaskEntity.localizedTaskTimeLabel(): String {
    val startDate = startAtMillis?.toDate()
    val dueDate = dueAtMillis?.toDate()
    val date = dueDate ?: startDate ?: return stringResource(R.string.inbox)
    val locale = LocalAppLocale.current
    val dateText = date.format(DateTimeFormatter.ofPattern("EEE, d. MMM", locale))
    val startTimed = startAtMillis?.takeIf { startHasTime }
    val dueTimed = dueAtMillis?.takeIf { dueHasTime }
    return when {
        startTimed != null && dueTimed != null && startDate == dueDate -> "$dateText, ${startTimed.toTimeText()}-${dueTimed.toTimeText()}"
        startTimed != null && dueTimed != null -> "${startTimed.toDate().format(DateTimeFormatter.ofPattern("d. MMM", locale))} ${startTimed.toTimeText()} - ${dueTimed.toDate().format(DateTimeFormatter.ofPattern("d. MMM", locale))} ${dueTimed.toTimeText()}"
        startTimed != null -> "$dateText, ${stringResource(R.string.from_time, startTimed.toTimeText())}"
        dueTimed != null -> "$dateText, ${stringResource(R.string.until_time, dueTimed.toTimeText())}"
        else -> "$dateText, ${stringResource(R.string.all_day)}"
    }
}

internal fun Color.isDark(): Boolean {
    val luminance = 0.299f * red + 0.587f * green + 0.114f * blue
    return luminance < 0.55f
}

internal fun Color.blendWith(target: Color, amount: Float): Color {
    val t = amount.coerceIn(0f, 1f)
    return Color(
        red = red + (target.red - red) * t,
        green = green + (target.green - green) * t,
        blue = blue + (target.blue - blue) * t,
        alpha = alpha + (target.alpha - alpha) * t,
    )
}
