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
) {
    val available: Int get() = actualAvailable ?: assigned - spent
    val assignedCents: Long get() = actualAssignedCents ?: assigned.toLong() * 100
    val balanceCents: Long get() = availableCents ?: available.toLong() * 100
}

data class BudgetHistory(val month: String, val assignedCents: Long, val spentCents: Long)

data class BudgetOverview(
    val toBudgetCents: Long?,
    val budgetedCents: Long,
    val spentCents: Long,
    val availableCents: Long,
)

data class BudgetGroup(
    val name: String,
    val categories: List<BudgetCategory>,
    val hidden: Boolean = false,
    val isIncome: Boolean = false,
)
