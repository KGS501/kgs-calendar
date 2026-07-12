package com.kgs.calendar.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.kgs.calendar.ui.theme.CalendarUiTokens
import com.kgs.calendar.ui.theme.LocalCalendarUiTokens
import java.util.Locale

internal val DefaultUiTokens = CalendarUiTokens.Default
internal val WarmBrown: Color @Composable get() = LocalCalendarUiTokens.current.warmBrown
internal val WarmPeach: Color @Composable get() = LocalCalendarUiTokens.current.warmPeach
internal val WarmGrid: Color @Composable get() = LocalCalendarUiTokens.current.warmGrid
internal val WarmLine: Color @Composable get() = LocalCalendarUiTokens.current.warmLine
internal val TaskHierarchyLine: Color @Composable get() = LocalCalendarUiTokens.current.taskHierarchyLine
internal val WarmInk: Color @Composable get() = LocalCalendarUiTokens.current.warmInk
internal val CurrentDarkPalette: Boolean @Composable get() = LocalCalendarUiTokens.current.darkPalette
internal val PriorityAnimationsEnabled: Boolean @Composable get() = LocalCalendarUiTokens.current.priorityAnimationsEnabled
internal val SyncPendingOrange = DefaultUiTokens.syncPendingOrange
internal val DraftAccent = DefaultUiTokens.draftAccent
internal val ItemColorPalette = DefaultUiTokens.itemColorPalette
internal val MotionStandard = DefaultUiTokens.motionStandard
internal val MotionStandardAccelerate = DefaultUiTokens.motionStandardAccelerate
internal val MotionEmphasized = DefaultUiTokens.motionEmphasized
internal val MorphEasing = DefaultUiTokens.morphEasing
internal val MotionShort = DefaultUiTokens.motionShortMillis
internal val MotionMedium = DefaultUiTokens.motionMediumMillis
internal val MotionLong = DefaultUiTokens.motionLongMillis
internal val PENDING_BADGE_DELAY_MILLIS = DefaultUiTokens.pendingBadgeDelayMillis
internal val DefaultHourRowHeightDp = DefaultUiTokens.defaultHourRowHeightDp
internal val AbsoluteMinHourRowHeightDp = DefaultUiTokens.absoluteMinHourRowHeightDp
internal val MaxHourRowHeightDp = DefaultUiTokens.maxHourRowHeightDp
internal val DraftSnapMinutes = DefaultUiTokens.draftSnapMinutes
internal val DraftMinDurationMinutes = DefaultUiTokens.draftMinDurationMinutes
internal val TimeSidebarWidth = DefaultUiTokens.timeSidebarWidth
internal val DayHeaderHeight = DefaultUiTokens.dayHeaderHeight
internal val DayColumnSpacing = DefaultUiTokens.dayColumnSpacing
internal val HourCellGap = DefaultUiTokens.hourCellGap
internal val EditorHorizontalPadding = DefaultUiTokens.editorHorizontalPadding
internal val EditorSectionHorizontalPadding = DefaultUiTokens.editorSectionHorizontalPadding
internal val UniversalControlHeight = DefaultUiTokens.universalControlHeight
internal val SettingsControlHeight = DefaultUiTokens.settingsControlHeight
internal val SettingsControlShape = DefaultUiTokens.settingsControlShape
internal val EditorTinyVisibleHeight = DefaultUiTokens.editorTinyVisibleHeight
internal val EditorSmallVisibleHeight = DefaultUiTokens.editorSmallVisibleHeight
internal const val UiReadOnlyCollectionPrefix = "readonly-"
internal const val UiLocalAccountId = "local"
internal const val UiLocalCollectionPrefix = "local://"
internal const val UiAndroidAccountId = "android-provider"
internal const val UiAndroidCollectionPrefix = "android://calendar/"
internal val LocalAppLocale = compositionLocalOf { Locale.getDefault() }

@Composable
internal fun appString(@StringRes id: Int, vararg args: Any): String {
    val context = LocalContext.current
    val locale = LocalAppLocale.current
    return remember(id, args.toList(), context, locale) {
        val localized = context.withAppLocale(locale)
        if (args.isEmpty()) localized.getString(id) else localized.getString(id, *args)
    }
}

internal val SecurePasswordKeyboardOptions = KeyboardOptions(
    capitalization = KeyboardCapitalization.None,
    keyboardType = KeyboardType.Password,
    imeAction = ImeAction.Done,
    autoCorrectEnabled = false,
)
internal val UrlKeyboardOptions = KeyboardOptions(
    capitalization = KeyboardCapitalization.None,
    keyboardType = KeyboardType.Uri,
    imeAction = ImeAction.Next,
    autoCorrectEnabled = false,
)
internal val UsernameKeyboardOptions = KeyboardOptions(
    capitalization = KeyboardCapitalization.None,
    keyboardType = KeyboardType.Email,
    imeAction = ImeAction.Next,
    autoCorrectEnabled = false,
)
