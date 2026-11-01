package com.kgs.calendar.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme

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
fun editorSectionColor(): Color =
    // In dark mode, raised cards are a touch lighter than the sheet, as elevation reads there.
    if (MaterialTheme.colorScheme.background.isDark()) {
        MaterialTheme.colorScheme.surface.blendWith(Color.White, 0.07f)
    } else {
        MaterialTheme.colorScheme.background
    }


@Composable
fun popupSurfaceColor(): Color =
    if (MaterialTheme.colorScheme.background.isDark()) Color(0xFF202124) else Color.White

@Composable
fun popupControlSurfaceColor(): Color =
    if (MaterialTheme.colorScheme.background.isDark()) Color(0xFF303134) else Color(0xFFF1F3F4)
