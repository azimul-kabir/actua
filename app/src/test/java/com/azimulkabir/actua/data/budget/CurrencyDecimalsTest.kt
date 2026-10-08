package com.azimulkabir.actua.data.budget

import org.junit.Assert.assertEquals
import org.junit.Test

class CurrencyDecimalsTest {
    @Test fun zeroDecimalCurrenciesFollowActualsCurrencyList() {
        assertEquals(0, CurrencyDecimals.of("JPY"))
        assertEquals(0, CurrencyDecimals.of("KRW"))
        assertEquals(0, CurrencyDecimals.of("IRR"))
        assertEquals(0, CurrencyDecimals.of("jpy"))
    }

    @Test fun everyOtherCurrencyAndNoCurrencyHaveTwo() {
        assertEquals(2, CurrencyDecimals.of("USD"))
        assertEquals(2, CurrencyDecimals.of("EUR"))
        assertEquals(2, CurrencyDecimals.of(""))
        assertEquals(2, CurrencyDecimals.of(null))
    }
}
