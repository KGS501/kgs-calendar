package com.kgs.calendar.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.test.platform.app.InstrumentationRegistry
import com.kgs.calendar.R
import com.kgs.calendar.data.local.entity.AccountEntity
import com.kgs.calendar.data.local.entity.CollectionEntity
import com.kgs.calendar.domain.source.CollectionVisibility
import com.kgs.calendar.ui.theme.KgsCalendarTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsLayoutInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun sidebarKeepsSettingsInViewBelowALongCalendarList() {
        val account = AccountEntity(id = "work", serverUrl = "https://cloud.example.com/", username = "ada", displayName = "Work", lastSyncAtMillis = 1L)
        val collections = (1..30).map { index ->
            CollectionEntity(
                href = "/calendars/ada/c$index/",
                accountId = account.id,
                displayName = "Calendar $index",
                color = 0xFF2563A8.toInt(),
                supportsEvents = true,
                supportsTasks = index % 2 == 0,
                syncToken = null,
                ctag = null,
            )
        }
        var settingsOpened = 0
        composeRule.setContent {
            KgsCalendarTheme {
                CalendarDrawer(
                    visible = true,
                    state = CalendarUiState(accounts = listOf(account), collections = collections),
                    onDismiss = {},
                    onViewSelected = {},
                    onSync = {},
                    onCollectionVisibleInViews = { _, _ -> },
                    onCollectionSettings = {},
                    onGroupSettings = {},
                    onAppSettings = { settingsOpened++ },
                    problems = emptyList(),
                    onProblems = {},
                )
            }
        }

        // Thirty calendars do not fit, yet the settings entry is on screen without scrolling.
        composeRule.onNodeWithText(context.getString(R.string.app_settings)).assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(1, settingsOpened) }
        // "Recently deleted" moved to the settings.
        assertTrue(
            composeRule.onAllNodes(hasText(context.getString(R.string.recently_deleted))).fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun sidebarToggleShowsAndHidesACalendar() {
        val account = AccountEntity(id = "work", serverUrl = "https://cloud.example.com/", username = "ada", displayName = "Work", lastSyncAtMillis = 1L)
        val collection = CollectionEntity(
            href = "/calendars/ada/home/",
            accountId = account.id,
            displayName = "Home",
            color = 0xFF2563A8.toInt(),
            supportsEvents = true,
            supportsTasks = false,
            syncToken = null,
            ctag = null,
        )
        var hidden by mutableStateOf(emptySet<String>())
        composeRule.setContent {
            KgsCalendarTheme {
                CalendarDrawer(
                    visible = true,
                    state = CalendarUiState(accounts = listOf(account), collections = listOf(collection), collectionVisibility = CollectionVisibility(hiddenCollectionHrefs = hidden)),
                    onDismiss = {},
                    onViewSelected = {},
                    onSync = {},
                    onCollectionVisibleInViews = { href, visible -> hidden = if (visible) hidden - href else hidden + href },
                    onCollectionSettings = {},
                    onGroupSettings = {},
                    onAppSettings = {},
                    problems = emptyList(),
                    onProblems = {},
                )
            }
        }

        val toggle = composeRule.onAllNodes(isToggleable()).onFirst()
        toggle.assertIsOn().performClick()
        composeRule.runOnIdle { assertEquals(setOf(collection.href), hidden) }
        toggle.assertIsOff().performClick()
        composeRule.runOnIdle { assertTrue(hidden.isEmpty()) }
    }

    @Test
    fun sliderSavesOnceWhenTheFingerLifts() {
        val saved = mutableListOf<Int>()
        composeRule.setContent {
            KgsCalendarTheme {
                Column(Modifier.testTag("slider")) {
                    SettingsSliderRow(
                        title = "Days",
                        value = 1,
                        range = 1..7,
                        onValueChanged = { saved += it },
                    )
                }
            }
        }

        // The slider is the bottom part of the row.
        composeRule.onNodeWithTag("slider").performTouchInput {
            swipe(start = Offset(left + 60f, bottom - 24f), end = Offset(right - 60f, bottom - 24f), durationMillis = 400)
        }
        composeRule.runOnIdle {
            assertEquals(1, saved.size)
            assertTrue(saved.single() > 1)
        }
    }

    @Test
    fun choicePillsSelectAnOption() {
        var selected by mutableStateOf("Monday")
        composeRule.setContent {
            KgsCalendarTheme {
                SettingsGroup {
                    SettingsChoiceRow(
                        title = "First day of week",
                        options = listOf("Monday", "Sunday", "Saturday"),
                        selected = selected,
                        label = { it },
                        onSelected = { selected = it },
                    )
                }
            }
        }

        composeRule.onNodeWithText("Sunday").performClick()
        composeRule.runOnIdle { assertEquals("Sunday", selected) }
    }
}
