package com.azimulkabir.actua.ui.home

import com.azimulkabir.actua.data.home.HomeSection
import com.azimulkabir.actua.model.Account
import com.azimulkabir.actua.model.BudgetCategory
import com.azimulkabir.actua.model.BudgetGroup
import com.azimulkabir.actua.model.BudgetOverview
import com.azimulkabir.actua.model.ReportDashboardPage
import com.azimulkabir.actua.model.Transaction
import com.azimulkabir.actua.model.Type
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeDashboardProjectionTest {
    @Test fun keeps_authoritative_models_and_filters_only_local_favorites() {
        val favourite = BudgetCategory("Groceries", 0, 0, id = "groceries")
        val hidden = BudgetCategory("Hidden", 0, 0, id = "hidden", hidden = true)
        val checking = Account("Checking", 0, "checking", id = "checking")
        val closed = Account("Old", 0, "checking", id = "old", closed = true)
        // Repository transaction dates use Actual's undashed yyyyMMdd encoding.
        val september = Transaction("sep", "20260920", "Shop", "Groceries", "Checking", -20, false)
        val august = september.copy(id = "aug", date = "20260831")
        val overview = BudgetOverview(500, 200, 100, 400)

        val favoriteReport = ReportDashboardPage("net-worth", "Net Worth", emptyList())
        val otherReport = ReportDashboardPage("spending", "Spending", emptyList())

        val projection = HomeDashboardProjection.from(
            budgetOverview = overview,
            budgetGroups = listOf(BudgetGroup("Everyday", listOf(favourite, hidden))),
            accounts = listOf(checking, closed),
            reportDashboards = listOf(favoriteReport, otherReport),
            schedules = emptyList(),
            transactions = listOf(september, august),
            favoriteCategoryIds = setOf("groceries", "hidden"),
            favoriteAccountIds = setOf("checking", "old"),
            favoriteReportIds = setOf("net-worth"),
            month = "2026-09",
        )

        assertEquals(overview, projection.budgetOverview)
        assertEquals(listOf(favourite), projection.favoriteCategories)
        assertEquals(listOf(checking), projection.favoriteAccounts)
        assertEquals(listOf(favoriteReport), projection.favoriteReports)
        assertEquals(listOf(september), projection.monthTransactions)
        assertEquals(listOf(september, august), projection.recentTransactions)
    }

    @Test fun bounds_recent_activity_to_ten_transactions() {
        val transactions = (1..12).map { day ->
            Transaction("id-$day", "2026-09-${day.toString().padStart(2, '0')}", "Payee", "", "Checking", -1, false)
        }

        val projection = HomeDashboardProjection.from(
            budgetOverview = BudgetOverview(null, 0, 0, 0),
            budgetGroups = emptyList(),
            accounts = emptyList(),
            reportDashboards = emptyList(),
            schedules = emptyList(),
            transactions = transactions,
            favoriteCategoryIds = emptySet(),
            favoriteAccountIds = emptySet(),
            favoriteReportIds = emptySet(),
            month = "2026-09",
        )

        assertEquals(transactions.take(10), projection.recentTransactions)
    }

    @Test fun this_month_activity_uses_category_cash_flow_and_excludes_transfers() {
        val summary = homeMonthActivity(listOf(
            transaction("income", 50_000, true),
            transaction("expense", -12_000, false),
            transaction("transfer-out", -8_000, null, Type.TRANSFER),
            transaction("transfer-in", 8_000, null, Type.TRANSFER),
            transaction("uncategorized", 90_000, null),
        ))

        assertEquals(50_000, summary.incomeCents)
        assertEquals(12_000, summary.expenseCents)
        assertEquals(38_000, summary.netCents)
    }

    // Section order is user-customizable via HomeLayout; the enum's declaration order is only the
    // default a fresh install (or a restored layout) starts from.
    @Test fun keeps_the_agreed_default_home_section_order() {
        assertEquals(
            listOf("Ready to Budget", "Favorite Categories", "Favorite Accounts", "Upcoming", "This Month", "Reports", "Recent Activity"),
            HomeSection.entries.map { it.title },
        )
        assertTrue(HomeSection.entries.map { it.name }.toSet().size == HomeSection.entries.size)
    }

    private fun transaction(id: String, amountCents: Long, categoryIsIncome: Boolean?,
        type: Type = if (amountCents >= 0) Type.INCOME else Type.EXPENSE) = Transaction(
        id = id, date = "20260913", payee = "", category = "", account = "Checking",
        amount = 0, cleared = true, amountCents = amountCents, type = type,
        categoryIsIncome = categoryIsIncome,
    )
}
