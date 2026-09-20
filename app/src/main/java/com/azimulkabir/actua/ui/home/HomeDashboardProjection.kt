package com.azimulkabir.actua.ui.home

import com.azimulkabir.actua.data.schedules.ScheduleListItem
import com.azimulkabir.actua.model.Account
import com.azimulkabir.actua.model.BudgetCategory
import com.azimulkabir.actua.model.BudgetGroup
import com.azimulkabir.actua.model.BudgetOverview
import com.azimulkabir.actua.model.Transaction

/**
 * Read-only inputs for Home. Keeping this boundary deliberately shallow means each Home section
 * can observe only the authoritative data it needs, instead of rebuilding Budget, Accounts or
 * Reports calculations inside one large dashboard composable.
 */
data class HomeDashboardProjection(
    val budgetOverview: BudgetOverview,
    val favoriteCategories: List<BudgetCategory>,
    val favoriteAccounts: List<Account>,
    val upcomingSchedules: List<ScheduleListItem>,
    val monthTransactions: List<Transaction>,
    val recentTransactions: List<Transaction>,
) {
    companion object {
        fun from(
            budgetOverview: BudgetOverview,
            budgetGroups: List<BudgetGroup>,
            accounts: List<Account>,
            schedules: List<ScheduleListItem>,
            transactions: List<Transaction>,
            favoriteCategoryIds: Set<String>,
            favoriteAccountIds: Set<String>,
            month: String,
        ): HomeDashboardProjection = HomeDashboardProjection(
            budgetOverview = budgetOverview,
            favoriteCategories = budgetGroups
                .asSequence()
                .filterNot { it.hidden }
                .flatMap { it.categories.asSequence() }
                .filterNot { it.hidden }
                .filter { it.id in favoriteCategoryIds }
                .toList(),
            favoriteAccounts = accounts.filter { !it.closed && it.id in favoriteAccountIds },
            upcomingSchedules = schedules,
            monthTransactions = transactions.filter { it.date.startsWith(month) },
            recentTransactions = transactions,
        )
    }
}

/** Stable section keys and the agreed V1 display order for the Home dashboard. */
enum class HomeSection(val title: String) {
    READY_TO_BUDGET("Ready to Budget"),
    FAVORITE_CATEGORIES("Favorite Categories"),
    FAVORITE_ACCOUNTS("Favorite Accounts"),
    UPCOMING("Upcoming"),
    THIS_MONTH("This Month"),
    REPORTS("Reports"),
    RECENT_ACTIVITY("Recent Activity"),
}
