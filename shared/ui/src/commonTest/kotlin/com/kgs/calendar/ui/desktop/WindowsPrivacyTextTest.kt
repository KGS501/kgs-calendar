package com.kgs.calendar.ui.desktop

import com.kgs.calendar.time.appLocaleOf
import com.kgs.calendar.ui.res.Res
import kotlin.test.Test
import kotlin.test.assertTrue

class WindowsPrivacyTextTest {
    /** People follow this path to delete their data; a single backslash in the strings file vanishes. */
    @Test fun theDataFolderKeepsItsBackslashes() {
        val folder = "%LOCALAPPDATA%\\KGS501\\KGS Calendar"
        for (language in listOf("en", "de")) {
            val locale = appLocaleOf(language)
            assertTrue(folder in Res.string.privacy_delete_body_windows.text(locale))
            assertTrue(folder in Res.string.privacy_delete_body_windows_store.text(locale))
        }
    }
}
