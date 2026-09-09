package com.azimulkabir.actua.ui.transactions

import org.junit.Assert.assertEquals
import org.junit.Test

class PayeePickerSearchTest {
    private val options = listOf(
        "Transfer: EXIM Savings",
        "Transfer: Cash",
        "EXIM Bank Card Fee",
        "Shop",
        "Shopper Market",
        "Super Shop",
        "The EXIM Cafe",
    )

    @Test
    fun searchCombinesPayeesAndAccountsAlphabetically() {
        assertEquals(
            listOf("EXIM Bank Card Fee", "Transfer: EXIM Savings", "The EXIM Cafe"),
            filterPickerOptions(options, "exim"),
        )
    }

    @Test
    fun everySearchTermReturnsAnAlphabeticalFilteredList() {
        assertEquals(
            listOf("Shop", "Shopper Market", "Super Shop"),
            filterPickerOptions(options, "shop"),
        )
        assertEquals(listOf("Transfer: Cash"), filterPickerOptions(options, "cash"))
    }

    @Test
    fun transferAccountsAreAlphabeticalBeforeSearchStarts() {
        assertEquals(
            listOf("Transfer: Cash", "Transfer: EXIM Savings"),
            alphabetizePickerOptions(options.filter { it.startsWith("Transfer: ") }),
        )
    }
}
