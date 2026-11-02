package com.kgs.calendar.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.window.WindowPlacement
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
import androidx.compose.ui.graphics.toArgb
import com.kgs.calendar.ui.platform.LocalDesktopWindowBehaviour
import com.kgs.calendar.ui.platform.LocalShortcutBindings
import com.kgs.calendar.ui.platform.LocalWindowChrome
import com.kgs.calendar.ui.theme.LocalAppFontFamily
import com.kgs.calendar.ui.theme.LocalAppPlatformTextStyle
import java.awt.Component
import java.awt.Container
import java.awt.Frame
import java.awt.SystemTray
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.RootPaneContainer
import javax.swing.SwingUtilities
import kotlin.system.exitProcess
import java.io.File
import java.util.Locale
import java.util.concurrent.CompletableFuture
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.runtime.withFrameNanos

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
    // Started again while it runs (the Start menu, a shortcut): the running app shows its window instead.
    val instance = SingleInstance.claim(dataDirectory) ?: exitProcess(0)
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
    val windowBehaviour = WindowBehaviourStore(File(dataDirectory, "desktop.properties")).restore()
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
            // Sync and reminders work in the background, never on the window's thread.
            app.graph.start(CoroutineScope(coroutineContext + Dispatchers.Default))
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
        // The window appears once its startup screen's colours are set (see below), never as a grey frame.
        var windowVisible by remember { mutableStateOf(false) }
        var trayHintShown by remember { mutableStateOf(false) }
        var composeWindow by remember { mutableStateOf<java.awt.Window?>(null) }
        var shownOnce by remember { mutableStateOf(false) }
        fun showFirstTime() {
            if (shownOnce) return
            shownOnce = true
            windowVisible = true
        }
        LaunchedEffect(Unit) {
            // Should the startup screen not report its colours, the window still appears.
            delay(FirstShowFallbackMs)
            showFirstTime()
        }
        fun showWindow() {
            windowVisible = true
            val window = composeWindow ?: return
            window.isVisible = true
            (window as? Frame)?.let { frame ->
                if (frame.extendedState and Frame.ICONIFIED != 0) frame.extendedState = frame.extendedState and Frame.ICONIFIED.inv()
            }
            window.toFront()
            window.requestFocus()
        }
        fun quit() {
            placementStore.save(windowState)
            exitApplication()
            // The app ends once its background work has stopped; should some of it not, it ends anyway.
            thread(name = "kgs-quit", isDaemon = true) {
                Thread.sleep(QuitGraceMs)
                exitProcess(0)
            }
        }
        LaunchedEffect(Unit) {
            instance.listen { SwingUtilities.invokeLater(::showWindow) }
        }
        Tray(
            icon = icon,
            state = trayState,
            tooltip = TrayTooltip,
            onAction = ::showWindow,
            menu = {
                Item(AppStrings.get(R.string.open, Locale.getDefault()), onClick = ::showWindow)
                Item(AppStrings.get(R.string.desktop_quit, Locale.getDefault()), onClick = ::quit)
            },
        )
        LaunchedEffect(Unit) {
            // Windows' own action for a tray icon is a double click; like other apps', this one opens
            // with one. (The icon is the one Tray just added.)
            repeat(20) {
                val icons = runCatching { SystemTray.getSystemTray().trayIcons.filter { it.toolTip == TrayTooltip } }.getOrDefault(emptyList())
                if (icons.isNotEmpty()) {
                    icons.forEach { trayIcon ->
                        trayIcon.addMouseListener(object : MouseAdapter() {
                            override fun mouseClicked(e: MouseEvent) {
                                if (e.button == MouseEvent.BUTTON1 && e.clickCount == 1) showWindow()
                            }
                        })
                    }
                    return@LaunchedEffect
                }
                delay(100)
            }
        }
        Window(
            visible = windowVisible,
            // Closing keeps the app in the notification area, so reminders still appear, as on Android,
            // unless the user turned that off.
            onCloseRequest = {
                placementStore.save(windowState)
                if (!windowBehaviour.closeToTray) {
                    quit()
                } else {
                    windowVisible = false
                    if (!trayHintShown) {
                        trayHintShown = true
                        trayState.sendNotification(Notification("KGS Calendar", AppStrings.get(R.string.desktop_running_in_tray, Locale.getDefault())))
                    }
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
            // Its colours are set while the startup screen is first composed (windowChrome below); then
            // it may appear.
            SideEffect { composeWindow = window }
            LaunchedEffect(window) { showFirstTime() }
            // The startup screen stays until the window has been drawn at the size it settles at: the
            // calendar's first frame takes a while, and a window still growing meanwhile would show the
            // startup screen at its old size in a corner. A window that was maximized opens over the
            // work area and maximizes now, which changes its size by no more than its borders.
            var windowSettled by remember { mutableStateOf(false) }
            LaunchedEffect(window) {
                withTimeoutOrNull(SettleTimeoutMs) {
                    while (!window.isShowing) delay(16)
                    awaitStableSize(window)
                }
                if (placementStore.maximizeOnStart) {
                    windowState.placement = WindowPlacement.Maximized
                    withTimeoutOrNull(SettleTimeoutMs) {
                        while (window.extendedState and Frame.MAXIMIZED_BOTH != Frame.MAXIMIZED_BOTH) withFrameNanos { }
                        awaitStableSize(window)
                    }
                    placementStore.finishMaximizedStart(window)
                }
                windowSettled = true
            }
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
                    // Where the window is larger than what was drawn yet (while it opens maximized, or
                    // grows), it shows the app's background rather than a grey frame.
                    paintBackground(window, java.awt.Color(background.toArgb() or (0xFF shl 24)))
                    showFirstTime()
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
                LocalDesktopWindowBehaviour provides windowBehaviour,
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
                    val app = loaded
                    if (app == null || !windowSettled) KgsStartupScreen(startupTheme, startupColor) else KgsCalendarApp(app.viewModel)
                }
            }
        }
    }
}

private const val ClassArchiveDelayMs = 45_000L

private const val TrayTooltip = "KGS Calendar"

private const val SettleFrames = 3
private const val SettleTimeoutMs = 1_500L

/** Waits until [window] has kept its size over a few drawn frames. */
private suspend fun awaitStableSize(window: java.awt.Window) {
    var size = window.size
    var stableFrames = 0
    while (stableFrames < SettleFrames) {
        withFrameNanos { }
        val now = window.size
        if (now == size) stableFrames++ else stableFrames = 0
        size = now
    }
}

private const val FirstShowFallbackMs = 3_000L

private const val QuitGraceMs = 5_000L

/** The window's background, and that of everything in it, behind what Compose draws. */
private fun paintBackground(window: java.awt.Window, color: java.awt.Color) {
    if (window.background == color) return
    window.background = color
    fun paint(component: Component) {
        component.background = color
        (component as? Container)?.components?.forEach(::paint)
    }
    (window as? RootPaneContainer)?.let { paint(it.rootPane) }
}
