package com.kgs.calendar.desktop

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font

/**
 * Roboto, the Android app's font, so the desktop app reads the same instead of in Windows' font.
 * One variable font covers every weight the app uses, including SemiBold. (SIL OFL, see fonts/.)
 */
val RobotoFontFamily: FontFamily = FontFamily(
    (1..9).map { step ->
        val weight = FontWeight(step * 100)
        Font(
            resource = "fonts/Roboto.ttf",
            weight = weight,
            style = FontStyle.Normal,
            variationSettings = FontVariation.Settings(weight, FontStyle.Normal),
        )
    },
)
