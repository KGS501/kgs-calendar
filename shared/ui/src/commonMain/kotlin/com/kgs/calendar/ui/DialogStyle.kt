package com.kgs.calendar.ui

import com.kgs.calendar.ui.theme.LocalAppFontFamily

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties

/*
 * Popups share one look with the settings and the editor: a plain surface with large rounding,
 * a bold title, filled choice cards instead of outlined boxes, and a filled pill for the main
 * action next to a quiet text button.
 */

val DialogShape = RoundedCornerShape(28.dp)

/**
 * Every popup uses nearly the whole width of a phone instead of the platform's narrow default,
 * and stays at a readable width on tablets. Use with [WideDialogProperties].
 */
fun Modifier.wideDialogWidth(): Modifier = widthIn(max = WideDialogMaxWidth).fillMaxWidth().padding(horizontal = 16.dp)

val WideDialogProperties = DialogProperties(usePlatformDefaultWidth = false)

private val WideDialogMaxWidth = 520.dp

/** An [AlertDialog] in the app's popup style. */
@Composable
fun KgsAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    containerColor: Color = popupSurfaceColor(),
    titleContentColor: Color = WarmInk,
    textContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier.wideDialogWidth().dialogEntrance(),
        properties = WideDialogProperties,
        dismissButton = dismissButton,
        title = title?.let { content ->
            { ProvideTextStyle(DialogTitleStyle.copy(fontFamily = LocalAppFontFamily.current)) { content() } }
        },
        text = text?.let { content -> { ProvidePopupSurface(content) } },
        shape = DialogShape,
        containerColor = containerColor,
        tonalElevation = 0.dp,
        titleContentColor = titleContentColor,
        textContentColor = textContentColor,
    )
}

/**
 * How a popup comes in: it fades in while rising a little and growing from just below its size,
 * instead of appearing at once. Put it on the dialog's outermost content.
 */
@Composable
fun Modifier.dialogEntrance(): Modifier {
    val progress = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        progress.animateTo(1f, androidx.compose.animation.core.tween(MotionMedium, easing = MotionEmphasized))
    }
    return graphicsLayer {
        val p = progress.value
        alpha = p
        val scale = 0.94f + 0.06f * p
        scaleX = scale
        scaleY = scale
        translationY = (1f - p) * 14.dp.toPx()
    }
}

private val DialogTitleStyle = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold)

/** The main action of a popup: a filled pill, red for destructive actions. */
@Composable
fun DialogConfirmButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (destructive) MaterialTheme.colorScheme.error else WarmBrown,
            contentColor = if (destructive) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

/** The secondary action of a popup, such as "Cancel". */
@Composable
fun DialogDismissButton(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(text, color = WarmBrown, fontWeight = FontWeight.Medium)
    }
}

/** Choice rows of a popup as one filled card, separated by thin gaps instead of outlines. */
@Composable
fun DialogChoiceGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        content = content,
    )
}

/** One choice in a [DialogChoiceGroup]: tinted when selected, with a check at the end. */
@Composable
fun DialogChoiceRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    leading: (@Composable () -> Unit)? = null,
    role: Role = Role.RadioButton,
) {
    val container by animateColorAsState(
        targetValue = if (selected) WarmPeach else editorSectionColor(),
        animationSpec = tween(MotionShort, easing = MotionStandard),
        label = "dialogChoiceRow",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .background(container)
            .clickable(role = role, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                label,
                color = WarmInk,
                fontSize = 16.sp,
                lineHeight = 21.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
            if (subtitle != null) {
                Text(subtitle, color = subtitleColor, fontSize = 13.sp, lineHeight = 17.sp)
            }
        }
        if (selected) {
            Icon(Icons.Default.Check, contentDescription = null, tint = WarmBrown, modifier = Modifier.size(20.dp))
        }
    }
}

/**
 * A segmented control without outlines: the options sit on a tinted track and the chosen one is
 * a raised pill. Used for short exclusive choices inside popups.
 */
@Composable
fun <T> DialogSegmentedControl(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(editorSectionColor())
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (isSelected) selectedSegmentColor() else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelected(option) }
                    .padding(horizontal = 8.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(option),
                    color = if (isSelected) WarmInk else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}

/** A filled text field without an outline, for numbers and short input inside popups. */
@Composable
fun DialogTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    numeric: Boolean = false,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
        shape = RoundedCornerShape(16.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = editorSectionColor(),
            unfocusedContainerColor = editorSectionColor(),
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            focusedLabelColor = WarmBrown,
            cursorColor = WarmBrown,
        ),
        modifier = modifier,
    )
}

/** Colors that turn an outlined text field into a filled one without an outline, for popups. */
@Composable
fun dialogFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Color.Transparent,
    unfocusedBorderColor = Color.Transparent,
    disabledBorderColor = Color.Transparent,
    errorBorderColor = Color.Transparent,
    focusedContainerColor = editorSectionColor(),
    unfocusedContainerColor = editorSectionColor(),
    cursorColor = WarmBrown,
)

/** A small caption above a popup field, instead of a label inside its outline. */
@Composable
fun DialogFieldCaption(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 17.sp, modifier = Modifier.padding(start = 4.dp))
}

/** The raised pill of a segmented control: lighter than its track in light and dark mode. */
@Composable
fun selectedSegmentColor(): Color =
    if (MaterialTheme.colorScheme.background.isDark()) {
        // Its track is lighter on a popup (see popupFieldColor), so the pill is too.
        MaterialTheme.colorScheme.surface.blendWith(Color.White, if (LocalOnPopupSurface.current) 0.27f else 0.18f)
    } else {
        Color.White.takeIf { LocalOnPopupSurface.current } ?: MaterialTheme.colorScheme.surface
    }

/** An on/off choice inside a popup, as a filled row with a switch. */
@Composable
fun DialogSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .background(editorSectionColor())
            .clickable(role = Role.Switch) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = WarmInk, fontSize = 16.sp, lineHeight = 21.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}
