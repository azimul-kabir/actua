package com.azimulkabir.actua.ui.settings

import com.azimulkabir.actua.billing.SupportOption
import com.azimulkabir.actua.billing.SupportProduct
import com.azimulkabir.actua.billing.SupportPurchaseState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportPromptTest {

    private val loaded = SupportPurchaseState(
        options = listOf(SupportOption(SupportProduct.SupporterBadge, "$2.99")),
        purchasesRestored = true,
    )

    @Test
    fun asksOnlyOncePlayHasConfirmedOptionsAndPurchases() {
        assertTrue(shouldAskForSupport(loaded))
        assertFalse(shouldAskForSupport(loaded.copy(purchasesRestored = false)))
        assertFalse(shouldAskForSupport(loaded.copy(options = emptyList())))
        // Billing is not available.
        assertFalse(shouldAskForSupport(SupportPurchaseState()))
    }

    @Test
    fun neverAsksASupporter() {
        assertFalse(shouldAskForSupport(loaded.copy(ownedProducts = setOf(SupportProduct.SupporterTier2))))
        assertFalse(shouldAskForSupport(loaded.copy(isSubscribed = true)))
    }
}
