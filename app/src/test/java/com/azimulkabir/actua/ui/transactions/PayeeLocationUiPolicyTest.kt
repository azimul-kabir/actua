package com.azimulkabir.actua.ui.transactions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PayeeLocationUiPolicyTest {
    private val ordinaryPayees = setOf("Cafe", "Market")

    @Test
    fun blankPayeeOffersNearbyLookup() {
        assertEquals(
            PayeeLocationInlineAction.Nearby,
            payeeLocationInlineAction("", ordinaryPayees, null, true, true),
        )
    }

    @Test
    fun ordinaryPayeeOffersSaveUntilKnownNearby() {
        assertEquals(
            PayeeLocationInlineAction.SaveLocation,
            payeeLocationInlineAction("Cafe", ordinaryPayees, null, true, true),
        )
        assertNull(
            payeeLocationInlineAction("Cafe", ordinaryPayees, setOf("Cafe"), true, true),
        )
    }

    @Test
    fun customTransferAndUnsupportedPayeesNeverOfferSave() {
        assertNull(payeeLocationInlineAction("New payee", ordinaryPayees, null, true, true))
        assertNull(payeeLocationInlineAction("Transfer: Cash", ordinaryPayees, null, true, true))
        assertNull(payeeLocationInlineAction("Cafe", ordinaryPayees, null, true, false))
    }

    @Test
    fun noLocationFeatureMeansNoInlineAction() {
        assertNull(payeeLocationInlineAction("", ordinaryPayees, null, false, false))
    }
}
