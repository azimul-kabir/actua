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

    @Test fun expressionDisplayKeepsEveryEnteredTermUntilFinish() {
        val state = CalculatorAmountState(conventionalAmountEntry = true)
        state.digit(1); state.digit(0); state.digit(0)
        state.operator(CalculatorAmountState.Operator.ADD)
        state.digit(1); state.digit(0); state.digit(0)
        state.operator(CalculatorAmountState.Operator.ADD)
        state.digit(2); state.digit(0); state.digit(0)

        assertEquals("100 + 100 + 200", state.expressionDisplay)
        assertEquals(40_000, state.finish())
        assertEquals("400", state.expressionDisplay)
    }

    @Test fun backspaceRemovesThePendingOperatorFromTheVisibleExpression() {
        val state = CalculatorAmountState(conventionalAmountEntry = true)
        state.digit(1); state.digit(0); state.digit(0)
        state.operator(CalculatorAmountState.Operator.ADD)
        state.digit(1); state.digit(0); state.digit(0)

        state.backspace()
        assertEquals("100 + 10", state.expressionDisplay)
        state.backspace()
        assertEquals("100 + 1", state.expressionDisplay)
        state.backspace()
        assertEquals("100 +", state.expressionDisplay)
        state.backspace()
        assertEquals("100", state.expressionDisplay)
    }

    @Test fun setCentsReplacesEntryAndPendingExpression() {
        val state = CalculatorAmountState(conventionalAmountEntry = true)
        state.digit(5)
        state.operator(CalculatorAmountState.Operator.ADD)
        state.digit(2)

        state.setCents(12345)
        assertEquals(12345, state.cents)
        assertEquals("123.45", state.display)

    }

    @Test fun zeroDecimalCurrencyDigitsAreWholeUnits() {
        val state = CalculatorAmountState(decimalPlaces = 0)
        state.digit(1); state.digit(0); state.digit(0); state.digit(0)
        assertEquals("1000", state.display)
        assertEquals(1000, state.cents)
        state.operator(CalculatorAmountState.Operator.MULTIPLY)
        state.digit(3)
        assertEquals(3000, state.finish())
        assertEquals("1000 ×", CalculatorAmountState(decimalPlaces = 0).apply {
            digit(1); digit(0); digit(0); digit(0); operator(CalculatorAmountState.Operator.MULTIPLY)
        }.expressionDisplay)
    }

    @Test fun conventionalEntryIgnoresTheDecimalPointWithoutMinorUnits() {
        val state = CalculatorAmountState(conventionalAmountEntry = true, decimalPlaces = 0)
        state.digit(1); state.decimalPoint(); state.digit(2)
        assertEquals(12, state.cents)
        state.backspace()
        assertEquals(1, state.cents)
    }

    @Test fun conventionalEntryTypesAndDeletesFractionDigits() {
        val state = CalculatorAmountState(conventionalAmountEntry = true, decimalPlaces = 2)
        state.digit(1); state.decimalPoint(); state.digit(2); state.digit(5)
        assertEquals(125, state.cents)
        state.digit(9)
        assertEquals(125, state.cents)
        state.backspace()
        assertEquals(120, state.cents)
        state.backspace()
        assertEquals(100, state.cents)
        state.backspace()
        assertEquals(100, state.cents)
        state.backspace()
        assertEquals(0, state.cents)
    }
}
