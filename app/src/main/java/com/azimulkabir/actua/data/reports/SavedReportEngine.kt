package com.azimulkabir.actua.data.reports

import com.azimulkabir.actua.data.budget.model.ActualAccount
import com.azimulkabir.actua.data.budget.model.ActualCategoryGroup
import com.azimulkabir.actua.data.budget.model.ActualTransaction
import com.azimulkabir.actua.data.rules.Rule
import com.azimulkabir.actua.data.rules.RuleContext
import com.azimulkabir.actua.data.rules.RulesEngine
import com.azimulkabir.actua.model.ReportCategory
import com.azimulkabir.actua.model.ReportPoint
import com.azimulkabir.actua.model.ReportViewFilter
import com.azimulkabir.actua.model.ReportWidget
import com.azimulkabir.actua.model.ReportWidgetKind
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth

/** Read-only evaluation of Actual's saved custom reports using the shared [ReportAggregator]. */
object SavedReportEngine {
    fun compute(
        row: SavedReportRow,
        transactions: List<ActualTransaction>,
        accounts: List<ActualAccount>,
        groups: List<ActualCategoryGroup>,
        today: LocalDate = LocalDate.now(),
        view: ReportViewFilter = ReportViewFilter(),
    ): ReportWidget {
        val aggregator = ReportAggregator(accounts, groups)
        val (start, end) = dateRange(
            view.datePreset?.let { row.copy(dateStatic = false, dateRange = it) } ?: row, today,
        )
        val selected = row.selectedCategories?.let { runCatching { JSONArray(it) }.getOrNull() }?.let { array ->
            (0 until array.length()).mapNotNull { array.optJSONObject(it)?.optString("id")?.takeIf(String::isNotBlank) }
        }.orEmpty().toSet()
        val filter = ReportFilter(
            startDate = start.toYmd(), endDate = end.toYmd(),
            accountIds = view.accountIds.takeIf { it.isNotEmpty() },
            categoryIds = selected.takeIf { it.isNotEmpty() },
            showOffBudget = row.showOffBudget, showHiddenCategories = row.showHidden,
            showUncategorized = row.showUncategorized,
            balanceType = balanceType(row.balanceType),
        )
        val conditions = parseConditions(row)
        val context = RuleContext(
            offBudgetAccountIds = accounts.filter { it.offBudget }.mapTo(mutableSetOf()) { it.id },
            accountNames = accounts.associate { it.id to it.name },
            categoryNames = groups.flatMap { it.categories }.associate { it.id to it.name },
            categoryGroupIds = groups.flatMap { it.categories }.associate { it.id to it.groupId },
            categoryGroupNames = groups.associate { it.id to it.name },
            payeeNames = transactions.mapNotNull { tx -> tx.payeeId?.let { it to tx.payeeName.orEmpty() } }.toMap(),
        )
        val scoped = if (conditions.first.isEmpty()) transactions else transactions.filter {
            RulesEngine.matches(it, conditions.first, conditions.second, context)
        }
        val included = aggregator.select(scoped, filter)
        val grouping = when (row.groupBy) {
            "Group" -> ReportGrouping.CATEGORY_GROUP
            "Payee" -> ReportGrouping.PAYEE
            "Account" -> ReportGrouping.ACCOUNT
            else -> ReportGrouping.CATEGORY
        }
        val segments = if (row.groupBy == "Interval") emptyList() else
            aggregator.groupTotals(scoped, filter, grouping).map { ReportCategory(it.name, it.totalCents, it.transactionIds) }
        val points = included.groupBy { YearMonth.of(it.date / 10000, it.date / 100 % 100) }.toSortedMap()
            .map { (month, rows) -> ReportPoint(month.toString(), rows.sumOf { it.amountCents }) }
        return ReportWidget(
            id = "saved:${row.id}", kind = ReportWidgetKind.CUSTOM_REPORT, name = row.name.ifBlank { "Untitled report" },
            valueCents = included.sumOf { it.amountCents }, categories = segments, points = points,
            graphType = row.graphType, subtitle = "$start – $end · ${row.groupBy}",
        )
    }

    internal fun balanceType(value: String): ReportBalanceType = when (value) {
        "Deposit", "Income", "totalAssets" -> ReportBalanceType.ASSETS
        "Net", "netAssets" -> ReportBalanceType.NET_ASSETS
        "netDebts" -> ReportBalanceType.NET_DEBTS
        else -> ReportBalanceType.DEBTS
    }

    internal fun dateRange(row: SavedReportRow, today: LocalDate): Pair<LocalDate, LocalDate> {
        fun parse(v: String?, end: Boolean): LocalDate? = when (v?.length) {
            7 -> runCatching { YearMonth.parse(v) }.getOrNull()?.let { if (end) it.atEndOfMonth() else it.atDay(1) }
            10 -> runCatching { LocalDate.parse(v) }.getOrNull()
            else -> null
        }
        val month = YearMonth.from(today)
        if (!row.dateStatic) when (row.dateRange) {
            "This week" -> today.minusDays((today.dayOfWeek.value % 7).toLong()).let { it to it.plusDays(6) }
            "Last week" -> today.minusDays((today.dayOfWeek.value % 7 + 7).toLong()).let { it to it.plusDays(6) }
            "This month" -> return month.atDay(1) to month.atEndOfMonth()
            "Last month" -> return month.minusMonths(1).let { it.atDay(1) to it.atEndOfMonth() }
            "Last 3 months" -> return month.minusMonths(3).atDay(1) to month.minusMonths(1).atEndOfMonth()
            "Last 6 months" -> return month.minusMonths(6).atDay(1) to month.minusMonths(1).atEndOfMonth()
            "Last 12 months" -> return month.minusMonths(12).atDay(1) to month.minusMonths(1).atEndOfMonth()
            "Year to date" -> return today.withDayOfYear(1) to today
            "Last year" -> return LocalDate.of(today.year - 1, 1, 1) to LocalDate.of(today.year - 1, 12, 31)
            "Prior year to date" -> return today.minusYears(1).withDayOfYear(1) to today.minusYears(1)
            "All time" -> return LocalDate.of(1900, 1, 1) to today
        }
        return (parse(row.startDate, false) ?: month.atDay(1)) to (parse(row.endDate, true) ?: month.atEndOfMonth())
    }

    private fun parseConditions(row: SavedReportRow): Pair<List<Rule.Condition>, Rule.ConditionsOp> {
        val raw = row.conditions?.takeIf(String::isNotBlank) ?: return emptyList<Rule.Condition>() to Rule.ConditionsOp.AND
        return runCatching {
            val array = JSONArray(raw)
            val kept = JSONArray()
            for (i in 0 until array.length()) array.optJSONObject(i)?.takeIf { it.optString("customName").isBlank() }
                ?.let(kept::put)
            val rule = Rule.parse("report", null, row.conditionsOp, kept.toString(), "[]")
            rule.conditions to rule.conditionsOp
        }.getOrDefault(emptyList<Rule.Condition>() to Rule.ConditionsOp.AND)
    }

    private fun LocalDate.toYmd() = year * 10000 + monthValue * 100 + dayOfMonth
}
