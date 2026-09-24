package com.azimulkabir.actua.data.reports

import com.azimulkabir.actua.data.budget.model.ActualCategory
import com.azimulkabir.actua.data.budget.model.ActualCategoryGroup
import com.azimulkabir.actua.data.budget.model.ActualTransaction
import com.azimulkabir.actua.data.rules.RuleContext
import com.azimulkabir.actua.model.ReportWidgetKind
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class SankeyTest {
    private val groups = listOf(
        ActualCategoryGroup("gi", "Income", true, false, 1.0, listOf(
            ActualCategory("salary", "Salary", "gi", true, false, 1.0),
            ActualCategory("bonus", "Bonus", "gi", true, false, 2.0),
        )),
        ActualCategoryGroup("ge", "Bills", false, false, 2.0, listOf(
            ActualCategory("rent", "Rent", "ge", false, false, 1.0),
        )),
    )
    private val context = RuleContext(
        categoryNames = groups.flatMap { it.categories }.associate { it.id to it.name },
        categoryGroupIds = groups.flatMap { it.categories }.associate { it.id to it.groupId },
        categoryGroupNames = groups.associate { it.id to it.name },
    )
    private val incomeCategoryIds = groups.flatMap { it.categories }.filter { it.isIncome }.mapTo(mutableSetOf()) { it.id }

    private fun tx(id: String, date: Int, amount: Long, cat: String?) = ActualTransaction(
        id, "a", date, amount, null, null, cat, null, null, false, false, null, false, null, false, null, null, null, null)

    private fun row(meta: String? = null) = DashboardWidgetRow("w", "sankey-card", meta)

    @Test fun `breaks income down by source category, unlike the group-level expense breakdown`() {
        val rows = listOf(
            tx("1", 20260405, 800000, "salary"),
            tx("2", 20260406, 200000, "bonus"),
            tx("3", 20260410, -300000, "rent"),
        )
        val widget = CoreReportEngine.compute(
            row("""{"timeFrame":{"mode":"static","start":"2026-04","end":"2026-04"}}"""),
            rows, context, incomeCategoryIds,
        )
        assertEquals(ReportWidgetKind.SANKEY, widget.kind)
        assertEquals(1000000L, widget.valueCents)
        assertEquals(listOf("Salary" to 800000L, "Bonus" to 200000L), widget.incomeCategories.map { it.name to it.spentCents })
        assertEquals(listOf("Bills" to 300000L), widget.categories.map { it.name to it.spentCents })
    }

    @Test fun `sets a subtitle with the resolved date range`() {
        val widget = CoreReportEngine.compute(
            row("""{"timeFrame":{"mode":"static","start":"2026-04","end":"2026-09"}}"""),
            emptyList(), context, incomeCategoryIds,
        )
        assertEquals("Apr 2026 - Sep 2026", widget.subtitle)
    }

    @Test fun `single-month range collapses the subtitle to one month`() {
        val widget = CoreReportEngine.compute(
            row("""{"timeFrame":{"mode":"static","start":"2026-04","end":"2026-04"}}"""),
            emptyList(), context, incomeCategoryIds,
        )
        assertEquals("Apr 2026", widget.subtitle)
    }
}

/**
 * PWA's own spending query (`spending-spreadsheet.ts`/`makeQuery.ts`) has no transfer exclusion —
 * it only drops the leg whose own account is off-budget or whose category is income. Actua used to
 * additionally hardcode `transferAccountId == null`, which silently dropped transfers PWA counts
 * (see actua#531).
 */
class SpendingTest {
    private val context = RuleContext(offBudgetAccountIds = setOf("off"))

    private fun tx(id: String, accountId: String, date: Int, amount: Long, transferAccountId: String?) =
        ActualTransaction(id, accountId, date, amount, null, null, null, null, null, false, false, null,
            false, null, false, null, null, null, transferAccountId)

    private fun row(meta: String? = null) = DashboardWidgetRow("w", "spending-card", meta)

    @Test fun `counts an on-budget-to-on-budget transfer leg as spending, matching PWA's hardcoded filters`() {
        val rows = listOf(tx("1", "checking", 20260405, -50000, transferAccountId = "savings"))
        val widget = CoreReportEngine.compute(
            row("""{"isLive":false,"compare":"2026-04"}"""), rows, context,
            today = LocalDate.of(2026, 4, 20),
        )
        assertEquals(ReportWidgetKind.SPENDING, widget.kind)
        assertEquals(50000L, widget.valueCents)
    }

    @Test fun `excludes only the leg whose own account is off-budget, not the whole transfer`() {
        val rows = listOf(
            tx("1", "checking", 20260405, -50000, transferAccountId = "off"),
            tx("2", "off", 20260405, 50000, transferAccountId = "checking"),
        )
        val widget = CoreReportEngine.compute(
            row("""{"isLive":false,"compare":"2026-04"}"""), rows, context,
            today = LocalDate.of(2026, 4, 20),
        )
        assertEquals(50000L, widget.valueCents)
    }
}
