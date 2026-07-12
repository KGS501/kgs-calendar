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
internal fun DetailSheetContent(
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

internal fun taskHierarchyLayerZ(depth: Int): Float = 100f - depth.coerceAtLeast(0)

internal fun taskPriorityLayerZ(task: TaskEntity, animationsEnabled: Boolean): Float {
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

internal fun Modifier.taskHierarchyConnector(
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

internal fun Modifier.taskHierarchyParentTail(
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
internal enum class EventDeleteScope { This, ThisAndFollowing, All }

@Composable
internal fun RecurringSaveScopeDialog(
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
