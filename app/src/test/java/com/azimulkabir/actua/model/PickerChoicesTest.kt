package com.azimulkabir.actua.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Actual allows duplicate account names and a category name in several groups (#747). */
class PickerChoicesTest {
    private val categories = PickerChoices.categories(listOf(
        "Food" to ("food-misc" to "Misc"),
        "Food" to ("groceries" to "Groceries"),
        "Home" to ("home-misc" to "Misc"),
    ))
    private val accounts = PickerChoices.accounts(listOf(
        "checking-a" to "Checking",
        "savings" to "Savings",
        "checking-b" to "Checking",
    ))

    @Test
    fun uniqueNamesKeepTheirNameAsLabel() {
        assertEquals(listOf("Misc (Food)", "Groceries", "Misc (Home)"), categories.labels)
        assertEquals(listOf("Checking (1)", "Savings", "Checking (2)"), accounts.labels)
    }

    @Test
    fun aGeneratedLabelNeverCollidesWithARealName() {
        val choices = PickerChoices.accounts(listOf("a" to "Cash", "b" to "Cash", "c" to "Cash (2)"))
        assertEquals(listOf("Cash (1)", "Cash (2)", "Cash (2) (2)"), choices.labels)
        assertEquals("c", choices.choice("Cash (2) (2)")?.id)
    }

    @Test
    fun labelsMapBackToTheirOwnRow() {
        assertEquals(PickerChoice("home-misc", "Misc", "Misc (Home)"), categories.choice("Misc (Home)"))
        assertEquals("Misc (Home)", categories.labelOf("home-misc", "Misc"))
        assertEquals("Checking (2)", accounts.labelOf("checking-b", "Checking"))
        // Without an id the first row with that name is used, and an unknown name stays as is.
        assertEquals("Checking (1)", accounts.labelOf(null, "Checking"))
        assertEquals("Uncategorized", categories.labelOf(null, "Uncategorized"))
    }

    @Test
    fun anEditedRowRoundTripsToTheRowsItCameFrom() {
        val row = Transaction(
            id = "t", date = "20260910", payee = "Shop", category = "Misc", account = "Checking",
            amount = -10, cleared = false, amountCents = -1_000,
            accountId = "checking-b", categoryId = "home-misc",
            splits = listOf(SplitLine(category = "Misc", amountCents = 1_000, categoryId = "home-misc")),
        )
        val labels = row.withChoiceLabels(accounts, categories)
        assertEquals("Checking (2)", labels.account)
        assertEquals("Misc (Home)", labels.category)
        assertEquals("Misc (Home)", labels.splits.single().category)

        val saved = labels.resolveChoices(accounts, categories)
        assertEquals("Checking" to "checking-b", saved.account to saved.accountId)
        assertEquals("Misc" to "home-misc", saved.category to saved.categoryId)
        assertEquals("Misc" to "home-misc", saved.splits.single().let { it.category to it.categoryId })
    }

    @Test
    fun aChangedChoiceReplacesTheOldId() {
        val draft = Transaction(
            id = "t", date = "20260910", payee = "", category = "Misc (Food)", account = "Savings",
            amount = -10, cleared = false, amountCents = -1_000, type = Type.TRANSFER,
            transferAccount = "Checking (2)", accountId = "stale", categoryId = "home-misc",
            splits = listOf(SplitLine(category = "Groceries", categoryId = "home-misc")),
        ).resolveChoices(accounts, categories)
        assertEquals("savings", draft.accountId)
        assertEquals("Checking" to "checking-b", draft.transferAccount to draft.transferAccountId)
        assertEquals("food-misc", draft.categoryId)
        assertEquals("groceries", draft.splits.single().categoryId)
    }

    @Test
    fun anUnknownLabelKeepsTheNameWithoutAnId() {
        val draft = Transaction(
            id = "", date = "20260910", payee = "", category = "Uncategorized", account = "Checking",
            amount = 0, cleared = false, categoryId = "food-misc",
        ).resolveChoices(PickerChoices.EMPTY, categories)
        assertEquals("Checking", draft.account)
        assertNull(draft.accountId)
        assertEquals("Uncategorized", draft.category)
        assertNull(draft.categoryId)
    }

    @Test
    fun byIdOrNamePrefersTheIdWhileTheNameStillMatches() {
        val rows = listOf("checking-a" to "Checking", "checking-b" to "Checking", "savings" to "Savings")
        fun pick(id: String?, name: String) = rows.byIdOrName(id, name, { it.first }, { it.second })?.first
        assertEquals("checking-b", pick("checking-b", "Checking"))
        assertEquals("checking-a", pick(null, "Checking"))
        // A copy that changed only the name must not be saved to the old id.
        assertEquals("savings", pick("checking-b", "Savings"))
        assertEquals("checking-a", pick("missing", "Checking"))
        assertNull(pick(null, "Cash"))
    }
}
