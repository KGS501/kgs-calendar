package com.kgs.calendar.data.account

import com.kgs.calendar.platform.toByteArray
import com.kgs.calendar.platform.toNSData
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileModificationDate
import platform.Foundation.NSURL
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.writeToFile
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfFile
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation

/** Source images as files in [directory] (an absolute path). */
class IosSourceImageFiles(private val directory: String) : SourceImageFiles {
    private val files = NSFileManager.defaultManager

    private fun ensureDirectory() {
        files.createDirectoryAtPath(directory, withIntermediateDirectories = true, attributes = null, error = null)
    }

    override fun list(): List<Pair<String, Long>> =
        (files.contentsOfDirectoryAtPath(directory, null) as? List<*>).orEmpty().mapNotNull { name ->
            val text = name as? String ?: return@mapNotNull null
            text to (lastModified(text) ?: 0L)
        }

    override fun pathOf(name: String): String = "$directory/$name"

    override fun writeReplacing(name: String, bytes: ByteArray): Boolean {
        ensureDirectory()
        return bytes.toNSData().writeToFile(pathOf(name), atomically = true)
    }

    override fun rename(from: String, to: String): Boolean =
        files.moveItemAtPath(pathOf(from), toPath = pathOf(to), error = null)

    override fun delete(name: String) {
        files.removeItemAtPath(pathOf(name), null)
    }

    override fun lastModified(name: String): Long? {
        val attributes = files.attributesOfItemAtPath(pathOf(name), null) ?: return null
        val date = attributes[NSFileModificationDate] as? NSDate ?: return null
        return (date.timeIntervalSince1970 * 1000).toLong()
    }

    override fun touch(name: String, millis: Long) {
        ensureDirectory()
        val path = pathOf(name)
        if (!files.fileExistsAtPath(path)) files.createFileAtPath(path, contents = null, attributes = null)
        files.setAttributes(mapOf(NSFileModificationDate to NSDate.dateWithTimeIntervalSince1970(millis / 1000.0)), ofItemAtPath = path, error = null)
    }
}

/** Center-crop and scale with UIKit, then PNG. */
object UIKitSquareImageEncoder : SquareImageEncoder {
    override fun encodeSquarePng(bytes: ByteArray, maxSizePx: Int): ByteArray? {
        val image = UIImage.imageWithData(bytes.toNSData()) ?: return null
        val (width, height) = image.size.useContents { width to height }
        if (width <= 0 || height <= 0) return null
        val scale = image.scale
        val side = minOf(width, height)
        val target = minOf(side * scale, maxSizePx.toDouble())
        val format = UIGraphicsImageRendererFormat.defaultFormat()
        format.scale = 1.0
        val renderer = UIGraphicsImageRenderer(size = CGSizeMake(target, target), format = format)
        val output = renderer.imageWithActions { _ ->
            val drawScale = target / side
            image.drawInRect(CGRectMake(-(width - side) / 2 * drawScale, -(height - side) / 2 * drawScale, width * drawScale, height * drawScale))
        }
        return UIImagePNGRepresentation(output)?.toByteArray()
    }
}
