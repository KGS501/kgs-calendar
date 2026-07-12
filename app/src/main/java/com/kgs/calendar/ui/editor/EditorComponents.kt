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
internal fun ReadOnlyEditorNotice(title: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title.ifBlank { stringResource(R.string.no_title) },
            color = WarmInk,
            fontSize = 22.sp,
            lineHeight = 25.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            stringResource(R.string.read_only_editor_notice),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            lineHeight = 17.sp,
        )
    }
}

@Composable
internal fun NoWritableSourceNotice(
    title: String,
    body: String,
    action: String,
    onAction: () -> Unit,
) {
    Column(
        modifier = Modifier
            .editorInset()
            .fillMaxWidth()
            .padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            title,
            color = WarmInk,
            fontSize = 20.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            lineHeight = 19.sp,
        )
        TextButton(
            onClick = onAction,
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 6.dp),
        ) {
            Text(
                action,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 15.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold,
                textDecoration = TextDecoration.Underline,
            )
        }
    }
}

@Composable
internal fun InvalidTimeRangeDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(appString(R.string.invalid_time_range_title)) },
        text = { Text(appString(R.string.invalid_time_range_message)) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(appString(R.string.ok))
            }
        },
        shape = RoundedCornerShape(26.dp),
        containerColor = popupSurfaceColor(),
    )
}

@Composable
internal fun EventScheduleEditor(
    schedule: EditorScheduleState,
    onScheduleChange: (EditorScheduleState) -> Unit,
    endIsError: Boolean = false,
) {
    Column(
        modifier = Modifier.animateContentSize(animationSpec = tween(MotionMedium, easing = MotionStandard)),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(animationSpec = tween(MotionMedium, easing = MotionStandard)),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DatePickerField(
                value = schedule.startDateText,
                onValueChange = { onScheduleChange(schedule.copy(startDateText = it).recalculatePreview()) },
                label = { Text(stringResource(R.string.start_date)) },
                modifier = Modifier.weight(1f).testTag("editor-start-date"),
            )
            AnimatedVisibility(
                visible = !schedule.allDay,
                enter = fadeIn(animationSpec = tween(MotionMedium, easing = MotionStandard)) +
                    expandHorizontally(animationSpec = tween(MotionMedium, easing = MotionStandard), expandFrom = Alignment.Start),
                exit = fadeOut(animationSpec = tween(MotionShort, easing = MotionStandardAccelerate)) +
                    shrinkHorizontally(animationSpec = tween(MotionShort, easing = MotionStandardAccelerate), shrinkTowards = Alignment.Start),
            ) {
                TimePickerField(
                    value = schedule.startTimeText,
                    onValueChange = { onScheduleChange(schedule.copy(startTimeText = it).recalculatePreview()) },
                    label = { Text(stringResource(R.string.start)) },
                    modifier = Modifier.width(112.dp).testTag("editor-start-time"),
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(animationSpec = tween(MotionMedium, easing = MotionStandard)),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DatePickerField(
                value = schedule.endDateText,
                onValueChange = { onScheduleChange(schedule.copy(endDateText = it).recalculatePreview()) },
                label = { Text(stringResource(R.string.end_date)) },
                isError = endIsError,
                modifier = Modifier.weight(1f).testTag("editor-end-date"),
            )
            AnimatedVisibility(
                visible = !schedule.allDay,
                enter = fadeIn(animationSpec = tween(MotionMedium, easing = MotionStandard)) +
                    expandHorizontally(animationSpec = tween(MotionMedium, easing = MotionStandard), expandFrom = Alignment.Start),
                exit = fadeOut(animationSpec = tween(MotionShort, easing = MotionStandardAccelerate)) +
                    shrinkHorizontally(animationSpec = tween(MotionShort, easing = MotionStandardAccelerate), shrinkTowards = Alignment.Start),
            ) {
                TimePickerField(
                    value = schedule.endTimeText,
                    onValueChange = { onScheduleChange(schedule.copy(endTimeText = it).recalculatePreview()) },
                    label = { Text(stringResource(R.string.end)) },
                    isError = endIsError,
                    modifier = Modifier.width(112.dp).testTag("editor-end-time"),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DatePickerField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: String? = null,
) {
    var dialogOpen by remember { mutableStateOf(false) }
    PickerOutlinedField(
        value = value,
        label = label,
        onClick = { dialogOpen = true },
        isError = isError,
        supportingText = supportingText,
        modifier = modifier,
    )
    if (dialogOpen) {
        val initialDate = runCatching { LocalDate.parse(value) }.getOrDefault(LocalCalendarTimeSnapshot.current.today)
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        PickerPopupTheme {
            DatePickerDialog(
                onDismissRequest = { dialogOpen = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pickerState.selectedDateMillis?.let { millis ->
                                onValueChange(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString())
                            }
                            dialogOpen = false
                        },
                    ) {
                        Text(stringResource(R.string.apply))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { dialogOpen = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            ) {
                DatePicker(state = pickerState)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimePickerField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: String? = null,
) {
    var dialogOpen by remember { mutableStateOf(false) }
    PickerOutlinedField(
        value = value,
        label = label,
        onClick = { dialogOpen = true },
        isError = isError,
        supportingText = supportingText,
        modifier = modifier,
    )
    if (dialogOpen) {
        val initialTime = runCatching { LocalTime.parse(value) }.getOrDefault(LocalTime.now())
        val pickerState = rememberTimePickerState(
            initialHour = initialTime.hour,
            initialMinute = initialTime.minute,
            is24Hour = true,
        )
        Dialog(onDismissRequest = { dialogOpen = false }) {
            PickerPopupTheme {
                Surface(
                    modifier = Modifier.widthIn(min = 328.dp, max = 360.dp),
                    shape = RoundedCornerShape(26.dp),
                    color = popupSurfaceColor(),
                    tonalElevation = 6.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            stringResource(R.string.choose_time),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 18.sp,
                            lineHeight = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        val accent = WarmBrown
                        val accentContainer = accentContainerColor()
                        val onAccent = if (accent.isDark()) Color.White else Color(0xFF1C1A18)
                        val onAccentContainer = if (accentContainer.isDark()) Color.White else Color(0xFF1C1A18)
                        TimePicker(
                            state = pickerState,
                            colors = TimePickerDefaults.colors(
                                clockDialColor = popupControlSurfaceColor(),
                                selectorColor = accent,
                                containerColor = popupSurfaceColor(),
                                periodSelectorBorderColor = MaterialTheme.colorScheme.outline,
                                clockDialSelectedContentColor = onAccent,
                                clockDialUnselectedContentColor = MaterialTheme.colorScheme.onSurface,
                                periodSelectorSelectedContainerColor = accentContainer,
                                periodSelectorUnselectedContainerColor = popupControlSurfaceColor(),
                                periodSelectorSelectedContentColor = onAccentContainer,
                                periodSelectorUnselectedContentColor = MaterialTheme.colorScheme.onSurface,
                                timeSelectorSelectedContainerColor = accentContainer,
                                timeSelectorUnselectedContainerColor = popupControlSurfaceColor(),
                                timeSelectorSelectedContentColor = onAccentContainer,
                                timeSelectorUnselectedContentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(onClick = { dialogOpen = false }) {
                                Text(stringResource(R.string.cancel))
                            }
                            TextButton(
                                onClick = {
                                    onValueChange(LocalTime.of(pickerState.hour, pickerState.minute).toString())
                                    dialogOpen = false
                                },
                            ) {
                                Text(stringResource(R.string.apply))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun PickerPopupTheme(content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val popupSurface = popupSurfaceColor()
    val controlSurface = popupControlSurfaceColor()
    val accentContainer = accentContainerColor()
    val onAccent = if (WarmBrown.isDark()) Color.White else Color(0xFF1C1A18)
    val onAccentContainer = if (accentContainer.isDark()) Color.White else Color(0xFF1C1A18)
    MaterialTheme(
        colorScheme = colors.copy(
            primary = WarmBrown,
            onPrimary = onAccent,
            primaryContainer = accentContainer,
            onPrimaryContainer = onAccentContainer,
            secondary = WarmBrown,
            onSecondary = onAccent,
            secondaryContainer = accentContainer,
            onSecondaryContainer = onAccentContainer,
            tertiary = WarmBrown,
            onTertiary = onAccent,
            tertiaryContainer = accentContainer,
            onTertiaryContainer = onAccentContainer,
            surface = popupSurface,
            surfaceVariant = controlSurface,
            surfaceContainerLowest = popupSurface,
            surfaceContainerLow = popupSurface,
            surfaceContainer = popupSurface,
            surfaceContainerHigh = popupSurface,
            surfaceContainerHighest = controlSurface,
        ),
        typography = MaterialTheme.typography,
        content = content,
    )
}

@Composable
internal fun popupSurfaceColor(): Color =
    if (MaterialTheme.colorScheme.background.isDark()) Color(0xFF202124) else Color.White

@Composable
internal fun popupControlSurfaceColor(): Color =
    if (MaterialTheme.colorScheme.background.isDark()) Color(0xFF303134) else Color(0xFFF1F3F4)

@Composable
internal fun PickerOutlinedField(
    value: String,
    label: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: String? = null,
) {
    val shape = RoundedCornerShape(16.dp)
    val supportingMessage = supportingText
    val supportingContent: (@Composable () -> Unit)? =
        if (supportingMessage == null) null else {
            { Text(supportingMessage) }
        }
    Box(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            label = label,
            singleLine = true,
            readOnly = true,
            shape = shape,
            isError = isError,
            supportingText = supportingContent,
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(shape)
                .clickable(onClick = onClick),
        )
    }
}

@Composable
internal fun EditorTopBar(
    title: String,
    onClose: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    saveEnabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .then(LocalSheetHeaderDragModifier.current)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        RoundEditorAction(
            icon = Icons.Default.Close,
            contentDescription = stringResource(R.string.close),
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f),
            contentColor = WarmInk,
            onClick = onClose,
        )
        Text(
            title,
            color = WarmInk,
            fontSize = 15.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier
                .weight(1f),
            textAlign = TextAlign.Center,
        )
        RoundEditorAction(
            icon = Icons.Default.Check,
            contentDescription = stringResource(R.string.save),
            containerColor = if (saveEnabled) WarmBrown else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f),
            contentColor = if (saveEnabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.52f),
            enabled = saveEnabled,
            onClick = onSave,
        )
    }
}

@Composable
internal fun RoundEditorAction(
    icon: ImageVector,
    contentDescription: String,
    containerColor: Color,
    contentColor: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(containerColor)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = contentColor, modifier = Modifier.size(20.dp))
    }
}

@Composable
internal fun TypeChip(text: String, selected: Boolean, onClick: () -> Unit = {}) {
    val shape = RoundedCornerShape(14.dp)
    Surface(
        modifier = Modifier
            .clip(shape)
            .clickable(onClick = onClick),
        shape = shape,
        color = if (selected) WarmPeach else Color.Transparent,
        border = if (selected) null else BorderStroke(1.dp, Color(0xFFD3C3BD)),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            fontSize = 14.sp,
            lineHeight = 17.sp,
            maxLines = 1,
            softWrap = false,
            color = WarmInk,
        )
    }
}

@Composable
internal fun FadedHorizontalScrollRow(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 0.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(8.dp),
    viewportBleed: Dp = 0.dp,
    initialScrollIndex: Int? = null,
    initialScrollKey: Any? = initialScrollIndex,
    content: @Composable RowScope.() -> Unit,
) {
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    LaunchedEffect(initialScrollKey, initialScrollIndex) {
        val index = initialScrollIndex?.takeIf { it > 0 } ?: return@LaunchedEffect
        repeat(4) { withFrameNanos { } }
        val target = with(density) { (132 * index).dp.roundToPx() }
            .coerceIn(0, scrollState.maxValue)
        scrollState.scrollTo(target)
    }
    val showStartFade = scrollState.value > 0
    val showEndFade = scrollState.maxValue > scrollState.value
    Box(
        modifier = modifier
            .fillMaxWidth()
            .horizontalBleed(viewportBleed)
            .horizontalEdgeFade(edgeWidth = 22.dp, fadeStart = showStartFade, fadeEnd = showEndFade),
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(scrollState)
                .padding(contentPadding),
            horizontalArrangement = horizontalArrangement,
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
internal fun EditorSection(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier
            .editorInset()
            .fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
        border = BorderStroke(1.dp, WarmLine.copy(alpha = 0.72f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = EditorSectionHorizontalPadding, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

@Composable
internal fun CalendarChip(title: String, color: Color) {
    Surface(shape = RoundedCornerShape(12.dp), color = WarmPeach.copy(alpha = 0.66f)) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(18.dp).clip(CircleShape).background(color))
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        }
    }
}

@Composable
internal fun CalendarSelectorChip(
    collection: CollectionEntity,
    selected: Boolean,
    hidden: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        modifier = Modifier
            .clip(shape)
            .clickable(onClick = onClick)
            .then(if (hidden) Modifier.dashedBorder(SyncPendingOrange, 12.dp) else Modifier),
        shape = shape,
        color = if (selected) WarmPeach else Color.Transparent,
        border = if (hidden) {
            null
        } else {
            BorderStroke(
                width = 1.dp,
                color = if (selected) WarmBrown.copy(alpha = 0.42f) else WarmLine.copy(alpha = 0.72f),
            )
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Color(collection.color)),
            )
            Text(
                collection.displayName,
                maxLines = 1,
                softWrap = false,
                color = WarmInk,
                fontSize = 14.sp,
                lineHeight = 17.sp,
            )
        }
    }
}

@Composable
internal fun EditorLine(
    icon: ImageVector?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.width(34.dp), contentAlignment = Alignment.Center) {
            icon?.let { Icon(it, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(23.dp)) }
        }
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
internal fun TaskScheduleEditor(
    schedule: EditorScheduleState,
    onScheduleChange: (EditorScheduleState) -> Unit,
    endIsError: Boolean = false,
) {
    val scheduled = schedule.hasStartDate || schedule.hasEndDate
    Column(
        modifier = Modifier.animateContentSize(animationSpec = tween(MotionMedium, easing = MotionStandard)),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (scheduled) {
            EditorLine(Icons.Default.AccessTime) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.all_day), fontSize = 17.sp, lineHeight = 20.sp)
                    Switch(
                        checked = schedule.allDay,
                        onCheckedChange = { allDay ->
                            onScheduleChange(
                                schedule.copy(
                                    allDay = allDay,
                                    hasStartTime = if (allDay) false else schedule.hasStartDate,
                                    hasEndTime = if (allDay) false else schedule.hasEndDate,
                                ).recalculatePreview(),
                            )
                        },
                    )
                }
            }
        } else {
            Text(stringResource(R.string.task_no_date), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, lineHeight = 17.sp)
        }
        TaskScheduleRow(
            label = stringResource(R.string.start_date),
            addLabel = stringResource(R.string.add_field, stringResource(R.string.start_date)),
            removeLabel = stringResource(R.string.remove_field, stringResource(R.string.start_date)),
            timeLabel = stringResource(R.string.start),
            hasDate = schedule.hasStartDate,
            onHasDateChange = { hasDate ->
                val allDay = if (hasDate && !schedule.hasStartDate && !schedule.hasEndDate) true else schedule.allDay
                onScheduleChange(
                    schedule.copy(
                        hasStartDate = hasDate,
                        hasStartTime = hasDate && !allDay,
                        allDay = allDay,
                    ).recalculatePreview(),
                )
            },
            dateText = schedule.startDateText,
            onDateTextChange = { onScheduleChange(schedule.copy(startDateText = it).recalculatePreview()) },
            hasTime = schedule.hasStartTime,
            onHasTimeChange = { onScheduleChange(schedule.copy(hasStartTime = it).recalculatePreview()) },
            timeText = schedule.startTimeText,
            onTimeTextChange = { onScheduleChange(schedule.copy(startTimeText = it).recalculatePreview()) },
            allDay = schedule.allDay,
        )
        TaskScheduleRow(
            label = stringResource(R.string.end_date),
            addLabel = stringResource(R.string.add_field, stringResource(R.string.end_date)),
            removeLabel = stringResource(R.string.remove_field, stringResource(R.string.end_date)),
            timeLabel = stringResource(R.string.end),
            hasDate = schedule.hasEndDate,
            onHasDateChange = { hasDate ->
                val allDay = if (hasDate && !schedule.hasStartDate && !schedule.hasEndDate) true else schedule.allDay
                onScheduleChange(
                    schedule.copy(
                        hasEndDate = hasDate,
                        hasEndTime = hasDate && !allDay,
                        allDay = allDay,
                    ).recalculatePreview(),
                )
            },
            dateText = schedule.endDateText,
            onDateTextChange = { onScheduleChange(schedule.copy(endDateText = it).recalculatePreview()) },
            hasTime = schedule.hasEndTime,
            onHasTimeChange = { onScheduleChange(schedule.copy(hasEndTime = it).recalculatePreview()) },
            timeText = schedule.endTimeText,
            onTimeTextChange = { onScheduleChange(schedule.copy(endTimeText = it).recalculatePreview()) },
            allDay = schedule.allDay,
            isError = endIsError,
        )
    }
}

@Composable
internal fun TaskParentSelector(
    selectedParentUid: String?,
    candidates: List<TaskEntity>,
    taskColorMode: TaskColorMode,
    onSelected: (String?) -> Unit,
) {
    var pickerOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val selected = candidates.firstOrNull { it.uid == selectedParentUid }
    LaunchedEffect(pickerOpen) {
        if (pickerOpen) query = ""
    }
    Column(
        modifier = Modifier.editorInset(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            stringResource(R.string.parent_task),
            color = WarmInk,
            fontSize = 14.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Box {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { pickerOpen = true },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
                border = BorderStroke(1.dp, WarmLine),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Default.TaskAlt, contentDescription = null, tint = WarmBrown, modifier = Modifier.size(20.dp))
                    Text(
                        selected?.title ?: stringResource(R.string.no_parent_task),
                        color = WarmInk,
                        fontSize = 14.sp,
                        lineHeight = 17.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = WarmInk, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
    if (pickerOpen) {
        TaskParentPickerDialog(
            selectedParentUid = selectedParentUid,
            candidates = candidates,
            query = query,
            onQueryChange = { query = it },
            taskColorMode = taskColorMode,
            onDismiss = { pickerOpen = false },
            onSelected = {
                onSelected(it)
                pickerOpen = false
            },
        )
    }
}

@Composable
internal fun TaskParentPickerDialog(
    selectedParentUid: String?,
    candidates: List<TaskEntity>,
    query: String,
    onQueryChange: (String) -> Unit,
    taskColorMode: TaskColorMode,
    onDismiss: () -> Unit,
    onSelected: (String?) -> Unit,
) {
    val filteredCandidates = remember(candidates, query) { candidates.filterForParentPicker(query) }
    val hierarchy = rememberTaskHierarchyPresentation(filteredCandidates, expandedByDefault = true)
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(R.string.parent_task),
                        color = WarmInk,
                        fontSize = 18.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close), tint = WarmInk)
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = { Text(stringResource(R.string.search_ellipsis)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onSelected(null) },
                    shape = RoundedCornerShape(16.dp),
                    color = if (selectedParentUid == null) WarmPeach.copy(alpha = 0.62f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
                    border = BorderStroke(1.dp, if (selectedParentUid == null) WarmBrown.copy(alpha = 0.5f) else WarmLine),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Default.TaskAlt, contentDescription = null, tint = WarmBrown, modifier = Modifier.size(20.dp))
                        Text(
                            stringResource(R.string.no_parent_task),
                            color = WarmInk,
                            fontSize = 14.sp,
                            lineHeight = 17.sp,
                            fontWeight = if (selectedParentUid == null) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.weight(1f),
                        )
                        if (selectedParentUid == null) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = WarmBrown, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                if (filteredCandidates.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(116.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (candidates.isEmpty()) stringResource(R.string.no_parent_task) else stringResource(R.string.no_matches),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp,
                            lineHeight = 17.sp,
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 430.dp)
                            .verticalScroll(rememberScrollState())
                            .animateContentSize(animationSpec = tween(MotionMedium, easing = MotionStandard))
                            .graphicsLayer { clip = false },
                    ) {
                        hierarchy.entries.forEach { entry ->
                            AnimatedTaskHierarchyEntry(entry) {
                                TaskRow(
                                    task = entry.task,
                                    taskColorMode = taskColorMode,
                                    onTaskStatusChanged = { _, _ -> onSelected(entry.task.uid) },
                                    prominent = true,
                                    hierarchyDepth = entry.depth,
                                    hierarchyContinuationLevels = entry.continuationLevels,
                                    hierarchyLastSibling = entry.lastSibling,
                                    hasSubtasks = entry.hasChildren,
                                    subtasksExpanded = entry.expanded,
                                    onToggleSubtasks = { hierarchy.toggle(entry.task) },
                                    outerHorizontalPadding = 0.dp,
                                    outerVerticalPadding = 3.dp,
                                    connectorStemInset = TaskHierarchyStemInset,
                                    onClick = { onSelected(entry.task.uid) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun List<TaskEntity>.filterForParentPicker(query: String): List<TaskEntity> {
    val normalized = query.trim().lowercase(Locale.ROOT)
    if (normalized.isBlank()) return this
    val byCollectionUid = associateBy { it.collectionHref to it.uid }
    val globallyUniqueByUid = groupBy { it.uid }
        .filterValues { it.size == 1 }
        .mapValues { it.value.single() }
    val included = mutableSetOf<String>()

    fun parentOf(task: TaskEntity): TaskEntity? {
        val parentUid = task.parentUid?.takeIf { it.isNotBlank() } ?: return null
        return byCollectionUid[task.collectionHref to parentUid] ?: globallyUniqueByUid[parentUid]
    }

    fun includeWithAncestors(task: TaskEntity) {
        var cursor: TaskEntity? = task
        val seen = mutableSetOf<String>()
        while (cursor != null && seen.add(cursor.resourceHref)) {
            included += cursor.resourceHref
            cursor = parentOf(cursor)
        }
    }

    fun TaskEntity.matchesSearch(): Boolean {
        return listOf(title, notes.orEmpty(), categories.orEmpty(), location.orEmpty())
            .any { it.lowercase(Locale.ROOT).contains(normalized) }
    }

    filter { it.matchesSearch() }.forEach(::includeWithAncestors)
    return filter { it.resourceHref in included }
}

internal data class TaskHierarchySubset(
    val tasks: List<TaskEntity>,
    val defaultExpandedResourceHrefs: Set<String> = emptySet(),
)

internal fun List<TaskEntity>.searchHierarchySubsetForMatches(matches: List<TaskEntity>): TaskHierarchySubset {
    if (matches.isEmpty()) return TaskHierarchySubset(emptyList())
    val base = (this + matches).distinctBy { it.resourceHref }
    val byResource = base.associateBy { it.resourceHref }
    val byCollectionUid = base.associateBy { it.collectionHref to it.uid }
    val globallyUniqueByUid = base.groupBy { it.uid }
        .filterValues { it.size == 1 }
        .mapValues { it.value.single() }
    val childrenByParent = base
        .filter { !it.parentUid.isNullOrBlank() }
        .groupBy { it.collectionHref to it.parentUid.orEmpty() }
    val included = linkedSetOf<String>()
    val expanded = linkedSetOf<String>()

    fun parentOf(task: TaskEntity): TaskEntity? {
        val parentUid = task.parentUid?.takeIf { it.isNotBlank() } ?: return null
        return byCollectionUid[task.collectionHref to parentUid] ?: globallyUniqueByUid[parentUid]
    }

    fun rootOf(task: TaskEntity): TaskEntity {
        var cursor = task
        val seen = mutableSetOf(task.resourceHref)
        while (true) {
            val parent = parentOf(cursor) ?: return cursor
            if (!seen.add(parent.resourceHref)) return cursor
            cursor = parent
        }
    }

    fun includeDescendants(task: TaskEntity) {
        val queue = ArrayDeque<TaskEntity>()
        val seen = mutableSetOf<String>()
        queue.add(task)
        while (queue.isNotEmpty()) {
            val cursor = queue.removeFirst()
            if (!seen.add(cursor.resourceHref)) continue
            included += cursor.resourceHref
            childrenByParent[cursor.collectionHref to cursor.uid].orEmpty().forEach(queue::add)
        }
    }

    fun defaultOpenAncestorsOf(task: TaskEntity) {
        var cursor = task
        val seen = mutableSetOf(task.resourceHref)
        while (true) {
            val parent = parentOf(cursor) ?: return
            if (!seen.add(parent.resourceHref)) return
            expanded += parent.resourceHref
            cursor = parent
        }
    }

    matches.forEach { rawMatch ->
        val match = byResource[rawMatch.resourceHref] ?: rawMatch
        includeDescendants(rootOf(match))
        defaultOpenAncestorsOf(match)
    }
    return TaskHierarchySubset(
        tasks = base.filter { it.resourceHref in included },
        defaultExpandedResourceHrefs = expanded,
    )
}

internal fun List<TaskEntity>.chainSubsetForTargets(
    targets: List<TaskEntity>,
    includeTargetDescendants: Boolean,
): List<TaskEntity> {
    if (targets.isEmpty()) return emptyList()
    val base = (this + targets).distinctBy { it.resourceHref }
    val byResource = base.associateBy { it.resourceHref }
    val byCollectionUid = base.associateBy { it.collectionHref to it.uid }
    val globallyUniqueByUid = base.groupBy { it.uid }
        .filterValues { it.size == 1 }
        .mapValues { it.value.single() }
    val childrenByParent = base
        .filter { !it.parentUid.isNullOrBlank() }
        .groupBy { it.collectionHref to it.parentUid.orEmpty() }
    val included = linkedSetOf<String>()

    fun parentOf(task: TaskEntity): TaskEntity? {
        val parentUid = task.parentUid?.takeIf { it.isNotBlank() } ?: return null
        return byCollectionUid[task.collectionHref to parentUid] ?: globallyUniqueByUid[parentUid]
    }

    fun includeAncestors(task: TaskEntity) {
        var cursor: TaskEntity? = task
        val seen = mutableSetOf<String>()
        while (cursor != null && seen.add(cursor.resourceHref)) {
            included += cursor.resourceHref
            cursor = parentOf(cursor)
        }
    }

    fun includeDescendants(task: TaskEntity) {
        val queue = ArrayDeque<TaskEntity>()
        queue.add(task)
        val seen = mutableSetOf<String>()
        while (queue.isNotEmpty()) {
            val cursor = queue.removeFirst()
            if (!seen.add(cursor.resourceHref)) continue
            included += cursor.resourceHref
            childrenByParent[cursor.collectionHref to cursor.uid].orEmpty().forEach(queue::add)
        }
    }

    targets.forEach { rawTarget ->
        val target = byResource[rawTarget.resourceHref] ?: rawTarget
        includeAncestors(target)
        if (includeTargetDescendants) includeDescendants(target)
    }
    return base.filter { it.resourceHref in included }
}

internal fun List<TaskEntity>.descendantUids(task: TaskEntity): Set<String> {
    val childrenByParent = filter { it.collectionHref == task.collectionHref && !it.parentUid.isNullOrBlank() }
        .groupBy { it.parentUid }
    val result = mutableSetOf<String>()
    val queue = ArrayDeque<String>()
    queue.add(task.uid)
    while (queue.isNotEmpty()) {
        val parent = queue.removeFirst()
        childrenByParent[parent].orEmpty().forEach { child ->
            if (result.add(child.uid)) queue.add(child.uid)
        }
    }
    return result
}

internal fun List<TaskEntity>.descendantsOf(task: TaskEntity): List<TaskEntity> {
    val childrenByParent = filter { it.collectionHref == task.collectionHref && !it.parentUid.isNullOrBlank() }
        .groupBy { it.parentUid }
    val result = mutableListOf<TaskEntity>()
    val seen = mutableSetOf(task.uid)

    fun append(parentUid: String) {
        childrenByParent[parentUid].orEmpty().forEach { child ->
            if (seen.add(child.uid)) {
                result += child
                append(child.uid)
            }
        }
    }

    append(task.uid)
    return result
}

internal data class TaskRootActivityPartition(
    val activeRootTasks: List<TaskEntity>,
    val inactiveRootTasks: List<TaskEntity>,
)

internal fun List<TaskEntity>.partitionByRootActivity(): TaskRootActivityPartition {
    val distinctTasks = distinctBy { it.resourceHref }
    val byCollectionUid = distinctTasks.associateBy { it.collectionHref to it.uid }
    val globallyUniqueByUid = distinctTasks.groupBy { it.uid }
        .filterValues { it.size == 1 }
        .mapValues { it.value.single() }

    fun parentOf(task: TaskEntity): TaskEntity? {
        val parentUid = task.parentUid?.takeIf { it.isNotBlank() } ?: return null
        return byCollectionUid[task.collectionHref to parentUid] ?: globallyUniqueByUid[parentUid]
    }

    fun rootOf(task: TaskEntity): TaskEntity {
        var current = task
        val seen = mutableSetOf(task.resourceHref)
        while (true) {
            val parent = parentOf(current) ?: return current
            if (!seen.add(parent.resourceHref)) return current
            current = parent
        }
    }

    val tasksByRoot = distinctTasks.groupBy { rootOf(it).resourceHref }
    val fullyInactiveRoots = tasksByRoot
        .filterValues { rootTasks -> rootTasks.isNotEmpty() && rootTasks.all { it.isInactive() } }
        .keys
    val active = tasksByRoot
        .filterKeys { it !in fullyInactiveRoots }
        .values
        .flatten()
    val inactive = distinctTasks.filter { it.isInactive() }
    return TaskRootActivityPartition(
        activeRootTasks = active,
        inactiveRootTasks = inactive,
    )
}

internal fun List<TaskEntity>.partitionByRootSchedule(): Pair<List<TaskEntity>, List<TaskEntity>> {
    val byCollectionUid = associateBy { it.collectionHref to it.uid }

    fun rootOf(task: TaskEntity): TaskEntity {
        var current = task
        val seen = mutableSetOf(task.resourceHref)
        while (!current.parentUid.isNullOrBlank()) {
            val parent = byCollectionUid[current.collectionHref to current.parentUid] ?: break
            if (!seen.add(parent.resourceHref)) break
            current = parent
        }
        return current
    }

    return partition { rootOf(it).taskDate() == null }
}

@Composable
internal fun TaskScheduleRow(
    label: String,
    addLabel: String,
    removeLabel: String,
    timeLabel: String,
    hasDate: Boolean,
    onHasDateChange: (Boolean) -> Unit,
    dateText: String,
    onDateTextChange: (String) -> Unit,
    hasTime: Boolean,
    onHasTimeChange: (Boolean) -> Unit,
    timeText: String,
    onTimeTextChange: (String) -> Unit,
    allDay: Boolean,
    isError: Boolean = false,
) {
    AnimatedContent(
        targetState = hasDate,
        transitionSpec = {
            (fadeIn(tween(MotionMedium, easing = MotionStandard)) + expandVertically(tween(MotionMedium, easing = MotionStandard))) togetherWith
                (fadeOut(tween(MotionShort, easing = MotionStandardAccelerate)) + shrinkVertically(tween(MotionShort, easing = MotionStandardAccelerate)))
        },
        label = "taskScheduleRow-$label",
    ) { dateVisible ->
        if (!dateVisible) {
            AddEditorValueRow(addLabel) { onHasDateChange(true) }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(animationSpec = tween(MotionMedium, easing = MotionStandard)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DatePickerField(
                    value = dateText,
                    onValueChange = onDateTextChange,
                    label = { Text(label) },
                    isError = isError,
                    modifier = Modifier.weight(1f),
                )
                AnimatedVisibility(
                    visible = !allDay && hasTime,
                    enter = fadeIn(tween(MotionMedium, easing = MotionStandard)) +
                        expandHorizontally(tween(MotionMedium, easing = MotionStandard), expandFrom = Alignment.Start),
                    exit = fadeOut(tween(MotionShort, easing = MotionStandardAccelerate)) +
                        shrinkHorizontally(tween(MotionShort, easing = MotionStandardAccelerate), shrinkTowards = Alignment.Start),
                ) {
                    TimePickerField(
                        value = timeText,
                        onValueChange = onTimeTextChange,
                        label = { Text(timeLabel) },
                        isError = isError,
                        modifier = Modifier.width(104.dp),
                    )
                }
                IconButton(
                    onClick = {
                        onHasDateChange(false)
                        onHasTimeChange(false)
                    },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(Icons.Default.Delete, contentDescription = removeLabel, tint = WarmInk, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
internal fun AddEditorValueRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(UniversalControlHeight)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, WarmLine, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Default.Add, contentDescription = label, tint = WarmBrown, modifier = Modifier.size(22.dp))
        Text(label, color = WarmInk, fontSize = 15.sp, lineHeight = 18.sp)
    }
}

@Composable
internal fun TagEditor(
    label: String,
    tags: List<String>,
    suggestions: List<String>,
    onTagsChanged: (List<String>) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val normalizedQuery = query.trim()
    val availableSuggestions = remember(tags, suggestions, normalizedQuery) {
        suggestions
            .filterNot { existing -> tags.any { it.equals(existing, ignoreCase = true) } }
            .filter { normalizedQuery.isBlank() || it.contains(normalizedQuery, ignoreCase = true) }
            .take(6)
    }
    fun addTag(value: String) {
        val cleaned = value.trim().trim(',')
        if (cleaned.isBlank() || tags.any { it.equals(cleaned, ignoreCase = true) }) return
        onTagsChanged(tags + cleaned)
        query = ""
    }
    EditorSection {
        Text(label, color = WarmInk, fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold)
        if (tags.isNotEmpty()) {
            FadedHorizontalScrollRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                tags.forEach { tag ->
                    TagPill(
                        tag = tag,
                        onRemove = { onTagsChanged(tags.filterNot { it == tag }) },
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.add_tag)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = { addTag(query) },
                enabled = normalizedQuery.isNotBlank(),
                modifier = Modifier.size(UniversalControlHeight),
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_tag), tint = WarmBrown)
            }
        }
        if (availableSuggestions.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                availableSuggestions.forEach { suggestion ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { addTag(suggestion) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = WarmBrown, modifier = Modifier.size(18.dp))
                        Text(suggestion, color = WarmInk, fontSize = 14.sp, lineHeight = 17.sp)
                    }
                }
            }
        }
    }
}

@Composable
internal fun TagPill(tag: String, onRemove: (() -> Unit)? = null) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = WarmPeach.copy(alpha = 0.84f),
        border = BorderStroke(1.dp, WarmLine.copy(alpha = 0.72f)),
    ) {
        Row(
            modifier = Modifier.padding(start = 11.dp, end = if (onRemove == null) 11.dp else 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(tag, color = WarmInk, fontSize = 13.sp, lineHeight = 16.sp, maxLines = 1)
            onRemove?.let {
                IconButton(onClick = it, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.remove_tag, tag), tint = WarmInk, modifier = Modifier.size(15.dp))
                }
            }
        }
    }
}

@Composable
internal fun CategoryDetailLine(label: String, categories: String) {
    val tags = remember(categories) { categories.toCategoryTags() }
    if (tags.isEmpty()) return
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold)
            FadedHorizontalScrollRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                tags.forEach { TagPill(it) }
            }
        }
    }
}
