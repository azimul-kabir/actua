package com.azimulkabir.actua.ui.budget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BudgetTableLayoutTest {
    private fun nonNull(layout: TableLayout?): TableLayout {
        assertNotNull(layout)
        return layout!!
    }

    @Test
    fun amountColumnsAreEqualAndTheNameKeepsActualisWidth() {
        val layout = nonNull(tableLayoutFor(availableWidth = 361f, showSpent = true, fontScale = 1f))
        assertEquals(72f, layout.budgeted.value, 0.001f)
        assertEquals(72f, layout.spent.value, 0.001f)
        assertEquals(72f, layout.balance.value, 0.001f)
        val columns = layout.budgeted.value + layout.spent.value + layout.balance.value
        assertEquals(361f - TableNameColumnWidth, columns, 0.001f)
    }

    @Test
    fun hidingTheSpentColumnSplitsTheRestBetweenTwoColumns() {
        val layout = nonNull(tableLayoutFor(availableWidth = 361f, showSpent = false, fontScale = 1f))
        assertEquals(0f, layout.spent.value, 0f)
        assertEquals(108f, layout.budgeted.value, 0.001f)
        assertEquals(108f, layout.balance.value, 0.001f)
    }

    @Test
    fun columnsDoNotDependOnTheAmounts() {
        // Wide amounts shrink in their own cell; the columns stay put.
        assertEquals(
            tableLayoutFor(availableWidth = 330f, showSpent = true, fontScale = 1f),
            tableLayoutFor(availableWidth = 330f, showSpent = true, fontScale = 1.15f),
        )
    }

    @Test
    fun accessibilityFontSizesStackInsteadOfShrinking() {
        assertNotNull(tableLayoutFor(availableWidth = 330f, showSpent = true, fontScale = TableStackFontScale))
        assertNull(tableLayoutFor(availableWidth = 330f, showSpent = true, fontScale = 1.5f))
    }

    @Test
    fun amountsThatFitAtTheFloorStaySideBySide() {
        // 72 dp columns hold a 190 dp amount at 35% (66.5 dp) plus the 4 dp gap.
        assertNotNull(tableLayoutFor(361f, showSpent = true, fontScale = 1f, budgetedText = 190f, spentText = 190f))
    }

    @Test
    fun anAmountTooWideEvenAtTheFloorStacksInsteadOfClipping() {
        // A 320 dp phone leaves 37 dp columns: a 120 dp amount needs 46 dp at 35%.
        assertNull(tableLayoutFor(256f, showSpent = true, fontScale = 1.3f, budgetedText = 120f))
        assertNull(tableLayoutFor(256f, showSpent = true, fontScale = 1f, spentText = 120f))
        // The balance column also holds the pill's padding.
        assertNotNull(tableLayoutFor(361f, showSpent = true, fontScale = 1f, balanceText = 180f))
        assertNull(tableLayoutFor(361f, showSpent = true, fontScale = 1f, balanceText = 185f))
        // A hidden Spent column doesn't count.
        assertNotNull(tableLayoutFor(361f, showSpent = false, fontScale = 1f, spentText = 1_000f))
    }

    @Test
    fun noRoomForAmountsStacks() {
        assertNull(tableLayoutFor(availableWidth = TableNameColumnWidth, showSpent = true, fontScale = 1f))
    }

    @Test
    fun anAmountShrinksToActualisFloor() {
        assertEquals(0.35f, TableAmountCellMinScale, 0f)
    }
}
