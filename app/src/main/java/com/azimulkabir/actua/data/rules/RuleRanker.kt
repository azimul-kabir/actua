package com.azimulkabir.actua.data.rules

object RuleRanker {
    private val scores = mapOf("is" to 10, "isNot" to 10, "oneOf" to 9, "notOneOf" to 9,
        "isapprox" to 5, "isbetween" to 5, "gt" to 1, "gte" to 1, "lt" to 1, "lte" to 1,
        "contains" to 0, "doesNotContain" to 0, "matches" to 0, "hasTags" to 0,
        "hasAnyTag" to 0, "onBudget" to 0, "offBudget" to 0)
    private val doubled = setOf("is", "isNot", "isapprox", "oneOf", "notOneOf")

    fun score(rule: Rule): Int {
        var total = 0
        rule.conditions.forEach { total = scores[it.op]?.let(total::plus) ?: 0 }
        return if (rule.conditions.isNotEmpty() && rule.conditions.all { it.op in doubled }) total * 2 else total
    }
    fun rank(rules: List<Rule>): List<Rule> = Rule.Stage.entries.flatMap { stage ->
        rules.filter { it.stage == stage }.sortedWith(compareBy(::score, Rule::id))
    }
}
