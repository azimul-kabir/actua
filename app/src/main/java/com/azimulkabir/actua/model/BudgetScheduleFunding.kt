package com.azimulkabir.actua.model

/**
 * Exact schedule facts needed by the budget-template planner.
 *
 * The repository builds this projection from Actual's schedule/rule rows. Keeping
 * it separate from the persistence model makes an unresolved schedule explicit
 * instead of treating it as a zero-dollar template.
 */
data class BudgetScheduleFunding(
    val id: String,
    val name: String?,
    val amountCents: Long,
    val occurrencesInMonth: Int,
    val monthsUntilNextOccurrence: Int,
    val categoryId: String? = null,
    val active: Boolean = true,
) {
    val referenceNames: Set<String> = buildSet {
        add(id)
        name?.trim()?.takeIf(String::isNotEmpty)?.let(::add)
    }

    fun requestedBudget(carryoverCents: Long): Long {
        if (!active || amountCents <= 0L || occurrencesInMonth < 0) return 0L
        if (occurrencesInMonth > 0) return amountCents * occurrencesInMonth
        val months = monthsUntilNextOccurrence.coerceAtLeast(1)
        return ((amountCents - carryoverCents).coerceAtLeast(0L) + months - 1L) / months
    }
}
