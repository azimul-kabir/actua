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
) {
    enum class Type(val label: String, val explanation: String) {
        MONTHLY_SPENDING("Monthly spending", "Set aside enough for this month's spending"),
        MONTHLY_SAVINGS("Save every month", "Add the same amount every month"),
        BY_DATE("Have amount by a date", "Spread the remaining amount across the months until a date"),
        REFILL("Refill up to amount", "Top the category back up to a balance cap"),
        WEEKLY_SPENDING("Spend every week", "Budget this amount for each week in the month"),
        AVERAGE("Average recent spending", "Use the average of recent months"),
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
        }
    }

    /** JSON accepted by Actual's visual budget-automation editor and engine. */
    fun toGoalDef(): String {
        val row = JSONObject().put("directive", "template").put("priority", 1)
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
                val refill = JSONObject().put("directive", "template").put("type", "refill").put("priority", 1)
                return JSONArray().put(cap).put(refill).toString()
            }
            Type.WEEKLY_SPENDING -> row.put("type", "periodic").put("amount", units(amountCents))
                .put("period", JSONObject().put("period", "week").put("amount", 1))
                .put("starting", startingDate)
            Type.AVERAGE -> row.put("type", "average").put("numMonths", averageMonths.coerceIn(1, 24))
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
                    return BudgetTarget(Type.REFILL, (cap.optDouble("amount", 0.0) * 100.0).toLong())
                }
                return null
            }
            val row = array.takeIf { it.length() == 1 }?.getJSONObject(0) ?: return null
            fun cents() = (row.optDouble("amount", 0.0) * 100.0).toLong()
            return when (row.optString("type")) {
                "spend" -> BudgetTarget(Type.MONTHLY_SPENDING, cents(), row.optString("month").ifBlank { null })
                "periodic" -> when (row.optJSONObject("period")?.optString("period")) {
                    "month" -> BudgetTarget(Type.MONTHLY_SAVINGS, cents(), startingDate = row.optString("starting").ifBlank { null })
                    "week" -> BudgetTarget(Type.WEEKLY_SPENDING, cents(), startingDate = row.optString("starting").ifBlank { null })
                    else -> null
                }
                "by" -> BudgetTarget(Type.BY_DATE, cents(), row.optString("month").ifBlank { null })
                "limit" -> BudgetTarget(Type.REFILL, cents())
                "average" -> BudgetTarget(Type.AVERAGE, averageMonths = row.optInt("numMonths", 3))
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

data class BudgetTemplatePreview(
    val month: String,
    val changes: List<BudgetTemplateChange>,
    val unchangedCount: Int,
    val unsupportedCategories: List<String>,
) {
    val netBudgetChangeCents: Long = changes.sumOf { it.proposedCents - it.currentCents }
}

/** Preview-first planner for the UI-managed target types Actua can evaluate exactly. */
object BudgetTemplatePlanner {
    fun preview(groups: List<BudgetGroup>, month: String): BudgetTemplatePreview {
        val changes = mutableListOf<BudgetTemplateChange>()
        val unsupported = mutableListOf<String>()
        var unchanged = 0
        for (group in groups.filterNot(BudgetGroup::isIncome)) {
            for (category in group.categories.filterNot(BudgetCategory::isIncome)) {
                if (category.hasUnsupportedTarget) {
                    unsupported += "${group.name} · ${category.name}"
                    continue
                }
                val targets = category.automations.ifEmpty { category.target?.let(::listOf).orEmpty() }
                if (targets.isEmpty()) continue
                if (targets.size > 1) {
                    unsupported += "${group.name} · ${category.name}"
                    continue
                }
                val proposed = targets.single().suggestedBudget(category, month)
                if (proposed == category.assignedCents) {
                    unchanged++
                } else {
                    val id = category.id
                    if (id == null) unsupported += "${group.name} · ${category.name}"
                    else changes += BudgetTemplateChange(
                        group.name, id, category.name, category.assignedCents, proposed,
                    )
                }
            }
        }
        return BudgetTemplatePreview(month, changes, unchanged, unsupported)
    }
}
