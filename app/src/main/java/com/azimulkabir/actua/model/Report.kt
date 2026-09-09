package com.azimulkabir.actua.model

data class ReportMonth(
    val month: String,
    val incomeCents: Long,
    val expenseCents: Long,
) { val netCents: Long get() = incomeCents - expenseCents }

data class ReportCategory(val name: String, val spentCents: Long)

data class ReportPoint(val period: String, val primaryCents: Long, val secondaryCents: Long = 0)

data class ReportDashboardPage(
    val id: String,
    val name: String,
    val widgets: List<ReportWidget>,
)

enum class ReportWidgetKind {
    SUMMARY, NET_WORTH, CASH_FLOW, SPENDING, MARKDOWN,
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
    val markdown: String? = null,
    val sourceType: String? = null,
)

data class ReportSnapshot(
    val months: List<ReportMonth>,
    val categories: List<ReportCategory>,
    val netWorthCents: Long,
    val dashboards: List<ReportDashboardPage> = emptyList(),
) {
    val current: ReportMonth? get() = months.lastOrNull()
}
