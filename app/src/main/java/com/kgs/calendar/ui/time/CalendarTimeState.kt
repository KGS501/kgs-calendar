package com.kgs.calendar.ui.time

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

data class CalendarTimeSnapshot(
    val today: LocalDate,
    val nowMinute: LocalTime,
    val revision: Long = 0,
) {
    companion object {
        fun now(revision: Long = 0): CalendarTimeSnapshot {
            val now = ZonedDateTime.now()
            return CalendarTimeSnapshot(
                today = now.toLocalDate(),
                nowMinute = now.toLocalTime().withSecond(0).withNano(0),
                revision = revision,
            )
        }
    }
}

val LocalCalendarTimeSnapshot = compositionLocalOf { CalendarTimeSnapshot.now() }

@Composable
fun rememberCalendarTimeState(): CalendarTimeSnapshot {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    return produceState(
        initialValue = CalendarTimeSnapshot.now(),
        context,
        lifecycle,
    ) {
        fun refresh() {
            value = CalendarTimeSnapshot.now(value.revision + 1)
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                refresh()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) refresh()
        }
        lifecycle.addObserver(lifecycleObserver)

        val alignedTicker = launch {
            while (isActive) {
                val nowMillis = System.currentTimeMillis()
                delay(60_000L - (nowMillis % 60_000L) + 25L)
                refresh()
            }
        }

        awaitDispose {
            alignedTicker.cancel()
            lifecycle.removeObserver(lifecycleObserver)
            context.unregisterReceiver(receiver)
        }
    }.value
}
