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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import com.kgs.calendar.time.defaultAppLocale
import com.kgs.calendar.ui.res.Res
import com.kgs.calendar.ui.theme.DesktopPlatformTextStyle
import com.kgs.calendar.ui.theme.RobotoFontFamily
import com.kgs.calendar.ui.CalendarViewModel
import com.kgs.calendar.ui.KgsCalendarApp
import com.kgs.calendar.ui.KgsStartupScreen
import com.kgs.calendar.ui.desktop.KgsTrayMenu
import com.kgs.calendar.ui.desktop.CalendarChrome
import com.kgs.calendar.ui.desktop.LocalCalendarChrome
import com.kgs.calendar.ui.desktop.LocalPaneLayout
import com.kgs.calendar.ui.platform.DesktopAppVersion
import com.kgs.calendar.ui.platform.DesktopShortcuts
import com.kgs.calendar.ui.platform.LocalDesktopShortcuts
import androidx.compose.ui.graphics.toArgb
import com.kgs.calendar.ui.platform.LocalDesktopWindowBehaviour
import com.kgs.calendar.ui.platform.LocalShortcutBindings
import com.kgs.calendar.ui.platform.LocalWindowChrome
import com.kgs.calendar.ui.theme.LocalAppFontFamily
import com.kgs.calendar.ui.theme.LocalAppPlatformTextStyle
import java.awt.AWTEvent
import java.awt.Component
import java.awt.Container
import java.awt.Frame
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import java.awt.event.KeyEvent
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

/** `--data-dir <folder>` keeps a separate profile, e.g. for testing; otherwise [DataLocation.default]. */
private fun dataDirectory(args: Array<String>): File {
    val explicit = args.indexOf("--data-dir").takeIf { it >= 0 }?.let { args.getOrNull(it + 1) }
    if (explicit != null) return File(explicit).absoluteFile
    return DataLocation.default.also(DataLocation::adoptPreviewData)
}

/** The calendar's data and logic, set up on their own thread while the window starts. */
private class LoadedApp(val graph: DesktopAppGraph, val viewModel: CalendarViewModel)

@OptIn(ExperimentalComposeUiApi::class)
fun main(args: Array<String>) {
    DesktopAppVersion = System.getProperty("kgs.version") ?: "V.2.0.1"
    val dataDirectory = dataDirectory(args)
    // Started again while it runs (the Start menu, a shortcut): the running app shows its window instead.
    val instance = SingleInstance.claim(dataDirectory) ?: exitProcess(0)
    // The calendar's database, settings and view model take a moment to set up, as do the window and
    // its renderer; the two happen side by side, and the window shows its startup screen meanwhile.
    val loading = CompletableFuture<LoadedApp>()
    thread(name = "kgs-startup") {
        runCatching {
            val graph = DesktopAppGraph(dataDirectory)
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
        val shortcuts = remember { DesktopShortcuts(shortcutBindings) }
        val windowState = remember { placementStore.restore() }
        val icon = painterResource("kgs_logo.png")
        // The tray's actions reach the window, which is set up further down.
        val trayActions = remember { TrayActions() }
        var menuAnchor by remember { mutableStateOf<java.awt.Point?>(null) }
        var menuPrepared by remember { mutableStateOf(false) }
        val tray = remember {
            AppTray(
                tooltip = TrayTooltip,
                notificationIcon = File(dataDirectory, "notification-icon.png"),
                onOpen = { trayActions.open() },
                onMenu = { point ->
                    menuPrepared = true
                    menuAnchor = point
                },
            )
        }
        DisposableEffect(tray) { onDispose { tray.remove() } }
        LaunchedEffect(loaded) {
            val app = loaded ?: return@LaunchedEffect
            app.graph.reminders.notifier = { title, body -> tray.notify(title, body.orEmpty()) }
            // Sync and reminders work in the background, never on the window's thread.
            app.graph.start(CoroutineScope(coroutineContext + Dispatchers.Default))
            // The next start's startup screen shows in the look the app has now.
            launch(Dispatchers.IO) {
                app.viewModel.uiState
                    .map { it.themeMode to it.colorMode }
                    .distinctUntilChanged()
                    .collect { (theme, color) -> appearanceStore.save(theme, color) }
            }
            // The tray icon's menu is made ready in good time, so it opens at once when first asked for.
            launch {
                delay(TrayMenuPrepareDelayMs)
                menuPrepared = true
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
            // Signing out, or an installer updating the app, closes it properly.
            SessionEnd.install { placementStore.save(windowState) }
            UpdateWatcher.start { SwingUtilities.invokeLater(::quit) }
        }
        trayActions.open = ::showWindow
        if (menuPrepared) {
            CompositionLocalProvider(
                LocalAppFontFamily provides RobotoFontFamily,
                LocalAppPlatformTextStyle provides DesktopPlatformTextStyle,
                LocalCalendarChrome provides CalendarChrome.Desktop,
            ) {
                KgsTrayMenu(
                    anchor = menuAnchor,
                    viewModel = loaded?.viewModel,
                    themeMode = startupTheme,
                    colorMode = startupColor,
                    shortcuts = shortcutBindings,
                    onDismiss = { menuAnchor = null },
                    onOpen = ::showWindow,
                    onAction = { action ->
                        showWindow()
                        shortcuts.perform(action)
                    },
                    onQuit = ::quit,
                    icon = icon,
                )
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
                        tray.notify("KGS Calendar", Res.string.desktop_running_in_tray.text(defaultAppLocale()))
                    }
                }
            },
            title = "KGS Calendar",
            icon = icon,
            state = windowState,
            // After dialogs and popups had their chance; Escape goes back through Compose's back handlers.
            onKeyEvent = { event -> shortcuts.handle(event) },
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
                        // It may have been changed in Windows' settings in the meantime.
                        windowBehaviour.refreshOpenAtLogin()
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
            DisposableEffect(window) {
                val stopListening = installMouseBackButton(window)
                onDispose { stopListening() }
            }
            CompositionLocalProvider(
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
                        .onKeyEvent { event -> shortcuts.handle(event) },
                ) {
                    // The app's own startup screen continues this one where it leaves off.
                    val app = loaded
                    if (app == null || !windowSettled) KgsStartupScreen(startupTheme, startupColor) else KgsCalendarApp(app.viewModel)
                }
            }
        }
    }
}

/** AWT's number for the mouse's back button. */
private const val MouseBackButton = 4

/**
 * Makes the mouse's back button go back like Escape, wherever in [window] it is pressed. It listens
 * ahead of Compose: sheets and popups are layers of their own, which a modifier on the content
 * doesn't hear. Returns how to stop listening.
 */
private fun installMouseBackButton(window: java.awt.Window): () -> Unit {
    val listener = AWTEventListener { event ->
        if (event is MouseEvent && event.id == MouseEvent.MOUSE_PRESSED && event.button == MouseBackButton &&
            SwingUtilities.isDescendingFrom(event.component, window)
        ) {
            window.pressEscape()
        }
    }
    Toolkit.getDefaultToolkit().addAWTEventListener(listener, AWTEvent.MOUSE_EVENT_MASK)
    return { Toolkit.getDefaultToolkit().removeAWTEventListener(listener) }
}

/** Escape as if typed: the way into Compose's back handling, which has no public entry point. */
private fun java.awt.Window.pressEscape() {
    // Posted, not dispatched: only posted key events are passed on to the component that has the focus.
    val queue = Toolkit.getDefaultToolkit().systemEventQueue
    for (id in intArrayOf(KeyEvent.KEY_PRESSED, KeyEvent.KEY_RELEASED)) {
        queue.postEvent(
            KeyEvent(this, id, System.currentTimeMillis(), 0, KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED, KeyEvent.KEY_LOCATION_STANDARD),
        )
    }
}

private const val ClassArchiveDelayMs = 45_000L

private const val TrayTooltip = "KGS Calendar"

private const val TrayMenuPrepareDelayMs = 8_000L

/** What the tray icon does, set once the window it acts on exists. */
private class TrayActions {
    var open: () -> Unit = {}
}

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
