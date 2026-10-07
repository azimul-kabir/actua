package com.azimulkabir.actua.data.rules

import com.azimulkabir.actua.data.budget.model.ActualTransaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Upstream `migrateIds` (rules/rule-utils.ts): rules follow merged payee/category ids (#893). */
class RuleIdMappingTest {
    private val mappings = mapOf("old-store" to "store", "old-cafe" to "store", "old-food" to "food")

    @Test fun mapsIdConditionsAndSetActions() {
        val rule = Rule("r", Rule.Stage.DEFAULT, Rule.ConditionsOp.AND, listOf(
            Rule.Condition("is", "payee", RuleValue.Text("old-store")),
            Rule.Condition("isNot", "category", RuleValue.Text("old-food")),
            Rule.Condition("oneOf", "payee", RuleValue.ListValue(listOf(
                RuleValue.Text("old-store"), RuleValue.Text("old-cafe"), RuleValue.Text("other"),
            ))),
            Rule.Condition("notOneOf", "payee", RuleValue.ListValue(listOf(RuleValue.Text("old-cafe")))),
        ), listOf(
            Rule.Action("set", "payee", RuleValue.Text("old-store")),
            Rule.Action("set", "category", RuleValue.Text("old-food")),
        ))

        val mapped = rule.withMappedIds(mappings)

        assertEquals(RuleValue.Text("store"), mapped.conditions[0].value)
        assertEquals(RuleValue.Text("food"), mapped.conditions[1].value)
        assertEquals(RuleValue.ListValue(listOf(RuleValue.Text("store"), RuleValue.Text("other"))), mapped.conditions[2].value)
        assertEquals(RuleValue.ListValue(listOf(RuleValue.Text("store"))), mapped.conditions[3].value)
        assertEquals(RuleValue.Text("store"), mapped.actions[0].value)
        assertEquals(RuleValue.Text("food"), mapped.actions[1].value)
    }

    @Test fun leavesTextFieldsOtherOpsAndUnmappedIdsAlone() {
        val rule = Rule("r", Rule.Stage.DEFAULT, Rule.ConditionsOp.AND, listOf(
            Rule.Condition("is", "notes", RuleValue.Text("old-store")),
            Rule.Condition("contains", "payee", RuleValue.Text("old-store")),
            Rule.Condition("is", "payee", RuleValue.Text("live")),
        ), listOf(
            Rule.Action("set", "notes", RuleValue.Text("old-store")),
            Rule.Action("append-notes", null, RuleValue.Text("old-store")),
        ))

        assertEquals(rule, rule.withMappedIds(mappings))
        assertSame(rule, rule.withMappedIds(emptyMap()))
    }

    @Test fun mergedPayeeRuleMatchesTheTargetPayeesTransactions() {
        val rule = Rule("r", Rule.Stage.DEFAULT, Rule.ConditionsOp.AND,
            listOf(Rule.Condition("is", "payee", RuleValue.Text("old-store"))),
            listOf(Rule.Action("set", "category", RuleValue.Text("old-food"))),
        )
        val transaction = ActualTransaction(
            id = "t", accountId = "checking", date = 20260801, amountCents = -250,
            payeeId = "store", payeeName = "Store", categoryId = null, categoryName = null,
            notes = null, cleared = false, reconciled = false, transferId = null, isParent = false,
            parentId = null, tombstone = false, sortOrder = 1.0, importedPayee = null, scheduleId = null,
            transferAccountId = null,
        )

        assertEquals(null, RulesEngine.apply(transaction, listOf(rule)).transaction.categoryId)
        val result = RulesEngine.apply(transaction, listOf(rule.withMappedIds(mappings)))
        assertEquals("food", result.transaction.categoryId)
        assertTrue("category" in result.changedFields)
    }
}
