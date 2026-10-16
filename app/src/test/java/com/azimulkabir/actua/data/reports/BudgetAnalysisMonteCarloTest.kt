package com.azimulkabir.actua.data.reports

import com.azimulkabir.actua.data.budget.model.ActualBudgetMonth
import com.azimulkabir.actua.data.budget.model.ActualCategoryBudget
import com.azimulkabir.actua.model.DrillDownDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** Slice 10 of #1106 (#1116): Budget Analysis overspending and Monte Carlo percentile bands. */
class BudgetAnalysisMonteCarloTest {
    private val today = LocalDate.of(2026, 5, 20)

    private fun category(
        month: String, id: String, budgeted: Long, spent: Long, available: Long, rollover: Boolean = false,
    ) = ActualCategoryBudget(
        month, id, id, "g", "G", 1.0, 1.0, budgeted, spent, available, 0, false, false, null, false, rollover, null, null, null,
    )

    private fun budget(month: String, vararg categories: ActualCategoryBudget) =
        ActualBudgetMonth(month, categories.toList(), emptyList(), null, emptyList(), emptyList())

    private val months = mapOf(
        // food overspends by 300 without rollover; fun overspends by 100 but rolls over.
        YearMonth.of(2026, 3) to budget("2026-03",
            category("2026-03", "food", 1_000, -1_300, -300),
            category("2026-03", "fun", 500, -600, -100, rollover = true),
            category("2026-03", "rent", 2_000, -2_000, 0)),
        // March's balances reset or carry; food is fine this month, fun still negative (carried -100 + 200 - 350).
        YearMonth.of(2026, 4) to budget("2026-04",
            category("2026-04", "food", 1_000, -700, 300),
            category("2026-04", "fun", 500, -350, -250, rollover = true),
            category("2026-04", "rent", 2_000, -2_000, 0)),
        YearMonth.of(2026, 5) to budget("2026-05",
            category("2026-05", "food", 1_000, -400, 900),
            category("2026-05", "fun", 500, -100, 150, rollover = true),
            category("2026-05", "rent", 2_000, -2_000, 0)),
    )

    private val analysis by lazy {
        CoreReportEngine.compute(
            DashboardWidgetRow("ba", "budget-analysis-card", """{"timeFrame":{"mode":"static","start":"2026-03","end":"2026-05"}}"""),
            emptyList(), budgetMonth = { months[it] }, today = today,
        )
    }

    @Test fun `budgeted spent and balance per month are the sums over the selected categories`() {
        assertEquals(listOf(3_500L, 3_500L, 3_500L), analysis.points.map { it.primaryCents })
        assertEquals(listOf(3_900L, 3_050L, 2_500L), analysis.points.map { it.secondaryCents })
        assertEquals(listOf(-400L, 50L, 1_050L), analysis.points.map { it.tertiaryCents })
        assertEquals(1_050L, analysis.balanceCents)
    }

    @Test fun `overspending is last months negative balances on categories without rollover`() {
        // First month has no previous month; March's food (-300) lands in April; fun rolls over so never counts.
        assertEquals(listOf(0L, 300L, 0L), analysis.points.map { it.overspendingCents })
    }

    @Test fun `each month can open its spending in the selected categories`() {
        val april = analysis.points[1].drillDownQuery
        assertNotNull(april)
        assertEquals(setOf("food", "fun", "rent"), april!!.categoryIds)
        assertEquals(DrillDownDirection.EXPENSE, april.direction)
        assertEquals(20260401, april.startDate)
        assertEquals(20260430, april.endDate)
    }

    private val monteCarloMeta = """{"currentAge":60,"targetAge":75,"simulationCount":2000,
        "pots":[{"id":"p1","startingBalance":50000000,"expectedReturnMean":0.05,"returnStdDev":0.12}],
        "spendingPhases":[{"annualWithdrawal":2200000}]}"""

    private fun monteCarlo() = CoreReportEngine.compute(
        DashboardWidgetRow("mc", "monte-carlo-card", monteCarloMeta), emptyList(), today = today,
    )

    @Test fun `monte carlo percentiles are ordered every year`() {
        val points = monteCarlo().points
        assertEquals(16, points.size)
        points.forEach { p ->
            assertEquals(5, p.percentileCents.size)
            p.percentileCents.zipWithNext().forEach { (lower, upper) ->
                assertTrue("${p.period}: ${p.percentileCents}", lower <= upper)
            }
            // The existing median and 10th percentile fields still agree with the bands.
            assertEquals(p.percentileCents[2], p.primaryCents)
            assertEquals(p.percentileCents[0], p.secondaryCents)
        }
    }

    @Test fun `monte carlo repeats exactly for the same seed`() {
        val first = monteCarlo()
        val second = monteCarlo()
        assertEquals(first.percentage, second.percentage)
        assertEquals(first.points, second.points)
    }

    @Test fun `monte carlo success rate and median are unchanged from before the bands`() {
        // Pinned from the engine before percentile bands were added (same fixed seed).
        val widget = monteCarlo()
        assertEquals(97.6, widget.percentage!!, 0.0)
        assertEquals(50_000_000L, widget.points.first().primaryCents)
        assertEquals(50_046_048L, widget.points[1].primaryCents)
        assertEquals(42_737_878L, widget.points[1].secondaryCents)
        assertEquals(38_543_996L, widget.points.last().primaryCents)
        assertEquals(10_337_183L, widget.points.last().secondaryCents)
    }
}
