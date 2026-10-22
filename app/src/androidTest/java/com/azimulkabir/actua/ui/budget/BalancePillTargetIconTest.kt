package com.azimulkabir.actua.ui.budget

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azimulkabir.actua.model.BudgetProgressState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** #1278: a balance pill shows a target icon only for a category with a target, and not in the aligned table view. */
@RunWith(AndroidJUnit4::class)
class BalancePillTargetIconTest {
    @get:Rule val compose = createComposeRule()

    private fun icons() = compose.onAllNodesWithContentDescription("Has a target")

    @Test fun aCategoryWithATargetShowsTheIcon() {
        compose.setContent {
            MaterialTheme { BalancePill(10_000, false, BudgetProgressState.FUNDED, hasTarget = true) }
        }
        icons().assertCountEquals(1)
    }

    @Test fun aCategoryWithoutATargetShowsNoIcon() {
        compose.setContent {
            MaterialTheme { BalancePill(10_000, false, BudgetProgressState.FUNDED) }
        }
        icons().assertCountEquals(0)
    }

    @Test fun theTableViewKeepsItsColumnsAndShowsNoIcon() {
        compose.setContent {
            MaterialTheme { BalancePill(10_000, false, BudgetProgressState.FUNDED, hasTarget = true, tableAmount = true) }
        }
        icons().assertCountEquals(0)
    }
}
