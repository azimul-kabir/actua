package com.azimulkabir.actua.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActuaFormTest {
    @get:Rule val compose = createComposeRule()

    @Test fun formRowShowsLabelAndValueAndIsAButton() {
        var clicks = 0
        compose.setContent {
            MaterialTheme {
                ActuaFormCard {
                    ActuaFormRow(
                        icon = Icons.Outlined.CalendarMonth,
                        label = "Date",
                        value = "30 Sep 2026",
                        onClick = { clicks++ },
                    )
                }
            }
        }

        compose.onNodeWithText("Date").assertExists()
        compose.onNodeWithText("30 Sep 2026", useUnmergedTree = true).assertExists()
        compose.onNode(hasRole(Role.Button)).assertHasClickAction().performClick()
        assertEquals(1, clicks)
    }

    @Test fun formRowShowsPlaceholder() {
        compose.setContent {
            MaterialTheme {
                ActuaFormRow(
                    icon = Icons.Outlined.CalendarMonth,
                    label = "Payee",
                    value = "Choose or add a payee",
                    valueIsPlaceholder = true,
                    onClick = {},
                )
            }
        }

        compose.onNodeWithText("Choose or add a payee", useUnmergedTree = true).assertExists()
    }

    @Test fun switchRowTogglesAsASwitch() {
        compose.setContent {
            MaterialTheme {
                var cleared by remember { mutableStateOf(false) }
                ActuaFormRow(
                    icon = Icons.Outlined.CalendarMonth,
                    label = "Cleared",
                    value = null,
                    checked = cleared,
                    onClick = { cleared = !cleared },
                )
            }
        }

        val row = compose.onNode(hasRole(Role.Switch))
        row.assertIsOff()
        row.performClick()
        row.assertIsOn()
    }

    @Test fun disabledRowIsNotClickable() {
        var clicks = 0
        compose.setContent {
            MaterialTheme {
                ActuaFormRow(
                    icon = Icons.Outlined.CalendarMonth,
                    label = "Category",
                    value = "Transfer",
                    caption = "Transfers between budget accounts aren't categorized",
                    enabled = false,
                    onClick = { clicks++ },
                )
            }
        }

        compose.onNodeWithText("Transfers between budget accounts aren't categorized").assertExists()
        compose.onNode(hasClickAction()).assertDoesNotExist()
        assertEquals(0, clicks)
    }

    @Test fun heroAmountIsOneAccessibleButton() {
        var clicks = 0
        compose.setContent {
            MaterialTheme {
                ActuaHeroAmount(
                    amount = "−$12.50",
                    caption = "Expense",
                    contentDescription = "Amount, Expense −$12.50",
                    onClickLabel = "Edit amount",
                    onClick = { clicks++ },
                    supportingText = "Checking → Savings",
                )
            }
        }

        compose.onNode(hasContentDescription("Amount, Expense −$12.50")).assertHasClickAction().performClick()
        compose.onNodeWithText("Checking → Savings").assertExists()
        assertEquals(1, clicks)
    }

    @Test fun groupedItemsRenderEachRow() {
        compose.setContent {
            MaterialTheme {
                androidx.compose.foundation.layout.Column {
                    listOf("Checking", "Savings", "Cash").forEachIndexed { index, name ->
                        ActuaGroupedItem(position = GroupPosition.of(index, 3)) {
                            ActuaFormRow(icon = Icons.Outlined.CalendarMonth, label = name, value = null)
                        }
                    }
                }
            }
        }

        listOf("Checking", "Savings", "Cash").forEach { compose.onNodeWithText(it).assertExists() }
    }

    @Test fun sheetActionsAreButtonsThatRunTheirAction() {
        var edits = 0
        var deletes = 0
        compose.setContent {
            MaterialTheme {
                ActuaSheetContent {
                    ActuaSheetTitle("Rent")
                    ActuaSheetCard {
                        ActuaSheetAction("Edit schedule", icon = Icons.Outlined.CalendarMonth, onClick = { edits++ })
                        ActuaCardDivider()
                        ActuaSheetAction("Delete schedule", destructive = true, onClick = { deletes++ })
                    }
                }
            }
        }

        compose.onNodeWithText("Rent").assertExists()
        compose.onNode(hasText("Edit schedule") and hasRole(Role.Button)).performClick()
        compose.onNode(hasText("Delete schedule") and hasRole(Role.Button)).performClick()

        assertEquals(1, edits)
        assertEquals(1, deletes)
    }

    private fun hasRole(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)
}
