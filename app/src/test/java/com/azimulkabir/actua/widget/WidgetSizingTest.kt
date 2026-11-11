package com.azimulkabir.actua.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetSizingTest {
    // The dp a launcher reports for a grid of n cells: 40, 110, 180, 250 for one to four.
    private fun cells(n: Int) = 70 * n - 30

    private fun <T> pick(variants: List<WidgetVariant<T>>, cols: Int, rows: Int) =
        WidgetSizing.pick(variants, cells(cols), cells(rows)).layout

    @Test
    fun budgetSnapshotUsesTheSpaceItIsGiven() {
        assertEquals(BudgetLayout.Mini, pick(BudgetVariants, 2, 1))
        assertEquals(BudgetLayout.Mini, pick(BudgetVariants, 3, 1))
        assertEquals(BudgetLayout.Small, pick(BudgetVariants, 2, 2))
        assertEquals(BudgetLayout.Strip, pick(BudgetVariants, 4, 1))
        assertEquals(BudgetLayout.Compact, pick(BudgetVariants, 3, 2))
        assertEquals(BudgetLayout.Compact, pick(BudgetVariants, 4, 2))
        assertEquals(BudgetLayout.Hero, pick(BudgetVariants, 2, 3))
        assertEquals(BudgetLayout.Hero, pick(BudgetVariants, 4, 3))
        assertEquals(BudgetLayout.HeroFull, pick(BudgetVariants, 2, 4))
        assertEquals(BudgetLayout.HeroFull, pick(BudgetVariants, 4, 4))
    }

    @Test
    fun quickTransactionUsesTheSpaceItIsGiven() {
        assertEquals(QuickLayout.Single, pick(QuickVariants, 2, 1))
        assertEquals(QuickLayout.Single, pick(QuickVariants, 3, 1))
        assertEquals(QuickLayout.Single, pick(QuickVariants, 2, 2))
        assertEquals(QuickLayout.Single, pick(QuickVariants, 3, 2))
        assertEquals(QuickLayout.Row, pick(QuickVariants, 4, 1))
        assertEquals(QuickLayout.Row, pick(QuickVariants, 4, 2))
        assertEquals(QuickLayout.Stacked, pick(QuickVariants, 2, 3))
        assertEquals(QuickLayout.Stacked, pick(QuickVariants, 3, 3))
        assertEquals(QuickLayout.Stacked, pick(QuickVariants, 4, 3))
    }

    @Test
    fun listWidgetsDropTheTitleWhenShort() {
        assertEquals(ListLayout.Mini, pick(ListVariants, 2, 1))
        assertEquals(ListLayout.Mini, pick(ListVariants, 4, 1))
        assertEquals(ListLayout.Titled, pick(ListVariants, 2, 2))
        assertEquals(ListLayout.Titled, pick(ListVariants, 4, 2))
        assertEquals(ListLayout.Full, pick(ListVariants, 2, 3))
        assertEquals(ListLayout.Full, pick(ListVariants, 4, 3))
    }

    @Test
    fun aSizeSmallerThanEveryVariantGetsTheSmallest() {
        assertEquals(BudgetLayout.Mini, WidgetSizing.pick(BudgetVariants, 20, 20).layout)
    }

    @Test
    fun anUnknownSizeGetsTheLargestVariant() {
        // Launchers that report no size leave the options unset, which the fallback reads as unbounded.
        assertEquals(BudgetLayout.HeroFull, WidgetSizing.pick(BudgetVariants, Int.MAX_VALUE, Int.MAX_VALUE).layout)
        assertEquals(QuickLayout.Stacked, WidgetSizing.pick(QuickVariants, Int.MAX_VALUE, Int.MAX_VALUE).layout)
    }

    @Test
    fun everyLayoutIsReachable() {
        val sizes = (1..5).flatMap { c -> (1..5).map { r -> cells(c) to cells(r) } }
        fun <T> reached(variants: List<WidgetVariant<T>>) =
            sizes.map { (w, h) -> WidgetSizing.pick(variants, w, h).layout }.toSet()
        assertEquals(BudgetLayout.entries.toSet(), reached(BudgetVariants))
        assertEquals(QuickLayout.entries.toSet(), reached(QuickVariants))
        assertEquals(ListLayout.entries.toSet(), reached(ListVariants))
    }

    @Test
    fun anEightRowListIsTheMaximum() {
        assertTrue(MAX_WIDGET_ROWS > 4)
        assertEquals(8, MAX_WIDGET_ROWS)
    }
}
