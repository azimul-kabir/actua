package com.azimulkabir.actua.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomeSpendingChartTest {
    private fun pairs(income: List<Long>, spent: List<Long>) = IncomeSpendingChart.pairs(
        income, spent, width = 300f, height = 100f, minBar = 4f, maxBarWidth = 40f, gap = 2f,
    )

    @Test
    fun bothSeriesShareOneScaleSoTheLargestAmountFillsTheHeight() {
        val pairs = pairs(listOf(1_000, 400), listOf(500, 2_000))
        assertEquals(listOf(50f, 20f), pairs.map { it.income.height })
        assertEquals(listOf(25f, 100f), pairs.map { it.spending.height })
        assertTrue("bars rest on the baseline", pairs.all { it.income.bottom == 100f && it.spending.bottom == 100f })
    }

    @Test
    fun anAmountOfNothingOrLessStillShowsAStub() {
        val pair = pairs(listOf(0), listOf(-300)).single()
        assertEquals(4f, pair.income.height)
        assertEquals(4f, pair.spending.height)
    }

    @Test
    fun aPairSitsCenteredInItsSlotWithTheIncomeBarFirst() {
        val pairs = pairs(listOf(1, 1, 1), listOf(1, 1, 1))
        // Three slots of 100px: a pair takes 78% of its slot (two bars and a gap), each bar below the 40px cap.
        pairs.forEachIndexed { index, pair ->
            assertEquals(38f, pair.income.right - pair.income.left)
            assertEquals(2f, pair.spending.left - pair.income.right)
            assertEquals(50f + 100f * index, (pair.income.left + pair.spending.right) / 2)
        }
    }

    @Test
    fun barsAreCappedInWideSlots() {
        val pair = IncomeSpendingChart.pairs(listOf(1L), listOf(1L), 300f, 50f, 2f, 40f, 2f).single()
        assertEquals(40f, pair.income.right - pair.income.left)
    }

    @Test
    fun narrowSlotsShrinkTheBarsToFit() {
        val pair = IncomeSpendingChart.pairs(List(6) { 1L }, List(6) { 1L }, 60f, 50f, 2f, 40f, 1f).first()
        // Ten pixel slots: the pair stays inside its slot.
        assertTrue(pair.spending.right - pair.income.left <= 10f)
    }

    @Test
    fun nothingToDrawForNoMonths() {
        assertTrue(pairs(emptyList(), emptyList()).isEmpty())
    }
}
