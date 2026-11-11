package com.azimulkabir.actua.data.reports

import com.azimulkabir.actua.model.ReportWidget
import com.azimulkabir.actua.model.ReportWidgetKind

/** A dashboard card the Report card home-screen widget can show: its name, kind and the page it is on. */
data class ReportCardChoice(val id: String, val name: String, val kindLabel: String, val pageName: String)

/** A report card's headline value, in the unit its kind is measured in. Only [Money] is an amount of money. */
sealed interface ReportCardHeadline {
    /** Integer cents, with the value the card compares it against when it has one (Spending). */
    data class Money(val cents: Long, val comparisonCents: Long? = null) : ReportCardHeadline

    /** A Summary card set to show a percentage. */
    data class Percent(val value: Double) : ReportCardHeadline

    /** Age of Money, in days. */
    data class Days(val days: Long) : ReportCardHeadline

    /** Crossover: the months until investment income covers expenses, or null when that is never reached. */
    data class MonthsToCrossover(val months: Long?) : ReportCardHeadline

    /** The card has nothing to show yet, such as an Age of Money with no spending or a formula that cannot be evaluated. */
    data object Unavailable : ReportCardHeadline
}

/** Which dashboard cards the Report card widget offers, and the single value each one comes down to. */
object ReportCards {
    /**
     * The dashboard card types with one headline value. Charts and dense cards (Cash Flow, Calendar, Sankey,
     * Budget Analysis, Balance Forecast, Monte Carlo, custom reports, notes) are left out.
     */
    val supportedTypes: Set<String> = setOf(
        "summary-card", "formula-card", "net-worth-card", "age-of-money-card", "spending-card", "crossover-card",
    )

    /** The cards of every dashboard page that the widget can show, in dashboard order. */
    fun offered(pages: List<DashboardPageRow>, widgets: (String?) -> List<DashboardWidgetRow>): List<ReportCardChoice> =
        CoreReportEngine.resolvedPages(pages).flatMap { page ->
            widgets(page.id.ifBlank { null }).filter { it.type in supportedTypes }.map { row ->
                ReportCardChoice(row.id, CoreReportEngine.cardName(row), CoreReportEngine.label(row.type), page.name.ifBlank { "Untitled" })
            }
        }

    /** The headline of a computed card, or null for a kind the widget does not show. */
    fun headline(widget: ReportWidget): ReportCardHeadline? = when (widget.kind) {
        ReportWidgetKind.SUMMARY -> widget.percentage?.let { ReportCardHeadline.Percent(it) }
            ?: ReportCardHeadline.Money(widget.valueCents ?: 0)
        ReportWidgetKind.NET_WORTH -> ReportCardHeadline.Money(widget.valueCents ?: 0)
        ReportWidgetKind.SPENDING -> ReportCardHeadline.Money(widget.valueCents ?: 0, widget.comparisonCents)
        ReportWidgetKind.FORMULA -> widget.valueCents?.let { ReportCardHeadline.Money(it) } ?: ReportCardHeadline.Unavailable
        ReportWidgetKind.AGE_OF_MONEY -> widget.valueCents?.let { ReportCardHeadline.Days(it) } ?: ReportCardHeadline.Unavailable
        ReportWidgetKind.CROSSOVER -> ReportCardHeadline.MonthsToCrossover(widget.valueCents)
        else -> null
    }
}
