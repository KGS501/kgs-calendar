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
import androidx.compose.foundation.interaction.DragInteraction
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
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.runtime.State
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
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
import com.kgs.calendar.data.search.CalendarSearchMode
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
import com.kgs.calendar.ui.agenda.AgendaTimelineEntry
import com.kgs.calendar.ui.agenda.AgendaTimelinePlanner
import com.kgs.calendar.ui.agenda.AgendaTimelineRow
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
import com.kgs.calendar.ui.model.agendaEventDateSpans
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
internal fun CalendarSearchOverlay(
    visible: Boolean,
    query: String,
    searchMode: CalendarSearchMode,
    results: List<EventEntity>,
    taskResults: List<TaskEntity>,
    allTasksForHierarchy: List<TaskEntity>,
    taskColorMode: TaskColorMode,
    subtasksExpandedByDefault: Boolean,
    onQueryChange: (String) -> Unit,
    onSearchModeChange: (CalendarSearchMode) -> Unit,
    onLoadEarlierOccurrences: () -> Unit,
    onLoadLaterOccurrences: () -> Unit,
    onTaskStatusChanged: (TaskEntity, String) -> Unit,
    onEventClick: (EventEntity) -> Unit,
    onTaskClick: (TaskEntity) -> Unit,
    onClose: () -> Unit,
    showCalendarWeeks: Boolean = false,
    firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
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
            SearchOptionsBar(
                searchMode = searchMode,
                onSearchModeChange = onSearchModeChange,
            )
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
                onLoadEarlierOccurrences = onLoadEarlierOccurrences,
                onLoadLaterOccurrences = onLoadLaterOccurrences,
                agendaDateHierarchy = true,
                showCalendarWeeks = showCalendarWeeks,
                firstDayOfWeek = firstDayOfWeek,
                agendaHeaderTopSpacing = 6.dp,
            )
        }
    }
}

@Composable
private fun SearchOptionsBar(
    searchMode: CalendarSearchMode,
    onSearchModeChange: (CalendarSearchMode) -> Unit,
) {
    val labels = mapOf(
        CalendarSearchMode.Text to stringResource(R.string.search_scope_text),
        CalendarSearchMode.TextAndLabels to stringResource(R.string.search_scope_text_and_labels),
        CalendarSearchMode.LabelsOnly to stringResource(R.string.search_scope_labels_only),
    )
    Column(
        modifier = Modifier
            .testTag("search-options-bar")
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(start = 18.dp, top = 6.dp, end = 18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CalendarSearchMode.entries.forEach { mode ->
                FilterChip(
                    selected = mode == searchMode,
                    onClick = { onSearchModeChange(mode) },
                    label = { Text(labels.getValue(mode)) },
                    modifier = Modifier.testTag("searchMode-${mode.name}"),
                )
            }
        }
        HorizontalDivider(
            modifier = Modifier.testTag("search-options-divider"),
            color = WarmLine,
        )
    }
}

@Composable
private fun SearchTopBar(query: String, onQueryChange: (String) -> Unit, onClose: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    var editorValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = query,
                selection = TextRange(query.length),
            ),
        )
    }
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    LaunchedEffect(query, focused) {
        if (!focused && query.isEmpty() && editorValue.text.isNotEmpty()) {
            editorValue = TextFieldValue("")
        }
    }
    LaunchedEffect(editorValue.text) {
        delay(searchTypingDelayMillis(editorValue.text))
        if (editorValue.text != query) {
            onQueryChange(editorValue.text)
        }
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
            value = editorValue,
            onValueChange = { changed ->
                editorValue = changed
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.headlineSmall.copy(
                color = WarmInk,
                fontSize = 22.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.Normal,
            ),
            modifier = Modifier
                .testTag("search-query-field")
                .weight(1f)
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { focused = it.isFocused },
            cursorBrush = SolidColor(WarmBrown),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (editorValue.text.isBlank() && !focused) {
                        Text(stringResource(R.string.search), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 22.sp, lineHeight = 26.sp)
                    }
                    innerTextField()
                }
            },
        )
        IconButton(
            modifier = Modifier.size(38.dp),
            onClick = {
            if (editorValue.text.isBlank()) {
                onClose()
            } else {
                editorValue = TextFieldValue("")
            }
        }) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close), tint = WarmInk, modifier = Modifier.size(25.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SearchResultsList(
    query: String,
    eventResults: List<EventEntity>,
    taskResults: List<TaskEntity>,
    allTasksForHierarchy: List<TaskEntity> = taskResults,
    taskColorMode: TaskColorMode,
    subtasksExpandedByDefault: Boolean,
    onEventClick: (EventEntity) -> Unit,
    onTaskClick: (TaskEntity) -> Unit,
    onTaskStatusChanged: (TaskEntity, String) -> Unit,
    showSearchIntro: Boolean = true,
    autoScrollToNow: Boolean = query.isNotBlank() || !showSearchIntro,
    emptyMessage: String? = null,
    scrollRequestKey: Int = 0,
    scrollTargetDate: LocalDate? = null,
    stickyHeaderBackground: Color = MaterialTheme.colorScheme.surface,
    showTaskChains: Boolean = true,
    expandMultiDayEventSpans: Boolean = false,
    onLoadEarlierOccurrences: (() -> Unit)? = null,
    onLoadLaterOccurrences: (() -> Unit)? = null,
    agendaDateHierarchy: Boolean = false,
    showCalendarWeeks: Boolean = false,
    firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    compositionObserver: (() -> Unit)? = null,
    agendaHeaderTopSpacing: Dp = 0.dp,
) {
    SideEffect { compositionObserver?.invoke() }
    val resolvedEmptyMessage = emptyMessage ?: stringResource(R.string.no_results)
    val noDateLabel = stringResource(R.string.no_date)
    val recurringTaskResults = remember(taskResults, showTaskChains) {
        if (!showTaskChains) {
            emptyList()
        } else {
            taskResults.filter { !it.recurrenceRule.isNullOrBlank() || !it.rDatesCsv.isNullOrBlank() }
        }
    }
    val hierarchyTaskResults = remember(taskResults, recurringTaskResults, showTaskChains) {
        if (!showTaskChains) {
            emptyList()
        } else {
            val recurringTasks = recurringTaskResults.toHashSet()
            taskResults.filterNot(recurringTasks::contains)
        }
    }
    val hierarchySubset = remember(query, showSearchIntro, showTaskChains, hierarchyTaskResults, allTasksForHierarchy) {
        if (!showTaskChains) {
            TaskHierarchySubset(emptyList())
        } else {
            when {
                query.isNotBlank() -> allTasksForHierarchy.searchHierarchySubsetForMatches(hierarchyTaskResults)
                !showSearchIntro -> TaskHierarchySubset(
                    tasks = allTasksForHierarchy.chainSubsetForTargets(
                        targets = hierarchyTaskResults,
                        includeTargetDescendants = true,
                    ),
                )
                else -> TaskHierarchySubset(tasks = hierarchyTaskResults)
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
    val flatTaskItems = remember(showTaskChains, taskResults, recurringTaskResults) {
        val flatTasks = if (showTaskChains) recurringTaskResults else taskResults
        flatTasks
            .filter { it.agendaSortMillis() != null }
            .map { CalendarSearchResult.TaskItem(it) }
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
    val groupedResults = remember(results, agendaDateHierarchy) {
        results.groupBy { item ->
            if (agendaDateHierarchy) {
                item.date?.let(::agendaDayGroupKey) ?: noDateLabel
            } else {
                item.date?.year?.toString() ?: noDateLabel
            }
        }
    }
    val today = LocalCalendarTimeSnapshot.current.today
    val agendaPlan = remember(
        results,
        agendaDateHierarchy,
        showCalendarWeeks,
        firstDayOfWeek,
        today,
        scrollTargetDate,
    ) {
        if (agendaDateHierarchy) {
            AgendaTimelinePlanner.plan(
                entries = results.map { result ->
                    AgendaTimelineEntry(
                        key = result.stableKey(),
                        date = result.date,
                        sortMillis = result.sortMillis,
                        value = result,
                    )
                },
                today = today,
                firstDayOfWeek = firstDayOfWeek,
                showCalendarWeeks = showCalendarWeeks,
                requiredAnchorDate = scrollTargetDate,
            )
        } else {
            null
        }
    }
    val agendaRows = agendaPlan?.rows.orEmpty()
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val firstFutureKey = remember(results, today) {
        results.firstOrNull { it.isFutureSearchSection(today) }?.stableKey()
    }
    var initialAutoScrollHandled by remember { mutableStateOf(false) }
    var lastHandledScrollRequestKey by remember { mutableStateOf(scrollRequestKey) }
    val firstFutureLazyIndex = remember(
        agendaRows,
        groupedResults,
        firstFutureKey,
        today,
        agendaDateHierarchy,
        showCalendarWeeks,
    ) {
        if (firstFutureKey == null) {
            null
        } else if (agendaDateHierarchy) {
            agendaRows.indexOfFirst { row ->
                row is AgendaTimelineRow.Entry && row.entry.key == firstFutureKey
            }.takeIf { it >= 0 }
        } else {
            var lazyIndex = 0
            var found: Int? = null
            var dividerInserted = false
            for (groupItems in groupedResults.values) {
                if (found != null) break
                lazyIndex += 1 // sticky year header
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
    val requestedDateLazyIndex = remember(
        agendaRows,
        groupedResults,
        scrollTargetDate,
        showSearchIntro,
        query,
        today,
        agendaDateHierarchy,
        showCalendarWeeks,
    ) {
        val targetDate = scrollTargetDate ?: return@remember null
        if (agendaDateHierarchy) {
            return@remember agendaPlan?.anchorsByDate?.get(targetDate)?.rowIndex
        }
        var lazyIndex = 0
        var found: Int? = null
        var dividerInserted = false
        for (groupItems in groupedResults.values) {
            if (found != null) break
            lazyIndex += 1 // sticky year header
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
            val targetIndex = if (agendaDateHierarchy) {
                baseTarget
            } else {
                (baseTarget - 2).coerceAtLeast(0)
            }
            val targetOffset = if (agendaDateHierarchy || showSearchIntro) 0 else -with(density) { 38.dp.roundToPx() }
            val jumpBehavior = if (showSearchIntro) {
                AgendaJumpBehavior.Direct
            } else {
                agendaJumpBehavior(initialAutoScrollHandled, explicitAgendaRequest)
            }
            when (jumpBehavior) {
                AgendaJumpBehavior.Direct -> listState.scrollToItem(targetIndex, targetOffset)
                AgendaJumpBehavior.Animated -> listState.animateScrollToItem(targetIndex, targetOffset)
                AgendaJumpBehavior.None -> return@LaunchedEffect
            }
            initialAutoScrollHandled = true
            lastHandledScrollRequestKey = scrollRequestKey
        }
    }
    var searchGestureActive by remember(listState) { mutableStateOf(false) }
    var searchGestureSerial by remember(listState) { mutableStateOf(0) }
    var earlierRequestedGesture by remember(listState) { mutableStateOf(-1) }
    var laterRequestedGesture by remember(listState) { mutableStateOf(-1) }
    val latestGestureActive = rememberUpdatedState(searchGestureActive)
    val latestGestureSerial = rememberUpdatedState(searchGestureSerial)
    val latestEarlierRequest = rememberUpdatedState(onLoadEarlierOccurrences)
    val latestLaterRequest = rememberUpdatedState(onLoadLaterOccurrences)
    LaunchedEffect(listState, onLoadEarlierOccurrences, onLoadLaterOccurrences) {
        if (onLoadEarlierOccurrences == null && onLoadLaterOccurrences == null) return@LaunchedEffect
        listState.interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> {
                    searchGestureSerial += 1
                    searchGestureActive = true
                }
                is DragInteraction.Cancel,
                is DragInteraction.Stop -> Unit // Keep the gesture armed through any fling; scroll settlement disarms it.
            }
        }
    }
    LaunchedEffect(listState, onLoadEarlierOccurrences, onLoadLaterOccurrences) {
        if (onLoadEarlierOccurrences == null && onLoadLaterOccurrences == null) return@LaunchedEffect
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) searchGestureActive = false
        }
    }
    LaunchedEffect(listState, onLoadEarlierOccurrences, onLoadLaterOccurrences) {
        if (onLoadEarlierOccurrences == null && onLoadLaterOccurrences == null) return@LaunchedEffect
        var previousFirstIndex: Int? = null
        var previousFirstOffset = 0
        snapshotFlow {
            val layout = listState.layoutInfo
            SearchOccurrenceScrollSnapshot(
                firstVisibleItemIndex = layout.visibleItemsInfo.firstOrNull()?.index ?: 0,
                firstVisibleItemOffset = layout.visibleItemsInfo.firstOrNull()?.offset ?: 0,
                lastVisibleItemIndex = layout.visibleItemsInfo.lastOrNull()?.index ?: 0,
                totalItemsCount = layout.totalItemsCount,
            )
        }.collect { snapshot ->
            val priorIndex = previousFirstIndex
            val movingTowardLater = priorIndex != null && (
                snapshot.firstVisibleItemIndex > priorIndex ||
                    (snapshot.firstVisibleItemIndex == priorIndex && snapshot.firstVisibleItemOffset < previousFirstOffset)
                )
            val movingTowardEarlier = priorIndex != null && (
                snapshot.firstVisibleItemIndex < priorIndex ||
                    (snapshot.firstVisibleItemIndex == priorIndex && snapshot.firstVisibleItemOffset > previousFirstOffset)
                )
            previousFirstIndex = snapshot.firstVisibleItemIndex
            previousFirstOffset = snapshot.firstVisibleItemOffset
            if (!movingTowardLater && !movingTowardEarlier) return@collect
            val gesture = latestGestureSerial.value
            val edge = SearchOccurrenceLoadingPolicy.edgeToExtend(
                firstVisibleItemIndex = snapshot.firstVisibleItemIndex,
                lastVisibleItemIndex = snapshot.lastVisibleItemIndex,
                totalItemsCount = snapshot.totalItemsCount,
                scrollingTowardLater = movingTowardLater,
                userGestureActive = latestGestureActive.value,
                edgeAlreadyRequestedForGesture = if (movingTowardLater) {
                    laterRequestedGesture == gesture
                } else {
                    earlierRequestedGesture == gesture
                },
            )
            when (edge) {
                SearchOccurrenceEdge.Earlier -> {
                    earlierRequestedGesture = gesture
                    latestEarlierRequest.value?.invoke()
                }
                SearchOccurrenceEdge.Later -> {
                    laterRequestedGesture = gesture
                    latestLaterRequest.value?.invoke()
                }
                null -> Unit
            }
        }
    }
    val agendaHeaderSignals = remember(
        agendaPlan,
        agendaDateHierarchy,
    ) {
        if (!agendaDateHierarchy) {
            AgendaHeaderSignals(emptyList(), emptyList(), emptyList(), emptyList())
        } else {
            agendaPlan?.headerSignals ?: AgendaHeaderSignals(emptyList(), emptyList(), emptyList(), emptyList())
        }
    }
    val firstAgendaDate = results.firstNotNullOfOrNull(CalendarSearchResult::date)
    val agendaDateStackHeight = resolvedAgendaDateStackHeight(density.fontScale)
    val agendaDayElementHeightPx = with(density) {
        resolvedAgendaDatePushDistance(density.fontScale).roundToPx()
    }
    val agendaInformationElementHeightPx = with(density) { 22.dp.roundToPx() }
    val agendaInformationContentTopInsetPx = with(density) { 5.dp.roundToPx() }
    val displayedAgendaHeaderViewport = remember(
        listState,
        agendaHeaderSignals,
        firstAgendaDate,
        agendaDayElementHeightPx,
        agendaInformationElementHeightPx,
        agendaInformationContentTopInsetPx,
    ) {
        derivedStateOf {
            if (firstAgendaDate == null) return@derivedStateOf null
            val layout = listState.layoutInfo
            agendaStickyHeaderViewport(
                signals = agendaHeaderSignals,
                visibleItems = layout.visibleItemsInfo.map { itemInfo ->
                    AgendaVisibleHeaderItem(
                        key = itemInfo.key,
                        offset = itemInfo.offset,
                        lazyIndex = itemInfo.index,
                    )
                },
                firstVisibleItemIndex = layout.visibleItemsInfo.firstOrNull()?.index ?: 0,
                viewportStartOffset = layout.viewportStartOffset,
                informationContentTopInset = agendaInformationContentTopInsetPx,
                dayElementHeight = agendaDayElementHeightPx,
                informationElementHeight = agendaInformationElementHeightPx,
            )
        }
    }

    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        state = listState,
        modifier = Modifier
            .testTag("search-results-list")
            .fillMaxSize(),
        contentPadding = PaddingValues(
            start = 18.dp,
            top = if (agendaDateHierarchy) {
                agendaDateStackHeight + 16.dp + agendaHeaderTopSpacing
            } else {
                20.dp
            },
            end = 18.dp,
            bottom = 20.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (query.isBlank() && showSearchIntro) {
            item {
                Text(stringResource(R.string.search_events_tasks), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, lineHeight = 18.sp, modifier = Modifier.padding(start = 62.dp, top = 18.dp))
            }
        } else {
            if (results.isEmpty()) {
                item {
                    Text(resolvedEmptyMessage, color = WarmInk, fontSize = 16.sp, lineHeight = 20.sp, modifier = Modifier.padding(start = 62.dp, top = 18.dp))
                }
            } else if (agendaDateHierarchy) {
                items(
                    items = agendaRows,
                    key = { row -> row.stableKey },
                    contentType = { row -> row::class },
                ) { row ->
                    when (row) {
                        is AgendaTimelineRow.Boundary -> AgendaBoundaryMarker(
                            date = row.date,
                            changes = row.changes,
                            showCalendarWeeks = showCalendarWeeks,
                            firstDayOfWeek = firstDayOfWeek,
                        )
                        is AgendaTimelineRow.DateAnchor -> Spacer(Modifier.fillMaxWidth().height(1.dp))
                        AgendaTimelineRow.PastFutureDivider -> SearchPastFutureDivider()
                        is AgendaTimelineRow.Entry -> CalendarSearchResultRow(
                            item = row.entry.value,
                            showDate = row.showDate,
                            taskColorMode = taskColorMode,
                            onTaskStatusChanged = onTaskStatusChanged,
                            agendaDateHierarchy = true,
                            showCalendarWeeks = showCalendarWeeks,
                            firstDayOfWeek = firstDayOfWeek,
                            taskHierarchy = taskHierarchy,
                            onEventClick = onEventClick,
                            onTaskClick = onTaskClick,
                        )
                        AgendaTimelineRow.Footer -> Spacer(Modifier.height(80.dp))
                    }
                }
            } else {
                var futureDividerInserted = false
                groupedResults.forEach { (group, groupItems) ->
                    stickyHeader(key = "search-year-$group") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .zIndex(1_000f)
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
                            CalendarSearchResultRow(
                                item = item,
                                showDate = showDate,
                                taskColorMode = taskColorMode,
                                onTaskStatusChanged = onTaskStatusChanged,
                                agendaDateHierarchy = false,
                                showCalendarWeeks = showCalendarWeeks,
                                firstDayOfWeek = firstDayOfWeek,
                                taskHierarchy = taskHierarchy,
                                onEventClick = onEventClick,
                                onTaskClick = onTaskClick,
                            )
                        }
                        lastDateKey = dateKey
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
    if (agendaDateHierarchy) {
        AgendaDynamicTopHeaderState(
            viewportState = displayedAgendaHeaderViewport,
            today = today,
            showCalendarWeeks = showCalendarWeeks,
            firstDayOfWeek = firstDayOfWeek,
            background = stickyHeaderBackground,
            topSpacing = agendaHeaderTopSpacing,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
    }
}

@Composable
internal fun AgendaDynamicTopHeaderState(
    viewportState: State<AgendaStickyHeaderViewport?>,
    today: LocalDate,
    showCalendarWeeks: Boolean,
    firstDayOfWeek: DayOfWeek,
    background: Color,
    topSpacing: Dp,
    modifier: Modifier = Modifier,
) {
    val viewport = viewportState.value ?: return
    AgendaDynamicTopHeader(
        viewport = viewport,
        today = today,
        showCalendarWeeks = showCalendarWeeks,
        firstDayOfWeek = firstDayOfWeek,
        background = background,
        topSpacing = topSpacing,
        modifier = modifier,
    )
}

private data class SearchOccurrenceScrollSnapshot(
    val firstVisibleItemIndex: Int,
    val firstVisibleItemOffset: Int,
    val lastVisibleItemIndex: Int,
    val totalItemsCount: Int,
)

internal enum class AgendaJumpBehavior {
    Direct,
    Animated,
    None,
}

internal fun agendaJumpBehavior(
    initialAutoScrollHandled: Boolean,
    explicitAgendaRequest: Boolean,
): AgendaJumpBehavior = when {
    explicitAgendaRequest -> AgendaJumpBehavior.Direct
    !initialAutoScrollHandled -> AgendaJumpBehavior.Direct
    else -> AgendaJumpBehavior.None
}

internal sealed interface CalendarSearchResult {
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

internal fun CalendarSearchResult.stableKey(): String = when (this) {
    is CalendarSearchResult.Event -> "event-${event.resourceHref}-${event.startsAtMillis}-$date-$spanEndDate"
    is CalendarSearchResult.TaskGroup -> "task-group-${entries.first().task.resourceHref}"
    is CalendarSearchResult.TaskItem -> "task-${task.resourceHref}-${task.agendaSortMillis() ?: 0L}"
}

internal fun CalendarSearchResult.isFutureSearchSection(today: LocalDate): Boolean =
    date?.isBefore(today) != true

@Composable
internal fun CalendarSearchResultRow(
    item: CalendarSearchResult,
    showDate: Boolean,
    taskColorMode: TaskColorMode,
    onTaskStatusChanged: (TaskEntity, String) -> Unit,
    agendaDateHierarchy: Boolean,
    showCalendarWeeks: Boolean,
    firstDayOfWeek: DayOfWeek,
    taskHierarchy: TaskHierarchyPresentation,
    onEventClick: (EventEntity) -> Unit,
    onTaskClick: (TaskEntity) -> Unit,
) {
    when (item) {
        is CalendarSearchResult.Event -> SearchResultCard(
            event = item.event,
            displayDate = item.date,
            spanEndDate = item.spanEndDate,
            showDate = showDate,
            agendaDateHierarchy = agendaDateHierarchy,
            showCalendarWeeks = showCalendarWeeks,
            firstDayOfWeek = firstDayOfWeek,
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
            agendaDateHierarchy = agendaDateHierarchy,
            showCalendarWeeks = showCalendarWeeks,
            firstDayOfWeek = firstDayOfWeek,
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
                        agendaDateHierarchy = agendaDateHierarchy,
                        showCalendarWeeks = showCalendarWeeks,
                        firstDayOfWeek = firstDayOfWeek,
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

internal fun buildAgendaEventResults(
    events: List<EventEntity>,
    tasks: List<TaskEntity>,
): List<CalendarSearchResult.Event> =
    agendaEventDateSpans(events, tasks).map { span ->
        CalendarSearchResult.Event(span.event, span.startDate, span.endDate)
    }

@Composable
private fun AgendaDynamicTopHeader(
    viewport: AgendaStickyHeaderViewport,
    today: LocalDate,
    showCalendarWeeks: Boolean,
    firstDayOfWeek: DayOfWeek,
    background: Color,
    topSpacing: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    val informationFontSize = agendaChronologyPresentation.informationFontSizeSp
    val neutralColor = MaterialTheme.colorScheme.onSurface
    val informationStart = 18.dp + agendaChronologyPresentation.eventGutterWidth + 12.dp
    val monthLaneWidth = agendaChronologyPresentation.monthLaneWidth
    val yearLaneWidth = agendaChronologyPresentation.yearLaneWidth
    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .zIndex(1_000f),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(agendaChronologyPresentation.topHeaderHeight + topSpacing)
                .background(
                    background.copy(alpha = calendarInformationChrome.agendaStickyHeaderAlpha),
                ),
        )
        Box(
            modifier = Modifier
                .width(informationStart)
                .fillMaxHeight()
                .background(
                    background.copy(alpha = calendarInformationChrome.agendaStickyHeaderAlpha),
                ),
        )
        AgendaStickyValueLane(
            viewport = viewport.day,
            modifier = Modifier
                .testTag("agenda-header-day")
                .offset(x = 18.dp, y = topSpacing)
                .width(agendaChronologyPresentation.eventGutterWidth)
                .fillMaxHeight(),
        ) { date ->
            AgendaDateStack(
                date = date,
                color = neutralColor,
                highlighted = date == today,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = 18.dp + agendaChronologyPresentation.eventGutterWidth + 12.dp,
                    top = topSpacing,
                    end = 18.dp,
                ),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                AgendaStickyValueLane(
                    viewport = viewport.month,
                    modifier = Modifier
                        .testTag("agenda-header-month")
                        .width(monthLaneWidth)
                        .fillMaxHeight(),
                ) { monthDate ->
                    Text(
                        text = YearMonth.from(monthDate).atDay(1).format(
                            DateTimeFormatter.ofPattern("MMMM", LocalAppLocale.current),
                        ),
                        color = neutralColor,
                        fontSize = informationFontSize.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                AgendaStickyValueLane(
                    viewport = viewport.year,
                    modifier = Modifier
                        .testTag("agenda-header-year")
                        .width(yearLaneWidth)
                        .fillMaxHeight(),
                ) { yearDate ->
                    Text(
                        text = yearDate.year.toString(),
                        color = neutralColor,
                        fontSize = informationFontSize.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }
            if (showCalendarWeeks) {
                AgendaStickyValueLane(
                    viewport = viewport.week,
                    modifier = Modifier
                        .testTag("agenda-header-week")
                        .fillMaxHeight(),
                ) { weekDate ->
                    Text(
                        text = stringResource(
                            R.string.calendar_week_label,
                            weekDate.calendarWeekNumber(firstDayOfWeek),
                        ),
                        color = neutralColor,
                        fontSize = agendaChronologyPresentation.weekFontSizeSp.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun AgendaStickyValueLane(
    viewport: AgendaStickyFieldViewport,
    modifier: Modifier = Modifier,
    content: @Composable (LocalDate) -> Unit,
) {
    Box(
        modifier = modifier.clipToBounds(),
    ) {
        viewport.elements.forEach { element ->
            key(element.key) {
                Box(
                    modifier = Modifier.offset {
                        IntOffset(x = 0, y = element.offset)
                    },
                ) {
                    content(element.date)
                }
            }
        }
    }
}

@Composable
private fun AgendaDateStack(
    date: LocalDate,
    color: Color,
    highlighted: Boolean = false,
) {
    val informationFontSize = agendaChronologyPresentation.informationFontSizeSp
    val highlightColor = WarmBrown
    val highlightedTextColor = Color.White
    Column(
        modifier = Modifier
            .width(agendaChronologyPresentation.eventGutterWidth)
            .then(if (highlighted) Modifier.testTag("agenda-current-day") else Modifier)
            .then(
                if (highlighted) {
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(highlightColor)
                } else {
                    Modifier
                },
            )
            .padding(
                horizontal = agendaChronologyPresentation.dateHorizontalPadding,
                vertical = agendaChronologyPresentation.dateVerticalPadding,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            color = if (highlighted) highlightedTextColor else color,
            fontSize = informationFontSize.sp,
            lineHeight = agendaChronologyPresentation.dateLineHeightSp.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        Text(
            text = date.format(DateTimeFormatter.ofPattern("EEE", LocalAppLocale.current)).replace(".", ""),
            color = if (highlighted) highlightedTextColor else color,
            fontSize = informationFontSize.sp,
            lineHeight = agendaChronologyPresentation.dateLineHeightSp.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
internal fun AgendaBoundaryMarker(
    date: LocalDate,
    changes: AgendaBoundaryChanges,
    showCalendarWeeks: Boolean,
    firstDayOfWeek: DayOfWeek,
) {
    if (!agendaChronologyRenderingPolicy.renderValuesInsideListRows) {
        Spacer(Modifier.fillMaxWidth().height(28.dp))
        return
    }
    val informationFontSize = agendaChronologyPresentation.informationFontSizeSp
    val neutralColor = MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 5.dp, bottom = 1.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.width(agendaChronologyPresentation.eventGutterWidth))
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (changes.monthChanged) {
                Text(
                    text = date.format(DateTimeFormatter.ofPattern("MMMM", LocalAppLocale.current)),
                    color = neutralColor,
                    fontSize = informationFontSize.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
            if (changes.yearChanged) {
                Text(
                    text = date.year.toString(),
                    color = neutralColor,
                    fontSize = informationFontSize.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.weight(1f))
            if (showCalendarWeeks && changes.weekChanged) {
                Text(
                    text = stringResource(
                        R.string.calendar_week_label,
                        date.calendarWeekNumber(firstDayOfWeek),
                    ),
                    color = neutralColor,
                    fontSize = agendaChronologyPresentation.weekFontSizeSp.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
internal fun SearchPastFutureDivider() {
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
    agendaDateHierarchy: Boolean = false,
    showCalendarWeeks: Boolean = false,
    firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
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
            agendaDateHierarchy = agendaDateHierarchy,
            showCalendarWeeks = showCalendarWeeks,
            firstDayOfWeek = firstDayOfWeek,
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
    onTaskStatusChanged: (TaskEntity, String) -> Unit,
    hierarchyDepth: Int = 0,
    hierarchyContinuationLevels: Set<Int> = emptySet(),
    hierarchyLastSibling: Boolean = true,
    agendaDateHierarchy: Boolean = false,
    showCalendarWeeks: Boolean = false,
    firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
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
            SearchDateColumn(
                date = taskDate,
                muted = isMuted,
                visible = showDate,
                agendaDateHierarchy = agendaDateHierarchy,
                showCalendarWeeks = showCalendarWeeks,
                firstDayOfWeek = firstDayOfWeek,
            )
        } else {
            Spacer(Modifier.width(searchDateColumnWidth(agendaDateHierarchy)))
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
    agendaDateHierarchy: Boolean = false,
    showCalendarWeeks: Boolean = false,
    firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
) {
    if (agendaDateHierarchy) {
        AgendaEventDateColumn(
            date = date,
            muted = muted,
            visible = visible,
            height = height,
        )
        return
    }
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

private fun searchDateColumnWidth(
    agendaDateHierarchy: Boolean,
): Dp = when {
    !agendaDateHierarchy -> 50.dp
    else -> agendaChronologyPresentation.eventGutterWidth
}

@Composable
private fun AgendaEventDateColumn(
    date: LocalDate?,
    muted: Boolean,
    visible: Boolean,
    height: Dp?,
) {
    Column(
        modifier = Modifier
            .width(agendaChronologyPresentation.eventGutterWidth)
            .then(if (height != null) Modifier.height(height) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (
            agendaChronologyRenderingPolicy.renderValuesInsideListRows &&
            visible &&
            date != null
        ) {
            AgendaDateStack(
                date = date,
                color = MaterialTheme.colorScheme.onSurface.copy(
                    alpha = if (muted) 0.58f else 1f,
                ),
            )
        }
    }
}
