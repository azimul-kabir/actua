package com.azimulkabir.actua.billing

import android.app.Activity
import kotlinx.coroutines.flow.StateFlow

/** How a [SupportProduct] behaves once bought. */
enum class SupportProductKind {
    /**
     * Non-consumable: owned by the Play account forever, so Play restores it after a reinstall or
     * on another device, and it can only be bought once. Every Supporter price tier is this.
     */
    Permanent,

    /** Auto-renewing subscription: counts while Play reports it active. */
    Subscription,
}

/**
 * The Support Actua products. Each [productId] must match a product created in Play Console;
 * products that Play doesn't return are simply not offered. Declaration order is display order.
 * None of them unlock features: everything in Actua stays free.
 */
enum class SupportProduct(val productId: String, val kind: SupportProductKind) {
    MonthlySupporter("supporter_monthly", SupportProductKind.Subscription),
    /** The original Supporter purchase; existing supporters own this one. */
    SupporterBadge("support_actua", SupportProductKind.Permanent),
    /** The same Supporter badge at higher prices (set per tier in Play Console). */
    SupporterTier2("support_actua_tier2", SupportProductKind.Permanent),
    SupporterTier3("support_actua_tier3", SupportProductKind.Permanent),
    SupporterTier4("support_actua_tier4", SupportProductKind.Permanent),
    ;

    companion object {
        fun fromProductId(productId: String): SupportProduct? = entries.firstOrNull { it.productId == productId }
    }
}

/** A product Play returned details for, with its localized price (e.g. "$4.99" or "৳500.00"). */
data class SupportOption(val product: SupportProduct, val priceLabel: String)

data class SupportPurchaseState(
    /** Products Play returned details for, in [SupportProduct] declaration order. */
    val options: List<SupportOption> = emptyList(),
    /** The product whose purchase sheet is open or whose purchase is being finished. */
    val processingProduct: SupportProduct? = null,
    /** Supporter price tiers Play confirms the account owns. */
    val ownedProducts: Set<SupportProduct> = emptySet(),
    /** True while Play reports an active monthly supporter subscription. */
    val isSubscribed: Boolean = false,
    /** True right after a purchase completes, until the Support page is left. */
    val showThanks: Boolean = false,
    /** True once Play has answered both purchase queries, so [isSupporter] can be trusted. */
    val purchasesRestored: Boolean = false,
) {
    val isAvailable: Boolean get() = options.isNotEmpty()
    val isProcessing: Boolean get() = processingProduct != null
    val isSupporter: Boolean get() = ownedProducts.isNotEmpty() || isSubscribed

    /** Options still worth offering: each drops out once owned, the subscription while it's active. */
    val offeredOptions: List<SupportOption>
        get() = options.filter { option ->
            when (option.product.kind) {
                SupportProductKind.Permanent -> option.product !in ownedProducts
                SupportProductKind.Subscription -> !isSubscribed
            }
        }

    /** Replaces the options for [products] with [loaded], keeping the others, in display order. */
    fun withOptions(products: Set<SupportProduct>, loaded: List<SupportOption>): SupportPurchaseState =
        copy(options = (options.filterNot { it.product in products } + loaded).sortedBy { it.product.ordinal })
}

/**
 * Drives the optional Support Actua purchases: a monthly supporter subscription, a permanent
 * Supporter badge at several prices. The playstore flavor backs this with Google Play Billing;
 * the fdroid flavor reports [SupportPurchaseState.isAvailable] as false so the settings row that
 * uses this stays hidden, since Play Billing can't work outside Play.
 */
interface SupportBillingManager {
    val state: StateFlow<SupportPurchaseState>

    fun start()

    fun stop()

    fun launchPurchase(activity: Activity, product: SupportProduct)

    /** Clears [SupportPurchaseState.showThanks], e.g. when the Support page is left. */
    fun dismissThanks()
}
