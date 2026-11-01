package com.kgs.calendar.data.account

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SourceImageStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun storesImagesAsSmallSquaresAndSurvivesARestart() {
        val store = SourceImageStore(folder.root)
        val key = SourceImageKeys.group("domain:school.org")

        assertTrue(store.save(key, png(800, 400)))

        val image = store.images.value.getValue(key)
        val stored = BitmapFactory.decodeFile(image.path)
        assertEquals(SourceImageStore.MAX_SIZE_PX, stored.width)
        assertEquals(SourceImageStore.MAX_SIZE_PX, stored.height)
        assertEquals(setOf(key), SourceImageStore(folder.root).images.value.keys)
    }

    @Test
    fun replacingAnImageChangesItsVersionAndDeletingRemovesIt() {
        val store = SourceImageStore(folder.root)
        val key = SourceImageKeys.account("account-1")
        store.save(key, png(40, 40))
        val first = store.images.value.getValue(key)

        store.save(key, png(50, 50))
        assertNotEquals(first.version, store.images.value.getValue(key).version)

        store.delete(key)
        assertFalse(key in store.images.value)
    }

    @Test
    fun rejectsDataThatIsNoImage() {
        val store = SourceImageStore(folder.root)

        assertFalse(store.save("account:x", "<html>".toByteArray()))
        assertTrue(store.images.value.isEmpty())
    }

    @Test
    fun refreshIsDueUntilMarkedAndAgainAfterTheInterval() {
        val store = SourceImageStore(folder.root)
        val key = SourceImageKeys.account("account-1")
        val now = 1_800_000_000_000L

        assertTrue(store.isRefreshDue(key, now, 1000L))
        store.markChecked(key, now)
        assertFalse(store.isRefreshDue(key, now + 999L, 1000L))
        assertTrue(store.isRefreshDue(key, now + 1000L, 1000L))
    }

    private fun png(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        return ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it); it.toByteArray() }
    }
}
