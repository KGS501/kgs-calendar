package com.kgs.calendar.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.kgs.calendar.ui.theme.CalendarUiTokens
import com.kgs.calendar.ui.theme.LocalCalendarUiTokens

val DefaultUiTokens = CalendarUiTokens.Default
val WarmBrown: Color @Composable get() = LocalCalendarUiTokens.current.warmBrown
val WarmPeach: Color @Composable get() = LocalCalendarUiTokens.current.warmPeach
val WarmGrid: Color @Composable get() = LocalCalendarUiTokens.current.warmGrid
val WarmLine: Color @Composable get() = LocalCalendarUiTokens.current.warmLine
val TaskHierarchyLine: Color @Composable get() = LocalCalendarUiTokens.current.taskHierarchyLine
val WarmInk: Color @Composable get() = LocalCalendarUiTokens.current.warmInk
val CurrentDarkPalette: Boolean @Composable get() = LocalCalendarUiTokens.current.darkPalette
val PriorityAnimationsEnabled: Boolean @Composable get() = LocalCalendarUiTokens.current.priorityAnimationsEnabled
val SyncPendingOrange = DefaultUiTokens.syncPendingOrange
val DraftAccent = DefaultUiTokens.draftAccent
val ItemColorPalette = DefaultUiTokens.itemColorPalette
val MotionStandard = DefaultUiTokens.motionStandard
val MotionStandardAccelerate = DefaultUiTokens.motionStandardAccelerate
val MotionEmphasized = DefaultUiTokens.motionEmphasized
val MorphEasing = DefaultUiTokens.morphEasing
val MotionShort = DefaultUiTokens.motionShortMillis
val MotionMedium = DefaultUiTokens.motionMediumMillis
val MotionLong = DefaultUiTokens.motionLongMillis
val PENDING_BADGE_DELAY_MILLIS = DefaultUiTokens.pendingBadgeDelayMillis
val DefaultHourRowHeightDp = DefaultUiTokens.defaultHourRowHeightDp
val AbsoluteMinHourRowHeightDp = DefaultUiTokens.absoluteMinHourRowHeightDp
val MaxHourRowHeightDp = DefaultUiTokens.maxHourRowHeightDp
val DraftSnapMinutes = DefaultUiTokens.draftSnapMinutes
val DraftMinDurationMinutes = DefaultUiTokens.draftMinDurationMinutes
val TimeSidebarWidth = DefaultUiTokens.timeSidebarWidth
val DayHeaderHeight = DefaultUiTokens.dayHeaderHeight
val DayColumnSpacing = DefaultUiTokens.dayColumnSpacing
val HourCellGap = DefaultUiTokens.hourCellGap
val EditorHorizontalPadding = DefaultUiTokens.editorHorizontalPadding
val EditorSectionHorizontalPadding = DefaultUiTokens.editorSectionHorizontalPadding
val UniversalControlHeight = DefaultUiTokens.universalControlHeight
val SettingsControlHeight = DefaultUiTokens.settingsControlHeight
val SettingsControlShape = DefaultUiTokens.settingsControlShape
val EditorTinyVisibleHeight = DefaultUiTokens.editorTinyVisibleHeight
val EditorSmallVisibleHeight = DefaultUiTokens.editorSmallVisibleHeight
const val UiLocalAccountId = "local"
const val UiLocalCollectionPrefix = "local://"
