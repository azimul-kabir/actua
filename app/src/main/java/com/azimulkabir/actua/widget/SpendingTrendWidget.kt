package com.azimulkabir.actua.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.ActuaRepository
import com.azimulkabir.actua.data.reports.SpendingTrend
import com.azimulkabir.actua.data.reports.SpendingTrendMonth
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** The Spending trend widget: a bar chart of spending over the recent months. */
internal object SpendingTrendWidget {
    /** Six months of made-up spending, drawn by the real chart code in the chosen style. */
    fun preview(context: Context): RemoteViews {
        val today = java.time.LocalDate.now()
        val sample = listOf(41_200L, 38_500L, 52_000L, 46_100L, 39_900L, 31_000L)
        val trend = SpendingTrend(
            sample.mapIndexed { index, cents ->
                SpendingTrendMonth(java.time.YearMonth.from(today).minusMonths((sample.size - 1 - index).toLong()), cents, current = index == sample.lastIndex)
            },
        )
        return bindTrend(context, 0, TrendLayout.Chart, TrendData(true, trend), 250, 110)
    }

    /** What the Spending trend widget draws: nothing without a budget, or the trend (which may be empty). */
    private class TrendData(val usingBudget: Boolean, val trend: SpendingTrend?)

    fun update(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val repository = ActuaRepository(context)
        val data = try {
            loadBudgetFormats(repository)
            val using = repository.isUsingActualBudget
            TrendData(using, if (using) repository.spendingTrend() else null)
        } finally {
            repository.close()
        }
        // The chart is a bitmap, so it is drawn for the widget's current size and redrawn when it is resized.
        val (widthDp, heightDp) = widgetSizeDp(context, manager, widgetId)
        manager.updateAppWidget(
            widgetId,
            responsive(manager, widgetId, TrendVariants) { bindTrend(context, widgetId, it, data, widthDp, heightDp) },
        )
    }

    /**
     * The Spending trend widget: the headline, and a bar chart of the months drawn into a bitmap sized to
     * the widget ([widthDp] x [heightDp]). Smaller layouts show this month against the last.
     */
    private fun bindTrend(
        context: Context,
        widgetId: Int,
        layout: TrendLayout,
        data: TrendData,
        widthDp: Int,
        heightDp: Int,
    ): RemoteViews {
        val small = layout == TrendLayout.Small
        val views = RemoteViews(
            context.packageName,
            if (small) WidgetStyles.layout(context, R.layout.widget_spending_trend_small, R.layout.widget_spending_trend_small_styled)
            else WidgetStyles.layout(context, R.layout.widget_spending_trend, R.layout.widget_spending_trend_styled),
        )
        views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_spending_trend))
        val trend = data.trend
        val message = when {
            !data.usingBudget -> context.getString(R.string.widget_no_budget)
            trend == null || trend.isEmpty -> context.getString(R.string.widget_trend_no_spending)
            else -> null
        }
        val showContent = message == null
        views.setViewVisibility(R.id.widget_trend_message, if (showContent) View.GONE else View.VISIBLE)
        views.setViewVisibility(R.id.widget_trend_chart, if (showContent) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_trend_total, if (showContent) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_trend_period, if (showContent) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_trend_content, if (showContent) View.VISIBLE else View.GONE)
        if (small) views.setViewVisibility(R.id.widget_title, View.GONE)
        if (message != null || trend == null) {
            views.setTextViewText(R.id.widget_trend_message, message.orEmpty())
            views.setContentDescription(R.id.widget_root, "${context.getString(R.string.widget_spending_trend)}. $message")
            // With no budget there is nothing to open in Reports yet; opening Actua lets the person choose one.
            views.setOnClickPendingIntent(
                R.id.widget_root,
                open(context, if (data.usingBudget) WidgetActions.REPORTS else WidgetActions.BUDGET, widgetId),
            )
            WidgetStyles.apply(context, views)
            return views
        }

        val shown = if (small) SpendingTrend(trend.months.takeLast(2)) else trend
        val hidden = balancesHidden(context)
        val headline = if (small) shown.currentCents else shown.totalCents
        views.setTextViewText(R.id.widget_trend_total, money(context, headline))
        views.setTextViewText(
            R.id.widget_trend_period,
            context.getString(if (small) R.string.widget_trend_period_this_month else R.string.widget_trend_period_months),
        )

        val density = context.resources.displayMetrics.density
        // Leave room for the padding and, in the full layout, the title and period lines above the chart.
        val chartWidthDp = if (small) 44 else (widthDp - 32).coerceAtLeast(60)
        val chartHeightDp = if (small) (heightDp - 12).coerceAtLeast(24) else (heightDp - 32 - 41).coerceAtLeast(36)
        val bitmap = SpendingChart.render(
            shown,
            widthPx = (chartWidthDp * density).toInt().coerceAtMost(MAX_CHART_PX),
            heightPx = (chartHeightDp * density).toInt().coerceAtMost(MAX_CHART_PX),
            colors = WidgetStyles.chartColors(context),
            metrics = context.resources.displayMetrics,
            locale = context.resources.configuration.locales[0],
            // Amounts are left off the picture when balances are hidden, and in the small layout.
            amountLabel = if (small || hidden) null else money(context, shown.currentCents),
        )
        views.setImageViewBitmap(R.id.widget_trend_chart, bitmap)
        views.setContentDescription(R.id.widget_trend_chart, trendDescription(context, shown, hidden))
        views.setOnClickPendingIntent(R.id.widget_root, open(context, WidgetActions.REPORTS, widgetId))
        WidgetStyles.apply(context, views)
        return views
    }

    /** The chart in words for a screen reader: each month and its amount, and the total. Amounts are not read when hidden. */
    private fun trendDescription(context: Context, trend: SpendingTrend, hidden: Boolean): String {
        if (hidden) return context.getString(R.string.widget_cd_spending_trend_hidden)
        val locale = context.resources.configuration.locales[0]
        val monthName = DateTimeFormatter.ofPattern("MMMM", locale)
        fun describe(month: SpendingTrendMonth): String {
            val name = month.month.format(monthName).let {
                if (month.current) context.getString(R.string.widget_cd_trend_this_month, it) else it
            }
            return context.getString(R.string.widget_cd_trend_month, name, money(context, month.spentCents))
        }
        return context.getString(
            R.string.widget_cd_spending_trend,
            trend.months.size, trend.months.joinToString(", ", transform = ::describe), money(context, trend.totalCents),
        )
    }
}
