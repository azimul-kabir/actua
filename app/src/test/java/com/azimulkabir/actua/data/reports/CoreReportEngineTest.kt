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
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

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

    private val april = """{"timeFrame":{"mode":"static","start":"2026-04","end":"2026-04"}}"""

    @Test fun `a refund to an expense category reduces that category's outflow instead of adding income`() {
        val rows = listOf(
            tx("1", 20260405, 800000, "salary"),
            tx("2", 20260410, -300000, "rent"),
            tx("3", 20260412, 50000, "rent"),
        )
        val widget = CoreReportEngine.compute(row(april), rows, context, incomeCategoryIds)
        assertEquals(800000L, widget.valueCents)
        assertEquals(listOf("Bills" to 250000L), widget.categories.map { it.name to it.spentCents })
        assertEquals(250000L, widget.comparisonCents)
    }

    @Test fun `uncategorized transactions and transfers stay out of the graph`() {
        val transfer = tx("4", 20260415, -100000, null).copy(transferAccountId = "savings")
        val rows = listOf(
            tx("1", 20260405, 800000, "salary"),
            tx("2", 20260410, -300000, "rent"),
            tx("3", 20260411, 20000, null),
            tx("5", 20260412, -7000, null),
            transfer,
        )
        val widget = CoreReportEngine.compute(row(april), rows, context, incomeCategoryIds)
        assertEquals(800000L, widget.valueCents)
        assertEquals(300000L, widget.comparisonCents)
        assertEquals(listOf("Salary"), widget.incomeCategories.map { it.name })
    }

    @Test fun `a net-positive expense category flows in rather than counting as spending`() {
        val rows = listOf(
            tx("1", 20260410, -10000, "rent"),
            tx("2", 20260412, 30000, "rent"),
        )
        val widget = CoreReportEngine.compute(row(april), rows, context, incomeCategoryIds)
        assertEquals(listOf("Rent" to 20000L), widget.incomeCategories.map { it.name to it.spentCents })
        assertEquals(emptyList<Any>(), widget.categories)
        assertEquals(0L, widget.comparisonCents)
    }

    @Test fun `a net-negative income category flows out`() {
        val rows = listOf(
            tx("1", 20260405, 800000, "salary"),
            tx("2", 20260406, -5000, "bonus"),
        )
        val widget = CoreReportEngine.compute(row(april), rows, context, incomeCategoryIds)
        assertEquals(800000L, widget.valueCents)
        assertEquals(listOf("Bonus" to 5000L), widget.categories.map { it.name to it.spentCents })
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

    @Test fun `a refund posted to an expense category nets against that month's spend instead of being dropped`() {
        // Upstream's expense query has no amount-sign filter: it sums every transaction in the
        // selected categories for the month and negates the total, so a positive refund/reimbursement
        // reduces that month's projected expense. Filtering to amountCents < 0 (as a naive port would)
        // drops the refund entirely and inflates the expense, understating years-to-retire.
        val rows = listOf(
            tx("1", "invest", 20260105, 100_000),
            tx("2", "checking", 20260110, -500, "rent"),
            tx("3", "checking", 20260115, 200, "rent"),
        )
        val widget = CoreReportEngine.compute(
            row("""{"timeFrame":{"mode":"static","start":"2026-01","end":"2026-01"},
                |"incomeAccountIds":["invest"],"expenseCategoryIds":["rent"]}""".trimMargin()),
            rows, today = LocalDate.of(2026, 2, 1),
        )
        assertEquals(300L, widget.points.first().secondaryCents)
    }

    @Test fun `an off-budget or transfer transaction in a selected expense category still counts, matching upstream`() {
        val rows = listOf(
            tx("1", "invest", 20260105, 100_000),
            tx("2", "checking", 20260110, -500, "rent").copy(transferAccountId = "savings"),
        )
        val context = RuleContext(offBudgetAccountIds = setOf("checking"))
        val widget = CoreReportEngine.compute(
            row("""{"timeFrame":{"mode":"static","start":"2026-01","end":"2026-01"},
                |"incomeAccountIds":["invest"],"expenseCategoryIds":["rent"]}""".trimMargin()),
            rows, context, today = LocalDate.of(2026, 2, 1),
        )
        assertEquals(500L, widget.points.first().secondaryCents)
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

    @Test fun `sliding-window mode re-anchors live to previous month instead of statically shifting the stored months`() {
        // Mirrors upstream's calculateTimeRange/getLatestRange plus Crossover.tsx's own -1-month
        // shift: the stored window's WIDTH (2 months, Jan-Mar) must be preserved but re-anchored to
        // end at "previous month" relative to `today`, not statically shifted from whatever start/end
        // was last saved - otherwise the historical window (and the CAGR/expense base it drives) goes
        // stale the longer it's been since the widget was saved.
        val months = generateSequence(YearMonth.of(2025, 11)) { it.plusMonths(1) }
            .takeWhile { !it.isAfter(YearMonth.of(2026, 7)) }.toList()
        val rows = months.mapIndexed { i, m -> tx("t$i", "invest", m.year * 10_000 + m.monthValue * 100 + 5, 1_000) }
        val widget = CoreReportEngine.compute(
            row("""{"timeFrame":{"mode":"sliding-window","start":"2026-01","end":"2026-03"},
                |"incomeAccountIds":["invest"]}""".trimMargin()),
            rows, today = LocalDate.of(2026, 8, 15),
        )
        assertEquals(listOf("2026-05", "2026-06", "2026-07"), widget.points.take(3).map { it.period })
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

    @Test fun `hidden categories named in an explicit expense list are excluded unless showHiddenCategories`() {
        // Upstream filters the resolved list with `showHiddenCategories || !c.hidden` after applying
        // the stored ids, so an explicit selection doesn't bring hidden categories back in.
        val context = RuleContext(hiddenCategoryIds = setOf("archived-rent"))
        val rows = listOf(
            tx("1", "invest", 20260105, 100_000),
            tx("2", "checking", 20260110, -100, "archived-rent"),
            tx("3", "checking", 20260110, -50, "groceries"),
        )
        fun expense(showHidden: Boolean) = CoreReportEngine.compute(
            row("""{"timeFrame":{"mode":"static","start":"2026-01","end":"2026-01"},"incomeAccountIds":["invest"],
                |"expenseCategoryIds":["archived-rent","groceries"],"showHiddenCategories":$showHidden}""".trimMargin()),
            rows, context, today = LocalDate.of(2026, 2, 1),
        ).points.first().secondaryCents
        assertEquals(50L, expense(showHidden = false))
        assertEquals(150L, expense(showHidden = true))
    }

    @Test fun `months to retire counts whole months from today to the start of the crossover month`() {
        // Income 100/month on a 1200 balance at 100% SWR, +1200 contributed monthly with 0% return:
        // income reaches the 400 expense in the third projected month (2026-04). Upstream's
        // differenceInMonths(2026-04-01, 2026-02-10) is 1, not the 2 calendar months between Feb and Apr.
        val rows = listOf(
            tx("1", "invest", 20260105, 1_200),
            tx("2", "checking", 20260110, -400, "rent"),
        )
        val widget = CoreReportEngine.compute(
            row("""{"timeFrame":{"mode":"static","start":"2026-01","end":"2026-01"},"incomeAccountIds":["invest"],
                |"safeWithdrawalRate":1.0,"estimatedReturn":0,"expectedContribution":1200}""".trimMargin()),
            rows, today = LocalDate.of(2026, 2, 10),
        )
        assertEquals("2026-04", widget.points.first { it.primaryCents >= 400 }.period)
        assertEquals(1L, widget.valueCents)
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

    private fun transferSchedule(day: DayDate, amount: Long) = ActualScheduleSummary(
        "save", null, null, day, null, null, "checking", "to-savings", ScheduledAmount.Fixed(amount),
        ScheduleAmountOp.EXACT, "is", ScheduleDateCondition.Fixed(day), false, false, null, null, false,
        null, null, null,
    )

    @Test fun `a transfer schedule between two forecast accounts nets out and counts once`() {
        val widget = CoreReportEngine.compute(
            row("""{"timeFrame":{"mode":"static","start":"2026-09","end":"2026-09"}}"""),
            emptyList(), context, today = LocalDate.of(2026, 9, 1),
            accountBalances = mapOf("checking" to 0L, "savings" to 0L),
            schedules = listOf(transferSchedule(DayDate(2026, 9, 5), -20_000)),
            transferAccountByPayee = mapOf("to-savings" to "savings", "to-checking" to "checking"),
        )
        assertEquals(0L, widget.valueCents)
        assertEquals("1 scheduled transactions included", widget.subtitle)
    }

    @Test fun `a transfer schedule into an account outside the forecast still leaves it`() {
        val widget = CoreReportEngine.compute(
            row("""{"accounts":["checking"],"timeFrame":{"mode":"static","start":"2026-09","end":"2026-09"}}"""),
            emptyList(), context, today = LocalDate.of(2026, 9, 1),
            accountBalances = mapOf("checking" to 0L, "savings" to 0L),
            schedules = listOf(transferSchedule(DayDate(2026, 9, 5), -20_000)),
            transferAccountByPayee = mapOf("to-savings" to "savings"),
        )
        assertEquals(-20_000L, widget.valueCents)
    }

    @Test fun `only the receiving leg is projected when just the destination account is selected`() {
        val widget = CoreReportEngine.compute(
            row("""{"accounts":["savings"],"timeFrame":{"mode":"static","start":"2026-09","end":"2026-09"}}"""),
            emptyList(), context, today = LocalDate.of(2026, 9, 1),
            accountBalances = mapOf("checking" to 0L, "savings" to 0L),
            schedules = listOf(transferSchedule(DayDate(2026, 9, 5), -20_000)),
            transferAccountByPayee = mapOf("to-savings" to "savings"),
        )
        assertEquals(20_000L, widget.valueCents)
        assertEquals("1 scheduled transactions included", widget.subtitle)
    }
}

/**
 * Ported from PWA's `useFormulaExecution.ts` (`prefetchFormulaQueries`) and Actuali's
 * `FormulaEngine.swift`: a saved sub-query with no explicit `timeFrame` mode means "no date
 * restriction" (all-time), not the widget-timeFrame default of the current calendar month; and a
 * `query("name")` referencing a name missing from the widget's `queries` map evaluates to 0
 * instead of silently summing every unfiltered current-month transaction (actua#548).
 */
class FormulaTest {
    private val context = RuleContext()

    private fun tx(id: String, date: Int, amount: Long, categoryId: String? = null) = ActualTransaction(
        id, "a", date, amount, null, null, categoryId, null, null, false, false, null, false, null, false, null, null, null, null)

    private fun row(meta: String) = DashboardWidgetRow("w", "formula-card", meta)

    @Test fun `a sub-query with no explicit timeFrame sums all-time, not just the current month`() {
        val rows = listOf(
            tx("1", 20240115, -1_000, "groceries"),
            tx("2", 20260924, -2_000, "groceries"),
        )
        val widget = CoreReportEngine.compute(
            row("""{"formula":"=query(\"all_groceries\")",
                |"queries":{"all_groceries":{"conditions":[{"field":"category","op":"is","value":"groceries"}]}}}""".trimMargin()),
            rows, context, today = LocalDate.of(2026, 9, 24),
        )
        assertEquals(ReportWidgetKind.FORMULA, widget.kind)
        assertEquals(-3_000L, widget.valueCents)
    }

    @Test fun `an unknown query name evaluates to 0 instead of matching every current-month transaction`() {
        val rows = listOf(tx("1", 20260924, -2_000, "groceries"))
        val widget = CoreReportEngine.compute(
            row("""{"formula":"=query(\"typo\")",
                |"queries":{"all_groceries":{"conditions":[{"field":"category","op":"is","value":"groceries"}]}}}""".trimMargin()),
            rows, context, today = LocalDate.of(2026, 9, 24),
        )
        assertEquals(0L, widget.valueCents)
    }

    @Test fun `a sub-query with an explicit timeFrame mode still applies its date restriction`() {
        val rows = listOf(
            tx("1", 20240115, -1_000, "groceries"),
            tx("2", 20260924, -2_000, "groceries"),
        )
        val widget = CoreReportEngine.compute(
            row("""{"formula":"=query(\"sep_groceries\")",
                |"queries":{"sep_groceries":{"conditions":[{"field":"category","op":"is","value":"groceries"}],
                |"timeFrame":{"mode":"static","start":"2026-09","end":"2026-09"}}}}""".trimMargin()),
            rows, context, today = LocalDate.of(2026, 9, 24),
        )
        assertEquals(-2_000L, widget.valueCents)
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

/** Upstream `calculateTimeRange` (`reportRanges.ts`) and the per-card default time frames (actua#948). */
class TimeFrameTest {
    private val today = LocalDate.of(2026, 8, 20)

    private fun range(meta: String?, type: String = "net-worth-card", latest: LocalDate? = null) =
        CoreReportEngine.timeFrame(meta?.let(::JSONObject), today, CoreReportEngine.defaultTimeFrame(type, today), latest)

    private fun tx(id: String, date: Int, amount: Long) = ActualTransaction(
        id, "a", date, amount, null, null, null, null, null, false, false, null, false, null, false, null, null, null, null)

    @Test fun `a card without a time frame or default covers the last 6 months`() {
        assertEquals(LocalDate.of(2026, 3, 1) to LocalDate.of(2026, 8, 31), range(null))
        assertEquals(LocalDate.of(2026, 3, 1) to LocalDate.of(2026, 8, 31), range(null, "sankey-card"))
    }

    @Test fun `card defaults match upstream`() {
        assertEquals(LocalDate.of(2026, 8, 1) to LocalDate.of(2026, 8, 31), range(null, "summary-card"))
        assertEquals(LocalDate.of(2026, 8, 1) to LocalDate.of(2026, 8, 31), range(null, "calendar-card"))
        assertEquals(LocalDate.of(2026, 8, 1) to LocalDate.of(2026, 8, 31), range(null, "cash-flow-card"))
        assertEquals(LocalDate.of(2026, 8, 1) to LocalDate.of(2027, 7, 31), range(null, "balance-forecast-card"))
    }

    @Test fun `a time frame without a mode slides`() {
        assertEquals(LocalDate.of(2026, 6, 1) to LocalDate.of(2026, 8, 31),
            range("""{"start":"2025-01","end":"2025-03"}"""))
    }

    @Test fun `quarter modes resolve to quarter bounds`() {
        assertEquals(LocalDate.of(2026, 7, 1) to LocalDate.of(2026, 9, 30), range("""{"mode":"currentQuarter"}"""))
        assertEquals(LocalDate.of(2026, 4, 1) to LocalDate.of(2026, 6, 30), range("""{"mode":"previousQuarter"}"""))
    }

    @Test fun `a day-shaped sliding window keeps its width in days and ends today`() {
        assertEquals(LocalDate.of(2026, 7, 22) to today,
            range("""{"mode":"sliding-window","start":"2025-01-01","end":"2025-01-30"}"""))
    }

    @Test fun `a reversed sliding window stays reversed`() {
        assertEquals(LocalDate.of(2026, 8, 1) to LocalDate.of(2026, 6, 30),
            range("""{"mode":"sliding-window","start":"2025-03","end":"2025-01"}"""))
    }

    @Test fun `full extends to the latest transaction's month when it is in the future`() {
        assertEquals(LocalDate.of(2024, 1, 1) to LocalDate.of(2026, 8, 31), range("""{"mode":"full","start":"2024-01"}"""))
        assertEquals(LocalDate.of(2024, 1, 1) to LocalDate.of(2026, 10, 31),
            range("""{"mode":"full","start":"2024-01"}""", latest = LocalDate.of(2026, 10, 3)))
    }

    @Test fun `static and calendar modes keep their bounds`() {
        assertEquals(LocalDate.of(2026, 1, 15) to LocalDate.of(2026, 2, 28),
            range("""{"mode":"static","start":"2026-01-15","end":"2026-02"}"""))
        assertEquals(LocalDate.of(2026, 1, 1) to LocalDate.of(2026, 8, 31), range("""{"mode":"yearToDate"}"""))
        assertEquals(LocalDate.of(2025, 1, 1) to LocalDate.of(2025, 8, 20), range("""{"mode":"priorYearToDate"}"""))
        assertEquals(LocalDate.of(2026, 7, 1) to LocalDate.of(2026, 7, 31), range("""{"mode":"lastMonth"}"""))
        assertEquals(LocalDate.of(2025, 1, 1) to LocalDate.of(2025, 12, 31), range("""{"mode":"lastYear"}"""))
    }

    @Test fun `a cash flow card without a time frame only counts this month`() {
        val widget = CoreReportEngine.compute(
            DashboardWidgetRow("w", "cash-flow-card", null),
            listOf(tx("old", 20260410, -1_000), tx("now", 20260805, -2_000)), today = today,
        )
        assertEquals(-2_000L, widget.valueCents)
    }

    @Test fun `a net worth card without a time frame charts the last 6 months`() {
        val widget = CoreReportEngine.compute(
            DashboardWidgetRow("w", "net-worth-card", null),
            listOf(tx("old", 20260410, 1_000), tx("now", 20260805, 2_000)), today = today,
        )
        assertEquals(listOf("2026-03-31", "2026-04-30", "2026-05-31", "2026-06-30", "2026-07-31", "2026-08-31"),
            widget.points.map { it.period })
        assertEquals(3_000L, widget.valueCents)
    }

    @Test fun `a day-shaped sliding window widens to whole months for a card`() {
        val widget = CoreReportEngine.compute(
            DashboardWidgetRow("w", "cash-flow-card",
                """{"timeFrame":{"mode":"sliding-window","start":"2025-01-01","end":"2025-01-30"}}"""),
            listOf(tx("july", 20260702, -1_000), tx("august", 20260805, -2_000)), today = today,
        )
        assertEquals(-3_000L, widget.valueCents)
    }
}

/** Upstream `CustomReportListCards`: a widget whose saved report is gone shows a placeholder (actua#949). */
class MissingCustomReportTest {
    private val today = LocalDate.of(2026, 8, 20)

    private fun tx(id: String, date: Int, amount: Long) = ActualTransaction(
        id, "a", date, amount, null, null, null, null, null, false, false, null, false, null, false, null, null, null, null)

    private fun widgets(savedReports: List<SavedReportRow>) = CoreReportEngine.dashboards(
        pages = emptyList(),
        widgets = { listOf(DashboardWidgetRow("w", "custom-report", """{"id":"r1"}""")) },
        transactions = listOf(tx("spent", 20260805, -2_000)),
        accounts = emptyList(), groups = emptyList(), savedReports = savedReports, today = today,
    ).single().widgets

    @Test fun `a widget for a deleted saved report shows no totals`() {
        val widget = widgets(emptyList()).single()
        assertEquals(ReportWidgetKind.MISSING_REPORT, widget.kind)
        assertEquals("This custom report has been deleted.", widget.markdown)
        assertEquals(null, widget.valueCents)
        assertEquals(0, widget.categories.size + widget.points.size)
    }

    @Test fun `a widget for a live saved report still renders it`() {
        val report = SavedReportRow("r1", "Spending", null, null, false, "This month", "Category", "Payment",
            false, false, true, null, "BarGraph", null, "and", "Monthly")
        val widget = widgets(listOf(report)).single()
        assertEquals(ReportWidgetKind.CUSTOM_REPORT, widget.kind)
        assertEquals("Spending", widget.name)
    }
}
