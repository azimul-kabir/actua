package com.azimulkabir.actua.data.rules

import com.azimulkabir.actua.data.budget.model.ActualTransaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferRuleSemanticsTest {
    private fun transfer(
        payeeId: String = "transfer-savings",
        transferAccountId: String = "savings",
        notes: String? = null,
        cleared: Boolean = false,
    ) = ActualTransaction(
        id = "draft",
        accountId = "checking",
        date = 20260915,
        amountCents = -10_000,
        payeeId = payeeId,
        payeeName = "Transfer: Savings",
        categoryId = null,
        categoryName = null,
        notes = notes,
        cleared = cleared,
        reconciled = false,
        transferId = null,
        isParent = false,
        parentId = null,
        tombstone = false,
        sortOrder = null,
        importedPayee = null,
        scheduleId = null,
        transferAccountId = transferAccountId,
    )

    private fun rule(vararg actions: Rule.Action) = Rule(
        id = "transfer-rule",
        stage = Rule.Stage.DEFAULT,
        conditionsOp = Rule.ConditionsOp.AND,
        conditions = listOf(Rule.Condition("is", "payee", RuleValue.Text("transfer-savings"))),
        actions = actions.toList(),
    )

    @Test fun `transfer payee rule can update notes and cleared`() {
        val result = RulesEngine.apply(
            transfer(),
            listOf(rule(
                Rule.Action("set", "notes", RuleValue.Text("#transfer-test")),
                Rule.Action("set", "cleared", RuleValue.BooleanValue(true)),
            )),
        )

        assertEquals("#transfer-test", result.transaction.notes)
        assertTrue(result.transaction.cleared)
        assertEquals("transfer-savings", result.transaction.payeeId)
        assertEquals("savings", result.transaction.transferAccountId)
    }

    @Test fun `unmatched transfer remains unchanged`() {
        val draft = transfer(payeeId = "transfer-other", transferAccountId = "other")
        val result = RulesEngine.apply(
            draft,
            listOf(rule(Rule.Action("set", "cleared", RuleValue.BooleanValue(true)))),
        )

        assertEquals(draft, result.transaction)
        assertFalse(result.transaction.cleared)
    }

    @Test fun `changing transfer destination changes matching result`() {
        val action = Rule.Action("set", "notes", RuleValue.Text("#savings"))
        assertEquals("#savings", RulesEngine.apply(transfer(), listOf(rule(action))).transaction.notes)
        assertEquals(null, RulesEngine.apply(
            transfer(payeeId = "transfer-investment", transferAccountId = "investment"),
            listOf(rule(action)),
        ).transaction.notes)
    }

    @Test fun `append action is deterministic from the same pristine draft`() {
        val append = rule(Rule.Action("append-notes", "notes", RuleValue.Text(" #transfer-test")))
        val draft = transfer(notes = "memo")
        val first = RulesEngine.apply(draft, listOf(append)).transaction
        val repeatedPreview = RulesEngine.apply(draft, listOf(append)).transaction

        assertEquals("memo #transfer-test", first.notes)
        assertEquals(first.notes, repeatedPreview.notes)
    }
}
