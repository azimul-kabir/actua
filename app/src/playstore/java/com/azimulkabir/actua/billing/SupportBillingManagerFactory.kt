package com.azimulkabir.actua.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "SupportBilling"

/** Product details plus, for the subscription, the base plan's offer token needed to launch it. */
private class LoadedProduct(val details: ProductDetails, val offerToken: String?)

/**
 * Backs [SupportBillingManager] with Google Play Billing.
 *
 * - Every Supporter price tier is non-consumable: acknowledged once, owned by the
 *   Play account forever and restored on every [start], so a reinstall or a new device keeps the
 *   badge. They are never consumed: Play can't list a consumed purchase, so it couldn't be restored.
 * - The monthly subscription is acknowledged once and counts while Play lists it as purchased.
 */
private class PlayStoreSupportBillingManager(context: Context) : SupportBillingManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(SupportPurchaseState())
    override val state: StateFlow<SupportPurchaseState> = _state

    private val loadedProducts = mutableMapOf<SupportProduct, LoadedProduct>()

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                if (purchases.isNullOrEmpty()) clearProcessing()
                purchases?.forEach { purchase -> scope.launch { handlePurchase(purchase, fromPurchaseFlow = true) } }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> clearProcessing()
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                // Owned on another device/install before this client had queried purchases.
                clearProcessing()
                scope.launch { restorePurchases() }
            }
            else -> {
                Log.w(TAG, "Purchase flow failed: ${billingResult.debugMessage}")
                clearProcessing()
            }
        }
    }

    private val billingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    override fun start() {
        if (billingClient.isReady) return
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    scope.launch { loadOneTimeProducts() }
                    scope.launch { loadSubscriptions() }
                    scope.launch { restorePurchases() }
                } else {
                    Log.w(TAG, "Billing setup failed: ${billingResult.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                // The next start() call (e.g. re-entering Settings) retries the connection.
            }
        })
    }

    /**
     * Terminal: a closed BillingClient can't be reused, so callers create a new manager per visit.
     * Ends the connection even while it is still being set up, and cancels pending queries so a
     * quick exit from Settings can't leave a Play service connection open.
     */
    override fun stop() {
        scope.cancel()
        billingClient.endConnection()
    }

    override fun launchPurchase(activity: Activity, product: SupportProduct) {
        if (_state.value.isProcessing) return
        val loaded = loadedProducts[product] ?: return
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(loaded.details)
            .apply { loaded.offerToken?.let(::setOfferToken) }
            .build()
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()
        _state.update { it.copy(processingProduct = product, showThanks = false) }
        val result = billingClient.launchBillingFlow(activity, params)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            // The purchase sheet never opened, so no listener callback will clear the spinner.
            Log.w(TAG, "Could not launch purchase flow: ${result.debugMessage}")
            clearProcessing()
        }
    }

    override fun dismissThanks() {
        _state.update { it.copy(showThanks = false) }
    }

    private fun clearProcessing() {
        _state.update { it.copy(processingProduct = null) }
    }

    private suspend fun loadOneTimeProducts() {
        val products = SupportProduct.entries.filter { it.kind != SupportProductKind.Subscription }.toSet()
        val loaded = queryDetails(products, BillingClient.ProductType.INAPP) { details ->
            details.oneTimePurchaseOfferDetails?.formattedPrice?.let { price -> LoadedProduct(details, null) to price }
        }
        publishOptions(products, loaded)
    }

    private suspend fun loadSubscriptions() {
        val products = SupportProduct.entries.filter { it.kind == SupportProductKind.Subscription }.toSet()
        val loaded = queryDetails(products, BillingClient.ProductType.SUBS) { details ->
            // Prefer the base plan (no offer id) over promotional offers.
            val offers = details.subscriptionOfferDetails.orEmpty()
            val offer = offers.firstOrNull { it.offerId == null } ?: offers.firstOrNull()
            val price = offer?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice
            if (offer != null && price != null) LoadedProduct(details, offer.offerToken) to price else null
        }
        publishOptions(products, loaded)
    }

    private suspend fun queryDetails(
        products: Set<SupportProduct>,
        productType: String,
        toLoaded: (ProductDetails) -> Pair<LoadedProduct, String>?,
    ): Map<SupportProduct, Pair<LoadedProduct, String>> {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                products.map { product ->
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(product.productId)
                        .setProductType(productType)
                        .build()
                },
            )
            .build()
        val result = billingClient.queryProductDetails(params)
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            Log.w(TAG, "Product details unavailable: ${result.billingResult.debugMessage}")
            return emptyMap()
        }
        return result.productDetailsList.orEmpty().mapNotNull { details ->
            val product = SupportProduct.fromProductId(details.productId) ?: return@mapNotNull null
            toLoaded(details)?.let { product to it }
        }.toMap()
    }

    private fun publishOptions(products: Set<SupportProduct>, loaded: Map<SupportProduct, Pair<LoadedProduct, String>>) {
        products.forEach { loadedProducts.remove(it) }
        loaded.forEach { (product, value) -> loadedProducts[product] = value.first }
        val options = loaded.map { (product, value) -> SupportOption(product, value.second) }
        _state.update { it.withOptions(products, options) }
    }

    /** Runs on every connection: restores owned products and the subscription, finishing any left unacknowledged. */
    private suspend fun restorePurchases() {
        // The state below passes through partial values while the queries run; don't let a reader
        // (the cached supporter status) trust it until both have answered.
        _state.update { it.copy(purchasesRestored = false) }
        val oneTime = billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
        )
        if (oneTime.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            // A refunded purchase no longer appears here, so it stops counting.
            val listed = oneTime.purchasesList
                .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                .flatMap { it.products }
                .mapNotNull(SupportProduct::fromProductId)
                .toSet()
            _state.update { it.copy(ownedProducts = it.ownedProducts intersect listed) }
            oneTime.purchasesList.forEach { handlePurchase(it, fromPurchaseFlow = false) }
        }
        val subscriptions = billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build(),
        )
        if (subscriptions.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            val active = subscriptions.purchasesList.filter { purchase ->
                purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
                    purchase.products.any { SupportProduct.fromProductId(it)?.kind == SupportProductKind.Subscription }
            }
            // An expired or cancelled-and-lapsed subscription no longer appears here.
            if (active.isEmpty()) _state.update { it.copy(isSubscribed = false) }
            active.forEach { handlePurchase(it, fromPurchaseFlow = false) }
            if (oneTime.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                _state.update { it.copy(purchasesRestored = true) }
            }
        }
    }

    private suspend fun handlePurchase(purchase: Purchase, fromPurchaseFlow: Boolean) {
        val product = purchase.products.firstNotNullOfOrNull(SupportProduct::fromProductId)
        if (product == null || purchase.purchaseState != Purchase.PurchaseState.PURCHASED) {
            // Pending (e.g. cash payment not yet made): finished by restorePurchases() on a later visit.
            if (fromPurchaseFlow) clearProcessing()
            return
        }
        val wasAcknowledged = purchase.isAcknowledged
        val granted = wasAcknowledged || acknowledge(purchase)
        _state.update { current ->
            val finished = if (fromPurchaseFlow) current.copy(processingProduct = null) else current
            if (!granted) return@update finished
            // Acknowledging on restore finishes a purchase (e.g. a pending payment) the app never saw end.
            val thanks = finished.showThanks || fromPurchaseFlow || !wasAcknowledged
            when (product.kind) {
                SupportProductKind.Permanent -> finished.copy(ownedProducts = finished.ownedProducts + product, showThanks = thanks)
                SupportProductKind.Subscription -> finished.copy(isSubscribed = true, showThanks = thanks)
            }
        }
    }

    private suspend fun acknowledge(purchase: Purchase): Boolean {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        val result = billingClient.acknowledgePurchase(params)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            Log.w(TAG, "Acknowledge failed: ${result.debugMessage}")
            return false
        }
        return true
    }
}

fun createSupportBillingManager(context: Context): SupportBillingManager = PlayStoreSupportBillingManager(context)

fun supporterPerksAvailable(): Boolean = true
