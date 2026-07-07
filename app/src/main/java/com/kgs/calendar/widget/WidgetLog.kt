package com.kgs.calendar.widget

import android.content.Context
import android.util.Log

internal object WidgetLog {
    private const val TAG = "KgsWidget"

    fun d(context: Context, message: String, throwable: Throwable? = null) {
        if (!context.isDebuggable) return
        if (throwable == null) {
            Log.d(TAG, message)
        } else {
            Log.d(TAG, message, throwable)
        }
    }
}

internal val Context.isDebuggable: Boolean
    get() = (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
