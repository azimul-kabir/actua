package com.azimulkabir.actua.ui.budget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetTableLayoutTest {
    private fun layout(
        budgeted: Float,
        spent: Float,
        balance: Float,
        width: Float = 330f,
        showSpent: Boolean = true,
    ) = tableLayoutFor(budgeted, spent, balance, width, showSpent, gap = 6f, pillPadding = 10f)

    private fun nonNull(layout: TableLayout?): TableLayout {
        assertNotNull(layout)
        return layout!!
    }

    @Test
    fun shortAmountsKeepTheNormalFontSizeAndSizeColumnsToTheirText() {
        val layout = nonNull(layout(budgeted = 55f, spent = 60f, balance = 50f))
        assertEquals(1f, layout.scale, 0f)
        // Text plus a 6dp gap and 2dp of slack.
        assertEquals(63f, layout.budgeted.value, 0.001f)
        assertEquals(68f, layout.spent.value, 0.001f)
        // The balance column also holds the pill's 10dp left padding.
        assertEquals(68f, layout.balance.value, 0.001f)
    }

    @Test
    fun wideAmountsShrinkEveryAmountByOneSharedScaleAndLeaveTheNameItsShare() {
        val layout = nonNull(layout(budgeted = 80f, spent = 75f, balance = 85f))
        assertTrue(layout.scale >= TableLayoutMinScale && layout.scale < 1f)
        val columns = layout.budgeted.value + layout.spent.value + layout.balance.value
        assertEquals(330f * (1f - TableNameMinShare), columns, 0.5f)
    }

    @Test
    fun amountsTooWideForTheScaleFloorStackInstead() {
        assertNull(layout(budgeted = 130f, spent = 130f, balance = 130f))
    }

    @Test
    fun hidingTheSpentColumnGivesItNoWidthAndMoreRoomToTheRest() {
        val withSpent = nonNull(layout(budgeted = 80f, spent = 75f, balance = 85f))
        val withoutSpent = nonNull(layout(budgeted = 80f, spent = 0f, balance = 85f, showSpent = false))
        assertEquals(0f, withoutSpent.spent.value, 0f)
        assertTrue(withoutSpent.scale > withSpent.scale)
    }

    @Test
    fun noAmountsMeansNoLayout() {
        assertNull(layout(budgeted = 0f, spent = 0f, balance = 0f))
    }

    @Test
    fun narrowerScreensShrinkMoreThanWiderOnes() {
        val narrow = nonNull(layout(budgeted = 80f, spent = 75f, balance = 85f, width = 320f))
        val wide = nonNull(layout(budgeted = 80f, spent = 75f, balance = 85f, width = 420f))
        assertTrue(narrow.scale < wide.scale)
        assertEquals(1f, wide.scale, 0f)
    }
}
