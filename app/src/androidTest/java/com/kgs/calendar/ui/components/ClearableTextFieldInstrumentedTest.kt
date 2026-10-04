package com.kgs.calendar.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kgs.calendar.data.settings.AppThemeMode
import com.kgs.calendar.ui.theme.KgsCalendarTheme
import org.junit.Rule
import org.junit.Test

class ClearableTextFieldInstrumentedTest {
    @get:Rule val rule = createComposeRule()

    @Test fun clearingRemovesAllTextAndFocus() {
        rule.setContent {
            var value by remember { mutableStateOf("Event location") }
            KgsCalendarTheme(themeMode = AppThemeMode.KgsBlue, darkTheme = false) {
                ClearableOutlinedTextField(value, { value = it }, modifier = Modifier.testTag("field"))
            }
        }
        val field = rule.onNodeWithTag("field")
        field.performClick().assertIsFocused()
        rule.onNodeWithTag("clear-text-button").performClick()
        field.assertTextEquals("").assertIsNotFocused()
        rule.onAllNodesWithTag("clear-text-button").assertCountEquals(0)
    }

    @Test fun emptyDisabledAndReadOnlyFieldsDoNotOfferClear() {
        rule.setContent {
            KgsCalendarTheme(themeMode = AppThemeMode.KgsBlue, darkTheme = false) {
                Column {
                    ClearableOutlinedTextField("", {})
                    ClearableOutlinedTextField("Disabled", {}, enabled = false)
                    ClearableOutlinedTextField("Date picker", {}, readOnly = true)
                }
            }
        }
        rule.onAllNodesWithTag("clear-text-button").assertCountEquals(0)
    }

    @Test fun existingTrailingControlRemainsAfterClearing() {
        rule.setContent {
            var value by remember { mutableStateOf("Password") }
            KgsCalendarTheme(themeMode = AppThemeMode.KgsBlue, darkTheme = false) {
                ClearableOutlinedTextField(value, { value = it }, trailingIcon = { Text("Visibility") })
            }
        }
        rule.onNodeWithText("Visibility").assertExists()
        rule.onNodeWithTag("clear-text-button").performClick()
        rule.onNodeWithText("Visibility").assertExists()
    }
}
