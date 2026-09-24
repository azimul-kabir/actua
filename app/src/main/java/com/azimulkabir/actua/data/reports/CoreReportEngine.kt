package com.azimulkabir.actua.data.reports

import com.azimulkabir.actua.data.budget.model.ActualAccount
import com.azimulkabir.actua.data.budget.model.ActualBudgetMonth
import com.azimulkabir.actua.data.budget.model.ActualCategoryBudget
import com.azimulkabir.actua.data.budget.model.ActualCategoryGroup
import com.azimulkabir.actua.data.budget.model.ActualTransaction
import com.azimulkabir.actua.data.rules.Rule
import com.azimulkabir.actua.data.rules.RuleContext
import com.azimulkabir.actua.data.rules.RulesEngine
import com.azimulkabir.actua.data.schedules.ActualScheduleSummary
import com.azimulkabir.actua.data.schedules.DayDate
import com.azimulkabir.actua.data.schedules.ScheduleDateCondition
import com.azimulkabir.actua.data.schedules.ScheduleRecurrence
import com.azimulkabir.actua.data.schedules.ScheduleStatusCalculator
import com.azimulkabir.actua.model.ReportDashboardPage
import com.azimulkabir.actua.model.ReportPoint
import com.azimulkabir.actua.model.ReportWidget
import com.azimulkabir.actua.model.ReportWidgetKind
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.roundToLong
import kotlin.math.sqrt

/** Core Actual dashboard widgets, ported from Actuali's report engines. */
object CoreReportEngine {
    fun dashboards(
        pages: List<DashboardPageRow>,
        widgets: (String?) -> List<DashboardWidgetRow>,
        transactions: List<ActualTransaction>,
        accounts: List<ActualAccount>,
        groups: List<ActualCategoryGroup>,
        savedReports: List<SavedReportRow> = emptyList(),
        schedules: List<ActualScheduleSummary> = emptyList(),
        budgetedByCategory: (YearMonth) -> Map<String, Long> = { emptyMap() },
        budgetMonth: (YearMonth) -> ActualBudgetMonth? = { null },
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
            hiddenCategoryIds = groups.flatMap { it.categories }.filter { it.hidden }.mapTo(mutableSetOf()) { it.id },
        )
        val incomeCategories = groups.flatMap { it.categories }.filter { it.isIncome }.mapTo(mutableSetOf()) { it.id }
        val savedReportsById = savedReports.associateBy { it.id }
        val savedShared = if (savedReports.isEmpty()) null else
            SavedReportEngine.Shared(transactions, accounts, groups)
        return resolvedPages.map { page ->
            ReportDashboardPage(
                page.id,
                page.name.ifBlank { "Untitled" },
                widgets(page.id.ifBlank { null }).map { row ->
                    val savedReport = if (row.type == "custom-report") {
                        row.metaJson?.let { runCatching { JSONObject(it) }.getOrNull() }
                            ?.optString("id")?.takeIf(String::isNotBlank)?.let(savedReportsById::get)
                    } else null
                    if (savedReport != null && savedShared != null) {
                        SavedReportEngine.compute(savedReport, transactions, accounts, groups, today,
                            shared = savedShared, budgetMonth = budgetMonth)
                            .copy(id = row.id)
                    } else {
                        compute(
                            row, transactions, context, incomeCategories, budgetedByCategory, today,
                            accounts.associate { it.id to it.balanceCents }, schedules, budgetMonth,
                        )
                    }
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
        accountBalances: Map<String, Long> = emptyMap(),
        schedules: List<ActualScheduleSummary> = emptyList(),
        budgetMonth: (YearMonth) -> ActualBudgetMonth? = { null },
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
            "cash-flow-card" -> cashFlow(row.id, name, filtered.filter { it.transferAccountId == null &&
                it.accountId !in context.offBudgetAccountIds && it.date <= minOf(end, today).toYmd() }, start, end)
            "spending-card" -> spending(row.id, name, meta, transactions, context, incomeCategoryIds,
                budgetedByCategory, today)
            "markdown-card" -> ReportWidget(row.id, ReportWidgetKind.MARKDOWN, name,
                markdown = meta?.optString("content").orEmpty())
            "age-of-money-card" -> ageOfMoney(
                row.id, name,
                transactions.filterNot { it.tombstone }
                    .filter { RulesEngine.matches(it, conditions.first, conditions.second, context) },
                context, conditions, start, minOf(end, today),
            )
            "formula-card" -> formula(row.id, name, meta, transactions, context, today)
            "custom-report" -> customReport(row.id, name, filtered, context, incomeCategoryIds)
            "calendar-card" -> {
                val monthStart = start.withDayOfMonth(1)
                val monthEnd = end.withDayOfMonth(end.lengthOfMonth())
                val calendarFiltered = transactions.asSequence()
                    .filterNot { it.tombstone }
                    .filter { it.date in monthStart.toYmd()..monthEnd.toYmd() }
                    .filter { RulesEngine.matches(it, conditions.first, conditions.second, context) }
                    .toList()
                calendar(row.id, name, calendarFiltered)
            }
            "crossover-card" -> crossover(row.id, name, meta, transactions, context, incomeCategoryIds,
                accountBalances, today)
            "budget-analysis-card" -> budgetAnalysis(row.id, name, meta, context, budgetMonth, start, end)
            "sankey-card" -> sankey(row.id, name, filtered, context, incomeCategoryIds, start, end)
            "balance-forecast-card" -> balanceForecast(row.id, name, meta, transactions, accountBalances, today, schedules, context)
            "monte-carlo-card" -> monteCarlo(row.id, name, meta, accountBalances, today)
            else -> ReportWidget(row.id, ReportWidgetKind.UNSUPPORTED, name, sourceType = row.type)
        }
    }

    /**
     * FIFO port of Actuali's AgeOfMoneyEngine. Values in [ReportPoint] are days, not cents.
     *
     * Mirrors the PWA's `buildTransferInclusionFilter` (age-of-money-spreadsheet.ts): by default
     * (no `account` condition on the widget), a transfer is excluded from the pool unless its
     * counterpart account is off-budget. When the widget's conditions include an `account` filter,
     * a transfer whose counterpart account falls outside that filtered set is also treated as real
     * money entering/leaving the pool, even if the counterpart is itself on-budget (e.g. a filtered
     * checking account paying off an on-budget credit card).
     */
    private fun ageOfMoney(
        id: String, name: String, scoped: List<ActualTransaction>, context: RuleContext,
        conditions: Pair<List<Rule.Condition>, Rule.ConditionsOp>, start: LocalDate, end: LocalDate,
    ): ReportWidget {
        data class Bucket(val date: LocalDate, var remaining: Long)
        val accountConditions = conditions.first.filter { it.field == "account" }
        val endYmd = end.toYmd()
        // Mirrors upstream's income/expense queries, both capped at `end` (already minOf(rawEnd,
        // today)): a transaction dated after the window must never enter the FIFO pool at all, not
        // just be excluded from the *displayed* ages afterward - otherwise a post-dated transaction
        // can still consume/produce buckets and skew every age computed from it.
        val pool = scoped.filter { it.date <= endYmd && it.accountId !in context.offBudgetAccountIds }
            .filter { transaction ->
                val transferAccountId = transaction.transferAccountId
                transferAccountId == null || transferAccountId in context.offBudgetAccountIds ||
                    (accountConditions.isNotEmpty() && !RulesEngine.matches(
                        transaction.copy(accountId = transferAccountId), accountConditions, conditions.second, context,
                    ))
            }
            .sortedWith(compareBy<ActualTransaction> { it.date }.thenBy { it.id })
        val buckets = pool.filter { it.amountCents > 0 }.map { Bucket(it.localDate(), it.amountCents) }
        var bucketIndex = 0
        val ages = mutableListOf<Pair<LocalDate, Int>>()
        pool.filter { it.amountCents < 0 }.forEach { expense ->
            var remaining = -expense.amountCents
            var lastDate: LocalDate? = null
            while (remaining > 0 && bucketIndex < buckets.size) {
                val bucket = buckets[bucketIndex]
                val used = minOf(bucket.remaining, remaining)
                bucket.remaining -= used; remaining -= used
                if (used > 0) lastDate = bucket.date
                if (bucket.remaining <= 0) bucketIndex++
            }
            lastDate?.let {
                ages += expense.localDate() to
                    ChronoUnit.DAYS.between(it, expense.localDate()).toInt().coerceAtLeast(0)
            }
        }
        val displayed = ages.filter { it.first >= YearMonth.from(start).atDay(1) && !it.first.isAfter(end) }
        val points = generateSequence(YearMonth.from(start)) { it.plusMonths(1) }
            .takeWhile { !it.isAfter(YearMonth.from(end)) }.map { month ->
                val through = displayed.filter { !YearMonth.from(it.first).isAfter(month) }.takeLast(10)
                ReportPoint(month.toString(), through.map { it.second }.average().takeUnless { it.isNaN() }?.roundToLong() ?: 0)
            }.toList()
        val current = displayed.takeLast(10).map { it.second }.average().takeUnless { it.isNaN() }?.roundToLong()
        return ReportWidget(id, ReportWidgetKind.AGE_OF_MONEY, name, valueCents = current, points = points)
    }

    private fun formula(
        id: String, name: String, meta: JSONObject?, all: List<ActualTransaction>,
        context: RuleContext, today: LocalDate,
    ): ReportWidget {
        var expression = meta?.optString("formula").orEmpty().removePrefix("=")
        val queries = meta?.optJSONObject("queries")
        Regex("query\\(\\s*[\\\"']([^\\\"']+)[\\\"']\\s*\\)", RegexOption.IGNORE_CASE)
            .findAll(expression).toList().asReversed().forEach { match ->
                val query = queries?.optJSONObject(match.groupValues[1])
                // Unlike a widget's own timeFrame, a sub-query with no explicit timeFrame mode
                // means "no date restriction" (all-time), matching upstream/Actuali; an unknown
                // query name (query == null) evaluates to 0 rather than matching everything.
                val timeFrameMeta = query?.optJSONObject("timeFrame")?.takeIf { it.has("mode") }
                val range = timeFrameMeta?.let { timeFrame(it, today) }
                val conditions = parseConditions(query)
                val cents = if (query == null) 0L else all.filterNot { it.tombstone }
                    .filter { range == null || it.date in range.first.toYmd()..range.second.toYmd() }
                    .filter { RulesEngine.matches(it, conditions.first, conditions.second, context) }.sumOf { it.amountCents }
                expression = expression.replaceRange(match.range, (cents / 100.0).toString())
            }
        val value = ArithmeticParser(expression).parse()?.times(100)?.roundToLong()
        return ReportWidget(id, ReportWidgetKind.FORMULA, name, valueCents = value,
            markdown = if (value == null) "This formula uses functions Actua cannot evaluate." else null)
    }

    private fun customReport(
        id: String, name: String, transactions: List<ActualTransaction>, context: RuleContext,
        incomeCategoryIds: Set<String>,
    ): ReportWidget {
        val expenses = transactions.filter { it.amountCents < 0 && it.transferAccountId == null &&
            it.accountId !in context.offBudgetAccountIds && it.categoryId !in incomeCategoryIds }
        val categories = expenses.groupBy {
            it.categoryId?.let(context.categoryNames::get).orEmpty().ifBlank { "Uncategorized" }
        }
            .map { (label, rows) -> com.azimulkabir.actua.model.ReportCategory(label, -rows.sumOf { it.amountCents }) }
            .sortedByDescending { it.spentCents }
        val points = expenses.groupBy { YearMonth.from(it.localDate()) }.toSortedMap().map { (month, rows) ->
            ReportPoint(month.toString(), -rows.sumOf { it.amountCents })
        }
        return ReportWidget(id, ReportWidgetKind.CUSTOM_REPORT, name,
            valueCents = categories.sumOf { it.spentCents }, categories = categories, points = points)
    }

    private fun calendar(id: String, name: String, transactions: List<ActualTransaction>): ReportWidget {
        val points = transactions.groupBy { it.localDate() }.toSortedMap().map { (date, rows) ->
            ReportPoint(date.toString(), rows.filter { it.amountCents > 0 }.sumOf { it.amountCents },
                -rows.filter { it.amountCents < 0 }.sumOf { it.amountCents }, rows.map { it.id })
        }
        return ReportWidget(id, ReportWidgetKind.CALENDAR, name,
            valueCents = points.sumOf { it.primaryCents }, comparisonCents = points.sumOf { it.secondaryCents }, points = points)
    }

    /**
     * Ported from Actual's `crossover-spreadsheet.ts` (`recalculate`): the historical window builds
     * per-selected-account monthly balances (starting balance = running total through the end of the
     * first month, then walked forward by each month's posted deltas) to derive the default annual
     * return as the CAGR between the first and last historical balance, and to chart real
     * income-vs-expenses per historical month. The projection seeds from the *last historical
     * balance* (not the account's live balance), grows monthly by the explicit `estimatedReturn` (if
     * set) or that CAGR, and projects expenses with the widget's `projectionType`
     * (`hampel`/`median`/`mean`) over the zero-filled monthly expense series, not a plain average.
     */
    private fun crossover(
        id: String, name: String, meta: JSONObject?, all: List<ActualTransaction>, context: RuleContext,
        incomeCategoryIds: Set<String>, accountBalances: Map<String, Long>, today: LocalDate,
    ): ReportWidget {
        val live = all.filterNot { it.tombstone }
        val selectedAccounts = meta?.optJSONArray("incomeAccountIds")?.strings()?.toSet().orEmpty()
            .ifEmpty { accountBalances.keys }
        // Mirrors upstream's `expenseCategoryIds` memo: an explicit (even empty) list is honored
        // as-is, but when the widget hasn't set one, the default is every non-income category,
        // excluding hidden ones unless `showHiddenCategories` is set - not "match everything".
        val explicitExpenseCategories = meta?.optJSONArray("expenseCategoryIds")?.strings()?.toSet()
        val showHiddenCategories = meta?.optBoolean("showHiddenCategories", false) ?: false

        val previousMonth = YearMonth.from(today).minusMonths(1)
        val earliestMonth = live.minOfOrNull { YearMonth.from(it.localDate()) } ?: previousMonth
        val timeFrameMeta = meta?.optJSONObject("timeFrame")
        fun storedMonth(key: String) = timeFrameMeta?.optString(key)?.let(::month)
        val start: YearMonth
        val end: YearMonth
        when (timeFrameMeta?.optString("mode") ?: "full") {
            "sliding-window" -> {
                start = (storedMonth("start") ?: earliestMonth).minusMonths(1).coerceIn(earliestMonth, previousMonth)
                end = (storedMonth("end") ?: previousMonth).minusMonths(1).coerceIn(earliestMonth, previousMonth)
            }
            "full" -> { start = earliestMonth; end = previousMonth }
            else -> {
                start = (storedMonth("start") ?: earliestMonth).coerceIn(earliestMonth, previousMonth)
                end = (storedMonth("end") ?: previousMonth).coerceIn(earliestMonth, previousMonth)
            }
        }
        val rangeEnd = if (end.isBefore(start)) start else end
        val months = generateSequence(start) { it.plusMonths(1) }.takeWhile { !it.isAfter(rangeEnd) }.toList()
        val startYmd = start.atDay(1).toYmd()
        val startEndYmd = start.atEndOfMonth().toYmd()
        val rangeEndYmd = rangeEnd.atEndOfMonth().toYmd()

        // Total balance across selected accounts per historical month, seeded from the running
        // balance through the end of the start month and walked forward by later months' deltas.
        val historicalBalances = LongArray(months.size)
        selectedAccounts.forEach { accountId ->
            val accountTx = live.filter { it.accountId == accountId }
            var running = accountTx.filter { it.date <= startEndYmd }.sumOf { it.amountCents }
            val deltasByMonth = accountTx.filter { it.date in startYmd..rangeEndYmd }
                .filter { YearMonth.from(it.localDate()) != start }
                .groupBy { YearMonth.from(it.localDate()) }
                .mapValues { (_, rows) -> rows.sumOf { it.amountCents } }
            months.forEachIndexed { i, m ->
                running += deltasByMonth[m] ?: 0L
                historicalBalances[i] += running
            }
        }

        val expenseByMonth = live.asSequence()
            .filter { it.amountCents < 0 && it.transferAccountId == null &&
                it.accountId !in context.offBudgetAccountIds && it.categoryId !in incomeCategoryIds &&
                it.date in startYmd..rangeEndYmd &&
                if (explicitExpenseCategories != null) it.categoryId?.let(explicitExpenseCategories::contains) == true
                else showHiddenCategories || it.categoryId !in context.hiddenCategoryIds }
            .groupBy { YearMonth.from(it.localDate()) }
            .mapValues { (_, rows) -> -rows.sumOf { it.amountCents } }

        val swr = meta?.optDouble("safeWithdrawalRate", 0.04)?.takeIf { it > 0 } ?: 0.04
        val monthlySwr = swr / 12.0

        val points = mutableListOf<ReportPoint>()
        months.forEachIndexed { i, m ->
            points += ReportPoint(m.toString(), (historicalBalances[i] * monthlySwr).roundToLong(), expenseByMonth[m] ?: 0L)
        }

        // Default (historical) monthly return: CAGR between the first and last non-zero historical balance.
        var defaultMonthlyReturn: Double? = null
        if (historicalBalances.size >= 2) {
            var startingBalance = historicalBalances[0].toDouble()
            val finalBalance = historicalBalances.last().toDouble()
            val n = historicalBalances.size - 1
            if (startingBalance == 0.0) {
                for (i in 1 until historicalBalances.size) {
                    if (historicalBalances[i] != 0L) { startingBalance = historicalBalances[i].toDouble(); break }
                }
            }
            defaultMonthlyReturn = if (startingBalance > 0 && finalBalance > 0 && n > 0) {
                Math.pow(finalBalance / startingBalance, 1.0 / n).minus(1).takeIf { it.isFinite() } ?: 0.0
            } else 0.0
        }
        val explicitAnnualReturn = meta?.optDouble("estimatedReturn", Double.NaN)?.takeIf { it.isFinite() }
        val monthlyReturn = explicitAnnualReturn?.let { Math.pow(1 + it, 1.0 / 12) - 1 } ?: defaultMonthlyReturn
        val contribution = meta?.optDouble("expectedContribution", 0.0)?.roundToLong() ?: 0L
        val adjustmentFactor = meta?.optDouble("expenseAdjustmentFactor", 1.0)?.takeIf { it.isFinite() } ?: 1.0

        val expenseSeries = months.map { (expenseByMonth[it] ?: 0L).toDouble() }
        val flatExpense = when (meta?.optString("projectionType", "hampel") ?: "hampel") {
            "median" -> median(expenseSeries)
            "mean" -> mean(expenseSeries)
            else -> hampelFilteredMedian(expenseSeries)
        }
        val adjustedExpenses = (maxOf(0.0, flatExpense) * adjustmentFactor).roundToLong()

        var projectedBalance = historicalBalances.lastOrNull() ?: 0L
        var monthCursor = rangeEnd
        var crossoverIteration: Int? = null
        var crossoverMonth: YearMonth? = null
        for (i in 1..600) {
            monthCursor = monthCursor.plusMonths(1)
            projectedBalance += contribution
            monthlyReturn?.let { projectedBalance = (projectedBalance * (1 + it)).roundToLong() }
            val projectedIncome = (projectedBalance * monthlySwr).roundToLong()
            val reached = projectedIncome >= adjustedExpenses
            if (i % 12 == 0 || (crossoverIteration == null && reached)) {
                points += ReportPoint(monthCursor.toString(), projectedIncome, adjustedExpenses)
            }
            if (crossoverIteration == null && reached) { crossoverIteration = i; crossoverMonth = monthCursor }
            if (crossoverIteration != null && i > crossoverIteration + 12) break
        }
        val monthsToRetire = crossoverMonth?.let { ChronoUnit.MONTHS.between(YearMonth.from(today), it) }?.coerceAtLeast(0)
        return ReportWidget(id, ReportWidgetKind.CROSSOVER, name, valueCents = monthsToRetire,
            comparisonCents = adjustedExpenses, points = points)
    }

    private fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        if (values.size == 1) return values[0]
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2 else sorted[mid]
    }

    private fun mean(values: List<Double>): Double = if (values.isEmpty()) 0.0 else values.sum() / values.size

    private fun hampelFilteredMedian(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        if (values.size == 1) return values[0]
        val med = median(values)
        val mad = median(values.map { kotlin.math.abs(it - med) })
        val threshold = 3.0
        val lower = med - 1.4826 * mad * threshold
        val upper = med + 1.4826 * mad * threshold
        val filtered = values.filter { it in lower..upper }
        return median(filtered)
    }

    /**
     * Ported from Actual's `budget-analysis-spreadsheet.ts`: reads each month's budget-engine cells
     * (already computed via [ActualBudgetDatabase.fetchBudgetMonth]'s own carry-forward walk, which
     * matches upstream `summarizeMonthCategories`/`getNextRunningBalance` - positive leftover always
     * rolls over, negative leftover only when the category has rollover overspending enabled),
     * scoped to the widget's own category/category-group conditions and `showHiddenCategories` meta,
     * instead of a naive unfiltered transaction sum.
     */
    private fun budgetAnalysis(
        id: String, name: String, meta: JSONObject?, context: RuleContext,
        budgetMonth: (YearMonth) -> ActualBudgetMonth?, start: LocalDate, end: LocalDate,
    ): ReportWidget {
        val conditions = parseConditions(meta)
        val categoryConditions = conditions.first.filter { it.field == "category" || it.field == "category_group" }
        val supported = categoryConditions.all {
            it.op in setOf("is", "isNot", "oneOf", "notOneOf", "contains", "doesNotContain", "matches")
        }
        val showHidden = meta?.optBoolean("showHiddenCategories", false) ?: false
        fun selected(month: YearMonth): List<ActualCategoryBudget> {
            val budget = budgetMonth(month) ?: return emptyList()
            val pool = if (showHidden) budget.categories + budget.hiddenCategories else budget.categories
            return if (categoryConditions.isEmpty() || !supported) pool else pool.filter {
                categoryMatches(it.categoryId, categoryConditions, conditions.second, context)
            }
        }
        val points = generateSequence(YearMonth.from(start)) { it.plusMonths(1) }
            .takeWhile { !it.isAfter(YearMonth.from(end)) }.map { month ->
                val categories = selected(month)
                ReportPoint(
                    month.toString(),
                    categories.sumOf { it.budgetedCents },
                    categories.sumOf { -it.spentCents },
                    tertiaryCents = categories.sumOf { it.availableCents },
                )
            }.toList()
        return ReportWidget(id, ReportWidgetKind.BUDGET_ANALYSIS, name, points = points,
            valueCents = points.sumOf { it.primaryCents }, comparisonCents = points.sumOf { it.secondaryCents },
            balanceCents = points.lastOrNull()?.tertiaryCents)
    }

    private fun sankey(
        id: String, name: String, transactions: List<ActualTransaction>, context: RuleContext,
        incomeCategoryIds: Set<String>, start: LocalDate, end: LocalDate,
    ): ReportWidget {
        fun groupLabel(transaction: ActualTransaction) =
            transaction.categoryId?.let(context.categoryGroupIds::get)?.let(context.categoryGroupNames::get)
                .orEmpty().ifBlank { "Other" }
        // Income is broken down per source category/payee (matching Actual's PWA), not per category
        // group like expenses: budgets typically keep every income source in a single "Income" group,
        // so grouping by group name would collapse them all into one bar.
        fun incomeLabel(transaction: ActualTransaction) =
            transaction.categoryId?.let(context.categoryNames::get)?.takeIf(String::isNotBlank)
                ?: transaction.payeeName?.takeIf(String::isNotBlank) ?: "Other"
        val incomeRows = transactions.filter { it.amountCents > 0 && it.transferAccountId == null &&
            it.accountId !in context.offBudgetAccountIds }
        val income = incomeRows.sumOf { it.amountCents }
        val incomeCategories = incomeRows.groupBy(::incomeLabel)
            .map { (label, rows) -> com.azimulkabir.actua.model.ReportCategory(label, rows.sumOf { it.amountCents }) }
            .sortedByDescending { it.spentCents }
        val categories = transactions.filter { it.amountCents < 0 && it.transferAccountId == null &&
            it.accountId !in context.offBudgetAccountIds && it.categoryId !in incomeCategoryIds }
            .groupBy(::groupLabel)
            .map { (label, rows) -> com.azimulkabir.actua.model.ReportCategory(label, -rows.sumOf { it.amountCents }) }
            .sortedByDescending { it.spentCents }
        val formatter = java.time.format.DateTimeFormatter.ofPattern("MMM yyyy", java.util.Locale.ENGLISH)
        val subtitle = if (YearMonth.from(start) == YearMonth.from(end)) start.format(formatter)
            else "${start.format(formatter)} - ${end.format(formatter)}"
        return ReportWidget(id, ReportWidgetKind.SANKEY, name, valueCents = income,
            comparisonCents = categories.sumOf { it.spentCents }, categories = categories,
            incomeCategories = incomeCategories, subtitle = subtitle)
    }

    /**
     * Ported from Actual's `forecast/generate` (loot-core `server/forecast/forecast-projection.ts`):
     * a starting balance summed from every posted transaction before the window, walked forward
     * day by day by posted transactions plus projected schedule occurrences, sampled once per
     * month for the chart. `valueCents` is the ending balance, `comparisonCents` the lowest daily
     * combined balance across the window ("Low"), and `subtitle` the scheduled-transactions note.
     */
    private fun balanceForecast(
        id: String, name: String, meta: JSONObject?, all: List<ActualTransaction>,
        balances: Map<String, Long>, today: LocalDate,
        schedules: List<ActualScheduleSummary>, context: RuleContext,
    ): ReportWidget {
        val selected = meta?.optJSONArray("accounts")?.strings()?.toSet().orEmpty().ifEmpty { balances.keys }
        val conditions = parseConditions(meta)
        val (rangeStart, rangeEnd) = meta?.optJSONObject("timeFrame")?.let { timeFrame(it, today) }
            ?: YearMonth.from(today).let { it.atDay(1) to it.plusMonths(11).atEndOfMonth() }
        val firstForecastDate = if (rangeEnd.isBefore(today)) rangeStart else maxOf(rangeStart, today)
        val startYmd = rangeStart.toYmd(); val endYmd = rangeEnd.toYmd(); val firstForecastYmd = firstForecastDate.toYmd()

        val relevant = all.asSequence().filterNot { it.tombstone }
            .filter { it.accountId in selected }
            .filter { RulesEngine.matches(it, conditions.first, conditions.second, context) }
            .toList()
        val startingBalance = relevant.filter { it.date < startYmd }.sumOf { it.amountCents }
        val postedByDate = relevant.filter { it.date in startYmd..endYmd }
            .groupBy { it.date }.mapValues { (_, rows) -> rows.sumOf { it.amountCents } }
        val postedDatesByScheduleId = relevant.mapNotNull { tx -> tx.scheduleId?.let { it to tx.date } }
            .groupBy({ it.first }, { it.second })

        // Mirrors upstream's `isScheduleOccurrencePosted`: an occurrence that already has a matching
        // posted transaction (e.g. a bill paid earlier this cycle) must not also be projected as a
        // synthetic schedule delta, or the running balance double-counts it.
        fun isOccurrencePosted(schedule: ActualScheduleSummary, day: DayDate): Boolean {
            val postedDates = postedDatesByScheduleId[schedule.id] ?: return false
            val matchStartYmd = ScheduleStatusCalculator.occurrenceMatchStartDate(
                day, schedule.dateOp, schedule.postsTransaction,
            ).yyyymmdd
            return postedDates.any { it in matchStartYmd..day.yyyymmdd }
        }

        val scheduleDeltasByDate = mutableMapOf<Int, Long>()
        val scheduleCountByDate = mutableMapOf<Int, MutableSet<String>>()
        schedules.forEach schedule@{ schedule ->
            if (schedule.completed) return@schedule
            val accountId = schedule.accountId ?: return@schedule
            if (accountId !in selected) return@schedule
            val amount = schedule.postAmount
            val occurrenceDays = when (val condition = schedule.dateCondition) {
                is ScheduleDateCondition.Fixed -> listOf(condition.day)
                is ScheduleDateCondition.Recurring -> schedule.nextDate?.let { nextDate ->
                    ScheduleRecurrence.upcomingDates(condition.config, 400, nextDate)
                }.orEmpty()
                ScheduleDateCondition.Unsupported, null -> emptyList()
            }
            occurrenceDays.forEach { day ->
                val ymd = day.yyyymmdd
                if (ymd < firstForecastYmd || ymd > endYmd) return@forEach
                if (isOccurrencePosted(schedule, day)) return@forEach
                val synthetic = ActualTransaction(
                    "schedule-${schedule.id}-$ymd", accountId, ymd, amount, schedule.payeeId, null,
                    schedule.categoryId, null, null, false, false, null, false, null, false, null, null, null, null,
                )
                if (!RulesEngine.matches(synthetic, conditions.first, conditions.second, context)) return@forEach
                scheduleDeltasByDate[ymd] = (scheduleDeltasByDate[ymd] ?: 0L) + amount
                scheduleCountByDate.getOrPut(ymd, ::mutableSetOf).add(schedule.id)
            }
        }

        var runningBalance = startingBalance
        var lowestBalance = Long.MAX_VALUE
        var scheduledOccurrenceCount = 0
        val points = mutableListOf<ReportPoint>()
        var day = rangeStart
        while (!day.isAfter(rangeEnd)) {
            val ymd = day.toYmd()
            runningBalance += (postedByDate[ymd] ?: 0L) + (scheduleDeltasByDate[ymd] ?: 0L)
            scheduledOccurrenceCount += scheduleCountByDate[ymd]?.size ?: 0
            if (runningBalance < lowestBalance) lowestBalance = runningBalance
            if (day == YearMonth.from(day).atEndOfMonth() || day == rangeEnd) {
                points += ReportPoint(YearMonth.from(day).toString(), runningBalance)
            }
            day = day.plusDays(1)
        }
        if (points.isEmpty()) points += ReportPoint(YearMonth.from(rangeStart).toString(), runningBalance)
        if (lowestBalance == Long.MAX_VALUE) lowestBalance = runningBalance

        val hasFilters = conditions.first.isNotEmpty()
        val subtitle = when {
            scheduledOccurrenceCount == 0 && hasFilters -> "Filtered running total only; no scheduled occurrences in this range"
            scheduledOccurrenceCount == 0 -> "No scheduled transactions in this range"
            hasFilters -> "$scheduledOccurrenceCount scheduled transactions included (filtered running total)"
            else -> "$scheduledOccurrenceCount scheduled transactions included"
        }
        return ReportWidget(
            id, ReportWidgetKind.BALANCE_FORECAST, name,
            valueCents = points.last().primaryCents, comparisonCents = lowestBalance,
            points = points, subtitle = subtitle,
        )
    }

    /**
     * Ported from Actual's `runMonteCarloSimulation` (desktop-client
     * `reports/reports/monte-carlo/monteCarloSimulation.ts`): a stochastic
     * drawdown simulation, not a deterministic compound-interest formula.
     * Simplified to the widget's common configuration - normal-distributed
     * yearly returns per pot, proportional or sequential withdrawal, optional
     * inflation and contributions - and drops upstream's dynamic withdrawal
     * rules, tax bands, fees and historical-return replay models, which
     * Actua's widget meta never configures. `percentage` is the success rate
     * (share of runs that never failed to fund a year's spending before
     * [subtitle]'s target age), and `points` are the median/10th-percentile
     * ending-balance bands per simulated year, driving the chart's
     * uncertainty cone.
     */
    private fun monteCarlo(
        id: String, name: String, meta: JSONObject?, balances: Map<String, Long>, today: LocalDate,
    ): ReportWidget {
        data class Pot(val balance: Double, val meanReturn: Double, val stdDev: Double, val accessAge: Int?)
        data class SpendingPhase(val fromAge: Int?, val annualWithdrawal: Double)
        data class Contribution(val potId: String, val fromAge: Int?, val toAge: Int?, val annualAmount: Double, val adjustsWithInflation: Boolean)

        val totalBalance = balances.values.sum().coerceAtLeast(0).toDouble()
        val potsMeta = meta?.optJSONArray("pots")
        val potIds = mutableListOf<String>()
        val pots = if (potsMeta != null && potsMeta.length() > 0) (0 until potsMeta.length()).mapNotNull { index ->
            val potMeta = potsMeta.optJSONObject(index) ?: return@mapNotNull null
            potIds += potMeta.optString("id", "pot-$index")
            val accountId = potMeta.optString("accountId").takeIf(String::isNotBlank)
            val starting = (accountId?.let(balances::get) ?: potMeta.optLong("startingBalance", 0)).toDouble().coerceAtLeast(0.0)
            Pot(
                starting,
                potMeta.optDouble("expectedReturnMean", 0.06),
                potMeta.optDouble("returnStdDev", 0.10).coerceAtLeast(0.0),
                potMeta.optInt("accessAge", -1).takeIf { potMeta.has("accessAge") && !potMeta.isNull("accessAge") },
            )
        } else {
            potIds += "pot-1"
            listOf(Pot(
                totalBalance,
                (meta?.optDouble("returnMean", 6.0) ?: 6.0) / 100.0,
                ((meta?.optDouble("returnStdDev", 10.0) ?: 10.0) / 100.0).coerceAtLeast(0.0),
                null,
            ))
        }

        val currentAge = meta?.optInt("currentAge", 60) ?: 60
        val targetAge = meta?.optInt("targetAge", 90) ?: 90
        val horizonYears = (targetAge - currentAge).coerceIn(1, 100)
        val simulationCount = (meta?.optInt("simulationCount", 5000) ?: 5000).coerceIn(1000, 10000)

        val phasesMeta = meta?.optJSONArray("spendingPhases")
        val phases = (if (phasesMeta != null && phasesMeta.length() > 0) (0 until phasesMeta.length()).mapNotNull { index ->
            val phaseMeta = phasesMeta.optJSONObject(index) ?: return@mapNotNull null
            SpendingPhase(
                phaseMeta.optInt("fromAge", -1).takeIf { phaseMeta.has("fromAge") && !phaseMeta.isNull("fromAge") },
                phaseMeta.optDouble("annualWithdrawal", 0.0),
            )
        } else emptyList()).ifEmpty { listOf(SpendingPhase(null, totalBalance * 0.04)) }
        fun activePhase(age: Int) = phases.filter { it.fromAge == null || it.fromAge <= age }
            .maxByOrNull { it.fromAge ?: Int.MIN_VALUE } ?: phases.first()

        val contributionsMeta = meta?.optJSONArray("contributions")
        val contributions = (0 until (contributionsMeta?.length() ?: 0)).mapNotNull { index ->
            val contributionMeta = contributionsMeta!!.optJSONObject(index) ?: return@mapNotNull null
            Contribution(
                contributionMeta.optString("potId"),
                contributionMeta.optInt("fromAge", -1).takeIf { contributionMeta.has("fromAge") && !contributionMeta.isNull("fromAge") },
                contributionMeta.optInt("toAge", -1).takeIf { contributionMeta.has("toAge") && !contributionMeta.isNull("toAge") },
                contributionMeta.optDouble("annualAmount", 0.0),
                contributionMeta.optBoolean("adjustsWithInflation", true),
            )
        }

        val inflationMean = when {
            meta == null || !meta.has("inflationMean") -> 0.025
            meta.isNull("inflationMean") -> null
            else -> meta.optDouble("inflationMean")
        }
        val inflationStdDev = (meta?.optDouble("inflationStdDev", 0.02) ?: 0.02).coerceAtLeast(0.0)
        val minimumSpending = meta?.optDouble("minimumSpending", 0.0) ?: 0.0
        val sequential = meta?.optString("withdrawalStrategy", "proportional") == "sequential"

        // Fixed-seed mulberry32 PRNG (ported from the same file) so the headline
        // percentage and chart are stable across recompositions instead of
        // flickering on every redraw.
        var state = 1234
        fun nextUniform(): Double {
            state += 0x6d2b79f5
            var mixed = (state xor (state ushr 15)) * (1 or state)
            mixed = (mixed + (mixed xor (mixed ushr 7)) * (61 or mixed)) xor mixed
            return ((mixed xor (mixed ushr 14)).toLong() and 0xFFFFFFFFL).toDouble() / 4294967296.0
        }
        fun nextNormal(): Double {
            val uniform1 = 1 - nextUniform()
            val uniform2 = nextUniform()
            return sqrt(-2 * ln(uniform1)) * cos(2 * Math.PI * uniform2)
        }

        val potCount = pots.size
        val potBalances = DoubleArray(potCount)
        val balancesByYear = Array(horizonYears + 1) { DoubleArray(simulationCount) }
        var survived = 0
        for (sim in 0 until simulationCount) {
            for (i in 0 until potCount) potBalances[i] = pots[i].balance
            var cumulativeInflation = 1.0
            var depleted = false
            balancesByYear[0][sim] = potBalances.sum()
            for (year in 1..horizonYears) {
                val age = currentAge + year - 1
                if (!depleted) {
                    inflationMean?.let { cumulativeInflation *= (1 + it + inflationStdDev * nextNormal()) }
                    contributions.forEach { contribution ->
                        if (contribution.fromAge != null && age < contribution.fromAge) return@forEach
                        if (contribution.toAge != null && age > contribution.toAge) return@forEach
                        val potIndex = potIds.indexOf(contribution.potId)
                        if (potIndex < 0) return@forEach
                        potBalances[potIndex] += contribution.annualAmount *
                            (if (contribution.adjustsWithInflation) cumulativeInflation else 1.0)
                    }
                    val planned = maxOf(activePhase(age).annualWithdrawal, minimumSpending) * cumulativeInflation
                    var accessibleTotal = 0.0
                    for (i in 0 until potCount) if (pots[i].accessAge == null || age >= pots[i].accessAge!!) accessibleTotal += potBalances[i]
                    val withdrawal = minOf(planned, accessibleTotal)
                    if (withdrawal < planned - 0.5) depleted = true
                    if (withdrawal > 0) {
                        var remaining = withdrawal
                        val lastAccessible = (0 until potCount).lastOrNull { pots[it].accessAge == null || age >= pots[it].accessAge!! } ?: -1
                        for (i in 0 until potCount) {
                            if (pots[i].accessAge != null && age < pots[i].accessAge!!) continue
                            if (sequential && remaining <= 0) continue
                            val take = when {
                                sequential -> minOf(potBalances[i], remaining)
                                i == lastAccessible -> remaining
                                accessibleTotal > 0 -> withdrawal * (potBalances[i] / accessibleTotal)
                                else -> 0.0
                            }
                            potBalances[i] -= take
                            remaining -= take
                        }
                    }
                    for (i in 0 until potCount) {
                        val yearReturn = pots[i].meanReturn + pots[i].stdDev * nextNormal()
                        potBalances[i] = (potBalances[i] * (1 + yearReturn)).coerceAtLeast(0.0)
                    }
                    if (depleted) for (i in 0 until potCount) potBalances[i] = 0.0
                }
                balancesByYear[year][sim] = potBalances.sum()
            }
            if (!depleted) survived++
        }
        val successRate = if (simulationCount > 0) survived.toDouble() / simulationCount * 100.0 else 0.0

        fun percentile(values: DoubleArray, fraction: Double): Long {
            val sorted = values.sortedArray()
            val position = (sorted.size - 1) * fraction
            val lower = position.toInt()
            val upper = kotlin.math.ceil(position).toInt().coerceAtMost(sorted.size - 1)
            val weight = position - lower
            return (sorted[lower] * (1 - weight) + sorted[upper] * weight).roundToLong()
        }
        val points = (0..horizonYears).map { year ->
            ReportPoint(
                YearMonth.from(today).plusYears(year.toLong()).toString(),
                percentile(balancesByYear[year], 0.5),
                percentile(balancesByYear[year], 0.1),
            )
        }
        return ReportWidget(
            id, ReportWidgetKind.MONTE_CARLO, name,
            percentage = (successRate * 10).roundToLong() / 10.0,
            subtitle = "to age $targetAge",
            points = points,
        )
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
                    rows.filter { it.amountCents < 0 }.sumOf { -it.amountCents }, rows.map { it.id })
            }.toList()
        return ReportWidget(id, ReportWidgetKind.CASH_FLOW, name, points = points,
            valueCents = points.sumOf { it.primaryCents - it.secondaryCents })
    }

    private fun spending(
        id: String, name: String, meta: JSONObject?, transactions: List<ActualTransaction>,
        context: RuleContext, incomeCategoryIds: Set<String>,
        budgetedByCategory: (YearMonth) -> Map<String, Long>, today: LocalDate,
    ): ReportWidget {
        // Mirrors PWA's hardcoded `makeQuery` filters (spending-spreadsheet.ts): exclude only the
        // transaction's own off-budget account and income-categorized rows. Transfers are not
        // excluded here — PWA doesn't exclude them either, so a transfer whose *other* leg is
        // off-budget (or between two on-budget accounts) still counts, same as PWA. Whatever the
        // widget's own `conditions` (set in the PWA) additionally exclude is applied below via
        // RulesEngine, not hardcoded here.
        val scoped = transactions.filterNot { it.tombstone ||
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
        fun spentRows(month: YearMonth, throughDay: Int? = null): List<ActualTransaction> {
            val endDay = throughDay?.coerceAtMost(month.lengthOfMonth())
            return matching.filter { transaction ->
                val date = transaction.localDate()
                YearMonth.from(date) == month && (endDay == null || date.dayOfMonth <= endDay)
            }
        }
        fun spent(month: YearMonth, throughDay: Int? = null): Long = -spentRows(month, throughDay).sumOf { it.amountCents }
        val current = spent(compare, currentCutoff)
        val currentIds = spentRows(compare, currentCutoff).map { it.id }
        var comparisonIds = emptyList<String>()
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
                    "all-time" -> transactions.filterNot { it.tombstone }
                        .minOfOrNull { YearMonth.from(it.localDate()) }
                        ?.let { ChronoUnit.MONTHS.between(it, compare).toInt() } ?: 0
                    else -> range?.optInt("months", 3)?.takeIf { it in setOf(3, 6, 12) } ?: 3
                }
                if (months <= 0) 0 else (1..months).map {
                    spent(compare.minusMonths(it.toLong()), comparisonCutoff)
                }.average().roundToLong()
            }
            else -> spent(compareTo, comparisonCutoff).also { comparisonIds = spentRows(compareTo, comparisonCutoff).map { it.id } }
        }
        return ReportWidget(id, ReportWidgetKind.SPENDING, name, valueCents = current, comparisonCents = comparison,
            valueTransactionIds = currentIds, comparisonTransactionIds = comparisonIds)
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
    private fun JSONArray.strings(): List<String> = (0 until length()).mapNotNull {
        optString(it).takeIf(String::isNotBlank)
    }

    /** Arithmetic subset used by Actuali's formula cards: +, -, *, /, parentheses and unary signs. */
    private class ArithmeticParser(private val source: String) {
        private var index = 0

        fun parse(): Double? = runCatching {
            val result = expression()
            skipSpaces()
            check(index == source.length)
            result
        }.getOrNull()

        private fun expression(): Double {
            var value = term()
            while (true) {
                skipSpaces()
                value = when (peek()) {
                    '+' -> { index++; value + term() }
                    '-' -> { index++; value - term() }
                    else -> return value
                }
            }
        }

        private fun term(): Double {
            var value = factor()
            while (true) {
                skipSpaces()
                value = when (peek()) {
                    '*' -> { index++; value * factor() }
                    '/' -> { index++; val divisor = factor(); check(divisor != 0.0); value / divisor }
                    else -> return value
                }
            }
        }

        private fun factor(): Double {
            skipSpaces()
            return when (peek()) {
                '+' -> { index++; factor() }
                '-' -> { index++; -factor() }
                '(' -> { index++; val value = expression(); skipSpaces(); check(peek() == ')'); index++; value }
                else -> number()
            }
        }

        private fun number(): Double {
            val start = index
            while (peek()?.let { it.isDigit() || it == '.' } == true) index++
            check(index > start)
            return source.substring(start, index).toDouble()
        }

        private fun skipSpaces() { while (peek()?.isWhitespace() == true) index++ }
        private fun peek(): Char? = source.getOrNull(index)
    }

    private fun label(type: String) = when (type) {
        "summary-card" -> "Summary"; "net-worth-card" -> "Net Worth"; "cash-flow-card" -> "Cash Flow"
        "spending-card" -> "Spending"; "markdown-card" -> "Notes"; "age-of-money-card" -> "Age of Money"
        "formula-card" -> "Formula"; "custom-report" -> "Custom Report"; "calendar-card" -> "Calendar"
        "crossover-card" -> "Crossover"; "budget-analysis-card" -> "Budget Analysis"; "sankey-card" -> "Sankey"
        "balance-forecast-card" -> "Balance Forecast"; "monte-carlo-card" -> "Monte Carlo Analysis"
        else -> type.ifBlank { "Unsupported report" }
    }
}
