package com.kgs.calendar.widget.theme

import android.content.Context
import android.content.res.Configuration
import com.kgs.calendar.R
import com.kgs.calendar.data.settings.AppColorMode
import com.kgs.calendar.data.settings.AppThemeMode

internal data class WidgetPalette(
    val accent: Int,
    val text: Int,
    val muted: Int,
    val faint: Int,
    val onAccent: Int,
    val rootBackgroundColor: Int,
    val rootBackgroundRes: Int,
    val bottomFadeRes: Int,
    val topFadeRes: Int,
    val sortBackgroundRes: Int,
    val hierarchyLine: Int,
    val itemBackgroundRes: Int,
    val compactItemBackgroundRes: Int,
    val badgeBackgroundRes: Int,
    val daySelectedBackgroundRes: Int,
    val monthTodayTextColor: Int,
) {
    companion object {
        fun from(context: Context, themeMode: AppThemeMode, colorMode: AppColorMode): WidgetPalette {
            val dark = when (colorMode) {
                AppColorMode.Light -> false
                AppColorMode.Dark -> true
                AppColorMode.Auto -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            }
            val accent = if (themeMode == AppThemeMode.SystemDynamic) {
                context.systemAccentColor(dark)
            } else {
                when (themeMode) {
                    AppThemeMode.KgsWarm -> if (dark) 0xFFFFB68B.toInt() else 0xFF9E572B.toInt()
                    AppThemeMode.KgsFresh -> if (dark) 0xFF76DCC4.toInt() else 0xFF0E7C66.toInt()
                    else -> if (dark) 0xFF9BCAFF.toInt() else 0xFF2563A8.toInt()
                }
            }
            return if (dark) {
                WidgetPalette(
                    accent = accent,
                    text = 0xFFE6EEF7.toInt(),
                    muted = 0xFFD6E0EC.toInt(),
                    faint = 0xFF7B8B9B.toInt(),
                    onAccent = if (accent.isDarkColor()) 0xFFFFFFFF.toInt() else 0xFF111827.toInt(),
                    rootBackgroundColor = 0xFF101923.toInt(),
                    rootBackgroundRes = R.drawable.widget_background_dark,
                    bottomFadeRes = R.drawable.widget_bottom_fade_dark,
                    topFadeRes = R.drawable.widget_top_fade_dark,
                    sortBackgroundRes = R.drawable.widget_sort_background_dark,
                    hierarchyLine = 0xC8AEBBCC.toInt(),
                    itemBackgroundRes = R.drawable.widget_item_background_dark,
                    compactItemBackgroundRes = R.drawable.widget_month_cell_compact_dark,
                    badgeBackgroundRes = R.drawable.widget_badge_background_dark,
                    daySelectedBackgroundRes = R.drawable.widget_month_day_selected_dark,
                    monthTodayTextColor = 0xFF9BCAFF.toInt(),
                )
            } else {
                val root = when (themeMode) {
                    AppThemeMode.KgsWarm -> R.drawable.widget_background_warm
                    AppThemeMode.KgsFresh -> R.drawable.widget_background_fresh
                    else -> R.drawable.widget_background
                }
                val rootColor = when (themeMode) {
                    AppThemeMode.KgsWarm -> 0xFFFFEFE8.toInt()
                    AppThemeMode.KgsFresh -> 0xFFECF8F4.toInt()
                    else -> 0xFFEEF6FF.toInt()
                }
                val bottomFade = when (themeMode) {
                    AppThemeMode.KgsWarm -> R.drawable.widget_bottom_fade_warm
                    AppThemeMode.KgsFresh -> R.drawable.widget_bottom_fade_fresh
                    else -> R.drawable.widget_bottom_fade
                }
                val topFade = when (themeMode) {
                    AppThemeMode.KgsWarm -> R.drawable.widget_top_fade_warm
                    AppThemeMode.KgsFresh -> R.drawable.widget_top_fade_fresh
                    else -> R.drawable.widget_top_fade
                }
                val badge = when (themeMode) {
                    AppThemeMode.KgsWarm -> R.drawable.widget_badge_background_warm
                    AppThemeMode.KgsFresh -> R.drawable.widget_badge_background_fresh
                    else -> R.drawable.widget_badge_background
                }
                val sortBackground = when (themeMode) {
                    AppThemeMode.KgsWarm -> R.drawable.widget_sort_background_warm
                    AppThemeMode.KgsFresh -> R.drawable.widget_sort_background_fresh
                    else -> R.drawable.widget_sort_background
                }
                val selected = when (themeMode) {
                    AppThemeMode.KgsWarm -> R.drawable.widget_month_day_selected_warm
                    AppThemeMode.KgsFresh -> R.drawable.widget_month_day_selected_fresh
                    else -> R.drawable.widget_month_day_selected
                }
                WidgetPalette(
                    accent = accent,
                    text = 0xFF17202A.toInt(),
                    muted = 0xFF526173.toInt(),
                    faint = 0xFFA4AFBA.toInt(),
                    onAccent = 0xFFFFFFFF.toInt(),
                    rootBackgroundColor = rootColor,
                    rootBackgroundRes = root,
                    bottomFadeRes = bottomFade,
                    topFadeRes = topFade,
                    sortBackgroundRes = sortBackground,
                    hierarchyLine = 0xFF707780.toInt(),
                    itemBackgroundRes = R.drawable.widget_item_background,
                    compactItemBackgroundRes = R.drawable.widget_month_cell_compact,
                    badgeBackgroundRes = badge,
                    daySelectedBackgroundRes = selected,
                    monthTodayTextColor = 0xFF1E5F9F.toInt(),
                )
            }
        }
    }
}

private fun Context.systemAccentColor(dark: Boolean): Int {
    val names = if (dark) {
        listOf("system_accent1_200", "system_accent1_300")
    } else {
        listOf("system_accent1_600", "system_accent1_500")
    }
    names.forEach { name ->
        val id = resources.getIdentifier(name, "color", "android")
        if (id != 0) {
            val color = runCatching { getColor(id) }.getOrNull()
            if (color != null) return color
        }
    }
    return if (dark) 0xFF9BCAFF.toInt() else 0xFF2563A8.toInt()
}
