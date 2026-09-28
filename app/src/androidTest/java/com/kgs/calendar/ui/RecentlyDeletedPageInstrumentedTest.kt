package com.kgs.calendar.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.kgs.calendar.data.local.entity.EventEntity
import com.kgs.calendar.data.local.entity.TaskEntity
import com.kgs.calendar.data.local.entity.TrashedItemEntity
import com.kgs.calendar.data.settings.AppThemeMode
import com.kgs.calendar.data.settings.TaskColorMode
import com.kgs.calendar.data.trash.TrashedItemPreview
import com.kgs.calendar.domain.model.ComponentType
import com.kgs.calendar.domain.model.SourceType
import com.kgs.calendar.ui.theme.KgsCalendarTheme
import com.kgs.calendar.ui.time.CalendarTimeSnapshot
import com.kgs.calendar.ui.time.LocalCalendarTimeSnapshot
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test

class RecentlyDeletedPageInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val today = LocalDate.now()
    private val zone = ZoneId.systemDefault()

    @Test
    fun undatedTaskLeavesTheDateColumnEmptyAndEventsKeepTheirTextLines() {
        val pastStart = today.minusDays(4).atTime(22, 0).atZone(zone).toInstant().toEpochMilli()
        val task = TaskEntity(
            uid = "groceries",
            collectionHref = "/local/",
            resourceHref = "trashed-item:1",
            title = "Buy groceries",
            notes = null,
            dueAtMillis = null,
            startAtMillis = null,
            completedAtMillis = null,
            isCompleted = false,
            priority = null,
            color = 0xFF1B6B5C.toInt(),
        )
        val event = EventEntity(
            uid = "brunch",
            collectionHref = "/local/",
            resourceHref = "trashed-item:2",
            title = "Team brunch",
            description = null,
            location = "Cafe Central",
            startsAtMillis = pastStart,
            endsAtMillis = pastStart + 60L * 60L * 1000L,
            allDay = false,
            recurrenceRule = null,
            isRecurring = false,
            timezoneId = zone.id,
            color = 0xFF1B6B5C.toInt(),
        )
        val deletedAt = System.currentTimeMillis()
        val trash = TrashUiState(
            entries = listOf(
                TrashedItemPreview(trashedItem(1, ComponentType.Task, task.title, null, deletedAt), task = task),
                TrashedItemPreview(trashedItem(2, ComponentType.Event, event.title, pastStart, deletedAt), event = event),
            ),
        )

        composeRule.setContent {
            CompositionLocalProvider(
                LocalCalendarTimeSnapshot provides CalendarTimeSnapshot(today, LocalTime.NOON),
            ) {
                KgsCalendarTheme(
                    themeMode = AppThemeMode.KgsBlue,
                    darkTheme = false,
                    priorityAnimationsEnabled = false,
                ) {
                    RecentlyDeletedPage(
                        trash = trash,
                        taskColorMode = TaskColorMode.Collection,
                        onItemClick = {},
                        onEmptyTrash = {},
                        onDismissNotice = {},
                        onOpened = {},
                        onClose = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Buy groceries").assertExists()
        composeRule.onAllNodesWithText("None").assertCountEquals(0)
        composeRule.onAllNodesWithText("Date").assertCountEquals(0)
        composeRule.onNodeWithText("Team brunch").assertExists()
        composeRule.onNodeWithText("Cafe Central").assertExists()
    }

    private fun trashedItem(
        id: Long,
        type: ComponentType,
        title: String,
        startMillis: Long?,
        deletedAt: Long,
    ) = TrashedItemEntity(
        id = id,
        componentType = type,
        uid = title,
        collectionHref = "/local/",
        accountId = "local",
        sourceType = SourceType.Local,
        resourceHref = "/local/$id.ics",
        rawIcs = "",
        title = title,
        startMillis = startMillis,
        hasTime = startMillis != null,
        collectionName = "Lokal",
        collectionColor = 0xFF1B6B5C.toInt(),
        deletedAtMillis = deletedAt,
    )
}
