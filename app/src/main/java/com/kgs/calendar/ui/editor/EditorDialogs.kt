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

private val ReminderPresets = listOf(0, 5, 10, 15, 30, 60, 120, 1440)

internal interface EventIcalOption {
    val value: String
    val label: String
}

internal enum class EventStatusOption(override val value: String, override val label: String) : EventIcalOption {
    Confirmed("CONFIRMED", "Confirmed"),
    Tentative("TENTATIVE", "Tentative"),
    Cancelled("CANCELLED", "Cancelled"),
}

internal enum class EventClassOption(override val value: String, override val label: String) : EventIcalOption {
    Public("PUBLIC", "Full details"),
    Confidential("CONFIDENTIAL", "Busy only"),
    Private("PRIVATE", "Hidden"),
}

internal enum class EventTransparencyOption(override val value: String, override val label: String) : EventIcalOption {
    Busy("OPAQUE", "Busy"),
    Free("TRANSPARENT", "Free"),
}

@Composable
internal fun EventIcalOption.localizedLabel(): String = when (value.uppercase(Locale.US)) {
    "CONFIRMED" -> appString(R.string.confirmed)
    "TENTATIVE" -> appString(R.string.tentative)
    "CANCELLED" -> appString(R.string.cancelled)
    "PUBLIC" -> appString(R.string.full_details)
    "CONFIDENTIAL" -> appString(R.string.busy_only)
    "PRIVATE" -> appString(R.string.hidden)
    "OPAQUE" -> appString(R.string.busy)
    "TRANSPARENT" -> appString(R.string.free)
    else -> label
}

@Composable
internal fun EventMetadataChipRow(
    options: List<out EventIcalOption>,
    selected: String,
    onSelected: (String) -> Unit,
) {
    FadedHorizontalScrollRow(contentPadding = PaddingValues(horizontal = 0.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = selected.equals(option.value, ignoreCase = true),
                onClick = { onSelected(option.value) },
                label = { Text(option.localizedLabel(), maxLines = 1, softWrap = false) },
            )
        }
    }
}

@Composable
internal fun SharedEventEditor(
    organizerJson: String?,
    attendeesJson: String?,
    onAttendeesJsonChange: (String?) -> Unit,
) {
    val organizer = remember(organizerJson) { organizerJson.toCalendarParticipant() }
    val attendees = remember(attendeesJson) { attendeesJson.toCalendarParticipants() }
    var newEmail by remember { mutableStateOf("") }
    fun addParticipant() {
        val email = newEmail.trim()
        if (!email.isLikelyEmailAddress()) return
        if (attendees.none { it.email.equals(email, ignoreCase = true) }) {
            onAttendeesJsonChange(
                (attendees + CalendarParticipant(name = email, email = email, rsvp = true)).toAttendeesJson(),
            )
        }
        newEmail = ""
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        EditorLine(Icons.Default.PersonAdd) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(stringResource(R.string.participants), fontSize = 15.sp, lineHeight = 18.sp, color = WarmInk, fontWeight = FontWeight.SemiBold)
                Text(
                    if (attendees.isEmpty()) stringResource(R.string.no_participants) else stringResource(R.string.participant_count, attendees.size),
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        organizer?.let {
            Text(stringResource(R.string.organized_by, it.displayName), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 16.sp)
        }
        attendees.forEachIndexed { index, attendee ->
            ParticipantEditorRow(
                attendee = attendee,
                onChange = { changed ->
                    onAttendeesJsonChange(attendees.toMutableList().also { it[index] = changed }.toAttendeesJson())
                },
                onRemove = {
                    onAttendeesJsonChange(attendees.toMutableList().also { it.removeAt(index) }.toAttendeesJson())
                },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = newEmail,
                onValueChange = { newEmail = it },
                label = { Text(stringResource(R.string.email_address)) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { addParticipant() }),
                modifier = Modifier
                    .weight(1f)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyUp && event.key == Key.Enter) {
                            addParticipant()
                            true
                        } else {
                            false
                        }
                    },
            )
            IconButton(
                enabled = newEmail.trim().isLikelyEmailAddress(),
                onClick = { addParticipant() },
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_participant), tint = WarmBrown)
            }
        }
    }
}

@Composable
internal fun ParticipantEditorRow(
    attendee: CalendarParticipant,
    onChange: (CalendarParticipant) -> Unit,
    onRemove: () -> Unit,
) {
    var roleMenuOpen by remember(attendee.email) { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(attendee.displayName, color = WarmInk, fontSize = 14.sp, lineHeight = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(attendee.email, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onRemove, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.remove_participant), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(19.dp))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = attendee.rsvp,
                    onCheckedChange = { onChange(attendee.copy(rsvp = it)) },
                    modifier = Modifier.size(34.dp),
                )
                Text(stringResource(R.string.request_response), color = WarmInk, fontSize = 13.sp, lineHeight = 16.sp)
            }
            Box {
                OutlinedButton(
                    onClick = { roleMenuOpen = true },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(attendee.role.localizedParticipantRoleLabel(), fontSize = 13.sp, lineHeight = 16.sp)
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(17.dp))
                }
                DropdownMenu(
                    expanded = roleMenuOpen,
                    onDismissRequest = { roleMenuOpen = false },
                    shape = RoundedCornerShape(14.dp),
                ) {
                    ParticipantRoleOption.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.localizedLabel()) },
                            onClick = {
                                roleMenuOpen = false
                                onChange(attendee.copy(role = option.value))
                            },
                        )
                    }
                }
            }
            attendee.localizedDeliveryStatusLabel()?.let {
                Text(it, color = attendee.deliveryStatusColor(), fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
internal fun reminderMinuteLabel(minutes: Int): String = when {
    minutes == REMINDER_AT_END -> appString(R.string.at_end)
    minutes == REMINDER_AT_START -> appString(R.string.at_start)
    minutes % 1440 == 0 -> appString(R.string.days_before, minutes / 1440)
    minutes % 60 == 0 -> appString(R.string.hours_before, minutes / 60)
    else -> appString(R.string.minutes_before, minutes)
}

@Composable
internal fun ReminderUnit.localizedLabel(): String = when (this) {
    ReminderUnit.Minutes -> appString(R.string.minutes)
    ReminderUnit.Hours -> appString(R.string.hours)
    ReminderUnit.Days -> appString(R.string.days)
    ReminderUnit.Weeks -> appString(R.string.weeks)
}

@Composable
internal fun ReminderChoice.localizedLabel(): String = when (this) {
    ReminderChoice.None -> appString(R.string.no_reminder)
    ReminderChoice.AtStart -> appString(R.string.at_start)
    ReminderChoice.AtEnd -> appString(R.string.at_end)
    ReminderChoice.FifteenMinutes -> appString(R.string.fifteen_min_before)
    ReminderChoice.TwoHours -> appString(R.string.two_hours_before)
    ReminderChoice.OneDay -> appString(R.string.one_day_before)
    ReminderChoice.OneWeek -> appString(R.string.one_week_before_full)
    ReminderChoice.Custom -> appString(R.string.custom_reminder)
}

@Composable
internal fun ModalEditorDialog(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
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
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(title, color = WarmInk, fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
                content()
            }
        }
    }
}

@Composable
internal fun DialogActions(
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    saveEnabled: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onDismiss) { Text(appString(R.string.cancel)) }
        Button(
            onClick = onSave,
            enabled = saveEnabled,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = WarmBrown),
        ) {
            Text(appString(R.string.save))
        }
    }
}

@Composable
internal fun CompactEditorHeaderLine(
    icon: ImageVector,
    title: String,
    value: String,
    actionIcon: ImageVector,
    actionDescription: String,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(19.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, color = WarmInk, fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onAction, modifier = Modifier.size(36.dp)) {
            Icon(actionIcon, contentDescription = actionDescription, tint = WarmBrown, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
internal fun CompactValueRow(value: String, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(UniversalControlHeight)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(value, color = WarmInk, fontSize = 14.sp, lineHeight = 17.sp, modifier = Modifier.weight(1f))
        IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit), modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.remove), modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
internal fun MetadataDropdownLine(
    label: String,
    options: List<out EventIcalOption>,
    selected: String,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val current = options.firstOrNull { it.value.equals(selected, ignoreCase = true) } ?: options.first()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, color = WarmInk, fontSize = 14.sp, lineHeight = 17.sp, modifier = Modifier.weight(1f))
        Box {
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(15.dp))
                    .clickable { expanded = true },
                shape = RoundedCornerShape(15.dp),
                color = WarmPeach.copy(alpha = 0.74f),
                border = BorderStroke(1.dp, WarmLine.copy(alpha = 0.72f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(current.localizedLabel(), color = WarmInk, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = WarmInk, modifier = Modifier.size(17.dp))
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                shape = RoundedCornerShape(18.dp),
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.localizedLabel()) },
                        onClick = {
                            onSelected(option.value)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun PresetListRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(UniversalControlHeight)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) WarmPeach else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (selected) WarmBrown else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)),
            )
            Text(label, color = WarmInk, fontSize = 14.sp, lineHeight = 17.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
}

@Composable
internal fun NumberUnitRow(
    amount: String,
    onAmountChange: (String) -> Unit,
    unitLabel: String,
    onUnitSelected: (String) -> Unit,
    unitOptions: List<String>,
    label: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = amount,
            onValueChange = { onAmountChange(it.filter(Char::isDigit).take(4)) },
            label = { Text(label) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.weight(1f),
        )
        DropdownPill(
            label = unitLabel,
            options = unitOptions,
            onSelected = onUnitSelected,
            modifier = Modifier.width(122.dp),
        )
    }
}

@Composable
internal fun DropdownPill(
    label: String,
    options: List<String>,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable { expanded = true },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, WarmLine),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(label, color = WarmInk, fontSize = 14.sp, lineHeight = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(18.dp),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
internal fun CompactReminderEditor(selected: Set<Int>, onSelectedChange: (Set<Int>) -> Unit) {
    var editingReminder by remember { mutableStateOf<Int?>(null) }
    var addingReminder by remember { mutableStateOf(false) }
    EditorSection {
        CompactEditorHeaderLine(
            icon = Icons.Default.Notifications,
            title = appString(R.string.reminders),
            value = if (selected.isEmpty()) appString(R.string.none) else selected.normalizedReminderOffsets().map { reminderMinuteLabel(it) }.joinToString(", "),
            actionIcon = Icons.Default.Add,
            actionDescription = stringResource(R.string.add_reminder),
            onAction = {
                editingReminder = null
                addingReminder = true
            },
        )
        AnimatedVisibility(visible = selected.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                selected.normalizedReminderOffsets().forEach { minutes ->
                    CompactValueRow(
                        value = reminderMinuteLabel(minutes),
                        onEdit = {
                            editingReminder = minutes
                            addingReminder = true
                        },
                        onDelete = { onSelectedChange(selected - minutes) },
                    )
                }
            }
        }
    }
    if (addingReminder) {
        ReminderDialog(
            initialMinutes = editingReminder,
            onDismiss = { addingReminder = false },
            onSave = { minutes ->
                onSelectedChange(
                    if (minutes == null) {
                        selected - listOfNotNull(editingReminder).toSet()
                    } else {
                        (selected - listOfNotNull(editingReminder).toSet() + minutes).normalizedReminderOffsets().toSet()
                    },
                )
                addingReminder = false
            },
        )
    }
}

@Composable
internal fun SettingsReminderEditor(
    title: String,
    selected: Set<Int>,
    onSelectedChange: (Set<Int>) -> Unit,
) {
    var editingReminder by remember { mutableStateOf<Int?>(null) }
    var addingReminder by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SettingsControlShape),
        shape = SettingsControlShape,
        color = settingsControlColor(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CompactEditorHeaderLine(
                icon = Icons.Default.Notifications,
                title = title,
                value = if (selected.isEmpty()) {
                    stringResource(R.string.none)
                } else {
                    selected.normalizedReminderOffsets().map { reminderMinuteLabel(it) }.joinToString(", ")
                },
                actionIcon = Icons.Default.Add,
                actionDescription = stringResource(R.string.add_reminder),
                onAction = {
                    editingReminder = null
                    addingReminder = true
                },
            )
            AnimatedVisibility(visible = selected.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    selected.normalizedReminderOffsets().forEach { minutes ->
                        CompactValueRow(
                            value = reminderMinuteLabel(minutes),
                            onEdit = {
                                editingReminder = minutes
                                addingReminder = true
                            },
                            onDelete = { onSelectedChange((selected - minutes).normalizedReminderOffsets().toSet()) },
                        )
                    }
                }
            }
        }
    }
    if (addingReminder) {
        ReminderDialog(
            initialMinutes = editingReminder,
            onDismiss = { addingReminder = false },
            onSave = { minutes ->
                onSelectedChange(
                    if (minutes == null) {
                        (selected - listOfNotNull(editingReminder).toSet()).normalizedReminderOffsets().toSet()
                    } else {
                        (selected - listOfNotNull(editingReminder).toSet() + minutes).normalizedReminderOffsets().toSet()
                    },
                )
                addingReminder = false
            },
        )
    }
}

@Composable
internal fun ReminderDialog(initialMinutes: Int?, onDismiss: () -> Unit, onSave: (Int?) -> Unit) {
    val initialChoice = remember(initialMinutes) {
        ReminderChoice.entries.firstOrNull { it.minutes == initialMinutes } ?: if (initialMinutes == null) ReminderChoice.None else ReminderChoice.Custom
    }
    val initial = remember(initialMinutes) { (initialMinutes ?: 15).toReminderAmountUnit() }
    var selectedChoice by rememberSaveable(initialMinutes) { mutableStateOf(initialChoice) }
    var amountText by rememberSaveable(initialMinutes) { mutableStateOf(initial.first.toString()) }
    var unit by rememberSaveable(initialMinutes) { mutableStateOf(initial.second) }
    val minuteUnitLabel = ReminderUnit.Minutes.localizedLabel()
    val hourUnitLabel = ReminderUnit.Hours.localizedLabel()
    val dayUnitLabel = ReminderUnit.Days.localizedLabel()
    val weekUnitLabel = ReminderUnit.Weeks.localizedLabel()
    val unitLabels = listOf(
        ReminderUnit.Minutes to minuteUnitLabel,
        ReminderUnit.Hours to hourUnitLabel,
        ReminderUnit.Days to dayUnitLabel,
        ReminderUnit.Weeks to weekUnitLabel,
    )
    ModalEditorDialog(title = appString(if (initialMinutes == null) R.string.add_reminder else R.string.edit_reminder), onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ReminderChoice.entries.forEach { choice ->
                    PresetListRow(
                        label = choice.localizedLabel(),
                        selected = selectedChoice == choice,
                        onClick = {
                            selectedChoice = choice
                            choice.minutes?.takeIf { it > 0 }?.let { minutes ->
                                val (amount, presetUnit) = minutes.toReminderAmountUnit()
                                amountText = amount.toString()
                                unit = presetUnit
                            }
                        },
                    )
                }
            }
            AnimatedVisibility(visible = selectedChoice == ReminderChoice.Custom) {
                NumberUnitRow(
                    amount = amountText,
                    onAmountChange = {
                        amountText = it
                        selectedChoice = ReminderChoice.Custom
                    },
                    unitLabel = unitLabels.firstOrNull { it.first == unit }?.second ?: minuteUnitLabel,
                    onUnitSelected = { label ->
                        unitLabels.firstOrNull { it.second == label }?.let { unit = it.first }
                        selectedChoice = ReminderChoice.Custom
                    },
                    unitOptions = unitLabels.map { it.second },
                    label = stringResource(R.string.custom_reminder),
                )
            }
            DialogActions(
                onDismiss = onDismiss,
                onSave = {
                    when (selectedChoice) {
                        ReminderChoice.None -> onSave(null)
                        ReminderChoice.Custom -> amountText.toIntOrNull()?.let { onSave((it * unit.minutes).coerceIn(0, MAX_REMINDER_MINUTES)) }
                        else -> onSave(selectedChoice.minutes)
                    }
                },
                saveEnabled = selectedChoice != ReminderChoice.Custom || amountText.toIntOrNull() != null,
            )
        }
    }
}

@Composable
internal fun ReminderEditor(selected: Set<Int>, onToggle: (Int) -> Unit) {
    var customMinutesText by rememberSaveable { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.editorInset(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Default.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Text(appString(R.string.reminders), color = WarmInk, fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold)
        }
        FadedHorizontalScrollRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReminderPresets.forEach { minutes ->
                FilterChip(
                    selected = minutes in selected,
                    onClick = { onToggle(minutes) },
                    label = { Text(reminderMinuteLabel(minutes)) },
                )
            }
        }
        Row(
            modifier = Modifier.editorInset(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = customMinutesText,
                onValueChange = { value -> customMinutesText = value.filter { it.isDigit() }.take(5) },
                label = { Text(appString(R.string.own_minutes_before)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = {
                    customMinutesText.toIntOrNull()?.let { onToggle(it.coerceIn(0, 60 * 24 * 30)) }
                    customMinutesText = ""
                },
                enabled = customMinutesText.toIntOrNull() != null,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WarmBrown),
            ) {
                Text(appString(R.string.add))
            }
        }
        selected
            .filterNot { preset -> preset in ReminderPresets }
            .sorted()
            .takeIf { it.isNotEmpty() }
            ?.let { values ->
                Text(
                    "${appString(R.string.custom)}: ${values.map { reminderMinuteLabel(it) }.joinToString(", ")}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.editorInset(),
                )
            }
    }
}

@Composable
internal fun CompactRecurrenceEditor(recurrenceRule: String, onRecurrenceRuleChange: (String?) -> Unit) {
    var dialogOpen by remember { mutableStateOf(false) }
    EditorSection {
        CompactEditorHeaderLine(
            icon = Icons.Default.Repeat,
            title = appString(R.string.recurrence),
            value = recurrenceRule.takeIf { it.isNotBlank() }?.toLocalizedRecurrenceLabel() ?: appString(R.string.none),
            actionIcon = if (recurrenceRule.isBlank()) Icons.Default.Add else Icons.Default.Edit,
            actionDescription = if (recurrenceRule.isBlank()) stringResource(R.string.add_recurrence) else stringResource(R.string.edit_recurrence),
            onAction = { dialogOpen = true },
        )
    }
    if (dialogOpen) {
        RecurrenceDialog(
            recurrenceRule = recurrenceRule,
            onDismiss = { dialogOpen = false },
            onSave = {
                onRecurrenceRuleChange(it)
                dialogOpen = false
            },
        )
    }
}

@Composable
internal fun RecurrenceDialog(
    recurrenceRule: String,
    onDismiss: () -> Unit,
    onSave: (String?) -> Unit,
) {
    var draft by remember(recurrenceRule) { mutableStateOf(RecurrenceDraft.fromRule(recurrenceRule)) }
    fun update(next: RecurrenceDraft) {
        draft = next
    }
    val dailyUnitLabel = RecurrenceOption.Daily.localizedIntervalUnitLabel()
    val weeklyUnitLabel = RecurrenceOption.Weekly.localizedIntervalUnitLabel()
    val monthlyUnitLabel = RecurrenceOption.Monthly.localizedIntervalUnitLabel()
    val yearlyUnitLabel = RecurrenceOption.Yearly.localizedIntervalUnitLabel()
    val recurrenceUnitLabels = listOf(
        RecurrenceOption.Daily to dailyUnitLabel,
        RecurrenceOption.Weekly to weeklyUnitLabel,
        RecurrenceOption.Monthly to monthlyUnitLabel,
        RecurrenceOption.Yearly to yearlyUnitLabel,
    )
    ModalEditorDialog(title = appString(R.string.recurrence), onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            RepeatModeSegmented(
                repeated = draft.option != RecurrenceOption.Once,
                onRepeatedChange = { repeated ->
                    update(if (repeated) draft.copy(option = RecurrenceOption.Weekly, interval = draft.interval.coerceAtLeast(1)) else RecurrenceDraft())
                },
            )
            AnimatedVisibility(visible = draft.option != RecurrenceOption.Once) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumberUnitRow(
                        amount = draft.interval.toString(),
                        onAmountChange = { value ->
                            update(draft.copy(interval = value.toIntOrNull()?.coerceIn(1, 999) ?: 1))
                        },
                        unitLabel = draft.option.localizedIntervalUnitLabel(),
                        onUnitSelected = { label ->
                            val option = recurrenceUnitLabels.firstOrNull { it.second == label }?.first ?: draft.option
                            update(draft.copy(option = option))
                        },
                        unitOptions = recurrenceUnitLabels.map { it.second },
                        label = stringResource(R.string.every),
                    )
                    Text(stringResource(R.string.ends), color = WarmInk, fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold)
                    RecurrenceEndSegmented(
                        selected = draft.endMode,
                        onSelected = { update(draft.copy(endMode = it)) },
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp),
                        contentAlignment = Alignment.TopStart,
                    ) {
                        AnimatedContent(
                            targetState = draft.endMode,
                            transitionSpec = {
                                (fadeIn(tween(MotionShort, easing = MotionStandard)) + scaleIn(tween(MotionShort, easing = MotionStandard), initialScale = 0.98f)) togetherWith
                                    (fadeOut(tween(MotionShort, easing = MotionStandardAccelerate)) + scaleOut(tween(MotionShort, easing = MotionStandardAccelerate), targetScale = 0.98f))
                            },
                            label = "recurrenceEndInput",
                        ) { endMode ->
                            when (endMode) {
                                RecurrenceEndMode.Never -> Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f),
                                    border = BorderStroke(1.dp, WarmLine.copy(alpha = 0.62f)),
                                ) {
                                    Text(
                                        stringResource(R.string.repeat_never_ends),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 14.sp,
                                        lineHeight = 17.sp,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                                    )
                                }
                                RecurrenceEndMode.Count -> OutlinedTextField(
                                    value = draft.count?.toString().orEmpty(),
                                    onValueChange = { value ->
                                        update(draft.copy(count = value.filter(Char::isDigit).toIntOrNull()?.coerceIn(1, 999)))
                                    },
                                    label = { Text(stringResource(R.string.repeat_after_count)) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                RecurrenceEndMode.Until -> DatePickerField(
                                    value = draft.untilDate.orEmpty(),
                                    onValueChange = { value -> update(draft.copy(untilDate = value.take(10))) },
                                    label = { Text(stringResource(R.string.end_date)) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
            AnimatedVisibility(visible = draft.option == RecurrenceOption.Once) {
                Text(
                    stringResource(R.string.does_not_repeat),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    lineHeight = 17.sp,
                )
            }
            DialogActions(
                onDismiss = onDismiss,
                onSave = { onSave(draft.toRule()) },
                saveEnabled = true,
            )
        }
    }
}

@Composable
internal fun RepeatModeSegmented(
    repeated: Boolean,
    onRepeatedChange: (Boolean) -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, WarmLine.copy(alpha = 0.72f), shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(false to stringResource(R.string.once), true to stringResource(R.string.multiple_times)).forEachIndexed { index, (value, label) ->
            val selected = repeated == value
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .background(if (selected) WarmPeach else MaterialTheme.colorScheme.surface)
                    .clickable { onRepeatedChange(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = WarmInk,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
            if (index == 0) {
                Box(Modifier.width(1.dp).height(42.dp).background(WarmLine.copy(alpha = 0.72f)))
            }
        }
    }
}

@Composable
internal fun RecurrenceEndSegmented(
    selected: RecurrenceEndMode,
    onSelected: (RecurrenceEndMode) -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, WarmLine.copy(alpha = 0.72f), shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RecurrenceEndMode.entries.forEachIndexed { index, mode ->
            val selectedMode = selected == mode
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .background(if (selectedMode) WarmPeach else MaterialTheme.colorScheme.surface)
                    .clickable { onSelected(mode) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    when (mode) {
                        RecurrenceEndMode.Never -> stringResource(R.string.never)
                        RecurrenceEndMode.Count -> stringResource(R.string.count)
                        RecurrenceEndMode.Until -> stringResource(R.string.date)
                    },
                    color = WarmInk,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    fontWeight = if (selectedMode) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
            if (index < RecurrenceEndMode.entries.lastIndex) {
                Box(Modifier.width(1.dp).height(42.dp).background(WarmLine.copy(alpha = 0.72f)))
            }
        }
    }
}

@Composable
internal fun RecurrenceEditor(recurrenceRule: String, onRecurrenceRuleChange: (String?) -> Unit) {
    val draft = remember(recurrenceRule) { RecurrenceDraft.fromRule(recurrenceRule) }
    fun update(next: RecurrenceDraft) {
        onRecurrenceRuleChange(next.toRule())
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.editorInset(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Default.Repeat, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Text(stringResource(R.string.recurrence), color = WarmInk, fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold)
        }
        FadedHorizontalScrollRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RecurrenceOption.entries.filterNot { it == RecurrenceOption.Custom }.forEach { option ->
                FilterChip(
                    selected = draft.option == option,
                    onClick = {
                        update(
                            if (option == RecurrenceOption.Once) {
                                RecurrenceDraft()
                            } else {
                                draft.copy(option = option, interval = draft.interval.coerceAtLeast(1))
                            },
                        )
                    },
                    label = { Text(option.localizedLabel()) },
                )
            }
        }
        AnimatedVisibility(visible = draft.option != RecurrenceOption.Once) {
            Column(
                modifier = Modifier.editorInset(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = draft.interval.toString(),
                    onValueChange = { value ->
                        update(draft.copy(interval = value.filter { it.isDigit() }.toIntOrNull()?.coerceIn(1, 999) ?: 1))
                    },
                    label = { Text(appString(R.string.every_x, draft.option.localizedIntervalUnitLabel())) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.ends), color = WarmInk, fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold)
                FadedHorizontalScrollRow(contentPadding = PaddingValues(horizontal = 0.dp)) {
                    RecurrenceEndMode.entries.forEach { mode ->
                        FilterChip(
                            selected = draft.endMode == mode,
                            onClick = { update(draft.copy(endMode = mode)) },
                            label = { Text(mode.localizedLabel()) },
                        )
                    }
                }
                AnimatedVisibility(visible = draft.endMode == RecurrenceEndMode.Count) {
                    OutlinedTextField(
                        value = draft.count?.toString().orEmpty(),
                        onValueChange = { value ->
                            update(draft.copy(count = value.filter { it.isDigit() }.toIntOrNull()?.coerceIn(1, 999)))
                        },
                        label = { Text(stringResource(R.string.repeat_after_count)) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                AnimatedVisibility(visible = draft.endMode == RecurrenceEndMode.Until) {
                    DatePickerField(
                        value = draft.untilDate.orEmpty(),
                        onValueChange = { value -> update(draft.copy(untilDate = value.take(10))) },
                        label = { Text(stringResource(R.string.end_date)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(
                    draft.toRule()?.toLocalizedRecurrenceLabel() ?: stringResource(R.string.one_time),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                )
            }
        }
    }
}

internal enum class RecurrenceEndMode(val label: String) {
    Never("Never"),
    Count("After count"),
    Until("Until date"),
}

@Composable
internal fun RecurrenceEndMode.localizedLabel(): String = when (this) {
    RecurrenceEndMode.Never -> appString(R.string.never)
    RecurrenceEndMode.Count -> appString(R.string.after_count)
    RecurrenceEndMode.Until -> appString(R.string.until_date)
}

internal data class RecurrenceDraft(
    val option: RecurrenceOption = RecurrenceOption.Once,
    val interval: Int = 1,
    val endMode: RecurrenceEndMode = RecurrenceEndMode.Never,
    val count: Int? = null,
    val untilDate: String? = null,
) {
    fun toRule(): String? {
        if (option == RecurrenceOption.Once) return null
        val freq = option.rule?.substringAfter("FREQ=")?.substringBefore(';') ?: return null
        val parts = mutableListOf("FREQ=$freq")
        if (interval > 1) parts += "INTERVAL=${interval.coerceAtLeast(1)}"
        when (endMode) {
            RecurrenceEndMode.Never -> Unit
            RecurrenceEndMode.Count -> count?.coerceAtLeast(1)?.let { parts += "COUNT=$it" }
            RecurrenceEndMode.Until -> untilDate?.toRecurrenceUntilValue()?.let { parts += "UNTIL=$it" }
        }
        return parts.joinToString(";")
    }

    companion object {
        fun fromRule(rule: String): RecurrenceDraft {
            val freq = rule.recurrenceFrequency()
            val option = when (freq) {
                "DAILY" -> RecurrenceOption.Daily
                "WEEKLY" -> RecurrenceOption.Weekly
                "MONTHLY" -> RecurrenceOption.Monthly
                "YEARLY" -> RecurrenceOption.Yearly
                else -> RecurrenceOption.Once
            }
            val interval = rule.recurrencePart("INTERVAL")?.toIntOrNull()?.coerceIn(1, 999) ?: 1
            val count = rule.recurrencePart("COUNT")?.toIntOrNull()?.coerceIn(1, 999)
            val until = rule.recurrencePart("UNTIL")?.toIsoUntilDate()
            val endMode = when {
                count != null -> RecurrenceEndMode.Count
                until != null -> RecurrenceEndMode.Until
                else -> RecurrenceEndMode.Never
            }
            return RecurrenceDraft(option = option, interval = interval, endMode = endMode, count = count, untilDate = until)
        }
    }
}
