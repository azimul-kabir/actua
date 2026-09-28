package com.azimulkabir.actua.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class CreditCardRowDescriptionTest {
    @Test fun describesTheCardInOnePhrase() {
        assertEquals(
            "Visa, balance -$120.00, cycle spend $80.00, Due tomorrow",
            creditCardRowDescription("Visa", "-$120.00", "$80.00", "Due tomorrow", null),
        )
    }

    @Test fun appendsAvailableCreditWhenALimitIsSet() {
        assertEquals(
            "Visa, balance -$120.00, cycle spend $80.00, Due in 9d, available credit $880.00",
            creditCardRowDescription("Visa", "-$120.00", "$80.00", "Due in 9d", "$880.00"),
        )
    }
}
