package com.azimulkabir.actua.ui.settings

import com.azimulkabir.actua.billing.SupportProduct
import com.azimulkabir.actua.billing.SupportPurchaseState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GooglePlayOnlyNoteTest {

    @Test
    fun hiddenWhilePlayIsStillLoading() {
        assertFalse(showGooglePlayOnlyNote(SupportPurchaseState()))
    }

    @Test
    fun shownOncePlayCantSellHere() {
        assertTrue(showGooglePlayOnlyNote(SupportPurchaseState(playUnavailable = true)))
    }

    @Test
    fun neverShownToASupporter() {
        assertFalse(showGooglePlayOnlyNote(SupportPurchaseState(playUnavailable = true, isSubscribed = true)))
        assertFalse(
            showGooglePlayOnlyNote(SupportPurchaseState(playUnavailable = true, ownedProducts = setOf(SupportProduct.SupporterBadge))),
        )
    }
}
