package com.azimulkabir.actua.data.budget

import org.json.JSONObject

data class BudgetMetadata(
    val id: String,
    val budgetName: String?,
    val cloudFileId: String?,
    val groupId: String?,
    val resetClock: Boolean?,
    val lastUploaded: String?,
    val encryptKeyId: String?,
) {
    companion object {
        fun fromJson(json: JSONObject): BudgetMetadata {
            val id = json.optString("id").takeIf(String::isNotBlank)
                ?: throw BudgetFileException.InvalidMetadata
            return BudgetMetadata(
                id = id,
                budgetName = json.optionalString("budgetName"),
                cloudFileId = json.optionalString("cloudFileId"),
                groupId = json.optionalString("groupId"),
                resetClock = if (json.has("resetClock") && !json.isNull("resetClock")) {
                    json.getBoolean("resetClock")
                } else null,
                lastUploaded = json.optionalString("lastUploaded"),
                encryptKeyId = json.optionalString("encryptKeyId"),
            )
        }
    }
}

private fun JSONObject.optionalString(key: String): String? =
    if (has(key) && !isNull(key)) optString(key).takeIf(String::isNotBlank) else null
