package com.azimulkabir.actua.ui.reports

import com.azimulkabir.actua.model.ReportCategory
import com.azimulkabir.actua.model.ReportPoint
import com.azimulkabir.actua.model.ReportWidget
import com.azimulkabir.actua.model.ReportWidgetKind

/** One row of a widget's data table; [drill] opens its transactions when it has any. */
data class ReportTableRow(val cells: List<String>, val drill: ReportCategory? = null) {
    val canDrill: Boolean get() = drill != null && (drill.drillDownQuery != null || drill.transactionIds.isNotEmpty())
}

/** Exact figures behind a chart: the accessible way to read it, with one row per period or category. */
data class ReportDataTable(val headers: List<String>, val rows: List<ReportTableRow>)

/**
 * The table for [widget], or null when it has no figures worth tabulating (notes, missing widgets).
 * [money] formats cents the way the rest of the screen does.
 */
fun reportDataTable(widget: ReportWidget, money: (Long) -> String): ReportDataTable? {
    fun pointDrill(point: ReportPoint, name: String, spent: Long) = ReportCategory(
        name, spent, point.transactionIds, drillDownQuery = point.drillDownQuery,
    )
    fun periodRows(build: (ReportPoint) -> ReportTableRow) = widget.points.map(build)

    return when (widget.kind) {
        ReportWidgetKind.NET_WORTH, ReportWidgetKind.BALANCE_FORECAST -> ReportDataTable(
            listOf("Period", "Value"),
            periodRows { p -> ReportTableRow(listOf(p.period.take(10), money(p.primaryCents)), pointDrill(p, p.period.take(10), p.primaryCents)) },
        )
        ReportWidgetKind.AGE_OF_MONEY -> ReportDataTable(
            listOf("Period", "Age"),
            periodRows { p -> ReportTableRow(listOf(p.period.take(10), "${p.primaryCents} days")) },
        )
        ReportWidgetKind.CASH_FLOW, ReportWidgetKind.INCOME_EXPENSE -> ReportDataTable(
            listOf("Period", "Income", "Expense", "Net"),
            periodRows { p ->
                val net = p.primaryCents - p.secondaryCents
                ReportTableRow(
                    listOf(p.period.take(7), money(p.primaryCents), money(p.secondaryCents), money(net)),
                    ReportCategory(
                        p.period.take(7), net, p.transactionIds, incomeCents = p.primaryCents,
                        expenseCents = p.secondaryCents, drillDownQuery = p.drillDownQuery,
                    ),
                )
            },
        )
        ReportWidgetKind.BUDGET_ANALYSIS -> ReportDataTable(
            listOf("Period", "Budgeted", "Spent", "Overspending", "Balance"),
            periodRows { p ->
                ReportTableRow(
                    listOf(p.period.take(7), money(p.primaryCents), money(p.secondaryCents), money(p.overspendingCents), money(p.tertiaryCents)),
                    ReportCategory("${p.period.take(7)} · Spent", p.secondaryCents, p.transactionIds,
                        expenseCents = p.secondaryCents, drillDownQuery = p.drillDownQuery),
                )
            },
        )
        ReportWidgetKind.CROSSOVER -> ReportDataTable(
            listOf("Period", "Investment income", "Monthly expenses"),
            periodRows { p -> ReportTableRow(listOf(p.period.take(7), money(p.primaryCents), money(p.secondaryCents))) },
        )
        ReportWidgetKind.MONTE_CARLO -> ReportDataTable(
            listOf("Period", "10th", "25th", "Median", "75th", "90th"),
            periodRows { p ->
                val bands = p.percentileCents.takeIf { it.size == 5 } ?: List(5) { 0L }
                ReportTableRow(listOf(p.period.take(7)) + bands.map(money))
            },
        )
        ReportWidgetKind.CALENDAR -> ReportDataTable(
            listOf("Date", "In", "Out", "Net"),
            periodRows { p ->
                val net = p.primaryCents - p.secondaryCents
                ReportTableRow(
                    listOf(p.period, money(p.primaryCents), money(p.secondaryCents), money(net)),
                    ReportCategory(p.period, net, p.transactionIds, incomeCents = p.primaryCents,
                        expenseCents = p.secondaryCents, drillDownQuery = p.drillDownQuery),
                )
            },
        )
        ReportWidgetKind.SANKEY -> ReportDataTable(
            listOf("Type", "Name", "Amount"),
            widget.incomeCategories.filter { it.spentCents > 0 }.map { ReportTableRow(listOf("Income", it.name, money(it.spentCents)), it) } +
                widget.categories.filter { it.spentCents > 0 }.map { ReportTableRow(listOf("Expense", it.name, money(it.spentCents)), it) },
        )
        ReportWidgetKind.SPENDING -> ReportDataTable(
            listOf("Period", "Amount"),
            listOf(
                ReportTableRow(listOf("This month", money(widget.valueCents ?: 0)),
                    ReportCategory("This month", widget.valueCents ?: 0, widget.valueTransactionIds, drillDownQuery = widget.valueDrillDownQuery)),
                ReportTableRow(listOf("Comparison", money(widget.comparisonCents ?: 0)),
                    ReportCategory("Comparison", widget.comparisonCents ?: 0, widget.comparisonTransactionIds, drillDownQuery = widget.comparisonDrillDownQuery)),
            ),
        )
        ReportWidgetKind.CUSTOM_REPORT -> {
            val categories = widget.categories.filter { it.spentCents != 0L }
            val total = categories.sumOf { kotlin.math.abs(it.spentCents) }.coerceAtLeast(1)
            if (categories.isNotEmpty()) ReportDataTable(
                listOf("Name", "Amount", "Share"),
                categories.map { c ->
                    ReportTableRow(listOf(c.name, money(c.spentCents), "${"%.1f".format(kotlin.math.abs(c.spentCents) * 100.0 / total)}%"), c)
                },
            ) else ReportDataTable(
                listOf("Period", "Amount"),
                periodRows { p -> ReportTableRow(listOf(p.period.take(10), money(p.primaryCents)), pointDrill(p, p.period.take(10), p.primaryCents)) },
            )
        }
        ReportWidgetKind.SUMMARY, ReportWidgetKind.FORMULA -> ReportDataTable(
            listOf("Value"),
            listOf(ReportTableRow(listOf(widget.percentage?.let { "${"%.2f".format(it)}%" } ?: money(widget.valueCents ?: 0)))),
        )
        ReportWidgetKind.MARKDOWN, ReportWidgetKind.MISSING_REPORT, ReportWidgetKind.UNSUPPORTED -> null
    }
}

/** Widget kinds whose data spans a time range the viewer can change in the full-screen view. */
fun isRangeable(kind: ReportWidgetKind): Boolean = when (kind) {
    ReportWidgetKind.NET_WORTH, ReportWidgetKind.CASH_FLOW, ReportWidgetKind.INCOME_EXPENSE,
    ReportWidgetKind.AGE_OF_MONEY, ReportWidgetKind.BUDGET_ANALYSIS, ReportWidgetKind.CALENDAR,
    ReportWidgetKind.SANKEY, ReportWidgetKind.CUSTOM_REPORT -> true
    else -> false
}

/** Widget kinds that offer the full-screen view. */
fun canExpand(kind: ReportWidgetKind): Boolean = when (kind) {
    ReportWidgetKind.MARKDOWN, ReportWidgetKind.MISSING_REPORT, ReportWidgetKind.UNSUPPORTED -> false
    else -> true
}
