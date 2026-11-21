package com.azimulkabir.actua.ui.settings

import com.azimulkabir.actua.R
import com.azimulkabir.actua.billing.SupportPurchaseState
import org.junit.Assert.assertEquals
import org.junit.Test

class SupporterDescriptionTest {
    @Test fun nonSupporterShowsDescription() {
        assertEquals(R.string.settings_support_description, supporterDescriptionRes(
            SupportPurchaseState(purchasesRestored = true)))
    }
    @Test fun playSubscriberShowsThanks() {
        assertEquals(R.string.settings_support_thank_you, supporterDescriptionRes(
            SupportPurchaseState(isSubscribed = true, purchasesRestored = true)))
    }
}
