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
    leading: (@Composable () -> Unit)? = null,
    warning: Boolean = false,
    trailingText: String? = null,
    onClick: () -> Unit,
) {
    SettingsRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
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
    SettingsRow(title = title, subtitle = value, subtitleColor = WarmBrown, icon = icon, onClick = onClick)
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
        Slider(
            value = shown.toFloat(),
            onValueChange = {
                val snapped = range.first + (((it - range.first) / safeStep).roundToInt() * safeStep)
                dragged = snapped.coerceIn(range.first, range.last)
            },
            onValueChangeFinished = {
                dragged?.let { if (it != value) onValueChanged(it) }
            },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (span / safeStep - 1).coerceAtLeast(0),
            colors = SliderDefaults.colors(
                thumbColor = WarmBrown,
                activeTrackColor = WarmBrown,
                inactiveTrackColor = WarmBrown.copy(alpha = 0.16f),
                activeTickColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                inactiveTickColor = WarmBrown.copy(alpha = 0.55f),
            ),
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

/** A choice between a few [options], shown as pills that wrap onto further lines. */
@Composable
internal fun <T> SettingsChoiceRow(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelected: (T) -> Unit,
    subtitle: String? = null,
    optionIcon: ((T) -> ImageVector?)? = null,
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
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                SettingsChoicePill(
                    label = label(option),
                    selected = option == selected,
                    icon = optionIcon?.invoke(option),
                    onClick = { onSelected(option) },
                )
            }
        }
    }
}

/** A borderless pill: filled with the accent when selected, a quiet tint otherwise. */
@Composable
internal fun SettingsChoicePill(
    label: String,
    selected: Boolean,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    val container by animateColorAsState(
        targetValue = if (selected) WarmBrown else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
        animationSpec = tween(MotionShort, easing = MotionStandard),
        label = "settingsChoicePill",
    )
    val content = if (selected) MaterialTheme.colorScheme.onPrimary else WarmInk
    Row(
        modifier = Modifier
            .heightIn(min = 38.dp)
            .clip(RoundedCornerShape(19.dp))
            .background(container)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val leadingIcon = if (selected) Icons.Default.Check else icon
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, tint = content, modifier = Modifier.size(17.dp))
        }
        Text(label, color = content, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
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

/** One kind of calendar source on the "Add calendar" page, with example services below it. */
@Composable
internal fun SourceOptionCard(
    title: String,
    value: String,
    leadingIcon: ImageVector,
    examples: List<SourceExampleBrand>,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .settingsRowBackground(settingsControlColor())
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsIconBadge(leadingIcon)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = WarmInk, fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold)
                Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 18.sp)
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(22.dp),
            )
        }
        FadedHorizontalScrollRow(
            contentPadding = PaddingValues(start = 70.dp, end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            examples.forEach { brand ->
                SourceExamplePill(brand)
            }
        }
    }
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
internal fun SettingsRadioRow(selected: Boolean, title: String, leadingIcon: ImageVector? = null, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(role = Role.RadioButton, onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) WarmPeach else Color.Transparent,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            leadingIcon?.let {
                Icon(it, contentDescription = null, tint = if (selected) WarmBrown else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
            Text(title, color = WarmInk, fontSize = 15.sp, lineHeight = 19.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f))
            if (selected) {
                Icon(Icons.Default.Check, contentDescription = null, tint = WarmBrown, modifier = Modifier.size(20.dp))
            }
        }
    }
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
