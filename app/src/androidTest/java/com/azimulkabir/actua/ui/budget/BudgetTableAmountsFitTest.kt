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
import com.azimulkabir.actua.ui.components.NumberDisplay
import com.azimulkabir.actua.ui.components.formatMoneyCents
import org.junit.After
import org.junit.Assert.assertEquals
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
    private var previousNumberFormat = NumberDisplay.format

    @Before fun useUsd() {
        previousCurrency = CurrencyDisplay.code
        previousNumberFormat = NumberDisplay.format
        CurrencyDisplay.code = "USD"
    }

    @After fun restoreCurrency() {
        CurrencyDisplay.code = previousCurrency
        NumberDisplay.format = previousNumberFormat
    }

    private fun setTable(groups: List<BudgetGroup>, overview: BudgetOverview, fontScale: Float) {
        setTable(mutableStateOf(groups), mutableStateOf(overview), fontScale)
    }

    private fun setTable(groups: List<BudgetGroup>, overview: State<BudgetOverview>, fontScale: Float) {
        setTable(mutableStateOf(groups), overview, fontScale)
    }

    private fun setTable(groups: State<List<BudgetGroup>>, overview: State<BudgetOverview>, fontScale: Float) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                MaterialTheme {
                    // The narrowest common phone width (and CI's default emulator).
                    Box(Modifier.width(320.dp)) {
                        BudgetScreen(
                            groups = groups.value,
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

    // #1088: category rows share the header's columns, so they must fit (or stack) with it.

    private val bills = BudgetGroup(
        name = "Bills",
        categories = listOf(
            BudgetCategory(name = "Rent", assigned = 1_100, spent = 1_100),
            BudgetCategory(name = "Savings", assigned = 12_339, spent = 0),
            BudgetCategory(name = "Groceries", assigned = 550, spent = 307),
        ),
    )

    @Test fun categoryRowAmountsFitAtDefaultFontScale() {
        setTable(listOf(bills), demoOverview, fontScale = 1f)
        listOf(110_000L, -110_000L, 1_233_900L, 55_000L, -30_700L, 24_300L)
            .forEach { assertShownInFull(formatMoneyCents(it, false)) }
    }

    @Test fun categoryRowAmountsFitAtLargeFontScale() {
        setTable(listOf(bills), demoOverview, fontScale = 1.3f)
        listOf(110_000L, -110_000L, 1_233_900L, 55_000L, -30_700L, 24_300L)
            .forEach { assertShownInFull(formatMoneyCents(it, false)) }
    }

    @Test fun categoryBalanceFitsAfterGrowingPastFirstLayout() {
        val groups = mutableStateOf(listOf(BudgetGroup(name = "Bills",
            categories = listOf(BudgetCategory(name = "Savings", assigned = 0, spent = 0)))))
        setTable(groups, mutableStateOf(demoOverview), fontScale = 1.3f)
        compose.runOnIdle {
            groups.value = listOf(BudgetGroup(name = "Bills",
                categories = listOf(BudgetCategory(name = "Savings", assigned = 98_765, spent = 0))))
        }
        compose.waitForIdle()
        assertShownInFull(formatMoneyCents(9_876_500L, false))
    }

    @Test fun categoryColumnsLineUpWithTheGroupHeaderAtLargeFontScale() {
        setTable(listOf(bills), demoOverview, fontScale = 1.3f)
        // Budgeted total and Rent's Budgeted sit in the same column, right-aligned.
        val headerRight = compose.onAllNodesWithText(formatMoneyCents(1_398_900L, false), useUnmergedTree = true)
            .fetchSemanticsNodes().single().boundsInRoot.right
        val rowRight = compose.onAllNodesWithText(formatMoneyCents(110_000L, false), useUnmergedTree = true)
            .fetchSemanticsNodes().first().boundsInRoot.right
        assertEquals(headerRight, rowRight, 2f)
    }

    @Test fun lakhGroupedAmountsFitAtDefaultFontScale() = assertLakhGroupedAmountsFit(fontScale = 1f)

    @Test fun lakhGroupedAmountsFitAtLargeFontScale() = assertLakhGroupedAmountsFit(fontScale = 1.3f)

    private fun assertLakhGroupedAmountsFit(fontScale: Float) {
        // "$1,00,000.00" is one character longer than "$100,000.00".
        NumberDisplay.format = "1,23,456.78"
        val essentials = BudgetGroup(
            name = "Essentials",
            categories = listOf(
                BudgetCategory(name = "Rent", assigned = 1_100, spent = 1_100),
                BudgetCategory(name = "Groceries", assigned = 100_000, spent = 307),
            ),
        )
        val overview = BudgetOverview(
            toBudgetCents = -9_745_000L,
            budgetedCents = 10_235_000L,
            spentCents = -165_456L,
            availableCents = 11_311_574L,
        )
        setTable(listOf(essentials), overview, fontScale)
        listOf(10_000_000L, -30_700L, 9_969_300L, 10_110_000L, 10_235_000L, 11_311_574L)
            .forEach { assertShownInFull(formatMoneyCents(it, false)) }
    }
}
