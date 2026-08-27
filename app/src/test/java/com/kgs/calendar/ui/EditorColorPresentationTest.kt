package com.kgs.calendar.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class EditorColorPresentationTest {
    @Test
    fun customColorChipDisplaysTheActiveCustomColor() {
        val palette = listOf(0xFF112233.toInt(), 0xFF445566.toInt())
        val custom = 0xFFABCDEF.toInt()

        assertEquals(
            CustomColorChipPresentation(displayedColor = custom, selected = true),
            customColorChipPresentation(selectedColor = custom, palette = palette),
        )
        assertEquals(
            CustomColorChipPresentation(displayedColor = null, selected = false),
            customColorChipPresentation(selectedColor = palette.first(), palette = palette),
        )
    }
}
