package com.kgs.calendar.ui

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kgs.calendar.data.local.entity.PendingMutationEntity
import com.kgs.calendar.data.settings.AppColorMode
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
    val baseContext = LocalContext.current
    val configuration = LocalConfiguration.current
    LaunchedEffect(configuration.orientation) {
        viewModel.setDeviceOrientation(
            configuration.orientation == Configuration.ORIENTATION_LANDSCAPE,
        )
    }
    val appLocale = remember(state.languageMode, configuration) {
        state.languageMode.resolveLocale(baseContext)
    }
    val localizedContext = remember(baseContext, appLocale) {
        baseContext.withAppLocale(appLocale)
    }
    val localizedConfiguration = remember(configuration, appLocale) {
        Configuration(configuration).apply { setLocale(appLocale) }
    }
    DisposableEffect(appLocale) {
        val previousLocale = Locale.getDefault()
        Locale.setDefault(appLocale)
        onDispose { Locale.setDefault(previousLocale) }
    }

    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedConfiguration,
        LocalAppLocale provides appLocale,
        LocalCalendarTimeSnapshot provides calendarTime,
    ) {
        KgsCalendarTheme(
            themeMode = state.themeMode,
            darkTheme = useDarkTheme,
            priorityAnimationsEnabled = state.priorityAnimationsEnabled,
        ) {
            val darkPalette = LocalCalendarUiTokens.current.darkPalette
            val view = LocalView.current
            SideEffect {
                view.context.findActivity()?.window?.let { window ->
                    WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkPalette
                    WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkPalette
                    window.navigationBarColor = Color.Transparent.toArgb()
                }
            }
            CalendarAppContent(
                viewModel = viewModel,
                state = state,
                today = calendarTime.today,
            )
        }
    }
}
