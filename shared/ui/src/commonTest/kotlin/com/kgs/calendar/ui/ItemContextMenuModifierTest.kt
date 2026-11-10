package com.kgs.calendar.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.ui.platform.onSecondaryClick
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The right-click modifiers add themselves once and keep the modifiers before them as they are.
 * They once added the card's whole chain a second time, so every timeline card had two drag
 * detectors that called each other's drag off: the card stopped following the finger.
 */
class ItemContextMenuModifierTest {
    private val task = TaskEntity(
        uid = "t",
        collectionHref = "/tasks/",
        resourceHref = "/tasks/t.ics",
        title = "t",
        notes = null,
        dueAtMillis = null,
        startAtMillis = null,
        completedAtMillis = null,
        isCompleted = false,
        status = "NEEDS-ACTION",
        priority = null,
        color = 0,
    )

    /** The card's own element plus the one the right-click modifier adds; three when doubled. */
    private fun Modifier.elementCount(): Int = foldIn(0) { count, _ -> count + 1 }

    @Test
    fun itemContextMenuKeepsTheCardModifiersOnce() {
        val card = Modifier.testTag("card")
        val withMenu = card.itemContextMenu(task)
        assertEquals(2, withMenu.elementCount())
    }

    @Test
    fun onSecondaryClickKeepsTheModifiersBeforeItOnce() {
        val card = Modifier.testTag("card")
        val withClick = card.onSecondaryClick { }
        assertEquals(2, withClick.elementCount())
    }

    @Test
    fun aDisabledSecondaryClickChangesNothing() {
        val card = Modifier.testTag("card")
        assertEquals(card, card.onSecondaryClick(enabled = false) { })
    }
}
