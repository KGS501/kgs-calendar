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
internal fun LocationSelectorField(location: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick),
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, WarmLine),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(21.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.location), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    location.ifBlank { stringResource(R.string.add_location) },
                    color = if (location.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else WarmInk,
                    fontSize = 16.sp,
                    lineHeight = 19.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun LocationPickerPage(
    initialQuery: String,
    onSelected: (LocationSelection) -> Unit,
    onClose: () -> Unit,
) {
    var query by rememberSaveable(initialQuery) { mutableStateOf(initialQuery) }
    var suggestions by remember { mutableStateOf<List<LocationSuggestion>>(emptyList()) }
    var searchedQuery by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val normalizedQuery = query.trim()
    val focusRequester = remember { FocusRequester() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var locationAnchor by remember { mutableStateOf<LocationAnchor?>(null) }
    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            locationAnchor = context.lastKnownLocationAnchor()
        }
    }

    fun searchLocations() {
        val searchQuery = normalizedQuery
        if (loading || searchQuery.length < 3) {
            suggestions = emptyList()
            searchedQuery = searchQuery
            failed = false
            return
        }
        loading = true
        failed = false
        searchedQuery = searchQuery
        scope.launch {
            runCatching { LocationLookup.search(searchQuery, anchor = locationAnchor) }
                .onSuccess {
                    if (query.trim() == searchQuery) {
                        suggestions = it
                        failed = false
                    }
                }
                .onFailure {
                    if (query.trim() == searchQuery) {
                        suggestions = emptyList()
                        failed = true
                    }
                }
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        delay(180)
        focusRequester.requestFocus()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationAnchor = context.lastKnownLocationAnchor()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier
                .editorInset()
                .height(64.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RoundEditorAction(
                icon = Icons.Default.ArrowBack,
                contentDescription = stringResource(R.string.back),
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f),
                contentColor = WarmInk,
                onClick = onClose,
            )
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    suggestions = emptyList()
                    searchedQuery = ""
                    failed = false
                },
                placeholder = { Text(stringResource(R.string.search_location)) },
                leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(20.dp)) },
                singleLine = true,
                shape = RoundedCornerShape(22.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { searchLocations() }),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
            )
            IconButton(
                onClick = { searchLocations() },
                enabled = normalizedQuery.length >= 3 && !loading,
            ) {
                Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search), tint = WarmBrown)
            }
        }
        Column(
            modifier = Modifier.padding(horizontal = EditorHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.location_search_privacy_notice),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 15.sp,
            )
            if (locationAnchor == null && ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                OutlinedButton(onClick = { locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.use_nearby_location))
                }
            }
        }
        if (loading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = EditorHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (normalizedQuery.isNotBlank()) {
                LocationChoiceRow(
                    title = normalizedQuery,
                    subtitle = stringResource(R.string.use_custom_location),
                    onClick = { onSelected(LocationSelection(normalizedQuery, mapVerified = false)) },
                )
            }
            suggestions.forEach { suggestion ->
                LocationChoiceRow(
                    title = suggestion.primaryName,
                    subtitle = suggestion.displayName,
                    onClick = { onSelected(LocationSelection(suggestion.displayName, mapVerified = true)) },
                )
            }
            if (failed) {
                Text(
                    stringResource(R.string.location_search_unavailable),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                )
            } else if (!loading && searchedQuery == normalizedQuery && normalizedQuery.length >= 3 && suggestions.isEmpty()) {
                Text(
                    stringResource(R.string.no_matching_locations),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
internal fun LocationChoiceRow(title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
        border = BorderStroke(1.dp, WarmLine.copy(alpha = 0.72f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Default.LocationOn, contentDescription = null, tint = WarmBrown, modifier = Modifier.size(21.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = WarmInk, fontSize = 16.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
internal fun PrioritySlider(
    priority: Int,
    onPriorityChange: (Int) -> Unit,
    onPriorityChangeFinished: (Int) -> Unit = {},
) {
    val trackColor = WarmLine
    var widthPx by remember { mutableStateOf(0) }
    val sliderInset = 8.dp
    val density = LocalDensity.current
    fun priorityFromX(x: Float): Int {
        val insetPx = with(density) { sliderInset.toPx() }
        val usable = (widthPx.toFloat() - insetPx * 2f).coerceAtLeast(1f)
        val fraction = ((x - insetPx) / usable).coerceIn(0f, 1f)
        return (9 - (fraction * 8f).roundToInt()).coerceIn(1, 9)
    }
    val thumbFraction = (9 - priority.coerceIn(1, 9)) / 8f
    Column(
        modifier = Modifier.padding(horizontal = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.priority), color = WarmInk, fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold)
            Text("$priority", color = priorityColor(priority), fontSize = 16.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .onSizeChanged { widthPx = it.width }
                .pointerInput(widthPx) {
                    detectTapGestures { offset ->
                        val next = priorityFromX(offset.x)
                        onPriorityChange(next)
                        onPriorityChangeFinished(next)
                    }
                }
                .pointerInput(widthPx) {
                    var dragPriority = priority.coerceIn(1, 9)
                    detectDragGestures(
                        onDragStart = { offset ->
                            dragPriority = priorityFromX(offset.x)
                            onPriorityChange(dragPriority)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            dragPriority = priorityFromX(change.position.x)
                            onPriorityChange(dragPriority)
                        },
                        onDragEnd = { onPriorityChangeFinished(dragPriority) },
                    )
                },
        ) {
            Canvas(Modifier.matchParentSize()) {
                val barHeight = 14.dp.toPx()
                val radius = barHeight / 2f
                val centerY = size.height / 2f
                val insetPx = sliderInset.toPx()
                val trackWidth = (size.width - insetPx * 2f).coerceAtLeast(1f)
                // Inset the dots (and the fill/thumb endpoints that align with them) by the bar's
                // corner radius so the first and last dot sit centred inside the rounded caps
                // instead of poking out past the ends of the grey bar.
                val dotSpanStart = insetPx + radius
                val dotSpanWidth = (trackWidth - radius * 2f).coerceAtLeast(1f)
                val thumbX = dotSpanStart + dotSpanWidth * thumbFraction
                drawRoundRect(
                    color = trackColor.copy(alpha = 0.62f),
                    topLeft = Offset(insetPx, centerY - barHeight / 2f),
                    size = Size(trackWidth, barHeight),
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
                drawRoundRect(
                    color = priorityColor(priority),
                    topLeft = Offset(insetPx, centerY - barHeight / 2f),
                    size = Size((thumbX - insetPx).coerceAtLeast(0f), barHeight),
                    cornerRadius = CornerRadius(radius, radius),
                )
                drawRoundRect(
                    color = priorityColor(priority),
                    topLeft = Offset(thumbX - 8.dp.toPx(), centerY - 12.dp.toPx()),
                    size = Size(16.dp.toPx(), 24.dp.toPx()),
                    cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.low), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Text(stringResource(R.string.high), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}

@Composable
internal fun ProgressSlider(
    progress: Int,
    onProgressChange: (Int) -> Unit,
    onProgressChangeFinished: (Int) -> Unit = {},
) {
    val trackColor = WarmLine
    val accentColor = WarmBrown
    var widthPx by remember { mutableStateOf(0) }
    val sliderInset = 12.dp
    val density = LocalDensity.current
    fun progressFromX(x: Float): Int {
        val insetPx = with(density) { sliderInset.toPx() }
        val usable = (widthPx.toFloat() - insetPx * 2f).coerceAtLeast(1f)
        return (((x - insetPx) / usable).coerceIn(0f, 1f) * 100f).roundToInt().coerceIn(0, 100)
    }
    val normalized = progress.coerceIn(0, 100)
    val fraction = normalized / 100f
    Column(
        modifier = Modifier.padding(horizontal = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.progress), color = WarmInk, fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold)
            Text("$normalized%", color = WarmBrown, fontSize = 16.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .onSizeChanged { widthPx = it.width }
                .pointerInput(widthPx) {
                    detectTapGestures { offset ->
                        val next = progressFromX(offset.x)
                        onProgressChange(next)
                        onProgressChangeFinished(next)
                    }
                }
                .pointerInput(widthPx) {
                    var dragProgress = progress.coerceIn(0, 100)
                    detectDragGestures(
                        onDragStart = { offset ->
                            dragProgress = progressFromX(offset.x)
                            onProgressChange(dragProgress)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            dragProgress = progressFromX(change.position.x)
                            onProgressChange(dragProgress)
                        },
                        onDragEnd = { onProgressChangeFinished(dragProgress) },
                    )
                },
        ) {
            Canvas(Modifier.matchParentSize()) {
                val barHeight = 12.dp.toPx()
                val radius = barHeight / 2f
                val centerY = size.height / 2f
                val insetPx = sliderInset.toPx()
                val trackWidth = (size.width - insetPx * 2f).coerceAtLeast(1f)
                drawRoundRect(
                    color = trackColor.copy(alpha = 0.68f),
                    topLeft = Offset(insetPx, centerY - barHeight / 2f),
                    size = Size(trackWidth, barHeight),
                    cornerRadius = CornerRadius(radius, radius),
                )
                drawRoundRect(
                    color = accentColor.copy(alpha = 0.52f),
                    topLeft = Offset(insetPx, centerY - barHeight / 2f),
                    size = Size(trackWidth * fraction, barHeight),
                    cornerRadius = CornerRadius(radius, radius),
                )
                val thumbX = insetPx + trackWidth * fraction
                drawCircle(
                    color = Color.White,
                    radius = 12.dp.toPx(),
                    center = Offset(thumbX, centerY),
                )
                drawCircle(
                    color = accentColor,
                    radius = 8.dp.toPx(),
                    center = Offset(thumbX, centerY),
                )
            }
        }
    }
}

internal fun priorityColor(priority: Int): Color = when (priority.coerceIn(1, 9)) {
    1 -> Color(0xFFD93025)
    2 -> Color(0xFFE7602A)
    3 -> Color(0xFFF29900)
    4 -> Color(0xFFF8C542)
    5 -> Color(0xFFFFD84D)
    6 -> Color(0xFF55A8F5)
    7 -> Color(0xFF2E8FD8)
    8 -> Color(0xFF20A386)
    else -> Color(0xFF2E7D32)
}

@Composable
internal fun ColorOverrideEditor(
    selectedColor: Int?,
    automaticColor: Int,
    onColorSelected: (Int?) -> Unit,
) {
    var colorPickerOpen by remember { mutableStateOf(false) }
    EditorSection {
        Text(stringResource(R.string.color), color = WarmInk, fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold)
        FadedHorizontalScrollRow(
            contentPadding = PaddingValues(horizontal = EditorSectionHorizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            viewportBleed = EditorSectionHorizontalPadding,
        ) {
            ColorChoiceChip(
                label = stringResource(R.string.auto),
                color = Color(automaticColor),
                selected = selectedColor == null,
                onClick = { onColorSelected(null) },
            )
            ItemColorPalette.forEach { color ->
                ColorChoiceChip(
                    label = null,
                    color = Color(color),
                    selected = selectedColor == color,
                    onClick = { onColorSelected(color) },
                )
            }
            ColorEditChip(onClick = { colorPickerOpen = true })
        }
    }
    if (colorPickerOpen) {
        ColorPickerDialog(
            initialColor = selectedColor ?: automaticColor,
            onDismiss = { colorPickerOpen = false },
            onSave = {
                onColorSelected(it)
                colorPickerOpen = false
            },
        )
    }
}

@Composable
internal fun ColorChoiceChip(label: String?, color: Color, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(17.dp)
    Surface(
        modifier = Modifier
            .height(34.dp)
            .clip(shape)
            .clickable(onClick = onClick),
        shape = shape,
        color = if (selected) WarmPeach else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) color else WarmLine),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (label == null) 8.dp else 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Box(Modifier.size(18.dp).clip(CircleShape).background(color))
            label?.let { Text(it, color = WarmInk, fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium) }
        }
    }
}

@Composable
internal fun ColorEditChip(onClick: () -> Unit) {
    val shape = RoundedCornerShape(17.dp)
    Surface(
        modifier = Modifier
            .height(34.dp)
            .clip(shape)
            .clickable(onClick = onClick),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, WarmLine),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(Icons.Default.Palette, contentDescription = null, tint = WarmBrown, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.custom), color = WarmInk, fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
internal fun ColorPickerDialog(initialColor: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    val initialHsv = remember(initialColor) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(initialColor, it) }
    }
    var hue by rememberSaveable(initialColor) { mutableFloatStateOf(initialHsv[0]) }
    var saturation by rememberSaveable(initialColor) { mutableFloatStateOf(initialHsv[1]) }
    var brightness by rememberSaveable(initialColor) { mutableFloatStateOf(initialHsv[2]) }
    val selected = Color.hsv(hue, saturation, brightness)
    fun selectPreset(color: Int) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color, hsv)
        hue = hsv[0]
        saturation = hsv[1]
        brightness = hsv[2]
    }
    ModalEditorDialog(title = appString(R.string.choose_color), onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(selected),
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ItemColorPalette) { color ->
                    val c = Color(color)
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(c)
                            .border(2.dp, if (c.toArgb() == selected.toArgb()) WarmInk else Color.White.copy(alpha = 0.9f), CircleShape)
                            .clickable { selectPreset(color) },
                    )
                }
            }
            Text(
                stringResource(R.string.custom_color),
                color = WarmInk,
                fontSize = 14.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
            SaturationBrightnessPicker(
                hue = hue,
                saturation = saturation,
                brightness = brightness,
                onChanged = { nextSaturation, nextBrightness ->
                    saturation = nextSaturation
                    brightness = nextBrightness
                },
            )
            HuePicker(
                hue = hue,
                onHueChanged = { hue = it },
            )
            DialogActions(
                onDismiss = onDismiss,
                onSave = { onSave(selected.toArgb()) },
                saveEnabled = true,
            )
        }
    }
}

@Composable
internal fun SaturationBrightnessPicker(
    hue: Float,
    saturation: Float,
    brightness: Float,
    onChanged: (Float, Float) -> Unit,
) {
    var widthPx by remember { mutableFloatStateOf(1f) }
    var heightPx by remember { mutableFloatStateOf(1f) }
    fun update(position: Offset) {
        onChanged(
            (position.x / widthPx).coerceIn(0f, 1f),
            (1f - position.y / heightPx).coerceIn(0f, 1f),
        )
    }
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(154.dp)
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(Color.White, Color.hsv(hue, 1f, 1f))))
            .drawWithContent {
                drawContent()
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            }
            .onSizeChanged {
                widthPx = it.width.toFloat().coerceAtLeast(1f)
                heightPx = it.height.toFloat().coerceAtLeast(1f)
            }
            .pointerInput(widthPx, heightPx) {
                detectTapGestures { update(it) }
            }
            .pointerInput(widthPx, heightPx) {
                detectDragGestures(
                    onDragStart = ::update,
                    onDrag = { change, _ ->
                        change.consume()
                        update(change.position)
                    },
                )
            },
    ) {
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = (saturation * widthPx - 9.dp.toPx()).roundToInt(),
                        y = ((1f - brightness) * heightPx - 9.dp.toPx()).roundToInt(),
                    )
                }
                .size(18.dp)
                .border(2.dp, Color.White, CircleShape)
                .border(1.dp, Color.Black.copy(alpha = 0.48f), CircleShape),
        )
    }
}

@Composable
internal fun HuePicker(hue: Float, onHueChanged: (Float) -> Unit) {
    var widthPx by remember { mutableFloatStateOf(1f) }
    fun update(position: Offset) {
        onHueChanged((position.x / widthPx * 360f).coerceIn(0f, 359.99f))
    }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(widthPx) {
                detectTapGestures { update(it) }
            }
            .pointerInput(widthPx) {
                detectDragGestures(
                    onDragStart = ::update,
                    onDrag = { change, _ ->
                        change.consume()
                        update(change.position)
                    },
                )
            },
    ) {
        val trackHeight = 14.dp.toPx()
        val centerY = size.height / 2f
        val radius = trackHeight / 2f
        drawRoundRect(
            brush = Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f, 1f, 1f) }),
            topLeft = Offset(0f, centerY - trackHeight / 2f),
            size = Size(size.width, trackHeight),
            cornerRadius = CornerRadius(radius, radius),
        )
        val thumbX = (hue / 360f * size.width).coerceIn(0f, size.width)
        drawCircle(Color.White, radius = 10.dp.toPx(), center = Offset(thumbX, centerY))
        drawCircle(Color.hsv(hue, 1f, 1f), radius = 7.dp.toPx(), center = Offset(thumbX, centerY))
    }
}

@Composable
internal fun TaskDateTimeEditor(
    label: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    dateText: String,
    onDateTextChange: (String) -> Unit,
    hasTime: Boolean,
    onHasTimeChange: (Boolean) -> Unit,
    timeText: String,
    onTimeTextChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Switch(enabled, onEnabledChange)
        }
        if (enabled) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    dateText,
                    onDateTextChange,
                    label = { Text(stringResource(R.string.date)) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f),
                )
                if (hasTime) {
                    OutlinedTextField(
                        timeText,
                        onTimeTextChange,
                        label = { Text(stringResource(R.string.time)) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.width(104.dp),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.time), color = WarmInk, fontSize = 15.sp, lineHeight = 18.sp)
                Switch(hasTime, onHasTimeChange)
            }
        }
    }
}

@Composable
internal fun EditorContainer(
    title: String,
    action: String,
    onAction: () -> Unit,
    onClose: () -> Unit,
    actionEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val editorScrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .animateContentSize(animationSpec = tween(MotionMedium, easing = MotionStandard)),
    ) {
        EditorTopBar(
            title = title,
            onClose = onClose,
            onSave = onAction,
            modifier = Modifier.editorInset(),
            saveEnabled = actionEnabled,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(editorScrollState),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Spacer(Modifier.height(10.dp))
            content()
            Spacer(Modifier.height(56.dp))
        }
    }
}
