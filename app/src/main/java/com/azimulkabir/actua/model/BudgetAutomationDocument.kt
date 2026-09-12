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
        fun decode(raw: String?, source: String?): BudgetAutomationDocument {
            if (raw.isNullOrBlank()) return BudgetAutomationDocument(emptyList())
            val array = runCatching { JSONArray(raw) }.getOrNull()
                ?: return BudgetAutomationDocument(emptyList(), listOf("invalid definition"), false)
            if (source != "ui") return BudgetAutomationDocument(
                emptyList(),
                (0 until array.length()).map { array.optJSONObject(it)?.optString("type").orEmpty().ifBlank { "unknown" } },
                false,
            )

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
                    ?.let { BudgetTarget(BudgetTarget.Type.REFILL, it) }
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
                    val target = BudgetTarget.fromGoalDef(JSONArray().put(JSONObject(row.toString())).toString(), "ui")
                    if (target == null) unsupported += type else supported += target
                }
            }
            return BudgetAutomationDocument(supported, unsupported.distinct())
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
            targets.forEachIndexed { index, target ->
                if (target.type != BudgetTarget.Type.AVERAGE && target.amountCents <= 0) {
                    add("Automation ${index + 1} needs a positive amount")
                }
                if (target.type == BudgetTarget.Type.AVERAGE && target.averageMonths !in 1..24) {
                    add("Automation ${index + 1} must average 1 to 24 months")
                }
            }
        }
    }
}
