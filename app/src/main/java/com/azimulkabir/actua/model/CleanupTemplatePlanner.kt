package com.azimulkabir.actua.model

/** One category whose budgeted amount cleanup proposes to change. */
data class CleanupChange(
    val groupName: String,
    val categoryId: String,
    val categoryName: String,
    val currentCents: Long,
    val proposedCents: Long,
)

/** A global-source category's goal is refreshed to match its post-cleanup budgeted amount. */
data class CleanupGoalChange(
    val groupName: String,
    val categoryId: String,
    val categoryName: String,
    val currentCents: Long?,
    val proposedCents: Long?,
)

data class CleanupPreview(
    val month: String,
    val changes: List<CleanupChange> = emptyList(),
    val goalChanges: List<CleanupGoalChange> = emptyList(),
    val warnings: List<String> = emptyList(),
    val invalidCategories: List<String> = emptyList(),
    val sourceCount: Int = 0,
    val sinkCount: Int = 0,
) {
    val isUpToDate: Boolean get() = changes.isEmpty() && goalChanges.isEmpty()
}

/**
 * Preview-first planner for Actual's month-end cleanup engine. Faithful reference:
 * `packages/loot-core/src/server/budget/cleanup-template.ts` at commit `2fc69915`.
 *
 * Cleanup-group scoped source/sink/overspend rows run against an isolated pool of that
 * group's own source funds. Global (`groupId == null`) sources return their leftover to the
 * shared Ready-to-Budget pool and refresh their goal; the shared pool then funds every
 * non-income, non-carryover overspent category (whether or not it participates in a cleanup
 * group) before any remaining pool is split across global weighted sinks. All allocation uses
 * deterministic integer-cent quotient/remainder division so distributed cents always sum
 * exactly to the pool being divided.
 */
object CleanupTemplatePlanner {

    private data class Entry(val group: BudgetGroup, val category: BudgetCategory)

    fun preview(
        groups: List<BudgetGroup>,
        cleanupGroupNames: Map<String, String>,
        month: String,
        toBudgetCents: Long,
    ): CleanupPreview {
        val warnings = mutableListOf<String>()
        val invalid = mutableListOf<String>()
        val proposed = mutableMapOf<String, Long>()
        val goalChanges = mutableListOf<CleanupGoalChange>()
        var sourceCount = 0
        var sinkCount = 0

        val eligible = groups.filterNot { it.isIncome || it.hidden }.flatMap { group ->
            group.categories.filterNot { it.isIncome || it.hidden }
                .filter { it.id != null }
                .map { Entry(group, it) }
        }
        val byId = eligible.associateBy { it.category.id!! }

        eligible.forEach { entry ->
            if (entry.category.cleanupInvalid) invalid += "${entry.group.name} · ${entry.category.name}"
        }

        fun budgeted(id: String) = proposed[id] ?: byId.getValue(id).category.assignedCents
        fun leftover(id: String): Long {
            val entry = byId.getValue(id)
            return entry.category.balanceCents + (budgeted(id) - entry.category.assignedCents)
        }

        // --- Cleanup-group scoped source/sink/overspend pools ---
        val sourceRows = eligible.flatMap { entry ->
            entry.category.cleanupTargets.filter { it.role == CleanupTarget.Role.SOURCE && it.groupId != null }
                .map { it.groupId!! to entry }
        }
        val sinkRows = eligible.flatMap { entry ->
            entry.category.cleanupTargets.filter { it.role == CleanupTarget.Role.SINK && it.groupId != null }
                .map { Triple(it.groupId!!, entry, it.weight) }
        }
        val overspendRows = eligible.flatMap { entry ->
            entry.category.cleanupTargets.filter { it.role == CleanupTarget.Role.OVERSPEND && it.groupId != null }
                .map { it.groupId!! to entry }
        }

        sourceRows.map { it.first }.distinct().forEach groupLoop@{ groupId ->
            val groupName = cleanupGroupNames[groupId] ?: groupId
            val sources = sourceRows.filter { it.first == groupId }.map { it.second }
            val sinks = sinkRows.filter { it.first == groupId }
            val overspends = overspendRows.filter { it.first == groupId }.map { it.second }
            if (sinks.isEmpty() && overspends.isEmpty()) {
                warnings += "Cleanup group \"$groupName\" has no matching sink categories."
                return@groupLoop
            }
            var available = 0L
            sources.forEach sourceLoop@{ entry ->
                val id = entry.category.id!!
                val balance = leftover(id)
                if (balance < 0L) {
                    warnings += "${entry.category.name} does not have available funds."
                    return@sourceLoop
                }
                proposed[id] = budgeted(id) - balance
                available += balance
                sourceCount++
            }
            overspends.forEach overspendLoop@{ entry ->
                if (available <= 0L || entry.category.carryoverEnabled) return@overspendLoop
                val id = entry.category.id!!
                val balance = leftover(id)
                if (balance >= 0L) return@overspendLoop
                val deficit = -balance
                if (deficit <= available) {
                    proposed[id] = budgeted(id) + deficit
                    available -= deficit
                } else {
                    proposed[id] = budgeted(id) + available
                    available = 0L
                }
            }
            if (sinks.isNotEmpty() && available > 0L) {
                val allocations = allocateByWeight(available, sinks.map { it.third })
                sinks.forEachIndexed { index, (_, entry, _) ->
                    val id = entry.category.id!!
                    proposed[id] = budgeted(id) + allocations[index]
                    if (allocations[index] != 0L) sinkCount++
                }
            }
        }

        // --- Global (groupId == null) sources return funds and refresh their goal ---
        eligible.filter { entry ->
            entry.category.cleanupTargets.any { it.role == CleanupTarget.Role.SOURCE && it.groupId == null }
        }.forEach sourceLoop@{ entry ->
            val id = entry.category.id!!
            val balance = leftover(id)
            if (balance < 0L) {
                warnings += "${entry.category.name} does not have available funds."
                return@sourceLoop
            }
            val newBudgeted = budgeted(id) - balance
            proposed[id] = newBudgeted
            goalChanges += CleanupGoalChange(entry.group.name, id, entry.category.name, entry.category.goalCents, newBudgeted)
            sourceCount++
        }

        var pool = toBudgetCents - eligible.sumOf { budgeted(it.category.id!!) - it.category.assignedCents }

        // --- Fund every overspent, non-income, non-carryover category from the shared pool ---
        eligible.forEach overspendLoop@{ entry ->
            if (pool <= 0L || entry.category.carryoverEnabled) return@overspendLoop
            val id = entry.category.id!!
            val balance = leftover(id)
            if (balance >= 0L) return@overspendLoop
            val deficit = -balance
            if (deficit <= pool) {
                proposed[id] = budgeted(id) + deficit
                pool -= deficit
            } else {
                proposed[id] = budgeted(id) + pool
                pool = 0L
            }
        }

        if (pool < 0L) warnings += "Global: No funds are available to reallocate."

        // --- Split any remaining shared pool across global weighted sinks ---
        val globalSinks = eligible.filter { entry ->
            entry.category.cleanupTargets.any { it.role == CleanupTarget.Role.SINK && it.groupId == null }
        }
        if (globalSinks.isNotEmpty() && pool > 0L) {
            val weights = globalSinks.map { entry ->
                entry.category.cleanupTargets.first { it.role == CleanupTarget.Role.SINK && it.groupId == null }.weight
            }
            val allocations = allocateByWeight(pool, weights)
            globalSinks.forEachIndexed { index, entry ->
                val id = entry.category.id!!
                proposed[id] = budgeted(id) + allocations[index]
                if (allocations[index] != 0L) sinkCount++
            }
        }

        val changes = proposed.mapNotNull { (id, amount) ->
            val entry = byId.getValue(id)
            if (amount == entry.category.assignedCents) null
            else CleanupChange(entry.group.name, id, entry.category.name, entry.category.assignedCents, amount)
        }.sortedBy { it.categoryId }

        return CleanupPreview(
            month = month,
            changes = changes,
            goalChanges = goalChanges,
            warnings = warnings.distinct(),
            invalidCategories = invalid.distinct(),
            sourceCount = sourceCount,
            sinkCount = sinkCount,
        )
    }

    /**
     * Deterministic integer-cent quotient/remainder split so a weighted distribution always
     * sums to exactly [total], instead of drifting from independent per-sink rounding.
     */
    private fun allocateByWeight(total: Long, weights: List<Int>): List<Long> {
        val totalWeight = weights.sumOf { it.toLong() }
        if (totalWeight <= 0L || total <= 0L) return weights.map { 0L }
        val base = weights.map { total * it / totalWeight }
        var remainder = total - base.sum()
        val order = weights.indices.sortedWith(compareByDescending<Int> { weights[it] }.thenBy { it })
        val result = base.toMutableList()
        var cursor = 0
        while (remainder > 0L) {
            result[order[cursor % order.size]] += 1
            remainder -= 1
            cursor += 1
        }
        return result
    }
}
