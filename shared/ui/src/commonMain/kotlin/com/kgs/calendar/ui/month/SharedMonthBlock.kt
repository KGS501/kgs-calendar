package com.kgs.calendar.ui.month

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kgs.calendar.ui.*

/** The Android month surface. Data, week numbers and occurrence overlays stay with the caller. */
@Composable
fun SharedMonthBlock(
    title: String,
    leadingDays: Int,
    dayCount: Int,
    textScale: Float = 1f,
    weekLabel: (@Composable (Int) -> Unit)? = null,
    dayContent: @Composable (Int) -> Unit,
    rowOverlay: @Composable (Int) -> Unit = {},
) {
    require(leadingDays in 0..6 && dayCount in 28..31)
    Column(Modifier.fillMaxWidth()) {
        Text(
            title, color = WarmBrown, fontSize = 22.sp, lineHeight = 26.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 12.dp, top = 22.dp, bottom = if (weekLabel != null) 2.dp else 8.dp),
        )
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat((leadingDays + dayCount + 6) / 7) { row ->
                Column(Modifier.fillMaxWidth()) {
                    weekLabel?.invoke(row)
                    Box(Modifier.fillMaxWidth().height(108.dp * textScale)) {
                        Row(Modifier.matchParentSize(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            repeat(7) { column ->
                                val number = row * 7 + column - leadingDays + 1
                                Box(Modifier.weight(1f).fillMaxHeight()) {
                                    if (number in 1..dayCount) dayContent(number)
                                }
                            }
                        }
                        rowOverlay(row)
                    }
                }
            }
        }
    }
}

/** Shared cell beneath Android's morph anchor and row-spanning occurrence pills. */
@Composable
fun SharedMonthDayCell(
    dayNumber: Int,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    textScale: Float = 1f,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val shape = RoundedCornerShape(12.dp)
    val color = WarmGrid.copy(alpha = if (MaterialTheme.colorScheme.background.isDark()) 0.92f else 0.96f)
    Column(
        modifier.clip(shape).background(color, shape)
            .then(if (isToday) Modifier.border(1.dp, WarmBrown, shape) else Modifier)
            .clickable(onClick = onClick).padding(horizontal = 3.dp, vertical = 3.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Box(Modifier.align(Alignment.CenterHorizontally).size(20.dp * textScale), contentAlignment = Alignment.Center) {
            Text(
                dayNumber.toString(), color = if (isToday) WarmBrown else WarmInk,
                fontSize = 12.sp, lineHeight = 13.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, softWrap = false,
            )
        }
        content()
    }
}
