package com.azimulkabir.actua.ui.navigation

import android.database.sqlite.SQLiteDatabase
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.budget.ActiveBudgetStore
import com.azimulkabir.actua.data.budget.BudgetFileManager
import com.azimulkabir.actua.data.preferences.TabBarPreferences
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class BudgetRepositoryNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun switchingBudgetsWaitsForTheReplacementAndShowsItsOwnData() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = BudgetFileManager(context)
        val active = ActiveBudgetStore(context)
        val previous = active.budgetId
        val tabs = TabBarPreferences(context)
        val previousTabs = tabs.layout()
        val first = files.createBudget("First ${UUID.randomUUID()}")
        val second = files.createBudget("Second ${UUID.randomUUID()}")
        val showing = mutableStateOf(true)
        try {
            for ((budget, name) in listOf(first to "First food", second to "Second food")) {
                SQLiteDatabase.openDatabase(files.databaseFile(budget.id).path, null, SQLiteDatabase.OPEN_READWRITE).use {
                    it.execSQL("UPDATE categories SET name = ? WHERE name = 'Food'", arrayOf(name))
                }
            }
            active.budgetId = first.id
            tabs.restoreDefaults()
            compose.setContent { if (showing.value) MaterialTheme { AppNavigation() } }
            compose.waitUntil(15_000) {
                compose.onAllNodesWithText(context.getString(R.string.navigation_loading_budget)).fetchSemanticsNodes().isEmpty() &&
                    compose.onAllNodesWithContentDescription("Budget", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("Budget", useUnmergedTree = true).performClick()
            waitForText("First food")
            repeat(2) { index ->
                val target = if (index == 0) second else first
                compose.onNodeWithContentDescription(context.getString(R.string.budget_display_options)).performClick()
                compose.onNodeWithText(context.getString(R.string.budget_switch_budget)).performClick()
                compose.onNodeWithText(requireNotNull(target.budgetName)).performClick()
                waitForText(if (index == 0) "Second food" else "First food")
                compose.waitUntil(15_000) {
                    compose.onAllNodesWithText(context.getString(R.string.navigation_loading_budget)).fetchSemanticsNodes().isEmpty()
                }
                compose.onNodeWithText(if (index == 0) "First food" else "Second food").assertDoesNotExist()
                assertEquals(target.id, active.budgetId)
            }
        } finally {
            compose.runOnIdle { showing.value = false }
            compose.waitForIdle()
            tabs.save(previousTabs)
            active.budgetId = previous
            files.deleteBudget(first.id)
            files.deleteBudget(second.id)
        }
    }

    private fun waitForText(text: String) {
        compose.waitUntil(15_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
}
