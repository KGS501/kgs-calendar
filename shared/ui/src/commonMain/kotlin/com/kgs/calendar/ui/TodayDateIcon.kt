package com.kgs.calendar.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Toolbar "Today" glyph: a calendar outline holding today's day number. */
@Composable
fun TodayDateIcon(day: Int, modifier: Modifier = Modifier) {
    val iconColor = WarmInk
    Box(
        modifier = modifier.drawBehind {
            val stroke = 1.9.dp.toPx()
            drawRoundRect(
                color = iconColor,
                topLeft = Offset(stroke / 2, stroke / 2),
                size = Size(size.width - stroke, size.height - stroke),
                cornerRadius = CornerRadius(4.5.dp.toPx(), 4.5.dp.toPx()),
                style = Stroke(width = stroke),
            )
            drawLine(
                color = iconColor,
                start = Offset(stroke * 2.2f, size.height * 0.28f),
                end = Offset(size.width - stroke * 2.2f, size.height * 0.28f),
                strokeWidth = stroke,
            )
        },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = day.toString(),
            color = WarmInk,
            fontSize = 11.sp,
            lineHeight = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.offset(y = 2.dp),
        )
    }
}
