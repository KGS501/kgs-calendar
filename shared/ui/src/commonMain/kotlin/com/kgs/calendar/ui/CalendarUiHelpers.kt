@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.kgs.calendar.ui

import com.kgs.calendar.time.*
import kotlinx.datetime.*
import com.kgs.calendar.domain.task.timedMultiDayMiddleDates
import com.kgs.calendar.ui.time.dayMonthFormatter
import com.kgs.calendar.platform.HttpRequestBuilder
import com.kgs.calendar.platform.LruCache
import com.kgs.calendar.platform.formUrlEncode
import com.kgs.calendar.platform.kgsPlatformName
import com.kgs.calendar.platform.percentDecode
import com.kgs.calendar.platform.platformHttpTransport
import com.kgs.calendar.ui.platform.PlatformContext
import com.kgs.calendar.ui.platform.decodeImageBitmap
import com.kgs.calendar.ui.platform.openMapQuery
import com.kgs.calendar.ui.platform.systemLocale
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.IO
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Event
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import com.kgs.calendar.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.input.key.key
import com.kgs.calendar.data.remote.isSyncConflictError
import com.kgs.calendar.domain.source.CategorySync
import com.kgs.calendar.domain.source.ReadOnlyFeedStatus
import com.kgs.calendar.domain.source.readOnlyFeedStatus
import com.kgs.calendar.ui.res.Res
import com.kgs.calendar.data.settings.AppLanguageMode
import com.kgs.calendar.data.local.entity.CollectionEntity
import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.local.entity.PendingMutationEntity
import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.data.settings.TaskColorMode
import com.kgs.calendar.domain.event.displayColor
import com.kgs.calendar.domain.event.isCancelled
import com.kgs.calendar.domain.event.isTentative
import com.kgs.calendar.domain.model.CalendarViewMode
import com.kgs.calendar.domain.model.MutationAction
import com.kgs.calendar.domain.model.SourceType
import com.kgs.calendar.domain.model.normalizedReminderOffsets
import com.kgs.calendar.domain.model.timelineDayCount
import com.kgs.calendar.domain.model.timelineVisibleAnchor
import com.kgs.calendar.domain.task.displayColor
import com.kgs.calendar.domain.sync.pendingMutationFor
import com.kgs.calendar.domain.task.effectiveStatus
import com.kgs.calendar.domain.task.occurrenceRecurrenceIdMillis
import com.kgs.calendar.domain.time.toDate
import com.kgs.calendar.domain.time.toTimeText
import com.kgs.calendar.ui.calendar.DayEndHour
import com.kgs.calendar.ui.calendar.DayStartHour
import com.kgs.calendar.ui.calendar.toDayDate
import com.kgs.calendar.ui.calendar.toDayPage
import com.kgs.calendar.ui.editor.EditorSchedulePreview
import com.kgs.calendar.ui.editor.EditorScheduleState
import com.kgs.calendar.ui.labels.RecurrenceOption
import com.kgs.calendar.ui.labels.recurrenceFrequency
import com.kgs.calendar.ui.labels.recurrencePart
import com.kgs.calendar.ui.labels.toIsoUntilDate
import com.kgs.calendar.ui.layout.AllDayOverlayItem
import com.kgs.calendar.ui.layout.AllDayScene
import com.kgs.calendar.ui.layout.allDayViewportPriorityTier
import com.kgs.calendar.ui.layout.buildAllDayScene
import com.kgs.calendar.ui.model.allDayTopEndDate
import com.kgs.calendar.ui.model.allDayTopStartDate
import com.kgs.calendar.ui.model.occurrenceStartForEdit
import com.kgs.calendar.ui.model.visibleDates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.kgs.calendar.json.JSONArray
import kotlinx.datetime.YearMonth
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import com.kgs.calendar.time.ZoneId
import com.kgs.calendar.time.DateTimeFormatter
import com.kgs.calendar.time.AppLocale as Locale
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

fun Modifier.dashedBorder(color: Color): Modifier = drawWithContent {
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

fun Modifier.dashedBorder(color: Color, radius: Dp): Modifier = drawWithContent {
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

fun Modifier.horizontalEdgeFade(
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

fun Modifier.horizontalBleed(bleed: Dp): Modifier =
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

fun Modifier.editorInset(): Modifier = padding(horizontal = EditorHorizontalPadding)

fun Modifier.verticalClipAllowHorizontalOverflow(): Modifier = drawWithContent {
    clipRect(
        left = -size.width * 4f,
        top = 0f,
        right = size.width * 5f,
        bottom = size.height,
    ) {
        this@drawWithContent.drawContent()
    }
}

data class LocationAnchor(val latitude: Double, val longitude: Double) {
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

data class LocationSuggestion(
    val displayName: String,
    val primaryName: String,
    val latitude: Double,
    val longitude: Double,
)

data class LocationSelection(
    val value: String,
    val mapVerified: Boolean,
)

object LocationLookup {
    private val USER_AGENT = "KGSCalendar/1.0 $kgsPlatformName (com.kgs501.kgscalendar)"
    private const val SEARCH_CACHE_ENTRIES = 64
    private const val TILE_CACHE_BYTES = 4 * 1024 * 1024
    private const val MAP_CACHE_BYTES = 8 * 1024 * 1024

    private val client by lazy { platformHttpTransport(connectTimeoutSeconds = 8, readTimeoutSeconds = 10) }
    // Bounded: every viewed location adds a ~650 KB preview bitmap, which must not pile up for the
    // whole process lifetime.
    private val searchCache = LruCache<String, List<LocationSuggestion>>(SEARCH_CACHE_ENTRIES)
    private val byteCache = object : LruCache<String, ByteArray>(TILE_CACHE_BYTES) {
        override fun sizeOf(key: String, value: ByteArray): Int = value.size
    }
    private val mapCache = object : LruCache<String, ImageBitmap>(MAP_CACHE_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    suspend fun search(query: String, limit: Int = 8, anchor: LocationAnchor? = null, allowAliases: Boolean = true): List<LocationSuggestion> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 3) return@withContext emptyList()
        val locale = com.kgs.calendar.time.defaultAppLocale()
        val country = locale.toLanguageTag().split('-').drop(1).firstOrNull { it.length == 2 && it.all(Char::isLetter) }?.lowercase()
        val language = locale.toLanguageTag()
        val key = listOf(trimmed.lowercase(), country.orEmpty(), language, limit, anchor?.cacheKey.orEmpty(), allowAliases).joinToString("|")
        searchCache.get(key)?.let { return@withContext it }
        val countryParam = country?.let { "&countrycodes=$it" }.orEmpty()
        val viewboxParam = anchor?.nominatimViewboxParam().orEmpty()
        val encodedQuery = formUrlEncode(trimmed)
        val url = "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=$limit&addressdetails=1&q=$encodedQuery$countryParam$viewboxParam"
        val response = client.execute(
            HttpRequestBuilder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept-Language", language)
                .build(),
        )
        if (!response.isSuccessful) return@withContext emptyList()
        val body = response.bodyText()
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
        searchCache.put(key, parsed)
        parsed
    }

    suspend fun bytes(url: String): ByteArray = withContext(Dispatchers.IO) {
        byteCache.get(url)?.let { return@withContext it }
        val response = client.execute(HttpRequestBuilder().url(url).header("User-Agent", USER_AGENT).build())
        if (!response.isSuccessful) error("Map request failed: ${response.code}")
        val bytes = response.body.takeIf { it.isNotEmpty() } ?: error("Empty map response")
        byteCache.put(url, bytes)
        bytes
    }

    suspend fun mapPreviewBitmap(suggestion: LocationSuggestion, width: Int = 640, height: Int = 260, zoom: Int = 15): ImageBitmap = withContext(Dispatchers.IO) {
        val key = "${suggestion.latitude},${suggestion.longitude},$width,$height,$zoom"
        mapCache.get(key)?.let { return@withContext it }
        val tileSize = 256
        val tileCount = 1 shl zoom
        val latRad = suggestion.latitude * PI / 180.0
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
        val bitmap = ImageBitmap(width, height)
        val canvas = Canvas(bitmap)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), Paint().apply { color = Color(0xFFE9DED8) })
        val paint = Paint().apply { isAntiAlias = true }
        for (tileX in firstTileX..lastTileX) {
            for (tileY in firstTileY..lastTileY) {
                if (tileY !in 0 until tileCount) continue
                val wrappedTileX = ((tileX % tileCount) + tileCount) % tileCount
                val tileUrl = "https://tile.openstreetmap.org/$zoom/$wrappedTileX/$tileY.png"
                val tileBytes = runCatching { bytes(tileUrl) }.getOrNull() ?: continue
                val tile = decodeImageBitmap(tileBytes) ?: continue
                val destLeft = (tileX * tileSize - leftPixel).roundToInt()
                val destTop = (tileY * tileSize - topPixel).roundToInt()
                canvas.drawImageRect(
                    tile,
                    srcSize = IntSize(tile.width, tile.height),
                    dstOffset = IntOffset(destLeft, destTop),
                    dstSize = IntSize(tileSize, tileSize),
                    paint = paint,
                )
            }
        }
        val cx = width / 2f
        val cy = height / 2f
        canvas.drawCircle(Offset(cx, cy), 16f, Paint().apply { isAntiAlias = true; color = Color.White; style = PaintingStyle.Stroke; strokeWidth = 5f })
        canvas.drawCircle(Offset(cx, cy), 12f, Paint().apply { isAntiAlias = true; color = Color(0xFFE53935) })
        canvas.drawCircle(Offset(cx, cy), 5f, Paint().apply { isAntiAlias = true; color = Color.White })
        mapCache.put(key, bitmap)
        bitmap
    }
}

fun String.shouldAttemptMapPreview(mapVerified: Boolean?): Boolean {
    if (mapVerified == false) return false
    if (mapVerified == true) return cleanCalendarDisplayText().length >= 3
    val cleaned = cleanCalendarDisplayText()
    if (cleaned.length < 8) return false
    val commaCount = cleaned.count { it == ',' }
    val hasAddressSignal = commaCount >= 2 || (commaCount >= 1 && cleaned.any { it.isDigit() })
    return hasAddressSignal && !cleaned.contains('\n')
}

/** Opens the maps app on [location], cleaned of calendar markup; nothing for blank text. */
fun PlatformContext.openMapLocation(location: String) {
    val cleaned = location.cleanCalendarDisplayText()
    if (cleaned.isBlank()) return
    openMapQuery(cleaned)
}

fun TaskEntity.displayProgress(): Int =
    when {
        isCompleted -> 100
        else -> percentComplete?.coerceIn(0, 100) ?: 0
    }

fun TaskEntity.cardTextDecoration(): TextDecoration? =
    if (effectiveStatus() == "CANCELLED") TextDecoration.LineThrough else null

data class EventCardVisuals(
    val baseColor: Color,
    val background: Color,
    val contentColor: Color,
    val borderColor: Color?,
    val dashedBorder: Boolean,
    val textDecoration: TextDecoration?,
)

fun EventEntity.cardVisuals(muted: Boolean = false, darkPalette: Boolean): EventCardVisuals {
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

fun Color.greyedOut(amount: Float): Color {
    val gray = (red * 0.299f + green * 0.587f + blue * 0.114f).coerceIn(0f, 1f)
    val mix = amount.coerceIn(0f, 1f)
    return Color(
        red = red + (gray - red) * mix,
        green = green + (gray - green) * mix,
        blue = blue + (gray - blue) * mix,
        alpha = alpha,
    )
}

fun String?.toCategoryTags(): List<String> =
    this
        ?.split(',')
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        ?.distinctBy { it.lowercase() }
        .orEmpty()

fun List<String>.toCategoriesCsv(): String =
    map { it.trim() }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase() }
        .joinToString(",")

/** The user's categories: those used in the calendars whose categories count (see [CategorySync]). */
fun CalendarUiState.allKnownCategoryTags(): List<String> {
    val sharedHrefs = collections.filter(categorySync::usesCategoriesOf).mapTo(HashSet()) { it.href }
    return buildList {
        fun add(collectionHref: String, categories: String?) {
            if (collectionHref in sharedHrefs) addAll(categories.toCategoryTags())
        }
        events.forEach { add(it.collectionHref, it.categories) }
        datedTasks.forEach { add(it.collectionHref, it.categories) }
        inboxTasks.forEach { add(it.collectionHref, it.categories) }
        scheduledOpenTasks.forEach { add(it.collectionHref, it.categories) }
        completedTasks.forEach { add(it.collectionHref, it.categories) }
    }.distinctBy { it.lowercase() }.sortedBy { it.lowercase() }
}

data class ProblemItem(
    val id: String,
    val title: String,
    val body: String,
    val target: ProblemTarget? = null,
    /** Set for an item whose upload hit a conflict; the user picks the version to keep. */
    val conflictResourceHref: String? = null,
)

sealed interface ProblemTarget {
    data class Event(val event: EventEntity) : ProblemTarget
    data class Task(val task: TaskEntity) : ProblemTarget
}

@Composable
fun CalendarUiState.problemItems(): List<ProblemItem> =
    buildList {
        message
            ?.takeIf { it.isNotBlank() && it.isProblemMessage() }
            ?.let {
                add(ProblemItem("message-$it", stringResource(Res.string.app_notice), friendlyErrorText(it)))
            }
        (accounts + listOfNotNull(account))
            .distinctBy { it.id }
            .forEach { source ->
                val sourceName = source.displayName ?: source.username
                val feedCollection = collections.firstOrNull { it.accountId == source.id }
                val feedStatus = feedCollection?.let { readOnlyFeedStatus(source, it) }
                // Feeds of one website share a host name; their calendar names tell them apart.
                val feedName = feedCollection?.displayName ?: sourceName
                source.syncError?.takeIf { it.isNotBlank() }?.let { error ->
                    add(
                        if (feedStatus == ReadOnlyFeedStatus.NoDataYet) {
                            // A subscription that never loaded: it was added, it just has nothing to show yet.
                            ProblemItem(
                                id = "source-${source.id}",
                                title = stringResource(Res.string.problem_feed_no_data_title, feedName),
                                body = stringResource(Res.string.problem_feed_no_data_body, friendlyErrorText(error)),
                            )
                        } else {
                            ProblemItem(
                                id = "source-${source.id}",
                                title = stringResource(Res.string.source_named, sourceName),
                                body = friendlyErrorText(error),
                            )
                        }
                    )
                }
                if (feedStatus == ReadOnlyFeedStatus.Empty) {
                    add(
                        ProblemItem(
                            id = "feed-empty-${source.id}",
                            title = stringResource(Res.string.problem_feed_empty_title, feedName),
                            body = stringResource(Res.string.problem_feed_empty_body),
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
                event != null -> stringResource(Res.string.event)
                task != null -> stringResource(Res.string.task)
                resource.componentType == com.kgs.calendar.domain.model.ComponentType.Event -> stringResource(Res.string.event)
                resource.componentType == com.kgs.calendar.domain.model.ComponentType.Task -> stringResource(Res.string.task)
                else -> stringResource(Res.string.item)
            }
            val itemTitle = (event?.title ?: task?.title)
                ?.ifBlank { stringResource(Res.string.no_title) }
                ?: stringResource(Res.string.unknown_item)
            val conflict = resource.syncError.isSyncConflictError()
            add(
                ProblemItem(
                    id = "resource-${resource.href}",
                    title = "$typeLabel: $itemTitle",
                    body = if (conflict) stringResource(Res.string.sync_conflict_body) else friendlyErrorText(resource.syncError.orEmpty()),
                    target = target,
                    conflictResourceHref = resource.href.takeIf { conflict },
                ),
            )
        }
        // A conflict already stands for its waiting change.
        val conflictHrefs = problemResources.filter { it.syncError.isSyncConflictError() }.mapTo(HashSet()) { it.href }
        pendingMutationItems.filterNot { it.resourceHref in conflictHrefs }.forEach { mutation ->
            val event = eventCandidates.firstOrNull { it.resourceHref == mutation.resourceHref }
            val task = taskCandidates.firstOrNull { it.resourceHref == mutation.resourceHref }
            val target = when {
                event != null -> ProblemTarget.Event(event)
                task != null -> ProblemTarget.Task(task)
                else -> null
            }
            val itemTitle = (event?.title ?: task?.title)
                ?.ifBlank { stringResource(Res.string.no_title) }
                ?: stringResource(Res.string.unknown_item)
            val actionLabel = when (mutation.action) {
                MutationAction.Delete -> stringResource(Res.string.delete_waits_sync)
                MutationAction.Put -> stringResource(Res.string.change_waits_sync)
                else -> stringResource(Res.string.change_waits_sync)
            }
            val typeLabel = when (mutation.componentType) {
                com.kgs.calendar.domain.model.ComponentType.Event -> stringResource(Res.string.event)
                com.kgs.calendar.domain.model.ComponentType.Task -> stringResource(Res.string.task)
                else -> stringResource(Res.string.item)
            }
            add(
                ProblemItem(
                    id = "pending-${mutation.id}",
                    title = "$typeLabel: $itemTitle",
                    body = stringResource(Res.string.pending_problem_body, actionLabel),
                    target = target,
                ),
            )
        }
    }.distinctBy { it.id }

fun String.isProblemMessage(): Boolean {
    val lower = lowercase()
    return listOf("error", "failed", "fehl", "konnte", "unable", "sync", "warn").any { it in lower }
}

data class SmoothRemovalResult<T>(
    val items: List<T>,
    val exitingResourceHrefs: Set<String>,
)

@Composable
fun <T> rememberSmoothRemoval(
    items: List<T>,
    itemKey: (T) -> String,
    resourceHref: (T) -> String,
    retainedResourceHrefs: Set<String>,
): SmoothRemovalResult<T> {
    val scope = rememberCoroutineScope()
    val exitingItems = remember { mutableStateMapOf<String, T>() }
    var previousItems by remember { mutableStateOf<Map<String, T>>(emptyMap()) }
    if (retainedResourceHrefs.isEmpty()) {
        SideEffect {
            exitingItems.clear()
            previousItems = emptyMap()
        }
        return SmoothRemovalResult(items = items, exitingResourceHrefs = emptySet())
    }
    val currentByKey = remember(items, retainedResourceHrefs) {
        items.asSequence()
            .filter { resourceHref(it) in retainedResourceHrefs }
            .associateBy(itemKey)
    }
    val removedNow = previousItems
        .filterKeys { it !in currentByKey.keys }
        .filterValues { resourceHref(it) in retainedResourceHrefs }
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

/** The pending change that applies to this occurrence of [event], if any. */
fun List<PendingMutationEntity>.pendingMutationForEvent(event: EventEntity): PendingMutationEntity? =
    pendingMutationFor(event.resourceHref) { event.occurrenceStartForEdit() }

/** The pending change that applies to this occurrence of [task], if any. */
fun List<PendingMutationEntity>.pendingMutationForTask(task: TaskEntity): PendingMutationEntity? =
    pendingMutationFor(task.resourceHref) { task.occurrenceRecurrenceIdMillis() }

@Composable
fun pendingMutationFor(event: EventEntity): PendingMutationEntity? {
    val mutations = LocalPendingMutations.current
    return remember(mutations, event) { mutations.pendingMutationForEvent(event) }
}

@Composable
fun pendingMutationFor(task: TaskEntity): PendingMutationEntity? {
    val mutations = LocalPendingMutations.current
    return remember(mutations, task) { mutations.pendingMutationForTask(task) }
}

@Composable
fun pendingDeleteAlpha(event: EventEntity): Float =
    pendingDeleteAlpha(event.resourceHref, pendingMutationFor(event))

@Composable
fun pendingDeleteAlpha(task: TaskEntity): Float =
    pendingDeleteAlpha(task.resourceHref, pendingMutationFor(task))

/** [pendingDeleteAlpha] of whichever of [event] and [task] is set; fully opaque for neither. */
@Composable
fun pendingDeleteAlpha(event: EventEntity?, task: TaskEntity?): Float = when {
    event != null -> pendingDeleteAlpha(event)
    task != null -> pendingDeleteAlpha(task)
    else -> 1f
}

@Composable
private fun pendingDeleteAlpha(resourceHref: String, pending: PendingMutationEntity?): Float {
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
fun PendingMutationBadge(
    event: EventEntity,
    modifier: Modifier = Modifier,
) {
    PendingMutationBadge(pendingMutationFor(event), modifier)
}

@Composable
fun PendingMutationBadge(
    task: TaskEntity,
    modifier: Modifier = Modifier,
) {
    PendingMutationBadge(pendingMutationFor(task), modifier)
}

/** [PendingMutationBadge] of whichever of [event] and [task] is set; nothing for neither. */
@Composable
fun PendingMutationBadge(
    event: EventEntity?,
    task: TaskEntity?,
    modifier: Modifier = Modifier,
) {
    when {
        event != null -> PendingMutationBadge(event, modifier)
        task != null -> PendingMutationBadge(task, modifier)
    }
}

@Composable
private fun PendingMutationBadge(
    pending: PendingMutationEntity?,
    modifier: Modifier,
) {
    var visible by remember(pending?.id, pending?.createdAtMillis) {
        mutableStateOf(
            pending != null &&
                nowMillis() - pending.createdAtMillis >= PENDING_BADGE_DELAY_MILLIS,
        )
    }
    LaunchedEffect(pending?.id, pending?.createdAtMillis) {
        val mutation = pending
        if (mutation == null) {
            visible = false
            return@LaunchedEffect
        }
        val remaining = PENDING_BADGE_DELAY_MILLIS -
            (nowMillis() - mutation.createdAtMillis)
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

data class CalendarParticipant(
    val name: String,
    val email: String,
    val partstat: String = "NEEDS-ACTION",
    val role: String = "REQ-PARTICIPANT",
    val rsvp: Boolean = false,
    val scheduleStatus: String? = null,
) {
    val displayName: String = name.ifBlank { email }
}

enum class ParticipantRoleOption(val value: String, val label: String) {
    Chair("CHAIR", "Chair"),
    Required("REQ-PARTICIPANT", "Required participant"),
    Optional("OPT-PARTICIPANT", "Optional participant"),
    NonParticipant("NON-PARTICIPANT", "Non-participant"),
}

@Composable
fun ParticipantRoleOption.localizedLabel(): String = when (this) {
    ParticipantRoleOption.Chair -> appString(Res.string.chair)
    ParticipantRoleOption.Required -> appString(Res.string.required_participant)
    ParticipantRoleOption.Optional -> appString(Res.string.optional_participant)
    ParticipantRoleOption.NonParticipant -> appString(Res.string.non_participant)
}

fun List<CollectionEntity>.sortedWithDefaultFirst(defaultHref: String?): List<CollectionEntity> {
    if (defaultHref.isNullOrBlank()) return this
    val default = firstOrNull { it.href == defaultHref } ?: return this
    return listOf(default) + filter { it.href != defaultHref }
}

/**
 * The calendar an editor saves to: [chosen] when it is one of the editor's [collections] (or the
 * item's own calendar, [ownHref]), else the default, else the first one. A new event or task can
 * never be saved without a calendar that holds its kind, even when a draft carries over another
 * editor's choice. Null only when there is no such calendar at all.
 */
fun List<CollectionEntity>.editorCollectionHref(chosen: String?, ownHref: String?, defaultHref: String?): String? =
    chosen?.takeIf { href -> href == ownHref || any { it.href == href } }
        ?: ownHref
        ?: defaultHref?.takeIf { href -> any { it.href == href } }
        ?: firstOrNull()?.href

fun CollectionEntity.canDeleteFromServerForUi(): Boolean =
    sourceType == SourceType.CalDav &&
        !readOnly &&
        capabilitiesJson.toJsonObjectOrNull()?.optBooleanOrNull("canDeleteResources") != false

data class EventEditorCapabilities(
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

// Used for every card on screen, so they are compiled once.
private val PercentEscape = Regex("%[0-9A-Fa-f]{2}")
private val LineBreakTag = Regex("(?i)<br\\s*/?>")
private val ParagraphEndTag = Regex("(?i)</p\\s*>")
private val AnyHtmlTag = Regex("(?s)<[^>]+>")

fun String.cleanCalendarDisplayText(): String {
    var normalized = trim().trimHtmlDataPrefixForDisplay()
    if (normalized.contains(PercentEscape)) {
        normalized = runCatching { percentDecode(normalized) }.getOrDefault(normalized).trimHtmlDataPrefixForDisplay()
    }
    if ('<' in normalized && '>' in normalized) {
        normalized = normalized
            .replace(LineBreakTag, "\n")
            .replace(ParagraphEndTag, "\n")
            .replace(AnyHtmlTag, "")
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

fun String.cardLocationText(mapVerified: Boolean?): String {
    val cleaned = cleanCalendarDisplayText()
    if (cleaned.isBlank()) return ""
    return if (mapVerified == true) cleaned.substringBefore(',').trim() else cleaned
}

fun AppLanguageMode.resolveLocale(context: PlatformContext): Locale =
    localeTag?.let { com.kgs.calendar.time.appLocaleOf(it) } ?: context.systemLocale()

private fun String.trimHtmlDataPrefixForDisplay(): String {
    val normalized = trim()
    return when {
        normalized.startsWith("data:text/html", ignoreCase = true) && "," in normalized -> normalized.substringAfter(',')
        normalized.startsWith("text/html,", ignoreCase = true) -> normalized.substringAfter(',')
        else -> normalized
    }
}

@Composable
fun CalendarParticipant.localizedDeliveryStatusLabel(): String? {
    val code = scheduleStatus
        ?.substringBefore(',')
        ?.substringBefore(';')
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: return null
    return when {
        code == "1.0" -> appString(Res.string.invitation_preparing)
        code == "1.2" || code == "2.0" -> null
        code.startsWith("1.") -> appString(Res.string.invitation_sent)
        code.startsWith("3.") || code.startsWith("5.") -> appString(Res.string.delivery_failed)
        else -> null
    }
}

@Composable
fun String.toLocalizedRecurrenceLabel(): String {
    val rule = trim()
    if (rule.isBlank()) return appString(Res.string.one_time)
    val freqLabel = when (rule.recurrenceFrequency()) {
        "DAILY" -> appString(Res.string.daily)
        "WEEKLY" -> appString(Res.string.weekly)
        "MONTHLY" -> appString(Res.string.monthly)
        "YEARLY" -> appString(Res.string.yearly)
        else -> appString(Res.string.custom_recurrence)
    }
    val dayPart = rule.recurrencePart("BYDAY") ?: rule.recurrencePart("DAY")
    val dayLabel = dayPart?.split(',')?.map { it.toLocalizedWeekdayLabel() }?.joinToString(", ")
    val interval = rule.recurrencePart("INTERVAL")?.toIntOrNull()?.takeIf { it > 1 }?.let { "${appString(Res.string.every)} $it" }
    val count = rule.recurrencePart("COUNT")?.let { appString(Res.string.times_count, it) }
    val until = rule.recurrencePart("UNTIL")?.toIsoUntilDate()
    return listOfNotNull(
        freqLabel,
        interval,
        dayLabel?.let { appString(Res.string.on_weekday, it) },
        count,
        until?.let { appString(Res.string.until, it) },
    ).joinToString(" ")
}

@Composable
fun String?.localizedReminderSummary(): String? {
    val minutes = this?.split(',')?.mapNotNull { it.trim().toIntOrNull() }?.normalizedReminderOffsets().orEmpty()
    if (minutes.isEmpty()) return null
    return minutes.map { reminderMinuteLabel(it) }.joinToString(", ")
}

@Composable
private fun String.toLocalizedWeekdayLabel(): String = when (uppercase()) {
    "MO" -> appString(Res.string.week_monday_short)
    "TU" -> appString(Res.string.week_tuesday_short)
    "WE" -> appString(Res.string.week_wednesday_short)
    "TH" -> appString(Res.string.week_thursday_short)
    "FR" -> appString(Res.string.week_friday_short)
    "SA" -> appString(Res.string.week_saturday_short)
    "SU" -> appString(Res.string.week_sunday_short)
    else -> this
}

fun continuationShape(continuesFromPrevious: Boolean, continuesToNext: Boolean): RoundedCornerShape =
    RoundedCornerShape(
        topStart = if (continuesFromPrevious) 0.dp else 8.dp,
        bottomStart = if (continuesFromPrevious) 0.dp else 8.dp,
        topEnd = if (continuesToNext) 0.dp else 8.dp,
        bottomEnd = if (continuesToNext) 0.dp else 8.dp,
    )

fun Modifier.drawContinuationBridge(
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

fun Int.toHexText(): String =
    "#" + (this and 0x00FFFFFF).toString(16).uppercase().padStart(6, '0')

private fun String.parseHexColorOrNull(): Int? {
    val normalized = trim().removePrefix("#")
    if (normalized.length != 6 || normalized.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) return null
    return (0xFF000000.toInt() or normalized.toInt(16))
}

fun LocalTime.nextDraftStart(): LocalTime {
    val nextHourMinute = ((hour + 1).coerceAtMost(23)) * 60
    return nextHourMinute.toDraftLocalTime()
}

fun LocalTime.defaultDraftEnd(durationMinutes: Int = 60): LocalTime =
    (minuteOfDay() + durationMinutes.coerceIn(15, 24 * 60)).coerceAtMost((DayEndHour + 1) * 60 - 1).toDraftLocalTime()

fun LocalTime.minuteOfDay(): Int = hour * 60 + minute

fun LocalDate.startOfDayMillis(): Long =
    atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

fun eventDateTimeRangeInvalid(
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
    // An event may end when it starts: a zero-length event, such as a deadline from another app.
    return endDate.atTime(endTime).isBefore(startDate.atTime(startTime))
}

fun taskDateTimeRangeInvalid(
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
        LocalTime.MAX_TIME
    }
    return !endDate.atTime(endTime).isAfter(startDate.atTime(startTime))
}

fun Int.toDraftLocalTime(): LocalTime {
    val minute = coerceIn(DayStartHour * 60, (DayEndHour + 1) * 60 - 1)
    return LocalTime.of(minute / 60, minute % 60)
}

fun Int.snapDraftMinute(): Int =
    ((this + DraftSnapMinutes / 2) / DraftSnapMinutes) * DraftSnapMinutes

fun EditorSchedulePreview.withDraggedMinutes(
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

data class AllDayAreaLayout(val height: Dp, val scene: AllDayScene?, val draftRows: Int)

fun List<LocalDate>.allDayAreaLayout(
    events: List<EventEntity>,
    tasks: List<TaskEntity>,
    maxVisibleItems: Int,
    expanded: Boolean,
    draftDate: LocalDate?,
    priorityPageCount: Int = size,
): AllDayAreaLayout {
    val visibleStartPage = minOfOrNull { it.toDayPage() } ?: return AllDayAreaLayout(22.dp, null, 0)
    val visibleEndPage = maxOfOrNull { it.toDayPage() } ?: visibleStartPage
    val priorityEndPage = min(visibleEndPage, visibleStartPage + priorityPageCount.coerceAtLeast(1) - 1)
    val overlayItems = buildAllDayOverlayItems(events, tasks, TaskColorMode.Collection, visibleStartPage, visibleEndPage,
        priorityStartPage = visibleStartPage, priorityEndPage = priorityEndPage)
    val scene = buildAllDayScene(
        overlayItems = overlayItems,
        visibleStartPage = visibleStartPage,
        visibleEndPage = visibleEndPage,
        priorityStartPage = visibleStartPage,
        priorityEndPage = priorityEndPage,
        maxVisibleItems = maxVisibleItems,
    )
    val displayedRows = if (expanded) scene.metrics.expandedRowCount else scene.metrics.collapsedRowCount
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
    val height = if (displayedRowsWithDraft == 0) 22.dp else (displayedRowsWithDraft * 29 + 10).dp
    return AllDayAreaLayout(height, scene, draftRows)
}

fun firstFreeLaneIndex(occupiedLanes: Set<Int>): Int {
    var lane = 0
    while (lane in occupiedLanes) lane++
    return lane
}

fun List<LocalDate>.hasAllDayOverflow(
    events: List<EventEntity>,
    tasks: List<TaskEntity>,
    maxVisibleItems: Int,
    priorityPageCount: Int = size,
): Boolean {
    val visibleStartPage = minOfOrNull { it.toDayPage() } ?: return false
    val visibleEndPage = maxOfOrNull { it.toDayPage() } ?: visibleStartPage
    val priorityEndPage = min(visibleEndPage, visibleStartPage + priorityPageCount.coerceAtLeast(1) - 1)
    val overlayItems = buildAllDayOverlayItems(events, tasks, TaskColorMode.Collection, visibleStartPage, visibleEndPage,
        priorityStartPage = visibleStartPage, priorityEndPage = priorityEndPage)
    return buildAllDayScene(
        overlayItems = overlayItems,
        visibleStartPage = visibleStartPage,
        visibleEndPage = visibleEndPage,
        priorityStartPage = visibleStartPage,
        priorityEndPage = priorityEndPage,
        maxVisibleItems = maxVisibleItems,
    ).metrics.hasCollapsedOverflow
}

fun List<EventEntity>.indexEventsByDay(): Map<LocalDate, List<EventEntity>> {
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

fun EventEntity.smoothRemovalKey(): String =
    "${resourceHref}:${startsAtMillis}"

fun TaskEntity.smoothRemovalKey(): String =
    "${resourceHref}:${startAtMillis ?: dueAtMillis ?: completedAtMillis ?: 0L}"

fun List<TaskEntity>.indexTasksByDay(): Map<LocalDate, List<TaskEntity>> =
    flatMap { task -> task.visibleDates().map { it to task } }
        .groupBy({ it.first }, { it.second })

fun CalendarUiState.defaultFabCreationDate(today: LocalDate = LocalDate.now()): LocalDate =
    when (selectedView) {
        CalendarViewMode.Day -> selectedDate
        CalendarViewMode.ThreeDay -> {
            val start = timelineVisibleAnchor(
                date = selectedDate,
                viewMode = selectedView,
                weekViewEnabled = weekViewEnabled,
                fullWeekSwipeEnabled = fullWeekSwipeEnabled,
                firstDayOfWeek = firstDayOfWeek,
            )
            val endExclusive = start.plusDays(
                timelineDayCount(selectedView, weekViewEnabled, multiDayCount).toLong(),
            )
            if (!today.isBefore(start) && today.isBefore(endExclusive)) today else start
        }
        CalendarViewMode.Month,
        CalendarViewMode.Agenda,
        CalendarViewMode.Tasks,
        -> today
    }

fun monthMarkersFor(
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

    // Events of other months are skipped without converting their times: there can be thousands.
    val monthStartMillis = first.startOfDayMillis()
    val nextMonthStartMillis = last.plusDays(1).startOfDayMillis()
    events.forEach { event ->
        if (event.startsAtMillis >= nextMonthStartMillis || event.endsAtMillis - 1 < monthStartMillis) return@forEach
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
    LocalDate.parse(this).format(dayMonthFormatter(com.kgs.calendar.time.defaultAppLocale(), weekday = "EEE", year = true))
}.getOrDefault(this)

enum class SheetSnap {
    Expanded,
    Half,
    Quarter,
    EditorSmall,
    EditorTiny,
}

enum class SheetAnchorMode {
    ContentFit,
    Editor,
}

data class DraftEventSelection(
    val date: LocalDate,
    val start: LocalTime,
    val end: LocalTime,
    val color: Int = DraftAccent.toArgb(),
    val allDay: Boolean = false,
)

data class MonthDayMarkers(
    val colors: List<Int>,
    val hasMore: Boolean,
)

enum class DraftDragMode {
    Move,
    Start,
    End,
}

@Composable
fun RecurrenceOption.localizedLabel(): String = when (this) {
    RecurrenceOption.Once -> appString(Res.string.one_time)
    RecurrenceOption.Daily -> appString(Res.string.daily)
    RecurrenceOption.Weekly -> appString(Res.string.weekly)
    RecurrenceOption.Monthly -> appString(Res.string.monthly)
    RecurrenceOption.Yearly -> appString(Res.string.yearly)
    RecurrenceOption.Custom -> appString(Res.string.custom_recurrence)
}

@Composable
fun RecurrenceOption.localizedIntervalUnitLabel(): String = when (this) {
    RecurrenceOption.Daily -> appString(Res.string.days)
    RecurrenceOption.Weekly -> appString(Res.string.weeks)
    RecurrenceOption.Monthly -> appString(Res.string.months)
    RecurrenceOption.Yearly -> appString(Res.string.years)
    else -> appString(Res.string.intervals)
}

data class EditorTransferDraft(
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

fun EditorTransferDraft.withDestinationReminderDefaults(destinationDefaultReminderMinutes: Set<Int>): EditorTransferDraft {
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

fun buildAllDayOverlayItems(
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

    // Two days of margin cover the zone differences between dates and instants.
    val earliestMillis = (visibleStartPage - 2).toDayDate().startOfDayMillis()
    val latestMillis = (visibleEndPage + 3).toDayDate().startOfDayMillis()
    val candidates = buildList {
        events.forEach { event ->
            if (event.startsAtMillis >= latestMillis || event.endsAtMillis < earliestMillis) return@forEach
            val topStart = event.allDayTopStartDate() ?: return@forEach
            val topEnd = event.allDayTopEndDate() ?: return@forEach
            val startPage = topStart.toDayPage()
            val endPage = topEnd.toDayPage()
            if (endPage >= visibleStartPage && startPage <= visibleEndPage) {
                add(
                    Candidate(
                        id = "event:${event.resourceHref}:${event.occurrenceStartForEdit()}",
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
                // Bounds suffice here: enumerating dates wastes work and used to truncate
                // long all-day tasks at the day-index helper's 370-day guard.
                val startPage = (task.startAtMillis ?: task.dueAtMillis)?.toDate()?.toDayPage() ?: return@forEach
                val endPage = ((task.dueAtMillis ?: task.startAtMillis)?.toDate()?.toDayPage() ?: startPage)
                    .coerceAtLeast(startPage)
                if (endPage >= visibleStartPage && startPage <= visibleEndPage) {
                    add(
                        Candidate(
                            id = "task:${task.resourceHref}:${task.occurrenceRecurrenceIdMillis()}",
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
        // The full days in the middle of a timed task over several days, like those of events.
        tasks.forEach { task ->
            val middle = task.timedMultiDayMiddleDates() ?: return@forEach
            val startPage = middle.start.toDayPage()
            val endPage = middle.endInclusive.toDayPage()
            if (endPage >= visibleStartPage && startPage <= visibleEndPage) {
                add(
                    Candidate(
                        id = "task-middle:${task.resourceHref}:${task.occurrenceRecurrenceIdMillis()}",
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
            .thenByDescending { it.endPage }
            .thenBy { it.startPage }
            .thenBy { it.title }
            .thenBy { it.id },
    )

    val laneCandidates = mutableListOf<MutableList<Candidate>>()
    val assigned = candidates.map { candidate ->
        val lane = laneCandidates.indexOfFirst { assignedCandidates ->
            assignedCandidates.none { existing ->
                candidate.startPage <= existing.endPage && existing.startPage <= candidate.endPage
            }
        }.let { index ->
            if (index >= 0) index else laneCandidates.size.also { laneCandidates.add(mutableListOf()) }
        }
        laneCandidates[lane] += candidate
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
fun EventEntity.localizedTimeLabel(): String {
    if (allDay) return stringResource(Res.string.all_day)
    return "${startsAtMillis.toTimeText()} - ${endsAtMillis.toTimeText()}"
}

@Composable
fun EventEntity.localizedAgendaSpanLabel(startDate: LocalDate, endDate: LocalDate): String {
    val dateFormatter = dayMonthFormatter(LocalAppLocale.current, year = true)
    val allDayLabel = appString(Res.string.all_day)
    return if (allDay) {
        "${startDate.format(dateFormatter)} - ${endDate.format(dateFormatter)}, $allDayLabel"
    } else {
        "${startDate.format(dateFormatter)} - ${endDate.format(dateFormatter)}"
    }
}

@Composable
fun EventEntity.localizedFullTimeLabel(): String {
    val dateFormatter = dayMonthFormatter(LocalAppLocale.current, weekday = "EEE", year = true)
    val allDayLabel = stringResource(Res.string.all_day)
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
fun TaskEntity.localizedTaskTimeLabel(): String {
    val startDate = startAtMillis?.toDate()
    val dueDate = dueAtMillis?.toDate()
    val date = dueDate ?: startDate ?: return stringResource(Res.string.inbox)
    val locale = LocalAppLocale.current
    val dateText = date.format(dayMonthFormatter(locale, weekday = "EEE"))
    val startTimed = startAtMillis?.takeIf { startHasTime }
    val dueTimed = dueAtMillis?.takeIf { dueHasTime }
    return when {
        startTimed != null && dueTimed != null && startDate == dueDate -> "$dateText, ${startTimed.toTimeText()}-${dueTimed.toTimeText()}"
        startTimed != null && dueTimed != null -> "${startTimed.toDate().format(dayMonthFormatter(locale))} ${startTimed.toTimeText()} - ${dueTimed.toDate().format(dayMonthFormatter(locale))} ${dueTimed.toTimeText()}"
        startTimed != null -> "$dateText, ${stringResource(Res.string.from_time, startTimed.toTimeText())}"
        dueTimed != null -> "$dateText, ${stringResource(Res.string.until_time, dueTimed.toTimeText())}"
        else -> "$dateText, ${stringResource(Res.string.all_day)}"
    }
}
