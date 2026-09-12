package com.azimulkabir.actua.model

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.max

/** A small, user-facing projection of Actual's UI-managed goal templates. */
data class BudgetTarget(
    val type: Type,
    val amountCents: Long = 0,
    val targetMonth: String? = null,
    val startingDate: String? = null,
    val averageMonths: Int = 3,
    val priority: Int = 1,
) {
    enum class Type(val label: String, val explanation: String) {
        MONTHLY_SPENDING("Monthly spending", "Set aside enough for this month's spending"),
        MONTHLY_SAVINGS("Save every month", "Add the same amount every month"),
        BY_DATE("Have amount by a date", "Spread the remaining amount across the months until a date"),
        REFILL("Refill up to amount", "Top the category back up to a balance cap"),
        WEEKLY_SPENDING("Spend every week", "Budget this amount for each week in the month"),
        AVERAGE("Average recent spending", "Use the average of recent months"),
        GOAL("Goal only", "Show a target balance without automatically budgeting money"),
    }

    fun suggestedBudget(category: BudgetCategory, month: String): Long {
        return when (type) {
        Type.MONTHLY_SAVINGS -> amountCents
        Type.MONTHLY_SPENDING, Type.REFILL -> max(0L, amountCents - category.carryoverCents)
        Type.BY_DATE -> {
            val end = runCatching { YearMonth.parse(targetMonth) }.getOrNull() ?: return 0L
            val current = runCatching { YearMonth.parse(month) }.getOrNull() ?: return 0L
            val months = max(1L, ChronoUnit.MONTHS.between(current, end) + 1L)
            ceil(max(0L, amountCents - category.carryoverCents).toDouble() / months).toLong()
        }
        Type.WEEKLY_SPENDING -> {
            val selected = runCatching { YearMonth.parse(month) }.getOrNull() ?: return 0L
            val start = runCatching { LocalDate.parse(startingDate) }.getOrNull() ?: selected.atDay(1)
            var date = start
            while (date.isBefore(selected.atDay(1))) date = date.plusWeeks(1)
            var count = 0
            while (!date.isAfter(selected.atEndOfMonth())) { count++; date = date.plusWeeks(1) }
            amountCents * count
        }
        Type.AVERAGE -> {
            val values = category.history.take(averageMonths.coerceIn(1, 24))
                .map { kotlin.math.abs(minOf(it.spentCents, 0L)) }
            if (values.isEmpty()) 0L else (values.sum().toDouble() / values.size).toLong()
        }
        Type.GOAL -> 0L
        }
    }

    /** JSON accepted by Actual's visual budget-automation editor and engine. */
    fun toGoalDef(): String {
        val row = JSONObject().put("directive", "template").put("priority", priority)
        when (type) {
            Type.MONTHLY_SPENDING -> row.put("type", "spend").put("amount", units(amountCents))
                .put("month", targetMonth).put("from", targetMonth).put("annual", false).put("repeat", 1)
            Type.MONTHLY_SAVINGS -> row.put("type", "periodic").put("amount", units(amountCents))
                .put("period", JSONObject().put("period", "month").put("amount", 1))
                .put("starting", startingDate)
            Type.BY_DATE -> row.put("type", "by").put("amount", units(amountCents))
                .put("month", targetMonth).put("annual", false)
            Type.REFILL -> {
                val cap = JSONObject().put("directive", "template").put("type", "limit")
                    .put("priority", JSONObject.NULL).put("amount", units(amountCents))
                    .put("hold", true).put("period", "monthly")
                val refill = JSONObject().put("directive", "template").put("type", "refill").put("priority", priority)
                return JSONArray().put(cap).put(refill).toString()
            }
            Type.WEEKLY_SPENDING -> row.put("type", "periodic").put("amount", units(amountCents))
                .put("period", JSONObject().put("period", "week").put("amount", 1))
                .put("starting", startingDate)
            Type.AVERAGE -> row.put("type", "average").put("numMonths", averageMonths.coerceIn(1, 24))
            Type.GOAL -> return JSONArray().put(
                JSONObject().put("directive", "goal").put("type", "goal").put("amount", units(amountCents)),
            ).toString()
        }
        return JSONArray().put(row).toString()
    }

    companion object {
        fun fromGoalDef(raw: String?, source: String?): BudgetTarget? {
            if (raw.isNullOrBlank() || source != "ui") return null
            val array = runCatching { JSONArray(raw) }.getOrNull() ?: return null
            if (array.length() == 2) {
                val rows = (0 until 2).map(array::getJSONObject)
                val cap = rows.firstOrNull { it.optString("type") == "limit" }
                if (cap != null && rows.any { it.optString("type") == "refill" }) {
                    val refill = rows.first { it.optString("type") == "refill" }
                    return BudgetTarget(
                        Type.REFILL,
                        (cap.optDouble("amount", 0.0) * 100.0).toLong(),
                        priority = refill.optInt("priority", 1),
                    )
                }
                return null
            }
            val row = array.takeIf { it.length() == 1 }?.getJSONObject(0) ?: return null
            fun cents() = (row.optDouble("amount", 0.0) * 100.0).toLong()
            val priority = row.optInt("priority", 1)
            return when (row.optString("type")) {
                "spend" -> BudgetTarget(Type.MONTHLY_SPENDING, cents(), row.optString("month").ifBlank { null }, priority = priority)
                "periodic" -> when (row.optJSONObject("period")?.optString("period")) {
                    "month" -> BudgetTarget(Type.MONTHLY_SAVINGS, cents(), startingDate = row.optString("starting").ifBlank { null }, priority = priority)
                    "week" -> BudgetTarget(Type.WEEKLY_SPENDING, cents(), startingDate = row.optString("starting").ifBlank { null }, priority = priority)
                    else -> null
                }
                "by" -> BudgetTarget(Type.BY_DATE, cents(), row.optString("month").ifBlank { null }, priority = priority)
                "limit" -> BudgetTarget(Type.REFILL, cents(), priority = priority)
                "average" -> BudgetTarget(Type.AVERAGE, averageMonths = row.optInt("numMonths", 3), priority = priority)
                "goal" -> row.takeIf { it.optString("directive") == "goal" }
                    ?.let { BudgetTarget(Type.GOAL, cents()) }
                else -> null
            }
        }

        private fun units(cents: Long): Any = if (cents % 100L == 0L) cents / 100L else cents / 100.0
    }
}

data class BudgetTemplateChange(
    val groupName: String,
    val categoryId: String,
    val categoryName: String,
    val currentCents: Long,
    val proposedCents: Long,
)

data class BudgetGoalChange(
    val groupName: String,
    val categoryId: String,
    val categoryName: String,
    val currentCents: Long?,
    val proposedCents: Long?,
)

data class BudgetTemplatePreview(
    val month: String,
    val changes: List<BudgetTemplateChange>,
    val unchangedCount: Int,
    val unsupportedCategories: List<String>,
    val limitedCategories: List<String> = emptyList(),
    val skippedExistingCount: Int = 0,
    val overwriteExisting: Boolean = false,
    val goalChanges: List<BudgetGoalChange> = emptyList(),
) {
    val netBudgetChangeCents: Long = changes.sumOf { it.proposedCents - it.currentCents }
}

/** Preview-first planner for the UI-managed target types Actua can evaluate exactly. */
object BudgetTemplatePlanner {
    fun preview(
        groups: List<BudgetGroup>,
        month: String,
        availableBudgetCents: Long = Long.MAX_VALUE,
        overwriteExisting: Boolean = false,
    ): BudgetTemplatePreview {
        val changes = mutableListOf<BudgetTemplateChange>()
        val unsupported = mutableListOf<String>()
        val limited = mutableListOf<String>()
        val goalChanges = mutableListOf<BudgetGoalChange>()
        var unchanged = 0
        var skippedExisting = 0
        val supported = groups.filterNot { it.isIncome || it.hidden }.flatMap { group ->
            group.categories.filterNot { it.isIncome || it.hidden }.map { group to it }
        }
        val eligible = supported.filter { (_, category) ->
            val hasTargets = category.automations.isNotEmpty() || category.target != null
            val canRun = !category.hasUnsupportedTarget && hasTargets
            if (canRun && !overwriteExisting && category.assignedCents != 0L) skippedExisting++
            canRun && (overwriteExisting || category.assignedCents == 0L)
        }
        var available = if (availableBudgetCents == Long.MAX_VALUE) Long.MAX_VALUE else
            availableBudgetCents + if (overwriteExisting) eligible.sumOf { it.second.assignedCents } else 0L
        val proposed = mutableMapOf<BudgetCategory, Long>()
        val priorities = eligible.flatMap {
            it.second.automations.ifEmpty { listOfNotNull(it.second.target) }
        }.map(BudgetTarget::priority).distinct().sorted()

        for (priority in priorities) {
            for ((group, category) in eligible) {
                val targets = category.automations.ifEmpty { category.target?.let(::listOf).orEmpty() }
                if (targets.isEmpty()) continue
                val atPriority = targets.filter { it.priority == priority }
                if (atPriority.isEmpty()) continue
                val before = proposed[category] ?: 0L
                val requested = requestedAtPriority(atPriority, category, month)
                val cap = targets.firstOrNull { it.type == BudgetTarget.Type.REFILL }?.amountCents
                val capped = cap?.let { minOf(requested, max(0L, it - category.carryoverCents - before)) } ?: requested
                val allocated = if (available == Long.MAX_VALUE || priority <= 0) capped else
                    minOf(capped, max(0L, available))
                if (allocated < capped) limited += "${group.name} · ${category.name}"
                proposed[category] = before + allocated
                if (available != Long.MAX_VALUE) available -= allocated
            }
        }
        for ((group, category) in supported) {
            if (category.hasUnsupportedTarget) {
                unsupported += "${group.name} · ${category.name}"
                continue
            }
            val targets = category.automations.ifEmpty { category.target?.let(::listOf).orEmpty() }
            val goal = targets.firstOrNull { it.type == BudgetTarget.Type.GOAL }?.amountCents
            val goalChanged = goal != category.goalCents || goal != null && !category.longGoal
            if (goalChanged && (targets.isEmpty() || overwriteExisting || category.assignedCents == 0L)) {
                val id = category.id
                if (id == null) unsupported += "${group.name} · ${category.name}"
                else goalChanges += BudgetGoalChange(group.name, id, category.name, category.goalCents, goal)
            }
            if (targets.isEmpty()) continue
            if (!overwriteExisting && category.assignedCents != 0L) continue
            val amount = proposed[category] ?: 0L
            if (amount == category.assignedCents) {
                    unchanged++
                } else {
                    val id = category.id
                    if (id == null) unsupported += "${group.name} · ${category.name}"
                    else changes += BudgetTemplateChange(
                        group.name, id, category.name, category.assignedCents, amount,
                    )
                }
        }
        return BudgetTemplatePreview(
            month, changes, unchanged, unsupported.distinct(), limited.distinct(),
            skippedExisting, overwriteExisting, goalChanges,
        )
    }

    private fun requestedAtPriority(targets: List<BudgetTarget>, category: BudgetCategory, month: String): Long {
        val by = targets.filter { it.type == BudgetTarget.Type.BY_DATE }
        val ordinary = targets.filterNot {
            it.type == BudgetTarget.Type.BY_DATE || it.type == BudgetTarget.Type.REFILL || it.type == BudgetTarget.Type.GOAL
        }
            .sumOf { it.suggestedBudget(category, month) }
        val byAmount = if (by.isEmpty()) 0L else combinedByDate(by, category, month)
        val refill = targets.firstOrNull { it.type == BudgetTarget.Type.REFILL }
            ?.let { max(0L, it.amountCents - category.carryoverCents) } ?: 0L
        return max(0L, ordinary + byAmount + refill)
    }

    /** Matches Actual's batch treatment of sibling `by` templates: carryover is deducted once. */
    private fun combinedByDate(targets: List<BudgetTarget>, category: BudgetCategory, month: String): Long {
        val current = runCatching { YearMonth.parse(month) }.getOrNull() ?: return 0L
        val months = targets.map { target ->
            runCatching { YearMonth.parse(target.targetMonth.orEmpty()) }.getOrNull()
                ?.let { max(0L, ChronoUnit.MONTHS.between(current, it)) } ?: 0L
        }
        val shortest = months.minOrNull() ?: 0L
        val needed = targets.zip(months).sumOf { (target, targetMonths) ->
            if (targetMonths > shortest) {
                Math.round(target.amountCents.toDouble() / (targetMonths + 1L) * (shortest + 1L))
            } else target.amountCents
        }
        return max(0L, Math.round((needed - category.carryoverCents).toDouble() / (shortest + 1L)))
    }
}
