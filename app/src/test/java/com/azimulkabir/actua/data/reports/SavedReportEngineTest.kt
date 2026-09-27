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

    @Test fun `include_current extends last-N ranges through the current interval`() {
        // Upstream getLiveRange: "Last 6 months" with include_current spans Mar-Sep, not Mar-Aug (actua#637).
        val today = LocalDate.of(2026, 9, 27)
        val included = row("Last 6 months").copy(includeCurrent = true)
        assertEquals(LocalDate.of(2026, 3, 1) to LocalDate.of(2026, 8, 31), SavedReportEngine.dateRange(row("Last 6 months"), today))
        assertEquals(LocalDate.of(2026, 3, 1) to LocalDate.of(2026, 9, 30), SavedReportEngine.dateRange(included, today))
        assertEquals(LocalDate.of(2026, 8, 1) to LocalDate.of(2026, 9, 30),
            SavedReportEngine.dateRange(row("Last month").copy(includeCurrent = true), today))
        // Sunday-start weeks: today is Sunday 2026-09-27.
        assertEquals(LocalDate.of(2026, 9, 20) to LocalDate.of(2026, 9, 26), SavedReportEngine.dateRange(row("Last week"), today))
        assertEquals(LocalDate.of(2026, 9, 20) to LocalDate.of(2026, 10, 3),
            SavedReportEngine.dateRange(row("Last week").copy(includeCurrent = true), today))
    }

    @Test fun `week, quarter and 30-day presets resolve live instead of falling back to stored dates`() {
        val today = LocalDate.of(2026, 9, 23)
        val stale = { range: String -> row(range, start = "2025-01", end = "2025-01") }
        assertEquals(LocalDate.of(2026, 9, 20) to LocalDate.of(2026, 9, 26), SavedReportEngine.dateRange(stale("This week"), today))
        assertEquals(LocalDate.of(2026, 9, 13) to LocalDate.of(2026, 9, 19), SavedReportEngine.dateRange(stale("Last week"), today))
        assertEquals(LocalDate.of(2026, 7, 1) to LocalDate.of(2026, 9, 30), SavedReportEngine.dateRange(stale("Current quarter"), today))
        assertEquals(LocalDate.of(2026, 4, 1) to LocalDate.of(2026, 6, 30), SavedReportEngine.dateRange(stale("Previous quarter"), today))
        assertEquals(LocalDate.of(2026, 8, 25) to today, SavedReportEngine.dateRange(stale("Last 30 days"), today))
    }

    @Test fun `static ranges use stored month bounds`() {
        val r = SavedReportEngine.dateRange(row(null, true, "2026-02", "2026-03"), LocalDate.of(2026, 9, 22))
        assertEquals(LocalDate.of(2026, 2, 1) to LocalDate.of(2026, 3, 31), r)
    }

    @Test fun `balance types map upstream's stored keys and format literals`() {
        // Upstream ReportOptions.ts balanceTypeOptions: key -> format (actua#646).
        mapOf(
            "Payment" to ReportBalanceType.DEBTS, "totalDebts" to ReportBalanceType.DEBTS,
            "Deposit" to ReportBalanceType.ASSETS, "totalAssets" to ReportBalanceType.ASSETS,
            "Net" to ReportBalanceType.NET, "totalTotals" to ReportBalanceType.NET,
            "Net Payment" to ReportBalanceType.NET_DEBTS, "netDebts" to ReportBalanceType.NET_DEBTS,
            "Net Deposit" to ReportBalanceType.NET_ASSETS, "netAssets" to ReportBalanceType.NET_ASSETS,
            "Budgeted" to ReportBalanceType.BUDGETED, "totalBudgeted" to ReportBalanceType.BUDGETED,
            "" to ReportBalanceType.DEBTS,
        ).forEach { (stored, expected) -> assertEquals(stored, expected, SavedReportEngine.balanceType(stored)) }
    }
}

/**
 * Regression coverage for https://github.com/azimul-kabir/actua/issues/646: upstream's
 * `totalTotals`/`netAssets`/`netDebts` (recalculate.ts, custom-spreadsheet.ts, filterEmptyRows.ts).
 */
class SavedReportNetBalanceTest {
    private val accounts = listOf(
        com.azimulkabir.actua.data.budget.model.ActualAccount("a", "A", com.azimulkabir.actua.data.budget.model.ActualAccountType.CHECKING, false, false, 0.0, 0),
    )
    private val groups = listOf(
        com.azimulkabir.actua.data.budget.model.ActualCategoryGroup("gi", "Income", true, false, 1.0,
            listOf(com.azimulkabir.actua.data.budget.model.ActualCategory("pay", "Pay", "gi", true, false, 1.0))),
        com.azimulkabir.actua.data.budget.model.ActualCategoryGroup("ge", "Bills", false, false, 2.0,
            listOf(
                com.azimulkabir.actua.data.budget.model.ActualCategory("groceries", "Groceries", "ge", false, false, 1.0),
                com.azimulkabir.actua.data.budget.model.ActualCategory("rent", "Rent", "ge", false, false, 2.0),
            )),
    )
    private fun tx(id: String, date: Int, amount: Long, cat: String) = com.azimulkabir.actua.data.budget.model.ActualTransaction(
        id, "a", date, amount, null, null, cat, null, null, false, false, null, false, null, false, null, null, null, null)
    private val today = LocalDate.of(2026, 3, 15)
    private fun saved(balanceType: String, graphType: String = "BarGraph", mode: String = "total") =
        SavedReportRow("r", "R", "2026-01", "2026-02", true, null, "Category", balanceType, false, false, true,
            null, graphType, null, "and", "Monthly", mode = mode)

    // Jan nets +185,000 (pay 300,000, rent -100,000, groceries -20,000 with a 5,000 refund);
    // Feb nets -110,000. The whole range nets +75,000.
    private val rows = listOf(
        tx("1", 20260105, 300000, "pay"), tx("2", 20260106, -100000, "rent"),
        tx("3", 20260107, -20000, "groceries"), tx("4", 20260108, 5000, "groceries"),
        tx("5", 20260205, -100000, "rent"), tx("6", 20260210, -10000, "groceries"),
    )
    private fun compute(row: SavedReportRow, transactions: List<com.azimulkabir.actua.data.budget.model.ActualTransaction> = rows) =
        SavedReportEngine.compute(row, transactions, accounts, groups, today)

    @Test fun `Net is the signed sum per group, per interval and overall`() {
        val w = compute(saved("Net"))
        assertEquals(75000L, w.valueCents)
        assertEquals(
            setOf("Pay" to 300000L, "Rent" to -200000L, "Groceries" to -25000L),
            w.categories.map { it.name to it.spentCents }.toSet(),
        )
        assertEquals(listOf(185000L, -110000L), w.points.map { it.primaryCents })
    }

    @Test fun `Net Deposit keeps positive nets and sums per-interval clamped totals`() {
        val w = compute(saved("Net Deposit"))
        // Rent and Groceries net negative, so upstream's filterEmptyRows hides them.
        assertEquals(listOf("Pay" to 300000L), w.categories.map { it.name to it.spentCents })
        assertEquals(listOf(185000L, 0L), w.points.map { it.primaryCents })
        // max(185,000, 0) + max(-110,000, 0), not max(75,000, 0).
        assertEquals(185000L, w.valueCents)
    }

    @Test fun `Net Payment keeps negative nets, netting refunds, and sums per-interval clamped totals`() {
        val w = compute(saved("Net Payment"))
        // Groceries nets its refund (-30,000 + 5,000); Payment (DEBTS) would have ignored it.
        assertEquals(
            listOf("Rent" to -200000L, "Groceries" to -25000L),
            w.categories.map { it.name to it.spentCents },
        )
        assertEquals(listOf(0L, -110000L), w.points.map { it.primaryCents })
        assertEquals(-110000L, w.valueCents)
        assertEquals(listOf("3", "4", "6"), w.categories.first { it.name == "Groceries" }.transactionIds.sorted())
    }

    @Test fun `a group that nets to zero stays in Net but drops out of Net Deposit and Net Payment`() {
        val wash = listOf(tx("1", 20260105, -500, "groceries"), tx("2", 20260106, 500, "groceries"))
        assertEquals(listOf("Groceries" to 0L), compute(saved("Net"), wash).categories.map { it.name to it.spentCents })
        assertEquals(emptyList<String>(), compute(saved("Net Deposit"), wash).categories.map { it.name })
        assertEquals(emptyList<String>(), compute(saved("Net Payment"), wash).categories.map { it.name })
    }

    @Test fun `Net Deposit and Net Payment summaries total and average the per-interval clamped nets`() {
        val deposit = compute(saved("Net Deposit")).summary!!
        assertEquals(com.azimulkabir.actua.model.ReportSummaryKind.NET_DEPOSIT, deposit.kind)
        assertEquals(185000L to 92500L, deposit.totalCents to deposit.averageCents)
        val payment = compute(saved("Net Payment")).summary!!
        assertEquals(com.azimulkabir.actua.model.ReportSummaryKind.NET_PAYMENT, payment.kind)
        assertEquals(-110000L to -55000L, payment.totalCents to payment.averageCents)
    }

    @Test fun `stacked Net Payment clamps each group's net per interval`() {
        val stackedRows = listOf(
            tx("1", 20260105, 3000, "groceries"), tx("2", 20260106, -1000, "rent"),
            tx("3", 20260210, -10000, "groceries"),
        )
        val w = compute(saved("Net Payment", graphType = "StackedBarGraph", mode = "time"), stackedRows)
        val jan = w.points.first { it.period == "2026-01" }.segments.associate { it.name to it.spentCents }
        val feb = w.points.first { it.period == "2026-02" }.segments.associate { it.name to it.spentCents }
        // Groceries' January refund nets positive, so it contributes nothing to that month's payments.
        assertEquals(mapOf("Groceries" to 0L, "Rent" to -1000L), jan)
        assertEquals(mapOf("Groceries" to -10000L, "Rent" to 0L), feb)
        // The total clamps each interval's net across all groups: January nets +2,000, so only
        // February's -10,000 counts, even though Rent's January segment shows -1,000.
        assertEquals(-10000L, w.valueCents)
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
        com.azimulkabir.actua.data.budget.model.ActualAccount("a", "A", com.azimulkabir.actua.data.budget.model.ActualAccountType.CHECKING, false, false, 0.0, 0),
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
        assertEquals(com.azimulkabir.actua.model.ReportSummaryKind.BUDGETED, w.summary?.kind)
        assertEquals(200000L, w.summary?.totalCents)
        assertEquals(200000L, w.summary?.averageCents)
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
        com.azimulkabir.actua.data.budget.model.ActualAccount("a", "A", com.azimulkabir.actua.data.budget.model.ActualAccountType.CHECKING, false, false, 0.0, 0),
        com.azimulkabir.actua.data.budget.model.ActualAccount("b", "B", com.azimulkabir.actua.data.budget.model.ActualAccountType.CHECKING, false, false, 1.0, 0),
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

/** Regression coverage for https://github.com/azimul-kabir/actua/issues/547. */
class SavedReportTransferTest {
    private val accounts = listOf(
        com.azimulkabir.actua.data.budget.model.ActualAccount("chk", "Checking", com.azimulkabir.actua.data.budget.model.ActualAccountType.CHECKING, false, false, 0.0, 0),
        com.azimulkabir.actua.data.budget.model.ActualAccount("sav", "Savings", com.azimulkabir.actua.data.budget.model.ActualAccountType.SAVINGS, false, false, 1.0, 0),
    )
    private val groups = listOf(com.azimulkabir.actua.data.budget.model.ActualCategoryGroup("ge", "Bills", false, false, 1.0,
        listOf(com.azimulkabir.actua.data.budget.model.ActualCategory("rent", "Rent", "ge", false, false, 1.0))))
    private fun tx(id: String, date: Int, amount: Long, cat: String?, transferTo: String? = null) =
        com.azimulkabir.actua.data.budget.model.ActualTransaction(id, "chk", date, amount, null, null, cat, null, null, false, false,
            transferTo?.let { "x" }, false, null, false, null, null, null, transferTo)

    // groupBy Category, balanceType Net (defaults), showUncategorized true - a default, unedited saved report.
    private val saved = SavedReportRow("r", "R", "2026-01", "2026-01", true, null, "Category", "Net", false, false, true,
        null, "DonutGraph", null, "and", "Monthly")

    @Test fun `on-budget-to-on-budget transfer joins the Transfers bucket and the total, like upstream`() {
        val rows = listOf(tx("1", 20260110, -100000, "rent"), tx("2", 20260112, -100000, null, transferTo = "sav"))
        val w = SavedReportEngine.compute(saved, rows, accounts, groups)
        assertEquals(-200000L, w.valueCents)
        assertEquals(
            setOf("Rent" to -100000L, "Transfers" to -100000L),
            w.categories.map { it.name to it.spentCents }.toSet(),
        )
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
        com.azimulkabir.actua.data.budget.model.ActualAccount("a", "A", com.azimulkabir.actua.data.budget.model.ActualAccountType.CHECKING, false, false, 0.0, 0),
        com.azimulkabir.actua.data.budget.model.ActualAccount("b", "B", com.azimulkabir.actua.data.budget.model.ActualAccountType.SAVINGS, false, false, 1.0, 0),
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
        com.azimulkabir.actua.data.budget.model.ActualAccount("a", "A", com.azimulkabir.actua.data.budget.model.ActualAccountType.CHECKING, false, false, 0.0, 0),
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

/** Upstream `ReportSummary.tsx` total and per-interval average (actua#644). */
class SavedReportSummaryTest {
    private val accounts = listOf(
        com.azimulkabir.actua.data.budget.model.ActualAccount("a", "A", com.azimulkabir.actua.data.budget.model.ActualAccountType.CHECKING, false, false, 0.0, 0),
    )
    private val groups = listOf(com.azimulkabir.actua.data.budget.model.ActualCategoryGroup("g", "G", false, false, 1.0,
        listOf(com.azimulkabir.actua.data.budget.model.ActualCategory("c", "C", "g", false, false, 1.0))))
    private fun tx(id: String, date: Int, amount: Long) = com.azimulkabir.actua.data.budget.model.ActualTransaction(
        id, "a", date, amount, null, null, "c", null, null, false, false, null, false, null, false, null, null, null, null)
    private fun report(range: String, balanceType: String = "Payment", interval: String = "Monthly", includeCurrent: Boolean = false) =
        SavedReportRow("r", "R", null, null, false, range, "Interval", balanceType, false, false, true,
            null, "BarGraph", null, "and", interval, mode = "time", includeCurrent = includeCurrent)
    private val today = LocalDate.of(2026, 9, 27)

    @Test fun `average rounds like JavaScript Math round in integer cents`() {
        assertEquals(333L, SavedReportEngine.roundedAverage(1000, 3))
        assertEquals(-333L, SavedReportEngine.roundedAverage(-1000, 3))
        assertEquals(3L, SavedReportEngine.roundedAverage(5, 2))
        assertEquals(-2L, SavedReportEngine.roundedAverage(-5, 2))
        assertEquals(0L, SavedReportEngine.roundedAverage(0, 6))
    }

    @Test fun `interval count spans the whole range inclusively`() {
        assertEquals(6, SavedReportEngine.intervalCount("Monthly", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 8, 31)))
        assertEquals(2, SavedReportEngine.intervalCount("Yearly", LocalDate.of(2025, 1, 1), LocalDate.of(2026, 12, 31)))
        // Sunday-start weeks: 2026-09-20 and 2026-09-27.
        assertEquals(2, SavedReportEngine.intervalCount("Weekly", LocalDate.of(2026, 9, 20), LocalDate.of(2026, 10, 3)))
        assertEquals(30, SavedReportEngine.intervalCount("Daily", LocalDate.of(2026, 8, 25), LocalDate.of(2026, 9, 23)))
        assertEquals(1, SavedReportEngine.intervalCount("Monthly", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 8, 1)))
    }

    @Test fun `include_current adds the current month to the average's interval count`() {
        val rows = listOf(tx("1", 20260310, -6000), tx("2", 20260915, -1000))
        val live = SavedReportEngine.compute(report("Last 6 months", includeCurrent = true), rows, accounts, groups, today)
        val summary = live.summary!!
        assertEquals(com.azimulkabir.actua.model.ReportSummaryKind.SPENDING, summary.kind)
        assertEquals(-7000L, summary.totalCents)
        assertEquals(live.points.sumOf { it.primaryCents }, summary.totalCents)
        assertEquals(7, summary.intervalCount)
        assertEquals(-1000L, summary.averageCents)

        val excluded = SavedReportEngine.compute(report("Last 6 months"), rows, accounts, groups, today).summary!!
        assertEquals(6, excluded.intervalCount)
        assertEquals(-6000L, excluded.totalCents)
        assertEquals(-1000L, excluded.averageCents)
    }

    @Test fun `yearly interval averages per year`() {
        val rows = listOf(tx("1", 20250110, -1001), tx("2", 20251215, -2000))
        val summary = SavedReportEngine.compute(report("Last year", interval = "Yearly"), rows, accounts, groups, today).summary!!
        assertEquals(1, summary.intervalCount)
        assertEquals("Yearly", summary.interval)
        assertEquals(-3001L, summary.averageCents)
    }

    @Test fun `net reports are labelled by whichever side dominates`() {
        val kind = { rows: List<com.azimulkabir.actua.data.budget.model.ActualTransaction>, type: String ->
            SavedReportEngine.compute(report("Last 6 months", balanceType = type), rows, accounts, groups, today).summary!!.kind
        }
        val spendHeavy = listOf(tx("1", 20260410, -5000), tx("2", 20260411, 2000))
        val incomeHeavy = listOf(tx("1", 20260410, -2000), tx("2", 20260411, 5000))
        assertEquals(com.azimulkabir.actua.model.ReportSummaryKind.NET_PAYMENT, kind(spendHeavy, "Net"))
        assertEquals(com.azimulkabir.actua.model.ReportSummaryKind.NET_DEPOSIT, kind(incomeHeavy, "Net"))
        assertEquals(com.azimulkabir.actua.model.ReportSummaryKind.NET_DEPOSIT, kind(spendHeavy, "netAssets"))
        assertEquals(com.azimulkabir.actua.model.ReportSummaryKind.NET_PAYMENT, kind(incomeHeavy, "netDebts"))
        // The stored keys resolve the same way as their format literals (actua#646).
        assertEquals(com.azimulkabir.actua.model.ReportSummaryKind.NET_DEPOSIT, kind(spendHeavy, "Net Deposit"))
        assertEquals(com.azimulkabir.actua.model.ReportSummaryKind.NET_PAYMENT, kind(incomeHeavy, "Net Payment"))
        assertEquals(com.azimulkabir.actua.model.ReportSummaryKind.DEPOSITS, kind(incomeHeavy, "Deposit"))
    }

    @Test fun `empty range yields a zero summary`() {
        val summary = SavedReportEngine.compute(report("Last 3 months"), emptyList(), accounts, groups, today).summary!!
        assertEquals(0L, summary.totalCents)
        assertEquals(0L, summary.averageCents)
        assertEquals(3, summary.intervalCount)
    }

    @Test fun `all time counts intervals from the earliest transaction, not the 1900 sentinel`() {
        val rows = listOf(tx("1", 20250610, -1600), tx("2", 20260110, -1600))
        val summary = SavedReportEngine.compute(report("All time"), rows, accounts, groups, LocalDate.of(2026, 9, 22)).summary!!
        // June 2025 through September 2026.
        assertEquals(16, summary.intervalCount)
        assertEquals(-200L, summary.averageCents)
    }
}
