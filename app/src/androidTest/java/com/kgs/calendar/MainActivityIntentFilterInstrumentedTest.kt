package com.kgs.calendar

import android.content.ComponentName
import android.content.Intent
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityIntentFilterInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val mainActivity = ComponentName(context, MainActivity::class.java)

    @Test
    fun appIsDiscoverableAsACalendarApp() {
        val intent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR).apply {
            selector?.setPackage(context.packageName)
        }

        assertTrue(
            context.packageManager.queryIntentActivities(intent, 0)
                .any { it.activityInfo.packageName == mainActivity.packageName && it.activityInfo.name == mainActivity.className },
        )
    }

    @Test
    fun activityUsesImeResizeWithoutMovingTheEdgeToEdgeWindow() {
        val activityInfo = context.packageManager.getActivityInfo(mainActivity, 0)
        val adjustMode = activityInfo.softInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST

        assertEquals(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE, adjustMode)
    }
}
