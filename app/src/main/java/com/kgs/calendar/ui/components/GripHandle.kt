package com.kgs.calendar.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** The drag handle of reorderable rows: six dots in two columns of three. */
internal val GripHandleIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "GripHandle",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            for (centerX in listOf(9f, 15f)) {
                for (centerY in listOf(6f, 12f, 18f)) {
                    moveTo(centerX + DotRadius, centerY)
                    arcToRelative(DotRadius, DotRadius, 0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = -2 * DotRadius, dy1 = 0f)
                    arcToRelative(DotRadius, DotRadius, 0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = 2 * DotRadius, dy1 = 0f)
                    close()
                }
            }
        }
    }.build()
}

private const val DotRadius = 1.9f
