package com.azimulkabir.actua.ui.budget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azimulkabir.actua.model.BudgetCategory
import com.azimulkabir.actua.model.BudgetGroup
import com.azimulkabir.actua.model.BudgetOverview
import com.azimulkabir.actua.ui.components.CurrencyDisplay
import com.azimulkabir.actua.ui.components.formatMoneyCents
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * #1090: with "Show balance pills" off, balances in both budget views (and the table overview's
 * Balance) are drawn without a pill fill but in the same place; the To budget chip keeps its fill.
 */
@RunWith(AndroidJUnit4::class)
class BudgetBalancePillsTest {
    @get:Rule
    val compose = createComposeRule()

    private var previousCurrency = CurrencyDisplay.code
    private val showPills = mutableStateOf(true)

    @Before fun useUsd() {
        previousCurrency = CurrencyDisplay.code
        CurrencyDisplay.code = "USD"
    }

    @After fun restoreCurrency() {
        CurrencyDisplay.code = previousCurrency
    }

    private val groups = listOf(
        BudgetGroup(
            name = "Bills",
            categories = listOf(
                BudgetCategory(name = "Rent", assigned = 1_100, spent = 1_000),
                BudgetCategory(name = "Dining", assigned = 50, spent = 120),
            ),
        ),
    )
    private val overview = BudgetOverview(
        toBudgetCents = 4_321_00L,
        budgetedCents = 1_150_00L,
        spentCents = -1_120_00L,
        availableCents = 9_876_54L,
    )
    // Getters, not initializers: these must format after useUsd() sets the currency.
    private val positiveBalance get() = formatMoneyCents(100_00L, false)
    private val negativeBalance get() = formatMoneyCents(-70_00L, false)
    private val overviewBalance get() = formatMoneyCents(9_876_54L, false)
    private val toBudget get() = formatMoneyCents(4_321_00L, false)

    private fun setBudget(view: String) {
        compose.setContent {
            MaterialTheme {
                // Wide enough that each amount has empty row background beside its pill.
                Box(Modifier.width(600.dp)) {
                    BudgetScreen(
                        groups = groups,
                        overview = overview,
                        budgetView = view,
                        showBalancePills = showPills.value,
                        // The overspent banner repeats "−$70.00" on its own tinted background.
                        showBudgetStatusBanners = false,
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun bounds(amount: String): Rect =
        compose.onAllNodesWithText(amount, useUnmergedTree = true).fetchSemanticsNodes().single().boundsInRoot

    /** The color just inside a pill's padding, left of [amount]'s text. */
    private fun insidePill(amount: String): Int = pixelLeftOf(amount, 3)

    /** The row's own background, well left of [amount]'s pill. */
    private fun besidePill(amount: String): Int = pixelLeftOf(amount, 28)

    private fun pixelLeftOf(amount: String, dp: Int): Int {
        val box = bounds(amount)
        val px = with(compose.density) { dp.dp.toPx() }
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        return pixels[(box.left - px).toInt(), box.center.y.toInt()].toArgb()
    }

    private fun assertPillsFollowTheOption(balances: List<String>) {
        val positions = balances.map { bounds(it) }
        balances.forEach { assertNotEquals("$it should sit on a pill", besidePill(it), insidePill(it)) }

        compose.runOnIdle { showPills.value = false }
        compose.waitForIdle()

        balances.forEach { assertEquals("$it should have no pill", besidePill(it), insidePill(it)) }
        assertEquals("amounts should stay aligned", positions, balances.map { bounds(it) })
    }

    @Test fun tableViewDropsBalancePills() {
        setBudget("Table")
        val toBudgetChip = insidePill(toBudget)
        assertPillsFollowTheOption(listOf(positiveBalance, negativeBalance, overviewBalance))
        assertEquals("To budget keeps its chip", toBudgetChip, insidePill(toBudget))
    }

    @Test fun planViewDropsBalancePills() {
        setBudget("Plan")
        assertPillsFollowTheOption(listOf(positiveBalance, negativeBalance))
    }
}
