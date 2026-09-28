package com.azimulkabir.actua.ui.settings

import com.azimulkabir.actua.model.Account
import org.junit.Assert.assertEquals
import org.junit.Test

class CreditCardCandidatesTest {
    private fun account(id: String, name: String, type: String, closed: Boolean = false) =
        Account(name, 0, type, closed = closed, id = id)

    @Test fun creditAccountsComeFirstThenNameOrder() {
        val accounts = listOf(
            account("checking", "Everyday", "Checking"),
            account("visa", "visa", "Credit"),
            account("amex", "Amex", "Credit"),
            account("savings", "Buffer", "Savings"),
            account("configured", "Mastercard", "Credit"),
            account("old", "Old card", "Credit", closed = true),
        )

        assertEquals(
            listOf("amex", "visa", "savings", "checking"),
            creditCardCandidates(accounts, setOf("configured")).map { it.id },
        )
    }

    @Test fun sameNamesKeepAStableIdOrder() {
        val accounts = listOf(account("b", "Card", "Credit"), account("a", "Card", "Credit"))
        assertEquals(listOf("a", "b"), creditCardCandidates(accounts, emptySet()).map { it.id })
    }
}
