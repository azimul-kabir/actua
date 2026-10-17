package com.azimulkabir.actua.billing

import android.app.Activity
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private class UnavailableSupportBillingManager : SupportBillingManager {
    override val state: StateFlow<SupportPurchaseState> =
        MutableStateFlow(SupportPurchaseState(isAvailable = false))

    override fun start() = Unit

    override fun stop() = Unit

    override fun launchPurchase(activity: Activity) = Unit
}

fun createSupportBillingManager(context: Context): SupportBillingManager = UnavailableSupportBillingManager()
