@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.kgs.calendar.ui

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kgs.calendar.R
import kotlin.math.roundToInt

@Composable
internal fun SettingsSection(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(icon, contentDescription = null, tint = WarmBrown, modifier = Modifier.size(20.dp))
            Text(title, color = WarmInk, fontSize = 16.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold)
        }
        content()
    }
}

@Composable
internal fun settingsControlColor(): Color =
    if (MaterialTheme.colorScheme.background.isDark()) MaterialTheme.colorScheme.surface else Color.White

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

@Composable
private fun SettingsWarningCard(problems: List<String>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = SettingsControlShape,
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = if (MaterialTheme.colorScheme.background.isDark()) 0.74f else 0.92f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Default.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(22.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(R.string.problems),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontSize = 15.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                problems.forEach { problem ->
                    Text(
                        "- $problem",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                    )
                }
            }
        }
    }
}

@Composable
internal fun SettingsHelpText(text: String) {
    var open by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        IconButton(onClick = { open = true }, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.HelpOutline, contentDescription = stringResource(R.string.explanation), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
        Text(
            text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(top = 5.dp),
        )
    }
    if (open) {
        AlertDialog(
            modifier = Modifier.padding(horizontal = 20.dp),
            onDismissRequest = { open = false },
            shape = RoundedCornerShape(24.dp),
            title = { Text(stringResource(R.string.explanation), color = WarmInk, fontWeight = FontWeight.SemiBold) },
            text = { SelectionContainer { Text(text, color = WarmInk, lineHeight = 20.sp) } },
            confirmButton = {
                TextButton(onClick = { open = false }) { Text(stringResource(R.string.close)) }
            },
        )
    }
}

@Composable
private fun InlineHelpButton(text: String) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }, modifier = Modifier.size(24.dp)) {
        Icon(Icons.Default.HelpOutline, contentDescription = stringResource(R.string.explanation), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
    }
    if (open) {
        AlertDialog(
            modifier = Modifier.padding(horizontal = 20.dp),
            onDismissRequest = { open = false },
            shape = RoundedCornerShape(24.dp),
            title = { Text(stringResource(R.string.explanation), color = WarmInk, fontWeight = FontWeight.SemiBold) },
            text = { SelectionContainer { Text(text, color = WarmInk, lineHeight = 20.sp) } },
            confirmButton = {
                TextButton(onClick = { open = false }) { Text(stringResource(R.string.close)) }
            },
        )
    }
}

@Composable
internal fun SettingsMenuRow(
    title: String,
    value: String,
    leadingIcon: ImageVector? = null,
    helpText: String? = null,
    warningBadge: Boolean = false,
    modifier: Modifier = Modifier,
    /** Shown instead of [leadingIcon], e.g. a source picture. */
    leading: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(SettingsControlHeight)
            .clip(SettingsControlShape)
            .clickable(onClick = onClick),
        shape = SettingsControlShape,
        color = settingsControlColor(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (leading != null) {
                leading()
            } else {
                leadingIcon?.let {
                    Icon(
                        imageVector = it,
                        contentDescription = null,
                        tint = WarmBrown,
                        modifier = Modifier.size(21.dp),
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = WarmInk, fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            helpText?.let { InlineHelpButton(it) }
            if (warningBadge) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(SyncPendingOrange.copy(alpha = if (MaterialTheme.colorScheme.background.isDark()) 0.34f else 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.WarningAmber,
                        contentDescription = stringResource(R.string.android_hidden_calendars_title),
                        tint = SyncPendingOrange,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

internal data class SourceExampleBrand(
    val name: String,
    val color: Color,
    val iconRes: Int? = null,
    val imageVector: ImageVector? = null,
)

@Composable
internal fun SourceOptionCard(
    title: String,
    value: String,
    leadingIcon: ImageVector,
    examples: List<SourceExampleBrand>,
    helpText: String? = null,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SettingsControlShape)
            .clickable(onClick = onClick),
        shape = SettingsControlShape,
        color = settingsControlColor(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = WarmBrown,
                    modifier = Modifier.size(21.dp),
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(title, color = WarmInk, fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 15.sp)
                }
                helpText?.let { InlineHelpButton(it) }
            }
            FadedHorizontalScrollRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                examples.forEach { brand ->
                    SourceExamplePill(brand)
                }
            }
        }
    }
}

@Composable
private fun SourceExamplePill(brand: SourceExampleBrand) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = brand.color.copy(alpha = if (MaterialTheme.colorScheme.background.isDark()) 0.24f else 0.13f),
        border = BorderStroke(1.dp, brand.color.copy(alpha = if (MaterialTheme.colorScheme.background.isDark()) 0.48f else 0.34f)),
    ) {
        Row(
            modifier = Modifier.padding(start = 6.dp, top = 5.dp, end = 10.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(brand.color),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    brand.iconRes != null -> Icon(
                        painter = painterResource(brand.iconRes),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(15.dp),
                    )
                    brand.imageVector != null -> Icon(
                        imageVector = brand.imageVector,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(15.dp),
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
}

@Composable
internal fun SettingsButtonRow(label: String, value: String, onClick: () -> Unit) {
    SettingsMenuRow(title = label, value = value, onClick = onClick)
}

@Composable
internal fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
    warningUnchecked: Boolean = false,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(SettingsControlHeight)
            .clip(SettingsControlShape)
            .clickable { onCheckedChange(!checked) },
        shape = SettingsControlShape,
        color = settingsControlColor(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = WarmInk, fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
                subtitle?.let {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        InlineHelpButton(it)
                        Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
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
}

@Composable
internal fun SettingsSliderRow(
    title: String,
    subtitle: String? = null,
    value: Int,
    valueLabel: String = value.toString(),
    range: IntRange,
    step: Int = 1,
    onValueChanged: (Int) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = SettingsControlShape,
        color = settingsControlColor(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    subtitle?.let { InlineHelpButton(it) }
                    Text(title, color = WarmInk, fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
                }
                Text(valueLabel, color = WarmBrown, fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
            }
            val safeStep = step.coerceAtLeast(1)
            val span = (range.last - range.first).coerceAtLeast(safeStep)
            Slider(
                value = value.toFloat(),
                onValueChange = {
                    val snapped = range.first + (((it - range.first) / safeStep).roundToInt() * safeStep)
                    onValueChanged(snapped.coerceIn(range.first, range.last))
                },
                valueRange = range.first.toFloat()..range.last.toFloat(),
                steps = (span / safeStep - 1).coerceAtLeast(0),
            )
        }
    }
}

@Composable
internal fun SettingsTwoOptionRow(
    title: String,
    subtitle: String,
    leftLabel: String,
    rightLabel: String,
    leftSelected: Boolean,
    onLeftSelected: () -> Unit,
    onRightSelected: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(SettingsControlHeight),
        shape = SettingsControlShape,
        color = settingsControlColor(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = WarmInk, fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    InlineHelpButton(subtitle)
                    Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(
                modifier = Modifier
                    .height(38.dp)
                    .clip(RoundedCornerShape(19.dp))
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.42f))
                    .padding(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SettingsSegmentPill(leftLabel, selected = leftSelected, onClick = onLeftSelected)
                SettingsSegmentPill(rightLabel, selected = !leftSelected, onClick = onRightSelected)
            }
        }
    }
}

@Composable
private fun SettingsSegmentPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) WarmBrown else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else WarmInk,
            fontSize = 12.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
internal fun SettingsInfoRow(label: String, value: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(SettingsControlHeight),
        shape = SettingsControlShape,
        color = settingsControlColor(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, color = WarmInk, fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 15.sp)
            }
        }
    }
}

@Composable
internal fun SettingsRadioRow(selected: Boolean, title: String, leadingIcon: ImageVector? = null, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(SettingsControlHeight)
            .clip(SettingsControlShape)
            .clickable(onClick = onClick),
        shape = SettingsControlShape,
        color = if (selected) WarmPeach else settingsControlColor(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            leadingIcon?.let {
                Icon(it, contentDescription = null, tint = if (selected) WarmBrown else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
            Text(title, color = WarmInk, fontSize = 15.sp, lineHeight = 18.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, modifier = Modifier.weight(1f))
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(WarmBrown),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
                }
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
