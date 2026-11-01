package com.kgs.calendar.data.platform

import android.database.SQLException
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.room.withTransaction
import com.kgs.calendar.data.local.KgsDatabase
import java.io.ByteArrayOutputStream

internal actual fun Throwable.isDatabaseFailure(): Boolean = this is SQLException

internal actual suspend fun <T> KgsDatabase.inWriteTransaction(block: suspend () -> T): T = withTransaction(block)

internal actual fun squarePng(bytes: ByteArray, maxSize: Int): ByteArray? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sampleSize = 1
    while (minOf(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= maxSize) sampleSize *= 2
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sampleSize })
        ?: return null
    val side = minOf(decoded.width, decoded.height)
    val cropped = Bitmap.createBitmap(decoded, (decoded.width - side) / 2, (decoded.height - side) / 2, side, side)
    val size = minOf(side, maxSize)
    val scaled = if (side == size) cropped else Bitmap.createScaledBitmap(cropped, size, size, true)
    if (cropped !== decoded) decoded.recycle()
    if (scaled !== cropped) cropped.recycle()
    return try {
        ByteArrayOutputStream().use { output ->
            scaled.compress(Bitmap.CompressFormat.PNG, 100, output)
            output.toByteArray()
        }
    } finally {
        scaled.recycle()
    }
}
