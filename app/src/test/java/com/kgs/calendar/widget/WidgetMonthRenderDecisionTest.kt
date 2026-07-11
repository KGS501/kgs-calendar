package com.kgs.calendar.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetMonthRenderDecisionTest {
    @Test
    fun matchingCompleteSignatureSkipsRemoteViewsBuild() {
        assertFalse(
            shouldBuildMonthRemoteViews("same") { candidate -> candidate == "same" },
        )
    }

    @Test
    fun changedCompleteSignatureBuildsRemoteViews() {
        assertTrue(
            shouldBuildMonthRemoteViews("new") { candidate -> candidate == "old" },
        )
    }

    @Test
    fun skeletonWithoutSignatureAlwaysBuildsRemoteViews() {
        assertTrue(
            shouldBuildMonthRemoteViews(null) { true },
        )
    }

    @Test
    fun multiSignatureCombinesMonthCollectionAndPanelGeometry() {
        val baseline = multiWidgetRenderSignature(
            collectionSignature = "agenda-a",
            monthSignature = "month-a",
            monthPanelHeightDp = 200,
            agendaPanelHeightDp = 300,
        )

        assertTrue(
            baseline == multiWidgetRenderSignature("agenda-a", "month-a", 200, 300),
        )
        assertFalse(
            baseline == multiWidgetRenderSignature("agenda-b", "month-a", 200, 300),
        )
        assertFalse(
            baseline == multiWidgetRenderSignature("agenda-a", "month-b", 200, 300),
        )
        assertFalse(
            baseline == multiWidgetRenderSignature("agenda-a", "month-a", 201, 299),
        )
    }
}
