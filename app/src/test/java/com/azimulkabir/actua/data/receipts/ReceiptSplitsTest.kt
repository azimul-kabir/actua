package com.azimulkabir.actua.data.receipts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic amounts only. */
class ReceiptSplitsTest {
    private val items = listOf(ReceiptItem("Milk", 250), ReceiptItem("Bread", 300))

    private fun build(
        total: Long = 594,
        tax: Long? = 44,
        items: List<ReceiptItem> = this.items,
        includeTax: Boolean = true,
        includeItems: Boolean = false,
        category: String = "Groceries",
    ) = ReceiptSplits.build(total, tax, items, includeTax, includeItems, category, "Tax")

    @Test fun taxOnlyKeepsTheRestOnTheCategoryAndAddsATaxLineWithoutACategory() {
        val lines = build()

        assertEquals(2, lines.size)
        assertEquals("Groceries", lines[0].category)
        assertEquals(550L, lines[0].amountCents)
        assertEquals("", lines[1].category)
        assertEquals(44L, lines[1].amountCents)
        assertEquals("Tax", lines[1].notes)
        assertEquals(0L, ReceiptSplits.difference(594, lines))
    }

    @Test fun itemsPlusTaxAddUpToTheTotal() {
        val lines = build(includeItems = true)

        assertEquals(listOf(250L, 300L, 44L), lines.map { it.amountCents })
        assertEquals(listOf("Milk", "Bread", "Tax"), lines.map { it.notes })
        assertEquals(0L, ReceiptSplits.difference(594, lines))
    }

    @Test fun anItemsOnlySplitWithoutTheTaxShowsTheTaxAsTheDifference() {
        val lines = build(includeTax = false, includeItems = true)

        assertEquals(2, lines.size)
        assertEquals(44L, ReceiptSplits.difference(594, lines))
    }

    @Test fun amountsThatDoNotAddUpAreLeftVisibleNotHidden() {
        val lines = build(total = 600, includeItems = true)

        assertEquals(listOf(250L, 300L, 44L), lines.map { it.amountCents })
        assertEquals(6L, ReceiptSplits.difference(600, lines))
    }

    @Test fun itemsThatExceedTheTotalGiveANegativeDifference() {
        val lines = build(total = 500, includeItems = true)

        assertEquals(-94L, ReceiptSplits.difference(500, lines))
    }

    @Test fun aTaxThatIsNotSmallerThanTheTotalGivesNoSplit() {
        assertTrue(build(total = 44, tax = 44).isEmpty())
    }

    @Test fun nothingChosenOrNothingFoundGivesNoSplit() {
        assertTrue(build(includeTax = false, includeItems = false).isEmpty())
        assertTrue(build(tax = null).isEmpty())
    }

    @Test fun aSingleLineIsNotASplit() {
        assertTrue(build(tax = null, includeTax = false, includeItems = true, items = items.take(1)).isEmpty())
    }

    @Test fun itemsWithoutAPositiveAmountAreDropped() {
        val lines = build(includeItems = true, items = items + ReceiptItem("Free", 0))

        assertEquals(3, lines.size)
    }

    @Test fun itemsTakeTheTransactionsCategory() {
        val lines = build(includeItems = true, category = "Groceries")

        assertEquals(listOf("Groceries", "Groceries", ""), lines.map { it.category })
    }
}
