package com.kgs.calendar.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kgs.calendar.res.SharedR as R
import com.kgs.calendar.res.stringResource
import com.kgs.calendar.ui.desktop.CalendarChrome
import com.kgs.calendar.ui.desktop.LocalCalendarChrome
import com.kgs.calendar.ui.layout.currentCalendarWindowLayout
import com.kgs.calendar.ui.theme.LocalAppFontFamily

/**
 * "Good to know": the gestures and shortcuts that aren't obvious at first sight, each with a small
 * looping animation that shows it. Tips that don't apply to this screen are left out.
 */
@Composable
internal fun GoodToKnowSettings() {
    val desktop = LocalCalendarChrome.current == CalendarChrome.Desktop
    val largeScreen = currentCalendarWindowLayout().isLargeScreen
    Tip(R.string.tip_move_title, R.string.tip_move_body) { MoveIllustration() }
    Tip(R.string.tip_status_title, R.string.tip_status_body) { StatusIllustration() }
    Tip(R.string.tip_day_title, R.string.tip_day_body) { DayToggleIllustration() }
    if (desktop || largeScreen) Tip(R.string.tip_sheet_title, R.string.tip_sheet_body) { SheetIllustration() }
    if (desktop) Tip(R.string.tip_seam_title, R.string.tip_seam_body) { SeamIllustration() }
    if (desktop) Tip(R.string.tip_keys_title, R.string.tip_keys_body) { KeysIllustration() }
}

@Composable
private fun Tip(title: Int, body: Int, illustration: @Composable () -> Unit) {
    SettingsGroup {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(settingsControlColor())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center,
            ) {
                // Drawn at about a phone's width, so wide settings columns don't stretch it.
                Box(Modifier.widthIn(max = 380.dp).fillMaxWidth()) { illustration() }
            }
            Text(
                stringResource(title),
                color = WarmInk,
                fontSize = 16.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 6.dp, start = 2.dp),
            )
            Text(
                stringResource(body),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                lineHeight = 19.sp,
                modifier = Modifier.padding(start = 2.dp, bottom = 2.dp),
            )
        }
    }
}

/** A loop of [durationMillis] as a value from 0 to 1. */
@Composable
private fun loopProgress(durationMillis: Int): Float {
    val transition = rememberInfiniteTransition(label = "tipLoop")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis, easing = LinearEasing), RepeatMode.Restart),
        label = "tipLoopProgress",
    )
    return progress
}

/** How far [t] is between [from] and [to], eased in and out, from 0 to 1. */
private fun phase(t: Float, from: Float, to: Float): Float {
    val x = ((t - from) / (to - from)).coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

private val TipCardColor = Color(0xFF1F7A63)
private val TipCardColorAlt = Color(0xFF7A57C6)
private val TipCardColorWarm = Color(0xFFE07A1F)

/** A pointer: a soft dot, with a ring that tightens while it presses. */
private fun DrawScope.pointer(center: Offset, alpha: Float, press: Float, ink: Color) {
    if (alpha <= 0.01f) return
    val radius = 11.dp.toPx()
    drawCircle(ink.copy(alpha = 0.22f * alpha), radius = radius * (1f - 0.12f * press), center = center)
    drawCircle(
        ink.copy(alpha = 0.55f * alpha),
        radius = radius * (1.55f - 0.55f * press),
        center = center,
        style = Stroke(1.5.dp.toPx()),
    )
}

private fun DrawScope.card(
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    color: Color,
    lift: Float = 0f,
    /** Where the title lines begin, e.g. right of a task's circle. */
    textInset: Float = 8.dp.toPx(),
) {
    val radius = CornerRadius(7.dp.toPx(), 7.dp.toPx())
    if (lift > 0f) {
        drawRoundRect(
            Color.Black.copy(alpha = 0.16f * lift),
            topLeft = Offset(left + 2.dp.toPx() * lift, top + 4.dp.toPx() * lift),
            size = Size(width, height),
            cornerRadius = radius,
        )
    }
    drawRoundRect(color, topLeft = Offset(left, top), size = Size(width, height), cornerRadius = radius)
    // Title and time lines on the card, centered in it as one block.
    val textWidth = (width - textInset - 8.dp.toPx()).coerceAtLeast(0f)
    val block = 4.dp.toPx() + 4.dp.toPx() + 3.dp.toPx()
    val blockTop = top + (height - block) / 2f
    drawRoundRect(
        Color.White.copy(alpha = 0.85f),
        topLeft = Offset(left + textInset, blockTop),
        size = Size(textWidth * 0.7f, 4.dp.toPx()),
        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
    )
    drawRoundRect(
        Color.White.copy(alpha = 0.55f),
        topLeft = Offset(left + textInset, blockTop + 8.dp.toPx()),
        size = Size(textWidth * 0.45f, 3.dp.toPx()),
        cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx()),
    )
}

/** Press and hold a card, it lifts, travels to another day and time, and settles. */
@Composable
private fun MoveIllustration() {
    val t = loopProgress(3200)
    val ink = WarmInk
    val grid = WarmLine
    Canvas(Modifier.fillMaxWidth().height(150.dp)) {
        val columns = 3
        val pad = 16.dp.toPx()
        val columnWidth = (size.width - pad * 2) / columns
        for (c in 0..columns) {
            val x = pad + c * columnWidth
            drawLine(grid, Offset(x, pad), Offset(x, size.height - pad), 1.dp.toPx())
        }
        for (r in 0..3) {
            val y = pad + r * (size.height - pad * 2) / 3f
            drawLine(grid.copy(alpha = 0.6f), Offset(pad, y), Offset(size.width - pad, y), 1.dp.toPx())
        }
        // The card fills one cell of the grid, inset a little, and moves from cell to cell.
        val rowHeight = (size.height - pad * 2) / 3f
        val inset = 3.dp.toPx()
        val cardWidth = columnWidth - inset * 2
        val cardHeight = rowHeight - inset * 2
        val start = Offset(pad + inset, pad + inset)
        val end = Offset(pad + columnWidth + inset, pad + rowHeight + inset)
        val appear = phase(t, 0.02f, 0.12f) * (1f - phase(t, 0.9f, 0.98f))
        val press = phase(t, 0.14f, 0.3f) * (1f - phase(t, 0.72f, 0.8f))
        val travel = phase(t, 0.32f, 0.7f)
        val back = phase(t, 0.9f, 1f)
        val position = Offset(
            start.x + (end.x - start.x) * (travel - back * travel),
            start.y + (end.y - start.y) * (travel - back * travel),
        )
        val scale = 1f + 0.05f * press
        val width = cardWidth * scale
        val height = cardHeight * scale
        card(position.x - (width - cardWidth) / 2f, position.y - (height - cardHeight) / 2f, width, height, TipCardColor, lift = press)
        pointer(Offset(position.x + cardWidth * 0.6f, position.y + cardHeight * 0.55f), appear, press, ink)
    }
}

/** A tap completes a task; holding the circle opens the choice of status. */
@Composable
private fun StatusIllustration() {
    val t = loopProgress(3600)
    val ink = WarmInk
    val accent = WarmBrown
    val surface = MaterialTheme.colorScheme.surface
    Canvas(Modifier.fillMaxWidth().height(150.dp)) {
        val cardLeft = size.width * 0.12f
        val cardWidth = size.width * 0.76f
        val cardTop = size.height * 0.5f - 22.dp.toPx()
        val cardHeight = 44.dp.toPx()
        // The task's circle sits at the card's start, its text right of it.
        card(cardLeft, cardTop, cardWidth, cardHeight, TipCardColor, textInset = 42.dp.toPx())
        val circle = Offset(cardLeft + 22.dp.toPx(), cardTop + cardHeight / 2f)
        val ringRadius = 9.dp.toPx()
        val hold = phase(t, 0.1f, 0.4f)
        val menu = phase(t, 0.4f, 0.5f) * (1f - phase(t, 0.82f, 0.9f))
        val choose = phase(t, 0.58f, 0.68f)
        // The hold fills the ring as it would with a long press.
        drawCircle(Color.White.copy(alpha = 0.95f), ringRadius, circle, style = Stroke(2.dp.toPx()))
        if (hold > 0f && menu < 1f) {
            drawArc(
                Color.White,
                startAngle = -90f,
                sweepAngle = 360f * hold,
                useCenter = false,
                topLeft = circle - Offset(ringRadius + 4.dp.toPx(), ringRadius + 4.dp.toPx()),
                size = Size((ringRadius + 4.dp.toPx()) * 2, (ringRadius + 4.dp.toPx()) * 2),
                style = Stroke(2.dp.toPx()),
            )
        }
        // After choosing "in progress" the circle shows its dot.
        if (choose > 0f) drawCircle(Color.White.copy(alpha = choose), ringRadius * 0.38f, circle)
        if (menu > 0f) {
            val menuLeft = circle.x - 8.dp.toPx()
            val menuTop = cardTop - 50.dp.toPx() * menu
            val menuWidth = 4 * 30.dp.toPx() + 10.dp.toPx()
            val menuHeight = 40.dp.toPx()
            drawRoundRect(
                Color.Black.copy(alpha = 0.12f * menu),
                topLeft = Offset(menuLeft + 1.dp.toPx(), menuTop + 3.dp.toPx()),
                size = Size(menuWidth, menuHeight),
                cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx()),
            )
            drawRoundRect(
                surface.copy(alpha = menu),
                topLeft = Offset(menuLeft, menuTop),
                size = Size(menuWidth, menuHeight),
                cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx()),
            )
            for (i in 0 until 4) {
                val c = Offset(menuLeft + 20.dp.toPx() + i * 30.dp.toPx(), menuTop + menuHeight / 2f)
                val selected = i == 1 && choose > 0f
                if (selected) drawCircle(accent.copy(alpha = 0.18f * menu), 13.dp.toPx(), c)
                val color = (if (selected) accent else ink).copy(alpha = menu)
                drawCircle(color, 7.dp.toPx(), c, style = Stroke(1.6.dp.toPx()))
                when (i) {
                    1 -> drawCircle(color, 2.6.dp.toPx(), c)
                    2 -> drawCircle(color, 7.dp.toPx(), c)
                    3 -> {
                        val a = 3.dp.toPx()
                        drawLine(color, c - Offset(a, a), c + Offset(a, a), 1.6.dp.toPx())
                        drawLine(color, c + Offset(-a, a), c + Offset(a, -a), 1.6.dp.toPx())
                    }
                }
            }
        }
        val pointerAt = if (choose > 0f || t > 0.5f) {
            val target = Offset(circle.x - 8.dp.toPx() + 20.dp.toPx() + 30.dp.toPx(), cardTop - 50.dp.toPx() + 20.dp.toPx())
            circle + (target - circle) * phase(t, 0.5f, 0.6f)
        } else {
            circle
        }
        val pointerAlpha = phase(t, 0.02f, 0.1f) * (1f - phase(t, 0.72f, 0.8f))
        val press = hold * (1f - phase(t, 0.42f, 0.48f)) + phase(t, 0.6f, 0.64f) * (1f - phase(t, 0.66f, 0.7f))
        pointer(pointerAt, pointerAlpha, press, ink)
    }
}

/** A tap on a date widens it to the whole view; another tap brings the other days back. */
@Composable
private fun DayToggleIllustration() {
    val t = loopProgress(3600)
    val ink = WarmInk
    val grid = WarmLine
    val measurer = rememberTextMeasurer()
    val style = TextStyle(fontFamily = LocalAppFontFamily.current, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = ink)
    val accent = WarmBrown
    Canvas(Modifier.fillMaxWidth().height(150.dp)) {
        val pad = 14.dp.toPx()
        val width = size.width - pad * 2
        // 0 = three days, 1 = only the middle day.
        val single = phase(t, 0.2f, 0.36f) * (1f - phase(t, 0.66f, 0.82f))
        val sideWidth = width / 3f * (1f - single)
        val middleWidth = width - sideWidth * 2
        val columns = listOf(pad to sideWidth, pad + sideWidth to middleWidth, pad + sideWidth + middleWidth to sideWidth)
        val headerHeight = 26.dp.toPx()
        listOf("Mo 2", "Di 3", "Mi 4").forEachIndexed { index, label ->
            val (left, w) = columns[index]
            val alpha = if (index == 1) 1f else 1f - single
            if (w < 1f) return@forEachIndexed
            if (index == 1) {
                drawRoundRect(
                    accent.copy(alpha = 0.14f * (0.4f + 0.6f * single)),
                    topLeft = Offset(left + w / 2f - 24.dp.toPx(), pad - 2.dp.toPx()),
                    size = Size(48.dp.toPx(), headerHeight - 4.dp.toPx()),
                    cornerRadius = CornerRadius(11.dp.toPx(), 11.dp.toPx()),
                )
            }
            val layout = measurer.measure(label, style.copy(color = ink.copy(alpha = alpha)))
            drawText(layout, topLeft = Offset(left + (w - layout.size.width) / 2f, pad + (headerHeight - 4.dp.toPx() - layout.size.height) / 2f - 2.dp.toPx()))
            drawRoundRect(
                grid.copy(alpha = 0.55f * alpha),
                topLeft = Offset(left + 3.dp.toPx(), pad + headerHeight),
                size = Size((w - 6.dp.toPx()).coerceAtLeast(0f), size.height - pad * 2 - headerHeight),
                cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
            )
            val cardColor = listOf(TipCardColorAlt, TipCardColor, TipCardColorWarm)[index]
            card(
                left + 7.dp.toPx(),
                pad + headerHeight + 14.dp.toPx() + index * 16.dp.toPx(),
                (w - 14.dp.toPx()).coerceAtLeast(0f),
                30.dp.toPx(),
                cardColor.copy(alpha = alpha),
            )
        }
        val labelCenter = Offset(columns[1].first + columns[1].second / 2f, pad + headerHeight / 2f)
        val firstTap = phase(t, 0.1f, 0.15f) * (1f - phase(t, 0.17f, 0.22f))
        val secondTap = phase(t, 0.56f, 0.61f) * (1f - phase(t, 0.63f, 0.68f))
        val alpha = phase(t, 0.02f, 0.08f) * (1f - phase(t, 0.24f, 0.3f)) + phase(t, 0.48f, 0.54f) * (1f - phase(t, 0.7f, 0.76f))
        pointer(labelCenter + Offset(6.dp.toPx(), 8.dp.toPx()), alpha.coerceIn(0f, 1f), maxOf(firstTap, secondTap), ink)
    }
}

/** A sheet's handle moves it up and down, and sideways where the screen is wide. */
@Composable
private fun SheetIllustration() {
    val t = loopProgress(4200)
    val ink = WarmInk
    val grid = WarmLine
    val surface = MaterialTheme.colorScheme.surface
    Canvas(Modifier.fillMaxWidth().height(150.dp)) {
        // The calendar behind: a few event cards.
        val pad = 14.dp.toPx()
        for (i in 0 until 5) {
            drawRoundRect(
                listOf(TipCardColor, TipCardColorAlt, TipCardColorWarm)[i % 3].copy(alpha = 0.45f),
                topLeft = Offset(pad + i * (size.width - pad * 2) / 5f + 3.dp.toPx(), pad + (i % 2) * 22.dp.toPx()),
                size = Size((size.width - pad * 2) / 5f - 6.dp.toPx(), 26.dp.toPx()),
                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
            )
        }
        val sheetWidth = size.width * 0.46f
        val free = size.width - sheetWidth - pad * 2
        // Centered, then to the left, then to the right, and back.
        val position = 0.5f - 0.5f * phase(t, 0.12f, 0.32f) + 1f * phase(t, 0.42f, 0.68f) - 0.5f * phase(t, 0.78f, 0.95f)
        val left = pad + free * position
        val top = size.height * 0.42f
        drawRoundRect(
            Color.Black.copy(alpha = 0.10f),
            topLeft = Offset(left - 2.dp.toPx(), top - 3.dp.toPx()),
            size = Size(sheetWidth + 4.dp.toPx(), size.height),
            cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
        )
        drawRoundRect(surface, topLeft = Offset(left, top), size = Size(sheetWidth, size.height), cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()))
        val handle = Offset(left + sheetWidth / 2f, top + 9.dp.toPx())
        drawLine(grid, handle - Offset(16.dp.toPx(), 0f), handle + Offset(16.dp.toPx(), 0f), 4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        for (r in 0 until 3) {
            drawRoundRect(
                grid.copy(alpha = 0.7f),
                topLeft = Offset(left + 14.dp.toPx(), top + 24.dp.toPx() + r * 14.dp.toPx()),
                size = Size(sheetWidth * (0.7f - r * 0.15f), 6.dp.toPx()),
                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
            )
        }
        val pressing = phase(t, 0.06f, 0.12f) * (1f - phase(t, 0.95f, 0.99f))
        pointer(handle + Offset(0f, 2.dp.toPx()), phase(t, 0.0f, 0.06f) * (1f - phase(t, 0.95f, 1f)), pressing, ink)
    }
}

/** The pill between two columns moves the boundary between them. */
@Composable
private fun SeamIllustration() {
    val t = loopProgress(3600)
    val ink = WarmInk
    val accent = WarmBrown
    val grid = WarmLine
    Canvas(Modifier.fillMaxWidth().height(150.dp)) {
        val pad = 14.dp.toPx()
        val fraction = 0.38f + 0.16f * phase(t, 0.15f, 0.42f) - 0.24f * phase(t, 0.55f, 0.8f) + 0.08f * phase(t, 0.88f, 0.98f)
        val seamX = pad + (size.width - pad * 2) * fraction
        val gap = 8.dp.toPx()
        drawRoundRect(grid.copy(alpha = 0.55f), topLeft = Offset(pad, pad), size = Size(seamX - gap - pad, size.height - pad * 2), cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()))
        drawRoundRect(grid.copy(alpha = 0.35f), topLeft = Offset(seamX + gap, pad), size = Size(size.width - pad - seamX - gap, size.height - pad * 2), cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()))
        for (r in 0 until 3) {
            card(seamX + gap + 8.dp.toPx(), pad + 10.dp.toPx() + r * 36.dp.toPx(), size.width - pad - seamX - gap - 16.dp.toPx(), 28.dp.toPx(), listOf(TipCardColor, TipCardColorAlt, TipCardColorWarm)[r])
        }
        val grab = phase(t, 0.08f, 0.14f) * (1f - phase(t, 0.92f, 0.98f))
        val pillHeight = 30.dp.toPx() + 18.dp.toPx() * grab
        val pillWidth = 4.dp.toPx() + 2.dp.toPx() * grab
        drawRoundRect(
            lerp(ink.copy(alpha = 0.2f), accent, grab),
            topLeft = Offset(seamX - pillWidth / 2f, size.height / 2f - pillHeight / 2f),
            size = Size(pillWidth, pillHeight),
            cornerRadius = CornerRadius(pillWidth / 2f, pillWidth / 2f),
        )
        pointer(Offset(seamX + 4.dp.toPx(), size.height / 2f + 6.dp.toPx()), phase(t, 0.0f, 0.08f) * (1f - phase(t, 0.94f, 1f)), grab, ink)
    }
}

/** Key caps lighting up as their shortcuts are pressed. */
@Composable
private fun KeysIllustration() {
    val t = loopProgress(3600)
    val ink = WarmInk
    val accent = WarmBrown
    val surface = MaterialTheme.colorScheme.surface
    val grid = WarmLine
    val measurer = rememberTextMeasurer()
    val family = LocalAppFontFamily.current
    Canvas(Modifier.fillMaxWidth().height(150.dp)) {
        val keys = listOf("T", "D", "W", "M", "Y", "Ctrl + N")
        val widths = keys.map { if (it.length > 1) 92.dp.toPx() else 40.dp.toPx() }
        val gap = 10.dp.toPx()
        val total = widths.sum() + gap * (keys.size - 1)
        var x = (size.width - total) / 2f
        val y = size.height / 2f - 20.dp.toPx()
        keys.forEachIndexed { index, key ->
            val slot = 1f / keys.size
            val press = phase(t, index * slot + 0.02f, index * slot + 0.07f) * (1f - phase(t, index * slot + 0.09f, index * slot + 0.15f))
            val w = widths[index]
            val sink = 3.dp.toPx() * press
            drawRoundRect(grid, topLeft = Offset(x, y + 4.dp.toPx()), size = Size(w, 40.dp.toPx()), cornerRadius = CornerRadius(9.dp.toPx(), 9.dp.toPx()))
            drawRoundRect(
                lerp(surface, accent.copy(alpha = 0.22f).compositeOverOpaque(surface), press),
                topLeft = Offset(x, y + sink),
                size = Size(w, 40.dp.toPx()),
                cornerRadius = CornerRadius(9.dp.toPx(), 9.dp.toPx()),
            )
            val layout = measurer.measure(
                key,
                TextStyle(fontFamily = family, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = lerp(ink, accent, press)),
            )
            drawText(layout, topLeft = Offset(x + (w - layout.size.width) / 2f, y + sink + (40.dp.toPx() - layout.size.height) / 2f))
            x += w + gap
        }
    }
}

private fun Color.compositeOverOpaque(background: Color): Color = lerp(background, copy(alpha = 1f), alpha)
