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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "SupportBilling"

/**
 * "Support Actua" is a permanent, non-consumable purchase: once acknowledged it's owned by the
 * Play account forever (restored on every [start], e.g. after reinstalling), so it's never
 * consumed and can't be bought again.
 */
private class PlayStoreSupportBillingManager(context: Context) : SupportBillingManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(SupportPurchaseState())
    override val state: StateFlow<SupportPurchaseState> = _state

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases?.forEach { handlePurchase(it) }
            BillingClient.BillingResponseCode.USER_CANCELED -> _state.update { it.copy(isProcessing = false) }
            else -> {
                Log.w(TAG, "Purchase flow failed: ${billingResult.debugMessage}")
                _state.update { it.copy(isProcessing = false) }
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
                    scope.launch { loadProductDetails() }
                    scope.launch { restoreOwnedPurchase() }
                } else {
                    Log.w(TAG, "Billing setup failed: ${billingResult.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                // The next start() call (e.g. re-entering Settings) retries the connection.
            }
        })
    }

    override fun stop() {
        if (billingClient.isReady) billingClient.endConnection()
    }

    override fun launchPurchase(activity: Activity) {
        val productDetails = cachedProductDetails ?: return
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(productDetails)
                        .build(),
                ),
            )
            .build()
        _state.update { it.copy(isProcessing = true) }
        billingClient.launchBillingFlow(activity, params)
    }

    private var cachedProductDetails: ProductDetails? = null

    private suspend fun loadProductDetails() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(SUPPORT_ACTUA_PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build(),
                ),
            )
            .build()
        val result = billingClient.queryProductDetails(params)
        val details = result.productDetailsList?.firstOrNull()
        if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK && details != null) {
            cachedProductDetails = details
            val price = details.oneTimePurchaseOfferDetails?.formattedPrice
            _state.update { current ->
                current.copy(isAvailable = true, priceLabel = price ?: current.priceLabel)
            }
        } else {
            Log.w(TAG, "Product details unavailable: ${result.billingResult.debugMessage}")
        }
    }

    /** Runs on every connection so a reinstall or a second device shows the badge as owned. */
    private suspend fun restoreOwnedPurchase() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val result = billingClient.queryPurchasesAsync(params)
        val owned = result.purchasesList
            .filter { it.products.contains(SUPPORT_ACTUA_PRODUCT_ID) }
            .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        owned.forEach { purchase ->
            if (!purchase.isAcknowledged) acknowledge(purchase) else _state.update { it.copy(isPurchased = true) }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) {
            _state.update { it.copy(isProcessing = false) }
            return
        }
        if (purchase.isAcknowledged) {
            _state.update { it.copy(isProcessing = false, isPurchased = true) }
        } else {
            scope.launch { acknowledge(purchase) }
        }
    }

    private suspend fun acknowledge(purchase: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        val result = billingClient.acknowledgePurchase(params)
        _state.update { current ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                current.copy(isProcessing = false, isPurchased = true)
            } else {
                Log.w(TAG, "Acknowledge failed: ${result.debugMessage}")
                current.copy(isProcessing = false)
            }
        }
    }
}

fun createSupportBillingManager(context: Context): SupportBillingManager = PlayStoreSupportBillingManager(context)
