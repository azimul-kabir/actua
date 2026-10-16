package com.azimulkabir.actua.model

/**
 * Direction of monetary flow for drill-down transactions.
 */
enum class DrillDownDirection {
    ANY,
    INCOME,
    EXPENSE,
}

/**
 * Defines a query to resolve contributing transactions for report drill-down off the main thread
 * using parameterized SQL, without having to embed large lists of transaction IDs into report models.
 */
data class DrillDownQuery(
    val startDate: Int? = null,
    val endDate: Int? = null,
    val accountIds: Set<String>? = null,
    val categoryIds: Set<String>? = null,
    val payeeId: String? = null,
    val direction: DrillDownDirection = DrillDownDirection.ANY,
    val onBudgetOnly: Boolean = false,
    val excludeTransfers: Boolean = false,
    val excludeIncomeCategories: Boolean = false,
    val includeHiddenCategories: Boolean = false,
    val accountName: String? = null,
    val categoryName: String? = null,
    val month: String? = null,
)
