package com.azimulkabir.actua.data.preferences

/**
 * The Budget tab's device-local display switches, grouped so the screen, the options sheet and
 * [DisplayPreferences] pass them as one value. Defaults are the stored defaults.
 */
data class BudgetDisplayOptions(
    val showOverview: Boolean = true,
    val showBudgetStatusBanners: Boolean = true,
    val showCategoryFilters: Boolean = true,
    val showSpent: Boolean = true,
    val showProgressBars: Boolean = true,
    val showGroupTotals: Boolean = true,
    val showBalancePills: Boolean = true,
    val showGoalIcons: Boolean = true,
    val hideFullySpent: Boolean = false,
    val showHidden: Boolean = false,
)
