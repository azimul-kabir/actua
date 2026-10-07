package com.azimulkabir.actua.data.rules

import com.azimulkabir.actua.data.budget.model.ActualTransaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferLegRulesTest {
    private fun leg(id: String, account: String, amount: Long, payee: String, transferId: String, category: String? = null) =
        ActualTransaction(
            id, account, 20260915, amount, payee, null, category, null, "Move", false, false, transferId,
            false, null, false, null, null, null, null,
        )

    private val source = leg("src", "checking", -5_000, "transfer-savings", "dst")
    private val partner = leg("dst", "savings", 5_000, "transfer-checking", "src")

    private fun rule(condition: Rule.Condition, vararg actions: Rule.Action) =
        Rule("r", Rule.Stage.DEFAULT, Rule.ConditionsOp.AND, listOf(condition), actions.toList())

    @Test fun otherLegTakesNotesClearedAndScheduleFromRules() {
        val rules = listOf(rule(
            Rule.Condition("is", "account", RuleValue.Text("savings")),
            Rule.Action("append-notes", null, RuleValue.Text(" #savings")),
            Rule.Action("set", "cleared", RuleValue.Flag(true)),
            Rule.Action("link-schedule", null, RuleValue.Text("sched")),
            // Anything else a rule sets is not kept.
            Rule.Action("set", "category", RuleValue.Text("food")),
            Rule.Action("set", "amount", RuleValue.Number(1.0)),
        ))

        val (ruledSource, ruledPartner) = TransferLegRules.apply(source, partner, rules, RuleContext())

        assertEquals("Move #savings", ruledPartner.notes)
        assertTrue(ruledPartner.cleared)
        assertEquals("sched", ruledPartner.scheduleId)
        assertNull(ruledPartner.categoryId)
        assertEquals(5_000L, ruledPartner.amountCents)
        // A schedule linked on the other leg is copied to the original leg; nothing else changes.
        assertEquals(source.copy(scheduleId = "sched"), ruledSource)
    }

    @Test fun rulesForOtherAccountsLeaveBothLegsUnchanged() {
        val rules = listOf(rule(
            Rule.Condition("is", "account", RuleValue.Text("checking")),
            Rule.Action("set", "notes", RuleValue.Text("changed")),
        ))

        val (ruledSource, ruledPartner) = TransferLegRules.apply(source, partner, rules, RuleContext())

        assertEquals(source, ruledSource)
        assertEquals(partner, ruledPartner)
        assertFalse(ruledPartner.cleared)
    }

    @Test fun otherLegIsMatchedWithoutItsCategory() {
        val withCategory = partner.copy(categoryId = "food")
        val rules = listOf(rule(
            Rule.Condition("is", "category", RuleValue.Null),
            Rule.Action("set", "notes", RuleValue.Text("no category")),
        ))

        val (_, ruledPartner) = TransferLegRules.apply(source, withCategory, rules, RuleContext())

        assertEquals("no category", ruledPartner.notes)
        assertEquals("food", ruledPartner.categoryId)
    }
}
