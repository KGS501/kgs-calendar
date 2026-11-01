package com.kgs.calendar.data.platform

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import androidx.sqlite.SQLiteException
import com.kgs.calendar.data.local.KgsDatabase
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

internal actual fun Throwable.isDatabaseFailure(): Boolean = this is SQLiteException

internal actual suspend fun <T> KgsDatabase.inWriteTransaction(block: suspend () -> T): T =
    useWriterConnection { connection -> connection.immediateTransaction { block() } }

internal actual fun squarePng(bytes: ByteArray, maxSize: Int): ByteArray? {
    val decoded = runCatching { ImageIO.read(ByteArrayInputStream(bytes)) }.getOrNull() ?: return null
    if (decoded.width <= 0 || decoded.height <= 0) return null
    val side = minOf(decoded.width, decoded.height)
    val size = minOf(side, maxSize)
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
