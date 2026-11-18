package com.kgs.calendar.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
actual fun TimeSidebarLabels(hours: IntRange, hourHeightDp: Float, textColor: Color, modifier: Modifier) {
    val measurer = rememberTextMeasurer(cacheSize = 32)
    val style = remember(textColor) { TextStyle(color = textColor, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
    Canvas(modifier) {
        val rowHeight = hourHeightDp.dp.toPx()
        val baselineOffset = 11.sp.toPx()
        hours.forEachIndexed { index, hour ->
            val layout = measurer.measure("${hour.toString().padStart(2, '0')}:00", style)
            drawText(
                layout,
                topLeft = Offset(size.width / 2f - layout.size.width / 2f, index * rowHeight + baselineOffset - layout.firstBaseline),
            )
        }
    }
}
