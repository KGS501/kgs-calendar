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
internal fun CalendarUiState.problemItems(): List<ProblemItem> =
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

internal data class SmoothRemovalResult<T>(
    val items: List<T>,
    val exitingResourceHrefs: Set<String>,
)

@Composable
internal fun <T> rememberSmoothRemoval(
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

internal fun String.isReadOnlyCollectionHrefUi(): Boolean = startsWith(UiReadOnlyCollectionPrefix)

internal fun CollectionEntity.isReadOnlyForUi(): Boolean = readOnly || href.isReadOnlyCollectionHrefUi()

internal fun CollectionEntity.canDeleteFromServerForUi(): Boolean =
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

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

internal fun AppLanguageMode.resolveLocale(context: Context): Locale =
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

internal fun Int.toHexText(): String =
    "#%06X".format(this and 0x00FFFFFF)

private fun String.parseHexColorOrNull(): Int? {
    val normalized = trim().removePrefix("#")
    if (normalized.length != 6 || normalized.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) return null
    return (0xFF000000.toInt() or normalized.toInt(16))
}

internal fun LocalTime.nextDraftStart(): LocalTime {
    val nextHourMinute = ((hour + 1).coerceAtMost(23)) * 60
    return nextHourMinute.toDraftLocalTime()
}

internal fun LocalTime.defaultDraftEnd(durationMinutes: Int = 60): LocalTime =
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

internal fun EventEntity.smoothRemovalKey(): String =
    "${resourceHref}:${startsAtMillis}"

internal fun TaskEntity.smoothRemovalKey(): String =
    "${resourceHref}:${startAtMillis ?: dueAtMillis ?: completedAtMillis ?: 0L}"

internal fun List<TaskEntity>.indexTasksByDay(): Map<LocalDate, List<TaskEntity>> =
    flatMap { task -> task.visibleDates().map { it to task } }
        .groupBy({ it.first }, { it.second })

internal fun CalendarUiState.defaultFabCreationDate(today: LocalDate = LocalDate.now()): LocalDate =
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
