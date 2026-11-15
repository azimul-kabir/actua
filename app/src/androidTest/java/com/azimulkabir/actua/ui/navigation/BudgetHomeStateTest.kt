package com.azimulkabir.actua.ui.navigation

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.data.preferences.FavoritePreferences
import com.azimulkabir.actua.data.preferences.HomePreferences
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BudgetHomeStateTest {
    @get:Rule val compose = createComposeRule()

    @Test fun budget_month_and_category_return_state_survive_recreation() {
        lateinit var state: BudgetSelectionState
        val restoration = StateRestorationTester(compose)
        restoration.setContent { state = rememberBudgetSelectionState() }
        compose.runOnIdle {
            state.budgetMonth.value = "2026-02"
            state.activeBudgetCategory.value = "category-1"
            state.reopenBudgetCategory.value = "category-2"
            state.transactionsReturnCategory.value = "category-3"
            state.editingAutomationCategory.value = "category-4"
        }
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle {
            assertEquals("2026-02", state.budgetMonth.value)
            assertEquals("category-1", state.activeBudgetCategory.value)
            assertEquals("category-2", state.reopenBudgetCategory.value)
            assertEquals("category-3", state.transactionsReturnCategory.value)
            assertEquals("category-4", state.editingAutomationCategory.value)
        }
    }

    @Test fun switching_budgets_reloads_month_start_and_all_favorite_types() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val home = HomePreferences(context)
        val favorites = FavoritePreferences(context)
        val ids = listOf("navigation-state-test-a", "navigation-state-test-b")
        ids.forEachIndexed { index, id ->
            home.setSummaryStartDay(id, 10 + index)
            FavoritePreferences.Type.entries.forEach { type -> favorites.replace(id, type, setOf("$id-${type.name}")) }
        }
        val budget = mutableStateOf(ids.first())
        lateinit var state: HomeBudgetPreferencesState
        try {
            compose.setContent { state = rememberHomeBudgetPreferencesState(home, favorites, budget.value) }
            compose.runOnIdle {
                assertEquals(10, state.homeMonthStart.value.day)
                budget.value = ids.last()
            }
            compose.runOnIdle {
                assertEquals(11, state.homeMonthStart.value.day)
                assertEquals(setOf("${ids.last()}-CATEGORY"), state.favoriteCategoryIds.value)
                assertEquals(setOf("${ids.last()}-ACCOUNT"), state.favoriteAccountIds.value)
                assertEquals(setOf("${ids.last()}-REPORT"), state.favoriteReportIds.value)
            }
        } finally {
            ids.forEach { id ->
                home.setSummaryStartDay(id, 1)
                FavoritePreferences.Type.entries.forEach { type -> favorites.replace(id, type, emptySet()) }
            }
        }
    }
}
