package com.kgs.calendar.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import com.sun.jna.Native
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.Window
import java.io.File
import java.util.Properties
import kotlin.math.roundToInt

/**
 * Remembers the window's size, position and maximized state between launches.
 *
 * A window that was maximized opens over the screen's work area first and only maximizes once it is
 * on screen ([maximizeOnStart], [finishMaximizedStart]): a window shown maximized at once draws its
 * first frames at its usual size in a corner while it grows.
 */
class WindowPlacementStore(private val file: File) {
    /** Whether the window should maximize once shown (it was maximized when the app last closed). */
    var maximizeOnStart = false
        private set

    /** The usual size and position (in the window's units) to return to from a maximized start. */
    private var normalBounds: Rectangle? = null

    /** Where the window opens over the whole work area before it maximizes. */
    private var startArea: Rectangle? = null

    fun restore(): WindowState {
        val values = load()
        val width = values.getProperty("width")?.toFloatOrNull()?.takeIf { it >= MIN_WIDTH } ?: DEFAULT_WIDTH
        val height = values.getProperty("height")?.toFloatOrNull()?.takeIf { it >= MIN_HEIGHT } ?: DEFAULT_HEIGHT
        val x = values.getProperty("x")?.toFloatOrNull()
        val y = values.getProperty("y")?.toFloatOrNull()
        val onScreen = x != null && y != null && isOnScreen(x, y, width)
        // A window saved on a monitor that is gone would open off screen; center it instead.
        val position = if (onScreen) WindowPosition(x!!.dp, y!!.dp) else WindowPosition.PlatformDefault
        if (values.getProperty("maximized") == "true") {
            val area = workArea(if (onScreen) x!! else null, if (onScreen) y!! else null)
            if (area != null) {
                maximizeOnStart = true
                startArea = area
                normalBounds = if (onScreen) {
                    Rectangle(x!!.roundToInt(), y!!.roundToInt(), width.roundToInt(), height.roundToInt())
                } else {
                    Rectangle(
                        area.x + ((area.width - width) / 2).roundToInt().coerceAtLeast(0),
                        area.y + ((area.height - height) / 2).roundToInt().coerceAtLeast(0),
                        width.roundToInt(),
                        height.roundToInt(),
                    )
                }
                return WindowState(
                    placement = WindowPlacement.Floating,
                    position = WindowPosition(area.x.dp, area.y.dp),
                    size = DpSize(area.width.dp, area.height.dp),
                )
            }
            return WindowState(placement = WindowPlacement.Maximized, position = position, size = DpSize(width.dp, height.dp))
        }
        return WindowState(placement = WindowPlacement.Floating, position = position, size = DpSize(width.dp, height.dp))
    }

    /**
     * Once [window] is maximized after a maximized start: the size it returns to when un-maximized is
     * the one saved, not the work area it opened over.
     */
    fun finishMaximizedStart(window: Window) {
        if (!maximizeOnStart) return
        maximizeOnStart = false
        val normal = normalBounds ?: return
        val area = startArea ?: return
        runCatching {
            val hwnd = WinDef.HWND(Native.getWindowPointer(window))
            val placement = WinUser.WINDOWPLACEMENT()
            if (!User32.INSTANCE.GetWindowPlacement(hwnd, placement).booleanValue()) return
            val scale = window.graphicsConfiguration.defaultTransform.scaleX
            // The restore position is in the work area's coordinates; it is the work area itself now.
            val offsetX = (area.x * scale).roundToInt() - placement.rcNormalPosition.left
            val offsetY = (area.y * scale).roundToInt() - placement.rcNormalPosition.top
            placement.rcNormalPosition.left = (normal.x * scale).roundToInt() - offsetX
            placement.rcNormalPosition.top = (normal.y * scale).roundToInt() - offsetY
            placement.rcNormalPosition.right = placement.rcNormalPosition.left + (normal.width * scale).roundToInt()
            placement.rcNormalPosition.bottom = placement.rcNormalPosition.top + (normal.height * scale).roundToInt()
            placement.showCmd = WinUser.SW_SHOWMAXIMIZED
            User32.INSTANCE.SetWindowPlacement(hwnd, placement)
        }
    }

    fun save(state: WindowState) {
        // Still opening over the work area: what was saved stays.
        if (maximizeOnStart) return
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
            load().forEach { (key, value) -> if (key != "maximized") values[key] = value }
        }
        runCatching {
            file.parentFile?.mkdirs()
            file.outputStream().use { values.store(it, null) }
        }
    }

    private fun load() = Properties().apply { if (file.isFile) runCatching { file.inputStream().use(::load) } }

    /** The work area (the screen without the taskbar) of the monitor at [x], [y], or the main one. */
    private fun workArea(x: Float?, y: Float?): Rectangle? = runCatching {
        val environment = GraphicsEnvironment.getLocalGraphicsEnvironment()
        val device = environment.screenDevices.firstOrNull { x != null && y != null && it.defaultConfiguration.bounds.contains(x.toInt() + 40, y.toInt() + 16) }
            ?: environment.defaultScreenDevice
        val configuration = device.defaultConfiguration
        val bounds = configuration.bounds
        val insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration)
        Rectangle(bounds.x + insets.left, bounds.y + insets.top, bounds.width - insets.left - insets.right, bounds.height - insets.top - insets.bottom)
    }.getOrNull()?.takeIf { it.width >= MIN_WIDTH && it.height >= MIN_HEIGHT }

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
