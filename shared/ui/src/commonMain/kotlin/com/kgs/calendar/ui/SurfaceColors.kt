package com.kgs.calendar.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf

fun Color.isDark(): Boolean {
    val luminance = 0.299f * red + 0.587f * green + 0.114f * blue
    return luminance < 0.55f
}

fun Color.blendWith(target: Color, amount: Float): Color {
    val t = amount.coerceIn(0f, 1f)
    return Color(
        red = red + (target.red - red) * t,
        green = green + (target.green - green) * t,
        blue = blue + (target.blue - blue) * t,
        alpha = alpha + (target.alpha - alpha) * t,
    )
}

@Composable
fun accentContainerColor(): Color =
    if (MaterialTheme.colorScheme.background.isDark()) {
        WarmBrown.blendWith(Color.White, 0.18f)
    } else {
        WarmBrown.blendWith(Color.White, 0.78f)
    }


@Composable
fun editorSectionColor(): Color = when {
    // On a popup, which is itself lifted, the fields and rows must stand out from it.
    LocalOnPopupSurface.current -> popupFieldColor()
    // In dark mode, raised cards are a touch lighter than the sheet, as elevation reads there.
    MaterialTheme.colorScheme.background.isDark() -> MaterialTheme.colorScheme.surface.blendWith(Color.White, 0.07f)
    else -> MaterialTheme.colorScheme.background
}

/**
 * True on a popup's surface ([popupSurfaceColor]), set by [ProvidePopupSurface]: text fields, switch rows
 * and choices there take [popupFieldColor], since the colors they have on a sheet are the popup's own.
 */
val LocalOnPopupSurface = compositionLocalOf { false }

/** Marks [content] as drawn on a popup's surface (see [LocalOnPopupSurface]). */
@Composable
fun ProvidePopupSurface(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalOnPopupSurface provides true, content = content)
}

/** Fields, switch rows and choices on a popup: clearly set off from its surface, in light and dark. */
@Composable
fun popupFieldColor(): Color {
    val colors = MaterialTheme.colorScheme
    return if (colors.background.isDark()) {
        colors.surface.blendWith(Color.White, 0.15f)
    } else {
        colors.background.blendWith(colors.onSurface, 0.045f)
    }
}


/** Menus and popups: white in the light, the palette's lifted surface in the dark (see the theme). */
@Composable
fun popupSurfaceColor(): Color = MaterialTheme.colorScheme.surfaceContainer

@Composable
fun popupControlSurfaceColor(): Color = MaterialTheme.colorScheme.surfaceContainerHighest
