package com.azimulkabir.actua.data.reports

import com.azimulkabir.actua.data.budget.model.ActualAccount
import com.azimulkabir.actua.data.budget.model.ActualCategoryGroup
import com.azimulkabir.actua.data.budget.model.ActualTransaction
import com.azimulkabir.actua.data.rules.Rule
import com.azimulkabir.actua.data.rules.RuleContext
import com.azimulkabir.actua.data.rules.RulesEngine
import com.azimulkabir.actua.model.ReportDashboardPage
import com.azimulkabir.actua.model.ReportPoint
import com.azimulkabir.actua.model.ReportWidget
import com.azimulkabir.actua.model.ReportWidgetKind
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.roundToLong

/** Core Actual dashboard widgets, ported from Actuali's report engines. */
object CoreReportEngine {
    fun dashboards(
        pages: List<DashboardPageRow>,
        widgets: (String?) -> List<DashboardWidgetRow>,
        transactions: List<ActualTransaction>,
        accounts: List<ActualAccount>,
        groups: List<ActualCategoryGroup>,
        budgetedByCategory: (YearMonth) -> Map<String, Long> = { emptyMap() },
        today: LocalDate = LocalDate.now(),
    ): List<ReportDashboardPage> {
        val resolvedPages = if (pages.isEmpty()) listOf(DashboardPageRow("", "Dashboard")) else pages
        val context = RuleContext(
            offBudgetAccountIds = accounts.filter { it.offBudget }.mapTo(mutableSetOf()) { it.id },
            accountNames = accounts.associate { it.id to it.name },
            categoryNames = groups.flatMap { it.categories }.associate { it.id to it.name },
            categoryGroupIds = groups.flatMap { it.categories }.associate { it.id to it.groupId },
            categoryGroupNames = groups.associate { it.id to it.name },
            payeeNames = transactions.mapNotNull { tx -> tx.payeeId?.let { it to tx.payeeName.orEmpty() } }.toMap(),
        )
        val incomeCategories = groups.flatMap { it.categories }.filter { it.isIncome }.mapTo(mutableSetOf()) { it.id }
        return resolvedPages.map { page ->
            ReportDashboardPage(
                page.id,
                page.name.ifBlank { "Untitled" },
                widgets(page.id.ifBlank { null }).map { row ->
                    compute(row, transactions, context, incomeCategories, budgetedByCategory, today)
                },
            )
        }
    }

    fun compute(
        row: DashboardWidgetRow,
        transactions: List<ActualTransaction>,
        context: RuleContext = RuleContext(),
        incomeCategoryIds: Set<String> = emptySet(),
        budgetedByCategory: (YearMonth) -> Map<String, Long> = { emptyMap() },
        today: LocalDate = LocalDate.now(),
    ): ReportWidget {
        val meta = row.metaJson?.let { runCatching { JSONObject(it) }.getOrNull() }
        val name = meta?.optString("name")?.takeIf(String::isNotBlank) ?: label(row.type)
        val conditions = parseConditions(meta)
        val (start, end) = timeFrame(meta?.optJSONObject("timeFrame"), today)
        val filtered = transactions.asSequence()
            .filterNot { it.tombstone }
            .filter { it.date in start.toYmd()..end.toYmd() }
            .filter { RulesEngine.matches(it, conditions.first, conditions.second, context) }
            .toList()
        return when (row.type) {
            "summary-card" -> summary(row.id, name, meta, transactions, conditions, context, start, end, today)
            "net-worth-card" -> netWorth(row.id, name, meta, transactions.filterNot { it.tombstone }
                .filter { RulesEngine.matches(it, conditions.first, conditions.second, context) }, start, end)
            "cash-flow-card" -> cashFlow(row.id, name, filtered.filter { it.transferAccountId == null && it.accountId !in context.offBudgetAccountIds }, start, end)
            "spending-card" -> spending(row.id, name, meta, transactions, context, incomeCategoryIds,
                budgetedByCategory, today)
            "markdown-card" -> ReportWidget(row.id, ReportWidgetKind.MARKDOWN, name,
                markdown = meta?.optString("content").orEmpty())
            else -> ReportWidget(row.id, ReportWidgetKind.UNSUPPORTED, name, sourceType = row.type)
        }
    }

    private fun summary(
        id: String, name: String, meta: JSONObject?, all: List<ActualTransaction>,
        conditions: Pair<List<Rule.Condition>, Rule.ConditionsOp>, context: RuleContext,
        start: LocalDate, end: LocalDate, today: LocalDate,
    ): ReportWidget {
        val contentValue = meta?.opt("content")
        val content = when (contentValue) {
            is JSONObject -> contentValue
            is String -> runCatching { JSONObject(contentValue) }.getOrNull()
            else -> null
        }
        val effectiveStart = YearMonth.from(start).atDay(1)
        val effectiveEnd = if (YearMonth.from(end) == YearMonth.from(today)) today else end
        val filtered = all.asSequence().filterNot { it.tombstone }
            .filter { it.date in effectiveStart.toYmd()..effectiveEnd.toYmd() }
            .filter { RulesEngine.matches(it, conditions.first, conditions.second, context) }.toList()
        val total = filtered.sumOf { it.amountCents }.toDouble()
        return when (content?.optString("type", "sum")) {
            "avgPerTransact" -> ReportWidget(id, ReportWidgetKind.SUMMARY, name,
                valueCents = if (filtered.isEmpty()) 0 else (total / filtered.size).roundToLong())
            "avgPerMonth" -> {
                val completeMonths = ChronoUnit.MONTHS.between(YearMonth.from(effectiveStart), YearMonth.from(effectiveEnd)).toDouble()
                val months = completeMonths + effectiveEnd.dayOfMonth.toDouble() / effectiveEnd.lengthOfMonth()
                ReportWidget(id, ReportWidgetKind.SUMMARY, name, valueCents = if (months > 0) (total / months).roundToLong() else 0)
            }
            "avgPerYear" -> {
                val years = (ChronoUnit.DAYS.between(effectiveStart, effectiveEnd) + 1) / 365.25
                ReportWidget(id, ReportWidgetKind.SUMMARY, name, valueCents = if (years > 0) (total / years).roundToLong() else 0)
            }
            "percentage" -> {
                val divisorMeta = JSONObject().apply {
                    put("conditions", content.optJSONArray("divisorConditions") ?: JSONArray())
                    put("conditionsOp", content.optString("divisorConditionsOp", "and"))
                }
                val divisorConditions = parseConditions(divisorMeta)
                val pool = all.filterNot { it.tombstone }.filter {
                    content.optBoolean("divisorAllTimeDateRange", false) ||
                        it.date in effectiveStart.toYmd()..effectiveEnd.toYmd()
                }.filter { RulesEngine.matches(it, divisorConditions.first, divisorConditions.second, context) }
                val divisor = pool.sumOf { it.amountCents }.toDouble()
                ReportWidget(id, ReportWidgetKind.SUMMARY, name,
                    percentage = if (divisor == 0.0) 0.0 else ((total / divisor * 10_000).roundToLong() / 100.0))
            }
            else -> ReportWidget(id, ReportWidgetKind.SUMMARY, name, valueCents = total.roundToLong())
        }
    }

    private fun netWorth(
        id: String, name: String, meta: JSONObject?, transactions: List<ActualTransaction>,
        start: LocalDate, end: LocalDate,
    ): ReportWidget {
        val interval = meta?.optString("interval", "Monthly") ?: "Monthly"
        val boundaries = boundaries(start, end, interval)
        val points = boundaries.map { boundary ->
            ReportPoint(boundary.toString(), transactions.filter { it.date <= boundary.toYmd() }.sumOf { it.amountCents })
        }
        return ReportWidget(id, ReportWidgetKind.NET_WORTH, name,
            valueCents = points.lastOrNull()?.primaryCents ?: 0, points = points)
    }

    private fun cashFlow(
        id: String, name: String, transactions: List<ActualTransaction>, start: LocalDate, end: LocalDate,
    ): ReportWidget {
        val points = generateSequence(YearMonth.from(start)) { it.plusMonths(1) }
            .takeWhile { !it.isAfter(YearMonth.from(end)) }.map { month ->
                val rows = transactions.filter { YearMonth.from(it.localDate()) == month }
                ReportPoint(month.toString(), rows.filter { it.amountCents >= 0 }.sumOf { it.amountCents },
                    rows.filter { it.amountCents < 0 }.sumOf { -it.amountCents })
            }.toList()
        return ReportWidget(id, ReportWidgetKind.CASH_FLOW, name, points = points,
            valueCents = points.sumOf { it.primaryCents - it.secondaryCents })
    }

    private fun spending(
        id: String, name: String, meta: JSONObject?, transactions: List<ActualTransaction>,
        context: RuleContext, incomeCategoryIds: Set<String>,
        budgetedByCategory: (YearMonth) -> Map<String, Long>, today: LocalDate,
    ): ReportWidget {
        val scoped = transactions.filterNot { it.tombstone || it.transferAccountId != null ||
            it.accountId in context.offBudgetAccountIds || it.categoryId in incomeCategoryIds }
        val conditions = parseConditions(meta)
        val matching = scoped.filter { RulesEngine.matches(it, conditions.first, conditions.second, context) }
        val currentMonth = YearMonth.from(today)
        val isLive = meta?.optBoolean("isLive", true) ?: true
        val mode = meta?.optString("mode", "single-month") ?: "single-month"
        val storedCompare = month(meta?.optString("compare"))
        val storedCompareTo = month(meta?.optString("compareTo"))
        val compare = when {
            isLive && mode in setOf("budget", "average") -> storedCompare ?: currentMonth
            isLive && mode == "single-month" && storedCompare != null -> storedCompare
            isLive -> currentMonth
            else -> storedCompare ?: currentMonth
        }
        val compareTo = when {
            isLive && mode == "single-month" && storedCompare != null -> storedCompareTo ?: compare.minusMonths(1)
            isLive && mode !in setOf("budget", "average") -> minOf(storedCompareTo ?: currentMonth.minusMonths(1), currentMonth)
            else -> storedCompareTo ?: compare.minusMonths(1)
        }
        val todayDay = today.dayOfMonth
        val currentCutoff = todayDay.takeIf { compare == currentMonth }
        val comparisonCutoff = todayDay.takeIf { compare == currentMonth && todayDay < 28 }
        fun spent(month: YearMonth, throughDay: Int? = null): Long {
            val endDay = throughDay?.coerceAtMost(month.lengthOfMonth())
            return -matching.filter { transaction ->
                val date = transaction.localDate()
                YearMonth.from(date) == month && (endDay == null || date.dayOfMonth <= endDay)
            }.sumOf { it.amountCents }
        }
        val current = spent(compare, currentCutoff)
        val comparison = when (meta?.optString("mode", "single-month")) {
            "budget" -> {
                val categoryConditions = conditions.first.filter { it.field == "category" || it.field == "category_group" }
                val supported = categoryConditions.all {
                    it.op in setOf("is", "isNot", "oneOf", "notOneOf", "contains", "doesNotContain", "matches")
                }
                val budgets = budgetedByCategory(compare)
                val selected = if (categoryConditions.isEmpty() || !supported) budgets else budgets.filterKeys { categoryId ->
                    categoryMatches(categoryId, categoryConditions, conditions.second, context)
                }
                val full = selected.values.sum()
                if (comparisonCutoff == null) full else
                    (full.toDouble() / compare.lengthOfMonth() * comparisonCutoff).roundToLong()
            }
            "average" -> {
                val range = meta?.optJSONObject("averageRange")
                val months = when (range?.optString("mode")) {
                    "year-to-date" -> compare.monthValue - 1
                    "all-time" -> scoped.minOfOrNull { YearMonth.from(it.localDate()) }
                        ?.let { ChronoUnit.MONTHS.between(it, compare).toInt() } ?: 0
                    else -> range?.optInt("months", 3)?.takeIf { it in setOf(3, 6, 12) } ?: 3
                }
                if (months <= 0) 0 else (1..months).map {
                    spent(compare.minusMonths(it.toLong()), comparisonCutoff)
                }.average().roundToLong()
            }
            else -> spent(compareTo, comparisonCutoff)
        }
        return ReportWidget(id, ReportWidgetKind.SPENDING, name, valueCents = current, comparisonCents = comparison)
    }

    private fun parseConditions(meta: JSONObject?): Pair<List<Rule.Condition>, Rule.ConditionsOp> {
        val source = meta?.optJSONArray("conditions") ?: JSONArray()
        val filtered = JSONArray()
        for (index in 0 until source.length()) source.optJSONObject(index)?.takeIf {
            it.optString("customName").isBlank()
        }?.let { filtered.put(it) }
        return runCatching {
            val rule = Rule.parse("report", null, meta?.optString("conditionsOp"), filtered.toString(), "[]")
            rule.conditions to rule.conditionsOp
        }.getOrDefault(emptyList<Rule.Condition>() to Rule.ConditionsOp.AND)
    }

    internal fun timeFrame(meta: JSONObject?, today: LocalDate): Pair<LocalDate, LocalDate> {
        val current = YearMonth.from(today)
        if (meta == null) return current.atDay(1) to current.atEndOfMonth()
        fun date(value: String?, end: Boolean = false): LocalDate? = when (value?.length) {
            7 -> month(value)?.let { if (end) it.atEndOfMonth() else it.atDay(1) }
            10 -> runCatching { LocalDate.parse(value) }.getOrNull()
            else -> null
        }
        return when (meta.optString("mode")) {
            "yearToDate" -> today.withDayOfYear(1) to today
            "priorYearToDate" -> today.minusYears(1).withDayOfYear(1) to today.minusYears(1)
            "lastMonth" -> current.minusMonths(1).let { it.atDay(1) to it.atEndOfMonth() }
            "lastYear" -> LocalDate.of(today.year - 1, 1, 1) to LocalDate.of(today.year - 1, 12, 31)
            "static" -> (date(meta.optString("start")) ?: current.atDay(1)) to (date(meta.optString("end"), true) ?: today)
            "full" -> (date(meta.optString("start")) ?: LocalDate.of(1900, 1, 1)) to current.atEndOfMonth()
            "sliding-window" -> {
                val storedStart = month(meta.optString("start")); val storedEnd = month(meta.optString("end"))
                if (storedStart == null || storedEnd == null) current.atDay(1) to current.atEndOfMonth()
                else {
                    val shift = ChronoUnit.MONTHS.between(storedEnd, current)
                    storedStart.plusMonths(shift).atDay(1) to storedEnd.plusMonths(shift).atEndOfMonth()
                }
            }
            else -> current.atDay(1) to current.atEndOfMonth()
        }
    }

    private fun categoryMatches(
        categoryId: String,
        conditions: List<Rule.Condition>,
        op: Rule.ConditionsOp,
        context: RuleContext,
    ): Boolean {
        fun match(condition: Rule.Condition): Boolean {
            val actual = if (condition.field == "category") categoryId else context.categoryGroupIds[categoryId]
            val name = if (condition.field == "category") context.categoryNames[categoryId].orEmpty() else
                actual?.let(context.categoryGroupNames::get).orEmpty()
            val values = condition.value.list?.mapNotNull { it.text }
            val hit = values?.contains(actual) ?: (actual == condition.value.text)
            return when (condition.op) {
                "is", "oneOf" -> hit
                "isNot", "notOneOf" -> !hit
                "contains" -> condition.value.text?.let { name.contains(it, true) } == true
                "doesNotContain" -> condition.value.text?.let { !name.contains(it, true) } == true
                "matches" -> condition.value.text?.takeIf { it.length <= 256 }
                    ?.let { runCatching { Regex(it, RegexOption.IGNORE_CASE).containsMatchIn(name) }.getOrDefault(false) } == true
                else -> false
            }
        }
        return if (op == Rule.ConditionsOp.AND) conditions.all(::match) else conditions.any(::match)
    }

    private fun month(value: String?): YearMonth? {
        val text = value?.take(7) ?: return null
        return runCatching { YearMonth.parse(text) }.getOrNull()
    }

    private fun boundaries(start: LocalDate, end: LocalDate, interval: String): List<LocalDate> = when (interval) {
        "Daily" -> generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.toList()
        "Weekly" -> generateSequence(start) { it.plusWeeks(1) }.takeWhile { !it.isAfter(end) }
            .map { minOf(it.plusDays((6 - it.dayOfWeek.value % 7).toLong()), end) }.distinct().toList()
        "Yearly" -> (start.year..end.year).map { minOf(LocalDate.of(it, 12, 31), end) }
        else -> generateSequence(YearMonth.from(start)) { it.plusMonths(1) }.takeWhile { !it.isAfter(YearMonth.from(end)) }
            .map { minOf(it.atEndOfMonth(), end) }.toList()
    }

    private fun ActualTransaction.localDate(): LocalDate = LocalDate.of(date / 10000, date / 100 % 100, date % 100)
    private fun LocalDate.toYmd(): Int = year * 10000 + monthValue * 100 + dayOfMonth
    private fun label(type: String) = when (type) {
        "summary-card" -> "Summary"; "net-worth-card" -> "Net Worth"; "cash-flow-card" -> "Cash Flow"
        "spending-card" -> "Spending"; "markdown-card" -> "Notes"; "age-of-money-card" -> "Age of Money"
        "formula-card" -> "Formula"; "custom-report" -> "Custom Report"; "calendar-card" -> "Calendar"
        "crossover-card" -> "Crossover"; "budget-analysis-card" -> "Budget Analysis"; "sankey-card" -> "Sankey"
        "balance-forecast-card" -> "Balance Forecast"; "monte-carlo-card" -> "Monte Carlo"
        else -> type.ifBlank { "Unsupported report" }
    }
}
