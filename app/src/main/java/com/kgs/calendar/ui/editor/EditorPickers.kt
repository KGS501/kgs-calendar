@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.kgs.calendar.ui

import android.Manifest
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.graphics.luminance
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.core.content.ContextCompat
import com.kgs.calendar.R
import com.kgs.calendar.domain.task.priorityColorArgb
import com.kgs.calendar.ui.haptics.rememberSliderStepHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
internal fun LocationSelectorField(location: String, onClick: () -> Unit) {
    EditorSection(onClick = onClick) {
        EditorLine(Icons.Default.LocationOn) {
            Text(
                location.ifBlank { stringResource(R.string.add_location) },
                color = if (location.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else WarmInk,
                fontSize = 16.sp,
                lineHeight = 21.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
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

    // The results fill the page: a sheet resting lower would hide the last ones below the screen.
    val expandSheet = LocalSheetExpander.current
    LaunchedEffect(Unit) {
        expandSheet()
        delay(180)
        focusRequester.requestFocus()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationAnchor = context.lastKnownLocationAnchor()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize(),
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
            CompactSearchField(
                value = query,
                onValueChange = {
                    query = it
                    suggestions = emptyList()
                    searchedQuery = ""
                    failed = false
                },
                placeholder = stringResource(R.string.search_location),
                onSearch = { searchLocations() },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
            )
        }
        Column(
            modifier = Modifier.padding(horizontal = EditorHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (locationAnchor == null && ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                FilledTonalButton(
                    onClick = { locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) },
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = editorSectionColor(), contentColor = WarmBrown),
                ) {
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
            Text(
                stringResource(R.string.location_search_privacy_notice),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 6.dp),
            )
            // The last result scrolls clear of the navigation bar and the keyboard.
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars.union(WindowInsets.ime)))
        }
    }
}

/**
 * A search field as tall as the round back button beside it (36 dp), without a leading icon or
 * search button: the keyboard's search key searches. It grows with large font sizes.
 */
@Composable
private fun CompactSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val textStyle = TextStyle(color = WarmInk, fontSize = 16.sp, lineHeight = 20.sp)
    val focusManager = LocalFocusManager.current
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = textStyle,
        cursorBrush = SolidColor(WarmBrown),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        modifier = modifier.heightIn(min = 36.dp),
    ) { innerTextField ->
        // Drawn here rather than with the Material field frame, whose icon slot is 48 dp tall.
        Row(
            modifier = Modifier
                .heightIn(min = 36.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(editorSectionColor())
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp, horizontal = 0.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) {
                    Text(placeholder, style = textStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                innerTextField()
            }
            if (value.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable(onClickLabel = stringResource(R.string.clear_text)) {
                            onValueChange("")
                            focusManager.clearFocus(force = true)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear_text), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            } else {
                Spacer(Modifier.width(12.dp))
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
        color = editorSectionColor(),
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
    val stepHaptics = rememberSliderStepHaptics(priority)
    fun change(next: Int) {
        stepHaptics(next)
        onPriorityChange(next)
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        EditorRowTitle(Icons.Default.Flag, stringResource(R.string.priority)) {
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
                        change(next)
                        onPriorityChangeFinished(next)
                    }
                }
                .pointerInput(widthPx) {
                    var dragPriority = priority.coerceIn(1, 9)
                    detectDragGestures(
                        onDragStart = { offset ->
                            dragPriority = priorityFromX(offset.x)
                            change(dragPriority)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            dragPriority = priorityFromX(change.position.x)
                            change(dragPriority)
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
                    topLeft = Offset(thumbX - 8.dp.toPx(), centerY - 10.dp.toPx()),
                    size = Size(16.dp.toPx(), 20.dp.toPx()),
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
    val stepHaptics = rememberSliderStepHaptics(normalized)
    fun change(next: Int) {
        stepHaptics(next)
        onProgressChange(next)
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        EditorRowTitle(Icons.Default.Percent, stringResource(R.string.progress)) {
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
                        change(next)
                        onProgressChangeFinished(next)
                    }
                }
                .pointerInput(widthPx) {
                    var dragProgress = progress.coerceIn(0, 100)
                    detectDragGestures(
                        onDragStart = { offset ->
                            dragProgress = progressFromX(offset.x)
                            change(dragProgress)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            dragProgress = progressFromX(change.position.x)
                            change(dragProgress)
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

internal fun priorityColor(priority: Int): Color = Color(priorityColorArgb(priority))

@Composable
internal fun ColorOverrideEditor(
    selectedColor: Int?,
    automaticColor: Int,
    onColorSelected: (Int?) -> Unit,
) {
    EditorSection {
        EditorRowTitle(Icons.Default.Palette, stringResource(R.string.color))
        ColorChoiceRow(
            selectedColor = selectedColor,
            automaticColor = automaticColor,
            onColorSelected = onColorSelected,
        )
    }
}

/**
 * The one way to pick a color in the app: automatic, one of the preset colors, or any color from
 * the color wheel behind "Custom". A null [selectedColor] is the automatic color.
 */
@Composable
internal fun ColorChoiceRow(
    selectedColor: Int?,
    automaticColor: Int,
    onColorSelected: (Int?) -> Unit,
    contentPadding: PaddingValues = PaddingValues(horizontal = EditorSectionHorizontalPadding),
    viewportBleed: Dp = EditorSectionHorizontalPadding,
) {
    var colorPickerOpen by remember { mutableStateOf(false) }
    val customColorPresentation = customColorChipPresentation(selectedColor)
    FadedHorizontalScrollRow(
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        viewportBleed = viewportBleed,
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
        ColorEditChip(
            displayedColor = customColorPresentation.displayedColor,
            selected = customColorPresentation.selected,
            onClick = { colorPickerOpen = true },
        )
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

internal data class CustomColorChipPresentation(
    val displayedColor: Int?,
    val selected: Boolean,
)

internal fun customColorChipPresentation(
    selectedColor: Int?,
    palette: List<Int> = ItemColorPalette,
): CustomColorChipPresentation = CustomColorChipPresentation(
    displayedColor = selectedColor?.takeIf { it !in palette },
    selected = selectedColor != null && selectedColor !in palette,
)

@Composable
internal fun ColorChoiceChip(label: String?, color: Color, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(17.dp)
    Surface(
        modifier = Modifier
            .height(34.dp)
            .clip(shape)
            .clickable(onClick = onClick),
        shape = shape,
        color = if (selected) WarmPeach else MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (label == null) 8.dp else 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            ColorSwatch(color = color, selected = selected)
            label?.let { Text(it, color = WarmInk, fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium) }
        }
    }
}

/** A color dot that shows a check while it is the chosen color. */
@Composable
private fun ColorSwatch(color: Color, selected: Boolean) {
    Box(
        Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(Icons.Default.Check, contentDescription = null, tint = if (color.luminance() > 0.62f) Color.Black else Color.White, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
internal fun ColorEditChip(
    displayedColor: Int?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(17.dp)
    Surface(
        modifier = Modifier
            .testTag("customColorChip")
            .height(34.dp)
            .clip(shape)
            .clickable(onClick = onClick),
        shape = shape,
        color = if (selected) WarmPeach else MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            if (displayedColor != null) {
                Box(
                    Modifier
                        .testTag("customColorSwatch")
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(Color(displayedColor)),
                )
            } else {
                Icon(Icons.Default.Palette, contentDescription = null, tint = WarmBrown, modifier = Modifier.size(18.dp))
            }
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
internal fun EditorContainer(
    title: String,
    action: String,
    onAction: () -> Unit,
    onClose: () -> Unit,
    actionEnabled: Boolean = true,
    typeSwitch: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val editorScrollState = rememberScrollState()
    val typeSlide = LocalEditorTypeSlide.current
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            if (typeSwitch != null) {
                Box(typeSlide?.run { Modifier.staysInPlace() } ?: Modifier) { typeSwitch() }
            }
            // Only the fields below the switch slide when it changes the editor type.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(typeSlide?.run { Modifier.slides() } ?: Modifier),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                content = content,
            )
            Spacer(Modifier.height(56.dp))
        }
    }
}
