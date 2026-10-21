package com.kgs.calendar.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.unit.DpSize
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kgs.calendar.R
import kotlin.math.roundToInt

/*
 * Settings are laid out as titled groups of rows. A group is one rounded card; its rows are
 * separated by thin gaps instead of lines. Titles wrap instead of being cut off, and only
 * settings that need it carry a short description under their title.
 */

internal val SettingsGroupShape = RoundedCornerShape(24.dp)
private val SettingsRowMinHeight = 60.dp
private val SettingsRowGap = 2.dp

/** A titled card of settings rows, with an optional note below it. */
@Composable
internal fun SettingsGroup(
    modifier: Modifier = Modifier,
    title: String? = null,
    footer: String? = null,
    horizontalPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = horizontalPadding)) {
        if (title != null) {
            Text(
                title,
                color = WarmBrown,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth().clip(SettingsGroupShape),
            verticalArrangement = Arrangement.spacedBy(SettingsRowGap),
            content = content,
        )
        if (footer != null) {
            SettingsFootnote(footer, Modifier.padding(top = 8.dp))
        }
    }
}

/** A short explanation below a group or a form. */
@Composable
internal fun SettingsFootnote(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        modifier = modifier.padding(horizontal = 16.dp),
    )
}

/** The row color inside settings groups; a sheet that is itself white provides a tint instead. */
internal val LocalSettingsRowColor = compositionLocalOf<Color?> { null }

@Composable
internal fun settingsControlColor(): Color =
    LocalSettingsRowColor.current
        ?: if (MaterialTheme.colorScheme.background.isDark()) MaterialTheme.colorScheme.surface else Color.White

@Composable
internal fun accentContainerColor(): Color =
    if (MaterialTheme.colorScheme.background.isDark()) {
        WarmBrown.blendWith(Color.White, 0.18f)
    } else {
        WarmBrown.blendWith(Color.White, 0.78f)
    }

@Composable
internal fun accentContainerContentColor(): Color =
    if (MaterialTheme.colorScheme.background.isDark()) {
        MaterialTheme.colorScheme.background
    } else {
        if (WarmBrown.isDark()) Color.White else Color(0xFF1C1A18)
    }

/** The background of a row inside a [SettingsGroup]. */
private fun Modifier.settingsRowBackground(color: Color): Modifier = fillMaxWidth().background(color)

/** An icon in its area's muted color on a soft tint of it, so each settings area is recognizable at a glance. */
@Composable
internal fun SettingsColorBadge(icon: ImageVector, color: Color) {
    val dark = MaterialTheme.colorScheme.background.isDark()
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = if (dark) 0.28f else 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (dark) color.blendWith(Color.White, 0.45f) else color, modifier = Modifier.size(21.dp))
    }
}

/** An icon in a soft accent circle, used in front of navigation rows. */
@Composable
internal fun SettingsIconBadge(icon: ImageVector, tint: Color = WarmBrown) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = if (MaterialTheme.colorScheme.background.isDark()) 0.22f else 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(21.dp))
    }
}

/**
 * The base settings row: an optional leading icon or picture, a title that wraps, an optional
 * [subtitle] and trailing content. Clickable when [onClick] is set.
 */
@Composable
internal fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconColor: Color? = null,
    leading: (@Composable () -> Unit)? = null,
    titleColor: Color = WarmInk,
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    enabled: Boolean = true,
    role: Role? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .settingsRowBackground(settingsControlColor())
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, role = role, onClick = onClick) else Modifier)
            .heightIn(min = SettingsRowMinHeight)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .alpha(if (enabled) 1f else 0.5f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when {
            leading != null -> leading()
            icon != null && iconColor != null -> SettingsColorBadge(icon, iconColor)
            icon != null -> SettingsIconBadge(icon)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = titleColor, fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, color = subtitleColor, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
        trailing?.invoke()
    }
}

/** A row that opens a page; [warning] marks a source that needs attention. */
@Composable
internal fun SettingsNavRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconColor: Color? = null,
    leading: (@Composable () -> Unit)? = null,
    warning: Boolean = false,
    trailingText: String? = null,
    onClick: () -> Unit,
) {
    SettingsRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        iconColor = iconColor,
        leading = leading,
        modifier = modifier,
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (warning) {
                Icon(
                    Icons.Default.WarningAmber,
                    contentDescription = stringResource(R.string.problems),
                    tint = SyncPendingOrange,
                    modifier = Modifier.size(20.dp),
                )
            }
            if (trailingText != null) {
                Text(trailingText, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, lineHeight = 18.sp)
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/** A row that shows its current [value] below the title and opens a picker. */
@Composable
internal fun SettingsValueRow(
    title: String,
    value: String,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    SettingsRow(title = title, icon = icon, role = Role.DropdownList, onClick = onClick) {
        SettingsValuePill(value)
    }
}

/** The current value of a setting as a tinted pill at the row's end, like a closed menu. */
@Composable
private fun SettingsValuePill(value: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(editorSectionColor())
            .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            value,
            color = WarmBrown,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(max = 150.dp),
        )
        Icon(Icons.Default.UnfoldMore, contentDescription = null, tint = WarmBrown, modifier = Modifier.size(18.dp))
    }
}

@Composable
internal fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    warningUnchecked: Boolean = false,
) {
    SettingsRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        enabled = enabled,
        role = Role.Switch,
        onClick = { onCheckedChange(!checked) },
    ) {
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = if (warningUnchecked) {
                SwitchDefaults.colors(
                    uncheckedThumbColor = SyncPendingOrange,
                    uncheckedTrackColor = SyncPendingOrange.copy(alpha = 0.28f),
                    uncheckedBorderColor = SyncPendingOrange,
                )
            } else {
                SwitchDefaults.colors()
            },
        )
    }
}

/**
 * A number on a small fixed scale. The setting is saved when the finger lifts, so dragging
 * does not store every step in between.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsSliderRow(
    title: String,
    value: Int,
    range: IntRange,
    onValueChanged: (Int) -> Unit,
    subtitle: String? = null,
    step: Int = 1,
    valueLabel: (Int) -> String = { it.toString() },
) {
    var dragged by remember(value) { mutableStateOf<Int?>(null) }
    val shown = dragged ?: value
    val safeStep = step.coerceAtLeast(1)
    val span = (range.last - range.first).coerceAtLeast(safeStep)
    Column(
        modifier = Modifier
            .settingsRowBackground(settingsControlColor())
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = WarmInk, fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium)
                if (!subtitle.isNullOrBlank()) {
                    Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 18.sp)
                }
            }
            Text(valueLabel(shown), color = WarmBrown, fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold)
        }
        val sliderInteraction = remember { MutableInteractionSource() }
        val steps = (span / safeStep - 1).coerceAtLeast(0)
        // Long scales get no tick dots; they would only crowd the track.
        val showTicks = steps <= 12
        val sliderColors = SliderDefaults.colors(
            thumbColor = WarmBrown,
            activeTrackColor = WarmBrown,
            inactiveTrackColor = WarmBrown.copy(alpha = 0.16f),
            activeTickColor = if (showTicks) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f) else Color.Transparent,
            inactiveTickColor = if (showTicks) WarmBrown.copy(alpha = 0.55f) else Color.Transparent,
        )
        Slider(
            value = shown.toFloat(),
            interactionSource = sliderInteraction,
            // A short handle that barely rises above the track.
            thumb = {
                SliderDefaults.Thumb(
                    interactionSource = sliderInteraction,
                    colors = sliderColors,
                    thumbSize = DpSize(4.dp, 26.dp),
                )
            },
            onValueChange = {
                val snapped = range.first + (((it - range.first) / safeStep).roundToInt() * safeStep)
                dragged = snapped.coerceIn(range.first, range.last)
            },
            onValueChangeFinished = {
                dragged?.let { if (it != value) onValueChanged(it) }
            },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = steps,
            colors = sliderColors,
        )
    }
}

/** A slider over a short list of fixed [options], such as sync intervals. */
@Composable
internal fun <T> SettingsStepSliderRow(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelected: (T) -> Unit,
    subtitle: String? = null,
) {
    val labels = options.map { label(it) }
    SettingsSliderRow(
        title = title,
        subtitle = subtitle,
        value = options.indexOf(selected).coerceAtLeast(0),
        range = 0..options.lastIndex,
        valueLabel = { labels[it] },
        onValueChanged = { onSelected(options[it]) },
    )
}

/**
 * A choice between two or three short options as one segmented control on a single line.
 * Longer or more options use [SettingsDropdownRow] instead, so nothing wraps.
 */
@Composable
internal fun <T> SettingsSegmentedRow(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelected: (T) -> Unit,
    subtitle: String? = null,
) {
    Column(
        modifier = Modifier
            .settingsRowBackground(settingsControlColor())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = WarmInk, fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
        DialogSegmentedControl(options = options, selected = selected, label = label, onSelected = onSelected)
    }
}

/**
 * A setting with several options: the row shows the current choice, and a tap opens a menu of
 * all options right at the row.
 */
@Composable
internal fun <T> SettingsDropdownRow(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelected: (T) -> Unit,
    subtitle: String? = null,
    icon: ImageVector? = null,
    optionIcon: ((T) -> ImageVector?)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val labels = options.map { label(it) }
    Box {
        SettingsRow(
            title = title,
            subtitle = subtitle,
            icon = icon,
            role = Role.DropdownList,
            onClick = { expanded = true },
        ) {
            SettingsValuePill(labels[options.indexOf(selected).coerceAtLeast(0)])
        }
        // Anchored at the row's end, so the menu opens right at the value pill.
        Box(Modifier.align(Alignment.BottomEnd).padding(end = 16.dp)) {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = popupSurfaceColor(),
            shadowElevation = 8.dp,
            offset = DpOffset(x = 0.dp, y = (-8).dp),
        ) {
            options.forEachIndexed { index, option ->
                val isSelected = option == selected
                DropdownMenuItem(
                    text = {
                        Text(
                            labels[index],
                            color = WarmInk,
                            fontSize = 15.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                    leadingIcon = optionIcon?.invoke(option)?.let { optionIconVector ->
                        { Icon(optionIconVector, contentDescription = null, tint = if (isSelected) WarmBrown else MaterialTheme.colorScheme.onSurfaceVariant) }
                    },
                    trailingIcon = if (isSelected) {
                        { Icon(Icons.Default.Check, contentDescription = null, tint = WarmBrown) }
                    } else {
                        null
                    },
                    onClick = {
                        expanded = false
                        onSelected(option)
                    },
                )
            }
        }
        }
    }
}

/** The main action of a page, such as adding a calendar: a filled, full-width button. */
@Composable
internal fun SettingsPrimaryAction(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.5f),
        shape = RoundedCornerShape(28.dp),
        color = WarmBrown,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Box(Modifier.width(10.dp))
            Text(label, fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

internal data class SourceExampleBrand(
    val name: String,
    val color: Color,
    val iconRes: Int? = null,
    val imageVector: ImageVector? = null,
)

/**
 * One kind of calendar source on the "Add calendar" page: its own card with a colored icon, a
 * short description, what it can do, and example services in their brand colors.
 */
@Composable
internal fun SourceOptionCard(
    title: String,
    leadingIcon: ImageVector,
    color: Color,
    chips: List<Pair<String, Color>>,
    examples: List<SourceExampleBrand>,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SettingsGroupShape)
            .background(settingsControlColor())
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsColorBadge(leadingIcon, color)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, color = WarmInk, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
                // What the source holds and how it syncs, as the same chips the sidebar uses.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    chips.forEach { (label, chipColor) -> CalendarCapabilityChip(label, chipColor) }
                }
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(22.dp),
            )
        }
        if (examples.isNotEmpty()) {
            FadedHorizontalScrollRow(
                contentPadding = PaddingValues(start = 72.dp, end = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                examples.forEach { brand ->
                    SourceExamplePill(brand)
                }
            }
        }
    }
}

/** Chip colors for what a calendar source holds and how it syncs. */
internal object SourceChipColors {
    val Events = Color(0xFF2F5AEA)
    val Tasks = Color(0xFF00A86B)
    val FullSync = Color(0xFF7B5EA7)
    val ReadOnly = Color(0xFFB7791F)
    val DeviceOnly = Color(0xFF64748B)
}

@Composable
private fun SourceExamplePill(brand: SourceExampleBrand) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(brand.color.copy(alpha = if (MaterialTheme.colorScheme.background.isDark()) 0.24f else 0.10f))
            .padding(start = 4.dp, top = 4.dp, end = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(brand.color),
            contentAlignment = Alignment.Center,
        ) {
            when {
                brand.iconRes != null -> Icon(
                    painter = painterResource(brand.iconRes),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(13.dp),
                )
                brand.imageVector != null -> Icon(
                    imageVector = brand.imageVector,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(13.dp),
                )
            }
        }
        Text(
            brand.name,
            color = WarmInk,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

/** A label and a read-only value, such as a count on the problems page. */
@Composable
internal fun SettingsInfoRow(label: String, value: String) {
    SettingsRow(title = label, subtitle = value)
}

@Composable
internal fun CalendarMetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            label,
            color = WarmInk,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(118.dp),
        )
        Text(
            value,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}
