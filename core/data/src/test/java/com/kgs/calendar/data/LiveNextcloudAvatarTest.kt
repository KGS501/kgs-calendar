package com.kgs.calendar.data

import android.graphics.Bitmap
import android.graphics.Color
import com.kgs.calendar.data.account.SourceImageKeys
import com.kgs.calendar.data.remote.CalDavHttpClient
import kotlinx.coroutines.runBlocking
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.net.URI

/** Opt-in check of the avatar endpoint against a disposable local Nextcloud. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LiveNextcloudAvatarTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun customAvatarBecomesTheSourceImageAndGeneratedOnesDoNot() = runBlocking {
        val base = System.getenv("KGS_TEST_NEXTCLOUD_URL")
        assumeTrue("Needs a disposable local Nextcloud", base != null)
        require(URI(base!!).host in setOf("localhost", "127.0.0.1"))
        val user = requireNotNull(System.getenv("KGS_TEST_NEXTCLOUD_USER"))
        val password = requireNotNull(System.getenv("KGS_TEST_NEXTCLOUD_PASSWORD"))
        RepositoryHarness(sourceImageDirectory = folder.root).use { harness ->
            val client = CalDavHttpClient(harness.httpClient)
            fun avatarRequest(method: String, body: okhttp3.RequestBody? = null): Int =
                harness.httpClient.newCall(
                    Request.Builder()
                        .url("${base.trimEnd('/')}/index.php/avatar/")
                        .header("Authorization", Credentials.basic(user, password))
                        // Nextcloud skips its CSRF check for API requests.
                        .header("OCS-APIRequest", "true")
                        .method(method, body)
                        .build(),
                ).execute().use { it.code }

            avatarRequest("DELETE")
            assertFalse(client.fetchNextcloudAvatar(base, user, password)!!.isCustom)

            val png = ByteArrayOutputStream().use { output ->
                Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
                    .compress(Bitmap.CompressFormat.PNG, 100, output)
                output.toByteArray()
            }
            val upload = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("files[]", "avatar.png", png.toRequestBody("image/png".toMediaType()))
                .build()
            try {
                assertEquals(200, avatarRequest("POST", upload))
                assertTrue(client.fetchNextcloudAvatar(base, user, password)!!.isCustom)

                val account = harness.repository.saveManualAccount(base, user, password)
                harness.repository.syncNow()
                assertTrue(SourceImageKeys.account(account.id) in harness.components.sourceImages.images.value)
            } finally {
                avatarRequest("DELETE")
            }
        }
    }
}
