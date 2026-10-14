package com.azimulkabir.actua.ui.budget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azimulkabir.actua.model.BudgetCategory
import com.azimulkabir.actua.model.BudgetGroup
import com.azimulkabir.actua.model.BudgetOverview
import com.azimulkabir.actua.ui.components.CurrencyDisplay
import com.azimulkabir.actua.ui.components.formatMoneyCents
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * #1082: the table view's overview card and group header totals must show every amount in full
 * on a narrow phone at a large font scale, not clip the Balance pill to "$13,665.".
 */
@RunWith(AndroidJUnit4::class)
class BudgetTableAmountsFitTest {
    @get:Rule
    val compose = createComposeRule()

    private var previousCurrency = CurrencyDisplay.code

    @Before fun useUsd() {
        previousCurrency = CurrencyDisplay.code
        CurrencyDisplay.code = "USD"
    }

    @After fun restoreCurrency() {
        CurrencyDisplay.code = previousCurrency
    }

    private fun setTable(groups: List<BudgetGroup>, overview: BudgetOverview, fontScale: Float) {
        val state = mutableStateOf(overview)
        setTable(groups, state, fontScale)
    }

    private fun setTable(groups: List<BudgetGroup>, overview: State<BudgetOverview>, fontScale: Float) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                MaterialTheme {
                    Box(Modifier.width(360.dp)) {
                        BudgetScreen(
                            groups = groups,
                            overview = overview.value,
                            budgetView = "Table",
                            showSpent = true,
                            showGroupTotals = true,
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun assertShownInFull(amount: String) {
        val nodes = compose.onAllNodesWithText(amount, useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("$amount is not shown", nodes.isNotEmpty())
        nodes.forEach { node ->
            val layouts = mutableListOf<TextLayoutResult>()
            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
            layouts.forEach { assertFalse("$amount is clipped", it.hasVisualOverflow) }
        }
    }

    private val demoOverview = BudgetOverview(
        toBudgetCents = 0L,
        budgetedCents = 290_000L,
        spentCents = -165_456L,
        availableCents = 1_366_500L,
    )

    @Test fun overviewAmountsFitAtDefaultFontScale() {
        setTable(emptyList(), demoOverview, fontScale = 1f)
        listOf(290_000L, -165_456L, 1_366_500L).forEach { assertShownInFull(formatMoneyCents(it, false)) }
    }

    @Test fun overviewAmountsFitAtLargeFontScale() {
        setTable(emptyList(), demoOverview, fontScale = 1.3f)
        listOf(290_000L, -165_456L, 1_366_500L).forEach { assertShownInFull(formatMoneyCents(it, false)) }
    }

    @Test fun overviewBalanceFitsAfterGrowingPastFirstLayout() {
        // The pill is first laid out for a short balance, then the real (longer) one arrives.
        val overview = mutableStateOf(demoOverview.copy(availableCents = 0L))
        setTable(emptyList(), overview, fontScale = 1f)
        compose.runOnIdle { overview.value = demoOverview.copy(availableCents = 1_366_574L) }
        compose.waitForIdle()
        assertShownInFull(formatMoneyCents(1_366_574L, false))
    }

    @Test fun groupHeaderTotalsFitAtLargeFontScale() {
        // Two categories so the header totals differ from every category row's amounts.
        val categories = listOf(
            BudgetCategory(name = "Rent", assigned = 600_000, spent = 100),
            BudgetCategory(name = "Utilities", assigned = 634_567, spent = 887_554),
        )
        setTable(listOf(BudgetGroup(name = "Bills", categories = categories)), demoOverview, fontScale = 1.3f)
        assertShownInFull(formatMoneyCents(categories.sumOf { it.assignedCents }, false))
        assertShownInFull(formatMoneyCents(-categories.sumOf { it.spentCents }, false))
        assertShownInFull(formatMoneyCents(categories.sumOf { it.balanceCents }, false))
    }
}
