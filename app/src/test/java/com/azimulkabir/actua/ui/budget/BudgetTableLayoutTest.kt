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
    fun noRoomForAmountsStacks() {
        assertNull(tableLayoutFor(availableWidth = TableNameColumnWidth, showSpent = true, fontScale = 1f))
    }

    @Test
    fun anAmountShrinksToActualisFloor() {
        assertEquals(0.35f, TableAmountCellMinScale, 0f)
    }
}
