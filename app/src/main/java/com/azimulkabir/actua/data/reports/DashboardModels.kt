package com.azimulkabir.actua.data.reports

data class DashboardPageRow(val id: String, val name: String)
data class DashboardWidgetRow(val id: String, val type: String, val metaJson: String?)

/** Row of Actual's `custom_reports` table (a saved report); read-only in Actua. */
data class SavedReportRow(
    val id: String,
    val name: String,
    val startDate: String?,
    val endDate: String?,
    val dateStatic: Boolean,
    val dateRange: String?,
    val groupBy: String,
    val balanceType: String,
    val showOffBudget: Boolean,
    val showHidden: Boolean,
    val showUncategorized: Boolean,
    val selectedCategories: String?,
    val graphType: String,
    val conditions: String?,
    val conditionsOp: String?,
    val interval: String,
)
