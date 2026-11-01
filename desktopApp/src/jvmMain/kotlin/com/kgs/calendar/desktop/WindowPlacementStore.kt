package com.kgs.calendar.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.io.File
import java.util.Properties

/** Remembers the window's size, position and maximized state between launches. */
class WindowPlacementStore(private val file: File) {
    fun restore(): WindowState {
        val values = Properties().apply { if (file.isFile) runCatching { file.inputStream().use(::load) } }
        val width = values.getProperty("width")?.toFloatOrNull()?.takeIf { it >= MIN_WIDTH } ?: DEFAULT_WIDTH
        val height = values.getProperty("height")?.toFloatOrNull()?.takeIf { it >= MIN_HEIGHT } ?: DEFAULT_HEIGHT
        val x = values.getProperty("x")?.toFloatOrNull()
        val y = values.getProperty("y")?.toFloatOrNull()
        // A window saved on a monitor that is gone would open off screen; center it instead.
        val position = if (x != null && y != null && isOnScreen(x, y, width)) {
            WindowPosition(x.dp, y.dp)
        } else {
            WindowPosition.PlatformDefault
        }
        val placement = if (values.getProperty("maximized") == "true") WindowPlacement.Maximized else WindowPlacement.Floating
        return WindowState(placement = placement, position = position, size = DpSize(width.dp, height.dp))
    }

    fun save(state: WindowState) {
        val values = Properties()
        values["maximized"] = (state.placement == WindowPlacement.Maximized).toString()
        // While maximized the floating size and position are kept from before.
        if (state.placement == WindowPlacement.Floating) {
            values["width"] = state.size.width.value.toString()
            values["height"] = state.size.height.value.toString()
            (state.position as? WindowPosition.Absolute)?.let {
                values["x"] = it.x.value.toString()
                values["y"] = it.y.value.toString()
            }
        } else {
            Properties().apply { if (file.isFile) runCatching { file.inputStream().use(::load) } }
                .forEach { (key, value) -> if (key != "maximized") values[key] = value }
        }
        runCatching {
            file.parentFile?.mkdirs()
            file.outputStream().use { values.store(it, null) }
        }
    }

    private fun isOnScreen(x: Float, y: Float, width: Float): Boolean = runCatching {
        val titleBar = Rectangle(x.toInt() + 40, y.toInt(), (width - 80).toInt().coerceAtLeast(1), 32)
        GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.any {
            it.defaultConfiguration.bounds.intersects(titleBar)
        }
    }.getOrDefault(false)

    private companion object {
        const val DEFAULT_WIDTH = 1280f
        const val DEFAULT_HEIGHT = 860f
        const val MIN_WIDTH = 420f
        const val MIN_HEIGHT = 560f
    }
}
