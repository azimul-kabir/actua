package com.azimulkabir.actua.model

import java.math.BigDecimal

data class BudgetNoteAutomationParse(
    val targets: List<BudgetTarget>,
    val errors: List<String>,
) {
    val valid: Boolean get() = errors.isEmpty()
}

object BudgetNoteAutomationParser {
    private val directive = Regex("""^#template(?:-(\d+))?\s+(.+)$""", RegexOption.IGNORE_CASE)
    private val goal = Regex("""^#goal\s+(-?\d+(?:\.\d+)?)$""", RegexOption.IGNORE_CASE)
    private val amount = Regex("""-?\d+(?:\.\d+)?""")

    fun parse(note: String): BudgetNoteAutomationParse {
        val targets = mutableListOf<BudgetTarget>()
        val errors = mutableListOf<String>()
        note.lineSequence().forEachIndexed { index, raw ->
            val line = raw.trim()
            if (line.isBlank() || !line.startsWith("#")) return@forEachIndexed
            val goalMatch = goal.matchEntire(line)
            if (goalMatch != null) {
                parseCents(goalMatch.groupValues[1])?.let {
                    targets += BudgetTarget(BudgetTarget.Type.GOAL, it)
                } ?: errors += "Line ${index + 1}: invalid goal amount"
                return@forEachIndexed
            }
            val match = directive.matchEntire(line)
            if (match == null) {
                if (line.startsWith("#template", ignoreCase = true)) {
                    errors += "Line ${index + 1}: invalid template directive"
                }
                return@forEachIndexed
            }
            val priority = match.groupValues[1].toIntOrNull() ?: 1
            val body = match.groupValues[2].trim()
            val target = parseBody(body, priority)
            if (target == null) errors += "Line ${index + 1}: unsupported template directive"
            else targets += target
        }
        if (targets.isEmpty() && errors.isEmpty() && note.contains("#")) {
            errors += "No supported template directives found"
        }
        errors += BudgetAutomationDocument.validate(targets)
        return BudgetNoteAutomationParse(targets, errors.distinct())
    }

    private fun parseBody(body: String, priority: Int): BudgetTarget? {
        Regex("""copy\s+from\s+([1-9]\d*)\s+months?\s+ago$""", RegexOption.IGNORE_CASE)
            .matchEntire(body)?.let {
                return BudgetTarget(BudgetTarget.Type.COPY, lookBackMonths = it.groupValues[1].toInt(), priority = priority)
            }
        Regex("""average\s+([1-9]\d*)\s+months?$""", RegexOption.IGNORE_CASE)
            .matchEntire(body)?.let {
                return BudgetTarget(BudgetTarget.Type.AVERAGE, averageMonths = it.groupValues[1].toInt(), priority = priority)
            }
        Regex("""remainder(?:\s+([1-9]\d*))?$""", RegexOption.IGNORE_CASE)
            .matchEntire(body)?.let {
                return BudgetTarget(BudgetTarget.Type.REMAINDER, weight = it.groupValues[1].toIntOrNull() ?: 1)
            }
        Regex("""([0-9]+(?:\.[0-9]+)?)\s+repeat\s+every\s+month(?:s)?\s+starting\s+(\d{4}-\d{2}-\d{2})$""", RegexOption.IGNORE_CASE)
            .matchEntire(body)?.let {
                return parseCents(it.groupValues[1])?.let { cents ->
                    BudgetTarget(BudgetTarget.Type.MONTHLY_SAVINGS, cents, startingDate = it.groupValues[2], priority = priority)
                }
            }
        Regex("""([0-9]+(?:\.[0-9]+)?)\s+by\s+(\d{4}-\d{2})$""", RegexOption.IGNORE_CASE)
            .matchEntire(body)?.let {
                return parseCents(it.groupValues[1])?.let { cents ->
                    BudgetTarget(BudgetTarget.Type.BY_DATE, cents, targetMonth = it.groupValues[2], priority = priority)
                }
            }
        parseCents(amount.find(body)?.value ?: return null)?.let { cents ->
            if (body.matches(Regex("""[0-9]+(?:\.[0-9]+)?"""))) {
                return BudgetTarget(BudgetTarget.Type.MONTHLY_SPENDING, cents, priority = priority)
            }
        }
        return null
    }

    private fun parseCents(raw: String): Long? = runCatching {
        BigDecimal(raw).movePointRight(2).longValueExact()
    }.getOrNull()?.takeIf { it > 0 }
}
