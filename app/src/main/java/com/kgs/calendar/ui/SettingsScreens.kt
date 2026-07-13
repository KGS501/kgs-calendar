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
internal fun SettingsPage(
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
    onWeekViewEnabledChanged: (Boolean) -> Unit,
    onFullWeekSwipeEnabledChanged: (Boolean) -> Unit,
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
    val timelineVisibility = timelineSettingsVisibility(
        weekViewEnabled = state.weekViewEnabled,
        sidebarControlsEnabled = state.multiDaySidebarControlsEnabled,
    )
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
                                    value = state.selectedView.localizedLabel(state.weekViewEnabled),
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
                                    title = stringResource(R.string.use_week_view),
                                    checked = state.weekViewEnabled,
                                    onCheckedChange = onWeekViewEnabledChanged,
                                    subtitle = stringResource(R.string.use_week_view_help),
                                )
                                AnimatedVisibility(
                                    visible = timelineVisibility.showFullWeekSwipe,
                                    enter = fadeIn(animationSpec = tween(MotionMedium, easing = MotionStandard)) +
                                        expandVertically(animationSpec = tween(MotionMedium, easing = MotionStandard)),
                                    exit = fadeOut(animationSpec = tween(120, easing = MotionStandardAccelerate)) +
                                        shrinkVertically(animationSpec = tween(160, easing = MotionStandardAccelerate)),
                                ) {
                                    SettingsSwitchRow(
                                        title = stringResource(R.string.swipe_full_weeks),
                                        checked = state.fullWeekSwipeEnabled,
                                        onCheckedChange = onFullWeekSwipeEnabledChanged,
                                        subtitle = stringResource(R.string.swipe_full_weeks_help),
                                    )
                                }
                                AnimatedVisibility(
                                    visible = timelineVisibility.showMultiDayControls,
                                    enter = fadeIn(animationSpec = tween(MotionMedium, easing = MotionStandard)) +
                                        expandVertically(animationSpec = tween(MotionMedium, easing = MotionStandard)),
                                    exit = fadeOut(animationSpec = tween(120, easing = MotionStandardAccelerate)) +
                                        shrinkVertically(animationSpec = tween(160, easing = MotionStandardAccelerate)),
                                ) {
                                    Column {
                                        SettingsSwitchRow(
                                            title = stringResource(R.string.multi_day_sidebar_controls),
                                            checked = state.multiDaySidebarControlsEnabled,
                                            onCheckedChange = onMultiDaySidebarControlsChanged,
                                            subtitle = stringResource(R.string.multi_day_sidebar_controls_help),
                                        )
                                        AnimatedVisibility(
                                            visible = timelineVisibility.showMultiDayCount,
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
                                    }
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
                                    value = state.widgetSettings.agendaWidgetThemeMode.localizedLabel(),
                                    leadingIcon = Icons.Default.ViewAgenda,
                                ) { navigateTo(SettingsDestination.WidgetAgenda) }
                                SettingsMenuRow(
                                    title = stringResource(R.string.widget_month_name),
                                    value = state.widgetSettings.monthWidgetThemeMode.localizedLabel(),
                                    leadingIcon = Icons.Default.CalendarMonth,
                                ) { navigateTo(SettingsDestination.WidgetMonth) }
                                SettingsMenuRow(
                                    title = stringResource(R.string.widget_tasks_name),
                                    value = state.widgetSettings.tasksWidgetThemeMode.localizedLabel(),
                                    leadingIcon = Icons.Default.TaskAlt,
                                ) { navigateTo(SettingsDestination.WidgetTasks) }
                                SettingsMenuRow(
                                    title = stringResource(R.string.widget_day_name),
                                    value = state.widgetSettings.dayWidgetThemeMode.localizedLabel(),
                                    leadingIcon = Icons.Default.ViewDay,
                                ) { navigateTo(SettingsDestination.WidgetDay) }
                                SettingsMenuRow(
                                    title = stringResource(R.string.widget_multi_name),
                                    value = state.widgetSettings.multiWidgetThemeMode.localizedLabel(),
                                    leadingIcon = Icons.Default.Widgets,
                                ) { navigateTo(SettingsDestination.WidgetMulti) }
                            }
                        }
                        SettingsDestination.WidgetAgenda -> {
                            SettingsSection(title = stringResource(R.string.widget_agenda_name), icon = Icons.Default.ViewAgenda) {
                                SettingsButtonRow(
                                    label = stringResource(R.string.color_scheme),
                                    value = state.widgetSettings.agendaWidgetThemeMode.localizedLabel(),
                                    onClick = { widgetThemeDialogTarget = SettingsDestination.WidgetAgenda },
                                )
                                SettingsButtonRow(
                                    label = stringResource(R.string.appearance),
                                    value = state.widgetSettings.agendaWidgetColorMode.localizedLabel(),
                                    onClick = { widgetColorModeDialogTarget = SettingsDestination.WidgetAgenda },
                                )
                            }
                        }
                        SettingsDestination.WidgetMonth -> {
                            SettingsSection(title = stringResource(R.string.widget_month_name), icon = Icons.Default.CalendarMonth) {
                                SettingsButtonRow(
                                    label = stringResource(R.string.color_scheme),
                                    value = state.widgetSettings.monthWidgetThemeMode.localizedLabel(),
                                    onClick = { widgetThemeDialogTarget = SettingsDestination.WidgetMonth },
                                )
                                SettingsButtonRow(
                                    label = stringResource(R.string.appearance),
                                    value = state.widgetSettings.monthWidgetColorMode.localizedLabel(),
                                    onClick = { widgetColorModeDialogTarget = SettingsDestination.WidgetMonth },
                                )
                                SettingsHelpText(stringResource(R.string.widget_month_settings_help))
                            }
                        }
                        SettingsDestination.WidgetTasks -> {
                            SettingsSection(title = stringResource(R.string.widget_tasks_name), icon = Icons.Default.TaskAlt) {
                                SettingsButtonRow(
                                    label = stringResource(R.string.color_scheme),
                                    value = state.widgetSettings.tasksWidgetThemeMode.localizedLabel(),
                                    onClick = { widgetThemeDialogTarget = SettingsDestination.WidgetTasks },
                                )
                                SettingsButtonRow(
                                    label = stringResource(R.string.appearance),
                                    value = state.widgetSettings.tasksWidgetColorMode.localizedLabel(),
                                    onClick = { widgetColorModeDialogTarget = SettingsDestination.WidgetTasks },
                                )
                                SettingsButtonRow(
                                    label = stringResource(R.string.tasks_widget_display),
                                    value = state.widgetSettings.tasksWidgetDisplayMode.localizedLabel(),
                                    onClick = { tasksWidgetDisplayDialogOpen = true },
                                )
                                AnimatedVisibility(visible = state.widgetSettings.tasksWidgetDisplayMode == WidgetTaskDisplayMode.Today) {
                                    SettingsSwitchRow(
                                        title = stringResource(R.string.include_overdue_tasks),
                                        checked = state.widgetSettings.tasksWidgetIncludeOverdue,
                                        onCheckedChange = onTasksWidgetIncludeOverdueChanged,
                                    )
                                }
                                SettingsButtonRow(
                                    label = stringResource(R.string.tasks_widget_plus_action),
                                    value = state.widgetSettings.tasksWidgetCreateMode.localizedLabel(),
                                    onClick = { tasksWidgetCreateDialogOpen = true },
                                )
                                SettingsButtonRow(
                                    label = stringResource(R.string.tasks_widget_subtasks_default),
                                    value = state.widgetSettings.tasksWidgetSubtaskDefaultMode.localizedLabel(),
                                    onClick = { tasksWidgetSubtaskDefaultDialogOpen = true },
                                )
                                SettingsHelpText(stringResource(R.string.tasks_widget_settings_help))
                            }
                        }
                        SettingsDestination.WidgetDay -> {
                            SettingsSection(title = stringResource(R.string.widget_day_name), icon = Icons.Default.ViewDay) {
                                SettingsButtonRow(
                                    label = stringResource(R.string.color_scheme),
                                    value = state.widgetSettings.dayWidgetThemeMode.localizedLabel(),
                                    onClick = { widgetThemeDialogTarget = SettingsDestination.WidgetDay },
                                )
                                SettingsButtonRow(
                                    label = stringResource(R.string.appearance),
                                    value = state.widgetSettings.dayWidgetColorMode.localizedLabel(),
                                    onClick = { widgetColorModeDialogTarget = SettingsDestination.WidgetDay },
                                )
                                SettingsSliderRow(
                                    title = stringResource(R.string.day_widget_scale),
                                    subtitle = stringResource(R.string.day_widget_scale_help),
                                    value = state.widgetSettings.dayWidgetScalePercent,
                                    valueLabel = "${state.widgetSettings.dayWidgetScalePercent}%",
                                    range = SettingsStore.MIN_DAY_WIDGET_SCALE_PERCENT..SettingsStore.MAX_DAY_WIDGET_SCALE_PERCENT,
                                    step = 5,
                                    onValueChanged = onDayWidgetScaleChanged,
                                )
                                SettingsSwitchRow(
                                    title = stringResource(R.string.day_widget_start_current_hour),
                                    subtitle = stringResource(R.string.day_widget_start_current_hour_help),
                                    checked = state.widgetSettings.dayWidgetStartAtCurrentHour,
                                    onCheckedChange = onDayWidgetStartAtCurrentHourChanged,
                                )
                                AnimatedVisibility(visible = !state.widgetSettings.dayWidgetStartAtCurrentHour) {
                                    SettingsSliderRow(
                                        title = stringResource(R.string.day_widget_start_hour),
                                        subtitle = stringResource(R.string.day_widget_start_hour_help),
                                        value = state.widgetSettings.dayWidgetStartHour,
                                        valueLabel = "%02d:00".format(state.widgetSettings.dayWidgetStartHour),
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
                                    value = state.widgetSettings.multiWidgetThemeMode.localizedLabel(),
                                    onClick = { widgetThemeDialogTarget = SettingsDestination.WidgetMulti },
                                )
                                SettingsButtonRow(
                                    label = stringResource(R.string.appearance),
                                    value = state.widgetSettings.multiWidgetColorMode.localizedLabel(),
                                    onClick = { widgetColorModeDialogTarget = SettingsDestination.WidgetMulti },
                                )
                                SettingsSliderRow(
                                    title = stringResource(R.string.multi_widget_split),
                                    subtitle = stringResource(R.string.multi_widget_split_help),
                                    value = state.widgetSettings.multiWidgetMonthPercent,
                                    valueLabel = "${state.widgetSettings.multiWidgetMonthPercent}% / ${100 - state.widgetSettings.multiWidgetMonthPercent}%",
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
                            title = view.localizedLabel(state.weekViewEnabled),
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
                SettingsDestination.WidgetAgenda -> state.widgetSettings.agendaWidgetColorMode
                SettingsDestination.WidgetMonth -> state.widgetSettings.monthWidgetColorMode
                SettingsDestination.WidgetTasks -> state.widgetSettings.tasksWidgetColorMode
                SettingsDestination.WidgetDay -> state.widgetSettings.dayWidgetColorMode
                SettingsDestination.WidgetMulti -> state.widgetSettings.multiWidgetColorMode
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
                SettingsDestination.WidgetAgenda -> state.widgetSettings.agendaWidgetThemeMode
                SettingsDestination.WidgetMonth -> state.widgetSettings.monthWidgetThemeMode
                SettingsDestination.WidgetTasks -> state.widgetSettings.tasksWidgetThemeMode
                SettingsDestination.WidgetDay -> state.widgetSettings.dayWidgetThemeMode
                SettingsDestination.WidgetMulti -> state.widgetSettings.multiWidgetThemeMode
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
            selected = state.widgetSettings.tasksWidgetDisplayMode,
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
            selected = state.widgetSettings.tasksWidgetCreateMode,
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
            selected = state.widgetSettings.tasksWidgetSubtaskDefaultMode,
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
internal fun ProblemsPage(
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
internal fun CollectionSettingsSheet(
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

internal fun String?.toJsonObjectOrNull(): JSONObject? =
    takeIf { !it.isNullOrBlank() }?.let { json ->
        runCatching { JSONObject(json) }.getOrNull()
    }

internal fun JSONObject.optBooleanOrNull(name: String): Boolean? =
    if (has(name) && !isNull(name)) optBoolean(name) else null

internal fun JSONObject.optIntOrNull(name: String): Int? =
    if (has(name) && !isNull(name)) optInt(name) else null

internal fun JSONObject.optStringOrNull(name: String): String? =
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

internal fun CollectionEntity.isVisibleInSettingsCalendarList(): Boolean =
    !(href.isLocalCollectionHrefUi() && !isEnabled)
