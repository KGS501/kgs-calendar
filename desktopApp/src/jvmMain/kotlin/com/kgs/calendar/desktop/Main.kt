package com.kgs.calendar.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import com.kgs.calendar.time.defaultAppLocale
import com.kgs.calendar.ui.CalendarViewModel
import com.kgs.calendar.ui.KgsCalendarApp
import com.kgs.calendar.ui.platform.DesktopAppVersion
import com.kgs.calendar.ui.platform.DesktopWindowChrome
import com.kgs.calendar.ui.res.Res
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.awt.Component
import java.awt.Container
import java.awt.Frame
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File
import java.util.concurrent.CompletableFuture
import javax.swing.RootPaneContainer
import javax.swing.SwingUtilities
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/** `--data-dir <folder>` keeps a separate profile, e.g. for testing; otherwise [DataLocation.default]. */
private fun dataDirectory(args: Array<String>): File {
    val explicit = args.indexOf("--data-dir").takeIf { it >= 0 }?.let { args.getOrNull(it + 1) }
    if (explicit != null) return File(explicit).absoluteFile
    return DataLocation.default.also(DataLocation::adoptPreviewData)
}

/** The calendar's data and logic, set up on their own thread while the window starts. */
private class LoadedApp(val graph: DesktopAppGraph, val viewModel: CalendarViewModel)

fun main(args: Array<String>) {
    DesktopAppVersion = System.getProperty("kgs.version") ?: "V.2.0.0"
    val dataDirectory = dataDirectory(args)
    // Started again while it runs (the Start menu, a shortcut): the running app shows its window instead.
    val instance = SingleInstance.claim(dataDirectory) ?: exitProcess(0)
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
    application {
        val loaded by produceState<LoadedApp?>(null) { value = loading.await() }
        val windowState = remember { placementStore.restore() }
        val icon = painterResource("kgs_logo.png")
        val trayActions = remember { TrayActions() }
        val tray = remember { AppTray(tooltip = "KGS Calendar", onOpen = { trayActions.open() }, onMenu = { trayActions.open() }) }
        DisposableEffect(tray) { onDispose { tray.remove() } }
        LaunchedEffect(loaded) {
            val app = loaded ?: return@LaunchedEffect
            app.graph.reminders.notifier = { title, body -> tray.notify(title, body.orEmpty()) }
            // Sync and reminders work in the background, never on the window's thread.
            app.graph.start(CoroutineScope(coroutineContext + Dispatchers.Default))
            // Once the app has settled, its classes are saved for faster starts (once per version).
            launch(Dispatchers.IO) {
                delay(ClassArchiveDelayMs)
                ClassDataArchive.build()
            }
        }
        var windowVisible by remember { mutableStateOf(true) }
        var trayHintShown by remember { mutableStateOf(false) }
        var composeWindow by remember { mutableStateOf<java.awt.Window?>(null) }
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
            SessionEnd.install { SwingUtilities.invokeLater(::quit) }
            UpdateWatcher.start { SwingUtilities.invokeLater(::quit) }
        }
        trayActions.open = ::showWindow
        Window(
            visible = windowVisible,
            // Closing keeps the app in the notification area, so reminders still appear, as on Android.
            onCloseRequest = {
                placementStore.save(windowState)
                windowVisible = false
                if (!trayHintShown) {
                    trayHintShown = true
                    tray.notify("KGS Calendar", Res.string.desktop_running_in_tray.text(defaultAppLocale()))
                }
            },
            title = "KGS Calendar",
            icon = icon,
            state = windowState,
        ) {
            val currentLoaded by rememberUpdatedState(loaded)
            SideEffect { composeWindow = window }
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
            DisposableEffect(window) {
                DesktopWindowChrome = { dark ->
                    val background = if (dark) Color(0xFF101923) else Color(0xFFEEF6FF)
                    WindowsChrome.apply(window, background, if (dark) Color.White else Color(0xFF1C1A18), dark)
                    paintBackground(window, java.awt.Color(background.toArgb(), true))
                }
                onDispose { DesktopWindowChrome = {} }
            }
            Box(Modifier.fillMaxSize()) {
                val app = loaded
                if (app != null && windowSettled) KgsCalendarApp(app.viewModel) else Box(Modifier.fillMaxSize().background(Color(0xFF101923)))
            }
        }
    }
}

private const val ClassArchiveDelayMs = 45_000L

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
