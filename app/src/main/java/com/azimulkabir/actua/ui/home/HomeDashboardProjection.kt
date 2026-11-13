package com.azimulkabir.actua.ui.home

import com.azimulkabir.actua.data.home.HomeSection
import com.azimulkabir.actua.data.home.HomeSummaryPeriod
import com.azimulkabir.actua.data.home.MonthStartRule
import com.azimulkabir.actua.data.schedules.DayDate
import com.azimulkabir.actua.data.schedules.ScheduleListItem
import com.azimulkabir.actua.data.schedules.ScheduleStatus
import com.azimulkabir.actua.model.Account
import com.azimulkabir.actua.model.BudgetCategory
import com.azimulkabir.actua.model.BudgetGroup
import com.azimulkabir.actua.model.BudgetOverview
import com.azimulkabir.actua.model.ReportDashboardPage
import com.azimulkabir.actua.model.Transaction
import java.time.LocalDate

/**
 * Read-only inputs for Home. Keeping this boundary deliberately shallow means each Home section
 * can observe only the authoritative data it needs, instead of rebuilding Budget, Accounts or
 * Reports calculations inside one large dashboard composable.
 */
data class HomeDashboardProjection(
    val budgetOverview: BudgetOverview,
    val favoriteCategories: List<BudgetCategory>,
    val favoriteAccounts: List<Account>,
    val favoriteReports: List<ReportDashboardPage>,
    val upcomingSchedules: List<ScheduleListItem>,
    val monthTransactions: List<Transaction>,
    val recentTransactions: List<Transaction>,
    /** The custom summary period behind [monthTransactions], or null for the budget's calendar month. */
    val summaryPeriod: HomeSummaryPeriod? = null,
) {
    companion object {
        fun empty(): HomeDashboardProjection = HomeDashboardProjection(
            budgetOverview = BudgetOverview(null, 0, 0, 0),
            favoriteCategories = emptyList(),
            favoriteAccounts = emptyList(),
            favoriteReports = emptyList(),
            upcomingSchedules = emptyList(),
            monthTransactions = emptyList(),
            recentTransactions = emptyList(),
        )

        fun from(
            budgetOverview: BudgetOverview,
            budgetGroups: List<BudgetGroup>,
            accounts: List<Account>,
            reportDashboards: List<ReportDashboardPage>,
            schedules: List<ScheduleListItem>,
            transactions: List<Transaction>,
            favoriteCategoryIds: Set<String>,
            favoriteAccountIds: Set<String>,
            favoriteReportIds: Set<String>,
            month: String,
            monthStart: MonthStartRule = MonthStartRule.CALENDAR_MONTH,
            today: LocalDate = LocalDate.now(),
        ): HomeDashboardProjection {
            val period = monthStart.takeUnless { it.isCalendarMonth }?.let { HomeSummaryPeriod.containing(today, it) }
            return HomeDashboardProjection(
                budgetOverview = budgetOverview,
                favoriteCategories = budgetGroups
                    .asSequence()
                    .filterNot { it.hidden }
                    .flatMap { it.categories.asSequence() }
                    .filterNot { it.hidden }
                    .filter { it.id in favoriteCategoryIds }
                    .toList(),
                favoriteAccounts = accounts.filter { !it.closed && it.id in favoriteAccountIds },
                favoriteReports = reportDashboards.filter { it.id in favoriteReportIds },
                upcomingSchedules = schedules,
                monthTransactions = transactions.filter {
                    val digits = it.date.filter(Char::isDigit)
                    if (period == null) digits.startsWith(month.filter(Char::isDigit))
                    else digits.length == 8 && digits.toInt() in period
                },
                recentTransactions = transactions.take(10),
                summaryPeriod = period,
            )
        }
    }
}

/** One figure of the supporter greeting. Counts only: never an amount. */
sealed interface GreetingFigure {
    data class CategoriesOnBudget(val onTrack: Int, val budgeted: Int) : GreetingFigure
    data class Uncategorized(val count: Int) : GreetingFigure
    data class BillsDueThisWeek(val count: Int) : GreetingFigure
    data class AddedThisWeek(val count: Int) : GreetingFigure
}

/** Builds the greeting's figures from the open budget; see [figures]. */
object GreetingProjection {
    /** The week the "this week" figures look at: today and the six days around it. */
    const val WEEK_DAYS = 7

    /**
     * The figures worth saying, in display order: categories on budget, transactions to categorize, bills due
     * this week, transactions added this week. A figure with nothing to say (zero, or no budgeted category) is
     * left out, so the list can be empty even for a budget with transactions.
     */
    fun figures(
        uncategorized: Int,
        addedThisWeek: Int,
        billsDueThisWeek: Int,
        budgetGroups: List<BudgetGroup>,
    ): List<GreetingFigure> = listOfNotNull(
        categoriesOnBudget(budgetGroups),
        uncategorized.takeIf { it > 0 }?.let(GreetingFigure::Uncategorized),
        billsDueThisWeek.takeIf { it > 0 }?.let(GreetingFigure::BillsDueThisWeek),
        addedThisWeek.takeIf { it > 0 }?.let(GreetingFigure::AddedThisWeek),
    )

    /** Schedules still to be paid with their next date from [today] through the next [WEEK_DAYS] - 1 days. */
    fun billsDueThisWeek(schedules: List<ScheduleListItem>, today: LocalDate = LocalDate.now()): Int {
        val first = DayDate(today.year, today.monthValue, today.dayOfMonth)
        val last = first.addingDays(WEEK_DAYS - 1)
        return schedules.count { item ->
            item.status != ScheduleStatus.COMPLETED && item.status != ScheduleStatus.PAID &&
                item.dueDate?.let { it >= first && it <= last } == true
        }
    }

    private fun categoriesOnBudget(groups: List<BudgetGroup>): GreetingFigure.CategoriesOnBudget? {
        val budgeted = groups.asSequence()
            .filterNot { it.hidden || it.isIncome }
            .flatMap { it.categories.asSequence() }
            .filterNot { it.hidden || it.isIncome }
            .filter { it.assignedCents != 0L }
            .toList()
        if (budgeted.isEmpty()) return null
        return GreetingFigure.CategoriesOnBudget(budgeted.count { it.balanceCents >= 0 }, budgeted.size)
    }
}
