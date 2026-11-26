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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onNodeWithText
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.preferences.DisplayPreferences
import com.azimulkabir.actua.ui.settings.SettingsScreen
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
        var showLabels by mutableStateOf(true)
        composeRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalFloatingTabBar provides !regular) {
                    val clearance = floatingBarClearance()
                    Text("Clearance: ${clearance.value.toInt()}")
                    BottomTabBar(
                        regular = regular,
                        showLabels = showLabels,
                        backdrop = null,
                        modifier = Modifier.testTag("bottomTabBar"),
                    ) {
                        BottomTabBarItem(
                            regular = regular,
                            selected = selected == 0,
                            onClick = { selected = 0 },
                            icon = { Icon(Icons.Outlined.Home, contentDescription = "Home") },
                            label = if (showLabels) ({ Text("Home") }) else null,
                        )
                        BottomTabBarItem(
                            regular = regular,
                            selected = selected == 1,
                            onClick = { selected = 1 },
                            icon = { Icon(Icons.Outlined.Add, contentDescription = "Add") },
                            label = if (showLabels) ({ Text("Add") }) else null,
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithText("Clearance: 0").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Home").assertIsSelected()
        composeRule.runOnIdle { regular = true }
        composeRule.onNodeWithText("Clearance: 0").assertExists()
        val bar = composeRule.onNodeWithTag("bottomTabBar")
        val labeledHeight = bar.getUnclippedBoundsInRoot().height
        composeRule.runOnIdle { showLabels = false }
        val iconOnlyHeight = bar.getUnclippedBoundsInRoot().height
        assertEquals(8f, (labeledHeight - iconOnlyHeight).value, 1f)
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

            composeRule.setContent {
                MaterialTheme {
                    CompositionLocalProvider(LocalContext provides context) {
                        SettingsScreen(tabBarDisplay = state)
                    }
                }
            }
            composeRule.onNodeWithContentDescription(base.getString(R.string.settings_open_settings)).performClick()
            composeRule.onNodeWithText(base.getString(R.string.settings_page_display)).performScrollTo().performClick()
            val style = composeRule.onNodeWithText(base.getString(R.string.settings_bottom_bar_style))
            style.performScrollTo().performClick()
            composeRule.onNodeWithText(base.getString(R.string.settings_bottom_bar_regular)).performClick()

            composeRule.runOnIdle {
                assertTrue(state.visible)
                val restored = TabBarScrollState(DisplayPreferences(context))
                assertTrue(restored.useRegularBar)
                restored.onScroll(-4f)
                assertTrue(restored.visible)
            }

            style.performScrollTo().performClick()
            composeRule.onNodeWithText(base.getString(R.string.settings_bottom_bar_floating)).performClick()
            composeRule.runOnIdle {
                assertFalse(DisplayPreferences(context).useRegularBottomBar)
                state.onScroll(-4f)
                assertFalse(state.visible)
            }
        } finally {
            base.deleteSharedPreferences("${prefix}_display_preferences")
        }
    }
}
