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
    val weight: Int = 1,
    val limitPeriod: LimitPeriod? = null,
    val limitAmountCents: Long? = null,
    val limitStartDate: String? = null,
    val limitHold: Boolean = false,
    val percentage: Int = 0,
    val percentageSource: String = "available funds",
    val percentagePrevious: Boolean = false,
    val scheduleId: String? = null,
    val scheduleName: String? = null,
) {
    enum class Type(val label: String, val explanation: String) {
        MONTHLY_SPENDING("Monthly spending", "Set aside enough for this month's spending"),
        MONTHLY_SAVINGS("Save every month", "Add the same amount every month"),
        BY_DATE("Have amount by a date", "Spread the remaining amount across the months until a date"),
        REFILL("Refill up to amount", "Top the category back up to a balance cap"),
        WEEKLY_SPENDING("Spend every week", "Budget this amount for each week in the month"),
        AVERAGE("Average recent spending", "Use the average of recent months"),
        GOAL("Goal only", "Show a target balance without automatically budgeting money"),
        REMAINDER("Split remaining funds", "Receive a weighted share of Ready to Budget after other automations"),
        PERCENTAGE("Percentage of available funds", "Budget a percentage of funds available at this priority"),
        SCHEDULE("Cover scheduled transaction", "Save up for a scheduled transaction"),
    }

    enum class LimitPeriod(val jsonValue: String) {
        DAILY("daily"),
        WEEKLY("weekly"),
        MONTHLY("monthly");

        companion object {
            fun fromJson(raw: String?): LimitPeriod? = when (raw?.trim()?.lowercase()) {
                "daily" -> DAILY
                "weekly" -> WEEKLY
                "monthly" -> MONTHLY
                else -> null
            }
        }
    }

    fun limitForMonth(month: String): Long {
        val limit = limitAmountCents ?: return 0L
        return when (limitPeriod ?: LimitPeriod.MONTHLY) {
            LimitPeriod.DAILY -> {
                val days = runCatching { YearMonth.parse(month).lengthOfMonth().toLong() }.getOrDefault(0L)
                Math.multiplyExact(limit, days)
            }
            LimitPeriod.WEEKLY -> {
                val monthYear = runCatching { YearMonth.parse(month) }.getOrNull() ?: return 0L
                val monthStart = monthYear.atDay(1)
                val nextMonthStart = monthYear.plusMonths(1).atDay(1)
                val startDate = runCatching { LocalDate.parse(limitStartDate ?: monthStart.toString()) }.getOrNull() ?: monthStart
                var date = startDate
                var weeks = 0L
                while (date.isBefore(nextMonthStart)) {
                    if (!date.isBefore(monthStart)) weeks += 1L
                    date = date.plusWeeks(1)
                }
                Math.multiplyExact(limit, weeks)
            }
            LimitPeriod.MONTHLY -> limit
        }
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
        Type.REMAINDER -> 0L
        Type.PERCENTAGE -> 0L
        Type.SCHEDULE -> 0L
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
            Type.REMAINDER -> {
                val remainder = JSONObject().put("directive", "template").put("type", "remainder")
                    .put("priority", JSONObject.NULL).put("weight", weight)
                val limit = limitPeriod?.let { period ->
                    val raw = JSONObject().put("amount", units(limitAmountCents ?: 0L)).put("period", period.jsonValue)
                        .put("hold", limitHold)
                    if (period == LimitPeriod.WEEKLY && !limitStartDate.isNullOrBlank()) {
                        raw.put("start", limitStartDate)
                    }
                    raw
                }
                if (limit != null) remainder.put("limit", limit)
                return JSONArray().put(remainder).toString()
            }
            Type.PERCENTAGE -> return JSONArray().put(
                JSONObject().put("directive", "template").put("type", "percentage")
                    .put("priority", priority).put("percent", percentage)
                    .put("category", percentageSource).put("previous", percentagePrevious),
            ).toString()
            Type.SCHEDULE -> {
                val schedule = JSONObject().put("directive", "template").put("type", "schedule")
                    .put("priority", priority)
                scheduleId?.takeIf(String::isNotBlank)?.let { schedule.put("scheduleId", it) }
                scheduleName?.takeIf(String::isNotBlank)?.let { schedule.put("name", it) }
                return JSONArray().put(schedule).toString()
            }
        }
        return JSONArray().put(row).toString()
    }

    companion object {
        fun fromGoalDef(
            raw: String?,
            source: String?,
            percentageSources: Set<String> = setOf("available funds"),
        ): BudgetTarget? {
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
            val limit = row.optJSONObject("limit")
            val parsedLimitPeriod = limit?.optString("period")?.let(LimitPeriod::fromJson)
            val parsedLimitCents = limit?.takeIf { it.has("amount") }?.let {
                val amount = it.optDouble("amount", Double.NaN)
                (amount * 100.0).toLong().takeIf { amount.isFinite() && it > 0 }
            }
            val validLimit = limit == null || (
                parsedLimitPeriod != null &&
                    parsedLimitCents != null &&
                    (parsedLimitPeriod != LimitPeriod.WEEKLY ||
                        !limit.optString("start").isNullOrBlank() &&
                        runCatching { LocalDate.parse(limit.optString("start")) }.isSuccess)
                )
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
                "remainder" -> row.takeIf {
                    it.optString("directive") == "template" && it.has("priority") && it.isNull("priority") &&
                        it.optInt("weight", 0) > 0
                }?.let {
                    val limitPeriod = parsedLimitPeriod
                    val limitCents = parsedLimitCents
                    if (!validLimit) null else BudgetTarget(
                        Type.REMAINDER,
                        weight = row.getInt("weight"),
                        limitPeriod = limitPeriod,
                        limitAmountCents = limitCents,
                        limitStartDate = limit?.optString("start")?.ifBlank { null },
                        limitHold = limit?.optBoolean("hold", false) == true,
                    )
                }
                "percentage" -> row.takeIf {
                    it.optString("directive") == "template" &&
                        it.optString("category").isNotBlank() &&
                        it.optString("category").lowercase() in percentageSources.map(String::lowercase).toSet() &&
                        it.optInt("percent", 0) in 1..100 &&
                        !it.optBoolean("previous", false)
                }?.let {
                    BudgetTarget(
                        Type.PERCENTAGE,
                        priority = priority,
                        percentage = it.optInt("percent"),
                    )
                }
                "schedule" -> row.takeIf {
                    it.optString("directive") == "template" &&
                        (it.optString("scheduleId").isNotBlank() || it.optString("name").isNotBlank())
                }?.let {
                    BudgetTarget(
                        Type.SCHEDULE,
                        priority = priority,
                        scheduleId = it.optString("scheduleId").ifBlank { null },
                        scheduleName = it.optString("name").ifBlank { null },
                    )
                }
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
    val cappedCategories: List<String> = emptyList(),
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
        schedules: List<BudgetScheduleFunding> = emptyList(),
    ): BudgetTemplatePreview {
        val changes = mutableListOf<BudgetTemplateChange>()
        val unsupported = mutableListOf<String>()
        val limited = mutableListOf<String>()
        val capped = mutableListOf<String>()
        val goalChanges = mutableListOf<BudgetGoalChange>()
        var unchanged = 0
        var skippedExisting = 0
        val supported = groups.filterNot { it.isIncome || it.hidden }.flatMap { group ->
            group.categories.filterNot { it.isIncome || it.hidden }.map { group to it }
        }
        val scheduleNames = schedules.flatMap { it.referenceNames }.toSet()
        val percentageSources = groups.filter { it.isIncome }.flatMap { it.categories }.flatMap { category ->
            listOfNotNull(category.id, category.name).map { it to category.balanceCents.coerceAtLeast(0L) }
        }.toMap() + ("all income" to groups.filter { it.isIncome }.flatMap { it.categories }
            .sumOf { it.balanceCents.coerceAtLeast(0L) })
        val eligible = supported.filter { (_, category) ->
            val hasTargets = category.automations.isNotEmpty() || category.target != null
            val targets = category.automations.ifEmpty { category.target?.let(::listOf).orEmpty() }
            val unresolvedSchedule = targets.any {
                it.type == BudgetTarget.Type.SCHEDULE &&
                    ((it.scheduleId ?: it.scheduleName).orEmpty() !in scheduleNames ||
                        schedules.none { schedule ->
                            targetReference(it) in schedule.referenceNames &&
                                (schedule.categoryId == null || schedule.categoryId == category.id)
                        })
            }
            val canRun = !category.hasUnsupportedTarget && hasTargets && !unresolvedSchedule
            if (canRun && !overwriteExisting && category.assignedCents != 0L) skippedExisting++
            canRun && (overwriteExisting || category.assignedCents == 0L)
        }
        eligible.forEach { (group, category) ->
            if (remainderLimit(category) != null) capped += "${group.name} · ${category.name}"
        }
        val proposed = mutableMapOf<BudgetCategory, Long>()
        var available = if (availableBudgetCents == Long.MAX_VALUE) Long.MAX_VALUE else
            availableBudgetCents + if (overwriteExisting) eligible.sumOf { it.second.assignedCents } else 0L
        val releasedByLimit = eligible.sumOf { (_, category) ->
            val limit = remainderLimit(category)?.limitForMonth(month) ?: return@sumOf 0L
            val excess = max(0L, category.carryoverCents - limit)
            if (excess > 0L && remainderLimit(category)?.limitHold == false) excess else 0L
        }
        if (available != Long.MAX_VALUE) available += releasedByLimit
        eligible.forEach { (_, category) ->
            val limitTarget = remainderLimit(category) ?: return@forEach
            val limit = limitTarget.limitForMonth(month)
            val excess = max(0L, category.carryoverCents - limit)
            if (excess > 0L && !limitTarget.limitHold) proposed[category] = -excess
        }
        val priorities = eligible.flatMap {
            it.second.automations.ifEmpty { listOfNotNull(it.second.target) }
        }.filterNot { it.type == BudgetTarget.Type.REMAINDER }
            .map(BudgetTarget::priority).distinct().sorted()

        for (priority in priorities) {
            val priorityAvailableStart = available
            for ((group, category) in eligible) {
                val targets = category.automations.ifEmpty { category.target?.let(::listOf).orEmpty() }
                if (targets.isEmpty()) continue
                val remainderLimit = remainderLimit(category)
                if (remainderLimit != null &&
                    category.carryoverCents >= remainderLimit.limitForMonth(month)
                ) continue
                val atPriority = targets.filter { it.priority == priority }
                if (atPriority.isEmpty()) continue
                val before = proposed[category] ?: 0L
                val requested = requestedAtPriority(
                    atPriority, category, month, priorityAvailableStart, schedules, percentageSources,
                )
                val refillCap = targets.firstOrNull { it.type == BudgetTarget.Type.REFILL }?.amountCents
                val remainderCap = remainderLimit?.limitForMonth(month)
                val cap = listOfNotNull(refillCap, remainderCap).minOrNull()
                val capped = cap?.let { minOf(requested, max(0L, it - category.carryoverCents - before)) } ?: requested
                val allocated = if (available == Long.MAX_VALUE || priority <= 0) capped else
                    minOf(capped, max(0L, available))
                if (allocated < capped) limited += "${group.name} · ${category.name}"
                proposed[category] = before + allocated
                if (available != Long.MAX_VALUE) available -= allocated
            }
        }
        distributeRemainder(eligible, proposed, available, month)
        for ((group, category) in supported) {
            val targets = category.automations.ifEmpty { category.target?.let(::listOf).orEmpty() }
            val unresolvedSchedule = targets.any {
                it.type == BudgetTarget.Type.SCHEDULE &&
                    schedules.none { schedule ->
                        targetReference(it) in schedule.referenceNames && schedule.active &&
                            (schedule.categoryId == null || schedule.categoryId == category.id)
                    }
            }
            if (category.hasUnsupportedTarget || unresolvedSchedule) {
                unsupported += "${group.name} · ${category.name}"
                continue
            }
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
            month = month,
            changes = changes,
            unchangedCount = unchanged,
            unsupportedCategories = unsupported.distinct(),
            limitedCategories = limited.distinct(),
            cappedCategories = capped.distinct(),
            skippedExistingCount = skippedExisting,
            overwriteExisting = overwriteExisting,
            goalChanges = goalChanges,
        )
    }

    private fun distributeRemainder(
        eligible: List<Pair<BudgetGroup, BudgetCategory>>,
        proposed: MutableMap<BudgetCategory, Long>,
        startingAvailable: Long,
        month: String,
    ): Long {
        var available = startingAvailable
        if (available == Long.MAX_VALUE || available <= 0L) return available
        while (available > 0L) {
            val active = eligible.mapNotNull { (_, category) ->
                val targets = category.automations.ifEmpty { category.target?.let(::listOf).orEmpty() }
                val remainderTargets = targets.filter { it.type == BudgetTarget.Type.REMAINDER }
                val weight = remainderTargets.sumOf { it.weight.toLong() }
                if (weight <= 0L) return@mapNotNull null
                val before = proposed[category] ?: 0L
                val cap = remainderTargets.firstOrNull()?.let { target ->
                    val limit = target.limitAmountCents ?: return@let null
                    val maxLimit = target.limitForMonth(month)
                    val remaining = max(0L, maxLimit - category.carryoverCents - before)
                    if (remaining <= 0L) return@let 0L
                    remaining
                }
                if (cap != null && cap <= 0L) return@mapNotNull null
                Triple(category, weight, cap)
            }
            if (active.isEmpty()) break
            val totalWeight = active.sumOf { it.second }
            val beforePass = available
            val allocations = active.map { (category, weight, cap) ->
                val before = proposed[category] ?: 0L
                val base = (available * weight) / totalWeight
                category to minOf(base, cap ?: Long.MAX_VALUE)
            }
            allocations.forEach { (category, allocated) ->
                if (allocated > 0L) {
                    proposed[category] = (proposed[category] ?: 0L) + allocated
                    available -= allocated
                }
            }
            if (available > 0L) {
                active.asReversed().forEach { (category, _, cap) ->
                    if (available <= 0L) return@forEach
                    val before = proposed[category] ?: 0L
                    val hasCapacity = cap == null || category.carryoverCents + before < cap
                    if (hasCapacity) {
                        proposed[category] = before + 1L
                        available--
                    }
                }
            }
            if (available == beforePass) break
        }
        return available
    }

    private fun remainderLimit(category: BudgetCategory): BudgetTarget? =
        category.automations.ifEmpty { category.target?.let(::listOf).orEmpty() }
            .firstOrNull { it.type == BudgetTarget.Type.REMAINDER && it.limitAmountCents != null }

    private fun requestedAtPriority(
        targets: List<BudgetTarget>,
        category: BudgetCategory,
        month: String,
        availableAtPriorityStart: Long,
        schedules: List<BudgetScheduleFunding>,
        percentageSources: Map<String, Long>,
    ): Long {
        val by = targets.filter { it.type == BudgetTarget.Type.BY_DATE }
        val ordinary = targets.filterNot {
            it.type == BudgetTarget.Type.BY_DATE || it.type == BudgetTarget.Type.REFILL ||
            it.type == BudgetTarget.Type.GOAL || it.type == BudgetTarget.Type.REMAINDER ||
            it.type == BudgetTarget.Type.PERCENTAGE || it.type == BudgetTarget.Type.SCHEDULE
        }
            .sumOf { it.suggestedBudget(category, month) }
        val schedule = targets.filter { it.type == BudgetTarget.Type.SCHEDULE }.sumOf { target ->
            schedules.firstOrNull {
                targetReference(target) in it.referenceNames &&
                    (it.categoryId == null || it.categoryId == category.id)
            }
                ?.requestedBudget(category.carryoverCents) ?: 0L
        }
        val percentage = targets.filter { it.type == BudgetTarget.Type.PERCENTAGE }
            .sumOf { target ->
            val source = if (target.percentageSource.equals("available funds", ignoreCase = true)) {
                availableAtPriorityStart
            } else {
                percentageSources.entries.firstOrNull {
                    it.key.equals(target.percentageSource, ignoreCase = true)
                }?.value ?: 0L
            }
            if (source == Long.MAX_VALUE) 0L
            else Math.round(max(0L, source).toDouble() * target.percentage / 100.0)
            }
        val byAmount = if (by.isEmpty()) 0L else combinedByDate(by, category, month)
        val refill = targets.firstOrNull { it.type == BudgetTarget.Type.REFILL }
            ?.let { max(0L, it.amountCents - category.carryoverCents) } ?: 0L
        return max(0L, ordinary + byAmount + refill + percentage + schedule)
    }

    private fun targetReference(target: BudgetTarget): String =
        target.scheduleId?.takeIf(String::isNotBlank)
            ?: target.scheduleName?.trim().orEmpty()

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
