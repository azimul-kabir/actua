package com.azimulkabir.actua.data.preferences

import com.azimulkabir.actua.billing.SupportOption
import com.azimulkabir.actua.billing.SupportProduct
import com.azimulkabir.actua.billing.SupportPurchaseState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SupporterPerksTest {

    private class FakeStore : SupporterPerksStore {
        override var isSupporter = false
        override var lastCheckedAt = 0L
    }

    private val store = FakeStore()
    private val perks = SupporterPerks(store)
    private val resets = mutableListOf<String>()

    private val owned = SupportPurchaseState(
        options = listOf(SupportOption(SupportProduct.SupporterBadge, "$2.99")),
        ownedProducts = setOf(SupportProduct.SupporterBadge),
        purchasesRestored = true,
    )

    @Before
    fun registerExtras() {
        SupporterPerks.clearRegistrations()
        SupporterPerks.register("icon") { resets += "icon" }
        SupporterPerks.register("theme") { resets += "theme" }
    }

    @After
    fun clearExtras() = SupporterPerks.clearRegistrations()

    @Test
    fun startsAsNonSupporter() {
        assertFalse(perks.isSupporter)
    }

    @Test
    fun anOwnedPurchaseOrSubscriptionUnlocksTheExtras() {
        assertFalse(perks.record(owned, nowMillis = 1_000))
        assertTrue(perks.isSupporter)

        store.isSupporter = false
        perks.record(owned.copy(ownedProducts = emptySet(), isSubscribed = true), nowMillis = 2_000)
        assertTrue(perks.isSupporter)
        assertTrue(resets.isEmpty())
    }

    @Test
    fun anUnconfirmedStateNeverChangesTheCache() {
        perks.record(owned, nowMillis = 1_000)
        val unconfirmed = owned.copy(ownedProducts = emptySet(), purchasesRestored = false)

        assertFalse(perks.record(unconfirmed, nowMillis = 2_000))

        assertTrue(perks.isSupporter)
        assertEquals(1_000L, store.lastCheckedAt)
        assertTrue(resets.isEmpty())
    }

    @Test
    fun losingSupporterStatusResetsEveryExtraOnce() {
        perks.record(owned, nowMillis = 1_000)

        assertTrue(perks.record(isSupporter = false, nowMillis = 2_000))
        assertFalse(perks.isSupporter)
        assertEquals(listOf("icon", "theme"), resets)

        // Already a non-supporter: nothing left to reset.
        assertFalse(perks.record(isSupporter = false, nowMillis = 3_000))
        assertEquals(2, resets.size)
    }

    @Test
    fun aNonSupporterStayingOneNeverResets() {
        assertFalse(perks.record(isSupporter = false, nowMillis = 1_000))
        assertTrue(resets.isEmpty())
    }

    @Test
    fun checksAtMostOnceADay() {
        val day = SupporterPerks.CHECK_INTERVAL_MILLIS
        assertTrue(perks.isCheckDue(nowMillis = day))

        perks.record(isSupporter = true, nowMillis = day)

        assertFalse(perks.isCheckDue(nowMillis = day + day - 1))
        assertTrue(perks.isCheckDue(nowMillis = day + day))
    }

    @Test
    fun registeringAnExtraAgainReplacesItsReset() {
        SupporterPerks.register("icon") { resets += "icon-v2" }
        SupporterPerks.resetAll()
        assertEquals(listOf("icon-v2", "theme"), resets)
    }
}
