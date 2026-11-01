package com.kgs.calendar.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import androidx.compose.ui.window.rememberWindowState
import com.kgs.calendar.ui.KgsCalendarApp
import com.kgs.calendar.ui.platform.BackDispatcher
import com.kgs.calendar.ui.platform.DesktopAppVersion
import com.kgs.calendar.ui.platform.LocalBackDispatcher
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File

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
    application {
        val viewModel = remember { graph.createViewModel() }
        val backDispatcher = remember { BackDispatcher() }
        val trayState = rememberTrayState()
        val windowState = rememberWindowState(width = 1280.dp, height = 860.dp)
        val icon = painterResource("kgs_logo.png")
        LaunchedEffect(Unit) {
            graph.reminders.notifier = { title, body -> trayState.sendNotification(Notification(title, body.orEmpty())) }
            graph.start(this)
        }
        Tray(icon = icon, state = trayState, tooltip = "KGS Calendar")
        Window(
            onCloseRequest = ::exitApplication,
            title = "KGS Calendar",
            icon = icon,
            state = windowState,
            onPreviewKeyEvent = { event ->
                event.type == KeyEventType.KeyDown && event.key == Key.Escape && backDispatcher.back()
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
            CompositionLocalProvider(LocalBackDispatcher provides backDispatcher) {
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
