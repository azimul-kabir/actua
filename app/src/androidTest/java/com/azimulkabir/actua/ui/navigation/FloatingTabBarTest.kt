package com.azimulkabir.actua.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Home
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FloatingTabBarTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun setBar(showLabels: Boolean, selected: () -> Int, onSelect: (Int) -> Unit) {
        composeRule.setContent {
            MaterialTheme {
                FloatingNavigationBar(showLabels = showLabels) {
                    listOf("Home" to Icons.Outlined.Home, "Add" to Icons.Outlined.Add).forEachIndexed { index, (name, icon) ->
                        FloatingNavigationBarItem(
                            selected = selected() == index,
                            onClick = { onSelect(index) },
                            icon = { Icon(icon, contentDescription = name) },
                            label = if (showLabels) ({ Text(name) }) else null,
                        )
                    }
                }
            }
        }
    }

    @Test fun selectedTabIsExposedAsSelectedAndClickSelectsTheOther() {
        var selected by mutableStateOf(0)
        setBar(showLabels = true, selected = { selected }, onSelect = { selected = it })

        composeRule.onNodeWithContentDescription("Home").assertIsSelected()
        composeRule.onNodeWithContentDescription("Add").assertIsNotSelected().performClick()
        composeRule.runOnIdle { assertEquals(1, selected) }
        composeRule.onNodeWithContentDescription("Add").assertIsSelected()
    }

    @Test fun iconOnlyBarKeepsAccessibilityNames() {
        setBar(showLabels = false, selected = { 0 }, onSelect = {})

        composeRule.onNodeWithContentDescription("Home").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Add").assertIsDisplayed()
    }
}
