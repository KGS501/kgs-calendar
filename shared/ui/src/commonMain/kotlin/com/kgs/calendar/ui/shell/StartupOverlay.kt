package com.kgs.calendar.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.kgs.calendar.data.settings.AppColorMode
import com.kgs.calendar.data.settings.AppThemeMode
import com.kgs.calendar.ui.res.stringResource
import com.kgs.calendar.ui.platform.PlatformSystemBarsEffect
import com.kgs.calendar.ui.theme.KgsCalendarTheme
import com.kgs.calendar.ui.theme.LocalCalendarUiTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlin.time.TimeSource
import com.kgs.calendar.ui.res.Res

/**
 * The startup screen for a platform to show while the app itself is still being set up (the desktop
 * window shows it before the calendar's data and logic are ready): the same cover the app then shows
 * until its calendar is ready, so one passes into the other unnoticed.
 */
@Composable
fun KgsStartupScreen(themeMode: AppThemeMode, colorMode: AppColorMode) {
    val dark = when (colorMode) {
        AppColorMode.Auto -> isSystemInDarkTheme()
        AppColorMode.Light -> false
        AppColorMode.Dark -> true
    }
    KgsCalendarTheme(themeMode = themeMode, darkTheme = dark) {
        PlatformSystemBarsEffect(darkPalette = LocalCalendarUiTokens.current.darkPalette)
        StartupCover(ready = false, leave = 0f)
    }
}

/**
 * Covers the app from its first frame until the calendar is loaded and laid out, then gives way to it:
 * the app's icon and name in the middle of the window, in the user's colours, as in the startup screen
 * the desktop window shows before the app is set up. [alwaysShow] puts it up even when the calendar is ready
 * at once, as on the desktop, where it continues the window's own startup screen.
 */
@Composable
internal fun StartupOverlay(ready: Boolean, alwaysShow: Boolean = false, onGone: () -> Unit = {}) {
    var gone by remember { mutableStateOf(ready && !alwaysShow) }
    val currentOnGone by rememberUpdatedState(onGone)
    LaunchedEffect(gone) { if (gone) currentOnGone() }
    if (gone) return
    val currentReady by rememberUpdatedState(ready)
    val leave = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        snapshotFlow { currentReady }.first { it }
        // A few frames for the calendar to settle in its final layout before it shows.
        repeat(3) { withFrameNanos { } }
        leave.animateTo(1f, tween(LeaveDurationMs, easing = MotionStandard))
        gone = true
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(1000f)
            // Nothing underneath takes clicks while the cover is up.
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } },
    ) {
        StartupCover(ready = ready, leave = leave.value)
    }
}

/**
 * Views that are slow the first time they appear (their code loads and warms up, their drawing is
 * prepared), each rendered once shortly after the start, underneath the app where nobody sees them, so
 * that they open smoothly the first time the user opens them. One at a time, with a pause between, while
 * the user is still taking in the calendar. Must be a direct child of the app's root box (it sits below
 * the rest), and starts once [active].
 */
@Composable
internal fun IdleWarmUp(active: Boolean, views: List<@Composable () -> Unit>) {
    var current by remember { mutableStateOf(-1) }
    var round by remember { mutableStateOf(0) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        delay(WarmUpDelayMs)
        // A second round builds each view afresh: building it is what the first visit costs most.
        repeat(WarmUpRounds) { r ->
            round = r
            for (index in views.indices) {
                current = index
                // Long enough for views that build up over a few frames (the year view) to be complete.
                repeat(WarmUpFrames) { withFrameNanos { } }
                current = -1
                delay(WarmUpPauseMs)
            }
        }
    }
    val view = views.getOrNull(current) ?: return
    Box(Modifier.fillMaxSize().zIndex(-1f)) {
        key(round, current) { view() }
        // Whatever reaches down here is not for these views.
        Box(
            Modifier
                .matchParentSize()
                .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } },
        )
    }
}

/** When the first startup screen appeared; a cover that follows another goes on from there. */
private val startupStart by lazy { TimeSource.Monotonic.markNow() }

/**
 * The icon and the name, centred and sized to the window; [leave] (0 to 1) fades the cover out while
 * they grow a little. A quiet progress bar appears under the name only if loading takes a while.
 */
@Composable
private fun StartupCover(ready: Boolean, leave: Float) {
    val start = startupStart
    var slow by remember { mutableStateOf(start.elapsedNow().inWholeMilliseconds >= SlowAfterMs) }
    LaunchedEffect(Unit) {
        delay((SlowAfterMs - start.elapsedNow().inWholeMilliseconds).coerceAtLeast(0L))
        slow = true
    }
    val progressAlpha by animateFloatAsState(if (slow && !ready) 1f else 0f, tween(400), label = "startupProgress")
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - leave }
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        // As large in a small window as in a large one, relative to it, within sensible bounds.
        val logoSize = (minOf(maxWidth, maxHeight) * 0.15f).coerceIn(72.dp, 112.dp)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.graphicsLayer {
                val scale = 1f + 0.05f * MotionEmphasized.transform(leave)
                scaleX = scale
                scaleY = scale
            },
        ) {
            StartupLogo(Modifier.size(logoSize))
            Spacer(Modifier.height(logoSize * 0.22f))
            Text(
                stringResource(Res.string.app_name),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(22.dp))
            LinearProgressIndicator(
                modifier = Modifier
                    .width(120.dp)
                    .height(3.dp)
                    .graphicsLayer { alpha = progressAlpha },
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                gapSize = 0.dp,
            )
        }
    }
}

@Composable
private fun StartupLogo(modifier: Modifier) = com.kgs.calendar.ui.KgsAppLogo(modifier)

private const val LeaveDurationMs = 320
private const val SlowAfterMs = 900L
private const val WarmUpDelayMs = 1_500L
private const val WarmUpPauseMs = 250L
private const val WarmUpFrames = 16
private const val WarmUpRounds = 2
