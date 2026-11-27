package com.azimulkabir.actua.data.importing

import org.json.JSONArray
import org.json.JSONObject

internal class InvalidCapturePattern(val index: Int, val problem: CapturePatternProblem) : IllegalArgumentException()

/** Shareable rules only. This file never contains notification history, tokens, or account mappings. */
internal object CapturePatternJson {
    fun encode(patterns: List<FinancialCapturePattern>): String = JSONObject()
        .put("version", 1)
        .put("patterns", JSONArray(patterns.map { rule ->
            JSONObject().put("name", rule.name).put("expression", rule.expression)
                .put("sourcePackage", rule.sourcePackage ?: JSONObject.NULL)
                .put("direction", rule.direction.name).put("dateFormat", rule.dateFormat)
                .put("decimalSeparator", rule.decimalSeparator.toString())
                .put("groupingSeparator", rule.groupingSeparator?.toString() ?: JSONObject.NULL)
        })).toString(2)

    fun decode(json: String): List<FinancialCapturePattern> {
        require(json.length <= MAX_CAPTURE_PATTERN_FILE_BYTES)
        val document = JSONObject(json)
        require(document.getInt("version") == 1)
        val rules = document.getJSONArray("patterns")
        require(rules.length() <= MAX_CAPTURE_PATTERNS)
        return List(rules.length()) { index ->
            val item = rules.getJSONObject(index)
            fun optional(key: String) = if (item.isNull(key)) null else item.getString(key)
            val decimal = item.getString("decimalSeparator").singleOrNull()
                ?: throw InvalidCapturePattern(index, CapturePatternProblem.SEPARATORS)
            val grouping = optional("groupingSeparator")?.let {
                it.singleOrNull() ?: throw InvalidCapturePattern(index, CapturePatternProblem.SEPARATORS)
            }
            val rule = FinancialCapturePattern(
                name = item.getString("name"),
                expression = item.getString("expression"),
                sourcePackage = optional("sourcePackage"),
                direction = CaptureDirection.valueOf(item.getString("direction")),
                dateFormat = item.optString("dateFormat"),
                decimalSeparator = decimal,
                groupingSeparator = grouping,
            )
            capturePatternProblem(rule)?.let { throw InvalidCapturePattern(index, it) }
            rule
        }
    }
}
