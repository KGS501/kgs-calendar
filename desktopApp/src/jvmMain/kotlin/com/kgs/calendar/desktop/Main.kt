package com.kgs.calendar.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import com.kgs.calendar.res.AppStrings
import com.kgs.calendar.res.SharedR as R
import com.kgs.calendar.ui.CalendarViewModel
import com.kgs.calendar.ui.KgsCalendarApp
import com.kgs.calendar.ui.KgsStartupScreen
import com.kgs.calendar.ui.desktop.CalendarChrome
import com.kgs.calendar.ui.desktop.LocalCalendarChrome
import com.kgs.calendar.ui.desktop.LocalPaneLayout
import com.kgs.calendar.ui.platform.BackDispatcher
import com.kgs.calendar.ui.platform.DesktopAppVersion
import com.kgs.calendar.ui.platform.DesktopShortcuts
import com.kgs.calendar.ui.platform.LocalBackDispatcher
import com.kgs.calendar.ui.platform.LocalDesktopShortcuts
import com.kgs.calendar.ui.platform.LocalShortcutBindings
import com.kgs.calendar.ui.platform.LocalWindowChrome
import com.kgs.calendar.ui.theme.LocalAppFontFamily
import com.kgs.calendar.ui.theme.LocalAppPlatformTextStyle
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File
import java.util.Locale
import java.util.concurrent.CompletableFuture
import kotlin.concurrent.thread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** `--data-dir <folder>` keeps a separate profile, e.g. for testing; otherwise `%LOCALAPPDATA%\KGS Calendar`. */
private fun dataDirectory(args: Array<String>): File {
    val explicit = args.indexOf("--data-dir").takeIf { it >= 0 }?.let { args.getOrNull(it + 1) }
    if (explicit != null) return File(explicit).absoluteFile
    val base = System.getenv("LOCALAPPDATA")?.let(::File) ?: File(System.getProperty("user.home"), ".local/share")
    return File(base, "KGS Calendar")
}

/** The calendar's data and logic, set up on their own thread while the window starts. */
private class LoadedApp(val graph: DesktopGraph, val viewModel: CalendarViewModel)

@OptIn(ExperimentalComposeUiApi::class)
fun main(args: Array<String>) {
    DesktopAppVersion = System.getProperty("kgs.version") ?: "V.1.4.6-3"
    val dataDirectory = dataDirectory(args)
    // The calendar's database, settings and view model take a moment to set up, as do the window and
    // its renderer; the two happen side by side, and the window shows its startup screen meanwhile.
    val loading = CompletableFuture<LoadedApp>()
    thread(name = "kgs-startup") {
        runCatching {
            val graph = DesktopGraph(dataDirectory)
            // Demo items only ever go into a separate, empty profile.
            if ("--demo" in args && "--data-dir" in args) {
                runBlocking {
                    if (graph.database.accountDao().getAll().isEmpty()) seedDemoData(graph.repository)
                }
            }
            LoadedApp(graph, graph.createViewModel())
        }.fold(loading::complete, loading::completeExceptionally)
    }
    val placementStore = WindowPlacementStore(File(dataDirectory, "window.properties"))
    val paneLayout = PaneLayoutStore(File(dataDirectory, "layout.properties")).restore()
    val shortcutBindings = ShortcutStore(File(dataDirectory, "shortcuts.properties")).restore()
    val appearanceStore = AppearanceStore(File(dataDirectory, "appearance.properties"))
    val (startupTheme, startupColor) = appearanceStore.restore()
    application {
        // Null until the calendar is set up; a failure there ends the app as it did before.
        val loaded by produceState<LoadedApp?>(null) { value = loading.await() }
        val backDispatcher = remember { BackDispatcher() }
        val shortcuts = remember { DesktopShortcuts(shortcutBindings) }
        val trayState = rememberTrayState()
        val windowState = remember { placementStore.restore() }
        val icon = painterResource("kgs_logo.png")
        LaunchedEffect(loaded) {
            val app = loaded ?: return@LaunchedEffect
            app.graph.reminders.notifier = { title, body -> trayState.sendNotification(Notification(title, body.orEmpty())) }
            app.graph.start(this)
            // The next start's startup screen shows in the look the app has now.
            launch(Dispatchers.IO) {
                app.viewModel.uiState
                    .map { it.themeMode to it.colorMode }
                    .distinctUntilChanged()
                    .collect { (theme, color) -> appearanceStore.save(theme, color) }
            }
            // Once the app has settled, its classes are saved for faster starts (once per version).
            launch(Dispatchers.IO) {
                delay(ClassArchiveDelayMs)
                ClassDataArchive.build()
            }
        }
        var windowVisible by remember { mutableStateOf(true) }
        var trayHintShown by remember { mutableStateOf(false) }
        fun showWindow() {
            windowVisible = true
        }
        fun quit() {
            placementStore.save(windowState)
            exitApplication()
        }
        Tray(
            icon = icon,
            state = trayState,
            tooltip = "KGS Calendar",
            onAction = ::showWindow,
            menu = {
                Item(AppStrings.get(R.string.open, Locale.getDefault()), onClick = ::showWindow)
                Item(AppStrings.get(R.string.desktop_quit, Locale.getDefault()), onClick = ::quit)
            },
        )
        Window(
            visible = windowVisible,
            // Closing keeps the app in the notification area, so reminders still appear, as on Android.
            onCloseRequest = {
                placementStore.save(windowState)
                windowVisible = false
                if (!trayHintShown) {
                    trayHintShown = true
                    trayState.sendNotification(Notification("KGS Calendar", AppStrings.get(R.string.desktop_running_in_tray, Locale.getDefault())))
                }
            },
            title = "KGS Calendar",
            icon = icon,
            state = windowState,
            // After dialogs and popups had their chance, like Android's back: the topmost layer goes first.
            onKeyEvent = { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) backDispatcher.back() else shortcuts.handle(event)
            },
        ) {
            val currentLoaded by rememberUpdatedState(loaded)
            DisposableEffect(window) {
                val listener = object : WindowAdapter() {
                    override fun windowActivated(e: WindowEvent?) {
                        currentLoaded?.graph?.windowActivatedAt?.tryEmit(System.currentTimeMillis())
                    }
                }
                window.addWindowListener(listener)
                window.minimumSize = java.awt.Dimension(420, 560)
                println("KGS renderer: ${window.renderApi}")
                onDispose { window.removeWindowListener(listener) }
            }
            val windowChrome = remember(window) {
                { background: androidx.compose.ui.graphics.Color, content: androidx.compose.ui.graphics.Color, dark: Boolean ->
                    WindowsChrome.apply(window, background, content, dark)
                }
            }
            CompositionLocalProvider(
                LocalBackDispatcher provides backDispatcher,
                LocalWindowChrome provides windowChrome,
                LocalDesktopShortcuts provides shortcuts,
                LocalCalendarChrome provides CalendarChrome.Desktop,
                LocalPaneLayout provides paneLayout,
                LocalAppFontFamily provides RobotoFontFamily,
                LocalAppPlatformTextStyle provides DesktopPlatformTextStyle,
                LocalShortcutBindings provides shortcutBindings,
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        // Keys a focused element didn't use reach the shortcuts here, before Compose
                        // would spend the arrow keys on moving focus between buttons.
                        .onKeyEvent { event -> shortcuts.handle(event) }
                        // The mouse's back button goes back like Escape.
                        .onPointerEvent(PointerEventType.Press) { event ->
                            if (event.button == PointerButton.Back) backDispatcher.back()
                        },
                ) {
                    // The app's own startup screen continues this one where it leaves off.
                    when (val app = loaded) {
                        null -> KgsStartupScreen(startupTheme, startupColor)
                        else -> KgsCalendarApp(app.viewModel)
                    }
                }
            }
        }
    }
}

private const val ClassArchiveDelayMs = 45_000L
