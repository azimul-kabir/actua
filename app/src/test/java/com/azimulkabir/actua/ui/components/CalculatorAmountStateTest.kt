package com.azimulkabir.actua.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class CalculatorAmountStateTest {
    @Test fun digitsShiftIntoCentsLikeIos() {
        val state = CalculatorAmountState()
        state.digit(1); state.digit(2); state.digit(0)
        assertEquals("1.20", state.display)
        assertEquals(120, state.cents)
    }

    @Test fun operatorsEvaluateLeftToRight() {
        val state = CalculatorAmountState()
        state.digit(1); state.digit(0); state.digit(0)
        state.operator(CalculatorAmountState.Operator.ADD)
        state.digit(2); state.digit(0); state.digit(0)
        state.operator(CalculatorAmountState.Operator.MULTIPLY)
        state.digit(3); state.digit(0); state.digit(0)
        assertEquals(900, state.finish())
    }

    @Test fun negativeBudgetAmountsAreSupported() {
        val state = CalculatorAmountState(allowsNegative = true)
        state.digit(5); state.digit(0); state.digit(0); state.toggleSign()
        assertEquals(-500, state.finish())
    }

    @Test fun divisionByZeroLeavesRunningTotal() {
        val state = CalculatorAmountState(1250)
        state.operator(CalculatorAmountState.Operator.DIVIDE)
        state.digit(0)
        assertEquals(1250, state.finish())
    }

    @Test fun clearResetsAnExistingEditedAmount() {
        val state = CalculatorAmountState(12_345)
        state.clear()
        state.digit(5); state.digit(0); state.digit(0)
        assertEquals(500, state.finish())
    }

    @Test fun conventionalDigitsEnterWholeUnitsLikeIos() {
        val state = CalculatorAmountState(conventionalAmountEntry = true)
        state.digit(3); state.digit(2); state.digit(4)
        assertEquals("324.00", state.display)
        assertEquals(32_400, state.finish())
    }

    @Test fun conventionalDecimalAcceptsTwoFractionDigits() {
        val state = CalculatorAmountState(conventionalAmountEntry = true)
        state.digit(1); state.digit(2); state.decimalPoint(); state.digit(3); state.digit(4); state.digit(9)
        assertEquals(1_234, state.finish())
    }
}
