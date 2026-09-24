package com.azimulkabir.actua.data.reports

import com.azimulkabir.actua.data.budget.model.ActualCategory
import com.azimulkabir.actua.data.budget.model.ActualCategoryGroup
import com.azimulkabir.actua.data.budget.model.ActualTransaction
import com.azimulkabir.actua.data.rules.RuleContext
import com.azimulkabir.actua.data.schedules.ActualScheduleSummary
import com.azimulkabir.actua.data.schedules.DayDate
import com.azimulkabir.actua.data.schedules.ScheduleAmountOp
import com.azimulkabir.actua.data.schedules.ScheduleDateCondition
import com.azimulkabir.actua.data.schedules.ScheduledAmount
import com.azimulkabir.actua.model.ReportWidgetKind
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class SankeyTest {
    private val groups = listOf(
        ActualCategoryGroup("gi", "Income", true, false, 1.0, listOf(
            ActualCategory("salary", "Salary", "gi", true, false, 1.0),
            ActualCategory("bonus", "Bonus", "gi", true, false, 2.0),
        )),
        ActualCategoryGroup("ge", "Bills", false, false, 2.0, listOf(
            ActualCategory("rent", "Rent", "ge", false, false, 1.0),
        )),
    )
    private val context = RuleContext(
        categoryNames = groups.flatMap { it.categories }.associate { it.id to it.name },
        categoryGroupIds = groups.flatMap { it.categories }.associate { it.id to it.groupId },
        categoryGroupNames = groups.associate { it.id to it.name },
    )
    private val incomeCategoryIds = groups.flatMap { it.categories }.filter { it.isIncome }.mapTo(mutableSetOf()) { it.id }

    private fun tx(id: String, date: Int, amount: Long, cat: String?) = ActualTransaction(
        id, "a", date, amount, null, null, cat, null, null, false, false, null, false, null, false, null, null, null, null)

    private fun row(meta: String? = null) = DashboardWidgetRow("w", "sankey-card", meta)

    @Test fun `breaks income down by source category, unlike the group-level expense breakdown`() {
        val rows = listOf(
            tx("1", 20260405, 800000, "salary"),
            tx("2", 20260406, 200000, "bonus"),
            tx("3", 20260410, -300000, "rent"),
        )
        val widget = CoreReportEngine.compute(
            row("""{"timeFrame":{"mode":"static","start":"2026-04","end":"2026-04"}}"""),
            rows, context, incomeCategoryIds,
        )
        assertEquals(ReportWidgetKind.SANKEY, widget.kind)
        assertEquals(1000000L, widget.valueCents)
        assertEquals(listOf("Salary" to 800000L, "Bonus" to 200000L), widget.incomeCategories.map { it.name to it.spentCents })
        assertEquals(listOf("Bills" to 300000L), widget.categories.map { it.name to it.spentCents })
    }

    @Test fun `sets a subtitle with the resolved date range`() {
        val widget = CoreReportEngine.compute(
            row("""{"timeFrame":{"mode":"static","start":"2026-04","end":"2026-09"}}"""),
            emptyList(), context, incomeCategoryIds,
        )
        assertEquals("Apr 2026 - Sep 2026", widget.subtitle)
    }

    @Test fun `single-month range collapses the subtitle to one month`() {
        val widget = CoreReportEngine.compute(
            row("""{"timeFrame":{"mode":"static","start":"2026-04","end":"2026-04"}}"""),
            emptyList(), context, incomeCategoryIds,
        )
        assertEquals("Apr 2026", widget.subtitle)
    }
}

/**
 * PWA's own spending query (`spending-spreadsheet.ts`/`makeQuery.ts`) has no transfer exclusion —
 * it only drops the leg whose own account is off-budget or whose category is income. Actua used to
 * additionally hardcode `transferAccountId == null`, which silently dropped transfers PWA counts
 * (see actua#531).
 */
class SpendingTest {
    private val context = RuleContext(offBudgetAccountIds = setOf("off"))

    private fun tx(
        id: String, accountId: String, date: Int, amount: Long,
        transferAccountId: String? = null, categoryId: String? = null,
    ) = ActualTransaction(id, accountId, date, amount, null, null, categoryId, null, null, false, false, null,
        false, null, false, null, null, null, transferAccountId)

    private fun row(meta: String? = null) = DashboardWidgetRow("w", "spending-card", meta)

    @Test fun `counts an on-budget-to-on-budget transfer leg as spending, matching PWA's hardcoded filters`() {
        val rows = listOf(tx("1", "checking", 20260405, -50000, transferAccountId = "savings"))
        val widget = CoreReportEngine.compute(
            row("""{"isLive":false,"compare":"2026-04"}"""), rows, context,
            today = LocalDate.of(2026, 4, 20),
        )
        assertEquals(ReportWidgetKind.SPENDING, widget.kind)
        assertEquals(50000L, widget.valueCents)
    }

    @Test fun `excludes only the leg whose own account is off-budget, not the whole transfer`() {
        val rows = listOf(
            tx("1", "checking", 20260405, -50000, transferAccountId = "off"),
            tx("2", "off", 20260405, 50000, transferAccountId = "checking"),
        )
        val widget = CoreReportEngine.compute(
            row("""{"isLive":false,"compare":"2026-04"}"""), rows, context,
            today = LocalDate.of(2026, 4, 20),
        )
        assertEquals(50000L, widget.valueCents)
    }

    @Test fun `all-time average range starts from the budget's earliest transaction, not the scoped one`() {
        // Earliest transaction overall is a 2024-01 off-budget opening balance, excluded from the
        // spending scope; on-budget spending only starts 2024-03. All-time average must still divide
        // by the number of months since 2024-01, matching PWA's unfiltered get-earliest-transaction.
        val rows = listOf(
            tx("1", "off", 20240101, 100_000),
            tx("2", "checking", 20240305, -600),
            tx("3", "checking", 20240405, -1_200),
        )
        val widget = CoreReportEngine.compute(
            row("""{"isLive":false,"compare":"2024-05","mode":"average",
                |"averageRange":{"mode":"all-time"}}""".trimMargin()),
            rows, context,
            today = LocalDate.of(2024, 5, 20),
        )
        // 4 months elapsed from 2024-01 to 2024-05 (exclusive of compare month); spend only in Mar/Apr,
        // averaged over all 4 months: (600 + 1200) / 4 = 450.
        assertEquals(450L, widget.comparisonCents)
    }
}

/**
 * Ported from Actual's `crossover-spreadsheet.ts`: the default annual return is the CAGR between
 * the first and last historical monthly balance (not the widget's safe-withdrawal-rate fallback),
 * the projection seeds from that last historical balance (not the account's live balance), and the
 * expense projection reads `projectionType` (`hampel`/`median`/`mean`) instead of a plain average.
 */
class CrossoverTest {
    private fun tx(id: String, accountId: String, date: Int, amount: Long, categoryId: String? = null) =
        ActualTransaction(id, accountId, date, amount, null, null, categoryId, null, null, false, false, null,
            false, null, false, null, null, null, null)

    private fun row(meta: String) = DashboardWidgetRow("w", "crossover-card", meta)

    @Test fun `default return is the CAGR of historical balances, seeded from the last historical balance not the live one`() {
        // "invest" grows 10%/month for 3 months (100000 -> 110000 -> 121000 -> 133100), so the CAGR
        // is exactly 10%/month. accountBalances carries a deliberately different "live" balance to
        // prove the projection seeds from the last *historical* balance (133100), not it.
        val rows = listOf(
            tx("1", "invest", 20260105, 100_000),
            tx("2", "invest", 20260205, 10_000),
            tx("3", "invest", 20260305, 11_000),
            tx("4", "invest", 20260405, 12_100),
            tx("5", "checking", 20260110, -100, "rent"),
            tx("6", "checking", 20260210, -100, "rent"),
            tx("7", "checking", 20260310, -100, "rent"),
            tx("8", "checking", 20260410, -100, "rent"),
        )
        val widget = CoreReportEngine.compute(
            row("""{"timeFrame":{"mode":"static","start":"2026-01","end":"2026-04"},
                |"incomeAccountIds":["invest"],"safeWithdrawalRate":1.0}""".trimMargin()),
            rows, accountBalances = mapOf("invest" to 999_999_999L),
            today = LocalDate.of(2026, 5, 15),
        )
        assertEquals(ReportWidgetKind.CROSSOVER, widget.kind)
        assertEquals(
            listOf("2026-01" to (8333L to 100L), "2026-02" to (9167L to 100L),
                "2026-03" to (10083L to 100L), "2026-04" to (11092L to 100L)),
            widget.points.take(4).map { it.period to (it.primaryCents to it.secondaryCents) },
        )
        // First projected month (2026-05): balance grown 10% from the historical seed (133100 -> 146410).
        val projected = widget.points[4]
        assertEquals("2026-05", projected.period)
        assertEquals(12_201L, projected.primaryCents)
        assertEquals(100L, projected.secondaryCents)
        assertEquals(0L, widget.valueCents)
        assertEquals(100L, widget.comparisonCents)
    }

    @Test fun `expense projection reads projectionType instead of always averaging`() {
        // Monthly expenses 100, 100, 200, 100000: mean is pulled way up by the outlier, plain median
        // isn't, and the Hampel filter drops the outlier before taking the median of what remains.
        val rows = listOf(
            tx("1", "invest", 20260105, 100_000),
            tx("2", "checking", 20260110, -100, "rent"),
            tx("3", "checking", 20260210, -100, "rent"),
            tx("4", "checking", 20260310, -200, "rent"),
            tx("5", "checking", 20260410, -100_000, "rent"),
        )
        fun comparison(projectionType: String) = CoreReportEngine.compute(
            row("""{"timeFrame":{"mode":"static","start":"2026-01","end":"2026-04"},
                |"incomeAccountIds":["invest"],"safeWithdrawalRate":1.0,"projectionType":"$projectionType"}""".trimMargin()),
            rows, today = LocalDate.of(2026, 5, 15),
        ).comparisonCents
        assertEquals(25_100L, comparison("mean"))
        assertEquals(150L, comparison("median"))
        assertEquals(100L, comparison("hampel"))
    }

    @Test fun `an explicit, even empty, expenseCategoryIds list is honored as-is instead of matching everything`() {
        // Upstream's expenseCategoryIds memo only falls back to "every non-income category" when the
        // widget meta key is absent; an explicitly stored empty list means "no categories selected",
        // i.e. zero expenses every month - not "no filter, match everything".
        val rows = listOf(
            tx("1", "invest", 20260105, 100_000),
            tx("2", "checking", 20260110, -100, "rent"),
        )
        val widget = CoreReportEngine.compute(
            row("""{"timeFrame":{"mode":"static","start":"2026-01","end":"2026-01"},
                |"incomeAccountIds":["invest"],"expenseCategoryIds":[]}""".trimMargin()),
            rows, today = LocalDate.of(2026, 2, 1),
        )
        assertEquals(0L, widget.points.first().secondaryCents)
    }

    @Test fun `without an explicit expenseCategoryIds list, hidden categories are excluded by default`() {
        val context = RuleContext(hiddenCategoryIds = setOf("archived-rent"))
        val rows = listOf(
            tx("1", "invest", 20260105, 100_000),
            tx("2", "checking", 20260110, -100, "archived-rent"),
            tx("3", "checking", 20260110, -50, "groceries"),
        )
        val widget = CoreReportEngine.compute(
            row("""{"timeFrame":{"mode":"static","start":"2026-01","end":"2026-01"},
                |"incomeAccountIds":["invest"]}""".trimMargin()),
            rows, context, today = LocalDate.of(2026, 2, 1),
        )
        assertEquals(50L, widget.points.first().secondaryCents)
    }

    @Test fun `showHiddenCategories includes hidden categories in the default expense set`() {
        val context = RuleContext(hiddenCategoryIds = setOf("archived-rent"))
        val rows = listOf(
            tx("1", "invest", 20260105, 100_000),
            tx("2", "checking", 20260110, -100, "archived-rent"),
            tx("3", "checking", 20260110, -50, "groceries"),
        )
        val widget = CoreReportEngine.compute(
            row("""{"timeFrame":{"mode":"static","start":"2026-01","end":"2026-01"},
                |"incomeAccountIds":["invest"],"showHiddenCategories":true}""".trimMargin()),
            rows, context, today = LocalDate.of(2026, 2, 1),
        )
        assertEquals(150L, widget.points.first().secondaryCents)
    }
}

/**
 * Ported from Actual's `age-of-money-spreadsheet.ts` (`buildTransferInclusionFilter`): without an
 * `account` condition on the widget, a transfer only counts as real money in/out of the pool when
 * its counterpart account is off-budget. With an `account` condition, a transfer whose counterpart
 * falls outside that filtered set also counts, even when the counterpart is itself on-budget (see
 * actua#532).
 */
class AgeOfMoneyTest {
    private val context = RuleContext()

    private fun tx(id: String, accountId: String, date: Int, amount: Long, transferAccountId: String? = null) =
        ActualTransaction(id, accountId, date, amount, null, null, null, null, null, false, false, null,
            false, null, false, null, null, null, transferAccountId)

    private fun row(meta: String) = DashboardWidgetRow("w", "age-of-money-card", meta)

    @Test fun `without an account filter, a transfer to an on-budget counterpart is excluded from the pool`() {
        val rows = listOf(
            tx("1", "checking", 20260401, 100_000),
            tx("2", "checking", 20260410, -50_000, transferAccountId = "cc"),
        )
        val widget = CoreReportEngine.compute(
            row("""{"timeFrame":{"mode":"static","start":"2026-04","end":"2026-04"}}"""),
            rows, context, today = LocalDate.of(2026, 4, 20),
        )
        assertEquals(ReportWidgetKind.AGE_OF_MONEY, widget.kind)
        assertEquals(null, widget.valueCents)
    }

    @Test fun `with an account filter, a transfer to an on-budget counterpart outside the filtered set counts as an expense`() {
        val rows = listOf(
            tx("1", "checking", 20260401, 100_000),
            tx("2", "checking", 20260410, -50_000, transferAccountId = "cc"),
        )
        val widget = CoreReportEngine.compute(
            row("""{"timeFrame":{"mode":"static","start":"2026-04","end":"2026-04"},
                |"conditions":[{"op":"is","field":"account","value":"checking"}]}""".trimMargin()),
            rows, context, today = LocalDate.of(2026, 4, 20),
        )
        assertEquals(ReportWidgetKind.AGE_OF_MONEY, widget.kind)
        assertEquals(9L, widget.valueCents)
    }

    @Test fun `with an account filter, a transfer to a counterpart inside the filtered set is still excluded`() {
        val rows = listOf(
            tx("1", "checking", 20260401, 100_000),
            tx("2", "checking", 20260410, -50_000, transferAccountId = "savings"),
        )
        val widget = CoreReportEngine.compute(
            row("""{"timeFrame":{"mode":"static","start":"2026-04","end":"2026-04"},
                |"conditions":[{"op":"oneOf","field":"account","value":["checking","savings"]}]}""".trimMargin()),
            rows, context, today = LocalDate.of(2026, 4, 20),
        )
        assertEquals(null, widget.valueCents)
    }

    /**
     * Ported from Actual's `age-of-money-spreadsheet.ts` (`makeIncomeQuery`/`makeExpenseQuery`):
     * both queries cap at `fixedEnd` (`minOf(end, today)`) *before* the FIFO draining runs, so a
     * post-dated transaction never enters the pool at all. Actua used to only filter the already-
     * computed ages afterward, so a post-dated income bucket could still be drained by an earlier
     * expense and silently zero out its age (see actua#543).
     */
    @Test fun `a post-dated income transaction is excluded from the FIFO pool, not just the displayed ages`() {
        val rows = listOf(
            tx("1", "checking", 20260901, 10_000),
            tx("2", "checking", 20261005, 50_000), // after "today" - must not enter the pool
            tx("3", "checking", 20260910, -60_000),
        )
        val widget = CoreReportEngine.compute(
            row("""{"timeFrame":{"mode":"static","start":"2026-09","end":"2026-09"}}"""),
            rows, context, today = LocalDate.of(2026, 9, 24),
        )
        // Without the post-dated bucket, the Sep-10 expense only drains the Sep-1 bucket and stops
        // there (age 9 days); with it, it would also drain the Oct-5 bucket and report a bogus age
        // of 0 (a future bucket date clamped by coerceAtLeast(0)).
        assertEquals(9L, widget.valueCents)
    }
}

/**
 * Ported from Actual's `isScheduleOccurrencePosted`/`buildFutureScheduleOccurrences`
 * (forecast-schedules.ts): an occurrence that already has a matching posted transaction must not
 * also be projected as a synthetic schedule delta, or the running balance and the "N scheduled
 * transactions included" count both double it (see actua#539).
 */
class BalanceForecastTest {
    private val context = RuleContext()

    private fun tx(id: String, date: Int, amount: Long, scheduleId: String? = null) = ActualTransaction(
        id, "checking", date, amount, null, null, null, null, null, false, false, null,
        false, null, false, null, scheduleId, null, null)

    private fun schedule(
        id: String, day: DayDate, amount: Long, dateOp: String? = "is", postsTransaction: Boolean = false,
    ) = ActualScheduleSummary(
        id, null, null, day, null, null, "checking", null, ScheduledAmount.Fixed(amount), ScheduleAmountOp.EXACT,
        dateOp, ScheduleDateCondition.Fixed(day), postsTransaction, false, null, null, false, null, null, null,
    )

    private fun row(meta: String? = null) = DashboardWidgetRow("w", "balance-forecast-card", meta)

    @Test fun `an occurrence already posted as a real transaction is not also projected as a schedule delta`() {
        val today = LocalDate.of(2026, 9, 10)
        val rows = listOf(tx("posted-1", 20260905, -50_000, scheduleId = "rent"))
        val schedules = listOf(schedule("rent", DayDate(2026, 9, 5), -50_000))
        val widget = CoreReportEngine.compute(
            row("""{"timeFrame":{"mode":"static","start":"2026-09","end":"2026-09"}}"""),
            rows, context, today = today, accountBalances = mapOf("checking" to -50_000L), schedules = schedules,
        )
        assertEquals(-50_000L, widget.valueCents)
        assertEquals("No scheduled transactions in this range", widget.subtitle)
    }

    @Test fun `an unposted occurrence is still projected as a schedule delta`() {
        val today = LocalDate.of(2026, 9, 1)
        val schedules = listOf(schedule("rent", DayDate(2026, 9, 5), -50_000))
        val widget = CoreReportEngine.compute(
            row("""{"timeFrame":{"mode":"static","start":"2026-09","end":"2026-09"}}"""),
            emptyList(), context, today = today, accountBalances = mapOf("checking" to 0L), schedules = schedules,
        )
        assertEquals(-50_000L, widget.valueCents)
        assertEquals("1 scheduled transactions included", widget.subtitle)
    }
}

/**
 * PWA (`calendar-spreadsheet.ts`) and Actuali's `CalendarEngine` both widen the resolved time frame
 * to whole calendar months before filtering transactions; a non-month-aligned resolved range (a
 * `static` range with day-level bounds, or `yearToDate`/`priorYearToDate` ending at `today`) must
 * not drop transactions outside the literal range but inside the containing month (actua#545).
 */
class CalendarTest {
    private val context = RuleContext()

    private fun tx(id: String, date: Int, amount: Long) = ActualTransaction(
        id, "a", date, amount, null, null, null, null, null, false, false, null, false, null, false, null, null, null, null)

    private fun row(meta: String? = null) = DashboardWidgetRow("w", "calendar-card", meta)

    @Test fun `widens a static day-level range to whole calendar months before filtering`() {
        val rows = listOf(
            tx("1", 20240105, 50000),
            tx("2", 20240220, -30000),
        )
        val widget = CoreReportEngine.compute(
            row("""{"timeFrame":{"mode":"static","start":"2024-01-15","end":"2024-02-10"}}"""),
            rows, context,
        )
        assertEquals(ReportWidgetKind.CALENDAR, widget.kind)
        assertEquals(50000L, widget.valueCents)
        assertEquals(30000L, widget.comparisonCents)
        assertEquals(2, widget.points.size)
    }

    @Test fun `widens yearToDate's today-bounded end to the end of the current month`() {
        val rows = listOf(tx("1", 20260930, 40000))
        val widget = CoreReportEngine.compute(
            row("""{"timeFrame":{"mode":"yearToDate"}}"""),
            rows, context, today = LocalDate.of(2026, 9, 24),
        )
        assertEquals(ReportWidgetKind.CALENDAR, widget.kind)
        assertEquals(40000L, widget.valueCents)
        assertEquals(1, widget.points.size)
    }
}
