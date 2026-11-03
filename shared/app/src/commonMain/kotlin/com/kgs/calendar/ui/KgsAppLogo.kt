package com.kgs.calendar.ui

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import com.kgs.calendar.res.Res
import com.kgs.calendar.res.kgs_logo_vector
import org.jetbrains.compose.resources.painterResource

/**
 * The app's icon as on the desktop's app icon: the blue calendar with its inner shapes solid white. In
 * the drawing they are cut out, so without the white behind them they would show whatever is beneath.
 */
@Composable
internal fun KgsAppLogo(modifier: Modifier = Modifier, contentDescription: String? = null) {
    Image(
        painter = painterResource(Res.drawable.kgs_logo_vector),
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        modifier = modifier.drawBehind {
            // Under the shapes only, inside the calendar's body (in the icon's 2000-unit grid).
            val unit = size.minDimension / 2000f
            val left = (size.width - size.minDimension) / 2f
            val top = (size.height - size.minDimension) / 2f
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(left + 560f * unit, top + 620f * unit),
                size = Size(890f * unit, 880f * unit),
                cornerRadius = CornerRadius(40f * unit),
            )
        },
    )
}
