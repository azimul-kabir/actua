package com.azimulkabir.actua.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * Actual's per-category `cleanup_def` cleanup automation row. `groupId == null` means the
 * global (whole-budget) source/sink pool rather than a named cleanup group.
 */
data class CleanupTarget(
    val role: Role,
    val groupId: String? = null,
    val weight: Int = 1,
) {
    enum class Role(val jsonValue: String) {
        SOURCE("source"),
        SINK("sink"),
        OVERSPEND("overspend"),
    }

    companion object {
        /** Decodes `cleanup_def`, mirroring Actual's `CleanupTemplate[]` shape exactly. */
        fun decode(raw: String?): CleanupDefinition {
            if (raw.isNullOrBlank()) return CleanupDefinition(emptyList())
            val array = runCatching { JSONArray(raw) }.getOrNull()
                ?: return CleanupDefinition(emptyList(), invalid = true)
            val rows = (0 until array.length()).mapNotNull(array::optJSONObject)
            if (rows.size != array.length()) return CleanupDefinition(emptyList(), invalid = true)
            if (rows.isEmpty()) return CleanupDefinition(emptyList())
            val targets = mutableListOf<CleanupTarget>()
            for (row in rows) {
                val role = when (row.optString("role")) {
                    "source" -> Role.SOURCE
                    "sink" -> Role.SINK
                    "overspend" -> Role.OVERSPEND
                    else -> null
                } ?: return CleanupDefinition(emptyList(), invalid = true)
                val groupId = if (!row.has("groupId") || row.isNull("groupId")) null else row.optString("groupId").ifBlank { null }
                if (role == Role.OVERSPEND && groupId == null) return CleanupDefinition(emptyList(), invalid = true)
                val weight = row.optInt("weight", 1)
                if (role == Role.SINK && weight <= 0) return CleanupDefinition(emptyList(), invalid = true)
                targets += CleanupTarget(role, groupId, weight)
            }
            return CleanupDefinition(targets)
        }

        fun encode(targets: List<CleanupTarget>): String? {
            if (targets.isEmpty()) return null
            val array = JSONArray()
            targets.forEach { target ->
                val row = JSONObject().put("role", target.role.jsonValue)
                    .put("groupId", target.groupId ?: JSONObject.NULL)
                if (target.role == Role.SINK) row.put("weight", target.weight)
                array.put(row)
            }
            return array.toString()
        }
    }
}

/** Loss-aware projection of a category's `cleanup_def`; `invalid` categories are never rewritten. */
data class CleanupDefinition(val targets: List<CleanupTarget>, val invalid: Boolean = false)

/** Actual's `cleanup_groups` row: a named pool referenced by source/sink/overspend rows. */
data class CleanupGroup(val id: String, val name: String)
