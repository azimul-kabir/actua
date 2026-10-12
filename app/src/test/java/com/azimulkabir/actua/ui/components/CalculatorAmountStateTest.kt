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
        // The number after × is a plain factor, not an amount (#1028): "3" is ×3.
        state.digit(3)
        assertEquals(900, state.finish())
    }

    @Test fun multiplyAndDivideTakePlainDecimalFactors() {
        val state = CalculatorAmountState()
        state.digit(1); state.digit(2); state.digit(0); state.digit(0)
        state.operator(CalculatorAmountState.Operator.MULTIPLY)
        state.digit(1); state.decimalPoint(); state.digit(5)
        assertEquals("12.00 × 1.5", state.display)
        assertEquals("12 × 1.5", state.expressionDisplay)
        assertEquals(1_800, state.cents)
        state.operator(CalculatorAmountState.Operator.DIVIDE)
        state.decimalPoint(); state.digit(5)
        assertEquals("18.00 ÷ 0.5", state.display)
        assertEquals(3_600, state.finish())
    }

    @Test fun divisionRoundsHalfUpToTheMinorUnit() {
        val state = CalculatorAmountState(1_000)
        state.operator(CalculatorAmountState.Operator.DIVIDE)
        state.digit(3)
        assertEquals(333, state.finish())
        val half = CalculatorAmountState(5)
        half.operator(CalculatorAmountState.Operator.DIVIDE)
        half.digit(2)
        assertEquals(3, half.finish())
    }

    @Test fun amountsAfterPlusAndMinusStillFillFromTheCents() {
        val state = CalculatorAmountState(1_000)
        state.operator(CalculatorAmountState.Operator.MULTIPLY)
        state.digit(2)
        state.operator(CalculatorAmountState.Operator.ADD)
        state.digit(5); state.digit(0)
        assertEquals("20.00 + 0.50", state.display)
        assertEquals(2_050, state.finish())
    }

    @Test fun factorBackspaceAndRetypingWork() {
        val state = CalculatorAmountState(1_000)
        state.operator(CalculatorAmountState.Operator.MULTIPLY)
        state.digit(2); state.decimalPoint(); state.digit(5)
        state.backspace(); state.backspace()
        assertEquals("10.00 × 2", state.display)
        state.backspace()
        assertEquals("10.00 ×", state.display)
        assertEquals(1_000, state.cents)
        state.digit(4)
        assertEquals(4_000, state.finish())
    }

    @Test fun factorAcceptsOneDecimalPointAndNoLeadingZeros() {
        val state = CalculatorAmountState(1_000)
        state.operator(CalculatorAmountState.Operator.MULTIPLY)
        state.digit(0); state.digit(0); state.digit(3)
        state.decimalPoint(); state.decimalPoint(); state.digit(5)
        assertEquals("10.00 × 3.5", state.display)
    }

    @Test fun conventionalEntryUsesTheSameFactors() {
        val state = CalculatorAmountState(conventionalAmountEntry = true)
        state.digit(4); state.decimalPoint(); state.digit(5)
        state.operator(CalculatorAmountState.Operator.MULTIPLY)
        state.digit(2)
        assertEquals(900, state.finish())
    }

    @Test fun anOverflowingProductLeavesTheRunningTotal() {
        val state = CalculatorAmountState(Long.MAX_VALUE / 2)
        state.operator(CalculatorAmountState.Operator.MULTIPLY)
        repeat(9) { state.digit(9) }
        assertEquals(Long.MAX_VALUE / 2, state.cents)
    }

    @Test fun equalsKeepsTheResultForFurtherEntry() {
        val state = CalculatorAmountState(1_000)
        state.operator(CalculatorAmountState.Operator.MULTIPLY)
        state.digit(3)
        assertEquals(3_000, state.finish())
        assertEquals("30.00", state.display)
        state.operator(CalculatorAmountState.Operator.SUBTRACT)
        state.digit(5); state.digit(0); state.digit(0)
        assertEquals(2_500, state.finish())
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
