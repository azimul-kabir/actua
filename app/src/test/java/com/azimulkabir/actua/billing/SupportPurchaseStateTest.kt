package com.azimulkabir.actua.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportPurchaseStateTest {

    private fun option(product: SupportProduct, price: String = "$1.99") = SupportOption(product, price)

    @Test
    fun productIdsMatchPlayConsoleAndAreUnique() {
        assertEquals("support_actua", SupportProduct.SupporterBadge.productId)
        assertEquals("supporter_monthly", SupportProduct.MonthlySupporter.productId)
        assertEquals(SupportProductKind.Subscription, SupportProduct.MonthlySupporter.kind)
        // Everything else is permanent so Play restores it: a consumed purchase can't be listed again.
        assertEquals(
            listOf("support_actua", "tip_small", "tip_medium", "tip_large"),
            SupportProduct.entries.filter { it.kind == SupportProductKind.Permanent }.map { it.productId },
        )
        assertEquals(SupportProduct.entries.size, SupportProduct.entries.map { it.productId }.toSet().size)
        SupportProduct.entries.forEach { assertEquals(it, SupportProduct.fromProductId(it.productId)) }
        assertNull(SupportProduct.fromProductId("unknown_product"))
    }

    @Test
    fun unavailableUntilPlayReturnsAProduct() {
        val empty = SupportPurchaseState()
        assertFalse(empty.isAvailable)
        assertTrue(empty.withOptions(setOf(SupportProduct.TipSmall), listOf(option(SupportProduct.TipSmall))).isAvailable)
    }

    @Test
    fun withOptionsMergesBothQueriesInDisplayOrder() {
        val oneTime = setOf(SupportProduct.SupporterBadge, SupportProduct.TipSmall, SupportProduct.TipLarge)
        val state = SupportPurchaseState()
            .withOptions(oneTime, listOf(option(SupportProduct.TipLarge), option(SupportProduct.SupporterBadge)))
            .withOptions(setOf(SupportProduct.MonthlySupporter), listOf(option(SupportProduct.MonthlySupporter)))
        assertEquals(
            listOf(SupportProduct.MonthlySupporter, SupportProduct.SupporterBadge, SupportProduct.TipLarge),
            state.options.map { it.product },
        )
        // Reloading one query replaces only its own products.
        val reloaded = state.withOptions(oneTime, listOf(option(SupportProduct.TipSmall, "$0.99")))
        assertEquals(listOf(SupportProduct.MonthlySupporter, SupportProduct.TipSmall), reloaded.options.map { it.product })
    }

    @Test
    fun ownedProductsAndActiveSubscriptionStopBeingOffered() {
        val all = SupportPurchaseState(options = SupportProduct.entries.map { option(it) })
        assertEquals(SupportProduct.entries, all.offeredOptions.map { it.product })
        val owned = all.copy(ownedProducts = setOf(SupportProduct.SupporterBadge, SupportProduct.TipMedium), isSubscribed = true)
        assertEquals(listOf(SupportProduct.TipSmall, SupportProduct.TipLarge), owned.offeredOptions.map { it.product })
    }

    @Test
    fun anyPurchaseMakesTheUserASupporter() {
        assertFalse(SupportPurchaseState().isSupporter)
        assertTrue(SupportPurchaseState(ownedProducts = setOf(SupportProduct.SupporterBadge)).isSupporter)
        assertTrue(SupportPurchaseState(ownedProducts = setOf(SupportProduct.TipSmall)).isSupporter)
        assertTrue(SupportPurchaseState(isSubscribed = true).isSupporter)
    }

    @Test
    fun processingTracksTheProductBeingBought() {
        assertFalse(SupportPurchaseState().isProcessing)
        assertTrue(SupportPurchaseState(processingProduct = SupportProduct.TipMedium).isProcessing)
    }
}
