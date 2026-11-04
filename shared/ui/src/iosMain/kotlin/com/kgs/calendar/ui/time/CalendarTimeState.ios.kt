package com.kgs.calendar.ui.time

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kgs.calendar.time.nowMillis
import com.kgs.calendar.time.refreshSystemZone
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSSystemClockDidChangeNotification
import platform.Foundation.NSSystemTimeZoneDidChangeNotification
import platform.UIKit.UIApplicationSignificantTimeChangeNotification

@Composable
actual fun rememberCalendarTimeState(): CalendarTimeSnapshot {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    return produceState(initialValue = CalendarTimeSnapshot.now(), lifecycle) {
        fun refresh() {
            value = CalendarTimeSnapshot.now(value.revision + 1)
        }
        val center = NSNotificationCenter.defaultCenter
        val observers = listOf(
            NSSystemClockDidChangeNotification,
            NSSystemTimeZoneDidChangeNotification,
            UIApplicationSignificantTimeChangeNotification,
        ).map { name ->
            center.addObserverForName(name, `object` = null, queue = NSOperationQueue.mainQueue) { _ ->
                // The system zone is cached; drop it so the new zone applies.
                refreshSystemZone()
                refresh()
            }
        }
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) refresh()
        }
        lifecycle.addObserver(lifecycleObserver)
        val alignedTicker = launch {
            while (isActive) {
                val now = nowMillis()
                delay(60_000L - (now % 60_000L) + 25L)
                refresh()
            }
        }
        awaitDispose {
            alignedTicker.cancel()
            lifecycle.removeObserver(lifecycleObserver)
            observers.forEach { center.removeObserver(it) }
        }
    }.value
}
