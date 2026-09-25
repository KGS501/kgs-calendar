package com.kgs.calendar.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kgs.calendar.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private data class LogoParticleSpec(
    val angle: Float,
    val distance: Float,
    val radius: Float,
    val delay: Float,
    val colorSlot: Int,
)

@Composable
internal fun KgsLogoBurstButton() {
    val scope = rememberCoroutineScope()
    val burstProgress = remember { Animatable(1f) }
    var burstJob by remember { mutableStateOf<Job?>(null) }
    val particles = remember {
        val random = Random(501)
        List(34) { index ->
            LogoParticleSpec(
                angle = ((index / 34f) * 2f * PI + random.nextFloat() * 0.34f).toFloat(),
                distance = 18f + random.nextFloat() * 23f,
                radius = 1.8f + random.nextFloat() * 2.8f,
                delay = random.nextFloat() * 0.26f,
                colorSlot = index % 4,
            )
        }
    }
    val progress = burstProgress.value
    val pop = sin(progress.toDouble() * PI).toFloat().coerceAtLeast(0f)
    val logoScale = 1f + pop * 0.18f
    val logoRotation = sin(progress.toDouble() * PI * 2.2).toFloat() * 9f * (1f - progress)
    val particleColors = listOf(
        WarmBrown,
        WarmPeach,
        Color(0xFFFFD166),
        Color(0xFF7BDFF2),
    )
    val ringColor = WarmBrown

    Box(
        modifier = Modifier
            .size(58.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                burstJob?.cancel()
                burstJob = scope.launch {
                    burstProgress.stop()
                    burstProgress.snapTo(0f)
                    burstProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(920, easing = MotionEmphasized),
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.matchParentSize()) {
            if (progress < 1f) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val ringAlpha = (1f - progress) * 0.34f
                drawCircle(
                    color = ringColor.copy(alpha = ringAlpha),
                    radius = 15.dp.toPx() + 17.dp.toPx() * progress,
                    center = center,
                    style = Stroke(width = (2.4f * (1f - progress)).coerceAtLeast(0.5f).dp.toPx()),
                )
                particles.forEach { spec ->
                    val localProgress = ((progress - spec.delay) / (1f - spec.delay)).coerceIn(0f, 1f)
                    if (localProgress > 0f) {
                        val eased = 1f - (1f - localProgress) * (1f - localProgress)
                        val alpha = (1f - localProgress) * (1f - localProgress)
                        val driftX = cos(spec.angle.toDouble()).toFloat() * spec.distance * eased
                        val driftY = sin(spec.angle.toDouble()).toFloat() * spec.distance * eased - 6f * progress
                        drawCircle(
                            color = particleColors[spec.colorSlot].copy(alpha = alpha),
                            radius = spec.radius * (1f + 0.45f * (1f - localProgress)),
                            center = Offset(center.x + driftX, center.y + driftY),
                        )
                    }
                }
            }
        }
        Image(
            painter = painterResource(R.drawable.kgs_logo_vector),
            contentDescription = stringResource(R.string.app_name),
            modifier = Modifier
                .size(42.dp)
                .graphicsLayer {
                    scaleX = logoScale
                    scaleY = logoScale
                    rotationZ = logoRotation
                    shadowElevation = 0f
                },
            contentScale = ContentScale.Fit,
        )
    }
}
