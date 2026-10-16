package com.azimulkabir.actua.data.reports

import com.azimulkabir.actua.data.budget.model.ActualAccount
import com.azimulkabir.actua.data.budget.model.ActualAccountType
import com.azimulkabir.actua.data.budget.model.ActualCategory
import com.azimulkabir.actua.data.budget.model.ActualCategoryGroup
import com.azimulkabir.actua.data.budget.model.ActualTransaction
import com.azimulkabir.actua.data.rules.RuleContext
import com.azimulkabir.actua.model.DrillDownDirection
import com.azimulkabir.actua.model.DrillDownQuery
import com.azimulkabir.actua.model.ReportWidgetKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DrillDownQueryUnitTest {
    private val accounts = listOf(
        ActualAccount("chk", "Checking", ActualAccountType.CHECKING, offBudget = false, closed = false, sortOrder = 1.0, balanceCents = 0),
        ActualAccount("sav", "Savings", ActualAccountType.SAVINGS, offBudget = false, closed = false, sortOrder = 2.0, balanceCents = 0),
        ActualAccount("inv", "Investment", ActualAccountType.INVESTMENT, offBudget = true, closed = false, sortOrder = 3.0, balanceCents = 0),
    )
    private val onBudgetAccountIds = accounts.filterNot { it.offBudget }.map { it.id }.toSet()

    private val groups = listOf(
        ActualCategoryGroup("gi", "Income", true, false, 1.0, listOf(
            ActualCategory("salary", "Salary", "gi", true, false, 1.0),
            ActualCategory("bonus", "Bonus", "gi", true, false, 2.0),
        )),
        ActualCategoryGroup("ge", "Bills", false, false, 2.0, listOf(
            ActualCategory("rent", "Rent", "ge", false, false, 1.0),
            ActualCategory("groceries", "Groceries", "ge", false, false, 2.0),
        )),
    )
    private val incomeCategoryIds = groups.flatMap { it.categories }.filter { it.isIncome }.mapTo(mutableSetOf()) { it.id }

    private val context = RuleContext(
        categoryNames = groups.flatMap { it.categories }.associate { it.id to it.name },
        categoryGroupIds = groups.flatMap { it.categories }.associate { it.id to it.groupId },
        categoryGroupNames = groups.associate { it.id to it.name },
        accountNames = accounts.associate { it.id to it.name },
        offBudgetAccountIds = accounts.filter { it.offBudget }.map { it.id }.toSet(),
    )

    private fun tx(
        id: String,
        acct: String,
        date: Int,
        amount: Long,
        cat: String? = null,
        payee: String? = null,
        transferAccount: String? = null,
        transferId: String? = null,
        isParent: Boolean = false,
        parentId: String? = null,
    ) = ActualTransaction(
        id = id,
        accountId = acct,
        date = date,
        amountCents = amount,
        payeeId = payee,
        payeeName = payee,
        categoryId = cat,
        categoryName = context.categoryNames[cat],
        notes = null,
        cleared = true,
        reconciled = false,
        transferId = transferId,
        isParent = isParent,
        parentId = parentId,
        tombstone = false,
        sortOrder = 0.0,
        importedPayee = null,
        scheduleId = null,
        transferAccountId = transferAccount,
    )

    private fun filterSynthetic(
        transactions: List<ActualTransaction>,
        query: DrillDownQuery,
    ): List<ActualTransaction> {
        return transactions.filter { t ->
            if (t.isParent) return@filter false
            if (query.startDate != null && t.date < query.startDate) return@filter false
            if (query.endDate != null && t.date > query.endDate) return@filter false
            if (!query.accountIds.isNullOrEmpty() && t.accountId !in query.accountIds) return@filter false
            if (!query.categoryIds.isNullOrEmpty() && (t.categoryId == null || t.categoryId !in query.categoryIds)) return@filter false
            if (query.payeeId != null && t.payeeId != query.payeeId) return@filter false
            if (query.excludeTransfers && (t.transferAccountId != null || t.transferId != null)) return@filter false
            if (query.onBudgetOnly && t.accountId !in onBudgetAccountIds) return@filter false
            if (query.excludeIncomeCategories && t.categoryId != null && t.categoryId in incomeCategoryIds) return@filter false
            when (query.direction) {
                DrillDownDirection.INCOME -> if (t.amountCents < 0) return@filter false
                DrillDownDirection.EXPENSE -> if (t.amountCents >= 0) return@filter false
                DrillDownDirection.ANY -> Unit
            }
            true
        }
    }

    @Test
    fun `cash flow report drill down query matches point and widget figures`() {
        val today = LocalDate.of(2026, 6, 15)
        val transactions = listOf(
            tx("1", "chk", 20260601, 500_000, "salary"),
            tx("2", "chk", 20260605, -150_000, "rent"),
            tx("3", "chk", 20260610, -50_000, "groceries"),
            // On-budget transfer: between checking and savings
            tx("4", "chk", 20260612, -100_000, null, transferAccount = "sav", transferId = "t-4"),
            tx("5", "sav", 20260612, 100_000, null, transferAccount = "chk", transferId = "t-4"),
            // Off-budget transfer: from checking to investment
            tx("6", "chk", 20260614, -50_000, null, transferAccount = "inv", transferId = "t-6"),
            tx("7", "inv", 20260614, 50_000, null, transferAccount = "chk", transferId = "t-6"),
        )

        val widget = CoreReportEngine.compute(
            DashboardWidgetRow("w", "cash-flow-card", """{"timeFrame":{"mode":"static","start":"2026-06","end":"2026-06"}}"""),
            transactions, context, incomeCategoryIds, today = today,
        )

        assertEquals(ReportWidgetKind.CASH_FLOW, widget.kind)
        // Net flow is 500_000 - 200_000 = 300_000
        assertEquals(300_000L, widget.valueCents)

        // Point has income 500_000 and expenses 200_000
        val point = widget.points.single()
        assertEquals(500_000L, point.primaryCents)
        assertEquals(200_000L, point.secondaryCents)
        assertNotNull(point.drillDownQuery)
        val pointTxs = filterSynthetic(transactions, point.drillDownQuery!!)
        // Excludes on-budget transfers, includes income and expenses
        val net = pointTxs.sumOf { it.amountCents }
        assertEquals(point.primaryCents - point.secondaryCents, net)
    }

    @Test
    fun `spending report drill down query matches categories and totals`() {
        val today = LocalDate.of(2026, 6, 15)
        val transactions = listOf(
            tx("1", "chk", 20260601, 500_000, "salary"),
            tx("2", "chk", 20260605, -150_000, "rent"),
            tx("3", "chk", 20260610, -50_000, "groceries"),
            tx("4", "chk", 20260612, 10_000, "groceries"), // refund
        )

        val widget = CoreReportEngine.compute(
            DashboardWidgetRow("w", "spending-card", """{"timeFrame":{"mode":"static","start":"2026-06","end":"2026-06"}}"""),
            transactions, context, incomeCategoryIds, today = today,
        )

        assertEquals(ReportWidgetKind.SPENDING, widget.kind)
        // Spending is 150_000 + (50_000 - 10_000) = 190_000
        assertEquals(190_000L, widget.valueCents)

        // Check widget valueDrillDownQuery
        val query = widget.valueDrillDownQuery
        assertNotNull(query)
        val filteredTxs = filterSynthetic(transactions, query!!)
        // Filtered transactions should contain only the expense rows
        assertEquals(setOf("2", "3", "4"), filteredTxs.map { it.id }.toSet())

        // Check category drill-down queries
        for (cat in widget.categories) {
            val catQuery = cat.drillDownQuery
            assertNotNull(catQuery)
            val catTxs = filterSynthetic(transactions, catQuery!!)
            val catSpent = catTxs.sumOf { -it.amountCents }
            assertEquals(cat.spentCents, catSpent)
        }
    }

    @Test
    fun `calendar report drill down query matches day transactions`() {
        val transactions = listOf(
            tx("1", "chk", 20260605, 100_000, "salary"),
            tx("2", "chk", 20260605, -30_000, "groceries"),
            tx("3", "chk", 20260610, -20_000, "rent"),
        )

        val widget = CoreReportEngine.compute(
            DashboardWidgetRow("w", "calendar-card", """{"timeFrame":{"mode":"static","start":"2026-06","end":"2026-06"}}"""),
            transactions, context, incomeCategoryIds,
        )

        assertEquals(ReportWidgetKind.CALENDAR, widget.kind)
        val day5 = widget.points.first { it.period == "2026-06-05" }
        assertNotNull(day5.drillDownQuery)
        val day5Txs = filterSynthetic(transactions, day5.drillDownQuery!!)
        assertEquals(setOf("1", "2"), day5Txs.map { it.id }.toSet())
        assertEquals(100_000L, day5Txs.filter { it.amountCents > 0 }.sumOf { it.amountCents })
        assertEquals(30_000L, day5Txs.filter { it.amountCents < 0 }.sumOf { -it.amountCents })
    }

    @Test
    fun `net worth report drill down query filters by cumulative date and accounts`() {
        val transactions = listOf(
            tx("1", "chk", 20260401, 100_000),
            tx("2", "chk", 20260501, 200_000),
            tx("3", "chk", 20260601, 300_000),
        )

        val widget = CoreReportEngine.compute(
            DashboardWidgetRow("w", "net-worth-card", """{"timeFrame":{"mode":"static","start":"2026-04","end":"2026-06"}}"""),
            transactions, context, incomeCategoryIds, today = LocalDate.of(2026, 6, 15),
        )

        assertEquals(ReportWidgetKind.NET_WORTH, widget.kind)
        assertTrue(widget.points.isNotEmpty())

        val mayPoint = widget.points.first { it.period.startsWith("2026-05") }
        assertNotNull(mayPoint.drillDownQuery)
        val mayTxs = filterSynthetic(transactions, mayPoint.drillDownQuery!!)
        assertEquals(setOf("1", "2"), mayTxs.map { it.id }.toSet())
        assertEquals(300_000L, mayTxs.sumOf { it.amountCents })
    }

    @Test
    fun `sankey report drill down query matches income and expense categories`() {
        val transactions = listOf(
            tx("1", "chk", 20260601, 800_000, "salary"),
            tx("2", "chk", 20260602, 200_000, "bonus"),
            tx("3", "chk", 20260605, -300_000, "rent"),
        )

        val widget = CoreReportEngine.compute(
            DashboardWidgetRow("w", "sankey-card", """{"timeFrame":{"mode":"static","start":"2026-06","end":"2026-06"}}"""),
            transactions, context, incomeCategoryIds,
        )

        assertEquals(ReportWidgetKind.SANKEY, widget.kind)

        // Income categories
        val salaryCat = widget.incomeCategories.first { it.name == "Salary" }
        assertNotNull(salaryCat.drillDownQuery)
        val salaryTxs = filterSynthetic(transactions, salaryCat.drillDownQuery!!)
        assertEquals(listOf("1"), salaryTxs.map { it.id })
        assertEquals(800_000L, salaryTxs.sumOf { it.amountCents })

        // Expense categories
        val billsCat = widget.categories.first { it.name == "Bills" }
        assertNotNull(billsCat.drillDownQuery)
        val billsTxs = filterSynthetic(transactions, billsCat.drillDownQuery!!)
        assertEquals(listOf("3"), billsTxs.map { it.id })
        assertEquals(300_000L, billsTxs.sumOf { -it.amountCents })
    }
}
