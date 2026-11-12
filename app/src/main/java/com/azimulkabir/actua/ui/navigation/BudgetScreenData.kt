package com.azimulkabir.actua.ui.navigation

import com.azimulkabir.actua.data.ActuaRepository
import com.azimulkabir.actua.data.budget.ActualBudgetDatabase
import com.azimulkabir.actua.model.BudgetGroup
import com.azimulkabir.actua.model.BudgetOverview
import com.azimulkabir.actua.model.BudgetScheduleFunding
import java.time.YearMonth

/** Everything the Budget screen reads for one [month], loaded together in one pass. */
internal data class BudgetMonthData(
    val month: String,
    val groups: List<BudgetGroup>,
    val scheduleFunding: List<BudgetScheduleFunding>,
    val overview: BudgetOverview,
    val note: String,
    val monthRange: ClosedRange<YearMonth>?,
) {
    companion object {
        /** What shows before the first load, and for a budget that has not loaded yet. */
        fun empty(month: String) = BudgetMonthData(
            month = month,
            groups = emptyList(),
            scheduleFunding = emptyList(),
            overview = BudgetOverview(toBudgetCents = null, budgetedCents = 0, spentCents = 0, availableCents = 0),
            note = "",
            monthRange = null,
        )

        /** Blocking: call off the main thread. */
        fun load(repository: ActuaRepository, month: String) = BudgetMonthData(
            month = month,
            groups = repository.budgetGroups(month),
            scheduleFunding = repository.budgetScheduleFunding(month),
            overview = repository.budgetOverview(month),
            note = repository.monthNote(month),
            monthRange = repository.budgetMonthRange(),
        )
    }
}

/** The budget-wide settings that do not depend on the selected month. */
internal data class BudgetSettings(
    val type: String,
    val hidesFraction: Boolean,
    val decimalPlaces: Int,
) {
    companion object {
        val Default = BudgetSettings(ActualBudgetDatabase.BUDGET_TYPE_ENVELOPE, hidesFraction = false, decimalPlaces = 2)

        /** Blocking: call off the main thread. */
        fun load(repository: ActuaRepository) = BudgetSettings(
            type = repository.budgetType(),
            hidesFraction = repository.budgetHidesFraction(),
            decimalPlaces = repository.budgetDecimalPlaces(),
        )
    }
}
