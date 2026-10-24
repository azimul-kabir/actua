package com.azimulkabir.actua.ui.budget

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azimulkabir.actua.model.BudgetProgressState
import com.azimulkabir.actua.model.TargetFunding
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** #1278, #1287: a balance pill shows a target icon only for a category with a target, in the Plan and table views. */
@RunWith(AndroidJUnit4::class)
class BalancePillTargetIconTest {
    @get:Rule val compose = createComposeRule()

    private fun icons() = compose.onAllNodesWithContentDescription("Has a target")

    @Test fun aCategoryWithATargetShowsTheIcon() {
        compose.setContent {
            MaterialTheme { BalancePill(10_000, false, BudgetProgressState.FUNDED, targetFunding = TargetFunding.PLAIN) }
        }
        icons().assertCountEquals(1)
    }

    private fun assertDescribedAs(state: TargetFunding, description: String) {
        compose.setContent {
            MaterialTheme { BalancePill(10_000, false, BudgetProgressState.FUNDED, targetFunding = state) }
        }
        compose.onAllNodesWithContentDescription(description).assertCountEquals(1)
    }

    @Test fun notStartedGoalIsDescribed() = assertDescribedAs(TargetFunding.NOT_STARTED, "Goal not started")
    @Test fun partialGoalIsDescribed() = assertDescribedAs(TargetFunding.PARTIAL, "Goal in progress")
    @Test fun reachedGoalIsDescribed() = assertDescribedAs(TargetFunding.REACHED, "Goal reached")
    @Test fun overfundedGoalIsDescribed() = assertDescribedAs(TargetFunding.OVERFUNDED, "Funded past goal")

    @Test fun aCategoryWithoutATargetShowsNoIcon() {
        compose.setContent {
            MaterialTheme { BalancePill(10_000, false, BudgetProgressState.FUNDED) }
        }
        icons().assertCountEquals(0)
    }

    @Test fun theTableViewShowsTheIconToo() {
        compose.setContent {
            MaterialTheme { BalancePill(10_000, false, BudgetProgressState.FUNDED, targetFunding = TargetFunding.PLAIN, tableAmount = true) }
        }
        icons().assertCountEquals(1)
    }

    @Test fun aTableCategoryWithoutATargetShowsNoIcon() {
        compose.setContent {
            MaterialTheme { BalancePill(10_000, false, BudgetProgressState.FUNDED, tableAmount = true) }
        }
        icons().assertCountEquals(0)
    }
}
