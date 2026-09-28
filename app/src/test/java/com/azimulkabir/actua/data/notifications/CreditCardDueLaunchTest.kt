package com.azimulkabir.actua.data.notifications

import com.azimulkabir.actua.model.Account
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CreditCardDueLaunchTest {
    private val accounts = listOf(
        Account("Everyday", 0, "checking", id = "checking"),
        Account("Visa Platinum", 0, "credit", id = "card1"),
    )

    @Test fun `tapped reminder resolves the card id to its current account name`() {
        assertEquals("Visa Platinum", CreditCardDueLaunch.accountName(accounts, "card1"))
    }

    @Test fun `missing or deleted accounts resolve to nothing`() {
        assertNull(CreditCardDueLaunch.accountName(accounts, "deleted"))
        assertNull(CreditCardDueLaunch.accountName(accounts, null))
        assertNull(CreditCardDueLaunch.accountName(emptyList(), "card1"))
    }
}
