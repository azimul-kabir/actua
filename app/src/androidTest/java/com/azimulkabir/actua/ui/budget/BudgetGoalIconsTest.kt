package com.azimulkabir.actua.ui.budget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azimulkabir.actua.model.BudgetCategory
import com.azimulkabir.actua.model.BudgetGroup
import com.azimulkabir.actua.model.BudgetOverview
import com.azimulkabir.actua.model.BudgetTarget
import com.azimulkabir.actua.ui.components.CurrencyDisplay
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** #1310: "Show goal icons" off removes the target icon in both the table and plan views. */
@RunWith(AndroidJUnit4::class)
class BudgetGoalIconsTest {
    @get:Rule val compose = createComposeRule()

    private var previousCurrency = CurrencyDisplay.code
    private val showIcons = mutableStateOf(true)

    @Before fun useUsd() {
        previousCurrency = CurrencyDisplay.code
        CurrencyDisplay.code = "USD"
    }

    @After fun restoreCurrency() {
        CurrencyDisplay.code = previousCurrency
    }

    private val groups = listOf(
        BudgetGroup(
            name = "Savings",
            categories = listOf(
                BudgetCategory(
                    name = "Holiday", assigned = 0, spent = 0,
                    actualAssignedCents = 0, spentCents = 0, availableCents = 5_000,
                    target = BudgetTarget(BudgetTarget.Type.GOAL, amountCents = 20_000),
                ),
            ),
        ),
    )

    private fun setBudget(view: String) {
        compose.setContent {
            MaterialTheme {
                Box(Modifier.width(600.dp)) {
                    BudgetScreen(
                        groups = groups,
                        overview = BudgetOverview(0L, 0L, 0L, 5_000L),
                        budgetView = view,
                        showGoalIcons = showIcons.value,
                        showBudgetStatusBanners = false,
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun icons() = compose.onAllNodesWithContentDescription("Goal in progress", useUnmergedTree = true)

    @Test fun planViewHidesTheIconAndShowsItAgain() {
        setBudget("Plan")
        icons().assertCountEquals(1)
        compose.runOnIdle { showIcons.value = false }
        compose.waitForIdle()
        icons().assertCountEquals(0)
        compose.runOnIdle { showIcons.value = true }
        compose.waitForIdle()
        icons().assertCountEquals(1)
    }

    @Test fun tableViewHidesTheIconAndShowsItAgain() {
        setBudget("Table")
        icons().assertCountEquals(1)
        compose.runOnIdle { showIcons.value = false }
        compose.waitForIdle()
        icons().assertCountEquals(0)
        compose.runOnIdle { showIcons.value = true }
        compose.waitForIdle()
        icons().assertCountEquals(1)
    }
}
