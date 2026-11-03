package com.kgs.calendar.desktop

import java.awt.Image
import java.awt.MouseInfo
import java.awt.Point
import java.awt.RenderingHints
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BaseMultiResolutionImage
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

/**
 * The app's icon in the notification area. One click opens the window (as other apps' icons do;
 * Windows' own action is a double click), the right button opens the app's own menu at [onMenu]'s
 * point, and reminders appear as the system's notifications from it.
 *
 * The icon comes in every size the notification area uses (16 px at 100 % up to 32 px at 200 %),
 * drawn from the large logo, cropped to the calendar so it is as large as other apps' icons: Java
 * would otherwise scale one small image up or down pixel by pixel.
 */
class AppTray(
    tooltip: String,
    private val onOpen: () -> Unit,
    private val onMenu: (Point) -> Unit,
) {
    private val icon: TrayIcon? = runCatching {
        if (!SystemTray.isSupported()) return@runCatching null
        TrayIcon(trayImage(), tooltip).apply {
            isImageAutoSize = true
            addActionListener { onOpen() }
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.button == MouseEvent.BUTTON1 && e.clickCount == 1) onOpen()
                }

                override fun mouseReleased(e: MouseEvent) {
                    if (e.isPopupTrigger || e.button == MouseEvent.BUTTON3) {
                        // The pointer's own position, in the same coordinates as the windows.
                        onMenu(MouseInfo.getPointerInfo()?.location ?: Point(e.xOnScreen, e.yOnScreen))
                    }
                }
            })
            SystemTray.getSystemTray().add(this)
        }
    }.getOrNull()

    /** Shows a notification (a reminder, a hint) from the app's icon. */
    fun notify(title: String, body: String) {
        icon?.displayMessage(title, body, TrayIcon.MessageType.NONE)
    }

    fun remove() {
        icon?.let { runCatching { SystemTray.getSystemTray().remove(it) } }
    }

    private companion object {
        val Sizes = listOf(16, 20, 24, 28, 32, 40, 48, 64)

        fun trayImage(): Image {
            val source = AppTray::class.java.getResourceAsStream("/kgs_logo.png")!!.use(ImageIO::read)
            val square = cropToSquare(source)
            return BaseMultiResolutionImage(*Sizes.map { size -> scaled(square, size) }.toTypedArray())
        }

        /** The opaque part of [image] in the middle of a square with a hair of room around it. */
        fun cropToSquare(image: BufferedImage): BufferedImage {
            var minX = image.width
            var minY = image.height
            var maxX = 0
            var maxY = 0
            for (y in 0 until image.height) for (x in 0 until image.width) {
                if ((image.getRGB(x, y) ushr 24) > 20) {
                    if (x < minX) minX = x
                    if (y < minY) minY = y
                    if (x > maxX) maxX = x
                    if (y > maxY) maxY = y
                }
            }
            if (maxX < minX) return image
            val width = maxX - minX + 1
            val height = maxY - minY + 1
            val side = (maxOf(width, height) * 1.04).toInt()
            val square = BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB)
            square.createGraphics().apply {
                drawImage(image, (side - width) / 2 - minX, (side - height) / 2 - minY, null)
                dispose()
            }
            return square
        }

        /** Halves the image step by step and then draws it at [size]: smooth at any size. */
        fun scaled(image: BufferedImage, size: Int): BufferedImage {
            var current = image
            while (current.width / 2 >= size * 2) current = resize(current, current.width / 2)
            return resize(current, size)
        }

        fun resize(image: BufferedImage, size: Int): BufferedImage {
            val result = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
            result.createGraphics().apply {
                setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
                setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
                setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                drawImage(image, 0, 0, size, size, null)
                dispose()
            }
            return result
        }
    }
}
