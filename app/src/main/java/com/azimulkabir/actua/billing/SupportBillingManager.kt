package com.azimulkabir.actua.billing

import android.app.Activity
import kotlinx.coroutines.flow.StateFlow

/** Managed one-time (non-consumable) product id, must match the product created in Play Console. */
const val SUPPORT_ACTUA_PRODUCT_ID = "support_actua"

data class SupportPurchaseState(
    val isAvailable: Boolean = false,
    val priceLabel: String = "$2.99",
    val isProcessing: Boolean = false,
    /** True once Play confirms the account owns the (permanent, non-consumable) purchase. */
    val isPurchased: Boolean = false,
)

/**
 * Drives the optional "Support Actua" in-app purchase: a permanent, non-consumable "Actua
 * Supporter" badge. The playstore flavor backs this with Google Play Billing; the fdroid flavor
 * reports [SupportPurchaseState.isAvailable] as false so the settings row that uses this stays
 * hidden, since Play Billing can't work outside Play.
 */
interface SupportBillingManager {
    val state: StateFlow<SupportPurchaseState>

    fun start()

    fun stop()

    fun launchPurchase(activity: Activity)
}
