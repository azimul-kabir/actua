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

    @Test fun scheduleOwnedRuleBypassesConditionsForItsOwnTransaction() {
        val ownedRule = Rule("owned", Rule.Stage.DEFAULT, Rule.ConditionsOp.AND,
            listOf(Rule.Condition("is", "date", RuleValue.Text("2099-01-01"))),
            listOf(Rule.Action("set", "notes", RuleValue.Text("tagged")), Rule.Action("link-schedule", null, RuleValue.Text("sched1"))))
        val result = RulesEngine.apply(transaction(scheduleId = "sched1"), listOf(ownedRule))
        assertEquals("tagged", result.transaction.notes)
    }

    @Test fun otherScheduleOwnedRulesAreSkippedEntirely() {
        val ownRule = Rule("own", Rule.Stage.DEFAULT, Rule.ConditionsOp.AND, emptyList(),
            listOf(Rule.Action("link-schedule", null, RuleValue.Text("sched1"))))
        val otherRule = Rule("other", Rule.Stage.DEFAULT, Rule.ConditionsOp.AND,
            listOf(Rule.Condition("is", "account", RuleValue.Text("a"))),
            listOf(Rule.Action("set", "notes", RuleValue.Text("hijacked")), Rule.Action("link-schedule", null, RuleValue.Text("sched2"))))
        val result = RulesEngine.apply(transaction(scheduleId = "sched1"), listOf(ownRule, otherRule))
        assertEquals(null, result.transaction.notes)
    }

    @Test fun ordinaryRulesStillConditionCheckOnScheduleTransactions() {
        val rule = Rule("ordinary", Rule.Stage.DEFAULT, Rule.ConditionsOp.AND,
            listOf(Rule.Condition("is", "account", RuleValue.Text("nope"))),
            listOf(Rule.Action("set", "notes", RuleValue.Text("should-not-apply"))))
        val result = RulesEngine.apply(transaction(scheduleId = "sched1"), listOf(rule))
        assertEquals(null, result.transaction.notes)
    }

    @Test fun recurringDateConditionMatchesThroughRulesEngine() {
        val config = com.azimulkabir.actua.data.schedules.RecurConfig(
            com.azimulkabir.actua.data.schedules.RecurConfig.Frequency.MONTHLY, 1,
            com.azimulkabir.actua.data.schedules.DayDate(2026, 1, 3))
        val configJson = RuleValue.fromJson(config.toJson())
        assertTrue(configJson is RuleValue.ObjectValue)
        val onSchedule = Rule("recur", Rule.Stage.DEFAULT, Rule.ConditionsOp.AND,
            listOf(Rule.Condition("is", "date", configJson)),
            listOf(Rule.Action("set", "notes", RuleValue.Text("matched"))))
        val matchResult = RulesEngine.apply(transaction(date = 20260503), listOf(onSchedule))
        assertEquals("matched", matchResult.transaction.notes)
        val noMatchResult = RulesEngine.apply(transaction(date = 20260504), listOf(onSchedule))
        assertEquals(null, noMatchResult.transaction.notes)
        assertEquals(true, RuleDateMatcher.matchesRecurring(20260503, "isapprox", configJson as RuleValue.ObjectValue))
    }

    private fun transaction(
        amount: Long = -100, imported: String? = null, payee: String? = null,
        payeeName: String? = null, notes: String? = null, date: Int = 20260503, scheduleId: String? = null,
    ) = ActualTransaction("t", "a", date, amount, payee, payeeName, null, null, notes,
        false, false, null, false, null, false, null, imported, scheduleId, null)
}
