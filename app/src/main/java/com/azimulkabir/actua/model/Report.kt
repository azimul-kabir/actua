package com.azimulkabir.actua.model

data class ReportMonth(
    val month: String,
    val incomeCents: Long,
    val expenseCents: Long,
) { val netCents: Long get() = incomeCents - expenseCents }

data class ReportCategory(
    val name: String,
    val spentCents: Long,
    /** Ids of the transactions that make up [spentCents], for read-only drill-down. */
    val transactionIds: List<String> = emptyList(),
)

data class ReportPoint(
    val period: String,
    val primaryCents: Long,
    val secondaryCents: Long = 0,
    /** Ids of the transactions behind this point, for read-only drill-down. */
    val transactionIds: List<String> = emptyList(),
    /** Per-category breakdown of this point, for a `StackedBarGraph` saved report. */
    val segments: List<ReportCategory> = emptyList(),
)

data class ReportDashboardPage(
    val id: String,
    val name: String,
    val widgets: List<ReportWidget>,
)

enum class ReportWidgetKind {
    SUMMARY, NET_WORTH, CASH_FLOW, INCOME_EXPENSE, SPENDING, MARKDOWN,
    AGE_OF_MONEY, FORMULA, CUSTOM_REPORT, CALENDAR, CROSSOVER,
    BUDGET_ANALYSIS, SANKEY, BALANCE_FORECAST, MONTE_CARLO, UNSUPPORTED,
}

data class ReportWidget(
    val id: String,
    val kind: ReportWidgetKind,
    val name: String,
    val valueCents: Long? = null,
    val percentage: Double? = null,
    val comparisonCents: Long? = null,
    val points: List<ReportPoint> = emptyList(),
    val categories: List<ReportCategory> = emptyList(),
    /** Income-side breakdown for the Sankey widget; empty for every other widget kind. */
    val incomeCategories: List<ReportCategory> = emptyList(),
    val markdown: String? = null,
    val sourceType: String? = null,
    /** Saved-report presentation hint (`DonutGraph`, `BarGraph`, ...). */
    val graphType: String? = null,
    val subtitle: String? = null,
    /** Saved report charts value per interval rather than per group. */
    val timeMode: Boolean = false,
    /** Ids of the transactions behind [valueCents], for widgets with no per-point breakdown (e.g. Spending). */
    val valueTransactionIds: List<String> = emptyList(),
    /** Ids of the transactions behind [comparisonCents]. */
    val comparisonTransactionIds: List<String> = emptyList(),
)

/** Viewer-side override applied on top of a saved report's own settings; never written back. */
data class ReportViewFilter(
    val datePreset: String? = null,
    val accountIds: Set<String> = emptySet(),
    val categoryGroupIds: Set<String> = emptySet(),
    /** Adds off-budget accounts on top of what the report itself includes. */
    val includeOffBudget: Boolean = false,
) {
    val isDefault: Boolean get() =
        datePreset == null && accountIds.isEmpty() && categoryGroupIds.isEmpty() && !includeOffBudget

    companion object {
        val datePresets = listOf(
            "This month", "Last month", "Last 3 months", "Last 6 months", "Last 12 months",
            "Year to date", "Last year", "All time",
        )
    }
}

data class ReportAccountOption(val id: String, val name: String)

data class ReportSnapshot(
    val months: List<ReportMonth>,
    val categories: List<ReportCategory>,
    val netWorthCents: Long,
    val dashboards: List<ReportDashboardPage> = emptyList(),
    val accountOptions: List<ReportAccountOption> = emptyList(),
    val groupOptions: List<ReportAccountOption> = emptyList(),
) {
    val current: ReportMonth? get() = months.lastOrNull()
}
