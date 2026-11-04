package com.kgs.calendar.ui.theme

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

/**
 * Subpixel smoothing for the app's text, as Windows draws its own (ClearType): grayscale smoothing,
 * Compose's default, looks soft at the usual 100 to 150 % display scales.
 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val DesktopPlatformTextStyle: androidx.compose.ui.text.PlatformTextStyle = androidx.compose.ui.text.PlatformTextStyle(
    spanStyle = null,
    paragraphStyle = androidx.compose.ui.text.PlatformParagraphStyle(
        fontRasterizationSettings = androidx.compose.ui.text.FontRasterizationSettings(
            smoothing = androidx.compose.ui.text.FontSmoothing.SubpixelAntiAlias,
            hinting = androidx.compose.ui.text.FontHinting.Slight,
            subpixelPositioning = true,
            autoHintingForced = false,
        ),
    ),
)
