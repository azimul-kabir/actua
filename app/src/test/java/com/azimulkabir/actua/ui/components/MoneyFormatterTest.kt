package com.azimulkabir.actua.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class MoneyFormatterTest {
    @Before fun resetCurrency() {
        CurrencyDisplay.code = "BDT"
        CurrencyDisplay.symbolOnly = false
        NumberDisplay.format = "1,234.56"
    }

    @Test fun supportsEuropeanAndSouthAsianGrouping() {
        NumberDisplay.format = "1.234,56"
        assertEquals("৳123.456,78", formatMoneyCents(12345678, false))
        NumberDisplay.format = "1,23,456.78"
        assertEquals("৳1,23,456.78", formatMoneyCents(12345678, false))
    }

    @Test fun supportsSpacesAndNoGrouping() {
        NumberDisplay.format = "1 234,56"
        assertEquals("৳123 456,78", formatMoneyCents(12345678, false))
        NumberDisplay.format = "1234.56"
        assertEquals("৳123456.78", formatMoneyCents(12345678, false))
    }

    @Test fun formatsExactCents() {
        assertEquals("৳1,234.56", formatMoneyCents(123456, hideDecimalPlaces = false))
        assertEquals("−৳1,234.56", formatMoneyCents(-123456, hideDecimalPlaces = false))
        assertEquals("+৳1,234.56", formatMoneyCents(123456, false, showPositiveSign = true))
    }

    @Test fun hidingDecimalsOnlyChangesPresentation() {
        assertEquals("৳1,234", formatMoneyCents(123456, hideDecimalPlaces = true))
        assertEquals("1234.56", centsToInput(123456))
        assertEquals(123456L, parseInputCents("1234.56"))
    }

    @Test fun inputRequiresAtMostExactCents() {
        assertEquals(120L, parseInputCents("1.20"))
        assertNull(parseInputCents("1.234"))
        assertNull(parseInputCents("not money"))
    }

    @Test fun supportsNoCurrency() {
        CurrencyDisplay.code = ""
        assertEquals("", currencyInputPrefix())
        assertEquals("1,234.56", formatMoneyCents(123456, false))
    }

    @Test fun supportsSymbolOnlyCurrency() {
        CurrencyDisplay.code = "USD"
        CurrencyDisplay.symbolOnly = true
        assertEquals("${'$'}", currencyInputPrefix())
        assertEquals("${'$'}1,234.56", formatMoneyCents(123456, false))
    }

    @Test fun amountEntryPrefixFollowsSelectedCurrency() {
        CurrencyDisplay.code = "EUR"
        CurrencyDisplay.symbolOnly = true
        assertEquals("€", currencyInputPrefix())

        CurrencyDisplay.code = "GBP"
        assertEquals("£", currencyInputPrefix())

        CurrencyDisplay.code = "BDT"
        CurrencyDisplay.symbolOnly = false
        assertEquals("৳", currencyInputPrefix())
    }

}
