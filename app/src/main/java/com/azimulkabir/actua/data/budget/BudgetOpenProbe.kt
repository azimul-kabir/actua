package com.azimulkabir.actua.data.budget

import java.time.LocalDate
import java.time.YearMonth

/** Exercises the same core reads the app performs when opening a budget. */
internal object BudgetOpenProbe {
    fun validate(file: java.io.File) = ActualBudgetDatabase.open(file).use(::validate)

    fun validate(database: ActualBudgetDatabase) {
        try {
            val accounts = database.fetchAccounts()
            val groups = database.fetchCategoryGroups()
            database.fetchTransactions(limit = 1)
            database.fetchPayees()
            database.fetchRules()
            database.fetchCreditCardConfigs()
            val schedules = database.fetchScheduleSummaries()
            schedules.forEach { schedule ->
                schedule.nextDate?.let { requireValidDate(it.yyyymmdd, "schedule ${schedule.id}") }
            }
            database.fetchPaidScheduleIds(schedules)
            database.fetchDashboardPages()
            database.fetchSavedReports()
            val reportTransactions = database.fetchTransactionsForReports()
            reportTransactions.forEach { transaction ->
                requireValidDate(transaction.date, "transaction ${transaction.id}")
            }
            database.fetchBudgetMonth(YearMonth.now().toString())
        } catch (error: BudgetFileException) {
            throw error
        } catch (_: Exception) {
            throw BudgetFileException.InvalidBudgetData(
                "This budget contains unsupported or unreadable data and could not be opened safely.",
            )
        }
    }

    private fun requireValidDate(value: Int, label: String) {
        try {
            LocalDate.of(value / 10_000, value / 100 % 100, value % 100)
        } catch (_: Exception) {
            throw BudgetFileException.InvalidBudgetData(
                "This budget contains an unreadable date in $label and could not be opened safely.",
            )
        }
    }
}
