package com.azimulkabir.actua.ui.navigation

import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Home
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithText
import com.azimulkabir.actua.data.preferences.DisplayPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import java.util.UUID
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

    @Test fun regularStyleKeepsTabSelectionAndUsesNoOverlayClearance() {
        var selected by mutableStateOf(0)
        var regular by mutableStateOf(false)
        composeRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalFloatingTabBar provides !regular) {
                    val clearance = floatingBarClearance()
                    Text("Clearance: ${clearance.value.toInt()}")
                    BottomTabBar(regular = regular, showLabels = false, backdrop = null) {
                        BottomTabBarItem(
                            regular = regular,
                            selected = selected == 0,
                            onClick = { selected = 0 },
                            icon = { Icon(Icons.Outlined.Home, contentDescription = "Home") },
                            label = null,
                        )
                        BottomTabBarItem(
                            regular = regular,
                            selected = selected == 1,
                            onClick = { selected = 1 },
                            icon = { Icon(Icons.Outlined.Add, contentDescription = "Add") },
                            label = null,
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithText("Clearance: 0").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Home").assertIsSelected()
        composeRule.runOnIdle { regular = true }
        composeRule.onNodeWithText("Clearance: 0").assertExists()
        composeRule.onNodeWithContentDescription("Home").assertIsSelected()
        composeRule.onNodeWithContentDescription("Add").assertIsNotSelected().performClick().assertIsSelected()
        composeRule.runOnIdle { regular = false }
        composeRule.onNodeWithContentDescription("Add").assertIsSelected()
    }

    @Test fun regularStylePersistsAndKeepsTheBarVisibleWhileScrolling() {
        val base = composeRule.activity
        val prefix = "regular_bottom_bar_test_${UUID.randomUUID()}"
        val context = object : ContextWrapper(base) {
            override fun getSharedPreferences(name: String, mode: Int) =
                base.getSharedPreferences("${prefix}_$name", mode)
            override fun getApplicationContext() = this
        }
        try {
            val preferences = DisplayPreferences(context)
            val state = TabBarScrollState(preferences)
            assertFalse(state.useRegularBar)
            state.onScroll(-4f)
            assertFalse(state.visible)

            state.updateUseRegularBar(true)
            assertTrue(state.visible)
            val restored = TabBarScrollState(DisplayPreferences(context))
            assertTrue(restored.useRegularBar)
            restored.onScroll(-4f)
            assertTrue(restored.visible)

            restored.updateUseRegularBar(false)
            restored.onScroll(-4f)
            assertFalse(restored.visible)
        } finally {
            base.deleteSharedPreferences("${prefix}_display_preferences")
        }
    }
}
