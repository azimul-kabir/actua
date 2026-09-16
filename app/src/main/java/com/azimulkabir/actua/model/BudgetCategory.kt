package com.azimulkabir.actua.model

data class BudgetCategory(
    val name: String,
    val assigned: Int,
    val spent: Int,
    private val actualAvailable: Int? = null,
    private val actualAssignedCents: Long? = null,
    val id: String? = null,
    val availableCents: Long? = null,
    val hidden: Boolean = false,
    val spentCents: Long = spent.toLong() * 100,
    val carryoverEnabled: Boolean = false,
    val note: String = "",
    val history: List<BudgetHistory> = emptyList(),
    val isIncome: Boolean = false,
    val target: BudgetTarget? = null,
    val hasUnsupportedTarget: Boolean = false,
    val automations: List<BudgetTarget> = target?.let(::listOf).orEmpty(),
    val unsupportedAutomationTypes: List<String> = emptyList(),
    val automationReadOnly: Boolean = false,
    val goalCents: Long? = null,
    val longGoal: Boolean = false,
    val cleanupTargets: List<CleanupTarget> = emptyList(),
    val cleanupInvalid: Boolean = false,
) {
    val available: Int get() = actualAvailable ?: assigned - spent
    val assignedCents: Long get() = actualAssignedCents ?: assigned.toLong() * 100
    val balanceCents: Long get() = availableCents ?: available.toLong() * 100
    val carryoverCents: Long get() = balanceCents - assignedCents + spentCents

    /**
     * The full balance this category is working toward, independent of this month's
     * installment. Prefers Actual's server-computed [goalCents] (kept in sync by whole-budget
     * template apply); falls back to the category's own "goal only" or long-term "by date"
     * target amount so a target set in Actua reflects immediately, without requiring Apply.
     */
    val effectiveGoalCents: Long? get() {
        goalCents?.takeIf { it > 0L }?.let { return it }
        val targets = automations.ifEmpty { target?.let(::listOf).orEmpty() }
        targets.firstOrNull { it.type == BudgetTarget.Type.GOAL }?.let { return it.amountCents }
        val byDate = targets.filter { it.type == BudgetTarget.Type.BY_DATE }.sumOf { it.amountCents }
        return byDate.takeIf { it > 0L }
    }

    // With an active goal, progress tracks balance funded toward it rather than spend-down.
    val progressFraction: Float get() {
        val goal = effectiveGoalCents
        if (goal != null && goal > 0L) return (balanceCents.toFloat() / goal).coerceIn(0f, 1f)
        return if (assignedCents <= 0L) 0f else (spentCents.toFloat() / assignedCents).coerceIn(0f, 1f)
    }
}

enum class BudgetCategoryView(val label: String) {
    ALL("All"),
    OVERSPENT("Overspent"),
    UNDERFUNDED("Underfunded"),
    OVERFUNDED("Overfunded"),
    MONEY_AVAILABLE("Money Available");

    fun matches(category: BudgetCategory): Boolean = when (this) {
        ALL -> true
        OVERSPENT -> category.balanceCents < 0L
        UNDERFUNDED -> (category.effectiveGoalCents ?: 0L) > 0L && category.balanceCents < category.effectiveGoalCents!!
        OVERFUNDED -> (category.effectiveGoalCents ?: 0L) > 0L && category.balanceCents > category.effectiveGoalCents!!
        MONEY_AVAILABLE -> category.balanceCents > 0L
    }

    companion object {
        fun fromLabel(label: String): BudgetCategoryView = entries.find { it.label == label } ?: ALL
    }
}

data class BudgetHistory(val month: String, val assignedCents: Long, val spentCents: Long)

data class BudgetOverview(
    val toBudgetCents: Long?,
    val budgetedCents: Long,
    val spentCents: Long,
    val availableCents: Long,
    val bufferedCents: Long = 0,
)

data class BudgetGroup(
    val name: String,
    val categories: List<BudgetCategory>,
    val hidden: Boolean = false,
    val isIncome: Boolean = false,
)
