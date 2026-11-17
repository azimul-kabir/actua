package com.azimulkabir.actua.ui.settings

import com.azimulkabir.actua.R
import com.azimulkabir.actua.billing.SupportPurchaseState
import org.junit.Assert.assertEquals
import org.junit.Test

class SupporterDescriptionTest {
    @Test fun activatedWebsitePurchaseShowsThanks() {
        assertEquals(R.string.settings_support_thank_you, supporterDescriptionRes(
            SupportPurchaseState(websiteAvailable = true, websiteSupporter = true, purchasesRestored = true)))
    }
    @Test fun unactivatedWebsitePurchaseShowsIntroduction() {
        assertEquals(R.string.settings_support_intro, supporterDescriptionRes(
            SupportPurchaseState(websiteAvailable = true)))
    }
    @Test fun playSubscriberShowsThanks() {
        assertEquals(R.string.settings_support_thank_you, supporterDescriptionRes(
            SupportPurchaseState(isSubscribed = true, purchasesRestored = true)))
    }
}
