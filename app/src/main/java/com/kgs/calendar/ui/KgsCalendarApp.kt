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
private val LocalTaskHierarchyExitProgress = compositionLocalOf { 0f }

@Composable
private fun SecurePasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(16.dp),
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = SecurePasswordKeyboardOptions,
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = stringResource(if (visible) R.string.hide_password else R.string.show_password),
                )
            }
        },
        shape = shape,
        modifier = modifier,
    )
}

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
private fun KgsLogoBurstButton() {
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
private fun CalendarDrawer(
    visible: Boolean,
    state: CalendarUiState,
    onDismiss: () -> Unit,
    onViewSelected: (CalendarViewMode) -> Unit,
    onSync: () -> Unit,
    onCollectionVisibleInViews: (String, Boolean) -> Unit,
    onCollectionSettings: (CollectionEntity) -> Unit,
    onAppSettings: () -> Unit,
    problems: List<ProblemItem>,
    onProblems: () -> Unit,
) {
    val drawerProgress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(if (visible) 135 else 115, easing = if (visible) MotionStandard else MotionStandardAccelerate),
        label = "drawerProgress",
    )
    val quietInteraction = remember { MutableInteractionSource() }
    val density = LocalDensity.current
    val drawerWidth = 292.dp
    val drawerWidthPx = with(density) { drawerWidth.toPx() }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    val effectiveProgress = (drawerProgress + dragOffsetPx / drawerWidthPx).coerceIn(0f, 1f)
    LaunchedEffect(visible) {
        if (visible) {
            dragOffsetPx = 0f
        } else {
            delay(150)
            dragOffsetPx = 0f
        }
    }
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(Modifier.fillMaxSize()) {
        if (effectiveProgress > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(effectiveProgress)
                    .background(Color.Black.copy(alpha = 0.58f))
                    .clickable(
                        interactionSource = quietInteraction,
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
        }
        Surface(
            modifier = Modifier
                .width(drawerWidth)
                .fillMaxHeight()
                .offset {
                    IntOffset(((-drawerWidthPx) * (1f - effectiveProgress)).roundToInt(), 0)
                }
                .pointerInput(visible, drawerWidthPx) {
                    detectHorizontalDragGestures(
                        onDragCancel = { dragOffsetPx = 0f },
                        onDragEnd = {
                            if (dragOffsetPx <= -drawerWidthPx * 0.28f) {
                                onDismiss()
                            } else {
                                dragOffsetPx = 0f
                            }
                        },
                    ) { change, dragAmount ->
                        if (dragAmount < 0f || dragOffsetPx < 0f) {
                            change.consume()
                            dragOffsetPx = (dragOffsetPx + dragAmount).coerceIn(-drawerWidthPx, 0f)
                        }
                    }
                },
            shape = RoundedCornerShape(topEnd = 18.dp, bottomEnd = 18.dp),
            color = MaterialTheme.colorScheme.background,
            shadowElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = statusTop + 22.dp, bottom = navBottom + 14.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    KgsLogoBurstButton()
                    Text(
                        "KGS Calendar",
                        fontSize = 22.sp,
                        lineHeight = 25.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.SansSerif,
                        color = WarmInk,
                    )
                }
                DrawerViewItem(stringResource(R.string.agenda), Icons.Default.ViewAgenda, CalendarViewMode.Agenda, state.selectedView, onViewSelected)
                DrawerViewItem(stringResource(R.string.day), Icons.Default.ViewDay, CalendarViewMode.Day, state.selectedView, onViewSelected)
                DrawerViewItem(stringResource(R.string.three_days), Icons.Default.ViewWeek, CalendarViewMode.ThreeDay, state.selectedView, onViewSelected)
                DrawerViewItem(stringResource(R.string.month), Icons.Default.CalendarMonth, CalendarViewMode.Month, state.selectedView, onViewSelected)
                HorizontalDivider(color = WarmLine, modifier = Modifier.padding(top = 10.dp))
                DrawerActionRow(stringResource(R.string.refresh), Icons.Default.Refresh, onSync)
                HorizontalDivider(color = WarmLine)
                val accounts = state.accounts.ifEmpty { state.account?.let(::listOf).orEmpty() }
                val drawerCollections = state.collections.filter { it.isEnabled }
                val collectionsByAccount = drawerCollections.groupBy { it.accountId }
                val visibleAccounts = accounts.filter { account ->
                    collectionsByAccount[account.id].orEmpty().isNotEmpty()
                }
                visibleAccounts.forEachIndexed { index, account ->
                    if (index > 0) {
                        HorizontalDivider(color = WarmLine.copy(alpha = 0.62f), modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp))
                    }
                    DrawerSourceSection(
                        account = account,
                        collections = collectionsByAccount[account.id].orEmpty(),
                        hiddenCollectionHrefs = state.hiddenCollectionHrefs,
                        onCollectionVisibleInViews = onCollectionVisibleInViews,
                        onCollectionSettings = onCollectionSettings,
                    )
                }
                HorizontalDivider(color = WarmLine, modifier = Modifier.padding(top = 12.dp))
                DrawerActionRow(stringResource(R.string.app_settings), Icons.Default.Settings, onAppSettings)
                if (problems.isNotEmpty()) {
                    DrawerProblemRow(
                        count = problems.size,
                        onClick = onProblems,
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerProblemRow(count: Int, onClick: () -> Unit) {
    val color = SyncPendingOrange
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(color.copy(alpha = if (MaterialTheme.colorScheme.background.isDark()) 0.34f else 0.18f))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Icon(
            imageVector = Icons.Default.WarningAmber,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(23.dp),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(stringResource(R.string.problems), color = WarmInk, fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.problem_count, count), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, lineHeight = 13.sp)
        }
    }
}

@Composable
private fun DrawerSourceSection(
    account: com.kgs.calendar.data.local.entity.AccountEntity,
    collections: List<CollectionEntity>,
    hiddenCollectionHrefs: Set<String>,
    onCollectionVisibleInViews: (String, Boolean) -> Unit,
    onCollectionSettings: (CollectionEntity) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val initial = (account.displayName ?: account.username).take(1).ifBlank { "?" }
        Box(Modifier.size(30.dp).clip(CircleShape).background(WarmBrown), contentAlignment = Alignment.Center) {
            Text(initial, color = Color.White, fontSize = 13.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f)) {
            Text(account.displayName ?: account.username, color = WarmInk, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val subtitle = when {
                account.id == UiLocalAccountId || account.serverUrl.startsWith(UiLocalCollectionPrefix) -> stringResource(R.string.local_calendar)
                account.isAndroidProviderForUi() -> stringResource(R.string.android_device_calendars)
                account.sourceType == SourceType.ReadOnlyUrl || account.username == "Read-only URL" -> stringResource(R.string.read_only_url)
                else -> stringResource(R.string.nextcloud)
            }
            Text(
                subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                lineHeight = 13.sp,
            )
        }
    }
    val sortedCollections = collections.sortedWith(
        compareBy<CollectionEntity> {
            when {
                it.supportsEvents && it.supportsTasks -> 0
                it.supportsEvents -> 1
                else -> 2
            }
        }.thenBy { it.displayName.lowercase(Locale.ROOT) },
    )
    sortedCollections.forEach { collection ->
        DrawerCollectionRow(
            collection = collection,
            visibleInViews = collection.href !in hiddenCollectionHrefs,
            onVisibleInViewsChanged = { onCollectionVisibleInViews(collection.href, it) },
            onSettings = { onCollectionSettings(collection) },
        )
    }
}

@Composable
private fun DrawerViewItem(
    label: String,
    icon: ImageVector,
    viewMode: CalendarViewMode,
    selectedView: CalendarViewMode,
    onViewSelected: (CalendarViewMode) -> Unit,
) {
    val selected = viewMode == selectedView
    val background by animateColorAsState(
        targetValue = if (selected) WarmPeach else Color.Transparent,
        animationSpec = tween(MotionMedium, easing = MotionStandard),
        label = "drawerSelection",
    )
    val glyphScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.92f,
        animationSpec = tween(MotionMedium, easing = MotionStandard),
        label = "drawerGlyphScale",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(background)
            .clickable { onViewSelected(viewMode) }
            .padding(horizontal = 20.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) WarmInk else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(23.dp).scale(glyphScale),
        )
        Text(label, color = WarmInk, fontSize = 15.sp, lineHeight = 18.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun DrawerActionRow(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 30.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Icon(icon, contentDescription = null, tint = WarmInk, modifier = Modifier.size(23.dp))
        Text(label, color = WarmInk, fontSize = 14.sp, lineHeight = 17.sp)
    }
}

@Composable
private fun DrawerCollectionGroupHeader(label: String) {
    Text(
        label,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 11.sp,
        lineHeight = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 30.dp, end = 24.dp, top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun DrawerCollectionRow(
    collection: CollectionEntity,
    visibleInViews: Boolean,
    onVisibleInViewsChanged: (Boolean) -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSettings)
            .padding(horizontal = 24.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        CollectionToggle(color = Color(collection.color), checked = visibleInViews) {
            onVisibleInViewsChanged(!visibleInViews)
        }
        Column(Modifier.weight(1f)) {
            Text(collection.displayName, color = WarmInk, fontSize = 14.sp, lineHeight = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(
                modifier = Modifier.padding(top = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (collection.supportsEvents) {
                    CalendarCapabilityChip(stringResource(R.string.events), Color(0xFF2F5AEA))
                }
                if (collection.supportsTasks) {
                    CalendarCapabilityChip(stringResource(R.string.tasks), Color(0xFF00A86B))
                }
            }
        }
    }
}

@Composable
private fun HiddenCalendarCreationDialog(
    collection: CollectionEntity,
    itemLabel: String,
    onDismiss: () -> Unit,
    onUnhide: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.hidden_calendar_creation_title))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    stringResource(R.string.hidden_calendar_creation_body, itemLabel, collection.displayName),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                )
                val hiddenCalendarShape = RoundedCornerShape(18.dp)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .dashedBorder(SyncPendingOrange, 18.dp)
                        .clickable(onClick = onUnhide),
                    shape = hiddenCalendarShape,
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        CollectionToggle(color = Color(collection.color), checked = false, onClick = onUnhide)
                        Column(Modifier.weight(1f)) {
                            Text(
                                collection.displayName,
                                color = WarmInk,
                                fontSize = 14.sp,
                                lineHeight = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Row(
                                modifier = Modifier.padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (collection.supportsEvents) {
                                    CalendarCapabilityChip(stringResource(R.string.events), Color(0xFF2F5AEA))
                                }
                                if (collection.supportsTasks) {
                                    CalendarCapabilityChip(stringResource(R.string.tasks), Color(0xFF00A86B))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onUnhide) {
                Text(stringResource(R.string.unhide_calendar))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        },
    )
}

@Composable
private fun CalendarCapabilityChip(label: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = if (MaterialTheme.colorScheme.background.isDark()) 0.30f else 0.14f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.42f)),
    ) {
        Text(
            label,
            color = color,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun CollectionToggle(color: Color, checked: Boolean, onClick: () -> Unit) {
    val fillColor by animateColorAsState(
        targetValue = if (checked) color else MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        animationSpec = tween(MotionMedium, easing = MotionStandard),
        label = "collectionToggleFill",
    )
    val borderColor by animateColorAsState(
        targetValue = if (checked) color else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f),
        animationSpec = tween(MotionMedium, easing = MotionStandard),
        label = "collectionToggleBorder",
    )
    val checkScale by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(MotionShort, easing = MotionStandard),
        label = "collectionToggleCheck",
    )
    val shape = RoundedCornerShape(7.dp)
    Box(
        modifier = Modifier
            .size(23.dp)
            .clip(shape)
            .background(fillColor)
            .border(1.2.dp, borderColor, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Default.Check,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .size(16.dp)
                .scale(checkScale),
        )
    }
}

@Composable
private fun DrawerGlyph(selected: Boolean, modifier: Modifier = Modifier) {
    val color = if (selected) WarmInk else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier
            .width(24.dp)
            .height(22.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        repeat(3) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color.copy(alpha = if (selected) 1f else 0.72f)),
            )
        }
    }
}

@Composable
private fun TaskDrawer(
    visible: Boolean,
    state: CalendarUiState,
    onDismiss: () -> Unit,
    onTaskStatusChanged: (String, String) -> Unit,
    onTaskClick: (TaskEntity) -> Unit,
    onShowCompleted: () -> Unit,
    onCreateTask: () -> Unit,
) {
    val drawerProgress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(if (visible) 145 else 115, easing = if (visible) MotionStandard else MotionStandardAccelerate),
        label = "taskDrawerProgress",
    )
    val quietInteraction = remember { MutableInteractionSource() }
    val density = LocalDensity.current
    val drawerWidth = 336.dp
    val drawerWidthPx = with(density) { drawerWidth.toPx() }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    val effectiveProgress = (drawerProgress - dragOffsetPx / drawerWidthPx).coerceIn(0f, 1f)
    LaunchedEffect(visible) {
        if (visible) {
            dragOffsetPx = 0f
        } else {
            delay(150)
            dragOffsetPx = 0f
        }
    }
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(Modifier.fillMaxSize()) {
        if (effectiveProgress > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(effectiveProgress)
                    .background(Color.Black.copy(alpha = 0.42f))
                    .clickable(
                        interactionSource = quietInteraction,
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
        }
        Surface(
            modifier = Modifier
                .fillMaxHeight()
                .width(drawerWidth)
                .align(Alignment.CenterEnd)
                .offset {
                    IntOffset((drawerWidthPx * (1f - effectiveProgress)).roundToInt(), 0)
                }
                .pointerInput(visible, drawerWidthPx) {
                    detectHorizontalDragGestures(
                        onDragCancel = { dragOffsetPx = 0f },
                        onDragEnd = {
                            if (dragOffsetPx >= drawerWidthPx * 0.28f) {
                                onDismiss()
                            } else {
                                dragOffsetPx = 0f
                            }
                        },
                    ) { change, dragAmount ->
                        if (dragAmount > 0f || dragOffsetPx > 0f) {
                            change.consume()
                            dragOffsetPx = (dragOffsetPx + dragAmount).coerceIn(0f, drawerWidthPx)
                        }
                    }
                },
            shape = RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp),
            color = MaterialTheme.colorScheme.background,
            shadowElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = statusTop + 18.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = WarmInk, modifier = Modifier.size(24.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.tasks), color = WarmInk, fontSize = 21.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold)
                    }
                    IconButton(
                        onClick = onCreateTask,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(accentContainerColor()),
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = stringResource(R.string.new_task),
                            tint = accentContainerContentColor(),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                TaskInbox(
                    state = state,
                    onTaskStatusChanged = onTaskStatusChanged,
                    onDetail = { detail ->
                        if (detail is DetailSheet.Task) onTaskClick(detail.task)
                    },
                    onShowCompleted = onShowCompleted,
                )
            }
        }
    }
}

@Composable
private fun CalendarSearchOverlay(
    visible: Boolean,
    query: String,
    results: List<EventEntity>,
    taskResults: List<TaskEntity>,
    allTasksForHierarchy: List<TaskEntity>,
    taskColorMode: TaskColorMode,
    subtasksExpandedByDefault: Boolean,
    onQueryChange: (String) -> Unit,
    onTaskStatusChanged: (String, String) -> Unit,
    onEventClick: (EventEntity) -> Unit,
    onTaskClick: (TaskEntity) -> Unit,
    onClose: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(animationSpec = tween(MotionLong, easing = MotionEmphasized)) { -it / 5 } +
            scaleIn(initialScale = 0.98f, animationSpec = tween(MotionLong, easing = MotionEmphasized)) +
            fadeIn(animationSpec = tween(MotionMedium, delayMillis = 50, easing = MotionStandard)),
        exit = slideOutVertically(animationSpec = tween(MotionMedium, easing = MotionStandardAccelerate)) { -it / 6 } +
            scaleOut(targetScale = 0.98f, animationSpec = tween(MotionMedium, easing = MotionStandardAccelerate)) +
            fadeOut(animationSpec = tween(MotionShort, easing = MotionStandardAccelerate)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
        ) {
            SearchTopBar(query = query, onQueryChange = onQueryChange, onClose = onClose)
            SearchResultsList(
                query = query,
                eventResults = results,
                taskResults = taskResults,
                allTasksForHierarchy = allTasksForHierarchy,
                taskColorMode = taskColorMode,
                subtasksExpandedByDefault = subtasksExpandedByDefault,
                onEventClick = onEventClick,
                onTaskClick = onTaskClick,
                onTaskStatusChanged = onTaskStatusChanged,
            )
        }
    }
}

@Composable
private fun SearchTopBar(query: String, onQueryChange: (String) -> Unit, onClose: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(statusTop + 62.dp)
            .background(MaterialTheme.colorScheme.background)
            .padding(start = 16.dp, end = 14.dp, top = statusTop),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconButton(onClick = onClose, modifier = Modifier.size(38.dp)) {
            Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back), tint = WarmInk, modifier = Modifier.size(25.dp))
        }
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.headlineSmall.copy(
                color = WarmInk,
                fontSize = 22.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.Normal,
            ),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTapGestures {
                        focused = true
                        focusRequester.requestFocus()
                    }
                }
                .focusRequester(focusRequester)
                .onFocusChanged { focused = it.isFocused },
            cursorBrush = SolidColor(WarmBrown),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isBlank() && !focused) {
                        Text(stringResource(R.string.search), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 22.sp, lineHeight = 26.sp)
                    }
                    innerTextField()
                }
            },
        )
        IconButton(
            modifier = Modifier.size(38.dp),
            onClick = {
            if (query.isBlank()) onClose() else onQueryChange("")
        }) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close), tint = WarmInk, modifier = Modifier.size(25.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchResultsList(
    query: String,
    eventResults: List<EventEntity>,
    taskResults: List<TaskEntity>,
    allTasksForHierarchy: List<TaskEntity> = taskResults,
    taskColorMode: TaskColorMode,
    subtasksExpandedByDefault: Boolean,
    onEventClick: (EventEntity) -> Unit,
    onTaskClick: (TaskEntity) -> Unit,
    onTaskStatusChanged: (String, String) -> Unit,
    showSearchIntro: Boolean = true,
    autoScrollToNow: Boolean = query.isNotBlank() || !showSearchIntro,
    emptyMessage: String? = null,
    scrollRequestKey: Int = 0,
    scrollTargetDate: LocalDate? = null,
    stickyHeaderBackground: Color = MaterialTheme.colorScheme.surface,
    showTaskChains: Boolean = true,
    expandMultiDayEventSpans: Boolean = false,
) {
    val resolvedEmptyMessage = emptyMessage ?: stringResource(R.string.no_results)
    val noDateLabel = stringResource(R.string.no_date)
    val hierarchySubset = remember(query, showSearchIntro, showTaskChains, taskResults, allTasksForHierarchy) {
        if (!showTaskChains) {
            TaskHierarchySubset(emptyList())
        } else {
            when {
                query.isNotBlank() -> allTasksForHierarchy.searchHierarchySubsetForMatches(taskResults)
                !showSearchIntro -> TaskHierarchySubset(
                    tasks = allTasksForHierarchy.chainSubsetForTargets(
                        targets = taskResults,
                        includeTargetDescendants = true,
                    ),
                )
                else -> TaskHierarchySubset(tasks = taskResults)
            }
        }
    }
    val hierarchyExpandedByDefault = when {
        query.isNotBlank() -> false
        !showSearchIntro -> true
        else -> subtasksExpandedByDefault
    }
    val taskHierarchy = rememberTaskHierarchyPresentation(
        tasks = hierarchySubset.tasks,
        expandedByDefault = hierarchyExpandedByDefault,
        defaultExpandedResourceHrefs = hierarchySubset.defaultExpandedResourceHrefs,
    )
    val taskGroups = remember(taskHierarchy.entries) {
        buildList {
            var current = mutableListOf<TaskHierarchyEntry>()
            taskHierarchy.entries.forEach { entry ->
                if (entry.depth == 0 && current.isNotEmpty()) {
                    add(CalendarSearchResult.TaskGroup(current.toList()))
                    current = mutableListOf()
                }
                current += entry
            }
            if (current.isNotEmpty()) add(CalendarSearchResult.TaskGroup(current.toList()))
        }
    }
    val flatTaskItems = remember(showTaskChains, taskResults) {
        if (showTaskChains) {
            emptyList()
        } else {
            taskResults
                .distinctBy { it.resourceHref }
                .filter { it.agendaSortMillis() != null }
                .map { CalendarSearchResult.TaskItem(it) }
        }
    }
    val eventItems = remember(eventResults, taskResults, expandMultiDayEventSpans) {
        if (expandMultiDayEventSpans) {
            buildAgendaEventResults(eventResults, taskResults)
        } else {
            eventResults.map { CalendarSearchResult.Event(it) }
        }
    }
    val results = remember(eventItems, taskGroups, flatTaskItems) {
        (eventItems + taskGroups + flatTaskItems)
            .sortedWith(
                compareBy<CalendarSearchResult> { it.sortMillis == null }
                    .thenBy { it.sortMillis ?: Long.MAX_VALUE },
            )
    }
    val groupedResults = remember(results) {
        results.groupBy { item -> item.date?.year?.toString() ?: noDateLabel }
    }
    val today = LocalCalendarTimeSnapshot.current.today
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val firstFutureKey = remember(results, today) {
        results.firstOrNull { it.isFutureSearchSection(today) }?.stableKey()
    }
    var initialAutoScrollHandled by remember { mutableStateOf(false) }
    var lastHandledScrollRequestKey by remember { mutableStateOf(scrollRequestKey) }
    val firstFutureLazyIndex = remember(groupedResults, firstFutureKey, today) {
        if (firstFutureKey == null) null else {
            var lazyIndex = if (showSearchIntro && query.isNotBlank()) 1 else 0
            var found: Int? = null
            var dividerInserted = false
            for (groupItems in groupedResults.values) {
                if (found != null) break
                lazyIndex++ // sticky year header
                for (item in groupItems) {
                    if (!dividerInserted && item.isFutureSearchSection(today)) {
                        lazyIndex++ // past/future divider
                        dividerInserted = true
                    }
                    if (item.stableKey() == firstFutureKey) {
                        found = lazyIndex
                        break
                    }
                    lazyIndex++
                }
            }
            found
        }
    }
    val requestedDateLazyIndex = remember(groupedResults, scrollTargetDate, showSearchIntro, query, today) {
        val targetDate = scrollTargetDate ?: return@remember null
        var lazyIndex = if (showSearchIntro && query.isNotBlank()) 1 else 0
        var found: Int? = null
        var dividerInserted = false
        for (groupItems in groupedResults.values) {
            if (found != null) break
            lazyIndex++ // sticky year header
            for (item in groupItems) {
                if (!dividerInserted && item.isFutureSearchSection(today)) {
                    lazyIndex++ // past/future divider
                    dividerInserted = true
                }
                val itemDate = item.date
                if (itemDate != null && !itemDate.isBefore(targetDate)) {
                    found = lazyIndex
                    break
                }
                lazyIndex++
            }
        }
        found
    }
    LaunchedEffect(query, firstFutureLazyIndex, requestedDateLazyIndex, scrollRequestKey) {
        if (autoScrollToNow) {
            val explicitAgendaRequest = !showSearchIntro && scrollRequestKey != lastHandledScrollRequestKey
            val requestedIndex = if (explicitAgendaRequest && requestedDateLazyIndex != null) requestedDateLazyIndex else null
            val baseTarget = requestedIndex ?: firstFutureLazyIndex
            if (baseTarget == null) return@LaunchedEffect
            val targetIndex = if (showSearchIntro) {
                (baseTarget - 2).coerceAtLeast(0)
            } else {
                (baseTarget - 2).coerceAtLeast(0)
            }
            val targetOffset = if (showSearchIntro) 0 else -with(density) { 38.dp.roundToPx() }
            if (initialAutoScrollHandled && explicitAgendaRequest) {
                listState.animateScrollToItem(targetIndex, targetOffset)
            } else {
                listState.scrollToItem(targetIndex, targetOffset)
            }
            initialAutoScrollHandled = true
            lastHandledScrollRequestKey = scrollRequestKey
        }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (query.isBlank() && showSearchIntro) {
            item {
                Text(stringResource(R.string.search_events_tasks), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, lineHeight = 18.sp, modifier = Modifier.padding(start = 62.dp, top = 18.dp))
            }
        } else {
            if (showSearchIntro && query.isNotBlank()) {
                item {
                    Text(
                        stringResource(R.string.search_may_miss_old_results),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(start = 62.dp, bottom = 8.dp),
                    )
                }
            }
            if (results.isEmpty()) {
                item {
                    Text(resolvedEmptyMessage, color = WarmInk, fontSize = 16.sp, lineHeight = 20.sp, modifier = Modifier.padding(start = 62.dp, top = 18.dp))
                }
            } else {
                var futureDividerInserted = false
                groupedResults.forEach { (group, groupItems) ->
                    stickyHeader(key = "search-year-$group") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(stickyHeaderBackground.copy(alpha = 0.97f))
                                .padding(start = 62.dp, top = 7.dp, bottom = 7.dp),
                        ) {
                            Text(
                                group,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 15.sp,
                                lineHeight = 19.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    var lastDateKey: String? = null
                    groupItems.forEach { item ->
                        val dateKey = item.date?.toString() ?: "__no_date__"
                        val showDate = dateKey != lastDateKey
                        if (!futureDividerInserted && item.isFutureSearchSection(today)) {
                            item(key = "search-past-future-divider") {
                                SearchPastFutureDivider()
                            }
                            futureDividerInserted = true
                        }
                        item(key = item.stableKey()) {
                        when (item) {
                            is CalendarSearchResult.Event -> SearchResultCard(
                                event = item.event,
                                displayDate = item.date,
                                spanEndDate = item.spanEndDate,
                                showDate = showDate,
                                onClick = { onEventClick(item.event) },
                            )
                            is CalendarSearchResult.TaskItem -> SearchTaskResultCard(
                                task = item.task,
                                showDate = showDate,
                                taskColorMode = taskColorMode,
                                onTaskStatusChanged = onTaskStatusChanged,
                                hierarchyDepth = 0,
                                hierarchyContinuationLevels = emptySet(),
                                hierarchyLastSibling = true,
                                hasSubtasks = false,
                                subtasksExpanded = false,
                                onToggleSubtasks = {},
                                onClick = { onTaskClick(item.task) },
                            )
                            is CalendarSearchResult.TaskGroup -> Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer { clip = false },
                            ) {
                                item.entries.forEach { entry ->
                                    AnimatedTaskHierarchyEntry(entry) {
                                        SearchTaskResultCard(
                                            task = entry.task,
                                            showDate = showDate,
                                            taskColorMode = taskColorMode,
                                            onTaskStatusChanged = onTaskStatusChanged,
                                            hierarchyDepth = entry.depth,
                                            hierarchyContinuationLevels = entry.continuationLevels,
                                            hierarchyLastSibling = entry.lastSibling,
                                            hasSubtasks = entry.hasChildren,
                                            subtasksExpanded = entry.expanded,
                                            onToggleSubtasks = { taskHierarchy.toggle(entry.task) },
                                            onClick = { onTaskClick(entry.task) },
                                        )
                                    }
                                }
                            }
                        }
                        }
                        lastDateKey = dateKey
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

private sealed interface CalendarSearchResult {
    val sortMillis: Long?
    val date: LocalDate?

    data class Event(
        val event: EventEntity,
        override val date: LocalDate = event.startsAtMillis.toDate(),
        val spanEndDate: LocalDate = date,
    ) : CalendarSearchResult {
        override val sortMillis: Long = if (date == event.startsAtMillis.toDate()) {
            event.startsAtMillis
        } else {
            date.startOfDayMillis()
        }
    }

    data class TaskGroup(val entries: List<TaskHierarchyEntry>) : CalendarSearchResult {
        private val root: TaskEntity = entries.first().task
        override val sortMillis: Long? = entries
            .mapNotNull { it.task.startAtMillis ?: it.task.dueAtMillis }
            .minOrNull()
            ?: root.startAtMillis
            ?: root.dueAtMillis
        override val date: LocalDate? = sortMillis?.toDate()
    }

    data class TaskItem(val task: TaskEntity) : CalendarSearchResult {
        override val sortMillis: Long? = task.agendaSortMillis()
        override val date: LocalDate? = sortMillis?.toDate()
    }
}

private fun CalendarSearchResult.stableKey(): String = when (this) {
    is CalendarSearchResult.Event -> "event-${event.resourceHref}-${event.startsAtMillis}-$date-$spanEndDate"
    is CalendarSearchResult.TaskGroup -> "task-group-${entries.first().task.resourceHref}"
    is CalendarSearchResult.TaskItem -> "task-${task.resourceHref}-${task.agendaSortMillis() ?: 0L}"
}

private fun CalendarSearchResult.isFutureSearchSection(today: LocalDate): Boolean =
    date?.isBefore(today) != true

private fun buildAgendaEventResults(
    events: List<EventEntity>,
    tasks: List<TaskEntity>,
): List<CalendarSearchResult.Event> {
    val taskDates = tasks
        .flatMap { it.visibleDates() }
        .toSet()
    val eventDatesByResource = events.associate { event ->
        event.resourceHref to event.visibleAgendaDates()
    }
    return events.flatMap { event ->
        val dates = eventDatesByResource[event.resourceHref].orEmpty()
        if (dates.size <= 1) {
            listOf(CalendarSearchResult.Event(event, dates.firstOrNull() ?: event.startsAtMillis.toDate()))
        } else {
            val interruptionDates = dates.filterTo(mutableSetOf()) { date ->
                date in taskDates || events.any { other ->
                    other.resourceHref != event.resourceHref && eventDatesByResource[other.resourceHref].orEmpty().contains(date)
                }
            }
            event.toAgendaSpanResults(dates, interruptionDates)
        }
    }
}

private fun EventEntity.toAgendaSpanResults(
    dates: List<LocalDate>,
    interruptionDates: Set<LocalDate>,
): List<CalendarSearchResult.Event> {
    val sortedDates = dates.sorted()
    if (sortedDates.isEmpty()) return emptyList()
    val results = mutableListOf<CalendarSearchResult.Event>()
    var segmentStart: LocalDate? = null
    var previous: LocalDate? = null

    fun flushSegment(end: LocalDate) {
        val start = segmentStart ?: return
        if (!end.isBefore(start)) {
            results += CalendarSearchResult.Event(this, start, end)
        }
        segmentStart = null
    }

    sortedDates.forEach { date ->
        val last = previous
        if (last != null && last.plusDays(1) != date) {
            flushSegment(last)
        }
        if (date in interruptionDates) {
            flushSegment(date.minusDays(1))
            results += CalendarSearchResult.Event(this, date, date)
        } else if (segmentStart == null) {
            segmentStart = date
        }
        previous = date
    }
    previous?.let(::flushSegment)
    return results
}

@Composable
private fun SearchPastFutureDivider() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalBleed(18.dp)
            .padding(top = 12.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            stringResource(R.string.past),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        HorizontalDivider(color = WarmLine)
        Text(
            stringResource(R.string.future),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SearchResultCard(
    event: EventEntity,
    showDate: Boolean,
    onClick: () -> Unit,
    displayDate: LocalDate = event.startsAtMillis.toDate(),
    spanEndDate: LocalDate = displayDate,
) {
    val isPast = event.endsAtMillis < System.currentTimeMillis()
    val isSpan = spanEndDate.isAfter(displayDate)
    val attendees = remember(event.attendeesJson) { event.attendeesJson.toCalendarParticipants() }
    val rowHeight = when {
        isSpan -> AgendaMultiDaySpanCardHeight
        attendees.isNotEmpty() -> 76.dp
        else -> 64.dp
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        val visuals = event.cardVisuals(muted = isPast, darkPalette = CurrentDarkPalette)
        SearchDateColumn(
            date = displayDate,
            muted = isPast,
            visible = showDate,
            endDate = spanEndDate.takeIf { isSpan },
            height = if (isSpan) rowHeight else null,
        )
        val eventTextStyle = tentativeReadableTextStyle(event.isTentative() && !isPast)
        val shape = RoundedCornerShape(13.dp)
        val pendingAlpha = pendingDeleteAlpha(event.resourceHref)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(rowHeight)
                .alpha(pendingAlpha)
                .clip(shape)
                .background(visuals.background)
                .then(
                    when {
                        visuals.dashedBorder && visuals.borderColor != null -> Modifier.dashedBorder(visuals.borderColor, 13.dp)
                        visuals.borderColor != null -> Modifier.border(1.dp, visuals.borderColor, shape)
                        else -> Modifier
                    },
                )
                .clickable(onClick = onClick)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .bottomEdgeFadeMask(10.dp)
                    .padding(start = 12.dp, top = 7.dp, end = 12.dp, bottom = 0.dp)
                    .padding(end = if (attendees.isNotEmpty()) 54.dp else 0.dp),
                verticalArrangement = Arrangement.Top,
            ) {
                FadingTimedText(event.title, color = visuals.contentColor, fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, textDecoration = visuals.textDecoration, shadow = eventTextStyle.shadow)
                FadingTimedText(
                    if (isSpan) event.localizedAgendaSpanLabel(displayDate, spanEndDate) else event.localizedTimeLabel(),
                    color = visuals.contentColor.copy(alpha = if (isPast) 0.72f else 0.92f),
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    maxLines = 1,
                    textDecoration = visuals.textDecoration,
                    shadow = eventTextStyle.shadow,
                )
                event.location?.takeIf { it.isNotBlank() }?.let {
                    FadingTimedText(it.cardLocationText(event.locationMapVerified), color = visuals.contentColor.copy(alpha = if (isPast) 0.66f else 0.86f), fontSize = 11.sp, lineHeight = 13.sp, maxLines = 1, textDecoration = visuals.textDecoration, shadow = eventTextStyle.shadow)
                }
            }
            EventParticipantStack(
                attendees = attendees,
                eventColor = visuals.baseColor,
                contentColor = visuals.contentColor,
                modifier = Modifier.align(Alignment.BottomEnd),
                circleSize = 24.dp,
                maxVisible = 5,
                muted = isPast,
            )
            PendingMutationBadge(
                resourceHref = event.resourceHref,
                modifier = Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-2).dp),
            )
        }
    }
}

@Composable
private fun SearchTaskResultCard(
    task: TaskEntity,
    showDate: Boolean,
    taskColorMode: TaskColorMode,
    onTaskStatusChanged: (String, String) -> Unit,
    hierarchyDepth: Int = 0,
    hierarchyContinuationLevels: Set<Int> = emptySet(),
    hierarchyLastSibling: Boolean = true,
    hasSubtasks: Boolean = false,
    subtasksExpanded: Boolean = true,
    onToggleSubtasks: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val taskDate = (task.startAtMillis ?: task.dueAtMillis)?.toDate()
    val isMuted = task.isInactive()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(taskHierarchyLayerZ(hierarchyDepth) + taskPriorityLayerZ(task, PriorityAnimationsEnabled))
            .graphicsLayer { clip = false },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (hierarchyDepth == 0) {
            SearchDateColumn(date = taskDate, muted = isMuted, visible = showDate)
        } else {
            Spacer(Modifier.width(50.dp))
        }
        TaskRow(
            task = task,
            taskColorMode = taskColorMode,
            onTaskStatusChanged = onTaskStatusChanged,
            prominent = true,
            hierarchyDepth = hierarchyDepth,
            hierarchyContinuationLevels = hierarchyContinuationLevels,
            hierarchyLastSibling = hierarchyLastSibling,
            hasSubtasks = hasSubtasks,
            subtasksExpanded = subtasksExpanded,
            onToggleSubtasks = onToggleSubtasks,
            modifier = Modifier.weight(1f),
            outerHorizontalPadding = 0.dp,
            outerVerticalPadding = 3.dp,
            connectorStemInset = TaskHierarchyStemInset,
            priorityMotionEnabled = !isMuted,
            onClick = onClick,
        )
    }
}

private val AgendaMultiDaySpanCardHeight = 96.dp

@Composable
private fun SearchDateColumn(
    date: LocalDate?,
    muted: Boolean,
    visible: Boolean = true,
    endDate: LocalDate? = null,
    height: Dp? = null,
) {
    Column(
        modifier = Modifier
            .width(50.dp)
            .then(if (height != null) Modifier.height(height) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (visible) {
            if (date == null) {
                Text(
                    appString(R.string.none),
                    color = WarmInk.copy(alpha = if (muted) 0.58f else 1f),
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    appString(R.string.date),
                    color = WarmInk.copy(alpha = if (muted) 0.58f else 1f),
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            } else {
                Text(
                    date.format(DateTimeFormatter.ofPattern("MMM", LocalAppLocale.current)).replace(".", ""),
                    color = WarmInk.copy(alpha = if (muted) 0.58f else 1f),
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    date.dayOfMonth.toString(),
                    color = WarmInk.copy(alpha = if (muted) 0.58f else 1f),
                    fontSize = 20.sp,
                    lineHeight = 23.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (endDate != null) {
                    Spacer(Modifier.height(3.dp))
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .weight(1f)
                            .clip(RoundedCornerShape(999.dp))
                            .background(WarmInk.copy(alpha = if (muted) 0.22f else 0.42f)),
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        endDate.format(DateTimeFormatter.ofPattern("MMM", LocalAppLocale.current)).replace(".", ""),
                        color = WarmInk.copy(alpha = if (muted) 0.58f else 1f),
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        endDate.dayOfMonth.toString(),
                        color = WarmInk.copy(alpha = if (muted) 0.58f else 1f),
                        fontSize = 17.sp,
                        lineHeight = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun LoginScreen(
    busy: Boolean,
    onManualLogin: (String, String, String, (Boolean, String?) -> Unit) -> Unit,
    onBrowserLogin: (String) -> Unit,
) {
    var serverUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var appPassword by remember { mutableStateOf("") }
    var loginError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.connect_nextcloud_summary), color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            serverUrl,
            {
                serverUrl = it
                loginError = null
            },
            label = { Text(stringResource(R.string.nextcloud_url)) },
            singleLine = true,
            keyboardOptions = UrlKeyboardOptions,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { onBrowserLogin(serverUrl) }, enabled = serverUrl.isNotBlank() && !busy, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Login, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.login_in_browser))
        }
        HorizontalDivider()
        OutlinedTextField(
            username,
            {
                username = it
                loginError = null
            },
            label = { Text(stringResource(R.string.username)) },
            singleLine = true,
            keyboardOptions = UsernameKeyboardOptions,
            modifier = Modifier.fillMaxWidth(),
        )
        SecurePasswordField(
            value = appPassword,
            onValueChange = {
                appPassword = it
                loginError = null
            },
            label = stringResource(R.string.app_password),
            modifier = Modifier.fillMaxWidth(),
        )
        AnimatedVisibility(visible = loginError != null) {
            Text(
                loginError.orEmpty(),
                color = MaterialTheme.colorScheme.error,
                fontSize = 13.sp,
                lineHeight = 17.sp,
            )
        }
        OutlinedButton(
            onClick = {
                onManualLogin(serverUrl, username, appPassword) { success, error ->
                    loginError = if (success) null else error
                    if (success) appPassword = ""
                }
            },
            enabled = serverUrl.isNotBlank() && username.isNotBlank() && appPassword.isNotBlank() && !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.use_app_password))
        }
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

@Composable
private fun MonthBlock(
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
private val MorphDayEnter: EnterTransition = fadeIn(tween(MorphContentFadeMs, easing = MorphEasing))
private val MorphDayExit: ExitTransition =
    fadeOut(tween(MorphContentFadeMs, delayMillis = MorphDurationMs - MorphContentFadeMs, easing = MorphEasing))
// The month cell (the morph's container source/target) now cross-fades gradually instead of
// snapping, so the tapped day's lighter background doesn't vanish/appear abruptly.
private val MorphCellEnter: EnterTransition =
    fadeIn(tween(MorphContentFadeMs, delayMillis = MorphDurationMs - MorphContentFadeMs, easing = MorphEasing))
private val MorphCellExit: ExitTransition = fadeOut(tween(MorphContentFadeMs, easing = MorphEasing))

// Option B: a single event/task morphing between its month-cell pill and its full 1-day card.
// The CARD side stays visible across (almost) the whole morph — it fades in near the start when
// growing and fades out only at the very end when shrinking — so its scale (and its text scaling
// with it) is visible the entire time, matching the smooth scale-DOWN. The PILL side is the
// opposite (only present at the cell-sized end), so it never lingers as a ballooning copy.
private val MorphItemFadeMs = MorphDurationMs / 6
internal val MorphItemCardEnter: EnterTransition = fadeIn(tween(MorphItemFadeMs, easing = MorphEasing))
internal val MorphItemCardExit: ExitTransition =
    fadeOut(tween(MorphItemFadeMs, delayMillis = MorphDurationMs - MorphItemFadeMs, easing = MorphEasing))
private val MorphItemPillEnter: EnterTransition =
    fadeIn(tween(MorphItemFadeMs, delayMillis = MorphDurationMs - MorphItemFadeMs, easing = MorphEasing))
private val MorphItemPillExit: ExitTransition = fadeOut(tween(MorphItemFadeMs, easing = MorphEasing))

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
private val MorphDayBoundsTransform = androidx.compose.animation.BoundsTransform { _, _ ->
    tween(MorphDurationMs, easing = MorphEasing)
}

private const val TaskDetailMorphDurationMs = 460
private const val TaskDetailControlsRevealDelayMs = 220
private val TaskDetailMorphBoundsTransform = androidx.compose.animation.BoundsTransform { _, _ ->
    tween(TaskDetailMorphDurationMs, easing = MotionEmphasized)
}

private fun taskDetailMorphKey(task: TaskEntity): String =
    "task-detail:${task.collectionHref}:${task.resourceHref}"

private fun DetailSheet.transitionKey(): String = when (this) {
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
private fun Modifier.taskDetailMorph(key: String, cornerRadius: Dp): Modifier =
    morphBounds(
        key = key,
        enter = EnterTransition.None,
        exit = fadeOut(animationSpec = snap()),
        overlayShape = RoundedCornerShape(cornerRadius),
        boundsTransform = TaskDetailMorphBoundsTransform,
        remeasureToBounds = true,
    )

@Composable
private fun Modifier.taskDetailFadeOverlay(): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    return with(shared) {
        this@taskDetailFadeOverlay.renderInSharedTransitionScopeOverlay(10f) { true }
    }
}

@Composable
private fun rememberTaskDetailMorphCornerRadius(
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
private fun rememberTaskDetailMorphProgress(): Float {
    val morphScope = LocalMorphAnimatedVisibilityScope.current ?: return 1f
    val progress by morphScope.transition.animateFloat(
        transitionSpec = { tween(TaskDetailMorphDurationMs, easing = MotionEmphasized) },
        label = "taskDetailMorphProgress",
    ) { state ->
        if (state == EnterExitState.Visible) 1f else 0f
    }
    return progress
}

private fun lerpFloat(start: Float, end: Float, fraction: Float): Float =
    start + (end - start) * fraction.coerceIn(0f, 1f)

private fun phasedProgress(progress: Float, start: Float, end: Float): Float {
    if (end <= start) return if (progress >= end) 1f else 0f
    return ((progress - start) / (end - start)).coerceIn(0f, 1f)
}

private fun Modifier.taskDetailRevealLayout(
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
private val MorphCornerRadiusDp = 12.dp
private val MorphRoundedClip: SharedTransitionScope.OverlayClip =
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
private fun CompletionBurst(playKey: Int, color: Color, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(1f) }
    LaunchedEffect(playKey) {
        if (playKey > 0) {
            progress.snapTo(0f)
            progress.animateTo(1f, animationSpec = tween(MotionLong, easing = MotionStandard))
        }
    }
    if (playKey <= 0 || progress.value >= 1f) return
    Canvas(modifier.fillMaxSize()) {
        val p = progress.value
        val alpha = (1f - p).coerceIn(0f, 1f)
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(color.copy(alpha = 0.22f * alpha), radius = size.minDimension * (0.34f + p * 0.75f), center = center)
        repeat(10) { index ->
            val angle = (Math.PI * 2.0 * index / 10.0).toFloat()
            val distance = size.minDimension * (0.18f + p * 0.62f)
            val particleCenter = Offset(
                x = center.x + kotlin.math.cos(angle) * distance,
                y = center.y + kotlin.math.sin(angle) * distance,
            )
            drawCircle(
                color = if (index % 2 == 0) DraftAccent.copy(alpha = alpha) else color.copy(alpha = alpha),
                radius = size.minDimension * (0.08f * (1f - p * 0.45f)),
                center = particleCenter,
            )
        }
    }
}

@Composable
internal fun TaskCardCompletionBurst(playKey: Int, color: Color, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(1f) }
    LaunchedEffect(playKey) {
        if (playKey > 0) {
            progress.snapTo(0f)
            progress.animateTo(1f, animationSpec = tween(620, easing = MotionEmphasized))
        }
    }
    if (playKey <= 0 || progress.value >= 1f) return
    Canvas(modifier.fillMaxSize()) {
        val p = progress.value
        val alpha = (1f - p).coerceIn(0f, 1f)
        val center = Offset(size.width / 2f, size.height / 2f)
        val glowRadius = max(size.width, size.height) * (0.18f + p * 0.82f)
        drawRoundRect(
            color = color.copy(alpha = 0.22f * alpha),
            topLeft = Offset(-size.width * 0.08f * p, -size.height * 0.42f * p),
            size = Size(size.width * (1f + 0.16f * p), size.height * (1f + 0.84f * p)),
            cornerRadius = CornerRadius(16.dp.toPx() + p * 18.dp.toPx(), 16.dp.toPx() + p * 18.dp.toPx()),
        )
        drawCircle(color.copy(alpha = 0.18f * alpha), radius = glowRadius, center = center)
        repeat(18) { index ->
            val lane = index / 6f
            val baseX = size.width * ((index % 6) + 0.5f) / 6f
            val angle = (Math.PI * 2.0 * (index / 18.0) + lane * 0.55).toFloat()
            val distance = size.minDimension * (0.12f + p * (0.95f + lane * 0.12f))
            val particleCenter = Offset(
                x = baseX + kotlin.math.cos(angle) * distance,
                y = center.y + kotlin.math.sin(angle) * distance * 0.7f,
            )
            drawCircle(
                color = if (index % 3 == 0) DraftAccent.copy(alpha = alpha) else color.copy(alpha = alpha * 0.92f),
                radius = size.minDimension.coerceAtLeast(18f) * (0.045f + (1f - p) * 0.045f),
                center = particleCenter,
            )
        }
    }
}

@Composable
private fun OneDayList(state: CalendarUiState, onTaskStatusChanged: (String, String) -> Unit, onDetail: (DetailSheet) -> Unit) {
    AgendaDaySection(
        state.selectedDate,
        state.events.filter { it.occursOn(state.selectedDate) },
        state.datedTasks.filter { it.taskDate() == state.selectedDate },
        state.taskColorMode,
        state.subtasksExpandedByDefault,
        onTaskStatusChanged,
        onDetail,
    )
}

@Composable
internal fun AgendaList(
    state: CalendarUiState,
    onTaskStatusChanged: (String, String) -> Unit,
    onDetail: (DetailSheet) -> Unit,
    todayScrollRequest: Int,
    scrollTargetDate: LocalDate?,
) {
    val calendarTasks = remember(state.datedTasks, state.showCompletedTasksInCalendar) {
        if (state.showCompletedTasksInCalendar) state.datedTasks else state.datedTasks.filterNot { it.isCompleted }
    }
    SearchResultsList(
        query = "",
        eventResults = state.events,
        taskResults = calendarTasks,
        allTasksForHierarchy = emptyList(),
        taskColorMode = state.taskColorMode,
        subtasksExpandedByDefault = state.subtasksExpandedByDefault,
        onEventClick = { onDetail(DetailSheet.Event(it)) },
        onTaskClick = { onDetail(DetailSheet.Task(it)) },
        onTaskStatusChanged = onTaskStatusChanged,
        showSearchIntro = false,
        autoScrollToNow = true,
        emptyMessage = stringResource(R.string.no_events_or_tasks),
        scrollRequestKey = todayScrollRequest,
        scrollTargetDate = scrollTargetDate,
        stickyHeaderBackground = MaterialTheme.colorScheme.background,
        showTaskChains = false,
        expandMultiDayEventSpans = true,
    )
}

@Composable
private fun AgendaDaySection(
    date: LocalDate,
    events: List<EventEntity>,
    tasks: List<TaskEntity>,
    taskColorMode: TaskColorMode,
    subtasksExpandedByDefault: Boolean,
    onTaskStatusChanged: (String, String) -> Unit,
    onDetail: (DetailSheet) -> Unit,
) {
    val hierarchy = rememberTaskHierarchyPresentation(
        tasks.sortedBy { it.startAtMillis ?: it.dueAtMillis ?: Long.MAX_VALUE },
        expandedByDefault = subtasksExpandedByDefault,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        AgendaDateColumn(date)
        Column(
            modifier = Modifier
                .weight(1f)
                .graphicsLayer { clip = false },
        ) {
            events.sortedBy { it.startsAtMillis }.forEach { event ->
                AgendaEventCard(event) { onDetail(DetailSheet.Event(event)) }
            }
            hierarchy.entries.forEach { entry ->
                val task = entry.task
                AnimatedTaskHierarchyEntry(entry) {
                    TaskRow(
                        task = task,
                        taskColorMode = taskColorMode,
                        onTaskStatusChanged = onTaskStatusChanged,
                        prominent = true,
                        hierarchyDepth = entry.depth,
                        hierarchyContinuationLevels = entry.continuationLevels,
                        hierarchyLastSibling = entry.lastSibling,
                        hasSubtasks = entry.hasChildren,
                        subtasksExpanded = entry.expanded,
                        onToggleSubtasks = { hierarchy.toggle(task) },
                        outerHorizontalPadding = 0.dp,
                        outerVerticalPadding = 0.dp,
                    ) { onDetail(DetailSheet.Task(task)) }
                }
            }
        }
    }
}

@Composable
private fun AgendaDateColumn(date: LocalDate) {
    Column(
        modifier = Modifier.width(38.dp).padding(top = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            date.format(DateTimeFormatter.ofPattern("MMM", LocalAppLocale.current)).replace(".", ""),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            date.dayOfMonth.toString(),
            color = WarmInk,
            fontSize = 22.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun AgendaEventCard(event: EventEntity, onClick: () -> Unit) {
    // Render exactly like the 3-day / 1-day cards (cardVisuals with no muting): the old
    // muted=isPast greyed past events into washed-out cards with dark text in dark mode.
    val darkPalette = CurrentDarkPalette
    val visuals = remember(event.status, event.manualColor, event.color, darkPalette) {
        event.cardVisuals(darkPalette = darkPalette)
    }
    val attendees = remember(event.attendeesJson) { event.attendeesJson.toCalendarParticipants() }
    val eventTextStyle = tentativeReadableTextStyle(event.isTentative())
    val shape = RoundedCornerShape(12.dp)
    val pendingAlpha = pendingDeleteAlpha(event.resourceHref)
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = visuals.background),
        shape = shape,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(pendingAlpha)
            .then(
                when {
                    visuals.dashedBorder && visuals.borderColor != null -> Modifier.dashedBorder(visuals.borderColor, 12.dp)
                    visuals.borderColor != null -> Modifier.border(1.dp, visuals.borderColor, shape)
                    else -> Modifier
                },
            ),
    ) {
        Box(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp).padding(end = if (attendees.isNotEmpty()) 44.dp else 0.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    event.title,
                    color = visuals.contentColor,
                    fontSize = 14.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = visuals.textDecoration,
                    style = eventTextStyle,
                )
                Text(
                    event.localizedTimeLabel(),
                    color = visuals.contentColor.copy(alpha = 0.78f),
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = visuals.textDecoration,
                    style = eventTextStyle,
                )
                event.location?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it.cardLocationText(event.locationMapVerified),
                        color = visuals.contentColor.copy(alpha = 0.74f),
                        fontSize = 11.sp,
                        lineHeight = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textDecoration = visuals.textDecoration,
                        style = eventTextStyle,
                    )
                }
            }
            EventParticipantStack(
                attendees = attendees,
                eventColor = visuals.baseColor,
                contentColor = visuals.contentColor,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 7.dp).fillMaxWidth(0.58f),
                circleSize = 23.dp,
                maxVisible = 4,
            )
            PendingMutationBadge(
                resourceHref = event.resourceHref,
                modifier = Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-2).dp),
            )
        }
    }
}

@Composable
internal fun TaskInbox(
    state: CalendarUiState,
    onTaskStatusChanged: (String, String) -> Unit,
    onDetail: (DetailSheet) -> Unit,
    onShowCompleted: (() -> Unit)? = null,
) {
    var plannedSort by rememberSaveable { mutableStateOf(PlannedTaskSort.Date) }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val activeRootTasks = remember(state.taskHierarchyTasks) {
        state.taskHierarchyTasks.partitionByRootActivity().activeRootTasks
    }
    val activeSections = remember(activeRootTasks) { activeRootTasks.partitionByRootSchedule() }
    val openInboxTasks = activeSections.first
    val scheduledTasks = remember(activeSections.second, plannedSort) {
        when (plannedSort) {
            PlannedTaskSort.Priority -> activeSections.second.sortedWith(
                compareBy<TaskEntity> { it.priority ?: 9 }
                    .thenBy { it.startAtMillis ?: it.dueAtMillis ?: Long.MAX_VALUE }
                    .thenBy { it.title.lowercase(Locale.ROOT) },
            )
            PlannedTaskSort.Status -> activeSections.second.sortedWith(
                compareBy<TaskEntity> { it.statusSortRank() }
                    .thenBy { it.startAtMillis ?: it.dueAtMillis ?: Long.MAX_VALUE }
                    .thenBy { it.title.lowercase(Locale.ROOT) },
            )
            PlannedTaskSort.Date -> activeSections.second.sortedWith(
                compareBy<TaskEntity> { it.startAtMillis ?: it.dueAtMillis ?: Long.MAX_VALUE }
                    .thenBy { it.priority ?: 9 }
                    .thenBy { it.title.lowercase(Locale.ROOT) },
            )
        }
    }
    val showDateColumn = plannedSort == PlannedTaskSort.Date
    val inboxSectionTasks = openInboxTasks
    val scheduledSectionTasks = scheduledTasks
    val inboxHierarchy = rememberTaskHierarchyPresentation(inboxSectionTasks, state.subtasksExpandedByDefault)
    val scheduledHierarchy = rememberTaskHierarchyPresentation(scheduledSectionTasks, state.subtasksExpandedByDefault)
    Box(
        modifier = Modifier
            .fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 14.dp, bottom = navBottom + 18.dp)
                .graphicsLayer { clip = false },
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TaskInboxSectionHeader(
                title = stringResource(R.string.no_date),
                count = inboxSectionTasks.size,
            )
            if (inboxSectionTasks.isEmpty()) {
                Text(
                    stringResource(R.string.no_unscheduled_tasks),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(start = 22.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                )
            }
            Column(Modifier.animateContentSize(animationSpec = tween(MotionMedium, easing = MotionStandard))) {
            inboxHierarchy.entries.forEach { entry ->
                val task = entry.task
                AnimatedTaskHierarchyEntry(entry) {
                    TaskRow(
                        task = task,
                        taskColorMode = state.taskColorMode,
                        onTaskStatusChanged = onTaskStatusChanged,
                        prominent = true,
                        hierarchyDepth = entry.depth,
                        hierarchyContinuationLevels = entry.continuationLevels,
                        hierarchyLastSibling = entry.lastSibling,
                        hasSubtasks = entry.hasChildren,
                        subtasksExpanded = entry.expanded,
                        onToggleSubtasks = { inboxHierarchy.toggle(task) },
                        outerHorizontalPadding = 18.dp,
                        outerVerticalPadding = 3.dp,
                    ) { onDetail(DetailSheet.Task(task)) }
                }
            }
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = WarmLine.copy(alpha = 0.72f), modifier = Modifier.padding(horizontal = 4.dp))
            TaskInboxSectionHeader(
                title = stringResource(R.string.with_date),
                count = scheduledSectionTasks.size,
                actionLabel = plannedSort.localizedLabel(),
                actionIcon = Icons.Default.Sort,
                onActionClick = { plannedSort = plannedSort.next() },
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { clip = false },
            ) {
                if (scheduledSectionTasks.isEmpty()) {
                    Text(
                        stringResource(R.string.no_scheduled_open_tasks),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(start = 22.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                    )
                } else {
                    var lastDate: LocalDate? = null
                    scheduledHierarchy.entries.forEach { entry ->
                        val task = entry.task
                        val date = (task.startAtMillis ?: task.dueAtMillis)?.toDate()
                        val showDate = showDateColumn && entry.depth == 0 && date != lastDate
                        AnimatedTaskHierarchyEntry(entry) {
                            ScheduledTaskRow(
                                task = task,
                                showDateColumn = showDateColumn,
                                showDate = showDate,
                                date = date,
                                taskColorMode = state.taskColorMode,
                                onTaskStatusChanged = onTaskStatusChanged,
                                hierarchyDepth = entry.depth,
                                hierarchyContinuationLevels = entry.continuationLevels,
                                hierarchyLastSibling = entry.lastSibling,
                                hasSubtasks = entry.hasChildren,
                                subtasksExpanded = entry.expanded,
                                onToggleSubtasks = { scheduledHierarchy.toggle(task) },
                                onClick = { onDetail(DetailSheet.Task(task)) },
                            )
                        }
                        if (showDateColumn && entry.depth == 0) {
                            lastDate = date
                        }
                    }
                }
            }
            if (onShowCompleted != null) {
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = WarmLine.copy(alpha = 0.72f), modifier = Modifier.padding(horizontal = 4.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onShowCompleted),
                    shape = RoundedCornerShape(14.dp),
                    // Light accent fill in light mode; a proper dark elevated surface in dark mode
                    // (the old white-blended fill stayed light and clashed with the dark UI).
                    color = if (MaterialTheme.colorScheme.background.isDark()) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        WarmBrown.blendWith(Color.White, 0.82f)
                    },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = WarmBrown, modifier = Modifier.size(20.dp))
                        Text(
                            stringResource(R.string.completed_tasks),
                            color = WarmInk,
                            fontSize = 14.sp,
                            lineHeight = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = WarmBrown.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(18.dp)
                .background(
                    Brush.verticalGradient(
                        0f to MaterialTheme.colorScheme.background.copy(alpha = 0f),
                        1f to MaterialTheme.colorScheme.background,
                    ),
                ),
        )
    }
}

@Composable
private fun CompletedTasksView(
    tasks: List<TaskEntity>,
    taskColorMode: TaskColorMode,
    subtasksExpandedByDefault: Boolean,
    onTaskStatusChanged: (String, String) -> Unit,
    onTaskClick: (TaskEntity) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(enabled = true) { onClose() }
    var query by rememberSaveable { mutableStateOf("") }
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val filtered = remember(tasks, query) {
        val q = query.trim().lowercase(Locale.ROOT)
        if (q.isEmpty()) tasks else tasks.filter {
            it.title.lowercase(Locale.ROOT).contains(q) ||
                (it.notes?.lowercase(Locale.ROOT)?.contains(q) == true) ||
                (it.categories?.lowercase(Locale.ROOT)?.contains(q) == true)
        }
    }
    val hierarchy = rememberTaskHierarchyPresentation(filtered, subtasksExpandedByDefault)
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = statusTop),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back), tint = WarmInk)
                }
                Text(
                    stringResource(R.string.completed_tasks),
                    color = WarmInk,
                    fontSize = 20.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.search_ellipsis)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )
            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (tasks.isEmpty()) stringResource(R.string.no_completed_tasks) else stringResource(R.string.no_matches),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .animateContentSize(animationSpec = tween(MotionMedium, easing = MotionStandard))
                        .padding(top = 6.dp, bottom = navBottom + 18.dp),
                ) {
                    hierarchy.entries.forEach { entry ->
                        val task = entry.task
                        AnimatedTaskHierarchyEntry(entry) {
                            TaskRow(
                                task = task,
                                taskColorMode = taskColorMode,
                                onTaskStatusChanged = onTaskStatusChanged,
                                prominent = true,
                                hierarchyDepth = entry.depth,
                                hierarchyContinuationLevels = entry.continuationLevels,
                                hierarchyLastSibling = entry.lastSibling,
                                hasSubtasks = entry.hasChildren,
                                subtasksExpanded = entry.expanded,
                                onToggleSubtasks = { hierarchy.toggle(task) },
                                outerHorizontalPadding = 16.dp,
                                outerVerticalPadding = 3.dp,
                                onClick = { onTaskClick(task) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskInboxSectionHeader(
    title: String,
    count: Int,
    actionLabel: String? = null,
    actionIcon: ImageVector? = null,
    onActionClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = WarmBrown,
            ) {
                Text(
                    count.toString(),
                    color = accentContainerContentColor(),
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
            Text(
                title,
                color = WarmInk,
                fontSize = 14.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (actionLabel != null && onActionClick != null) {
                val sortShape = RoundedCornerShape(999.dp)
                Surface(
                    modifier = Modifier
                        .width(118.dp)
                        .animateContentSize(animationSpec = tween(MotionMedium, easing = MotionStandard))
                        .clip(sortShape)
                        .clickable(onClick = onActionClick),
                    shape = sortShape,
                    color = WarmBrown,
                    border = null,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        if (actionIcon != null) {
                            Icon(actionIcon, contentDescription = null, tint = accentContainerContentColor(), modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(7.dp))
                        }
                        AnimatedContent(
                            targetState = actionLabel,
                            transitionSpec = { fadeIn(tween(MotionShort)) togetherWith fadeOut(tween(MotionShort)) },
                            label = "taskSortLabel",
                        ) { label ->
                            Text(
                                label,
                                color = accentContainerContentColor(),
                                fontSize = 12.sp,
                                lineHeight = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduledTaskRow(
    task: TaskEntity,
    showDateColumn: Boolean,
    showDate: Boolean,
    date: LocalDate?,
    taskColorMode: TaskColorMode,
    onTaskStatusChanged: (String, String) -> Unit,
    hierarchyDepth: Int = 0,
    hierarchyContinuationLevels: Set<Int> = emptySet(),
    hierarchyLastSibling: Boolean = true,
    hasSubtasks: Boolean = false,
    subtasksExpanded: Boolean = true,
    onToggleSubtasks: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { clip = false },
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Spacer(Modifier.width(16.dp))
        AnimatedVisibility(
            visible = showDateColumn,
            enter = slideInHorizontally(animationSpec = tween(MotionMedium, easing = MotionStandard)) { -it } +
                expandHorizontally(animationSpec = tween(MotionMedium, easing = MotionStandard), expandFrom = Alignment.Start) +
                fadeIn(animationSpec = tween(MotionShort, easing = MotionStandard)),
            exit = slideOutHorizontally(animationSpec = tween(MotionShort, easing = MotionStandardAccelerate)) { -it } +
                shrinkHorizontally(animationSpec = tween(MotionShort, easing = MotionStandardAccelerate), shrinkTowards = Alignment.Start) +
                fadeOut(animationSpec = tween(MotionShort, easing = MotionStandardAccelerate)),
        ) {
            MiniTaskDateColumn(
                date = date,
                visible = showDate,
                modifier = Modifier.padding(top = 7.dp),
            )
        }
        val rowHorizontalPadding by animateDpAsState(
            targetValue = 2.dp,
            animationSpec = tween(MotionMedium, easing = MotionStandard),
            label = "scheduledTaskRowHorizontalPadding",
        )
        val rowVerticalPadding by animateDpAsState(
            targetValue = 3.dp,
            animationSpec = tween(MotionMedium, easing = MotionStandard),
            label = "scheduledTaskRowVerticalPadding",
        )
        TaskRow(
            task = task,
            taskColorMode = taskColorMode,
            onTaskStatusChanged = onTaskStatusChanged,
            prominent = true,
            hierarchyDepth = hierarchyDepth,
            hierarchyContinuationLevels = hierarchyContinuationLevels,
            hierarchyLastSibling = hierarchyLastSibling,
            hasSubtasks = hasSubtasks,
            subtasksExpanded = subtasksExpanded,
            onToggleSubtasks = onToggleSubtasks,
            modifier = Modifier.weight(1f),
            outerHorizontalPadding = rowHorizontalPadding,
            outerVerticalPadding = rowVerticalPadding,
            onClick = onClick,
        )
        Spacer(Modifier.width(16.dp))
    }
}

@Composable
private fun MiniTaskDateColumn(date: LocalDate?, visible: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.width(30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (visible) {
            if (date == null) {
                Text(stringResource(R.string.no_date_short_top), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp, lineHeight = 11.sp, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.no_date_short_bottom), color = WarmInk, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.SemiBold)
            } else {
                Text(
                    date.format(DateTimeFormatter.ofPattern("MMM", LocalAppLocale.current)).replace(".", ""),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    date.dayOfMonth.toString(),
                    color = WarmInk,
                    fontSize = 17.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun EventRow(event: EventEntity, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(Color(event.displayColor())))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(event.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                Text(event.localizedTimeLabel(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun TaskRow(
    task: TaskEntity,
    taskColorMode: TaskColorMode,
    onTaskStatusChanged: (String, String) -> Unit,
    prominent: Boolean = false,
    hierarchyDepth: Int = 0,
    hierarchyContinuationLevels: Set<Int> = emptySet(),
    hierarchyLastSibling: Boolean = true,
    hasSubtasks: Boolean = false,
    subtasksExpanded: Boolean = true,
    onToggleSubtasks: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    outerHorizontalPadding: Dp? = null,
    outerVerticalPadding: Dp? = null,
    connectorStemInset: Dp? = null,
    fixedCardHeight: Dp? = null,
    fadeTextAtBottom: Boolean = false,
    priorityMotionEnabled: Boolean = true,
    detailMorphKey: String? = null,
    detailMorphFromHeader: Boolean = false,
    onClick: () -> Unit,
) {
    val hierarchyLineColor = TaskHierarchyLine
    val detailMorphProgress = if (detailMorphFromHeader) rememberTaskDetailMorphProgress() else 1f
    val subtaskArrowRotation by animateFloatAsState(
        targetValue = if (subtasksExpanded) 0f else -90f,
        animationSpec = tween(MotionMedium, easing = MotionStandard),
        label = "subtaskArrowRotation",
    )
    var lastCompleted by remember(task.resourceHref) { mutableStateOf(task.isCompleted) }
    var burstKey by remember(task.resourceHref) { mutableStateOf(0) }
    LaunchedEffect(task.isCompleted) {
        if (task.isCompleted && !lastCompleted) burstKey++
        lastCompleted = task.isCompleted
    }
    val inactive = task.isInactive()
    val taskAlpha by animateFloatAsState(
        targetValue = if (inactive) 0.62f else 1f,
        animationSpec = tween(MotionMedium, easing = MotionStandard),
        label = "taskRowAlpha",
    )
    val cardColor = Color(task.displayColor(taskColorMode))
    val renderedCardColor by animateColorAsState(
        targetValue = if (inactive) {
            cardColor.blendWith(MaterialTheme.colorScheme.background, 0.48f).copy(alpha = 1f)
        } else {
            cardColor.copy(alpha = 1f)
        },
        animationSpec = tween(MotionMedium, easing = MotionStandard),
        label = "taskRowCardColor",
    )
    val baseContentColor = if (renderedCardColor.isDark()) Color.White else Color(0xFF1C1A18)
    val contentColor = baseContentColor.copy(alpha = taskAlpha)
    val pendingAlpha = pendingDeleteAlpha(task.resourceHref)
    val motionPadding = outerHorizontalPadding ?: if (prominent) 16.dp else 0.dp
    val verticalMotionPadding = outerVerticalPadding ?: if (prominent) 5.dp else 0.dp
    val hierarchyExitProgress = LocalTaskHierarchyExitProgress.current.coerceIn(0f, 1f)
    val renderedVerticalMotionPadding = verticalMotionPadding * (1f - hierarchyExitProgress)
    val hierarchyConnectorProgress = if (hierarchyExitProgress <= 0f) {
        1f
    } else {
        1f - phasedProgress(hierarchyExitProgress, 0f, 0.72f)
    }
    val hierarchyExitCardAlpha = if (hierarchyExitProgress < 0.82f) {
        1f
    } else {
        1f - phasedProgress(hierarchyExitProgress, 0.82f, 1f)
    }
    val cardRadius = if (prominent) 14.dp else 8.dp
    val renderedCardRadius = rememberTaskDetailMorphCornerRadius(
        enabled = detailMorphKey != null,
        ownRadius = cardRadius,
        counterpartRadius = 18.dp,
    )
    val resolvedConnectorStemInset = connectorStemInset ?: (motionPadding + TaskHierarchyStemInset)
    val parentTailTarget = if (hasSubtasks && subtasksExpanded) 1f else 0f
    val parentTailProgress by animateFloatAsState(
        targetValue = parentTailTarget,
        animationSpec = tween(
            durationMillis = if (parentTailTarget == 0f) MotionShort else MotionMedium,
            easing = if (parentTailTarget == 0f) MotionStandardAccelerate else MotionEmphasized,
        ),
        label = "taskHierarchyParentTailProgress",
    )
    val priorityMotionTask = task.takeIf { priorityMotionEnabled }
    val titleFontSize = if (detailMorphFromHeader) lerpFloat(22f, 13f, detailMorphProgress).sp else if (prominent) 13.sp else 14.sp
    val titleLineHeight = if (detailMorphFromHeader) lerpFloat(26f, 16f, detailMorphProgress).sp else if (prominent) 16.sp else 17.sp
    val subtitleFontSize = if (detailMorphFromHeader) lerpFloat(12f, 11f, detailMorphProgress).sp else if (prominent) 11.sp else 12.sp
    val subtitleLineHeight = if (detailMorphFromHeader) lerpFloat(14f, 14f, detailMorphProgress).sp else if (prominent) 14.sp else 15.sp
    val checkboxBoxSize = if (detailMorphFromHeader) lerpFloat(42f, 30f, detailMorphProgress).dp else if (prominent) 30.dp else 40.dp
    val checkboxIconSize = if (detailMorphFromHeader) lerpFloat(28f, 20f, detailMorphProgress).dp else if (prominent) 20.dp else 24.dp
    val cardContentHorizontalPadding = if (detailMorphFromHeader) lerpFloat(14f, 9f, detailMorphProgress).dp else if (prominent) 9.dp else 12.dp
    val cardContentTopPadding = if (detailMorphFromHeader) lerpFloat(12f, 7f, detailMorphProgress).dp else if (prominent) 7.dp else 10.dp
    val cardContentBottomPadding = when {
        detailMorphFromHeader -> lerpFloat(12f, if (fadeTextAtBottom) 0f else 7f, detailMorphProgress).dp
        fadeTextAtBottom -> 0.dp
        prominent -> 7.dp
        else -> 10.dp
    }
    val checkboxTextSpacing = if (detailMorphFromHeader) lerpFloat(10f, 6f, detailMorphProgress).dp else if (prominent) 6.dp else 8.dp
    val titleFontWeight = if (detailMorphFromHeader) {
        FontWeight(lerpFloat(600f, 500f, detailMorphProgress).roundToInt())
    } else if (prominent) {
        FontWeight.Medium
    } else {
        FontWeight.Normal
    }
    val subtitleText = if (detailMorphFromHeader && detailMorphProgress < 0.48f) {
        stringResource(if (task.isCompleted) R.string.completed_task else R.string.task)
    } else {
        task.localizedTaskTimeLabel()
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .zIndex(
                taskHierarchyLayerZ(hierarchyDepth) +
                    (priorityMotionTask?.let { taskPriorityLayerZ(it, PriorityAnimationsEnabled) } ?: 0f),
            )
            .graphicsLayer { clip = false },
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .zIndex(0f)
                .taskHierarchyConnector(
                    depth = hierarchyDepth,
                    continuationLevels = hierarchyContinuationLevels,
                    lastSibling = hierarchyLastSibling,
                    branchEndInset = motionPadding,
                    stemInset = resolvedConnectorStemInset,
                    progress = hierarchyConnectorProgress,
                    lineColor = hierarchyLineColor,
                )
                .taskHierarchyParentTail(
                    depth = hierarchyDepth,
                    stemInset = resolvedConnectorStemInset,
                    verticalPadding = renderedVerticalMotionPadding,
                    progress = parentTailProgress,
                    lineColor = hierarchyLineColor,
                ),
        )
        Card(
            onClick = onClick,
            shape = RoundedCornerShape(renderedCardRadius),
            colors = CardDefaults.cardColors(containerColor = renderedCardColor),
            modifier = Modifier
                .padding(start = (hierarchyDepth * 18).dp)
                .padding(horizontal = motionPadding, vertical = renderedVerticalMotionPadding)
                .fillMaxWidth()
                .then(fixedCardHeight?.let { Modifier.height(it) } ?: Modifier)
                .then(detailMorphKey?.let { Modifier.taskDetailMorph(it, renderedCardRadius) } ?: Modifier)
                .zIndex(1f)
                .alpha(pendingAlpha * hierarchyExitCardAlpha)
                .graphicsLayer { clip = false }
                .taskPriorityMotion(if (inactive || !priorityMotionEnabled) null else task.priority, cardColor),
        ) {
            Box(Modifier.fillMaxWidth()) {
                TaskCardCompletionBurst(burstKey, color = contentColor, modifier = Modifier.matchParentSize())
                Row(
                    Modifier
                        .then(if (fixedCardHeight != null) Modifier.fillMaxSize() else Modifier)
                        .padding(horizontal = cardContentHorizontalPadding)
                        .padding(
                            top = cardContentTopPadding,
                            bottom = cardContentBottomPadding,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TaskStatusCheckbox(
                        status = task.effectiveStatus(),
                        tint = contentColor,
                        onStatusChange = { onTaskStatusChanged(task.resourceHref, it) },
                        boxSize = checkboxBoxSize,
                        iconSize = checkboxIconSize,
                    )
                    Spacer(Modifier.width(checkboxTextSpacing))
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .then(
                                if (fadeTextAtBottom) {
                                    Modifier
                                        .fillMaxHeight()
                                        .bottomEdgeFadeMask(10.dp)
                                } else {
                                    Modifier
                                },
                            ),
                        verticalArrangement = Arrangement.spacedBy(1.dp),
                    ) {
                        if (fadeTextAtBottom) {
                            FadingTimedText(
                                text = task.title,
                                color = contentColor,
                                fontSize = titleFontSize,
                                lineHeight = titleLineHeight,
                                fontWeight = titleFontWeight,
                                maxLines = if (prominent) 2 else 1,
                            )
                            AnimatedContent(
                                targetState = subtitleText,
                                transitionSpec = {
                                    fadeIn(tween(110, easing = MotionStandard)) togetherWith
                                        fadeOut(tween(110, easing = MotionStandardAccelerate))
                                },
                                label = "taskRowMorphSubtitle",
                            ) { text ->
                                FadingTimedText(
                                    text = text,
                                    color = contentColor.copy(alpha = 0.74f),
                                    fontSize = subtitleFontSize,
                                    lineHeight = subtitleLineHeight,
                                )
                            }
                        } else {
                            Text(
                                task.title,
                                maxLines = if (prominent) 2 else 1,
                                overflow = TextOverflow.Ellipsis,
                                color = contentColor,
                                fontSize = titleFontSize,
                                lineHeight = titleLineHeight,
                                fontWeight = titleFontWeight,
                            )
                            AnimatedContent(
                                targetState = subtitleText,
                                transitionSpec = {
                                    fadeIn(tween(110, easing = MotionStandard)) togetherWith
                                        fadeOut(tween(110, easing = MotionStandardAccelerate))
                                },
                                label = "taskRowMorphSubtitle",
                            ) { text ->
                                Text(
                                    text,
                                    color = contentColor.copy(alpha = 0.74f),
                                    fontSize = subtitleFontSize,
                                    lineHeight = subtitleLineHeight,
                                )
                            }
                        }
                    }
                    if (detailMorphFromHeader) {
                        val targetActionWidth = if (hasSubtasks && onToggleSubtasks != null) 30f else 0f
                        val actionWidth = lerpFloat(96f, targetActionWidth, detailMorphProgress).dp
                        Box(
                            modifier = Modifier
                                .width(actionWidth)
                                .height(42.dp),
                            contentAlignment = Alignment.CenterEnd,
                        ) {
                            Row(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .alpha(1f - phasedProgress(detailMorphProgress, 0.08f, 0.62f)),
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = contentColor,
                                    modifier = Modifier.size(24.dp),
                                )
                                Spacer(Modifier.width(24.dp))
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = null,
                                    tint = contentColor,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                            if (hasSubtasks && onToggleSubtasks != null) {
                                IconButton(
                                    onClick = onToggleSubtasks,
                                    modifier = Modifier
                                        .size(30.dp)
                                        .alpha(phasedProgress(detailMorphProgress, 0.42f, 0.88f)),
                                ) {
                                    Icon(
                                        Icons.Default.KeyboardArrowDown,
                                        contentDescription = stringResource(
                                            if (subtasksExpanded) R.string.collapse_subtasks else R.string.expand_subtasks,
                                        ),
                                        tint = contentColor,
                                        modifier = Modifier
                                            .size(20.dp)
                                            .graphicsLayer { rotationZ = subtaskArrowRotation },
                                    )
                                }
                            }
                        }
                    } else if (hasSubtasks && onToggleSubtasks != null) {
                        IconButton(
                            onClick = onToggleSubtasks,
                            modifier = Modifier.size(if (prominent) 30.dp else 36.dp),
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = stringResource(
                                    if (subtasksExpanded) R.string.collapse_subtasks else R.string.expand_subtasks,
                                ),
                                tint = contentColor,
                                modifier = Modifier
                                    .size(20.dp)
                                    .graphicsLayer { rotationZ = subtaskArrowRotation },
                            )
                        }
                    }
                }
                PendingMutationBadge(
                    resourceHref = task.resourceHref,
                    modifier = Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-2).dp),
                )
            }
        }
    }
}

@Composable
private fun ParentTaskPeek(
    parentTask: TaskEntity,
    taskColorMode: TaskColorMode,
    onClick: () -> Unit,
) {
    val hierarchyLineColor = TaskHierarchyLine
    val morphProgress = rememberTaskDetailMorphProgress()
    val morphCornerRadius = rememberTaskDetailMorphCornerRadius(
        enabled = true,
        ownRadius = 14.dp,
        counterpartRadius = 18.dp,
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .zIndex(201f)
            .clipToBounds()
            .taskDetailMorph(taskDetailMorphKey(parentTask), morphCornerRadius),
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .zIndex(0f)
                .drawBehind {
                    val x = TaskHierarchyStemInset.toPx()
                    val overlap = TaskHierarchyLineOverlap.toPx()
                    drawLine(
                        color = hierarchyLineColor,
                        start = Offset(x, size.height - 16.dp.toPx() * morphProgress),
                        end = Offset(x, size.height + overlap),
                        strokeWidth = 1.65.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                },
        )
        TaskRow(
            task = parentTask,
            taskColorMode = taskColorMode,
            onTaskStatusChanged = { _, _ -> onClick() },
            prominent = true,
            modifier = Modifier
                .offset(y = (-15).dp),
            outerHorizontalPadding = 0.dp,
            outerVerticalPadding = 0.dp,
            fixedCardHeight = 64.dp,
            fadeTextAtBottom = true,
            priorityMotionEnabled = false,
            detailMorphFromHeader = true,
            onClick = onClick,
        )
    }
}

@Composable
private fun TaskDetailTopFadeOverlay(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    val morphProgress = rememberTaskDetailMorphProgress()
    val fadeAlpha = if (visible) {
        phasedProgress(morphProgress, start = 0f, end = 0.34f)
    } else {
        0f
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(20.dp)
            .zIndex(1000f)
            .taskDetailFadeOverlay()
            .background(
                Brush.verticalGradient(
                    0f to MaterialTheme.colorScheme.surface.copy(alpha = fadeAlpha),
                    1f to Color.Transparent,
                ),
            ),
    )
}

@Composable
private fun TaskDetailHeaderCard(
    task: TaskEntity,
    taskColorMode: TaskColorMode,
    hasSubtasks: Boolean,
    collections: List<CollectionEntity>,
    hiddenCollectionHrefs: Set<String>,
    readOnlySource: Boolean,
    burstKey: Int,
    onTaskStatusChanged: (String, String) -> Unit,
    onEditTask: (TaskEntity) -> Unit,
    onDuplicateTask: (TaskEntity) -> Unit,
    onCopyTaskTo: (TaskEntity, String) -> Unit,
    onDeleteTask: (String) -> Unit,
    morphGeneration: Int,
) {
    val cardColor = Color(task.displayColor(taskColorMode))
    val contentColor = if (cardColor.isDark()) Color.White else Color(0xFF1C1A18)
    val morphCornerRadius = rememberTaskDetailMorphCornerRadius(
        enabled = true,
        ownRadius = 18.dp,
        counterpartRadius = 14.dp,
    )
    val morphProgress = rememberTaskDetailMorphProgress()
    val titleFontSize = lerpFloat(13f, 22f, morphProgress).sp
    val titleLineHeight = lerpFloat(16f, 26f, morphProgress).sp
    val subtitleFontSize = lerpFloat(11f, 12f, morphProgress).sp
    val subtitleLineHeight = lerpFloat(14f, 14f, morphProgress).sp
    val checkboxBoxSize = lerpFloat(30f, 42f, morphProgress).dp
    val checkboxIconSize = lerpFloat(20f, 28f, morphProgress).dp
    val checkboxContainerSize = lerpFloat(30f, 42f, morphProgress).dp
    val rowStartPadding = lerpFloat(9f, 14f, morphProgress).dp
    val rowEndPadding = lerpFloat(9f, 6f, morphProgress).dp
    val rowVerticalPadding = lerpFloat(7f, 12f, morphProgress).dp
    val actionSpaceWidth = lerpFloat(if (hasSubtasks) 30f else 0f, 96f, morphProgress).dp
    val titleFontWeight = FontWeight(lerpFloat(500f, 600f, morphProgress).roundToInt())
    val subtitleText = if (morphProgress < 0.52f) {
        task.localizedTaskTimeLabel()
    } else {
        stringResource(if (task.isCompleted) R.string.completed_task else R.string.task)
    }
    var controlsVisible by remember(task.resourceHref) { mutableStateOf(morphGeneration == 0) }
    LaunchedEffect(task.resourceHref, morphGeneration) {
        if (morphGeneration == 0) {
            controlsVisible = true
        } else {
            controlsVisible = false
            delay(TaskDetailControlsRevealDelayMs.toLong())
            controlsVisible = true
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .taskDetailMorph(taskDetailMorphKey(task), morphCornerRadius)
            .zIndex(200f)
            .graphicsLayer { clip = false }
            .background(cardColor, RoundedCornerShape(morphCornerRadius)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { clip = false }
                .padding(start = rowStartPadding, end = rowEndPadding, top = rowVerticalPadding, bottom = rowVerticalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(checkboxContainerSize), contentAlignment = Alignment.Center) {
                CompletionBurst(burstKey, color = contentColor, modifier = Modifier.size(56.dp))
                TaskStatusCheckbox(
                    status = task.effectiveStatus(),
                    tint = contentColor,
                    onStatusChange = { onTaskStatusChanged(task.resourceHref, it) },
                    boxSize = checkboxBoxSize,
                    iconSize = checkboxIconSize,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SelectionContainer {
                    Text(
                        task.title,
                        color = contentColor,
                        fontSize = titleFontSize,
                        lineHeight = titleLineHeight,
                        fontWeight = titleFontWeight,
                    )
                }
                AnimatedContent(
                    targetState = subtitleText,
                    transitionSpec = {
                        fadeIn(tween(110, easing = MotionStandard)) togetherWith
                            fadeOut(tween(110, easing = MotionStandardAccelerate))
                    },
                    label = "taskHeaderMorphSubtitle",
                ) { text ->
                    Text(
                        text,
                        color = contentColor.copy(alpha = 0.74f),
                        fontSize = subtitleFontSize,
                        lineHeight = subtitleLineHeight,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Box(
                modifier = Modifier.width(actionSpaceWidth),
                contentAlignment = Alignment.CenterEnd,
            ) {
                if (hasSubtasks && morphProgress < 1f) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(20.dp)
                            .alpha(1f - phasedProgress(morphProgress, 0.18f, 0.68f)),
                    )
                }
                TaskDetailHeaderActions(
                    visible = controlsVisible,
                    task = task,
                    collections = collections,
                    hiddenCollectionHrefs = hiddenCollectionHrefs,
                    readOnlySource = readOnlySource,
                    contentColor = contentColor,
                    onEditTask = onEditTask,
                    onDuplicateTask = onDuplicateTask,
                    onCopyTaskTo = onCopyTaskTo,
                    onDeleteTask = onDeleteTask,
                )
            }
        }
    }
}

@Composable
private fun TaskDetailHeaderActions(
    visible: Boolean,
    task: TaskEntity,
    collections: List<CollectionEntity>,
    hiddenCollectionHrefs: Set<String>,
    readOnlySource: Boolean,
    contentColor: Color,
    onEditTask: (TaskEntity) -> Unit,
    onDuplicateTask: (TaskEntity) -> Unit,
    onCopyTaskTo: (TaskEntity, String) -> Unit,
    onDeleteTask: (String) -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(MotionShort, easing = MotionStandard)) +
            scaleIn(initialScale = 0.9f, animationSpec = tween(MotionShort, easing = MotionEmphasized)),
        exit = fadeOut(tween(90, easing = MotionStandardAccelerate)),
    ) {
        Row {
            IconButton(onClick = { onEditTask(task) }) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit), tint = contentColor)
            }
            DetailOverflowMenu(
                copyTargets = collections.filter { it.supportsTasks && it.href != task.collectionHref && !it.isReadOnlyForUi() },
                hiddenCollectionHrefs = hiddenCollectionHrefs,
                recurringScopes = false,
                itemLabel = stringResource(R.string.task),
                canDelete = !readOnlySource,
                iconTint = contentColor,
                onDuplicate = { onDuplicateTask(task) },
                onCopyTo = { onCopyTaskTo(task, it) },
                onDeleteThis = {},
                onDeleteFollowing = {},
                onDeleteAll = { onDeleteTask(task.resourceHref) },
            )
        }
    }
}

@Composable
private fun DetailSheetContent(
    detail: DetailSheet,
    collections: List<CollectionEntity>,
    hiddenCollectionHrefs: Set<String>,
    accounts: List<AccountEntity>,
    problemResources: List<CalendarResourceEntity>,
    taskColorMode: TaskColorMode,
    eventFieldOrder: List<String>,
    taskFieldOrder: List<String>,
    autoLoadMapPreviews: Boolean,
    accountEmails: List<String>,
    allTasks: List<TaskEntity>,
    taskMorphGeneration: Int,
    taskMorphSourceHref: String?,
    onTaskStatusChanged: (String, String) -> Unit,
    onTaskPriorityChanged: (String, Int) -> Unit,
    onTaskProgressChanged: (String, Int) -> Unit,
    onEventParticipationChanged: (String, String) -> Unit,
    onEditEvent: (EventEntity) -> Unit,
    onDuplicateEvent: (EventEntity) -> Unit,
    onCopyEventTo: (EventEntity, String) -> Unit,
    onDeleteEvent: (String, EventDeleteScope, Long) -> Unit,
    onEditTask: (TaskEntity) -> Unit,
    onDuplicateTask: (TaskEntity) -> Unit,
    onCopyTaskTo: (TaskEntity, String) -> Unit,
    onDeleteTask: (String) -> Unit,
    onOpenSubtask: (TaskEntity, TaskEntity) -> Unit,
    onOpenParentTask: (TaskEntity) -> Unit,
    onAddSubtask: (TaskEntity) -> Unit,
    onClose: () -> Unit,
) {
    val task = (detail as? DetailSheet.Task)?.task
    val descendantTasks = remember(task?.resourceHref, allTasks) {
        task?.let { allTasks.descendantsOf(it) }.orEmpty()
    }
    val descendantTasksByUid = remember(descendantTasks) {
        descendantTasks.associateBy { it.collectionHref to it.uid }
    }
    val directSubtaskHasFollowing = remember(task?.uid, descendantTasks) {
        val directChildren = task?.let { current ->
            descendantTasks.filter { it.collectionHref == current.collectionHref && it.parentUid == current.uid }
        }.orEmpty()
        directChildren.mapIndexed { index, child -> child.uid to (index != directChildren.lastIndex) }.toMap()
    }
    fun directRootUidFor(descendant: TaskEntity): String {
        val currentTask = task ?: return descendant.uid
        var cursor = descendant
        val seen = mutableSetOf(descendant.uid)
        while (!cursor.parentUid.isNullOrBlank() && cursor.parentUid != currentTask.uid) {
            val parent = descendantTasksByUid[cursor.collectionHref to cursor.parentUid] ?: break
            if (!seen.add(parent.uid)) break
            cursor = parent
        }
        return cursor.uid
    }
    val parentTask = remember(task?.parentUid, task?.collectionHref, allTasks) {
        task?.parentUid?.takeIf { it.isNotBlank() }?.let { parentUid ->
            allTasks.firstOrNull { it.collectionHref == task.collectionHref && it.uid == parentUid }
        }
    }
    // Detail sheets always start with only the current task's direct children visible.
    // Nested descendants can be expanded explicitly and ignore the app-wide list default.
    val subtaskHierarchy = rememberTaskHierarchyPresentation(descendantTasks, expandedByDefault = false)
    val sourceCollection = remember(detail, collections) {
        collections.firstOrNull { collection ->
            when (detail) {
                is DetailSheet.Event -> collection.href == detail.event.collectionHref
                is DetailSheet.Task -> collection.href == detail.task.collectionHref
            }
        }
    }
    val readOnlySource = sourceCollection?.isReadOnlyForUi() == true
    val eventDetailCapabilities = sourceCollection?.eventEditorCapabilities() ?: EventEditorCapabilities.Full
    var lastCompleted by remember(task?.resourceHref) { mutableStateOf(task?.isCompleted ?: false) }
    var burstKey by remember(task?.resourceHref) { mutableStateOf(0) }
    LaunchedEffect(task?.isCompleted) {
        val current = task?.isCompleted ?: false
        if (current && !lastCompleted) burstKey++
        lastCompleted = current
    }
    val detailResourceHref = when (detail) {
        is DetailSheet.Event -> detail.event.resourceHref
        is DetailSheet.Task -> detail.task.resourceHref
    }
    val pendingMutation = pendingMutationFor(detailResourceHref)
    val problemResource = remember(detailResourceHref, problemResources) {
        problemResources.firstOrNull {
            it.href == detailResourceHref && !it.syncError.isNullOrBlank()
        }
    }
    val sheetScrollState = rememberScrollState()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(sheetScrollState)
            .padding(horizontal = 22.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        when {
            pendingMutation != null -> PendingSyncDetailBanner(pendingMutation)
            problemResource != null -> SyncIssueDetailBanner(problemResource.syncError.orEmpty())
        }
        when (detail) {
            is DetailSheet.Event -> {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = WarmPeach.copy(alpha = 0.42f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(Modifier.size(18.dp).clip(CircleShape).background(Color(detail.event.displayColor())))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                stringResource(R.string.event),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                lineHeight = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            SelectionContainer {
                                Text(
                                    detail.event.title,
                                    color = WarmInk,
                                    fontSize = 22.sp,
                                    lineHeight = 26.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                        val ev = detail.event
                        val recurring = ev.isRecurring || !ev.recurrenceRule.isNullOrBlank()
                        IconButton(onClick = { onEditEvent(ev) }) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit), tint = WarmInk)
                        }
                        DetailOverflowMenu(
                            copyTargets = collections.filter { it.supportsEvents && it.href != ev.collectionHref && !it.isReadOnlyForUi() },
                            hiddenCollectionHrefs = hiddenCollectionHrefs,
                            recurringScopes = recurring,
                            itemLabel = stringResource(R.string.event),
                            canDelete = !readOnlySource,
                            onDuplicate = { onDuplicateEvent(ev) },
                            onCopyTo = { onCopyEventTo(ev, it) },
                            onDeleteThis = { onDeleteEvent(ev.resourceHref, EventDeleteScope.This, ev.startsAtMillis) },
                            onDeleteFollowing = { onDeleteEvent(ev.resourceHref, EventDeleteScope.ThisAndFollowing, ev.startsAtMillis) },
                            onDeleteAll = { onDeleteEvent(ev.resourceHref, EventDeleteScope.All, ev.startsAtMillis) },
                        )
                    }
                }
            }
            is DetailSheet.Task -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { clip = false },
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { clip = false },
                    ) {
                        if (parentTask != null) {
                        ParentTaskPeek(
                            parentTask = parentTask,
                            taskColorMode = taskColorMode,
                            onClick = { onOpenParentTask(parentTask) },
                        )
                        TaskHierarchyConnectorStrip(
                            stemInset = TaskHierarchyStemInset,
                            modifier = Modifier.offset(y = (-3).dp),
                            height = 3.dp,
                            progress = rememberTaskDetailMorphProgress(),
                        )
                        }
                        TaskDetailHeaderCard(
                            task = detail.task,
                            taskColorMode = taskColorMode,
                            hasSubtasks = descendantTasks.any { it.parentUid == detail.task.uid },
                            collections = collections,
                            hiddenCollectionHrefs = hiddenCollectionHrefs,
                            readOnlySource = readOnlySource,
                            burstKey = burstKey,
                            onTaskStatusChanged = onTaskStatusChanged,
                            onEditTask = onEditTask,
                            onDuplicateTask = onDuplicateTask,
                            onCopyTaskTo = onCopyTaskTo,
                            onDeleteTask = onDeleteTask,
                            morphGeneration = taskMorphGeneration,
                        )
                        val detailMorphProgress = rememberTaskDetailMorphProgress()
                        val hierarchyProgress = if (taskMorphGeneration > 0) {
                            phasedProgress(detailMorphProgress, start = 0f, end = 0.82f)
                        } else {
                            1f
                        }
                        val hierarchyAlpha = if (taskMorphGeneration > 0) {
                            phasedProgress(detailMorphProgress, start = 0f, end = 0.38f)
                        } else {
                            1f
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .zIndex(150f)
                                .taskDetailRevealLayout(
                                    progress = hierarchyProgress,
                                    offsetY = 18.dp,
                                    alphaProgress = hierarchyAlpha,
                                ),
                        ) {
                            if (subtaskHierarchy.entries.any { it.visible }) {
                                TaskHierarchyConnectorStrip(
                                    stemInset = TaskHierarchyStemInset,
                                    progress = hierarchyProgress,
                                )
                            }
                            subtaskHierarchy.entries.forEachIndexed { entryIndex, entry ->
                                val virtualParentContinuation = if (directSubtaskHasFollowing[directRootUidFor(entry.task)] == true) {
                                    setOf(0)
                                } else {
                                    emptySet()
                                }
                                val shiftedContinuationLevels = entry.continuationLevels.map { it + 1 }.toSet() + virtualParentContinuation
                                val entryProgress = if (taskMorphGeneration > 0) {
                                    phasedProgress(
                                        detailMorphProgress,
                                        start = 0.03f + entryIndex * 0.025f,
                                        end = 0.42f + entryIndex * 0.025f,
                                    )
                                } else {
                                    1f
                                }
                                AnimatedTaskHierarchyEntry(
                                    entry = entry,
                                    modifier = Modifier.graphicsLayer {
                                        alpha = entryProgress
                                        translationY = -12.dp.toPx() * (1f - entryProgress)
                                        clip = false
                                    },
                                ) {
                                    TaskRow(
                                        task = entry.task,
                                        taskColorMode = taskColorMode,
                                        onTaskStatusChanged = onTaskStatusChanged,
                                        prominent = true,
                                        hierarchyDepth = entry.depth + 1,
                                        hierarchyContinuationLevels = shiftedContinuationLevels,
                                        hierarchyLastSibling = entry.lastSibling,
                                        hasSubtasks = entry.hasChildren,
                                        subtasksExpanded = entry.expanded,
                                        onToggleSubtasks = { subtaskHierarchy.toggle(entry.task) },
                                        outerHorizontalPadding = 0.dp,
                                        outerVerticalPadding = 3.dp,
                                        connectorStemInset = TaskHierarchyStemInset,
                                        detailMorphKey = taskDetailMorphKey(entry.task),
                                        detailMorphFromHeader = entry.task.resourceHref == taskMorphSourceHref,
                                        onClick = { onOpenSubtask(detail.task, entry.task) },
                                    )
                                }
                            }
                            if (!readOnlySource) {
                                TextButton(
                                    onClick = { onAddSubtask(detail.task) },
                                    modifier = Modifier
                                        .align(Alignment.End)
                                        .padding(top = 6.dp),
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.add_subtask))
                                }
                            }
                        }
                    }
                    TaskDetailTopFadeOverlay(
                        visible = parentTask != null,
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                }
            }
        }
        when (detail) {
            is DetailSheet.Event -> {
                EventMetadata(
                    event = detail.event,
                    accountEmails = accountEmails,
                    fieldOrder = eventFieldOrder.filter { eventDetailCapabilities.allows(it) },
                    readOnlySource = readOnlySource,
                    autoLoadMapPreviews = autoLoadMapPreviews,
                    onParticipationChanged = { partstat -> onEventParticipationChanged(detail.event.resourceHref, partstat) },
                )
                detail.sourceFootnoteText(collections, accounts)?.let { SourceFootnote(it) }
                Spacer(Modifier.height(56.dp))
            }
            is DetailSheet.Task -> {
                val detailMorphProgress = rememberTaskDetailMorphProgress()
                val supportingProgress = if (taskMorphGeneration > 0) {
                    phasedProgress(detailMorphProgress, start = 0.04f, end = 0.88f)
                } else {
                    1f
                }
                val supportingAlpha = if (taskMorphGeneration > 0) {
                    phasedProgress(detailMorphProgress, start = 0.42f, end = 0.82f)
                } else {
                    1f
                }
                Column(
                    modifier = Modifier.graphicsLayer {
                        alpha = supportingAlpha
                        translationY = 22.dp.toPx() * (1f - supportingProgress)
                        clip = false
                    },
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    TaskMetadata(
                        task = detail.task,
                        fieldOrder = taskFieldOrder,
                        autoLoadMapPreviews = autoLoadMapPreviews,
                        editable = !readOnlySource,
                        onPriorityChanged = { onTaskPriorityChanged(detail.task.resourceHref, it) },
                        onProgressChanged = { onTaskProgressChanged(detail.task.resourceHref, it) },
                    )
                    detail.sourceFootnoteText(collections, accounts)?.let { SourceFootnote(it) }
                    Spacer(Modifier.height(56.dp))
                }
            }
        }
    }
}

@Composable
private fun SyncIssueDetailBanner(syncError: String) {
    val container = if (MaterialTheme.colorScheme.background.isDark()) {
        SyncPendingOrange.copy(alpha = 0.22f)
    } else {
        SyncPendingOrange.copy(alpha = 0.18f)
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = container,
        border = BorderStroke(1.dp, SyncPendingOrange.copy(alpha = 0.58f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Default.WarningAmber, contentDescription = null, tint = SyncPendingOrange, modifier = Modifier.size(19.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    stringResource(R.string.sync_issue_title),
                    color = WarmInk,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(R.string.sync_issue_item_body),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                )
                syncError.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

internal data class TaskHierarchyEntry(
    val task: TaskEntity,
    val depth: Int,
    val hasChildren: Boolean,
    val expanded: Boolean,
    val visible: Boolean,
    val continuationLevels: Set<Int>,
    val lastSibling: Boolean,
)

internal data class TaskHierarchyPresentation(
    val entries: List<TaskHierarchyEntry>,
    val toggle: (TaskEntity) -> Unit,
)

internal val TaskHierarchyStemInset = 10.dp
private val TaskHierarchyLineOverlap = 4.dp

private fun taskHierarchyLayerZ(depth: Int): Float = 100f - depth.coerceAtLeast(0)

private fun taskPriorityLayerZ(task: TaskEntity, animationsEnabled: Boolean): Float {
    if (!animationsEnabled || task.isInactive()) return 0f
    val intensity = taskPriorityIntensity(task.priority)
    return if (intensity > 0f) 1_000f + intensity * 100f else 0f
}

@Composable
internal fun rememberTaskHierarchyPresentation(
    tasks: List<TaskEntity>,
    expandedByDefault: Boolean,
    defaultExpandedResourceHrefs: Set<String> = emptySet(),
): TaskHierarchyPresentation {
    val expansionOverrides = remember(tasks.map { it.resourceHref }.toSet(), expandedByDefault, defaultExpandedResourceHrefs) {
        mutableStateMapOf<String, Boolean>()
    }
    val entries = remember(tasks, expandedByDefault, defaultExpandedResourceHrefs, expansionOverrides.toMap()) {
        val distinctTasks = tasks.distinctBy { it.resourceHref }
        val position = distinctTasks.withIndex().associate { it.value.resourceHref to it.index }
        val byCollectionUid = distinctTasks.associateBy { it.collectionHref to it.uid }
        val globallyUniqueByUid = distinctTasks.groupBy { it.uid }
            .filterValues { it.size == 1 }
            .mapValues { it.value.single() }

        fun candidateParent(task: TaskEntity): TaskEntity? {
            val parentUid = task.parentUid?.takeIf { it.isNotBlank() } ?: return null
            return byCollectionUid[task.collectionHref to parentUid] ?: globallyUniqueByUid[parentUid]
        }

        val parentByResource = distinctTasks.associate { task ->
            var parent = candidateParent(task)
            val seen = mutableSetOf(task.resourceHref)
            while (parent != null && seen.add(parent.resourceHref)) {
                parent = candidateParent(parent)
            }
            task.resourceHref to if (parent == null) candidateParent(task) else null
        }
        val childrenByParent = distinctTasks
            .mapNotNull { child -> parentByResource[child.resourceHref]?.resourceHref?.let { it to child } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, children) -> children.sortedBy { position[it.resourceHref] } }
        val roots = distinctTasks.filter { parentByResource[it.resourceHref] == null }
        val out = mutableListOf<TaskHierarchyEntry>()
        val emitted = mutableSetOf<String>()

        fun append(
            task: TaskEntity,
            depth: Int,
            visible: Boolean,
            continuationLevels: Set<Int>,
            lastSibling: Boolean,
        ) {
            if (!emitted.add(task.resourceHref)) return
            val children = childrenByParent[task.resourceHref].orEmpty()
            val expanded = expansionOverrides[task.resourceHref]
                ?: if (task.resourceHref in defaultExpandedResourceHrefs) true else expandedByDefault
            out += TaskHierarchyEntry(
                task = task,
                depth = depth,
                hasChildren = children.isNotEmpty(),
                expanded = expanded,
                visible = visible,
                continuationLevels = continuationLevels,
                lastSibling = lastSibling,
            )
            children.forEachIndexed { index, child ->
                val childLast = index == children.lastIndex
                val childContinuationLevels = if (childLast) continuationLevels else continuationLevels + depth
                append(
                    task = child,
                    depth = depth + 1,
                    visible = visible && expanded,
                    continuationLevels = childContinuationLevels,
                    lastSibling = childLast,
                )
            }
        }
        roots.forEachIndexed { index, root ->
            append(
                task = root,
                depth = 0,
                visible = true,
                continuationLevels = emptySet(),
                lastSibling = index == roots.lastIndex,
            )
        }
        distinctTasks.filterNot { it.resourceHref in emitted }.forEach {
            append(it, 0, visible = true, continuationLevels = emptySet(), lastSibling = true)
        }
        out
    }
    return TaskHierarchyPresentation(
        entries = entries,
        toggle = { task ->
            val current = expansionOverrides[task.resourceHref]
                ?: if (task.resourceHref in defaultExpandedResourceHrefs) true else expandedByDefault
            expansionOverrides[task.resourceHref] = !current
        },
    )
}

private fun Modifier.taskHierarchyConnector(
    depth: Int,
    continuationLevels: Set<Int>,
    lastSibling: Boolean,
    branchEndInset: Dp = 0.dp,
    stemInset: Dp = 9.dp,
    progress: Float = 1f,
    lineColor: Color,
): Modifier {
    if (depth <= 0 || progress <= 0.001f) return this
    return drawBehind {
        val indent = 18.dp.toPx()
        val stemInsetPx = stemInset.toPx()
        val resolvedProgress = progress.coerceIn(0f, 1f)
        val overlapPx = TaskHierarchyLineOverlap.toPx() * resolvedProgress
        val centerY = size.height / 2f
        val color = lineColor.copy(alpha = resolvedProgress)
        val stroke = 1.65.dp.toPx()
        continuationLevels.forEach { level ->
            val x = (level * indent) + stemInsetPx
            drawLine(
                color,
                Offset(x, centerY + (-overlapPx - centerY) * resolvedProgress),
                Offset(x, centerY + (size.height + overlapPx - centerY) * resolvedProgress),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
        val branchLevel = depth - 1
        val branchX = (branchLevel * indent) + stemInsetPx
        val branchBottom = if (lastSibling) centerY else size.height + overlapPx
        drawLine(
            color,
            Offset(branchX, centerY + (-overlapPx - centerY) * resolvedProgress),
            Offset(branchX, centerY + (branchBottom - centerY) * resolvedProgress),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        val branchEndX = depth * indent + branchEndInset.toPx() + overlapPx
        drawLine(
            color,
            Offset(branchX, centerY),
            Offset(branchX + (branchEndX - branchX) * resolvedProgress, centerY),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

private fun Modifier.taskHierarchyParentTail(
    depth: Int,
    stemInset: Dp,
    verticalPadding: Dp,
    progress: Float,
    lineColor: Color,
): Modifier {
    if (progress <= 0.001f) return this
    return drawBehind {
        val indent = 18.dp.toPx()
        val x = depth * indent + stemInset.toPx()
        val stroke = 1.65.dp.toPx()
        val resolvedProgress = progress.coerceIn(0f, 1f)
        val startY = (size.height - verticalPadding.toPx() - TaskHierarchyLineOverlap.toPx()).coerceIn(0f, size.height)
        val endY = startY + (size.height - startY) * resolvedProgress
        drawLine(
            lineColor.copy(alpha = resolvedProgress),
            Offset(x, startY),
            Offset(x, endY),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun TaskHierarchyConnectorStrip(
    stemInset: Dp,
    modifier: Modifier = Modifier,
    height: Dp = 3.dp,
    progress: Float = 1f,
) {
    val lineColor = TaskHierarchyLine
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .drawBehind {
                val x = stemInset.toPx()
                val overlapPx = TaskHierarchyLineOverlap.toPx()
                val stroke = 1.65.dp.toPx()
                drawLine(
                    lineColor,
                    Offset(x, size.height + overlapPx - (size.height + overlapPx * 2f) * progress.coerceIn(0f, 1f)),
                    Offset(x, size.height + overlapPx),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            },
    )
}

@Composable
internal fun AnimatedTaskHierarchyEntry(
    entry: TaskHierarchyEntry,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val visibilityState = remember(entry.task.resourceHref) {
        MutableTransitionState(entry.visible)
    }
    visibilityState.targetState = entry.visible
    val exiting = visibilityState.currentState && !visibilityState.targetState
    AnimatedVisibility(
        visibleState = visibilityState,
        enter = expandVertically(
            expandFrom = Alignment.Top,
            clip = false,
            animationSpec = tween(MotionMedium, easing = MotionEmphasized),
        ) + fadeIn(animationSpec = tween(MotionShort, easing = MotionStandard)),
        exit = slideOutVertically(
            animationSpec = tween(MotionMedium, easing = MotionEmphasized),
        ) { -it / 6 } + shrinkVertically(
            shrinkTowards = Alignment.Top,
            clip = false,
            animationSpec = tween(MotionMedium, easing = MotionEmphasized),
        ),
        modifier = modifier
            .zIndex(
                if (exiting) {
                    -1f + entry.depth * 0.01f
                } else {
                    taskHierarchyLayerZ(entry.depth) + taskPriorityLayerZ(entry.task, PriorityAnimationsEnabled)
                },
            )
            .graphicsLayer {
                clip = false
            },
    ) {
        val hierarchyExitProgress by transition.animateFloat(
            transitionSpec = { tween(MotionMedium, easing = MotionEmphasized) },
            label = "taskHierarchyExitProgress",
        ) { state ->
            if (state == EnterExitState.PostExit) 1f else 0f
        }
        CompositionLocalProvider(LocalTaskHierarchyExitProgress provides hierarchyExitProgress) {
            content()
        }
    }
}

@Composable
private fun PendingSyncDetailBanner(mutation: PendingMutationEntity) {
    val container = if (MaterialTheme.colorScheme.background.isDark()) {
        SyncPendingOrange.copy(alpha = 0.22f)
    } else {
        SyncPendingOrange.copy(alpha = 0.18f)
    }
    val body = when (mutation.action) {
        MutationAction.Delete -> stringResource(R.string.pending_deleted)
        MutationAction.Put -> stringResource(R.string.pending_changed)
        else -> stringResource(R.string.pending_changed)
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = container,
        border = BorderStroke(1.dp, SyncPendingOrange.copy(alpha = 0.58f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Default.WarningAmber, contentDescription = null, tint = SyncPendingOrange, modifier = Modifier.size(19.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.not_synced_yet),
                    color = WarmInk,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                )
            }
        }
    }
}

@Composable
private fun SourceFootnote(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
        fontSize = 11.sp,
        lineHeight = 14.sp,
        modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 2.dp),
    )
}

@Composable
private fun DetailSheet.sourceFootnoteText(
    collections: List<CollectionEntity>,
    accounts: List<AccountEntity>,
): String? {
    val href = when (this) {
        is DetailSheet.Event -> event.collectionHref
        is DetailSheet.Task -> task.collectionHref
    }
    val collection = collections.firstOrNull { it.href == href } ?: return null
    val account = accounts.firstOrNull { it.id == collection.accountId }
    val source = when {
        collection.href.isLocalCollectionHrefUi() -> stringResource(R.string.local_calendar)
        collection.isAndroidProviderForUi() -> account?.displayName ?: stringResource(R.string.android_device_calendars)
        collection.isReadOnlyForUi() -> account?.displayName ?: collection.displayName
        else -> account?.displayName ?: account?.username ?: stringResource(R.string.source)
    }
    return stringResource(R.string.source_line, source, collection.displayName)
}

@Composable
private fun DetailOverflowMenu(
    copyTargets: List<CollectionEntity>,
    hiddenCollectionHrefs: Set<String> = emptySet(),
    recurringScopes: Boolean,
    itemLabel: String,
    canDelete: Boolean = true,
    iconTint: Color = WarmInk,
    onDuplicate: () -> Unit,
    onCopyTo: (String) -> Unit,
    onDeleteThis: () -> Unit,
    onDeleteFollowing: () -> Unit,
    onDeleteAll: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var copyExpanded by remember { mutableStateOf(false) }
    var deleteDialogOpen by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options), tint = iconTint)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(18.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.duplicate)) },
                onClick = {
                    expanded = false
                    onDuplicate()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.copy_to)) },
                enabled = copyTargets.isNotEmpty(),
                onClick = {
                    expanded = false
                    copyExpanded = true
                },
            )
            if (canDelete) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                    onClick = {
                        expanded = false
                        deleteDialogOpen = true
                    },
                )
            }
        }
        if (copyExpanded) {
            CopyTargetDialog(
                copyTargets = copyTargets,
                hiddenCollectionHrefs = hiddenCollectionHrefs,
                onDismiss = { copyExpanded = false },
                onCopyTo = { href ->
                    copyExpanded = false
                    onCopyTo(href)
                },
            )
        }
        if (deleteDialogOpen) {
            DeleteConfirmationDialog(
                itemLabel = itemLabel,
                recurringScopes = recurringScopes,
                onDismiss = { deleteDialogOpen = false },
                onDeleteThis = {
                    deleteDialogOpen = false
                    onDeleteThis()
                },
                onDeleteFollowing = {
                    deleteDialogOpen = false
                    onDeleteFollowing()
                },
                onDeleteAll = {
                    deleteDialogOpen = false
                    onDeleteAll()
                },
            )
        }
    }
}

@Composable
private fun CopyTargetDialog(
    copyTargets: List<CollectionEntity>,
    hiddenCollectionHrefs: Set<String> = emptySet(),
    onDismiss: () -> Unit,
    onCopyTo: (String) -> Unit,
) {
    AlertDialog(
        modifier = Modifier.padding(horizontal = 20.dp),
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(stringResource(R.string.copy_to), color = WarmInk, fontWeight = FontWeight.SemiBold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                copyTargets.forEach { collection ->
                    val hidden = collection.href in hiddenCollectionHrefs
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .then(if (hidden) Modifier.dashedBorder(SyncPendingOrange, 14.dp) else Modifier)
                            .clickable { onCopyTo(collection.href) }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(Color(collection.color)))
                        Text(
                            collection.displayName,
                            color = WarmInk,
                            fontSize = 16.sp,
                            lineHeight = 19.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(appString(R.string.cancel)) }
        },
    )
}

/** Which slice of a recurring event a delete action targets. */
private enum class EventDeleteScope { This, ThisAndFollowing, All }

@Composable
private fun RecurringSaveScopeDialog(
    itemLabel: String,
    onDismiss: () -> Unit,
    onSaveThis: () -> Unit,
    onSaveFollowing: () -> Unit,
    onSaveAll: () -> Unit,
) {
    AlertDialog(
        modifier = Modifier.padding(horizontal = 20.dp),
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = scopeDialogBackground(),
        titleContentColor = WarmInk,
        textContentColor = WarmInk,
        title = { Text(appString(R.string.edit_repeat_question), fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    appString(R.string.edit_series_body, itemLabel),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                )
                Spacer(Modifier.height(2.dp))
                DeleteScopeButton(appString(R.string.only_this_repeat), onClick = onSaveThis)
                DeleteScopeButton(appString(R.string.this_and_following), onClick = onSaveFollowing)
                DeleteScopeButton(appString(R.string.all_repeats), onClick = onSaveAll)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(appString(R.string.cancel)) } },
    )
}

@Composable
private fun DeleteConfirmationDialog(
    itemLabel: String,
    recurringScopes: Boolean,
    onDismiss: () -> Unit,
    onDeleteThis: () -> Unit,
    onDeleteFollowing: () -> Unit,
    onDeleteAll: () -> Unit,
) {
    if (recurringScopes) {
        // Step 1: pick a scope. Step 2: confirm that choice before anything is deleted.
        var pending by remember { mutableStateOf<EventDeleteScope?>(null) }
        val pendingScope = pending
        if (pendingScope == null) {
            // A recurring event offers three choices, laid out as a clean vertical stack of
            // full-width buttons instead of cramming them into the dialog's button row.
            AlertDialog(
                modifier = Modifier.padding(horizontal = 20.dp),
                onDismissRequest = onDismiss,
                shape = RoundedCornerShape(24.dp),
                containerColor = scopeDialogBackground(),
                titleContentColor = WarmInk,
                textContentColor = WarmInk,
                title = { Text(appString(R.string.delete_repeat_question), fontWeight = FontWeight.SemiBold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            appString(R.string.event_belongs_to_series_delete),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp,
                            lineHeight = 18.sp,
                        )
                        Spacer(Modifier.height(2.dp))
                        DeleteScopeButton(appString(R.string.only_this_event), onClick = { pending = EventDeleteScope.This })
                        DeleteScopeButton(appString(R.string.this_and_following_events), onClick = { pending = EventDeleteScope.ThisAndFollowing })
                        DeleteScopeButton(appString(R.string.all_events_in_series), destructive = true, onClick = { pending = EventDeleteScope.All })
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = onDismiss) { Text(appString(R.string.cancel)) } },
            )
        } else {
            AlertDialog(
                modifier = Modifier.padding(horizontal = 20.dp),
                onDismissRequest = { pending = null },
                shape = RoundedCornerShape(24.dp),
                containerColor = scopeDialogBackground(),
                titleContentColor = WarmInk,
                textContentColor = WarmInk,
                title = { Text(appString(R.string.truly_delete), fontWeight = FontWeight.SemiBold) },
                text = {
                    Text(
                        when (pendingScope) {
                            EventDeleteScope.This -> appString(R.string.only_this_deleted)
                            EventDeleteScope.ThisAndFollowing -> appString(R.string.this_and_following_deleted)
                            EventDeleteScope.All -> appString(R.string.all_series_deleted)
                        },
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            when (pendingScope) {
                                EventDeleteScope.This -> onDeleteThis()
                                EventDeleteScope.ThisAndFollowing -> onDeleteFollowing()
                                EventDeleteScope.All -> onDeleteAll()
                            }
                        },
                    ) { Text(appString(R.string.delete), color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { pending = null }) { Text(appString(R.string.cancel)) } },
            )
        }
    } else {
        AlertDialog(
            modifier = Modifier.padding(horizontal = 20.dp),
            onDismissRequest = onDismiss,
            shape = RoundedCornerShape(24.dp),
            containerColor = scopeDialogBackground(),
            titleContentColor = WarmInk,
            textContentColor = WarmInk,
            title = { Text(appString(R.string.delete_confirm_question, itemLabel), fontWeight = FontWeight.SemiBold) },
            text = { Text(appString(R.string.delete_irreversible)) },
            confirmButton = {
                TextButton(onClick = onDeleteAll) {
                    Text(appString(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(appString(R.string.cancel)) } },
        )
    }
}

@Composable
private fun DeleteScopeButton(text: String, destructive: Boolean = false, onClick: () -> Unit) {
    val container = if (destructive) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val outline = if (destructive) {
        MaterialTheme.colorScheme.error.copy(alpha = 0.46f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = container,
        border = BorderStroke(1.dp, outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            color = if (destructive) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
            fontSize = 15.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun scopeDialogBackground(): Color =
    popupSurfaceColor()

@Composable
private fun EventMetadata(
    event: EventEntity,
    accountEmails: List<String>,
    fieldOrder: List<String>,
    readOnlySource: Boolean,
    autoLoadMapPreviews: Boolean,
    onParticipationChanged: (String) -> Unit,
) {
    val organizer = remember(event.organizerJson) { event.organizerJson.toCalendarParticipant() }
    val attendees = remember(event.attendeesJson) { event.attendeesJson.toCalendarParticipants() }
    val accountEmailSet = remember(accountEmails) { accountEmails.map { it.lowercase(Locale.ROOT) }.toSet() }
    val ownAttendee = attendees.firstOrNull { it.email.lowercase(Locale.ROOT) in accountEmailSet }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        fieldOrder.forEach { field ->
            when (field) {
                "time" -> DetailLine(stringResource(R.string.time), event.localizedFullTimeLabel())
                "location" -> event.location?.takeIf { it.isNotBlank() }?.let { LocationDetailLine(it, event.locationMapVerified, autoLoadMapPreviews) }
                "participants" -> if (organizer != null || attendees.isNotEmpty()) {
                    SharedEventDetail(
                        organizer = organizer,
                        attendees = attendees,
                        ownAttendee = ownAttendee,
                        onParticipationChanged = onParticipationChanged,
                    )
                }
                "notes" -> event.description?.takeIf { it.isNotBlank() }?.let { DetailLine(stringResource(R.string.notes), it) }
                "categories" -> event.categories?.takeIf { it.isNotBlank() }?.let { CategoryDetailLine(stringResource(R.string.categories), it) }
                "status" -> if (!readOnlySource) {
                    DetailLine(stringResource(R.string.event_status), event.statusLabelText())
                    DetailLine(stringResource(R.string.sharing_visibility), event.classLabelText())
                    DetailLine(stringResource(R.string.availability), event.transparencyLabelText())
                }
                "recurrence" -> event.recurrenceRule?.takeIf { it.isNotBlank() }?.let { DetailLine(stringResource(R.string.recurrence), it.toLocalizedRecurrenceLabel()) }
                "reminders" -> event.remindersCsv.localizedReminderSummary()?.let { DetailLine(stringResource(R.string.reminders), it) }
            }
        }
        event.syncError?.takeIf { it.isNotBlank() }?.let { DetailLine(stringResource(R.string.sync), it) }
    }
}

@Composable
private fun SharedEventDetail(
    organizer: CalendarParticipant?,
    attendees: List<CalendarParticipant>,
    ownAttendee: CalendarParticipant?,
    onParticipationChanged: (String) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.participants), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold)
            organizer?.let {
                Text(stringResource(R.string.organized_by, it.displayName), color = WarmInk, fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium)
            }
            attendees.forEach { attendee ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ParticipantCircle(
                        label = attendee.initials(),
                        color = attendee.avatarColor(),
                        contentColor = Color.White,
                        borderColor = attendee.displayParticipationColor(),
                        modifier = Modifier.size(34.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        SelectionContainer {
                            Text(attendee.displayName, color = WarmInk, fontSize = 14.sp, lineHeight = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(attendee.email, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(attendee.localizedDisplayParticipationLabel(), color = attendee.displayParticipationColor(), fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            ownAttendee?.let {
                HorizontalDivider(color = WarmLine.copy(alpha = 0.72f))
                Text(stringResource(R.string.my_participation), color = WarmInk, fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold)
                FadedHorizontalScrollRow(contentPadding = PaddingValues(horizontal = 0.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("ACCEPTED" to stringResource(R.string.accept), "TENTATIVE" to stringResource(R.string.tentative), "DECLINED" to stringResource(R.string.decline)).forEach { (value, label) ->
                        FilterChip(
                            selected = it.partstat.equals(value, ignoreCase = true),
                            onClick = { onParticipationChanged(value) },
                            label = { Text(label) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskMetadata(
    task: TaskEntity,
    fieldOrder: List<String>,
    autoLoadMapPreviews: Boolean,
    editable: Boolean,
    onPriorityChanged: (Int) -> Unit,
    onProgressChanged: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        fieldOrder.forEach { field ->
            when (field) {
                "status" -> DetailLine(stringResource(R.string.status), task.localizedStatusLabel())
                "time" -> DetailLine(stringResource(R.string.time), task.localizedTaskTimeLabel())
                "location" -> task.location?.takeIf { it.isNotBlank() }?.let { LocationDetailLine(it, task.locationMapVerified, autoLoadMapPreviews) }
                "notes" -> task.notes?.takeIf { it.isNotBlank() }?.let { DetailLine(stringResource(R.string.note), it) }
                "url" -> task.url?.takeIf { it.isNotBlank() }?.let { DetailLine(stringResource(R.string.url), it) }
                "priority" -> task.priority?.let {
                    if (editable) EditablePriorityLine(it, onPriorityChanged) else ReadOnlyPriorityLine(it)
                }
                "progress" -> {
                    val progress = task.displayProgress()
                    if (editable) EditableProgressLine(progress, onProgressChanged) else ReadOnlyProgressLine(progress)
                }
                "tags" -> task.categories?.takeIf { it.isNotBlank() }?.let { CategoryDetailLine(stringResource(R.string.categories), it) }
                "recurrence" -> task.recurrenceRule?.takeIf { it.isNotBlank() }?.let { DetailLine(stringResource(R.string.recurrence), it.toLocalizedRecurrenceLabel()) }
                "reminders" -> task.remindersCsv.localizedReminderSummary()?.let { DetailLine(stringResource(R.string.reminders), it) }
            }
        }
        task.completedAtMillis?.let { DetailLine(stringResource(R.string.completed_on), it.toDate().toString()) }
        task.syncError?.takeIf { it.isNotBlank() }?.let { DetailLine(stringResource(R.string.sync), it) }
    }
}

@Composable
private fun EditablePriorityLine(priority: Int, onPriorityChanged: (Int) -> Unit) {
    var draftPriority by remember(priority) { mutableStateOf(priority.coerceIn(1, 9)) }
    DetailMetricLine(label = stringResource(R.string.priority), value = draftPriority.coerceIn(1, 9).toString()) {
        PrioritySlider(
            priority = draftPriority,
            onPriorityChange = { draftPriority = it },
            onPriorityChangeFinished = onPriorityChanged,
        )
    }
}

@Composable
private fun EditableProgressLine(progress: Int, onProgressChanged: (Int) -> Unit) {
    var draftProgress by remember(progress) { mutableStateOf(progress.coerceIn(0, 100)) }
    DetailMetricLine(label = stringResource(R.string.progress), value = "${draftProgress.coerceIn(0, 100)}%") {
        ProgressSlider(
            progress = draftProgress,
            onProgressChange = { draftProgress = it },
            onProgressChangeFinished = onProgressChanged,
        )
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    val displayValue = remember(value) { value.cleanCalendarDisplayText() }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(MotionMedium, easing = MotionStandard)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold)
            LinkableSelectableText(displayValue)
        }
    }
}

@Composable
private fun ReadOnlyProgressLine(progress: Int) {
    DetailMetricLine(label = stringResource(R.string.progress), value = "${progress.coerceIn(0, 100)}%") {
        ReadOnlyProgressBar(progress = progress, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ReadOnlyPriorityLine(priority: Int) {
    DetailMetricLine(label = stringResource(R.string.priority), value = priority.coerceIn(1, 9).toString()) {
        ReadOnlyPriorityBar(priority = priority, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun DetailMetricLine(
    label: String,
    value: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(MotionMedium, easing = MotionStandard)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(value, color = WarmInk, fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold)
            }
            content()
        }
    }
}

@Composable
private fun ReadOnlyProgressBar(progress: Int, modifier: Modifier = Modifier) {
    val normalized = progress.coerceIn(0, 100)
    val animated by animateFloatAsState(
        targetValue = normalized / 100f,
        animationSpec = tween(MotionMedium, easing = MotionStandard),
        label = "readonlyProgress",
    )
    Box(
        modifier = modifier
            .height(12.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(WarmLine.copy(alpha = 0.68f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(animated)
                .background(WarmBrown.copy(alpha = 0.68f)),
        )
    }
}

@Composable
private fun ReadOnlyPriorityBar(priority: Int, modifier: Modifier = Modifier) {
    val normalizedPriority = priority.coerceIn(1, 9)
    val thumbFraction = (9 - normalizedPriority) / 8f
    val trackColor = WarmLine
    Canvas(
        modifier = modifier
            .height(34.dp)
            .padding(vertical = 8.dp),
    ) {
        val barHeight = 12.dp.toPx()
        val radius = barHeight / 2f
        val centerY = size.height / 2f
        // Inset the dots by the bar's corner radius so the first/last dot sit inside the rounded
        // caps rather than poking past them.
        val dotSpanStart = radius
        val dotSpanWidth = (size.width - radius * 2f).coerceAtLeast(1f)
        val fillEnd = dotSpanStart + dotSpanWidth * thumbFraction
        drawRoundRect(
            color = trackColor.copy(alpha = 0.62f),
            topLeft = Offset(0f, centerY - barHeight / 2f),
            size = Size(size.width, barHeight),
            cornerRadius = CornerRadius(radius, radius),
        )
        (1..9).forEach { level ->
            val x = dotSpanStart + dotSpanWidth * ((9 - level) / 8f)
            drawCircle(
                color = priorityColor(level),
                radius = 4.dp.toPx(),
                center = Offset(x, centerY),
            )
        }
        // Solid active fill drawn OVER the lower-priority (higher-number) dots, like the editor
        // slider — replaces the old faint fill that sat under the dots.
        drawRoundRect(
            color = priorityColor(normalizedPriority),
            topLeft = Offset(0f, centerY - barHeight / 2f),
            size = Size(fillEnd, barHeight),
            cornerRadius = CornerRadius(radius, radius),
        )
        // Current-value marker on top of the fill so it stays visible at the fill's leading edge.
        drawCircle(color = Color.White, radius = 5.5.dp.toPx(), center = Offset(fillEnd, centerY))
        drawCircle(color = priorityColor(normalizedPriority), radius = 3.6.dp.toPx(), center = Offset(fillEnd, centerY))
    }
}

@Composable
private fun LocationDetailLine(location: String, mapVerified: Boolean?, autoLoadMapPreviews: Boolean) {
    val context = LocalContext.current
    val displayValue = remember(location) { location.cleanCalendarDisplayText() }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(MotionMedium, easing = MotionStandard)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.location), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold)
            LinkableSelectableText(displayValue, onClick = { context.openMapLocation(displayValue) })
            LocationMapPreview(displayValue, mapVerified, autoLoadMapPreviews)
        }
    }
}

@Composable
private fun LocationMapPreview(location: String, mapVerified: Boolean?, autoLoadMapPreviews: Boolean) {
    if (!location.shouldAttemptMapPreview(mapVerified)) return
    var suggestion by remember(location) { mutableStateOf<LocationSuggestion?>(null) }
    var previewRequested by remember(location, autoLoadMapPreviews) { mutableStateOf(autoLoadMapPreviews) }
    var resolved by remember(location) { mutableStateOf(false) }

    if (!previewRequested) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(
                onClick = { previewRequested = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.show_map_preview))
            }
            Text(
                stringResource(R.string.osm_attribution),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                lineHeight = 13.sp,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        return
    }

    LaunchedEffect(location, previewRequested) {
        if (previewRequested) {
            resolved = false
            suggestion = runCatching { LocationLookup.search(location, limit = 1, allowAliases = false).firstOrNull() }.getOrNull()
            resolved = true
        }
    }

    val resolvedSuggestion = suggestion ?: return
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(128.dp),
        shape = RoundedCornerShape(16.dp),
        color = WarmLine.copy(alpha = if (resolved) 0.35f else 0.18f),
        border = BorderStroke(1.dp, WarmLine.copy(alpha = 0.7f)),
    ) {
        OpenStreetMapPreview(
            suggestion = resolvedSuggestion,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun OpenStreetMapPreview(suggestion: LocationSuggestion, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val mapModifier = modifier.clickable { context.openMapLocation(suggestion) }
    var imageBitmap by remember(suggestion.latitude, suggestion.longitude) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var failed by remember(suggestion.latitude, suggestion.longitude) { mutableStateOf(false) }
    LaunchedEffect(suggestion.latitude, suggestion.longitude) {
        failed = false
        imageBitmap = runCatching {
            withContext(Dispatchers.IO) {
                LocationLookup.mapPreviewBitmap(suggestion).asImageBitmap()
            }
        }.getOrNull()
        failed = imageBitmap == null
    }
    Box(mapModifier.clipToBounds()) {
        when {
            imageBitmap != null -> Image(
                bitmap = imageBitmap!!,
                contentDescription = stringResource(R.string.map_preview_content_description),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            failed -> Box(Modifier.fillMaxSize().background(WarmLine.copy(alpha = 0.32f)))
            else -> Box(Modifier.fillMaxSize().background(WarmLine.copy(alpha = 0.18f)))
        }
        Surface(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(6.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(
                stringResource(R.string.osm_attribution),
                color = WarmInk,
                fontSize = 9.sp,
                lineHeight = 11.sp,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            )
        }
    }
}

@Composable
private fun LinkableSelectableText(value: String, onClick: (() -> Unit)? = null) {
    val textColor = WarmInk
    val linkColor = MaterialTheme.colorScheme.primary
    val highlightColor = WarmPeach.copy(alpha = 0.64f).toArgb()
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { context ->
            TextView(context).apply {
                includeFontPadding = false
                setTextIsSelectable(true)
                linksClickable = true
                movementMethod = LinkMovementMethod.getInstance()
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setLineSpacing(0f, 1.08f)
                this.highlightColor = highlightColor
            }
        },
        update = { textView ->
            val linkedText = SpannableString(value)
            Linkify.addLinks(linkedText, Linkify.WEB_URLS or Linkify.EMAIL_ADDRESSES)
            textView.text = linkedText
            textView.setTextColor(textColor.toArgb())
            textView.setLinkTextColor(linkColor.toArgb())
            textView.isClickable = onClick != null
            textView.setOnClickListener(if (onClick != null) android.view.View.OnClickListener { onClick() } else null)
        },
    )
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
private fun WelcomeScreen(
    onStartFresh: () -> Unit,
    onConnectCalendars: () -> Unit,
) {
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(120f),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp)
                .padding(top = statusTop + 30.dp, bottom = navBottom + 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(0.35f))
            Image(
                painter = painterResource(R.drawable.kgs_logo_vector),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier.size(104.dp),
                contentScale = ContentScale.Fit,
            )
            Spacer(Modifier.height(22.dp))
            Text(
                stringResource(R.string.app_name),
                color = WarmInk,
                fontSize = 31.sp,
                lineHeight = 35.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.welcome_question),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 17.sp,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.weight(0.85f))
            WelcomeActionButton(
                title = stringResource(R.string.welcome_start_fresh),
                icon = Icons.Default.Add,
                primary = false,
                onClick = onStartFresh,
            )
            Spacer(Modifier.height(12.dp))
            WelcomeActionButton(
                title = stringResource(R.string.welcome_connect_calendars),
                icon = Icons.Default.CalendarMonth,
                primary = true,
                onClick = onConnectCalendars,
            )
        }
    }
}

@Composable
private fun WelcomeActionButton(
    title: String,
    icon: ImageVector,
    primary: Boolean,
    onClick: () -> Unit,
) {
    val container = if (primary) WarmBrown else settingsControlColor()
    val content = if (primary) MaterialTheme.colorScheme.onPrimary else WarmInk
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
            .clip(SettingsControlShape)
            .clickable(onClick = onClick),
        shape = SettingsControlShape,
        color = container,
        border = if (primary) null else BorderStroke(1.dp, WarmLine),
        shadowElevation = if (primary) 8.dp else 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (primary) Color.White.copy(alpha = 0.18f) else WarmPeach),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(25.dp))
            }
            Text(
                title,
                color = content,
                fontSize = 18.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = content.copy(alpha = 0.82f), modifier = Modifier.size(24.dp))
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

private enum class SettingsDestination(val title: String) {
    Main("Settings"),
    Accounts("Calendar"),
    AddSource("Add calendar"),
    AddAccount("Add CalDAV"),
    AddReadOnly("Read-only calendar"),
    AccountDetail("Edit source"),
    Behavior("Behavior"),
    Design("Design"),
    Widgets("Widgets"),
    WidgetAgenda("Agenda widget"),
    WidgetMonth("Month widget"),
    WidgetTasks("Tasks widget"),
    WidgetMulti("Multi widget"),
    WidgetDay("Day widget"),
    Privacy("Privacy"),
    EventFieldOrder("Event fields"),
    TaskFieldOrder("Task fields"),
    Sources("Calendars & sources"),
    Reorder("Order"),
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

private fun String.shouldAttemptMapPreview(mapVerified: Boolean?): Boolean {
    if (mapVerified == false) return false
    if (mapVerified == true) return cleanCalendarDisplayText().length >= 3
    val cleaned = cleanCalendarDisplayText()
    if (cleaned.length < 8) return false
    val commaCount = cleaned.count { it == ',' }
    val hasAddressSignal = commaCount >= 2 || (commaCount >= 1 && cleaned.any { it.isDigit() })
    return hasAddressSignal && !cleaned.contains('\n')
}

private fun Context.openMapLocation(suggestion: LocationSuggestion) {
    val latLng = "${suggestion.latitude},${suggestion.longitude}"
    val encodedLabel = Uri.encode(suggestion.primaryName.ifBlank { suggestion.displayName })
    val mapIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:${suggestion.latitude},${suggestion.longitude}?q=$latLng($encodedLabel)"))
    val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openstreetmap.org/?mlat=${suggestion.latitude}&mlon=${suggestion.longitude}#map=16/${suggestion.latitude}/${suggestion.longitude}"))
    runCatching { startActivity(mapIntent) }
        .recoverCatching { startActivity(fallbackIntent) }
}

private fun Context.openMapLocation(location: String) {
    val cleaned = location.cleanCalendarDisplayText()
    if (cleaned.isBlank()) return
    val encodedQuery = Uri.encode(cleaned)
    val mapIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$encodedQuery"))
    val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openstreetmap.org/search?query=$encodedQuery"))
    runCatching { startActivity(mapIntent) }
        .recoverCatching { startActivity(fallbackIntent) }
}

private fun TaskEntity.displayProgress(): Int =
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

private fun Color.greyedOut(amount: Float): Color {
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
private fun TaskEntity.localizedStatusLabel(): String = when (status?.uppercase()) {
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

private data class ProblemItem(
    val id: String,
    val title: String,
    val body: String,
    val target: ProblemTarget? = null,
)

private sealed interface ProblemTarget {
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
private fun pendingMutationFor(resourceHref: String): PendingMutationEntity? {
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
private fun ParticipantCircle(
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

private fun CalendarParticipant.initials(): String {
    val source = displayName.ifBlank { email }
    val parts = source.split(Regex("\\s+")).filter { it.isNotBlank() }
    val initials = when {
        parts.size >= 2 -> "${parts.first().first()}${parts.last().first()}"
        source.isNotBlank() -> source.take(2)
        else -> "?"
    }
    return initials.uppercase(Locale.ROOT)
}

private fun CalendarParticipant.avatarColor(): Color {
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

private fun String.participantRoleLabel(): String =
    ParticipantRoleOption.entries.firstOrNull { it.value.equals(this, ignoreCase = true) }?.label ?: this

private fun String.eventStatusLabel(): String = when (uppercase(Locale.US)) {
    "TENTATIVE" -> "Tentative"
    "CANCELLED" -> "Cancelled"
    else -> "Confirmed"
}

@Composable
private fun EventEntity.statusLabelText(): String = when ((status ?: EventStatusOption.Confirmed.value).uppercase(Locale.US)) {
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
private fun EventEntity.classLabelText(): String = when ((classification ?: EventClassOption.Public.value).uppercase(Locale.US)) {
    "CONFIDENTIAL" -> appString(R.string.busy_only)
    "PRIVATE" -> appString(R.string.hidden)
    else -> appString(R.string.full_details)
}

private fun String.eventTransparencyLabel(): String = when (uppercase(Locale.US)) {
    "TRANSPARENT" -> "Free"
    else -> "Busy"
}

@Composable
private fun EventEntity.transparencyLabelText(): String = when ((transparency ?: EventTransparencyOption.Busy.value).uppercase(Locale.US)) {
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
private fun CalendarParticipant.localizedDisplayParticipationLabel(): String =
    localizedDeliveryStatusLabel() ?: when (partstat.uppercase(Locale.US)) {
        "ACCEPTED" -> appString(R.string.accept)
        "DECLINED" -> appString(R.string.decline)
        "TENTATIVE" -> appString(R.string.tentative)
        "DELEGATED" -> appString(R.string.delegated)
        else -> appString(R.string.no_response)
    }

@Composable
private fun CalendarParticipant.displayParticipationColor(): Color =
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
private fun TaskEntity.statusSortRank(): Int = when (effectiveStatus()) {
    "IN-PROCESS" -> 0
    "NEEDS-ACTION" -> 1
    "COMPLETED" -> 2
    "CANCELLED" -> 3
    else -> 4
}

private enum class PlannedTaskSort(val label: String) {
    Date("Date"),
    Priority("Priority"),
    Status("Status");

    fun next(): PlannedTaskSort = entries[(ordinal + 1) % entries.size]
}

@Composable
private fun PlannedTaskSort.localizedLabel(): String = when (this) {
    PlannedTaskSort.Date -> appString(R.string.date)
    PlannedTaskSort.Priority -> appString(R.string.priority)
    PlannedTaskSort.Status -> appString(R.string.status)
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

private fun String.isLocalCollectionHrefUi(): Boolean = startsWith(UiLocalCollectionPrefix)

private fun CollectionEntity.isAndroidProviderForUi(): Boolean =
    sourceType == SourceType.AndroidProvider || href.startsWith(UiAndroidCollectionPrefix)

private fun AccountEntity.isAndroidProviderForUi(): Boolean =
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

private fun String.cleanCalendarDisplayText(): String {
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
private fun String?.localizedReminderSummary(): String? {
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

private fun LocalDate.startOfDayMillis(): Long =
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

private fun EventEntity.monthOccurrenceKey(): String =
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

sealed interface CreationSheet {
    data object EventLow : CreationSheet
    data object EventFull : CreationSheet
    data object TaskLow : CreationSheet
    data object Task : CreationSheet
    data class TaskForParent(val parent: TaskEntity) : CreationSheet
    data class EditEvent(val event: EventEntity) : CreationSheet
    data class EditTask(val task: TaskEntity) : CreationSheet
    data class DuplicateEvent(val event: EventEntity) : CreationSheet
    data class DuplicateTask(val task: TaskEntity) : CreationSheet
}

private data class HiddenSaveNotice(
    val collectionHref: String,
    val kind: HiddenSaveKind,
)

private enum class HiddenSaveKind {
    Event,
    Task,
}

private sealed interface ConversionSource {
    data class Event(val event: EventEntity) : ConversionSource
    data class Task(val task: TaskEntity) : ConversionSource
}

private sealed interface RecurringSaveRequest {
    data class Event(val event: EventEntity, val payload: EventEditPayload) : RecurringSaveRequest
    data class Task(val task: TaskEntity, val payload: TaskEditPayload) : RecurringSaveRequest
}

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

private fun DetailSheet.preferredInitialSnap(): SheetSnap = when (this) {
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

private fun DetailSheet.estimatedPopoverHeight(): Dp = when (this) {
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

private fun CollectionEntity.estimatedSettingsHeight(): Dp =
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

sealed interface DetailSheet {
    data class Event(val event: EventEntity) : DetailSheet
    data class Task(val task: TaskEntity) : DetailSheet
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
private fun EventEntity.localizedTimeLabel(): String {
    if (allDay) return stringResource(R.string.all_day)
    return "${startsAtMillis.toTimeText()} - ${endsAtMillis.toTimeText()}"
}

@Composable
private fun EventEntity.localizedAgendaSpanLabel(startDate: LocalDate, endDate: LocalDate): String {
    val dateFormatter = DateTimeFormatter.ofPattern("d. MMM yyyy", LocalAppLocale.current)
    val allDayLabel = appString(R.string.all_day)
    return if (allDay) {
        "${startDate.format(dateFormatter)} - ${endDate.format(dateFormatter)}, $allDayLabel"
    } else {
        "${startDate.format(dateFormatter)} - ${endDate.format(dateFormatter)}"
    }
}

@Composable
private fun EventEntity.localizedFullTimeLabel(): String {
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
private fun TaskEntity.localizedTaskTimeLabel(): String {
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
