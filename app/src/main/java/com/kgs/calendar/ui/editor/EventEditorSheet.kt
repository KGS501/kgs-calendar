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
internal fun EventEditorSheet(
    state: CalendarUiState,
    schedule: EditorScheduleState,
    onScheduleChange: (EditorScheduleState) -> Unit,
    expanded: Boolean,
    initialEvent: EventEntity?,
    transferDraft: EditorTransferDraft? = null,
    headerTitle: String? = null,
    requestTitleFocus: Boolean = false,
    readOnlyRemote: Boolean = false,
    onDraftCollectionColorChanged: (Int) -> Unit = {},
    onSave: (EventEditPayload) -> Unit,
    onSwitchToTask: (EditorTransferDraft) -> Unit,
    onOpenCalendarSources: () -> Unit = {},
    onClose: () -> Unit,
) {
    val eventCollections = remember(state.collections, state.defaultEventCollectionHref, initialEvent?.collectionHref, readOnlyRemote) {
        if (readOnlyRemote && initialEvent != null) {
            state.collections.filter { it.href == initialEvent.collectionHref }
        } else {
            state.collections
                .filter { it.supportsEvents && it.isEnabled && !it.isReadOnlyForUi() }
                .sortedWithDefaultFirst(state.defaultEventCollectionHref)
        }
    }
    var title by remember(initialEvent?.uid, transferDraft) { mutableStateOf(initialEvent?.title ?: transferDraft?.title.orEmpty()) }
    var selectedCollectionHref by remember(initialEvent?.uid, eventCollections, state.defaultEventCollectionHref) {
        val preferred = state.defaultEventCollectionHref
            ?.takeIf { href -> eventCollections.any { it.href == href } }
        mutableStateOf(
            initialEvent?.collectionHref
                ?: preferred
                ?: eventCollections.firstOrNull()?.href,
        )
    }
    val selectedCollectionIndex = remember(eventCollections, selectedCollectionHref) {
        eventCollections.indexOfFirst { it.href == selectedCollectionHref }
    }
    val dateText = schedule.startDateText
    val endDateText = schedule.endDateText
    val startText = schedule.startTimeText
    val endText = schedule.endTimeText
    val allDay = schedule.allDay
    var location by remember(initialEvent?.uid, transferDraft) { mutableStateOf(initialEvent?.location ?: transferDraft?.location.orEmpty()) }
    var locationMapVerified by remember(initialEvent?.uid, transferDraft) { mutableStateOf(initialEvent?.locationMapVerified ?: transferDraft?.locationMapVerified) }
    var manualColor by remember(initialEvent?.uid, transferDraft) { mutableStateOf(initialEvent?.manualColor ?: transferDraft?.manualColor) }
    var description by remember(initialEvent?.uid, transferDraft) { mutableStateOf(initialEvent?.description ?: transferDraft?.notes.orEmpty()) }
    var recurrenceRule by remember(initialEvent?.uid, transferDraft) { mutableStateOf(initialEvent?.recurrenceRule ?: transferDraft?.recurrenceRule.orEmpty()) }
    var eventStatus by remember(initialEvent?.uid) { mutableStateOf(initialEvent?.status ?: EventStatusOption.Confirmed.value) }
    var eventClassification by remember(initialEvent?.uid) { mutableStateOf(initialEvent?.classification ?: EventClassOption.Public.value) }
    var eventTransparency by remember(initialEvent?.uid) { mutableStateOf(initialEvent?.transparency ?: EventTransparencyOption.Busy.value) }
    var categories by remember(initialEvent?.uid, transferDraft) { mutableStateOf(initialEvent?.categories ?: transferDraft?.categories.orEmpty()) }
    val knownCategories = remember(state.events, state.datedTasks, state.inboxTasks, state.completedTasks) {
        state.allKnownCategoryTags()
    }
    var reminderMinutes by remember(initialEvent?.uid, transferDraft, state.defaultEventReminderMinutes) {
        mutableStateOf(
            when {
                initialEvent != null -> initialEvent.remindersCsv.parseReminderMinutes()
                transferDraft != null -> transferDraft.reminderMinutes
                else -> state.defaultEventReminderMinutes
            },
        )
    }
    val organizerJson = initialEvent?.organizerJson ?: remember(selectedCollectionHref, state.accounts, eventCollections) {
        val accountId = eventCollections.firstOrNull { it.href == selectedCollectionHref }?.accountId
        val account = state.accounts.firstOrNull { it.id == accountId }
        val email = account?.username?.trim()?.takeIf { it.isLikelyEmailAddress() }
        email?.let {
            JSONObject()
                .put("name", account.displayName?.takeIf { name -> name.isNotBlank() } ?: email)
                .put("email", email)
                .toString()
        }
    }
    var attendeesJson by remember(initialEvent?.uid) { mutableStateOf(initialEvent?.attendeesJson) }
    var locationPickerOpen by remember(initialEvent?.uid) { mutableStateOf(false) }
    var invalidRangeDialogOpen by remember(initialEvent?.uid) { mutableStateOf(false) }
    val invalidTimeRange = eventDateTimeRangeInvalid(
        startDateText = dateText,
        endDateText = endDateText,
        startTimeText = startText,
        endTimeText = endText,
        allDay = allDay,
    )

    fun currentTransferDraft(): EditorTransferDraft =
        EditorTransferDraft(
            title = title,
            notes = description,
            location = location,
            locationMapVerified = locationMapVerified,
            manualColor = manualColor,
            categories = categories,
            recurrenceRule = recurrenceRule,
            reminderMinutes = reminderMinutes,
            sourceDefaultReminderMinutes = transferDraft?.sourceDefaultReminderMinutes
                ?: if (initialEvent == null) state.defaultEventReminderMinutes else emptySet(),
            date = runCatching { LocalDate.parse(dateText) }.getOrNull(),
            endDate = runCatching { LocalDate.parse(endDateText) }.getOrNull(),
            startTime = if (allDay) null else runCatching { LocalTime.parse(startText) }.getOrNull(),
            endTime = if (allDay) null else runCatching { LocalTime.parse(endText) }.getOrNull(),
            allDay = allDay,
            schedule = schedule,
        )

    LaunchedEffect(initialEvent?.uid, selectedCollectionHref, eventCollections) {
        if (initialEvent == null) {
            eventCollections.firstOrNull { it.href == selectedCollectionHref }
                ?.let { onDraftCollectionColorChanged(it.color) }
        }
    }

    AnimatedContent(
        targetState = locationPickerOpen,
        transitionSpec = {
            (slideInHorizontally(animationSpec = tween(MotionMedium, easing = MotionStandard)) { width -> width / 3 } + fadeIn(tween(MotionShort))) togetherWith
                (slideOutHorizontally(animationSpec = tween(MotionShort, easing = MotionStandardAccelerate)) { width -> -width / 3 } + fadeOut(tween(MotionShort)))
        },
        label = "eventLocationPicker",
    ) { pickingLocation ->
        if (pickingLocation) {
            LocationPickerPage(
                initialQuery = location,
                onSelected = { selection ->
                    location = selection.value
                    locationMapVerified = selection.mapVerified
                    locationPickerOpen = false
                },
                onClose = { locationPickerOpen = false },
            )
        } else {
            if (!readOnlyRemote && eventCollections.isEmpty()) {
                EditorContainer(
                    title = headerTitle ?: stringResource(if (initialEvent == null) R.string.new_event else R.string.edit_event),
                    action = stringResource(R.string.save),
                    onAction = {},
                    onClose = onClose,
                    actionEnabled = false,
                ) {
                    FadedHorizontalScrollRow(
                        contentPadding = PaddingValues(horizontal = EditorHorizontalPadding),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        TypeChip(stringResource(R.string.event), selected = true)
                        TypeChip(
                            stringResource(R.string.task),
                            selected = false,
                            onClick = {
                                onSwitchToTask(currentTransferDraft())
                            },
                        )
                    }
                    NoWritableSourceNotice(
                        title = stringResource(R.string.no_writable_event_calendar_title),
                        body = stringResource(R.string.no_writable_event_calendar_body),
                        action = stringResource(R.string.add_calendar_source),
                        onAction = onOpenCalendarSources,
                    )
                }
            } else {
                EditorContainer(
                title = headerTitle ?: stringResource(if (initialEvent == null) R.string.new_event else R.string.edit_event),
                action = stringResource(R.string.save),
                onAction = {
                    if (invalidTimeRange) {
                        invalidRangeDialogOpen = true
                        return@EditorContainer
                    }
                    onSave(
                        EventEditPayload(
                            title = title,
                            collectionHref = selectedCollectionHref,
                            date = LocalDate.parse(dateText),
                            endDate = LocalDate.parse(endDateText),
                            startTime = if (allDay) null else LocalTime.parse(startText),
                            endTime = if (allDay) null else LocalTime.parse(endText),
                            allDay = allDay,
                            description = description,
                            location = location,
                            locationMapVerified = locationMapVerified,
                            manualColor = manualColor,
                            recurrenceRule = recurrenceRule.ifBlank { null },
                            reminderMinutes = reminderMinutes.normalizedReminderOffsets(),
                            status = eventStatus,
                            classification = eventClassification,
                            transparency = eventTransparency,
                            categories = categories.ifBlank { null },
                            organizerJson = organizerJson,
                            attendeesJson = attendeesJson,
                        ),
                    )
                },
                onClose = onClose,
            ) {
                if (!readOnlyRemote) {
                    FadedHorizontalScrollRow(
                        contentPadding = PaddingValues(horizontal = EditorHorizontalPadding),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        TypeChip(stringResource(R.string.event), selected = true)
                        TypeChip(
                            stringResource(R.string.task),
                            selected = false,
                            onClick = {
                                onSwitchToTask(currentTransferDraft())
                            },
                        )
                    }
                }
                EditorSection {
                    if (readOnlyRemote) {
                        ReadOnlyEditorNotice(title = title)
                    } else {
                        EventTitleField(
                            value = title,
                            onValueChange = { title = it },
                            requestFocus = requestTitleFocus && initialEvent == null,
                        )
                    }
                }
                if (!readOnlyRemote) {
                    Text(
                        stringResource(R.string.calendar),
                        color = WarmInk,
                        fontSize = 14.sp,
                        lineHeight = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.editorInset(),
                    )
                    FadedHorizontalScrollRow(
                        contentPadding = PaddingValues(horizontal = EditorHorizontalPadding),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        initialScrollIndex = selectedCollectionIndex.takeIf { initialEvent != null && it >= 0 },
                        initialScrollKey = initialEvent?.resourceHref,
                    ) {
                        eventCollections.forEach { collection ->
                            CalendarSelectorChip(
                                collection = collection,
                                selected = collection.href == selectedCollectionHref,
                                onClick = { selectedCollectionHref = collection.href },
                                hidden = collection.href in state.hiddenCollectionHrefs,
                            )
                        }
                    }
                    EditorSection {
                        EditorLine(Icons.Default.AccessTime) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.all_day), fontSize = 17.sp, lineHeight = 20.sp)
                                Switch(
                                    checked = allDay,
                                    onCheckedChange = {
                                        onScheduleChange(
                                            schedule.copy(
                                                allDay = it,
                                                hasStartTime = !it,
                                                hasEndTime = !it,
                                            ).recalculatePreview(),
                                        )
                                    },
                                )
                            }
                        }
                        EventScheduleEditor(schedule = schedule, onScheduleChange = onScheduleChange, endIsError = invalidTimeRange)
                    }
                }
                val selectedCollection = eventCollections.firstOrNull { it.href == selectedCollectionHref }
                val eventCapabilities = selectedCollection?.eventEditorCapabilities() ?: EventEditorCapabilities.Full
                val editableFields = if (readOnlyRemote) {
                    listOf("color")
                } else {
                    state.eventFieldOrder.filterNot { it == "time" }.filter { eventCapabilities.allows(it) }
                }
                editableFields.forEach { field ->
                    when (field) {
                        "recurrence" -> CompactRecurrenceEditor(recurrenceRule = recurrenceRule, onRecurrenceRuleChange = { recurrenceRule = it.orEmpty() })
                        "reminders" -> CompactReminderEditor(selected = reminderMinutes, onSelectedChange = { reminderMinutes = it })
                        "location" -> Box(Modifier.editorInset()) {
                            LocationSelectorField(location = location, onClick = { locationPickerOpen = true })
                        }
                        "notes" -> OutlinedTextField(
                            description,
                            { description = it },
                            label = { Text(stringResource(R.string.notes)) },
                            minLines = 3,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.editorInset().fillMaxWidth(),
                        )
                        "status" -> EditorSection {
                            MetadataDropdownLine(stringResource(R.string.event_status), EventStatusOption.entries, eventStatus) { eventStatus = it }
                            HorizontalDivider(color = WarmLine.copy(alpha = 0.7f))
                            MetadataDropdownLine(stringResource(R.string.sharing_visibility), EventClassOption.entries, eventClassification) { eventClassification = it }
                            HorizontalDivider(color = WarmLine.copy(alpha = 0.7f))
                            MetadataDropdownLine(stringResource(R.string.availability), EventTransparencyOption.entries, eventTransparency) { eventTransparency = it }
                        }
                        "categories" -> TagEditor(stringResource(R.string.categories), categories.toCategoryTags(), knownCategories) { categories = it.toCategoriesCsv() }
                        "color" -> ColorOverrideEditor(
                            selectedColor = manualColor,
                            automaticColor = eventCollections.firstOrNull { it.href == selectedCollectionHref }?.color ?: WarmBrown.toArgb(),
                            onColorSelected = { manualColor = it },
                        )
                        "participants" -> EditorSection {
                            SharedEventEditor(organizerJson, attendeesJson) { attendeesJson = it }
                        }
                    }
                }
            }
            }
        }
    }
    if (invalidRangeDialogOpen) {
        InvalidTimeRangeDialog(onDismiss = { invalidRangeDialogOpen = false })
    }
}

@Composable
private fun EventTitleField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier, requestFocus: Boolean = false) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            delay(220)
            focusRequester.requestFocus()
        }
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(stringResource(R.string.add_title)) },
        singleLine = true,
        textStyle = MaterialTheme.typography.titleLarge.copy(
            color = WarmInk,
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
            lineHeight = 25.sp,
        ),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .focusRequester(focusRequester),
    )
}
