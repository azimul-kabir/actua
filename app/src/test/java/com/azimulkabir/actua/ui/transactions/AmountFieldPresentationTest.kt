package com.azimulkabir.actua.ui.transactions

import org.junit.Assert.assertEquals
import org.junit.Test

class AmountFieldPresentationTest {
    @Test
    fun blankAmountRemainsEmptyAcrossCursorBlink() {
        val visibleCursor = amountFieldPresentation("৳", "", active = true, cursor = " │")
        val hiddenCursor = amountFieldPresentation("৳", "", active = true, cursor = "")

        assertEquals("", visibleCursor.value)
        assertEquals("", hiddenCursor.value)
        assertEquals("Amount │", visibleCursor.placeholder)
        assertEquals("Amount", hiddenCursor.placeholder)
    }

    @Test
    fun enteredAmountKeepsCurrencyAndCursorInValue() {
        val presentation = amountFieldPresentation("৳", "12.34", active = true, cursor = " │")

        assertEquals("৳12.34 │", presentation.value)
        assertEquals("Amount", presentation.placeholder)
    }
}
