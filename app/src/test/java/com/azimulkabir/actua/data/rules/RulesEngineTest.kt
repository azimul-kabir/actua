package com.azimulkabir.actua.data.rules

import com.azimulkabir.actua.data.budget.model.ActualTransaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesEngineTest {
    @Test fun ranksStagesThenLeastToMostSpecific() {
        fun rule(id: String, stage: Rule.Stage, vararg ops: String) = Rule(id, stage, Rule.ConditionsOp.AND,
            ops.map { Rule.Condition(it, "notes", RuleValue.Text("x")) }, emptyList())
        val ranked = RuleRanker.rank(listOf(rule("post", Rule.Stage.POST, "is"), rule("specific", Rule.Stage.DEFAULT, "is"),
            rule("broad", Rule.Stage.DEFAULT, "contains"), rule("pre", Rule.Stage.PRE, "is")))
        assertEquals(listOf("pre", "broad", "specific", "post"), ranked.map(Rule::id))
    }

    @Test fun appliesMatchingActionsAndResolvesPendingPayee() {
        val rule = Rule("rule", Rule.Stage.DEFAULT, Rule.ConditionsOp.AND, listOf(
            Rule.Condition("contains", "imported_payee", RuleValue.Text("coffee")),
            Rule.Condition("is", "amount", RuleValue.Number(450.0), mapOf("outflow" to RuleValue.Flag(true))),
        ), listOf(
            Rule.Action("set", "category", RuleValue.Text("dining")),
            Rule.Action("set", "payee_name", RuleValue.Text("Coffee Shop")),
            Rule.Action("append-notes", null, RuleValue.Text(" #cafe")),
            Rule.Action("set", "cleared", RuleValue.Flag(true)),
        ))
        val result = RulesEngine.apply(transaction(amount = -450, imported = "THE COFFEE PLACE", notes = "morning"), listOf(rule))
        assertEquals("dining", result.transaction.categoryId)
        assertEquals("Coffee Shop", result.pendingPayeeName)
        assertEquals("morning #cafe", result.transaction.notes)
        assertTrue(result.transaction.cleared)
        assertEquals(setOf("category", "payee_name", "notes", "cleared"), result.changedFields)
    }

    @Test fun exactRulesRunLastAndDateTagsAndBudgetConditionsMatch() {
        val rules = listOf(
            Rule("exact", Rule.Stage.DEFAULT, Rule.ConditionsOp.AND,
                listOf(Rule.Condition("is", "payee", RuleValue.Text("p"))),
                listOf(Rule.Action("set", "notes", RuleValue.Text("exact")))),
            Rule("broad", Rule.Stage.DEFAULT, Rule.ConditionsOp.AND,
                listOf(Rule.Condition("contains", "payee_name", RuleValue.Text("shop"))),
                listOf(Rule.Action("set", "notes", RuleValue.Text("broad")))),
            Rule("tag", Rule.Stage.POST, Rule.ConditionsOp.AND,
                listOf(Rule.Condition("hasTags", "notes", RuleValue.Text("exact"))), emptyList()),
        )
        val result = RulesEngine.apply(transaction(payee = "p", payeeName = "Shop", notes = "#start"), rules,
            RuleContext(payeeNames = mapOf("p" to "Shop")))
        assertEquals("exact", result.transaction.notes)
        assertEquals(true, RuleDateMatcher.matches(20260505, "isapprox", "2026-05-03"))
        assertTrue(TagFilter.contains("hello #One #two", "#one"))
        assertFalse(TagFilter.contains("##hidden", "#hidden"))
    }

    @Test fun deleteAndOffBudgetActionsFollowIos() {
        val rule = Rule("delete", Rule.Stage.DEFAULT, Rule.ConditionsOp.AND,
            listOf(Rule.Condition("offBudget", "account", RuleValue.Null)),
            listOf(Rule.Action("delete-transaction", null, RuleValue.Null)))
        val result = RulesEngine.apply(transaction(), listOf(rule), RuleContext(offBudgetAccountIds = setOf("a")))
        assertTrue(result.isDeleted)
        assertTrue(result.transaction.tombstone)
    }

    private fun transaction(
        amount: Long = -100, imported: String? = null, payee: String? = null,
        payeeName: String? = null, notes: String? = null,
    ) = ActualTransaction("t", "a", 20260503, amount, payee, payeeName, null, null, notes,
        false, false, null, false, null, false, null, imported, null, null)
}
