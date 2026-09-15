package com.azimulkabir.actua.data.rules

import com.azimulkabir.actua.data.budget.model.ActualTransaction
import com.azimulkabir.actua.model.Transaction
import com.azimulkabir.actua.model.Type
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionRulePreviewTest {
    private val choices = RulePreviewChoices(
        accountNames = mapOf("checking" to "Checking", "cash" to "Cash", "tracking" to "Tracking"),
        offBudgetAccountIds = setOf("tracking"),
        categoryNames = mapOf("food" to "Food"),
        payeeNames = mapOf("shop" to "Shop"),
    )

    @Test fun `maps a multi-action rule into visible draft fields`() {
        val result = RulesEngine.apply(actual(), listOf(Rule(
            "shop-rule", Rule.Stage.DEFAULT, Rule.ConditionsOp.AND,
            listOf(Rule.Condition("is", "payee", RuleValue.Text("shop"))),
            listOf(
                Rule.Action("set", "account", RuleValue.Text("cash")),
                Rule.Action("set", "category", RuleValue.Text("food")),
                Rule.Action("set", "cleared", RuleValue.Flag(true)),
                Rule.Action("set", "notes", RuleValue.Text("Rule note")),
                Rule.Action("set", "payee_name", RuleValue.Text("Coffee Shop")),
                Rule.Action("set", "date", RuleValue.Text("2026-09-12")),
                Rule.Action("set", "amount", RuleValue.Number(1250.0)),
            ),
        )), RuleContext(payeeNames = mapOf("shop" to "Shop")))

        val preview = TransactionRulePreview.map(draft(), result, choices)

        assertEquals("Cash", preview.account)
        assertEquals("Food", preview.category)
        assertTrue(preview.cleared)
        assertEquals("Rule note", preview.notes)
        assertEquals("Coffee Shop", preview.payee)
        assertEquals("20260912", preview.date)
        assertEquals(1250L, preview.amountCents)
        assertEquals(Type.INCOME, preview.type)
        assertTrue(preview.rulesApplied)
    }

    @Test fun `rejects invalid targets and clears category for off-budget account`() {
        val invalid = RuleRunResult(
            actual().copy(accountId = "missing", categoryId = "missing"),
            setOf("account", "category"), null, false,
        )
        val invalidPreview = TransactionRulePreview.map(draft(), invalid, choices)
        assertEquals("Checking", invalidPreview.account)
        assertEquals("Existing", invalidPreview.category)

        val offBudget = invalid.copy(
            transaction = actual().copy(accountId = "tracking", categoryId = "food"),
        )
        val offBudgetPreview = TransactionRulePreview.map(draft(), offBudget, choices)
        assertEquals("Tracking", offBudgetPreview.account)
        assertEquals("", offBudgetPreview.category)
    }

    @Test fun `transfer draft keeps payee, category and type fixed while notes and cleared apply`() {
        val transferDraft = Transaction(
            id = "", date = "20260913", payee = "Transfer: Savings", category = "",
            account = "Checking", amount = -100, cleared = false, amountCents = 10_000,
            type = Type.TRANSFER, transferAccount = "Savings",
        )
        val result = RuleRunResult(
            actual().copy(
                payeeId = "transfer-savings", transferAccountId = "savings",
                notes = "#transfer-test", cleared = true, categoryId = "food",
            ),
            setOf("notes", "cleared", "category"), null, false,
        )

        val preview = TransactionRulePreview.map(transferDraft, result, choices)

        assertEquals(Type.TRANSFER, preview.type)
        assertEquals("Transfer: Savings", preview.payee)
        assertEquals("", preview.category)
        assertEquals("#transfer-test", preview.notes)
        assertTrue(preview.cleared)
        assertEquals("Savings", preview.transferAccount)
    }

    private fun draft() = Transaction(
        id = "", date = "20260913", payee = "Shop", category = "Existing",
        account = "Checking", amount = -10, cleared = false, amountCents = 1000,
        type = Type.EXPENSE,
    )

    private fun actual() = ActualTransaction(
        id = "preview", accountId = "checking", date = 20260913, amountCents = -1000,
        payeeId = "shop", payeeName = "Shop", categoryId = null, categoryName = null,
        notes = null, cleared = false, reconciled = false, transferId = null,
        isParent = false, parentId = null, tombstone = false, sortOrder = null,
        importedPayee = "Shop", scheduleId = null, transferAccountId = null,
    )
}
