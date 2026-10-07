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

    @Test fun evaluatesTemplateFormulaAndBalanceOf() {
        val context = RuleContext(
            accountNames = mapOf("a" to "Checking"),
            runningBalanceCents = { 650L },
            balanceOfCents = { _, literal -> if (literal == "Checking") 1234L else 0L },
        )
        val result = RulesEngine.apply(
            transaction(amount = -2500, imported = "act-79"),
            listOf(
                Rule("template", Rule.Stage.DEFAULT, Rule.ConditionsOp.AND,
                    listOf(Rule.Condition("is", "account", RuleValue.Text("a"))), listOf(
                    Rule.Action("set", "notes", RuleValue.Text(""), mapOf("template" to RuleValue.Text("{{imported_payee}} on {{today}}"))),
                )),
                Rule("formula", Rule.Stage.DEFAULT, Rule.ConditionsOp.AND,
                    listOf(Rule.Condition("is", "account", RuleValue.Text("a"))), listOf(
                    Rule.Action("set", "amount", RuleValue.Number(0.0), mapOf("formula" to RuleValue.Text("=amount*2"))),
                )),
            ),
            context,
        )
        assertTrue(result.transaction.notes!!.startsWith("act-79 on "))
        assertEquals(-500000L, result.transaction.amountCents)

        val balanceResult = RulesEngine.apply(
            transaction(), listOf(Rule("balance", Rule.Stage.DEFAULT, Rule.ConditionsOp.AND,
                listOf(Rule.Condition("is", "account", RuleValue.Text("a"))), listOf(
                Rule.Action("set", "amount", RuleValue.Number(0.0), mapOf("formula" to RuleValue.Text("=BALANCE_OF(\"Checking\")"))),
            ))), context,
        )
        assertEquals(123400L, balanceResult.transaction.amountCents)
        val runningBalanceResult = RulesEngine.apply(
            transaction(), listOf(Rule("running-balance", Rule.Stage.DEFAULT, Rule.ConditionsOp.AND,
                listOf(Rule.Condition("is", "account", RuleValue.Text("a"))), listOf(
                Rule.Action("set", "amount", RuleValue.Number(0.0), mapOf("formula" to RuleValue.Text("=balance"))),
            ))), context,
        )
        assertEquals(65000L, runningBalanceResult.transaction.amountCents)
    }

    @Test fun appliesSplitActionsAndAssignsRemaindersByIndex() {
        val rule = Rule("split", Rule.Stage.DEFAULT, Rule.ConditionsOp.AND,
            listOf(Rule.Condition("is", "account", RuleValue.Text("a"))), listOf(
            Rule.Action("set-split-amount", null, RuleValue.Number(50.0), mapOf(
                "splitIndex" to RuleValue.Number(1.0), "method" to RuleValue.Text("fixed-percent"),
            )),
            Rule.Action("set", "category", RuleValue.Text("food"), mapOf("splitIndex" to RuleValue.Number(1.0))),
            Rule.Action("set-split-amount", null, RuleValue.Null, mapOf(
                "splitIndex" to RuleValue.Number(2.0), "method" to RuleValue.Text("remainder"),
            )),
            Rule.Action("set", "category", RuleValue.Text("fun"), mapOf("splitIndex" to RuleValue.Number(2.0))),
        ))
        var childId = 0
        val result = RulesEngine.apply(
            transaction(amount = -1001, payee = "shop", payeeName = "Shop").copy(categoryId = "food"),
            listOf(rule),
            idFactory = { "child-${childId++}" },
        )
        assertTrue(result.transaction.isParent)
        assertEquals(null, result.transaction.payeeId)
        assertEquals("food", result.transaction.categoryId)
        assertEquals(listOf(-500L, -501L), result.splitChildren.map { it.transaction.amountCents })
        assertEquals(listOf("food", "fun"), result.splitChildren.map { it.transaction.categoryId })
        assertEquals(-1001L, result.splitChildren.sumOf { it.transaction.amountCents })
    }

    @Test fun splitAmountFormulaAndSingleRemainderFollowActualCents() {
        val rule = Rule("split-formula", Rule.Stage.DEFAULT, Rule.ConditionsOp.AND,
            listOf(Rule.Condition("is", "account", RuleValue.Text("a"))), listOf(
                Rule.Action("set-split-amount", null, RuleValue.Null, mapOf(
                    "splitIndex" to RuleValue.Number(1.0), "method" to RuleValue.Text("formula"),
                    "formula" to RuleValue.Text("=parent_amount/200"),
                )),
                Rule.Action("set-split-amount", null, RuleValue.Null, mapOf(
                    "splitIndex" to RuleValue.Number(2.0), "method" to RuleValue.Text("remainder"),
                )),
            ))
        val result = RulesEngine.apply(transaction(amount = -1000), listOf(rule))
        assertEquals(listOf(-500L, -500L), result.splitChildren.map { it.transaction.amountCents })

        val single = RulesEngine.apply(
            transaction(amount = -1000),
            listOf(Rule("one-line", Rule.Stage.DEFAULT, Rule.ConditionsOp.AND,
                listOf(Rule.Condition("is", "account", RuleValue.Text("a"))), listOf(
                    Rule.Action("set-split-amount", null, RuleValue.Null, mapOf(
                        "splitIndex" to RuleValue.Number(1.0), "method" to RuleValue.Text("remainder"),
                    )),
                ))),
        )
        assertEquals(listOf(-1000L), single.splitChildren.map { it.transaction.amountCents })
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

    @Test fun laterRulesSeeEarlierRulesChanges() {
        fun rule(id: String, stage: Rule.Stage, condition: Rule.Condition, action: Rule.Action) =
            Rule(id, stage, Rule.ConditionsOp.AND, listOf(condition), listOf(action))
        val context = RuleContext(
            payeeNames = mapOf("amazon" to "Amazon"),
            categoryNames = mapOf("shopping" to "Shopping"),
            categoryGroupIds = mapOf("shopping" to "spending"),
            categoryGroupNames = mapOf("spending" to "Spending"),
        )
        val rules = listOf(
            // Renames the imported payee to an existing payee, then categorizes by that payee.
            rule("rename", Rule.Stage.PRE, Rule.Condition("contains", "imported_payee", RuleValue.Text("amzn")),
                Rule.Action("set", "payee_name", RuleValue.Text("amazon"))),
            rule("categorize", Rule.Stage.DEFAULT, Rule.Condition("is", "payee", RuleValue.Text("amazon")),
                Rule.Action("set", "category", RuleValue.Text("shopping"))),
            rule("group", Rule.Stage.POST, Rule.Condition("is", "category_group", RuleValue.Text("spending")),
                Rule.Action("append-notes", null, RuleValue.Text(" grouped"))),
            rule("groupName", Rule.Stage.POST, Rule.Condition("contains", "category_group", RuleValue.Text("spend")),
                Rule.Action("append-notes", null, RuleValue.Text(" named"))),
            // Checks the notes the original transaction had, which the post rules above changed.
            rule("stale", Rule.Stage.POST, Rule.Condition("is", "notes", RuleValue.Text("order")),
                Rule.Action("set", "cleared", RuleValue.Flag(true))),
        )
        val result = RulesEngine.apply(transaction(imported = "AMZN Mktp", notes = "order"), rules, context)
        assertEquals("shopping", result.transaction.categoryId)
        assertEquals("order named grouped", result.transaction.notes)
        assertFalse(result.transaction.cleared)
        // The payee is still written through the pending name, as before.
        assertEquals("amazon", result.pendingPayeeName)
        assertEquals(null, result.transaction.payeeId)
    }

    @Test fun ruleRunsTestIdFieldTextOpsAgainstIdsWhileFiltersUseNames() {
        fun contains(op: String, value: String) = listOf(Rule.Condition(op, "payee", RuleValue.Text(value)))
        val rule = { op: String, value: String -> Rule("r", Rule.Stage.DEFAULT, Rule.ConditionsOp.AND, contains(op, value),
            listOf(Rule.Action("set", "notes", RuleValue.Text("hit")))) }
        val context = RuleContext(payeeNames = mapOf("pay-1a" to "Amazon Shop"))
        val tx = transaction(payee = "pay-1a", payeeName = "Amazon Shop")
        fun run(op: String, value: String) = RulesEngine.apply(tx, listOf(rule(op, value)), context).transaction.notes
        assertEquals(null, run("contains", "Amazon"))
        assertEquals("hit", run("doesNotContain", "Amazon"))
        assertEquals(null, run("matches", "^amazon"))
        assertEquals("hit", run("contains", "1a"))
        // The id is lowercased but the value is not, as in upstream's Condition.eval.
        assertEquals(null, run("contains", "1A"))
        assertEquals("hit", run("matches", "^pay-"))
        // No payee matches nothing, including doesNotContain.
        assertEquals(null, RulesEngine.apply(transaction(), listOf(rule("doesNotContain", "x")), context).transaction.notes)
        // Report and transaction filters keep name matching.
        assertTrue(RulesEngine.matches(tx, contains("contains", "amazon"), context = context))
    }

    private fun transaction(
        amount: Long = -100, imported: String? = null, payee: String? = null,
        payeeName: String? = null, notes: String? = null, date: Int = 20260503, scheduleId: String? = null,
    ) = ActualTransaction("t", "a", date, amount, payee, payeeName, null, null, notes,
        false, false, null, false, null, false, null, imported, scheduleId, null)
}
