package com.kgs.calendar.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kgs.calendar.data.settings.AppThemeMode

private val DefaultItemColorPalette = listOf(
    0xFF1E88E5.toInt(),
    0xFF00ACC1.toInt(),
    0xFF43A047.toInt(),
    0xFF7CB342.toInt(),
    0xFFFDD835.toInt(),
    0xFFFFB300.toInt(),
    0xFFFB8C00.toInt(),
    0xFFE53935.toInt(),
    0xFF8E24AA.toInt(),
    0xFF6D4C41.toInt(),
)

data class CalendarUiTokens(
    val warmBrown: Color = Color(0xFF2563A8),
    val warmPeach: Color = Color(0xFFDCEBFF),
    val warmGrid: Color = Color(0xFFFAFCFF),
    val warmLine: Color = Color(0xFFD2E0EF),
    val taskHierarchyLine: Color = Color(0xFF707780),
    val warmInk: Color = Color(0xFF17202A),
    val darkPalette: Boolean = false,
    val priorityAnimationsEnabled: Boolean = true,
    val syncPendingOrange: Color = Color(0xFFFF9800),
    val draftAccent: Color = Color(0xFFF6D35A),
    val itemColorPalette: List<Int> = DefaultItemColorPalette,
    val motionStandard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f),
    val motionStandardAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 1f, 1f),
    val motionEmphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f),
    val morphEasing: Easing = CubicBezierEasing(0.45f, 0f, 0.25f, 1f),
    val motionShortMillis: Int = 150,
    val motionMediumMillis: Int = 300,
    val motionLongMillis: Int = 450,
    val pendingBadgeDelayMillis: Long = 30_000L,
    val defaultHourRowHeightDp: Float = 46f,
    val absoluteMinHourRowHeightDp: Float = 18f,
    val maxHourRowHeightDp: Float = 92f,
    val draftSnapMinutes: Int = 15,
    val draftMinDurationMinutes: Int = 15,
    val timeSidebarWidth: Dp = 42.dp,
    val dayHeaderHeight: Dp = 56.dp,
    val dayColumnSpacing: Dp = 4.dp,
    val hourCellGap: Dp = 2.dp,
    val editorHorizontalPadding: Dp = 22.dp,
    val editorSectionHorizontalPadding: Dp = 14.dp,
    val universalControlHeight: Dp = 56.dp,
    val settingsControlHeight: Dp = 70.dp,
    val settingsControlShape: RoundedCornerShape = RoundedCornerShape(25.dp),
    val editorTinyVisibleHeight: Dp = 74.dp,
    val editorSmallVisibleHeight: Dp = 310.dp,
) {
    companion object {
        val Default = CalendarUiTokens()

        fun forTheme(
            colorScheme: ColorScheme,
            themeMode: AppThemeMode,
            priorityAnimationsEnabled: Boolean,
        ): CalendarUiTokens {
            val background = colorScheme.background
            val darkPalette = (0.299f * background.red + 0.587f * background.green + 0.114f * background.blue) < 0.55f
            return Default.copy(
                warmBrown = colorScheme.primary,
                warmPeach = when {
                    darkPalette -> colorScheme.primary.copy(alpha = 0.28f)
                    themeMode == AppThemeMode.KgsWarm -> Color(0xFFFFD8C2)
                    themeMode == AppThemeMode.SystemDynamic -> colorScheme.primary.copy(alpha = 0.26f)
                    else -> colorScheme.primary.copy(alpha = 0.24f)
                },
                warmGrid = if (darkPalette) colorScheme.surfaceVariant else Color.White,
                warmLine = colorScheme.outline.copy(alpha = if (darkPalette) 0.62f else 0.72f),
                taskHierarchyLine = if (darkPalette) {
                    colorScheme.outline.copy(alpha = 0.78f)
                } else {
                    Color(0xFF707780)
                },
                warmInk = colorScheme.onBackground,
                darkPalette = darkPalette,
                priorityAnimationsEnabled = priorityAnimationsEnabled,
            )
        }
    }
}

val LocalCalendarUiTokens = compositionLocalOf { CalendarUiTokens.Default }
