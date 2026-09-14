package com.azimulkabir.actua.model

import org.json.JSONArray
import org.json.JSONObject

/** Loss-aware projection of Actual's categories.goal_def automation array. */
data class BudgetAutomationDocument(
    val supported: List<BudgetTarget>,
    val unsupportedTypes: List<String> = emptyList(),
    val editable: Boolean = true,
) {
    val hasUnsupported: Boolean get() = unsupportedTypes.isNotEmpty() || !editable

    companion object {
        fun decode(
            raw: String?,
            source: String?,
            percentageSources: Set<String> = setOf("available funds"),
        ): BudgetAutomationDocument {
            if (raw.isNullOrBlank()) return BudgetAutomationDocument(emptyList())
            val array = runCatching { JSONArray(raw) }.getOrNull()
                ?: return BudgetAutomationDocument(emptyList(), listOf("invalid definition"), source == "ui")
            val rows = (0 until array.length()).mapNotNull(array::optJSONObject)
            if (rows.size != array.length()) {
                return BudgetAutomationDocument(emptyList(), listOf("invalid definition"), false)
            }
            val supported = mutableListOf<BudgetTarget>()
            val unsupported = mutableListOf<String>()
            val consumed = mutableSetOf<Int>()
            val limit = rows.indexOfFirst { it.optString("type") == "limit" }
            val refill = rows.indexOfFirst { it.optString("type") == "refill" }
            val refillTarget = if (limit >= 0 && refill >= 0) {
                consumed += limit
                consumed += refill
                (rows[limit].optDouble("amount", 0.0) * 100.0).toLong().takeIf { it > 0 }
                    ?.let { BudgetTarget(BudgetTarget.Type.REFILL, it, priority = rows[refill].optInt("priority", 1)) }
                    ?: run { unsupported += "limit/refill"; null }
            } else null
            rows.forEachIndexed { index, row ->
                if (index in consumed) {
                    if (index == minOf(limit, refill) && refillTarget != null) supported += refillTarget
                    return@forEachIndexed
                }
                val type = row.optString("type").ifBlank { "unknown" }
                if (type == "limit" || type == "refill") {
                    unsupported += type
                } else {
                    val target = BudgetTarget.fromGoalDef(
                        JSONArray().put(JSONObject(row.toString())).toString(),
                        "ui",
                        percentageSources,
                    )
                    if (target == null) unsupported += type
                    else supported += target
                }
            }
            if (supported.filter { it.type == BudgetTarget.Type.BY_DATE }.map(BudgetTarget::priority).distinct().size > 1) {
                unsupported += "by priorities"
            }
            if (validate(supported).isNotEmpty()) unsupported += "invalid supported definition"
            return BudgetAutomationDocument(supported, unsupported.distinct(), editable = source == "ui")
        }

        fun encode(targets: List<BudgetTarget>): String? {
            if (targets.isEmpty()) return null
            validate(targets).firstOrNull()?.let { throw IllegalArgumentException(it) }
            val array = JSONArray()
            targets.forEach { target ->
                val encoded = JSONArray(target.toGoalDef())
                for (index in 0 until encoded.length()) array.put(encoded.getJSONObject(index))
            }
            return array.toString()
        }

        fun validate(targets: List<BudgetTarget>): List<String> = buildList {
            if (targets.size > 20) add("A category can have at most 20 automations")
            if (targets.count { it.type == BudgetTarget.Type.REFILL } > 1) add("Only one refill automation is allowed")
            if (targets.count { it.type == BudgetTarget.Type.GOAL } > 1) add("Only one goal-only automation is allowed")
            if (targets.any { it.type == BudgetTarget.Type.REMAINDER && it.weight < 1 }) {
                add("Remainder weights must be at least 1")
            }
            if (targets.any {
                    it.type == BudgetTarget.Type.REMAINDER && it.limitPeriod != null &&
                        it.limitAmountCents != null && it.limitAmountCents <= 0
                }) {
                add("Remainder limits must be positive")
            }
            if (targets.any {
                    it.type == BudgetTarget.Type.REMAINDER &&
                        ((it.limitPeriod == null) != (it.limitAmountCents == null))
                }) {
                add("Remainder limits need both a period and amount")
            }
            if (targets.any {
                    it.type == BudgetTarget.Type.REMAINDER &&
                        it.limitPeriod == BudgetTarget.LimitPeriod.WEEKLY &&
                        (it.limitStartDate.isNullOrBlank() ||
                            runCatching { java.time.LocalDate.parse(it.limitStartDate) }.isFailure)
                }) {
                add("Weekly remainder limits need a valid start date")
            }
            if (targets.any { it.priority < 0 }) add("Automation priority cannot be negative")
            if (targets.any { it.type == BudgetTarget.Type.PERCENTAGE && it.percentage !in 1..100 }) {
                add("Percentage automations must be between 1 and 100")
            }
            if (targets.any { it.type == BudgetTarget.Type.COPY && it.lookBackMonths !in 1..24 }) {
                add("Copy automations must look back 1 to 24 months")
            }
            if (targets.any { it.type == BudgetTarget.Type.PERCENTAGE && it.percentageSource.isBlank() }) {
                add("Percentage automations need an income source")
            }
            if (targets.any { it.type == BudgetTarget.Type.PERCENTAGE && it.percentagePrevious }) {
                add("Previous-month percentage automations are not supported")
            }
            if (targets.any {
                    it.type == BudgetTarget.Type.SCHEDULE &&
                        it.scheduleId.isNullOrBlank() && it.scheduleName.isNullOrBlank()
                }) {
                add("Schedule automations need a schedule ID or name")
            }
            val scheduleAndByPriorities = targets.filter {
                it.type == BudgetTarget.Type.SCHEDULE || it.type == BudgetTarget.Type.BY_DATE
            }.map(BudgetTarget::priority).distinct()
            if (scheduleAndByPriorities.size > 1) {
                add("Schedule and date automations must use the same priority")
            }
            if (targets.filter { it.type == BudgetTarget.Type.BY_DATE }.map(BudgetTarget::priority).distinct().size > 1) {
                add("Date targets must use the same priority")
            }
            targets.forEachIndexed { index, target ->
                if (target.type != BudgetTarget.Type.AVERAGE && target.type != BudgetTarget.Type.COPY &&
                    target.type != BudgetTarget.Type.REMAINDER &&
                    target.type != BudgetTarget.Type.PERCENTAGE && target.type != BudgetTarget.Type.SCHEDULE &&
                    target.amountCents <= 0
                ) {
                    add("Automation ${index + 1} needs a positive amount")
                }
                if (target.type == BudgetTarget.Type.AVERAGE && target.averageMonths !in 1..24) {
                    add("Automation ${index + 1} must average 1 to 24 months")
                }
                if (target.type == BudgetTarget.Type.BY_DATE &&
                    runCatching { java.time.YearMonth.parse(target.targetMonth.orEmpty()) }.isFailure
                ) add("Automation ${index + 1} needs a valid target month")
                if (target.type == BudgetTarget.Type.WEEKLY_SPENDING && target.startingDate != null &&
                    runCatching { java.time.LocalDate.parse(target.startingDate.orEmpty()) }.isFailure
                ) add("Automation ${index + 1} needs a valid starting date")
            }
        }
    }
}
