package com.azimulkabir.actua.ui.transactions

import org.junit.Assert.assertEquals
import org.junit.Test

class AmountFieldPresentationTest {
    @Test
    fun activeBlankAmountKeepsCaretSeparateFromPlaceholder() {
        val visibleCursor = amountFieldPresentation("৳", "", active = true, cursor = " │")
        val hiddenCursor = amountFieldPresentation("৳", "", active = true, cursor = "")

        assertEquals("", visibleCursor.value)
        assertEquals("", hiddenCursor.value)
        assertEquals("", visibleCursor.placeholder)
        assertEquals("", hiddenCursor.placeholder)
        assertEquals(true, visibleCursor.showEmptyCaret)
        assertEquals(true, hiddenCursor.showEmptyCaret)
    }

    @Test
    fun inactiveBlankAmountUsesAPlainPlaceholder() {
        val presentation = amountFieldPresentation("৳", "", active = false, cursor = "")

        assertEquals("", presentation.value)
        assertEquals("Amount", presentation.placeholder)
        assertEquals(false, presentation.showEmptyCaret)
    }

    @Test
    fun enteredAmountKeepsCurrencyAndCursorInValue() {
        val presentation = amountFieldPresentation("৳", "12.34", active = true, cursor = " │")

        assertEquals("৳12.34 │", presentation.value)
        assertEquals("Amount", presentation.placeholder)
        assertEquals(false, presentation.showEmptyCaret)
    }
}
