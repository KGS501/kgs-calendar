package com.kgs.calendar

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.kgs.calendar.navigation.SharedEventDraft

/**
 * The "Create event" share target. It reads only the text extras of the share (subject, text,
 * HTML text) and hands them to [MainActivity] in the app's own task, where the event editor opens
 * prefilled; the running app gets the share through `onNewIntent`. Nothing is saved until the
 * user saves the editor.
 */
class ShareToCalendarActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val draft = SharedEventDraft.fromShareIntent(intent)
        if (draft != null && savedInstanceState == null) {
            startActivity(
                draft.writeTo(Intent(this, MainActivity::class.java))
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP,
                    ),
            )
        }
        finish()
    }
}
