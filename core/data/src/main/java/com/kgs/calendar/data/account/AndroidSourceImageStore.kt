package com.kgs.calendar.data.account

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.File

/** The Android store: PNG files in [directory] (null keeps nothing), scaled with Bitmap. */
fun SourceImageStore(directory: File?): SourceImageStore =
    SourceImageStore(directory?.let(::FileSourceImageFiles), BitmapSquareImageEncoder)

class FileSourceImageFiles(private val directory: File) : SourceImageFiles {
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

object BitmapSquareImageEncoder : SquareImageEncoder {
    override fun encodeSquarePng(bytes: ByteArray, maxSizePx: Int): ByteArray? {
        val bitmap = decodeSquare(bytes, maxSizePx) ?: return null
        return try {
            ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    internal fun decodeSquare(bytes: ByteArray, maxSizePx: Int = SourceImageStore.MAX_SIZE_PX): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sampleSize = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= maxSizePx) sampleSize *= 2
        // The shorter side decides the quality, but the longer one the memory: a strip of a few
        // pixels by a hundred thousand must not be decoded whole just to become a small avatar.
        while ((bounds.outWidth / sampleSize).toLong() * (bounds.outHeight / sampleSize) > MAX_DECODED_PIXELS) sampleSize *= 2
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sampleSize })
            ?: return null
        val side = minOf(decoded.width, decoded.height)
        val cropped = Bitmap.createBitmap(decoded, (decoded.width - side) / 2, (decoded.height - side) / 2, side, side)
        val size = minOf(side, maxSizePx)
        val scaled = if (side == size) cropped else Bitmap.createScaledBitmap(cropped, size, size, true)
        if (cropped !== decoded) decoded.recycle()
        if (scaled !== cropped) cropped.recycle()
        return scaled
    }

    /** About 16 MB at four bytes per pixel, plenty for a 256 px avatar. */
    private const val MAX_DECODED_PIXELS = 4_000_000L
}
