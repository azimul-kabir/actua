package com.azimulkabir.actua.model

data class ReportMonth(
    val month: String,
    val incomeCents: Long,
    val expenseCents: Long,
) { val netCents: Long get() = incomeCents - expenseCents }

data class ReportCategory(val name: String, val spentCents: Long)

data class ReportSnapshot(
    val months: List<ReportMonth>,
    val categories: List<ReportCategory>,
    val netWorthCents: Long,
) {
    val current: ReportMonth? get() = months.lastOrNull()
}
