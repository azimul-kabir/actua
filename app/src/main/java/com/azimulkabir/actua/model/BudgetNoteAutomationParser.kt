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

    fun parse(note: String): BudgetNoteAutomationParse {
        val targets = mutableListOf<BudgetTarget>()
        val errors = mutableListOf<String>()
        note.lineSequence().forEachIndexed { index, raw ->
            val line = raw.trim()
            if (line.isBlank() || !line.startsWith("#")) return@forEachIndexed
            val goalMatch = goal.matchEntire(line)
            if (goalMatch != null) {
                val cents = parseCents(goalMatch.groupValues[1])
                if (cents == null) {
                    errors += "Line ${index + 1}: invalid goal amount"
                } else {
                    targets += BudgetTarget(BudgetTarget.Type.GOAL, cents)
                }
                return@forEachIndexed
            }
            val match = directive.matchEntire(line)
            if (match == null) {
                if (line.startsWith("#template", ignoreCase = true)) {
                    errors += "Line ${index + 1}: invalid template directive"
                }
                return@forEachIndexed
            }
            // Actual's grammar stores `+priority`, so a line without `-N` has priority 0.
            val priority = match.groupValues[1].toIntOrNull() ?: 0
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
                return BudgetTarget(
                    BudgetTarget.Type.HISTORICAL,
                    historicalMode = BudgetTarget.HistoricalMode.COPY,
                    historicalMonths = it.groupValues[1].toInt(),
                    priority = priority,
                )
            }
        Regex("""average\s+([1-9]\d*)\s+months?$""", RegexOption.IGNORE_CASE)
            .matchEntire(body)?.let {
                return BudgetTarget(
                    BudgetTarget.Type.HISTORICAL,
                    historicalMode = BudgetTarget.HistoricalMode.AVERAGE,
                    historicalMonths = it.groupValues[1].toInt(),
                    priority = priority,
                )
            }
        Regex("""remainder(?:\s+([1-9]\d*))?$""", RegexOption.IGNORE_CASE)
            .matchEntire(body)?.let {
                return BudgetTarget(BudgetTarget.Type.REMAINDER, weight = it.groupValues[1].toIntOrNull() ?: 1)
            }
        Regex("""([0-9]+(?:\.[0-9]+)?)\s+repeat\s+every\s+month(?:s)?\s+starting\s+(\d{4}-\d{2}-\d{2})$""", RegexOption.IGNORE_CASE)
            .matchEntire(body)?.let {
                return parseCents(it.groupValues[1])?.let { cents ->
                    BudgetTarget(BudgetTarget.Type.FIXED, cents, startingDate = it.groupValues[2], priority = priority)
                }
            }
        Regex("""([0-9]+(?:\.[0-9]+)?)\s+by\s+(\d{4}-\d{2})$""", RegexOption.IGNORE_CASE)
            .matchEntire(body)?.let {
                return parseCents(it.groupValues[1])?.let { cents ->
                    BudgetTarget(BudgetTarget.Type.BY_DATE, cents, targetMonth = it.groupValues[2], priority = priority)
                }
            }
        return parseSimple(body, priority)
    }

    /** Actual's `simple` template: `50`, `50 up to 100`, `up to 100 per day`, `up to 25 per week starting 2026-07-06 hold`. */
    private fun parseSimple(body: String, priority: Int): BudgetTarget? {
        val match = simple.matchEntire(body) ?: return null
        val (monthlyRaw, limitRaw, weekStart, perDay, hold) = match.destructured
        if (monthlyRaw.isEmpty() && limitRaw.isEmpty()) return null
        val monthly = if (monthlyRaw.isEmpty()) 0L else parseCents(monthlyRaw) ?: return null
        val limit = if (limitRaw.isEmpty()) null else parseCents(limitRaw) ?: return null
        val period = when {
            limit == null -> null
            weekStart.isNotEmpty() -> BudgetTarget.LimitPeriod.WEEKLY
            perDay.isNotEmpty() -> BudgetTarget.LimitPeriod.DAILY
            else -> BudgetTarget.LimitPeriod.MONTHLY
        }
        return BudgetTarget(
            BudgetTarget.Type.FIXED, monthly, priority = priority, simple = true,
            limitPeriod = period, limitAmountCents = limit,
            limitStartDate = weekStart.ifEmpty { null }, limitHold = hold.isNotEmpty(),
        )
    }

    private val simple = Regex(
        """(?:([0-9]+(?:\.[0-9]{1,2})?))?\s*(?:up\s+to\s+([0-9]+(?:\.[0-9]{1,2})?)""" +
            """(?:\s+per\s+week\s+starting\s+(\d{4}-\d{2}-\d{2})|\s*(per\s+day))?(\s+hold)?)?""",
        RegexOption.IGNORE_CASE,
    )

    private fun parseCents(raw: String): Long? = runCatching {
        BigDecimal(raw).movePointRight(2).longValueExact()
    }.getOrNull()?.takeIf { it > 0 }
}
