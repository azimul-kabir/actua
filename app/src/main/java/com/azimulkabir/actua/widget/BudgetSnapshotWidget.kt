package com.azimulkabir.actua.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.text.format.DateFormat
import android.view.View
import android.widget.RemoteViews
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.ActuaRepository
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Date

/** The Budget snapshot widget: ready to budget, budgeted and balance for the current month. */
internal object BudgetSnapshotWidget {
    fun preview(context: Context): RemoteViews {
        val month = YearMonth.now()
            .format(DateTimeFormatter.ofPattern("MMMM yyyy", context.resources.configuration.locales[0]))
        val ready = sample(context, R.string.widget_sample_amount_4)
        val budgeted = sample(context, R.string.widget_sample_amount_5)
        val balance = sample(context, R.string.widget_sample_amount_6)
        return bindBudget(
            context, null, 0, BudgetLayout.Hero,
            BudgetData(month, ready, budgeted, balance, spokenReady = ready, spokenBudgeted = budgeted, spokenBalance = balance),
            null,
        )
    }

    /** The figures as displayed, and as spoken (null when balances are hidden, so they are never read out). */
    private class BudgetData(
        val month: String,
        val ready: String,
        val budgeted: String,
        val balance: String,
        val hasBudget: Boolean = true,
        val spokenReady: String? = null,
        val spokenBudgeted: String? = null,
        val spokenBalance: String? = null,
    )

    private fun loadBudget(context: Context): BudgetData {
        val repository = ActuaRepository(context)
        try {
            loadBudgetFormats(repository)
            if (!repository.isUsingActualBudget) {
                return BudgetData(context.getString(R.string.widget_no_budget), "—", "—", "—", hasBudget = false)
            }
            val overview = repository.budgetOverview()
            val month = YearMonth.now()
                .format(DateTimeFormatter.ofPattern("MMMM yyyy", context.resources.configuration.locales[0]))
            return BudgetData(
                month,
                overview.toBudgetCents?.let { money(context, it) } ?: "—",
                money(context, overview.budgetedCents),
                money(context, overview.availableCents),
                spokenReady = overview.toBudgetCents?.let { spoken(context, it) } ?: "—",
                spokenBudgeted = spoken(context, overview.budgetedCents),
                spokenBalance = spoken(context, overview.availableCents),
            )
        } finally {
            repository.close()
        }
    }

    fun update(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val data = loadBudget(context)
        val updatedAt = DateFormat.getTimeFormat(context).format(Date())
        manager.updateAppWidget(
            widgetId,
            responsive(manager, widgetId, BudgetVariants) { bindBudget(context, manager, widgetId, it, data, updatedAt) },
        )
    }

    /** [manager] and [updatedAt] are null for a picker preview, which has nothing to refresh. */
    private fun bindBudget(
        context: Context,
        manager: AppWidgetManager?,
        widgetId: Int,
        layout: BudgetLayout,
        data: BudgetData,
        updatedAt: String?,
    ): RemoteViews {
        val layoutId = when (layout) {
            BudgetLayout.Mini -> WidgetStyles.layout(context, R.layout.widget_budget_snapshot_mini, R.layout.widget_budget_snapshot_mini_styled)
            BudgetLayout.Small -> WidgetStyles.layout(context, R.layout.widget_budget_snapshot_small, R.layout.widget_budget_snapshot_small_styled)
            BudgetLayout.Strip -> WidgetStyles.layout(context, R.layout.widget_budget_snapshot_strip, R.layout.widget_budget_snapshot_strip_styled)
            BudgetLayout.Compact -> WidgetStyles.layout(context, R.layout.widget_budget_snapshot_compact, R.layout.widget_budget_snapshot_compact_styled)
            BudgetLayout.Hero, BudgetLayout.HeroFull -> WidgetStyles.layout(context, R.layout.widget_budget_snapshot, R.layout.widget_budget_snapshot_styled)
        }
        val views = RemoteViews(context.packageName, layoutId)
        // RemoteViews inflate in the launcher's locale, so rebind every catalog label through
        // the app-language context (needed on API 28-32; API 33+ handles it in the framework).
        views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_budget_snapshot))
        views.setTextViewText(R.id.widget_ready_label, context.getString(R.string.widget_ready))
        views.setTextViewText(R.id.widget_budgeted_label, context.getString(R.string.widget_budgeted))
        views.setTextViewText(R.id.widget_balance_label, context.getString(R.string.widget_balance))
        views.setTextViewText(R.id.widget_month, data.month)
        views.setViewVisibility(R.id.widget_month, View.VISIBLE)
        views.setTextViewText(R.id.widget_ready_value, data.ready)
        views.setTextViewText(R.id.widget_budgeted_value, data.budgeted)
        views.setTextViewText(R.id.widget_balance_value, data.balance)
        views.setOnClickPendingIntent(R.id.widget_root, open(context, WidgetActions.BUDGET, widgetId))
        bindFreshness(context, manager, views, widgetId, layout == BudgetLayout.HeroFull && manager != null, updatedAt.orEmpty())
        // One announcement for the whole widget, whichever layout it is showing.
        val hidden = context.getString(R.string.widget_cd_hidden)
        views.setContentDescription(
            R.id.widget_root,
            if (!data.hasBudget) {
                "${context.getString(R.string.widget_budget_snapshot)}, ${data.month}"
            } else {
                context.getString(
                    R.string.widget_cd_budget_snapshot,
                    context.getString(R.string.widget_budget_snapshot), data.month,
                    context.getString(R.string.widget_ready), data.spokenReady ?: hidden,
                    context.getString(R.string.widget_budgeted), data.spokenBudgeted ?: hidden,
                    context.getString(R.string.widget_balance), data.spokenBalance ?: hidden,
                )
            },
        )
        WidgetStyles.apply(context, views)
        return views
    }
}
