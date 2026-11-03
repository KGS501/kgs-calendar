package com.kgs.calendar.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
actual fun TimeSidebarLabels(hours: IntRange, hourHeightDp: Float, textColor: Color, modifier: Modifier) {
    Canvas(modifier) {
        val rowHeight = hourHeightDp.dp.toPx()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor.toArgb()
            textSize = 10.sp.toPx()
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val baselineOffset = 11.sp.toPx()
        hours.forEachIndexed { index, hour ->
            drawContext.canvas.nativeCanvas.drawText(
                "%02d:00".format(hour),
                size.width / 2f,
                index * rowHeight + baselineOffset,
                paint,
            )
        }
    }
}
