package com.kgs.calendar.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import com.kgs.calendar.res.AppStrings
import com.kgs.calendar.res.SharedR as R
import com.kgs.calendar.ui.KgsCalendarApp
import com.kgs.calendar.ui.desktop.CalendarChrome
import com.kgs.calendar.ui.desktop.LocalCalendarChrome
import com.kgs.calendar.ui.desktop.LocalPaneLayout
import com.kgs.calendar.ui.platform.BackDispatcher
import com.kgs.calendar.ui.platform.DesktopAppVersion
import com.kgs.calendar.ui.platform.DesktopShortcuts
import com.kgs.calendar.ui.platform.LocalBackDispatcher
import com.kgs.calendar.ui.platform.LocalDesktopShortcuts
import com.kgs.calendar.ui.platform.LocalWindowChrome
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.Locale

/** `--data-dir <folder>` keeps a separate profile, e.g. for testing; otherwise `%LOCALAPPDATA%\KGS Calendar`. */
private fun dataDirectory(args: Array<String>): File {
    val explicit = args.indexOf("--data-dir").takeIf { it >= 0 }?.let { args.getOrNull(it + 1) }
    if (explicit != null) return File(explicit).absoluteFile
    val base = System.getenv("LOCALAPPDATA")?.let(::File) ?: File(System.getProperty("user.home"), ".local/share")
    return File(base, "KGS Calendar")
}

@OptIn(ExperimentalComposeUiApi::class)
fun main(args: Array<String>) {
    DesktopAppVersion = System.getProperty("kgs.version") ?: "V.1.4.6-3"
    val graph = DesktopGraph(dataDirectory(args))
    val placementStore = WindowPlacementStore(File(graph.dataDirectory, "window.properties"))
    val paneLayout = PaneLayoutStore(File(graph.dataDirectory, "layout.properties")).restore()
    // Demo items only ever go into a separate, empty profile.
    if ("--demo" in args && "--data-dir" in args) {
        runBlocking {
            if (graph.database.accountDao().getAll().isEmpty()) seedDemoData(graph.repository)
        }
    }
    application {
        val viewModel = remember { graph.createViewModel() }
        val backDispatcher = remember { BackDispatcher() }
        val shortcuts = remember { DesktopShortcuts() }
        val trayState = rememberTrayState()
        val windowState = remember { placementStore.restore() }
        val icon = painterResource("kgs_logo.png")
        LaunchedEffect(Unit) {
            graph.reminders.notifier = { title, body -> trayState.sendNotification(Notification(title, body.orEmpty())) }
            graph.start(this)
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
            DisposableEffect(window) {
                val listener = object : WindowAdapter() {
                    override fun windowActivated(e: WindowEvent?) {
                        graph.windowActivatedAt.tryEmit(System.currentTimeMillis())
                    }
                }
                window.addWindowListener(listener)
                window.minimumSize = java.awt.Dimension(420, 560)
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
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        // The mouse's back button goes back like Escape.
                        .onPointerEvent(PointerEventType.Press) { event ->
                            if (event.button == PointerButton.Back) backDispatcher.back()
                        },
                ) {
                    KgsCalendarApp(viewModel)
                }
            }
        }
    }
}
