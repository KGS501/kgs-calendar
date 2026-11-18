package com.kgs.calendar.data.account

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO

/** Source images as PNG files in [directory]. */
class DesktopSourceImageFiles(private val directory: File) : SourceImageFiles {
    override fun list(): List<Pair<String, Long>> =
        directory.listFiles { file -> file.isFile }.orEmpty().map { it.name to it.lastModified() }

    override fun pathOf(name: String): String = File(directory, name).absolutePath

    override fun writeReplacing(name: String, bytes: ByteArray): Boolean {
        directory.mkdirs()
        val target = File(directory, name)
        val temporary = File(directory, "$name.tmp")
        try {
            temporary.writeBytes(bytes)
            if (!temporary.renameTo(target)) {
                target.delete()
                if (!temporary.renameTo(target)) return false
            }
        } finally {
            temporary.delete()
        }
        return true
    }

    override fun rename(from: String, to: String): Boolean = File(directory, from).renameTo(File(directory, to))

    override fun delete(name: String) {
        File(directory, name).delete()
    }

    override fun lastModified(name: String): Long? = File(directory, name).takeIf { it.exists() }?.lastModified()

    override fun touch(name: String, millis: Long) {
        directory.mkdirs()
        val file = File(directory, name)
        if (!file.exists()) file.createNewFile()
        file.setLastModified(millis)
    }
}

/** Center-crops and scales with Java's imaging, then PNG. */
object AwtSquareImageEncoder : SquareImageEncoder {
    override fun encodeSquarePng(bytes: ByteArray, maxSizePx: Int): ByteArray? {
        val decoded = runCatching { ImageIO.read(ByteArrayInputStream(bytes)) }.getOrNull() ?: return null
        if (decoded.width <= 0 || decoded.height <= 0) return null
        val side = minOf(decoded.width, decoded.height)
        val size = minOf(side, maxSizePx)
        val square = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val graphics = square.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            val left = (decoded.width - side) / 2
            val top = (decoded.height - side) / 2
            graphics.drawImage(decoded, 0, 0, size, size, left, top, left + side, top + side, null)
        } finally {
            graphics.dispose()
        }
        return ByteArrayOutputStream().use { output ->
            ImageIO.write(square, "png", output)
            output.toByteArray()
        }
    }
}
