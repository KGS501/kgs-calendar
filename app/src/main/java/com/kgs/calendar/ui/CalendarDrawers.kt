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
internal fun CalendarDrawer(
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
                DrawerViewItem(
                    stringResource(CalendarViewMode.ThreeDay.labelRes(state.weekViewEnabled)),
                    Icons.Default.ViewWeek,
                    CalendarViewMode.ThreeDay,
                    state.selectedView,
                    onViewSelected,
                )
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
internal fun HiddenCalendarCreationDialog(
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
internal fun CalendarCapabilityChip(label: String, color: Color) {
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
internal fun TaskDrawer(
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
