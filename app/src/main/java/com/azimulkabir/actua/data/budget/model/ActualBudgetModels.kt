package com.azimulkabir.actua.data.budget.model

data class ActualCategoryBudget(
    val month: String,
    val categoryId: String,
    val categoryName: String,
    val groupId: String,
    val groupName: String,
    val groupSortOrder: Double,
    val categorySortOrder: Double,
    val budgetedCents: Long,
    val spentCents: Long,
    val availableCents: Long,
    val carryoverCents: Long,
    val hidden: Boolean,
    val groupHidden: Boolean,
    val goalCents: Long?,
    val longGoal: Boolean,
    val carryoverEnabled: Boolean,
)

data class ActualIncomeBudget(
    val month: String,
    val categoryId: String,
    val categoryName: String,
    val groupName: String,
    val sortOrder: Double,
    val budgetedCents: Long,
    val receivedCents: Long,
    val hidden: Boolean,
    val groupHidden: Boolean,
)

data class ActualBudgetMonth(
    val month: String,
    val categories: List<ActualCategoryBudget>,
    val incomeCategories: List<ActualIncomeBudget>,
    val toBudgetCents: Long?,
    val hiddenCategories: List<ActualCategoryBudget>,
    val hiddenIncomeCategories: List<ActualIncomeBudget>,
) {
    val isTracking: Boolean get() = toBudgetCents == null
}
