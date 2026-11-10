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
            listOf("support_actua", "support_actua_tier2", "support_actua_tier3", "support_actua_tier4"),
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
        assertTrue(empty.withOptions(setOf(SupportProduct.SupporterTier2), listOf(option(SupportProduct.SupporterTier2))).isAvailable)
    }

    @Test
    fun withOptionsMergesBothQueriesInDisplayOrder() {
        val oneTime = setOf(SupportProduct.SupporterBadge, SupportProduct.SupporterTier2, SupportProduct.SupporterTier4)
        val state = SupportPurchaseState()
            .withOptions(oneTime, listOf(option(SupportProduct.SupporterTier4), option(SupportProduct.SupporterBadge)))
            .withOptions(setOf(SupportProduct.MonthlySupporter), listOf(option(SupportProduct.MonthlySupporter)))
        assertEquals(
            listOf(SupportProduct.MonthlySupporter, SupportProduct.SupporterBadge, SupportProduct.SupporterTier4),
            state.options.map { it.product },
        )
        // Reloading one query replaces only its own products.
        val reloaded = state.withOptions(oneTime, listOf(option(SupportProduct.SupporterTier2, "$4.99")))
        assertEquals(listOf(SupportProduct.MonthlySupporter, SupportProduct.SupporterTier2), reloaded.options.map { it.product })
    }

    @Test
    fun ownedProductsAndActiveSubscriptionStopBeingOffered() {
        val all = SupportPurchaseState(options = SupportProduct.entries.map { option(it) })
        assertEquals(SupportProduct.entries, all.offeredOptions.map { it.product })
        val owned = all.copy(ownedProducts = setOf(SupportProduct.SupporterBadge, SupportProduct.SupporterTier3), isSubscribed = true)
        assertEquals(listOf(SupportProduct.SupporterTier2, SupportProduct.SupporterTier4), owned.offeredOptions.map { it.product })
    }

    @Test
    fun anyPurchaseMakesTheUserASupporter() {
        assertFalse(SupportPurchaseState().isSupporter)
        assertTrue(SupportPurchaseState(ownedProducts = setOf(SupportProduct.SupporterBadge)).isSupporter)
        assertTrue(SupportPurchaseState(ownedProducts = setOf(SupportProduct.SupporterTier4)).isSupporter)
        assertTrue(SupportPurchaseState(isSubscribed = true).isSupporter)
    }

    @Test
    fun processingTracksTheProductBeingBought() {
        assertFalse(SupportPurchaseState().isProcessing)
        assertTrue(SupportPurchaseState(processingProduct = SupportProduct.SupporterTier3).isProcessing)
    }
}
