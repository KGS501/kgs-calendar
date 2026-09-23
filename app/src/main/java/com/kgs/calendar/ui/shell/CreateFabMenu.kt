package com.kgs.calendar.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kgs.calendar.R
import kotlin.math.sqrt

@Composable
internal fun CreateFabMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onCreateTask: () -> Unit,
    onCreateEvent: () -> Unit,
) {
    val quietInteraction = remember { MutableInteractionSource() }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val fabSize by animateDpAsState(
        targetValue = if (expanded) 62.dp else 56.dp,
        animationSpec = tween(MotionMedium, easing = MotionEmphasized),
        label = "fabSize",
    )
    val fabCorner by animateDpAsState(
        targetValue = if (expanded) 31.dp else 18.dp,
        animationSpec = tween(MotionMedium, easing = MotionEmphasized),
        label = "fabCorner",
    )
    val fabColor by animateColorAsState(
        targetValue = if (expanded) WarmBrown else accentContainerColor(),
        animationSpec = tween(MotionMedium, easing = MotionStandard),
        label = "fabColor",
    )
    val fabElevation by animateDpAsState(
        targetValue = if (expanded) 16.dp else 12.dp,
        animationSpec = tween(MotionMedium, easing = MotionStandard),
        label = "fabElevation",
    )
    val fabIconRotation by animateFloatAsState(
        targetValue = if (expanded) 45f else 0f,
        animationSpec = tween(MotionMedium, easing = MotionEmphasized),
        label = "fabIconRotation",
    )
    val fabIconScale by animateFloatAsState(
        targetValue = if (expanded) 32f / 34f else 1f,
        animationSpec = tween(MotionMedium, easing = MotionEmphasized),
        label = "fabIconScale",
    )
    val overlayProgress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(210, easing = MotionEmphasized),
        label = "createMenuOverlayWave",
    )
    val overlayColor = MaterialTheme.colorScheme.surface
    Box(Modifier.fillMaxSize()) {
        if (overlayProgress > 0.01f || expanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = quietInteraction,
                        indication = null,
                        onClick = { onExpandedChange(false) },
                    ),
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val center = Offset(size.width - 56.dp.toPx(), size.height - 52.dp.toPx())
                    val maxRadius = sqrt(size.width * size.width + size.height * size.height) * 1.08f
                    drawCircle(
                        color = overlayColor.copy(alpha = 0.54f * overlayProgress),
                        radius = maxRadius * overlayProgress,
                        center = center,
                    )
                    drawCircle(
                        color = overlayColor.copy(alpha = 0.04f * overlayProgress),
                        radius = maxRadius * overlayProgress * 0.74f,
                        center = center,
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(animationSpec = tween(MotionMedium, delayMillis = 40, easing = MotionStandard)) +
                scaleIn(initialScale = 0.9f, animationSpec = tween(MotionMedium, easing = MotionEmphasized)) +
                slideInVertically(animationSpec = tween(MotionMedium, easing = MotionEmphasized)) { it / 3 },
            exit = fadeOut(animationSpec = tween(MotionShort, easing = MotionStandardAccelerate)) +
                scaleOut(targetScale = 0.92f, animationSpec = tween(MotionShort, easing = MotionStandardAccelerate)) +
                slideOutVertically(animationSpec = tween(MotionShort, easing = MotionStandardAccelerate)) { it / 4 },
            modifier = Modifier.align(Alignment.BottomEnd),
        ) {
            Column(
                modifier = Modifier
                    .padding(end = 24.dp, bottom = navBottom + 108.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.End,
            ) {
                CreateMenuButton(Icons.Default.TaskAlt, stringResource(R.string.task)) {
                    onExpandedChange(false)
                    onCreateTask()
                }
                CreateMenuButton(Icons.Default.Event, stringResource(R.string.event)) {
                    onExpandedChange(false)
                    onCreateEvent()
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 28.dp, bottom = navBottom + 28.dp)
                .size(fabSize)
                .shadow(fabElevation, RoundedCornerShape(fabCorner))
                .clip(RoundedCornerShape(fabCorner))
                .background(fabColor)
                .clickable { onExpandedChange(!expanded) },
            contentAlignment = Alignment.Center,
        ) {
            // A plus rotated by 45 degrees is the close glyph. Keeping one vector alive makes the
            // icon, corner radius, size, colour and elevation read as one continuous FAB morph.
            val iconBackground = if (expanded) WarmBrown else accentContainerColor()
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = if (expanded) stringResource(R.string.close) else stringResource(R.string.create),
                tint = if (iconBackground.isDark()) Color.White else Color(0xFF1C1A18),
                modifier = Modifier
                    .size(34.dp)
                    .graphicsLayer {
                        rotationZ = fabIconRotation
                        scaleX = fabIconScale
                        scaleY = fabIconScale
                    },
            )
        }
    }
}

@Composable
private fun CreateMenuButton(icon: ImageVector, text: String, onClick: () -> Unit) {
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(MotionMedium, easing = MotionEmphasized),
        label = "createMenuButtonScale",
    )
    val container = accentContainerColor()
    // The accent container is light in both themes, so text/icon must be a dark glyph to stay
    // legible (WarmInk/WarmBrown turn light in dark mode and disappeared into the fill).
    val onContainer = if (container.isDark()) Color.White else Color(0xFF1C1A18)
    Surface(
        onClick = onClick,
        modifier = Modifier
            .width(178.dp)
            .height(54.dp)
            .scale(scale),
        shape = RoundedCornerShape(27.dp),
        color = container,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(icon, contentDescription = null, tint = onContainer, modifier = Modifier.size(24.dp))
            Text(text, color = onContainer, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1)
        }
    }
}
