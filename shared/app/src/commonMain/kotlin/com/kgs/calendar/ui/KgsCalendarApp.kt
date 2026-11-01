package com.kgs.calendar.ui

import com.kgs.calendar.ui.platform.ProvidePlatformLocale
import com.kgs.calendar.ui.platform.SystemBarAppearanceEffect
import com.kgs.calendar.ui.platform.systemLocale
import com.kgs.calendar.res.LocalAppLocale
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kgs.calendar.data.local.entity.PendingMutationEntity
import com.kgs.calendar.data.settings.AppColorMode
import com.kgs.calendar.ui.haptics.LocalKgsHaptics
import com.kgs.calendar.ui.haptics.rememberKgsHaptics
import com.kgs.calendar.ui.layout.FoldPosture
import com.kgs.calendar.ui.layout.LocalFoldPosture
import com.kgs.calendar.ui.layout.currentCalendarWindowLayout
import com.kgs.calendar.ui.layout.forBookCalendarPane
import com.kgs.calendar.ui.theme.KgsCalendarTheme
import com.kgs.calendar.ui.theme.LocalCalendarUiTokens
import com.kgs.calendar.ui.time.LocalCalendarTimeSnapshot
import com.kgs.calendar.ui.time.rememberCalendarTimeState
import java.util.Locale

internal val LocalExitingResourceHrefs = compositionLocalOf<Set<String>> { emptySet() }
internal val LocalTaskHierarchyExitProgress = compositionLocalOf { 0f }
internal val LocalSheetHeaderDragModifier = compositionLocalOf<Modifier> { Modifier }
internal val LocalPendingMutations = compositionLocalOf<List<PendingMutationEntity>> { emptyList() }

@Composable
fun KgsCalendarApp(viewModel: CalendarViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val calendarTime = rememberCalendarTimeState()
    val systemDark = isSystemInDarkTheme()
    val useDarkTheme = when (state.colorMode) {
        AppColorMode.Auto -> systemDark
        AppColorMode.Light -> false
        AppColorMode.Dark -> true
    }
    val systemLocale = systemLocale()
    LaunchedEffect(calendarTime.today) {
        viewModel.setCurrentDay(calendarTime.today)
    }
    // In the book posture the calendar only has the left pane, so its size class follows that pane.
    val bookPosture = LocalFoldPosture.current as? FoldPosture.Book
    val density = LocalDensity.current
    val windowLayout = currentCalendarWindowLayout().let { layout ->
        bookPosture?.let { layout.forBookCalendarPane(with(density) { it.hingeLeftPx.toDp().value }) } ?: layout
    }
    // Forgotten when the Activity is recreated, so a new window re-applies the landscape entry view
    // exactly like an orientation change; plain resizes (split screen, freeform) only update the size class.
    var reportedWindowOrientation by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(windowLayout) {
        val orientationChanged = reportedWindowOrientation != windowLayout.isLandscape
        reportedWindowOrientation = windowLayout.isLandscape
        viewModel.setWindowLayout(windowLayout, applyOrientationEntryView = orientationChanged)
    }
    val appLocale = remember(state.languageMode, systemLocale) {
        state.languageMode.localeTag?.let(Locale::forLanguageTag) ?: systemLocale
    }
    DisposableEffect(appLocale) {
        val previousLocale = Locale.getDefault()
        Locale.setDefault(appLocale)
        onDispose { Locale.setDefault(previousLocale) }
    }

    ProvidePlatformLocale(appLocale) {
        CompositionLocalProvider(
            LocalAppLocale provides appLocale,
            LocalCalendarTimeSnapshot provides calendarTime,
            LocalKgsHaptics provides rememberKgsHaptics(state.hapticFeedbackEnabled),
            LocalReminderPresets provides state.reminderPresets,
            LocalProMode provides state.proMode,
        ) {
            KgsCalendarTheme(
                themeMode = state.themeMode,
                darkTheme = useDarkTheme,
                priorityAnimationsEnabled = state.priorityAnimationsEnabled,
            ) {
                SystemBarAppearanceEffect(darkPalette = LocalCalendarUiTokens.current.darkPalette)
                CalendarAppContent(
                    viewModel = viewModel,
                    state = state,
                    today = calendarTime.today,
                )
            }
        }
    }
}
