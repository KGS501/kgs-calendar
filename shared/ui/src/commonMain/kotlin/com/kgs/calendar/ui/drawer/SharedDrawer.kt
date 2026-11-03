package com.kgs.calendar.ui.drawer

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kgs.calendar.ui.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** The side drawer chrome: scrim, slide, swipe-to-close and the rounded sheet. */
@Composable
fun SharedDrawerSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val drawerProgress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(if (visible) 135 else 115, easing = if (visible) MotionStandard else MotionStandardAccelerate),
        label = "drawerProgress",
    )
    val quietInteraction = remember { MutableInteractionSource() }
    val drawerWidth = 292.dp
    val drawerWidthPx = with(LocalDensity.current) { drawerWidth.toPx() }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    val effectiveProgress = (drawerProgress + dragOffsetPx / drawerWidthPx).coerceIn(0f, 1f)
    LaunchedEffect(visible) {
        if (!visible) delay(150)
        dragOffsetPx = 0f
    }
    Box(Modifier.fillMaxSize()) {
        if (effectiveProgress > 0.01f) {
            Box(
                Modifier.fillMaxSize().alpha(effectiveProgress).background(Color.Black.copy(alpha = 0.58f))
                    .clickable(interactionSource = quietInteraction, indication = null, onClick = onDismiss),
            )
        }
        if (effectiveProgress > 0f) Surface(
            modifier = Modifier
                .width(drawerWidth)
                .fillMaxHeight()
                .offset { IntOffset(((-drawerWidthPx) * (1f - effectiveProgress)).roundToInt(), 0) }
                .pointerInput(visible, drawerWidthPx) {
                    detectHorizontalDragGestures(
                        onDragCancel = { dragOffsetPx = 0f },
                        onDragEnd = { if (dragOffsetPx <= -drawerWidthPx * 0.28f) onDismiss() else dragOffsetPx = 0f },
                    ) { change, dragAmount ->
                        if (dragAmount < 0f || dragOffsetPx < 0f) {
                            change.consume()
                            dragOffsetPx = (dragOffsetPx + dragAmount).coerceIn(-drawerWidthPx, 0f)
                        }
                    }
                },
            shape = RoundedCornerShape(topEnd = 18.dp, bottomEnd = 18.dp),
            color = MaterialTheme.colorScheme.background,
            shadowElevation = 0.dp,
            content = content,
        )
    }
}

/** Logo and app name at the top of the drawer. */
@Composable
fun SharedDrawerHeader(appName: String) {
    Row(
        modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        KgsLogoBurstButton(contentDescription = appName)
        Text(appName, fontSize = 22.sp, lineHeight = 25.sp, fontWeight = FontWeight.ExtraBold, color = WarmInk)
    }
}

@Composable
fun SharedDrawerViewItem(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val background by animateColorAsState(
        targetValue = if (selected) WarmPeach else Color.Transparent,
        animationSpec = tween(MotionMedium, easing = MotionStandard),
        label = "drawerSelection",
    )
    val glyphScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.92f,
        animationSpec = tween(MotionMedium, easing = MotionStandard),
        label = "drawerGlyphScale",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) WarmInk else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(23.dp).scale(glyphScale),
        )
        Text(label, color = WarmInk, fontSize = 15.sp, lineHeight = 18.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
fun SharedDrawerActionRow(label: String, icon: ImageVector, onClick: () -> Unit, count: Int? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(28.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(23.dp))
        Text(label, color = WarmInk, fontSize = 15.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f))
        if (count != null) {
            Text(count.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 17.sp)
        }
    }
}

private data class LogoParticleSpec(val angle: Float, val distance: Float, val radius: Float, val delay: Float, val colorSlot: Int)

/** The app logo; a tap sets off a short burst of particles. */
@Composable
fun KgsLogoBurstButton(contentDescription: String?) {
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
    val particleColors = listOf(WarmBrown, WarmPeach, Color(0xFFFFD166), Color(0xFF7BDFF2))
    val ringColor = WarmBrown
    Box(
        modifier = Modifier
            .size(58.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                burstJob?.cancel()
                burstJob = scope.launch {
                    burstProgress.stop()
                    burstProgress.snapTo(0f)
                    burstProgress.animateTo(targetValue = 1f, animationSpec = tween(920, easing = MotionEmphasized))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.matchParentSize()) {
            if (progress < 1f) {
                val center = Offset(size.width / 2f, size.height / 2f)
                drawCircle(
                    color = ringColor.copy(alpha = (1f - progress) * 0.34f),
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
            imageVector = KgsLogoVector,
            contentDescription = contentDescription,
            modifier = Modifier.size(42.dp).graphicsLayer {
                scaleX = logoScale
                scaleY = logoScale
                rotationZ = logoRotation
            },
            contentScale = ContentScale.Fit,
        )
    }
}

private val KgsLogoPaths = listOf(
    "M1437.71,381.64H1375v65.21a83.72,83.72 0,0 1,-167.44 0V381.64h-415v65.21a83.72,83.72 0,0 1,-167.44 0V381.64H562.29c-102.07,0 -184.82,82.75 -184.82,184.82v875.41c0,102.07 82.75,184.82 184.82,184.82h875.42c102.07,0 184.82,-82.75 184.82,-184.82V566.46c0,-102.07 -82.75,-184.82 -184.82,-184.82ZM821.84,1421.92a51.47,51.47 0,0 1,-51.47 51.46H647.17a51.46,51.46 0,0 1,-51.46 -51.46V700.27a51.46,51.46 0,0 1,51.46 -51.46h123.2a51.47,51.47 0,0 1,51.47 51.46ZM1130.53,1131.43a51.46,51.46 0,0 1,-51.46 51.46H938.4a51.47,51.47 0,0 1,-51.47 -51.46V990.76a51.47,51.47 0,0 1,51.47 -51.46h140.67a51.46,51.46 0,0 1,51.46 51.46ZM1413,1421.92a51.47,51.47 0,0 1,-51.47 51.46H1220.89a51.46,51.46 0,0 1,-51.46 -51.46V1281.25a51.46,51.46 0,0 1,51.46 -51.46h140.67a51.47,51.47 0,0 1,51.47 51.46ZM1413,840.92a51.47,51.47 0,0 1,-51.47 51.46H1220.89a51.46,51.46 0,0 1,-51.46 -51.46V700.27a51.46,51.46 0,0 1,51.46 -51.46h140.67A51.47,51.47 0,0 1,1413 700.27Z",
    "M708.78,265.71c27.56,0 49.9,22.34 49.9,49.9v132.05c0,27.56 -22.34,49.9 -49.9,49.9s-49.91,-22.34 -49.91,-49.9V315.61c0,-27.56 22.35,-49.9 49.91,-49.9Z",
    "M1291.22,265.71c27.56,0 49.91,22.34 49.91,49.9v132.05c0,27.56 -22.35,49.9 -49.91,49.9s-49.9,-22.34 -49.9,-49.9V315.61c0,-27.56 22.34,-49.9 49.9,-49.9Z",
)

/** Same paths and colour as Android's `kgs_logo_vector.xml`. */
val KgsLogoVector: ImageVector by lazy {
    ImageVector.Builder("KgsLogo", 288.dp, 288.dp, 2000f, 2000f).apply {
        KgsLogoPaths.forEach { addPath(PathParser().parsePathString(it).toNodes(), fill = SolidColor(Color(0xFF2F5AEA))) }
    }.build()
}
