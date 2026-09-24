package com.azimulkabir.actua.data.reports

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class SavedReportEngineTest {
    private fun row(range: String?, static: Boolean = false, start: String? = null, end: String? = null) =
        SavedReportRow("r", "R", start, end, static, range, "Category", "Payment", false, false, true, null,
            "DonutGraph", null, "and", "Monthly")

    @Test fun `relative ranges resolve from today`() {
        val today = LocalDate.of(2026, 9, 22)
        assertEquals(LocalDate.of(2026, 6, 1) to LocalDate.of(2026, 8, 31), SavedReportEngine.dateRange(row("Last 3 months"), today))
        assertEquals(LocalDate.of(2026, 1, 1) to today, SavedReportEngine.dateRange(row("Year to date"), today))
    }

    @Test fun `static ranges use stored month bounds`() {
        val r = SavedReportEngine.dateRange(row(null, true, "2026-02", "2026-03"), LocalDate.of(2026, 9, 22))
        assertEquals(LocalDate.of(2026, 2, 1) to LocalDate.of(2026, 3, 31), r)
    }

    @Test fun `balance types map to upstream semantics`() {
        assertEquals(ReportBalanceType.DEBTS, SavedReportEngine.balanceType("Payment"))
        assertEquals(ReportBalanceType.ASSETS, SavedReportEngine.balanceType("Deposit"))
        assertEquals(ReportBalanceType.NET_ASSETS, SavedReportEngine.balanceType("Net"))
        assertEquals(ReportBalanceType.BUDGETED, SavedReportEngine.balanceType("Budgeted"))
    }
}

/** Regression coverage for https://github.com/azimul-kabir/actua/issues/546. */
class SavedReportBudgetedTest {
    private val groups = listOf(
        com.azimulkabir.actua.data.budget.model.ActualCategoryGroup("gi", "Income", true, false, 1.0,
            listOf(com.azimulkabir.actua.data.budget.model.ActualCategory("pay", "Pay", "gi", true, false, 1.0))),
        com.azimulkabir.actua.data.budget.model.ActualCategoryGroup("ge", "Bills", false, false, 2.0,
            listOf(
                com.azimulkabir.actua.data.budget.model.ActualCategory("groceries", "Groceries", "ge", false, false, 1.0),
                com.azimulkabir.actua.data.budget.model.ActualCategory("rent", "Rent", "ge", false, false, 2.0),
            )),
    )
    private val accounts = listOf(
        com.azimulkabir.actua.data.budget.model.ActualAccount("a", "A", com.azimulkabir.actua.data.budget.model.ActualAccountType.CHECKING, false, false, 0, 0),
    )
    private fun tx(id: String, date: Int, amount: Long, cat: String) = com.azimulkabir.actua.data.budget.model.ActualTransaction(
        id, "a", date, amount, null, null, cat, null, null, false, false, null, false, null, false, null, null, null, null)
    private fun categoryBudget(categoryId: String, name: String, groupId: String, groupName: String, budgeted: Long, spent: Long) =
        com.azimulkabir.actua.data.budget.model.ActualCategoryBudget(
            "2026-06", categoryId, name, groupId, groupName, 0.0, 0.0, budgeted, spent, 0, 0,
            false, false, null, false, false, null, null, null,
        )
    private fun incomeBudget(categoryId: String, name: String, groupName: String) =
        com.azimulkabir.actua.data.budget.model.ActualIncomeBudget("2026-06", categoryId, name, groupName, 0.0, 0, 0, false, false)
    private val budgetMonth = com.azimulkabir.actua.data.budget.model.ActualBudgetMonth(
        "2026-06",
        listOf(categoryBudget("groceries", "Groceries", "ge", "Bills", 50000, -12000), categoryBudget("rent", "Rent", "ge", "Bills", 150000, -150000)),
        listOf(incomeBudget("pay", "Pay", "Income")), 0, emptyList(), emptyList(),
    )
    private val saved = SavedReportRow("r", "R", "2026-06", "2026-06", true, null, "Category", "Budgeted", false, false, true,
        null, "DonutGraph", null, "and", "Monthly")

    @Test fun `reads budget-engine cells rather than summing transactions`() {
        // $500 budgeted to Groceries but only $120 spent - the report must show the budgeted
        // amount, not the transaction sum that the DEBTS branch would silently compute.
        val transactions = listOf(tx("1", 20260605, -12000, "groceries"))
        val w = SavedReportEngine.compute(saved, transactions, accounts, groups, budgetMonth = { budgetMonth })
        assertEquals(200000L, w.valueCents)
        assertEquals(setOf("Groceries" to 50000L, "Rent" to 150000L), w.categories.map { it.name to it.spentCents }.toSet())
    }

    @Test fun `excludes income categories`() {
        val monthWithIncome = budgetMonth.copy(
            categories = budgetMonth.categories + categoryBudget("pay", "Pay", "gi", "Income", 999999, 0),
        )
        val w = SavedReportEngine.compute(saved, emptyList(), accounts, groups, budgetMonth = { monthWithIncome })
        assertEquals(false, w.categories.any { it.name == "Pay" })
    }

    @Test fun `groups by category group when requested`() {
        val w = SavedReportEngine.compute(saved.copy(groupBy = "Group"), emptyList(), accounts, groups, budgetMonth = { budgetMonth })
        assertEquals(listOf("Bills" to 200000L), w.categories.map { it.name to it.spentCents })
    }

    @Test fun `no budget data for the month yields zero rather than falling back to transactions`() {
        val transactions = listOf(tx("1", 20260605, -12000, "groceries"))
        val w = SavedReportEngine.compute(saved, transactions, accounts, groups, budgetMonth = { null })
        assertEquals(0L, w.valueCents)
        assertEquals(emptyList<com.azimulkabir.actua.model.ReportCategory>(), w.categories)
    }
}

class SavedReportViewFilterTest {
    private val accounts = listOf(
        com.azimulkabir.actua.data.budget.model.ActualAccount("a", "A", com.azimulkabir.actua.data.budget.model.ActualAccountType.CHECKING, false, false, 0, 0),
        com.azimulkabir.actua.data.budget.model.ActualAccount("b", "B", com.azimulkabir.actua.data.budget.model.ActualAccountType.CHECKING, false, false, 1, 0),
    )
    private val groups = listOf(com.azimulkabir.actua.data.budget.model.ActualCategoryGroup("g", "G", false, false, 1.0,
        listOf(com.azimulkabir.actua.data.budget.model.ActualCategory("c", "C", "g", false, false, 1.0))))
    private fun tx(id: String, acct: String, date: Int, amount: Long) = com.azimulkabir.actua.data.budget.model.ActualTransaction(
        id, acct, date, amount, null, null, "c", null, null, false, false, null, false, null, false, null, null, null, null)
    private val saved = SavedReportRow("r", "R", "2026-01", "2026-01", true, null, "Category", "Payment", false, false, true,
        null, "DonutGraph", null, "and", "Monthly")
    private val rows = listOf(tx("1", "a", 20260110, -100), tx("2", "b", 20260111, -50), tx("3", "a", 20250601, -7))
    private val today = LocalDate.of(2026, 9, 22)

    @Test fun `account override narrows totals`() {
        val w = SavedReportEngine.compute(saved, rows, accounts, groups, today,
            com.azimulkabir.actua.model.ReportViewFilter(accountIds = setOf("a")))
        assertEquals(-100L, w.valueCents)
    }

    @Test fun `date preset overrides saved range`() {
        val w = SavedReportEngine.compute(saved, rows, accounts, groups, today,
            com.azimulkabir.actua.model.ReportViewFilter(datePreset = "All time"))
        assertEquals(-157L, w.valueCents)
    }
}

class IntervalPointsTest {
    private fun tx(date: Int, amount: Long) = com.azimulkabir.actua.data.budget.model.ActualTransaction(
        "t$date", "a", date, amount, null, null, null, null, null, false, false, null, false, null, false, null, null, null, null)
    private val today = LocalDate.of(2026, 3, 15)

    @Test fun `monthly buckets fill gaps and sum cents`() {
        val pts = SavedReportEngine.intervalPoints(listOf(tx(20260105, -100), tx(20260120, -1), tx(20260310, -5)),
            "Monthly", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), today)
        assertEquals(listOf("2026-01" to -101L, "2026-02" to 0L, "2026-03" to -5L), pts.map { it.period to it.primaryCents })
    }

    @Test fun `weekly buckets start on Sunday`() {
        val pts = SavedReportEngine.intervalPoints(listOf(tx(20260304, -10), tx(20260308, -20)),
            "Weekly", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 14), today)
        assertEquals(listOf("2026-03-01" to -10L, "2026-03-08" to -20L), pts.map { it.period to it.primaryCents })
    }
}

class IncomeExpenseTest {
    private val accounts = listOf(
        com.azimulkabir.actua.data.budget.model.ActualAccount("a", "A", com.azimulkabir.actua.data.budget.model.ActualAccountType.CHECKING, false, false, 0, 0),
        com.azimulkabir.actua.data.budget.model.ActualAccount("b", "B", com.azimulkabir.actua.data.budget.model.ActualAccountType.SAVINGS, false, false, 1, 0),
    )
    private val groups = listOf(
        com.azimulkabir.actua.data.budget.model.ActualCategoryGroup("gi", "Income", true, false, 1.0,
            listOf(com.azimulkabir.actua.data.budget.model.ActualCategory("pay", "Pay", "gi", true, false, 1.0))),
        com.azimulkabir.actua.data.budget.model.ActualCategoryGroup("ge", "Bills", false, false, 2.0,
            listOf(com.azimulkabir.actua.data.budget.model.ActualCategory("rent", "Rent", "ge", false, false, 1.0))),
    )
    private fun tx(id: String, date: Int, amount: Long, cat: String?, transferTo: String? = null) =
        com.azimulkabir.actua.data.budget.model.ActualTransaction(id, "a", date, amount, null, null, cat, null, null, false, false,
            transferTo?.let { "x" }, false, null, false, null, null, null, transferTo)

    @Test fun `classifies by category, nets refunds and ignores transfers`() {
        val rows = listOf(
            tx("1", 20260105, 300000, "pay"), tx("2", 20260106, -100000, "rent"), tx("3", 20260107, 2500, "rent"),
            tx("4", 20260108, -50000, null, transferTo = "b"),
        )
        val today = LocalDate.of(2026, 1, 20)
        val w = SavedReportEngine.incomeExpense(rows, com.azimulkabir.actua.model.ReportViewFilter("This month"), today,
            SavedReportEngine.Shared(rows, accounts, groups))
        assertEquals(300000L, w.categories[0].spentCents)
        assertEquals(97500L, w.categories[1].spentCents)
        assertEquals(202500L, w.valueCents)
        assertEquals(listOf("1"), w.categories[0].transactionIds)
        assertEquals(300000L to 97500L, w.points.single().primaryCents to w.points.single().secondaryCents)
    }
}

class StackedIntervalPointsTest {
    private val accounts = listOf(
        com.azimulkabir.actua.data.budget.model.ActualAccount("a", "A", com.azimulkabir.actua.data.budget.model.ActualAccountType.CHECKING, false, false, 0, 0),
    )
    private val groups = listOf(
        com.azimulkabir.actua.data.budget.model.ActualCategoryGroup("g", "G", false, false, 1.0,
            listOf(
                com.azimulkabir.actua.data.budget.model.ActualCategory("rent", "Rent", "g", false, false, 1.0),
                com.azimulkabir.actua.data.budget.model.ActualCategory("food", "Food", "g", false, false, 2.0),
            )),
    )
    private fun tx(id: String, date: Int, amount: Long, cat: String) = com.azimulkabir.actua.data.budget.model.ActualTransaction(
        id, "a", date, amount, null, null, cat, null, null, false, false, null, false, null, false, null, null, null, null)
    private val today = LocalDate.of(2026, 3, 15)
    private val saved = SavedReportRow("r", "R", null, null, false, "Last 3 months", "Category", "Payment", false, false, true,
        null, "StackedBarGraph", null, "and", "Monthly", mode = "time")

    @Test fun `stacked bar mode carries a per-category breakdown for each interval`() {
        val rows = listOf(
            tx("1", 20260105, -1000, "rent"), tx("2", 20260110, -200, "food"),
            tx("3", 20260210, -1200, "rent"),
        )
        val w = SavedReportEngine.compute(saved, rows, accounts, groups, today)
        assertEquals(true, w.timeMode)
        val jan = w.points.first { it.period == "2026-01" }
        assertEquals(setOf("Rent", "Food"), jan.segments.map { it.name }.toSet())
        assertEquals(-1000L, jan.segments.first { it.name == "Rent" }.spentCents)
        assertEquals(-200L, jan.segments.first { it.name == "Food" }.spentCents)
        assertEquals(listOf("1"), jan.segments.first { it.name == "Rent" }.transactionIds)

        // A category with no activity that period is zero-filled rather than dropped, so every
        // bar keeps the same stack order and the same set of categories.
        val feb = w.points.first { it.period == "2026-02" }
        assertEquals(-1200L, feb.segments.first { it.name == "Rent" }.spentCents)
        assertEquals(0L, feb.segments.first { it.name == "Food" }.spentCents)
        assertEquals(emptyList<String>(), feb.segments.first { it.name == "Food" }.transactionIds)
    }

    @Test fun `non-stacked graph types keep the flat per-interval total with no segments`() {
        val rows = listOf(tx("1", 20260105, -1000, "rent"), tx("2", 20260110, -200, "food"))
        val w = SavedReportEngine.compute(saved.copy(graphType = "BarGraph"), rows, accounts, groups, today)
        assertEquals(-1200L, w.points.first { it.period == "2026-01" }.primaryCents)
        assertEquals(true, w.points.all { it.segments.isEmpty() })
    }
}

class ViewFilterGroupTest {
    @Test fun `default only when nothing is overridden`() {
        assertEquals(true, com.azimulkabir.actua.model.ReportViewFilter().isDefault)
        assertEquals(false, com.azimulkabir.actua.model.ReportViewFilter(categoryGroupIds = setOf("g")).isDefault)
        assertEquals(false, com.azimulkabir.actua.model.ReportViewFilter(includeOffBudget = true).isDefault)
    }
}
