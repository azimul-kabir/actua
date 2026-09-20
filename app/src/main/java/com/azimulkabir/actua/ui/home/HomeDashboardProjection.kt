package com.azimulkabir.actua.ui.home

import com.azimulkabir.actua.data.home.HomeSection
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
        fun empty(): HomeDashboardProjection = HomeDashboardProjection(
            budgetOverview = BudgetOverview(null, 0, 0, 0),
            favoriteCategories = emptyList(),
            favoriteAccounts = emptyList(),
            upcomingSchedules = emptyList(),
            monthTransactions = emptyList(),
            recentTransactions = emptyList(),
        )

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
            recentTransactions = transactions.take(10),
        )
    }
}
