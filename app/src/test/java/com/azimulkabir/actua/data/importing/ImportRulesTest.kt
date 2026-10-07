package com.azimulkabir.actua.data.importing

import com.azimulkabir.actua.data.budget.model.ActualTransaction
import com.azimulkabir.actua.data.rules.Rule
import com.azimulkabir.actua.data.rules.RuleContext
import com.azimulkabir.actua.data.rules.RuleValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImportRulesTest {
    private val context = RuleContext(payeeNames = mapOf("grocer" to "Grocer"))
    private val existing = mapOf("grocer" to "grocer")

    private fun draft(id: String, payee: String?, notes: String? = null) = ActualTransaction(
        id, "checking", 20260915, -1_250, null, null, null, null, notes, false, false, null, false, null,
        false, null, payee, null, null,
    )

    private fun rule(id: String, stage: Rule.Stage, condition: Rule.Condition, vararg actions: Rule.Action) =
        Rule(id, stage, Rule.ConditionsOp.AND, listOf(condition), actions.toList())

    private fun run(drafts: List<ActualTransaction>, rules: List<Rule>): List<ImportRules.Prepared> {
        var next = 0
        return ImportRules.apply(drafts, rules, context, { existing[it.lowercase()] }, { "new-${next++}" })
    }

    @Test fun categorizeRuleAppliesToImportedRows() {
        val rules = listOf(rule("food", Rule.Stage.DEFAULT,
            Rule.Condition("contains", "imported_payee", RuleValue.Text("coffee")),
            Rule.Action("set", "category", RuleValue.Text("dining"))))

        val result = run(listOf(draft("t1", "Coffee Place")), rules).single()

        assertEquals("dining", result.transaction.categoryId)
        assertEquals("Coffee Place", result.createPayeeName)
        assertEquals("Coffee Place", result.transaction.importedPayee)
    }

    @Test fun existingPayeeIsResolvedBeforeRulesRun() {
        val rules = listOf(rule("grocer", Rule.Stage.DEFAULT,
            Rule.Condition("is", "payee", RuleValue.Text("grocer")),
            Rule.Action("set", "category", RuleValue.Text("food"))))

        val result = run(listOf(draft("t1", "GROCER")), rules).single()

        assertEquals("grocer", result.transaction.payeeId)
        assertEquals("food", result.transaction.categoryId)
        assertNull(result.createPayeeName)
    }

    @Test fun renamedPayeeLeavesNoOrphanPayee() {
        val rules = listOf(rule("rename", Rule.Stage.PRE,
            Rule.Condition("contains", "imported_payee", RuleValue.Text("amzn")),
            Rule.Action("set", "payee_name", RuleValue.Text("Amazon"))))

        val result = run(listOf(draft("t1", "AMZN Mktp 123")), rules).single()

        // Only the renamed payee is created; the bank name stays as imported_payee.
        assertEquals("Amazon", result.createPayeeName)
        assertNull(result.transaction.payeeId)
        assertEquals("AMZN Mktp 123", result.transaction.importedPayee)
    }

    @Test fun rowsWithTheSameNewNameShareOnePayeeAndDeletedRowsAreDropped() {
        val rules = listOf(rule("drop", Rule.Stage.DEFAULT,
            Rule.Condition("is", "notes", RuleValue.Text("skip")),
            Rule.Action("delete-transaction", null, RuleValue.Null)))

        val result = run(listOf(draft("t1", "Bakery"), draft("t2", "bakery"), draft("t3", "Bakery", notes = "skip")), rules)

        assertEquals(listOf("t1", "t2"), result.map { it.transaction.id })
        assertEquals(listOf("Bakery", "Bakery"), result.map { it.createPayeeName })
    }

    @Test fun rowWithoutPayeeStaysWithoutOne() {
        val result = run(listOf(draft("t1", null)), emptyList()).single()

        assertNull(result.transaction.payeeId)
        assertNull(result.createPayeeName)
    }

    @Test fun splitRuleReturnsChildRowsAndTheirPendingPayee() {
        val split = rule("split", Rule.Stage.DEFAULT,
            Rule.Condition("contains", "imported_payee", RuleValue.Text("Market")),
            Rule.Action("set-split-amount", null, RuleValue.Null, mapOf(
                "splitIndex" to RuleValue.Number(1.0), "method" to RuleValue.Text("remainder"),
            )),
            Rule.Action("set", "category", RuleValue.Text("food"), mapOf("splitIndex" to RuleValue.Number(1.0))),
            Rule.Action("set-split-amount", null, RuleValue.Null, mapOf(
                "splitIndex" to RuleValue.Number(2.0), "method" to RuleValue.Text("remainder"),
            )),
            Rule.Action("set", "category", RuleValue.Text("fun"), mapOf("splitIndex" to RuleValue.Number(2.0))),
        )

        val result = run(listOf(draft("split", "Market Purchase")), listOf(split)).single()

        assertEquals(true, result.transaction.isParent)
        assertEquals(2, result.splitChildren.size)
        assertEquals(listOf(-625L, -625L), result.splitChildren.map { it.transaction.amountCents })
        assertEquals(listOf("food", "fun"), result.splitChildren.map { it.transaction.categoryId })
        assertEquals(listOf("Market Purchase", "Market Purchase"), result.splitChildren.map { it.pendingPayeeName })
    }
}
