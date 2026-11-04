package com.azimulkabir.actua.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PastedAmountTest {
    private fun cents(text: String?, decimalPlaces: Int = 2) = PastedAmount.parseCents(text, decimalPlaces)

    @Test fun plainDecimalAmount() {
        assertEquals(1250L, cents("12.50"))
        assertEquals(1250L, cents("12,50"))
        assertEquals(1200L, cents("12"))
        assertEquals(5L, cents("0.05"))
    }

    @Test fun currencySymbolsCodesAndWordsAreIgnored() {
        assertEquals(123456L, cents("$1,234.56"))
        assertEquals(1250L, cents("USD 12.50"))
        assertEquals(1250L, cents("12.50 €"))
        assertEquals(1250L, cents("Total: 12.50."))
    }

    @Test fun groupingSeparatorsInEitherConvention() {
        assertEquals(123456L, cents("1,234.56"))
        assertEquals(123456L, cents("1.234,56"))
        assertEquals(123456789L, cents("1,234,567.89"))
        assertEquals(123456789L, cents("1.234.567,89"))
        assertEquals(123456700L, cents("1,234,567"))
        assertEquals(123456L, cents("1 234,56"))
        assertEquals(123456L, cents("1 234.56"))
        assertEquals(123450L, cents("1'234.50"))
    }

    @Test fun loneSeparatorWithThreeDigitsIsGrouping() {
        assertEquals(123400L, cents("1,234"))
        assertEquals(123400L, cents("1.234"))
    }

    @Test fun signIsDropped() {
        assertEquals(4500L, cents("-45"))
        assertEquals(4500L, cents("−45"))
        assertEquals(4550L, cents("(45.50)"))
    }

    @Test fun leadingSeparatorAndZeros() {
        assertEquals(50L, cents(".50"))
        assertEquals(7L, cents("0.07"))
        assertEquals(700L, cents("007"))
    }

    @Test fun currenciesWithOtherDecimalPlaces() {
        assertEquals(1234L, cents("1,234", decimalPlaces = 0))
        assertEquals(1234L, cents("1234", decimalPlaces = 0))
        assertNull(cents("12.5", decimalPlaces = 0))
        assertEquals(1234L, cents("1.234", decimalPlaces = 3))
        assertEquals(12500L, cents("12.5", decimalPlaces = 3))
    }

    @Test fun refusesTextWithoutExactlyOneAmount() {
        assertNull(cents(null))
        assertNull(cents(""))
        assertNull(cents("   "))
        assertNull(cents("abc"))
        assertNull(cents("Paid 12 on 3 Nov"))
        assertNull(cents("12 50"))
        assertNull(cents("12.50 and 13.00"))
    }

    @Test fun refusesMalformedOrTooPreciseNumbers() {
        assertNull(cents("12.3456"))
        assertNull(cents("1,2,3"))
        assertNull(cents("1,23.456"))
        assertNull(cents("1.234,5678"))
        assertNull(cents("99999999999999999999"))
    }
}
