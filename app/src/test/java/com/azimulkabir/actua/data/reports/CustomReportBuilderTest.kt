package com.azimulkabir.actua.data.reports

import com.azimulkabir.actua.data.budget.model.ActualAccount
import com.azimulkabir.actua.data.budget.model.ActualAccountType
import com.azimulkabir.actua.data.budget.model.ActualCategory
import com.azimulkabir.actua.data.budget.model.ActualCategoryGroup
import com.azimulkabir.actua.data.budget.model.ActualTransaction
import com.azimulkabir.actua.data.rules.Rule
import com.azimulkabir.actua.data.rules.RuleValue
import com.azimulkabir.actua.model.ReportWidgetKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CustomReportBuilderTest {
    private val accounts = listOf(
        ActualAccount("a1", "Checking", ActualAccountType.CHECKING, false, false, 0.0, 0),
        ActualAccount("a2", "Savings", ActualAccountType.SAVINGS, false, false, 0.0, 0),
    )
    private val groups = listOf(
        ActualCategoryGroup(
            "g1", "Living", false, false, 1.0,
            listOf(
                ActualCategory("c1", "Groceries", "g1", false, false, 1.0),
                ActualCategory("c2", "Rent", "g1", false, false, 2.0),
            )
        ),
        ActualCategoryGroup(
            "g2", "Income", true, false, 2.0,
            listOf(
                ActualCategory("c3", "Salary", "g2", true, false, 1.0),
            )
        )
    )

    private fun tx(id: String, date: Int, amount: Long, cat: String, acct: String = "a1") =
        ActualTransaction(
            id, acct, date, amount, null, null, cat, null, null, false, false, null, false, null, false, null, null, null, null
        )

    private val transactions = listOf(
        tx("t1", 20260105, -5000, "c1"),   // Groceries -50.00
        tx("t2", 20260115, -120000, "c2"), // Rent -1200.00
        tx("t3", 20260120, 300000, "c3"),  // Salary +3000.00
        tx("t4", 20260205, -6000, "c1"),   // Groceries -60.00
        tx("t5", 20260215, -120000, "c2"), // Rent -1200.00
    )

    private val today = LocalDate.of(2026, 3, 1)

    private fun sampleRow(
        id: String = "rep-1",
        name: String = "Custom Report",
        graphType: String = "BarGraph",
        conditionsJson: String? = null,
        conditionsOp: String? = "and",
    ) = SavedReportRow(
        id = id,
        name = name,
        startDate = "2026-01-01",
        endDate = "2026-02-28",
        dateStatic = true,
        dateRange = null,
        groupBy = "Category",
        balanceType = "Payment",
        showOffBudget = false,
        showHidden = false,
        showUncategorized = false,
        selectedCategories = null,
        graphType = graphType,
        conditions = conditionsJson,
        conditionsOp = conditionsOp,
        interval = "Monthly",
        sortBy = "desc",
    )

    @Test
    fun `parseConditions round-trips condition list and op`() {
        val conditions = listOf(
            Rule.Condition("is", "category", RuleValue.Text("c1")),
            Rule.Condition("gt", "amount", RuleValue.Number(1000.0)),
            Rule.Condition("contains", "notes", RuleValue.Text("tax")),
        )
        val json = Rule("", Rule.Stage.DEFAULT, Rule.ConditionsOp.OR, conditions, emptyList()).conditionsJson
        val row = sampleRow(conditionsJson = json, conditionsOp = "or")
        val (parsed, op) = SavedReportEngine.parseConditions(row)

        assertEquals(3, parsed.size)
        assertEquals(Rule.ConditionsOp.OR, op)

        assertEquals("is", parsed[0].op)
        assertEquals("category", parsed[0].field)
        assertEquals("c1", parsed[0].value.text)

        assertEquals("gt", parsed[1].op)
        assertEquals("amount", parsed[1].field)
        assertEquals(1000.0, parsed[1].value.number)

        assertEquals("contains", parsed[2].op)
        assertEquals("notes", parsed[2].field)
        assertEquals("tax", parsed[2].value.text)
    }

    @Test
    fun `parseConditions handles empty or corrupt JSON gracefully`() {
        assertTrue(SavedReportEngine.parseConditions(sampleRow(conditionsJson = null)).first.isEmpty())
        assertTrue(SavedReportEngine.parseConditions(sampleRow(conditionsJson = "")).first.isEmpty())
        assertTrue(SavedReportEngine.parseConditions(sampleRow(conditionsJson = "invalid json")).first.isEmpty())
        assertTrue(SavedReportEngine.parseConditions(sampleRow(conditionsJson = "{}")).first.isEmpty())
    }

    @Test
    fun `compute preview sets customReportId and aggregates filtered transactions`() {
        val conditions = listOf(Rule.Condition("is", "category", RuleValue.Text("c1")))
        val json = Rule("", Rule.Stage.DEFAULT, Rule.ConditionsOp.AND, conditions, emptyList()).conditionsJson
        val report = sampleRow(
            id = "custom-rep-42",
            name = "Groceries Over Time",
            graphType = "BarGraph",
            conditionsJson = json,
        )

        val widget = SavedReportEngine.compute(report, transactions, accounts, groups, today)
        assertNotNull(widget)
        assertEquals("custom-rep-42", widget.customReportId)
        assertEquals("Groceries Over Time", widget.name)
        assertEquals(ReportWidgetKind.CUSTOM_REPORT, widget.kind)
        assertEquals("BarGraph", widget.graphType)
        // Only groceries: -5000 + -6000 = -11000 cents
        assertEquals(-11000L, widget.valueCents)
    }

    @Test
    fun `graphType in SavedReportRow is preserved on ReportWidget`() {
        listOf("DonutGraph", "BarGraph", "LineGraph", "AreaGraph", "StackedBarGraph").forEach { type ->
            val report = sampleRow(graphType = type)
            val widget = SavedReportEngine.compute(report, transactions, accounts, groups, today)
            assertEquals(type, widget.graphType)
        }
    }
}
