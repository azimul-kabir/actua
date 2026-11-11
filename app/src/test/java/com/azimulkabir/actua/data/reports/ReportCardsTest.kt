package com.azimulkabir.actua.data.reports

import com.azimulkabir.actua.data.budget.model.ActualTransaction
import com.azimulkabir.actua.model.ReportWidget
import com.azimulkabir.actua.model.ReportWidgetKind
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReportCardsTest {
    private val today = LocalDate.of(2026, 11, 12)

    private fun tx(id: String, date: Int, amount: Long) = ActualTransaction(
        id, "checking", date, amount, null, null, "food", null, null, false, false, null,
        false, null, false, null, null, null, null,
    )

    private val pages = listOf(DashboardPageRow("main", "Main"), DashboardPageRow("extra", ""))
    private val widgets = mapOf(
        "main" to listOf(
            DashboardWidgetRow("summary", "summary-card", """{"name":"Groceries this year"}"""),
            DashboardWidgetRow("flow", "cash-flow-card", null),
            DashboardWidgetRow("worth", "net-worth-card", null),
            DashboardWidgetRow("notes", "markdown-card", """{"content":"hello"}"""),
        ),
        "extra" to listOf(
            DashboardWidgetRow("sankey", "sankey-card", null),
            DashboardWidgetRow("age", "age-of-money-card", null),
            DashboardWidgetRow("formula", "formula-card", """{"name":" "}"""),
            DashboardWidgetRow("spending", "spending-card", null),
            DashboardWidgetRow("crossover", "crossover-card", null),
            DashboardWidgetRow("custom", "custom-report", """{"id":"r1"}"""),
            DashboardWidgetRow("calendar", "calendar-card", null),
            DashboardWidgetRow("analysis", "budget-analysis-card", null),
            DashboardWidgetRow("forecast", "balance-forecast-card", null),
            DashboardWidgetRow("monte", "monte-carlo-card", null),
        ),
    )

    private fun offered() = ReportCards.offered(pages) { widgets[it].orEmpty() }

    @Test
    fun offersOnlyTheSingleValueCardsInDashboardOrder() {
        assertEquals(listOf("summary", "worth", "age", "formula", "spending", "crossover"), offered().map { it.id })
    }

    @Test
    fun aCardIsNamedAsInActualOrByItsKindAndSaysWhichPageItIsOn() {
        val cards = offered().associateBy { it.id }
        assertEquals("Groceries this year", cards.getValue("summary").name)
        assertEquals("Summary", cards.getValue("summary").kindLabel)
        assertEquals("Main", cards.getValue("summary").pageName)
        assertEquals("Net Worth", cards.getValue("worth").name)
        assertEquals("a blank name falls back to the kind", "Formula", cards.getValue("formula").name)
        assertEquals("Untitled", cards.getValue("age").pageName)
    }

    @Test
    fun aBudgetFromBeforeDashboardPagesOffersItsUnpagedCards() {
        val cards = ReportCards.offered(emptyList()) { pageId ->
            if (pageId == null) listOf(DashboardWidgetRow("worth", "net-worth-card", null)) else emptyList()
        }
        assertEquals(listOf("worth"), cards.map { it.id })
        assertEquals("Dashboard", cards.single().pageName)
    }

    @Test
    fun nothingIsOfferedWithoutSingleValueCards() {
        assertEquals(emptyList<ReportCardChoice>(), ReportCards.offered(pages) { emptyList() })
    }

    private fun widget(kind: ReportWidgetKind, value: Long? = null, percentage: Double? = null, comparison: Long? = null) =
        ReportWidget("w", kind, "Card", valueCents = value, percentage = percentage, comparisonCents = comparison)

    @Test
    fun moneyCardsKeepIntegerCents() {
        assertEquals(ReportCardHeadline.Money(-12_345), ReportCards.headline(widget(ReportWidgetKind.SUMMARY, -12_345)))
        assertEquals(ReportCardHeadline.Money(9_800_00), ReportCards.headline(widget(ReportWidgetKind.NET_WORTH, 9_800_00)))
        assertEquals(ReportCardHeadline.Money(150_00), ReportCards.headline(widget(ReportWidgetKind.FORMULA, 150_00)))
    }

    @Test
    fun spendingCarriesWhatItIsComparedWith() {
        assertEquals(
            ReportCardHeadline.Money(42_000, comparisonCents = 38_500),
            ReportCards.headline(widget(ReportWidgetKind.SPENDING, 42_000, comparison = 38_500)),
        )
    }

    @Test
    fun aPercentageSummaryIsNotMoney() {
        assertEquals(ReportCardHeadline.Percent(12.34), ReportCards.headline(widget(ReportWidgetKind.SUMMARY, percentage = 12.34)))
    }

    @Test
    fun ageOfMoneyIsDaysAndCrossoverIsMonthsNeverMoney() {
        assertEquals(ReportCardHeadline.Days(27), ReportCards.headline(widget(ReportWidgetKind.AGE_OF_MONEY, 27)))
        // Crossover's comparison is its monthly expenses, not something the headline is compared with.
        assertEquals(
            ReportCardHeadline.MonthsToCrossover(150),
            ReportCards.headline(widget(ReportWidgetKind.CROSSOVER, 150, comparison = 300_000)),
        )
        assertEquals(ReportCardHeadline.MonthsToCrossover(null), ReportCards.headline(widget(ReportWidgetKind.CROSSOVER)))
    }

    @Test
    fun aCardWithNothingToShowIsUnavailable() {
        assertEquals(ReportCardHeadline.Unavailable, ReportCards.headline(widget(ReportWidgetKind.AGE_OF_MONEY)))
        assertEquals(ReportCardHeadline.Unavailable, ReportCards.headline(widget(ReportWidgetKind.FORMULA)))
    }

    @Test
    fun otherKindsHaveNoHeadline() {
        val offeredKinds = setOf(
            ReportWidgetKind.SUMMARY, ReportWidgetKind.NET_WORTH, ReportWidgetKind.SPENDING, ReportWidgetKind.FORMULA,
            ReportWidgetKind.AGE_OF_MONEY, ReportWidgetKind.CROSSOVER,
        )
        (ReportWidgetKind.entries - offeredKinds).forEach { assertNull("$it", ReportCards.headline(widget(it, 1))) }
    }

    @Test
    fun aChosenCardWithACustomTimeFrameMatchesTheDashboard() {
        val rows = listOf(
            tx("old", 20260310, -9_999), tx("a", 20260615, -1_000), tx("b", 20260720, -2_500), tx("c", 20260805, -4_000),
            tx("later", 20261101, -700),
        )
        val custom = DashboardWidgetRow(
            "custom-range", "summary-card", """{"name":"Summer","timeFrame":{"mode":"static","start":"2026-06","end":"2026-08"}}""",
        )
        val cards = listOf(DashboardWidgetRow("worth", "net-worth-card", null), custom)
        fun dashboard(filter: (DashboardWidgetRow) -> Boolean) = CoreReportEngine.dashboards(
            listOf(DashboardPageRow("main", "Main")), { cards }, rows, emptyList(), emptyList(), today = today, widgetFilter = filter,
        ).flatMap { it.widgets }

        // What the widget computes: only the chosen card.
        val chosen = dashboard { it.id == "custom-range" }.single()
        // What Reports computes: the whole dashboard.
        val inReports = dashboard { true }.first { it.id == "custom-range" }
        assertEquals(inReports, chosen)
        assertEquals("Summer", chosen.name)
        assertEquals(ReportCardHeadline.Money(-7_500), ReportCards.headline(chosen))
    }
}
