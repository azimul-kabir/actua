package com.azimulkabir.actua.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.ActuaRepository
import com.azimulkabir.actua.data.reports.IncomeSpending
import com.azimulkabir.actua.data.reports.IncomeSpendingMonth
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** The Income and spending widget: what is left this month, and paired income and spending bars. */
internal object IncomeSpendingWidget {
    /** Six months of made-up income and spending, drawn by the real chart code in the chosen style. */
    fun preview(context: Context): RemoteViews {
        val today = java.time.LocalDate.now()
        val income = listOf(420_000L, 415_000L, 430_000L, 420_000L, 425_000L, 410_000L)
        val spent = listOf(371_200L, 388_500L, 352_000L, 396_100L, 369_900L, 301_000L)
        val series = IncomeSpending(
            income.indices.map { index ->
                IncomeSpendingMonth(
                    java.time.YearMonth.from(today).minusMonths((income.size - 1 - index).toLong()),
                    income[index], spent[index], current = index == income.lastIndex,
                )
            },
        )
        return bindIncomeSpending(context, 0, TrendLayout.Chart, IncomeSpendingData(true, series), 250, 110)
    }

    /** What the Income and spending widget draws: nothing without a budget, or the series (which may be empty). */
    private class IncomeSpendingData(val usingBudget: Boolean, val series: IncomeSpending?)

    fun update(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val repository = ActuaRepository(context)
        val data = try {
            loadBudgetFormats(repository)
            val using = repository.isUsingActualBudget
            IncomeSpendingData(using, if (using) repository.incomeSpending() else null)
        } finally {
            repository.close()
        }
        val (widthDp, heightDp) = widgetSizeDp(context, manager, widgetId)
        manager.updateAppWidget(
            widgetId,
            responsive(manager, widgetId, IncomeSpendingVariants) { bindIncomeSpending(context, widgetId, it, data, widthDp, heightDp) },
        )
    }

    /**
     * The Income and spending widget: what is left this month with its savings rate, this month's income and
     * spending, and paired bars for the months drawn into a bitmap sized to the widget ([widthDp] x
     * [heightDp]). The smallest layout shows this month's income against spending.
     */
    private fun bindIncomeSpending(
        context: Context,
        widgetId: Int,
        layout: TrendLayout,
        data: IncomeSpendingData,
        widthDp: Int,
        heightDp: Int,
    ): RemoteViews {
        val small = layout == TrendLayout.Small
        val views = RemoteViews(
            context.packageName,
            if (small) WidgetStyles.layout(context, R.layout.widget_income_spending_small, R.layout.widget_income_spending_small_styled)
            else WidgetStyles.layout(context, R.layout.widget_income_spending, R.layout.widget_income_spending_styled),
        )
        views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_income_spending))
        val series = data.series
        val message = when {
            !data.usingBudget -> context.getString(R.string.widget_no_budget)
            series == null || series.isEmpty -> context.getString(R.string.widget_is_empty)
            else -> null
        }
        val showContent = message == null
        views.setViewVisibility(R.id.widget_is_message, if (showContent) View.GONE else View.VISIBLE)
        views.setViewVisibility(R.id.widget_is_chart, if (showContent) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_is_left, if (showContent) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_is_period, if (showContent) View.VISIBLE else View.GONE)
        if (small) {
            views.setViewVisibility(R.id.widget_is_content, if (showContent) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.widget_title, View.GONE)
        } else {
            views.setViewVisibility(R.id.widget_is_income, if (showContent) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.widget_is_spent, if (showContent) View.VISIBLE else View.GONE)
        }
        if (message != null || series == null) {
            views.setTextViewText(R.id.widget_is_message, message.orEmpty())
            views.setContentDescription(R.id.widget_root, "${context.getString(R.string.widget_income_spending)}. $message")
            // With no budget there is nothing to open in Reports yet; opening Actua lets the person choose one.
            views.setOnClickPendingIntent(
                R.id.widget_root,
                open(context, if (data.usingBudget) WidgetActions.REPORTS else WidgetActions.BUDGET, widgetId),
            )
            WidgetStyles.apply(context, views)
            return views
        }

        val hidden = balancesHidden(context)
        val shown = if (small) IncomeSpending(series.months.takeLast(1)) else series
        val now = series.current ?: series.months.last()
        views.setTextViewText(R.id.widget_is_left, money(context, now.leftCents))
        // The savings rate is a share of income, so it is left off with the amounts when balances are hidden.
        val rate = now.savingsRatePercent.takeIf { !hidden }
        views.setTextViewText(
            R.id.widget_is_period,
            if (rate != null && !small) context.getString(R.string.widget_is_left_rate, rate)
            else context.getString(R.string.widget_is_left_label),
        )
        if (!small) {
            views.setTextViewText(R.id.widget_is_income, context.getString(R.string.widget_is_income, money(context, now.incomeCents)))
            views.setTextViewText(R.id.widget_is_spent, context.getString(R.string.widget_is_spent, money(context, now.spentCents)))
        }

        val density = context.resources.displayMetrics.density
        // Leave room for the padding and, in the full layout, the title, period and amount lines above the chart.
        val chartWidthDp = if (small) 44 else (widthDp - 32).coerceAtLeast(60)
        val chartHeightDp = if (small) (heightDp - 12).coerceAtLeast(24) else (heightDp - 32 - 56).coerceAtLeast(30)
        val bitmap = IncomeSpendingChart.render(
            shown,
            widthPx = (chartWidthDp * density).toInt().coerceAtMost(MAX_CHART_PX),
            heightPx = (chartHeightDp * density).toInt().coerceAtMost(MAX_CHART_PX),
            colors = WidgetStyles.chartColors(context),
            metrics = context.resources.displayMetrics,
            locale = context.resources.configuration.locales[0],
        )
        views.setImageViewBitmap(R.id.widget_is_chart, bitmap)
        views.setContentDescription(R.id.widget_is_chart, incomeSpendingDescription(context, shown, now, hidden))
        views.setOnClickPendingIntent(R.id.widget_root, open(context, WidgetActions.REPORTS, widgetId))
        WidgetStyles.apply(context, views)
        return views
    }

    /**
     * The chart in words for a screen reader: each month's income and spending, and this month's left over
     * and savings rate. Nothing is read when balances are hidden.
     */
    private fun incomeSpendingDescription(context: Context, series: IncomeSpending, now: IncomeSpendingMonth, hidden: Boolean): String {
        if (hidden) return context.getString(R.string.widget_cd_income_spending_hidden)
        val locale = context.resources.configuration.locales[0]
        val monthName = DateTimeFormatter.ofPattern("MMMM", locale)
        fun describe(month: IncomeSpendingMonth): String {
            val name = month.month.format(monthName).let {
                if (month.current) context.getString(R.string.widget_cd_is_this_month, it) else it
            }
            return context.getString(
                R.string.widget_cd_is_month, name, money(context, month.incomeCents), money(context, month.spentCents),
            )
        }
        val summary = context.getString(
            R.string.widget_cd_income_spending, series.months.size, series.months.joinToString(", ", transform = ::describe),
        )
        val left = context.getString(R.string.widget_cd_is_left, money(context, now.leftCents))
        val rate = now.savingsRatePercent?.let { context.getString(R.string.widget_cd_is_rate, it) }
        return listOfNotNull(summary, left, rate).joinToString(" ")
    }
}
