@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.kgs.calendar.ui

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.kgs.calendar.R
import com.kgs.calendar.data.local.entity.CollectionEntity

@Composable
internal fun EditorTopBar(
    title: String,
    onClose: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    saveEnabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .then(LocalSheetHeaderDragModifier.current)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        RoundEditorAction(
            icon = Icons.Default.Close,
            contentDescription = stringResource(R.string.close),
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f),
            contentColor = WarmInk,
            onClick = onClose,
        )
        Text(
            title,
            color = WarmInk,
            fontSize = 15.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier
                .weight(1f),
            textAlign = TextAlign.Center,
        )
        RoundEditorAction(
            icon = Icons.Default.Check,
            contentDescription = stringResource(R.string.save),
            containerColor = if (saveEnabled) WarmBrown else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f),
            contentColor = if (saveEnabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.52f),
            enabled = saveEnabled,
            onClick = onSave,
        )
    }
}

@Composable
internal fun RoundEditorAction(
    icon: ImageVector,
    contentDescription: String,
    containerColor: Color,
    contentColor: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(containerColor)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = contentColor, modifier = Modifier.size(20.dp))
    }
}

@Composable
internal fun TypeChip(text: String, selected: Boolean, onClick: () -> Unit = {}) {
    val shape = RoundedCornerShape(14.dp)
    Surface(
        modifier = Modifier
            .clip(shape)
            .clickable(onClick = onClick),
        shape = shape,
        color = if (selected) WarmPeach else Color.Transparent,
        border = if (selected) null else BorderStroke(1.dp, Color(0xFFD3C3BD)),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            fontSize = 14.sp,
            lineHeight = 17.sp,
            maxLines = 1,
            softWrap = false,
            color = WarmInk,
        )
    }
}

@Composable
internal fun FadedHorizontalScrollRow(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 0.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(8.dp),
    viewportBleed: Dp = 0.dp,
    initialScrollIndex: Int? = null,
    initialScrollKey: Any? = initialScrollIndex,
    content: @Composable RowScope.() -> Unit,
) {
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    LaunchedEffect(initialScrollKey, initialScrollIndex) {
        val index = initialScrollIndex?.takeIf { it > 0 } ?: return@LaunchedEffect
        repeat(4) { withFrameNanos { } }
        val target = with(density) { (132 * index).dp.roundToPx() }
            .coerceIn(0, scrollState.maxValue)
        scrollState.scrollTo(target)
    }
    val showStartFade = scrollState.value > 0
    val showEndFade = scrollState.maxValue > scrollState.value
    Box(
        modifier = modifier
            .fillMaxWidth()
            .horizontalBleed(viewportBleed)
            .horizontalEdgeFade(edgeWidth = 22.dp, fadeStart = showStartFade, fadeEnd = showEndFade),
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(scrollState)
                .padding(contentPadding),
            horizontalArrangement = horizontalArrangement,
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
internal fun EditorSection(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier
            .editorInset()
            .fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
        border = BorderStroke(1.dp, WarmLine.copy(alpha = 0.72f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = EditorSectionHorizontalPadding, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

@Composable
internal fun CalendarChip(title: String, color: Color) {
    Surface(shape = RoundedCornerShape(12.dp), color = WarmPeach.copy(alpha = 0.66f)) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(18.dp).clip(CircleShape).background(color))
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        }
    }
}

@Composable
internal fun CalendarSelectorChip(
    collection: CollectionEntity,
    selected: Boolean,
    hidden: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        modifier = Modifier
            .clip(shape)
            .clickable(onClick = onClick)
            .then(if (hidden) Modifier.dashedBorder(SyncPendingOrange, 12.dp) else Modifier),
        shape = shape,
        color = if (selected) WarmPeach else Color.Transparent,
        border = if (hidden) {
            null
        } else {
            BorderStroke(
                width = 1.dp,
                color = if (selected) WarmBrown.copy(alpha = 0.42f) else WarmLine.copy(alpha = 0.72f),
            )
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Color(collection.color)),
            )
            Text(
                collection.displayName,
                maxLines = 1,
                softWrap = false,
                color = WarmInk,
                fontSize = 14.sp,
                lineHeight = 17.sp,
            )
        }
    }
}

@Composable
internal fun EditorLine(
    icon: ImageVector?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.width(34.dp), contentAlignment = Alignment.Center) {
            icon?.let { Icon(it, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(23.dp)) }
        }
        Box(Modifier.weight(1f)) { content() }
    }
}
